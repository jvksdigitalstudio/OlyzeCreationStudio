package com.yeivikas.olyzecs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.yeivikas.olyzecs.engine.color.GroupGradientEngine

/**
 * Paleta de marca única de Olyze: degradado morado → azul, con el
 * morado como color dominante. Este archivo es la ÚNICA fuente de verdad
 * para el color de la app — cualquier pantalla nueva debe usar
 * [OlyzeGradient] como fondo y [OlyzeTheme] como wrapper de
 * MaterialTheme, para que la identidad visual sea 100% consistente en
 * toda la app (Mis proyectos, Editor, diálogos, etc).
 */

// --- Morado (dominante) ---
val BrandPurpleDeep = Color(0xFF1E0F45)   // esquina superior, casi negro-morado
val BrandPurple = Color(0xFF4B2A9E)       // morado principal de marca
val BrandPurpleLight = Color(0xFF7C4DFF)  // morado vivo, para acentos/botones

// --- Azul (minoría, cierre del degradado) ---
val BrandBlue = Color(0xFF2F5FE0)         // azul principal de marca
val BrandBlueLight = Color(0xFF5B8DEF)    // azul vivo, para acentos secundarios

// --- Superficies neutras con tinte morado (para paneles, tarjetas, etc.) ---
val SurfaceTintedDark = Color(0xFF16102C)
val SurfaceTintedElevated = Color(0xFF241A46)

/**
 * Gris neutro (plomo), DELIBERADAMENTE fuera de la paleta morado→azul de
 * arriba. Uso: chrome de UTILIDAD sobre contenido ajeno a la marca —hoy,
 * el marco de las miniaturas del filmstrip del visor de imágenes
 * (`GalleryFilmstripThumbnail` en `FilePreviewDialog.kt`)—, donde la app
 * está mostrando LA FOTO DE LA PERSONA, no su propia identidad visual.
 * Usar un tono de la paleta de marca ahí (como se hizo en un primer
 * intento con `SurfaceTintedElevated`) hace que cualquier miniatura se
 * vea "teñida de morado/azul de la app" — exactamente el defecto
 * reportado y corregido en FASE 17. Mismo criterio que ya sigue, por
 * ejemplo, cualquier visor de fotos profesional (Google Fotos, Apple
 * Fotos): su chrome de navegación es neutro, nunca con el color de marca
 * de la empresa, para no competir con la foto que se está mostrando.
 */
val NeutralChromeGray = Color(0xFF57534E)

/**
 * Verde chroma-key (croma), el mismo tono que usan los telones físicos de
 * cine/TV (RGB 0,177,64 — separa bien de tonos de piel y evita "spill").
 * Es el fondo POR DEFECTO del lienzo de preview/edición cuando no hay
 * nada cubriendo esa zona: así cualquier capa que el usuario recorte o
 * exporte queda lista para hacer chroma key en otro software si quiere,
 * o puede reemplazarlo con su propio fondo (botón "F" de importar fondo).
 */
val ChromaKeyGreen = Color(0xFF00B140)

/**
 * Paleta de colores para distinguir cada capa dentro del timeline, al
 * estilo de los canales de FL Studio Mobile (ver referencia que mandó
 * J James): cada capa nueva toma el siguiente color de esta lista, de
 * forma cíclica, según su [com.yeivikas.olyzecs.engine.scene.Layer.colorIndex]
 * — asignado UNA sola vez al crear la capa (ver LayerRepository), así el
 * color viaja CON la capa sin importar cuántas veces se reordene.
 *
 * Elegidos con suficiente saturación para leerse bien sobre el morado
 * oscuro de fondo del timeline (BrandPurpleDeep) sin llegar a competir
 * con BrandPurpleLight, que queda reservado para focos/acentos de UI.
 */
val LayerTrackColors = listOf(
    Color(0xFF2FA8A0), // teal / verde azulado
    Color(0xFF4CAF6D), // verde
    Color(0xFFD1449A), // magenta / rosa
    Color(0xFFE08A3C), // naranja
    Color(0xFF4C7FD6), // azul
    Color(0xFFC94F4F), // rojo ladrillo
    Color(0xFFC9A63C), // ámbar / mostaza
    Color(0xFF8B6FD1), // lavanda
    Color(0xFF3CC9C0), // cian
    Color(0xFF7FB33C)  // lima
)

