package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.engine.audio.label
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Rect
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.yeivikas.olyzecs.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
//  FloatingWindow.kt — infraestructura de la ventana flotante de herramientas del editor
// ============================================================================
//  Cabecera, registro de ventanas minimizadas, menú de opciones, presets, insignia minimizada y zona de borrado.
//
//  ORIGEN: movido TAL CUAL desde `EditorScreen.kt` (Fase 3 del plan de ingeniería, corte 1; ADR-022). Sin cambios de lógica:
//  la única diferencia de código es que `FloatingWindowDeleteDropZone` pasó de `private` a `internal` porque `EditorScreen`, que
//  se queda en su archivo, la usa. El paquete SIGUE siendo `com.yeivikas.olyzecs.ui` (la carpeta `editor/` es solo organización):
//  así ningún símbolo cambia de nombre ni de ámbito y no hay que tocar imports en el resto del código.
// ============================================================================

/**
 * Ventana flotante genérica estilo "plugin de audio" — A PEDIDO DEL
 * USUARIO: reemplaza el panel angosto y vertical pegado al borde
 * izquierdo (ver KDoc viejo de [RecolorFloatingWindow], que describía
 * ese layout ya retirado) por una ventanita que vive DENTRO del canvas,
 * se puede arrastrar desde su cabecera a cualquier parte de la pantalla
 * y redimensionar desde su esquina inferior derecha — el mismo patrón
 * que un plugin de audio flotante (FL Studio, VST hosts, etc: cabecera
 * arrastrable arriba, manija de tamaño en la esquina). El usuario pidió
 * explícitamente referenciarse en ese patrón sin copiar ningún plugin en
 * particular.
 *
 * Pensada para reusarse en el resto de herramientas de edición más
 * adelante (por ahora solo "Recolor" la usa — ver [RecolorFloatingWindow]
 * — a pedido explícito: "por mientras después en el resto de opciones se
 * aplicará esto").
 *
 * Primera versión, a pedido explícito del usuario ("por ahora está bien,
 * luego lo pulimos"): solo arrastre por la cabecera + redimensionado por
 * la esquina inferior derecha, sin snapping a los bordes del canvas, sin
 * recordar posición/tamaño entre sesiones y sin límite superior de
 * tamaño — el único límite real es [minSize], para que no se pueda
 * encoger tanto que el contenido deje de ser usable.
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO — controles de cabecera estilo "plugin
 * de audio flotante" (referencia entregada: cabecera de un plugin de FL
 * Studio, "School Piano"), agregados del lado derecho de la cabecera,
 * ANTES del botón de cerrar, sin agrandar la fila de 38.dp de alto:
 * - [FloatingWindowPresetsControl]: "Presets" (o el nombre del preset
 *   activo, una vez elegido). Al tocarlo despliega — todavía vacía, a
 *   pedido explícito ("esto por ahora") — la lista de presets de ESTA
 *   ventana/módulo. El contenido real de esa lista llega después; lo que
 *   se arma ahora es el mecanismo completo (botón + estado de selección
 *   + panel desplegable) para que conectar presets reales más adelante
 *   sea solo llenar `presets`.
 * - Flechas `<` `>` ([ic_chevron_left]/[ic_chevron_right], mismos SVG
 *   premium que ya usa el resto de la app — no se creó ningún ícono
 *   nuevo): retroceden/avanzan un preset dentro de `presets`. Sin
 *   presets cargados quedan atenuadas y sin acción, en vez de romper o
 *   quedar “vivas” sin hacer nada.
 * - `-` ([ic_remove], mismo SVG que ya existe en la app): minimiza la
 *   ventana a solo su ícono ([FloatingWindowMinimizedBadge]) — un toque
 *   sobre ese ícono la restaura tal cual estaba, en el mismo lugar.
 */
private val FLOATING_WINDOW_HEADER_HEIGHT = 38.dp
private val FLOATING_WINDOW_MINIMIZED_BADGE_SIZE = 40.dp
// A PEDIDO EXPLÍCITO DEL USUARIO — imán al pie del header (referencia
// entregada con capturas: franja horizontal completa, pegada
// JUSTO AFUERA del header — no adentro de él —, de punta a punta de la
// pantalla). Reemplaza el intento anterior con los bordes laterales
// (retirado por completo a pedido, sin dejar rastro — ver conversación).
// Esta franja es mucho más confiable que aquella: es un chequeo de Y
// nada más (no de X), y el "techo" de esta zona es el CANVAS mismo —
// el Box que contiene las tres ventanas empieza justo debajo del
// header (con el `padding` del Scaffold ya restándole su alto), así
// que `offsetPx.y` cercano a 0 YA ES, sin necesitar medir nada más,
// "la ventana está pegada al pie del header". Al soltar ahí, se
// minimiza sola a su ícono — reusa el mismo mecanismo que ya existe
// para minimizar a mano (`isMinimized`).
private val FLOATING_WINDOW_TOP_MAGNET_MARGIN = 40.dp

// A PEDIDO EXPLÍCITO DEL USUARIO — "lanzar" la ventana hacia arriba
// desde CUALQUIER punto de la pantalla (no solo cuando ya está cerca
// del pie del header, como el imán de acá arriba) para que se minimice
// sola, igual que tirar una carta sobre un mazo: mantener pulsada la
// cabecera y soltarla con un golpe rápido hacia arriba la manda directo
// a minimizarse, sin tener que arrastrarla físicamente hasta la franja
// imán. Pedido explícito aparte, con el mismo mensaje: un arrastre
// LENTO — aunque cruce toda la pantalla de punta a punta — NO tiene que
// disparar esto, para poder seguir moviendo la ventana con calma sin
// que "se escape" sola hacia arriba. La distinción entre ambos casos es
// pura VELOCIDAD del gesto al soltar (ver `flungUpwardFast`, en el
// `pointerInput` de la cabecera, más abajo): un valor bien por encima
// de lo que un arrastre normal de reubicación alcanza, pero fácil de
// lograr con un golpe de muñeca deliberado.
//
// CALIBRACIÓN CORREGIDA DOS VECES (reportado por el usuario: "por más
// rápido que lanzo, la ventana nunca vuela sola"): 2600.dp/seg era
// irreal para un dedo humano, se bajó a 900.dp/seg — y el usuario
// reportó que SEGUÍA sin dispararse, todavía "muy veloz". La causa
// más probable no es solo el número: con un flick corto (el gesto
// pedido: tocar y lanzar casi sin recorrido) el `VelocityTracker`
// recibe pocas muestras antes del `up`, y su algoritmo de ajuste
// (mínimos cuadrados sobre el historial reciente) tiende a
// SUBESTIMAR la velocidad real cuando hay pocos puntos — así que la
// velocidad calculada queda por debajo de lo que el dedo hizo de
// verdad. Bajar el umbral de nuevo, bastante más, compensa esa
// subestimación y deja el gesto accesible con un flick corto y
// cómodo, sin exigir un golpe violento de muñeca.
private val FLOATING_WINDOW_FLING_MINIMIZE_VELOCITY_DP_PER_SEC = 450.dp
// Duración del "vuelo" animado de la ventana hasta el pie del header
// una vez detectado el lanzamiento — corto a propósito, para que se
// sienta como una respuesta inmediata al gesto y no como una animación
// decorativa de por sí.
private const val FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS = 180

// A PEDIDO EXPLÍCITO DEL USUARIO — ver el comentario grande sobre
// [FloatingWindowMinimizedRegistry.resolveFreeSlot] y sobre
// `settleMinimizedPosition`, unas líneas más abajo: duración del
// deslizamiento animado cuando un ícono recién minimizado tiene que
// correrse al lado de una ventana todavía abierta (o de otro ícono).
// Corto a propósito, igual que [FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS]:
// tiene que sentirse como una respuesta inmediata al gesto — "se corrió
// al lado" — no como una animación decorativa que demora el resultado.
private const val FLOATING_WINDOW_MINIMIZED_SETTLE_ANIMATION_MS = 160

// A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
// captura): al minimizar dos (o las tres) ventanas flotantes con el
// imán del pie del header, todas terminaban en el MISMO punto — el
// imán solo fuerza `offsetPx.y = 0f` (ver `magnetizeIfNearHeaderBottom`,
// más abajo) sin mirar nunca la coordenada X ni si ya había otro ícono
// minimizado ahí. Como las tres ventanas nacen escalonadas pero cerca
// unas de otras (ver `floatingWindowStaggerPx`, donde se instancian),
// arrastrarlas todas a la franja imán las dejaba prácticamente
// superpuestas — exactamente el ícono "doble" de la captura.
//
// Este registro es el mecanismo que lo corrige: cada [FloatingToolWindow]
// (Recolor / Color Básico / 3D Básico) se identifica acá con su propio
// `title` (únicos entre sí — ver los tres llamadores, más abajo) y, ni
// bien queda minimizada, registra su posición actual. Antes de asentarse
// en un punto — sea por el imán o por el botón "-" de la cabecera, o
// por arrastrarse ya minimizada encima de otra — cada ventana consulta
// [resolveFreeSlot] para saber si ese punto colisiona con algún OTRO
// ícono ya minimizado y, si colisiona, se corre a la derecha en línea
// hasta encontrar el primer hueco libre — nunca se apilan dos íconos en
// el mismo lugar.
private val FLOATING_WINDOW_MINIMIZED_BADGE_SPACING = 10.dp

// Visibilidad `internal` (antes `private`) — ÚNICO cambio de este ajuste:
// ZoomXFloatingWindow (ver ZoomXFloatingWindow.kt, archivo propio y
// separado a pedido explícito del usuario: "que no combine funciones,
// solo para este módulo") necesita reusar EXACTAMENTE este mismo
// registro compartido — el que ya evita que los íconos minimizados de
// Recolor/Color Básico/3D Básico se apilen entre sí — para que "Zoom X"
// participe del mismo mecanismo en vez de duplicar su propia versión
// paralela. `internal` (visible en todo el módulo `app`, no fuera de él)
// es la mínima ampliación real: nunca se vuelve parte de una API
// pública, y no cambia ni un bit de comportamiento para los llamadores
// que ya existían dentro de este archivo.
internal class FloatingWindowMinimizedRegistry {
    // `mutableStateMapOf` (no un `Map` a secas) a propósito: varias
    // ventanas leen este registro durante la composición de las OTRAS
    // (para calcular su propio hueco libre), así que un cambio acá tiene
    // que disparar recomposición en quien lo esté leyendo.
    private val positions = mutableStateMapOf<String, Offset>()

    // --- BUG REAL corregido (reportado con captura): [resolveFreeSlot]
    // solo esquivaba OTROS íconos ya minimizados (`positions`, acá
    // arriba) — nunca miraba si en ese punto había una ventana TODAVÍA
    // ABIERTA (cabecera + contenido, no solo su ícono). Resultado
    // exacto de la captura: al lanzar/minimizar una ventana hacia la
    // franja de arriba mientras otra ventana de efecto seguía abierta
    // ahí mismo, el ícono nuevo aterrizaba literalmente ENCIMA de esa
    // cabecera (tapando sus controles de Presets/‹/›), en vez de
    // correrse al costado — porque para [resolveFreeSlot] esa zona
    // estaba "libre": no había ningún OTRO ícono minimizado registrado
    // ahí, así que no tenía forma de saber que una ventana abierta
    // ocupaba ese lugar.
    //
    // La corrección agrega un segundo registro, en paralelo al de
    // íconos (`positions`): el rectángulo REAL (posición + tamaño
    // actuales) de cada [FloatingToolWindow] mientras está ABIERTA —
    // ver `registerOpenWindow`/`unregisterOpenWindow`, publicados desde
    // un `SideEffect` en [FloatingToolWindow] (se actualiza solo,
    // siguiendo la ventana en vivo mientras se arrastra o cambia de
    // alto/ancho, y desaparece apenas se minimiza o se cierra). Con eso,
    // [resolveFreeSlot] ahora también comprueba, antes que nada, si el
    // candidato cae ADENTRO del rectángulo de alguna ventana abierta —
    // y si es así, lo corre hasta pasar su borde derecho (más el mismo
    // margen que ya separa a dos íconos entre sí), quedando al LADO de
    // esa ventana en vez de tapada por/tapando a ella.
    private val openWindowRects = mutableStateMapOf<String, Rect>()

    /** Guarda/actualiza dónde está AHORA el ícono minimizado de [id]. */
    fun register(id: String, position: Offset) {
        positions[id] = position
    }

    /** Ventana restaurada (o cerrada): deja de contar para colisiones. */
    fun unregister(id: String) {
        positions.remove(id)
    }

    /**
     * Publica el rectángulo REAL (posición + tamaño, en px) de la
     * ventana [id] mientras está ABIERTA — ver el comentario grande de
     * más arriba. Se llama en cada composición desde un `SideEffect` en
     * [FloatingToolWindow], así que siempre refleja dónde está la
     * ventana AHORA MISMO, no una posición vieja.
     */
    fun registerOpenWindow(id: String, rect: Rect) {
        openWindowRects[id] = rect
    }

    /** Ventana minimizada (o cerrada): deja de contar para colisiones. */
    fun unregisterOpenWindow(id: String) {
        openWindowRects.remove(id)
    }

    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
    // capturas): todo lo de arriba (`resolveFreeSlot`) solo evita que un
    // ícono NUEVO aterrice encima de una ventana ya abierta — pero no
    // hace nada por un ícono que YA estaba minimizado y quieto en un
    // punto cuando OTRA ventana se abre (o crece) justo ENCIMA de él
    // después. Ese ícono quieto se quedaba tapado por la ventana nueva
    // sin moverse un pixel — exactamente el caso de la captura: abrir
    // "Color Básico" con el ícono de "3D Básico" ya minimizado ahí
    // cerca terminaba con la cabecera nueva dibujada literalmente
    // encima del ícono viejo, en vez de empujarlo al costado como un
    // acordeón.
    //
    // Esta función es lo que le permite a cada ícono, en cada
    // composición mientras sigue minimizado, preguntar "¿alguna ventana
    // ABIERTA me está tapando ahora mismo?" — leyendo el mismo
    // `openWindowRects` de arriba. [FloatingToolWindow] usa la
    // respuesta para disparar el mismo `settleMinimizedPosition()` que
    // ya usa al minimizarse/arrastrarse (mismo deslizamiento animado,
    // mismo `resolveFreeSlot` para encontrar el hueco libre más
    // cercano) — así el ícono se corre solo, en vivo, apenas la ventana
    // nueva empieza a taparlo, en vez de quedar tapado para siempre.
    fun overlapsAnyOpenWindow(id: String, position: Offset, badgeSizePx: Float): Boolean {
        val badgeRect = Rect(position, Size(badgeSizePx, badgeSizePx))
        return openWindowRects.filterKeys { it != id }.values.any { it.overlaps(badgeRect) }
    }

