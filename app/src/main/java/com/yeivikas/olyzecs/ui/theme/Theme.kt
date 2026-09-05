package com.yeivikas.olyzecs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb

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
val BrandBlueDeep = Color(0xFF10214F)     // cierre inferior, azul oscuro

// --- Superficies neutras con tinte morado (para paneles, tarjetas, etc.) ---
val SurfaceTintedDark = Color(0xFF16102C)
val SurfaceTintedElevated = Color(0xFF241A46)

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
 * Versión "fuerte" del color de identidad de una capa: mismo matiz (hue) que
 * [layerTrackColor], pero con la saturación llevada al máximo y opacidad
 * completa — se usa para el recuadro de la miniatura y para el panel de
 * opciones desplegado de esa capa, que deben leerse más "pintados"/potentes
 * que el resto de la barra (esa usa el color suave, a baja opacidad, tal
 * cual ya estaba). Mismo tono en toda la fila, solo cambia cuánto se nota.
 */
fun layerTrackColorStrong(colorIndex: Int): Color = strongVariant(layerTrackColor(colorIndex))

/**
 * Sube la saturación de cualquier color a tope (mismo matiz, "más pintado")
 * y realza levemente el brillo — la misma transformación que ya usaba
 * [layerTrackColorStrong] para la paleta fija, factorizada acá para que
 * también la puedan usar los colores personalizados de la rueda de color
 * (ver [effectiveLayerColorStrong]).
 */
private fun strongVariant(base: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (base.red * 255f).toInt().coerceIn(0, 255),
        (base.green * 255f).toInt().coerceIn(0, 255),
        (base.blue * 255f).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[1] = 1f // saturación al máximo — el mismo matiz pero "más pintado"
    hsv[2] = (hsv[2] * 1.05f).coerceIn(0f, 1f) // brillo levemente realzado
    // A propósito SIEMPRE devuelve alfa=100% (HSVToColor(FloatArray) de
    // Android no admite alfa, y no se lo pedimos): esta variante es para
    // acentos de UI (bordes, contorno del diálogo, panel de opciones) que
    // deben leerse sólidos y nítidos SIEMPRE, sin importar si el usuario
    // eligió una capa semitransparente — un borde de acento que se
    // desvanece según la opacidad de la capa se vería roto/apagado.
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Color de identidad REAL de una capa: si tiene un DEGRADADO activo
 * ([com.yeivikas.olyzecs.engine.scene.Layer.useGradientColor]), esto
 * entrega un color plano promedio de sus dos extremos — pensado para los
 * pocos lugares que necesitan sí o sí un solo Color (bordes de diálogo,
 * Surface.color, etc.), NO para donde se vea el degradado de verdad (ver
 * [effectiveLayerBrush] para eso). Si no hay degradado, se comporta igual
 * que antes: manda el color sólido elegido a mano en la rueda
 * ([com.yeivikas.olyzecs.engine.scene.Layer.customColorArgb]) sobre
 * cualquier otra cosa — la rueda permite cualquier matiz/saturación/
 * brillo, no solo los 10 de [LayerTrackColors]. Si todavía no personalizó
 * nada, cae al color cíclico de la paleta fija de siempre (colorIndex).
 */
fun effectiveLayerColor(layer: com.yeivikas.olyzecs.engine.scene.Layer): Color {
    if (layer.useGradientColor) {
        val start = layer.customGradientStartArgb
        val end = layer.customGradientEndArgb
        if (start != null && end != null) return blendColors(Color(start), Color(end))
    }
    val custom = layer.customColorArgb
    return if (custom != null) Color(custom) else layerTrackColor(layer.colorIndex)
}

/** Promedio simple de dos colores — usado solo como aplanado de respaldo para degradados en lugares que no pueden pintar un Brush (ver [effectiveLayerColor]). */
private fun blendColors(a: Color, b: Color): Color = Color(
    red = (a.red + b.red) / 2f,
    green = (a.green + b.green) / 2f,
    blue = (a.blue + b.blue) / 2f,
    // Antes esto forzaba alfa=1 sin importar la opacidad real de los dos
    // extremos — con el motor de color ahora soportando semitransparencia
    // de verdad, un degradado con ambos extremos al 40% de opacidad debía
    // aplanarse a ~40%, no a 100% opaco.
    alpha = (a.alpha + b.alpha) / 2f
)

/**
 * Versión [Brush] de [effectiveLayerColor]: si la capa tiene un degradado
 * activo, entrega el degradado REAL de sus dos colores (de arriba A hacia
 * abajo B) en vez de aplanarlo — para usar en cualquier
 * `Modifier.background(brush = ...)` que deba mostrar el degradado de
 * verdad (miniatura de la capa, barra de identidad de la fila). Si no hay
 * degradado activo, es el mismo color sólido de siempre, envuelto en
 * [SolidColor] para que el tipo siga siendo Brush en ambos casos.
 */
fun effectiveLayerBrush(layer: com.yeivikas.olyzecs.engine.scene.Layer): Brush {
    if (layer.useGradientColor) {
        val start = layer.customGradientStartArgb
        val end = layer.customGradientEndArgb
        if (start != null && end != null) {
            return gradientBrushFor(Color(start), Color(end), layer.gradientAngleDegrees, layer.gradientIsRadial)
        }
    }
    return SolidColor(effectiveLayerColor(layer))
}

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

/** Versión "fuerte" (ver [strongVariant]) de [effectiveLayerColor]. */
fun effectiveLayerColorStrong(layer: com.yeivikas.olyzecs.engine.scene.Layer): Color =
    strongVariant(effectiveLayerColor(layer))

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

/** Variante más sutil del degradado, para paneles internos (no pantalla completa). */
val OlyzeGradientSubtle = Brush.verticalGradient(
    colors = listOf(SurfaceTintedDark, SurfaceTintedElevated, SurfaceTintedDark)
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
