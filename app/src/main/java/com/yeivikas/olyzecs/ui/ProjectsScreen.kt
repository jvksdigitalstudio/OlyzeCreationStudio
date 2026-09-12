package com.yeivikas.olyzecs.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.debug.AppLogger
import com.yeivikas.olyzecs.data.DEFAULT_PROJECT_NAME
import com.yeivikas.olyzecs.data.MoveDirection
import com.yeivikas.olyzecs.data.ProjectSummary
import com.yeivikas.olyzecs.platform.DisplayRefreshRate
import com.yeivikas.olyzecs.engine.scene.AspectRatio
import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset
import com.yeivikas.olyzecs.engine.scene.CanvasFactory
import com.yeivikas.olyzecs.engine.scene.CanvasLimits
import com.yeivikas.olyzecs.engine.scene.CanvasSpec
import com.yeivikas.olyzecs.engine.scene.DefaultCanvasFactory
import com.yeivikas.olyzecs.engine.scene.FormatCatalog
import com.yeivikas.olyzecs.engine.scene.FormatPreset
import com.yeivikas.olyzecs.engine.scene.PresetCategory
import com.yeivikas.olyzecs.engine.scene.StaticFormatCatalog
import com.yeivikas.olyzecs.viewmodel.DEFAULT_PROJECT_FPS
import com.yeivikas.olyzecs.viewmodel.ProjectsViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState

/**
 * Relación de aspecto ancho/alto de la tarjeta de portada en "Mis
 * proyectos" — la usa tanto [ProjectCard] como [CoverAdjustDialog], para
 * que el recuadro de ajuste de portada muestre EXACTAMENTE el mismo
 * encuadre que después se ve en la tarjeta (nada de sorpresas al guardar).
 */
const val PROJECT_CARD_ASPECT_RATIO = 9f / 14f

