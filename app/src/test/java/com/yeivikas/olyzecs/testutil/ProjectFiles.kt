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

    /** Contenido del código fuente, p. ej. `main("ui/EditorScreen.kt")`; la prueba falla si el archivo no existe. */
    fun main(relative: String): String = read(mainFile(relative))

    /** Contenido de un recurso, p. ej. `res("values-en/strings.xml")`; la prueba falla si no existe. */
    fun res(relative: String): String = read(resFile(relative))

    private fun read(file: File): String {
        assertTrue("Falta ${file.path}", file.isFile)
        return file.readText()
    }
}
