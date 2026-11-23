package com.yeivikas.olyzecs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de [classifyFilePreviewKind] (FASE 13 — visor de archivos de la
 * papelera de proyectos, ver `docs/fases/FASE_13_VISOR_DE_ARCHIVOS_EN_PAPELERA.md`):
 * función pura que decide, solo por el nombre del archivo, qué contenido
 * renderiza [FilePreviewDialog]. Cubre la tabla completa de extensiones
 * reconocidas más los casos borde (mayúsculas, sin extensión, extensión
 * compuesta, nombre vacío) — ninguno de estos casos debía quedar sin
 * probar antes de que un archivo real de un proyecto (`project.json`,
 * `thumbnail.jpg`, un clip de `audio/`) llegara al visor por primera vez.
 */
class FilePreviewClassificationTest {

    @Test
    fun `extensiones de imagen conocidas clasifican como IMAGE`() {
        listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif").forEach { ext ->
            assertEquals("foto.$ext", FilePreviewKind.IMAGE, classifyFilePreviewKind("foto.$ext"))
        }
    }

    @Test
    fun `json siempre clasifica como JSON, nunca como TEXT generico`() {
        assertEquals(FilePreviewKind.JSON, classifyFilePreviewKind("project.json"))
    }

    @Test
    fun `extensiones de texto plano clasifican como TEXT`() {
        listOf("txt", "md", "markdown", "xml", "csv", "log", "yml", "yaml", "properties", "srt", "vtt", "ini", "gradle", "kts").forEach { ext ->
            assertEquals("archivo.$ext", FilePreviewKind.TEXT, classifyFilePreviewKind("archivo.$ext"))
        }
    }

    @Test
    fun `extensiones de audio conocidas clasifican como AUDIO`() {
        listOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "3gp", "opus").forEach { ext ->
            assertEquals("clip.$ext", FilePreviewKind.AUDIO, classifyFilePreviewKind("clip.$ext"))
        }
    }

    @Test
    fun `extensiones de video conocidas clasifican como VIDEO`() {
        listOf("mp4", "mov", "mkv", "webm", "3gp2").forEach { ext ->
            assertEquals("clip.$ext", FilePreviewKind.VIDEO, classifyFilePreviewKind("clip.$ext"))
        }
    }

    @Test
    fun `extension desconocida clasifica como UNSUPPORTED, nunca crashea`() {
        assertEquals(FilePreviewKind.UNSUPPORTED, classifyFilePreviewKind("modelo.glb"))
        assertEquals(FilePreviewKind.UNSUPPORTED, classifyFilePreviewKind("datos.bin"))
    }

    @Test
    fun `sin extension clasifica como UNSUPPORTED en vez de romper`() {
        assertEquals(FilePreviewKind.UNSUPPORTED, classifyFilePreviewKind("LICENSE"))
        assertEquals(FilePreviewKind.UNSUPPORTED, classifyFilePreviewKind(""))
    }

    @Test
    fun `clasificacion es insensible a mayusculas en la extension`() {
        assertEquals(FilePreviewKind.IMAGE, classifyFilePreviewKind("PORTADA.JPG"))
        assertEquals(FilePreviewKind.JSON, classifyFilePreviewKind("PROJECT.JSON"))
    }

    @Test
    fun `extension compuesta usa solo el ultimo segmento tras el punto`() {
        // "archive.tar.gz" -> extensión real es "gz", no "tar.gz" — coherente
        // con `String.substringAfterLast('.', "")`, no un caso especial.
        assertEquals(FilePreviewKind.UNSUPPORTED, classifyFilePreviewKind("backup.tar.gz"))
        assertEquals(FilePreviewKind.IMAGE, classifyFilePreviewKind("proyecto.final.png"))
    }

    @Test
    fun `nombre que termina en punto sin extension no crashea`() {
        assertEquals(FilePreviewKind.UNSUPPORTED, classifyFilePreviewKind("archivo."))
    }
}