/**
 * Pantalla de inicio: la biblioteca de proyectos guardados, estilo "hub" de
 * cualquier editor de video profesional (CapCut, Premiere Rush, LumaFusion).
 * Cada tarjeta muestra la miniatura REAL del proyecto —con el look
 * cinematográfico ya aplicado, generada por [com.yeivikas.olyzecs.engine.timeline.ThumbnailRenderer]—
 * no un ícono genérico.
 *
 * [refreshKey] se incrementa desde fuera (MainActivity) cada vez que se
 * vuelve del editor, para refrescar la lista sin necesidad de un
 * ViewModel propio para esta pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    refreshKey: Int,
    onOpenProject: (String) -> Unit,
    // La duración ya no la elige el usuario acá — el proyecto siempre
    // arranca en 1 minuto y crece solo (ver TimelineDurationManager);
    // por eso esta firma ya no lleva ningún parámetro de duración.
    onCreateProject: (id: String, name: String, canvas: CanvasSpec, fps: Int) -> Unit,
    // Lanza el selector de imágenes del sistema para elegir una portada
    // personalizada; vive en MainActivity (nivel Activity) porque el picker
    // de SAF necesita registrarse ahí — ver pickCoverLauncher.
    onPickCoverImage: (onPicked: (android.net.Uri) -> Unit) -> Unit,
    // Exporta el proyecto como archivo .olyze y abre la hoja para
    // compartir con cualquier app instalada (WhatsApp, Telegram, Drive,
    // correo, Bluetooth...) — vive en MainActivity porque lanzar un
    // Intent.ACTION_SEND necesita un Activity Context.
    onShareProject: (projectId: String, projectName: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val projects = uiState.projects
    val isLoading = uiState.isLoading

    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var menuTargetId by remember { mutableStateOf<String?>(null) }
    // A diferencia de deleteTarget (que abre un Dialog flotante
    // centrado en toda la pantalla), el panel de info se dibuja DENTRO de la
    // tarjeta correspondiente — por eso acá alcanza con guardar el id: cada
    // ProjectCard decide mostrar su propio overlay comparando contra este id.
    var infoTargetId by remember { mutableStateOf<String?>(null) }
    // Imagen recién elegida desde el picker del sistema, pendiente de que
    // el usuario la encuadre en CoverAdjustDialog antes de guardarla como
    // portada — ver comentario grande en [onPickCoverImage] de MainActivity
    // y en [CoverAdjustDialog].
    var coverAdjustState by remember { mutableStateOf<Pair<String, android.net.Uri>?>(null) }

    // Menú premium (ícono a la izquierda del título "Menu") y su única
    // opción por ahora, "Registro de errores" — ver AppMenuDrawer.kt y
    // ErrorLogScreen.kt.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showErrorLog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) { viewModel.refresh() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onErrorLogClick = { showErrorLog = true },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.White,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PremiumMenuButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                title = {
                    Column {
                        Text(
                            "Menu",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            "Mis proyectos",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            // El botón flotante "+" solo tiene sentido una vez que ya existe
            // al menos un proyecto — con la lista vacía, el CTA central
            // "Crear tu primer proyecto" ya cubre esa acción; mostrar los
            // dos a la vez es redundante. En cuanto se crea el primero, el
            // FAB aparece y queda fijo en su lugar (comportamiento nativo
            // del Scaffold: no se mueve al scrollear la grilla).
            if (projects.isNotEmpty()) {
                FloatingActionButton(onClick = { showCreateDialog = true }) { Text("+") }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                projects.isEmpty() -> EmptyProjectsState(
                    modifier = Modifier.align(Alignment.Center),
                    onCreateProject = { showCreateDialog = true }
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            menuExpanded = menuTargetId == project.id,
                            onOpenMenu = { menuTargetId = project.id },
                            onCloseMenu = { menuTargetId = null },
                            onOpen = { onOpenProject(project.id) },
                            onDuplicate = {
                                viewModel.duplicateProject(project.id, "Copia de ${project.name}")
                            },
                            onDelete = { deleteTarget = project },
                            onSetCover = {
                                // Ya NO se guarda directo a ciegas: se abre
                                // el editor de encuadre (CoverAdjustDialog,
                                // más abajo) y recién cuando el usuario
                                // confirma ahí se persiste la portada.
                                onPickCoverImage { uri -> coverAdjustState = project.id to uri }
                            },
                            onShare = { onShareProject(project.id, project.name) },
                            onRemoveCover = {
                                viewModel.removeCoverImage(project.id)
                            },
                            onMoveUp = {
                                viewModel.moveProject(project.id, MoveDirection.UP)
                            },
                            onMoveDown = {
                                viewModel.moveProject(project.id, MoveDirection.DOWN)
                            },
                            onShowInfo = { infoTargetId = project.id },
                            onCloseInfo = { infoTargetId = null },
                            showInfo = infoTargetId == project.id
                        )
                    }
                }
            }
        }
    }
    } // cierre de ModalNavigationDrawer

    if (showErrorLog) {
        ErrorLogScreen(onClose = { showErrorLog = false })
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, canvas, fps ->
                showCreateDialog = false
                val newId = viewModel.newProjectId()
                onCreateProject(newId, name.trim(), canvas, fps)
            }
        )
    }

    coverAdjustState?.let { (targetProjectId, uri) ->
        CoverAdjustDialog(
            imageUri = uri,
            onDismiss = { coverAdjustState = null },
            onConfirm = { croppedBitmap ->
                coverAdjustState = null
                viewModel.setCoverImage(targetProjectId, croppedBitmap)
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar \"${target.name}\"?") },
            text = { Text("Esta acción no se puede deshacer. Se borrarán todas sus capas, keyframes e imágenes guardadas.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.deleteProject(target.id)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EmptyProjectsState(modifier: Modifier = Modifier, onCreateProject: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_film),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Todavía no tenés proyectos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Creá uno y convertí tus imágenes en una escena con cámara animada.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onCreateProject) { Text("Crear tu primer proyecto") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    project: ProjectSummary,
    menuExpanded: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSetCover: () -> Unit,
    onShare: () -> Unit,
    onRemoveCover: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onShowInfo: () -> Unit,
    onCloseInfo: () -> Unit,
    showInfo: Boolean
) {
    val context = LocalContext.current
    val hasDescription = project.description.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PROJECT_CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onOpen, onLongClick = onOpenMenu)
    ) {
        // Portada: prioriza la elegida a mano por el usuario sobre la
        // miniatura auto-generada — ver [ProjectSummary.displayImageFile].
        val coverImage = project.displayImageFile
        if (coverImage != null) {
            // Coil cachea por defecto usando la ruta del archivo como clave,
            // así que sobrescribir "cover.jpg"/"thumbnail.jpg" EN EL MISMO
            // PATH (que es justo lo que hace ProjectStorage al cambiar la
            // portada) no invalida esa caché — la tarjeta seguía mostrando
            // la imagen vieja hasta reabrir la app. Se arregla incluyendo la
            // fecha de última modificación del archivo en la clave de caché
            // de memoria/disco: apenas cambia el contenido, cambia la clave,
            // y Coil vuelve a decodificar en vez de servir el bitmap stale.
            val cacheKey = remember(coverImage.path, coverImage.lastModified()) {
                "${coverImage.path}#${coverImage.lastModified()}"
            }
            AsyncImage(
                model = remember(cacheKey) {
                    ImageRequest.Builder(context)
                        .data(coverImage)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .build()
                },
                contentDescription = project.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { state ->
                    AppLogger.w("ProjectsScreen", "No se pudo cargar la portada del proyecto '${project.name}'", state.result.throwable)
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_image_placeholder),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Cabecera superior: scrim + TÍTULO grande y bien destacado —
        // ligeramente más grande que el resto de la UI de la tarjeta (es
        // portada, no una fila de lista), pero sin llegar a un tamaño
        // desproporcionado que rompa el balance de la miniatura.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .padding(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Text(
                project.name,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Pie: SOLO aparece cuando el proyecto tiene descripción — la
        // metadata técnica (fecha/capas) ya no vive acá, se movió entera al
        // panel del ícono ⓘ para no duplicar información en la portada. Si
        // no hay descripción, no se dibuja nada abajo. Si la hay, un
        // degradado oscuro que arranca a la mitad de la tarjeta hacia abajo
        // le da fondo propio; el bloque "Acerca de" + descripción se ancla
        // ARRIBA de esa mitad (no pegado al borde inferior de la tarjeta),
        // para que quede un margen de aire antes de que termine la portada.
        if (hasDescription) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.78f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Column {
                    Text(
                        "Acerca de",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        project.description,
                        color = Color.White.copy(alpha = 0.95f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Botón de menú "⋮" arriba a la derecha de la tarjeta.
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Surface(
                modifier = Modifier.clickable { onOpenMenu() },
                color = Color.Black.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert),
                    contentDescription = "Opciones de \"${project.name}\"",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
            // ARREGLADO: le había puesto `shape = RectangleShape` para la
            // esquina recta, asumiendo sin verificar que este parámetro
            // existía en la versión de Material3 que trae este proyecto
            // (compose-bom 2024.06.00) — igual error de fondo que con
            // `Icons.Filled.RestartAlt` antes: supuse un API que no pude
            // chequear contra la librería real. Cuando esa llamada no
            // resuelve, el compilador pierde el tipo esperado del
            // contenido de adentro (el lambda de `DropdownMenuItem`s), y
            // por eso los `Icon(...)` de "Compartir"/"Duplicar"/etc.
            // explotaban con "@Composable invocations can only happen..."
            // — no tenían nada malo en sí mismos. Se saca el parámetro:
            // este menú en particular se queda con la esquina redondeada
            // por defecto de Material3 hasta poder confirmar el parámetro
            // correcto contra un build real.
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onCloseMenu) {
                DropdownMenuItem(
                    text = { Text(if (project.coverImageFile != null) "Cambiar portada" else "Agregar portada") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_image_placeholder), contentDescription = null) },
                    onClick = { onCloseMenu(); onSetCover() }
                )
                if (project.coverImageFile != null) {
                    DropdownMenuItem(
                        text = { Text("Quitar portada", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                painterResource(id = R.drawable.ic_close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { onCloseMenu(); onRemoveCover() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Compartir") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_share), contentDescription = null) },
                    onClick = { onCloseMenu(); onShare() }
                )
                DropdownMenuItem(
                    text = { Text("Duplicar") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_copy), contentDescription = null) },
                    onClick = { onCloseMenu(); onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text("Mover arriba") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_chevron_up), contentDescription = null) },
                    enabled = project.canMoveUp,
                    onClick = { onCloseMenu(); onMoveUp() }
                )
                DropdownMenuItem(
                    text = { Text("Mover abajo") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.ic_chevron_down), contentDescription = null) },
                    enabled = project.canMoveDown,
                    onClick = { onCloseMenu(); onMoveDown() }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.ic_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = { onCloseMenu(); onDelete() }
                )
            }
        }

        // Ícono "ⓘ" — info del proyecto — abajo a la derecha, DENTRO de la
        // portada, encima del scrim. Se oculta mientras el panel de info
        // está abierto (lo reemplaza el botón "cerrar" del propio panel).
        if (!showInfo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Surface(
                    modifier = Modifier.clickable { onShowInfo() },
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = "Información de \"${project.name}\"",
                        tint = Color.White,
                        modifier = Modifier.padding(5.dp).size(16.dp)
                    )
                }
            }
        }

        // Panel de detalles del proyecto: a diferencia de los demás diálogos
        // de la pantalla (que son un Dialog centrado en TODA la pantalla),
        // este se dibuja DENTRO del mismo Box de la tarjeta — hereda su
        // recorte de esquinas redondeadas y queda contenido exactamente
        // dentro de la portada correspondiente, nunca flotando afuera.
        if (showInfo) {
            ProjectInfoOverlay(project = project, onDismiss = onCloseInfo)
        }
    }
}

/**
 * Overlay de info que se dibuja DENTRO de la portada del proyecto (ver
 * comentario en el llamado desde [ProjectCard]) — no es un Dialog del
 * sistema, es contenido normal dentro del mismo Box recortado a las
 * esquinas redondeadas de la tarjeta. `.clickable(onClick = {})` sin más
 * que un lambda vacío consume cualquier toque sobre el panel para que no
 * se filtre hacia el `combinedClickable` de la tarjeta de atrás (que abre
 * el proyecto).
 */
