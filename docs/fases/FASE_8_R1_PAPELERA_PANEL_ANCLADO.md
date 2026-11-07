# FASE 8-R1 — Papelera de capas: de pantalla completa a panel anclado

> Origen: pedido explícito del usuario/product owner, con tres capturas de
> pantalla adjuntas de la implementación de la Fase 8 (`ui/
> LayerTrashScreen.kt`) mostrando el `LayerTrashDialog` original: un
> `Dialog` que ocupaba el 100% de la pantalla para mostrar una lista de,
> en el ejemplo de las capturas, 5 capas eliminadas. Observación textual
> del usuario: "no crees que la papelera sea una ventana que se despliegue
> debajo de su ícono [...] en lugar de que se muestre toda la pantalla
> grande mejor que sea una ventana chica debajo de su ícono [...]
> calculando profesionalmente no demasiado pequeño [...] ni muy grande ni
> demasiado pequeño". Esta fase es exclusivamente ese cambio — no toca
> modelo de datos, persistencia ni ninguna de las 5 operaciones de
> `ProjectStorage` descritas en la Fase 8 original, que quedan intactas.

## Resumen ejecutivo

| # | Decisión / problema | Resolución | Archivo(s) |
|---|---|---|---|
| 1 | La papelera se abría como `Dialog(usePlatformDefaultWidth = false)` a pantalla completa — desproporcionado para revisar unas pocas capas eliminadas, tapaba el lienzo entero | Reemplazado por un panel flotante implementado con `Popup` + `PopupPositionProvider` personalizado, anclado al propio ícono de papelera del toolbar | `ui/LayerTrashScreen.kt` |
| 2 | El diálogo se hospedaba a nivel raíz de `EditorScreen` (`Box` exterior de la pantalla), desacoplado del ícono que lo abría — posición imposible de anclar a un composable arbitrario con `Dialog` | El panel ahora se declara como hermano del `IconButton` ancla, dentro del mismo `Box`, en la fila de íconos del toolbar | `ui/EditorScreen.kt` |
| 3 | Si el usuario vaciaba la papelera con el panel abierto, la condición de visibilidad del ícono (`trashedLayers.isNotEmpty()`) se volvía falsa — el ícono ancla desaparecería de golpe y se llevaría el panel abierto con él, cortando en seco el estado "papelera vacía" que el propio panel debía mostrar | Condición de visibilidad del `Box` ancla ampliada a `trashedLayers.isNotEmpty() \|\| showLayerTrashDialog`; el badge numérico de cantidad, ahora condicionado por separado, solo a `trashedLayers.isNotEmpty()` | `ui/EditorScreen.kt` |
| 4 | Tamaño y posición del panel no podían ser arbitrarios ("ni muy grande ni muy chico", pedido explícito) ni inconsistentes con el resto de menús anclados a íconos que ya existen en el mismo toolbar (`GridMenu`, el popup de capa en `TimelineView.kt`) | Ancho fijo calculado (320dp, recortado en pantallas angostas), alto de lista acotado a una fracción de la pantalla con scroll interno, y posicionamiento centrado bajo el ícono replicando exactamente el criterio ya establecido por `BelowAnchorCenteredPopupPositionProvider` | `ui/LayerTrashScreen.kt` |
| 5 | El nombre `LayerTrashDialog` y su encabezado grande (ícono circular de 38dp, título de 18sp) tenían sentido para un `Dialog` de pantalla completa pero eran redundantes/sobredimensionados para un panel chico que ya cuelga visualmente de su propio ícono | Renombrado a `LayerTrashPanel`; encabezado condensado (sin ícono decorativo circular, tipografía más chica, botones de acción a ancho de columna en vez de fila completa) | `ui/LayerTrashScreen.kt` |

## 1. Por qué `Popup` y no `Dialog`

