# ADR-005 — Canvas con dimensiones propias (reemplazo de `AspectRatioPreset`)

**Estado:** Decidido. Pendiente de implementación por fases (ver
`docs/fases/`, fases a crear: A–G).

## Contexto
`AspectRatioPreset` (`engine/scene/AspectRatioPreset.kt`) es un enum de
3 valores (`REELS`, `SQUARE`, `WIDESCREEN`) que solo representa una
*relación* (9:16, 1:1, 16:9), sin ancho/alto en píxeles. Las
dimensiones reales solo existen dentro de
`computeExportDimensions(quality, aspect)`
(`engine/export/ExportQuality.kt`), calculadas al momento de exportar
combinando esa relación con el `ExportQuality` elegido.

Verificado en el código real: el `Box` de Compose que contiene el
preview GL (`EditorScreen.kt`, ~línea 1986) no tiene ningún
`.aspectRatio(...)` ligado a `AspectRatioPreset` — usa
`.fillMaxWidth()` + `.fillMaxHeight(0.46f)` o `.weight(1f)` según
`canvasFillsScreen`. El área de edición nunca tuvo, en ningún momento
de la edición, la forma del aspecto elegido al crear el proyecto.

`ProjectData.aspectRatio` (`data/ProjectModels.kt`) persiste el
`name` del enum como `String`, con fallback ya implementado en
`ProjectStorage.kt` (`runCatching { AspectRatioPreset.valueOf(...) }
.getOrDefault(REELS)`) si el valor guardado no se reconoce.

## Problema
1. No existe un Canvas real: el "espacio de composición del proyecto"
   nunca se modeló como entidad propia, solo como un parámetro de
   exportación reutilizado.
2. El enum fijo no escala: agregar un formato nuevo (4:5, 3:4, etc.)
   requiere tocar el enum y, con él, cada `when` exhaustivo que lo
   consume (`computeExportDimensions`, UI de creación de proyecto).
3. Presets que comparten el mismo tamaño físico (Instagram Reel,
   TikTok, YouTube Shorts → 1080×1920) no tienen forma de compartir esa
   dimensión sin duplicarla si se modelan como una lista plana de
   presets con ancho/alto embebido.
4. Un cambio futuro al catálogo de presets no debe alterar proyectos
   ya guardados con un preset que después cambie o desaparezca.

## Opciones consideradas

**D1 — Representación de la relación de aspecto**
- A) `String` (`"16:9"`). Descartada: obliga a parsear/comparar texto
  para una operación que es aritmética.
- B) Enum fijo (estado actual). Descartada: no escala, cada preset
  nuevo requiere tocar código y recompilar todos los `when`
  exhaustivos que lo consumen.
- C) Value type numérico `AspectRatio(widthUnits, heightUnits)` con
  `ratio: Float` calculado. **Elegida.**

**D2 — Deduplicación de formatos compartidos entre plataformas**
- A) Cada preset con su ancho/alto embebido directamente. Descartada:
  duplica el mismo número (1080×1920) en 3+ lugares sin una fuente de
  verdad única.
- B) `CanvasFormat(widthPx, heightPx)` compartido + `FormatPreset`
  (id, label, categoría) que referencia un `CanvasFormat`. **Elegida.**

**D3 — Resolución lógica vs. física del preview**
- A) Canvas lógico y framebuffer de preview del mismo tamaño, con un
  techo de renderizado fijo independiente del tamaño del proyecto (el
  preview nunca renderiza por encima de esa cota, sin importar el
  Canvas). **Elegida para esta fase.**
- B) Desacople completo lógico/físico con escalado explícito (Canvas
  1080×1920 lógico → framebuffer 2160×3840 físico). Diferida: no hay
  hoy un Canvas grande real que sea lento; diseñarla ahora sería
  resolver un problema que no existe todavía.

**D4 — Alcance de esta fase**
- A) Implementar todo de una vez (Canvas + catálogo + Custom +
  migración + Safe Areas + versionado de schema + resolución
  lógica/física). Descartada: sobreingeniería — varias de esas piezas
  no tienen un caso de uso real hoy.
- B) Fase 1 = Canvas real + catálogo como datos + Custom + migración +
  preview ligado al aspecto real. Fase 2 (diferida, no diseñada en
  detalle todavía) = Safe Areas, versionado de schema más allá de un
  entero simple, resolución lógica/física separada, compatibilidad
  3D/motion-capture/colaboración online. **Elegida.**

## Decisión
**C (value type) + B (CanvasFormat compartido) + A (mismo tamaño con
techo de render) + B (Fase 1 acotada, Fase 2 diferida y documentada).**

