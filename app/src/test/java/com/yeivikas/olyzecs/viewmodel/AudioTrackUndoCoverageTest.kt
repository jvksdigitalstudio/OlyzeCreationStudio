package com.yeivikas.olyzecs.viewmodel

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Todo cambio de los carriles de audio debe poder deshacerse.
 *
 * El snapshot de deshacer restaura `audioTracks` COMPLETO. Un cambio que no toma checkpoint antes no se puede deshacer y, peor, queda
 * revertido en silencio por el siguiente deshacer de CUALQUIER otra acción. Ya pasó con el color del carril (ADR-015) y otra vez con el
 * largo del clip (estirar un borde), la rejilla y el candado de orden.
 *
 * Esta prueba guarda la CLASE de defecto y no solo esos casos: recorre las funciones de `EditorViewModel` y exige que cada una que
 * modifica los carriles o clips de audio tome un checkpoint, salvo una lista explícita y justificada. Un caso nuevo y legítimo obliga a
 * decidirlo a propósito (añadir el checkpoint o justificar la excepción aquí) en vez de olvidarlo. Se verifica sobre el código fuente
 * porque `EditorViewModel` depende de Android y no se puede construir en una prueba de JVM.
 */
class AudioTrackUndoCoverageTest {

    private val source = ProjectFiles.main("viewmodel/EditorViewModel.kt")

    private class Fn(val name: String, val body: String)

    private val functionStart = Regex("""^ {4}(?:(?:private|internal|override) )*fun (?:<[^>]*> )?(?:\w+\.)?(\w+)\(""")
    private val mutatesAudio = Regex("""audioTracks\s*=\s|replaceAudioTracks?\(|replaceAudioClip\(|withTrack\(""")
    private val takesCheckpoint = Regex("""pushUndoCheckpoint|recordUndo\s*=\s*true""")

    /** Funciones de primer nivel del ViewModel con su cuerpo (hasta la siguiente). Las locales cuentan como parte de su función. */
    private fun functions(): List<Fn> {
        val lines = source.lines()
        val starts = lines.mapIndexedNotNull { index, line -> functionStart.find(line)?.let { index to it.groupValues[1] } }
        return starts.mapIndexed { k, (index, name) ->
            val end = if (k + 1 < starts.size) starts[k + 1].first else lines.size
            Fn(name, lines.subList(index, end).joinToString("\n"))
        }
    }

    /**
     * Funciones que modifican los carriles de audio SIN checkpoint a propósito:
     *  - infraestructura: no son ediciones del usuario (ciclo de vida, guardado, el propio mecanismo de deshacer);
     *  - los tres mutadores genéricos: su parámetro `recordUndo` o el llamador toman el checkpoint (los llamadores SÍ se comprueban aquí).
     */
    private val allowedWithoutCheckpoint = setOf(
        "onCleared", "discardChangesAndExit", "captureSnapshot", "restoreSnapshot", "scheduleAutosave",
        "replaceAudioClip", "replaceAudioTrack", "replaceAudioTracks"
    )

    @Test
    fun `toda edicion de los carriles de audio toma checkpoint de deshacer o esta justificada`() {
        val violations = functions()
            .filter { mutatesAudio.containsMatchIn(it.body) && !takesCheckpoint.containsMatchIn(it.body) && it.name !in allowedWithoutCheckpoint }
            .map { it.name }
        assertTrue(
            "Estas funciones modifican audioTracks sin checkpoint (no serían deshacibles y un deshacer ajeno las revertiría en silencio): " +
                "$violations. Añade `pushUndoCheckpoint(force = true)` / `recordUndo = true`, o justifica la excepción en este test.",
            violations.isEmpty()
        )
    }

    @Test
    fun `la lista de excepciones no guarda funciones que ya no existen`() {
        val existing = functions().map { it.name }.toSet()
        val stale = allowedWithoutCheckpoint - existing
        assertTrue("Excepciones obsoletas (la función ya no existe): $stale", stale.isEmpty())
    }

    @Test
    fun `los bordes del clip, la rejilla y el candado de orden son deshacibles`() {
        val byName = functions().associateBy { it.name }
        for (name in listOf("setAudioClipLength", "moveAudioClipLeftEdge", "setAudioGrid", "toggleAudioOrderLock")) {
            val fn = byName[name]
            assertTrue("no existe $name", fn != null)
            assertTrue("$name debe tomar checkpoint de deshacer", takesCheckpoint.containsMatchIn(fn?.body.orEmpty()))
        }
    }

    @Test
    fun `un gesto de borde que no cambia nada no crea un paso de deshacer vacio`() {
        val byName = functions().associateBy { it.name }
        for (name in listOf("setAudioClipLength", "moveAudioClipLeftEdge")) {
            val body = byName[name]?.body.orEmpty()
            val noOpGuard = body.indexOf(") return")
            val checkpoint = body.indexOf("pushUndoCheckpoint")
            assertTrue("$name debe salir antes del checkpoint si no hay cambio real", noOpGuard in 0 until checkpoint)
        }
    }
}