`Dialog` (usado en la Fase 8 original) siempre se centra en la ventana y
no admite anclarse a la posición de otro composable — es la herramienta
correcta para un modal que exige toda la atención (por eso las dos
confirmaciones destructivas, `EmptyTrashConfirmDialog` y
`DeleteSelectedFromTrashConfirmDialog`, siguen siendo `AlertDialog`,
sin cambios en esta fase), pero la herramienta equivocada para "una
ventana chica debajo de su ícono".

`Popup`, en cambio, acepta un `popupPositionProvider: PopupPositionProvider`
que recibe, en cada apertura, los bounds reales del ancla
(`anchorBounds`) y el tamaño de la ventana (`windowSize`) para calcular
dónde ubicarse — el mecanismo estándar de Compose para exactamente este
caso, y el mismo que ya usa `DropdownMenu` de Material por debajo. No es
una elección nueva para este proyecto: es el mismo mecanismo que
`GridMenu`, el popup angosto de capa en `TimelineView.kt` y otro menú más
en `EditorScreen.kt` ya usan para sus propios íconos del toolbar (los
cuatro, vía la clase compartida `BelowAnchorCenteredPopupPositionProvider`
declarada al final de `EditorScreen.kt`).

## 2. Posicionamiento (`TrashPanelPositionProvider`)

`LayerTrashScreen.kt` es autocontenido a propósito (ver el comentario
grande al inicio del archivo, sin cambios desde la Fase 8: ni un import
de `EditorViewModel`) — por eso no se reutiliza directamente la clase
`private` `BelowAnchorCenteredPopupPositionProvider` de `EditorScreen.kt`
(no es visible fuera de ese archivo). Se replica su mismo criterio, con
el mismo nombre de parámetro y el mismo `gapPx` de 8px, en una clase
propia:

```kotlin
private class TrashPanelPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
        var x = anchorCenterX - popupContentSize.width / 2
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = anchorBounds.bottom + gapPx
        return IntOffset(x, y)
    }
}
```

Efecto: el panel aparece centrado horizontalmente bajo el ícono de
papelera, recortado contra los bordes de la pantalla si ese ícono está
cerca del borde izquierdo o derecho del toolbar, separado 8px del borde
inferior del ícono. Mismo comportamiento, mismo `gapPx`, que sus vecinos
en el mismo toolbar — dentro de una misma fila de íconos, ningún usuario
debería notar que la papelera está implementada en un archivo distinto.

No se implementó un "flip" hacia arriba cuando no entra el alto completo
del panel hacia abajo (a diferencia de, por ejemplo,
`DropdownMenu`/`ExposedDropdownMenu` de Material). Se evaluó y se
descartó a propósito: el ícono de papelera vive siempre en el toolbar
SUPERIOR de la pantalla de edición, con todo el resto de la pantalla
disponible debajo — el caso que ese flip resolvería (ancla cerca del
borde inferior) no es alcanzable en este punto de la UI, así que
agregarlo sería complejidad sin beneficio real, no una mejora.

## 3. Tamaño del panel (calculado, no arbitrario)

Tres constantes centralizadas al final del bloque del panel, en vez de
números sueltos en medio del composable:

```kotlin
private const val PANEL_WIDTH_DP = 320
private const val PANEL_SCREEN_MARGIN_DP = 12
private const val PANEL_ANCHOR_GAP_DP = 8
private const val PANEL_LIST_HEIGHT_FRACTION = 0.42
private const val PANEL_LIST_MAX_HEIGHT_DP = 340
```

- **Ancho — `320dp`**, recortado con `min(320, screenWidthDp - 24)` para
  no tocar los bordes en pantallas angostas (tablets chicas en vertical,
  teléfonos plegados). Elegido por ser suficiente para mostrar nombre de
  archivo + fecha completa + peso en MB sin elipsis prematura (ver
  `TrashLayerRow`, sin cambios de esta fase), sin acercarse al ancho de
  pantalla completo de un teléfono típico (360–412dp) — se lee como panel
  flotante, no como otra pantalla.
