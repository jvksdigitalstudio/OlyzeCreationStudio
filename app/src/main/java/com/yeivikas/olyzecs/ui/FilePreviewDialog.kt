@file:OptIn(ExperimentalFoundationApi::class)

// El error real de CI (Android CI Build #525) fue: `:app:compileDebugKotlin`
// falló porque `PagerState`/`rememberPagerState`/`HorizontalPager`/
// `PagerState.animateScrollToPage` siguen marcados `@ExperimentalFoundationApi`
// (nivel de opt-in ERROR, no solo un warning) en la versión de Compose
// Foundation que resuelve el BOM 2024.06.00 de este proyecto — sin
// `@OptIn`, usarlos es un error de compilación real, no una advertencia
// ignorable. El opt-in se declara a nivel de ARCHIVO (no acotado a una
// sola función) a propósito: sin poder compilar en este entorno de
// trabajo para confirmar si además `awaitEachGesture`/`calculateZoom`/
// `calculatePan`/`calculateCentroidSize` (usados más abajo en
// `detectZoomAwarePanGesture`) comparten la misma marca en esta versión
// exacta, un opt-in de archivo entero cubre de una sola vez cualquier
// superficie de `ExperimentalFoundationApi` que haya en este archivo —
// preferible a arriesgar una segunda corrida de CI fallida por haber
// acotado el `@OptIn` a una sola función y quedarse corto.
package com.yeivikas.olyzecs.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.os.Build
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.NeutralChromeGray
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val TAG = "FilePreviewDialog"

/**
 * Visor de archivos con vista previa real por tipo — completa, a nivel de
 * ARCHIVO SUELTO, lo mismo que el navegador de carpetas de la papelera de
 * proyectos ya hace a nivel de carpeta (ver ProjectsTrashScreen.kt): antes,
 * tocar una fila que no era carpeta no hacía nada; ahora cualquier archivo
 * es interactuable, con la vista previa apropiada a su contenido —
 * exactamente el criterio de un explorador de archivos profesional
 * (Google Files, ES Explorador de Archivos, macOS Quick Look), no una
 * lista de nombres inerte.
 *
 * [items] es la lista COMPLETA de archivos navegables en este visor y
 * [initialIndex] cuál de ellos se abre primero — nunca un solo archivo
 * aislado. Esto es lo que habilita, cuando el archivo abierto es una
 * imagen y hay más de una en la carpeta, la galería inmersiva de
 * [ImageGalleryDialogContent] (deslizar entre fotos + filmstrip de
 * miniaturas abajo, igual criterio que Google Fotos / ES Explorador de
 * Archivos): [items] SIGUE siendo, en ese caso, solo las imágenes de la
 * carpeta — nunca mezcla JSON/audio/etc. en el mismo carrusel. Para
 * cualquier otro tipo de archivo (JSON, texto, audio, video, sin
 * previsualización) [items] es simplemente una lista de un solo elemento
 * y el visor se comporta como una ficha única, sin carrusel ni
 * inmersión — ese comportamiento es EXCLUSIVO del visor de imagen, a
 * pedido explícito de diseño.
 *
 * Qué decide qué se renderiza dentro de cada ficha (ver
 * [classifyFilePreviewKind]): imagen con zoom/pan real dentro de la
 * galería, JSON con resaltado de sintaxis, texto plano, audio o video con
 * reproductor propio, o — para cualquier extensión que no se sepa
 * previsualizar — una ficha con los datos del archivo más
 * "Compartir"/"Abrir con…", nunca un callejón sin salida.
 *
 * Deliberadamente NO conoce [com.yeivikas.olyzecs.data.TrashedProjectEntry]
 * ni ningún otro modelo del dominio de proyectos: cada [FilePreviewItem]
 * recibe su propio [FilePreviewItem.onPrepareForShare] ya cerrado sobre lo
 * que el llamador tenga a mano, para poder reusarse el día de mañana desde
 * cualquier otro lugar de la app que liste archivos (p. ej. un futuro
 * explorador de `images/`/`audio/` de un proyecto abierto).
 */
@Composable
fun FilePreviewDialog(
    items: List<FilePreviewItem>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) {
        // Defensivo: el único call site siempre construye `items` con al
        // menos un elemento (el archivo recién tocado), pero un visor no
        // debe poder quedar "abierto" mostrando nada — se autocierra en
        // vez de crashear con un índice fuera de rango.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val startIndex = remember(items, initialIndex) { initialIndex.coerceIn(0, items.lastIndex) }
    // La decisión "¿es esto una galería de imágenes?" depende SOLO del
    // archivo que se abrió — por contrato del único call site
    // (ProjectsTrashScreen.kt), si ese archivo es una imagen, TODA la
    // lista `items` son imágenes de la misma carpeta; si no lo es,
    // `items` es un único archivo no-imagen. No hace falta (ni conviene
    // por costo) recorrer la lista entera para confirmarlo en cada
    // recomposición.
    val startKind = remember(items, startIndex) { classifyFilePreviewKind(items[startIndex].fileName) }

    // --- Por qué esto NO es un `Dialog` de Compose (y antes sí lo era) ---
    // `Dialog` abre su PROPIA `Window` de Android, superpuesta a la
    // `Window` real de `MainActivity` (ver `DialogWindowProvider`). Esa es
    // la causa raíz real — confirmada en dispositivo — de la franja
    // morada/azul (`brand_purple_deep`, ver themes.xml) arriba y abajo que
    // seguía apareciendo en el visor inmersivo por más exhaustivo que
    // fuera el ajuste de `statusBarColor`/`navigationBarColor`/
    // `windowBackground`/insets de la Window DEL DIÁLOGO (ver el bloque
    // extenso de comentarios, ahora eliminado, que documentaba cada
    // intento): con dos `Window` de Android superpuestas, el compositor
    // del sistema puede seguir pintando restos del `statusBarColor`/
    // `navigationBarColor` de la Window de ABAJO (la de la Activity) en la
    // fila de píxeles de las barras del sistema, sin importar qué tan bien
    // configurada esté la Window de ARRIBA (la del diálogo) — un
    // comportamiento de compositing de plataforma que varía por
    // fabricante/versión y que ningún ajuste adicional sobre la Window del
    // diálogo puede garantizar en el 100% de los dispositivos.
    //
    // La solución de raíz — no otro ajuste más sobre la Window del
    // diálogo — es no tener una segunda Window: este visor se monta como
    // una pantalla más DENTRO de la única Window de `MainActivity`, mismo
    // patrón que ya usa esa Activity para alternar entre "Mis proyectos"
    // y el editor. `ProjectsTrashScreen` (el único llamador de este
    // visor) se convirtió a ese mismo patrón en esta misma tanda de
    // cambios — antes también se abría con su propio `Dialog`, lo que
    // sumaba una Window más a la pila (ver FASE 16 en
    // docs/fases/). `ErrorLogScreen` sigue usando `Dialog` a propósito:
    // no interviene en el camino de este visor y no tenía el bug
    // reportado, así que quedó fuera de alcance de este cambio (ver
    // "Qué NO se tocó" en ese mismo documento). El modo inmersivo (barras
    // ocultas, fondo negro de borde a borde) se logra manipulando
    // DIRECTAMENTE la Window real de la Activity mientras el visor está
    // en pantalla, y restaurándola por completo al cerrar — ver
    // `ImmersiveActivityWindow` más abajo.
    BackHandler(onBack = onDismiss)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (startKind == FilePreviewKind.IMAGE) {
            ImageGalleryDialogContent(items = items, startIndex = startIndex, onDismiss = onDismiss)
        } else {
            SingleFilePreviewScaffold(item = items[startIndex], onDismiss = onDismiss)
        }
    }
}

/**
 * Busca hacia arriba en la cadena de [ContextWrapper] hasta encontrar la
 * [Activity] real — necesario porque `LocalContext.current` dentro de
 * Compose puede llegar envuelto (p. ej. en un `ContextThemeWrapper`) en
 * vez de ser la `Activity` en sí. Usado para tomar la `Window` real de
 * `MainActivity` y aplicarle el modo inmersivo del visor (ver
 * [ImmersiveActivityWindow]) — sin esto no hay forma de llegar a esa
 * `Window` desde un composable que no es una `Activity`.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Un archivo previsualizable en su forma mínima y agnóstica de dominio
 * (ver el comentario de cabecera de este archivo). [onPrepareForShare]
 * queda cerrado sobre la entrada real de quien construye la lista (p. ej.
 * `ProjectsTrashScreen` cerrando sobre su propio `TrashedProjectEntry`),
 * así este archivo sigue sin conocer ningún modelo de proyecto — cada
 * ítem de una galería de varias fotos trae su PROPIO lambda de compartir
 * porque cada foto es, en el dominio de quien llama, una entrada distinta
 * (con su propia ruta real en disco).
 *
 * [onDelete] sigue el mismo criterio de agnosticismo: `null` (valor por
 * defecto) significa "este visor no ofrece eliminar este archivo" — el
 * menú de opciones (ver [FilePreviewOverflowMenu]) simplemente no
 * muestra "Eliminar" en ese caso, en vez de mostrarlo deshabilitado o
 * fallar. Cuando el llamador sí lo provee, es un lambda SIN retorno
 * (fire-and-forget) cerrado sobre la operación real de borrado de su
 * propio dominio — mismo contrato que ya usa el resto de "Eliminar" de
 * esta app (ver `ProjectsTrashViewModel.deleteEntry`, invocado igual de
 * "dispara y listo" desde `DeleteTrashedEntryConfirmDialog` en
 * ProjectsTrashScreen.kt): el visor no espera ninguna confirmación de
 * éxito/fracaso antes de cerrarse — confía en que quien llama actualice
 * su propio estado de forma asíncrona, exactamente como ya ocurre en
 * cualquier otro punto de borrado de la app.
 */
data class FilePreviewItem(
    val fileName: String,
    val file: File,
    val sizeBytes: Long,
    val onPrepareForShare: suspend () -> File?,
    val onDelete: (() -> Unit)? = null
)

// ============================================================================
// Ficha de archivo único — JSON, texto, audio, video o sin vista previa.
// Las imágenes NUNCA llegan acá: se enrutan siempre a
// ImageGalleryDialogContent desde el dispatcher de FilePreviewDialog.
// ============================================================================