/** Devuelve el color de paleta que le toca a una capa según su colorIndex, cíclico. */
fun layerTrackColor(colorIndex: Int): Color {
    val size = LayerTrackColors.size
    val safeIndex = ((colorIndex % size) + size) % size
    return LayerTrackColors[safeIndex]
}

/**
 * Realce de acento de una fila suelta (ver [GroupGradientEngine.strongAccent]):
 * misma regla única, sin fórmula propia en la UI. SIEMPRE devuelve alfa 100 %:
 * es para acentos de UI (bordes, contorno del diálogo, panel de opciones) que
 * deben leerse sólidos y nítidos sin importar si el usuario eligió una capa
 * semitransparente; un borde que se desvanece con la opacidad se vería roto.
 */
private fun strongVariant(base: Color): Color =
    Color(GroupGradientEngine.strongAccent(base.toArgb()))

/** Única regla de realce de la fila: un color de grupo se queda exacto; cualquier otro pasa por [strongVariant]. */
private fun rowAccentColor(base: Color, inGradientGroup: Boolean): Color =
    if (inGradientGroup) base else strongVariant(base)

/**
 * Color de identidad REAL de una capa: si tiene un DEGRADADO activo
 * ([com.yeivikas.olyzecs.engine.scene.Layer.useGradientColor]), esto
 * entrega un color plano (el punto medio de sus dos extremos) — pensado para los
 * pocos lugares que necesitan sí o sí un solo Color (bordes de diálogo,
 * Surface.color, etc.), NO para donde se vea el degradado de verdad (ver
 * [effectiveLayerBrush] para eso). Si no hay degradado, se comporta igual
 * que antes: manda el color sólido elegido a mano en la rueda
 * ([com.yeivikas.olyzecs.engine.scene.Layer.customColorArgb]) sobre
 * cualquier otra cosa — la rueda permite cualquier matiz/saturación/
 * brillo, no solo los 10 de [LayerTrackColors]. Si todavía no personalizó
 * nada, cae al color cíclico de la paleta fija de siempre (colorIndex).
 */
fun effectiveLayerColor(layer: com.yeivikas.olyzecs.engine.scene.Layer): Color =
    resolveRowColor(
        useGradient = layer.useGradientColor,
        startArgb = layer.customGradientStartArgb,
        endArgb = layer.customGradientEndArgb,
        customArgb = layer.customColorArgb,
        fallback = layerTrackColor(layer.colorIndex)
    )

/**
 * Resolución ÚNICA del color plano de una fila (capa o carril de audio): un
 * degradado propio activo se aplana a su punto medio; si no, manda el color
 * sólido elegido; si tampoco hay, [fallback].
 */
private fun resolveRowColor(
    useGradient: Boolean,
    startArgb: Int?,
    endArgb: Int?,
    customArgb: Int?,
    fallback: Color
): Color {
    if (useGradient && startArgb != null && endArgb != null) return blendColors(Color(startArgb), Color(endArgb))
    return if (customArgb != null) Color(customArgb) else fallback
}

/** Resolución ÚNICA del [Brush] de una fila: degradado propio real si está activo; si no, el color plano [flat]. */
private fun resolveRowBrush(
    useGradient: Boolean,
    startArgb: Int?,
    endArgb: Int?,
    angleDegrees: Float,
    isRadial: Boolean,
    flat: Color
): Brush {
    if (useGradient && startArgb != null && endArgb != null) {
        return gradientBrushFor(Color(startArgb), Color(endArgb), angleDegrees, isRadial)
    }
    return SolidColor(flat)
}

/**
 * Color plano de un degradado de dos extremos para los lugares que no pueden
 * pintar un [Brush] (ver [effectiveLayerColor]): su punto medio según
 * [GroupGradientEngine.midpoint] — el mismo espacio de color (Oklab) que el
 * degradado repartido, no un promedio RGB propio, que daba tonos apagados. Se
 * conserva la opacidad: un degradado con ambos extremos al 40 % se aplana a
 * ~40 %, no a 100 %.
 */