- **Alto de la lista — `min(42% del alto de pantalla, 340dp)`**, no el
  alto del panel completo: la `LazyColumn` interna es la única parte que
  scrollea; encabezado y pie de acciones se mantienen siempre visibles.
  El panel entero nunca fuerza un alto — con pocas capas, se achica al
  contenido real en vez de dejar espacio vacío antes del pie de acciones
  (ver la nota técnica de la sección 4).
- El panel vacío (`items.isEmpty()`) no reserva el espacio de la lista:
  muestra directamente el estado "papelera vacía" con su propio padding,
  sin la altura mínima que tenía el `Box.weight(1f)` de la versión a
  pantalla completa.

## 4. Nota técnica: por qué el alto se limita en el `LazyColumn`, no en un `Column` padre

Un detalle real de Compose, no solo de este archivo: una `LazyColumn` sin
modificador de alto explícito consume todo el alto máximo que le ofrezca
su padre, incluso con pocos elementos — es el comportamiento correcto
para una pantalla completa (Fase 8 original, `Modifier.weight(1f)` dentro
de un `Column.fillMaxSize()`), pero produce un panel siempre estirado al
máximo, con espacio vacío de sobra, si simplemente se le pone un
`heightIn(max = X)` al `Column` contenedor sin tocar la `LazyColumn` en
sí. La corrección real (no un parche) fue acotar el alto directamente en
el modificador de la propia `LazyColumn`
(`Modifier.heightIn(max = listMaxHeight)`), que sí respeta el contenido
real y solo scrollea/trunca cuando el contenido excede ese máximo — el
panel se ve compacto con 2 capas y con scroll interno con 20.

## 5. Cambios de visibilidad en `EditorScreen.kt`

El `Box` que envuelve el ícono de papelera es, desde esta fase, también
el ancla del panel (`Popup` necesita que su composable esté declarado
como hijo del mismo nodo de layout que quiere usar como referencia). Dos
ajustes derivados de eso, ninguno cosmético:

1. **Condición de visibilidad del `Box` ampliada**: de
   `trashedLayers.isNotEmpty()` a
   `trashedLayers.isNotEmpty() || showLayerTrashDialog`. Sin este cambio,
   vaciar la papelera con el panel abierto haría desaparecer el ícono
   ancla de golpe — y con él, el panel entero, cortando en seco el
   mensaje "la papelera está vacía" que el panel mismo tiene que mostrar
   en ese momento (comportamiento ya descrito y decidido en la Fase 8
   original, sección 8: "Eliminar seleccionadas"/"Vaciar papelera" NO
   cierran el panel).
2. **Badge numérico separado en su propia condición**:
   `if (state.trashedLayers.isNotEmpty()) { /* badge */ }`, ahora
   independiente de la visibilidad del ícono — para que, en el estado
   transitorio de "papelera recién vaciada, panel todavía abierto", el
   ícono se siga viendo pero sin un badge mostrando "0".

El call site de `LayerTrashPanel` se movió del `Box` exterior de toda la
pantalla (línea ~6142 en la Fase 8 original) a este `Box` interior,
como último hijo, después del `IconButton` y del badge condicional.

## 6. Renombrado: `LayerTrashDialog` → `LayerTrashPanel`

El nombre `LayerTrashDialog` describía correctamente un `Dialog`; ya no
describe lo que es (un `Popup` anclado). Se renombró en su única
declaración y su único call site — verificado con una búsqueda completa
en `app/src/main` de que no quedó ninguna referencia al nombre anterior
fuera de comentarios que hablan del *archivo* `LayerTrashScreen.kt` (esos
sí siguen vigentes, el archivo no cambió de nombre). El encabezado
interno del panel también se condensó a juego con el nuevo tamaño:
sin el ícono circular decorativo de 38dp (redundante — el panel ya
cuelga visualmente de su propio ícono real en el toolbar), título a
15sp en vez de 18sp, botones de "Eliminar"/"Restaurar" con texto más
corto y `contentPadding` más ajustado para las columnas más angostas del
panel.

