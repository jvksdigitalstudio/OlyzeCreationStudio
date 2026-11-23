# FASE 8-R2 — Papelera de capas: capa restaurada invisible + cierre al scrollear

## Estado
Corregido. Dos bugs reales, independientes entre sí, en el flujo de la
papelera de capas introducido en FASE_8_PAPELERA_DE_CAPAS.md y anclado
como panel flotante en FASE_8_R1_PAPELERA_PANEL_ANCLADO.md.

## Reporte original
1. Al restaurar una capa desde la papelera, la capa vuelve al lienzo
   pero **no se ve** — el timeline la muestra, el motor la sube a GPU,
   pero visualmente no aparece.
2. Al hacer scroll hacia abajo dentro de la mini-ventana de la papelera
   (para ver más capas y poder seleccionarlas), **la app se cierra**.

---

## BUG 1 — Capa restaurada invisible

### Causa raíz real
`EditorViewModel.restoreLayersFromTrash` conservaba el `zIndex` que la
capa tenía **antes** de eliminarse (comportamiento documentado a
propósito en la versión R1: "el orden de pintado lo sigue decidiendo
zIndex, preservado desde antes de eliminarlas").

Esa decisión es correcta únicamente si nada cambió en el lienzo
mientras la capa estuvo en la papelera. En el uso real casi nunca se
cumple: el usuario borra una capa, sigue editando (importa un fondo
nuevo, agrega otras capas), y ese trabajo posterior queda con `zIndex`
iguales o mayores al de la capa vieja.

`GLRenderer.onDrawFrame` pinta en orden ascendente de `zIndex`:

```kotlin
val orderedSnapshotLayers = renderSnapshot.layers.sortedBy { it.zIndex }
```

Una capa restaurada con `zIndex` más chico que una capa opaca agregada
después (típicamente un fondo, como se ve en el reporte: una capa verde
sólida cubriendo todo el lienzo) queda pintada **por debajo** de esa
capa opaca. El motor la sube a GPU y la dibuja con total normalidad —
no es un bug de render, es un bug de **orden**.

### Corrección
`EditorViewModel.restoreLayersFromTrash` ahora reasigna `zIndex` a las
capas restauradas para que queden siempre **por encima** de todo lo que
ya está en el lienzo en el instante de restaurar — mismo criterio que
ya usa el resto de "agregar una capa" de esta clase (`importImages`,
`importBackgroundLayer`: `startingZIndex` siempre arriba de todo lo
existente). Si se restauran varias capas a la vez, su orden relativo
**entre ellas** se preserva (se ordenan primero por su `zIndex`
original antes de reasignar), para que una restauración múltiple no
revuelva al azar qué quedaba arriba de qué antes de eliminarse juntas.

Archivo: `EditorViewModel.kt`, función `restoreLayersFromTrash`.

---

## BUG 2 — Cierre de la app al scrollear la lista de la papelera

### Causa raíz real
`TrashPanelPositionProvider` (la clase que calcula dónde se dibuja el
panel flotante, anclado al ícono de papelera del toolbar) clampeaba el
eje X contra los bordes de la pantalla:

```kotlin
x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
```

...pero el eje Y **no tenía ningún clamp**:

```kotlin
val y = anchorBounds.bottom + gapPx   // sin verificar que entre en la ventana
```

Con el ícono ancla lo bastante abajo en el toolbar (o en una pantalla
de poco alto), el panel completo — hasta `PANEL_LIST_MAX_HEIGHT_DP` +
encabezado + pie — quedaba posicionado parcialmente **fuera** de los
límites visibles reales de la ventana. En un dispositivo con
navegación por gestos, esa franja fuera de límites cae dentro (o cerca)
de la zona inferior que el sistema operativo reserva para el gesto
"ir a inicio"/"atrás".

El gesto de scroll vertical del `LazyColumn` interno, al empezar o
terminar dentro de esa franja, quedaba capturado por la navegación del
sistema en lugar de llegar al `LazyColumn` — percibido por el usuario
como "la app se cierra" (en realidad la app pasa a segundo plano por un
gesto de sistema mal capturado, no por un crash).

### Corrección
`TrashPanelPositionProvider` ahora aplica al eje Y el mismo criterio de
"encajar siempre dentro de la pantalla" que ya tenía el eje X, con una
estrategia de tres pasos:

1. Preferir **debajo** del ícono (comportamiento normal — sin cambio
   visual para el caso común en el que ya entraba).
2. Si no entra completo debajo, probar **arriba** del ícono.
3. Si tampoco entra completo arriba (panel más alto que el espacio
   disponible, caso extremo), clampear duro contra los bordes reales de
   la ventana.

Además se agregó un margen de seguridad (`screenMarginPx`, mismo valor
que `PANEL_SCREEN_MARGIN_DP` ya usado para el ancho) también en Y, y se
envolvió la construcción de `TrashPanelPositionProvider` en `remember`
(antes se creaba una instancia nueva en cada recomposición — evitable,
aunque no era la causa del cierre).

Archivo: `LayerTrashScreen.kt`, clase `TrashPanelPositionProvider` y su
punto de construcción en `LayerTrashPanel`.

## Verificación pendiente en dispositivo real
Ambos fixes son deterministas y no dependen de temporización/carrera —
se pueden validar así:

- **Bug 1**: eliminar una capa, agregar/importar otra capa nueva (para
  que exista algo con `zIndex` mayor), restaurar la capa eliminada
  desde la papelera → debe aparecer visible, por encima de las demás.
- **Bug 2**: abrir la papelera con el ícono lo más cerca posible del
  borde inferior de la pantalla (o en un dispositivo/tablet de poco
  alto), con 8+ capas eliminadas, y hacer scroll completo hasta el
  final de la lista → el panel debe quedar siempre completamente
  dentro de la pantalla y el scroll no debe sacar la app a segundo
  plano.
