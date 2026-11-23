package com.yeivikas.olyzecs.ui.effects

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda, sobre los archivos reales, la regla de calidad de los efectos: la vista previa nunca se ve peor que la
 * pantalla y lo que se guarda NUNCA sale de una copia reducida. Si alguien reintroduce el defecto original (un
 * `fullBitmap ?: liveBitmap`, un guardado fuera del orquestador), estas pruebas fallan.
 */
class EffectsProgressiveRenderStructureTest {

    private fun stripComments(src: String): String =
        src.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")

    private val editor get() = stripComments(ProjectFiles.editorScreen())

    @Test
    fun `ningun sitio vuelve a caer a la copia chica para guardar`() {
        assertFalse(editor.contains("fullBitmap ?: liveBitmap"))
        assertFalse(editor.contains("?: liveBitmap ?: return"))
        assertFalse("la imagen completa ya no es un valor anulable que se pueda esquivar", editor.contains("fullBitmap"))
    }

    @Test
    fun `todo guardado de efectos pasa por el orquestador`() {
        val calls = Regex("""commitLayerRecolor\(""").findAll(editor).count()
        val viaScheduler = Regex("""onFinal = \{ viewModel\.commitLayerRecolor\(layer\.id, it, source = "\w+"\) \}""").findAll(editor).count()
        assertTrue("debe haber guardados de efectos", calls > 0)
        assertEquals("cada commitLayerRecolor debe ser el onFinal de un orquestador", calls, viaScheduler)
    }

    @Test
    fun `las nueve ventanas de efectos usan el orquestador y ninguna conserva jobs propios`() {
        assertEquals(9, Regex("""rememberProgressiveRenderScheduler\(""").findAll(editor).count())
        for (legacy in listOf("commitJob", "liveRenderJob", "lastLiveRenderAtMs", "applyLivePreviewAndScheduleCommit", "scheduleLivePreview")) {
            assertFalse("quedó código viejo: $legacy", editor.contains(legacy))
        }
    }

    @Test
    fun `las ventanas con base propia la recrean cuando cambia el archivo de la capa`() {
        // Diseño multi-ventana: Recolor y Color Básico re-decodifican su base al cambiar `layer.sourceUri`.
        val keyed = Regex("""val sources = remember\(layer\.id, layer\.sourceUri\) \{ EffectSources<Bitmap>\(\) \}""").findAll(editor).count()
        assertEquals(2, keyed)
    }

    @Test
    fun `la carga de la imagen completa y de la copia de pantalla usa el cargador comun`() {
        assertEquals(5, Regex("""EffectSourceLoader\.loadRemaining\(""").findAll(editor).count())
        assertFalse("ningún sitio decodifica la imagen completa por su cuenta", editor.contains("maxDimension = ImageDecoding.NO_LIMIT"))
    }

    @Test
    fun `la copia chica de los efectos compartidos usa la constante central`() {
        assertEquals(2, Regex("""maxDimension = EffectsPreviewSizing\.QUICK_MAX_DIMENSION""").findAll(editor).count())
        assertTrue(editor.contains("decode(EffectsPreviewSizing.QUICK_MAX_DIMENSION)"))
    }

    @Test
    fun `el nucleo del orquestador es puro y no depende de Android ni de Compose`() {
        for (file in listOf("EffectSources.kt", "EffectsPreviewSizing.kt", "EffectSourceLoader.kt", "ProgressiveRenderScheduler.kt")) {
            val source = stripComments(ProjectFiles.main("ui/effects/$file"))
            assertFalse("$file no debe importar android", source.contains("import android."))
            assertFalse("$file no debe importar androidx", source.contains("import androidx."))
        }
    }

    @Test
    fun `el guardado solo se renderiza desde la imagen completa en el orquestador`() {
        val scheduler = stripComments(ProjectFiles.main("ui/effects/ProgressiveRenderScheduler.kt"))
        assertTrue(scheduler.contains("val full = sources.awaitFull()"))
        assertTrue(
            "FINAL se renderiza solo si hay imagen completa, y dentro del cerrojo de renderizados pesados",
            scheduler.contains("if (full == null) null else renderExclusively(full, RenderTier.FINAL)")
        )
        assertEquals("FINAL solo se renderiza desde `full`", 1, Regex("""RenderTier\.FINAL\)""").findAll(scheduler).count())
    }

    @Test
    fun `el contenedor de fuentes no ofrece ninguna forma sincrona de obtener la imagen completa`() {
        val sources = stripComments(ProjectFiles.main("ui/effects/EffectSources.kt"))
        assertTrue(sources.contains("suspend fun awaitFull()"))
        assertFalse(Regex("""(val|var) full\b""").containsMatchIn(sources.replace("private val full = CompletableDeferred", "")))
    }

