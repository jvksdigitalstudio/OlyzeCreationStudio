package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.data.TrashedLayerInfo
import com.yeivikas.olyzecs.ui.theme.BrandBlueLight
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import kotlin.math.min

/**
 * Papelera de capas — decisión de producto (ver conversación de diseño):
 * eliminar una capa nunca destruye nada al instante. `EditorViewModel.
 * removeLayer` solo la saca del lienzo; el próximo autoguardado la mueve a
 * `ProjectData.trashedLayers` (ver el comentario grande en
 * `ProjectStorage.saveProject`), donde queda esperando — con su imagen,
 * keyframes y look intactos — hasta que el usuario decide, a propósito,
 * una de tres cosas: restaurarla, eliminarla para siempre, o vaciar la
 * papelera entera. SIN límite de retención automático: se acumula hasta
 * que el usuario mismo actúa, a cambio de que este panel siempre
 * muestre cuánto pesa, para que esa decisión sea informada.
 *
 * Este archivo es autocontenido a propósito (ni un import de
 * `EditorViewModel`): recibe datos + callbacks, igual que
 * `ProjectInfoPanel`/`ErrorLogScreen` — el acoplamiento con el ViewModel
 * vive únicamente en el call site (EditorScreen.kt).
 */

// ============================================================================
// Aviso proactivo (banner)
// ============================================================================

/**
 * Aviso NO bloqueante que avisa, una sola vez por episodio de apertura
 * del proyecto (ver `EditorUiState.showTrashRecoveryBanner` y el
 * comentario grande de FASE 8-R3 en `ProjectStorage.loadProject`), que
 * este proyecto tiene capas eliminadas esperando en la papelera. A
 * diferencia de un `AlertDialog`, el usuario puede simplemente seguir
 * editando sin tocarlo — nunca bloquea el lienzo.
 *
 * FASE 8-R3 (a pedido explícito, con capturas): antes solo se disparaba
 * si el proyecto había quedado con una sola capa viva — un umbral que ya
 * no tiene sentido ahora que el ÍCONO de la papelera (ver el `Box` en
 * EditorScreen.kt, condicionado a `EditorUiState.hasTrashedLayers` — ver
 * FASE 8-R5 para por qué ya no es un `trashedLayers.isNotEmpty()` directo)
 * aparece con cualquier cantidad de capas restantes. Ahora este banner
 * sigue el mismo criterio que el ícono, con una diferencia deliberada de
 * MOMENTO: el ícono refleja el estado en tiempo real mientras se edita
 * (instantáneo desde FASE 8-R5, sin esperar al autoguardado — se consulta
 * cuando el usuario quiere); este banner, en cambio, solo puede encenderse
 * al (re)abrir el proyecto — nunca en medio de una sesión activa, porque
 * `EditorUiState.showTrashRecoveryBanner` se escribe ÚNICAMENTE desde
 * `ProjectStorage.loadProject` (ver ese comentario) — así funciona como un
 * recordatorio puntual de "che, la última vez que estuviste acá quedó esto
 * pendiente de revisar", no como una interrupción por cada eliminación.
 *
 * AJUSTE DE DISEÑO (a pedido explícito, con capturas): la primera versión
 * era una franja de punta a punta usando el mismo gradiente gris/morado
 * que el resto de los encabezados de la app — visualmente se confundía
 * con "un header más" en vez de leerse como lo que es, un aviso puntual
 * de la papelera. Ahora es una pastilla FLOTANTE, del ancho de su propio
 * contenido (no `fillMaxWidth`), centrada horizontalmente sobre el
 * lienzo, con color de identidad propio: el mismo ámbar "AVISO" que ya
 * usa `ErrorLogScreen` para su badge de warning — se reutiliza a
 * propósito el mismo color con el mismo significado ("atención, hay algo
 * para revisar") ya establecido en otra parte de la app, en vez de
 * inventar un tono nuevo sin relación con nada.
 */
