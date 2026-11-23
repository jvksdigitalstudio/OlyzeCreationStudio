package com.yeivikas.olyzecs.platform

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda los gates de calidad (ver docs/QUALITY_GATES.md y ADR-021). Son invariantes de CONFIGURACIÓN (workflow, .editorconfig, Gradle)
 * que solo existen como texto; el comportamiento real de los gates lo ejecuta el CI.
 */
class QualityGatesStructureTest {

    private val workflow get() = ProjectFiles.repo(".github/workflows/android-build.yml")
    private val appGradle get() = ProjectFiles.module("build.gradle.kts")
    private val editorConfig get() = ProjectFiles.repo(".editorconfig")

    @Test
    fun `ktlint esta fijado a una version concreta y no a latest`() {
        val m = Regex("""KTLINT_VERSION:\s*"(\d+\.\d+\.\d+)"""").find(workflow)
        assertTrue("KTLINT_VERSION debe ser una versión exacta (un 'latest' hace el CI no reproducible)", m != null)
        assertFalse(workflow.contains("ktlint_version: latest"))
    }

    @Test
    fun `el CI ejecuta ktlint y Android Lint`() {
        assertTrue(workflow.contains("name: Estilo de Kotlin (ktlint)"))
        assertTrue(workflow.contains("gradle lintDebug"))
    }

    @Test
    fun `ambos gates son bloqueantes cuando existe su baseline`() {
        // El modo informativo (sin baseline) es la única vía que tolera fallos; con baseline NO debe haber `|| true`.
        val ktlintBlocking = workflow.substringAfter("Modo BLOQUEANTE: solo fallan las infracciones NUEVAS").substringBefore("else")
        assertFalse("ktlint con baseline no puede tolerar fallos", ktlintBlocking.contains("|| true"))
        val lintBlocking = workflow.substringAfter("Modo BLOQUEANTE: solo fallan los problemas NUEVOS").substringBefore("else")
        assertFalse("lint con baseline no puede tolerar fallos", lintBlocking.contains("|| true"))
    }

    @Test
    fun `el baseline de lint solo se asigna si existe o se esta generando`() {
        // Apuntar a un baseline inexistente haría que assembleRelease (lintVital) intentara crearlo y fallara.
        val code = appGradle.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertTrue(code.contains("""lintBaselineFile.exists() || project.hasProperty("olyze.lintGenerateBaseline")"""))
        assertFalse("baseline asignado incondicionalmente", Regex("""(?m)^\s*baseline\s*=\s*file\(""").containsMatchIn(code))
    }

    @Test
    fun `los reportes y baselines se suben aunque un gate falle`() {
        val step = workflow.substringAfter("name: Subir reportes y baselines de calidad")
        assertTrue(step.lineSequence().take(3).any { it.contains("if: always()") })
    }

    @Test
    fun `editorconfig define el estilo y respeta las funciones composables`() {
        assertTrue(editorConfig.contains("ktlint_code_style = android_studio"))
        assertTrue(editorConfig.contains("ktlint_function_naming_ignore_when_annotated_with = Composable"))
        assertTrue(Regex("""max_line_length\s*=\s*\d+""").containsMatchIn(editorConfig))
    }

    @Test
    fun `StrictMode solo se instala en builds depurables`() {
        val guard = ProjectFiles.main("debug/StrictModeGuard.kt")
        assertTrue(guard.contains("FLAG_DEBUGGABLE"))
        assertFalse("penaltyDeath mataría la app durante las pruebas en el teléfono", guard.contains("penaltyDeath"))
    }

    private val regenerate get() = ProjectFiles.repo(".github/workflows/regenerate-baselines.yml")

    @Test
    fun `la regeneracion de baselines usa exactamente los mismos globs que el CI principal`() {
        // Si difirieran, el baseline regenerado tendría rutas distintas a las que ktlint busca en el CI y todo parecería "nuevo".
        val globs = Regex("""globs=\(.*\)""")
        assertTrue("el CI principal no define globs", globs.containsMatchIn(workflow))
        assertTrue(globs.find(workflow)!!.value == globs.find(regenerate)?.value)
    }

    @Test
    fun `la regeneracion lee la version de ktlint del CI principal`() {
        assertTrue("una segunda versión escrita a mano acabaría desincronizada", regenerate.contains("android-build.yml"))
        assertFalse(Regex("""KTLINT_VERSION:\s*"\d""").containsMatchIn(regenerate))
    }

    @Test
    fun `la regeneracion es manual, solo en main y tiene salvaguarda contra el crecimiento`() {
        assertTrue(regenerate.contains("workflow_dispatch:"))
        assertFalse("no debe dispararse sola", Regex("""(?m)^\s*(push|pull_request|schedule):""").containsMatchIn(regenerate))
        assertTrue(regenerate.contains("if: github.ref == 'refs/heads/main'"))
        assertTrue("sin salvaguarda se congelarían infracciones reales", regenerate.contains("Salvaguarda (el baseline no puede crecer)"))
        assertTrue(regenerate.contains("allow_growth"))
    }

    @Test
    fun `los permisos de escritura se limitan al workflow de regeneracion`() {
        assertTrue(regenerate.contains("contents: write"))
        assertFalse("el CI principal no debe poder escribir en el repositorio", workflow.contains("contents: write"))
    }
}