private fun blendColors(a: Color, b: Color): Color =
    Color(GroupGradientEngine.midpoint(a.toArgb(), b.toArgb())).copy(alpha = (a.alpha + b.alpha) / 2f)

/**
 * Versión [Brush] de [effectiveLayerColor]: si la capa tiene un degradado
 * activo, entrega el degradado REAL de sus dos colores (de arriba A hacia
 * abajo B) en vez de aplanarlo — para usar en cualquier
 * `Modifier.background(brush = ...)` que deba mostrar el degradado de
 * verdad (miniatura de la capa, barra de identidad de la fila). Si no hay
 * degradado activo, es el mismo color sólido de siempre, envuelto en
 * [SolidColor] para que el tipo siga siendo Brush en ambos casos.
 */
fun effectiveLayerBrush(layer: com.yeivikas.olyzecs.engine.scene.Layer): Brush =
    resolveRowBrush(
        useGradient = layer.useGradientColor,
        startArgb = layer.customGradientStartArgb,
        endArgb = layer.customGradientEndArgb,
        angleDegrees = layer.gradientAngleDegrees,
        isRadial = layer.gradientIsRadial,
        flat = effectiveLayerColor(layer)
    )

/**
 * Construye el [Brush] real de un degradado de dos colores, respetando
 * [isRadial] (circular, ignora el ángulo) o, si es lineal, [angleDegrees]
 * en CUALQUIER grado — no solo vertical/horizontal/diagonal fijo. Un
 * [Brush.linearGradient] normal necesita un [androidx.compose.ui.geometry.Offset]
 * de inicio/fin en píxeles, que no se conocen hasta que Compose dibuja
 * de verdad (acá solo se está construyendo el Brush, sin tamaño todavía)
 * — por eso para un ángulo arbitrario se usa un [AngledLinearGradientBrush]
 * a medida, que recién calcula esos dos puntos dentro de [ShaderBrush.createShader],
 * al momento real del dibujo, cuando el tamaño ya se conoce.
 */
fun gradientBrushFor(colorA: Color, colorB: Color, angleDegrees: Float, isRadial: Boolean): Brush {
    if (isRadial) return Brush.radialGradient(listOf(colorA, colorB))
    return AngledLinearGradientBrush(listOf(colorA, colorB), angleDegrees)
}

/**
 * Degradado lineal a un ángulo arbitrario en grados (0°=izquierda→derecha,
 * 90°=arriba→abajo, igual convención que un sistema de coordenadas de
 * pantalla normal). [ShaderBrush.createShader] recibe el tamaño real del
 * área a pintar, así que acá sí se puede calcular con trigonometría un
 * punto de inicio/fin que cubra la diagonal completa del rectángulo en
 * la dirección pedida — cosa que un [Brush.linearGradient] con offsets
 * fijos no puede hacer para un ángulo cualquiera.
 */