## 7. Qué NO cambió

- Ninguna de las 5 operaciones de `ProjectStorage` (`trashInfo`,
  `restoreLayersFromTrash`, `deleteLayersFromTrashPermanently`,
  `emptyTrash`, `acknowledgeTrashBanner`) — cero cambios, esta fase es
  puramente de presentación.
- El modelo de datos (`DeletedLayerData`, `ProjectData.trashedLayers`,
  `trashBannerAcknowledged`) — sin cambios.
- `TrashRecoveryBanner` (el aviso flotante de "quedaste con 1 capa") —
  sin cambios de comportamiento; solo se corrigió, dentro de su propio
  comentario de diseño, la mención al nombre `LayerTrashDialog` para que
  siga apuntando al nombre real (`LayerTrashPanel`).
- `TrashLayerRow`, `EmptyTrashConfirmDialog`,
  `DeleteSelectedFromTrashConfirmDialog` — sin cambios funcionales; las
  dos confirmaciones siguen siendo `AlertDialog` centrados, a propósito
  (ver sección 1).

## Archivos de esta fase

| Archivo | Tipo de cambio |
|---|---|
| `ui/LayerTrashScreen.kt` | `LayerTrashDialog` (Dialog, pantalla completa) → `LayerTrashPanel` (Popup anclado); nueva clase `TrashPanelPositionProvider`; encabezado condensado; imports ajustados (se agregan `Popup`/`PopupPositionProvider`/`PopupProperties`/`LocalConfiguration`/`LocalDensity`/`IntOffset`/`IntRect`/`IntSize`/`LayoutDirection`/`kotlin.math.min`; se quitan `Dialog`/`DialogProperties`/`CircleShape`, sin uso tras el rediseño) |
| `ui/EditorScreen.kt` | Call site de `LayerTrashPanel` movido al `Box` ancla del ícono; condición de visibilidad del `Box` y del badge separadas; comentario en el lugar del call site anterior para trazabilidad |

## Criterios de aceptación

- [x] La papelera ya no ocupa la pantalla completa: aparece como panel
      flotante, centrado bajo su ícono.
- [x] El panel nunca queda cortado contra los bordes de la pantalla
      (recorte horizontal verificado por cálculo, mismo criterio que los
      otros 3 menús anclados del mismo toolbar).
- [x] Vaciar la papelera o restaurar/eliminar la última capa seleccionada
      con el panel abierto no lo cierra de golpe salvo que la propia
      acción lo indique (restaurar sí cierra, por diseño ya acordado en
      la Fase 8; vaciar/eliminar seleccionadas no).
- [x] Cero referencias sueltas al nombre `LayerTrashDialog` en código
      Kotlin tras el renombrado (verificado por búsqueda completa).
- [x] Cero imports sin usar en `LayerTrashScreen.kt` tras el cambio.
- [x] Balance de llaves y paréntesis verificado en ambos archivos
      tocados, por conteo automatizado contra la versión anterior al
      cambio (deltas simétricos, sin desbalance introducido).
- [ ] Sin verificación en dispositivo/emulador real todavía — ver
      "Estado real / pendiente".

## Estado real / pendiente

- **No se compiló el proyecto en esta sesión.** El entorno de esta
  sesión no tiene el SDK de Android ni acceso a red para resolver
  dependencias de Gradle — la verificación de esta fase fue por
  inspección de código (estructura, tipos, balance sintáctico, búsqueda
  de referencias rotas), no por build real. Corresponde al usuario, como
  ya está previsto en el flujo de trabajo acordado, compilar y probar en
  dispositivo/tablet real antes de dar por cerrada la fase.
- **Sin tests instrumentados nuevos.** No existían tests de UI para
  `LayerTrashScreen.kt` antes de esta fase tampoco — no es una regresión
  de cobertura, es el mismo estado de deuda técnica ya documentado en la
  Fase 8 original.