    @Test
    fun `el limite sin reducir del cargador coincide con el del decodificador de la app`() {
        val decoder = stripComments(ProjectFiles.main("data/ImageDecoding.kt"))
        val loader = stripComments(ProjectFiles.main("ui/effects/EffectSourceLoader.kt"))
        assertTrue(decoder.contains("const val NO_LIMIT = Int.MAX_VALUE"))
        assertTrue("si cambia el valor en ImageDecoding, el cargador debe cambiar igual", loader.contains("const val NO_LIMIT = Int.MAX_VALUE"))
    }

    @Test
    fun `toda carga registra los fallos de decodificacion en vez de tragarlos`() {
        val loads = Regex("""EffectSourceLoader\.loadRemaining\(""").findAll(editor).count()
        val logged = Regex("""onDecodeFailure = ::logEffectDecodeFailure""").findAll(editor).count()
        assertTrue(loads > 0)
        assertEquals("cada carga debe pasar el registrador", loads, logged)
    }

    @Test
    fun `las decodificaciones del cargador son independientes entre si`() {
        val loader = stripComments(ProjectFiles.main("ui/effects/EffectSourceLoader.kt"))
        assertTrue(loader.contains("catch (cancelled: CancellationException)"))
        assertTrue("la cancelación debe propagarse", loader.contains("throw cancelled"))
        assertTrue("el fallo se informa", loader.contains("onDecodeFailure(requested, failure)"))
    }

    @Test
    fun `Distorsion no inventa un campo identidad para guardar`() {
        val start = editor.indexOf("val scheduler = rememberProgressiveRenderScheduler(", editor.indexOf("fun DistortionPanel("))
        assertTrue(start > 0)
        val block = editor.substring(start, editor.indexOf("onPreview", start))
        assertFalse("render no debe fabricar un campo identidad", block.contains("DistortionField.identity"))
        assertTrue("sin campo no hay nada que renderizar", block.contains("if (currentField == null) null"))
    }

    @Test
    fun `el contrato de render admite null para no renderizar`() {
        val scheduler = stripComments(ProjectFiles.main("ui/effects/ProgressiveRenderScheduler.kt"))
        assertTrue(scheduler.contains("suspend (source: B, tier: RenderTier) -> B?"))
        assertTrue("arrastre", scheduler.contains("render(quick, RenderTier.DRAG) ?: return@launch"))
        assertTrue("reposo", scheduler.contains("renderExclusively(source, RenderTier.SETTLE) ?: return@launch"))
        assertTrue("el reposo y el guardado comparten el cerrojo y comprueban vigencia tras esperarlo", scheduler.contains("heavyRender.withLock {\n        currentCoroutineContext().ensureActive()\n        render(source, tier)"))
        assertTrue("guardado: sin render no hay nada que guardar", scheduler.contains("if (rendered == null) {"))
    }

    @Test
    fun `el decodificador real sigue siendo el modelo del que depende la garantia de nitidez`() {
        val decoder = stripComments(ProjectFiles.main("data/ImageDecoding.kt"))
        assertTrue(decoder.contains("while (rawWidth / sampleSize > maxDimension || rawHeight / sampleSize > maxDimension)"))
        assertTrue("el muestreo debe seguir siendo por potencias de dos", decoder.contains("sampleSize *= 2"))
    }

    @Test
    fun `todas las copias de reposo se piden con el limite garantizado y no con el objetivo`() {
        assertEquals(5, Regex("""displayDecodeLimit = \{ context\.effectsDisplayDecodeLimit\(EditViewFit\.FILL_FRACTION\) \}""").findAll(editor).count())
        assertFalse(editor.contains("effectsDisplayMaxDimension"))
        assertFalse(editor.contains("displayMaxDimension"))
    }

    @Test
    fun `las nueve ventanas vuelcan su guardado pendiente en un ambito que sobrevive a la ventana`() {
        assertEquals(9, Regex("""persistScope = viewModel\.viewModelScope,""").findAll(editor).count())
        val adapter = stripComments(ProjectFiles.main("ui/effects/RememberProgressiveRenderScheduler.kt"))
        assertTrue("solo al SALIR de la composición", adapter.contains("DisposableEffect(Unit)"))
        assertTrue(adapter.contains("latestScheduler.flushPendingCommit(persistScope)"))
        assertTrue("al sustituirse solo se cancela", adapter.contains("DisposableEffect(scheduler)"))
    }

    @Test
    fun `el limite de frecuencia del arrastre usa un reloj monotonico`() {
        val scheduler = stripComments(ProjectFiles.main("ui/effects/ProgressiveRenderScheduler.kt"))
        assertFalse("el reloj de pared puede retroceder y congelar la vista previa", scheduler.contains("currentTimeMillis"))
        assertTrue(scheduler.contains("System.nanoTime()"))
        assertTrue(scheduler.contains("private val nowMs: () -> Long = ::monotonicMillis"))
    }
}