    // --- A PEDIDO EXPLÍCITO DEL USUARIO: arrastrar el ÍCONO YA
    // MINIMIZADO ("la bola flotante") hacia una zona de "Eliminar" en la
    // parte de abajo del canvas borra ESE efecto por completo — la
    // ventana y su configuración — no solo la oculta como ya hace el
    // botón "×" de la cabecera (ver `onClose`). A propósito, SOLO el
    // ícono minimizado activa esto: arrastrar la ventana ABIERTA desde
    // su cabecera por toda la pantalla (moverla, acomodarla, etc.)
    // nunca muestra ni activa nada de lo de acá abajo — el usuario lo
    // pidió explícito después de la primera pasada de este mismo
    // mecanismo.
    //
    // Estos tres campos son el canal compartido entre CADA badge (que
    // publica si se está arrastrando y dónde, vía [FloatingToolWindow])
    // y la zona de destino visual (que publica su rectángulo real y
    // consulta si algo la está sobrevolando ahora mismo, ver
    // [FloatingWindowDeleteDropZone] más abajo):
    //
    // - `isMinimizedBadgeDragging`: true mientras CUALQUIERA de los
    //   íconos minimizados está siendo arrastrado con el dedo todavía
    //   abajo — es lo que decide si la zona de "Eliminar" se dibuja o
    //   no (aparece recién al empezar a arrastrar, desaparece al
    //   soltar).
    // - `deleteZoneRect`: el rectángulo REAL (posición + tamaño, en px)
    //   de la zona de "Eliminar" mientras está dibujada — publicado por
    //   ella misma con `onGloballyPositioned`, consultado por cada
    //   badge en cada frame de su propio arrastre para saber si el
    //   dedo ya la superpone.
    // - `isDragOverDeleteZone`: true apenas el badge que se está
    //   arrastrando ahora mismo cae encima de `deleteZoneRect` — la
    //   zona lo usa para animar su propio "encendido" (escala + brillo)
    //   y disparar la vibración de confirmación exactamente una vez al
    //   entrar, no en cada frame que pasa por encima.
    var isMinimizedBadgeDragging by mutableStateOf(false)
    var isDragOverDeleteZone by mutableStateOf(false)
    var deleteZoneRect: Rect? by mutableStateOf(null)

    /**
     * Punto libre más cercano a [desired] que no colisiona ni con
     * ningún OTRO ícono ya registrado ni con el rectángulo de ninguna
     * ventana TODAVÍA ABIERTA (se ignora la propia entrada de [id], si
     * existe en cualquiera de los dos registros — una ventana nunca
     * colisiona contra sí misma). Corre el candidato en línea
     * horizontal, de a un "paso" por vez (diámetro del ícono + margen
     * contra otro ícono; borde derecho + margen contra una ventana
     * abierta), hasta no superponerse con nadie — así, tanto si varios
     * íconos terminan queriendo el mismo punto como si ese punto cae
     * arriba de una ventana abierta, el resultado es quedar en fila
     * prolija al lado, nunca apilado ni tapando/tapado.
     */
    fun resolveFreeSlot(
        id: String,
        desired: Offset,
        badgeSizePx: Float,
        spacingPx: Float
    ): Offset {
        val minDistance = badgeSizePx + spacingPx
        val otherBadges = positions.filterKeys { it != id }.values
        val otherOpenWindows = openWindowRects.filterKeys { it != id }.values
        if (otherBadges.isEmpty() && otherOpenWindows.isEmpty()) return desired
        var candidate = desired
        // Techo de intentos = cantidad total de "obstáculos" (íconos +
        // ventanas abiertas) + 1: correrse para esquivar a uno puede
        // acercar el candidato a un tercero que antes estaba lejos, así
        // que hace falta poder reintentar más de una vez — pero nunca
        // más veces de las que hay obstáculos con los que podría
        // chocar, para no colgarse en un caso raro.
        val maxAttempts = otherBadges.size + otherOpenWindows.size + 1
        var attempts = 0
        while (attempts <= maxAttempts) {
            val badgeRect = Rect(candidate, Size(badgeSizePx, badgeSizePx))
            val windowCollision = otherOpenWindows.firstOrNull { it.overlaps(badgeRect) }
            if (windowCollision != null) {
                // Al lado de la ventana abierta, no encima: se corre
                // hasta pasar su borde derecho real.
                candidate = Offset(windowCollision.right + spacingPx, candidate.y)
                attempts += 1
                continue
            }
            val badgeCollision = otherBadges.firstOrNull { (candidate - it).getDistance() < minDistance }
            if (badgeCollision != null) {
                candidate = Offset(badgeCollision.x + minDistance, candidate.y)
                attempts += 1
                continue
            }
            return candidate
        }
        return candidate
    }
}

// --- Auto-tamaño al primer abrir (A PEDIDO EXPLÍCITO DEL USUARIO):
// antes `windowSize` arrancaba siempre en el `initialSize` fijo que le
// pasaba cada llamador (RECOLOR_FLOATING_WINDOW_DEFAULT_SIZE,
// COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
// BASICO_FLOATING_WINDOW_DEFAULT_SIZE, más abajo en este archivo) — un
// número adivinado a mano que no tiene ninguna relación real con cuánto
// contenido tiene CADA ventana (cabecera + título + el último control de
// abajo). Resultado real reportado por el usuario, con capturas: la
// primera vez que se abre la ventana queda "cortada" (el último ajuste,
// p.ej. "Tono", no se ve completo) y la manija de redimensionar (esquina
// inferior derecha) termina dibujada ENCIMA de ese último control, en vez
// de debajo de él.
//
// La solución: la primera vez que la ventana se compone, no se le
// impone ningún alto — se la deja crecer a su alto NATURAL (envolviendo
// cabecera + contenido) hasta un techo razonable (`maxAutoHeight`, ver
// más abajo — el área REAL disponible, para que una ventana con MUCHO
// contenido no tape todo el canvas), se mide ese
// alto real con `onGloballyPositioned` y recién ahí se "congela" como el
// `windowSize` de verdad — sumándole
// [FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE] de aire extra abajo para que
// la manija de redimensionar quede SIEMPRE por debajo del último
// control, nunca encima. De ahí en adelante la ventana se comporta
// exactamente como antes: tamaño fijo, redimensionable a mano desde la
// esquina — eso sigue siendo 100% opcional para el usuario, tal como
// pidió explícitamente ("ya es opcional").
//
// BUG REAL corregido (reportado con captura: "no calcula bien el
// ancho, en nombres largos se ve cortante"): el ANCHO seguía siendo el
// `initialSize.width` fijo de cada llamador, sin relación real con
// cuánto necesita la cabecera — y la cabecera no es solo el título:
// también carga el grupo de controles "Presets ‹ › ─ - ×" (ver más
// abajo, [FloatingWindowHeaderRowContent]). Con `initialSize.width` en
// 260.dp, ese grupo de controles por sí solo ya consume casi todo ese
// ancho, así que a la etiqueta de la izquierda (`weight(1f)`) le queda
// casi nada de lugar real — el título terminaba truncado con "..."
// aunque el texto en sí no fuera particularmente largo ("Color Básico"
// → "Color Bá..."). Ya NO se deja esto en manos de la elipsis: el ancho
// se mide igual de en serio que el alto — [FloatingWindowHeaderRowContent]
// se compone una segunda vez, "fantasma" (nunca se dibuja, mide 0×0 en
// pantalla — ver el `Layout` de acá abajo), con el título SIN
// `weight()` para que ocupe su ancho NATURAL sin recortar, y ESE ancho
// real de la cabecera completa (ícono + título + controles) es lo que
// entra a competir con `initialSize.width` para decidir el ancho final:
// gana el que sea más grande, hasta `maxAutoWidth` (ver más abajo — el
// área REAL disponible) como techo (mismo criterio que ya usa el alto,
// para que un preset con un nombre absurdamente largo no dispare la
// ventana fuera de la pantalla). La elipsis del título sigue ahí como
// red de seguridad para ese caso límite — no como el mecanismo principal.
// NOTA: la fase "fill hasta abajo" (alto = `maxAutoHeight` siempre) que
// pasó por acá se revirtió — confirmado por el usuario con captura: daba
// un panel gigante casi vacío, peor que el bug original. Se volvió al
// criterio de wrap-content de siempre, así que
// [FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE] vuelve a hacer falta, tal
// cual estaba.
private val FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE = 20.dp

/**
 * Contenido de la fila de cabecera — ícono de arrastre, título y grupo
 * de controles "Presets ‹ › ─ - ×" — factorizado en una sola función
 * para que [FloatingToolWindow] lo componga DOS veces con el mismo
 * código exacto: una vez VISIBLE (título con `weight(1f)`, recortado con
 * elipsis si hace falta) y otra "fantasma", nunca dibujada, solo para
 * medir (título SIN `weight()`, con su ancho natural completo) — ver el
 * comentario grande sobre `autoWidth` en [FloatingToolWindow] para el
 * porqué. Mantener esto en un solo lugar evita que las dos copias se
 * desincronicen si el header cambia más adelante (un ícono nuevo, un
 * botón de menos, etc.): hay un solo lugar para tocar, no dos.
 */
@Composable
private fun RowScope.FloatingWindowHeaderRowContent(
    title: String,
    titleIcon: @Composable (Color) -> Unit,
    titleWeighted: Boolean,
    presets: List<String>,
    selectedPresetIndex: Int?,
    presetsMenuExpanded: Boolean,
    onPresetsTogglePress: () -> Unit,
    onPresetsDismiss: () -> Unit,
    onPresetSelect: (Int) -> Unit,
    onPresetPrev: () -> Unit,
    onPresetNext: () -> Unit,
    onMinimize: () -> Unit,
    onClose: (() -> Unit)?,
    // A PEDIDO DEL USUARIO: botón de menú de la ventana ("los tres
    // puntos" — acá el glifo de tres cuadrados, ver [ic_menu_option]),
    // pegado del lado IZQUIERDO de [titleIcon] — el primer elemento de
    // toda la cabecera. Abre [FloatingWindowOptionsMenuButton] (ver más
    // abajo): una barra vertical angosta, propia de ESTA ventana, todavía vacía
    // a pedido explícito ("por el momento solo ventana vacía") — el
    // lugar donde van a vivir las opciones de cada módulo más adelante.
    menuExpanded: Boolean,
    onMenuTogglePress: () -> Unit,
    onMenuDismiss: () -> Unit
) {
    FloatingWindowOptionsMenuButton(
        expanded = menuExpanded,
        onTogglePress = onMenuTogglePress,
        onDismiss = onMenuDismiss
    )
    Spacer(modifier = Modifier.width(6.dp))
    titleIcon(Color.White.copy(alpha = 0.7f))
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        title,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (titleWeighted) Modifier.weight(1f) else Modifier.wrapContentWidth()
    )

    Spacer(modifier = Modifier.width(6.dp))

    FloatingWindowPresetsControl(
        presets = presets,
        selectedIndex = selectedPresetIndex,
        expanded = presetsMenuExpanded,
        onTogglePress = onPresetsTogglePress,
        onDismiss = onPresetsDismiss,
        onSelect = onPresetSelect
    )

    FloatingWindowHeaderIconButton(
        iconRes = R.drawable.ic_chevron_left,
        contentDescription = "Preset anterior",
        enabled = presets.isNotEmpty(),
        onClick = onPresetPrev
    )
    FloatingWindowHeaderIconButton(
        iconRes = R.drawable.ic_chevron_right,
        contentDescription = "Preset siguiente",
        enabled = presets.isNotEmpty(),
        onClick = onPresetNext
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(16.dp)
            .background(Color.White.copy(alpha = 0.14f))
    )

    FloatingWindowHeaderIconButton(
        iconRes = R.drawable.ic_remove,
        contentDescription = "Minimizar",
        onClick = onMinimize
    )

    if (onClose != null) {
        FloatingWindowHeaderIconButton(
            iconRes = R.drawable.ic_close,
            contentDescription = "Cerrar",
            onClick = onClose
        )
    }
}