Nuevas abstracciones de dominio, sin dependencias de `android.*`/
`androidx.*`, ubicadas en `engine/scene/` (mismo paquete que
`AspectRatioPreset` hoy, por la misma razón que ya documenta su KDoc:
describen el lienzo del proyecto, no un parámetro de exportación
puntual):

```kotlin
data class AspectRatio(val widthUnits: Int, val heightUnits: Int) {
    val ratio: Float get() = widthUnits.toFloat() / heightUnits.toFloat()
}

data class CanvasFormat(val widthPx: Int, val heightPx: Int) {
    val aspect: AspectRatio get() = AspectRatio.of(widthPx, heightPx)
}

data class FormatPreset(
    val id: String,           // ej. "instagram.reels.vertical"
    val label: String,
    val subtitle: String,
    val category: PresetCategory,
    val format: CanvasFormat
)

data class CanvasSpec(
    val widthPx: Int,
    val heightPx: Int,
    val originPresetId: String? = null   // informativo, no una dependencia funcional
)

interface FormatCatalog {
    fun categories(): List<PresetCategory>
    fun presetsIn(category: PresetCategory): List<FormatPreset>
    fun findById(id: String): FormatPreset?
}

interface CanvasFactory {
    fun fromPreset(preset: FormatPreset): CanvasSpec
    fun custom(widthPx: Int, heightPx: Int): Result<CanvasSpec>
}
```

`Orientation` (`PORTRAIT`/`LANDSCAPE`/`SQUARE`) se deriva siempre de
`widthPx`/`heightPx` — nunca se persiste, para que no pueda contradecir
las dimensiones reales.

`ProjectData` agrega `canvasWidthPx`, `canvasHeightPx`,
`canvasOriginPresetId`, `canvasSchemaVersion` (default `0` = formato
viejo). El campo `aspectRatio: String` se conserva, deprecado,
solo para la ruta de migración (mismo criterio de compatibilidad hacia
atrás que ya sigue el resto de `ProjectData` con campos nullable/con
default para no romper proyectos guardados antes de cada mejora).

`computeExportDimensions` cambia de firma de
`(ExportQuality, AspectRatioPreset)` a `(ExportQuality, CanvasSpec)` —
deja de inventar el aspecto desde un enum de 3 valores y en cambio
escala el aspecto real del Canvas a la calidad elegida.

## Consecuencias
- `AspectRatioPreset` queda `@Deprecated`, usado solo por el bloque de
  migración de proyectos antiguos (no se elimina: rompería
  `runCatching { AspectRatioPreset.valueOf(...) }` sobre proyectos ya
  guardados).
- El `Box` del preview en `EditorScreen.kt` pasa a reflejar el aspecto
  real del `CanvasSpec` del proyecto — antes no reflejaba ningún
  aspecto. Es el cambio de mayor riesgo visual/de interacción
  (hit-test y arrastre de capas dependen de `hitTestBoxSize`); se
  implementa en su propia fase (F), aislada del resto.
- Ningún archivo del nuevo dominio de Canvas puede importar
  `android.*`/`androidx.*` — mismo principio que ya cumplen
  `Layer.kt`/`CameraFrame.kt`.
- Safe Areas, versionado de schema más allá de un entero, y el
  desacople lógico/físico del framebuffer quedan fuera de esta fase,
  documentados en la propuesta técnica previa, sin código todavía.

## Plan de fases (a ejecutar una por vez, con checkpoint del usuario entre cada una)
- **Fase A** — Dominio (`AspectRatio`, `CanvasFormat`, `FormatPreset`,
  `CanvasSpec`, `FormatCatalog`, `CanvasFactory`) + tests. Sin wiring,
  riesgo nulo.
- **Fase B** — `StaticFormatCatalog` con datos reales + tests de
  catálogo (sin ids duplicados, todo preset resuelve a un formato
  válido).
- **Fase C** — Migración de `ProjectData`/`ProjectStorage` + tests
  con proyectos `.olycs` reales como fixture (el Canvas migrado debe
  dar exactamente las mismas dimensiones que hoy calcula
  `computeExportDimensions` para ese mismo proyecto).
- **Fase D** — Nueva firma de `computeExportDimensions` + call sites
  en `EditorViewModel`.
- **Fase E** — `CreateProjectDialog` (`ProjectsScreen.kt`): grid de
  presets por categoría + diálogo Custom con validación 100–4096 px.
- **Fase F** — Ligar el `Box` del preview al aspecto real del
  `CanvasSpec`. Aislada, QA manual en dispositivo antes de fusionar.
- **Fase G** — Deprecar `AspectRatioPreset`, limpieza final.