@Composable
private fun ProjectInfoOverlay(project: ProjectSummary, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(onClick = {})
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(end = 20.dp)
        ) {
            Text(
                project.name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Año y categoría son opcionales (el usuario los llena desde
            // el panel "Información del proyecto" dentro del editor) — si
            // todavía no se llenaron, no mostramos la fila para no dejar
            // un "—" feo colgando.
            project.releaseYear?.let { InfoDetailRow("Año", it.toString()) }
            project.genre?.let { InfoDetailRow("Categoría", it) }
            InfoDetailRow("Creado", formatFullDate(project.createdAtMs))
            InfoDetailRow("Editado", formatFullDate(project.updatedAtMs))
            InfoDetailRow("Tamaño", formatFileSize(project.sizeBytes))
            InfoDetailRow("Capas", "${project.layerCount}")
            InfoDetailRow("Duración", formatProjectDuration(project.projectDurationMs))
            InfoDetailRow("Formato", formatDisplayFor(project))
            InfoDetailRow("FPS", "${project.fps} fps")
        }

        // Botón "cerrar" arriba a la derecha del panel — mismo lugar donde
        // vivía el menú "⋮", que se oculta mientras este panel está abierto.
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable { onDismiss() },
            color = Color.White.copy(alpha = 0.12f),
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Cerrar información",
                tint = Color.White,
                modifier = Modifier.padding(5.dp).size(14.dp)
            )
        }
    }
}