@Composable
// Visibilidad `internal` (antes `private`) — mismo motivo y mismo
// criterio que el cambio en [FloatingWindowMinimizedRegistry] de más
// arriba: ZoomXFloatingWindow (archivo propio, ver su comentario grande)
// reusa esta MISMA cabecera arrastrable/redimensionable — así "Zoom X"
// se ve y se comporta IDÉNTICO a Recolor/Color Básico/3D Básico (mismo
// drag, mismo resize, mismo imán de minimizado, mismo z-order al tocar)
// sin duplicar un solo píxel de esa lógica en el archivo nuevo.
internal fun FloatingToolWindow(
    title: String,
    // Identidad de ESTA ventana ante el registro compartido de minimizadas ([FloatingWindowMinimizedRegistry]). Por defecto es
    // el título (cada módulo de imagen tiene uno único); un módulo que puede tener VARIAS ventanas con el mismo título (p. ej.
    // el Reverb de cada carril) pasa una identidad propia para que no se confundan entre sí.
    windowId: String = title,
    // Ícono de identidad de ESTA ventana, mostrado en la esquina superior
    // izquierda de la cabecera — el MISMO glifo que ya usa esta misma
    // herramienta en su menú de selección (p.ej. [ColorMenuBasicoIcon]
    // para "Color Básico", [ColorMenuRecolorIcon] para "Recolor",
    // [Menu3DBasicCubeIcon] para "3D Básico" — ver [EditImageColorMenu]/
    // [EditImage3DMenu]). A PEDIDO DEL USUARIO — BUG REAL corregido: acá
    // había un ícono de "mover en los dos ejes" (`ic_move_both_axes`)
    // FIJO para las tres ventanas por igual, sin relación ninguna con
    // cuál era cuál — el usuario lo notó comparando esta cabecera contra
    // el menú de selección, donde cada opción SÍ tiene su propio glifo
    // distinto. Sin valor por defecto a propósito: cada llamador
    // (Recolor / Color Básico / 3D Básico) tiene que pasar el suyo de
    // forma explícita — así, si mañana se agrega una cuarta ventana y se
    // olvida este parámetro, no compila en silencio con el ícono
    // genérico de otra por error.
    titleIcon: @Composable (Color) -> Unit,
    initialOffset: Offset,
    initialSize: DpSize,
    minSize: DpSize = DpSize(220.dp, 260.dp),
    onClose: (() -> Unit)? = null,
    // A PEDIDO EXPLÍCITO DEL USUARIO: distinto de `onClose` (que solo
    // OCULTA la ventana, dejando el efecto ya aplicado tal cual quedó —
    // ver el comentario de `onClose` en cada llamador). Esto BORRA el
    // efecto entero — vuelve la capa a como estaba antes de tocar esta
    // herramienta y recién DESPUÉS cierra la ventana. Se dispara desde
    // un solo lugar: soltar el ícono ya minimizado ("la bola flotante")
    // encima de la zona de "Eliminar" que aparece abajo del canvas
    // mientras ese ícono se arrastra (ver [FloatingWindowDeleteDropZone]
    // y el comentario grande sobre `isMinimizedBadgeDragging` en
    // [FloatingWindowMinimizedRegistry]) — nunca desde arrastrar la
    // ventana ABIERTA, a pedido explícito del usuario. `null` (default)
    // en cualquier llamador que todavía no tenga una forma real de
    // "deshacer" su efecto — el gesto de arrastrar-y-soltar sigue
    // funcionando igual, solo que sin nada que restablecer.
    onDeleteEffect: (() -> Unit)? = null,
    // Presets de ESTE módulo: los nombres que ofrece el panel desplegable y la navegación ‹ ›. Vacía en los módulos que
    // todavía no tienen presets (Recolor / Color Básico / 3D Básico / Zoom X): el módulo que sí los tiene es DUEÑO del
    // preset elegido ([selectedPresetIndex]; `null` = "a medida", se muestra "Presets") y recibe el cambio en
    // [onPresetSelected] — la ventana no guarda la selección, para que nunca muestre un preset que ya no coincide con los valores.
    presets: List<String> = emptyList(),
    selectedPresetIndex: Int? = null,
    onPresetSelected: (Int) -> Unit = {},
    // --- BUG REAL corregido (reportado con captura): el popup de
    // "Presets" (ver [FloatingWindowPresetsControl]) es un `Popup` real
    // de Android — crea su PROPIA capa de ventana del sistema, aparte de
    // la jerarquía normal de Compose, así que SIEMPRE se dibuja por
    // encima de todo lo demás en la Activity, sin importar ningún
    // `zIndex` de Compose. Eso en sí mismo es correcto (un menú
    // desplegable tiene que verse completo, no tapado) — el problema
    // real era otro: acá nunca existió ningún concepto de "esta ventana
    // está delante/detrás de las otras dos" — Recolor, Color Básico y
    // 3D Básico compartían el mismo `zIndex(20f)` fijo (ver el lugar
    // donde se instancian, en el Box de más arriba), así que el orden
    // entre ellas tres dependía solo del orden en que están ESCRITAS en
    // el código, no de cuál tocó el usuario último. Resultado: tocar
    // "Presets" en una ventana que el usuario considera "de atrás" hacía
    // que su popup tapara a una ventana "de adelante" — se veía roto.
    //
    // La corrección real es traer la ventana al frente de las otras dos
    // (subir su `zIndex` de Compose por encima de las demás) apenas el
    // usuario la toca — así, para cuando el popup de "Presets" se abre,
    // esa ventana YA es, sin ambigüedad, la de adelante, y que su propio
    // popup tape a las otras dos deja de ser un bug: es lo esperado,
    // igual que cualquier host de ventanas flotantes real (FL Studio,
    // Photoshop). `onInteracted` es el aviso hacia el llamador (ver el
    // Box exterior donde se instancian las tres) de "tocaron esta
    // ventana, subila". Se dispara con el PRIMER toque de cada gesto,
    // en TODA la ventana (no solo en la cabecera) — ver el
    // `pointerInput` de acá abajo — sin consumir el evento, así que
    // sliders/botones/arrastre siguen funcionando exactamente igual;
    // esto solo OBSERVA que hubo un toque, nunca lo intercepta.
    onInteracted: () -> Unit = {},
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido: ver el
    // comentario grande sobre [FloatingWindowMinimizedRegistry], justo
    // arriba de [FLOATING_WINDOW_TOP_MAGNET_MARGIN]. Un solo registro,
    // creado UNA vez donde se instancian las tres ventanas (Recolor /
    // Color Básico / 3D Básico — ver ese `remember` en el Box exterior)
    // y compartido entre ellas acá, es lo que le permite a cada una
    // saber dónde están los OTROS íconos minimizados para no
    // superponerse. Sin valor por defecto a propósito, mismo criterio
    // que [titleIcon]: si mañana se agrega una cuarta ventana y se
    // olvida pasar el registro compartido, no compila en silencio con
    // una que solo se ve a sí misma.
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Tamaño REAL (en px) del área donde viven las tres ventanas
    // flotantes — medido con `onSizeChanged` en el Box exterior donde se
    // instancian (ver `floatingWindowAreaSizePx`, junto a ese Box) y
    // reenviado sin cambios por cada envoltorio (Recolor / Color Básico /
    // 3D Básico). Es el límite real contra el que se recortan los
    // arrastres hacia los costados y hacia abajo, más abajo en esta
    // función — ver `clampToSideLimits`/`clampToBottomLimit`.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido dos veces
    // seguidas (reportado la primera vez: "ahora ni eso ni nada, no
    // siento nada"; y de nuevo después de la primera corrección: "esa
    // vibración... no funciona, no siento nada"). La causa de fondo, en
    // las dos vueltas, es la MISMA: pedir el pulso más débil que la API
    // de Android permite.
    //
    // Primera vuelta: `LocalHapticFeedback` + `HapticFeedbackType.LongPress`
    // — pensada para acuses de clic MUY sutiles de la propia UI de
    // Compose (selección de texto, por ejemplo), casi imperceptible en
    // la mayoría de los equipos.
    //
    // Segunda vuelta: ya se había cambiado a `Vibrator`/`VibrationEffect`
    // de verdad, pero con `DEFAULT_AMPLITUDE` (le deja al sistema
    // decidir qué tan fuerte vibrar — en muchos equipos, sobre todo con
    // el volumen de "vibración táctil" bajo en Ajustes, eso termina
    // siendo casi nada) y una duración de apenas 35ms — más corta que
    // el tiempo de arranque de muchos motores lineales modernos (~20-30ms),
    // así que en la práctica el motor ni llegaba a moverse antes de que
    // Android le mandara "parar".
    //
    // La corrección: `AMPLITUDE_MAX = amplitud MÁXIMA posible pedida
    // explícita (no "la que decida el sistema"), duraciones más largas
    // (50ms para el pulso de "entraste a la zona", un patrón de DOS
    // golpes de 40ms para el de "confirmado, se borró" — inconfundible
    // incluso si el primero no se llegó a sentir bien) — ver
    // `triggerDeleteZoneHoverPulse`/`triggerDeleteZoneConfirmPulse` más
    // abajo. `minSdk` de este proyecto es 26 (ver app/build.gradle.kts)
    // — exactamente la versión donde se agregó `VibrationEffect` — así
    // que no hace falta ningún camino alternativo para versiones más
    // viejas de Android.
    val hapticContext = LocalContext.current
    // A PEDIDO EXPLÍCITO DEL USUARIO — TERCERA vuelta sobre este mismo
    // bug ("la vibración no funciona"): las dos vueltas anteriores (ver
    // el comentario grande de arriba) atacaron la AMPLITUD/DURACIÓN del
    // pulso, pero había un problema de fondo que ninguna de las dos
    // podía haber revelado nunca: `getVibrator()`/`vibrate()` estaban
    // envueltos en `catch (_: Exception) { }` — CUALQUIER fallo real
    // (equipo sin motor de vibración, `Vibrator` nulo, permiso
    // bloqueado por batería/Do-Not-Disturb en algún fabricante, etc.)
    // se tragaba en silencio, sin dejar ningún rastro en ningún lado.
    // Es imposible saber si "no funciona" es un bug de este código o
    // una vibración real que el equipo de prueba no puede sentir si el
    // fallo nunca queda escrito en ninguna parte.
    //
    // La corrección: seguir sin romper el flujo si algo falla (una
    // vibración fallida no debe tirar abajo un arrastre en curso), pero
    // ahora CADA fallo queda anotado en [AppLogger] — visible en la
    // pantalla de "Registro de errores" de la propia app, sin depender
    // de un cable USB ni de Logcat. También se registra, como aviso
    // (no error), el caso de equipo SIN motor de vibración en absoluto
    // (`hasVibrator() == false`) — la causa más común y más difícil de
    // sospechar de "no siento nada" en emuladores y en algunas tablets.
    fun getVibrator(): android.os.Vibrator? = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val manager = hapticContext.getSystemService(android.os.VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            hapticContext.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    } catch (e: Exception) {
        AppLogger.e("DeleteZoneHaptics", "No se pudo obtener el Vibrator del sistema", e)
        null
    }
    // Pulso corto, al ENTRAR a la zona de "Eliminar" con el ícono
    // arrastrado — un "clic" táctil de aviso, no de confirmación.
    fun triggerDeleteZoneHoverPulse() {
        try {
            val vibrator = getVibrator()
            if (vibrator == null || !vibrator.hasVibrator()) {
                AppLogger.w("DeleteZoneHaptics", "Pulso de entrada omitido: este equipo no reporta motor de vibración")
                return
            }
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(50L, 255))
        } catch (e: Exception) {
            AppLogger.e("DeleteZoneHaptics", "Fallo al disparar el pulso de entrada a la zona de eliminar", e)
        }
    }
    // Pulso de DOS golpes, al SOLTAR y confirmar que el efecto se borró
    // de verdad — a propósito más largo/distinto al de arriba, para que
    // se sienta como un cierre, no como un aviso más.
    fun triggerDeleteZoneConfirmPulse() {
        try {
            val vibrator = getVibrator()
            if (vibrator == null || !vibrator.hasVibrator()) {
                AppLogger.w("DeleteZoneHaptics", "Pulso de confirmación omitido: este equipo no reporta motor de vibración")
                return
            }
            vibrator.vibrate(
                android.os.VibrationEffect.createWaveform(
                    longArrayOf(0L, 40L, 30L, 60L),
                    intArrayOf(0, 255, 0, 255),
                    -1
                )
            )
        } catch (e: Exception) {
            AppLogger.e("DeleteZoneHaptics", "Fallo al disparar el pulso de confirmación de borrado", e)
        }
    }
    var offsetPx by remember { mutableStateOf(initialOffset) }
    // BUG REAL corregido (a pedido explícito del usuario, con captura):
    // ventana e ícono minimizado se "perdían" arrastrando hacia los
    // COSTADOS o hacia ABAJO — ningún lado tenía techo/piso ahí, así que
    // `offsetPx` podía crecer sin límite hasta quedar fuera del área
    // visible. A PEDIDO EXPLÍCITO: esto NO toca el límite de ARRIBA —
    // ese ya tiene su propia lógica (`coerceAtLeast(0f)` + el imán de
    // [FLOATING_WINDOW_TOP_MAGNET_MARGIN], más abajo) y se deja
    // exactamente como está. Estas dos funciones solo agregan techo por
    // la derecha (`clampToSideLimits`) y techo por abajo
    // (`clampToBottomLimit`), contra el tamaño REAL del área medida en
    // `containerSizePx` (ver su declaración junto a donde se instancian
    // las tres ventanas) — si todavía no se midió (0×0, primer frame),
    // no recortan nada para no forzar la ventana a (0,0) por error.
    fun clampToSideLimits(x: Float, itemWidthPx: Float): Float {
        if (containerSizePx.width <= 0) return x
        val maxX = (containerSizePx.width - itemWidthPx).coerceAtLeast(0f)
        return x.coerceIn(0f, maxX)
    }
    fun clampToBottomLimit(y: Float, itemHeightPx: Float): Float {
        if (containerSizePx.height <= 0) return y
        val maxY = (containerSizePx.height - itemHeightPx).coerceAtLeast(0f)
        return y.coerceAtMost(maxY)
    }
    // Asienta esta ventana en un punto que no colisione con ningún otro
    // ícono ya minimizado (ver [FloatingWindowMinimizedRegistry]) y deja
    // esa posición final registrada para que, a su vez, las PRÓXIMAS
    // ventanas que se minimicen esquiven a esta. Un solo punto de
    // entrada para los tres caminos que llevan a "quedar minimizada":
    // el imán del pie del header, el botón "-" de la cabecera, y
    // arrastrar el ícono ya minimizado encima de otro.
    //
    // BUG VISUAL corregido (reportado con captura: "se ve feo, como un
    // bug, aunque no lo sea" — el ícono llegaba pegado al lado de una
    // ventana TODAVÍA ABIERTA, ver [FloatingWindowMinimizedRegistry]
    // más arriba, y en el instante exacto de asentarse "TELETRANSPORTABA"
    // de golpe al hueco libre, sin ningún paso intermedio: un salto seco
    // de un punto a otro en el mismo frame). La corrección de fondo
    // (esquivar la ventana abierta) ya era la correcta — lo que se veía
    // mal era la FALTA de transición: un salto instantáneo siempre lee
    // como un glitch, sin importar que el destino sea el correcto. Acá
    // NO se cambia el destino calculado por [resolveFreeSlot] — se
    // anima el camino hacia él, igual que ya hace el "lanzamiento" hacia
    // arriba (ver [FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS], unas
    // líneas más arriba) para que, en vez de "aparecer" al lado de la
    // ventana abierta, se lo vea DESLIZARSE hasta ahí — una corrección
    // que se lee como intencional, no como un error.
    val settleMinimizedPosition: () -> Unit = {
        val badgeSizePx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SIZE.toPx() }
        val spacingPx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SPACING.toPx() }
        val resolved = minimizedRegistry.resolveFreeSlot(windowId, offsetPx, badgeSizePx, spacingPx)
        // BUG REAL corregido: al esquivar a otro ícono, [resolveFreeSlot]
        // corre el candidato en línea hacia la DERECHA (ver su código) y
        // puede terminar más allá del borde derecho real de la pantalla
        // si ya hay varios íconos en fila — mismo síntoma de "se pierde"
        // que el arrastre a mano. Solo se recorta el lado (X); el eje Y
        // no lo toca [resolveFreeSlot] nunca, así que no hace falta
        // tocarlo acá tampoco.
        val clamped = resolved.copy(x = clampToSideLimits(resolved.x, badgeSizePx))
        // Se registra el destino FINAL desde ya — antes de que termine
        // de deslizarse hasta ahí — para que cualquier OTRA ventana que se
        // minimice mientras esta animación todavía está en vuelo ya la
        // esquive a ELLA en su punto de llegada, no en el punto de
        // partida (que un instante después iba a quedar libre igual).
        minimizedRegistry.register(windowId, clamped)
        if (clamped == offsetPx) {
            // Nada que esquivar: no hace falta animar un desplazamiento
            // de cero píxeles.
            offsetPx = clamped
        } else {
            val startOffset = offsetPx
            coroutineScope.launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FLOATING_WINDOW_MINIMIZED_SETTLE_ANIMATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) { fraction, _ ->
                    offsetPx = Offset(
                        lerp(startOffset.x, clamped.x, fraction),
                        lerp(startOffset.y, clamped.y, fraction)
                    )
                }
                offsetPx = clamped
            }
        }
    }
    // Red de seguridad: si la ventana se CIERRA del todo (× o
    // `onClose`) mientras estaba minimizada, esta instancia de
    // [FloatingToolWindow] se descompone entera y ningún `onClick`/
    // `onMinimize` de acá arriba llega a correr — sin esto, su entrada
    // quedaría viva para siempre en [minimizedRegistry], "reservando" un
    // hueco que ya no tiene ícono real ahí.
    DisposableEffect(Unit) {
        onDispose {
            minimizedRegistry.unregister(windowId)
            // Misma red de seguridad que ya existía para el ícono
            // minimizado (línea de arriba), ahora también para el
            // rectángulo de "ventana abierta" (ver [SideEffect] de más
            // arriba y el comentario grande sobre
            // [FloatingWindowMinimizedRegistry]): si la ventana se
            // CIERRA del todo mientras seguía abierta (sin pasar antes
            // por minimizar), ningún `SideEffect` vuelve a correr para
            // limpiarlo — sin esto, quedaría "reservando" ese lugar
            // para siempre contra ventanas que se minimicen después.
            minimizedRegistry.unregisterOpenWindow(windowId)
        }
    }
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido: la primera
    // versión de este auto-tamaño medía el alto UNA sola vez, en la
    // primerísima composición, y lo congelaba ahí para siempre. Eso se
    // rompía con contenido que cambia de alto después de esa primera
    // composición — por ejemplo cualquier [LabeledSlider] al abrir su campo de edición
    // manual, que agrega un `OutlinedTextField` más alto que la fila
    // normal. En cualquiera de esos casos, la ventana se congelaba en el
    // alto del contenido CHICO, y cuando el contenido real (más alto)
    // aparecía un instante después ya no había alto reservado — quedaba
    // cortado, exactamente el mismo bug que se pidió corregir.
    //
    // Ahora `windowSize` no se congela por una sola medición: sigue
    // recalculándose contra el alto NATURAL real del contenido en cada
    // composición mientras `userResized` sea `false`. Recién cuando el
    // usuario arrastra la manija de la esquina por primera vez
    // (`userResized = true`, ver la manija más abajo) el tamaño pasa a
    // ser fijo de verdad y queda 100% bajo su control, tal como se pidió
    // ("ya es opcional").
    var windowSize by remember { mutableStateOf<DpSize?>(null) }
    var userResized by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    // --- BUG REAL corregido (reportado con captura: "la ventana debería
    // llegar hasta abajo... no toca, no llega abajo, no aprovecha el
    // tamaño de la pantalla"). El techo del auto-tamaño (`maxAutoHeight`/
    // `maxAutoWidth`) se calculaba contra `configuration.screenHeightDp`/
    // `screenWidthDp` — el tamaño FÍSICO TOTAL del dispositivo, sin
    // descontar el header ni el resto de la interfaz — en vez de contra
    // `containerSizePx`, que es el área REAL donde viven estas tres
    // ventanas (ya medida con `onSizeChanged` en el Box exterior, ver su
    // declaración junto a `floatingWindowAreaSizePx`, y pasada acá mismo
    // como parámetro). Esa base de cálculo estaba mal: es la causa de
    // que la ventana nunca pudiera aprovechar el espacio real disponible
    // ni llegar a tocar el borde inferior verdadero del área de trabajo,
    // aunque hubiera lugar de sobra para eso — se quedaba corta contra un
    // porcentaje de una medida que no es la que importa acá.
    //
    // La corrección usa `containerSizePx` (ya en píxeles reales) como
    // base — con el mismo fallback a la medida de pantalla completa
    // SOLO para el primerísimo frame, antes de que `onSizeChanged` mida
    // nada todavía (`containerSizePx` en 0×0) — y sube el techo a
    // prácticamente el área completa (no ya un 72%/92% arbitrario): la
    // ventana puede ahora crecer hasta tocar el borde real del área de
    // trabajo cuando el contenido lo necesita, sin quedar nunca más
    // corta por una cuenta hecha contra el número equivocado.
    val containerHeightDp = with(density) { containerSizePx.height.toDp() }
    val containerWidthDp = with(density) { containerSizePx.width.toDp() }
    // SEGUNDA CORRECCIÓN REAL sobre el mismo bug (el intento anterior, en
    // el comentario de arriba, cambió la BASE del cálculo pero seguía sin
    // restar la posición vertical de la propia ventana): `containerHeightDp`
    // es el alto TOTAL del área de trabajo medida desde y=0, pero la
    // ventana no arranca en y=0 — arranca en `offsetPx.y` (su posición
    // actual, ya sea la inicial escalonada o donde el usuario la arrastró).
    // Usar el alto total como techo, sin descontar ese offset, es
    // exactamente la cuenta mal hecha que el usuario sigue viendo: le
    // permite a la ventana "pedir" hasta el 100% del área aunque ya esté
    // parada 300dp más abajo del techo real, así que un panel que sí
    // necesita ese alto se queda corto contra el borde inferior real en
    // esa misma medida (300dp de hueco) en vez de tocarlo.
    //
    // La cuenta correcta es el espacio que queda DEBAJO de donde la
    // ventana ya está: `containerHeightDp - offsetY`. Se recalcula en
    // cada composición (no es un `remember`), así que sigue la posición
    // en vivo mientras se arrastra la ventana verticalmente.
    val offsetYDp = with(density) { offsetPx.y.toDp() }
    val maxAutoHeight = (if (containerSizePx.height > 0) (containerHeightDp - offsetYDp) else (configuration.screenHeightDp.dp - offsetYDp))
        .coerceAtLeast(minSize.height)
    val maxAutoWidth = (if (containerSizePx.width > 0) containerWidthDp else configuration.screenWidthDp.dp)
        .coerceAtLeast(minSize.width)
    // Ancho NATURAL real de la cabecera (ícono + título SIN truncar +
    // grupo de controles), medido por la composición "fantasma" de
    // [FloatingWindowHeaderRowContent] de acá abajo — ver el comentario
    // grande sobre el bug de ancho, más arriba. Arranca en 0 (nada
    // medido todavía, primerísimo frame); el `coerceAtLeast` de abajo,
    // al calcular `autoWidth`, ya cubre ese caso sin dejar la ventana en
    // 0.dp por un instante.
    var naturalHeaderWidthPx by remember { mutableStateOf(0) }
    val autoWidth = maxOf(initialSize.width, with(density) { naturalHeaderWidthPx.toDp() })
        .coerceAtMost(maxAutoWidth)
    // Minimizado: colapsa TODA la ventana (cabecera + contenido) a solo
    // su ícono — ver [FloatingWindowMinimizedBadge] más abajo. Vive en
    // el mismo Box con el mismo `.offset`, así que el ícono aparece
    // exactamente donde estaba la ventana, y restaurarla la deja igual
    // de tamaño/posición que antes de minimizarla (ni `windowSize` ni
    // `offsetPx` se tocan al minimizar/restaurar).
    var isMinimized by remember { mutableStateOf(false) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — ver el comentario grande sobre
    // esta variable en el Box raíz de más abajo (donde se usa para
    // subir el `zIndex`): a diferencia de
    // `minimizedRegistry.isMinimizedBadgeDragging` (compartida entre
    // las tres ventanas, dice "se está arrastrando ALGUNO"), esta es
    // LOCAL de esta ventana — dice "soy YO el que se está arrastrando
    // ahora mismo", que es lo único que hace falta saber para decidir
    // si ESTE ícono en particular necesita pasar por encima de todo lo
    // demás.
    var isDraggingThisBadge by remember { mutableStateOf(false) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
    // captura): "cuando abro cualquier icono minimizado al lado del
    // otro se superpone encima del otro, debería empujarlo a un lado
    // como un acordeón". Causa real: [resolveFreeSlot]/
    // `settleMinimizedPosition` (ver el comentario grande sobre
    // [FloatingWindowMinimizedRegistry]) solo corren en el momento en
    // que UN ícono se minimiza o se arrastra — nunca vuelven a correr
    // para un ícono que ya estaba quieto y minimizado cuando es OTRA
    // ventana la que se abre (o crece) encima de él. Se leía el mapa de
    // "ventanas abiertas" para que un ícono NUEVO las esquive al
    // aterrizar, pero un ícono VIEJO ya asentado nunca volvía a
    // preguntar si seguía teniendo el lugar libre.
    //
    // La corrección: mientras este ícono sigue minimizado, en cada
    // composición se pregunta (vía [FloatingWindowMinimizedRegistry.overlapsAnyOpenWindow],
    // definido junto al registro) si alguna ventana ABIERTA lo está
    // tapando ahora mismo — esa lectura de estado compartido dispara
    // recomposición sola cada vez que otra ventana publica su
    // rectángulo en vivo (ver el `SideEffect` más abajo, que registra el
    // rectángulo de ESTA ventana para las demás). Apenas la respuesta
    // pasa a "sí", el `LaunchedEffect` de acá abajo llama al mismo
    // `settleMinimizedPosition()` que ya usa el resto de los caminos
    // hacia "quedar minimizado" — mismo deslizamiento animado, mismo
    // hueco libre calculado — así que el ícono se corre solo al
    // costado, como un acordeón, apenas la ventana nueva empieza a
    // taparlo, en vez de quedar tapado. Tiene que ir DESPUÉS de
    // `isMinimized` (recién declarada arriba) — antes vivía más arriba
    // en la función y rompía la compilación (`Unresolved reference
    // 'isMinimized'`) por leerla antes de que existiera.
    val minimizedBadgeSizePx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SIZE.toPx() }
    val isCoveredByOpenWindow = isMinimized &&
        minimizedRegistry.overlapsAnyOpenWindow(windowId, offsetPx, minimizedBadgeSizePx)
    LaunchedEffect(isCoveredByOpenWindow) {
        if (isCoveredByOpenWindow) {
            settleMinimizedPosition()
        }
    }

    var presetsMenuExpanded by remember { mutableStateOf(false) }
    var optionsMenuExpanded by remember { mutableStateOf(false) }

    // --- BUG REAL corregido (reportado con captura: la ventana de
    // "Color Básico" abriéndose hacia AFUERA de la pantalla, con el
    // panel de sliders cortado por el borde). `clampToSideLimits`/
    // `clampToBottomLimit` (definidos más arriba) solo se ejecutaban
    // dentro de los dos `detectDragGestures` de esta función — es decir,
    // únicamente cuando el USUARIO arrastraba la ventana o el ícono
    // minimizado a mano. El tamaño de la ventana, en cambio, también
    // cambia SOLO, sin ningún arrastre de por medio: `windowSize` se
    // recalcula en cada composición mientras `!userResized` (ver el
    // comentario grande sobre `userResized`, más arriba) para seguir el
    // alto NATURAL real del contenido — por ejemplo, el salto de un
    // estado más chico a los sliders reales de Nitidez/Saturación/
    // Brillo/Contraste/Tono al abrir/expandir el panel. Ese crecimiento
    // nunca volvía a comprobar si la ventana seguía completa dentro del
    // área visible: una ventana ya posicionada cerca del borde derecho
    // o inferior terminaba creciendo hacia AFUERA de la pantalla en vez
    // de hacia adentro, exactamente lo reportado ("se abre pero para
    // afuera y no para dentro... se ve metido eso").
    //
    // La corrección: cada vez que el tamaño REAL de la ventana
    // (`windowSize`, ya sea porque el auto-tamaño lo recalculó o porque
    // el usuario la redimensionó a mano) o el tamaño del área contenedora
    // (`containerSizePx`) cambian, se vuelve a correr el MISMO recorte
    // que ya usa el arrastre — si la ventana ya no entra donde estaba,
    // se corre lo mínimo necesario hacia la izquierda y/o hacia arriba
    // para quedar completa dentro de la pantalla, en vez de dejarla
    // creciendo hacia afuera sin límite. No toca `offsetPx` para nada si
    // la ventana ya entra bien donde está — mismo criterio de "solo lo
    // mínimo necesario" que ya usa `clampToSideLimits`/
    // `clampToBottomLimit` en el arrastre.
    val currentWindowWidthPx = with(density) { (windowSize ?: initialSize).width.toPx() }
    val currentWindowHeightPx = with(density) { (windowSize ?: initialSize).height.toPx() }
    LaunchedEffect(currentWindowWidthPx, currentWindowHeightPx, containerSizePx) {
        val clampedX = clampToSideLimits(offsetPx.x, currentWindowWidthPx)
        val clampedY = clampToBottomLimit(offsetPx.y, currentWindowHeightPx)
        if (clampedX != offsetPx.x || clampedY != offsetPx.y) {
            offsetPx = offsetPx.copy(x = clampedX, y = clampedY)
        }
    }
    // BUG REAL corregido (ver el comentario grande sobre
    // [FloatingWindowMinimizedRegistry], junto a su declaración): otra
    // ventana a punto de minimizarse necesita saber, en el momento
    // exacto de asentarse, si ESTA ventana sigue abierta y dónde —
    // no solo dónde están los OTROS íconos ya minimizados. `SideEffect`
    // (no `LaunchedEffect`: esto es una publicación síncrona de "así
    // estoy AHORA", no una corrutina) corre después de cada composición
    // exitosa, así que el rectángulo publicado sigue a `offsetPx`/
    // `windowSize` en vivo mientras se arrastra o cambia de tamaño, y
    // se retira del registro apenas esta ventana se minimiza (o se
    // cierra, vía el `DisposableEffect` de más arriba) — dejando de
    // "reservar" ese lugar para las que se minimicen después.
    SideEffect {
        if (isMinimized) {
            minimizedRegistry.unregisterOpenWindow(windowId)
        } else {
            minimizedRegistry.registerOpenWindow(
                windowId,
                Rect(offsetPx, Size(currentWindowWidthPx, currentWindowHeightPx))
            )
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetPx.x.roundToInt(), offsetPx.y.roundToInt()) }
            // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido
            // (reportado con captura): "la bola flotante al ubicarse
            // encima del ícono eliminar va por detrás, debería ir por
            // encima". Causa real: [FloatingWindowDeleteDropZone] vivía
            // con un `zIndex` fijo altísimo (1000f, ver su
            // instanciación) para no quedar tapada por ninguna ventana —
            // pero eso incluía, sin querer, al PROPIO ícono que se
            // arrastra HACIA ella: como las tres ventanas comparten un
            // `zIndex` base de "20f + orden de toque" (ver el Box donde
            // se instancian las tres), cualquiera de ellas quedaba, sí o
            // sí, por DEBAJO de esos 1000f apenas se superponía
            // visualmente con la zona — el ícono desaparecía "detrás"
            // del círculo de eliminar justo en el momento en que más
            // importa verlo (a punto de soltar).
            //
            // La corrección: mientras ESTE ícono en particular se está
            // arrastrando (`isDraggingThisBadge`, ver el `onDragStart`/
            // `onDragEnd` del badge más abajo — variable LOCAL de esta
            // ventana, no la del registro compartido, que es "está
            // arrastrándose ALGUNO" sin decir cuál), su `zIndex` sube
            // muy por encima de cualquier otra cosa en pantalla
            // (incluida la zona de eliminar, ya bajada a un `zIndex`
            // bajo — ver su instanciación). `Modifier` (vacío) cuando NO
            // se está arrastrando, a propósito: así se respeta el
            // `zIndex` que ya trae `modifier` desde afuera (el orden
            // entre las tres ventanas) en vez de pisarlo con un valor
            // fijo.
            .then(if (isDraggingThisBadge) Modifier.zIndex(5000f) else Modifier)
    ) {
        if (isMinimized) {
            // A PEDIDO EXPLÍCITO DEL USUARIO: minimizado, el ícono tiene
            // que poder arrastrarse por toda la pantalla exactamente
            // igual que la ventana abierta — antes quedaba fijo porque
            // [FloatingWindowMinimizedBadge] no tenía ningún
            // `pointerInput` propio. `onDrag` acá reusa el MISMO
            // `offsetPx` que ya mueve la ventana completa (ver el
            // `.offset` del Box padre, un poco más arriba) — así que
            // restaurarla más tarde la deja EXACTAMENTE donde el usuario
            // soltó el ícono, sin ningún salto de posición.
            FloatingWindowMinimizedBadge(
                title = title,
                titleIcon = titleIcon,
                onClick = {
                    // BUG REAL corregido (reportado: arrastrar el ícono
                    // minimizado hacia un costado y tocar para expandir
                    // abre la ventana "hacia afuera", casi desaparecida,
                    // como si saltara hacia la derecha). Causa real: el
                    // ícono minimizado se arrastra y se recorta contra su
                    // propio tamaño chico (`FLOATING_WINDOW_MINIMIZED_BADGE_SIZE`,
                    // ver `onDrag` acá abajo) — una posición perfectamente
                    // válida para un círculo de ~48dp puede estar a solo
                    // unos pocos dp del borde derecho/inferior real. Al
                    // restaurar, la ventana vuelve a su tamaño COMPLETO
                    // (~260dp o más de ancho) en ESA MISMA posición sin
                    // que nada la revise de nuevo — el `LaunchedEffect`
                    // que sí re-clampa tamaño-vs-posición más abajo solo
                    // se dispara cuando cambian `windowSize`/
                    // `containerSizePx`, y ninguno de los dos cambia al
                    // pasar de minimizado a restaurado (el booleano
                    // `isMinimized` no es una de sus claves) — por eso
                    // nunca corría acá. La corrección: re-clampar acá
                    // mismo, ANTES de restaurar, usando el tamaño REAL con
                    // el que la ventana está por aparecer (el de la fase
                    // "auto" — `autoWidth`/`maxAutoHeight` — o el tamaño
                    // fijo que el usuario ya le haya dado a mano).
                    // `windowSize` ya refleja el tamaño real envuelto al
                    // contenido (medido por `onGloballyPositioned`, ver
                    // más abajo) tanto en fase "auto" como "manual" — se
                    // usa tal cual acá, con `initialSize` como único
                    // fallback para el caso límite de minimizar la
                    // ventana antes de su primer layout medido.
                    val restoredSize = windowSize ?: initialSize
                    val restoredWidthPx = with(density) { restoredSize.width.toPx() }
                    val restoredHeightPx = with(density) { restoredSize.height.toPx() }
                    offsetPx = offsetPx.copy(
                        x = clampToSideLimits(offsetPx.x, restoredWidthPx),
                        y = clampToBottomLimit(offsetPx.y, restoredHeightPx)
                    )
                    isMinimized = false
                    minimizedRegistry.unregister(windowId)
                },
                onDrag = { dragAmount ->
                    // TECHO DURO (a pedido explícito del usuario, con
                    // captura): nada puede arrastrarse por ENCIMA del
                    // header, ni siquiera el ícono minimizado. `offsetPx.y`
                    // en 0 ya es "pegado al pie del header" (ver el
                    // comentario grande sobre [FLOATING_WINDOW_TOP_MAGNET_MARGIN]
                    // más arriba) — `coerceAtLeast(0f)` evita que un
                    // arrastre rápido hacia arriba lo empuje más allá de
                    // ese límite y termine encima/detrás del header.
                    //
                    // BUG REAL corregido (ver [FloatingWindowMinimizedRegistry]):
                    // arrastrar este ícono ya minimizado ENCIMA de otro
                    // ícono minimizado los dejaba superpuestos, sin
                    // ningún aviso. `settleMinimizedPosition()` corre
                    // cada nueva posición contra el registro compartido
                    // y, si colisiona, la corre al hueco libre más
                    // cercano — el ícono "resbala" al lado del otro en
                    // vez de taparlo.
                    // BUG REAL corregido (a pedido explícito del usuario,
                    // con captura): arrastrar el ícono minimizado hacia
                    // los COSTADOS o hacia ABAJO lo perdía fuera del área
                    // visible — acá solo se agrega el techo por la
                    // derecha/izquierda y por abajo (`clampToSideLimits`/
                    // `clampToBottomLimit`, definidos más arriba); el
                    // límite de ARRIBA sigue siendo, sin tocar nada, el
                    // `coerceAtLeast(0f)` de la línea de acá abajo.
                    val badgeSizePx = with(density) { FLOATING_WINDOW_MINIMIZED_BADGE_SIZE.toPx() }
                    offsetPx = (offsetPx + dragAmount).let {
                        it.copy(
                            x = clampToSideLimits(it.x, badgeSizePx),
                            y = clampToBottomLimit(it.y.coerceAtLeast(0f), badgeSizePx)
                        )
                    }
                    // A PEDIDO EXPLÍCITO DEL USUARIO: mientras se arrastra
                    // este ícono, en cada frame se consulta el rectángulo
                    // REAL de la zona de "Eliminar" (publicado por
                    // [FloatingWindowDeleteDropZone] mientras está en
                    // pantalla — ver `deleteZoneRect` en
                    // [FloatingWindowMinimizedRegistry]) contra el CENTRO
                    // de este ícono, no contra su rectángulo completo: se
                    // siente más preciso soltar "apuntando" con el dedo
                    // que con cualquier borde del círculo. `!=` en vez de
                    // reescribir siempre el mismo valor: la vibración de
                    // abajo tiene que dispararse UNA sola vez al ENTRAR a
                    // la zona, no en cada uno de los muchos frames que el
                    // dedo pasa por encima de ella.
                    val badgeCenter = offsetPx + Offset(badgeSizePx / 2f, badgeSizePx / 2f)
                    val zone = minimizedRegistry.deleteZoneRect
                    val hoveringDeleteZoneNow = zone != null && zone.contains(badgeCenter)
                    if (hoveringDeleteZoneNow != minimizedRegistry.isDragOverDeleteZone) {
                        minimizedRegistry.isDragOverDeleteZone = hoveringDeleteZoneNow
                        if (hoveringDeleteZoneNow) {
                            triggerDeleteZoneHoverPulse()
                        }
                    }
                    settleMinimizedPosition()
                },
                onDragStart = {
                    // A PEDIDO EXPLÍCITO DEL USUARIO: la zona de
                    // "Eliminar" (ver [FloatingWindowDeleteDropZone])
                    // solo puede aparecer mientras se arrastra el ÍCONO
                    // YA MINIMIZADO — nunca al arrastrar la ventana
                    // abierta desde su cabecera. `isMinimizedBadgeDragging`
                    // es la única señal que la muestra/oculta.
                    minimizedRegistry.isMinimizedBadgeDragging = true
                    // Ver el comentario grande sobre `isDraggingThisBadge`,
                    // junto a su declaración: sube el `zIndex` de ESTE
                    // ícono por encima de todo (incluida la zona de
                    // eliminar) mientras dura el arrastre.
                    isDraggingThisBadge = true
                },
                onDragEnd = {
                    // Se decide acá, UNA sola vez al soltar el dedo, en
                    // vez de en cada frame de `onDrag`: evita borrar el
                    // efecto por error si el usuario pasó de refilón por
                    // encima de la zona sin querer soltar ahí — solo
                    // cuenta la posición FINAL, al momento exacto de
                    // soltar.
                    val shouldDelete = minimizedRegistry.isDragOverDeleteZone
                    minimizedRegistry.isMinimizedBadgeDragging = false
                    minimizedRegistry.isDragOverDeleteZone = false
                    isDraggingThisBadge = false
                    if (shouldDelete) {
                        // Pulso de confirmación (más largo y distinto al
                        // de "entraste a la zona", ver su comentario
                        // grande junto a la declaración) — el cierre
                        // táctil del gesto, no un aviso más.
                        triggerDeleteZoneConfirmPulse()
                        onDeleteEffect?.invoke()
                    }
                },
                // A PEDIDO EXPLÍCITO DEL USUARIO: solo ESTE ícono se
                // pinta de rojo — la combinación de "soy yo el que se
                // está arrastrando" (`isDraggingThisBadge`, LOCAL de
                // esta ventana) y "el que se arrastra está sobre la
                // zona" (`minimizedRegistry.isDragOverDeleteZone`,
                // COMPARTIDA entre las tres) es necesaria: sin la
                // primera, CUALQUIER badge minimizado se pintaría de
                // rojo apenas OTRO empezara a sobrevolar la zona.
                isOverDeleteZone = isDraggingThisBadge && minimizedRegistry.isDragOverDeleteZone
            )
            return@Box
        }

        // --- Tamaño de la ventana: dos fases.
        // Fase "auto" (`!userResized`, la de por defecto): ancho Y alto
        // LIBRES — envuelve cabecera + contenido a su tamaño NATURAL,
        // con techo en `maxAutoHeight`/`maxAutoWidth` (el área REAL
        // disponible) para que nunca se salga de pantalla. Se re-mide en
        // CADA composición (no solo la primera vez) para seguirle el
        // paso a contenido que cambia de forma después de abierta la
        // ventana (spinner de carga → sliders reales, cambio de capa
        // seleccionada, etc.) — ver el comentario grande sobre
        // `userResized`, más abajo, con el detalle del bug que esto
        // corrige.
        //
        // CORRECCIÓN REVERTIDA (confirmado por el usuario con captura:
        // "no puede ser, me jodiste el proyecto"): un intento anterior
        // FORZABA el alto de esta fase a `maxAutoHeight` siempre —
        // pensado para que la ventana "llegue hasta abajo" incluso con
        // poco contenido — pero el resultado real fue un panel gigante,
        // casi vacío, tapando media pantalla (los 5 sliders de "Color
        // Básico" arriba del todo y un enorme hueco morado debajo, sin
        // nada). Eso es peor que el bug original. Se vuelve al criterio
        // de siempre: la ventana envuelve su contenido real
        // (`heightIn(max = maxAutoHeight)` — techo, nunca piso), y
        // `maxAutoHeight` en sí SIGUE con su corrección real (más
        // arriba: ahora sí descuenta `offsetPx.y`, la posición vertical
        // de la ventana, algo que antes de esa corrección faltaba y
        // hacía que el techo calculado fuera mayor al espacio real
        // disponible) — así que una ventana con contenido genuinamente
        // alto (que sí necesite acercarse al borde inferior) puede
        // seguir haciéndolo sin recortarse, pero una ventana con poco
        // contenido (como "Color Básico") ya NO se estira de más para
        // "rellenar" un espacio que no necesita.
        // Ninguno de los `fillMaxSize()` de acá abajo se aplica todavía
        // en esta fase — ver `sized` — porque `fillMaxSize()` reclamaría
        // de entrada el techo completo en vez de envolver el contenido
        // real.
        // Fase "manual" (`userResized`, arranca cuando el usuario
        // arrastra la manija por primera vez): tamaño fijo de siempre,
        // exactamente igual que el comportamiento original — desde acá
        // el auto-tamaño no vuelve a tocar `windowSize`.
        val sized = userResized
        Box(
            modifier = Modifier
                .then(
                    if (sized) {
                        Modifier.size(windowSize ?: initialSize)
                    } else {
                        Modifier
                            .width(autoWidth)
                            .heightIn(min = 0.dp, max = maxAutoHeight)
                    }
                )
                // Ver el comentario grande sobre `onInteracted`, en la
                // firma de esta función, para el porqué: observa el
                // primer toque de CADA gesto en TODA la ventana (no solo
                // la cabecera) sin consumirlo (`requireUnconsumed =
                // false` + nunca se llama `change.consume()`), así que
                // esto convive sin pisar el arrastre de la cabecera ni
                // los sliders/botones de adentro — solo avisa "tocaron
                // esta ventana" para que el llamador la traiga al
                // frente de las otras dos.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onInteracted()
                    }
                }
                .onGloballyPositioned { coordinates ->
                    // Mientras el usuario no haya redimensionado a mano,
                    // esto corre en CADA layout — no solo el primero —
                    // así que si el contenido cambia de alto de verdad
                    // (spinner → sliders reales, u otro cambio), la
                    // ventana lo sigue automáticamente en vez de quedar
                    // pegada al alto del primer frame.
                    if (!userResized) {
                        val measuredHeight = with(density) { coordinates.size.height.toDp() }
                        windowSize = DpSize(
                            width = autoWidth,
                            height = (measuredHeight + FLOATING_WINDOW_RESIZE_HANDLE_CLEARANCE)
                                .coerceIn(minSize.height, maxAutoHeight)
                        )
                    }
                }
        ) {
          Surface(
            modifier = Modifier
                .then(if (sized) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top))
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp)),
            color = SurfaceTintedElevated,
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(
                modifier = if (sized) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top)
            ) {
                // --- Cabecera arrastrable: mismo rol que la barra de
                // título de un plugin flotante — tocar y arrastrar CUALQUIER
                // punto de esta fila reubica toda la ventana dentro del
                // canvas. `change.consume()` evita que el gesto se filtre
                // hacia abajo, al canvas (que interpretaría el mismo
                // arrastre como mover la CAPA de la imagen, no la ventana).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FLOATING_WINDOW_HEADER_HEIGHT)
                        .background(Color.Black.copy(alpha = 0.25f))
                        .pointerInput(Unit) {
                            // A PEDIDO EXPLÍCITO DEL USUARIO — imán al pie
                            // del header (ver el comentario grande sobre
                            // [FLOATING_WINDOW_TOP_MAGNET_MARGIN], más
                            // arriba, con las referencias entregadas).
                            // Se usa `onDragEnd` Y `onDragCancel` con la
                            // MISMA lógica — si algo externo corta el
                            // gesto a mitad de camino, igual se revisa
                            // dónde quedó la ventana en ese instante.
                            val magnetizeIfNearHeaderBottom: () -> Unit = {
                                val marginPx = with(density) {
                                    FLOATING_WINDOW_TOP_MAGNET_MARGIN.toPx()
                                }
                                if (offsetPx.y <= marginPx) {
                                    // Efecto imán de verdad, no solo un
                                    // umbral que dispara: al minimizar
                                    // acá, la ventana queda pegada
                                    // EXACTO al pie del header (y = 0),
                                    // sin importar en qué punto exacto
                                    // de la franja la hayas soltado.
                                    offsetPx = offsetPx.copy(y = 0f)
                                    isMinimized = true
                                    // BUG REAL corregido (ver
                                    // [FloatingWindowMinimizedRegistry]):
                                    // como las tres ventanas nacen cerca
                                    // unas de otras y todas magnetizan al
                                    // mismo `y = 0`, arrastrarlas por
                                    // turno a esta franja las dejaba a
                                    // todas en (casi) el mismo punto —
                                    // el ícono "doble" reportado. Ahora,
                                    // antes de asentarse, se corre al
                                    // hueco libre más cercano en esa
                                    // misma franja si ya hay otro ícono
                                    // ahí.
                                    settleMinimizedPosition()
                                }
                            }
                            // A PEDIDO EXPLÍCITO DEL USUARIO — "lanzar"
                            // la ventana hacia arriba desde cualquier
                            // parte de la pantalla (ver el comentario
                            // grande sobre
                            // [FLOATING_WINDOW_FLING_MINIMIZE_VELOCITY_DP_PER_SEC],
                            // más arriba, con el detalle completo del
                            // pedido). `VelocityTracker` mide qué tan
                            // rápido iba el dedo al soltar — no solo
                            // hacia dónde — que es exactamente lo que
                            // separa "un golpe rápido hacia arriba" de
                            // "un arrastre tranquilo que cruzó toda la
                            // pantalla": ambos pueden terminar recorriendo
                            // la misma distancia, pero solo el primero
                            // llega con velocidad alta al soltar.
                            val flingThresholdPx = with(density) {
                                FLOATING_WINDOW_FLING_MINIMIZE_VELOCITY_DP_PER_SEC.toPx()
                            }
                            // TERCER AJUSTE (reportado: "el primer intento
                            // sí funciona, pero repitiendo la prueba en la
                            // misma ventana cuesta más, a veces hacen
                            // falta varios intentos"): exigir un único
                            // pico de velocidad instantánea por encima de
                            // un umbral fijo es "todo o nada" — un flick
                            // real nunca sale exactamente igual dos veces
                            // seguidas, así que un umbral que hoy se
                            // cruza por poco mañana puede no cruzarse por
                            // poco también, aunque el gesto haya sido
                            // igual de intencional. Se agrega una SEGUNDA
                            // vía de detección, más tolerante: si durante
                            // el gesto la ventana ya recorrió una
                            // distancia real hacia arriba (no solo un
                            // pico de velocidad al soltar) Y terminó con
                            // una velocidad moderada — bastante menos
                            // exigente que el umbral "puro" de arriba —
                            // también cuenta como lanzamiento. Esto cubre
                            // el flick "medio" que antes quedaba en tierra
                            // de nadie: ni lento como para ser un
                            // reacomodo tranquilo, ni con un pico tan
                            // marcado como para cruzar el umbral solo por
                            // velocidad.
                            val flingDistanceThresholdPx = with(density) { 56.dp.toPx() }
                            val flingModerateVelocityThresholdPx = flingThresholdPx * 0.45f
                            val handleHeaderDragEnd: (VelocityTracker, Float) -> Unit =
                                { velocityTracker, totalDragY ->
                                val flingVelocityY = velocityTracker.calculateVelocity().y
                                // Negativo = hacia arriba (mismo sentido
                                // que ya usa el resto de la app para
                                // velocidad de arrastre — ver el
                                // "flick" de [GridAxisStepper], más
                                // abajo en este archivo). Cualquiera de
                                // las dos vías de acá abajo cuenta como
                                // lanzamiento real; si ninguna se cumple,
                                // cae al comportamiento de siempre (el
                                // imán, si ya estaba cerca del pie del
                                // header, o directamente nada).
                                val isFastFlick = flingVelocityY <= -flingThresholdPx
                                val isModerateFlickWithTravel =
                                    totalDragY <= -flingDistanceThresholdPx &&
                                        flingVelocityY <= -flingModerateVelocityThresholdPx
                                if (isFastFlick || isModerateFlickWithTravel) {
                                    val startY = offsetPx.y
                                    coroutineScope.launch {
                                        animate(
                                            initialValue = startY,
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = FLOATING_WINDOW_FLING_MINIMIZE_ANIMATION_MS,
                                                easing = FastOutLinearInEasing
                                            )
                                        ) { value, _ ->
                                            // Solo el alto "vuela" hasta
                                            // el pie del header — el
                                            // ancho (X) se queda donde
                                            // el golpe la soltó, así el
                                            // ícono no salta de lado a
                                            // lado de la pantalla, solo
                                            // sube.
                                            offsetPx = offsetPx.copy(y = value)
                                        }
                                        isMinimized = true
                                        settleMinimizedPosition()
                                    }
                                } else {
                                    magnetizeIfNearHeaderBottom()
                                }
                            }
                            // A PEDIDO EXPLÍCITO DEL USUARIO — SEGUNDO BUG
                            // REAL corregido (reportado: "a veces hay que
                            // lanzar varias veces para que se dispare").
                            // La primera versión de este bloque alimentaba
                            // el `VelocityTracker` con
                            // `addPosition(change.uptimeMillis,
                            // change.position)` — SOLO la posición final
                            // de cada evento. Pero en un gesto rápido,
                            // Android suele agrupar varios movimientos
                            // intermedios del dedo dentro de un mismo
                            // evento (sus muestras "históricas",
                            // expuestas en `change.historical`) en vez de
                            // entregar un evento por cada movimiento real
                            // — así que un flick veloz podía llegar acá
                            // como un solo evento con la posición final,
                            // perdiendo TODO el recorrido intermedio que
                            // definía qué tan rápido fue en verdad el
                            // golpe. Resultado: velocidad calculada baja
                            // e inconsistente según cómo el sistema
                            // decidiera agrupar ESE gesto puntual — de ahí
                            // que a veces disparara y otras veces no, con
                            // el mismo esfuerzo real del dedo.
                            // `VelocityTracker.addPointerInputChange(...)`
                            // es la función que expone Compose
                            // específicamente para esto: procesa también
                            // `change.historical`, así que ningún punto
                            // intermedio del flick se pierde y la
                            // velocidad calculada refleja el gesto
                            // completo, no solo su último instante.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPointerInputChange(down)
                                val pointerId = down.id
                                // Distancia total recorrida en Y durante
                                // ESTE gesto puntual (negativo = hacia
                                // arriba) — alimenta la segunda vía de
                                // detección de `handleHeaderDragEnd`, más
                                // arriba, junto al pico de velocidad.
                                var totalDragY = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null) {
                                        // Gesto cortado desde afuera (mismo
                                        // caso que cubría `onDragCancel`
                                        // antes) — igual se revisa la
                                        // velocidad acumulada hasta acá.
                                        handleHeaderDragEnd(velocityTracker, totalDragY)
                                        break
                                    }
                                    velocityTracker.addPointerInputChange(change)
                                    if (change.changedToUpIgnoreConsumed()) {
                                        change.consume()
                                        handleHeaderDragEnd(velocityTracker, totalDragY)
                                        break
                                    }
                                    val dragAmount = change.positionChange()
                                    change.consume()
                                    totalDragY += dragAmount.y
                                    // Mismo techo duro que en el badge
                                    // minimizado, más arriba — ver ese
                                    // comentario para el porqué. Antes esto
                                    // era `offsetPx += dragAmount` sin ningún
                                    // límite, así que un arrastre hacia arriba
                                    // metía la ventana entera (cabecera
                                    // incluida) por detrás/encima del header
                                    // real de la pantalla. Eso sigue igual,
                                    // sin tocarlo.
                                    //
                                    // BUG REAL corregido (a pedido explícito
                                    // del usuario, con captura): arrastrar la
                                    // ventana ABIERTA hacia los COSTADOS o
                                    // hacia ABAJO la perdía fuera del área
                                    // visible, exactamente igual que al ícono
                                    // minimizado — acá solo se agrega el
                                    // límite de costados/abajo, contra el
                                    // tamaño REAL actual de la ventana
                                    // (`windowSize` mientras se está
                                    // auto-midiendo, o `initialSize` si
                                    // todavía no se midió nada).
                                    val currentWindowSizePx = with(density) {
                                        val size = windowSize ?: initialSize
                                        Offset(size.width.toPx(), size.height.toPx())
                                    }
                                    offsetPx = (offsetPx + dragAmount).let {
                                        it.copy(
                                            x = clampToSideLimits(it.x, currentWindowSizePx.x),
                                            y = clampToBottomLimit(it.y.coerceAtLeast(0f), currentWindowSizePx.y)
                                        )
                                    }
                                }
                            }
                        }
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido:
                    // acá había `weight(1f, fill = false)` en el `Text` del
                    // título, que hacía que NUNCA reclamara el espacio
                    // sobrante de la fila — solo ocupaba lo que su propio
                    // contenido medía. Resultado: al ensanchar la ventana
                    // (arrastrando la manija de la esquina), el grupo de
                    // controles de acá abajo (Presets/‹›/-/×) quedaba
                    // pegado justo después del título, en vez de seguir la
                    // esquina superior derecha del header como corresponde.
                    // `weight(1f)` a secas (fill = true, el default, ver
                    // [FloatingWindowHeaderRowContent] con `titleWeighted =
                    // true` acá abajo) es lo que hace que ESTE Text sea el
                    // que se estira para absorber todo el ancho sobrante —
                    // y como el grupo de controles va inmediatamente
                    // después de él en la Row, eso es lo que lo deja
                    // siempre pegado a la esquina derecha, sin importar
                    // cuánto se ensanche la ventana. La elipsis
                    // (maxLines=1 + TextOverflow.Ellipsis) sigue ahí como
                    // red de seguridad para un preset con nombre
                    // absurdamente largo — ver el comentario grande sobre
                    // `autoWidth`, más arriba, para el porqué el título ya
                    // NO depende de la elipsis en el caso normal.
                    FloatingWindowHeaderRowContent(
                        title = title,
                        titleIcon = titleIcon,
                        titleWeighted = true,
                        presets = presets,
                        selectedPresetIndex = selectedPresetIndex,
                        presetsMenuExpanded = presetsMenuExpanded,
                        onPresetsTogglePress = { presetsMenuExpanded = !presetsMenuExpanded },
                        onPresetsDismiss = { presetsMenuExpanded = false },
                        onPresetSelect = { index ->
                            onPresetSelected(index)
                            presetsMenuExpanded = false
                        },
                        onPresetPrev = {
                            val current = selectedPresetIndex ?: 0
                            onPresetSelected((current - 1 + presets.size) % presets.size)
                        },
                        onPresetNext = {
                            val current = selectedPresetIndex ?: -1
                            onPresetSelected((current + 1) % presets.size)
                        },
                        onMinimize = {
                            isMinimized = true
                            // Mismo ajuste anti-superposición que el
                            // imán del pie del header — ver el
                            // comentario grande sobre
                            // [FloatingWindowMinimizedRegistry]: el
                            // botón "-" también puede dejar dos íconos
                            // en el mismo punto si la ventana ya estaba
                            // arrastrada justo encima de donde otra
                            // quedó minimizada antes.
                            settleMinimizedPosition()
                        },
                        onClose = onClose,
                        menuExpanded = optionsMenuExpanded,
                        onMenuTogglePress = { optionsMenuExpanded = !optionsMenuExpanded },
                        onMenuDismiss = { optionsMenuExpanded = false }
                    )
                }

                // --- Medición "fantasma" del ancho NATURAL de la cabecera
                // (ver el comentario grande sobre `autoWidth`, más arriba):
                // compone la MISMA [FloatingWindowHeaderRowContent] de
                // arriba, pero con `titleWeighted = false` (el título toma
                // su ancho real, sin recortar) dentro de un `Row` con el
                // MISMO padding horizontal que la cabecera visible, medido
                // con `Constraints()` — sin límite de ancho, algo que la
                // cabecera VISIBLE nunca podría aceptar sin romperse (un
                // `Row` con un hijo `weight()` no admite un ancho máximo
                // infinito). El resultado de este `Layout` nunca se
                // dibuja — `layout(0, 0) {}` lo deja en tamaño cero, así
                // que no ocupa lugar ni es tocable — solo existe para
                // capturar el ancho real en `naturalHeaderWidthPx`.
                if (!userResized) {
                    Layout(
                        content = {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FloatingWindowHeaderRowContent(
                                    title = title,
                                    titleIcon = titleIcon,
                                    titleWeighted = false,
                                    presets = presets,
                                    selectedPresetIndex = selectedPresetIndex,
                                    presetsMenuExpanded = false,
                                    onPresetsTogglePress = {},
                                    onPresetsDismiss = {},
                                    onPresetSelect = {},
                                    onPresetPrev = {},
                                    onPresetNext = {},
                                    onMinimize = {},
                                    onClose = onClose,
                                    menuExpanded = false,
                                    onMenuTogglePress = {},
                                    onMenuDismiss = {}
                                )
                            }
                        }
                    ) { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        if (placeable.width != naturalHeaderWidthPx) {
                            naturalHeaderWidthPx = placeable.width
                        }
                        layout(0, 0) {}
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Column(
                    modifier = Modifier
                        .then(if (sized) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    content = content
                )
            }
        }

        // --- Manija de redimensionar: esquina inferior derecha, mismo
        // patrón visual "tres rayas diagonales" que usan la mayoría de
        // ventanas flotantes de escritorio.
        //
        // BUG REAL corregido (reportado con captura: la ventana quedaba
        // con texto cortado en el borde derecho — "Nitid", "Satu",
        // "Cont", "Tono" — sin importar dónde se reposicionara).
        // `coerceAtLeast(minSize...)` le ponía un PISO (nunca encogerla
        // tanto que el contenido deje de caber/leerse) pero NUNGÚN
        // TECHO: arrastrando esta manija hacia afuera, la ventana podía
        // crecer más ANCHA (o más ALTA) que el área real disponible.
        // Una vez así de grande, NINGÚN reposicionamiento la arregla —
        // ni el recorte de `offsetPx` de más arriba (ver el
        // `LaunchedEffect` sobre `clampToSideLimits`/`clampToBottomLimit`)
        // puede hacer que una ventana más ancha que la pantalla quepa
        // entera: mover la ventana solo decide QUÉ PARTE se corta, nunca
        // evita que se corte. La causa real estaba acá, en el tamaño
        // mismo, no en la posición.
        //
        // La corrección agrega el techo que faltaba: `coerceIn` en vez
        // de `coerceAtLeast`, con `maxAutoWidth`/`maxAutoHeight` (ya
        // corregidos más arriba para reflejar el área REAL del
        // contenedor, no la pantalla física completa) como límite
        // superior — exactamente el mismo número que ya usa el
        // auto-tamaño, así que el resultado final es consistente sin
        // importar si la ventana llegó a este tamaño sola o porque el
        // usuario arrastró la manija a mano.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaWidth = with(density) { dragAmount.x.toDp() }
                        val deltaHeight = with(density) { dragAmount.y.toDp() }
                        // Primer arrastre de la manija: acá es donde la
                        // ventana pasa de "auto" a "manual" de verdad
                        // (ver `userResized`, más arriba) — de acá en
                        // adelante el auto-tamaño no vuelve a tocar
                        // `windowSize`, es 100% del usuario, dentro del
                        // rango [minSize, maxAutoWidth/maxAutoHeight].
                        val base = windowSize ?: initialSize
                        windowSize = DpSize(
                            width = (base.width + deltaWidth).coerceIn(minSize.width, maxAutoWidth),
                            height = (base.height + deltaHeight).coerceIn(minSize.height, maxAutoHeight)
                        )
                        userResized = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val stroke = Stroke(width = 1.6.dp.toPx())
                val c = Color.White.copy(alpha = 0.55f)
                for (i in 0..2) {
                    val offset = i * 5.dp.toPx()
                    drawLine(
                        color = c,
                        start = androidx.compose.ui.geometry.Offset(size.width, offset),
                        end = androidx.compose.ui.geometry.Offset(offset, size.height),
                        strokeWidth = stroke.width
                    )
                }
            }
        }
        }
    }
}

