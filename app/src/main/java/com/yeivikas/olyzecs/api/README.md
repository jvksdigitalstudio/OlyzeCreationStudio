# com.yeivikas.olyzecs.api

Reservado para **EliNer API**. Vacío a propósito — la Etapa 4 del plan de
refactorización es explícita: *"No quiero crear EliNer API todavía... Todavía
sin implementar la API. Solo preparando el proyecto."*

## Por qué es un paquete aparte de `engine/`

```
Usuario → UI → EliNer API → EliNer Engine → Render / Audio / Animación / Física / Exportación
```

`EliNer API` es la capa que se ve DESDE afuera del motor — no es parte del
motor. Por eso no vive dentro de `engine/` (ni siquiera en `engine/core`):
`engine/core` son contratos que los módulos DEL MOTOR comparten entre sí
(ej. `PixelColorSource`, que hoy implementa `engine.render.GLRenderer`).
`api/` va a ser la fachada que el motor completo expone hacia afuera —
un nivel más arriba, entre `viewmodel/` y `engine/`.

## Qué va a pasar acá cuando se construya

Hoy el flujo es:

```
UI → EditorViewModel → engine/*
```

Cuando se construya `EliNer API`, pasará a ser:

```
UI → EditorViewModel → api.EliNerApi → engine/*
```

`EditorViewModel` (ver su KDoc de clase) ya está documentado como el punto
de inserción: sus funciones públicas de hoy (`addLayer`, `setKeyframe`,
`exportVideo`, etc.) son candidatas 1 a 1 a pasar de llamar `engine/*`
directo a llamar `EliNerApi.algo(...)`. El mapeo completo de qué llamada
de hoy correspondería a qué método de la futura API está en
`ETAPA4_PREPARACION_ELINER_API.md`, en la raíz del proyecto de
refactorización (no se versiona acá para no mezclar documentación de
proceso con código fuente).

## Qué NO incluye este paquete (todavía)

- Ninguna clase, interfaz ni función real.
- Ningún cambio en cómo `EditorViewModel` llama a `engine/*` hoy —
  sigue llamándolo directo, a propósito, hasta que esta capa exista.

## Estado real (Etapa 1 — Implementación de estructura base)

Lo de arriba describe el estado ANTES de esta etapa — se conserva sin
editar porque sigue siendo la explicación correcta de por qué el
paquete existe. A partir de acá, lo que cambió:

Existe la estructura base del CONTRATO (interfaces + modelos
públicos), en:

```
api/
    EliNerApi.kt              — fachada principal (Paso 4)
    model/LayerSnapshot.kt    — único modelo público nuevo (Layer sin
                                 Bitmap/glTextureId/CameraTrack vivos)
    scene/LayerApi.kt
    camera/CameraApi.kt
    animation/AnimationApi.kt + AnimationApiImpl.kt   ← IMPLEMENTADO
    timeline/TimelineApi.kt
    audio/AudioApi.kt
    export/ExportApi.kt
    mesh3d/Mesh3DApi.kt + Mesh3DApiImpl.kt            ← IMPLEMENTADO
```

Solo 2 de los 8 dominios (`animation`, `mesh3d`) tienen una
implementación real conectada al motor — son los únicos sin ninguna
dependencia de `Context`/UI/ViewModel para funcionar, así que
implementarlos no tocó ningún consumidor existente. El resto define el
contrato (compila, es auditable) pero no está conectado — eso es
migración de consumidores, deliberadamente para una etapa posterior.
`EditorViewModel` sigue llamando a `engine/*` directo, sin cambios, tal
como este README ya decía arriba.

Ver el informe completo de esta etapa para el detalle técnico
(archivos, verificación, riesgos, decisiones).

## Alcance: qué queda fuera de EliNer API

`ProjectsViewModel` (gestión de la biblioteca de proyectos: listar, crear,
duplicar, borrar) queda fuera de este diagrama a propósito. `EliNer API`
es la fachada del MOTOR (render/audio/animación/física/exportación) — la
gestión de archivos de proyecto es un concern de persistencia/aplicación,
no del motor, y seguirá hablando con `data/ProjectStorage` directo como
hoy, sin pasar por acá.
