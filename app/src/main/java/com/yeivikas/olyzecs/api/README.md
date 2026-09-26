# com.yeivikas.olyzecs.api

## Estado actual (Fase 4.2 — Migración de consumidores hacia EliNer API v1)

**Auditoría de Fase 4.2 completada para Layer/Camera/Timeline/Audio** (ver
"Fase 4.2 — hallazgos" más abajo). **Migración de consumidores de UI**
(`EditorScreen`/`EditorBottomBar`/`TimelineView` llamando a `LayerApi`/
`CameraApi`/`TimelineApi`/`AudioApi` en vez de a `EditorViewModel`
directo) queda **PENDIENTE** — bloqueada por una incompatibilidad real de
firmas (`suspend` vs. síncrono) documentada abajo, no por falta de
tiempo. Una corrección puntual y acotada sí se aplicó esta fase (ver
"Fase 4.2 — cambios aplicados").

### Fase 4.2 — hallazgos

1. **`EditorViewModel` como `ActiveProjectReader`/`ActiveProjectMutator`
   ya está limpio.** Se auditaron las 9 implementaciones `override` de
   Layer (`getLayers`/`getLayer`/`addLayers`/`setLayerVisible`/
   `setLayerLocked`/`setLayerOrderLocked`/`setLayerZIndex`/
   `setLayerLookSettings`/`deleteLayer`) — todas delegan correctamente a
   los métodos canónicos ya existentes (`toggleLayerVisibility`,
   `toggleLayerLock`, `reorderLayer`, `removeLayer`, etc.), sin lógica
   duplicada. No hay ningún bug de "dos caminos que hacen lo mismo
   distinto" para corregir acá — ya es un único punto de verdad.

2. **Bloqueo real para migrar consumidores de UI: `suspend` vs.
   síncrono.** Los 4 contratos de dominio (`LayerApi`/`CameraApi`/
   `TimelineApi`/`AudioApi`) exponen sus mutaciones como `suspend fun` —
   correcto para el contrato público. Pero los métodos canónicos que HOY
   ya usa la UI (`toggleLayerVisibility`, `reorderLayer`,
   `updateBaseFrameForSelectedLayer`, `addKeyframeToSelectedLayer`,
   `setAudioVolume`, etc.) son deliberadamente NO-`suspend` — llamadas
   sincrónicas "fire-and-forget" que internamente lanzan su propia
   corrutina (`viewModelScope.launch`) cuando corresponde. Esto ya está
   documentado explícitamente en el KDoc de `ActiveProjectMutator`
   (sección de audio: "NO se llaman igual... para no romper esas
   funciones existentes... ni obligarlas a volverse suspend").
   Reemplazar mecánicamente cada `viewModel.toggleLayerVisibility(id)`
   (y equivalentes) de `EditorScreen.kt`/`EditorBottomBar.kt`/
   `TimelineView.kt` por `layerApi.setVisible(id, visible)` (`suspend`)
   exige envolver cada uno de esos sitios de llamada en un
   `CoroutineScope` — decenas de sitios repartidos en ~22.000 líneas
   combinadas, sin compilador disponible en este entorno para verificar
   cada uno. Hacerlo a ciegas es exactamente el tipo de riesgo que esta
   fase pide evitar (sección 26 del brief: "no quiero una solución que
   simplemente compile"). Se documenta como bloqueo real, no como
   pendiente por pereza — el trabajo concreto que falta es acotado y
   mecánico, pero necesita poder compilarse/probarse para hacerse con
   seguridad.

3. Mismo patrón confirmado en Camera (`updateBaseFrameForSelectedLayer`/
   `addKeyframeToSelectedLayer`, no-`suspend`, vs. `CameraApi.setBaseFrame`/
   `setKeyframe`, `suspend`) y en Audio (`ActiveProjectMutator` ya
   documenta explícitamente la razón del split de nombres `apply*` vs.
   `set*` por este mismo motivo).

### Fase 4.2 — cambios aplicados

- **Sección 11 del brief ("Export especial")**: `EditorViewModel.exportVideo()`
  creaba una instancia nueva de `ExportApiImpl` en cada llamada. Se
  confirmó que `ExportApiImpl` es *stateless* (no es un bug de
  comportamiento), y se corrigió con una memoización perezosa
  (`exportApiFor(appContext)`, campo privado `cachedExportApi`) — sin
  guardar `Context` en el constructor del ViewModel (evita el riesgo de
  fuga que motivó ADR-002 a mantener `Context` solo por parámetro) y sin
  introducir singleton ni service locator: la instancia sigue siendo
  propiedad explícita de este `EditorViewModel`.

### Fase 4.2 — siguiente paso recomendado (no ejecutado en esta fase)

Antes de migrar los sitios de llamada de UI, definir explícitamente
UNA de estas dos estrategias (afecta a los 4 dominios por igual, así
que conviene decidirla una sola vez, no por dominio):

  a) Los 4 contratos de dominio ganan una variante NO-`suspend`
     "fire-and-forget" (mismo patrón que `ActiveProjectMutator` ya usa
     para audio con el prefijo `apply*`), que internamente lanza su
     propia corrutina — los sitios de llamada de UI cambian de nombre
     de función pero no de forma (siguen siendo llamadas síncronas).

  b) Los sitios de llamada de UI migran a `suspend`, envueltos en el
     `CoroutineScope`/`LaunchedEffect` que ya exista en cada composable
     — más alineado con Compose idiomático, pero families de cambios
     más grande y más sensible a errores sin compilador a mano.