// --- Reporte del usuario, con captura de "Color Básico": "al
// expandirse se ve como que se expande una parte y luego el resto...
// trabado". Historial de intentos:
// 1) Se intentó envolver el `if (isLoading)` de
//    [ColorBasicoFloatingWindow]/[Basico3DFloatingWindow] en
//    `AnimatedContent` + `SizeTransform` — ESO INTRODUJO UNA REGRESIÓN
//    PEOR, confirmada por el usuario con otra captura: texto de los
//    sliders (Nitidez/Saturación/Brillo/Contraste/Tono) superpuesto y
//    mezclado, ilegible. Revertido por completo en su momento.
// 2) Diagnóstico real, confirmado después: el salto de tamaño no era un
//    problema de ANIMACIÓN — era que el `isLoading` en sí no tenía
//    motivo de existir ahí. Los sliders de estas ventanas (y los de
//    [EffectsPanel]/[DistortionPanel], mismo patrón exacto) no dibujan
//    ni dependen del bitmap para nada — solo leen/escriben estado en
//    memoria (`ctrl`, o variables locales como `rotationX`). El bitmap
//    que `isLoading` esperaba es SOLO para la vista previa en vivo
//    sobre el canvas, algo que las funciones que la disparan
//    (`applyLivePreviewAndScheduleCommit`/`scheduleLivePreview`, etc.)
//    ya tratan como opcional (`if (small != null ...)` / `?: return`).
//    La corrección final fue sacar el `if (isLoading) spinner else
//    contenido` de las cuatro ventanas y mostrar los controles directo
//    — sin animación de por medio, porque ya no hay dos tamaños
//    distintos entre los que saltar. Ver el comentario grande dentro de
//    [ColorBasicoFloatingWindow], justo antes de su `EffectsCategoryColor(...)`,
//    para el detalle completo.
// 3) [RecolorFloatingWindow] quedó AFUERA de la corrección del punto 2 —
//    a diferencia de las otras cuatro, sus sliders SÍ dependen de un
//    dato real que hay que leer del bitmap (la paleta de colores), así
//    que en un primer intento se le agregó su propio spinner de carga
//    (`isLoadingPalette`) más un estado "atenuado/deshabilitado" para el
//    bloque de sliders+rueda mientras no había color elegido. Confirmado
//    por el usuario con otra captura: eso se veía PEOR que el bug
//    original, dos señales de "cargando" a la vez y nada instantáneo
//    como en Color Básico/3D Básico. Corrección final, ahora sí igual de
//    radical que en el punto 2: se sacó el spinner Y el atenuado. La fila
//    de paleta reserva su alto fijo desde el primer frame (34.dp, tenga
//    0 o 10 colores) y los sliders + la rueda están SIEMPRE visibles y
//    habilitados — arrancan en su valor default (blanco a pleno brillo)
//    y `selectSwatch` los actualiza solo apenas la paleta real llega, sin
//    ningún salto de tamaño ni ningún estado visual "cargando" de por
//    medio. Ver el bloque `Box(Modifier...height(34.dp))` dentro de
//    [RecolorFloatingWindow] para el detalle.
//
/**
 * Botón compacto de cabecera (flechas `‹`/`›` y `-`) — mismo estilo para
 * los tres, a propósito: círculo invisible de 20.dp (área de toque),
 * ícono SVG premium de 11.dp ya existente en `res/drawable` (nunca uno
 * nuevo, tal como se pidió explícitamente). `enabled = false` atenúa el
 * ícono y desactiva el toque, en vez de ocultar el botón — así la
 * cabecera no "salta" de ancho cuando `presets` está vacía.
 */