@Composable
private fun SingleFilePreviewScaffold(item: FilePreviewItem, onDismiss: () -> Unit) {
    val kind = remember(item.fileName) { classifyFilePreviewKind(item.fileName) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isPreparingAction by remember { mutableStateOf(false) }
    // Estado del menú de opciones (⋮ → Eliminar/Compartir/Propiedades) y
    // de sus dos superposiciones — ver el comentario de cabecera de
    // [FilePreviewOverflowMenu] sobre por qué ninguna de las dos es un
    // `Dialog`/`AlertDialog` de Compose.
    var showProperties by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun launchExternalIntent(action: String, chooserTitle: String) {
        if (isPreparingAction) return
        isPreparingAction = true
        scope.launch {
            shareOrOpenWith(context, action, chooserTitle, item.fileName, item.onPrepareForShare)
            isPreparingAction = false
        }
    }

    // Tamaño REAL (px) de este visor completo — medido una sola vez acá,
    // en el `Box` MÁS EXTERIOR de este scaffold (cabecera + contenido +
    // overlays, los tres). Es el límite contra el que [FloatingAudioPlayerPill]
    // recorta su arrastre: al vivir ese reproductor como hermano de la
    // `Column` de abajo (no adentro del `Box` de contenido con `weight(1f)`),
    // puede desplazarse por TODA la pantalla — cabecera incluida — en vez
    // de quedar encerrado bajo la barra superior. Mismo criterio de
    // "medir el contenedor real con `onSizeChanged`" que ya usa el pellizco
    // de la galería de imágenes más abajo en este mismo archivo (ver
    // `containerSize` en `ImageGalleryDialogContent`).
    var containerSizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { containerSizePx = it }) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilePreviewTopBar(
                fileName = item.fileName,
                subtitle = formatFileSize(item.sizeBytes),
                onBack = onDismiss,
                isBusy = isPreparingAction,
                onShare = { launchExternalIntent(Intent.ACTION_SEND, "Compartir \"${item.fileName}\"") },
                onShowProperties = { showProperties = true },
                onDeleteRequested = item.onDelete?.let { { showDeleteConfirm = true } }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (kind) {
                    FilePreviewKind.JSON -> TextualPreviewContent(
                        file = item.file, sizeBytes = item.sizeBytes, isJson = true,
                        onCopyAll = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        }
                    )
                    FilePreviewKind.TEXT -> TextualPreviewContent(
                        file = item.file, sizeBytes = item.sizeBytes, isJson = false,
                        onCopyAll = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        }
                    )
                    // Solo el telón de fondo (glifo + nombre) — los
                    // controles reales de reproducción viven en
                    // [FloatingAudioPlayerPill], instanciado más abajo
                    // como HERMANO de esta `Column`, no adentro de este
                    // `Box`. Ver el comentario grande sobre `containerSizePx`
                    // más arriba para el porqué.
                    FilePreviewKind.AUDIO -> AudioPreviewBackdrop(fileName = item.fileName)
                    FilePreviewKind.VIDEO -> VideoPreviewContent(file = item.file)
                    FilePreviewKind.UNSUPPORTED -> UnsupportedPreviewContent(
                        fileName = item.fileName,
                        sizeBytes = item.sizeBytes,
                        isBusy = isPreparingAction,
                        onOpenWith = { launchExternalIntent(Intent.ACTION_VIEW, "Abrir \"${item.fileName}\" con…") },
                        onShare = { launchExternalIntent(Intent.ACTION_SEND, "Compartir \"${item.fileName}\"") }
                    )
                    FilePreviewKind.IMAGE -> error(
                        "Invariante rota: FilePreviewKind.IMAGE nunca debe llegar a SingleFilePreviewScaffold " +
                            "— el dispatcher de FilePreviewDialog debió enrutarlo a ImageGalleryDialogContent."
                    )
                }
            }
        }

        // Reproductor flotante de audio — HERMANO de la `Column` de arriba
        // (no adentro de su `Box` de contenido), a propósito: así queda
        // libre para arrastrarse sobre TODA la superficie del visor,
        // cabecera incluida, en vez de recortado bajo ella. Ver
        // [FloatingAudioPlayerPill] para el diseño completo (pastilla
        // delgada, arrastrable, con transporte real).
        if (kind == FilePreviewKind.AUDIO) {
            FloatingAudioPlayerPill(
                file = item.file,
                fileName = item.fileName,
                containerSizePx = containerSizePx,
                onDismiss = onDismiss
            )
        }

        if (showProperties) {
            FilePropertiesOverlay(item = item, onDismiss = { showProperties = false })
        }
        if (showDeleteConfirm) {
            FilePreviewDeleteConfirmOverlay(
                fileName = item.fileName,
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    item.onDelete?.invoke()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Comparte o abre-con [fileName] usando lo que devuelva [onPrepareForShare]
 * — helper compartido entre la ficha de archivo único y la galería de
 * imágenes para no duplicar la construcción del Intent/FileProvider en
 * dos lugares. Devuelve `true` si se pudo lanzar el Intent.
 */
private suspend fun shareOrOpenWith(
    context: Context,
    action: String,
    chooserTitle: String,
    fileName: String,
    onPrepareForShare: suspend () -> File?
): Boolean {
    val shareableFile = onPrepareForShare()
    if (shareableFile == null) {
        Toast.makeText(context, "No se pudo preparar \"$fileName\" para compartir", Toast.LENGTH_SHORT).show()
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareableFile)
    val mimeType = guessMimeType(fileName)
    val intent = Intent(action).apply {
        if (action == Intent.ACTION_VIEW) {
            setDataAndType(uri, mimeType)
        } else {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        true
    } catch (t: Throwable) {
        AppLogger.w(TAG, "No hay ninguna app instalada que pueda manejar este archivo", t)
        Toast.makeText(context, "No hay ninguna app instalada para abrir este archivo", Toast.LENGTH_SHORT).show()
        false
    }
}

// ============================================================================
// Clasificación por extensión
// ============================================================================

internal enum class FilePreviewKind { IMAGE, JSON, TEXT, AUDIO, VIDEO, UNSUPPORTED }

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown", "xml", "csv", "log", "yml", "yaml", "properties", "srt", "vtt", "ini", "gradle", "kts")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "3gp", "opus")
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "3gp2")

/** true si [name] tiene una extensión de imagen previsualizable con Coil — mismo criterio que usa TrashedEntryRow. */
internal fun classifyFilePreviewKind(name: String): FilePreviewKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        in IMAGE_EXTENSIONS -> FilePreviewKind.IMAGE
        "json" -> FilePreviewKind.JSON
        in TEXT_EXTENSIONS -> FilePreviewKind.TEXT
        in AUDIO_EXTENSIONS -> FilePreviewKind.AUDIO
        in VIDEO_EXTENSIONS -> FilePreviewKind.VIDEO
        else -> FilePreviewKind.UNSUPPORTED
    }
}

private fun guessMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext == "json") return "application/json"
    if (ext == "md" || ext == "markdown") return "text/markdown"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

// ============================================================================
// Encabezado — reusado tanto por la ficha de archivo único (siempre
// visible) como por la galería de imágenes (se oculta/muestra con el modo
// inmersivo, ver ImageGalleryDialogContent).
// ============================================================================

@Composable
private fun FilePreviewTopBar(
    fileName: String,
    subtitle: String,
    onBack: () -> Unit,
    isBusy: Boolean,
    onShare: () -> Unit,
    onShowProperties: () -> Unit,
    // `null` = este archivo no se puede eliminar desde acá (ver el KDoc de
    // [FilePreviewItem.onDelete]) — el menú de "⋮" entonces no muestra
    // "Eliminar" en absoluto, nunca una opción deshabilitada.
    onDeleteRequested: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SurfaceTintedElevated, SurfaceTintedElevated.copy(alpha = 0.92f))))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(fileName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xFF8A7DB8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp, color = BrandPurpleLight)
        } else {
            // El botón "⋮" y su menú viven en el MISMO `Box`: es lo que le
            // permite a [FilePreviewOverflowMenu] anclarse a este botón por
            // construcción (ver el KDoc de esa función) en vez de necesitar
            // que este `Row` le pase ninguna coordenada.
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(painterResource(R.drawable.ic_overflow_menu), contentDescription = "Más opciones", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                FilePreviewOverflowMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onDelete = onDeleteRequested,
                    onShare = onShare,
                    onShowProperties = onShowProperties
                )
            }
        }
    }
}

// ============================================================================
// Menú "⋮" (Eliminar / Compartir / Propiedades) — ver la captura de
// referencia del cliente (menú blanco nativo tipo Android: Eliminar arriba,
// Compartir y Propiedades abajo, cada uno con su ícono a la izquierda).
//
// Deliberadamente NO usa `DropdownMenu` de Material3: esa API, en la
// versión de Compose BOM que fija este proyecto (2024.06.00 → Material3
// 1.2.x, ver app/build.gradle.kts), no expone parámetros propios para
// tematizar forma/color/elevación del contenedor (`containerColor`/
// `shape`/`shadowElevation` recién se agregaron en versiones posteriores
// de Material3) — hubiera quedado con el gris de Material por defecto,
// no el look "premium" pedido explícitamente ni la paleta de marca de
// esta app. En su lugar, este menú es un `Popup` (misma primitiva de
// bajo nivel sobre la que Material3 construye su propio `DropdownMenu`)
// con un contenedor 100% propio — mismo idioma visual que ya usa el
// resto de la app para tarjetas flotantes (`.shadow()` → `.clip()` →
// `.background()` → `.border()`, ver [GalleryFilmstripThumbnail] más
// abajo en este mismo archivo).
//
// Tampoco es un `Dialog`/`AlertDialog`: un `Popup` de Compose se adjunta
// como una sub-ventana del mismo token de ventana de `MainActivity`
// (`TYPE_APPLICATION_PANEL`), sin `statusBarColor`/`navigationBarColor`
// propios — no reintroduce el bug de "franja morada/azul" que motivó
// eliminar por completo el uso de `Dialog` en este visor (ver el
// comentario extenso junto a `FilePreviewDialog` sobre esa causa raíz).
//
// --- Anclaje: el mismo `PopupPositionProvider` que ya usa el resto de la
// app para esta clase exacta de menú (ver [BelowAnchorRightAlignedPopup-
// PositionProvider] más abajo), NO coordenadas medidas a mano ---
// Versión anterior de este menú: medía la posición del botón "⋮" en
// coordenadas de VENTANA (`onGloballyPositioned` + `boundsInWindow()`) y
// calculaba a mano el offset absoluto del `Popup`. Ese cálculo dependía
// de que la medición llegara ya estable y con las mismas coordenadas que
// terminaba usando el compositor de la Window real — con
// `WindowCompat.setDecorFitsSystemWindows(window, false)` activo durante
// el modo inmersivo de este visor (ver [ImmersiveActivityWindow]) y el
// fundido de entrada/salida del propio encabezado (`AnimatedVisibility`
// en [ImageGalleryDialogContent]), esa medición podía quedar desalineada
// de la posición real en pantalla — visible como el menú abriéndose muy
// por debajo del botón "⋮" en vez de pegado a él, en vez del anclaje
// nativo tipo Android que pedía el diseño.
//
// La corrección de raíz: este `FilePreviewOverflowMenu` se compone ahora
// SIEMPRE como hijo directo del mismo `Box` que envuelve al botón "⋮"
// (ver [FilePreviewTopBar]) — nunca aislado ni con su posición pasada por
// parámetro — y se posiciona con un `PopupPositionProvider` propio que
// Compose alimenta con el `anchorBounds` REAL de ese `Box` en cada
// medición, ya resuelto por el framework. Es el mismo patrón — mismo
// nombre de parámetro `gapPx`, mismo criterio de recorte a los bordes de
// pantalla — que ya usan `BelowAnchorCenteredPopupPositionProvider`
// (EditorScreen.kt), `TrashPanelPositionProvider` (LayerTrashScreen.kt) y
// `BelowAnchorPopupPositionProvider`/`BesideAnchorPopupPositionProvider`
// (TimelineView.kt) para cada uno de sus propios menús anclados a un
// ícono de toolbar; se replica acá (en vez de importarse desde alguno de
// esos archivos) por el mismo motivo que ya documenta
// `TrashPanelPositionProvider`: son `private` de sus archivos y este
// archivo es autocontenido a propósito. Ya no hace falta medir ventana ni
// densidad de píxeles a mano: Compose ya le da a
// [BelowAnchorRightAlignedPopupPositionProvider.calculatePosition] el
// `anchorBounds` correcto, el tamaño real ya medido del menú
// (`popupContentSize`) y el tamaño de la ventana — con eso alcanza para
// alinear el borde derecho del menú al del botón, justo debajo, sin que
// se salga de pantalla en dispositivos angostos.
// ============================================================================

private val OVERFLOW_MENU_WIDTH = 218.dp

/**
 * Alinea el borde derecho del `Popup` con el borde derecho de su ancla
 * (el botón "⋮"), justo debajo con un margen de [gapPx] — el mismo
 * anclaje visual que cualquier menú "⋮" nativo de Android. Recorta contra
 * los bordes de la ventana (margen [screenMarginPx]) para que nunca quede
 * cortado en dispositivos angostos o si el ancla está pegada al borde.
 */
private class BelowAnchorRightAlignedPopupPositionProvider(
    private val gapPx: Int,
    private val screenMarginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        var x = anchorBounds.right - popupContentSize.width
        val maxX = (windowSize.width - popupContentSize.width - screenMarginPx).coerceAtLeast(screenMarginPx)
        x = x.coerceIn(screenMarginPx, maxX)

        var y = anchorBounds.bottom + gapPx
        val maxY = (windowSize.height - popupContentSize.height - screenMarginPx).coerceAtLeast(screenMarginPx)
        y = y.coerceIn(screenMarginPx, maxY)

        return IntOffset(x, y)
    }
}

/**
 * Menú de opciones del visor de archivos ("⋮" → Eliminar/Compartir/
 * Propiedades). Debe componerse SIEMPRE como hijo directo del mismo `Box`
 * que envuelve al `IconButton` que lo abre (ver [FilePreviewTopBar]) — es
 * de ese `Box` de donde [BelowAnchorRightAlignedPopupPositionProvider]
 * toma su `anchorBounds` real. Nunca recibe la posición del botón por
 * parámetro: ver el comentario de cabecera de esta sección sobre por qué
 * ese enfoque anterior quedó descartado.
 */
