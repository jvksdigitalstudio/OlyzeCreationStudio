package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.audio.label
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.viewmodel.EditorViewModel
import com.yeivikas.olyzecs.data.ColorExtraction
import com.yeivikas.olyzecs.engine.mesh3d.Extrude3D
import com.yeivikas.olyzecs.ui.effects.EffectSourceLoader
import com.yeivikas.olyzecs.ui.effects.EffectSources
import com.yeivikas.olyzecs.ui.effects.EffectsPreviewSizing
import com.yeivikas.olyzecs.ui.effects.RenderTier
import com.yeivikas.olyzecs.ui.effects.effectsDisplayDecodeLimit
import com.yeivikas.olyzecs.ui.effects.logEffectDecodeFailure
import com.yeivikas.olyzecs.ui.effects.rememberProgressiveRenderScheduler
import com.yeivikas.olyzecs.ui.editview.EditViewFit
import com.yeivikas.olyzecs.data.ImageDecoding
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
//  ColorAnd3DEffectWindows.kt — Ventanas de efectos: Recolor, Color básico y Básico 3D
// ============================================================================
//  ORIGEN: movido TAL CUAL desde `EditorScreen.kt` (Fase 3 del plan de ingeniería; ADR-022). Sin cambios de lógica.
//  Visibilidad: Basico3DFloatingWindow, COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE, ColorBasicoFloatingWindow,
//      ColorMenuBasicoIcon, ColorMenuRecolorIcon, EffectsCategoryColor, EffectsControlsState, Extrude3DGestureBridge,
//      Menu3DBasicCubeIcon, RecolorFloatingWindow, colorDistanceSquared, rememberEffectsControlsState pasan de `private` a
//      `internal` (se usan entre archivos del paquete).
//  El paquete SIGUE siendo `com.yeivikas.olyzecs.ui` (la carpeta `editor/` es solo organización).
// ============================================================================

/**
 * Ventana flotante de "Recolor": paleta de colores extraídos + Brillo/
 * Saturación/Opacidad + la rueda de color. A PEDIDO DEL USUARIO: ya NO
 * es un panel angosto pegado al borde izquierdo de la pantalla — ahora
 * es una ventana flotante DENTRO del canvas (ver [FloatingToolWindow]),
 * que se puede arrastrar desde su cabecera y redimensionar desde su
 * esquina inferior derecha, igual que un plugin de audio flotante.
 *
 * Misma lógica de siempre por debajo — nada de esto cambió, solo dónde
 * se dibuja: [liveBitmap] (chica, ~220px de lado) para que la extracción
 * de paleta sea instantánea y cada frame de arrastre de la rueda se
 * recoloree y suba a GL sin lag notable; [fullBitmap] (hasta 1024px) que
 * se decodifica aparte, en paralelo, y es la que de verdad se recolorea
 * y persiste a disco (ver EditorViewModel.commitLayerRecolor) 500ms
 * después del último cambio.
 */
private val RECOLOR_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(240.dp, 460.dp)

