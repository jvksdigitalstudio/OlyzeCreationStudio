package com.yeivikas.olyzecs.platform

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda la base de dependencias de la Fase 2 (ver docs/MIGRACION_API36.md, §8): catálogo de versiones como única
 * fuente de verdad, Kotlin moderno y ninguna API del build que las versiones nuevas de Kotlin/Compose hayan retirado.
 * Prueba ESTRUCTURAL sobre los archivos reales (JVM puro).
 */
class DependencyBaselineStructureTest {

    private val catalog get() = ProjectFiles.module("../gradle/libs.versions.toml")
    private val appGradle get() = ProjectFiles.module("build.gradle.kts")
    private val rootGradle get() = ProjectFiles.module("../build.gradle.kts")

    private fun stripGradleComments(src: String): String =
        src.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")

    private fun section(name: String): String {
        val start = catalog.indexOf("[" + name + "]")
        assertTrue("Falta la sección [$name] en libs.versions.toml", start >= 0)
        val next = Regex("""(?m)^\[""").find(catalog, start + 1)?.range?.first ?: catalog.length
        return catalog.substring(start, next)
    }

    private fun version(key: String): List<Int> {
        val m = Regex("""(?m)^$key\s*=\s*"([\d.]+)"""").find(section("versions"))
        assertTrue("Falta la versión '$key' en [versions]", m != null)
        return m!!.groupValues[1].split(".").map { it.toInt() }
    }

    @Test
    fun `Kotlin es 2_2 o posterior`() {
        val (major, minor) = version("kotlin")
        assertTrue("Kotlin < 2.2: kotlinOptions y varias APIs de Compose actuales no compilan", major > 2 || (major == 2 && minor >= 2))
    }

    @Test
    fun `AGP sigue en la serie 8 mientras compileSdk sea 36`() {
        // compileSdk 37 exige AGP 9.1.1+ (y core-ktx 1.19 / lifecycle 2.11 lo piden): subir uno sin el otro rompe el build.
        assertEquals(8, version("agp")[0])
        assertTrue(Regex("""compileSdk\s*=\s*36\b""").containsMatchIn(appGradle))
    }

    @Test
    fun `todas las referencias del catalogo apuntan a una version declarada`() {
        val declared = Regex("""(?m)^(\w+)\s*=""").findAll(section("versions")).map { it.groupValues[1] }.toSet()
        val used = Regex("""version\.ref\s*=\s*"(\w+)"""").findAll(catalog).map { it.groupValues[1] }.toSet()
        assertTrue("version.ref sin declarar: ${used - declared}", declared.containsAll(used))
        assertTrue("Versiones declaradas y sin usar: ${declared - used}", used.containsAll(declared))
    }

    @Test
    fun `el build no declara versiones sueltas ni plugins con numero`() {
        val app = stripGradleComments(appGradle)
        val root = stripGradleComments(rootGradle)
        val literal = Regex("""(implementation|testImplementation|androidTestImplementation|debugImplementation)\(\s*"[^"]+:[^"]+:[^"]+"\s*\)""")
        assertFalse("Dependencia con versión literal en app/build.gradle.kts (va al catálogo)", literal.containsMatchIn(app))
        assertFalse("Plugin con versión literal en el build raíz (va al catálogo)", Regex("""version\s+"\d""").containsMatchIn(root))
        assertTrue(root.contains("alias(libs.plugins.kotlin.android)"))
    }

    @Test
    fun `el destino de bytecode se fija con compilerOptions y no con kotlinOptions`() {
        val app = stripGradleComments(appGradle)
        assertFalse("kotlinOptions fue eliminado en Kotlin 2.4", app.contains("kotlinOptions"))
        assertTrue(app.contains("jvmTarget.set(JvmTarget.JVM_17)"))
        assertTrue("El bytecode (17) debe coincidir con compileOptions", app.contains("JavaVersion.VERSION_17"))
    }

    @Test
    fun `LocalLifecycleOwner viene de lifecycle-runtime-compose`() {
        assertTrue(catalog.contains("lifecycle-runtime-compose"))
        val offenders = ProjectFiles.allMainKotlin().filter {
            it.readText().contains("import androidx.compose.ui.platform.LocalLifecycleOwner")
        }.map { it.name }
        assertTrue("Import obsoleto de LocalLifecycleOwner en: $offenders", offenders.isEmpty())
    }

    @Test
    fun `los iconos de material siguen la version del BOM y no una clavada`() {
        val line = catalog.lines().first { it.startsWith("androidx-compose-material-icons-core") }
        assertFalse("material-icons-core no debe llevar versión propia (la fija el BOM)", line.contains("version"))
    }
}