@Composable
fun TrashRecoveryBanner(
    itemCount: Int,
    onView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mismo ámbar que AppLogger.Level.WARN en ErrorLogScreen.kt — ver el
    // comentario de la función.
    val noticeAmber = Color(0xFFE0A83C)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = noticeAmber,
            shadowElevation = 6.dp,
            modifier = Modifier
                .wrapContentWidth()
                .clickable(onClick = onView)
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    tint = BrandPurpleDeep,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        append(
                            if (itemCount == 1) "Tenés 1 capa eliminada recientemente · "
                            else "Tenés $itemCount capas eliminadas recientemente · "
                        )
                        // "Ver papelera" es la parte accionable de la frase —
                        // subrayada y en el azul de acentos de la marca
                        // (BrandBlueLight, ya usado en el gradiente del
                        // encabezado de LayerTrashPanel) para que se lea
                        // como un link real dentro del banner, no como texto
                        // informativo más. A pedido explícito, con captura.
                        withStyle(
                            SpanStyle(
                                color = BrandBlueLight,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("Ver papelera")
                        }
                    },
                    color = BrandPurpleDeep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Descartar aviso",
                        tint = BrandPurpleDeep.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// Panel desplegable de la papelera (ancla: el ícono de papelera del toolbar)
// ============================================================================

/**
 * Posición del panel: centrado horizontalmente bajo el ícono de papelera
 * que lo abre, recortado contra los bordes de la pantalla para que nunca
 * quede cortado si ese ícono está cerca del borde derecho o izquierdo del
 * toolbar. A PROPÓSITO es el mismo criterio, con el mismo nombre de
 * parámetro y el mismo `gapPx` de 8px, que
 * `BelowAnchorCenteredPopupPositionProvider` — el proveedor que ya usan
 * `GridMenu` y los demás menús anclados a un ícono del toolbar de
 * `EditorScreen.kt`. No se reutiliza esa clase directamente porque es
 * `private` de ese archivo y este archivo es autocontenido a propósito
 * (ver el comentario grande al principio); se replica su mismo criterio acá
 * para que, dentro de la misma fila de íconos, la papelera se comporte
 * exactamente igual que sus vecinas — ningún usuario debería notar que
 * están implementadas en archivos distintos.
 */
private class TrashPanelPositionProvider(
    private val gapPx: Int,
    private val screenMarginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
        var x = anchorCenterX - popupContentSize.width / 2
        val maxX = (windowSize.width - popupContentSize.width - screenMarginPx).coerceAtLeast(screenMarginPx)
        x = x.coerceIn(screenMarginPx, maxX)

        // BUG REAL corregido acá (reporte: "al scrollear la lista de la
        // papelera hacia abajo, la app se cierra"): esta función clampeaba
        // el eje X contra los bordes de la pantalla (ver `x` arriba) pero
        // el eje Y NO tenía ningún clamp — `y = anchorBounds.bottom +
        // gapPx` se devolvía tal cual, sin verificar que el panel ENTERO
        // (con [popupContentSize.height], que puede llegar a
        // [PANEL_LIST_MAX_HEIGHT_DP] + encabezado + pie) entrara en la
        // ventana. Con el ícono ancla lo bastante abajo en el toolbar (o
        // en una pantalla de poco alto), el panel quedaba posicionado
        // parcialmente FUERA de los límites visibles reales — invadiendo,
        // en dispositivos con navegación por gestos, la franja inferior
        // que el sistema reserva para el gesto "ir a inicio"/"atrás". El
        // resultado real reportado por el usuario: el gesto de scroll
        // vertical del `LazyColumn`, al empezar (o terminar) dentro de esa
        // franja, se lo llevaba puesto la navegación del sistema en vez de
        // llegar al `LazyColumn` — percibido como "la app se cierra",
        // aunque la app nunca crashea de verdad, solo queda en segundo
        // plano por un gesto de sistema mal capturado.
        //
        // La corrección: el mismo criterio de "encajar siempre dentro de
        // la pantalla" que ya existía para X, aplicado también a Y, con
        // una estrategia de tres pasos — (1) preferir DEBAJO del ícono
        // (comportamiento normal, sin cambios visuales para el caso común
        // donde ya entraba); (2) si no entra completo debajo, probar
        // ARRIBA del ícono; (3) si tampoco entra completo arriba (panel
        // más alto que la pantalla disponible, caso extremo), clampear
        // duro contra los bordes reales de la ventana. El panel queda
        // SIEMPRE 100% contenido en pantalla — nunca a medias, nunca
        // invadiendo la zona de gestos del sistema.
        val below = anchorBounds.bottom + gapPx
        val fitsBelow = below + popupContentSize.height <= windowSize.height - screenMarginPx
        val above = anchorBounds.top - gapPx - popupContentSize.height
        val fitsAbove = above >= screenMarginPx

        var y = when {
            fitsBelow -> below
            fitsAbove -> above
            else -> below
        }
        val maxY = (windowSize.height - popupContentSize.height - screenMarginPx).coerceAtLeast(screenMarginPx)
        y = y.coerceIn(screenMarginPx, maxY)

        return IntOffset(x, y)
    }
}