private class AngledLinearGradientBrush(
    private val colors: List<Color>,
    private val angleDegrees: Float
) : androidx.compose.ui.graphics.ShaderBrush() {
    // Igualdad ESTRUCTURAL: effectiveLayerBrush se recalcula en cada
    // recomposición; sin esto cada instancia nueva se vería "distinta" para
    // Compose y la fila se repintaría aunque el degradado no haya cambiado.
    override fun equals(other: Any?): Boolean =
        other is AngledLinearGradientBrush && colors == other.colors && angleDegrees == other.angleDegrees

    override fun hashCode(): Int = 31 * colors.hashCode() + angleDegrees.hashCode()

    override fun createShader(size: androidx.compose.ui.geometry.Size): android.graphics.Shader {
        val angleRad = Math.toRadians(angleDegrees.toDouble())
        val dx = kotlin.math.cos(angleRad).toFloat()
        val dy = kotlin.math.sin(angleRad).toFloat()
        // Media diagonal: la distancia mínima desde el centro que
        // garantiza cubrir el rectángulo entero en cualquier dirección.
        val halfDiagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height) / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val startX = centerX - dx * halfDiagonal
        val startY = centerY - dy * halfDiagonal
        val endX = centerX + dx * halfDiagonal
        val endY = centerY + dy * halfDiagonal
        return android.graphics.LinearGradient(
            startX, startY, endX, endY,
            colors.map { it.toArgb() }.toIntArray(),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
}

/**
 * Color de la columna, bordes y paneles de la fila de una capa. Una capa que
 * pertenece a un degradado repartido entre varias filas
 * ([com.yeivikas.olyzecs.engine.scene.Layer.gradientGroup]) muestra su color
 * EXACTO — es el tono que le tocó, y realzarlo rompería el degradado —; el
 * resto de capas conserva el realce de siempre ([strongVariant]).
 */
fun effectiveLayerColorStrong(layer: com.yeivikas.olyzecs.engine.scene.Layer): Color =
    rowAccentColor(effectiveLayerColor(layer), inGradientGroup = layer.gradientGroup != null)

/**
 * Equivalente exacto de [effectiveLayerColor] para el CARRIL de audio del
 * proyecto (ver AudioClip.kt — FASE 1, "carril con varios clips": el
 * color/degradado es identidad del CARRIL, no de ningún clip suelto, por
 * eso recibe [com.yeivikas.olyzecs.engine.audio.AudioTrack] y no
 * `AudioClip`) — mismas reglas que una capa: un degradado activo se
 * aplana a su punto medio, si no hay degradado manda el color
 * sólido personalizado, y si tampoco hay color personalizado cae al "de
 * fábrica" ([fallback], el AUDIO_TRACK_COLOR de AudioTrackRow.kt) en vez
 * del cíclico por colorIndex — un carril de audio no tiene colorIndex ni
 * ningún píxel del que extraer un dominante.
 */
fun effectiveAudioColor(audioTrack: com.yeivikas.olyzecs.engine.audio.AudioTrack, fallback: Color): Color =
    resolveRowColor(
        useGradient = audioTrack.useGradientColor,
        startArgb = audioTrack.customGradientStartArgb,
        endArgb = audioTrack.customGradientEndArgb,
        customArgb = audioTrack.customColorArgb,
        fallback = fallback
    )

/** Equivalente exacto de [effectiveLayerBrush] para el carril de audio — ver [effectiveAudioColor]. */
fun effectiveAudioBrush(audioTrack: com.yeivikas.olyzecs.engine.audio.AudioTrack, fallback: Color): Brush =
    resolveRowBrush(
        useGradient = audioTrack.useGradientColor,
        startArgb = audioTrack.customGradientStartArgb,
        endArgb = audioTrack.customGradientEndArgb,
        angleDegrees = audioTrack.gradientAngleDegrees,
        isRadial = audioTrack.gradientIsRadial,
        flat = effectiveAudioColor(audioTrack, fallback)
    )

/**
 * Muestreos opacos del fondo del panel de opciones de una fila, para calcular el contraste de sus
 * íconos ([rowIconPalette]): el color plano y, con degradado propio, también sus dos extremos (el
 * panel los muestra todos); más la variante realzada [strong], que usa el acordeón del pie. Un
 * color con opacidad se compone sobre el fondo de la lista y, con la capa oculta ([dimmed]), sobre el
 * 40 % de negro con que el panel se oscurece.
 */
private fun panelBackgrounds(
    useGradient: Boolean,
    startArgb: Int?,
    endArgb: Int?,
    flat: Color,
    strong: Color,
    dimmed: Boolean
): List<Color> {
    val shown = if (useGradient && startArgb != null && endArgb != null) {
        listOf(Color(startArgb), flat, Color(endArgb))
    } else {
        listOf(flat)
    }
    return (shown + strong).map { color ->
        val opaque = color.compositeOver(SurfaceTintedDark)
        if (dimmed) Color.Black.copy(alpha = 0.4f).compositeOver(opaque) else opaque
    }
}

/** Fondos del panel de opciones de una capa de imagen, ver [panelBackgrounds]. */
fun layerPanelBackgrounds(layer: com.yeivikas.olyzecs.engine.scene.Layer): List<Color> =
    panelBackgrounds(
        useGradient = layer.useGradientColor,
        startArgb = layer.customGradientStartArgb,
        endArgb = layer.customGradientEndArgb,
        flat = effectiveLayerColor(layer),
        strong = effectiveLayerColorStrong(layer),
        dimmed = !layer.visible
    )

/** Fondos del panel de opciones de un carril de audio, ver [panelBackgrounds]. */
fun audioPanelBackgrounds(audioTrack: com.yeivikas.olyzecs.engine.audio.AudioTrack, fallback: Color): List<Color> =
    panelBackgrounds(
        useGradient = audioTrack.useGradientColor,
        startArgb = audioTrack.customGradientStartArgb,
        endArgb = audioTrack.customGradientEndArgb,
        flat = effectiveAudioColor(audioTrack, fallback),
        strong = effectiveAudioColorStrong(audioTrack, fallback),
        dimmed = false
    )

/**
 * Color de la SEÑAL (onda) de un carril de audio a partir del tono de identidad
 * [trackColorStrong] de su fila: ese tono aclarado hacia blanco, para que la onda
 * resalte sobre el fondo. Es la ÚNICA definición: la usan la barra del carril
 * (`AudioTrackRow`) y la ventana ampliada del clip (`AudioClipDetailHost`), así que
 * ambas muestran siempre exactamente el mismo color.
 */
fun audioSignalColor(trackColorStrong: Color): Color =
    lerp(trackColorStrong, Color.White, AUDIO_SIGNAL_WHITE_MIX)

/** Cuánto se aclara hacia blanco el tono de la fila para obtener el color de la señal. */
private const val AUDIO_SIGNAL_WHITE_MIX = 0.55f

/** Equivalente de [effectiveLayerColorStrong] para un carril de audio, con la misma regla de grupo. */
fun effectiveAudioColorStrong(audioTrack: com.yeivikas.olyzecs.engine.audio.AudioTrack, fallback: Color): Color =
    rowAccentColor(effectiveAudioColor(audioTrack, fallback), inGradientGroup = audioTrack.gradientGroup != null)

/**
 * El degradado oficial de toda la app: morado arriba, azul abajo, con el
 * morado ocupando la mayor parte del recorrido (~65-70%) antes de
 * transicionar a azul cerca del final, tal como pide la identidad de
 * marca ("que predomine el morado").
 */
val OlyzeGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to BrandPurpleDeep,
        0.75f to BrandPurple,
        0.96f to BrandPurpleLight.copy(alpha = 0.9f).compositeOverPurple(),
        1.0f to BrandBlue
    )
)