@Composable
internal fun RecolorFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    initialOffset: Offset,
    onClose: () -> Unit,
    // Ver el comentario grande sobre `onInteracted` en [FloatingToolWindow]
    // y sobre `floatingWindowZOrderCounter` donde se instancian las tres
    // ventanas — esto solo se reenvía tal cual, sin lógica propia acá.
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Ver el comentario grande sobre `floatingWindowAreaSizePx` donde se
    // instancian las tres ventanas, y su uso dentro de [FloatingToolWindow]
    // — esto solo se reenvía tal cual, sin lógica propia acá.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    val sources = remember(layer.id, layer.sourceUri) { EffectSources<Bitmap>() }
    var palette by remember(layer.id) { mutableStateOf<List<Int>>(emptyList()) }
    var selectedOriginal by remember(layer.id) { mutableStateOf<Int?>(null) }
    val remaps = remember(layer.id) { mutableStateMapOf<Int, Int>() }
    var wheelHue by remember(layer.id) { mutableStateOf(0f) }
    var wheelSat by remember(layer.id) { mutableStateOf(0f) }
    var wheelVal by remember(layer.id) { mutableStateOf(1f) }
    // "Opacidad" del panel: cuánto pisa el color recoloreado al color
    // original de la imagen (1 = recolor a pleno, 0 = imagen intacta) —
    // ver el parámetro `intensity` de ColorExtraction.recolor. Empieza en
    // 1 (comportamiento de siempre) para no sorprender con un resultado
    // "apagado" apenas se entra al panel.
    var recolorOpacity by remember(layer.id) { mutableStateOf(1f) }

    // A PEDIDO EXPLÍCITO DEL USUARIO — soporte de "Eliminar efecto"
    // (arrastrar el ícono ya minimizado a la zona de "Eliminar", ver
    // `onDeleteEffect` en [FloatingToolWindow]): el `Uri` (NO el
    // contenido) que la capa tenía ANTES de que esta ventana la
    // tocara, capturado UNA sola vez al entrar — `remember(layer.id)`
    // ejecuta este lambda una única vez, en la primerísima composición
    // de esta ventana para esta capa, así que congela el `sourceUri`
    // que había EN ESE MOMENTO, antes de cualquier commit de esta
    // sesión.
    //
    // BUG REAL corregido (reportado con captura: el lienzo quedó
    // COMPLETAMENTE VACÍO después de "Eliminar"): la primera versión de
    // esto volvía a DECODIFICAR de forma asíncrona (en un hilo de IO
    // aparte) el `sourceUri` que la capa tenía al abrir la ventana,
    // para guardar el Bitmap resultante y commitearlo de vuelta al
    // eliminar. Esa decodificación no tenía ninguna garantía de
    // terminar ANTES de que otro commit de esta misma capa (de esta
    // ventana o de OTRA abierta en paralelo sobre la misma capa)
    // sobreescribiera el archivo que estaba leyendo a mitad de
    // lectura — una carrera real de archivo. En la práctica, terminó
    // decodificando un archivo corrupto/a medio escribir y commiteando
    // ESO de vuelta, que es justamente lo que borró la imagen entera.
    //
    // La corrección: no decodificar NADA de nuevo — guardar solo el
    // `Uri` (un string, sin IO) y, al eliminar, pisar `sourceUri` de
    // vuelta a ese mismo Uri exacto vía `EditorViewModel.revertLayerToUri`
    // (ver su comentario grande, en el ViewModel, para la explicación
    // completa). Sin decodificación de por medio no hay ninguna carrera
    // posible.
    val originalSourceUriBeforeSession = remember(layer.id) { layer.sourceUri }

    // BUG REAL corregido acá, de raíz — los dos intentos anteriores
    // (animar el "salto" de blanco a color real, primero solo en la
    // apertura, después en cada re-selección) seguían mostrando el
    // valor FALSO en pantalla, aunque sea por una fracción de segundo o
    // suavizado — y ESO es lo que se percibe como bug, no la velocidad
    // de la transición. La única forma de que nunca se vea un valor
    // incorrecto es no dibujarlo nunca: los controles (sliders + rueda)
    // quedan invisibles (alpha 0, mismo alto reservado, sin saltos de
    // layout) hasta que `selectSwatch` los cargó con el color REAL por
    // primera vez para esta capa — recién ahí aparecen, ya con el valor
    // correcto puesto, y con un fundido corto para que no sea un pop
    // brusco de "nada" a "controles". Ninguna versión con datos falsos
    // llega a pintarse en pantalla, ni un frame.
    var hasRealPaletteData by remember(layer.id) { mutableStateOf(false) }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (hasRealPaletteData) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "recolorControlsAlpha"
    )

    fun selectSwatch(originalColor: Int) {
        selectedOriginal = originalColor
        val effective = remaps[originalColor] ?: originalColor
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(effective, hsv)
        wheelHue = hsv[0]
        wheelSat = hsv[1]
        wheelVal = hsv[2]
        hasRealPaletteData = true
    }

    LaunchedEffect(layer.id, layer.sourceUri) {
        // BUG REAL corregido acá: `remaps` vive en remember(layer.id) — NO
        // se reinicia cuando `layer.sourceUri` cambia. Y sí cambia: apenas
        // pasan los 500ms de debounce, commitLayerRecolor guarda el
        // resultado en un ARCHIVO NUEVO y actualiza sourceUri a esa nueva
        // ruta (ver comentario de commitLayerRecolor en EditorViewModel).
        // Ese archivo nuevo YA tiene el cambio anterior horneado en los
        // píxeles — pero la entrada vieja de `remaps` (color original de
        // ANTES → el color que se eligió) seguía viva en memoria. Si el
        // usuario volvía a tocar la rueda para elegir OTRO color, el mapa
        // de recolor terminaba con DOS entradas compitiendo por los mismos
        // píxeles: la vieja (que en muchos casos seguía siendo "válida"
        // porque su color de origen todavía estaba cerca del color actual)
        // y la nueva recién elegida — y la mezcla ponderada de recolor()
        // terminaba tirando el resultado de vuelta hacia el color viejo,
        // por más que se arrastrara la rueda a un tono distinto. Con una
        // imagen de un solo color plano esto era especialmente notorio:
        // la entrada vieja competía cabeza a cabeza contra la nueva en
        // CASI todos los píxeles a la vez, así que el cambio nuevo casi no
        // se notaba — "se queda pegado en el mismo verde".
        //
        // Ahora, cada vez que se carga una imagen nueva desde disco (osea,
        // cada vez que el commit anterior YA quedó guardado), se limpia
        // `remaps` entero: el color base que se acaba de cargar YA ES el
        // resultado final del cambio anterior, así que cualquier delta
        // viejo relativo a un color-de-origen que ya no existe en el
        // archivo debe descartarse, no acumularse para siempre.
        // BUG REAL #2 corregido acá: antes de limpiar el estado, guardamos
        // qué color se veía en pantalla para el swatch que el usuario
        // tenía elegido (su remap si lo tenía, si no el original). Este
        // LaunchedEffect no corre solo una vez al entrar al panel: corre
        // CADA VEZ que cambia `layer.sourceUri`, y sourceUri cambia solo
        // 500ms después de CADA pausa al arrastrar un slider o la rueda
        // (ver commitLayerRecolor). O sea que este bloque se re-ejecutaba
        // en medio de una sesión de edición normal, no solo al abrir el
        // panel.
        val previousEffectiveColor = selectedOriginal?.let { remaps[it] ?: it }

        remaps.clear()
        selectedOriginal = null

        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = 220)
        }
        liveBitmap = small
        sources.publishQuick(small)
        val extracted = small?.let { ColorExtraction.extractPalette(it) } ?: emptyList()
        palette = extracted
        // BUG REAL #2, la parte que de verdad se sentía como "se
        // restablece solo": acá SIEMPRE se auto-seleccionaba
        // `extracted.firstOrNull()`, sin importar qué color tenía
        // elegido el usuario. Como este efecto se repite en cada pausa
        // de edición (no solo al abrir el panel), si el usuario estaba
        // ajustando el 2do o 3er color de la paleta, la selección saltaba
        // de vuelta al primero cada medio segundo — el recolor SÍ se
        // aplicaba y se guardaba bien, pero el panel visualmente
        // "olvidaba" en qué color estabas parado, así que mover un
        // slider parecía no hacer nada o quedar limitado.
        //
        // Ahora, si había un color seleccionado antes, se busca en la
        // paleta nueva el más parecido (por distancia RGB) a como se
        // veía ese color en pantalla, y se re-selecciona ESE — la
        // selección "sigue" al mismo color a través de los recargues
        // automáticos. Solo si no había nada elegido todavía (primera
        // vez que se abre el panel para esta capa) se cae al
        // comportamiento original de elegir el más presente.
        val toReselect = previousEffectiveColor
            ?.let { target -> extracted.minByOrNull { candidate -> colorDistanceSquared(candidate, target) } }
            ?: extracted.firstOrNull()
        if (toReselect != null) {
            selectSwatch(toReselect)
        } else {
            // BUG REAL corregido acá: si `extracted` viene vacía (imagen
            // 100% transparente, o falló la decodificación — ambos casos
            // ya contemplados arriba con su propio manejo), `toReselect`
            // es null y `selectSwatch` nunca se llamaba. Como
            // `hasRealPaletteData` solo se ponía en `true` DENTRO de
            // `selectSwatch`, los sliders y la rueda (atados a
            // `controlsAlpha`) se quedaban en alpha 0 para siempre: ni un
            // control visible, ni ningún mensaje de qué pasó. No hay
            // color real que cargar, pero el panel igual tiene que
            // mostrarse — así que no queda ningún color seleccionado
            // (la fila de paleta cae en su estado "Sin colores") y los
            // sliders/rueda se muestran con su valor default (blanco a
            // pleno brillo), igual que se ven un instante antes de que
            // `selectSwatch` los actualice en el caso normal.
            selectedOriginal = null
            hasRealPaletteData = true
        }
        EffectSourceLoader.loadRemaining(
            sources = sources,
            displayDecodeLimit = { context.effectsDisplayDecodeLimit(EditViewFit.FILL_FRACTION) },
            onDecodeFailure = ::logEffectDecodeFailure,
            decode = { maxDimension ->
                withContext(Dispatchers.IO) {
                    ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = maxDimension)
                }
            }
        )
    }

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ ->
            val remapsSnapshot = remaps.toMap()
            val opacitySnapshot = recolorOpacity
            withContext(Dispatchers.Default) { ColorExtraction.recolor(source, remapsSnapshot, intensity = opacitySnapshot) }
        },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "recolor") }
    )

    // Compartida por la rueda Y por los sliders de Brillo/Saturación: sea
    // cual sea el control que se mueva, todos terminan en el mismo lugar
    // — arman el color HSV completo, lo guardan como remap del color
    // seleccionado, y disparan la vista previa en vivo + el guardado con
    // debounce. Sin esto, cada control tendría que repetir esa misma
    // secuencia de 3 pasos por separado.
    fun applyCurrentWheelColor() {
        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(wheelHue, wheelSat, wheelVal))
        selectedOriginal?.let { remaps[it] = newColor }
        scheduler.onParametersChanged()
    }

    // --- Ventana flotante, arrastrable y redimensionable ---
    // A PEDIDO DEL USUARIO: reemplaza el panel angosto y vertical pegado
    // al borde izquierdo por [FloatingToolWindow] — cabecera arrastrable
    // con el título "Recolor" (así que no hace falta repetirlo acá
    // adentro) + botón de cerrar, y una manija de redimensionar en la
    // esquina inferior derecha. El contenido mantiene scroll vertical
    // propio (heredado de FloatingToolWindow), así que con la ventana
    // achicada al mínimo (o con muchos colores en la paleta) nada queda
    // cortado — simplemente se puede deslizar en vez de que algo
    // desaparezca.
    FloatingToolWindow(
        title = "Recolor",
        // Mismo glifo (gota de pintura) que ya usa "Recolor" en
        // [EditImageColorMenu] — ver [ColorMenuRecolorIcon].
        titleIcon = { tint -> ColorMenuRecolorIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = RECOLOR_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // A PEDIDO EXPLÍCITO DEL USUARIO: soltar el ícono minimizado
        // sobre la zona de "Eliminar" (ver [FloatingWindowDeleteDropZone])
        // borra el efecto entero, no solo cierra la ventana — vuelve la
        // capa a `originalSourceUriBeforeSession` (el Uri que tenía
        // ANTES de que esta ventana tocara nada, ver su comentario
        // grande de más arriba para el porqué de usar el Uri y no un
        // Bitmap decodificado) y RECIÉN AHÍ cierra, con `onClose()` tal
        // cual. `scheduler.cancelPending()` primero: si había un commit con
        // debounce en camino (de un cambio de hace menos de 500ms),
        // tiene que descartarse — si llegara a correr DESPUÉS de este
        // reset, pisaría el original de vuelta con el remap viejo.
        onDeleteEffect = {
            scheduler.cancelPending()
            viewModel.revertLayerToUri(layer.id, originalSourceUriBeforeSession)
            onClose()
        },
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
            // BUG REAL corregido acá — el mismo diagnóstico que ya se
            // había aplicado en [ColorBasicoFloatingWindow]/
            // [Basico3DFloatingWindow]/[EffectsPanel]/[DistortionPanel]
            // (ver el comentario grande justo después de
            // [FloatingToolWindow], más arriba), pero que en la primera
            // pasada por Recolor quedó aplicado A MEDIAS: acá SEGUÍA
            // habiendo un `CircularProgressIndicator` girando mientras
            // `isLoadingPalette` era `true`, que se reemplazaba de golpe
            // por la fila de colores apenas terminaba de cargar — la
            // MISMA clase de salto que ya se había eliminado en las otras
            // tres ventanas, nada más que acá sobrevivió escondido en la
            // paleta en vez de en los sliders. Encima, la primera
            // corrección le sumó un estado "atenuado/deshabilitado" a los
            // sliders y la rueda mientras tanto — dos señales de "cargando"
            // a la vez (el spinner arriba Y los controles grises abajo),
            // peor que antes.
            //
            // La corrección real, ahora sí completa: nada de spinner, nada
            // de atenuado. La fila de paleta reserva su alto fijo desde el
            // primer frame (mismo alto tenga 0 o 10 colores, ver `height`
            // acá abajo) y los sliders + la rueda están SIEMPRE visibles,
            // SIEMPRE habilitados, exactamente igual que en Color Básico
            // — la única diferencia real es que sus valores arrancan en el
            // default (blanco a pleno brillo) hasta que la paleta llega
            // (típicamente un instante después) y `selectSwatch` los
            // actualiza solo. Sin `if` que decida si el bloque grande
            // existe: existe siempre, con el mismo alto siempre.
            Text(
                "Color",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
                if (palette.isEmpty()) {
                    Text(
                        "Sin colores",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { originalColor ->
                            val effectiveColor = remaps[originalColor] ?: originalColor
                            val isSelected = selectedOriginal == originalColor
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(effectiveColor))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectSwatch(originalColor) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(4.dp))

            // BUG REAL corregido acá: todo este bloque (sliders + rueda)
            // se dibuja con `alpha = controlsAlpha`, que arranca en 0 y
            // recién sube a 1 cuando `selectSwatch` ya cargó el color
            // REAL por primera vez para esta capa (ver `hasRealPaletteData`
            // más arriba). El alto se sigue reservando igual esté
            // visible o no — solo cambia la opacidad, nunca el layout —
            // así que no hay salto de tamaño de ventana, y tampoco hay
            // ningún frame donde se vea Brillo 100%/Saturación 0% (el
            // valor de mentira): esos valores existen en memoria un
            // instante, pero nunca llegan a pintarse en pantalla.
            Column(modifier = Modifier.graphicsLayer(alpha = controlsAlpha)) {
            // Brillo y Saturación son la MISMA información que ya
            // controla la posición del dedo en la rueda (radio =
            // saturación, y el brillo pinta el anillo) — tenerlos
            // también como sliders no es redundante en un panel
            // profesional: permite un ajuste fino de precisión
            // (0.01 en vez de depender del pulso del dedo en una
            // pantalla chica) sin perder la rueda como forma rápida
            // de elegir el matiz. Los tres controles escriben al
            // mismo estado (wheelHue/wheelSat/wheelVal), así que
            // mover un slider también mueve el punto de la rueda, y
            // viceversa — quedan siempre sincronizados.
            LabeledSlider(
                label = "Brillo",
                value = wheelVal,
                range = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                // `displayScale` acá, escribir a mano "83" en un control interno
                // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                // `displayScale = 100f` hace que el campo de edición manual (y su
                // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                // y tipea (0-100, o -100..100), no en la escala cruda interna.
                displayScale = 100f
            ) { v ->
                wheelVal = v
                applyCurrentWheelColor()
            }
            LabeledSlider(
                label = "Saturación",
                value = wheelSat,
                range = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                // `displayScale` acá, escribir a mano "83" en un control interno
                // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                // `displayScale = 100f` hace que el campo de edición manual (y su
                // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                // y tipea (0-100, o -100..100), no en la escala cruda interna.
                displayScale = 100f
            ) { s ->
                wheelSat = s
                applyCurrentWheelColor()
            }
            // "Opacidad" en vez de un slider de "suavizado": es el
            // control que de verdad tiene sentido para el usuario acá
            // — cuánto pisa el color nuevo al original — en vez de
            // exponer un parámetro técnico interno (radio de mezcla)
            // que no se entiende sin leer el código. Ver el
            // parámetro `intensity` de ColorExtraction.recolor.
            LabeledSlider(
                label = "Opacidad",
                value = recolorOpacity,
                range = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                // `displayScale` acá, escribir a mano "83" en un control interno
                // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                // `displayScale = 100f` hace que el campo de edición manual (y su
                // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                // y tipea (0-100, o -100..100), no en la escala cruda interna.
                displayScale = 100f
            ) { o ->
                recolorOpacity = o
                scheduler.onParametersChanged()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rueda de color, centrada — su ancho fijo (168dp) entra
            // cómodo incluso con la ventana flotante en su tamaño
            // mínimo (ver `minSize` en FloatingToolWindow).
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ColorWheelPicker(
                    hue = wheelHue,
                    saturation = wheelSat,
                    brightness = wheelVal,
                    onColorChange = { h, s ->
                                wheelHue = h
                        wheelSat = s
                        applyCurrentWheelColor()
                    },
                    modifier = Modifier.size(168.dp)
                )
            }
            } // fin de la Column con controlsAlpha

            Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Ventana flotante de "Básico" (pestaña "Color", ARRIBA de "Recolor" en
 * [EditImageColorMenu]): los 5 ajustes de corrección de color global
 * del sujeto — Nitidez, Saturación, Brillo, Contraste, Tono — que antes
 * vivían adentro de "Efecto", como su propia categoría "Color" (ver
 * [EffectsCategoryColor], reutilizada tal cual acá abajo, sin tocar un
 * solo slider ni valor por defecto). A PEDIDO DEL USUARIO: esa
 * categoría se saca de "Efecto" y se muda a la pestaña "Color", porque
 * es un ajuste de COLOR, no un efecto — mismo criterio, mismo trato
 * visual y mismo mecanismo que [RecolorFloatingWindow]/
 * [Basico3DFloatingWindow]: ventana flotante DENTRO del canvas (ver
 * [FloatingToolWindow]), arrastrable desde su cabecera y redimensionable
 * desde su esquina inferior derecha.
 *
 * Importante — por qué NO se reinventa el guardado acá: "Color"
 * (sharpen/saturation/brightness/contrast/hue) es apenas una porción de
 * [com.yeivikas.olyzecs.engine.effects.ImageEffectsParams], un objeto
 * ÚNICO que agrupa TODOS los efectos de la capa (Fondo, Contorno,
 * Resplandor, Sombra, Reflejo, Presets, Distorsión incluidos) — el
 * mismo que ya arma/persiste [EffectsPanel] para la pestaña "Efecto".
 * Estos efectos NO quedan guardados como números editables en la capa:
 * cada commit los "hornea" directo sobre los píxeles y reemplaza
 * `layer.sourceUri` (igual que "Recolor"/"3D"). Por eso, en cada
 * apertura de ESTA ventana (igual que en cada apertura de "Efecto"), el
 * resto de las ~60 propiedades de `ImageEffectsParams` arranca en su
 * valor NEUTRO (los mismos valores por defecto que ya trae la propia
 * clase — 0 de intensidad, 1 de escala — ver su declaración): eso
 * significa "no agregar NADA nuevo de esas otras categorías en esta
 * sesión", no "borrar lo que ya estaba horneado en la imagen" — los
 * efectos ya aplicados en commits anteriores siguen ahí, en los
 * píxeles. Es EXACTAMENTE lo mismo que ya pasaba cuando "Color" vivía
 * adentro de "Efecto": ni un bit de este comportamiento cambia, solo
 * dónde se abre la ventana.
 */
internal val COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(260.dp, 420.dp)

@Composable
internal fun ColorBasicoFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    initialOffset: Offset,
    onClose: () -> Unit,
    // Ver el comentario grande sobre `onInteracted` en [FloatingToolWindow]
    // y sobre `floatingWindowZOrderCounter` donde se instancian las tres
    // ventanas — esto solo se reenvía tal cual, sin lógica propia acá.
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Ver el comentario grande sobre `floatingWindowAreaSizePx` donde se
    // instancian las tres ventanas, y su uso dentro de [FloatingToolWindow]
    // — esto solo se reenvía tal cual, sin lógica propia acá.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    val sources = remember(layer.id, layer.sourceUri) { EffectSources<Bitmap>() }

    // Mismo `EffectsControlsState`/`EffectsCategoryColor` que usa
    // [EffectsPanel] para esta misma categoría — cada `remember` con la
    // misma key crea su propio objeto por sitio de llamada, así que
    // nunca comparten la instancia en memoria.
    val ctrl = rememberEffectsControlsState(layer.id)


    // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
    // [RecolorFloatingWindow] (ver su comentario grande sobre
    // `originalSourceUriBeforeSession`, junto a su declaración, para el
    // porqué completo de guardar el Uri y no un Bitmap): el Uri que
    // tenía la capa ANTES de que esta ventana la tocara, capturado UNA
    // sola vez al entrar, sin IO, sin corrutina — nada que pueda entrar
    // en carrera con los commits de esta u otra ventana editando la
    // misma capa.
    val originalSourceUriBeforeSession = remember(layer.id) { layer.sourceUri }

    // A PEDIDO DEL USUARIO — MULTI-VENTANA: esta ventana puede convivir
    // en pantalla con "Recolor" (y con "3D"/"Efecto") editando la MISMA
    // capa al mismo tiempo, cada una escribiendo su resultado en
    // `layer.sourceUri` con debounce (ver commitLayerRecolor). Si esta
    // ventana decodificara su base UNA sola vez al entrar y se quedara
    // con esa copia fija toda la sesión, un commit hecho por OTRA
    // ventana mientras ésta sigue abierta quedaría pisado por el
    // próximo commit de ÉSTA (que partiría de una base vieja, sin el
    // cambio ajeno) — pérdida silenciosa de trabajo. Por eso, igual que
    // [RecolorFloatingWindow], la key incluye `layer.sourceUri`: cada vez
    // que CUALQUIER ventana (incluida esta) actualiza el archivo de la
    // capa, esta ventana vuelve a decodificar la base fresca y sigue
    // aplicando sus propios sliders (que no se resetean — viven en
    // `remember(layer.id)`, sin `sourceUri`) sobre el resultado más
    // reciente, en vez de sobre uno desactualizado.
    LaunchedEffect(layer.id, layer.sourceUri) {
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = EffectsPreviewSizing.QUICK_MAX_DIMENSION)
        }
        liveBitmap = small
        sources.publishQuick(small)
        EffectSourceLoader.loadRemaining(
            sources = sources,
            displayDecodeLimit = { context.effectsDisplayDecodeLimit(EditViewFit.FILL_FRACTION) },
            onDecodeFailure = ::logEffectDecodeFailure,
            decode = { maxDimension ->
                withContext(Dispatchers.IO) {
                    ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = maxDimension)
                }
            }
        )
    }

    // Ídem `currentParams()` de [EffectsPanel], pero solo con los 5
    // campos que esta ventana de verdad expone — el resto queda en su
    // valor por defecto (ver el comentario grande de más arriba: eso es
    // "no tocar esas otras categorías", no "borrarlas").
    fun currentParams() = com.yeivikas.olyzecs.engine.effects.ImageEffectsParams(
        sharpen = ctrl.sharpen,
        saturation = ctrl.saturation,
        brightness = ctrl.brightness,
        contrast = ctrl.contrast,
        hue = ctrl.hue
    )

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ -> viewModel.applyImageEffects(source, currentParams()) },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "colorBasico") }
    )

    // --- Ventana flotante, arrastrable y redimensionable ---
    // Mismo patrón exacto que [RecolorFloatingWindow]/
    // [Basico3DFloatingWindow] — cabecera arrastrable con el título
    // "Color Básico" + botón de cerrar, y una manija de redimensionar en
    // la esquina inferior derecha.
    FloatingToolWindow(
        title = "Color Básico",
        // Mismo glifo (tres sliders) que ya usa "Básico" en
        // [EditImageColorMenu] — ver [ColorMenuBasicoIcon].
        titleIcon = { tint -> ColorMenuBasicoIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
        // [RecolorFloatingWindow]: soltar el ícono minimizado sobre la
        // zona de "Eliminar" vuelve la capa a `originalSourceUriBeforeSession`
        // (el Uri que tenía ANTES de que esta ventana tocara nada, sin
        // importar cuántos commits hizo esta sesión de Nitidez/
        // Saturación/Brillo/Contraste/Tono) y RECIÉN AHÍ cierra.
        // `commitJob`/`liveRenderJob` se cancelan primero por la misma
        // razón: un commit con debounce en camino de un cambio de hace
        // menos de 500ms no puede pisar el original de vuelta después.
        onDeleteEffect = {
            scheduler.cancelPending()
            viewModel.revertLayerToUri(layer.id, originalSourceUriBeforeSession)
            onClose()
        },
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        // BUG REAL corregido acá — reportado con captura ("al expandirse
        // se ve una parte y luego el resto, como si tuviera lag"): antes
        // TODA esta ventana se quedaba atrás de `isLoading` (un spinner
        // de 120dp) hasta que `liveBitmap` terminaba de decodificarse en
        // segundo plano — aunque los 5 sliders de acá abajo NO necesitan
        // ese bitmap para nada, solo leen/escriben `ctrl` (en memoria,
        // disponible de una). Como la decodificación tarda apenas unos
        // milisegundos, lo que el usuario veía era la ventana abriendo
        // en su tamaño chico de spinner y, uno o dos frames después,
        // saltando de golpe a su alto real de sliders — un cambio de
        // tamaño en dos pasos que se lee como una animación entrecortada
        // o un lag de renderizado, nada profesional.
        //
        // `liveBitmap`/`fullBitmap` siguen cargándose exactamente igual
        // en el `LaunchedEffect` de arriba — lo único que cambia es que
        // los sliders ya no esperan a que terminen: `scheduler.onParametersChanged()`
        // ya comprobaba `if (small != null ...)` antes de esto, así que
        // si el usuario mueve un slider en el primerísimo instante (antes
        // de que la decodificación de ~milisegundos termine) simplemente
        // no hay vista previa en vivo para ESE tick puntual — el commit
        // final 500ms después sigue esperando a `fullBitmap`/`liveBitmap`
        // como siempre. Ningún dato se pierde, la ventana solo deja de
        // fingir una carga que en los hechos no bloquea nada visible.
        EffectsCategoryColor(ctrl, onChanged = { scheduler.onParametersChanged() })
    }
}


