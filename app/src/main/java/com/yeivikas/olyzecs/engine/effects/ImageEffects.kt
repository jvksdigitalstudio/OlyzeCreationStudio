package com.yeivikas.olyzecs.engine.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Parámetros de la pestaña "Efectos" del modo "Editando imagen" (ver
 * EditorScreen.EditImageToolsHeader / EffectsPanel): vive al lado de
 * "Recolor" y "3D", con nivel de control "premium" — no solo intensidad
 * de la sombra, sino también su suavizado, distancia y ángulo, como en
 * cualquier herramienta de edición profesional (estilo "Drop Shadow" de
 * Photoshop/Affinity).
 *
 * Todos los campos están normalizados 0..1 (o 0..360 para el ángulo) para
 * que la UI trabaje siempre con sliders "de porcentaje" simples, y sea
 * [ImageEffects] quien traduce eso a píxeles/unidades reales según el
 * tamaño de cada imagen — así el mismo ajuste se ve proporcional tanto en
 * una capa chica como en una grande.
 */
data class ImageEffectsParams(
    // BUG REAL corregido acá (reporte del usuario: "difuminar... está al
    // revés, afecta a la imagen y su sombra"): `blur` YA NO difumina el
    // sujeto entero — difumina el FONDO alrededor de él (estilo "retrato"/
    // profundidad de campo de una cámara real), protegiendo por completo
    // el sujeto y dejando su sombra (que ya se genera aparte, ver
    // [compositeWithEffects]) totalmente intacta. Ver [applyBackgroundBlur].
    val blur: Float = 0f,             // 0..1 — difuminado del FONDO (protege sujeto y sombra)
    val edgeFeather: Float = 0f,      // 0..1 — suaviza la transición sujeto/fondo del difuminado de fondo
    val sharpen: Float = 0f,          // 0..1 — nitidez del sujeto (realce de bordes/detalle, "unsharp mask")
    val saturation: Float = 1f,       // 0..2 — 0 = blanco y negro, 1 = original, 2 = saturación máxima
    val brightness: Float = 0f,       // -1..1 — brillo aditivo por canal RGB (-100%..+100%)
    val contrast: Float = 1f,         // 0..2 — 1 = neutro, fórmula estándar (color-128)*contrast+128
    val hue: Float = 0f,              // 0..360 — rotación de matiz sobre TODO el sujeto (0 = sin cambio)
    val outlineIntensity: Float = 0f, // 0..1 — grosor/intensidad del contorno (0 = sin contorno)
    val outlineColor: Int = Color.WHITE,
    val glowIntensity: Float = 0f,    // 0..1 — opacidad del resplandor (0 = sin resplandor)
    val glowBlur: Float = 0.5f,       // 0..1 — difuminado propio del resplandor (radio)
    val glowColor: Int = Color.WHITE,
    val shadowIntensity: Float = 0f,  // 0..1 — opacidad de la sombra proyectada (0 = sin sombra)
    val shadowBlur: Float = 0.5f,     // 0..1 — suavizado/difuminado propio de la sombra
    // "Spread"/Expansión — el control "Spread" clásico de Photoshop:
    // agranda la silueta de la sombra ANTES de difuminarla (dilatación
    // dura) y, a la vez, reduce proporcionalmente el radio de difuminado
    // final — el resultado es una sombra con un núcleo más grande y
    // sólido y un borde más ajustado/definido, en vez de simplemente
    // "más grande y más difusa" (que es lo único que shadowBlur/
    // shadowDistance ya lograban). En 0 el comportamiento es
    // exactamente el de antes (sin cambios).
    val shadowSpread: Float = 0f,     // 0..1 — expansión del núcleo de la sombra antes del difuminado
    // Escala independiente de la sombra proyectada — en una sombra real
    // de piso, el tamaño de la sombra casi nunca es 1:1 con el objeto
    // (varía con la altura de la fuente de luz y el ángulo), así que
    // fijarla siempre al mismo tamaño del sujeto es la limitación más
    // notoria de cualquier dropshadow "básico". Pivotea desde el punto
    // de apoyo (el pie del sujeto, ver [compositeWithEffects]), nunca
    // desde el centro, para que escalar no "levante" la sombra del piso.
    val shadowScale: Float = 1f,      // 0.4..2 — 1 = mismo tamaño que el sujeto (comportamiento clásico)
    // Ruido/grano sobre la silueta de la sombra — una sombra 100% lisa
    // se lee como "digital"/vectorial; una pizca de ruido de grano fino
    // rompe esa uniformidad perfecta y ayuda a que se sienta proyectada
    // sobre una superficie real (concreto, tela, tierra), en vez de
    // flotar como una silueta perfecta. Se aplica sobre el ALFA de la
    // silueta antes del difuminado (ver [applyShadowGrain]), así el
    // propio difuminado lo suaviza a una textura sutil en vez de ruido
    // "crudo" de píxel a píxel.
    val shadowNoise: Float = 0f,      // 0..1 — cantidad de grano (0 = sombra lisa, comportamiento clásico)
    val shadowDistance: Float = 0.35f,// 0..1 — qué tan lejos se proyecta la sombra
    val shadowAngleDeg: Float = 135f, // 0..360 — dirección de la sombra (135° = clásica abajo-derecha)
    val shadowColor: Int = Color.BLACK, // color ARGB de la silueta de la sombra (antes fijo en negro)
    val shadowSkewDegrees: Float = 0f,     // -45..45 — inclinación/perspectiva de la sombra proyectada (look "piso en ángulo", 0 = sombra recta)
    val shadowFadeByDistance: Float = 0f,  // 0..1 — cuánto se desvanece la sombra en su extremo más lejano del punto de apoyo (0 = opacidad pareja, comportamiento clásico)
    // Modo de mezcla MULTIPLICAR entre toda la "familia de sombras"
    // (proyectada + relleno + contacto) — true por defecto porque es el
    // criterio profesional estándar: dos sombras que se superponen
    // deben OSCURECERSE naturalmente entre sí (como luz bloqueada dos
    // veces), no mezclarse por transparencia normal (que da un gris
    // "sucio"/lechoso donde se tocan). Ver el comentario grande en
    // [blitBlend] para el porqué de la implementación manual píxel a
    // píxel en vez del Xfermode nativo de Android.
    val shadowBlendMultiply: Boolean = true,
    // --- Sombra de RELLENO: una segunda sombra, más suave y corta, en
    // un ángulo distinto a la proyectada principal — representa la luz
    // rebotada/ambiente que en una escena real SIEMPRE ilumina algo el
    // lado "de sombra" del sujeto, evitando que se vea como una silueta
    // con una sola sombra dura y plana. Mismo recurso de "luz de
    // relleno" (fill light) que cualquier set de fotografía o render 3D
    // usa junto a la luz clave. No tiene inclinación ni desvanecimiento
    // propios (esos quedan como el toque extra exclusivo de la sombra
    // principal) para no duplicar controles — ya es, en sí misma, el
    // control "extra" sobre la sombra base.
    val fillShadowIntensity: Float = 0f,   // 0..1 — opacidad de la sombra de relleno (0 = sin ella)
    val fillShadowBlur: Float = 0.7f,      // 0..1 — difuminado propio (más alto que el de la principal por defecto: la luz rebotada es más difusa)
    val fillShadowDistance: Float = 0.2f,  // 0..1 — qué tan lejos se proyecta (más corta que la principal por defecto)
    val fillShadowAngleDeg: Float = 315f,  // 0..360 — dirección (315° por defecto: aprox. opuesta a la principal en 135°, como rebote de luz)
    val fillShadowColor: Int = Color.rgb(30, 34, 48), // color propio, independiente de shadowColor (por defecto un gris-azulado frío, típico de luz ambiente/rebotada)
    // Escala independiente de la sombra de relleno — mismo criterio que
    // [shadowScale]: pivotea desde el punto de apoyo del sujeto, nunca
    // desde el centro.
    val fillShadowScale: Float = 1f,       // 0.4..2 — 1 = mismo tamaño que el sujeto
    val reflectionIntensity: Float = 0f, // 0..1 — opacidad del reflejo (copia volteada, degradada hacia abajo)
    val reflectionGap: Float = 0f,      // 0..1 — separación entre el pie del sujeto y su reflejo (0 = pegado, estilo clásico)
    val reflectionLength: Float = 1f,   // 0.1..1 — qué porción del alto del sujeto llega a reflejarse (1 = reflejo completo, <1 = reflejo corto que se corta antes de desvanecer del todo)
    val reflectionBlur: Float = 0f,     // 0..1 — difuminado propio del reflejo (look "piso pulido"/vidrio esmerilado), independiente del resto
    val reflectionSkewDegrees: Float = 0f, // -45..45 — inclinación horizontal del reflejo (look "piso en perspectiva", 0 = reflejo recto)
    val reflectionTintIntensity: Float = 0f, // 0..1 — cuánto del color del reflejo se reemplaza por [reflectionTintColor] (look "reflejo en agua/vidrio de color")
    val reflectionTintColor: Int = Color.rgb(58, 110, 150), // color del tinte del reflejo (solo aplica si reflectionTintIntensity > 0)
    val reflectionEdgeFade: Float = 0f, // 0..1 — desvanece los bordes izquierdo/derecho del reflejo (evita el look "recortado" al ras de los costados)
    val reflectionRippleIntensity: Float = 0f, // 0..1 — amplitud de la ondulación tipo "agua" del reflejo (0 = espejo perfectamente plano)
    val reflectionRippleScale: Float = 0.5f,   // 0..1 — densidad de las ondas (0 = pocas y anchas, 1 = muchas y finas)
    // Curva de opacidad del reflejo — antes era un único degradado
    // LINEAL fijo (100%→0% parejo); con esto se puede elegir una caída
    // más lenta al principio y brusca al final, o al revés, en vez de
    // una única pendiente recta — el mismo control de curva de
    // opacidad que ya se sumó a la sombra de contacto
    // ([contactShadowFalloff]), aplicado ahora al reflejo. 0.5 (default)
    // reproduce EXACTAMENTE el degradado lineal de siempre.
    val reflectionOpacityCurve: Float = 0.5f, // 0..1 — 0 = caída lenta al inicio/brusca al final, 0.5 = lineal (clásico), 1 = caída brusca al inicio/lenta al final
    // Perspectiva/compresión del reflejo — un reflejo real sobre un piso
    // visto en ángulo casi nunca tiene la misma altura 1:1 que el
    // sujeto: se ve "aplastado" verticalmente por la perspectiva. Se
    // aplica como una escala vertical pura pivotando desde la fila
    // pegada al pie del sujeto (nunca cambia el ancho — para eso ya
    // existe [reflectionSkewDegrees]). En 1 (default) no hay cambio,
    // comportamiento idéntico al de siempre.
    val reflectionPerspective: Float = 1f, // 0.3..1.5 — 1 = reflejo sin comprimir/estirar (clásico)
    // --- Sombra de CONTACTO: mancha corta y suave pegada exactamente al
    // punto de apoyo del sujeto, independiente de la sombra proyectada
    // larga de arriba (shadowIntensity/Distance/Angle) — el mismo
    // recurso "doble sombra" que usa cualquier motor de render 3D o
    // compositing profesional para que el sujeto se sienta "anclado" al
    // piso, además de proyectar sombra lejos. No tiene ángulo ni
    // distancia propios porque, a diferencia de la sombra proyectada,
    // SIEMPRE va justo debajo del sujeto — solo varía su tamaño e
    // intensidad. Reutiliza [shadowColor] a propósito (misma fuente de
    // luz = mismo color de sombra en ambas).
    val contactShadowIntensity: Float = 0f, // 0..1 — opacidad de la sombra de contacto (0 = sin ella)
    val contactShadowSize: Float = 0.5f,    // 0.1..1 — ancho de la mancha, relativo al ancho del sujeto
    val contactShadowBlur: Float = 0.4f,    // 0..1 — difuminado extra sobre el degradado radial base
    // Color propio de la sombra de contacto — antes reutilizaba
    // [shadowColor] a la fuerza ("misma fuente de luz"); en compositing
    // profesional real la mancha de contacto casi siempre queda MÁS
    // oscura/densa que la sombra proyectada larga (la luz está más
    // bloqueada justo en el punto de apoyo), así que necesita su propio
    // control independiente para lograr ese matiz sin arrastrar el color
    // de la otra sombra.
    val contactShadowColor: Int = Color.BLACK,
    // Curva de caída (falloff) del degradado radial — antes era un
    // degradado lineal fijo (0% en el centro → 100% transparente en el
    // borde); con esto se puede elegir entre una mancha de núcleo denso
    // que corta rápido (falloff alto, look "contacto duro") o una que se
    // desvanece de forma más gradual y extendida (falloff bajo, look
    // "apoyo suave") — el mismo control de curva de opacidad que trae
    // cualquier herramienta de sombra de contacto/AO de un motor de
    // render profesional, en vez de un único degradado lineal para todos
    // los casos.
    val contactShadowFalloff: Float = 0.5f, // 0..1 — 0 = caída gradual y extendida, 1 = caída rápida y concentrada
    // --- LIGHT WRAP: envoltura de luz alrededor del borde del sujeto —
    // el recurso que hace que un recorte se sienta "integrado" a su
    // entorno en vez de una pegatina plana encima de él, usado en
    // prácticamente todo compositing profesional de cine/VFX. Como en
    // esta etapa del pipeline el sujeto todavía no tiene un fondo final
    // detrás (es una capa recortada con alfa transparente, el fondo
    // definitivo se decide en otra parte de la app), no hay un color de
    // "escena real" para tomar prestado — así que en vez de simularlo
    // mal, se usa un color elegido a propósito ([lightWrapColor],
    // pensado como la luz ambiente/fondo que va a rodear al sujeto) y se
    // filtra ÚNICAMENTE hacia el anillo interior del borde, nunca hacia
    // afuera de la silueta. Ver [applyLightWrap].
    val lightWrapIntensity: Float = 0f, // 0..1 — cuánto se mezcla el color de envoltura en el borde (0 = sin envoltura)
    val lightWrapColor: Int = Color.rgb(255, 244, 214), // color de la luz que "envuelve" el borde (default: luz cálida neutra)
    val lightWrapWidth: Float = 0.4f    // 0..1 — qué tan ancho hacia adentro llega el anillo de envoltura
) {
    /** true si ningún control se movió del neutro — evita reprocesar de más. */
    val isNeutral: Boolean
        get() = blur <= 0.001f &&
            sharpen <= 0.001f &&
            abs(saturation - 1f) <= 0.001f &&
            abs(brightness) <= 0.001f &&
            abs(contrast - 1f) <= 0.001f &&
            (((hue % 360f) + 360f) % 360f) <= 0.001f &&
            outlineIntensity <= 0.001f &&
            glowIntensity <= 0.001f &&
            shadowIntensity <= 0.001f &&
            fillShadowIntensity <= 0.001f &&
            reflectionIntensity <= 0.001f &&
            contactShadowIntensity <= 0.001f &&
            lightWrapIntensity <= 0.001f

    fun sanitized(): ImageEffectsParams = copy(
        blur = blur.coerceIn(0f, 1f),
        edgeFeather = edgeFeather.coerceIn(0f, 1f),
        sharpen = sharpen.coerceIn(0f, 1f),
        saturation = saturation.coerceIn(0f, 2f),
        brightness = brightness.coerceIn(-1f, 1f),
        contrast = contrast.coerceIn(0f, 2f),
        hue = ((hue % 360f) + 360f) % 360f,
        outlineIntensity = outlineIntensity.coerceIn(0f, 1f),
        glowIntensity = glowIntensity.coerceIn(0f, 1f),
        glowBlur = glowBlur.coerceIn(0f, 1f),
        shadowIntensity = shadowIntensity.coerceIn(0f, 1f),
        shadowBlur = shadowBlur.coerceIn(0f, 1f),
        shadowSpread = shadowSpread.coerceIn(0f, 1f),
        shadowScale = shadowScale.coerceIn(0.4f, 2f),
        shadowNoise = shadowNoise.coerceIn(0f, 1f),
        shadowDistance = shadowDistance.coerceIn(0f, 1f),
        shadowAngleDeg = ((shadowAngleDeg % 360f) + 360f) % 360f,
        shadowSkewDegrees = shadowSkewDegrees.coerceIn(-45f, 45f),
        shadowFadeByDistance = shadowFadeByDistance.coerceIn(0f, 1f),
        fillShadowIntensity = fillShadowIntensity.coerceIn(0f, 1f),
        fillShadowBlur = fillShadowBlur.coerceIn(0f, 1f),
        fillShadowDistance = fillShadowDistance.coerceIn(0f, 1f),
        fillShadowAngleDeg = ((fillShadowAngleDeg % 360f) + 360f) % 360f,
        fillShadowScale = fillShadowScale.coerceIn(0.4f, 2f),
        reflectionIntensity = reflectionIntensity.coerceIn(0f, 1f),
        reflectionGap = reflectionGap.coerceIn(0f, 1f),
        reflectionLength = reflectionLength.coerceIn(0.1f, 1f),
        reflectionBlur = reflectionBlur.coerceIn(0f, 1f),
        reflectionSkewDegrees = reflectionSkewDegrees.coerceIn(-45f, 45f),
        reflectionTintIntensity = reflectionTintIntensity.coerceIn(0f, 1f),
        reflectionEdgeFade = reflectionEdgeFade.coerceIn(0f, 1f),
        reflectionRippleIntensity = reflectionRippleIntensity.coerceIn(0f, 1f),
        reflectionRippleScale = reflectionRippleScale.coerceIn(0f, 1f),
        reflectionOpacityCurve = reflectionOpacityCurve.coerceIn(0f, 1f),
        reflectionPerspective = reflectionPerspective.coerceIn(0.3f, 1.5f),
        contactShadowIntensity = contactShadowIntensity.coerceIn(0f, 1f),
        contactShadowSize = contactShadowSize.coerceIn(0.1f, 1f),
        contactShadowBlur = contactShadowBlur.coerceIn(0f, 1f),
        contactShadowFalloff = contactShadowFalloff.coerceIn(0f, 1f),
        lightWrapIntensity = lightWrapIntensity.coerceIn(0f, 1f),
        lightWrapWidth = lightWrapWidth.coerceIn(0f, 1f)
    )
}

