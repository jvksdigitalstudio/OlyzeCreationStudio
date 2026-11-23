package com.yeivikas.olyzecs.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * StrictMode SOLO para builds depurables (debug): detecta E/S de disco o red en el hilo principal y fugas de objetos, que en un teléfono
 * real se notan como tirones o ANR y que ninguna prueba de JVM ve.
 *
 * - **Nunca en `perf` ni `release`** (no son depurables): ahí se mide fluidez real y no debe haber coste ni ruido.
 * - Las violaciones se registran en [AppLogger] como WARN (la pantalla de registro de errores de la app las muestra: no hace falta Logcat
 *   ni cable USB), una sola vez por origen y con tope, para no ahogar los errores reales. Nunca matan la app (la política no incluye ninguna penalización fatal).
 * - El listener corre en un hilo propio: [AppLogger] persiste en segundo plano, así que registrar no provoca nuevas violaciones en el
 *   hilo principal (sin riesgo de bucle).
 * - Requiere API 28 para el listener; en 26-27 se usa solo Logcat.
 */
internal object StrictModeGuard {

    private const val TAG = "StrictMode"

    /** Tope de violaciones distintas registradas por sesión: más serían ruido, no información. */
    private const val MAX_REPORTS = 40

    private val reportedOrigins: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val reportCount = AtomicInteger(0)

    fun installIfDebuggable(context: Context) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installWithListener() else installLogcatOnly()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun installWithListener() {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "olyze-strictmode") }
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyListener(executor) { violation -> report(violation) }
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyListener(executor) { violation -> report(violation) }
                .build()
        )
    }

    private fun installLogcatOnly() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
    }

    private fun report(violation: Throwable) {
        // La primera línea de la traza que pertenece a la app identifica el ORIGEN; sin ella, es código del sistema o de terceros.
        val frame = violation.stackTrace.firstOrNull { it.className.startsWith("com.yeivikas.olyzecs") }
        val origin = "${violation.javaClass.simpleName}@${frame?.className}.${frame?.methodName}:${frame?.lineNumber}"
        if (!reportedOrigins.add(origin)) return
        if (reportCount.incrementAndGet() > MAX_REPORTS) return
        AppLogger.w(
            TAG,
            "Violación de StrictMode (solo debug): ${violation.javaClass.simpleName} en ${frame ?: "código del sistema o de terceros"}",
            violation
        )
    }
}