@Composable
private fun FilePreviewOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: (() -> Unit)?,
    onShare: () -> Unit,
    onShowProperties: () -> Unit
) {
    if (!expanded) return
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        BelowAnchorRightAlignedPopupPositionProvider(
            gapPx = with(density) { 6.dp.roundToPx() },
            screenMarginPx = with(density) { 8.dp.roundToPx() }
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(OVERFLOW_MENU_WIDTH)
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(16.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceTintedElevated)
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(16.dp))
                .padding(vertical = 6.dp)
        ) {
            if (onDelete != null) {
                FilePreviewMenuItem(
                    icon = R.drawable.ic_delete,
                    label = "Eliminar",
                    tint = Color(0xFFE05C5C),
                    onClick = {
                        onDismissRequest()
                        onDelete()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 6.dp))
            }
            FilePreviewMenuItem(
                icon = R.drawable.ic_share,
                label = "Compartir",
                tint = Color.White,
                onClick = {
                    onDismissRequest()
                    onShare()
                }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 6.dp))
            FilePreviewMenuItem(
                icon = R.drawable.ic_info,
                label = "Propiedades",
                tint = Color.White,
                onClick = {
                    onDismissRequest()
                    onShowProperties()
                }
            )
        }
    }
}

/** Una fila del menú "⋮" — ícono + etiqueta, mismo color en ambos para que "Eliminar" se lea de inmediato como la única acción destructiva. */
@Composable
private fun FilePreviewMenuItem(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================================================================
// "¿Eliminar…?" — superposición dentro del mismo `Surface`, NO un
// `AlertDialog` de Compose. Mismo razonamiento arquitectónico que
// [FilePreviewOverflowMenu] de arriba (evitar una Window nueva encima del
// visor inmersivo), pero acá directamente sin ninguna sub-ventana: es un
// `Box` de pantalla completa con scrim oscuro + tarjeta centrada, mismo
// patrón que [ProjectInfoOverlay] en ProjectsScreen.kt (la superposición
// de "Información del proyecto" sobre la portada) — el criterio ya
// establecido en esta app para cualquier confirmación que deba aparecer
// ENCIMA de una pantalla que ya vive dentro de la única Window real de
// `MainActivity`.
// ============================================================================

@Composable
private fun FilePreviewDeleteConfirmOverlay(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            // Tocar el scrim (fuera de la tarjeta) cierra, igual que el
            // scrim de cualquier diálogo estándar.
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 340.dp)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceTintedElevated)
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(20.dp))
                // Consume el toque para que NO se propague al `.clickable`
                // del scrim de atrás — tocar dentro de la tarjeta nunca
                // debe cerrar el diálogo por accidente.
                .clickable(onClick = {})
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFE05C5C).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = Color(0xFFE05C5C), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("¿Eliminar \"$fileName\"?", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Esta acción es irreversible. Este archivo se borrará de forma permanente.",
                color = Color(0xFFD6CFEF),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onConfirm) { Text("Eliminar", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ============================================================================
// "Propiedades" — misma decisión arquitectónica que la superposición de
// borrado de arriba (superposición dentro del `Surface`, no un Dialog).
// Estructura de campos inspirada en la referencia visual del cliente
// (captura de "Propiedades" de Google Fotos: ícono, ruta, Tamaño, Fecha),
// pero con el lenguaje visual PROPIO de esta app (`SurfaceTintedElevated`
// + acento morado, no la tarjeta blanca de la referencia) — mismo criterio
// que ya usa [ProjectInfoOverlay] en ProjectsScreen.kt (que reutiliza para
// "Información del proyecto"), del que esta superposición toma la
// estructura general (scrim + columna con scroll + botón "cerrar" circular
// arriba a la derecha).
// ============================================================================

/**
 * FASE 19 — corrección real del "texto fantasma" reportado: el fondo de
 * este panel usaba `Color.Black.copy(alpha = 0.92f)` (translúcido al
 * 92%) — el 8% restante seguía dejando pasar lo que hay DETRÁS en la
 * galería (el nombre de archivo blanco de [FilePreviewTopBar] y las
 * miniaturas del filmstrip, ambos siguen activos y animados mientras
 * este panel está abierto). Con la barra superior y el filmstrip
 * quietos eso pasaría inadvertido, pero acá el texto propio del panel
 * ("Ruta", el nombre de archivo del encabezado…) se dibuja DIRECTO sobre
 * ese fondo — el resultado es el texto duplicado/fantasma reportado, un
 * bug real de opacidad, no un efecto buscado.
 *
 * Corrección: fondo 100% opaco (`SurfaceTintedDark`, el morado casi
 * negro de marca — no `Color.Black` puro, para quedar en el mismo
 * lenguaje visual del resto de la app) — cero bleed-through posible sin
 * importar qué haya montado detrás. Distinto del scrim de
 * [FilePreviewDeleteConfirmOverlay] (que SÍ debe quedar translúcido a
 * propósito: ahí el texto vive dentro de una tarjeta opaca propia, y el
 * área tenue de alrededor es el scrim intencional que deja adivinar el
 * contenido de fondo, patrón estándar de cualquier diálogo de
 * confirmación).
 */
@Composable
private fun FilePropertiesOverlay(item: FilePreviewItem, onDismiss: () -> Unit) {
    val isImage = remember(item.fileName) { classifyFilePreviewKind(item.fileName) == FilePreviewKind.IMAGE }
    // Las dimensiones de una imagen no vienen gratis en [FilePreviewItem]
    // (que a propósito no carga nada pesado por adelantado — ver su
    // KDoc): se decodifican bajo demanda, SOLO cuando el panel de
    // propiedades se abre, con `inJustDecodeBounds = true` (lee apenas la
    // cabecera del archivo, nunca decodifica los píxeles en memoria) en
    // Dispatchers.IO para no bloquear el hilo principal.
    var dimensions by remember(item.file) { mutableStateOf<Pair<Int, Int>?>(null) }
    var dimensionsReady by remember(item.file) { mutableStateOf(false) }

    LaunchedEffect(item.file, isImage) {
        if (!isImage) {
            dimensionsReady = true
            return@LaunchedEffect
        }
        dimensions = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(item.file.absolutePath, options)
                if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
            }.getOrNull()
        }
        dimensionsReady = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceTintedDark)
            .clickable(onClick = {})
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(end = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(SurfaceTintedElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (isImage) {
                        AsyncImage(
                            model = item.file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp))
                        )
                    } else {
                        Icon(painterResource(R.drawable.ic_file_generic), contentDescription = null, tint = Color(0xFF8A7DB8), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    item.fileName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(18.dp))
            FilePropertyRow("Ruta", item.file.absolutePath)
            FilePropertyRow("Tamaño", formatFileSize(item.sizeBytes))
            FilePropertyRow("Tipo", filePropertyTypeLabel(item.fileName))
            if (isImage) {
                FilePropertyRow(
                    "Dimensiones",
                    if (!dimensionsReady) "Calculando…"
                    else dimensions?.let { (w, h) -> "$w × $h px" } ?: "No disponibles"
                )
            }
            FilePropertyRow("Modificado", formatFullDate(item.file.lastModified()))
        }

        // Botón "cerrar" arriba a la derecha del panel — mismo lugar y
        // mismo look que en [ProjectInfoOverlay].
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).clickable { onDismiss() },
            color = Color.White.copy(alpha = 0.12f),
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Cerrar propiedades",
                tint = Color.White,
                modifier = Modifier.padding(5.dp).size(14.dp)
            )
        }
    }
}

/** Fila compacta "etiqueta: valor" del panel de propiedades — mismo criterio visual que `InfoDetailRow` en ProjectsScreen.kt, pero privada a este archivo (ver el comentario de cabecera sobre por qué FilePreviewDialog.kt no depende de nada de ese archivo). */
@Composable
private fun FilePropertyRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/** "Tipo" del panel de propiedades — la extensión del archivo en mayúsculas, o "Desconocido" si no tiene ninguna. */
private fun filePropertyTypeLabel(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").uppercase()
    return extension.ifEmpty { "Desconocido" }
}

// ============================================================================
// GALERÍA DE IMÁGENES — visor inmersivo estilo Google Fotos / ES
// Explorador de Archivos (referencia visual explícita del pedido):
//
//   · Carrusel horizontal real entre todas las imágenes de la carpeta
//     (deslizar con el dedo, igual que el filmstrip de abajo).
//   · Filmstrip de miniaturas en el pie, SOLO si hay más de una imagen —
//     tocar cualquier miniatura salta a esa foto en grande al instante.
//     La miniatura de la foto actual se ve un poco más grande y con
//     borde, para saber en qué imagen se está sin tener que leer el
//     contador de arriba.
//   · Modo inmersivo real: un toque en la imagen oculta a la vez el
//     encabezado de arriba, el filmstrip de abajo Y las barras del
//     sistema (status bar / barra de navegación del propio teléfono) —
//     queda SOLO la foto, de borde a borde. Otro toque las devuelve a
//     las tres juntas. Deslizar entre fotos NUNCA cambia este estado —
//     solo lo cambia un toque explícito, igual que cualquier visor de
//     fotos profesional (Google Fotos, Apple Fotos, ES Explorador de
//     Archivos).
//   · Pellizcar para acercar / doble tap para alternar 1x↔2.5x, igual que
//     antes — pero ahora coordinado con el carrusel: mientras una foto
//     está en 1x, arrastrar el dedo pasa a la foto siguiente/anterior;
//     en cuanto se hace zoom (>1x), ese mismo arrastre pasa a panear la
//     foto ampliada en vez de cambiar de foto — ver
//     [detectZoomAwarePanGesture].
// ============================================================================

private const val MIN_IMAGE_SCALE = 1f
private const val MAX_IMAGE_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

// Ventana de tiempo, en milisegundos, para que dos toques consecutivos
// cuenten como doble-tap (ver [detectTapAndDoubleTapImmediate]). 300ms es
// el valor estándar de Android (`android.view.ViewConfiguration
// .getDoubleTapTimeout()`) — el mismo umbral que usa por debajo
// `detectTapGestures` de Compose, así que el doble-tap se sigue sintiendo
// igual de "generoso"; lo único que cambia es que ya no se le hace
// esperar ese tiempo al TAP SIMPLE antes de reaccionar.
private const val DOUBLE_TAP_TIMEOUT_MILLIS = 300L

// FASE 18 — REVISIÓN QUIRÚRGICA (corrige FASE 17 sin tocar la miniatura
// seleccionada): reporte de cliente con captura en dispositivo dijo que la
// miniatura seleccionada (92dp) se veía "demasiado grande" — pero acotó
// explícitamente que ESE tamaño está bien y no debe tocarse; lo que hay
// que ajustar es la brecha de contraste contra el resto. Con 72dp de base
// la diferencia era de 20dp (72→92, +27.8%), una brecha demasiado amplia
// para el ojo. La corrección real es subir SOLO la base de 72dp a 82dp
// (+10dp / +13.9% hacia la seleccionada, brecha final de solo 10dp /
// +12.2%): la seleccionada sigue destacando con claridad — sigue siendo
// la más grande — pero la transición entre "seleccionada" y "resto" ya no
// se lee como un salto brusco de tamaño. `FILMSTRIP_THUMB_SIZE_SELECTED`
// permanece en 92dp, sin cambios, tal como pidió el cliente.
private val FILMSTRIP_THUMB_SIZE = 82.dp
private val FILMSTRIP_THUMB_SIZE_SELECTED = 92.dp
private val FILMSTRIP_SPACING = 8.dp
private const val CHROME_FADE_MS = 220