/**
 * forma o texto ya rasterizado por igual, no hace falta que sea un
 * "sticker". A PEDIDO DEL USUARIO: mismo tratamiento que "Recolor" (ver
 * [RecolorFloatingWindow], acá arriba) — ya NO es un panel fijo pegado
 * abajo de la pantalla; ahora es una ventana flotante DENTRO del canvas
 * (ver [FloatingToolWindow]), arrastrable desde su cabecera y
 * redimensionable desde su esquina inferior derecha, igual que un
 * plugin de audio flotante. Mismo patrón de vista previa en vivo +
 * guardado con debounce de 500ms que [RecolorFloatingWindow] usa para
 * "Recolor": cada movimiento de slider recalcula sobre una copia chica
 * (liviano, sin lag) y sube esa vista previa; medio segundo después del
 * último movimiento, se recalcula sobre la copia grande y se persiste
 * como archivo nuevo (reusa EditorViewModel.previewLayerRecolor/
 * commitLayerRecolor tal cual — son genéricos, no hacen nada específico
 * de "recolorear").
 *
 * Nota honesta: como el cuerpo extruido puede sobresalir del cuadro
 * original de la imagen (por la rotación y la profundidad), el bitmap
 * resultante es más grande que el original con un margen alrededor —
 * evita que el efecto se vea recortado, a costa de que el tamaño en
 * píxeles de la capa cambie al aplicar el efecto.
 */