/** Fila compacta "etiqueta: valor" para el panel de info dentro de la portada. */
@Composable
private fun InfoDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/**
 * Texto de "Formato" para el panel de info de un proyecto (ícono ⓘ en
 * "Mis proyectos"). Ver ADR-005, auditoría de Fases F/G: antes esto
 * mostraba `aspectRatioLabel(project.aspectRatio)` — el `String` legacy
 * de 3 valores fijos, que para cualquier proyecto Cuadrado/Custom/
 * General (creado desde la Fase E) mostraba una relación FALSA (ej.
 * "16:9" para un Canvas 4:3, porque ambos son "landscape" y el legacy
 * solo distingue 3 categorías). Ahora usa el Canvas real
 * (`canvasWidthPx`/`canvasHeightPx`, ya saneados en `listProjects`)
 * cuando está disponible, con el mismo criterio de texto que
 * `ExportQualityPanel`: proporción real + descripción del preset de
 * origen si el catálogo lo reconoce, o "Personalizado" si no.
 */
private fun formatDisplayFor(project: ProjectSummary): String {
    val widthPx = project.canvasWidthPx
    val heightPx = project.canvasHeightPx
    if (widthPx == null || heightPx == null) {
        // Proyecto sin Canvas propio todavía (formato viejo,
        // `canvasSchemaVersion == 0`) o con datos corruptos que
        // `sanitizeProjectData` ya descartó — único caso en el que el
        // `String` legacy sigue siendo la única fuente disponible.
        return legacyAspectRatioLabel(project.aspectRatio)
    }
    val aspect = AspectRatio.of(widthPx, heightPx)
    val presetDescription = project.canvasOriginPresetId
        ?.let { StaticFormatCatalog.findById(it)?.label }
        ?: "Personalizado"
    return "$aspect ($presetDescription)"
}

private fun legacyAspectRatioLabel(aspectRatio: String): String = when (aspectRatio) {
    "REELS" -> "9:16"
    "SQUARE" -> "1:1"
    "WIDESCREEN" -> "16:9"
    else -> aspectRatio
}

/** Tamaño de archivo legible (B/KB/MB/GB), para el panel de info del proyecto. */
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

/** Fecha y hora completas (no relativas), para el panel de info del proyecto. */
private fun formatFullDate(atMs: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale("es")).format(java.util.Date(atMs))

/**
 * Segmented control real para las categorías del selector de formato
 * (hallazgo 3.1 de la auditoría: antes reusaba `SelectableChip`, el
 * mismo componente que los presets, y no se distinguía nivel-1 de
 * nivel-2). Riel translúcido + pastilla sólida en la pestaña activa —
 * mismo lenguaje que un segmented control nativo, deliberadamente
 * distinto en forma y color de las tarjetas de [FormatPresetCard] que
 * van debajo, para que a simple vista se lea "esto agrupa, esto elige".
 */
