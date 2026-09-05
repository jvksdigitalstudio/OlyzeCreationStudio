package com.yeivikas.olyzecs.data

import com.yeivikas.olyzecs.engine.animation.FreezeFrame
import com.yeivikas.olyzecs.engine.animation.SpeedKeyframe
import com.yeivikas.olyzecs.engine.camera.CameraFrame
import com.yeivikas.olyzecs.engine.camera.Keyframe
import com.yeivikas.olyzecs.engine.effects.LookSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de serialización/round-trip de [ProjectData] (el modelo real que
 * [ProjectStorage] escribe como `project.json`).
 *
 * Corre como JVM unit test puro: `ProjectData` y todo lo que cuelga de
 * ella (`LayerData`, `AudioTrackData`, `LookSettings`, `Keyframe`,
 * `CameraFrame`, `SpeedKeyframe`, `FreezeFrame`) son `@Serializable` sin
 * ninguna dependencia de `Context` de Android — a diferencia del resto de
 * `ProjectStorage` (copiar imágenes, generar miniaturas, autosave), que sí
 * necesita `Context`/`Bitmap` y por eso queda fuera del alcance de esta
 * Fase A como JVM unit test (ver informe de la fase).
 *
 * La config de [Json] se replica IDÉNTICA a la de `ProjectStorage.json`
 * (`ignoreUnknownKeys=true, prettyPrint=true, encodeDefaults=true`) a
 * propósito, para que el test refleje el comportamiento real de
 * producción y no una configuración inventada.
 */
class ProjectDataSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun representativeProject(): ProjectData = ProjectData(
        id = "proj-123",
        name = "Mi Película",
        createdAtMs = 1_700_000_000_000L,
        updatedAtMs = 1_700_000_500_000L,
        projectDurationMs = 45_000L,
        layers = listOf(
            LayerData(
                id = "layer-1",
                imageFileName = "layer-1.png",
                name = "Fondo",
                zIndex = 0,
                parallaxFactor = 0.5f,
                locked = false,
                orderLocked = true,
                visible = true,
                lookSettings = LookSettings(saturation = 1.2f, contrast = 0.9f, warmth = 0.1f),
                keyframes = listOf(
                    Keyframe(timeMs = 0L, translateX = 0f, translateY = 0f, scale = 1f),
                    Keyframe(timeMs = 1_000L, translateX = 0.3f, translateY = -0.2f, scale = 1.5f, rotationDeg = 15f)
                ),
                baseFrame = CameraFrame(translateX = 0f, translateY = 0f, scale = 1f, rotationDeg = 0f, alpha = 1f),
                widthPx = 1080,
                heightPx = 1920,
                colorIndex = 2,
                customColorArgb = -0x1000000,
                importedDefaultColorArgb = -0xFF0001,
                useGradientColor = true,
                gradientAngleDegrees = 45f,
                gradientIsRadial = false
            ),
            LayerData(
                id = "layer-2",
                imageFileName = "layer-2.png",
                name = "Personaje",
                zIndex = 1
            )
        ),
        audioTrack = AudioTrackData(
            audioFileName = "track.m4a",
            displayName = "Música de fondo",
            sourceDurationMs = 180_000L,
            volume = 0.8f,
            muted = false,
            trimStartMs = 5_000L,
            loop = true
        ),
        speedKeyframes = listOf(SpeedKeyframe(timeMs = 0L, speed = 1f), SpeedKeyframe(timeMs = 10_000L, speed = 2f)),
        freezeFrames = listOf(FreezeFrame(id = "f1", atMs = 5_000L, holdMs = 800L)),
        aspectRatio = "REELS",
        fps = 30,
        description = "Descripción de prueba",
        coverImageFileName = "cover.jpg",
        orderIndex = -1_700_000_500_000L,
        releaseYear = 2026,
        genre = "Drama",
        infoDurationMinutes = 91,
        castPhotoFileNames = listOf("cast_0.jpg", null, "cast_2.jpg", null),
        gridEnabled = true,
        gridShapeName = "CIRCLE",
        gridColumns = 4,
        gridRows = 5,
        gridLineColorEnabled = true,
        gridLineHue = 200f,
        gridLineThicknessDp = 2f,
        gridLineOpacity = 0.6f
    )

    @Test
    fun `un proyecto representativo sobrevive intacto una vuelta completa de serializacion`() {
        val original = representativeProject()

        val serialized = json.encodeToString(original)
        val restored = json.decodeFromString<ProjectData>(serialized)

        // data class -> equals estructural: si CUALQUIER campo (no solo el
        // nombre) se perdiera o cambiara en la vuelta, esto falla.
        assertEquals(original, restored)
    }

    @Test
    fun `los campos anidados especificos sobreviven la vuelta, no solo el objeto entero`() {
        val original = representativeProject()
        val restored = json.decodeFromString<ProjectData>(json.encodeToString(original))

        assertEquals(original.layers[0].keyframes, restored.layers[0].keyframes)
        assertEquals(original.layers[0].lookSettings, restored.layers[0].lookSettings)
        assertEquals(original.layers[0].baseFrame, restored.layers[0].baseFrame)
        assertEquals(original.audioTrack, restored.audioTrack)
        assertEquals(original.speedKeyframes, restored.speedKeyframes)
        assertEquals(original.freezeFrames, restored.freezeFrames)
        assertEquals(original.castPhotoFileNames, restored.castPhotoFileNames)
    }

    @Test
    fun `un proyecto minimo sin capas ni audio tambien sobrevive la vuelta`() {
        val minimal = ProjectData(id = "empty", name = "Vacio", createdAtMs = 0L, updatedAtMs = 0L)
        val restored = json.decodeFromString<ProjectData>(json.encodeToString(minimal))
        assertEquals(minimal, restored)
    }

    @Test
    fun `un json viejo sin campos nuevos carga con los defaults, no rompe (compatibilidad hacia adelante)`() {
        // Simula project.json guardado por una version anterior de la app,
        // ANTES de que existieran campos como gridEnabled, colorIndex,
        // orderLocked, etc. (todos con default en ProjectData/LayerData
        // justamente por esto). ignoreUnknownKeys=true además protege en
        // la otra direccion: un project.json de una version FUTURA con
        // campos que esta version todavia no conoce tampoco rompe la carga.
        val oldJson = """
            {
                "id": "old-project",
                "name": "Proyecto viejo",
                "createdAtMs": 1000,
                "updatedAtMs": 2000,
                "layers": [
                    {"id": "l1", "imageFileName": "l1.png", "name": "Capa 1", "zIndex": 0}
                ],
                "unknownFutureField": "algo que esta version no conoce todavia"
            }
        """.trimIndent()

        val restored = json.decodeFromString<ProjectData>(oldJson)

        assertEquals("old-project", restored.id)
        assertEquals(1, restored.layers.size)
        assertEquals(false, restored.layers[0].orderLocked) // default para proyectos viejos
        assertEquals(null, restored.layers[0].colorIndex)   // default: "sin asignar todavia"
        assertEquals(false, restored.gridEnabled)            // default de la grilla
        assertEquals(8_000L, restored.projectDurationMs)     // default declarado en ProjectData
    }
}