// Pequeño helper para que el stop de transición se sienta como una mezcla
// morado→azul y no un salto brusco de morado vivo a azul.
private fun Color.compositeOverPurple(): Color = Color(
    red = (this.red * 0.7f + BrandBlue.red * 0.3f),
    green = (this.green * 0.7f + BrandBlue.green * 0.3f),
    blue = (this.blue * 0.7f + BrandBlue.blue * 0.3f),
    alpha = 1f
)

private val OlyzeColorScheme = darkColorScheme(
    primary = BrandPurpleLight,
    onPrimary = Color.White,
    primaryContainer = BrandPurple,
    onPrimaryContainer = Color.White,
    secondary = BrandBlueLight,
    onSecondary = Color.White,
    secondaryContainer = BrandBlue,
    onSecondaryContainer = Color.White,
    tertiary = BrandBlueLight,
    onTertiary = Color.White,
    background = BrandPurpleDeep,
    onBackground = Color.White,
    surface = SurfaceTintedDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceTintedElevated,
    onSurfaceVariant = Color(0xFFD6CFEF),
    outline = Color(0xFF8A7DB8)
)

@Composable
fun OlyzeTheme(content: @Composable () -> Unit) {
    // La app tiene una identidad de marca fija (degradado morado→azul) que
    // no depende del tema claro/oscuro del sistema — siempre se usa el
    // esquema oscuro de marca para mantener congruencia en todas las
    // pantallas y opciones.
    MaterialTheme(
        colorScheme = OlyzeColorScheme,
        content = content
    )
}
