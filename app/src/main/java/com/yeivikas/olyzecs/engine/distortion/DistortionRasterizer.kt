package com.yeivikas.olyzecs.engine.distortion

import android.graphics.Bitmap

/**
 * Convierte una [DistortionField] (matemática pura, sin Android — ver ese
 * archivo) en un `Bitmap` real: por cada píxel de salida, interpola
 * bilinealmente la malla para saber de qué UV de [source] tomar el
 * color, y samplea esa fuente con interpolación bilineal — así el
 * resultado se ve fino incluso en zoom (ni la malla ni el color final
 * "escalonan"), sin tener que manipular píxel por píxel a lo bruto.
 *
 * [outWidth]/[outHeight] pueden ser DISTINTOS de las dimensiones de
 * [source]: el mismo [field] (siempre en UV, resolución independiente)
 * sirve tanto para la vista previa en vivo (bitmap chico, mientras se
 * arrastra el dedo) como para el guardado final en alta resolución
 * (recién al soltar) — mismo criterio de downsampling-para-preview /
 * recálculo-en-full-res-al-soltar que ya usa el resto del motor de
 * edición (Recolor, 3D, Efectos), ahora aplicado acá.
 */
object DistortionRasterizer {

    fun render(source: Bitmap, field: DistortionField, outWidth: Int = source.width, outHeight: Int = source.height): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val srcPixels = IntArray(srcW * srcH)
        source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val outW = outWidth.coerceAtLeast(1)
        val outH = outHeight.coerceAtLeast(1)
        val out = IntArray(outW * outH)

        for (oy in 0 until outH) {
            val v = (oy + 0.5f) / outH
            val rowBase = oy * outW
            for (ox in 0 until outW) {
                val u = (ox + 0.5f) / outW
                val (su, sv) = field.sampleBilinear(u, v)
                out[rowBase + ox] = sampleBilinearPixel(srcPixels, srcW, srcH, su, sv)
            }
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
    }

    /**
     * BUG REAL encontrado y corregido acá: interpolaba R/G/B directo
     * sobre valores SIN premultiplicar por alfa — el mismo tipo de error
     * de compositing que ya se había encontrado y corregido en
     * [com.yeivikas.olyzecs.engine.effects.ImageEffects.blitBlend] y en
     * [com.yeivikas.olyzecs.engine.render.LayerDrawer]/[com.yeivikas.olyzecs.engine.render.ShaderProgram],
     * pero que a este archivo nunca había llegado.
     *
     * El problema concreto: en el borde suavizado de cualquier recorte
     * (alpha bajando de 255 a 0 en pocos píxeles — el caso normal en
     * TODAS las imágenes de esta app, que son personajes/objetos
     * recortados sobre fondo transparente), un píxel opaco de color
     * (p.ej. rojo puro, alpha=255) vecino a uno totalmente transparente
     * (RGB indefinido en la práctica, casi siempre 0,0,0 con alpha=0) se
     * promediaba en RGB SIN pesar por cuánto pesa cada uno en la mezcla
     * final — el resultado era un franja oscura/negra visible en el
     * borde de cualquier zona distorsionada con la herramienta de
     * Distorsión, más notoria cuanto más se estira/curva esa zona.
     *
     * La corrección: premultiplicar R/G/B por su propio alpha ANTES de
     * interpolar (así un texel 100% transparente aporta CERO al
     * promedio de color, no un negro "de mentira") y despremultiplicar
     * recién al final, dividiendo por el alfa interpolado — misma
     * fórmula estándar de cualquier motor de compositing profesional
     * para hacer bilinear filtering correcto sobre canales con alfa.
     */
    private fun sampleBilinearPixel(pixels: IntArray, w: Int, h: Int, u: Float, v: Float): Int {
        val fx = u.coerceIn(0f, 1f) * (w - 1).coerceAtLeast(0)
        val fy = v.coerceIn(0f, 1f) * (h - 1).coerceAtLeast(0)
        val x0 = fx.toInt().coerceIn(0, w - 1)
        val y0 = fy.toInt().coerceIn(0, h - 1)
        val x1 = (x0 + 1).coerceAtMost(w - 1)
        val y1 = (y0 + 1).coerceAtMost(h - 1)
        val tx = fx - x0
        val ty = fy - y0

        val c00 = pixels[y0 * w + x0]
        val c10 = pixels[y0 * w + x1]
        val c01 = pixels[y1 * w + x0]
        val c11 = pixels[y1 * w + x1]

        fun alphaOf(c: Int) = (c ushr 24) and 0xFF
        // Canal premultiplicado: valor * (alpha/255) — un texel
        // transparente aporta 0 sin importar qué RGB "de relleno" tenga.
        fun premulChan(c: Int, shift: Int): Float {
            val channel = (c ushr shift) and 0xFF
            val a = alphaOf(c)
            return channel * (a / 255f)
        }

        fun lerp(v0: Float, v1: Float, t: Float) = v0 + (v1 - v0) * t

        val a00 = alphaOf(c00).toFloat(); val a10 = alphaOf(c10).toFloat()
        val a01 = alphaOf(c01).toFloat(); val a11 = alphaOf(c11).toFloat()
        val aTop = lerp(a00, a10, tx); val aBot = lerp(a01, a11, tx)
        val aOut = lerp(aTop, aBot, ty).coerceIn(0f, 255f)

        if (aOut <= 0.001f) return 0 // totalmente transparente: sin color que despremultiplicar

        val rTopPm = lerp(premulChan(c00, 16), premulChan(c10, 16), tx)
        val rBotPm = lerp(premulChan(c01, 16), premulChan(c11, 16), tx)
        val gTopPm = lerp(premulChan(c00, 8), premulChan(c10, 8), tx)
        val gBotPm = lerp(premulChan(c01, 8), premulChan(c11, 8), tx)
        val bTopPm = lerp(premulChan(c00, 0), premulChan(c10, 0), tx)
        val bBotPm = lerp(premulChan(c01, 0), premulChan(c11, 0), tx)

        val rPm = lerp(rTopPm, rBotPm, ty)
        val gPm = lerp(gTopPm, gBotPm, ty)
        val bPm = lerp(bTopPm, bBotPm, ty)

        // Despremultiplicar: volver a RGB "recto" dividiendo por el
        // alfa final ya interpolado — inversa exacta de premulChan.
        val alphaFraction = aOut / 255f
        val a = aOut.toInt().coerceIn(0, 255)
        val r = (rPm / alphaFraction).toInt().coerceIn(0, 255)
        val g = (gPm / alphaFraction).toInt().coerceIn(0, 255)
        val b = (bPm / alphaFraction).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
