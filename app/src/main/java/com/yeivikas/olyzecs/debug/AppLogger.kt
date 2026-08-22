package com.yeivikas.olyzecs.debug

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Registro de errores en tiempo real de toda la app — la versión "Debug"
 * de Olyze (mismo concepto que la pestaña Debug de FL Studio o el log
 * de un build fallido de GitHub Actions): un único punto central donde
 * queda anotado TODO lo que sale mal mientras la app corre, sin importar
 * en qué pantalla o hilo haya pasado.
 *
 * Captura tres cosas:
 *  1. Crashes fatales de cualquier hilo (vía [Thread.setDefaultUncaughtExceptionHandler]).
 *  2. Errores/advertencias que el propio código reporta a mano con
 *     [e]/[w]/[i] — reemplazo directo de `android.util.Log` en los
 *     puntos donde ya se manejaban fallos (export de video, decodificación
 *     de audio, shaders, miniaturas, etc.), así el registro no se pierde
 *     dentro de logcat, adonde el usuario final no tiene acceso.
 *  3. Cualquier excepción que otra parte de la app decida envolver con
 *     [runCatchingLogged] en el futuro.
 *
 * REGLA DE ORO al llamar [e]/[w] desde cualquier parte del código: solo se
 * registra algo cuando de verdad salió mal. Un archivo que todavía no
 * existe porque el proyecto es nuevo, un permiso que un proveedor no
 * soporta pero no hace falta, etc. NO son errores — no deben pasar por
 * acá, para que el registro no se llene de falsas alarmas y lo que sí
 * aparece se pueda tomar en serio.
 *
 * Esta es la ÚNICA pieza del proyecto que sabe guardar/exponer errores:
 * ni `AudioProcessor`, ni `VideoExporter`, ni ningún otro módulo mantiene
 * su propio historial paralelo — todos delegan acá, incluyendo el
 * "último error entendible para mostrarle al usuario" ([lastUserFacingError]/
 * [consumeLastUserFacingError]), que antes vivía duplicado dentro de
 * `AudioProcessor`. Si en el futuro hace falta registrar algo nuevo, esto
 * (y solo esto) es el archivo a tocar.
 *
 * Se guarda en memoria (para la pantalla de "Registro de errores", vía
 * [entries] como StateFlow reactivo) Y en disco (para que un crash fatal,
 * que mata el proceso, no borre la evidencia — al reabrir la app el
 * historial sigue ahí). El archivo se recorta solo si crece demasiado,
 * nunca crece sin límite.
 */
object AppLogger {

    enum class Level { INFO, WARN, ERROR }

    /** Un registro individual, ya formateado como bloque de texto listo para mostrar/copiar. */
    data class LogEntry(
        val timestampMillis: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val stackTrace: String?
    )

    private const val LOG_FILE_NAME = "olyze_registro_errores.log"
    private const val ENTRY_SEPARATOR = "\n§§§\n" // separador poco común, no choca con stacktraces normales
    private const val MAX_ENTRIES_IN_MEMORY = 400
    private const val MAX_FILE_SIZE_BYTES = 400 * 1024 // ~400 KB — de sobra para cientos de errores con stacktrace

    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false

    // Último error "entendible" que un módulo quiere mostrarle al usuario en
    // su propia UI (p. ej. el motivo por el que falló un export) — la ÚNICA
    // pieza de estado de este tipo en todo el proyecto, para que ningún otro
    // módulo tenga que mantener su propia variable paralela para lo mismo.
    // Se limpia solo al leerse (ver [consumeLastUserFacingError]), así nunca
    // arrastra un error viejo a un intento nuevo.
    @Volatile private var lastUserFacingError: String? = null

    /**
     * Se llama UNA vez desde [com.yeivikas.olyzecs.OlyzeApp.onCreate],
     * antes que cualquier otra cosa — así el handler de crashes queda activo
     * desde el primer instante de vida del proceso.
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        installUncaughtExceptionHandler()
        loadPersistedEntries()
        i("AppLogger", "Registro de errores iniciado — ${oneLineDeviceSummary()}")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) = record(Level.ERROR, tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = record(Level.WARN, tag, message, throwable)
    fun i(tag: String, message: String) = record(Level.INFO, tag, message, null)

    /**
     * Guarda [message] como el último error "para humanos" disponible — lo
     * usan pantallas que quieren mostrar un motivo de fallo puntual (p. ej.
     * "no se pudo exportar el audio") sin tener que llevar su propia
     * variable de estado. No reemplaza a [e]/[w]: quien llame a esto
     * normalmente YA llamó a [e] o [w] antes con el detalle técnico
     * completo — esto es solo el resumen corto para la UI.
     */
    fun setLastUserFacingError(message: String) {
        lastUserFacingError = message
    }