/**
 * Panel de la papelera de capas: selección múltiple con "Seleccionar todo",
 * más las dos acciones acordadas — "Restaurar seleccionadas"/"Eliminar
 * seleccionadas" (dependen de la selección) y "Vaciar papelera" (acción de
 * un solo toque, independiente de qué esté tildado).
 *
 * AJUSTE DE DISEÑO (a pedido explícito, con capturas): la primera versión
 * era un `Dialog` a pantalla completa — tapaba todo el lienzo para revisar
 * unas pocas capas eliminadas, una desproporción entre la acción (un
 * vistazo rápido a la papelera) y su costo visual (perder de vista el
 * proyecto entero). Ahora es un panel FLOTANTE anclado al propio ícono de
 * papelera que lo abre — se implementa con `Popup` (no con `Dialog`) porque
 * `Popup` es el único de los dos que admite un `PopupPositionProvider`
 * personalizado ([TrashPanelPositionProvider]): `Dialog` siempre se centra
 * en la pantalla y no puede anclarse a un composable arbitrario. El
 * tamaño es fijo y calculado a propósito (ver [PANEL_WIDTH_DP] y el
 * cálculo de alto máximo más abajo) —
 * ancho suficiente para no truncar nombres de archivo ni el peso en MB,
 * alto acotado a una fracción de la pantalla para que jamás la tape por
 * completo, con la propia lista interna haciendo scroll cuando hay más
 * capas de las que entran. El "call site" (`EditorScreen.kt`) es
 * responsable de declarar este composable como HERMANO del `IconButton`
 * ancla dentro del mismo `Box`, condición necesaria para que
 * `anchorBounds` en [trashPanelPositionProvider] refleje la posición real
 * de ese ícono en pantalla.
 *
 * Ambas confirmaciones destructivas ([EmptyTrashConfirmDialog] y
 * [DeleteSelectedFromTrashConfirmDialog]) se disparan desde ACÁ, no en el
 * caller — así ningún futuro call site puede llamar a `onEmptyTrash`/
 * `onDeleteSelected` sin pasar antes por su propio aviso. Esas dos SÍ
 * siguen siendo `AlertDialog` centrados: son confirmaciones irreversibles,
 * quieren toda la atención del usuario, a diferencia del panel en sí.
 */