@Composable
private fun FloatingWindowHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.75f else 0.25f),
            modifier = Modifier.size(11.dp)
        )
    }
}

// --- Panel de OPCIONES de la ventana (A PEDIDO DEL USUARIO — rediseño
// v2, con referencias reales entregadas: el menú de opciones de un
// plugin de FL Studio, tipo "FL Keys" — ver capturas). La versión
// anterior era una "bandeja lateral" angosta pegada al costado de la
// ventana; el usuario pidió explícitamente que en vez de eso sea un
// desplegable NORMAL, como cualquier menú de aplicación: aparece DEBAJO
// de su ícono (el mismo lenguaje que ya usa [FloatingWindowPresetsControl]
// con "Presets"), pegado cerca de la ventana — no lanzado lejos hacia
// un costado — y con un ancho de menú real (no una barra angostísima de
// 64.dp, pensada para eso ya no aplica ahora que el contenido va a ser
// texto en filas, como en la referencia de FL Studio).
private val OPTIONS_MENU_PANEL_WIDTH = 216.dp
private val OPTIONS_MENU_PANEL_CORNER_RADIUS = 14.dp
private val OPTIONS_MENU_PANEL_ACCENT_HEIGHT = 3.dp

/**
 * Botón de cabecera que abre [FloatingWindowOptionsMenuButton] — el
 * glifo de tres cuadrados ([R.drawable.ic_menu_option], el SVG premium
 * entregado por el usuario, sin modificar) en el mismo botón circular
 * de 22.dp que ya usan `‹`/`›`/`-` (ver [FloatingWindowHeaderIconButton]),
 * pegado como el PRIMER elemento de la cabecera — a la izquierda de
 * [titleIcon] — tal como se pidió explícitamente ("al lado izquierdo del
 * icono del efecto arriba superior izquierda del header").
 */