Cualquiera de las dos requiere poder compilar y probar cada sitio de
llamada — se deja registrado acá como la continuación concreta de esta
fase, no como diseño abierto.

---

## Contexto histórico (Fase 4.1 y anteriores)

Lo que sigue describe el estado de este paquete **antes** de la
auditoría de Fase 4.2 de arriba. Se conserva como registro — la tabla
de dominios y su estado "IMPLEMENTADO / con consumidor real / sin
consumidor externo real" sigue siendo precisa hoy (la Fase 4.2 no migró
ningún consumidor nuevo de UI, ver hallazgos arriba), solo agrega la
razón concreta del porqué.


Este paquete **ya no está vacío ni reservado**: es la frontera
arquitectónica real entre la aplicación y el motor de edición.

```
Usuario → UI → EliNer API → EliNer Engine → Render / Audio / Animación / Física / Exportación
```

`EliNer API` es la capa que se ve DESDE afuera del motor — no es parte del
motor. Por eso no vive dentro de `engine/` (ni siquiera en `engine/core`):
`engine/core` son contratos que los módulos DEL MOTOR comparten entre sí
(ej. `PixelColorSource`, que hoy implementa `engine.render.GLRenderer`).
`api/` es la fachada que el motor expone hacia afuera — un nivel más
arriba, entre `viewmodel/` y `engine/`.

**EliNer API v1 contiene 9 dominios**, con contratos reales (interfaces +
implementaciones) en código, no solo diseño en papel:

```
api/
    EliNerApi.kt / EliNerApiImpl.kt     — fachada principal, agregador
                                           sin ownership propio (9
                                           dominios contractuales; 8
                                           actualmente componibles,
                                           `render` pendiente de
                                           composición productiva, ver
                                           más abajo)
    model/LayerSnapshot.kt              — modelo público de capa
    project/ActiveProjectReader.kt      — lectura del proyecto activo
                                           (implementada por
                                           EditorViewModel)
    project/ActiveProjectMutator.kt     — mutación acotada del proyecto
                                           activo (implementada por
                                           EditorViewModel)
    scene/LayerApi.kt + LayerApiImpl.kt             — IMPLEMENTADO, sin consumidor externo real
    camera/CameraApi.kt + CameraApiImpl.kt          — IMPLEMENTADO, sin consumidor externo real
    animation/AnimationApi.kt + AnimationApiImpl.kt — IMPLEMENTADO, consumidor real (EditorViewModel)
    timeline/TimelineApi.kt + TimelineApiImpl.kt    — IMPLEMENTADO (con puente temporal en generateThumbnail), sin consumidor externo real
    audio/AudioApi.kt + AudioApiImpl.kt             — IMPLEMENTADO, sin consumidor externo real
    export/ExportApi.kt + ExportApiImpl.kt          — IMPLEMENTADO, consumidor real (EditorViewModel.exportVideo)
    mesh3d/Mesh3DApi.kt + Mesh3DApiImpl.kt          — IMPLEMENTADO, consumidor real (EditorViewModel.renderExtrude3D)
    distortion/DistortionApi.kt + DistortionApiImpl.kt — IMPLEMENTADO, consumidor real (EditorViewModel.renderDistortion)
```

