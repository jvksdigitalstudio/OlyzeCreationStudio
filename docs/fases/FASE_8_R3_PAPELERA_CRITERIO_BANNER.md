# FASE 8-R3 — Papelera de capas: criterio del banner de aviso

## Estado
Corregido. Ajuste de comportamiento a pedido explícito del cliente (con
capturas), sobre el trabajo de FASE_8_R1_PAPELERA_PANEL_ANCLADO.md y
FASE_8_R2_PAPELERA_FIX_INVISIBLE_Y_SCROLL.md.

## Contexto: dos piezas distintas, dos criterios distintos
La papelera de capas siempre tuvo dos superficies de aviso separadas,
con arquitecturas distintas a propósito:

1. **El ícono de la papelera (toolbar) + su badge numérico** — vive en
   `EditorScreen.kt`, condicionado a `state.trashedLayers.isNotEmpty()`.
   Refleja el estado **en tiempo real** mientras se edita: aparece desde
   la primera capa que se elimina, sin importar cuántas queden vivas en
   el lienzo, y el número se actualiza al instante con cada cambio.
   **Esta pieza ya funcionaba así antes de esta fase** — confirmado con
   capturas del propio cliente (ícono con badge "2" visible con 3+ capas
   en el lienzo).

2. **El banner flotante ámbar** ("Tenés N capas eliminadas recientemente
   · Ver papelera") — vive en `EditorUiState.showTrashRecoveryBanner`,
   que **estructuralmente solo se enciende desde un único punto**:
   `ProjectStorage.loadProject` (ver el campo `LoadedProject.
   showTrashRecoveryBanner`). Ninguna operación de edición en caliente
   (`removeLayer`, autoguardado, etc.) lo toca — por diseño, este banner
   solo puede aparecer al **abrir/reabrir** un proyecto, nunca en medio
   de una sesión activa.

## El pedido
El ícono (pieza 1) ya se comportaba como se pedía. Lo que había que
corregir era el **criterio de disparo** del banner (pieza 2): antes
exigía `layers.size == 1` — es decir, solo avisaba si el proyecto había
quedado al límite, con una sola capa viva. Eso lo dejaba **inconsistente
con el ícono**: un proyecto podía tener 5 capas en la papelera y jamás
mostrar el banner, porque nunca llegó a quedarse con una sola capa viva
en el lienzo.

El criterio pedido: el banner debe aparecer **cada vez que se reabre un
proyecto** que tiene algo sin revisar en la papelera — sin importar
cuántas capas queden — mostrando cuántas hay para recuperar, y
descartable con la (X) (mecanismo que ya existía).

## Corrección

`ProjectStorage.kt`, función `loadProject`:

```kotlin
// Antes
val showTrashRecoveryBanner = layers.size == 1 && trashSummary.items.isNotEmpty() && !data.trashBannerAcknowledged

// Ahora
val showTrashRecoveryBanner = trashSummary.items.isNotEmpty() && !data.trashBannerAcknowledged
```

Se eliminó únicamente la condición `layers.size == 1`. El resto de la
lógica de persistencia no cambió y sigue siendo la correcta para el
comportamiento pedido:

- `trashBannerAcknowledged` (en `ProjectData`) se reinicia a `false` en
  `ProjectStorage.saveProject` cada vez que una capa **nueva** cae a la
  papelera en ESE guardado — así que un episodio nuevo de eliminación
  vuelve a merecer aviso la próxima vez que se abra el proyecto.
- `EditorViewModel.dismissTrashBanner()` (ya existente, sin cambios) es
  lo que atiende la (X)/"Ver papelera": apaga el banner en memoria al
  instante y persiste `trashBannerAcknowledged = true` en segundo plano,
  para que no vuelva a aparecer en la próxima apertura mientras no caiga
  nada nuevo a la papelera.

Como `showTrashRecoveryBanner` solo se escribe desde `loadProject`, el
resultado neto es exactamente el pedido: el banner nunca interrumpe en
medio de una sesión de edición activa (solo el ícono, silencioso,
refleja cambios en caliente) — únicamente al volver a entrar al
proyecto, y solo si queda algo sin que el usuario lo haya descartado ya.

## Documentación actualizada
Se corrigieron, para que queden consistentes con el comportamiento
real, los comentarios KDoc que describían el criterio viejo ("una sola
capa viva") en:
- `LayerTrashScreen.kt` — KDoc de `TrashRecoveryBanner`.
- `EditorViewModel.kt` — KDoc de `EditorUiState.showTrashRecoveryBanner`
  y de `dismissTrashBanner()`.
- `ProjectModels.kt` — KDoc de `ProjectData.trashBannerAcknowledged`.

## Verificación pendiente en dispositivo real
1. Eliminar una capa cualquiera (sin llegar a "una sola capa viva") →
   el ícono de papelera debe aparecer de inmediato con su contador — ya
   funcionaba, sirve para confirmar que no se rompió nada.
2. Cerrar el proyecto (volver a "Mis proyectos") y volver a abrirlo →
   el banner ámbar debe aparecer, mostrando la cantidad correcta.
3. Tocar la (X) del banner, salir del proyecto y reabrirlo de nuevo SIN
   eliminar nada más → el banner NO debe reaparecer (ya reconocido).
4. Eliminar otra capa más (episodio nuevo), salir y reabrir → el banner
   debe volver a aparecer, reflejando el conteo actualizado.
