package com.yeivikas.olyzecs.ui

import com.yeivikas.olyzecs.engine.audio.fx.ReverbPresetId
import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * El módulo Reverb de los carriles: textos de la ventana, nombres de presets y el cableado cajón → editor → ViewModel, que
 * no se puede ejercitar sin un dispositivo y por eso se verifica sobre el código fuente.
 */
class ReverbModuleStructureTest {

    @Before
    fun fixLocale() = Locale.setDefault(Locale.US)

    // ---------------------------------------------------------------- formatos

    @Test
    fun `las frecuencias se muestran en Hz o kHz segun su magnitud`() {
        assertEquals("20 Hz", formatReverbHz(20f))
        assertEquals("850 Hz", formatReverbHz(850f))
        assertEquals("999 Hz", formatReverbHz(999.4f))
        assertEquals("1 kHz", formatReverbHz(1_000f))
        assertEquals("1.2 kHz", formatReverbHz(1_234f))
        assertEquals("8 kHz", formatReverbHz(8_000f))
        assertEquals("14 kHz", formatReverbHz(14_000f))
        assertEquals("20 kHz", formatReverbHz(20_000f))
    }

    @Test
    fun `los porcentajes se redondean al entero mas cercano`() {
        assertEquals("0%", formatReverbPercent(0f))
        assertEquals("30%", formatReverbPercent(0.30f))
        assertEquals("75%", formatReverbPercent(0.751f))
        assertEquals("100%", formatReverbPercent(1f))
    }

    // ---------------------------------------------------------------- textos

    @Test
    fun `cada preset tiene su propio texto traducido`() {
        val names = ReverbPresetId.values().map { reverbPresetNameRes(it) }
        assertEquals(ReverbPresetId.values().size, names.distinct().size)
        assertTrue(names.all { it != 0 })
    }

