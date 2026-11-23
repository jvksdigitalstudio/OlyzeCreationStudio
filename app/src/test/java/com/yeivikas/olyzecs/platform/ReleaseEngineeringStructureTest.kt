package com.yeivikas.olyzecs.platform

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Blinda la ingeniería de release (ver docs/RELEASE_ENGINEERING.md y ADR-020). Son invariantes de CONFIGURACIÓN que solo existen
 * como texto en el repositorio (reglas R8, firma, .gitignore), así que la comprobación estructural es la herramienta correcta;
 * el comportamiento real del release lo comprueba el CI (`tools/check_release_mapping.py`, apksigner, zipalign).
 */
class ReleaseEngineeringStructureTest {

    private val proguard get() = ProjectFiles.module("proguard-rules.pro")
    private val appGradle get() = ProjectFiles.module("build.gradle.kts")

    private fun withoutComments(src: String, marker: String): String =
        src.lines().filterNot { it.trimStart().startsWith(marker) }.joinToString("\n")

    @Test
    fun `R8 no usa keep amplios sobre paquetes de la app`() {
        val rules = withoutComments(proguard, "#")
        val broad = Regex("""-keep(classmembers|classeswithmembers)?\s+(,\w+)*\s*class\s+com\.yeivikas\.olyzecs\.[\w.]*\*\*\s*\{\s*\*;?\s*\}""")
        assertFalse("keep amplio sobre un paquete de la app: anula la reducción y la ofuscación", broad.containsMatchIn(rules))
    }

    @Test
    fun `R8 conserva el puente JNI por nombre`() {
        val rules = withoutComments(proguard, "#")
        assertTrue(rules.contains("-keepclasseswithmembernames,includedescriptorclasses class * {"))
        assertTrue(rules.contains("native <methods>;"))
    }

    @Test
    fun `R8 conserva numeros de linea para desofuscar los fallos`() {
        val rules = withoutComments(proguard, "#")
        assertTrue(rules.contains("-keepattributes SourceFile,LineNumberTable"))
    }

    @Test
    fun `el release minimiza y reduce recursos`() {
        val release = appGradle.substringAfter("release {").substringBefore("debug {")
        assertTrue(Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(release))
        assertTrue(Regex("""isShrinkResources\s*=\s*true""").containsMatchIn(release))
    }

    @Test
    fun `ningun secreto de firma esta escrito en el build`() {
        val code = withoutComments(appGradle, "//")
        assertFalse("storePassword literal en build.gradle.kts", Regex("""storePassword\s*=\s*"[^"]""").containsMatchIn(code))
        assertFalse("keyPassword literal en build.gradle.kts", Regex("""keyPassword\s*=\s*"[^"]""").containsMatchIn(code))
        assertTrue("la firma debe resolverse con ReleaseConfig", code.contains("ReleaseConfig.signing(project)"))
    }

    @Test
    fun `los archivos de claves estan fuera del control de versiones`() {
        val ignore = File(File("").absoluteFile.let { dir -> generateSequence(dir) { it.parentFile }.first { File(it, ".gitignore").isFile } }, ".gitignore").readText()
        for (pattern in listOf("keystore.properties", "*.jks", "*.keystore")) {
            assertTrue("falta '$pattern' en .gitignore", ignore.lines().any { it.trim() == pattern })
        }
    }

    @Test
    fun `el versionCode sale de ReleaseConfig y no esta clavado`() {
        val code = withoutComments(appGradle, "//")
        assertTrue(code.contains("versionCode = ReleaseConfig.versionCode(project)"))
        assertFalse(Regex("""versionCode\s*=\s*\d+""").containsMatchIn(code))
    }

    @Test
    fun `el archivo dorado del reverb esta declarado como salida del test para la cache de Gradle`() {
        val code = withoutComments(appGradle, "//")
        assertTrue("sin declararlo, testDebugUnitTest FROM-CACHE no genera el archivo y el paso de paridad falla en un runner limpio",
            code.contains("outputs.dir(layout.buildDirectory.dir(\"native-golden\"))"))
        val writer = ProjectFiles.module("src/test/java/com/yeivikas/olyzecs/engine/audio/fx/StereoReverbGoldenTest.kt")
        assertTrue("el test dorado debe seguir escribiendo en build/native-golden", writer.contains("build/native-golden/reverb_golden.f32"))
    }
}