@Composable
private fun ImageGalleryDialogContent(
    items: List<FilePreviewItem>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    // Visibilidad conjunta de encabezado + filmstrip + barras del sistema
    // — las tres se mueven SIEMPRE juntas, nunca por separado (así lo
    // pidió el diseño): un solo estado gobierna las tres.
    var chromeVisible by remember { mutableStateOf(true) }
    // Si la foto actualmente en pantalla está con zoom (>1x). Vive a
    // este nivel (no dentro de cada página) porque el propio
    // HorizontalPager necesita leerlo para decidir si puede deslizar de
    // foto o si el arrastre debe ir a panear el zoom.
    var currentPageIsZoomed by remember { mutableStateOf(false) }
    var isPreparingAction by remember { mutableStateOf(false) }
    // Igual estado que en [SingleFilePreviewScaffold] para el menú "⋮" y
    // sus dos superposiciones — acá referidas siempre a `current` (la
    // imagen visible en este instante del pager), nunca a la que estaba
    // en pantalla cuando se abrió la galería.
    var showProperties by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Cambiar de foto (swipe o tap en miniatura) nunca debe "heredar" el
    // zoom de la foto anterior — cada imagen abre siempre a 1x.
    LaunchedEffect(pagerState.currentPage) { currentPageIsZoomed = false }

    fun shareCurrent() {
        if (isPreparingAction) return
        val current = items[pagerState.currentPage]
        isPreparingAction = true
        scope.launch {
            shareOrOpenWith(context, Intent.ACTION_SEND, "Compartir \"${current.fileName}\"", current.fileName, current.onPrepareForShare)
            isPreparingAction = false
        }
    }

    // --- Modo inmersivo real, borde a borde, sin ninguna franja residual
    // arriba ni abajo — aplicado DIRECTAMENTE sobre la única Window real de
    // MainActivity (ver el comentario junto a `FilePreviewDialog` sobre por
    // qué esto YA NO es un `Dialog` de Compose con su propia Window
    // separada: esa segunda Window era la causa raíz real de la franja,
    // no la configuración que se le aplicaba).
    ImmersiveActivityWindow(chromeVisible = chromeVisible)

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !currentPageIsZoomed,
            key = { page -> items[page].file.absolutePath },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableGalleryImage(
                file = items[page].file,
                onTap = { chromeVisible = !chromeVisible },
                onZoomStateChanged = { zoomed -> if (page == pagerState.currentPage) currentPageIsZoomed = zoomed }
            )
        }

        val current = items[pagerState.currentPage]
        val subtitle = if (items.size > 1) {
            "${pagerState.currentPage + 1} de ${items.size} · ${formatFileSize(current.sizeBytes)}"
        } else {
            formatFileSize(current.sizeBytes)
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(CHROME_FADE_MS)),
            exit = fadeOut(tween(CHROME_FADE_MS)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            FilePreviewTopBar(
                fileName = current.fileName,
                subtitle = subtitle,
                onBack = onDismiss,
                isBusy = isPreparingAction,
                onShare = ::shareCurrent,
                onShowProperties = { showProperties = true },
                onDeleteRequested = current.onDelete?.let { { showDeleteConfirm = true } }
            )
        }

        if (items.size > 1) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(tween(CHROME_FADE_MS)) + slideInVertically(tween(CHROME_FADE_MS)) { it / 2 },
                exit = fadeOut(tween(CHROME_FADE_MS)) + slideOutVertically(tween(CHROME_FADE_MS)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GalleryFilmstrip(
                    items = items,
                    currentIndex = pagerState.currentPage,
                    onThumbnailClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    // Segunda forma de navegar, además del tap directo:
                    // arrastre continuo (scrub). A diferencia del tap, acá
                    // se llama en cada paso del arrastre mientras el dedo
                    // sigue en pantalla, así que se salta directo a la
                    // página (sin animación) — animar cada paso intermedio
                    // se vería con retraso frente al dedo del usuario.
                    onScrub = { index -> scope.launch { pagerState.scrollToPage(index) } }
                )
            }
        }

        if (showProperties) {
            FilePropertiesOverlay(item = current, onDismiss = { showProperties = false })
        }
        if (showDeleteConfirm) {
            FilePreviewDeleteConfirmOverlay(
                fileName = current.fileName,
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    // Ver el KDoc de [FilePreviewItem.onDelete]: es
                    // fire-and-forget, así que acá no se espera ningún
                    // resultado — se cierra la galería entera de una,
                    // igual que al borrar el único archivo de
                    // [SingleFilePreviewScaffold]. No se intenta remover
                    // solo esta imagen de `items` y quedarse navegando
                    // el resto de la galería: `items`/`startIndex` están
                    // deliberadamente congelados al abrir este visor (ver
                    // el comentario de cabecera de `FilePreviewDialog`)
                    // para que el índice nunca se corra bajo el dedo del
                    // usuario — mutar la lista a mitad de sesión violaría
                    // esa misma garantía.
                    current.onDelete?.invoke()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Modo inmersivo del visor, aplicado sobre la Window REAL de `MainActivity`
 * (única Window de toda la app — ver el comentario junto a
 * `FilePreviewDialog` sobre por qué ya no existe una segunda Window de
 * diálogo). Mientras este composable esté en pantalla, dos cosas se hacen
 * y luego se DESHACEN por completo al salir:
 *
 *   1) Se pone la Window en modo borde a borde real y transparente, con la
 *      foto pudiendo ocupar la fila de píxeles de las barras del sistema
 *      y el recorte/notch en cualquier orientación.
 *   2) Se muestran u ocultan las barras del sistema según [chromeVisible]
 *      (encabezado + filmstrip + barras, siempre juntos).
 *
 * Al cerrar el visor, `onDispose` restaura EXACTAMENTE los valores
 * estáticos de `Theme.Olyze` (ver themes.xml: `brand_purple_deep` en
 * `windowBackground`/`statusBarColor`/`navigationBarColor`) — el resto de
 * la app (ProjectsScreen, EditorScreen, etc.) nunca pidió modo inmersivo y
 * no debe heredar ningún resto de esta configuración.
 */
@Composable
private fun ImmersiveActivityWindow(chromeVisible: Boolean) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    val brandPurpleDeepArgb = remember(context) {
        ContextCompat.getColor(context, R.color.brand_purple_deep)
    }

    DisposableEffect(window) {
        if (window == null) {
            AppLogger.w(TAG, "No se encontró la Activity real desde el Context del visor — modo inmersivo no aplicado")
            return@DisposableEffect onDispose {}
        }

        // Cada paso en su PROPIO runCatching, independiente de los demás —
        // si alguno falla en un fabricante puntual, el resto igual se
        // aplica, y el fallo queda registrado en "Registro de errores" de
        // la app en vez de perderse en silencio.
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }.onFailure { AppLogger.w(TAG, "setDecorFitsSystemWindows(false) falló", it) }

        runCatching {
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
        }.onFailure { AppLogger.w(TAG, "setBackgroundDrawable(BLACK) falló", it) }

        runCatching {
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
        }.onFailure { AppLogger.w(TAG, "statusBarColor/navigationBarColor TRANSPARENT falló", it) }

        runCatching {
            // Desde Android 10 (API 29) el sistema dibuja por defecto un
            // scrim semitransparente propio detrás de las barras aun con
            // `statusBarColor`/`navigationBarColor` en TRANSPARENT, para
            // mantener legibles sus iconos — se desactiva explícitamente:
            // el criterio correcto para un visor inmersivo real es que la
            // foto se vea sin ningún tinte ajeno superpuesto.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }.onFailure { AppLogger.w(TAG, "isStatusBarContrastEnforced/isNavigationBarContrastEnforced falló", it) }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    // ALWAYS (no SHORT_EDGES): deja dibujar SIEMPRE hasta el
                    // borde físico real, incluida el área del recorte/notch,
                    // sin importar la orientación.
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
        }.onFailure { AppLogger.w(TAG, "layoutInDisplayCutoutMode(ALWAYS) falló", it) }

        runCatching {
            // Hallazgo en dispositivo real (OnePlus, Android 16 — más
            // nuevo que el `targetSdk` de este proyecto): si CUALQUIER
            // vista de la jerarquía nativa del decorView le aplica los
            // insets de barras como padding — comportamiento por defecto
            // de Android salvo que se consuman — puede seguir viéndose una
            // franja del color de fondo original, aun con
            // `decorFitsSystemWindows(false)` ya puesto arriba. Se
            // consumen explícitamente mientras el visor está abierto.
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, _ ->
                WindowInsetsCompat.CONSUMED
            }
        }.onFailure { AppLogger.w(TAG, "setOnApplyWindowInsetsListener(CONSUMED) falló", it) }

        onDispose {
            // Restauración COMPLETA y explícita — no basta con "mostrar las
            // barras": cada propiedad tocada arriba se devuelve a su valor
            // real de `Theme.Olyze`, para que el resto de la app (que nunca
            // pidió modo inmersivo) quede exactamente como antes de abrir
            // el visor.
            runCatching {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }.onFailure { AppLogger.w(TAG, "mostrar barras del sistema al cerrar falló", it) }

            runCatching {
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }.onFailure { AppLogger.w(TAG, "setDecorFitsSystemWindows(true) al cerrar falló", it) }

            runCatching {
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.setBackgroundDrawable(ColorDrawable(brandPurpleDeepArgb))
            }.onFailure { AppLogger.w(TAG, "restaurar windowBackground al cerrar falló", it) }

            runCatching {
                window.statusBarColor = brandPurpleDeepArgb
                window.navigationBarColor = brandPurpleDeepArgb
            }.onFailure { AppLogger.w(TAG, "restaurar statusBarColor/navigationBarColor al cerrar falló", it) }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = true
                    window.isNavigationBarContrastEnforced = true
                }
            }.onFailure { AppLogger.w(TAG, "restaurar isStatusBarContrastEnforced al cerrar falló", it) }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }.onFailure { AppLogger.w(TAG, "restaurar layoutInDisplayCutoutMode al cerrar falló", it) }

            runCatching {
                // Devuelve el decorView al despacho de insets por defecto
                // de Android (`null` = sin listener propio) — el resto de
                // la app nunca pidió consumirlos.
                ViewCompat.setOnApplyWindowInsetsListener(window.decorView, null)
            }.onFailure { AppLogger.w(TAG, "quitar listener de insets al cerrar falló", it) }
        }
    }

    LaunchedEffect(window, chromeVisible) {
        if (window == null) return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE: con el chrome oculto, un
        // deslizamiento corto desde el borde vuelve a mostrar las barras de
        // forma transitoria y flotante (sin desplazar el contenido) — el
        // comportamiento estándar de cualquier visor profesional en modo
        // inmersivo (YouTube, Google Fotos), en vez de dejar a la persona
        // sin forma de recuperar las barras del sistema.
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Filmstrip de miniaturas en el pie de la galería — SOLO se renderiza si
 * hay más de una imagen (ver el call site). La miniatura de la foto
 * actual se anima a un tamaño mayor y con borde de acento; el filmstrip
 * se autocentra sobre ella cada vez que cambia (swipe en el visor
 * principal, tap en otra miniatura o scrub) — igual comportamiento que el
 * filmstrip de cualquier galería profesional: SIGUE al visor, nunca al
 * revés.
 *
 * Dos formas de navegar conviven acá, a pedido explícito de diseño:
 *   1) Tap en cualquier miniatura → salta directo a esa foto
 *      ([onThumbnailClick], vía el `clickable` de cada
 *      [GalleryFilmstripThumbnail]).
 *   2) Presionar y arrastrar SIN SOLTAR sobre cualquier miniatura →
 *      scrubber de video profesional ([onScrub]): el marco de selección
 *      SIGUE AL DEDO en tiempo real, 1:1, seleccionando exactamente la
 *      miniatura que está bajo el punto de contacto en cada instante —
 *      igual criterio que el scrubber de miniaturas de cualquier editor
 *      de video profesional (o el selector de fecha por arrastre de
 *      Google Fotos), nunca una aproximación por distancia recorrida.
 *
 * ## FASE 18 — revisión quirúrgica del scrub (corrige la desincronización
 * reportada: "muevo el dedo sobre una cartilla y el marco selecciona
 * otra, por otro lado")
 * La implementación anterior no conocía la posición real de ninguna
 * miniatura: acumulaba el DELTA de arrastre (`dx`) en píxeles y, cada vez
 * que la suma superaba un umbral abstracto (`FILMSTRIP_SCRUB_STEP`,
 * 36dp), avanzaba o retrocedía UN índice — una heurística de "distancia
 * recorrida", completamente desacoplada de dónde estuviera el dedo
 * realmente sobre la fila. Como cada miniatura mide entre [FILMSTRIP_THUMB_SIZE]
 * y [FILMSTRIP_THUMB_SIZE_SELECTED] (82–92dp, bastante más que el umbral
 * de 36dp), el marco de selección "corría por delante" del dedo o se
 * quedaba atrás según la velocidad del arrastre — el bug reportado.
 *
 * La corrección real: cada [GalleryFilmstripThumbnail] reporta su propio
 * rango horizontal EXACTO en pantalla (`itemBounds`, vía
 * `onGloballyPositioned`, medido relativo a este mismo contenedor —
 * `filmstripCoordinates`), y el gesto de arrastre ([detectFilmstripScrubGesture]
 * acá abajo) reporta la posición X ABSOLUTA del dedo en cada frame —
 * nunca un delta. En cada frame se hace hit-test matemático directo:
 * ¿en el rango de qué miniatura cae la posición X actual del dedo? Esa
 * es, sin aproximaciones, la miniatura que debe seleccionarse — el marco
 * queda sincronizado con el dedo con precisión de píxel, exactamente
 * igual que el tap directo, solo que sin necesidad de soltar entre una
 * miniatura y la siguiente.
 *
 * Ambas formas de navegar conviven sin pisarse: el gesto de scrub no
 * consume nada hasta superar el umbral de movimiento (touch slop) del
 * sistema, así que un toque corto sin arrastre sigue llegando intacto al
 * `clickable` de la miniatura tocada; recién al superar el slop, el
 * scrub pasa a consumir el resto del arrastre y el tap subyacente queda
 * cancelado (mismo criterio que Compose usa en cualquier par
 * gesto-corto/gesto-largo, ver también [detectZoomAwarePanGesture] más
 * abajo en este archivo para la imagen en pantalla completa).
 */