    /** Lee el último error "para humanos" guardado con [setLastUserFacingError] y lo limpia (no se repite en el próximo intento). */
    fun consumeLastUserFacingError(): String? {
        val message = lastUserFacingError
        lastUserFacingError = null
        return message
    }

    /** Ejecuta [block]; si lanza una excepción, la registra como ERROR y la vuelve a lanzar (no la "traga"). */
    inline fun <T> runCatchingLogged(tag: String, message: String, block: () -> T): T {
        try {
            return block()
        } catch (t: Throwable) {
            e(tag, message, t)
            throw t
        }
    }

    /** Borra el historial completo, tanto en memoria como en disco. */
    fun clear() {
        _entries.value = emptyList()
        ioExecutor.execute {
            try {
                appContext?.let { File(it.filesDir, LOG_FILE_NAME).delete() }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Arma el texto completo, formateado y listo para pegar en un chat: encabezado con
     * info del dispositivo/versión + cada entrada en orden cronológico. Es lo que usa
     * el botón "Copiar todo" de la pantalla de registro.
     */
    fun formatAllForCopy(): String {
        val header = buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("OLYZE — REGISTRO DE ERRORES")
            appendLine("═══════════════════════════════════════")
            deviceInfoLines().forEach { appendLine(it) }
            appendLine("Total de registros: ${_entries.value.size}")
            appendLine("═══════════════════════════════════════")
        }
        if (_entries.value.isEmpty()) {
            return header + "\n(Sin errores registrados todavía — la app viene funcionando limpia.)"
        }
        return header + "\n" + _entries.value.joinToString("\n\n") { formatEntry(it) }
    }

    // --- Interno ------------------------------------------------------------

    /**
     * Todo lo que hace falta para reproducir/diagnosticar un problema desde
     * otro dispositivo: fabricante, modelo, versión de Android, arquitectura,
     * memoria, pantalla y versión de la app — nada de identificadores
     * personales (ni cuenta, ni ubicación, ni contactos, ni nada que
     * identifique a la persona, solo datos del propio sistema/hardware que
     * corre la app).
     */
    private fun deviceInfoLines(): List<String> {
        val ctx = appContext
        val lines = mutableListOf<String>()
        lines += "Generado: ${timeFormatter.format(java.util.Date())}"
        lines += "App: Olyze Creation Studio ${appVersionName()} (build ${appVersionCode()}) · ${if (isDebugBuild()) "debug" else "release"}"
        lines += "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})" +
            if (!Build.BRAND.equals(Build.MANUFACTURER, ignoreCase = true)) " · marca ${Build.BRAND}" else ""
        lines += "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.joinToString("/")}"
        if (ctx != null) {
            memoryInfoLine(ctx)?.let { lines += it }
            screenInfoLine(ctx)?.let { lines += it }
            lines += "Idioma del sistema: ${Locale.getDefault()}"
        }
        return lines
    }

    private fun memoryInfoLine(ctx: Context): String? = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalGb = info.totalMem / 1_073_741_824.0
        val availGb = info.availMem / 1_073_741_824.0
        "RAM: %.1f GB total, %.1f GB libre%s".format(
            Locale.US, totalGb, availGb, if (info.lowMemory) " (¡memoria baja!)" else ""
        )
    } catch (_: Exception) {
        null
    }

    private fun screenInfoLine(ctx: Context): String? = try {
        val metrics: DisplayMetrics = ctx.resources.displayMetrics
        "Pantalla: ${metrics.widthPixels}x${metrics.heightPixels}px · densidad ${metrics.densityDpi}dpi"
    } catch (_: Exception) {
        null
    }

    private fun isDebugBuild(): Boolean {
        val ctx = appContext ?: return false
        return try {
            (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) {
            false
        }
    }

    /** Resumen de una sola línea, para el mensaje de arranque del propio registro (ver [init]). */
    private fun oneLineDeviceSummary(): String =
        "Olyze Creation Studio ${appVersionName()} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    private fun record(level: Level, tag: String, message: String, throwable: Throwable?) {
        // Se sigue mandando a Logcat igual que antes: no se pierde nada para
        // quien esté conectado por USB/Android Studio, esto es un AGREGADO.
        when (level) {
            Level.ERROR -> Log.e(tag, message, throwable)
            Level.WARN -> Log.w(tag, message, throwable)
            Level.INFO -> Log.i(tag, message)
        }
        val entry = LogEntry(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            stackTrace = throwable?.stackTraceToString()
        )
        _entries.update { (it + entry).takeLast(MAX_ENTRIES_IN_MEMORY) }
        persist(entry, synchronous = false)
    }

    private fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val entry = LogEntry(
                    timestampMillis = System.currentTimeMillis(),
                    level = Level.ERROR,
                    tag = "FATAL",
                    message = "La app se cerró por un error no controlado en el hilo '${thread.name}': ${throwable.message}",
                    stackTrace = throwable.stackTraceToString()
                )
                _entries.update { (it + entry).takeLast(MAX_ENTRIES_IN_MEMORY) }
                // Escritura SINCRÓNICA a propósito: el proceso está a punto de morir,
                // no hay garantía de que la cola del executor llegue a correr.
                persist(entry, synchronous = true)
            } catch (_: Throwable) {
                // Nunca dejar que el propio logger tape la excepción original.
            }
            // Delega al handler por defecto del sistema para que la app siga
            // cerrándose de forma normal (mismo comportamiento de siempre).
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun formatEntry(entry: LogEntry): String {
        val levelLabel = when (entry.level) {
            Level.ERROR -> "ERROR"
            Level.WARN -> "AVISO"
            Level.INFO -> "INFO"
        }
        val time = timeFormatter.format(java.util.Date(entry.timestampMillis))
        return buildString {
            append("[$time] $levelLabel/${entry.tag}: ${entry.message}")
            if (entry.stackTrace != null) {
                append("\n")
                append(entry.stackTrace.trimEnd())
            }
        }
    }