@Composable
fun LayerTrashPanel(
    items: List<TrashedLayerInfo>,
    totalSizeBytes: Long,
    onRestoreSelected: (Set<String>) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Un id que ya no está en la papelera (se restauró/eliminó desde otra
    // instancia de este mismo panel, o esta lista se refrescó por otro
    // motivo) nunca debe seguir "marcado" — evita que "Restaurar
    // seleccionadas" intente operar sobre algo que ya no existe.
    LaunchedEffect(items) {
        val validIds = items.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in validIds }.toSet()
    }

    val allSelected = items.isNotEmpty() && selectedIds.size == items.size

    val configuration = LocalConfiguration.current
    // Ancho: cómodo para nombre de archivo + fecha + peso sin elipsis
    // prematura, pero recortado en pantallas angostas (tablets chicas en
    // vertical, teléfonos plegados) para no tocar los bordes de la
    // ventana. Alto de lista: fracción de la pantalla, nunca la pantalla
    // completa — un panel que se abre "debajo de su ícono" pero igual
    // tapa todo el lienzo no resuelve el problema que motivó este cambio.
    val panelWidth = min(PANEL_WIDTH_DP, configuration.screenWidthDp - 2 * PANEL_SCREEN_MARGIN_DP).dp
    val listMaxHeight = min(
        (configuration.screenHeightDp * PANEL_LIST_HEIGHT_FRACTION).toInt(),
        PANEL_LIST_MAX_HEIGHT_DP
    ).dp

    // `remember` a propósito (antes se creaba una instancia NUEVA en cada
    // recomposición): [TrashPanelPositionProvider] es puro dato de
    // configuración (dos enteros) que no cambia mientras el panel sigue
    // abierto, así que no hay ninguna razón real para reconstruirlo en
    // cada recomposición — reconstruirlo de más no causaba, por sí solo,
    // el cierre reportado (la causa real es la falta de clamp en Y, ya
    // corregida arriba), pero sigue siendo una asignación evitable en un
    // composable que ya recompone seguido (cada toggle de selección);
    // `remember` es la forma correcta de expresar "esto es estable
    // mientras el panel exista", no una micro-optimización cosmética.
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        TrashPanelPositionProvider(
            gapPx = with(density) { PANEL_ANCHOR_GAP_DP.dp.roundToPx() },
            screenMarginPx = with(density) { PANEL_SCREEN_MARGIN_DP.dp.roundToPx() }
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.width(panelWidth),
            shape = RoundedCornerShape(18.dp),
            color = BrandPurpleDeep,
            shadowElevation = 16.dp,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // --- Encabezado (condensado: sin ícono decorativo grande,
                // este panel ya cuelga visualmente del ícono real en el
                // toolbar, repetirlo adentro sería redundante) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(SurfaceTintedElevated, SurfaceTintedDark)))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Papelera de capas", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            Text(
                                if (items.isEmpty()) "Vacía"
                                else "${items.size} ${if (items.size == 1) "capa" else "capas"} · ${formatFileSize(totalSizeBytes)}",
                                fontSize = 11.sp,
                                color = Color(0xFFBFB3E0)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    selectedIds = if (allSelected) emptySet() else items.map { it.id }.toSet()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (allSelected) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
                                    ),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Seleccionar todo", color = Color(0xFFD6CFEF), fontSize = 12.sp)
                            }
                            // Acción independiente de la selección — a
                            // propósito bien separada visualmente (color de
                            // peligro, sin depender de tildar nada) para que
                            // nunca se confunda con "Eliminar seleccionadas".
                            TextButton(onClick = { showEmptyConfirm = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Vaciar papelera", color = Color(0xFFE05C5C), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // --- Lista (acotada en alto — nunca "fillMaxSize": scrollea
                // internamente en vez de forzar al panel a crecer más allá
                // de [listMaxHeight]) ---
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            tint = Color(0xFF8A7DB8),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("La papelera está vacía", color = Color(0xFFD6CFEF), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "Las capas que elimines del lienzo van a aparecer acá, listas para recuperar.",
                            color = Color(0xFF8A7DB8),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            TrashLayerRow(
                                item = item,
                                selected = item.id in selectedIds,
                                onToggle = {
                                    selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                                }
                            )
                        }
                    }

                    // --- Pie: acciones sobre la selección ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceTintedElevated)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE05C5C))
                        ) {
                            Text(
                                if (selectedIds.isEmpty()) "Eliminar" else "Eliminar (${selectedIds.size})",
                                maxLines = 1,
                                fontSize = 12.sp,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                // A PEDIDO EXPLÍCITO DEL USUARIO (con capturas):
                                // restaurar es "quiero volver a trabajar con
                                // esto YA" — el destino natural es el lienzo,
                                // no quedarse mirando la lista de la papelera
                                // hasta que el usuario mismo cierre. Se cierra
                                // el panel acá mismo para que la capa
                                // restaurada se vea en tiempo real apenas se
                                // toca el botón — mismo criterio que usan
                                // Google Drive/Google Fotos al restaurar de
                                // su papelera. A propósito NO se aplica el
                                // mismo cierre automático a "Eliminar
                                // seleccionadas" ni a "Vaciar papelera": esas
                                // son acciones de limpieza donde tiene
                                // sentido seguir revisando el resto de la
                                // papelera después.
                                onRestoreSelected(selectedIds)
                                selectedIds = emptySet()
                                onDismiss()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurpleLight, contentColor = Color.White)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_restore_all),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (selectedIds.isEmpty()) "Restaurar" else "Restaurar (${selectedIds.size})",
                                maxLines = 1,
                                fontSize = 12.sp,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        EmptyTrashConfirmDialog(
            itemCount = items.size,
            onConfirm = {
                showEmptyConfirm = false
                onEmptyTrash()
            },
            onDismiss = { showEmptyConfirm = false }
        )
    }
    if (showDeleteConfirm) {
        DeleteSelectedFromTrashConfirmDialog(
            count = selectedIds.size,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteSelected(selectedIds)
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

// Constantes de layout del panel — centralizadas acá (no "números mágicos"
// sueltos en medio del composable) para que ajustar el tamaño en el futuro
// sea un cambio de una sola línea por constante, no una cacería por todo el
// archivo.
private const val PANEL_WIDTH_DP = 320
private const val PANEL_SCREEN_MARGIN_DP = 12
private const val PANEL_ANCHOR_GAP_DP = 8
private const val PANEL_LIST_HEIGHT_FRACTION = 0.42
private const val PANEL_LIST_MAX_HEIGHT_DP = 340

@Composable
private fun TrashLayerRow(item: TrashedLayerInfo, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrandPurpleLight.copy(alpha = 0.16f) else SurfaceTintedElevated)
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                id = if (selected) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
            ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceTintedDark)
        ) {
            if (item.imageFile != null) {
                AsyncImage(
                    model = item.imageFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = Color(0xFF8A7DB8),
                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Eliminada el ${formatFullDate(item.deletedAtMs)} · ${formatFileSize(item.sizeBytes)}",
                color = Color(0xFF8A7DB8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// Confirmaciones (misma familia visual que el resto de diálogos destructivos
// de la app — ver, por ejemplo, el diálogo "¿Eliminar esta capa?" en
// EditorScreen.kt y "¿Limpiar el registro?" en ErrorLogScreen.kt).
// ============================================================================

/**
 * Confirmación de "Vaciar papelera" — frase acordada explícitamente con el
 * cliente: deja clarísimo que es irreversible y qué se pierde exactamente
 * (imágenes, keyframes, ajustes), no solo "¿estás seguro?" genérico.
 */
@Composable
private fun EmptyTrashConfirmDialog(itemCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text("¿Vaciar la papelera?", color = Color.White) },
        text = {
            Text(
                "Esta acción es irreversible. Las $itemCount ${if (itemCount == 1) "capa" else "capas"} de la papelera, " +
                    "junto con sus imágenes, keyframes y ajustes, se eliminarán de forma permanente y no podrán recuperarse.",
                color = Color(0xFFD6CFEF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Vaciar papelera", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) }
        }
    )
}

/** Confirmación de "Eliminar seleccionadas" — mismo criterio que [EmptyTrashConfirmDialog], acotado a la cantidad tildada. */
@Composable
private fun DeleteSelectedFromTrashConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text(if (count == 1) "¿Eliminar 1 capa?" else "¿Eliminar $count capas?", color = Color.White) },
        text = {
            Text(
                "Esta acción es irreversible. " +
                    (if (count == 1) "La capa seleccionada" else "Las $count capas seleccionadas") +
                    ", junto con sus imágenes, keyframes y ajustes, se eliminarán de forma permanente y no podrán recuperarse.",
                color = Color(0xFFD6CFEF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) }
        }
    )
}
