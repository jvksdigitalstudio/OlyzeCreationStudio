package com.yeivikas.olyzecs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.data.TrashedProjectEntry
import com.yeivikas.olyzecs.data.TrashedProjectSummary
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import com.yeivikas.olyzecs.viewmodel.ProjectsTrashViewModel

/**
 * "Papelera de proyectos" — decisión de producto que completa, a nivel de
 * BIBLIOTECA, lo mismo que la papelera de capas ya garantiza dentro de un
 * proyecto abierto (ver LayerTrashScreen.kt): eliminar un proyecto entero
 * desde "Mis proyectos" ya nunca es una acción de un solo toque sin vuelta
 * atrás — se mueve acá (ver [ProjectStorage.moveProjectToTrash][
 * com.yeivikas.olyzecs.data.ProjectStorage.moveProjectToTrash]), donde
 * queda esperando hasta que el usuario, a propósito, decide restaurarlo,
 * eliminarlo para siempre, o vaciar la papelera entera. SIN límite de
 * retención automático (decisión explícita del cliente): se acumula hasta
 * que el usuario mismo actúa.
 *
 * A pedido explícito de diseño (con referencia visual de "Recently
 * Deleted" de otra app): cada proyecto de la papelera se puede ABRIR y
 * navegar por dentro — la carpeta real del proyecto, organizada en sus
 * subcarpetas de siempre (`images/`, `audio/`, `cast/`, `project.json`,
 * miniatura, portada) — para borrar una entrada puntual sin tener que
 * restaurar el proyecto completo primero, o para borrar la carpeta entera
 * desde ahí mismo. Esta pantalla es la única superficie de la app que
 * expone ese navegador; el resto de "Mis proyectos" sigue trabajando
 * exclusivamente con [TrashedProjectSummary] (tarjetas, nunca archivos
 * sueltos).
 *
 * Se muestra como pantalla completa dentro de la única `Window` real de
 * `MainActivity` — no una nueva "ruta" de navegación (esta app no usa
 * Navigation Compose), sino el mismo patrón que ya usa esa Activity para
 * alternar entre "Mis proyectos" y el editor: un `if (bandera) { ... }`
 * normal, sin abrir ninguna `Window` de diálogo propia. Antes se abría
 * con su propio `Dialog`, igual que sigue haciendo [ErrorLogScreen] hoy;
 * se cambió a este patrón en FASE 16 (ver docs/fases/) al diagnosticarse
 * que esa `Window` extra, sumada a la del visor de imágenes que esta
 * pantalla abre ([FilePreviewDialog]), era la causa raíz real de una
 * franja de color residual arriba/abajo del visor en modo inmersivo.
 *
 * [onProjectRestored] se dispara cada vez que un proyecto vuelve a "Mis
 * proyectos" — el call site (ProjectsScreen.kt) lo usa para refrescar esa
 * lista; este composable no conoce [ProjectsViewModel][
 * com.yeivikas.olyzecs.viewmodel.ProjectsViewModel] en absoluto.
 */
