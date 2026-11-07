package com.yeivikas.olyzecs.engine.audio

import android.content.Context
import com.yeivikas.olyzecs.engine.audio.decode.WAV_HEADER_BYTES
import com.yeivikas.olyzecs.engine.audio.decode.wavHeaderBytes
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Almacén en disco de WAVs renderizados para la vista previa (audio invertido, mezcla seca de carriles con Reverb): la mecánica de
 * archivos que comparten [ReversedAudioCache] y [DryTrackCache]. NO decide qué ni cuándo renderizar; solo guarda y encuentra.
 *
 *  - El nombre del archivo es un SHA-256 de la clave: una colisión haría sonar el audio de OTRO origen.
 *  - Escritura atómica (archivo temporal + rename): el reproductor jamás ve un WAV a medio escribir.
 *  - Solo se conservan [maxFiles] archivos; se descartan primero los menos recientemente usados (es caché, no datos del usuario:
 *    si se pierde, se vuelve a renderizar). "Usado" incluye leerlo ([peek]), no solo escribirlo, y quien guarda puede PROTEGER las
 *    claves que siguen en uso: sin eso, con más carriles/archivos en uso que [maxFiles], cada render borraba el WAV de otro
 *    y los renders se encadenaban sin fin.
 */
internal class PreviewWavCache(private val dirName: String, private val maxFiles: Int) {

    private fun fileFor(context: Context, key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, dirName), "$name.wav")
    }

    /** El WAV guardado bajo [key], o `null` si todavía no existe. Marca el archivo como usado (con un mínimo de E/S: el preview pregunta a ~20 Hz). */
    fun peek(context: Context, key: String): File? {
        val file = fileFor(context, key).takeIf { it.isFile && it.length() > WAV_HEADER_BYTES } ?: return null
        val now = System.currentTimeMillis()
        if (now - file.lastModified() > TOUCH_INTERVAL_MS) file.setLastModified(now)
        return file
    }

    /**
     * Dónde vive el WAV de [key], exista o no todavía. Para quien lo produce FUERA de la JVM (el motor nativo escribe el
     * archivo de forma atómica con `tmp` + `rename`): ver [finishExternalWrite].
     */
    fun targetFile(context: Context, key: String): File {
        val file = fileFor(context, key)
        file.parentFile?.mkdirs()
        return file
    }

    /** Tras escribir [key] con [targetFile]: lo registra como usado y purga los menos usados (sin tocar [protectedKeys]). */
    fun finishExternalWrite(context: Context, key: String, protectedKeys: Collection<String> = emptyList()) {
        val target = fileFor(context, key)
        purgeOld(target.parentFile, keep = setOf(target.name) + protectedKeys.map { fileFor(context, it).name })
    }

    /**
     * Escribe [samples] (16-bit, intercalado) como el WAV de [key] y purga los menos usados, sin tocar los de
     * [protectedKeys] (los que todavía se están reproduciendo o esperando).
     */
    fun store(
        context: Context,
        key: String,
        samples: ShortArray,
        sampleRateHz: Int,
        channels: Int,
        protectedKeys: Collection<String> = emptyList()
    ) {
        val target = fileFor(context, key)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.outputStream().buffered().use { writeWav16(it, samples, sampleRateHz, channels) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        purgeOld(target.parentFile, keep = setOf(target.name) + protectedKeys.map { fileFor(context, it).name })
    }

    private fun purgeOld(dir: File?, keep: Set<String>) {
        if (dir == null) return
        purgeDirectory(dir, keep, maxFiles, System.currentTimeMillis())
    }

    private companion object {
        /** Cada cuánto se refresca la fecha de uso de un WAV que se sigue leyendo (basta una vez por minuto para ordenar por uso). */
        const val TOUCH_INTERVAL_MS = 60_000L
    }
}

/** Sufijo del WAV intermedio que deja el decodificador mientras el motor nativo lo convierte (ver `EngineSourceCache`). */
internal const val INTERMEDIATE_WAV_SUFFIX = ".src.wav"

/**
 * Un temporal (`.tmp`) o intermedio solo se considera ABANDONADO pasado este tiempo sin que nadie lo escriba. Es largo a propósito: el
 * intermedio queda quieto mientras el motor nativo lo convierte, y una fuente de horas tarda minutos. Un archivo vivo se está escribiendo
 * (su fecha cambia) o se lee; uno que lleva media hora intacto es de un proceso que murió.
 */
internal const val STALE_TEMP_MS = 30 * 60_000L

/**
 * Purga de [dir], pura sobre archivos (se prueba con una carpeta temporal).
 *
 *  - Temporales e intermedios: se borran SOLO los abandonados ([STALE_TEMP_MS]). Antes se borraba todo `.tmp`, lo que era correcto con un
 *    único hilo escribiendo, pero con dos preparaciones a la vez (carril corto y largo) eliminaba el temporal de la OTRA justo antes de
 *    su `rename`, y esa fuente fallaba sin culpa.
 *  - WAV de caché: se conservan [maxFiles]; salen primero los menos recientemente usados, nunca los de [keep]. Los intermedios no cuentan
 *    como WAV de caché (no son datos reutilizables ni deben competir con ellos).
 */
internal fun purgeDirectory(dir: File, keep: Set<String>, maxFiles: Int, nowMs: Long) {
    val all = dir.listFiles { f -> f.isFile } ?: return
    all.filter { it.name.endsWith(".tmp") || it.name.endsWith(INTERMEDIATE_WAV_SUFFIX) }
        .filter { nowMs - it.lastModified() > STALE_TEMP_MS }
        .forEach { it.delete() }
    val files = all.filter { it.name.endsWith(".wav") && !it.name.endsWith(INTERMEDIATE_WAV_SUFFIX) }
    if (files.size <= maxFiles) return
    files.filter { it.name !in keep }
        .sortedBy { it.lastModified() }
        .take(files.size - maxFiles)
        .forEach { it.delete() }
}

/**
 * Escribe [samples] (16-bit, intercalado por canal) como un WAV PCM canónico de 44 bytes de cabecera. Pura sobre un
 * [OutputStream] — se prueba con un `ByteArrayOutputStream`, sin archivos ni Android.
 */
internal fun writeWav16(out: OutputStream, samples: ShortArray, sampleRateHz: Int, channels: Int) {
    val dataBytes = samples.size.toLong() * 2L
    out.write(wavHeaderBytes(dataBytes, sampleRateHz, channels))
    // En bloques: evita duplicar todo el PCM en un ByteArray gigante.
    val chunkSamples = 32 * 1024
    val buf = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
    var i = 0
    while (i < samples.size) {
        val n = minOf(chunkSamples, samples.size - i)
        buf.clear()
        for (k in 0 until n) buf.putShort(samples[i + k])
        out.write(buf.array(), 0, n * 2)
        i += n
    }
    out.flush()
}
