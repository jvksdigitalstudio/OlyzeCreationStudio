package com.yeivikas.olyzecs.data

import android.graphics.Bitmap
import android.graphics.Color as AColor
import com.yeivikas.olyzecs.debug.AppLogger

/**
 * Extrae un color representativo de una imagen, para pintar la barra de
 * su capa en el timeline apenas se importa (ver LayerRepository.importAsLayers).
 *
 * No usa androidx.palette (no está entre las dependencias del proyecto y
 * agregarla obliga a bajar una librería nueva sin necesidad real): esto es
 * un muestreo manual, chico y autocontenido, pensado para las imágenes
 * PNG recortadas típicas de este editor (personaje/objeto sobre fondo
 * transparente, tipo las capturas de ejemplo) — por eso:
 *  - Ignora píxeles transparentes o casi transparentes (alpha bajo), para
 *    no promediar con el "vacío" alrededor del recorte.
 *  - Ignora píxeles casi blancos/negros/grises (poca saturación con
 *    brillo muy alto o muy bajo), que no aportan identidad de color y
 *    solo empujan el promedio hacia un gris sin sentido.
 *  - Agrupa por matiz (hue) en baldes de 10° y pesa cada píxel por
 *    saturación×brillo×alpha, así el balde ganador es el tono más VIVO y
 *    predominante a simple vista, no un promedio aritmético que puede caer
 *    en un marrón intermedio que no está en ningún lado de la imagen real.
 */
object ColorExtraction {

    private const val TAG = "ColorExtraction"

    /** Lado máximo (en px) al que se reduce la imagen antes de muestrear — de sobra para estimar el color dominante y rápido incluso con fotos grandes. */
    private const val MAX_SAMPLE_SIDE = 48

    fun dominantColor(source: Bitmap): Int {
        if (source.width <= 0 || source.height <= 0) return FALLBACK_NEUTRAL

        val scale = MAX_SAMPLE_SIDE.toFloat() / maxOf(source.width, source.height)
        val sampled = if (scale < 1f) {
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            runCatching { Bitmap.createScaledBitmap(source, w, h, true) }
                .onFailure { AppLogger.w(TAG, "No se pudo reducir la imagen para muestrear su color dominante, se usa el original", it) }
                .getOrNull() ?: source
        } else source

        return try {
            val width = sampled.width
            val height = sampled.height
            val pixels = IntArray(width * height)
            sampled.getPixels(pixels, 0, width, 0, 0, width, height)
            extractFrom(pixels)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error extrayendo el color dominante de una imagen, se usa el color neutro de respaldo", t)
            FALLBACK_NEUTRAL
        } finally {
            if (sampled !== source) sampled.recycle()
        }
    }

    /**
     * Un "candidato" a color dominante: un balde de píxeles parecidos
     * (mismo matiz, o acromático) con su peso total y la suma de sus
     * componentes RGB — de acá sale el promedio real del balde ganador.
     */
    private class Bucket {
        var weight = 0.0
        var r = 0.0
        var g = 0.0
        var b = 0.0

        fun add(pr: Int, pg: Int, pb: Int, w: Double) {
            weight += w
            r += pr * w
            g += pg * w
            b += pb * w
        }

        fun toColorOrNull(): Int? {
            if (weight <= 0.0) return null
            return AColor.rgb(
                (r / weight).toInt().coerceIn(0, 255),
                (g / weight).toInt().coerceIn(0, 255),
                (b / weight).toInt().coerceIn(0, 255)
            )
        }
    }

