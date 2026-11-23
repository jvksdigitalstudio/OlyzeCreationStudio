package com.yeivikas.olyzecs.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contraste automático de los íconos del panel de opciones de una fila (capa o pista de audio).
 *
 * El panel toma el color de identidad de la fila, que el usuario elige libremente: sobre un cian o
 * un amarillo claro los íconos blancos y el rojo de "Eliminar" se perdían, y sobre un rosa/magenta
 * el rojo desaparecía. Esto NO toca el color del panel ni de la capa: calcula, a partir del fondo
 * real, con qué color debe dibujarse cada ícono para que siempre se lea.
 *
 * Regla (WCAG 2.x, relación de contraste de la luminancia relativa):
 *  1. Color base de los íconos ([RowIconPalette.content]): blanco mientras alcance [ICON_MIN_CONTRAST]
 *     (3:1, mínimo de WCAG 1.4.11 para componentes de interfaz) contra TODOS los muestreos del
 *     fondo; si no, el tinte oscuro [RowIconInk], si lo supera. Como una de las dos opciones
 *     siempre da ≥ 4,2:1 sobre cualquier color (el cruce blanco/tinte cae ahí porque el tinte no es
 *     negro puro), un ícono base nunca baja de [ICON_MIN_CONTRAST].
 *  2. Eliminar: conserva su rojo mientras logre [GLYPH_MIN_CONTRAST] (4,5:1, el nivel de texto de
 *     WCAG: un trazo de 15 dp es fino y 3:1 queda al límite); se aclara u oscurece lo mínimo
 *     ([ensureContrast]) y, si ningún rojo llega (fondos rojos o rosas de tono medio), usa el color
 *     base: legible antes que tono sobre tono.
 *  3. Candados ACTIVOS: patrón de "interruptor encendido". El ícono va sobre un chip relleno con el
 *     color del estado ([RowIconPalette.lockOn]/[RowIconPalette.orderLockOn], con [ICON_MIN_CONTRAST]
 *     contra el panel) y su trazo toma blanco o negro puro ([RowIconGlyphDark]) según lo que mejor
 *     lea sobre el chip ([RowIconPalette.onLockOn]/[RowIconPalette.onOrderLockOn]), lo que garantiza
 *     [GLYPH_MIN_CONTRAST] sobre cualquier chip. El estado se distingue por la
 *     FORMA (chip lleno vs. ícono liso), no solo por el tono, así que se ve igual sobre cualquier
 *     color de capa.
 *  4. Degradados: se evalúan sus extremos y su punto medio; el color elegido debe leerse en todos.
 */
internal const val ICON_MIN_CONTRAST = 3f

/** Contraste del trazo de un ícono con significado propio (nivel de texto de WCAG). */
internal const val GLYPH_MIN_CONTRAST = 4.5f

/** Tinte oscuro de los íconos sobre fondos claros (el morado casi negro de la marca). */
internal val RowIconInk = Color(0xFF1A1233)
private val RowIconLight = Color.White

/**
 * Trazo oscuro de los íconos sobre un chip relleno. Es negro puro a propósito: entre blanco y negro el
 * peor contraste posible es ≈4,58:1 (cruce de luminancias), así que siempre se cumple
 * [GLYPH_MIN_CONTRAST]. El tinte de marca [RowIconInk] no sirve aquí: no es negro y su peor caso
 * (≈4,2:1) queda por debajo de 4,5 sobre chips de tono medio.
 */
private val RowIconGlyphDark = Color.Black

private const val SEARCH_STEPS = 20

/** Luminancia relativa WCAG de un color opaco (0 = negro, 1 = blanco). */
internal fun relativeLuminance(color: Color): Float {
    fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
}

/** Relación de contraste WCAG entre dos colores opacos, de 1 (iguales) a 21 (blanco/negro). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** El peor contraste de [foreground] contra cualquiera de los [backgrounds]. */
internal fun worstContrast(foreground: Color, backgrounds: List<Color>): Float =
    backgrounds.minOf { contrastRatio(foreground, it) }

/**
 * Devuelve [foreground] tal cual si ya contrasta [minRatio] contra todos los [backgrounds]; si no,
 * el color más cercano en la misma gama (mismo matiz, aclarado hacia blanco u oscurecido hacia
 * [RowIconInk]) que sí lo logra. Se queda con la dirección que exige menos cambio. Si ninguna
 * dirección lo alcanza (fondos de tono medio o varios fondos opuestos), cae al color base de los
 * íconos ([baseContent]): legible antes que conservar el matiz.
 */
