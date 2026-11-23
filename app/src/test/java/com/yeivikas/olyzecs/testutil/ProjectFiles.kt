package com.yeivikas.olyzecs.testutil

import org.junit.Assert.assertTrue
import java.io.File

/**
 * Acceso de las pruebas a los archivos del proyecto (código fuente y recursos), para las pruebas "de estructura" que
 * verifican cableados que no se pueden ejercitar sin un dispositivo (qué lambda llama a qué, que dos idiomas tengan las
 * mismas claves…). Un solo lugar para localizar la raíz del módulo, en vez de repetirlo en cada prueba.
 */
internal object ProjectFiles {
    private val moduleDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/java").isDirectory }

    /** El archivo de código fuente bajo `com/yeivikas/olyzecs/` (puede no existir: sirve para comprobar que ALGO ya no existe). */
    fun mainFile(relative: String): File = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/$relative")

    /** El recurso bajo `src/main/res/` (puede no existir). */
    fun resFile(relative: String): File = File(moduleDir, "src/main/res/$relative")

    /** Contenido de cualquier archivo relativo a la raíz del módulo `app/` (p. ej. `build.gradle.kts`, `src/main/AndroidManifest.xml`). */
    fun module(relative: String): String = read(File(moduleDir, relative))

    /** Todos los `.kt` de producción (`src/main/java`), para pruebas que vigilan que un patrón NO reaparezca en ningún lado. */
    fun allMainKotlin(): List<File> =
        File(moduleDir, "src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Contenido del código fuente, p. ej. `main("ui/EditorScreen.kt")`; la prueba falla si el archivo no existe. */
    fun main(relative: String): String = read(mainFile(relative))

    /**
     * Código fuente de la pantalla del editor, sin depender de CÓMO esté repartido en archivos: `ui/EditorScreen.kt` más cualquier `.kt`
     * bajo `ui/editor/`. Las pruebas de cableado (qué lambda llama a qué) deben leer por aquí y no `main("ui/EditorScreen.kt")`: así partir
     * el archivo de 18 000 líneas en varios (Fase 3 del plan) no las rompe, y se comprueba lo que importa (el cableado) y no dónde vive.
     */
    fun editorScreen(): String = buildString {
        append(main("ui/EditorScreen.kt"))
        val dir = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/ui/editor")
        if (dir.isDirectory) {
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.forEach { append('\n').append(it.readText()) }
        }
    }

    /** Los `.kt` de `ui/editor/` (nombre de archivo → contenido), ordenados por nombre. Vacío si la carpeta aún no existe. */
    fun editorFiles(): Map<String, String> {
        val dir = File(moduleDir, "src/main/java/com/yeivikas/olyzecs/ui/editor")
        if (!dir.isDirectory) return emptyMap()
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.name }.associate { it.name to it.readText() }
    }

    /** Contenido de un archivo relativo a la RAÍZ del repositorio (p. ej. `.editorconfig`, `.github/workflows/android-build.yml`). */
    fun repo(relative: String): String {
        val root = generateSequence(moduleDir) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        return read(File(root, relative))
    }

    /** Contenido de un recurso, p. ej. `res("values-en/strings.xml")`; la prueba falla si no existe. */
    fun res(relative: String): String = read(resFile(relative))

    private fun read(file: File): String {
        assertTrue("Falta ${file.path}", file.isFile)
        return file.readText()
    }
}