@Composable
private fun FloatingWindowOptionsMenuButton(
    expanded: Boolean,
    onTogglePress: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val edgeMarginPx = with(density) { 8.dp.roundToPx() }
    val verticalGapPx = with(density) { 6.dp.roundToPx() }
    val panelWidthPx = with(density) { OPTIONS_MENU_PANEL_WIDTH.roundToPx() }
    // A PEDIDO EXPLÍCITO DEL USUARIO — referencias entregadas (menú de
    // opciones de un plugin de FL Studio, ver capturas): el panel ahora
    // se abre como un desplegable NORMAL, DEBAJO de este ícono — igual
    // que [FloatingWindowPresetsControl] con "Presets" — en vez de la
    // "bandeja lateral" de la versión anterior. Sigue midiendo la
    // posición real del botón en pantalla (`onGloballyPositioned` +
    // `positionInWindow()`, ver comentario de más abajo sobre el bug de
    // la v1), pero ahora solo para mantenerlo PEGADO cerca de la
    // ventana ("no muy afuera", pedido textual): si abrirlo pegado al
    // borde izquierdo del ícono lo saca de la pantalla por la derecha,
    // se corre lo mínimo necesario hacia la izquierda para que quede
    // completo — nunca lo lanza lejos hacia un costado como antes.
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    var anchorXInWindowPx by remember { mutableStateOf(0f) }
    var anchorYInWindowPx by remember { mutableStateOf(0f) }
    var anchorHeightPx by remember { mutableStateOf(0) }
    var panelHeightPx by remember { mutableStateOf(0) }

    // Corrimiento horizontal mínimo para que el panel no se salga de la
    // pantalla por la derecha — si entra completo abierto pegado al
    // ícono (el caso normal, ventana no pegada al borde), este valor es
    // 0 y el panel queda exactamente debajo del ícono, sin correrse.
    val horizontalOverflowPx = (anchorXInWindowPx.toInt() + panelWidthPx + edgeMarginPx) - screenWidthPx
    val offsetXPx = if (horizontalOverflowPx > 0) -horizontalOverflowPx else 0

    // Igual que arriba pero en vertical: si no entra hacia abajo (ventana
    // pegada cerca del borde inferior de la pantalla), se abre hacia
    // ARRIBA del ícono en su lugar — nunca queda cortado por abajo.
    val opensUpward = panelHeightPx > 0 &&
        anchorYInWindowPx + anchorHeightPx + verticalGapPx + panelHeightPx + edgeMarginPx > screenHeightPx
    val offsetYPx = if (opensUpward) {
        -(panelHeightPx + verticalGapPx)
    } else {
        anchorHeightPx + verticalGapPx
    }

    Box {
        Box(
            modifier = Modifier
                .size(22.dp)
                .onGloballyPositioned { coordinates ->
                    val positionInWindow = coordinates.positionInWindow()
                    anchorXInWindowPx = positionInWindow.x
                    anchorYInWindowPx = positionInWindow.y
                    anchorHeightPx = coordinates.size.height
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePress
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_menu_option),
                contentDescription = "Opciones de la ventana",
                tint = Color.White.copy(alpha = if (expanded) 0.95f else 0.75f),
                modifier = Modifier.size(12.dp)
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = offsetXPx, y = offsetYPx),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .width(OPTIONS_MENU_PANEL_WIDTH)
                        .onGloballyPositioned { coordinates ->
                            panelHeightPx = coordinates.size.height
                        }
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(OPTIONS_MENU_PANEL_CORNER_RADIUS),
                            clip = false
                        ),
                    color = SurfaceTintedDark,
                    shape = RoundedCornerShape(OPTIONS_MENU_PANEL_CORNER_RADIUS),
                    border = BorderStroke(1.dp, BrandPurpleLight.copy(alpha = 0.4f))
                ) {
                    Column {
                        // Misma franja de acento que [FloatingWindowPresetsControl]
                        // — la firma visual que identifica esto como un panel
                        // propio de la app — ahora horizontal, como en ese
                        // panel, porque el desplegable en sí es ancho y bajo
                        // (un menú normal), no angosto y alto.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(OPTIONS_MENU_PANEL_ACCENT_HEIGHT)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = OPTIONS_MENU_PANEL_CORNER_RADIUS,
                                        topEnd = OPTIONS_MENU_PANEL_CORNER_RADIUS
                                    )
                                )
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(BrandPurpleLight, BrandPurpleDeep)
                                    )
                                )
                        )
                        // Estado vacío — mismo lenguaje que el estado vacío
                        // de [FloatingWindowPresetsControl] (ícono atenuado +
                        // texto corto), en vez del espacio en blanco liso de
                        // la versión anterior: todavía no hay opciones
                        // reales para listar (el contenido de este menú —
                        // qué comandos van adentro, en qué secciones — lo
                        // define el usuario en un paso siguiente), pero
                        // mientras tanto se ve como un panel terminado, no
                        // como un rectángulo vacío sin explicación.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu_option),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Todavía no hay opciones disponibles",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// A PEDIDO EXPLÍCITO DEL USUARIO: el panel de presets tenía el MISMO
