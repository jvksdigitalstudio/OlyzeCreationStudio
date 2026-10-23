package com.yeivikas.olyzecs.engine.audio.nativeengine

/**
 * Declaraciones JNI del motor de audio nativo EliNer (`app/src/main/cpp`). Es lo ÚNICO que toca la frontera nativa:
 * la lógica vive en [NativeAudioEngine] y el resto de la app no sabe que hay C++ detrás.
 *
 * Los nombres y firmas deben coincidir con `cpp/android/jni_bridge.cpp` (macro `JNI_FN`). `proguard-rules.pro` ya conserva
 * `engine.**`, así que R8 no renombra estos métodos.
 *
 * Todas las funciones se llaman desde hilos de control (principal o de trabajo), nunca desde el hilo de audio.
 */
internal object NativeEngineBindings {

    /** `false` si la biblioteca nativa no pudo cargarse (ABI no soportada): la app cae al reproductor anterior en vez de cerrarse. */
    val isAvailable: Boolean = try {
        System.loadLibrary("olyzeaudio")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    // --- ciclo de vida ---
    @JvmStatic external fun nativeEngineRateHz(): Int
    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeStartStream(handle: Long): Boolean
    @JvmStatic external fun nativeStopStream(handle: Long)

    // --- fuentes ---
    /**
     * Convierte la VENTANA [startFrame, startFrame + frameCount) del WAV estéreo de 16 bits [inPath] a la tasa del motor y la deja en
     * [outPath] (atómico). `null` si convirtió; si no, el motivo. La ventana es la parte que queda tras el recorte gapless.
     */
    @JvmStatic external fun nativeConvertWavToEngineRate(inPath: String, outPath: String, startFrame: Long, frameCount: Long): String?
    /** `null` si cargó; si no, el motivo. */
    @JvmStatic external fun nativeLoadSource(handle: Long, sourceId: Long, path: String): String?
    @JvmStatic external fun nativeUnloadSource(handle: Long, sourceId: Long)

    // --- línea de tiempo ---
    /**
     * Publica la línea de tiempo por CARRILES. [clipLongs]: 8 valores por clip (trackId, sourceId, startFrame, lengthFrames,
     * trimFrame, fadeInFrames, fadeOutFrames, flags: bit0 = loop, bit1 = reversed); [clipFloats]: 2 (gain, pan).
     * [trackLongs]: 2 por carril (trackId, hasReverb 0/1); [trackFloats]: los 11 campos de `ReverbParams` por carril, en el
     * orden de `ReverbParams.toNativeArray`. Devuelve cuántos clips esperan aún su fuente, o -1 si el formato es inválido.
     */
    @JvmStatic external fun nativeSetTimeline(
        handle: Long,
        clipLongs: LongArray,
        clipFloats: FloatArray,
        clipCount: Int,
        trackLongs: LongArray,
        trackFloats: FloatArray,
        trackCount: Int,
        projectEndFrame: Long
    ): Int

    /**
     * Mueve las perillas del reverb de un carril SIN publicar una línea de tiempo. `false` si el carril no tiene reverb o el
     * cambio es estructural (`size`/`preDelayMs`): entonces hay que publicar la línea de tiempo.
     */
    @JvmStatic external fun nativeSetReverbParams(handle: Long, trackId: Long, params: FloatArray): Boolean

    // --- transporte ---
    @JvmStatic external fun nativePlay(handle: Long): Boolean
    @JvmStatic external fun nativePause(handle: Long): Boolean
    @JvmStatic external fun nativeSeek(handle: Long, frame: Long): Boolean
    /**
     * Plan de bucle: [startsAndEnds] lleva `[inicio0, fin0, inicio1, fin1, …]` en frames (ordenados y sin solaparse; el motor lo
     * normaliza igualmente). [rangeCount] = 0 lo desactiva.
     */
    @JvmStatic external fun nativeSetLoopPlan(handle: Long, startsAndEnds: LongArray, rangeCount: Int)

    // --- estado ---
    @JvmStatic external fun nativeAudiblePositionFrames(handle: Long): Long
    @JvmStatic external fun nativeIsStreamRunning(handle: Long): Boolean
    /** Rellena [out] (≥ 5): underruns, latencia (frames), frames por ráfaga, tamaño de búfer, 1 si baja latencia. */
    @JvmStatic external fun nativeStreamStats(handle: Long, out: LongArray)
}