@Composable
private fun GalleryFilmstrip(
    items: List<FilePreviewItem>,
    currentIndex: Int,
    onThumbnailClick: (Int) -> Unit,
    onScrub: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // El gesto de scrub vive en un `pointerInput` de vida larga (key fija
    // = cantidad de ítems, que no cambia durante la sesión del visor); por
    // eso necesita leer SIEMPRE el índice más reciente vía
    // `rememberUpdatedState` en lugar del parámetro `currentIndex`
    // capturado el día que ese gesto arrancó — si no, el scrub navegaría
    // siempre a partir de la foto con la que se abrió la galería, sin
    // enterarse de los saltos posteriores.
    val latestIndex = rememberUpdatedState(currentIndex)

    // Coordenadas del contenedor del filmstrip (este mismo
    // `BoxWithConstraints`) y rango horizontal real, EN ESE MISMO SISTEMA
    // DE COORDENADAS, de cada miniatura visible — la base matemática del
    // hit-test de arriba. Es una `Map` mutable simple (no `State`
    // observable de Compose) a propósito: se escribe desde
    // `onGloballyPositioned` (fuera de la fase de composición) y se lee
    // desde la corrutina del gesto, nunca desde código que deba
    // recomponerse al cambiar — usar `mutableStateMapOf` acá dispararía
    // una recomposición del filmstrip completo en cada medición de
    // layout, sin ningún beneficio.
    var filmstripCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val itemBounds = remember { mutableMapOf<Int, ClosedFloatingPointRange<Float>>() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.72f))))
            .padding(vertical = 14.dp)
            .onGloballyPositioned { filmstripCoordinates = it }
            .pointerInput(items.size) {
                detectFilmstripScrubGesture { fingerX ->
                    // Hit-test matemático directo contra los rangos reales
                    // medidos por cada miniatura — sin acumuladores, sin
                    // pasos, sin asunciones sobre el ancho de cada ítem.
                    val bounds = itemBounds
                    if (bounds.isEmpty()) return@detectFilmstripScrubGesture

                    val exactMatch = bounds.entries.firstOrNull { (_, range) -> fingerX in range }
                    val target = exactMatch?.key ?: run {
                        // El dedo se arrastró más allá del borde izquierdo
                        // o derecho de la última miniatura medida (p. ej.
                        // sobre el padding del contenedor, o sobre una
                        // miniatura aún no compuesta fuera de la ventana
                        // visible del `LazyRow`): se clampa a la más
                        // cercana por distancia real al centro de su
                        // rango — nunca se pierde el seguimiento del dedo.
                        bounds.minByOrNull { (_, range) ->
                            val center = (range.start + range.endInclusive) / 2f
                            abs(center - fingerX)
                        }?.key
                    }

                    if (target != null && target != latestIndex.value) onScrub(target)
                }
            }
    ) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val selectedThumbPx = with(density) { FILMSTRIP_THUMB_SIZE_SELECTED.toPx() }

        // Centra la miniatura seleccionada dentro del ancho visible del
        // filmstrip — un scrollOffset negativo "retrocede" desde el
        // inicio del ítem objetivo lo suficiente para que quede a mitad
        // de la pantalla en vez de pegado al borde izquierdo.
        LaunchedEffect(currentIndex, viewportPx) {
            if (viewportPx <= 0f) return@LaunchedEffect
            val centeringOffset = -((viewportPx - selectedThumbPx) / 2f).toInt()
            listState.animateScrollToItem(index = currentIndex, scrollOffset = centeringOffset)
        }

        LazyRow(
            state = listState,
            userScrollEnabled = false, // el desplazamiento por esta fila lo conduce el scrub de arriba + el autocentrado; un scroll libre independiente del índice actual permitiría "perder" la miniatura seleccionada fuera de vista sin ningún control sincronizado con el visor.
            // Alineación INFERIOR, no la del default (`Alignment.Top`) de
            // `LazyRow`. Con el default, todas las miniaturas anclan su
            // borde SUPERIOR a la fila, así que al agrandarse la
            // seleccionada ([FILMSTRIP_THUMB_SIZE] → [FILMSTRIP_THUMB_SIZE_SELECTED])
            // el borde que se movía era el INFERIOR — el crecimiento se
            // sentía "hacia abajo". Con el borde INFERIOR anclado en su
            // lugar, es el borde SUPERIOR el que sube al agrandarse — el
            // mismo criterio que cualquier filmstrip/selector de fotos
            // profesional (selector nativo de Android, Google Fotos): la
            // miniatura activa "crece hacia arriba" desde la fila, nunca
            // hacia abajo.
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(FILMSTRIP_SPACING),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items, key = { _, item -> item.file.absolutePath }) { index, item ->
                GalleryFilmstripThumbnail(
                    item = item,
                    isSelected = index == currentIndex,
                    onClick = { onThumbnailClick(index) },
                    modifier = Modifier.onGloballyPositioned { thumbCoordinates ->
                        // Rango horizontal REAL de esta miniatura, medido
                        // relativo al contenedor del filmstrip — el mismo
                        // sistema de coordenadas en el que llega `fingerX`
                        // desde `detectFilmstripScrubGesture` arriba.
                        // `localPositionOf` resuelve la posición exacta
                        // sin importar el offset de scroll actual del
                        // `LazyRow` ni el padding aplicado: es la fuente
                        // de verdad, no una aproximación por índice.
                        val container = filmstripCoordinates ?: return@onGloballyPositioned
                        val topLeft = container.localPositionOf(thumbCoordinates, Offset.Zero)
                        val width = thumbCoordinates.size.width.toFloat()
                        if (width > 0f) itemBounds[index] = topLeft.x..(topLeft.x + width)
                    }
                )
            }
        }
    }
}

/**
 * Tap simple instantáneo + doble-tap para zoom sobre la imagen del
 * visor (ver el único punto de uso, [ZoomableGalleryImage]) — reemplaza a
 * [androidx.compose.foundation.gestures.detectTapGestures].
 *
 * ## El bug que corrige
 * Cuando a `detectTapGestures` se le pasan `onTap` Y `onDoubleTap`
 * juntos, Compose RETIENE a propósito cada toque durante la ventana
 * estándar de doble-tap de Android (300ms —
 * [DOUBLE_TAP_TIMEOUT_MILLIS]) antes de decidir si llamar a `onTap`:
 * necesita ese tiempo para saber si lo que está viendo es un toque
 * simple o la primera mitad de un doble-tap. Ese retardo, sumado a los
 * ~220ms de fade de [chromeVisible] ([CHROME_FADE_MS]), es exactamente
 * el delay perceptible reportado al mostrar/ocultar el encabezado y el
 * filmstrip con un solo toque en la imagen: la experiencia se sentía con
 * "lag", no instantánea.
 *
 * ## La solución
 * [onTap] se dispara EN EL ACTO, en el instante en que el dedo se
 * levanta — nunca se espera a ver si viene un segundo toque. El
 * doble-tap se detecta por separado, comparando el `uptimeMillis` (y la
 * posición) de ESTE levantamiento contra el del anterior: si cae dentro
 * de [DOUBLE_TAP_TIMEOUT_MILLIS] y suficientemente cerca en pantalla, se
 * dispara [onDoubleTap] ADEMÁS del [onTap] que ya se disparó con el
 * primer toque. Es el mismo comportamiento que cualquier visor de fotos
 * profesional (Google Fotos, Apple Fotos): el primer toque siempre
 * reacciona al instante, y un segundo toque rápido simplemente agrega la
 * acción de zoom encima — nunca hay nada que esperar.
 *
 * Un arrastre que supera el touch slop del sistema (foto que se
 * desliza, pinch-zoom que ya consumió el evento en
 * [detectZoomAwarePanGesture]) no cuenta como tap ni como doble-tap.
 */
private suspend fun PointerInputScope.detectTapAndDoubleTapImmediate(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit
) {
    // Estado del ÚLTIMO toque exitoso, persistido ENTRE gestos (fuera del
    // `awaitEachGesture`) — así se puede comparar el toque actual contra
    // el anterior para reconocer el doble-tap.
    var lastTapUpTimeMillis = -1L
    var lastTapUpPosition = Offset.Zero
    val doubleTapPositionSlop = viewConfiguration.touchSlop * 4f

    awaitEachGesture {
        val slop = viewConfiguration.touchSlop
        // `lastChange` se actualiza en cada vuelta del loop — al salir de
        // él, es el evento EXACTO del levantamiento del dedo (o el último
        // visto antes de cancelarse), nunca el de la bajada inicial.
        var lastChange: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
        var totalMotion = 0f
        var pastSlop = false
        var released = false
        var canceled = false

        do {
            val event = awaitPointerEvent()
            val change = event.changes.first()
            lastChange = change
            canceled = change.isConsumed
            if (!canceled) {
                if (!pastSlop) {
                    totalMotion += change.positionChange().getDistance()
                    if (totalMotion > slop) pastSlop = true
                }
                if (!change.pressed) released = true
            }
        } while (!canceled && !released && event.changes.any { it.pressed })

        if (released && !pastSlop && !canceled) {
            val upTimeMillis = lastChange.uptimeMillis
            val upPosition = lastChange.position
            val isDoubleTap = lastTapUpTimeMillis >= 0L &&
                (upTimeMillis - lastTapUpTimeMillis) < DOUBLE_TAP_TIMEOUT_MILLIS &&
                (upPosition - lastTapUpPosition).getDistance() < doubleTapPositionSlop

            onTap()
            if (isDoubleTap) {
                onDoubleTap()
                // Se reinicia para que un tercer toque rápido no se lea
                // como "doble-tap de un doble-tap anterior" — arranca de
                // cero, como cualquier gesto nuevo.
                lastTapUpTimeMillis = -1L
            } else {
                lastTapUpTimeMillis = upTimeMillis
                lastTapUpPosition = upPosition
            }
        }
    }
}

/**
 * Gesto de arrastre horizontal para el scrub del filmstrip (ver
 * [GalleryFilmstrip]). No consume nada hasta superar el touch slop del
 * sistema — así un tap corto sigue llegando intacto al `clickable` de la
 * miniatura tocada.
 *
 * A diferencia de la versión anterior (que reportaba un DELTA de
 * arrastre para que el llamador lo acumulara por pasos abstractos — ver
 * el KDoc de [GalleryFilmstrip], sección "FASE 18"), esta reporta en
 * [onPositionChange] la posición X ABSOLUTA del dedo en cada movimiento,
 * en el sistema de coordenadas del propio elemento con `pointerInput`
 * (mismo sistema que [LayoutCoordinates.localPositionOf] usa para medir
 * el rango de cada miniatura) — la única forma de que el hit-test contra
 * posiciones reales sea posible.
 */