    private fun persist(entry: LogEntry, synchronous: Boolean) {
        val task = Runnable { persistBlocking(entry) }
        if (synchronous) task.run() else ioExecutor.execute(task)
    }

    private fun persistBlocking(entry: LogEntry) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, LOG_FILE_NAME)
            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                // Se pasó del límite: se recorta a la mitad más reciente en vez
                // de seguir creciendo para siempre.
                val existing = file.readText().split(ENTRY_SEPARATOR)
                val trimmed = existing.takeLast((existing.size / 2).coerceAtLeast(1))
                file.writeText(trimmed.joinToString(ENTRY_SEPARATOR))
            }
            file.appendText(formatEntry(entry) + ENTRY_SEPARATOR)
        } catch (_: Exception) {
            // Si falla escribir el log del error... no hay a quién más avisarle.
            // Se ignora a propósito para no generar un loop de fallos.
        }
    }

    private fun loadPersistedEntries() {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, LOG_FILE_NAME)
            if (!file.exists()) return
            val blocks = file.readText().split(ENTRY_SEPARATOR).filter { it.isNotBlank() }
            val restored = blocks.takeLast(MAX_ENTRIES_IN_MEMORY).mapNotNull { parseEntry(it) }
            if (restored.isNotEmpty()) {
                _entries.update { (restored + it).takeLast(MAX_ENTRIES_IN_MEMORY) }
            }
        } catch (_: Exception) {
            // Historial corrupto o ilegible: se arranca en blanco, sin romper la app.
        }
    }

    /** Reconstruye un [LogEntry] desde el texto formateado que guarda [formatEntry] — best-effort, no crítico si falla. */
    private fun parseEntry(block: String): LogEntry? {
        val firstLine = block.lineSequence().firstOrNull() ?: return null
        val match = Regex("""^\[(.+?)] (ERROR|AVISO|INFO)/(.+?): (.*)$""").find(firstLine) ?: return null
        val (timeText, levelText, tag, message) = match.destructured
        val millis = try {
            timeFormatter.parse(timeText)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        val level = when (levelText) {
            "ERROR" -> Level.ERROR
            "AVISO" -> Level.WARN
            else -> Level.INFO
        }
        val rest = block.substringAfter("\n", missingDelimiterValue = "")
        return LogEntry(millis, level, tag, message, rest.ifBlank { null })
    }

    private fun appVersionName(): String {
        val ctx = appContext ?: return "?"
        return try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    private fun appVersionCode(): Long {
        val ctx = appContext ?: return 0L
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }
}
