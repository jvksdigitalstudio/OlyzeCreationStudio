package com.yeivikas.olyzecs.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Reglas puras de los nombres de carril y de clip de audio (`AudioNaming.kt`).
 * Sin clips reales: `AudioClip` lleva un `android.net.Uri`, que no existe en JUnit
 * puro, así que lo que depende del clip se prueba por los núcleos con primitivos
 * ([resolveAudioClipLabel], [resolveAudioTrackLabel], [resolveCustomAudioName]) y
 * lo del carril con carriles sin clips.
 */
class AudioNamingTest {

    private fun track(name: String? = null) = AudioTrack(id = "t", trackOrder = 0f, name = name)

    // ---------- normalizeAudioName ----------

    @Test
    fun `null y texto sin contenido significan sin nombre personalizado`() {
        assertNull(normalizeAudioName(null))
        assertNull(normalizeAudioName(""))
        assertNull(normalizeAudioName("   \t\n  "))
    }

    @Test
    fun `recorta los extremos y colapsa los espacios repetidos`() {
        assertEquals("Bombo grave", normalizeAudioName("  Bombo    grave  "))
    }

    @Test
    fun `los caracteres de control pasan a espacio y no quedan saltos de linea`() {
        assertEquals("Voz Coro", normalizeAudioName("Voz\nCoro"))
        assertEquals("Voz Coro", normalizeAudioName("Voz\u0000\tCoro"))
    }

    @Test
    fun `un nombre dentro del tope queda intacto`() {
        val exact = "a".repeat(AUDIO_NAME_MAX_LENGTH)
        assertEquals(exact, normalizeAudioName(exact))
    }

    @Test
    fun `un nombre largo se acota al tope`() {
        val result = normalizeAudioName("a".repeat(AUDIO_NAME_MAX_LENGTH + 40))
        assertEquals(AUDIO_NAME_MAX_LENGTH, result!!.length)
    }

    @Test
    fun `el tope cuenta code points y nunca parte un emoji a la mitad`() {
        val emoji = "\uD83C\uDFB5" // 🎵: 1 code point, 2 unidades UTF-16
        val result = normalizeAudioName(emoji.repeat(AUDIO_NAME_MAX_LENGTH + 5))!!
        assertEquals(AUDIO_NAME_MAX_LENGTH, result.codePointCount(0, result.length))
        assertEquals(AUDIO_NAME_MAX_LENGTH * 2, result.length)
        // Ninguna unidad suelta: cada surrogate alto va seguido de su par.
        assertEquals(emoji.repeat(AUDIO_NAME_MAX_LENGTH), result)
    }

    @Test
    fun `normalizar dos veces da lo mismo - es idempotente`() {
        val once = normalizeAudioName("  Mezcla\n  final   v2 ")
        assertEquals(once, normalizeAudioName(once))
    }

    // ---------- etiquetas visibles ----------

    @Test
    fun `un clip sin nombre propio muestra el nombre de su archivo`() {
        assertEquals("Kick 01.wav", resolveAudioClipLabel(customName = null, fileName = "Kick 01.wav"))
        assertEquals("Kick 01.wav", resolveAudioClipLabel(customName = "   ", fileName = "Kick 01.wav"))
    }

    @Test
    fun `un clip con nombre propio lo muestra en lugar del archivo`() {
        assertEquals("Bombo", resolveAudioClipLabel(customName = "Bombo", fileName = "Kick 01.wav"))
    }

    @Test
    fun `un carril con nombre propio lo muestra`() {
        assertEquals("Percusión", resolveAudioTrackLabel(trackName = "Percusión", firstClipFileName = "Kick 01.wav"))
    }

    @Test
    fun `un carril sin nombre propio muestra el archivo del primer clip`() {
        assertEquals("Kick 01.wav", resolveAudioTrackLabel(trackName = null, firstClipFileName = "Kick 01.wav"))
    }

    @Test
    fun `un carril sin nombre ni clip cae al texto de ultimo recurso`() {
        assertEquals(AUDIO_TRACK_FALLBACK_LABEL, resolveAudioTrackLabel(trackName = null, firstClipFileName = null))
        assertEquals(AUDIO_TRACK_FALLBACK_LABEL, resolveAudioTrackLabel(trackName = " ", firstClipFileName = " "))
    }

    // ---------- resolveCustomAudioName ----------

    @Test
    fun `pedir el nombre de fabrica equivale a no personalizar`() {
        assertNull(resolveCustomAudioName(requested = "Kick 01.wav", fallback = "Kick 01.wav"))
        assertNull(resolveCustomAudioName(requested = "  Kick 01.wav ", fallback = "Kick 01.wav"))
    }

    @Test
    fun `un nombre distinto del de fabrica se conserva normalizado`() {
        assertEquals("Bombo", resolveCustomAudioName(requested = " Bombo ", fallback = "Kick 01.wav"))
    }

    @Test
    fun `sin nombre de fabrica cualquier nombre valido se conserva`() {
        assertEquals("Bombo", resolveCustomAudioName(requested = "Bombo", fallback = null))
        assertNull(resolveCustomAudioName(requested = null, fallback = null))
    }

    // ---------- AudioTrack.withName ----------

    @Test
    fun `renombrar el carril guarda el nombre normalizado y lo muestra`() {
        val renamed = track().withName("  Voces   principales ")
        assertEquals("Voces principales", renamed.name)
        assertEquals("Voces principales", renamed.label)
    }

    @Test
    fun `renombrar al mismo nombre devuelve la misma instancia`() {
        val named = track(name = "Voces")
        assertSame(named, named.withName("Voces"))
        assertSame(named, named.withName("  Voces "))
    }

    @Test
    fun `un carril sin nombre al que se le pide nada sigue siendo la misma instancia`() {
        val unnamed = track()
        assertSame(unnamed, unnamed.withName(null))
        assertSame(unnamed, unnamed.withName("   "))
    }

    @Test
    fun `restablecer con null o vacio quita el nombre propio`() {
        val named = track(name = "Voces")
        assertNull(named.withName(null).name)
        assertNull(named.withName("").name)
        assertNotSame(named, named.withName(null))
    }

    @Test
    fun `renombrar un carril no toca ninguno de sus otros datos`() {
        val original = AudioTrack(id = "t", trackOrder = 3f, orderLocked = true)
        val renamed = original.withName("Voces")
        assertEquals(original.copy(name = "Voces"), renamed)
        assertEquals(original.id, renamed.id)
        assertEquals(original.trackOrder, renamed.trackOrder, 0f)
        assertEquals(original.orderLocked, renamed.orderLocked)
    }
}