@Composable
private fun FormatCategoryTabs(
    categories: List<PresetCategory>,
    selected: PresetCategory,
    onSelect: (PresetCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(3.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    }
                    .clickable { onSelect(category) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Miniatura de la proporción real (alto/ancho) de un formato — un
 * rectángulo a escala dentro de una caja fija de [boxSize], nunca
 * deformado (hallazgo 3.5 de la auditoría: antes solo había texto,
 * "Reels"/"Feed cuadrado"/etc., sin ningún apoyo visual que dejara ver
 * de un vistazo si un formato es vertical, cuadrado u horizontal).
 */
@Composable
private fun AspectRatioGlyph(
    widthPx: Int,
    heightPx: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 22.dp
) {
    val ratio = widthPx.toFloat() / heightPx.toFloat()
    val glyphWidth = if (ratio >= 1f) boxSize else boxSize * ratio
    val glyphHeight = if (ratio >= 1f) boxSize / ratio else boxSize
    Box(modifier = modifier.size(boxSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = glyphWidth, height = glyphHeight)
                .border(width = 1.6.dp, color = tint, shape = RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Mapea el id de un [FormatPreset] al logo de marca que se dibuja como
 * fondo de su [FormatPresetCard] (ronda 4 de la auditoría de UX).
 * `null` para cualquier id que no tenga plataforma asociada — hoy eso
 * cubre "Personalizado" (no pasa por acá, ni siquiera tiene
 * `FormatPreset`) y los dos presets de [PresetCategory.GENERAL]
 * ("Clásico horizontal"/"Clásico vertical", ver [StaticFormatCatalog]),
 * que son relaciones de aspecto genéricas sin marca.
 *
 * Reels y Stories usan `logo_facebook_instagram` (el degradado
 * combinado Facebook/Instagram) en vez de un solo logo: a diferencia
 * de "Feed cuadrado" (que sigue siendo pura Instagram), ambos formatos
 * existen igual de nativos en Facebook que en Instagram — mostrar solo
 * el logo de Instagram ahí sería incorrecto, no solo incompleto. No
 * hay un logo de Facebook suelto en el proyecto (se borró por no
 * tener ningún preset que lo usara) — si algún día hace falta el
 * ícono de Facebook solo, sin el degradado combinado, hay que agregar
 * el archivo de nuevo antes de referenciarlo acá.
 */
@DrawableRes
private fun brandLogoFor(presetId: String): Int? = when (presetId) {
    "social.vertical.reels",
    "social.vertical.stories" -> R.drawable.logo_facebook_instagram
    "social.square.feed" -> R.drawable.logo_instagram
    "social.vertical.tiktok" -> R.drawable.logo_tiktok
    "social.vertical.youtubeshorts",
    "social.widescreen.youtube" -> R.drawable.logo_youtube
    else -> null
}

/**
 * Tarjeta de preset del selector de formato — reemplaza el
 * `SelectableChip` de texto plano que usaban tanto los presets como
 * "Personalizado" (hallazgos 3.2, 3.4 y 3.5 de la auditoría):
 * - Muestra un [AspectRatioGlyph] (o un ícono "+" si [isCustomEntry])
 *   en vez de depender solo del nombre para entender la forma.
 * - La selección no se comunica SOLO con color: hay borde más grueso,
 *   texto en negrita y un círculo con check arriba a la derecha — así
 *   sigue siendo legible para daltonismo o poco contraste (hallazgo 3.4).
 * - Expone `Role.RadioButton` + `selected` por semántica, para que un
 *   lector de pantalla (TalkBack) anuncie el estado real, cosa que el
 *   `SelectableChip` original no hacía.
 * - Ancho MÍNIMO (no fijo) para todas las tarjetas, incluida
 *   "Personalizado" (antes era un chip `fillMaxWidth()`,
 *   desproporcionado frente al resto — hallazgo 3.2). Ronda 3 de la
 *   auditoría: un ancho FIJO de 86dp cortaba con "..." cualquier
 *   etiqueta que no entrara en una sola línea a ese ancho —
 *   "Personalizado", "Feed cuadrado", "YouTube · horizontal", "Clásico
 *   horizontal" y "Clásico vertical" quedaban todas truncadas. Ir
 *   acortando cada etiqueta a mano no ataca la causa: el mismo corte
 *   reaparece con cualquier preset nuevo, o con la letra del sistema
 *   agrandada por accesibilidad. `widthIn(min = ...)` deja que la
 *   tarjeta CREZCA lo que el texto necesite, sin techo — las etiquetas
 *   cortas (Reels, TikTok, Shorts) se ven exactamente igual que antes,
 *   nunca se truncan de más.
 * - Sin subtítulo de proporción/tamaño dentro de la tarjeta: ese dato
 *   ya se muestra una sola vez, debajo de la fila de presets (ver el
 *   `Text` con "${canvas.widthPx} × ${canvas.heightPx} px..." más
 *   abajo), así que repetirlo tarjeta por tarjeta era redundante y
 *   generaba ruido visual (p. ej. "9:16" tres veces seguidas para
 *   Reels/TikTok/Stories).
 * - [brandLogo] (ronda 4 de la auditoría): logo de la plataforma como
 *   marca de agua de fondo, MUY tenue (16% de opacidad) y detrás de
 *   todo el contenido — nunca compite en contraste con el ícono de
 *   proporción ni con la etiqueta, que siguen siendo la única fuente
 *   de verdad legible. `null` para "Personalizado" y para los presets
 *   de [PresetCategory.GENERAL] (Clásico horizontal/vertical), que no
 *   pertenecen a ninguna plataforma — mostrar un logo ahí sería
 *   incorrecto, no solo innecesario. El mapeo preset→logo vive en el
 *   caller (`CreateProjectDialog`), no acá: este componente solo
 *   dibuja el recurso que le pasan, no decide a qué plataforma
 *   corresponde cada preset (esa decisión es de más alto nivel y
 *   puede cambiar sin tocar la tarjeta en sí).
 */
@Composable
private fun FormatPresetCard(
    label: String,
    aspectWidthPx: Int,
    aspectHeightPx: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCustomEntry: Boolean = false,
    @DrawableRes brandLogo: Int? = null
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    val glyphTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    // El círculo de "seleccionado" es intencionalmente verde, no morado
    // como el resto de la tarjeta (borde/fondo/glifo siguen usando
    // `colorScheme.primary`, ver arriba) — así el check queda como una
    // señal de estado ("confirmado") claramente distinta del color de
    // marca, en vez de un morado más sobre otro morado.
    val selectedCheckColor = Color(0xFF22C55E)

    Box(
        modifier = modifier
            .widthIn(min = 86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(width = if (selected) 1.6.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(onClick = onClick),
        // Sin esto, el Box por defecto alinea su contenido a TopStart:
        // el Column interno centra ícono+texto ENTRE SÍ, pero el Column
        // como bloque queda pegado a la izquierda cada vez que la
        // tarjeta es más ancha que su contenido — y con
        // `widthIn(min = 86.dp)` eso pasa en casi todas las etiquetas
        // cortas (Reels, Stories, TikTok), produciendo el ícono/texto
        // corridos a la izquierda que se ve en las capturas.
        contentAlignment = Alignment.Center
    ) {
        if (brandLogo != null) {
            // `matchParentSize` en vez de `fillMaxSize`: necesita medirse
            // DESPUÉS de que el Box ya sabe su tamaño final (el que
            // termina definiendo el contenido de la Column de abajo),
            // no antes — si no, el logo forzaría su propio tamaño y
            // rompería el `widthIn(min = ...)` de la tarjeta.
            //
            // `ContentScale.Crop` sin padding (a diferencia del
            // watermark chico y tenue de antes): el pedido explícito
            // fue que el fondo ocupe TODO el marco de la tarjeta, borde
            // a borde, no un ícono flotando en el centro. El `.clip`
            // que ya tiene el `Box` exterior (línea de arriba) se
            // encarga de redondear las esquinas del logo junto con las
            // del resto de la tarjeta, así que no hace falta repetir el
            // `RoundedCornerShape` acá.
            //
            // Alpha en 0.28f (bajado de 0.55f): pedido explícito de que
            // el ícono de proporción y el texto de la tarjeta destaquen
            // por sobre el fondo de marca, no al revés — con 0.55f el
            // logo de fondo competía demasiado en contraste.
            Image(
                painter = painterResource(id = brandLogo),
                contentDescription = null,
                alpha = 0.28f,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Box y glifo subidos de 28dp/22dp a 36dp/28dp (y el "+" de
            // Personalizado de 18dp a 22dp, misma proporción) — pedido
            // explícito de que el ícono de cada lienzo se vea más
            // grande dentro de su marco.
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (isCustomEntry) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = null,
                        tint = glyphTint,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    AspectRatioGlyph(
                        widthPx = aspectWidthPx,
                        heightPx = aspectHeightPx,
                        tint = glyphTint,
                        boxSize = 28.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            // Badge de "seleccionado" en la esquina superior derecha del
            // MARCO de la tarjeta entera, no del ícono de proporción —
            // antes vivía adentro del Box de 28dp del ícono, así que
            // quedaba flotando cerca del centro de la tarjeta en vez de
            // anclado a su esquina, un detalle que se nota sobre todo en
            // tarjetas anchas (Feed cuadrado, Personalizado). Es un
            // hijo directo del Box exterior (no del Column de
            // ícono+texto) justamente para poder alinearlo contra ESE
            // marco con `Alignment.TopEnd` + `padding`, en vez de
            // heredar el centrado del Column.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(selectedCheckColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check_simple),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Degradado en los bordes de una fila con scroll horizontal, visible
 * SOLO del lado en que de verdad hay más contenido para deslizar —
 * [ScrollState.canScrollBackward]/[canScrollForward] ya traen esa
 * información resuelta, no hace falta calcularla a mano contra
 * `maxValue`. Corrige el hallazgo 3.3 de la auditoría: antes tanto la
 * fila de presets como la de fps se cortaban en seco sin ningún aviso
 * (en la captura original, "YouTube · horizontal" no se veía y
 * "144fps" quedaba cortado a la mitad, "14", sin ninguna pista de que
 * hubiera que deslizar).
 *
 * Implementado con `BlendMode.DstIn` sobre un `graphicsLayer` en modo
 * offscreen (el patrón estándar de "fading edge" en Compose) en vez de
 * dibujar un rectángulo del color de fondo encima: así el degradado
 * funciona sin importar qué haya detrás (no depende de adivinar el
 * color exacto de la `Surface` del diálogo con su `tonalElevation`).
 */
private fun Modifier.horizontalFadingEdges(
    scrollState: ScrollState,
    edgeWidth: Dp = 20.dp
): Modifier = composed {
    val edgeWidthPx = with(LocalDensity.current) { edgeWidth.toPx() }
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (scrollState.canScrollBackward) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = edgeWidthPx
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            if (scrollState.canScrollForward) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - edgeWidthPx,
                        endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
}

/**
 * Diálogo "Nuevo proyecto": nombre, formato de salida y cuadros por
 * segundo — quedan fijados desde el arranque porque son propiedades del
 * PROYECTO en sí (afectan todo el timeline, no solo el archivo final),
 * a diferencia de la calidad de bitrate/resolución, que sigue siendo
 * pura configuración de exportación y se elige recién al exportar (ver
 * el ícono de exportar dentro del editor).
 *
 * La duración YA NO se elige acá: todo proyecto arranca en 1 minuto,
 * oculto para el usuario, y crece solo a medida que hace falta más
 * espacio — ver com.yeivikas.olyzecs.engine.timeline.TimelineDurationManager.
 * Sacar ese control de este diálogo es a propósito: mantiene la creación
 * de un proyecto tan simple como en un editor de video profesional, sin
 * pedirle al usuario un dato que la propia app puede administrar mejor.
 *
 * Ver ADR-005, Fase E: el selector de formato ya no ofrece el enum
 * legacy de 3 valores fijos ([AspectRatioPreset]) — usa el
 * [FormatCatalog] real (pestañas por categoría, cada una con sus
 * presets en una fila horizontal deslizable, ver [FormatCategoryTabs]/
 * [FormatPresetCard]) más la opción "Personalizado", que abre
 * [CustomCanvasDialog] para introducir un tamaño propio validado contra
 * [CanvasLimits]. El resultado siempre es un [CanvasSpec] concreto en
 * píxeles, sea cual sea el camino elegido.
 */
@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, canvas: CanvasSpec, fps: Int) -> Unit,
    // Auditoría del selector de formato, hallazgo 3.8: antes `catalog` y
    // `canvasFactory` se instanciaban a fuego dentro del Composable
    // (`remember { StaticFormatCatalog }` / `remember { DefaultCanvasFactory() }`),
    // sin forma de reemplazarlos por un catálogo de prueba en un test de
    // Compose UI. Ambos son `stateless` (ver KDoc de StaticFormatCatalog/
    // DefaultCanvasFactory), así que recibirlos como parámetro con default
    // no cambia el comportamiento para quien llama a este diálogo sin
    // pasar nada — solo habilita testear con un `FormatCatalog`/`CanvasFactory`
    // falso el día que haga falta.
    catalog: FormatCatalog = StaticFormatCatalog,
    canvasFactory: CanvasFactory = DefaultCanvasFactory()
) {
    var name by remember { mutableStateOf("") }

    // Mismo preset que ya era el default antes de esta fase (REELS,
    // 1080×1920) — no se cambia el comportamiento de quien no toca nada
    // en el diálogo, solo la forma en que se representa internamente.
    val defaultPreset = remember { catalog.findById("social.vertical.reels")!! }
    var selectedPresetId by remember { mutableStateOf<String?>(defaultPreset.id) }
    var canvas by remember { mutableStateOf(canvasFactory.fromPreset(defaultPreset)) }
    var showCustomDialog by remember { mutableStateOf(false) }

    // Pestaña de categoría activa (ver auditoría posterior a la Fase G):
    // arranca en la categoría del preset seleccionado por defecto
    // (SOCIAL), independiente de qué preset esté elegido — cambiar de
    // pestaña solo cambia qué presets se VEN, nunca toca `selectedPresetId`.
    var selectedCategory by remember { mutableStateOf(defaultPreset.category) }

    var fps by remember { mutableStateOf(DEFAULT_PROJECT_FPS) }
    var showFpsHelp by remember { mutableStateOf(false) }

    // Opciones REALES para este equipo: estándares de siempre (24/30/60)
    // más lo que la pantalla física soporte por encima de eso (90/120/144
    // según el modelo) — nunca se ofrece un fps que la pantalla del
    // dispositivo no pueda mostrar de verdad en el preview en vivo. Ver
    // DisplayRefreshRate para el porqué no existe un "máximo universal".
    val context = LocalContext.current
    val availableFpsOptions = remember(context) { DisplayRefreshRate.availableProjectFps(context) }
    val deviceMaxRefreshHz = remember(context) { DisplayRefreshRate.maxSupportedRefreshRateHz(context) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 420.dp)
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Nuevo proyecto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(DEFAULT_PROJECT_NAME) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text("Formato del lienzo", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // Auditoría del selector de formato — hallazgo 3.1: antes
                // las pestañas de categoría usaban el MISMO `SelectableChip`
                // que los presets de abajo (mismo color, misma forma),
                // así que nivel-1 ("Redes sociales"/"Formatos generales") y
                // nivel-2 ("Reels", "TikTok"...) eran visualmente
                // indistinguibles. `FormatCategoryTabs` (ver más abajo) es
                // un segmented control real —pastilla con fondo sólido
                // sobre un riel translúcido— que no se parece a una
                // tarjeta de preset ni por asomo. Sigue leyendo
                // `catalog.categories()` igual que antes: con 2 categorías
                // pobladas hoy se reparte 50/50, y sigue funcionando
                // igual si CINEMATIC se puebla en el futuro (Fase 2, ADR-005 D4).
                FormatCategoryTabs(
                    categories = catalog.categories(),
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Presets de la categoría activa + "Personalizado" al
                // PRINCIPIO de la MISMA fila, delante del primer preset
                // (ronda 2 de la auditoría de UX: el usuario pidió
                // explícitamente que "Personalizado" quede antes de
                // "Reels", no al final — antes de eso, "Personalizado"
                // era un chip aparte con `fillMaxWidth()`, el elemento
                // más grande del diálogo pese a ser la opción menos
                // usada, ver hallazgo 3.2 de la auditoría original). Acá
                // comparte tamaño y forma con el resto de las tarjetas
                // (`FormatPresetCard`), solo que con ícono "+" en vez de
                // un glifo de proporción (mismo componente, flag
                // `isCustomEntry`) — no representa un tamaño fijo, así
                // que no tiene sentido mostrarle una forma. Va PRIMERO y
                // fuera del `forEach` a propósito: es conceptualmente
                // independiente de la categoría activa (no pertenece a
                // SOCIAL ni a GENERAL, es una tercera vía ortogonal a
                // "elegir un preset ya armado"), así que aparece igual
                // sin importar qué pestaña esté abierta — y si el
                // usuario lo elige y después cambia de pestaña, sigue
                // marcado como activo (el subtítulo de abajo, "WxH px ·
                // personalizado", ya deja esto claro en cualquier caso).
                // Sigue siendo una fila `Row` simple con scroll
                // horizontal (no `LazyRow`, mismos motivos que ya
                // documentaba la versión anterior: pocos ítems fijos, no
                // una lista sin límite) pero ahora envuelta en
                // `horizontalFadingEdges` (hallazgo 3.3) para que el
                // usuario vea, con un degradado real, que hay más
                // contenido para deslizar — en la captura original
                // "YouTube · horizontal" quedaba directamente invisible,
                // sin ninguna pista de que existía.
                val presetsScrollState = rememberScrollState()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .horizontalScroll(presetsScrollState)
                        .horizontalFadingEdges(presetsScrollState)
                ) {
                    FormatPresetCard(
                        label = "Personalizado",
                        aspectWidthPx = canvas.widthPx,
                        aspectHeightPx = canvas.heightPx,
                        selected = selectedPresetId == null,
                        isCustomEntry = true,
                        onClick = { showCustomDialog = true }
                    )
                    catalog.presetsIn(selectedCategory).forEach { preset ->
                        FormatPresetCard(
                            label = preset.label,
                            aspectWidthPx = preset.format.widthPx,
                            aspectHeightPx = preset.format.heightPx,
                            selected = selectedPresetId == preset.id,
                            brandLogo = brandLogoFor(preset.id),
                            onClick = {
                                selectedPresetId = preset.id
                                canvas = canvasFactory.fromPreset(preset)
                            }
                        )
                    }
                }

                Text(
                    text = selectedPresetId
                        ?.let { catalog.findById(it)?.subtitle }
                        ?: "${canvas.widthPx} × ${canvas.heightPx} px · personalizado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Cuadros por segundo (fps)", style = MaterialTheme.typography.labelMedium)
                    // Ícono circular de ayuda, mismo patrón que se ve en apps
                    // y programas profesionales al lado de un título técnico:
                    // un "?" chico que abre una ventana con contexto, sin
                    // ensuciar el título en sí ni ocupar espacio permanente.
                    IconButton(
                        onClick = { showFpsHelp = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_help),
                            contentDescription = "Ayuda sobre fps",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Mismo hallazgo 3.3 que la fila de presets: esta fila
                // también se recortaba sin aviso (el "144fps" de la
                // captura quedaba cortado a la mitad, "14"). Mismo arreglo:
                // `horizontalFadingEdges` sobre su propio `ScrollState`.
                val fpsScrollState = rememberScrollState()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(fpsScrollState)
                        .horizontalFadingEdges(fpsScrollState)
                ) {
                    availableFpsOptions.forEach { preset ->
                        SelectableChip(
                            label = "${preset}fps",
                            selected = preset == fps,
                            onClick = { fps = preset },
                            modifier = Modifier.widthIn(min = 64.dp)
                        )
                    }
                }
                Text(
                    when {
                        fps > deviceMaxRefreshHz ->
                            "El video final SÍ va a tener $fps fps, pero la pantalla de este equipo " +
                                "(máx. ${deviceMaxRefreshHz}Hz) no puede refrescar el preview en vivo tan rápido — se va a ver más fluido recién al exportar."
                        fps > 30 -> "Movimiento más fluido — archivo final más pesado"
                        else -> "Estándar, el más liviano para compartir"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(name.trim(), canvas, fps) }) { Text("Crear") }
                }
            }
        }
    }

    if (showFpsHelp) {
        FpsHelpDialog(onDismiss = { showFpsHelp = false })
    }

    if (showCustomDialog) {
        CustomCanvasDialog(
            factory = canvasFactory,
            onDismiss = { showCustomDialog = false },
            onConfirm = { customCanvas ->
                canvas = customCanvas
                selectedPresetId = null
                showCustomDialog = false
            }
        )
    }
}

/**
 * Diálogo "Introduce el tamaño en píxeles" del Canvas Custom (ver
 * ADR-005, Fase E). Valida con [CanvasFactory.custom] — nunca reimplementa
 * el rango acá — y muestra el mensaje de error real que devuelve
 * `Result.failure` si el usuario ingresa algo fuera de [CanvasLimits].
 */
@Composable
private fun CustomCanvasDialog(
    factory: CanvasFactory,
    onDismiss: () -> Unit,
    onConfirm: (CanvasSpec) -> Unit
) {
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 360.dp)
            ) {
                Text("Tamaño personalizado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Introduce el tamaño en píxeles (mín. ${CanvasLimits.MIN_DIMENSION_PX}, máx. ${CanvasLimits.MAX_DIMENSION_PX}).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it.filter(Char::isDigit) },
                        label = { Text("Ancho (px)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter(Char::isDigit) },
                        label = { Text("Alto (px)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                errorMessage?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val width = widthText.toIntOrNull()
                        val height = heightText.toIntOrNull()
                        if (width == null || height == null) {
                            errorMessage = "Ingresá ancho y alto en píxeles."
                            return@Button
                        }
                        factory.custom(width, height).fold(
                            onSuccess = { spec ->
                                errorMessage = null
                                onConfirm(spec)
                            },
                            onFailure = { error -> errorMessage = error.message }
                        )
                    }) { Text("Usar este tamaño") }
                }
            }
        }
    }
}

/**
 * Ventana flotante de ayuda que dispara el ícono "?" al lado del título
 * "Cuadros por segundo (fps)" — mismo patrón que se ve en apps y software
 * profesional: un ícono chico junto al título técnico que, al tocarlo,
 * despliega contexto/consejo sin ensuciar la UI principal en ningún otro
 * momento.
 */
@Composable
private fun FpsHelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RectangleShape,
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(20.dp).widthIn(max = 420.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_help),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Cuadros por segundo (fps)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                FpsHelpBullet("El video final SIEMPRE se exporta al fps exacto que elijas acá — eso no depende de tu pantalla ni de tu equipo.")
                FpsHelpBullet("Las opciones que aparecen (24/30/60 y, si tu equipo lo soporta, 90/120/144) se calculan según lo que la pantalla real de tu dispositivo puede refrescar.")
                FpsHelpBullet("El PREVIEW en vivo dentro del editor sí depende de esa pantalla: por más fps que elijas, nunca se va a ver más fluido que el refresco máximo real del panel.")
                FpsHelpBullet("¿No ves el fps máximo que esperabas, o el preview no se siente tan fluido? Revisá Ajustes del sistema → Pantalla → Velocidad de actualización (o \"Frecuencia de actualización\" / \"Suavidad de pantalla\", el nombre cambia según el fabricante) y activá el modo más alto disponible ahí.")

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("Entendido") }
                }
            }
        }
    }
}

@Composable
private fun FpsHelpBullet(text: String) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp, end = 10.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** mm:ss para la duración del proyecto (hasta 7 min); segundos con un decimal por debajo de 1 minuto. */
private fun formatProjectDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return if (totalSeconds < 60) {
        "%.1fs".format(ms / 1000f)
    } else {
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