internal fun ensureContrast(
    foreground: Color,
    backgrounds: List<Color>,
    minRatio: Float = ICON_MIN_CONTRAST
): Color {
    if (worstContrast(foreground, backgrounds) >= minRatio) return foreground
    val towardLight = firstReaching(foreground, RowIconLight, backgrounds, minRatio)
    val towardInk = firstReaching(foreground, RowIconInk, backgrounds, minRatio)
    return when {
        towardLight != null && towardInk != null -> if (towardLight.step < towardInk.step) towardLight.color else towardInk.color
        towardLight != null -> towardLight.color
        towardInk != null -> towardInk.color
        else -> baseContent(backgrounds)
    }
}

private class Reached(val color: Color, val step: Int)

private fun firstReaching(from: Color, target: Color, backgrounds: List<Color>, minRatio: Float): Reached? {
    for (step in 1..SEARCH_STEPS) {
        val candidate = lerp(from, target, step.toFloat() / SEARCH_STEPS)
        if (worstContrast(candidate, backgrounds) >= minRatio) return Reached(candidate, step)
    }
    return null
}

/** Blanco mientras alcance [ICON_MIN_CONTRAST] en el peor muestreo; si no, el mejor entre blanco y [RowIconInk]. */
private fun baseContent(backgrounds: List<Color>): Color {
    val light = worstContrast(RowIconLight, backgrounds)
    return if (light >= ICON_MIN_CONTRAST || light >= worstContrast(RowIconInk, backgrounds)) RowIconLight else RowIconInk
}

/** Qué color lleva un ícono del panel: el base, o uno con significado propio. */
internal enum class ActionTone {
    /** Ícono común (renombrar, candado abierto, flechas…): el color base de la paleta. */
    Content,

    /** Acción destructiva (eliminar). */
    Danger,

    /** Candado de canvas activo: trazo sobre chip relleno. */
    LockOn,

    /** Candado de orden activo: trazo sobre chip relleno. */
    OrderLockOn,

    /** Respeta los colores propios del dibujo (paleta, ojo tachado); sobre fondo claro se invierte, ver [RowIconPalette.invertOriginalTones]. */
    Original
}

/** Colores de los íconos de UN panel, calculados contra su fondo real. */
@Immutable
internal class RowIconPalette(
    val content: Color,
    val danger: Color,
    /** Relleno del chip del candado de canvas activo. */
    val lockOn: Color,
    /** Relleno del chip del candado de orden activo. */
    val orderLockOn: Color
) {
    /** Trazo del candado de canvas activo, sobre su chip [lockOn]. */
    val onLockOn: Color = readableOn(lockOn)

    /** Trazo del candado de orden activo, sobre su chip [orderLockOn]. */
    val onOrderLockOn: Color = readableOn(orderLockOn)

    /**
     * true cuando el base es el tinte oscuro (fondo claro): los dibujos con colores propios
     * ([ActionTone.Original]) están hechos en blanco con detalles casi negros y se verían blancos
     * sobre claro, así que se les invierten los tonos (blanco → tinte oscuro, oscuro → claro).
     */
    val invertOriginalTones: Boolean get() = content == RowIconInk

    /** Relleno del chip del ícono, o null si [tone] se dibuja sin chip. */
    fun chipFor(tone: ActionTone): Color? = when (tone) {
        ActionTone.LockOn -> lockOn
        ActionTone.OrderLockOn -> orderLockOn
        else -> null
    }

    /** Color del trazo; [Color.Unspecified] para [ActionTone.Original] (no se tiñe, ver [invertOriginalTones]). */
    fun colorFor(tone: ActionTone): Color = when (tone) {
        ActionTone.Content -> content
        ActionTone.Danger -> danger
        ActionTone.LockOn -> onLockOn
        ActionTone.OrderLockOn -> onOrderLockOn
        ActionTone.Original -> Color.Unspecified
    }
}

/** Blanco o [RowIconGlyphDark], el que mejor se lea sobre [chip] (siempre ≥ 4,58:1). */
private fun readableOn(chip: Color): Color =
    if (contrastRatio(RowIconLight, chip) >= contrastRatio(RowIconGlyphDark, chip)) RowIconLight else RowIconGlyphDark

private val DANGER_BASE = Color(0xFFFF6B6B)
private val LOCK_ON_BASE = Color(0xFFFFC107)
private val ORDER_LOCK_ON_BASE = Color(0xFF4FC3F7)

/**
 * Paleta para un panel cuyo fondo se ve como [backgrounds] (opacos, ya compuestos sobre lo que haya
 * detrás): un muestreo por extremo del degradado y por cada variante del color que se dibuja.
 */
internal fun rowIconPalette(backgrounds: List<Color>): RowIconPalette {
    require(backgrounds.isNotEmpty()) { "Hace falta al menos un muestreo del fondo" }
    return RowIconPalette(
        content = baseContent(backgrounds),
        danger = ensureContrast(DANGER_BASE, backgrounds, GLYPH_MIN_CONTRAST),
        lockOn = ensureContrast(LOCK_ON_BASE, backgrounds),
        orderLockOn = ensureContrast(ORDER_LOCK_ON_BASE, backgrounds)
    )
}