Leyenda de estado, por dominio:

- **IMPLEMENTADO**: el `*ApiImpl` real existe y delega correctamente al
  Engine (todos los de la tabla de arriba).
- **Con consumidor real**: `EditorViewModel` ya llama a esa API en vez
  de al Engine directo — hoy son `animation`, `export`, `mesh3d` y
  `distortion` (4 de los 9 dominios).
- **Sin consumidor externo real**: `LayerApiImpl`/`CameraApiImpl`/
  `AudioApiImpl`/`TimelineApiImpl` ya se construyen con datos reales
  en `MainActivity` (composition root), pero ningún código de la app
  los invoca todavía — la UI sigue hablando con `EditorViewModel`
  directo para esas operaciones. Migrar esos consumidores es trabajo
  posterior, fuera de alcance de esta fase.
- **PUENTE TEMPORAL**: `TimelineApiImpl.generateThumbnail` — ver KDoc
  de `TimelineApi.kt`.
- **PENDIENTE**: `render` (el único de los 9 dominios sin conectar a
  la fachada, ver `EliNerApi.kt`/`EliNerApiImpl.kt`) — su respaldo real
  (`PixelColorSource`/`GLRenderer`) requiere una superficie GL viva
  que hoy solo existe dentro de un Composable de UI (`ui/GLPreview.kt`),
  fuera de alcance de esta fase (no se modifica UI/renderer). Por esto
  mismo, `MainActivity` construye hoy los 8 dominios restantes como
  instancias sueltas (`eliNerLayerApi`, `eliNerCameraApi`, etc.) listas
  para componerse en un único `EliNerApiImpl`, pero todavía no arma esa
  instancia única — sería un objeto con 8 de 9 campos resueltos y
  ninguna forma no artificial de resolver el noveno ahí mismo. Esto es
  **intencional**: no se fabrica una implementación falsa de `render`
  solo para poder decir que la fachada ya está completa.

La ausencia de una instancia productiva completa de `EliNerApiImpl` **no
significa que la API esté vacía** — significa que 8 de 9 dominios ya son
componibles/operables de forma real, y que el noveno (`render`) tiene un
bloqueo concreto y documentado, no un vacío de diseño.

`EditorViewModel` sigue siendo el owner real del estado del proyecto
activo — ninguna pieza de `api/` lo reemplaza ni duplica su estado (ver
`ActiveProjectReader`/`ActiveProjectMutator` arriba).

## Alcance: qué queda fuera de EliNer API

`ProjectsViewModel` (gestión de la biblioteca de proyectos: listar, crear,
duplicar, borrar) queda fuera de este diagrama a propósito. `EliNer API`
es la fachada del MOTOR (render/audio/animación/física/exportación) — la
gestión de archivos de proyecto es un concern de persistencia/aplicación,
no del motor, y seguirá hablando con `data/ProjectStorage` directo como
hoy, sin pasar por acá.

---

## Contexto histórico (anterior a la implementación real de la API)

Las secciones que siguen describen etapas **anteriores** de este paquete
y se conservan únicamente como registro histórico. Ninguna de ellas
representa el estado actual — ver la sección "Estado actual" arriba.

### Histórico — Etapa 0 (paquete reservado, sin código)

En su momento, este paquete estaba reservado y vacío a propósito: la
Etapa 4 del plan de refactorización de entonces era explícita en no
crear todavía la API, solo preparar el proyecto. El flujo de esa etapa
era `UI → EditorViewModel → engine/*` directo, sin ninguna capa
intermedia.

El mapeo histórico de qué llamada de `EditorViewModel` correspondería a
qué método de la futura API estuvo descrito en un documento de proceso
(`ETAPA4_PREPARACION_ELINER_API.md`) que pertenecía al proyecto de
refactorización y no se versionó en este repositorio.

### Histórico — Etapa 1 (estructura base del contrato)

Se introdujo la estructura base del CONTRATO (interfaces + modelos
públicos), sin conexión real al motor todavía: en ese momento solo 2 de
los 8 dominios definidos hasta entonces tenían implementación real
conectada.

