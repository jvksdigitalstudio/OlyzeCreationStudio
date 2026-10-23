# ADR-001 — Reubicación de Timeline a `engine/timeline/`

**Estado:** Aplicado (Fase D).

## Contexto
`TimelineExpansionPolicy`, `TimelineDurationManager`, `TimelineLimits`,
`TimelineState` y `TimelineEvents` vivían en el paquete `timeline/`
(paralelo a `engine/`, no dentro de él) pese a ser lógica de motor pura:
cero imports de `android.*`/`androidx.*` en ninguno de los 5 archivos.
`engine/timeline/ThumbnailRenderer.kt` ya existía como precedente exacto
del mismo dominio dentro de `engine/`.

## Problema
La definición de "Engine" del proyecto ("todo lo que `engine/*` contiene
es motor, verificado archivo por archivo") dejaba de ser 100% cierta
mientras este caso no se resolviera — quedaba un componente de motor
real fuera de `engine/` sin una razón documentada.

## Opciones consideradas
- **A) Mover a `engine/timeline/`.** Consistencia total de
  nomenclatura. Costo: toca 4 consumidores (`EditorScreen`,
  `ProjectsScreen`, `EditorViewModel`, `EditorViewModelFactory`) + 2
  archivos de test.
- **B) Dejarlo donde está**, documentando por qué como excepción
  aceptada.
- **C) Sub-paquete nuevo** separado de `ThumbnailRenderer`. Descartada:
  fragmentación artificial sin beneficio (ambos ya comparten dominio).

## Decisión
**Opción A.** Movido en la Fase D.

## Justificación
Precedente directo ya exitoso (`Extrude3D` en Fase B, mismo patrón
mecánico). El costo es bajo y verificable (balance de llaves, DAG sin
cambios, tests actualizados junto con el código de producción).

## Consecuencias
- `package` de los 5 archivos cambiado a
  `com.yeivikas.olyzecs.engine.timeline`.
- Imports actualizados en `EditorScreen.kt`, `ProjectsScreen.kt`
  (comentario), `EditorViewModel.kt`, `EditorViewModelFactory.kt`.
- Tests movidos a
  `app/src/test/java/.../engine/timeline/` con su `package` actualizado
  (`TimelineDurationManagerTest`, `TimelineExpansionPolicyTest`).
- Cero cambios de lógica/comportamiento — verificado línea por línea.

## Impacto futuro en EliNer
El dominio Timeline queda dentro de `engine/*` sin ambigüedad de
paquete, listo para quedar detrás de EliNer API sin otro movimiento.

## Impacto futuro en C++
Agrupa 3 candidatos fuertes a migración (matemática pura, sin Android)
junto a `ThumbnailRenderer` (que NO migra, depende de EGL) — al migrar,
solo se llevarían los 3 archivos de política, no todo `engine/timeline/`.