private suspend fun PointerInputScope.detectFilmstripScrubGesture(onPositionChange: (fingerX: Float) -> Unit) {
    awaitEachGesture {
        var totalDrag = 0f
        var pastSlop = false
        val slop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val change = event.changes.first()
                if (!pastSlop) {
                    totalDrag += change.positionChange().getDistance()
                    if (totalDrag > slop) pastSlop = true
                }
                if (pastSlop) {
                    onPositionChange(change.position.x)
                    change.consume()
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

/**
 * Radio de esquina, ancho de marco y elevación de cada miniatura del
 * filmstrip (ver [GalleryFilmstripThumbnail]).
 *
 * **Historial de diseño del recorte de la imagen (FASE 17 → revisión
 * actual):** la versión anterior usaba `ContentScale.Crop` para llenar
 * el 100% del cuadrado — evitaba el marco "flotante sin fondo" de un
 * intento todavía más viejo, pero traía su propio problema: recortaba la
 * imagen completa (cabeza/pies de un personaje, por ejemplo) para llenar
 * el cuadrado, viéndose la miniatura "cortada" — reporte explícito del
 * cliente comparando contra el selector nativo de fotos de Android (su
 * captura de referencia: cada miniatura muestra la imagen COMPLETA,
 * dentro de un cuadro gris/letterbox cuando la proporción no calza,
 * nunca recortada). La corrección real es `ContentScale.Fit`: la imagen
 * completa siempre visible, con [NeutralChromeGray] (ver más abajo) de
 * fondo rellenando el espacio sobrante — el mismo fondo que ya existía
 * para el estado de carga, ahora también visible como letterbox
 * permanente cuando la proporción de la imagen no es cuadrada.
 *
 * Fondo/marco (sigue vigente, sin cambios en esta revisión): antes cada
 * miniatura tenía fondo (`SurfaceTintedDark`) y esquinas redondeadas,
 * pero como la foto llenaba el 100% del cuadrado, ese fondo nunca
 * llegaba a verse: contra el negro del visor, cada miniatura se leía
 * como una imagen plana "flotando" sin ningún borde — el reporte
 * original de diseño ("se ven cuadradas pero sin marco").
 *
 * Primer intento (FASE 17) — corregido acá tras una segunda revisión de
 * diseño con capturas reales en dispositivo: se usó `SurfaceTintedElevated`
 * (paleta de MARCA morado→azul) para ese marco. Resultado: cada miniatura
 * se veía "teñida del morado/azul de la app" — el reporte pasó de "sin
 * marco" a "con un marco del color equivocado". La corrección real es
 * [NeutralChromeGray] (ver `Theme.kt`): un gris neutro FUERA de la
 * paleta de marca, mismo criterio que cualquier visor de fotos
 * profesional (Google Fotos, Apple Fotos) — su chrome de navegación
 * nunca usa el color corporativo de la empresa, para no competir con la
 * foto de la persona que se está mostrando.
 */
private val FILMSTRIP_THUMB_CORNER_RADIUS = 10.dp
private val FILMSTRIP_THUMB_BORDER_WIDTH = 1.5.dp
private val FILMSTRIP_THUMB_BORDER_WIDTH_SELECTED = 2.5.dp
private val FILMSTRIP_THUMB_ELEVATION = 3.dp
private val FILMSTRIP_THUMB_ELEVATION_SELECTED = 8.dp

@Composable
private fun GalleryFilmstripThumbnail(
    item: FilePreviewItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val size by animateDpAsState(
        targetValue = if (isSelected) FILMSTRIP_THUMB_SIZE_SELECTED else FILMSTRIP_THUMB_SIZE,
        label = "filmstrip_thumb_size"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) FILMSTRIP_THUMB_BORDER_WIDTH_SELECTED else FILMSTRIP_THUMB_BORDER_WIDTH,
        label = "filmstrip_thumb_border_width"
    )
    // [NeutralChromeGray] en reposo — gris neutro, fuera de la paleta de
    // marca (ver el comentario de arriba y `Theme.kt`). El acento morado
    // ([BrandPurpleLight]) queda reservado, como en el resto de la app,
    // para marcar cuál es la foto actual — un solo elemento con el color
    // de marca, no todas las miniaturas.
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) BrandPurpleLight else NeutralChromeGray,
        label = "filmstrip_thumb_border_color"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) FILMSTRIP_THUMB_ELEVATION_SELECTED else FILMSTRIP_THUMB_ELEVATION,
        label = "filmstrip_thumb_elevation"
    )
    val shape = RoundedCornerShape(FILMSTRIP_THUMB_CORNER_RADIUS)

    Box(
        modifier = modifier
            .size(size)
            // La sombra va ANTES del `.clip()` — a propósito: `.shadow()`
            // necesita ver la forma completa (incluida el área que el
            // clip recorta) para proyectar el degradado hacia afuera de
            // los bordes; si fuera después del `.clip()`, la sombra
            // quedaría recortada junto con el contenido y no se vería.
            .shadow(elevation = elevation, shape = shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            // Mismo [NeutralChromeGray] de relleno mientras `AsyncImage`
            // carga — así el placeholder de carga tampoco se ve "teñido
            // de app", consistente con el marco.
            .background(NeutralChromeGray)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.file,
            contentDescription = item.fileName,
            // `Fit`, NO `Crop` — la imagen COMPLETA siempre visible (ver
            // el comentario de [FILMSTRIP_THUMB_CORNER_RADIUS] arriba
            // sobre por qué se abandonó `Crop`). El espacio sobrante
            // cuando la proporción de la imagen no es cuadrada lo llena
            // el `.background(NeutralChromeGray)` del Box padre, que
            // queda visible detrás como letterbox.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Ya NO se oscurece la miniatura no seleccionada (antes: capa
        // negra al 38% de alpha encima). Esa oscurecida, sumada a la
        // falta de marco de arriba, era la otra mitad del mismo reporte
        // ("que no se pierdan en el color" — se veían apagadas contra el
        // fondo negro). El tamaño (ver [FILMSTRIP_THUMB_SIZE_SELECTED]) y
        // el marco de acento de arriba ya distinguen sin ambigüedad cuál
        // es la foto actual, sin necesidad de apagar el resto.
    }
}

/**
 * Gesto combinado pinch-zoom + paneo para una imagen que vive DENTRO de
 * un [HorizontalPager] — variante de
 * [androidx.compose.foundation.gestures.detectTransformGestures] que
 * decide, gesto por gesto, si consumir el arrastre de un solo dedo:
 *
 * - Dos dedos (pinch real) → SIEMPRE se consume acá: zoom, sin importar
 *   la escala actual de la imagen.
 * - Un dedo, con la imagen YA ampliada (`scaleProvider() > 1x`) → se
 *   consume acá: panea la imagen ampliada.
 * - Un dedo, con la imagen a 1x (sin zoom) → NO se consume: el evento
 *   sigue de largo hacia el [HorizontalPager] que envuelve esta imagen,
 *   que es quien lo necesita para deslizar a la foto siguiente/anterior.
 *
 * Sin esta distinción, cualquier imagen sin zoom "atraparía" el arrastre
 * de un solo dedo y el carrusel de fotos nunca respondería al swipe — el
 * bug clásico de "zoomable image dentro de un pager" en Compose.
 */
private suspend fun PointerInputScope.detectZoomAwarePanGesture(
    scaleProvider: () -> Float,
    onGesture: (pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        var zoomAccum = 1f
        var panAccum = Offset.Zero
        var pastSlop = false
        val slop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val isMultiTouch = event.changes.size > 1

                if (!pastSlop) {
                    zoomAccum *= zoomChange
                    panAccum += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1f - zoomAccum) * centroidSize
                    val panMotion = panAccum.getDistance()
                    if (zoomMotion > slop || panMotion > slop) pastSlop = true
                }

                if (pastSlop && (isMultiTouch || scaleProvider() > MIN_IMAGE_SCALE + 0.01f)) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                // Si ya se pasó el slop pero es un solo dedo Y la imagen
                // sigue a 1x: deliberadamente no se consume nada acá — el
                // gesto sigue de largo hacia el HorizontalPager.
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

/**
 * Una página del carrusel de [ImageGalleryDialogContent]: imagen con
 * zoom/pan real (pellizcar, doble tap 1x↔2.5x) coordinada con el pager
 * (ver [detectZoomAwarePanGesture]), más un tap simple que alterna el
 * modo inmersivo vía [onTap] — disparado AL INSTANTE, sin el retardo de
 * ~300ms que introduciría `detectTapGestures` al combinar `onTap` con
 * `onDoubleTap` (ver [detectTapAndDoubleTapImmediate]). [onZoomStateChanged]
 * avisa al carrusel cada vez que esta página entra o sale de zoom, para
 * que el pager sepa si debe dejarse deslizar o no.
 */
@Composable
private fun ZoomableGalleryImage(
    file: File,
    onTap: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit
) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Offset.Zero) }
    var loadState by remember(file) { mutableStateOf<AsyncImagePainter.State?>(null) }

    LaunchedEffect(scale) { onZoomStateChanged(scale > MIN_IMAGE_SCALE + 0.01f) }

    fun coerceOffset(newOffset: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f) return Offset.Zero
        val maxX = (containerSize.x * (currentScale - 1f)) / 2f
        val maxY = (containerSize.y * (currentScale - 1f)) / 2f
        return Offset(newOffset.x.coerceIn(-maxX, maxX), newOffset.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size -> containerSize = Offset(size.width.toFloat(), size.height.toFloat()) }
            .pointerInput(file) {
                detectZoomAwarePanGesture(scaleProvider = { scale }) { pan, zoom ->
                    val newScale = (scale * zoom).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
                    scale = newScale
                    // `pan` ya viene en píxeles de pantalla reales (delta del
                    // dedo). `offset` se aplica como `translationX/Y` en un
                    // `graphicsLayer` cuyo `scaleX/Y` ya escala la imagen
                    // alrededor de su centro ANTES de trasladarla — la
                    // traslación de Compose ocurre en el espacio de pantalla,
                    // no en el espacio pre-escala del contenido. Multiplicar
                    // `pan` por `newScale` (como antes) amplificaba cada
                    // movimiento del dedo en proporción directa al zoom
                    // (hasta 6x en MAX_IMAGE_SCALE), mientras que el límite en
                    // `coerceOffset` está calculado en esos mismos píxeles de
                    // pantalla sin ese multiplicador — el offset chocaba
                    // contra el clamp casi de inmediato, sintiéndose como un
                    // salto/rebote en vez de un arrastre 1:1 con el dedo.
                    offset = coerceOffset(offset + pan, newScale)
                }
            }
            .pointerInput(file) {
                detectTapAndDoubleTapImmediate(
                    onTap = { onTap() },
                    onDoubleTap = {
                        val target = if (scale > MIN_IMAGE_SCALE + 0.01f) MIN_IMAGE_SCALE else DOUBLE_TAP_SCALE
                        scale = target
                        offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { loadState = it },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        when (loadState) {
            is AsyncImagePainter.State.Loading, null ->
                CircularProgressIndicator(color = BrandPurpleLight)
            is AsyncImagePainter.State.Error ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_image_placeholder), null, tint = Color(0xFF8A7DB8), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No se pudo cargar la imagen", color = Color(0xFF8A7DB8), fontSize = 12.sp)
                }
            else -> Unit
        }
    }
}

// ============================================================================
// JSON / texto plano — lectura en IO, resaltado de sintaxis básico para
// JSON (claves, strings, números, booleanos/null, puntuación), selección y
// copiado real de texto (SelectionContainer), igual que un editor de
// código liviano.
//
// FASE 20 — reescritura real del renderizado por lag/retardo reportado al
// navegar/scrollear un JSON. Causa raíz real (no una suposición): la
// versión anterior renderizaba el archivo ENTERO como un único `Text` con
// UN SOLO `AnnotatedString` gigante — para un `project.json` de 16.5 KB
// con resaltado de sintaxis eso son varios miles de `SpanStyle`
// individuales (uno por token: cada clave, string, número, coma, corchete,
// espacio) en un solo nodo de layout. Compose no tiene forma de
// virtualizar ESO: con `verticalScroll` (no `LazyColumn`), el nodo entero
// se mide y se pinta completo sin importar cuánto esté visible en
// pantalla, y encima `SelectionContainer` tiene que poder hacer hit-test
// de selección contra ese mismo nodo gigante en cada gesto — el costo por
// frame escala con el tamaño TOTAL del archivo, no con lo que se ve.
//
// La corrección real es virtualizar por línea: el archivo se divide en
// líneas ANTES de llegar a Compose (`splitAnnotatedStringIntoLines`, ver
// abajo, corre en Dispatchers.IO igual que el resto de `loadTextPreview`)
// y cada línea es su propio `Text` dentro de un `LazyColumn` — así Compose
// solo mide/pinta las líneas realmente visibles en pantalla,
// sin importar si el archivo tiene 50 líneas o 50.000. El scroll
// horizontal (necesario porque una línea de JSON con mucha indentación
// puede ser más ancha que la pantalla) ya no puede depender de medir el
// ancho real de cada línea —eso otra vez obligaría a medir el archivo
// entero, texto por línea, arruinando la virtualización que se acaba de
// ganar—, así que se calcula analíticamente: la fuente es monoespaciada a
// tamaño fijo, así que el ancho de UN carácter medido una sola vez
// (`rememberTextMeasurer`) alcanza para saber el ancho total de la línea
// más larga con una simple multiplicación, sin medir cada línea.
// ============================================================================

