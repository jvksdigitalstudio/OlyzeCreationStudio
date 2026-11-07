# Fase — El cajón «Módulos» muestra la lista del tipo de capa seleccionado (2026-10-18)

## Síntoma (capturas del usuario, pantalla «Cr7»)
Con un carril de audio seleccionado, el cajón de Módulos mostraba Efectos (Sombra, Reflejo), Animación y Cámara (Zoom X): módulos de
imagen/cámara que no tienen sentido ni coherencia sobre audio. Referencia pedida: FL Studio Mobile, cuyo panel «Modules» ofrece solo
los módulos del tipo de pista seleccionada.

## Causa raíz
`ModulesDrawerPanel` pintaba SIEMPRE una única lista (la de imagen), sin importar qué estaba seleccionado.

## Solución (código real, sin parches)
- **Destino explícito** `ModulesTarget { LAYER, AUDIO }` + `modulesTargetFor(audioTrackSelected)` — `ui/ModulesTarget.kt` (puro, probado).
  Un carril de audio seleccionado es excluyente con la selección de capas (`selectedAudioTrackId`), así que basta ese dato.
- `EditorScreen` pasa `modulesTarget` a `SectionPlaceholderPanel` → `ModulesDrawerPanel`, que elige la lista con un `when (target)`:
  - `LAYER` → `LayerModulesList` (Efectos, Animación, Cámara/Zoom X). Es el contenido de siempre, **movido tal cual** a su propia función
    (cero duplicación; ningún módulo se eliminó ni cambió). También es el destino sin nada seleccionado (comportamiento previo).
  - `AUDIO` → `AudioModulesList` (`ui/AudioModulesList.kt`), hoy limpia: solo el rótulo «Sin módulos de audio».
- El título del panel acompaña al destino: «Módulos de capa» / «Módulos de audio». Buscador, marco y animación del cajón son los mismos.
- `ModulesCategoryHeader` y `ModuleRow` pasaron de `private` a `internal` para que los futuros módulos de audio (en archivos propios)
  reutilicen las mismas filas, sin copiarlas.

## Cómo se agregan los módulos de audio reales (dónde enchufarlos)
En `AudioModulesList`, por categorías (`ModulesCategoryHeader` + `ModuleRow`), cada módulo con su lógica en archivos propios, igual que
Zoom X. Nada de eso toca `LayerModulesList`.

## Lo que NO se tocó
Los módulos de capa (Sombra, Reflejo, Animación, Cámara/Zoom X) y sus ventanas flotantes; la pestaña Control; la selección de capas.

## Pruebas
`ModulesTargetTest`: destino por selección; el cajón elige la lista por destino; los módulos de capa siguen existiendo; la lista de audio no
contiene módulos de imagen/cámara; el editor cablea el destino.

## Verificación pendiente (sin SDK/Gradle en esta sesión)
1. `./gradlew assembleDebug testDebugUnitTest`.
2. Seleccionar un carril de audio → Módulos → ☰: solo «Sin módulos de audio»; el título dice «Módulos de audio».
3. Seleccionar una capa de imagen → Módulos → ☰: Efectos, Animación y Cámara (Zoom X abre su ventana) como siempre.
4. Con el cajón abierto, cambiar la selección entre audio e imagen: la lista cambia sin cerrar el cajón.
