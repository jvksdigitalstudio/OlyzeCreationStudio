# Fase — Fade (fundidos) pasa del cajón «Módulos» al menú del clip (2026-10-18)

Continúa `FASE_AUDIO_LOOP_EN_MENU_DEL_CLIP.md` (mismo criterio: ajustes por clip viven en el menú del clip).

## Cambio
| Antes | Ahora |
|-------|-------|
| Módulos → Audio → **Fade** abría una ventana flotante con dos deslizadores («Fade in» / «Fade out», 0–5000 ms). | **Fade** se retiró del cajón. Mantener presionado un clip → **Fundidos** → diálogo con **Fundido de entrada** y **Fundido de salida**. |

«Fade in / fade out» y «fundido de entrada / salida» son lo mismo; la interfaz usa los nombres en español.
Del cajón «Módulos → Audio» solo quedaba **Recorte** (retirado después: ver `FASE_AUDIO_RECORTE_EN_VENTANA_DEL_CLIP.md`).

## Diseño
- A diferencia de Loop (un interruptor), Fade son DOS valores continuos, así que la opción abre un diálogo (`AudioClipFadeDialog.kt`), como «Renombrar»
  (mismo estilo: superficie recta, borde con el color del carril).
- **Edición en vivo, sin Cancelar**: cada movimiento llama a `AudioClipEditActions.onFadeChange` → `EditorViewModel.setAudioFade` (sin cambios; un solo
  paso de deshacer por gesto). El clip de atrás redibuja las rampas al instante. Botones: «Quitar fundidos» (los deja en 0) y «Listo».
- **Techo real de los deslizadores** (`audioFadeSliderMaxMs`): mitad del tramo audible del clip, con tope de 5 s. Es la regla del motor
  (`effectiveFadesMs`), así el deslizador no tiene recorrido «muerto» donde se mueve el dedo y no cambia lo que suena. Con un clip de menos de 2 ms
  audibles los controles se deshabilitan (`audioFadeControlsEnabled`).
- Se escriben los valores ya acotados, para que el modelo no guarde lo que el deslizador no puede mostrar.
- Sin cambios: modelo (`fadeInMs`/`fadeOutMs`, defaults 400/600), motor, exportación, dibujo de rampas, persistencia y API (`applyAudioFade`).

## Código retirado (sin consumidores)
`AudioModuleId.FADE` (queda solo `TRIM`), la fila «Fade» de `ModulesDrawerPanel`, `audioFadeWindowOpen`, `audioFadeWindowZIndex` y su bloque en
`EditorScreen.kt`, `AudioFadeFloatingWindow.kt` y `res/drawable/ic_fade.xml`.

## Auditoría de duplicados (Loop + Fade)
Búsqueda en `app/`, `docs/` y raíz de: `AudioFadeFloatingWindow`, `AudioToggleFloatingWindow`, `audioFadeWindow*`, `audioLoopWindow*`,
`AudioModuleId.FADE/LOOP`, `ic_fade`, `ic_loop`. Solo quedan menciones históricas en docs y las aserciones de los tests estructurales.
Cada opción existe UNA vez en el menú (el test lo verifica). Los botones de bucle/reversa de la cabecera de la ventana ampliada del clip son otra
función (vista previa, no se guarda) y no se tocaron.

## Pruebas
- `AudioClipFadeDialogTest`: techo = regla del motor, tope de 5 s y mínimo 1 ms, habilitación y rótulos.
- `ClipMenuFadePlacementStructureTest`: cajón sin Fade, sin ventana ni estado, menú con «Fundidos» (una sola vez) y cableado hasta el ViewModel.

## Verificación pendiente (sin SDK/Gradle en esta sesión)
1. `./gradlew assembleDebug testDebugUnitTest`.
2. Módulos → Audio muestra solo Recorte.
3. Mantener presionado un clip → «Fundidos»: mover cada deslizador y ver la rampa del clip cambiar en vivo; «Quitar fundidos» deja ambos en 0.
4. Deshacer revierte todo el gesto de fundido de una vez; guardar y reabrir conserva los valores; exportar aplica los fundidos.
5. Clip corto (p. ej. 1 s): el techo baja a 0,5 s por lado.
6. Menú con 8 opciones: confirmar que no se corta en el borde inferior.