// color que la ventana principal (SurfaceTintedElevated) y prácticamente
// se perdía contra ella — sin sombra propia, sin borde, sin ninguna
// marca de identidad. Estas constantes son SU paleta propia, un
// escalón más oscuro que la ventana que lo contiene (mismo criterio que
// ya usa el resto de la app para "un panel flotando sobre otro" — ver
// SurfaceTintedDark vs. SurfaceTintedElevated en Theme.kt), más un
// acento de marca (la franja superior) para que se identifique de un
// vistazo como una ventana propia, no como una continuación de la de
// abajo.
private val PRESETS_PANEL_WIDTH = 224.dp
private val PRESETS_PANEL_CORNER_RADIUS = 14.dp
private val PRESETS_PANEL_ACCENT_HEIGHT = 3.dp

/**
 * Control "Presets" de la cabecera — a pedido explícito:
 * - Sin preset elegido: muestra la etiqueta genérica "Presets".
 * - Con un preset elegido (ver [FloatingToolWindow.selectedPresetIndex]):
 *   pasa a mostrar el NOMBRE de ese preset en su lugar.
 * - Tocarlo despliega el panel de presets de este módulo.
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO — el panel ahora tiene identidad visual
 * PROPIA en vez de mimetizarse con la ventana que lo contiene:
 * - Color propio, un escalón más oscuro que la ventana (`SurfaceTintedDark`
 *   vs. el `SurfaceTintedElevated` de la ventana — ver constantes de
 *   arriba), en vez de compartir exactamente el mismo tono.
 * - Borde sutil en el morado de marca ([BrandPurpleLight]) — la misma
 *   marca visual que ya usan otras superficies elevadas de la app (ver
 *   [FloatingWindowMinimizedBadge]) para separarse de lo que hay debajo.
 * - Franja de acento en la parte superior, en degradé de marca — la
 *   "firma" que identifica esto como el panel de Presets de un vistazo,
 *   sin necesidad de leer el título.
 * - Sombra propia, más pronunciada que la de la ventana (24.dp vs.
 *   16.dp) — reforzando que este panel "flota" POR ENCIMA de la
 *   ventana, no que es parte de ella.
 *
 * Técnicamente, se armó con [Popup] en vez de [DropdownMenu]: así el
 * panel es 100% nuestro (color, forma, sombra, franja de acento) sin
 * pelear contra el `Surface` interno por defecto que trae `DropdownMenu`
 * — y sigue sin recortarse contra los bordes redondeados de la ventana
 * (mismo motivo por el que `DropdownMenu` también usa un `Popup` por
 * debajo: un panel que puede sobresalir de una ventana angosta o
 * pequeña no puede vivir DENTRO del `Surface` que la recorta).
 */
