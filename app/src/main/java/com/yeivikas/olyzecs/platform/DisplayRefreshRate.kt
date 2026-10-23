package com.yeivikas.olyzecs.platform

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.yeivikas.olyzecs.debug.AppLogger
import kotlin.math.roundToInt

/**
 * FASE B — reclasificación de paquete: este archivo vivía en
 * `engine/render/` pese a no tener ninguna relación con el pipeline de
 * render (nada de OpenGL/GLES/EGL/shaders acá — la auditoría lo detectó
 * como mal ubicado). Es una consulta de una CAPACIDAD DEL DISPOSITIVO
 * (tasa de refresco de la pantalla vía `WindowManager`/`Display`), no
 * motor: por eso pasa a `platform/`, un paquete nuevo y deliberadamente
 * chico para infraestructura Android de bajo nivel que no es UI, no es
 * Engine y no es persistencia — no encajaba bien en ninguno de los
 * paquetes existentes (`data/` es para persistencia de proyecto, no
 * para esto). Contenido sin cambios, solo el paquete.
 *
 * Consecuencia directa de este movimiento: `ui/ProjectsScreen.kt` sigue
 * llamando a `DisplayRefreshRate` directo desde la UI (para poblar el
 * selector de fps al crear un proyecto), pero eso ya no es un caso de
 * "UI accede a Engine" — es UI consultando una capacidad de la
 * plataforma, exactamente igual que llamar a `LocalContext.current` o
 * `Build.VERSION.SDK_INT` directo desde un Composable, algo aceptado en
 * el resto del proyecto y no correspondiente a la relación
 * arquitectónica que la Fase B busca corregir (UI → lógica de motor).
 *
 * "120 fps" en el selector del proyecto es un objetivo de PLAYBACK/EXPORT
 * (a qué velocidad avanza el playhead, y a qué fps se codifica el video
 * final) — no tiene nada que ver, por sí solo, con la tasa de refresco de
 * la PANTALLA del celular. Son dos cosas separadas:
 *
 *  1. El proyecto puede estar a 120fps aunque la pantalla sea de 60Hz: el
 *     video exportado sí va a tener 120 cuadros por segundo (eso siempre
 *     funciona, no depende del hardware — se decodifica en un archivo,
 *     nadie lo "ve" en vivo cuadro a cuadro).
 *  2. Pero el PREVIEW en vivo dentro del editor sí está limitado por la
 *     pantalla real: no existe forma de que un panel de 60Hz muestre más
 *     de 60 imágenes distintas por segundo, sin importar qué tan rápido
 *     internamente se calculen. Y ojo: la mayoría de Android NO le da a
 *     una app la tasa de refresco máxima del panel automáticamente — si
 *     nadie la pide explícitamente, el sistema deja la app corriendo al
 *     modo de refresco "por defecto" (típicamente 60Hz) aunque el
 *     hardware soporte 90/120/144Hz. [applyHighestRefreshRate] es ese
 *     pedido explícito.
 *
 * El máximo real varía por equipo: la inmensa mayoría de gama media son
 * 60Hz, gama alta reciente suele ser 90 o 120Hz, y solo un puñado de
 * equipos gamer llega a 144–165Hz. No existe un "máximo de fps en
 * mobile" universal — hay que preguntarle a la pantalla concreta que
 * tiene el usuario, por eso esto se resuelve en runtime y no como
 * constante fija.
 */
object DisplayRefreshRate {

    private const val TAG = "DisplayRefreshRate"

    private fun currentDisplay(context: Context): Display? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> context.display
        else -> @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }

    /**
     * Tasa de refresco máxima (en Hz, redondeada) que soporta la pantalla
     * de este dispositivo en cualquiera de sus modos, no solo el activo
     * en este momento. 60 como piso/fallback si no se puede consultar
     * (nunca debería pasar en un dispositivo real, pero mejor un valor
     * conservador que uno inventado).
     */
    fun maxSupportedRefreshRateHz(context: Context): Int {
        val display = currentDisplay(context) ?: return 60
        val fromModes = runCatching {
            display.supportedModes.maxOf { it.refreshRate }
        }.onFailure { AppLogger.w(TAG, "No se pudo consultar los modos de refresco soportados por la pantalla", it) }
            .getOrNull()
        val best = fromModes ?: display.refreshRate
        return best.roundToInt().coerceAtLeast(60)
    }

    /**
     * Construye la lista de fps para ofrecer al crear un proyecto:
     * siempre incluye los estándares de la industria (24/30/60, cine y
     * "video" clásico) y agrega cualquier tasa alta que la pantalla real
     * soporte (90/120/144/...), sin repetir ni pasarse del máximo real
     * del equipo — así el selector nunca ofrece "144fps" en un teléfono
     * de 60Hz, ni se queda corto en uno de 120Hz.
     */
    fun availableProjectFps(context: Context): List<Int> {
        val deviceMax = maxSupportedRefreshRateHz(context)
        val standard = listOf(24, 30, 60)
        val highRefreshCandidates = listOf(90, 120, 144, 165)
        val extra = highRefreshCandidates.filter { it <= deviceMax }
        return (standard + extra).distinct().sorted()
    }

    /**
     * Le pide a la ventana de la Activity que use el modo de refresco MÁS
     * ALTO disponible para la resolución actual, en vez de quedarse en el
     * default (normalmente 60Hz) del sistema. Sin esto, `RENDERMODE_
     * CONTINUOUSLY` de GLSurfaceView igual queda atado al ritmo de vsync
     * que el sistema le haya asignado a la app — que puede ser 60Hz aunque
     * el panel soporte más. Es un pedido (`preferredDisplayModeId`), no
     * una garantía: el sistema puede ignorarlo (ahorro de batería, modo de
     * bajo consumo, política del fabricante) — no hay API pública para
     * forzarlo, y no se recomienda intentar burlarla; si el fabricante
     * decide no dar más de 60Hz a esta app, no hay browser code que lo
     * cambie. Segura de llamar en cualquier dispositivo: en los que no
     * soportan más de 60Hz, o en versiones de Android sin esta API,
     * simplemente no hace nada.
     */
    fun applyHighestRefreshRate(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = currentDisplay(activity) ?: return
        val bestMode = runCatching {
            display.supportedModes.maxByOrNull { it.refreshRate }
        }.onFailure { AppLogger.w(TAG, "No se pudo determinar el mejor modo de refresco", it) }
            .getOrNull() ?: return
        runCatching {
            val window = activity.window
            val params = window.attributes
            params.preferredDisplayModeId = bestMode.modeId
            window.attributes = params
        }.onFailure { AppLogger.w(TAG, "No se pudo aplicar la tasa de refresco alta a la ventana", it) }
    }
}