    private fun extractFrom(pixels: IntArray): Int {
        // Baldes de matiz (colores "vivos": rojo, azul, verde, etc), uno
        // cada 10°, pesados por viveza (saturación×brillo) — como antes,
        // así un logo chico pero muy saturado le puede ganar a un fondo
        // grande y apagado.
        val hueBuckets = Array(HUE_BUCKETS) { Bucket() }

        // --- BUG REAL corregido acá: antes, TODO píxel sin matiz
        // reconocible (gris/negro/blanco) se descartaba por completo de
        // la competencia (`continue`, sin sumar a ningún balde) — solo
        // sobrevivía en el promedio de emergencia ([fallback], que ni
        // siquiera se usa si algún matiz vivo ganó, aunque sea un pixelito
        // perdido). Resultado: una imagen con fondo NEGRO cubriendo el
        // 90% del área pero con un logo chico de color, terminaba
        // pintando la barra de la capa con el color del logo, nunca con
        // el negro que en verdad predomina a simple vista.
        //
        // Ahora el negro/gris/blanco también arma sus propios baldes
        // (acromático, tres franjas de brillo) pesados por ÁREA real
        // (cuánto cubren, vía alphaWeight) — no por viveza, porque un
        // negro puro no tiene saturación que pesar. Al final se compara
        // el balde de matiz más fuerte contra estos tres, balde por
        // balde, y gana el que de verdad ocupe más superficie de la
        // imagen — ya sea un color vivo O un negro/gris/blanco dominante.
        val blackBucket = Bucket()
        val grayBucket = Bucket()
        val whiteBucket = Bucket()

        var fallbackR = 0.0
        var fallbackG = 0.0
        var fallbackB = 0.0
        var fallbackWeight = 0.0

        val hsv = FloatArray(3)
        for (px in pixels) {
            val a = (px ushr 24) and 0xFF
            if (a < MIN_ALPHA) continue
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val alphaWeight = a / 255.0

            fallbackR += r * alphaWeight
            fallbackG += g * alphaWeight
            fallbackB += b * alphaWeight
            fallbackWeight += alphaWeight

            AColor.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            val value = hsv[2]

            if (sat < MIN_SATURATION_FOR_LIGHT) {
                // Sin matiz reconocible: negro, gris o blanco según su
                // brillo — pesado por ÁREA (alphaWeight), no por viveza.
                val bucket = when {
                    value < MIN_VALUE_FOR_BLACK -> blackBucket
                    value > MAX_VALUE_FOR_GRAY -> whiteBucket
                    else -> grayBucket
                }
                bucket.add(r, g, b, alphaWeight)
                continue
            }
            if (value < MIN_VALUE) continue // demasiado oscuro para distinguir el matiz, y muy poco saturado para contar como "negro puro"

            val weight = (BASE_WEIGHT + sat) * value * alphaWeight
            val bucketIndex = (hsv[0] / (360f / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
            hueBuckets[bucketIndex].add(r, g, b, weight)
        }

        val bestHueBucket = hueBuckets.maxByOrNull { it.weight }
        val winner = listOfNotNull(bestHueBucket, blackBucket, grayBucket, whiteBucket)
            .maxByOrNull { it.weight }
        winner?.toColorOrNull()?.let { return it }

        // Nada compitió (imagen 100% transparente): cae al promedio
        // general de lo opaco, y si tampoco hay nada opaco, al neutro fijo.
        if (fallbackWeight <= 0.0) return FALLBACK_NEUTRAL
        return AColor.rgb(
            (fallbackR / fallbackWeight).toInt().coerceIn(0, 255),
            (fallbackG / fallbackWeight).toInt().coerceIn(0, 255),
            (fallbackB / fallbackWeight).toInt().coerceIn(0, 255)
        )
    }

    private const val HUE_BUCKETS = 36
    private const val MIN_ALPHA = 40
    private const val MIN_VALUE = 0.08f
    private const val MIN_VALUE_FOR_BLACK = 0.18f
    private const val MIN_SATURATION_FOR_LIGHT = 0.12f
    private const val MAX_VALUE_FOR_GRAY = 0.92f
    private const val BASE_WEIGHT = 0.15
    // Gris neutro de la propia UI (ver Theme.kt) — solo se usa si la
    // imagen es 100% transparente y no hay ningún píxel de qué sacar color.
    private const val FALLBACK_NEUTRAL = 0xFF6B6B78.toInt()

    // ================================================================
    // Paleta completa + recoloreo (panel "Ajustes y parámetros de
    // edición" del modo edición dedicado — ver EditorScreen.kt, el
    // pedido: "que extraiga todos los colores y los divida en cuadros,
    // cada cuadro seleccionable en la rueda para recolorear en vivo").
    // ================================================================

    /** Lado máximo (px) al que se reduce la imagen para EXTRAER la paleta — más grande que MAX_SAMPLE_SIDE (que es solo para 1 color dominante) porque acá hace falta distinguir colores chicos/finos, pero sigue siendo chico para que la extracción sea instantánea. */
    private const val MAX_PALETTE_SAMPLE_SIDE = 160

    /**
     * Extrae TODOS los colores distintos de [source] (no solo el
     * dominante): agrupa píxeles parecidos en baldes cuantizados de
     * [quantStep] por canal (para que el antialiasing de un borde no
     * genere decenas de cuadritos casi idénticos), pesa cada balde por
     * cuánta superficie real ocupa (alpha × cantidad de píxeles) y
     * devuelve como mucho [maxColors] colores, ordenados de más a menos
     * presente en la imagen. Si la imagen tiene pocos colores (un logo
     * plano de 2-3 tonos), el resultado naturalmente tiene pocos
     * elementos — no se "rellena" a la fuerza.
     */
    fun extractPalette(source: Bitmap, maxColors: Int = 24, quantStep: Int = 24): List<Int> {
        if (source.width <= 0 || source.height <= 0) return emptyList()
        val scale = MAX_PALETTE_SAMPLE_SIDE.toFloat() / maxOf(source.width, source.height)
        val sampled = if (scale < 1f) {
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            runCatching { Bitmap.createScaledBitmap(source, w, h, true) }
                .onFailure { AppLogger.w(TAG, "No se pudo reducir la imagen para extraer su paleta, se usa el original", it) }
                .getOrNull() ?: source
        } else source

        return try {
            val width = sampled.width
            val height = sampled.height
            val pixels = IntArray(width * height)
            sampled.getPixels(pixels, 0, width, 0, 0, width, height)

            val buckets = HashMap<Long, PaletteBucket>()
            for (px in pixels) {
                val a = (px ushr 24) and 0xFF
                if (a < MIN_ALPHA) continue
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val key = paletteBucketKey(r, g, b, quantStep)
                val bucket = buckets.getOrPut(key) { PaletteBucket() }
                bucket.add(r, g, b, a / 255.0)
            }
            buckets.values
                .mapNotNull { bucket -> bucket.toColorOrNull()?.let { color -> color to bucket.weight } }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(maxColors)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Error extrayendo la paleta de colores de una imagen", t)
            emptyList()
        } finally {
            if (sampled !== source) sampled.recycle()
        }
    }

    private class PaletteBucket {
        var weight = 0.0
        var r = 0.0
        var g = 0.0
        var b = 0.0
        fun add(pr: Int, pg: Int, pb: Int, w: Double) {
            weight += w
            r += pr * w
            g += pg * w
            b += pb * w
        }
        fun toColorOrNull(): Int? {
            if (weight <= 0.0) return null
            return AColor.rgb(
                (r / weight).toInt().coerceIn(0, 255),
                (g / weight).toInt().coerceIn(0, 255),
                (b / weight).toInt().coerceIn(0, 255)
            )
        }
    }

    private fun paletteBucketKey(r: Int, g: Int, b: Int, step: Int): Long {
        val qr = (r / step).toLong()
        val qg = (g / step).toLong()
        val qb = (b / step).toLong()
        return (qr shl 20) or (qg shl 10) or qb
    }

    /**
     * Recolorea [source] aplicando [remaps] (color original extraído →
     * color nuevo elegido en la rueda). Para cada píxel, mezcla la
     * influencia de TODOS los remaps a la vez, pesada por qué tan cerca
     * está el píxel de cada color original — así se aplica a todos los
     * píxeles parecidos al color que el usuario tocó — el sombreado/
     * antialiasing real casi nunca es un único valor puro, nunca
     * matchearía con una igualdad exacta.
     *
     * BUG REAL corregido acá: antes esto era un corte binario — "gana el
     * remap más cercano si su distancia entra dentro de [toleranceRgb],
     * si no, el píxel queda 100% intacto". En una zona con degradado o
     * antialiasing (la inmensa mayoría de una foto real), un píxel podía
     * quedar apenas ADENTRO del radio y su vecino apenas AFUERA — uno se
     * repintaba entero, el otro nada — y ese salto de golpe entre "100%
     * nuevo color" y "0%, sin tocar" en píxeles vecinos es exactamente lo
     * que se ve como bloques/bandas pixeladas en vez de una transición
     * suave.
     *
     * Ahora cada remap pesa según una caída suave (curva coseno, sin
     * escalón) desde el centro de su tolerancia hasta el borde — no
     * "todo o nada" — y el resultado final es una mezcla ponderada de
     * TODOS los remaps que tengan algo de influencia sobre ese píxel
     * (no solo el ganador), sumada al color original con el peso
     * restante. Un píxel en el borde entre dos zonas de color queda
     * parcialmente mezclado entre ambas, no con un salto brusco.
     *
     * [intensity] (0..1) es el control "Opacidad" del panel de Recolor:
     * mezcla el resultado recoloreado con el píxel ORIGINAL sin tocar,
     * DESPUÉS de todo lo anterior — 1f = el recolor de siempre (a pleno),
     * 0f = imagen intacta, valores intermedios dejan pasar algo del color
     * de base por debajo del nuevo tono (útil para look "desteñido" /
     * recolor sutil en vez de plancha total, sin tener que tocar la
     * saturación del propio color elegido).
     *
     * Conserva el brillo (V de HSV) original de cada píxel y solo
     * reemplaza matiz+saturación por los del color nuevo — así una
     * imagen con sombreado o brillo (ej. un logo metálico con
     * degradado) no queda plana al recolorear: mantiene su forma/relieve,
     * solo cambia de color base. Nunca modifica [source]: siempre
     * devuelve un bitmap nuevo.
     */
    fun recolor(source: Bitmap, remaps: Map<Int, Int>, toleranceRgb: Int = 42, intensity: Float = 1f): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        if (remaps.isNotEmpty() && intensity > 0f) {
            val fromEntries = remaps.entries.map { (from, to) ->
                val fromRgb = intArrayOf((from ushr 16) and 0xFF, (from ushr 8) and 0xFF, from and 0xFF)
                val toHsv = FloatArray(3)
                AColor.colorToHSV(to, toHsv)
                fromRgb to toHsv
            }
            // El radio de influencia es un poco más generoso que la
            // tolerancia original: como ahora la mezcla decae suave en
            // vez de cortar en seco, un radio igual al viejo dejaba
            // franjas visibles justo donde antes terminaba el círculo
            // "sí entra". Con la caída coseno, los píxeles cerca del
            // borde ya pesan casi nada por sí solos, así que agrandar el
            // radio no reintroduce el efecto de bloque, solo suaviza más
            // la transición.
            val radius = toleranceRgb * 1.6f
            val clampedIntensity = intensity.coerceIn(0f, 1f)
            val hsv = FloatArray(3)
            for (i in pixels.indices) {
                val px = pixels[i]
                val a = (px ushr 24) and 0xFF
                if (a == 0) continue
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                AColor.RGBToHSV(r, g, b, hsv)
                val originalValue = hsv[2]

                var totalWeight = 0f
                var mixR = 0f
                var mixG = 0f
                var mixB = 0f
                for ((fromRgb, toHsv) in fromEntries) {
                    val dr = r - fromRgb[0]
                    val dg = g - fromRgb[1]
                    val db = b - fromRgb[2]
                    val dist = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
                    if (dist >= radius) continue
                    // Caída coseno de 1 (en el centro exacto del color
                    // original) a 0 (en el borde del radio) — continua y
                    // sin escalón, a diferencia del corte binario de antes.
                    val t = dist / radius
                    val weight = (0.5f * (1f + kotlin.math.cos(t * Math.PI.toFloat()))).coerceIn(0f, 1f)
                    if (weight <= 0f) continue
                    val remapColor = AColor.HSVToColor(floatArrayOf(toHsv[0], toHsv[1], originalValue))
                    mixR += ((remapColor ushr 16) and 0xFF) * weight
                    mixG += ((remapColor ushr 8) and 0xFF) * weight
                    mixB += (remapColor and 0xFF) * weight
                    totalWeight += weight
                }

                if (totalWeight <= 0f) continue
                // Si varios remaps se superponen sobre el mismo píxel, no
                // dejar que su peso combinado "sature" más allá del color
                // original — se normaliza a como mucho 1, y el resto (si
                // sobra) se completa con el color de origen sin tocar.
                // La "Opacidad" del panel (intensity) se aplica ACÁ, como
                // un tope adicional sobre ese mismo blend: 1f no cambia
                // nada (el tope sigue siendo 1), valores menores bajan el
                // techo del blend así el color original vuelve a asomar
                // más, sin importar qué tan cerca haya caído el píxel del
                // centro de su balde.
                val blend = totalWeight.coerceAtMost(1f) * clampedIntensity
                val keep = 1f - blend
                val newR = (mixR / totalWeight) * blend + r * keep
                val newG = (mixG / totalWeight) * blend + g * keep
                val newB = (mixB / totalWeight) * blend + b * keep
                pixels[i] = (a shl 24) or
                    (newR.toInt().coerceIn(0, 255) shl 16) or
                    (newG.toInt().coerceIn(0, 255) shl 8) or
                    newB.toInt().coerceIn(0, 255)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