internal const val MAX_TEXT_PREVIEW_BYTES = 2_000_000L // 2 MB — de ahí para arriba se trunca, no tiene sentido cargar un log entero a memoria solo para "ver qué es"
private const val MAX_SYNTAX_HIGHLIGHT_CHARS = 300_000 // resaltar JSON gigante de más frena la carga inicial sin agregar nada útil a la lectura

/**
 * [displayLines] es la unidad real que consume [TextualPreviewContent]: una
 * entrada por línea del archivo, ya resaltada — nunca un único
 * `AnnotatedString` con el archivo entero (ver el comentario de cabecera de
 * esta sección sobre por qué eso fue la causa real del lag reportado).
 * [longestLineChars] es el largo (en caracteres) de la línea más larga de
 * TODO el archivo — se calcula acá, en el mismo paso de I/O, para que la UI
 * pueda dimensionar el ancho del scroll horizontal con una sola
 * multiplicación en vez de tener que medir línea por línea (ver
 * [TextualPreviewContent]).
 */
internal data class TextPreviewResult(
    val displayLines: List<AnnotatedString>,
    val longestLineChars: Int,
    val rawText: String,
    val truncated: Boolean,
    val error: String?
)

@Composable
private fun TextualPreviewContent(file: File, sizeBytes: Long, isJson: Boolean, onCopyAll: (String) -> Unit) {
    var result by remember(file) { mutableStateOf<TextPreviewResult?>(null) }

    LaunchedEffect(file) {
        result = withContext(Dispatchers.IO) { loadTextPreview(file, sizeBytes, isJson) }
    }

    val current = result
    if (current == null) {
        CircularProgressIndicator(color = BrandPurpleLight)
        return
    }

    // Ancho de UN carácter en la tipografía monoespaciada real del visor
    // (misma familia/tamaño que el `Text` de cada línea, más abajo) —
    // medido una sola vez con `TextMeasurer` y cacheado mientras dure la
    // composición, nunca recalculado por línea ni por frame.
    val textMeasurer = rememberTextMeasurer()
    val monospaceCharWidthPx = remember(textMeasurer) {
        textMeasurer.measure(text = "0", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp)).size.width.toFloat()
    }
    val listState = remember(file) { LazyListState() }
    val horizontalScrollState = remember(file) { ScrollState(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (current.truncated) {
                Text(
                    "Archivo grande — mostrando solo los primeros ${formatFileSize(MAX_TEXT_PREVIEW_BYTES)}",
                    color = Color(0xFFE0A83C),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().background(SurfaceTintedDark).padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            if (current.error != null) {
                Text(
                    current.error,
                    color = Color(0xFFE05C5C),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().background(SurfaceTintedDark).padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            SelectionContainer(modifier = Modifier.fillMaxSize().background(SurfaceTintedDark)) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Ancho real del contenido = el de la línea más larga del
                    // archivo + el padding horizontal de 14dp por lado del
                    // propio `LazyColumn` de abajo — sin sumar ese padding acá,
                    // el ancho fijo le restaría 28dp reales al espacio
                    // disponible para el texto y la línea más larga quedaría
                    // recortada por la derecha. +4 caracteres de margen extra:
                    // "monoespaciada" en la práctica puede no ser exacta al
                    // 100% para cada glifo (acentos, emoji) — un pequeño
                    // colchón cuesta unos px de scroll vacío de más, mucho
                    // más barato que arriesgar recortar el final de la línea
                    // más larga del archivo. Nunca menor al ancho visible de
                    // la pantalla (para que el fondo/la selección cubran todo
                    // el ancho aunque el JSON tenga líneas cortas).
                    val contentWidth = with(LocalDensity.current) {
                        maxOf((monospaceCharWidthPx * (current.longestLineChars + 4)).toDp() + 28.dp, maxWidth)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .horizontalScroll(horizontalScrollState)
                            .width(contentWidth)
                            .fillMaxHeight()
                            .padding(14.dp)
                    ) {
                        itemsIndexed(current.displayLines) { _, line ->
                            Text(
                                line,
                                color = Color(0xFFD6CFEF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Botón flotante "Copiar todo" — SelectionContainer ya permite
        // seleccionar a mano, esto cubre el caso común de querer el
        // archivo entero en el portapapeles sin tener que arrastrar el
        // dedo por un JSON de cientos de líneas.
        IconButton(
            onClick = { onCopyAll(current.rawText) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BrandPurpleLight)
        ) {
            Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copiar todo", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Lee y prepara [file] para su vista previa como texto/JSON — I/O pura, sin Compose. `internal` a propósito: ver [com.yeivikas.olyzecs.ui.TextPreviewLoadingTest]. */
internal fun loadTextPreview(file: File, sizeBytes: Long, isJson: Boolean): TextPreviewResult {
    val truncated = sizeBytes > MAX_TEXT_PREVIEW_BYTES
    val limit = if (truncated) MAX_TEXT_PREVIEW_BYTES else sizeBytes
    // Lee como MUCHO `limit` bytes DESDE EL STREAM — nunca
    // `file.readBytes()` completo seguido de un recorte en memoria, que
    // volvería inútil el límite de arriba frente a un archivo realmente
    // gigante (un log de varios GB igual se cargaría entero antes de
    // descartar el resto).
    val rawBytes = runCatching {
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(limit.toInt())
            var totalRead = 0
            while (totalRead < buffer.size) {
                val n = input.read(buffer, totalRead, buffer.size - totalRead)
                if (n == -1) break
                totalRead += n
            }
            if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
        }
    }.getOrElse { t ->
        AppLogger.e(TAG, "No se pudo leer '${file.name}' para la vista previa de texto", t)
        return TextPreviewResult(listOf(AnnotatedString("")), 0, "", truncated = false, error = "No se pudo leer el archivo")
    }
    val raw = String(rawBytes, Charsets.UTF_8)

    if (!isJson) {
        return textPreviewResultFor(AnnotatedString(raw), raw, truncated, error = null)
    }

    // JSON: se intenta re-formatear con pretty-print real (no solo
    // resaltar el texto crudo tal cual venía guardado) — si el archivo
    // está truncado o corrupto, un JSON parcial no parsea, y ahí se cae
    // con gracia al texto crudo resaltado igual, en vez de mostrar un
    // error y dejar al usuario sin nada que leer.
    val prettyPrinter = Json { prettyPrint = true; prettyPrintIndent = "  " }
    val formatted = if (!truncated) {
        runCatching { prettyPrinter.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw)) }.getOrNull()
    } else null

    val textToHighlight = formatted ?: raw
    val parseError = if (formatted == null && !truncated) "JSON inválido — mostrando el contenido crudo" else null

    val annotated = if (textToHighlight.length <= MAX_SYNTAX_HIGHLIGHT_CHARS) {
        highlightJson(textToHighlight)
    } else {
        AnnotatedString(textToHighlight)
    }
    return textPreviewResultFor(annotated, textToHighlight, truncated, parseError)
}

/** Arma el [TextPreviewResult] final a partir del texto ya resaltado — parte en líneas (ver [splitAnnotatedStringIntoLines]) y calcula el largo de la línea más larga, los dos datos que necesita el `LazyColumn` virtualizado de [TextualPreviewContent] para renderizar rápido sin importar el tamaño del archivo. */
private fun textPreviewResultFor(annotated: AnnotatedString, rawText: String, truncated: Boolean, error: String?): TextPreviewResult {
    val lines = splitAnnotatedStringIntoLines(annotated)
    val longestLineChars = lines.maxOfOrNull { it.length } ?: 0
    return TextPreviewResult(lines, longestLineChars, rawText, truncated, error)
}

/**
 * Divide un [AnnotatedString] ya resaltado en líneas individuales,
 * preservando en cada una los [SpanStyle] que le correspondan —
 * `AnnotatedString.subSequence` ya recorta los spans correctamente al
 * rango pedido, no hace falta reimplementar ese recorte a mano. Mismo
 * criterio de "\n" que `String.lines()` de Kotlin (una línea vacía al
 * final si el texto termina en salto de línea), para que reconstruir con
 * `displayLines.joinToString("\n")` reproduzca el texto original exacto.
 * `internal` (no `private`): función pura testeable, mismo criterio que
 * [highlightJson] — ver [com.yeivikas.olyzecs.ui.TextPreviewLineSplitTest].
 */
internal fun splitAnnotatedStringIntoLines(text: AnnotatedString): List<AnnotatedString> {
    if (text.isEmpty()) return listOf(AnnotatedString(""))
    val lines = mutableListOf<AnnotatedString>()
    var lineStart = 0
    for (i in 0 until text.length) {
        if (text.text[i] == '\n') {
            lines.add(text.subSequence(lineStart, i))
            lineStart = i + 1
        }
    }
    lines.add(text.subSequence(lineStart, text.length))
    return lines
}

// --- Resaltado de sintaxis JSON ---------------------------------------------

private val JSON_TOKEN_REGEX = Regex(
    "\"(?:\\\\.|[^\"\\\\])*\"" + "|" + // strings (con escapes)
        "-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?" + "|" + // números
        "true|false|null" + "|" + // literales
        "[{}\\[\\],:]" + "|" + // puntuación estructural
        "\\s+" // espacios/saltos de línea (se preservan tal cual)
)

internal val JsonKeyColor = BrandPurpleLight
internal val JsonStringColor = Color(0xFF7FD9A6)
internal val JsonNumberColor = Color(0xFFE0A83C)
internal val JsonLiteralColor = Color(0xFF5B8DEF)
internal val JsonPunctColor = Color(0xFF8A7DB8)
internal val JsonDefaultColor = Color(0xFFD6CFEF)

/** Resalta JSON ya formateado token por token: claves en un color, strings-valor en otro, números/booleanos/null/puntuación cada uno con el suyo. `internal` (no `private`) a propósito — ver [com.yeivikas.olyzecs.ui.JsonSyntaxHighlightTest], igual criterio que ya usa `AlignmentGuides.kt` para su lógica pura testeable. */
internal fun highlightJson(text: String): AnnotatedString {
    data class Token(val text: String, val start: Int)
    val tokens = mutableListOf<Token>()
    var lastEnd = 0
    for (match in JSON_TOKEN_REGEX.findAll(text)) {
        if (match.range.first > lastEnd) tokens.add(Token(text.substring(lastEnd, match.range.first), lastEnd))
        tokens.add(Token(match.value, match.range.first))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) tokens.add(Token(text.substring(lastEnd), lastEnd))

    fun isWhitespace(t: String) = t.isNotEmpty() && t.all { it.isWhitespace() }
    fun nextNonWhitespace(fromIndex: Int): String? {
        var i = fromIndex + 1
        while (i < tokens.size && isWhitespace(tokens[i].text)) i++
        return tokens.getOrNull(i)?.text
    }

    return buildAnnotatedString {
        tokens.forEachIndexed { index, token ->
            val t = token.text
            val color = when {
                isWhitespace(t) -> null
                t.startsWith('"') -> if (nextNonWhitespace(index) == ":") JsonKeyColor else JsonStringColor
                t == "true" || t == "false" || t == "null" -> JsonLiteralColor
                t.length == 1 && t[0] in "{}[],:" -> JsonPunctColor
                t.toDoubleOrNull() != null -> JsonNumberColor
                else -> JsonDefaultColor
            }
            if (color == null) {
                append(t)
            } else {
                withStyle(SpanStyle(color = color, fontWeight = if (color == JsonKeyColor) FontWeight.SemiBold else FontWeight.Normal)) {
                    append(t)
                }
            }
        }
    }
}

// ============================================================================
// Audio — telón de fondo estático (FASE 18) + reproductor flotante real.
//
// Antes de FASE 18 esto era UN solo composable: una ficha centrada de
// pantalla completa con ícono, nombre, `Slider` de Material y un botón
// de play/pausa grande — funcional, pero clavada en el centro de la
// pantalla, sin poder moverse de ahí. A pedido explícito de diseño (con
// referencia visual: una pastilla delgada tipo "mini-reproductor" de
// sistema operativo — nota musical + barra + tiempo + play + cerrar,
// TODO en una sola fila angosta) se separó en dos piezas:
//
//   1. [AudioPreviewBackdrop] — de acá para abajo: solo el glifo grande
//      y el nombre de archivo, decorativo, SIN ningún control. Ocupa el
//      mismo lugar donde antes vivía la ficha completa (el `Box` con
//      `weight(1f)` de `SingleFilePreviewScaffold`).
//   2. [FloatingAudioPlayerPill] — la pastilla real, con el MediaPlayer y
//      TODA la interacción (play/pausa, arrastrar para buscar, cerrar).
//      Se instancia como HERMANA de la `Column` de cabecera+contenido en
//      `SingleFilePreviewScaffold` (no adentro de ella) — así puede
//      arrastrarse LIBREMENTE por toda la superficie del visor, cabecera
//      incluida, en vez de quedar encerrada bajo el `Box` de contenido.
//
// El motor de reproducción es EXACTAMENTE el mismo de antes de esta fase
// (un `MediaPlayer` propio, armado en `Dispatchers.IO` — sin ninguna
// dependencia nueva): lo único que cambió es la cáscara visual y que
// ahora vive en una posición arrastrable en vez de fija. Alcance
// deliberado de esta fase: el reproductor sigue atado al ciclo de vida
// de ESTE visor (se libera con el mismo `DisposableEffect` de siempre al
// cerrarlo) — no sobrevive a navegar a otra pantalla ni corre en un
// `Service` en segundo plano; eso sería una pieza de arquitectura
// bastante más grande (notificación de medios, foreground service,
// `MediaSession`) que no se pidió y que se dejó fuera a propósito de
// este cambio en vez de improvisarla a medias.
// ============================================================================

private val AUDIO_PILL_WIDTH = 296.dp
private val AUDIO_PILL_HEIGHT = 56.dp
private val AUDIO_PILL_CORNER = AUDIO_PILL_HEIGHT / 2
private val AUDIO_PILL_DEFAULT_MARGIN = 16.dp
private val AUDIO_PILL_SEEK_TRACK_HEIGHT = 3.dp

/** Telón de fondo del visor de audio — sin controles, ver comentario de arriba. */
@Composable
private fun AudioPreviewBackdrop(fileName: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)).background(SurfaceTintedElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(fileName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Recorta [value] contra `[0, containerSizePx - itemSizePx]` — mismo
 * criterio exacto de `clampToSideLimits`/`clampToBottomLimit` en
 * `FloatingToolWindow` (EditorScreen.kt): si el contenedor todavía no se
 * midió (0, primer frame antes de que `onSizeChanged` dispare), no
 * recorta nada — evita forzar la pastilla a `(0,0)` por error mientras
 * `containerSizePx` sigue en su valor inicial.
 */
private fun clampPillAxis(value: Float, itemSizePx: Float, containerSizePx: Int): Float {
    if (containerSizePx <= 0) return value
    val maxValue = (containerSizePx - itemSizePx).coerceAtLeast(0f)
    return value.coerceIn(0f, maxValue)
}

/**
 * Reproductor de audio flotante — delgado, arrastrable a cualquier punto
 * de [containerSizePx] (medido por el llamador, ver `containerSizePx` en
 * `SingleFilePreviewScaffold`). Diseño a pedido explícito, con referencia
 * visual: una sola fila angosta — nota musical, barra de progreso fina
 * (arrastrable para buscar), tiempo transcurrido, play/pausa, cerrar —
 * sobre una superficie de vidrio con degradé y sombra real, para que se
 * lea como un control flotante "encima" del visor, no como una tarjeta
 * más incrustada en el layout.
 *
 * [onDismiss] es lo que dispara el botón "×" de la pastilla: cierra el
 * visor entero (mismo destino que la flecha "‹" de la cabecera) en vez
 * de dejar la pastilla oculta con el visor todavía abierto detrás — un
 * único punto de salida, sin estados "a medio cerrar" colgando.
 */
@Composable
private fun FloatingAudioPlayerPill(
    file: File,
    fileName: String,
    containerSizePx: IntSize,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val containerSizeLatest by rememberUpdatedState(containerSizePx)

    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(file) { mutableStateOf(false) }
    var durationMs by remember(file) { mutableIntStateOf(0) }
    var positionMs by remember(file) { mutableIntStateOf(0) }
    var loadError by remember(file) { mutableStateOf(false) }

    // Estado de "buscar" (arrastrar la barra fina) — separado por
    // completo del estado de "mover la pastilla entera" de acá abajo:
    // son dos gestos distintos sobre dos áreas distintas, cada uno con
    // su propio `pointerInput`, y `change.consume()` en el de la barra
    // evita que el arrastre de búsqueda se filtre hacia el arrastre de
    // la pastilla.
    var isSeeking by remember(file) { mutableStateOf(false) }
    var seekDragMs by remember(file) { mutableFloatStateOf(0f) }
    var seekBarWidthPx by remember(file) { mutableFloatStateOf(0f) }

    // Posición de la pastilla en la pantalla — arranca en la esquina
    // superior-izquierda (mismo punto que la referencia visual entregada)
    // y desde ahí es libre: el usuario la lleva a donde quiera.
    var offsetPx by remember(file) {
        val margin = with(density) { AUDIO_PILL_DEFAULT_MARGIN.toPx() }
        mutableStateOf(Offset(margin, margin))
    }
    var isDraggingPill by remember(file) { mutableStateOf(false) }

    val pillWidthPx = with(density) { AUDIO_PILL_WIDTH.toPx() }
    val pillHeightPx = with(density) { AUDIO_PILL_HEIGHT.toPx() }

    // `MediaPlayer.prepare()` (sync) hace I/O real de disco — se arma en
    // un coroutine sobre Dispatchers.IO en vez de en el DisposableEffect
    // de acá abajo, que corre en el hilo principal de composición.
    LaunchedEffect(file) {
        val mp = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                }
            }.onFailure { t -> AppLogger.e(TAG, "No se pudo preparar el audio '${file.name}' para previsualizar", t) }
                .getOrNull()
        }
        if (mp != null) {
            player = mp
            durationMs = mp.duration
            mp.setOnCompletionListener { isPlaying = false; positionMs = 0 }
        } else {
            loadError = true
        }
    }

    DisposableEffect(file) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (isPlaying && player != null) {
            if (!isSeeking) positionMs = player?.currentPosition ?: positionMs
            delay(200)
        }
    }

    // Sombra/escala animadas al levantar la pastilla — el mismo tipo de
    // "lift" táctil que ya usa `FloatingToolWindow` al arrastrar sus
    // ventanas (ver EditorScreen.kt), acá con una curva más liviana
    // porque esta pastilla es mucho más chica que aquellas ventanas.
    val liftElevation by animateDpAsState(if (isDraggingPill) 20.dp else 8.dp, label = "audioPillElevation")
    val liftScale by animateFloatAsState(if (isDraggingPill) 1.04f else 1f, label = "audioPillScale")

    val shownPosition = if (isSeeking) seekDragMs else positionMs.toFloat()
    val durationForMath = max(durationMs, 1)

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetPx.x.roundToInt(), offsetPx.y.roundToInt()) }
            .size(width = AUDIO_PILL_WIDTH, height = AUDIO_PILL_HEIGHT)
            .graphicsLayer { scaleX = liftScale; scaleY = liftScale }
            .shadow(liftElevation, RoundedCornerShape(AUDIO_PILL_CORNER), clip = false)
            .clip(RoundedCornerShape(AUDIO_PILL_CORNER))
            .background(
                Brush.linearGradient(listOf(SurfaceTintedElevated, SurfaceTintedDark))
            )
            .border(1.dp, BrandPurpleLight.copy(alpha = 0.30f), RoundedCornerShape(AUDIO_PILL_CORNER))
            // Arrastre de TODA la pastilla — vive en el `Box` exterior,
            // así que solo dispara cuando el gesto empieza en una zona
            // que ningún hijo (barra de búsqueda, botones) ya consumió.
            .pointerInput(file) {
                detectDragGestures(
                    onDragStart = {
                        isDraggingPill = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { isDraggingPill = false },
                    onDragCancel = { isDraggingPill = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val container = containerSizeLatest
                        offsetPx = Offset(
                            clampPillAxis(offsetPx.x + dragAmount.x, pillWidthPx, container.width),
                            clampPillAxis(offsetPx.y + dragAmount.y, pillHeightPx, container.height)
                        )
                    }
                )
            }
            .semantics { contentDescription = "Reproductor flotante de audio: $fileName" },
        contentAlignment = Alignment.Center
    ) {
        if (loadError) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "No se pudo reproducir este audio",
                    color = Color(0xFFD6CFEF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            return@Box
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = BrandPurpleLight,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))

            // Barra de búsqueda — delgada de verdad (3dp visibles) pero
            // con un área de toque más generosa (altura total del Row)
            // para que arrastrarla con el dedo sea cómodo sin agrandar
            // la línea visible, exactamente el criterio de la referencia.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { seekBarWidthPx = it.width.toFloat() }
                    .pointerInput(file, durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { isSeeking = true; seekDragMs = positionMs.toFloat() },
                            onDragEnd = {
                                isSeeking = false
                                val target = seekDragMs.roundToInt()
                                player?.seekTo(target)
                                positionMs = target
                            },
                            onDragCancel = { isSeeking = false }
                        ) { change, dragAmountX ->
                            change.consume()
                            if (seekBarWidthPx <= 0f) return@detectHorizontalDragGestures
                            val deltaMs = (dragAmountX / seekBarWidthPx) * durationForMath
                            seekDragMs = (seekDragMs + deltaMs).coerceIn(0f, durationForMath.toFloat())
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(AUDIO_PILL_SEEK_TRACK_HEIGHT)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                )
                Box(
                    Modifier
                        .fillMaxWidth((shownPosition / durationForMath).coerceIn(0f, 1f))
                        .height(AUDIO_PILL_SEEK_TRACK_HEIGHT)
                        .clip(CircleShape)
                        .background(BrandPurpleLight)
                )
            }

            Spacer(Modifier.width(10.dp))
            Text(
                formatDurationMs(shownPosition.toLong()),
                color = Color(0xFFD6CFEF),
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val mp = player ?: return@IconButton
                    if (isPlaying) {
                        mp.pause()
                    } else {
                        if (mp.currentPosition >= mp.duration - 50) mp.seekTo(0)
                        mp.start()
                    }
                    isPlaying = mp.isPlaying
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = "Cerrar reproductor",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ============================================================================
// Video — VideoView + MediaController nativos de Android, sin dependencias
// nuevas (mismo criterio que el reproductor de audio de acá arriba).
// ============================================================================

@Composable
private fun VideoPreviewContent(file: File) {
    val context = LocalContext.current
    var loadError by remember(file) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                VideoView(context).apply {
                    setVideoPath(file.absolutePath)
                    val controller = MediaController(context)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setOnPreparedListener { start() }
                    setOnErrorListener { _, _, _ -> loadError = true; true }
                }
            },
            onRelease = { it.stopPlayback() }
        )
        if (loadError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_film), null, tint = Color(0xFF8A7DB8), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("No se pudo reproducir este archivo de video", color = Color(0xFF8A7DB8), fontSize = 12.sp)
            }
        }
    }
}