    private fun stringKeys(xml: String): Set<String> =
        Regex("""<string name="([a-z0-9_]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()

    @Test
    fun `espanol e ingles tienen exactamente las mismas claves del modulo`() {
        val es = stringKeys(ProjectFiles.res("values/strings.xml")).filter { it.startsWith("reverb_") || it == "audio_module_category_space" }
        val en = stringKeys(ProjectFiles.res("values-en/strings.xml")).filter { it.startsWith("reverb_") || it == "audio_module_category_space" }
        assertTrue("Sin textos del Reverb", es.isNotEmpty())
        assertEquals(es.toSet(), en.toSet())
    }

    @Test
    fun `el panel y la ventana solo usan claves que existen`() {
        val declared = stringKeys(ProjectFiles.res("values/strings.xml"))
        val used = listOf("ui/ReverbModulePanel.kt", "ui/ReverbFloatingWindow.kt", "ui/AudioModulesList.kt")
            .flatMap { Regex("""R\.string\.([a-z0-9_]+)""").findAll(ProjectFiles.main(it)).map { m -> m.groupValues[1] }.toList() }
            .toSet()
        assertTrue(used.isNotEmpty())
        assertTrue("Claves sin declarar: ${used - declared}", declared.containsAll(used))
    }

    // ---------------------------------------------------------------- cableado

    @Test
    fun `el cajon registra el Reverb y reenvia el toque hasta el editor`() {
        assertTrue(ProjectFiles.main("ui/AudioModulesList.kt").contains("onClick = onReverbClick"))
        val bar = ProjectFiles.main("ui/EditorBottomBar.kt")
        assertTrue(bar.contains("onReverbClick = onReverbModuleClick"))
        assertTrue(bar.contains("ModulesTarget.AUDIO -> AudioModulesList(onReverbClick = onReverbClick)"))
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertTrue(editor.contains("onReverbModuleClick = {"))
        assertTrue(editor.contains("viewModel.addReverb(it)"))
    }

    @Test
    fun `la ventana del Reverb habla con el ViewModel solo por las operaciones del carril seleccionado`() {
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        for (call in listOf("setReverbParams", "applyReverbPreset", "setReverbEnabled", "removeReverb")) {
            assertTrue("Falta $call en el editor", editor.contains("viewModel::$call"))
        }
        // Estado ligado al CARRIL, no a la capa: cambiar de carril cierra la ventana.
        assertTrue(editor.contains("remember(selectedAudioTrackId) { mutableStateOf(false) }"))
    }

    @Test
    fun `el editor no crece - la ventana del Reverb entra como UNA llamada, sin bloques ni variables locales en su lambda`() {
        // Regresion real: un bloque de ~25 lineas con variables locales y lambdas dentro del cuerpo de EditorScreen hizo que
        // Android rechazara la clase (VerifyError) y la app se cerrara al abrir el editor. La logica vive en ReverbWindowHost.
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertTrue(editor.contains("ReverbWindowHost("))
        assertFalse(editor.contains("val reverbTrackId"))
        assertFalse(editor.contains("val reverbEffect"))
        assertFalse(editor.contains("ReverbFloatingWindow("))
        assertTrue(ProjectFiles.main("ui/ReverbFloatingWindow.kt").contains("fun ReverbWindowHost("))
    }

    @Test
    fun `ni la ventana ni el panel conocen al ViewModel ni al DSP`() {
        for (file in listOf("ui/ReverbFloatingWindow.kt", "ui/ReverbModulePanel.kt", "ui/ReverbModuleIcon.kt")) {
            val source = ProjectFiles.main(file)
            assertFalse("$file conoce al ViewModel", source.contains("EditorViewModel"))
            assertFalse("$file conoce al DSP", source.contains("StereoReverb"))
        }
    }

    @Test
    fun `cada ventana se identifica por su carril ante el registro de minimizadas`() {
        assertTrue(ProjectFiles.main("ui/ReverbFloatingWindow.kt").contains("windowId = \"reverb:\$trackId\""))
        // El marco ya no usa el titulo como identidad en ninguna llamada al registro.
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertFalse(Regex("""minimizedRegistry\.(resolveFreeSlot|register|unregister|unregisterOpenWindow|overlapsAnyOpenWindow)\(title""").containsMatchIn(editor))
    }

    @Test
    fun `el marco ya no guarda el preset elegido sino que lo recibe del modulo`() {
        val editor = ProjectFiles.main("ui/EditorScreen.kt")
        assertFalse(editor.contains("var selectedPresetIndex by remember"))
        assertTrue(editor.contains("onPresetSelected: (Int) -> Unit = {}"))
    }

    @Test
    fun `el panel del Reverb no anida un scroll vertical dentro del de la ventana flotante`() {
        // Regresion real (crash al tocar "Reverb"): FloatingToolWindow ya envuelve su contenido en verticalScroll, y un segundo
        // verticalScroll dentro recibe altura infinita -> IllegalStateException al medir. El scroll vive SOLO en la ventana.
        val frame = ProjectFiles.main("ui/EditorScreen.kt")
        assertTrue(frame.contains(".verticalScroll(rememberScrollState())\n                        .padding(horizontal = 14.dp, vertical = 12.dp)"))
        for (file in listOf("ui/ReverbModulePanel.kt", "ui/ReverbFloatingWindow.kt")) {
            val code = ProjectFiles.main(file).lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }.joinToString("\n")
            assertFalse("$file anida un verticalScroll", code.contains("verticalScroll"))
            assertFalse("$file anida un LazyColumn", code.contains("LazyColumn"))
        }
    }

    @Test
    fun `el checkpoint de deshacer se toma ANTES de leer el estado que se va a copiar`() {
        // Regresion real: `captureCheckpoint` escribe undoAvailable en el estado; copiar un estado leido ANTES del checkpoint
        // lo devolvia a false y el boton Deshacer no se encendia al cargar/quitar un modulo (Reverb) de un carril.
        val vm = ProjectFiles.main("viewmodel/EditorViewModel.kt")
        val body = vm.substringAfter("private fun replaceAudioTracks(").substringBefore("// Reproducci")
        val checkpoint = body.indexOf("pushUndoCheckpoint(force = true)")
        val read = body.indexOf("val state = _uiState.value")
        assertTrue("Falta el checkpoint o la lectura del estado", checkpoint >= 0 && read >= 0)
        assertTrue("El estado se lee antes del checkpoint", checkpoint < read)
    }

    @Test
    fun `los mensajes de la mezcla no tienen plantillas rotas`() {
        // `$mix.channelCount` imprime el objeto entero seguido de ".channelCount": hay que usar `${mix.channelCount}`.
        assertFalse(ProjectFiles.main("engine/audio/AudioProcessor.kt").contains("\$mix."))
    }

    @Test
    fun `el motivo de fallo del procesador es por hilo y no un estado global compartido`() {
        val source = ProjectFiles.main("engine/audio/AudioProcessor.kt")
        assertTrue(source.contains("ThreadLocal<String?>()"))
        assertFalse(source.contains("@Volatile private var lastFailureReason"))
    }

    @Test
    fun `la purga de los WAV de la vista previa respeta los que siguen en uso`() {
        val cache = ProjectFiles.main("engine/audio/DryTrackCache.kt")
        assertTrue(cache.contains("protectedKeys = keyMemo.values.map { it.second }"))
        assertTrue(ProjectFiles.main("engine/audio/AudioPreviewPlayers.kt").contains("DryTrackCache.retain(liveIds)"))
        assertTrue(ProjectFiles.main("engine/audio/PreviewWavCache.kt").contains("files.filter { it.name !in keep }"))
    }

    @Test
    fun `un fallo del render de la vista previa no queda como error visible de la exportacion`() {
        val cache = ProjectFiles.main("engine/audio/DryTrackCache.kt")
        assertTrue(cache.contains("AppLogger.restoreLastUserFacingError(previousUserFacingError)"))
    }

    // ---------------------------------------------------------------- motor en tiempo real

    @Test
    fun `el bake de la vista previa fue retirado`() {
        assertFalse(ProjectFiles.mainFile("engine/audio/TrackBake.kt").exists())
        assertFalse(ProjectFiles.mainFile("engine/audio/TrackBakeCache.kt").exists())
        val players = ProjectFiles.main("engine/audio/AudioPreviewPlayers.kt")
        assertFalse(players.contains("TrackBakeCache"))
        assertFalse(players.contains("bakedForPlayback"))
    }

    @Test
    fun `la mezcla seca se renderiza sin efectos y su clave no los incluye`() {
        val cache = ProjectFiles.main("engine/audio/DryTrackCache.kt")
        assertTrue(cache.contains("track.copy(effects = emptyList())"))
        val key = ProjectFiles.main("engine/audio/DryTrackMix.kt").substringAfter("fun dryMixKey(").substringBefore("/** `true` si al menos un clip")
        assertFalse("La clave de la mezcla seca no puede depender de los efectos", key.contains("effect", ignoreCase = true))
    }

    @Test
    fun `la vista previa de un carril con Reverb pasa por el motor en vivo`() {
        val players = ProjectFiles.main("engine/audio/AudioPreviewPlayers.kt")
        assertTrue(players.contains("LiveReverbTrackPlayer(dry.file, params)"))
        assertTrue(players.contains("planLiveSync("))
        assertTrue(players.contains("player.setParams(params)"))
        val live = ProjectFiles.main("engine/audio/LiveReverbTrackPlayer.kt")
        assertTrue(live.contains("LiveReverbStream("))
        assertTrue(live.contains("AudioTrack.MODE_STREAM"))
        assertTrue(live.contains("THREAD_PRIORITY_URGENT_AUDIO"))
    }

    @Test
    fun `solo el hilo de audio toca el AudioTrack y el motor`() {
        // Lo que ejecuta el hilo principal (la API pública del reproductor) no puede llamar al dispositivo ni al DSP.
        val live = ProjectFiles.main("engine/audio/LiveReverbTrackPlayer.kt")
        val api = live.substringAfter("val totalFrames: Long get()").substringBefore("// HILO DE AUDIO")
        for (forbidden in listOf("output.", "stream.", "source.", "AudioTrack(")) {
            assertFalse("La API del hilo principal usa `$forbidden`", api.contains(forbidden))
        }
    }
}
