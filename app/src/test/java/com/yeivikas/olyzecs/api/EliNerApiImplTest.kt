package com.yeivikas.olyzecs.api

import android.graphics.Bitmap
import android.net.Uri
import com.yeivikas.olyzecs.api.animation.AnimationApi
import com.yeivikas.olyzecs.api.audio.AudioApi
import com.yeivikas.olyzecs.api.camera.CameraApi
import com.yeivikas.olyzecs.api.distortion.DistortionApi
import com.yeivikas.olyzecs.api.export.ExportApi
import com.yeivikas.olyzecs.api.mesh3d.Mesh3DApi
import com.yeivikas.olyzecs.api.model.LayerSnapshot
import com.yeivikas.olyzecs.api.scene.LayerApi
import com.yeivikas.olyzecs.api.timeline.TimelineApi
import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.FreezeRuntimeState
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.audio.AudioClip
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.core.PixelColorSource
import com.yeivikas.olyzecs.engine.distortion.DistortionField
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.export.ExportProgress
import com.yeivikas.olyzecs.engine.export.ExportSettings
import com.yeivikas.olyzecs.engine.mesh3d.Extrude3D
import com.yeivikas.olyzecs.engine.timeline.TimelineEvent
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [EliNerApiImpl] es, por diseño (ver su KDoc), un agregador puro sin
 * ownership propio: no debe hacer nada más que exponer, tal cual, las
 * instancias que recibe por constructor. Estas pruebas verifican
 * exactamente eso — con fakes mínimos, sin tocar ningún método real de
 * ninguno de los 9 dominios (no se necesita: el contrato bajo prueba es
 * "el agregador no fabrica ni sustituye nada"), consistente con el resto
 * de las pruebas del proyecto (JUnit puro, sin Robolectric/mocking —
 * ver AnimationApiImplTest).
 *
 * No se re-prueba acá el comportamiento interno de cada `*ApiImpl` (eso
 * ya lo cubre, o debería cubrirlo, la prueba de esa clase en particular
 * — p. ej. AnimationApiImplTest); esta prueba demuestra únicamente "la
 * fachada preserva el contrato de composición".
 */
class EliNerApiImplTest {

    private val scene = FakeLayerApi()
    private val camera = FakeCameraApi()
    private val animation = FakeAnimationApi()
    private val timeline = FakeTimelineApi()
    private val render = FakePixelColorSource()
    private val audio = FakeAudioApi()
    private val export = FakeExportApi()
    private val mesh3d = FakeMesh3DApi()
    private val distortion = FakeDistortionApi()

    private val api: EliNerApi = EliNerApiImpl(
        scene = scene,
        camera = camera,
        animation = animation,
        timeline = timeline,
        render = render,
        audio = audio,
        export = export,
        mesh3d = mesh3d,
        distortion = distortion
    )

    @Test
    fun `expone cada dominio como exactamente la misma instancia recibida`() {
        // No "una instancia equivalente" ni "una copia" — LA MISMA
        // referencia. Un agregador que reconstruyera o envolviera lo
        // recibido dejaría de ser un agregador sin ownership propio.
        assertSame(scene, api.scene)
        assertSame(camera, api.camera)
        assertSame(animation, api.animation)
        assertSame(timeline, api.timeline)
        assertSame(render, api.render)
        assertSame(audio, api.audio)
        assertSame(export, api.export)
        assertSame(mesh3d, api.mesh3d)
        assertSame(distortion, api.distortion)
    }

    @Test
    fun `distortion ya no queda huerfano fuera de la fachada`() {
        // Regresión puntual del hallazgo prioritario de la Fase 4.1:
        // antes de este cambio, EliNerApi no tenia una propiedad
        // `distortion` en absoluto (no compilaba lo de abajo).
        val exposedDistortion: DistortionApi = api.distortion
        assertSame(distortion, exposedDistortion)
    }

    // ---- Fakes mínimos: nunca se invocan sus métodos en esta prueba, ----
    // ---- solo existen para poder construir un EliNerApiImpl real.    ----

    private class FakeLayerApi : LayerApi {
        override suspend fun createLayers(sourceUri: Uri): List<LayerSnapshot> = TODO("no usado en esta prueba")
        override suspend fun deleteLayer(layerId: String) = TODO("no usado en esta prueba")
        override suspend fun reorderLayer(layerId: String, newZIndex: Int) = TODO("no usado en esta prueba")
        override suspend fun setVisible(layerId: String, visible: Boolean) = TODO("no usado en esta prueba")
        override suspend fun setLocked(layerId: String, locked: Boolean) = TODO("no usado en esta prueba")
        override suspend fun setOrderLocked(layerId: String, orderLocked: Boolean) = TODO("no usado en esta prueba")
        override suspend fun setLookSettings(layerId: String, look: LookSettings) = TODO("no usado en esta prueba")
        override fun getLayer(layerId: String): LayerSnapshot? = TODO("no usado en esta prueba")
        override fun getLayers(): List<LayerSnapshot> = TODO("no usado en esta prueba")
    }