/**
 * Pipeline de efectos "en bitmap" (CPU) para la pestaña "Efectos": a
 * diferencia de [LookSettings] (que es un grading en tiempo real dentro
 * del shader GL, ver ShaderProgram), estos efectos se hornean sobre una
 * copia de la imagen — mismo enfoque que ya usan `ColorExtraction.recolor`
 * (pestaña "Recolor") y `Extrude3D.render` (pestaña "3D"): se calcula
 * sobre una copia chica para la vista previa en vivo y sobre la copia
 * grande para el guardado final con debounce (ver EffectsPanel en
 * EditorScreen.kt). Se eligió CPU en vez de shader acá a propósito: la
 * sombra proyectada necesita AGRANDAR el lienzo (el bitmap resultante es
 * más grande que el original, con margen alrededor para que la sombra no
 * quede recortada) — igual que ya hace Extrude3D con su cuerpo extruido —
 * algo que un uniform de shader sobre el mismo tamaño de textura no puede
 * hacer sin cambiar toda la tubería de capas.
 */
object ImageEffects {

    /**
     * Aplica [params] sobre [source] y devuelve un bitmap nuevo (no muta
     * [source]). Orden del pipeline (igual criterio que cualquier editor
     * profesional: primero se corrige el color del sujeto en sí, después
     * se realza detalle, después se trabaja el fondo, y por último se
     * arman las capas compuestas alrededor del sujeto de atrás hacia
     * adelante): Brillo/Contraste → Saturación → Tono → Nitidez →
     * Difuminar fondo → (Contorno + Resplandor + Sombra, compuestas
     * detrás del sujeto, + Reflejo, compuesto debajo de todo el
     * conjunto).
     */
    fun apply(source: Bitmap, rawParams: ImageEffectsParams): Bitmap {
        val params = rawParams.sanitized()
        if (params.isNeutral) return source.copy(Bitmap.Config.ARGB_8888, false)

        val colorAdjusted = if (abs(params.brightness) > 0.001f || abs(params.contrast - 1f) > 0.001f) {
            applyBrightnessContrast(source, params.brightness, params.contrast)
        } else {
            source
        }

        val saturated = if (abs(params.saturation - 1f) > 0.001f) {
            applySaturation(colorAdjusted, params.saturation)
        } else {
            colorAdjusted
        }
        if (saturated !== colorAdjusted && colorAdjusted !== source) colorAdjusted.recycle()

        val hued = if (params.hue > 0.001f) {
            applyHueRotation(saturated, params.hue)
        } else {
            saturated
        }
        if (hued !== saturated && saturated !== source) saturated.recycle()

        val sharpened = if (params.sharpen > 0.001f) {
            applySharpen(hued, params.sharpen)
        } else {
            hued
        }
        if (sharpened !== hued && hued !== source) hued.recycle()

        // "Difuminar" (fondo): usa el alfa del ORIGINAL como máscara de
        // protección — el sujeto (alfa alto) sale intacto de acá, solo se
        // difumina alrededor de él. Ver comentario grande en
        // [applyBackgroundBlur] para el porqué completo.
        val foreground = if (params.blur > 0.001f) {
            applyBackgroundBlur(source, sharpened, params.blur, params.edgeFeather)
        } else {
            sharpened
        }
        if (foreground !== sharpened && sharpened !== source) sharpened.recycle()

        // Light wrap: se aplica sobre el sujeto YA terminado (color,
        // nitidez y difuminado de fondo horneados), como último retoque
        // al sujeto en sí antes de armar las capas compuestas alrededor
        // — mismo criterio que el resto del pipeline: cada paso trabaja
        // sobre el resultado del anterior, de adentro (color) hacia
        // afuera (composición).
        val wrapped = if (params.lightWrapIntensity > 0.001f) {
            applyLightWrap(foreground, params.lightWrapIntensity, params.lightWrapColor, params.lightWrapWidth)
        } else {
            foreground
        }
        if (wrapped !== foreground && foreground !== source) foreground.recycle()

        val needsComposite = params.shadowIntensity > 0.001f ||
            params.fillShadowIntensity > 0.001f ||
            params.glowIntensity > 0.001f ||
            params.outlineIntensity > 0.001f ||
            params.reflectionIntensity > 0.001f ||
            params.contactShadowIntensity > 0.001f
        if (!needsComposite) {
            return if (wrapped === source) source.copy(Bitmap.Config.ARGB_8888, false) else wrapped
        }

        // Contorno, resplandor, sombra y reflejo se calculan SIEMPRE a
        // partir del alfa/píxeles del [source] ORIGINAL o de [wrapped]
        // ya terminado (nunca reprocesando el resultado de OTRO de estos
        // cuatro) — así quedan totalmente independientes entre sí. Ver
        // [compositeWithEffects].
        return compositeWithEffects(source, wrapped, params)
    }