@Composable
fun ProjectsTrashScreen(
    viewModel: ProjectsTrashViewModel,
    onClose: () -> Unit,
    onProjectRestored: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var deleteProjectTarget by remember { mutableStateOf<TrashedProjectSummary?>(null) }
    var deleteEntryTarget by remember { mutableStateOf<TrashedProjectEntry?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    // Archivo (no carpeta) tocado dentro del navegador — abre FilePreviewDialog
    // por encima de todo, en vez de navegar (eso solo aplica a carpetas, ver
    // TrashedEntryRow más abajo). Ver FilePreviewDialog.kt.
    //
    // Se congela la lista de `items` y el índice de apertura EN EL MOMENTO
    // del tap (no de forma reactiva a `uiState.currentEntries`): si en vez
    // de eso la galería recalculara sobre la lista en vivo, borrar otra
    // imagen mientras el visor está abierto correría el índice/tamaño de
    // la galería bajo los dedos del usuario a mitad de un swipe.
    var previewRequest by remember { mutableStateOf<FilePreviewRequest?>(null) }
    // Distingue "Eliminar carpeta completa" disparado DESDE DENTRO del
    // navegador (donde además hay que cerrarlo al confirmar) de
    // "Eliminar" disparado desde la fila de la lista — mismo diálogo de
    // confirmación, dos orígenes.
    var deleteWholeFromBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Atrás del sistema (gesto o botón físico de Android — mecanismo
    // TOTALMENTE SEPARADO del ícono "←" de TrashBrowserHeader, que tiene
    // su propio onClick): sin este BackHandler, el atrás del sistema
    // cerraría TODA la papelera de un salto (comportamiento por defecto
    // al no interceptarlo) — aunque el usuario esté navegando una
    // subcarpeta adentro de un proyecto. El botón visual "←" sí
    // retrocedía un nivel a la vez (subcarpeta → raíz del proyecto →
    // lista); el atrás del sistema no sabía nada de eso. Esto replica
    // exactamente los mismos tres niveles que ese botón.
    //
    // Por qué esta pantalla YA NO se abre con `Dialog(...)` (y antes sí):
    // un `Dialog` de Compose abre su PROPIA Window de Android, separada
    // de la de `MainActivity` — con esta pantalla Y el visor de imágenes
    // (`FilePreviewDialog`, ver ese archivo) cada uno con su propia
    // Window, llegaban a apilarse hasta TRES Windows superpuestas
    // (Activity → esta papelera → el visor) cuando se abría una foto.
    // Esa pila de Windows es la causa raíz real, confirmada en
    // dispositivo, de la franja morada/azul (`brand_purple_deep`, ver
    // themes.xml) arriba y abajo del visor inmersivo: el compositor del
    // sistema puede seguir pintando restos del `statusBarColor`/
    // `navigationBarColor` de una Window de más abajo en la pila, sin
    // importar qué tan bien configurada esté la de más arriba. La
    // solución de raíz es no tener Windows de más: esta pantalla ahora es
    // un `Surface` normal, DENTRO de la única Window real de
    // `MainActivity` — mismo criterio que ya usa esa Activity para
    // alternar entre "Mis proyectos" y el editor (ver MainActivity.kt).
    BackHandler(enabled = true) {
        when {
            uiState.browsingProjectId != null && uiState.currentPath.isNotEmpty() -> viewModel.navigateUp()
            uiState.browsingProjectId != null -> viewModel.closeBrowser()
            else -> onClose()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BrandPurpleDeep) {
        Column(modifier = Modifier.fillMaxSize()) {
            val browsingId = uiState.browsingProjectId
            if (browsingId == null) {
                TrashListHeader(
                    totalCount = uiState.projects.size,
                    totalSizeBytes = uiState.projects.sumOf { it.sizeBytes },
                    onClose = onClose,
                    onEmptyTrash = { showEmptyTrashConfirm = true }
                )
            } else {
                TrashBrowserHeader(
                    projectName = uiState.browsingProjectName,
                    pathSegments = uiState.pathSegments,
                    onBackToList = viewModel::closeBrowser,
                    onNavigateUp = viewModel::navigateUp,
                    onNavigateToRoot = viewModel::navigateToRoot,
                    onBreadcrumbClick = viewModel::navigateToBreadcrumb,
                    onClose = onClose
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    browsingId == null && uiState.isLoading ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    browsingId == null && uiState.projects.isEmpty() ->
                        EmptyProjectsTrashState(modifier = Modifier.align(Alignment.Center))

                    browsingId == null -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.projects, key = { it.id }) { project ->
                            TrashedProjectRow(
                                project = project,
                                onOpen = { viewModel.openProject(project.id, project.name) },
                                onRestore = { viewModel.restoreProject(project.id, onRestored = onProjectRestored) },
                                onDelete = { deleteProjectTarget = project }
                            )
                        }
                    }

                    uiState.isLoadingEntries ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    uiState.currentEntries.isEmpty() ->
                        EmptyTrashFolderState(modifier = Modifier.align(Alignment.Center))

                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.currentEntries, key = { it.relativePath }) { entry ->
                            TrashedEntryRow(
                                entry = entry,
                                onOpen = {
                                    when {
                                        entry.isDirectory -> viewModel.navigateInto(entry)
                                        // Imagen: la galería del visor son TODAS las imágenes
                                        // de esta misma carpeta (nunca solo la tocada), para
                                        // habilitar deslizar/filmstrip entre ellas — ver el
                                        // comentario de cabecera de FilePreviewDialog.kt.
                                        classifyFilePreviewKind(entry.name) == FilePreviewKind.IMAGE -> {
                                            val siblingImages = uiState.currentEntries.filter {
                                                !it.isDirectory && classifyFilePreviewKind(it.name) == FilePreviewKind.IMAGE
                                            }
                                            previewRequest = FilePreviewRequest(
                                                items = siblingImages.map { img ->
                                                    FilePreviewItem(
                                                        fileName = img.name,
                                                        file = img.file,
                                                        sizeBytes = img.sizeBytes,
                                                        onPrepareForShare = { viewModel.prepareEntryForSharing(img) },
                                                        onDelete = { viewModel.deleteEntry(img) }
                                                    )
                                                },
                                                initialIndex = siblingImages
                                                    .indexOfFirst { it.relativePath == entry.relativePath }
                                                    .coerceAtLeast(0)
                                            )
                                        }
                                        else -> previewRequest = FilePreviewRequest(
                                            items = listOf(
                                                FilePreviewItem(
                                                    fileName = entry.name,
                                                    file = entry.file,
                                                    sizeBytes = entry.sizeBytes,
                                                    onPrepareForShare = { viewModel.prepareEntryForSharing(entry) },
                                                    onDelete = { viewModel.deleteEntry(entry) }
                                                )
                                            ),
                                            initialIndex = 0
                                        )
                                    }
                                },
                                onDelete = { deleteEntryTarget = entry }
                            )
                        }
                    }
                }

                // --- Pie fijo del navegador: acciones sobre el PROYECTO entero que se está viendo ---
                if (browsingId != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(SurfaceTintedElevated.copy(alpha = 0.0f), SurfaceTintedElevated)))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { deleteWholeFromBrowser = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE05C5C))
                        ) {
                            Icon(painterResource(R.drawable.ic_delete), null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Eliminar carpeta", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { viewModel.restoreProject(browsingId, onRestored = onProjectRestored) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurpleLight, contentColor = Color.White)
                        ) {
                            Icon(painterResource(R.drawable.ic_restore_all), null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restaurar proyecto", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    deleteProjectTarget?.let { target ->
        DeleteTrashedProjectConfirmDialog(
            projectName = target.name,
            onConfirm = {
                viewModel.deleteProjectPermanently(target.id)
                deleteProjectTarget = null
            },
            onDismiss = { deleteProjectTarget = null }
        )
    }

    if (deleteWholeFromBrowser) {
        DeleteTrashedProjectConfirmDialog(
            projectName = uiState.browsingProjectName,
            onConfirm = {
                uiState.browsingProjectId?.let { viewModel.deleteProjectPermanently(it) }
                deleteWholeFromBrowser = false
            },
            onDismiss = { deleteWholeFromBrowser = false }
        )
    }

    deleteEntryTarget?.let { entry ->
        DeleteTrashedEntryConfirmDialog(
            entry = entry,
            onConfirm = {
                viewModel.deleteEntry(entry)
                deleteEntryTarget = null
            },
            onDismiss = { deleteEntryTarget = null }
        )
    }

    if (showEmptyTrashConfirm) {
        EmptyProjectsTrashConfirmDialog(
            itemCount = uiState.projects.size,
            onConfirm = {
                viewModel.emptyTrash()
                showEmptyTrashConfirm = false
            },
            onDismiss = { showEmptyTrashConfirm = false }
        )
    }

    previewRequest?.let { request ->
        FilePreviewDialog(
            items = request.items,
            initialIndex = request.initialIndex,
            onDismiss = { previewRequest = null }
        )
    }
}

/**
 * Snapshot congelado, en el momento del tap, de qué archivo(s) mostrar en
 * [FilePreviewDialog] — ver el comentario junto a `previewRequest` más
 * arriba sobre por qué no se recalcula de forma reactiva.
 */
private data class FilePreviewRequest(val items: List<FilePreviewItem>, val initialIndex: Int)

// ============================================================================
// Encabezados
// ============================================================================

@Composable
private fun TrashListHeader(totalCount: Int, totalSizeBytes: Long, onClose: () -> Unit, onEmptyTrash: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SurfaceTintedElevated, SurfaceTintedDark)))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceTintedDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_delete), null, tint = Color(0xFFE05C5C), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Papelera de proyectos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (totalCount == 0) "Vacía"
                    else "$totalCount ${if (totalCount == 1) "proyecto" else "proyectos"} · ${formatFileSize(totalSizeBytes)}",
                    color = Color(0xFFBFB3E0),
                    fontSize = 12.sp
                )
            }
            if (totalCount > 0) {
                TextButton(onClick = onEmptyTrash) {
                    Text("Vaciar", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TrashBrowserHeader(
    projectName: String,
    pathSegments: List<String>,
    onBackToList: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToRoot: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SurfaceTintedElevated, SurfaceTintedDark)))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (pathSegments.isEmpty()) onBackToList() else onNavigateUp() }) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                projectName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        // --- Migas de pan: raíz del proyecto + cada subcarpeta navegada ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BreadcrumbChip(label = "Proyecto", isCurrent = pathSegments.isEmpty(), onClick = onNavigateToRoot)
            pathSegments.forEachIndexed { index, segment ->
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = Color(0xFF8A7DB8),
                    modifier = Modifier.size(12.dp).padding(horizontal = 2.dp)
                )
                BreadcrumbChip(
                    label = segment,
                    isCurrent = index == pathSegments.lastIndex,
                    onClick = { onBreadcrumbClick(index) }
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbChip(label: String, isCurrent: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (isCurrent) Color.White else Color(0xFFBFB3E0),
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

// ============================================================================
// Estados vacíos
// ============================================================================

@Composable
private fun EmptyProjectsTrashState(modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(32.dp)) {
        Icon(painterResource(R.drawable.ic_delete), null, tint = Color(0xFF8A7DB8), modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("La papelera de proyectos está vacía", color = Color(0xFFD6CFEF), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "Los proyectos que elimines desde \"Mis proyectos\" van a aparecer acá, listos para restaurar.",
            color = Color(0xFF8A7DB8),
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun EmptyTrashFolderState(modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(32.dp)) {
        Icon(painterResource(R.drawable.ic_folder), null, tint = Color(0xFF8A7DB8), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Esta carpeta está vacía", color = Color(0xFF8A7DB8), fontSize = 12.sp)
    }
}

// ============================================================================
// Filas
// ============================================================================

@Composable
private fun TrashedProjectRow(
    project: TrashedProjectSummary,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceTintedElevated)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceTintedDark)
        ) {
            if (project.displayImageFile != null) {
                AsyncImage(
                    model = project.displayImageFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_image_placeholder),
                    contentDescription = null,
                    tint = Color(0xFF8A7DB8),
                    modifier = Modifier.align(Alignment.Center).size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(project.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "Eliminado el ${formatFullDate(project.trashedAtMs)} · ${formatFileSize(project.sizeBytes)}",
                color = Color(0xFF8A7DB8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRestore, modifier = Modifier.size(34.dp)) {
            Icon(painterResource(R.drawable.ic_restore_all), contentDescription = "Restaurar", tint = BrandPurpleLight, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Eliminar para siempre", tint = Color(0xFFE05C5C), modifier = Modifier.size(18.dp))
        }
    }
}

/** true si [name] tiene una extensión de imagen soportada para previsualizar con AsyncImage. */
private fun isPreviewableImage(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
}

@Composable
private fun TrashedEntryRow(entry: TrashedProjectEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    // A partir de acá cualquier fila es interactuable, no solo las
    // carpetas: tocar un archivo abre FilePreviewDialog con la vista
    // previa apropiada a su contenido (imagen, JSON, texto, audio, video,
    // o una ficha con "Abrir con…"/"Compartir" si no se sabe previsualizar
    // — ver classifyFilePreviewKind), en vez de no hacer nada como antes.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceTintedElevated)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(SurfaceTintedDark),
            contentAlignment = Alignment.Center
        ) {
            when {
                entry.isDirectory -> Icon(painterResource(R.drawable.ic_folder), null, tint = Color(0xFFE0A83C), modifier = Modifier.size(20.dp))
                isPreviewableImage(entry.name) -> AsyncImage(
                    model = entry.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))
                )
                else -> Icon(painterResource(R.drawable.ic_file_generic), null, tint = Color(0xFF8A7DB8), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.isDirectory) "${entry.childCount ?: 0} elemento(s) · ${formatFileSize(entry.sizeBytes)}"
                else formatFileSize(entry.sizeBytes),
                color = Color(0xFF8A7DB8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Eliminar", tint = Color(0xFFE05C5C), modifier = Modifier.size(16.dp))
        }
    }
}

// ============================================================================
// Confirmaciones (misma familia visual que EmptyTrashConfirmDialog en
// LayerTrashScreen.kt y el diálogo "¿Eliminar...?" en ProjectsScreen.kt)
// ============================================================================

@Composable
private fun DeleteTrashedProjectConfirmDialog(projectName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text("¿Eliminar \"$projectName\" para siempre?", color = Color.White) },
        text = {
            Text(
                "Esta acción es irreversible. Se borrará por completo la carpeta de este proyecto — capas, imágenes, " +
                    "audio y miniaturas — y no podrá recuperarse.",
                color = Color(0xFFD6CFEF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar para siempre", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) } }
    )
}

@Composable
private fun DeleteTrashedEntryConfirmDialog(entry: TrashedProjectEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text(if (entry.isDirectory) "¿Eliminar la carpeta \"${entry.name}\"?" else "¿Eliminar \"${entry.name}\"?", color = Color.White) },
        text = {
            Text(
                if (entry.isDirectory) "Esta acción es irreversible. Se borrará esta subcarpeta y todo su contenido (${entry.childCount ?: 0} elemento(s))."
                else "Esta acción es irreversible. Este archivo se borrará de forma permanente.",
                color = Color(0xFFD6CFEF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) } }
    )
}

@Composable
private fun EmptyProjectsTrashConfirmDialog(itemCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        shape = RectangleShape,
        onDismissRequest = onDismiss,
        containerColor = SurfaceTintedElevated,
        title = { Text("¿Vaciar la papelera de proyectos?", color = Color.White) },
        text = {
            Text(
                "Esta acción es irreversible. Los $itemCount ${if (itemCount == 1) "proyecto" else "proyectos"} de la papelera " +
                    "se eliminarán de forma permanente, junto con todas sus capas, imágenes y audio, y no podrán recuperarse.",
                color = Color(0xFFD6CFEF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Vaciar papelera", color = Color(0xFFE05C5C), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFD6CFEF)) } }
    )
}
