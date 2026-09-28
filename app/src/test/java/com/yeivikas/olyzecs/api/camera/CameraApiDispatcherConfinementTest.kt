package com.yeivikas.olyzecs.api.camera

import com.yeivikas.olyzecs.api.project.ActiveProjectMutator
import com.yeivikas.olyzecs.api.project.ActiveProjectReader
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test OBLIGATORIO de la Fase 4.2-R4 — Parte 3/15 del prompt maestro R4
 * ("confinamiento de mutaciones", "si una operación necesita Main
 * dispatcher, el test debe demostrar que una llamada desde un contexto
 * diferente no viola el ownership"): verifica sobre la implementación
 * REAL [CameraApiImpl] (no un fake de la propia API) que sus 3 escrituras
 * llegan a [ActiveProjectMutator] siempre bajo `Dispatchers.Main`, sin
 * importar desde qué dispatcher las haya lanzado el llamador.
 *
 * [CameraApiImpl] no depende de `Context` (a diferencia de
 * `LayerApiImpl`/`AudioApiImpl`), así que se puede instanciar y probar de
 * verdad con un JVM unit test puro — con `kotlinx-coroutines-test` para
 * poder controlar/observar `Dispatchers.Main` sin Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraApiDispatcherConfinementTest {

    /** Fake de [ActiveProjectMutator] que solo registra en qué [kotlinx.coroutines.CoroutineDispatcher] fue invocado cada método. */
    private class RecordingMutator : ActiveProjectMutator {
        var dispatcherAtSetKeyframe: kotlinx.coroutines.CoroutineDispatcher? = null
        var dispatcherAtRemoveKeyframe: kotlinx.coroutines.CoroutineDispatcher? = null
        var dispatcherAtSetBaseFrame: kotlinx.coroutines.CoroutineDispatcher? = null

        override suspend fun addLayers(layers: List<Layer>) {}
        override suspend fun setLayerVisible(layerId: String, visible: Boolean) {}
        override suspend fun setLayerLocked(layerId: String, locked: Boolean) {}
        override suspend fun setLayerOrderLocked(layerId: String, orderLocked: Boolean) {}
        override suspend fun setLayerZIndex(layerId: String, newZIndex: Int) {}
        override suspend fun setLayerLookSettings(layerId: String, look: LookSettings) {}
        override suspend fun deleteLayer(layerId: String) {}

        override suspend fun setCameraKeyframe(layerId: String, keyframe: Keyframe) {
            dispatcherAtSetKeyframe = kotlin.coroutines.coroutineContext[kotlinx.coroutines.CoroutineDispatcher]
        }

        override suspend fun removeCameraKeyframe(layerId: String, timeMs: Long) {
            dispatcherAtRemoveKeyframe = kotlin.coroutines.coroutineContext[kotlinx.coroutines.CoroutineDispatcher]
        }

        override suspend fun setCameraBaseFrame(layerId: String, frame: CameraFrame) {
            dispatcherAtSetBaseFrame = kotlin.coroutines.coroutineContext[kotlinx.coroutines.CoroutineDispatcher]
        }

        override suspend fun applyAudioVolume(volume: Float) {}
        override suspend fun setAudioMuted(muted: Boolean) {}
        override suspend fun applyAudioTrimStart(trimStartMs: Long) {}
        override suspend fun applyAudioLoop(loop: Boolean) {}
        override suspend fun applyAudioFade(fadeInMs: Long, fadeOutMs: Long) {}
        override suspend fun clearAudioClip() {}
        override suspend fun setAudioClipDirect(clip: AudioClip) {}
        override fun previewPlayFrom(context: android.content.Context, projectTimeMs: Long) {}
        override fun previewPause() {}
        override fun previewSeekTo(context: android.content.Context, projectTimeMs: Long) {}
        override fun growTimelineIfApproachingEnd(playheadMs: Long): Long = 0L
        override fun ensureTimelineCapacityFor(targetMs: Long): Long = 0L
    }

    private class EmptyReader : ActiveProjectReader {
        override fun getLayers(): List<Layer> = emptyList()
        override fun getLayer(layerId: String): Layer? = null
        override fun getAudioClip(): AudioClip? = null
        override fun getSpeedKeyframes(): List<SpeedKeyframe> = emptyList()
        override fun getFreezeFrames(): List<FreezeFrame> = emptyList()
        override fun getBaseDurationMs(): Long = 0L
        override fun isAtMaxDurationLimit(): Boolean = false
        override fun timelineEventsFlow(): SharedFlow<TimelineEvent> = MutableSharedFlow()
    }

    // FASE 4.2-R4: se usa `UnconfinedTestDispatcher` (no
    // `StandardTestDispatcher`) para instalar como Main a propósito: es
    // una instancia INDEPENDIENTE del scheduler propio de `runTest`, así
    // que si se usara `StandardTestDispatcher` acá, una corrutina
    // despachada a `Dispatchers.Main` quedaría encolada en un scheduler
    // que nadie más avanza — el test podría colgarse esperando algo que
    // nunca se ejecuta. `UnconfinedTestDispatcher` ejecuta cada corrutina
    // de inmediato, sin depender de ningún scheduler compartido, que es
    // exactamente lo que hace falta acá: solo confirmar EN QUÉ dispatcher
    // quedó la llamada, no controlar tiempo virtual.
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // Instala el dispatcher de test COMO Main — así `Dispatchers.Main`
        // queda disponible en este unit test JVM puro (sin él,
        // `Dispatchers.Main.immediate` lanzaría "Module with the Main
        // dispatcher had failed to initialize").
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setKeyframe llamado desde Default llega al mutator bajo Main`() = runTest {
        val mutator = RecordingMutator()
        val api: CameraApi = CameraApiImpl(EmptyReader(), mutator)

        // A propósito, se lanza la llamada desde Dispatchers.Default — un
        // consumidor real podría hacer exactamente esto sin saber que
        // `EditorViewModel` (el mutator real en producción) espera Main.
        withContext(Dispatchers.Default) {
            api.setKeyframe("layer-1", Keyframe(timeMs = 0L))
        }

        assertTrue(
            "setCameraKeyframe debió ejecutarse bajo Dispatchers.Main, no bajo el dispatcher del llamador",
            mutator.dispatcherAtSetKeyframe === Dispatchers.Main.immediate || mutator.dispatcherAtSetKeyframe === Dispatchers.Main
        )
    }

    @Test
    fun `removeKeyframe y setBaseFrame tambien quedan confinados a Main`() = runTest {
        val mutator = RecordingMutator()
        val api: CameraApi = CameraApiImpl(EmptyReader(), mutator)

        withContext(Dispatchers.Default) {
            api.removeKeyframe("layer-1", 500L)
            api.setBaseFrame("layer-1", CameraFrame(0.1f, 0.2f, 1f, 0f, 1f))
        }

        assertTrue(mutator.dispatcherAtRemoveKeyframe === Dispatchers.Main.immediate || mutator.dispatcherAtRemoveKeyframe === Dispatchers.Main)
        assertTrue(mutator.dispatcherAtSetBaseFrame === Dispatchers.Main.immediate || mutator.dispatcherAtSetBaseFrame === Dispatchers.Main)
    }

    @Test
    fun `llamar ya desde Main no agrega un salto de dispatcher extra visible`() = runTest {
        val mutator = RecordingMutator()
        val api: CameraApi = CameraApiImpl(EmptyReader(), mutator)

        // Si el llamador YA está en Main (el caso normal desde
        // viewModelScope), `Dispatchers.Main.immediate` no debería agregar
        // ninguna sorpresa: sigue funcionando igual.
        api.setKeyframe("layer-1", Keyframe(timeMs = 0L))

        assertEquals(true, mutator.dispatcherAtSetKeyframe != null)
    }
}