    /** Radio de difuminado en píxeles, proporcional al lado mayor de la imagen (máx. razonable ~5%). */
    private fun blurRadiusPx(amount: Float, width: Int, height: Int): Int {
        val maxRadius = (max(width, height) * 0.05f).coerceIn(2f, 48f)
        return (amount.coerceIn(0f, 1f) * maxRadius).roundToInt().coerceAtLeast(1)
    }

    /**
     * Difuminado de FONDO estilo "retrato"/profundidad de campo: en vez de
     * emborronar toda la imagen (lo que antes también volvía borroso al
     * sujeto y, visualmente, la zona donde se apoya su sombra — el reporte
     * real que motivó este cambio), esto difumina [sharpFg] entero y
     * después mezcla, píxel a píxel, entre esa versión difuminada y la
     * versión nítida — usando el alfa del [original] como "mapa de
     * protección": donde el alfa es alto (adentro del sujeto) se queda con
     * el píxel nítido tal cual vino; donde el alfa es bajo (fondo,
     * transparente) se usa el píxel difuminado.
     *
     * El ALFA del resultado se conserva siempre igual al de [sharpFg] —
     * nunca se difumina el alfa acá — así el contorno/silueta del sujeto
     * (y por lo tanto la sombra, que se genera aparte a partir del alfa
     * del original, ver [compositeWithDropShadow]) no cambia de forma ni
     * un píxel por mover este slider.
     *
     * Nota honesta: sobre un recorte "duro" con fondo 100% transparente,
     * esto no se ve — no hay nada ahí que difuminar, y es lo correcto (no
     * se puede desenfocar lo que no existe). El efecto se nota en el
     * halo/borde suavizado del recorte (más marcado cuanto más alto esté
     * [feather]) y en imágenes con fondo real (no recortadas a
     * transparencia), que es el caso de uso principal de un difuminado de
     * fondo tipo "retrato".
     */
    private fun applyBackgroundBlur(original: Bitmap, sharpFg: Bitmap, amount: Float, feather: Float): Bitmap {
        val w = sharpFg.width
        val h = sharpFg.height

        val blurred = boxBlur(sharpFg, blurRadiusPx(amount, w, h))
        val protectionMask = buildFeatheredProtectionMask(original, feather)

        val sharpPixels = IntArray(w * h)
        sharpFg.getPixels(sharpPixels, 0, w, 0, 0, w, h)
        val blurPixels = IntArray(w * h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        if (blurred !== sharpFg) blurred.recycle()

        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val protect = protectionMask[i] / 255f
            val sharp = sharpPixels[i]
            val blur = blurPixels[i]
            val a = (sharp ushr 24) and 0xFF // alfa SIEMPRE del nítido — la silueta no se toca
            val sr = (sharp ushr 16) and 0xFF
            val sg = (sharp ushr 8) and 0xFF
            val sb = sharp and 0xFF
            val br = (blur ushr 16) and 0xFF
            val bg = (blur ushr 8) and 0xFF
            val bb = blur and 0xFF
            val r = (sr * protect + br * (1f - protect)).roundToInt().coerceIn(0, 255)
            val g = (sg * protect + bg * (1f - protect)).roundToInt().coerceIn(0, 255)
            val b = (sb * protect + bb * (1f - protect)).roundToInt().coerceIn(0, 255)
            outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Light wrap: mezcla [wrapColor] hacia el ANILLO INTERIOR del borde
     * del sujeto (nunca hacia afuera de su silueta), simulando la luz
     * ambiente de un fondo "envolviendo" el contorno — el detalle que
     * distingue un compuesto integrado de un recorte pegado encima.
     *
     * Técnica: se toma el alfa del sujeto, se INVIERTE (255-alfa, así
     * "afuera" queda en blanco y "adentro" en negro) y se difumina esa
     * máscara invertida con [boxBlur] — el difuminado hace que el
     * blanco de "afuera" sangre hacia adentro, más fuerte cerca del
     * borde y decayendo hacia el centro. Multiplicar ese resultado por
     * el alfa ORIGINAL (sin invertir) recorta esa sangría exactamente a
     * la silueta del sujeto: lo que queda es un anillo que vive
     * ÚNICAMENTE por dentro del contorno, más intenso pegado al borde y
     * desvaneciéndose hacia el centro — exactamente el patrón de un
     * light wrap real, sin necesitar un fondo de verdad detrás para
     * tomarlo prestado.
     */
    private fun applyLightWrap(foreground: Bitmap, intensity: Float, wrapColor: Int, width: Float): Bitmap {
        val w = foreground.width
        val h = foreground.height
        val radiusPx = blurRadiusPx(width, w, h)

        val alphaMask = foreground.extractAlpha()
        val alphaPixels = IntArray(w * h)
        // extractAlpha() entrega un bitmap ALPHA_8 — hay que pasarlo por
        // un ARGB intermedio (mismo patrón que ya usa el resto del
        // archivo, ver [buildFeatheredProtectionMask]) para poder leerlo
        // con getPixels() y tener el valor de alfa accesible por canal.
        val alphaArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(alphaArgb).drawBitmap(alphaMask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        alphaMask.recycle()
        alphaArgb.getPixels(alphaPixels, 0, w, 0, 0, w, h)

        val invertedArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val invertedPixels = IntArray(w * h) { i ->
            val a = (alphaPixels[i] ushr 24) and 0xFF
            val inv = 255 - a
            (inv shl 24) or (inv shl 16) or (inv shl 8) or inv
        }
        invertedArgb.setPixels(invertedPixels, 0, w, 0, 0, w, h)

        val bled = if (radiusPx >= 1) boxBlur(invertedArgb, radiusPx) else invertedArgb
        if (bled !== invertedArgb) invertedArgb.recycle()
        val bledPixels = IntArray(w * h)
        bled.getPixels(bledPixels, 0, w, 0, 0, w, h)
        bled.recycle()

        val fgPixels = IntArray(w * h)
        foreground.getPixels(fgPixels, 0, w, 0, 0, w, h)

        val wr = (wrapColor ushr 16) and 0xFF
        val wg = (wrapColor ushr 8) and 0xFF
        val wb = wrapColor and 0xFF

        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val p = fgPixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) {
                outPixels[i] = p
                continue
            }
            // El anillo solo existe adentro de la silueta: se multiplica
            // la sangría difuminada por el alfa ORIGINAL (no el
            // invertido) para recortarla exactamente al sujeto.
            val ringStrength = ((bledPixels[i] ushr 24) and 0xFF) * (a / 255f) / 255f
            val mix = (ringStrength * intensity).coerceIn(0f, 1f)
            if (mix <= 0.001f) {
                outPixels[i] = p
                continue
            }
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val outR = (r * (1f - mix) + wr * mix).roundToInt().coerceIn(0, 255)
            val outG = (g * (1f - mix) + wg * mix).roundToInt().coerceIn(0, 255)
            val outB = (b * (1f - mix) + wb * mix).roundToInt().coerceIn(0, 255)
            outPixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Mapa de protección 0..255 por píxel (255 = sujeto, protegido del
     * difuminado; 0 = fondo, se difumina a pleno) a partir del alfa del
     * [original]. [feather] suaviza la transición entre ambos con el
     * mismo [boxBlur] de siempre — así el borde del recorte no queda con
     * un límite duro de "difuminado / no difuminado" pegado al pixel
     * exacto del contorno, sino una zona de transición gradual (más ancha
     * cuanto más alto el slider "Suavizado de contorno").
     */
    private fun buildFeatheredProtectionMask(original: Bitmap, feather: Float): IntArray {
        val w = original.width
        val h = original.height
        val alphaMask = original.extractAlpha()
        val maskArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(maskArgb).drawBitmap(alphaMask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        alphaMask.recycle()

        val featherPx = (feather.coerceIn(0f, 1f) * (max(w, h) * 0.03f).coerceIn(1f, 24f)).roundToInt()
        val featheredMask = if (featherPx >= 1) boxBlur(maskArgb, featherPx) else maskArgb
        if (featheredMask !== maskArgb) maskArgb.recycle()

        val pixels = IntArray(w * h)
        featheredMask.getPixels(pixels, 0, w, 0, 0, w, h)
        featheredMask.recycle()

        return IntArray(w * h) { (pixels[it] ushr 24) and 0xFF }
    }

    /**
     * Nitidez (realce de detalle): "unsharp mask" clásico — se resta una
     * versión levemente difuminada de [source] al [source] mismo,
     * amplificando esa diferencia (los bordes/detalle fino) y sumándola
     * de vuelta. Radio de difuminado interno chico y fijo (no
     * configurable desde afuera) porque lo que hace "nítida" a una
     * imagen es realzar detalle FINO — un radio grande se acercaría más a
     * un efecto de contraste local que a nitidez real.
     */
    private fun applySharpen(source: Bitmap, amount: Float): Bitmap {
        val w = source.width
        val h = source.height
        val innerRadius = (max(w, h) * 0.01f).roundToInt().coerceIn(1, 6)
        val blurred = boxBlur(source, innerRadius)

        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val blurPixels = IntArray(w * h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        if (blurred !== source) blurred.recycle()

        val strength = amount.coerceIn(0f, 1f) * 1.5f
        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val s = srcPixels[i]
            val b = blurPixels[i]
            val a = (s ushr 24) and 0xFF
            val r = sharpenChannel(s, b, 16, strength)
            val g = sharpenChannel(s, b, 8, strength)
            val bl = sharpenChannel(s, b, 0, strength)
            outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or bl
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun sharpenChannel(src: Int, blurred: Int, shift: Int, strength: Float): Int {
        val sv = (src ushr shift) and 0xFF
        val bv = (blurred ushr shift) and 0xFF
        return (sv + (sv - bv) * strength).roundToInt().coerceIn(0, 255)
    }

    /**
     * Brillo (ajuste aditivo simple por canal RGB, clamp 0..255) +
     * Contraste (fórmula estándar `(color-128) * factor + 128`) en un
     * solo pase por píxel — ambos son ajustes lineales por canal así que
     * combinarlos en una sola pasada evita un bitmap intermedio extra sin
     * cambiar el resultado visual de aplicarlos por separado. El brillo
     * se aplica ANTES que el contraste (mismo orden que Photoshop/
     * Lightroom: contraste sobre un punto medio ya desplazado por el
     * brillo, no al revés). El alfa nunca se toca acá.
     */
    private fun applyBrightnessContrast(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val brightAdd = (brightness.coerceIn(-1f, 1f) * 255f).roundToInt()
        val contrastFactor = contrast.coerceIn(0f, 2f)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = adjustBrightnessContrastChannel((p ushr 16) and 0xFF, brightAdd, contrastFactor)
            val g = adjustBrightnessContrastChannel((p ushr 8) and 0xFF, brightAdd, contrastFactor)
            val b = adjustBrightnessContrastChannel(p and 0xFF, brightAdd, contrastFactor)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun adjustBrightnessContrastChannel(value: Int, brightAdd: Int, contrastFactor: Float): Int {
        val brightened = (value + brightAdd).coerceIn(0, 255)
        val contrasted = ((brightened - 128) * contrastFactor + 128f).roundToInt()
        return contrasted.coerceIn(0, 255)
    }

    /**
     * Tono (hue): rotación de matiz sobre TODO el sujeto, [hueDeg] grados
     * (0..360). Implementación vía `Color.RGBToHSV`/`Color.HSVToColor`
     * (convertir cada píxel a HSV, sumar el matiz, reconvertir) en vez de
     * una ColorMatrix con senos/cosenos: más lento píxel a píxel, pero
     * mucho más simple de mantener y sin las distorsiones de saturación
     * que puede introducir una matriz de rotación de hue aproximada sobre
     * el espacio RGB. Los píxeles totalmente transparentes (alfa 0, muy
     * comunes en recortes) se copian tal cual: no tienen color "real" que
     * rotar y `RGBToHSV` sobre un RGB de relleno arbitrario podría teñir
     * de forma visible el borde del recorte si alguna vez se compone
     * sobre algo que no sea 100% opaco.
     */
    private fun applyHueRotation(source: Bitmap, hueDeg: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val shift = ((hueDeg % 360f) + 360f) % 360f
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) continue
            Color.RGBToHSV((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF, hsv)
            hsv[0] = (hsv[0] + shift) % 360f
            pixels[i] = Color.HSVToColor(a, hsv)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applySaturation(source: Bitmap, saturation: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /**
     * Silueta del [original] tiñida 100% con [color] (SRC_IN reemplaza el
     * color por el elegido pero conserva la forma/alfa de la máscara) —
     * pieza compartida entre la sombra y el resplandor, que son
     * conceptualmente el mismo elemento (silueta teñida + difuminada),
     * solo con distinto offset/color/intensidad. Devuelve un bitmap
     * nuevo del mismo tamaño que [original]; el llamador es dueño de
     * reciclarlo.
     */
    private fun tintedSilhouette(original: Bitmap, color: Int): Bitmap {
        val w = original.width
        val h = original.height
        val alphaMask = original.extractAlpha()
        val silhouette = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(silhouette).drawBitmap(
            alphaMask,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        )
        alphaMask.recycle()
        return silhouette
    }

    /**
     * Desvanece [silhouette] verticalmente: opacidad completa en la fila
     * inferior (y = h-1, el punto de apoyo/pie del sujeto — "distancia
     * cero") y una opacidad reducida en la fila superior (y = 0, el
     * punto más lejano del apoyo dentro de la silueta), con [amount]
     * controlando cuánto se reduce ese extremo lejano (0 = sin cambio,
     * 1 = se desvanece casi del todo). Se usa para que la sombra
     * proyectada "se pierda" gradualmente en vez de tener opacidad
     * pareja de punta a punta — el mismo recurso que un dropshadow de
     * piso profesional, sin necesitar rehacer la sombra como un
     * degradado en la dirección exacta del offset (que además cambiaría
     * de eje cada vez que se mueve el ángulo).
     */
    private fun applyVerticalFade(silhouette: Bitmap, amount: Float): Bitmap {
        val w = silhouette.width
        val h = silhouette.height
        val pixels = IntArray(w * h)
        silhouette.getPixels(pixels, 0, w, 0, 0, w, h)

        val lastRow = (h - 1).coerceAtLeast(1)
        val fadeAmount = amount.coerceIn(0f, 1f)
        for (y in 0 until h) {
            val t = y.toFloat() / lastRow // 0 = fila superior, 1 = fila inferior (pie)
            val fade = 1f - fadeAmount * (1f - t)
            val rowStart = y * w
            for (x in 0 until w) {
                val idx = rowStart + x
                val p = pixels[idx]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                val newAlpha = (a * fade).roundToInt().coerceIn(0, 255)
                pixels[idx] = (newAlpha shl 24) or (p and 0x00FFFFFF)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Contorno sólido de [thicknessNormalized] (0..1, misma escala que
     * [blurRadiusPx]) alrededor de la silueta del [original], teñido de
     * [color]. Implementación por dilatación aproximada: se difumina la
     * máscara de alfa con un radio igual al grosor pedido (reusando
     * [boxBlur], como pide mantener el mismo algoritmo de difuminado en
     * toda la app) y después se aplica un umbral bajo — cualquier píxel
     * que, tras ese difuminado, conservó algo de alfa (es decir, estaba a
     * `thicknessNormalized`-píxeles o menos del sujeto original) pasa a
     * alfa 255 sólido. El resultado es una silueta "agrandada" con borde
     * duro: al componerla DETRÁS del sujeto (ver [compositeWithEffects]),
     * lo único que queda visible de ella es el anillo que sobresale del
     * contorno real — un contorno de grosor uniforme, sin necesidad de
     * una dilatación morfológica exacta.
     */
    private fun buildDilatedOutline(original: Bitmap, color: Int, thicknessNormalized: Float): Bitmap {
        val w = original.width
        val h = original.height
        val outlinePx = blurRadiusPx(thicknessNormalized, w, h)

        val alphaMask = original.extractAlpha()
        val maskArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(maskArgb).drawBitmap(alphaMask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        alphaMask.recycle()

        val dilatedGray = boxBlur(maskArgb, outlinePx)
        if (dilatedGray !== maskArgb) maskArgb.recycle()

        val pixels = IntArray(w * h)
        dilatedGray.getPixels(pixels, 0, w, 0, 0, w, h)
        // Recicla SIEMPRE (no condicional): si boxBlur devolvió el mismo
        // objeto que maskArgb (radio < 1), la línea de arriba lo dejó sin
        // reciclar a propósito para evitar un doble-free — hay que
        // reciclarlo acá, una sola vez, para no filtrarlo. Si son objetos
        // distintos, maskArgb ya se recicló arriba y esto libera el otro.
        // Mismo patrón exacto que [buildFeatheredProtectionMask].
        dilatedGray.recycle()

        // Umbral bajo a propósito: casi cualquier resto de alfa después
        // del difuminado significa "adentro del radio de dilatación" —
        // un umbral alto encogería visiblemente el anillo resultante.
        val threshold = 6
        val cr = (color ushr 16) and 0xFF
        val cg = (color ushr 8) and 0xFF
        val cb = color and 0xFF
        val solidColor = (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            outPixels[i] = if (a > threshold) solidColor else 0
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Dilatación aproximada de [silhouette] por [radiusPx]: mismo truco
     * que ya usa [buildDilatedOutline] (difuminar la máscara con
     * [boxBlur] y después aplicar un umbral bajo para volver cualquier
     * resto de alfa a 255 sólido) pero conservando el color de
     * [silhouette] en vez de forzar uno nuevo — así sirve como paso
     * previo genérico de "agrandar el núcleo, con borde duro" para
     * cualquier silueta ya teñida, reutilizado por [shadowSpread].
     */
    private fun dilateSilhouette(silhouette: Bitmap, radiusPx: Int): Bitmap {
        if (radiusPx < 1) return silhouette
        val w = silhouette.width
        val h = silhouette.height
        val blurred = boxBlur(silhouette, radiusPx)
        val pixels = IntArray(w * h)
        blurred.getPixels(pixels, 0, w, 0, 0, w, h)
        if (blurred !== silhouette) blurred.recycle()

        val threshold = 6
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            pixels[i] = if (a > threshold) (0xFF shl 24) or (p and 0x00FFFFFF) else 0
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Grano/ruido determinístico sobre el ALFA de [silhouette]: sin esto,
     * una sombra queda perfectamente lisa/uniforme, algo que casi nunca
     * pasa sobre una superficie real (concreto, tela, tierra, siempre
     * tiene micro-variación). Se aplica ANTES del difuminado final (ver
     * llamada en el pipeline de la sombra) para que ese blur lo suavice
     * a una textura sutil de grano fino, no a ruido "crudo" píxel a
     * píxel. El ruido se calcula con un hash 2D determinístico de (x, y)
     * — NO con `Random` de estado global — a propósito: así el patrón de
     * grano es siempre idéntico para el mismo píxel en cada render, sin
     * "hormiguear"/parpadear entre fotogramas mientras el usuario mueve
     * otro slider cualquiera.
     */
    private fun applyShadowGrain(silhouette: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.001f) return silhouette
        val w = silhouette.width
        val h = silhouette.height
        val pixels = IntArray(w * h)
        silhouette.getPixels(pixels, 0, w, 0, 0, w, h)

        val strength = amount.coerceIn(0f, 1f) * 90f // variación máxima de alfa en el pico del ruido
        for (y in 0 until h) {
            val rowStart = y * w
            for (x in 0 until w) {
                val idx = rowStart + x
                val p = pixels[idx]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                // Hash entero determinístico de (x, y) — variante simple
                // de un hash de mezcla de bits (estilo MurmurHash),
                // suficiente para ruido visual sin necesitar una tabla de
                // permutación tipo Perlin.
                var hash = x * 374761393 + y * 668265263
                hash = (hash xor (hash ushr 13)) * 1274126177
                hash = hash xor (hash ushr 16)
                val n = (hash and 0xFFFF) / 65535f // 0..1
                val delta = ((n - 0.5f) * 2f * strength).roundToInt()
                val newAlpha = (a + delta).coerceIn(0, 255)
                pixels[idx] = (newAlpha shl 24) or (p and 0x00FFFFFF)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Compone, de atrás hacia adelante, las capas que rodean al sujeto:
     * sombra proyectada (con offset por distancia+ángulo) → resplandor
     * (silueta teñida y difuminada, SIN offset, 360° alrededor) →
     * contorno sólido → [foreground] (el sujeto ya procesado por
     * saturación/tono/nitidez/difuminado de fondo) → reflejo (debajo de
     * TODO el conjunto anterior, ver [buildReflection]). Sombra/
     * resplandor/contorno se calculan siempre a partir del alfa del
     * [original] SIN procesar — nunca del resultado de otro efecto —
     * para que queden totalmente independientes entre sí (mover un
     * slider nunca "acumula" un difuminado extra sobre el de otro
     * control); el reflejo, en cambio, sí espeja [foreground] tal cual
     * (con sus propios ajustes de color/nitidez/difuminado de fondo ya
     * horneados), porque conceptualmente es "lo mismo que se ve, dado
     * vuelta" — no un efecto adicional sobre el sujeto. El lienzo final
     * se agranda con margen ([pad], simétrico en los 4 lados para
     * sombra/resplandor/contorno) más una franja extra SOLO abajo
     * ([reflectionExtraHeight]) del alto exacto del sujeto para que el
     * reflejo entero entre sin recortarse — igual criterio que ya usa
     * Extrude3D para su cuerpo extruido.
     */
    private fun compositeWithEffects(
        original: Bitmap,
        foreground: Bitmap,
        params: ImageEffectsParams
    ): Bitmap {
        val w = original.width
        val h = original.height

        var shadowLayer: Bitmap? = null
        var shadowDx = 0f
        var shadowDy = 0f
        var shadowBlurPx = 0
        var shadowSpreadPx = 0
        if (params.shadowIntensity > 0.001f) {
            // shadowBlur nunca en 0 puro: una sombra sin NADA de
            // difuminado se ve como un recorte duro pegado a la imagen,
            // no como una sombra — se garantiza un mínimo sutil para que
            // siempre "lea" como sombra incluso con el slider al piso.
            shadowBlurPx = blurRadiusPx(params.shadowBlur.coerceIn(0.08f, 1f), w, h)
            val silhouette = tintedSilhouette(original, params.shadowColor)

            // Spread: dilata el núcleo ANTES de desvanecer/difuminar —
            // el radio de dilatación se escala respecto al blur elegido
            // (así "Expansión" siempre se siente proporcional al
            // "Difuminado" actual, nunca desconectado de él).
            shadowSpreadPx = (params.shadowSpread * shadowBlurPx).roundToInt()
            val spread = if (shadowSpreadPx >= 1) {
                val d = dilateSilhouette(silhouette, shadowSpreadPx)
                if (d !== silhouette) silhouette.recycle()
                d
            } else {
                silhouette
            }

            // Desvanecimiento por distancia: se aplica ANTES del blur
            // (sobre la silueta recién teñida/expandida) para que el
            // difuminado final suavice también la transición del
            // degradado, en vez de quedar como un corte aparte encima de
            // una sombra ya difuminada.
            val faded = if (params.shadowFadeByDistance > 0.001f) {
                val f = applyVerticalFade(spread, params.shadowFadeByDistance)
                if (f !== spread) spread.recycle()
                f
            } else {
                spread
            }
            val grained = if (params.shadowNoise > 0.001f) {
                val g = applyShadowGrain(faded, params.shadowNoise)
                if (g !== faded) faded.recycle()
                g
            } else {
                faded
            }
            // El radio de difuminado EFECTIVO se reduce a medida que
            // sube el Spread — mismo trade-off que el "Spread" de
            // Photoshop: a más núcleo sólido, borde relativamente más
            // ajustado, en vez de simplemente sumar tamaño de más al
            // difuminado ya existente.
            val effectiveBlurPx = (shadowBlurPx * (1f - params.shadowSpread * 0.6f)).roundToInt().coerceAtLeast(0)
            val blurred = boxBlur(grained, effectiveBlurPx)
            if (blurred !== grained) grained.recycle()
            shadowLayer = blurred

            val maxOffset = max(w, h) * 0.18f
            val distancePx = params.shadowDistance * maxOffset
            val angleRad = Math.toRadians(params.shadowAngleDeg.toDouble())
            shadowDx = (cos(angleRad) * distancePx).toFloat()
            shadowDy = (sin(angleRad) * distancePx).toFloat()
        }

        // Sombra de RELLENO: misma receta que la principal (silueta
        // teñida + difuminada + offset por distancia/ángulo) pero con
        // sus PROPIOS color/difuminado/distancia/ángulo — sin
        // inclinación ni desvanecimiento propios, para mantenerla como
        // un segundo "golpe" simple de luz ambiente en vez de duplicar
        // todos los controles de la principal.
        var fillShadowLayer: Bitmap? = null
        var fillShadowDx = 0f
        var fillShadowDy = 0f
        var fillShadowBlurPx = 0
        if (params.fillShadowIntensity > 0.001f) {
            fillShadowBlurPx = blurRadiusPx(params.fillShadowBlur.coerceIn(0.08f, 1f), w, h)
            val silhouette = tintedSilhouette(original, params.fillShadowColor)
            val blurred = boxBlur(silhouette, fillShadowBlurPx)
            if (blurred !== silhouette) silhouette.recycle()
            fillShadowLayer = blurred

            val maxOffset = max(w, h) * 0.18f
            val distancePx = params.fillShadowDistance * maxOffset
            val angleRad = Math.toRadians(params.fillShadowAngleDeg.toDouble())
            fillShadowDx = (cos(angleRad) * distancePx).toFloat()
            fillShadowDy = (sin(angleRad) * distancePx).toFloat()
        }

        var glowLayer: Bitmap? = null
        var glowBlurPx = 0
        if (params.glowIntensity > 0.001f) {
            glowBlurPx = blurRadiusPx(params.glowBlur, w, h)
            val silhouette = tintedSilhouette(original, params.glowColor)
            val blurred = if (glowBlurPx >= 1) boxBlur(silhouette, glowBlurPx) else silhouette
            if (blurred !== silhouette) silhouette.recycle()
            glowLayer = blurred
        }

        var outlineLayer: Bitmap? = null
        var outlinePx = 0
        if (params.outlineIntensity > 0.001f) {
            outlinePx = blurRadiusPx(params.outlineIntensity, w, h)
            outlineLayer = buildDilatedOutline(original, params.outlineColor, params.outlineIntensity)
        }

        // Sombra de CONTACTO: mancha corta con caída radial suave,
        // centrada en el punto donde el sujeto "pisa" — totalmente
        // aparte de la sombra proyectada de arriba (esa sí se mueve con
        // distancia/ángulo; esta siempre queda pegada al pie). Se calcula
        // ANTES del cómputo de [pad] porque también necesita margen
        // propio para no recortarse.
        var contactShadowLayer: Bitmap? = null
        if (params.contactShadowIntensity > 0.001f) {
            val built = buildContactShadow(w, params.contactShadowSize, params.contactShadowColor, params.contactShadowFalloff)
            contactShadowLayer = if (params.contactShadowBlur > 0.001f) {
                val cBlurPx = blurRadiusPx(params.contactShadowBlur, w, h)
                val blurred = boxBlur(built, cBlurPx)
                if (blurred !== built) built.recycle()
                blurred
            } else {
                built
            }
        }

        var reflectionLayer: Bitmap? = null
        var reflectionGapPx = 0
        if (params.reflectionIntensity > 0.001f) {
            val built = buildReflection(
                foreground,
                params.reflectionIntensity,
                params.reflectionLength,
                params.reflectionTintIntensity,
                params.reflectionTintColor,
                params.reflectionEdgeFade,
                params.reflectionOpacityCurve
            )
            // Ondulación tipo "agua": se aplica ANTES del difuminado
            // propio del reflejo (si lo hay) para que ese blur suavice
            // también los bordes de la onda — el mismo orden lógico que
            // el desvanecimiento por distancia de la sombra: primero se
            // distorsiona la forma, después se difumina el resultado.
            val rippled = if (params.reflectionRippleIntensity > 0.001f) {
                applyRipple(built, params.reflectionRippleIntensity, params.reflectionRippleScale)
            } else {
                built
            }
            if (rippled !== built) built.recycle()
            reflectionLayer = if (params.reflectionBlur > 0.001f) {
                // Difuminado propio del reflejo (look "piso pulido"/vidrio
                // esmerilado): usa el mismo box-blur de 3 pasadas que ya
                // usan sombra/resplandor, con su propio radio — totalmente
                // independiente del difuminado de fondo o de la sombra.
                val reflBlurPx = blurRadiusPx(params.reflectionBlur, w, h)
                val blurred = boxBlur(rippled, reflBlurPx)
                if (blurred !== rippled) rippled.recycle()
                blurred
            } else {
                rippled
            }
            // Distancia/separación entre el pie del sujeto y su reflejo:
            // 0 = pegado (comportamiento clásico, igual que antes), hasta
            // un 25% del alto del sujeto para el máximo del slider — así
            // el "despegue" se ve proporcional tanto en una capa chica
            // como en una grande, mismo criterio que shadowDistance.
            reflectionGapPx = (params.reflectionGap * h * 0.25f).roundToInt()
        }
        val reflectionLayerHeight = reflectionLayer?.height ?: 0
        // Si hay compresión/estiramiento por perspectiva, la altura
        // ocupada en el lienzo final crece o se achica en la misma
        // proporción (pivota desde la fila 0, pegada al pie, así que
        // TODO el estiramiento extra cae hacia abajo).
        val reflectionScaledHeight = if (reflectionLayer != null) {
            (reflectionLayerHeight * params.reflectionPerspective).roundToInt()
        } else 0
        val reflectionExtraHeight = if (reflectionLayer != null) reflectionScaledHeight + reflectionGapPx else 0

        // Inclinación (perspectiva) del reflejo: un shear horizontal que
        // pivotea desde la fila pegada al pie del sujeto (ahí nunca se
        // mueve) y abre hacia el extremo lejano del reflejo — igual look
        // que un piso visto en ángulo. Como esto puede correr el reflejo
        // hacia un costado, el lienzo necesita margen horizontal EXTRA
        // (más allá de [pad]) del lado que corresponda.
        val reflectionSkewFactor = if (reflectionLayer != null && abs(params.reflectionSkewDegrees) > 0.01f) {
            tan(Math.toRadians(params.reflectionSkewDegrees.toDouble())).toFloat()
        } else {
            0f
        }
        val reflectionSkewPad = (abs(reflectionSkewFactor) * reflectionScaledHeight).roundToInt()

        // Inclinación (perspectiva) de la sombra proyectada: mismo shear
        // horizontal que el reflejo, pero pivotando desde la fila
        // INFERIOR de la silueta completa (el pie, el punto de apoyo del
        // sujeto sobre el "piso") en vez de la fila 0 — tiene sentido
        // físico distinto: acá lo que se inclina es todo el cuerpo
        // proyectado, no una copia espejada que ya arranca pegada al pie.
        val shadowSkewFactor = if (shadowLayer != null && abs(params.shadowSkewDegrees) > 0.01f) {
            tan(Math.toRadians(params.shadowSkewDegrees.toDouble())).toFloat()
        } else {
            0f
        }
        val shadowSkewPad = (abs(shadowSkewFactor) * h).roundToInt()

        val shadowReach = if (shadowLayer != null) {
            val scaleExtra = max(0f, params.shadowScale - 1f) * max(w, h)
            shadowBlurPx * 2f + shadowSpreadPx + scaleExtra + max(abs(shadowDx), abs(shadowDy))
        } else 0f
        val fillShadowReach = if (fillShadowLayer != null) {
            val scaleExtra = max(0f, params.fillShadowScale - 1f) * max(w, h)
            fillShadowBlurPx * 2f + scaleExtra + max(abs(fillShadowDx), abs(fillShadowDy))
        } else 0f
        val glowReach = if (glowLayer != null) glowBlurPx * 2f else 0f
        val outlineReach = outlinePx.toFloat()
        val contactShadowReach = if (contactShadowLayer != null) contactShadowLayer.height / 2f else 0f
        val pad = max(shadowReach, max(fillShadowReach, max(glowReach, max(outlineReach, contactShadowReach)))).roundToInt() + 4
        val padX = pad + max(reflectionSkewPad, shadowSkewPad)
        val padY = pad

        val outW = w + padX * 2
        val outH = h + padY * 2 + reflectionExtraHeight
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Familia de sombras (relleno + proyectada + contacto): se
        // componen juntas en un búfer de píxeles PROPIO (no directamente
        // sobre [canvas]) usando [blitBlend], que sabe mezclar en modo
        // NORMAL o MULTIPLICAR según [shadowBlendMultiply] — así, donde
        // la sombra de contacto se superpone con la proyectada (el caso
        // más común, justo en el pie), el resultado se OSCURECE de forma
        // natural en vez de mezclarse como un gris translúcido. Orden
        // "de atrás hacia adelante" dentro de la familia: relleno
        // (la más ambiental/difusa) → proyectada (la principal) →
        // contacto (la más pegada/definida) — mismo criterio pictórico
        // que el resto de esta función.
        if (shadowLayer != null || fillShadowLayer != null || contactShadowLayer != null) {
            val shadowGroupPixels = IntArray(outW * outH)

            fillShadowLayer?.let {
                val matrix = Matrix()
                if (abs(params.fillShadowScale - 1f) > 0.001f) {
                    matrix.postScale(params.fillShadowScale, params.fillShadowScale, w / 2f, (h - 1).toFloat())
                }
                matrix.postTranslate(padX + fillShadowDx, padY + fillShadowDy)
                blitBlend(shadowGroupPixels, outW, outH, it, matrix, params.fillShadowIntensity, params.shadowBlendMultiply)
                it.recycle()
            }

            shadowLayer?.let {
                // Orden de composición del transform: ESCALA primero
                // (pivote en el pie del sujeto, x=w/2, y=h-1, para que
                // escalar nunca "levante" la sombra del piso) → INCLINACIÓN
                // (perspectiva) segundo → TRASLACIÓN (offset por
                // distancia/ángulo + posición en el lienzo) al final —
                // mismo criterio de pivote que ya usa la inclinación acá
                // abajo, aplicado ahora también a la escala.
                val matrix = Matrix()
                if (abs(params.shadowScale - 1f) > 0.001f) {
                    matrix.postScale(params.shadowScale, params.shadowScale, w / 2f, (h - 1).toFloat())
                }
                if (shadowSkewFactor != 0f) {
                    // x' = x + shadowSkewFactor * (y - (h-1)) — pivota
                    // en la fila inferior (el pie, y=h-1), donde el
                    // shift es 0, y abre hacia arriba a medida que
                    // crece la silueta — el mismo shear que el
                    // reflejo, pero anclado al punto de apoyo en vez
                    // de a la fila 0.
                    val skewMatrix = Matrix().apply {
                        setValues(
                            floatArrayOf(
                                1f, shadowSkewFactor, -shadowSkewFactor * (h - 1),
                                0f, 1f, 0f,
                                0f, 0f, 1f
                            )
                        )
                    }
                    matrix.postConcat(skewMatrix)
                }
                matrix.postTranslate(padX + shadowDx, padY + shadowDy)
                blitBlend(shadowGroupPixels, outW, outH, it, matrix, params.shadowIntensity, params.shadowBlendMultiply)
                it.recycle()
            }

            // La sombra de contacto va centrada horizontalmente y con su
            // centro vertical apoyado justo en el borde inferior del
            // sujeto — ahí es donde "toca el piso".
            contactShadowLayer?.let {
                val dx = padX + w / 2f - it.width / 2f
                val dy = padY + h - it.height / 2f
                val matrix = Matrix().apply { postTranslate(dx, dy) }
                blitBlend(shadowGroupPixels, outW, outH, it, matrix, params.contactShadowIntensity, params.shadowBlendMultiply)
                it.recycle()
            }

            val shadowGroupBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            shadowGroupBitmap.setPixels(shadowGroupPixels, 0, outW, 0, 0, outW, outH)
            // El grupo ya resuelto (con su mezcla interna correcta) se
            // pinta sobre [canvas] con composición NORMAL: sigue siendo
            // matemáticamente correcto porque [canvas] está vacío en
            // este punto — mezclar en "multiplicar" contra transparencia
            // pura da exactamente el mismo resultado que mezclar normal
            // (ver comentario grande en [blitBlend]).
            canvas.drawBitmap(shadowGroupBitmap, 0f, 0f, drawPaint)
            shadowGroupBitmap.recycle()
        }

        glowLayer?.let {
            val glowPaint = Paint(drawPaint).apply {
                alpha = (params.glowIntensity * 255f).roundToInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(it, padX.toFloat(), padY.toFloat(), glowPaint)
            it.recycle()
        }

        outlineLayer?.let {
            canvas.drawBitmap(it, padX.toFloat(), padY.toFloat(), drawPaint)
            it.recycle()
        }

        // El reflejo se dibuja ANTES que el sujeto en el orden de
        // pintado (queda "debajo" en el z-order) pero su POSICIÓN es
        // relativa al borde inferior del sujeto (padY + h), corrida hacia
        // abajo por [reflectionGapPx] cuando el usuario pide separación
        // — con el slider "Distancia" en 0 queda pegada, igual que antes.
        // Cuando hay inclinación y/o perspectiva, se dibuja con una
        // matriz (drawBitmap con Matrix) en vez de una posición simple,
        // para no tener que rehacer los píxeles del bitmap.
        reflectionLayer?.let {
            val hasSkew = reflectionSkewFactor != 0f
            val hasPerspective = abs(params.reflectionPerspective - 1f) > 0.001f
            if (hasSkew || hasPerspective) {
                val matrix = Matrix()
                if (hasPerspective) {
                    // Escala vertical pura, pivotando en y=0 (la fila
                    // pegada al pie) — nunca cambia el ancho, para eso
                    // ya está reflectionSkewFactor.
                    matrix.postScale(1f, params.reflectionPerspective, 0f, 0f)
                }
                if (hasSkew) {
                    // x' = x + reflectionSkewFactor * y (pivota en y=0,
                    // la fila pegada al pie); y' = y sin cambios.
                    val skewMatrix = Matrix().apply {
                        setValues(
                            floatArrayOf(
                                1f, reflectionSkewFactor, 0f,
                                0f, 1f, 0f,
                                0f, 0f, 1f
                            )
                        )
                    }
                    matrix.postConcat(skewMatrix)
                }
                matrix.postTranslate(padX.toFloat(), (padY + h + reflectionGapPx).toFloat())
                canvas.drawBitmap(it, matrix, drawPaint)
            } else {
                canvas.drawBitmap(it, padX.toFloat(), (padY + h + reflectionGapPx).toFloat(), drawPaint)
            }
            it.recycle()
        }

        canvas.drawBitmap(foreground, padX.toFloat(), padY.toFloat(), drawPaint)

        return result
    }

    /**
     * Copia de [foreground] volteada verticalmente ("espejo"), con un
     * degradado de opacidad lineal: 100% de [intensity] justo pegado al
     * borde inferior del sujeto (fila 0 del reflejo, que es la fila MÁS
     * BAJA del sujeto original, ya que está volteado) y 0% en el borde
     * más lejano — el efecto clásico de "reflejo sobre una superficie
     * brillante" de cualquier editor profesional. Se refleja
     * [foreground] (el sujeto YA con sus ajustes de color/nitidez/
     * difuminado de fondo horneados), no el bitmap original sin
     * procesar, porque el reflejo tiene que verse como el sujeto
     * final, dado vuelta — no como una versión sin editar de él.
     *
     * [length] (0.1..1) controla qué porción del alto del sujeto llega a
     * reflejarse: con 1 (por defecto, comportamiento original) el reflejo
     * ocupa el alto completo del sujeto y el degradado de opacidad recorre
     * ese alto entero; con un valor menor, el reflejo se corta antes —
     * queda más corto y compacto, como el reflejo de un piso pequeño en
     * vez de un espejo de cuerpo entero — pero el degradado sigue yendo
     * de 100% a 0% dentro de ese tramo más corto, así nunca termina en un
     * borde duro.
     *
     * [tintIntensity]/[tintColor]: mezcla lineal del color de cada píxel
     * del reflejo hacia [tintColor], look "reflejo en agua/vidrio de
     * color" de cualquier render profesional (piso de mármol, agua,
     * metal pulido con temperatura de color propia). En 0 el reflejo
     * conserva el color exacto del sujeto, igual que antes.
     */
    private fun buildReflection(
        foreground: Bitmap,
        intensity: Float,
        length: Float = 1f,
        tintIntensity: Float = 0f,
        tintColor: Int = Color.WHITE,
        edgeFade: Float = 0f,
        opacityCurve: Float = 0.5f
    ): Bitmap {
        val w = foreground.width
        val fullH = foreground.height
        val outH = (fullH * length.coerceIn(0.1f, 1f)).roundToInt().coerceIn(1, fullH)
        val pixels = IntArray(w * fullH)
        foreground.getPixels(pixels, 0, w, 0, 0, w, fullH)

        val applyTint = tintIntensity > 0.001f
        val tR = (tintColor ushr 16) and 0xFF
        val tG = (tintColor ushr 8) and 0xFF
        val tB = tintColor and 0xFF

        // Desvanecimiento de bordes izquierdo/derecho: un ancho de
        // rampa proporcional al ancho del sujeto (hasta 35% de cada
        // lado con el slider al máximo, así ambos lados nunca se llegan
        // a superponer ni cortan por dentro un reflejo angosto) —
        // precalculado una sola vez por columna, fuera del loop de filas,
        // porque no depende de [y].
        val edgeFadeAmount = edgeFade.coerceIn(0f, 1f)
        val rampPx = (edgeFadeAmount * w * 0.35f).roundToInt()
        val edgeFactors = if (rampPx >= 1) {
            FloatArray(w) { x ->
                when {
                    x < rampPx -> x.toFloat() / rampPx
                    x >= w - rampPx -> (w - 1 - x).toFloat() / rampPx
                    else -> 1f
                }.coerceIn(0f, 1f)
            }
        } else {
            null
        }

        val outPixels = IntArray(w * outH)
        val lastRow = (outH - 1).coerceAtLeast(1)
        // Gamma de la curva de opacidad: mapeo exponencial centrado en
        // opacityCurve=0.5 → gamma=1 (degradado lineal, EXACTAMENTE el
        // comportamiento de siempre). Por debajo de 0.5 la curva se
        // "acuesta" (gamma < 1: cae rápido apenas empieza y se estira
        // lenta hacia el final — reflejo que se apaga casi enseguida);
        // por encima de 0.5 se "empina" (gamma > 1: se mantiene fuerte
        // más tiempo y cae de golpe al final — reflejo que se sostiene
        // largo y corta abrupto).
        val gamma = Math.pow(2.0, ((opacityCurve.coerceIn(0f, 1f) - 0.5f) * 4f).toDouble())
        for (y in 0 until outH) {
            // fade: 1f en la fila pegada al sujeto (y=0), 0f en la última
            // fila del reflejo (y=outH-1, que puede ser antes de llegar a
            // la punta del sujeto si [length] < 1) — con curva gamma
            // configurable en vez de degradado lineal fijo.
            val t = y.toFloat() / lastRow
            val fade = Math.pow((1.0 - t), gamma).toFloat()
            val srcY = fullH - 1 - y // voltea verticalmente
            val srcRowStart = srcY * w
            val outRowStart = y * w
            for (x in 0 until w) {
                val p = pixels[srcRowStart + x]
                val a = (p ushr 24) and 0xFF
                val edgeFactor = edgeFactors?.get(x) ?: 1f
                val newAlpha = (a * fade * intensity * edgeFactor).roundToInt().coerceIn(0, 255)
                val rgb = if (applyTint) {
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val outR = (r * (1f - tintIntensity) + tR * tintIntensity).roundToInt().coerceIn(0, 255)
                    val outG = (g * (1f - tintIntensity) + tG * tintIntensity).roundToInt().coerceIn(0, 255)
                    val outB = (b * (1f - tintIntensity) + tB * tintIntensity).roundToInt().coerceIn(0, 255)
                    (outR shl 16) or (outG shl 8) or outB
                } else {
                    p and 0x00FFFFFF
                }
                outPixels[outRowStart + x] = (newAlpha shl 24) or rgb
            }
        }

        val result = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, outH)
        return result
    }

    /**
     * Ondulación tipo "agua": desplaza cada FILA del reflejo
     * horizontalmente según una onda seno cuya fase depende de [y] —
     * `offset(y) = amplitud * sin(y * frecuencia)` — el mismo principio
     * que un filtro de distorsión de agua de cualquier editor de imagen
     * profesional. El desplazamiento se resuelve con interpolación
     * lineal entre los dos píxeles vecinos más cercanos (sub-píxel), no
     * con un corrimiento entero, para que la onda se vea suave y no
     * "escalonada" — mismo estándar de calidad que el resto del motor
     * (ver [buildContactShadow], que usa el mismo criterio de suavidad
     * vía degradado radial real en vez de una aproximación).
     *
     * [intensity] (0..1) controla la AMPLITUD (hasta ~5% del ancho del
     * reflejo en el máximo). [scale] (0..1) controla la DENSIDAD de las
     * ondas: de 2 ondas completas a lo largo del alto (0, ondas anchas y
     * lentas) hasta 10 (1, ondas finas y rápidas).
     */
    private fun applyRipple(source: Bitmap, intensity: Float, scale: Float): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 2 || h < 1) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val amplitudePx = intensity.coerceIn(0f, 1f) * w * 0.05f
        val wavesCount = 2f + scale.coerceIn(0f, 1f) * 8f
        val freq = (2f * Math.PI.toFloat() * wavesCount) / h.toFloat()

        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = amplitudePx * sin(y * freq)
            val rowStart = y * w
            for (x in 0 until w) {
                val srcXf = x - offset
                val x0 = floor(srcXf).toInt()
                val x1 = x0 + 1
                val t = srcXf - x0
                val p0 = if (x0 in 0 until w) pixels[rowStart + x0] else 0
                val p1 = if (x1 in 0 until w) pixels[rowStart + x1] else 0
                outPixels[rowStart + x] = lerpArgb(p0, p1, t)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /** Interpolación lineal ARGB canal a canal, [t] en 0..1. */
    private fun lerpArgb(p0: Int, p1: Int, t: Float): Int {
        val tc = t.coerceIn(0f, 1f)
        val a0 = (p0 ushr 24) and 0xFF
        val r0 = (p0 ushr 16) and 0xFF
        val g0 = (p0 ushr 8) and 0xFF
        val b0 = p0 and 0xFF
        val a1 = (p1 ushr 24) and 0xFF
        val r1 = (p1 ushr 16) and 0xFF
        val g1 = (p1 ushr 8) and 0xFF
        val b1 = p1 and 0xFF
        val a = (a0 + (a1 - a0) * tc).roundToInt().coerceIn(0, 255)
        val r = (r0 + (r1 - r0) * tc).roundToInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * tc).roundToInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * tc).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Compone (mezcla) [src], transformado por [matrix], sobre el búfer
     * de píxeles [dst] (tamaño [dstW]x[dstH]) — a mano, píxel a píxel,
     * en vez de usar `Canvas.drawBitmap` + `Xfermode` nativo de Android.
     *
     * POR QUÉ A MANO: el modo `PorterDuff.Mode.MULTIPLY` "clásico" de
     * Android define alfa de salida como `Sa * Da` — es decir, donde el
     * destino todavía es transparente (alfa 0, exactamente la situación
     * de este lienzo, que arranca en blanco antes de pintar nada), el
     * resultado sería SIEMPRE invisible (alfa 0 * cualquier cosa = 0),
     * borrando la sombra en vez de oscurecerla. El `BlendMode.MULTIPLY`
     * "moderno" (el correcto, del estándar de composición de W3C) recién
     * está disponible desde Android 10 (API 29) vía `Paint.blendMode` —
     * pero este proyecto soporta desde API 26 (ver `minSdk` en
     * `app/build.gradle.kts`), así que depender de esa API rompería la
     * compatibilidad hacia atrás. La solución: implementar la fórmula
     * CORRECTA del estándar (la misma que usa `BlendMode.MULTIPLY`) a
     * mano con aritmética de píxeles simple, que YA es el patrón que usa
     * el resto de este archivo (ver [boxBlur], [tintedSilhouette]) — sin
     * depender de ninguna versión de Android en particular.
     *
     * La fórmula (con colores SIN premultiplicar, alfas 0..1):
     * ```
     * outA = Sa + Da*(1-Sa)
     * blend(Cs,Cd) = Cs      (modo NORMAL)
     *              = Cs * Cd (modo MULTIPLICAR)
     * Co = Sa*(1-Da)*Cs + Da*(1-Sa)*Cd + Sa*Da*blend(Cs,Cd)
     * Cout = Co / outA   (des-premultiplicar para guardar en ARGB recto)
     * ```
     * Esta fórmula, con `Da=0` (destino vacío), se reduce exactamente a
     * `Cout = Cs`, `outA = Sa` — o sea, el resultado es IDÉNTICO al modo
     * NORMAL cuando no hay nada debajo, sin importar si `multiply` está
     * activo o no. La diferencia solo aparece (a propósito) donde dos
     * capas de esta misma familia de sombras se superponen entre sí.
     */
    private fun blitBlend(
        dst: IntArray,
        dstW: Int,
        dstH: Int,
        src: Bitmap,
        matrix: Matrix,
        globalAlpha: Float,
        multiply: Boolean
    ) {
        val ga = globalAlpha.coerceIn(0f, 1f)
        if (ga <= 0.001f) return
        val srcW = src.width
        val srcH = src.height
        if (srcW < 1 || srcH < 1) return
        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val inverse = Matrix()
        if (!matrix.invert(inverse)) return

        // Caja delimitadora del [src] YA transformado, en espacio de
        // destino — así el recorrido solo toca los píxeles que
        // realmente puede afectar, en vez de barrer TODO [dst].
        val corners = floatArrayOf(0f, 0f, srcW.toFloat(), 0f, srcW.toFloat(), srcH.toFloat(), 0f, srcH.toFloat())
        matrix.mapPoints(corners)
        var minX = corners[0]
        var maxX = corners[0]
        var minY = corners[1]
        var maxY = corners[1]
        for (i in 1 until 4) {
            val x = corners[i * 2]
            val y = corners[i * 2 + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val startX = kotlin.math.floor(minX).toInt().coerceIn(0, dstW)
        val endX = kotlin.math.ceil(maxX).toInt().coerceIn(0, dstW)
        val startY = kotlin.math.floor(minY).toInt().coerceIn(0, dstH)
        val endY = kotlin.math.ceil(maxY).toInt().coerceIn(0, dstH)
        if (startX >= endX || startY >= endY) return

        val srcPt = FloatArray(2)
        for (dy in startY until endY) {
            for (dx in startX until endX) {
                srcPt[0] = dx + 0.5f
                srcPt[1] = dy + 0.5f
                inverse.mapPoints(srcPt)
                val sx = srcPt[0].toInt()
                val sy = srcPt[1].toInt()
                if (sx < 0 || sx >= srcW || sy < 0 || sy >= srcH) continue
                val sp = srcPixels[sy * srcW + sx]
                val sA = ((sp ushr 24) and 0xFF) / 255f * ga
                if (sA <= 0.001f) continue
                val sR = ((sp ushr 16) and 0xFF) / 255f
                val sG = ((sp ushr 8) and 0xFF) / 255f
                val sB = (sp and 0xFF) / 255f

                val dstIdx = dy * dstW + dx
                val dp = dst[dstIdx]
                val dA = ((dp ushr 24) and 0xFF) / 255f
                val dR = ((dp ushr 16) and 0xFF) / 255f
                val dG = ((dp ushr 8) and 0xFF) / 255f
                val dB = (dp and 0xFF) / 255f

                val outA = sA + dA * (1f - sA)
                if (outA <= 0.001f) {
                    dst[dstIdx] = 0
                    continue
                }

                val coR: Float
                val coG: Float
                val coB: Float
                if (multiply) {
                    coR = sA * (1f - dA) * sR + dA * (1f - sA) * dR + sA * dA * (sR * dR)
                    coG = sA * (1f - dA) * sG + dA * (1f - sA) * dG + sA * dA * (sG * dG)
                    coB = sA * (1f - dA) * sB + dA * (1f - sA) * dB + sA * dA * (sB * dB)
                } else {
                    coR = sA * sR + dA * (1f - sA) * dR
                    coG = sA * sG + dA * (1f - sA) * dG
                    coB = sA * sB + dA * (1f - sA) * dB
                }

                val outR = (coR / outA).coerceIn(0f, 1f)
                val outG = (coG / outA).coerceIn(0f, 1f)
                val outB = (coB / outA).coerceIn(0f, 1f)

                val outAlphaInt = (outA * 255f).roundToInt().coerceIn(0, 255)
                val outRInt = (outR * 255f).roundToInt().coerceIn(0, 255)
                val outGInt = (outG * 255f).roundToInt().coerceIn(0, 255)
                val outBInt = (outB * 255f).roundToInt().coerceIn(0, 255)
                dst[dstIdx] = (outAlphaInt shl 24) or (outRInt shl 16) or (outGInt shl 8) or outBInt
            }
        }
    }

    /**
     * Mancha de sombra de CONTACTO: un óvalo aplanado con caída radial
     * suave (centro opaco → borde transparente), pensado para pegarse
     * justo debajo del pie del sujeto — el recurso que usa cualquier
     * motor de render o compositing profesional para "anclar" un objeto
     * al piso además de su sombra proyectada larga. Se resuelve con un
     * `RadialGradient` circular y luego se aplana verticalmente con
     * `Canvas.scale` (en vez de dibujar una elipse con degradado propio,
     * que Android no soporta nativamente) — resultado idéntico a una
     * elipse con degradado radial real, sin aproximaciones visibles.
     *
     * [size] (0.1..1) es el ancho de la mancha relativo al ancho del
     * sujeto ([subjectWidth]); el alto queda fijo en 32% del ancho
     * (proporción "sombra de piso" estándar). [color] es el color propio
     * de esta sombra (independiente de la proyectada principal).
     *
     * [falloff] (0..1) controla la CURVA del degradado, no solo sus dos
     * puntas: en vez del único degradado lineal 0%→100% de antes, se
     * arma un `RadialGradient` de múltiples paradas con una curva
     * gamma (`alpha(t) = (1-t)^gamma`), donde `gamma` sube con
     * [falloff] — 0 da una caída lenta y extendida (gamma bajo, mancha
     * "difusa" de punta a punta) y 1 da una caída rápida y concentrada
     * (gamma alto, núcleo denso que corta antes) — el control de curva
     * de opacidad estándar de cualquier herramienta de sombra de
     * contacto/oclusión ambiental profesional.
     */
    private fun buildContactShadow(subjectWidth: Int, size: Float, color: Int, falloff: Float = 0.5f): Bitmap {
        val ovalWidth = (subjectWidth * size.coerceIn(0.1f, 1f)).roundToInt().coerceAtLeast(4)
        val ovalHeight = (ovalWidth * 0.32f).roundToInt().coerceAtLeast(2)
        val pad = (ovalHeight * 0.6f).roundToInt() + 2
        val bw = ovalWidth + pad * 2
        val bh = ovalHeight + pad * 2

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = bw / 2f
        val cy = bh / 2f
        val radius = ovalWidth / 2f

        // gamma en [0.4 .. 3.2]: por debajo de 1 la caída es más lenta
        // que lineal (mancha extendida/difusa), por encima de 1 es más
        // rápida que lineal (núcleo denso y borde corto) — 0.5 de
        // [falloff] (el valor por defecto) da gamma ≈ 1, equivalente al
        // degradado lineal simple que tenía esta función antes.
        val gamma = 0.4f + falloff.coerceIn(0f, 1f) * 2.8f
        val stopCount = 10
        val colors = IntArray(stopCount + 1)
        val positions = FloatArray(stopCount + 1)
        val cr = (color ushr 16) and 0xFF
        val cg = (color ushr 8) and 0xFF
        val cb = color and 0xFF
        for (i in 0..stopCount) {
            val t = i / stopCount.toFloat()
            positions[i] = t
            val alpha = (255f * Math.pow((1.0 - t).toDouble(), gamma.toDouble())).roundToInt().coerceIn(0, 255)
            colors[i] = (alpha shl 24) or (cr shl 16) or (cg shl 8) or cb
        }
        val shader = RadialGradient(cx, cy, radius, colors, positions, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }

        canvas.save()
        canvas.scale(1f, ovalHeight / ovalWidth.toFloat(), cx, cy)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()

        return bmp
    }

    /**
     * Desenfoque de caja (box blur) de 3 pasadas horizontal+vertical —
     * aproxima muy bien un desenfoque gaussiano real a una fracción del
     * costo, con ventana deslizante O(ancho*alto) por pasada (no
     * O(ancho*alto*radio)), así que sigue siendo rápido incluso con
     * radios grandes sobre la copia en alta resolución. Se eligió esto en
     * vez de RenderScript (deprecado/retirado en Android moderno) para no
     * atar el efecto a una API que ya no tiene soporte a futuro.
     *
     * Cada canal (incluido alfa) se difumina por separado sobre valores
     * NO premultiplicados (así entrega/recibe `Bitmap.getPixels`), que es
     * el mismo enfoque que ya usa el resto de este archivo.
     */
    fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return source
        val w = source.width
        val h = source.height
        if (w < 2 || h < 2) return source

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // 3 pasadas con radios que se reparten el radio total pedido —
        // el resultado final se percibe como un desenfoque más suave/
        // gaussiano que una única pasada de caja con el radio completo.
        val passRadius = max(1, radius / 3)
        repeat(3) {
            boxBlurHorizontal(pixels, w, h, passRadius)
            boxBlurVertical(pixels, w, h, passRadius)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlurHorizontal(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        val rowOut = IntArray(w)
        for (y in 0 until h) {
            val rowStart = y * w
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (dx in -radius..radius) {
                val xx = dx.coerceIn(0, w - 1)
                val p = pixels[rowStart + xx]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            for (x in 0 until w) {
                rowOut[x] = ((sumA / div) shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div)
                val addX = (x + radius + 1).coerceIn(0, w - 1)
                val subX = (x - radius).coerceIn(0, w - 1)
                val addP = pixels[rowStart + addX]
                val subP = pixels[rowStart + subX]
                sumA += ((addP ushr 24) and 0xFF) - ((subP ushr 24) and 0xFF)
                sumR += ((addP ushr 16) and 0xFF) - ((subP ushr 16) and 0xFF)
                sumG += ((addP ushr 8) and 0xFF) - ((subP ushr 8) and 0xFF)
                sumB += (addP and 0xFF) - (subP and 0xFF)
            }
            System.arraycopy(rowOut, 0, pixels, rowStart, w)
        }
    }

    private fun boxBlurVertical(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        val colOut = IntArray(h)
        for (x in 0 until w) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (dy in -radius..radius) {
                val yy = dy.coerceIn(0, h - 1)
                val p = pixels[yy * w + x]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            for (y in 0 until h) {
                colOut[y] = ((sumA / div) shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div)
                val addY = (y + radius + 1).coerceIn(0, h - 1)
                val subY = (y - radius).coerceIn(0, h - 1)
                val addP = pixels[addY * w + x]
                val subP = pixels[subY * w + x]
                sumA += ((addP ushr 24) and 0xFF) - ((subP ushr 24) and 0xFF)
                sumR += ((addP ushr 16) and 0xFF) - ((subP ushr 16) and 0xFF)
                sumG += ((addP ushr 8) and 0xFF) - ((subP ushr 8) and 0xFF)
                sumB += (addP and 0xFF) - (subP and 0xFF)
            }
            for (y in 0 until h) {
                pixels[y * w + x] = colOut[y]
            }
        }
    }
}