@Composable
private fun FloatingWindowPresetsControl(
    presets: List<String>,
    selectedIndex: Int?,
    expanded: Boolean,
    onTogglePress: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val label = selectedIndex?.let { presets.getOrNull(it) } ?: "Presets"
    val hasSelection = selectedIndex != null && presets.getOrNull(selectedIndex) != null
    val density = LocalDensity.current
    // Alto real del botón "Presets" — se mide una sola vez con
    // `onGloballyPositioned` para poder ubicar el panel emergente
    // pegado justo debajo, sin un margen "a ojo" fijo que quede mal si
    // el texto del botón cambia de tamaño (p. ej. al pasar de "Presets"
    // a un nombre de preset más alto).
    var anchorHeightPx by remember { mutableStateOf(0) }
    val verticalGapPx = with(density) { 6.dp.roundToPx() }

    Box {
        Row(
            modifier = Modifier
                .onGloballyPositioned { coordinates -> anchorHeightPx = coordinates.size.height }
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePress
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .widthIn(max = 84.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = if (hasSelection) 0.92f else 0.45f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (hasSelection) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = anchorHeightPx + verticalGapPx),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .width(PRESETS_PANEL_WIDTH)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(PRESETS_PANEL_CORNER_RADIUS),
                            clip = false
                        ),
                    color = SurfaceTintedDark,
                    shape = RoundedCornerShape(PRESETS_PANEL_CORNER_RADIUS),
                    border = BorderStroke(1.dp, BrandPurpleLight.copy(alpha = 0.4f))
                ) {
                    Column {
                        // --- Franja de acento: la "firma" visual propia
                        // de este panel, en degradé de marca. Va PEGADA
                        // arriba, recortada a las esquinas redondeadas
                        // del panel (mismo `RoundedCornerShape` que el
                        // Surface que la contiene) para que no se vean
                        // picos cuadrados asomando por detrás del borde
                        // curvo.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PRESETS_PANEL_ACCENT_HEIGHT)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = PRESETS_PANEL_CORNER_RADIUS,
                                        topEnd = PRESETS_PANEL_CORNER_RADIUS
                                    )
                                )
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(BrandPurpleLight, BrandPurpleDeep)
                                    )
                                )
                        )
                        Text(
                            text = "PRESETS",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            // BUG REAL DE COMPILACIÓN corregido — esto es lo
                            // que rompió el build en GitHub Actions:
                            // `Modifier.padding(horizontal, top, bottom)`
                            // NO es ninguna de las sobrecargas válidas de
                            // `padding()` (solo existen: `all`, `horizontal`
                            // + `vertical`, `start/top/end/bottom` los
                            // cuatro juntos, o `PaddingValues`) — mezclar
                            // `horizontal` con `top`/`bottom` sueltos no
                            // resuelve a ninguna. Usando los cuatro lados
                            // explícitos (`start`/`top`/`end`/`bottom`) en
                            // vez de `horizontal` es lo que sí compila.
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp)
                        )
                        if (presets.isEmpty()) {
                            // Estado vacío — a pedido explícito ("esto por
                            // ahora"): el panel ya tiene su propia
                            // identidad visual y ya existe el lugar donde
                            // van a vivir los presets de este módulo, pero
                            // todavía no hay ninguno cargado.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_image_placeholder),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Todavía no hay presets guardados",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                presets.forEachIndexed { index, presetName ->
                                    val isSelected = index == selectedIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onSelect(index) }
                                            )
                                            .background(
                                                if (isSelected) BrandPurpleLight.copy(alpha = 0.16f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = presetName,
                                            color = if (isSelected) Color.White
                                                else Color.White.copy(alpha = 0.75f),
                                            fontWeight = if (isSelected) FontWeight.SemiBold
                                                else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ventana minimizada — a pedido explícito: al minimizar, la ventana
 * flotante entera se "recoge" a solo este ícono circular (mismo glifo
 * que ya usa la cabecera como identificador de la ventana, ver
 * [FloatingToolWindow] — no se dibuja ningún ícono nuevo), en la misma
 * posición donde estaba. Un solo toque la restaura tal cual — mismo
 * tamaño y lugar que tenía antes de minimizarla.
 *
 * A PEDIDO EXPLÍCITO DEL USUARIO — corregido: este ícono ahora es
 * arrastrable por TODA la pantalla, igual que la cabecera de la ventana
 * cuando está abierta (mismo mecanismo — `detectDragGestures` +
 * `change.consume()` — para que el gesto no se filtre hacia el canvas de
 * atrás). `onDrag` reporta cada delta hacia [FloatingToolWindow], que lo
 * suma al mismo `offsetPx` que ya posiciona la ventana completa, así que
 * restaurarla después deja la ventana exactamente donde quedó el ícono.
 *
 * BUG REAL corregido (reportado con captura, comparando la cabecera
 * contra el badge minimizado): este badge dibujaba SIEMPRE
 * `ic_move_both_axes` fijo, sin relación con qué ventana era —
 * Recolor, Color Básico y 3D Básico quedaban con el mismo ícono
 * genérico de "mover" apenas se minimizaban, aunque cada una ya tenía
 * su propio glifo distinto en la cabecera abierta ([titleIcon], pasado
 * por cada llamador — ver [FloatingToolWindow]). Ahora este badge
 * recibe ese mismo `titleIcon` y lo reutiliza tal cual, así el ícono
 * minimizado es siempre el mismo que el de la cabecera de esa ventana,
 * nunca el de otra.
 */
@Composable
private fun FloatingWindowMinimizedBadge(
    title: String,
    titleIcon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    onDrag: (Offset) -> Unit,
    // A PEDIDO EXPLÍCITO DEL USUARIO: la zona de "Eliminar" (ver
    // [FloatingWindowDeleteDropZone]) solo tiene que aparecer MIENTRAS
    // el dedo sigue abajo arrastrando este ícono — no antes, no
    // después. `detectDragGestures` ya distingue estos tres momentos
    // por separado (a diferencia del `onDrag` de arriba, que solo
    // reporta el DELTA de cada frame, sin avisar cuándo empieza o
    // termina el gesto completo) — `onDragCancel` cuenta como "terminó"
    // igual que soltar, para que la zona nunca quede pegada en pantalla
    // si el sistema cancela el gesto a mitad de camino (una llamada
    // entrante, un scroll del sistema, etc.).
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    // A PEDIDO EXPLÍCITO DEL USUARIO — BUG REAL corregido (reportado con
    // captura): "al ubicarse arriba [de la zona de Eliminar] todas las
    // bolas o íconos flotantes deben ponerse o pintarse rojo". Antes
    // SOLO la zona de destino reaccionaba (crecía + se ponía roja) — el
    // ícono que se está arrastrando se quedaba con su mismo color
    // morado de siempre, sin ninguna señal propia de "esto se va a
    // borrar". Es exactamente al revés de cómo se comporta cualquier
    // "arrastrar para eliminar" serio (el Dock de macOS, por ejemplo):
    // el elemento que ESTÁS SOSTENIENDO tiene que avisar por sí mismo,
    // no depender solo de mirar el blanco de abajo.
    //
    // `true` mientras este ícono en particular está sobrevolando la
    // zona (ver `isDraggingThisBadge && minimizedRegistry.isDragOverDeleteZone`
    // en [FloatingToolWindow] — a propósito la combinación de ambas, no
    // solo la segunda: esa es compartida entre las tres ventanas, así
    // que sin la primera CUALQUIER badge se pintaría de rojo apenas
    // OTRO empezara a sobrevolar la zona).
    isOverDeleteZone: Boolean = false
) {
    val deleteRed = Color(0xFFFF3B30)
    // Mismo criterio de color/duración que la propia zona de "Eliminar"
    // (ver [FloatingWindowDeleteDropZone]) — que ambos lados del gesto
    // "hablen el mismo idioma visual" en el momento de la confirmación.
    val badgeBackgroundColor by animateColorAsState(
        targetValue = if (isOverDeleteZone) deleteRed else SurfaceTintedElevated,
        animationSpec = tween(160),
        label = "minimizedBadgeDeleteBackground"
    )
    val badgeBorderColor by animateColorAsState(
        targetValue = if (isOverDeleteZone) Color.White.copy(alpha = 0.85f) else BrandPurpleLight.copy(alpha = 0.45f),
        animationSpec = tween(160),
        label = "minimizedBadgeDeleteBorder"
    )
    val badgeIconColor by animateColorAsState(
        targetValue = if (isOverDeleteZone) Color.White else Color.White.copy(alpha = 0.85f),
        animationSpec = tween(160),
        label = "minimizedBadgeDeleteIconTint"
    )
    Box(
        modifier = Modifier
            .size(FLOATING_WINDOW_MINIMIZED_BADGE_SIZE)
            .shadow(
                elevation = if (isOverDeleteZone) 18.dp else 10.dp,
                shape = CircleShape,
                ambientColor = if (isOverDeleteZone) deleteRed else Color.Black,
                spotColor = if (isOverDeleteZone) deleteRed else Color.Black
            )
            .clip(CircleShape)
            .background(badgeBackgroundColor)
            .border(if (isOverDeleteZone) 2.dp else 1.dp, badgeBorderColor, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = "Restaurar $title" },
        contentAlignment = Alignment.Center
    ) {
        titleIcon(badgeIconColor)
    }
}

// A PEDIDO EXPLÍCITO DEL USUARIO — tamaño de reposo de la zona de
// "Eliminar", más grande que un badge normal (ver
// FLOATING_WINDOW_MINIMIZED_BADGE_SIZE) a propósito: tiene que leerse
// como un blanco fácil de acertar al soltar, no como un botón chico más.
private val FLOATING_WINDOW_DELETE_ZONE_SIZE = 68.dp

/**
 * Zona de "Eliminar" — aparece flotando en la parte de ABAJO del
 * canvas, centrada, únicamente MIENTRAS se arrastra el ÍCONO YA
 * MINIMIZADO de cualquiera de las tres ventanas de efectos (Recolor /
 * Color Básico / 3D Básico) — nunca al arrastrar la ventana ABIERTA
 * desde su cabecera, a pedido explícito del usuario (ver
 * `isMinimizedBadgeDragging` en [FloatingWindowMinimizedRegistry], el
 * único interruptor que la muestra/oculta). Soltar el ícono encima de
 * esta zona borra ese efecto por completo — no solo lo minimiza ni lo
 * cierra — ver `onDeleteEffect` en [FloatingToolWindow].
 *
 * Estados visuales, de reposo a confirmación, pensados para leerse como
 * un gesto "premium" real (referencia: la papelera de macOS/iOS al
 * arrastrar un ícono del Dock) y no como un simple ícono estático:
 *
 * 1. REPOSO (apenas empieza el arrastre): A PEDIDO EXPLÍCITO DEL
 *    USUARIO — la zona entera "sube" desde abajo del canvas (no desde
 *    su propio centro): entra deslizándose verticalmente desde una
 *    posición inicial igual a su propia altura (`slideInVertically`,
 *    `initialOffsetY = { fullHeight -> fullHeight }`) MÁS un fundido y
 *    un "pop" de escala leve (entra un poco más chica que su tamaño
 *    final y se asienta, en vez de aparecer ya a tamaño completo — se
 *    siente más vivo, menos un simple `visible = true/false`). Mientras
 *    nadie la sobrevuela, respira sola con un pulso muy sutil de escala
 *    (`idlePulse`, infinito) — una invitación pasiva a "soltá acá", no
 *    una animación decorativa que grite.
 * 2. SOBREVOLADA (el ícono arrastrado entra en su rectángulo, ver
 *    `isDragOverDeleteZone`): el pulso de reposo se apaga (ver más
 *    abajo, `idleScale`) y la zona entera "salta" a una escala mayor
 *    con resorte (`spring`, no `tween` — un resorte se siente como una
 *    reacción física real al contacto, un tween se siente animado a
 *    propósito) + el anillo y el fondo se intensifican a rojo sólido +
 *    la sombra se agranda (más "elevación" = más foco). Junto con esto
 *    se dispara la vibración de confirmación (una sola vez al entrar,
 *    ver el `onDrag` del badge en [FloatingToolWindow]).
 * 3. SALIDA (se deja de arrastrar, con o sin soltar encima): el mismo
 *    viaje que la entrada pero al revés — se hunde de nuevo hacia abajo
 *    (`slideOutVertically`, mismo `targetOffsetY`) + fundido + reducción
 *    de escala, simétrico a como apareció. Que entre y salga por el
 *    mismo camino (subir/bajar, no aparecer/desaparecer en el lugar) es
 *    lo que la hace leerse como un objeto físico real entrando al
 *    cuadro, no como un ícono que un `if` prende y apaga.
 */
@Composable
internal fun BoxScope.FloatingWindowDeleteDropZone(
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    modifier: Modifier = Modifier
) {
    val isDragging = minimizedRegistry.isMinimizedBadgeDragging
    val isHovering = minimizedRegistry.isDragOverDeleteZone

    AnimatedVisibility(
        visible = isDragging,
        // A PEDIDO EXPLÍCITO DEL USUARIO: entra/sale deslizándose desde
        // ABAJO del canvas (no un fundido+escala que "aparece" en el
        // lugar) — `initialOffsetY`/`targetOffsetY` reciben la altura YA
        // MEDIDA de este propio composable (`fullHeight`, en píxeles) y
        // devuelven esa misma altura completa como desplazamiento
        // inicial/final, así el punto de partida real es "un cuerpo
        // entero por debajo de su posición de reposo" sin necesidad de
        // adivinar ningún valor fijo en dp. El fundido + "pop" de escala
        // quedan encima del slide (no lo reemplazan) para que la llegada
        // se sienta amortiguada, no un golpe seco al llegar a destino.
        enter = slideInVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> fullHeight }
        ) + fadeIn(animationSpec = tween(200)) +
            scaleIn(initialScale = 0.82f, animationSpec = tween(260, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            targetOffsetY = { fullHeight -> fullHeight }
        ) + fadeOut(animationSpec = tween(150)) +
            scaleOut(targetScale = 0.82f, animationSpec = tween(150, easing = FastOutSlowInEasing)),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 28.dp)
            // Publica el rectángulo REAL de esta zona (posición + tamaño,
            // en el MISMO sistema de coordenadas que `offsetPx` de cada
            // badge — ambos son hijos directos del mismo Box exterior,
            // ver `floatingWindowAreaSizePx` donde se mide ese Box) para
            // que [FloatingToolWindow] pueda comparar la posición del
            // ícono arrastrado contra ella en cada frame.
            .onGloballyPositioned { coordinates ->
                minimizedRegistry.deleteZoneRect = Rect(
                    coordinates.positionInParent(),
                    Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                )
            }
    ) {
        // Pulso de reposo, infinito y sutil — se congela apenas
        // `isHovering` pasa a true (ver `finalScale` más abajo) para no
        // competir contra el "salto" de escala de la confirmación.
        val infiniteTransition = rememberInfiniteTransition(label = "deleteZoneIdlePulse")
        val idlePulse by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(780, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "deleteZoneIdlePulseValue"
        )
        val hoverScale by animateFloatAsState(
            targetValue = if (isHovering) 1.22f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "deleteZoneHoverScale"
        )
        val finalScale = if (isHovering) hoverScale else hoverScale * idlePulse

        val deleteRed = Color(0xFFFF3B30)
        val ringColor by animateColorAsState(
            targetValue = if (isHovering) deleteRed else deleteRed.copy(alpha = 0.5f),
            animationSpec = tween(160),
            label = "deleteZoneRingColor"
        )
        val backgroundColor by animateColorAsState(
            targetValue = if (isHovering) deleteRed.copy(alpha = 0.28f) else SurfaceTintedElevated,
            animationSpec = tween(160),
            label = "deleteZoneBackgroundColor"
        )
        val shadowElevation by animateFloatAsState(
            targetValue = if (isHovering) 24f else 10f,
            animationSpec = tween(160),
            label = "deleteZoneShadowElevation"
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = finalScale
                    scaleY = finalScale
                }
                .size(FLOATING_WINDOW_DELETE_ZONE_SIZE)
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = CircleShape,
                    ambientColor = deleteRed,
                    spotColor = deleteRed
                )
                .clip(CircleShape)
                .background(backgroundColor)
                .border(2.dp, ringColor, CircleShape)
                .semantics { contentDescription = "Soltar acá para eliminar el efecto" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete_effect_trash),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