private val BASICO_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(260.dp, 480.dp)

@Composable
internal fun Basico3DFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    extrude3DBridge: Extrude3DGestureBridge,
    initialOffset: Offset,
    onClose: () -> Unit,
    // Ver el comentario grande sobre `onInteracted` en [FloatingToolWindow]
    // y sobre `floatingWindowZOrderCounter` donde se instancian las tres
    // ventanas — esto solo se reenvía tal cual, sin lógica propia acá.
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    // Ver el comentario grande sobre `floatingWindowAreaSizePx` donde se
    // instancian las tres ventanas, y su uso dentro de [FloatingToolWindow]
    // — esto solo se reenvía tal cual, sin lógica propia acá.
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {

    var liveBitmap by remember(layer.id) { mutableStateOf<Bitmap?>(null) }
    val sources = remember(layer.id) { EffectSources<Bitmap>() }

    var rotationX by remember(layer.id) { mutableStateOf(0f) }
    var rotationY by remember(layer.id) { mutableStateOf(0f) }
    var rotationZ by remember(layer.id) { mutableStateOf(0f) }
    var depth by remember(layer.id) { mutableStateOf(0.35f) }
    var bevel by remember(layer.id) { mutableStateOf(0.5f) }
    var materialOpacity by remember(layer.id) { mutableStateOf(1f) }
    // Throttle de la vista previa en vivo — ver el porqué completo en
    // el comentario de applyLivePreviewAndScheduleCommit más abajo.

    // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
    // [RecolorFloatingWindow]/[ColorBasicoFloatingWindow] (ver el
    // comentario grande sobre `originalSourceUriBeforeSession`, junto a
    // su declaración en [RecolorFloatingWindow], para el porqué
    // completo de usar el Uri y no un Bitmap decodificado): el Uri que
    // tenía la capa ANTES de que esta ventana la tocara (plana, sin
    // extruir), capturado UNA sola vez al entrar, sin IO, sin
    // corrutina. Acá importa todavía más que en las otras dos: el
    // estado "por defecto" de los sliders (depth=0.35, bevel=0.5) YA es
    // un efecto 3D visible, no un neutro — así que "Eliminar" no puede
    // simplemente resetear los sliders a esos valores de apertura, que
    // seguirían extruyendo la imagen. Tiene que volver directo a este
    // Uri plano, sin pasar de nuevo por Extrude3D.render.
    val originalSourceUriBeforeSession = remember(layer.id) { layer.sourceUri }

    // BUG REAL corregido acá: este efecto estaba keyeado en
    // (layer.id, layer.sourceUri), copiando el patrón de
    // LayerColorEditPanel — pero ahí ese patrón funciona porque
    // `remaps` guarda un DELTA de color que se limpia en cada recarga
    // (el remap ya quedó horneado en el archivo nuevo, así que
    // arrancar de cero es correcto). Acá `currentParams()` no es un
    // delta: son ángulos y profundidad ABSOLUTOS que Extrude3D.render
    // aplica sobre la imagen que se le pase como si fuera plana.
    //
    // commitLayerRecolor (reusado tal cual para el 3D) escribe el
    // RESULTADO YA EXTRUIDO a un archivo nuevo y actualiza
    // `layer.sourceUri` para apuntar ahí, 500ms después de cada pausa
    // al mover un slider. Con el key viejo, ese cambio de sourceUri
    // volvía a disparar este LaunchedEffect, que recargaba
    // `liveBitmap`/`fullBitmap` desde ese archivo YA EXTRUIDO — o sea,
    // la imagen "plana" que Extrude3D.render usaba de ahí en más ya
    // no era plana, era el cuerpo 3D anterior aplastado a 2D otra vez.
    // El siguiente movimiento de slider extruía ESE resultado de
    // vuelta (re-extruyendo un extruido), y como cada pausa dispara
    // un commit nuevo, mover varios sliders con pausas entre medio
    // (uso normal) iba acumulando esa distorsión — de ahí que la
    // figura se achicara/corriera cada vez más con el uso.
    //
    // Ahora el efecto solo depende de `layer.id`: la imagen base se
    // decodifica UNA vez al entrar al panel para esta capa (siempre
    // la plana original, o la que estaba guardada al abrir), y
    // `currentParams()` se sigue aplicando sobre esa MISMA base fija
    // durante toda la sesión de edición, sin importar cuántas veces
    // sourceUri cambie por los commits de fondo. Cambiar de capa (o
    // volver a entrar más tarde) sí recarga desde el sourceUri vigente
    // en ese momento, que es el comportamiento esperado.
    LaunchedEffect(layer.id) {
        val small = withContext(Dispatchers.IO) {
            ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = EffectsPreviewSizing.QUICK_MAX_DIMENSION)
        }
        liveBitmap = small
        sources.publishQuick(small)
        // Resolución completa para el commit final (lo que se GUARDA de
        // verdad en la capa) — solo `liveBitmap` de arriba se achica, y
        // solo para que arrastrar el slider se sienta fluido en tiempo
        // real. Un límite acá capaba para siempre la calidad de la capa
        // apenas se aplicaba esta herramienta.
        EffectSourceLoader.loadRemaining(
            sources = sources,
            displayDecodeLimit = { context.effectsDisplayDecodeLimit(EditViewFit.FILL_FRACTION) },
            onDecodeFailure = ::logEffectDecodeFailure,
            decode = { maxDimension ->
                withContext(Dispatchers.IO) {
                    ImageDecoding.decodeSampledFromUri(context.contentResolver, layer.sourceUri, maxDimension = maxDimension)
                }
            }
        )
    }

    fun currentParams() = Extrude3D.Params(
        rotationXDeg = rotationX,
        rotationYDeg = rotationY,
        rotationZDeg = rotationZ,
        depth = depth,
        bevel = bevel,
        opacity = materialOpacity
    )

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, tier -> viewModel.renderExtrude3D(source, currentParams(), highQuality = tier != RenderTier.DRAG) },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "3d") }
    )

    // Registro en el puente hacia el gesto del canvas (ver
    // [Extrude3DGestureBridge]): mientras esta pestaña está en
    // pantalla para esta capa, el canvas de arriba deja de dibujar el
    // marco/manijas normales y en cambio manda acá los grados de
    // arrastre/pellizco — acá se traducen a rotaciones absolutas
    // (mismos campos que ya mueven los sliders, así que sliders y
    // dedo quedan siempre sincronizados) y se dispara la MISMA vista
    // previa en vivo + guardado con debounce de cualquier otro
    // cambio. `keys(layer.id)` en el DisposableEffect: si se cambia
    // de capa sin salir de la pestaña "3D", primero se da de baja el
    // registro viejo (onDispose) y se crea uno nuevo ya atado a la
    // capa correcta — así el gesto nunca termina rotando por error la
    // capa anterior.
    DisposableEffect(layer.id) {
        extrude3DBridge.active = true
        extrude3DBridge.onOrbitDrag = { dxDeg, dyDeg ->
            // Izquierda/derecha del dedo → Rotación Y (mismo eje que
            // el slider "Rotación Y"); arriba/abajo del dedo →
            // Rotación X. El signo de X va invertido (se resta el
            // delta vertical) para que arrastrar HACIA ARRIBA incline
            // el cuerpo como si se lo estuviera mirando desde abajo
            // subiendo la vista — la convención habitual en apps de
            // modelado 3D con un dedo (arrastrar arriba = la cámara
            // "sube" alrededor del objeto).
            rotationY = (rotationY + dxDeg).coerceIn(-180f, 180f)
            rotationX = (rotationX - dyDeg).coerceIn(-180f, 180f)
            scheduler.onParametersChanged()
        }
        extrude3DBridge.onTwistDrag = { dzDeg ->
            rotationZ = (rotationZ + dzDeg).coerceIn(-180f, 180f)
            scheduler.onParametersChanged()
        }
        onDispose {
            extrude3DBridge.active = false
            extrude3DBridge.onOrbitDrag = null
            extrude3DBridge.onTwistDrag = null
        }
    }

    // --- Ventana flotante, arrastrable y redimensionable ---
    // A PEDIDO DEL USUARIO: mismo patrón exacto que [RecolorFloatingWindow]
    // — cabecera arrastrable con el título "Básico" + botón de cerrar, y
    // una manija de redimensionar en la esquina inferior derecha. El
    // contenido mantiene scroll vertical propio (heredado de
    // [FloatingToolWindow]), así que con la ventana achicada al mínimo
    // nada queda cortado — simplemente se puede deslizar.
    FloatingToolWindow(
        title = "3D Básico",
        // Mismo glifo (cubo) que ya usa "Básico" en [EditImage3DMenu] —
        // ver [Menu3DBasicCubeIcon].
        titleIcon = { tint -> Menu3DBasicCubeIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // A PEDIDO EXPLÍCITO DEL USUARIO — mismo mecanismo que
        // [RecolorFloatingWindow]/[ColorBasicoFloatingWindow], con una
        // diferencia importante (ver el comentario grande sobre
        // `originalSourceUriBeforeSession` más arriba en esta función):
        // acá NO alcanza con resetear rotationX/Y/Z, depth, bevel y
        // materialOpacity a sus valores de apertura, porque esos valores
        // de apertura YA extruyen la imagen — "Eliminar" tiene que
        // volver directo al Uri plano original, sin pasar de nuevo por
        // Extrude3D.render.
        onDeleteEffect = {
            scheduler.cancelPending()
            viewModel.revertLayerToUri(layer.id, originalSourceUriBeforeSession)
            onClose()
        },
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        // BUG REAL corregido acá — mismo caso que [ColorBasicoFloatingWindow]
        // (ver el comentario grande de acá arriba, en esa función, para
        // el detalle completo): estos 6 sliders son ángulos/porcentajes
        // en memoria (`rotationX`/`rotationY`/.../`materialOpacity`), no
        // dependen del bitmap para nada — el spinner de `isLoading` solo
        // agregaba un salto de tamaño visible (spinner chico → panel
        // real) apenas unos milisegundos después de abrir, que es
        // exactamente el "se expande por partes" reportado. `liveBitmap`/
        // `fullBitmap` se siguen cargando igual en el `LaunchedEffect` de
        // arriba; `scheduler.onParametersChanged()` ya los trata como
        // opcionales (`liveBitmap` puede ser null en el primerísimo tick).
                LabeledSlider(
                    label = "Rotación X (arriba/abajo)",
                    value = rotationX,
                    range = -180f..180f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { rotationX = it; scheduler.onParametersChanged() }

                LabeledSlider(
                    label = "Rotación Y (izquierda/derecha)",
                    value = rotationY,
                    range = -180f..180f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { rotationY = it; scheduler.onParametersChanged() }

                LabeledSlider(
                    label = "Rotación Z (giro plano)",
                    value = rotationZ,
                    range = -180f..180f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { rotationZ = it; scheduler.onParametersChanged() }

                LabeledSlider(
                    label = "Profundidad",
                    value = depth,
                    range = 0.05f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { depth = it; scheduler.onParametersChanged() }

                LabeledSlider(
                    label = "Bisel (borde redondeado)",
                    value = bevel,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { bevel = it; scheduler.onParametersChanged() }

                LabeledSlider(
                    label = "Opacidad del material",
                    value = materialOpacity,
                    range = 0.2f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { materialOpacity = it; scheduler.onParametersChanged() }
    }
}