// ============================================================================
// Fallback — cualquier extensión que no se sepa previsualizar todavía sigue
// siendo interactuable: ficha con los datos del archivo + "Abrir con…"
// (delega la vista previa real a cualquier app que el usuario ya tenga
// instalada) + "Compartir".
// ============================================================================

@Composable
private fun UnsupportedPreviewContent(
    fileName: String,
    sizeBytes: Long,
    isBusy: Boolean,
    onOpenWith: () -> Unit,
    onShare: () -> Unit
) {
    val extension = fileName.substringAfterLast('.', "").uppercase()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Box(
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(SurfaceTintedElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_file_generic), contentDescription = null, tint = Color(0xFF8A7DB8), modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(fileName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(
            "${if (extension.isNotEmpty()) "$extension · " else ""}${formatFileSize(sizeBytes)}",
            color = Color(0xFF8A7DB8),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "No hay una vista previa para este tipo de archivo",
            color = Color(0xFF8A7DB8),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onOpenWith,
                enabled = !isBusy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(painterResource(R.drawable.ic_fullscreen), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Abrir con…", fontSize = 13.sp)
            }
            Button(
                onClick = onShare,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurpleLight, contentColor = Color.White)
            ) {
                Icon(painterResource(R.drawable.ic_share), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Compartir", fontSize = 13.sp)
            }
        }
    }
}
