package com.yeivikas.olyzecs.platform

import com.yeivikas.olyzecs.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blinda la migración a API 36 (Android 16) para que ninguna edición futura la deshaga sin que CI lo note.
 * Prueba ESTRUCTURAL sobre los archivos reales (JVM puro): lo que el sistema hace al ejecutar en un dispositivo
 * (barras, "atrás" predictivo, orientación) solo se ve en el equipo, pero las decisiones de configuración que lo
 * provocan son texto verificable. Ver docs/MIGRACION_API36.md para el porqué de cada una.
 */
class Api36MigrationStructureTest {

    private val gradle get() = ProjectFiles.module("build.gradle.kts")
    private val manifest get() = ProjectFiles.module("src/main/AndroidManifest.xml")

    /** Quita comentarios `//` y `/* */` para que mencionar un API en un comentario no cuente como usarlo. */
    private fun stripKotlinComments(src: String): String =
        src.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")

    @Test
    fun `compileSdk y targetSdk son 36`() {
        assertTrue(Regex("""compileSdk\s*=\s*36\b""").containsMatchIn(gradle))
        assertTrue(Regex("""targetSdk\s*=\s*36\b""").containsMatchIn(gradle))
    }

    @Test
    fun `el NDK esta fijado en r28 o posterior para alinear a 16 KB`() {
        val m = Regex("""ndkVersion\s*=\s*"(\d+)\.""").find(gradle)
        assertTrue("Falta ndkVersion fijado en app/build.gradle.kts", m != null)
        assertTrue("NDK < r28: libc++_shared.so saldría alineada a 4 KB", m!!.groupValues[1].toInt() >= 28)
    }

    @Test
    fun `Oboe es 1_9_3 o posterior (prefab alineado a 16 KB)`() {
        // La versión vive en el catálogo (gradle/libs.versions.toml), no en app/build.gradle.kts.
        val catalog = ProjectFiles.module("../gradle/libs.versions.toml")
        val m = Regex("""(?m)^oboe\s*=\s*"(\d+)\.(\d+)\.(\d+)"""").find(catalog)
        assertTrue("No se encontró la versión de Oboe en el catálogo", m != null)
        val (major, minor, patch) = m!!.destructured
        val version = major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
        assertTrue("Oboe < 1.9.3 trae liboboe.so alineada a 4 KB", version >= 1_009_003)
    }

    @Test
    fun `la biblioteca nativa propia se enlaza con paginas de 16 KB`() {
        val cmake = ProjectFiles.module("src/main/cpp/CMakeLists.txt")
        assertTrue(cmake.contains("-Wl,-z,max-page-size=16384"))
    }

    @Test
    fun `el atras predictivo esta declarado y nadie usa el atras legado`() {
        assertTrue(manifest.contains("""android:enableOnBackInvokedCallback="true""""))
        // Con targetSdk 36 el sistema ya no llama a onBackPressed ni envía KEYCODE_BACK: usarlos sería código muerto.
        val offenders = ProjectFiles.allMainKotlin().filter { f ->
            val code = stripKotlinComments(f.readText())
            Regex("""\bfun\s+onBackPressed\b|\bKEYCODE_BACK\b|\bonKeyDown\b""").containsMatchIn(code)
        }.map { it.name }
        assertTrue("Atrás legado en: $offenders", offenders.isEmpty())
    }

    @Test
    fun `la orientacion en tablets conserva la salida oficial temporal y el teclado se gestiona como inset`() {
        assertTrue(manifest.contains("PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"))
        assertTrue(manifest.contains("""android:windowSoftInputMode="adjustResize""""))
    }

    @Test
    fun `el tema ya no declara colores de barras que Android 15 ignora`() {
        val theme = ProjectFiles.res("values/themes.xml").replace(Regex("""<!--[\s\S]*?-->"""), "")
        assertFalse(theme.contains("android:statusBarColor"))
        assertFalse(theme.contains("android:navigationBarColor"))
        assertTrue("windowBackground debe seguir para el arranque en frío", theme.contains("android:windowBackground"))
    }

    @Test
    fun `ningun codigo pinta las barras ni pide que la decor encaje con las barras`() {
        val offenders = ProjectFiles.allMainKotlin().filter { f ->
            val code = stripKotlinComments(f.readText())
            Regex("""\.(statusBarColor|navigationBarColor)\s*=""").containsMatchIn(code) ||
                Regex("""setDecorFitsSystemWindows\(\s*\w+\s*,\s*true\s*\)""").containsMatchIn(code)
        }.map { it.name }
        assertTrue("Ignorado por Android 15+ (o rompe el borde a borde en 8-14) en: $offenders", offenders.isEmpty())
    }

    @Test
    fun `MainActivity activa el borde a borde antes de super_onCreate`() {
        val code = stripKotlinComments(ProjectFiles.main("MainActivity.kt"))
        val create = code.indexOf("override fun onCreate(")
        val bars = code.indexOf("OlyzeSystemBars.apply(this)", create)
        val sup = code.indexOf("super.onCreate(savedInstanceState)", create)
        assertTrue(create >= 0 && bars > create)
        assertTrue("enableEdgeToEdge debe ir ANTES de super.onCreate()", bars < sup)
    }

    @Test
    fun `el visor inmersivo restaura las barras por la misma ruta que el arranque`() {
        val code = stripKotlinComments(ProjectFiles.main("ui/FilePreviewDialog.kt"))
        assertTrue(code.contains("OlyzeSystemBars.apply(activity)"))
    }

    @Test
    fun `las pantallas respetan los insets del sistema`() {
        val editor = stripKotlinComments(ProjectFiles.main("ui/EditorScreen.kt"))
        val projects = stripKotlinComments(ProjectFiles.main("ui/ProjectsScreen.kt"))
        // Barra superior propia del editor: inset de barras + recorte, y ANTES de heightIn (la altura mínima es la de la barra).
        val inset = editor.indexOf("WindowInsets.systemBars")
        val minHeight = editor.indexOf(".heightIn(min = 64.dp)", inset)
        assertTrue("La barra superior del editor no aplica el inset de la barra de estado", inset >= 0 && minHeight > inset)
        assertTrue(editor.contains("WindowInsets.displayCutout"))
        // Teclado: ambas pantallas lo consumen como inset IME, sin contar dos veces la barra de navegación.
        for ((name, src) in listOf("EditorScreen" to editor, "ProjectsScreen" to projects)) {
            val consume = src.indexOf(".consumeWindowInsets(padding)")
            val ime = src.indexOf(".imePadding()")
            assertTrue("$name: falta consumeWindowInsets(padding) + imePadding()", consume >= 0 && ime > consume)
        }
        assertEquals(1, Regex("""\.imePadding\(\)""").findAll(editor).count())
    }
}