    private class FakeCameraApi : CameraApi {
        override suspend fun setKeyframe(layerId: String, keyframe: Keyframe) = TODO("no usado en esta prueba")
        override suspend fun removeKeyframe(layerId: String, timeMs: Long) = TODO("no usado en esta prueba")
        override suspend fun setBaseFrame(layerId: String, frame: CameraFrame) = TODO("no usado en esta prueba")
        override fun getKeyframes(layerId: String): List<Keyframe> = TODO("no usado en esta prueba")
        override fun frameAt(layerId: String, timeMs: Long): CameraFrame = TODO("no usado en esta prueba")
    }

    private class FakeAnimationApi : AnimationApi {
        override fun speedAt(speedKeyframes: List<SpeedKeyframe>, baseTimeMs: Long): Float = TODO("no usado en esta prueba")
        override fun step(
            currentBaseMs: Long,
            tickMs: Long,
            freezeState: FreezeRuntimeState,
            baseDurationMs: Long,
            speedKeyframes: List<SpeedKeyframe>,
            freezeFrames: List<FreezeFrame>
        ): Pair<Long, FreezeRuntimeState> = TODO("no usado en esta prueba")
        override fun computeOutputDurationMs(
            baseDurationMs: Long,
            speedKeyframes: List<SpeedKeyframe>,
            freezeFrames: List<FreezeFrame>,
            fps: Int
        ): Long = TODO("no usado en esta prueba")
    }

    private class FakeTimelineApi : TimelineApi {
        override fun currentDurationMs(): Long = TODO("no usado en esta prueba")
        override val isAtMaxLimit: Boolean get() = TODO("no usado en esta prueba")
        override fun growIfApproachingEnd(playheadMs: Long): Long = TODO("no usado en esta prueba")
        override fun ensureCapacityFor(targetMs: Long): Long = TODO("no usado en esta prueba")
        override val events: SharedFlow<TimelineEvent> = MutableSharedFlow()
        override suspend fun generateThumbnail(timeMs: Long, widthPx: Int, heightPx: Int): Bitmap? =
            TODO("no usado en esta prueba")
    }

    private class FakePixelColorSource : PixelColorSource {
        override fun requestPixelColor(xPx: Int, yPx: Int, callback: (argbColor: Int) -> Unit) =
            TODO("no usado en esta prueba")
    }

    private class FakeAudioApi : AudioApi {
        override fun getAudioClip(): AudioClip? = TODO("no usado en esta prueba")
        override suspend fun setAudioClip(sourceUri: Uri): Boolean = TODO("no usado en esta prueba")
        override suspend fun clearAudioClip() = TODO("no usado en esta prueba")
        override suspend fun setVolume(volume: Float) = TODO("no usado en esta prueba")
        override suspend fun setMuted(muted: Boolean) = TODO("no usado en esta prueba")
        override suspend fun setTrimStart(trimStartMs: Long) = TODO("no usado en esta prueba")
        override suspend fun setLoop(loop: Boolean) = TODO("no usado en esta prueba")
        override suspend fun setFade(fadeInMs: Long, fadeOutMs: Long) = TODO("no usado en esta prueba")
        override suspend fun probeDurationMs(sourceUri: Uri): Long = TODO("no usado en esta prueba")
        override fun playFrom(projectTimeMs: Long) = TODO("no usado en esta prueba")
        override fun pause() = TODO("no usado en esta prueba")
        override fun seekTo(projectTimeMs: Long) = TODO("no usado en esta prueba")
    }

    private class FakeExportApi : ExportApi {
        override fun export(outputFile: File, settings: ExportSettings): Flow<ExportProgress> = emptyFlow()
    }

    private class FakeMesh3DApi : Mesh3DApi {
        override suspend fun extrude(source: Bitmap, params: Extrude3D.Params, highQuality: Boolean): Bitmap =
            TODO("no usado en esta prueba")
    }

    private class FakeDistortionApi : DistortionApi {
        override suspend fun render(source: Bitmap, field: DistortionField, outWidth: Int, outHeight: Int): Bitmap =
            TODO("no usado en esta prueba")
    }
}
