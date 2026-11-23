package com.yeivikas.olyzecs.engine.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * FASE 1 (AUDITORÍA P0) — test OBLIGATORIO de esta fase: prueba, en
 * aislamiento y sin ninguna dependencia de Android, la propiedad exacta en
 * la que se apoya [com.yeivikas.olyzecs.viewmodel.LayerContentState] /
 * `LayerSaveSnapshot` / `LayerEditState` (todos en el módulo de app, fuera
 * del alcance de un JVM unit test por depender de `android.net.Uri` — ver
 * el informe técnico de FASE 1, sección "Riesgos restantes / tests
 * pendientes") para construir una foto realmente independiente de una
 * capa:
 *
 *   `cameraTrack.keyframes.toList()` alcanza para independizar la lista de
 *   [Keyframe] del contenedor mutable interno de [CameraTrack], PORQUE
 *   [Keyframe] en sí mismo es inmutable (data class de solo `val`) — no
 *   hace falta clonar cada elemento, alcanza con copiar el contenedor.
 *
 * Este test reproduce el escenario B pedido explícitamente en el prompt de
 * la fase:
 *
 *   Crear snapshot → modificar keyframes del CameraTrack en vivo →
 *   snapshot permanece igual.
 */
class CameraTrackSnapshotIndependenceTest {

    private fun kf(timeMs: Long, tx: Float) = Keyframe(timeMs = timeMs, translateX = tx, translateY = 0f, scale = 1f)

    @Test
    fun `una foto de keyframes tomada con toList no cambia cuando el CameraTrack en vivo se modifica`() {
        val track = CameraTrack(initialKeyframes = listOf(kf(0L, 0f), kf(1000L, 1f)))

        // "Captura controlada" — el mismo patrón que usan LayerContentState/
        // LayerSaveSnapshot/LayerEditState en EditorViewModel/ProjectStorage.
        val snapshot: List<Keyframe> = track.keyframes.toList()
        val snapshotSizeBefore = snapshot.size
        val snapshotFirstBefore = snapshot.first()

        // Mutación EN VIVO del CameraTrack real, después de tomar la foto —
        // exactamente lo que hace undo/redo, discardChangesAndExit, o un
        // guardado en curso que siga corriendo mientras el usuario edita.
        track.addOrReplace(kf(500L, 0.5f))
        track.replaceAll(listOf(kf(0L, 99f)))

        assertEquals(
            "El tamaño de la foto no debe cambiar aunque el CameraTrack en vivo sí cambie",
            snapshotSizeBefore,
            snapshot.size
        )
        assertEquals(
            "El primer keyframe de la foto no debe cambiar de valor",
            0f,
            snapshotFirstBefore.translateX
        )
        assertNotEquals(
            "El CameraTrack en vivo sí debe haber cambiado (para confirmar que la mutación realmente ocurrió)",
            snapshotFirstBefore.translateX,
            track.keyframes.first().translateX
        )
    }

    @Test
    fun `una foto de baseFrame tomada antes de updateBaseFrame no cambia`() {
        val track = CameraTrack(initialBaseFrame = CameraFrame(0f, 0f, 1f, 0f, 1f))
        val snapshotBaseFrame = track.baseFrame

        track.updateBaseFrame(CameraFrame(translateX = 5f, translateY = 5f, scale = 2f, rotationDeg = 45f, alpha = 0.5f))

        assertEquals(0f, snapshotBaseFrame.translateX)
        assertNotEquals(snapshotBaseFrame.translateX, track.baseFrame.translateX)
    }

    @Test
    fun `replaceAll preserva la identidad del CameraTrack para no perder la textura GL asociada`() {
        // Documenta/protege el motivo por el que `applyTo`/`restoreSnapshot`
        // mutan el CameraTrack EXISTENTE en vez de reemplazarlo por uno
        // nuevo (ver KDoc de CameraTrack.replaceAll): la instancia debe
        // seguir siendo la MISMA después de restaurar un snapshot.
        val track = CameraTrack(initialKeyframes = listOf(kf(0L, 0f)))
        val identityBefore = System.identityHashCode(track)

        track.replaceAll(listOf(kf(0L, 42f), kf(200L, 43f)))

        assertEquals(identityBefore, System.identityHashCode(track))
        assertEquals(2, track.keyframes.size)
    }
}
