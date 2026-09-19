package com.yeivikas.olyzecs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeivikas.olyzecs.data.DEFAULT_PROJECT_NAME
import com.yeivikas.olyzecs.data.LayerRepository
import com.yeivikas.olyzecs.data.ProjectStorage
import com.yeivikas.olyzecs.engine.scene.CanvasSpec
import com.yeivikas.olyzecs.ui.BackgroundAdjustDialog
import com.yeivikas.olyzecs.ui.EditorScreen
import com.yeivikas.olyzecs.ui.ProjectsScreen
import com.yeivikas.olyzecs.ui.theme.OlyzeGradient
import com.yeivikas.olyzecs.ui.theme.OlyzeTheme
import com.yeivikas.olyzecs.viewmodel.EditorViewModel
import com.yeivikas.olyzecs.viewmodel.EditorViewModelFactory
import com.yeivikas.olyzecs.viewmodel.ProjectsViewModel
import com.yeivikas.olyzecs.viewmodel.ProjectsViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mime type propio del archivo exportado ".olycs" — también declarado en AndroidManifest.xml. */
private const val OLYCS_MIME_TYPE = "application/x-olycs"

/**
 * Punto de entrada de la UI. Maneja una navegación deliberadamente simple
 * de dos pantallas (sin agregar Navigation Compose como dependencia nueva):
 * `openProjectId == null` → "Mis proyectos"; si no, el editor de ese
 * proyecto. `openProjectId` se guarda con `rememberSaveable` para que,
 * si el sistema mata el proceso en background y lo recrea, el usuario
 * vuelva exactamente al proyecto que tenía abierto, no a la lista.
 */
class MainActivity : ComponentActivity() {

    // Los pickers del sistema (SAF) se registran una sola vez a nivel de
    // Activity; qué hacer con el resultado se decide en el momento del
    // lanzamiento vía estas referencias, para poder apuntar siempre al
    // ViewModel del proyecto que esté abierto en ese instante.
    private var onImagesPicked: ((List<Uri>) -> Unit)? = null
    private var onBackgroundPicked: ((Uri?) -> Unit)? = null
    private var onReplacementPicked: ((Uri?) -> Unit)? = null
    private var onAudioPicked: ((Uri?) -> Unit)? = null
    private var onCoverPicked: ((Uri?) -> Unit)? = null
    // Foto elegida para una casilla de elenco/personajes del panel
    // "Información del proyecto" — ver pickCastPhotoLauncher más abajo.
    private var onCastPhotoPicked: ((Uri?) -> Unit)? = null

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> onImagesPicked?.invoke(uris) }

    private val pickBackgroundLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onBackgroundPicked?.invoke(uri) }

    private val pickReplacementLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onReplacementPicked?.invoke(uri) }

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onAudioPicked?.invoke(uri) }

    // Portada personalizada de un proyecto, elegida desde "Mis proyectos"
    // (menú "⋮" → Portada), no desde el editor — por eso vive acá al lado
    // de los demás pickers de nivel Activity en vez de en EditorScreen.
    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onCoverPicked?.invoke(uri) }

    // Foto de una casilla de elenco/personajes (panel "Información del
    // proyecto", dentro del editor) — mismo tipo de picker que
    // pickReplacementLauncher, pero con su propio callback para no
    // pisarse con el reemplazo de imagen de una capa.
    private val pickCastPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onCastPhotoPicked?.invoke(uri) }

    // --- "Elige un fondo" del diálogo "Nuevo proyecto" (ver
    // BackgroundPickerSection.kt / ProjectsScreen.CreateProjectDialog) ---
    // Callbacks y launchers propios, separados de pickBackgroundLauncher/
    // onBackgroundPicked de arriba (esos son del botón "Importar fondo"
    // DENTRO del editor, con un proyecto ya abierto) — mismo criterio que
    // pickCoverLauncher vs pickReplacementLauncher: un launcher dedicado
    // por contexto, aunque el tipo de picker sea el mismo, para que dos
    // flujos nunca puedan pisarse el callback del otro.
    private var onNewProjectBackgroundImagePicked: ((Uri) -> Unit)? = null
    private var onNewProjectBackgroundPhotoTaken: ((Uri) -> Unit)? = null
    // Uri del archivo temporal (cache/camera_captures/, ver file_paths.xml)
    // que se le pasa a ActivityResultContracts.TakePicture ANTES de abrir
    // la cámara — TakePicture solo devuelve un Boolean de éxito, no el
    // Uri, así que hay que recordar acá cuál era para poder usarlo cuando
    // el callback avisa que sí se guardó la foto.
    private var pendingBackgroundCameraUri: Uri? = null

    // Auditoría de septiembre 2026, hallazgo 4: este picker puntual (solo
    // el de "Elige un fondo" del diálogo "Nuevo proyecto") pasa de SAF
    // (`OpenDocument`) al Photo Picker nativo de Android
    // (`PickVisualMedia`, API de plataforma desde Android 11 vía módulo
    // de Google Play system update, nativo desde Android 13+). No
    // requiere el permiso de almacenamiento en tiempo de ejecución que sí
    // pedía SAF, y ofrece la UI con pestañas "Fotos"/"Colecciones" que el
    // cliente pidió explícitamente como referencia. Los demás 7 launchers
    // de tipo OpenDocument()/OpenMultipleDocuments() de esta Activity NO
    // se tocan — el pedido es específico a este selector.
    //
    // Nota de compatibilidad: los Uri que entrega el Photo Picker son de
    // tipo `content://media/picker/...`, de solo lectura temporal — no
    // soportan `takePersistableUriPermission` y lanzan `SecurityException`
    // si se intenta (ver `LayerRepository.decode()`). Eso ya está
    // contemplado ahí como caso "no grave" (se loguea y se sigue
    // adelante), porque el permiso de un Uri de Photo Picker dura
    // mientras la app lo tiene en memoria/lo usa en el momento — más que
    // suficiente para el uso que se le da acá (leerlo una vez, generar la
    // capa de fondo, nunca volver a abrir ese Uri después).
    private val pickNewProjectBackgroundImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onNewProjectBackgroundImagePicked?.invoke(uri) }

    private val takeNewProjectBackgroundPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingBackgroundCameraUri
        pendingBackgroundCameraUri = null
        if (success && uri != null) onNewProjectBackgroundPhotoTaken?.invoke(uri)
        // success == false (el usuario canceló la captura desde la propia
        // app de cámara): no se llama a ningún callback — el diálogo
        // "Nuevo proyecto" simplemente se queda con lo que ya tenía elegido
        // (el chroma key por defecto, u otro fondo elegido antes), en vez
        // de quedar en un estado a medio camino.
    }

    // El permiso de CÁMARA es peligroso — se pide en tiempo de ejecución
    // recién cuando el usuario toca el ícono de cámara en "Elige un
    // fondo", nunca antes (no tiene sentido pedirlo al abrir la app si
    // todavía no lo necesita para nada).
    private val requestBackgroundCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchBackgroundCameraCapture()
        } else {
            Toast.makeText(
                this, "Sin permiso de cámara no se puede tomar la foto de fondo", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun launchBackgroundCameraCapture() {
        val dir = java.io.File(cacheDir, "camera_captures").apply { mkdirs() }
        val file = java.io.File(dir, "background_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingBackgroundCameraUri = uri
        takeNewProjectBackgroundPhotoLauncher.launch(uri)
    }

    // Uri de un archivo ".olycs" recibido desde AFUERA de la app —
    // alguien lo compartió por WhatsApp/Telegram/Drive/correo/etc. y el
    // usuario tocó "Abrir con Olyze", o lo abrió directo desde el
    // explorador de archivos. Se guarda como propiedad de clase (no dentro
    // de setContent) para que tanto onCreate como onNewIntent puedan
    // completarla por igual — Compose la observa como cualquier State.
    private var incomingImportUri: Uri? by mutableStateOf(null)

    /**
     * Extrae el Uri del archivo compartido/abierto, sin importar si llegó
     * como ACTION_VIEW (abrir directo, típico al tocar el archivo en un
     * explorador o en un chat) o ACTION_SEND (otra app lo compartió hacia
     * Olyze desde su propia hoja "Compartir").
     */
    private fun extractImportUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode="singleTask" (ver AndroidManifest.xml) hace que la
        // Activity ya viva en memoria reciba acá el intent nuevo, en vez de
        // levantar una segunda instancia — así importar un archivo con la
        // app ya abierta funciona igual que con la app cerrada.
        extractImportUri(intent)?.let { incomingImportUri = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pide el modo de refresco más alto que soporte la pantalla (ver
        // DisplayRefreshRate) — sin esto el preview en vivo queda atado al
        // default del sistema (normalmente 60Hz) aunque el proyecto esté
        // configurado a 90/120fps y el panel del equipo lo soporte.
        com.yeivikas.olyzecs.platform.DisplayRefreshRate.applyHighestRefreshRate(this)

        val layerRepository = LayerRepository(applicationContext)
        val projectStorage = ProjectStorage(applicationContext)
        extractImportUri(intent)?.let { incomingImportUri = it }

        setContent {
            var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
            var projectsRefreshKey by remember { mutableStateOf(0) }
            var pendingProjectName by rememberSaveable { mutableStateOf(DEFAULT_PROJECT_NAME) }
            // Ver ADR-005, Fase E: Canvas real elegido en el
            // `CreateProjectDialog` (grid de presets/Custom). Se guarda
            // como 3 primitivos por separado (no el `CanvasSpec` como tal)
            // porque `rememberSaveable` necesita un `Saver` explícito para
            // tipos que no sean Parcelable/Serializable/primitivos — mismo
            // criterio ya usado en el resto de este bloque de estado.
            // Default = mismas dimensiones que ya tenía REELS, para no
            // cambiar el comportamiento de nadie que no haya interactuado
            // todavía con el diálogo nuevo.
            var pendingProjectCanvasWidthPx by rememberSaveable { mutableStateOf(1080) }
            var pendingProjectCanvasHeightPx by rememberSaveable { mutableStateOf(1920) }
            var pendingProjectCanvasOriginPresetId by rememberSaveable {
                mutableStateOf<String?>("social.vertical.reels")
            }
            // La duración ya no se elige al crear el proyecto — arranca
            // fija en 1 minuto y crece sola (ver TimelineDurationManager),
            // así que ya no hace falta guardar ningún valor "pendiente" acá.
            var pendingProjectFps by rememberSaveable { mutableStateOf(30) }
            // "Elige un fondo" (ver BackgroundPickerSection.kt): el Uri ya
            // resuelto (color sólido generado como bitmap, imagen o foto)
            // que CreateProjectDialog entrega al confirmar. Uri es
            // Parcelable, así que rememberSaveable lo persiste sin
            // necesitar un Saver a mano, igual que cualquier otro tipo
            // simple de este bloque de estado.
            var pendingProjectBackgroundUri by rememberSaveable { mutableStateOf<Uri?>(null) }

            OlyzeTheme {
                // Fondo de marca único para TODA la app: degradado morado
                // (dominante) → azul. Surface se deja transparente para
                // que ninguna pantalla lo tape con un color sólido; cada
                // Scaffold (ProjectsScreen, EditorScreen) usa
                // containerColor = Color.Transparent para heredarlo.
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OlyzeGradient),
                    color = Color.Transparent,
                    contentColor = Color.White
                ) {
                    // Importación de un ".olycs" recibido de afuera: se
                    // procesa acá (nivel raíz de la navegación) para que
                    // funcione sin importar si en ese momento se está
                    // viendo "Mis proyectos" o el editor de otro proyecto.
                    // Al terminar, abre directo el proyecto recién
                    // importado — igual que crear uno nuevo.
                    val pendingImportUri = incomingImportUri
                    LaunchedEffect(pendingImportUri) {
                        val uri = pendingImportUri ?: return@LaunchedEffect
                        incomingImportUri = null
                        val importedId = projectStorage.importProjectZip(uri)
                        if (importedId != null) {
                            projectsRefreshKey++
                            openProjectId = importedId
                            Toast.makeText(
                                this@MainActivity, "Proyecto importado ✅ — ya lo podés editar", Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Ese archivo no es un proyecto de Olyze Creation Studio válido (.olycs)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val projectId = openProjectId
                    if (projectId == null) {
                        val projectsViewModel: ProjectsViewModel =
                            viewModel(factory = ProjectsViewModelFactory(projectStorage))
                        ProjectsScreen(
                            viewModel = projectsViewModel,
                            refreshKey = projectsRefreshKey,
                            onOpenProject = { id -> openProjectId = id },
                            onCreateProject = { id, name, canvas, fps, backgroundUri ->
                                pendingProjectCanvasWidthPx = canvas.widthPx
                                pendingProjectCanvasHeightPx = canvas.heightPx
                                pendingProjectCanvasOriginPresetId = canvas.originPresetId
                                pendingProjectFps = fps
                                pendingProjectBackgroundUri = backgroundUri
                                if (name.isNotBlank()) {
                                    // El usuario SÍ escribió un nombre en el diálogo
                                    // "Nuevo proyecto": se usa tal cual, no hace
                                    // falta resolver nada.
                                    pendingProjectName = name
                                    openProjectId = id
                                } else {
                                    // No escribió nombre: se resuelve el próximo
                                    // "ProjectNN" LIBRE (ver
                                    // ProjectStorage.nextAvailableDefaultName) ANTES
                                    // de abrir el editor, para que el título ya
                                    // aparezca correcto y único desde el primer
                                    // instante — nada de mostrarlo vacío o con un
                                    // "Project01" que capaz ya usa otro proyecto,
                                    // como haría una app poco prolija.
                                    lifecycleScope.launch {
                                        pendingProjectName = projectStorage.nextAvailableDefaultName()
                                        openProjectId = id
                                    }
                                }
                            },
                            onPickCoverImage = { onPicked ->
                                onCoverPicked = { uri -> if (uri != null) onPicked(uri) }
                                pickCoverLauncher.launch(arrayOf("image/*"))
                            },
                            onPickBackgroundImage = { onPicked ->
                                onNewProjectBackgroundImagePicked = onPicked
                                pickNewProjectBackgroundImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onTakeBackgroundPhoto = { onCaptured ->
                                onNewProjectBackgroundPhotoTaken = onCaptured
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity, Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    launchBackgroundCameraCapture()
                                } else {
                                    requestBackgroundCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onShareProject = { id, name ->
                                lifecycleScope.launch {
                                    val zipFile = projectStorage.exportProjectZip(id)
                                    if (zipFile == null) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "No se pudo preparar \"$name\" para compartir",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        this@MainActivity, "$packageName.fileprovider", zipFile
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = OLYCS_MIME_TYPE
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, name)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Te comparto mi proyecto de Olyze \"$name\" — abrilo con la app Olyze para seguir editándolo en tu teléfono."
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    // Intent.createChooser lista TODAS las apps instaladas
                                    // que puedan recibir un archivo adjunto — WhatsApp,
                                    // Telegram, Instagram, Gmail, Drive, Bluetooth, etc. —
                                    // sin tener que integrar cada red social a mano.
                                    startActivity(Intent.createChooser(sendIntent, "Compartir \"$name\""))
                                }
                            }
                        )
                    } else {
                        // Mesh3DApi/AnimationApi se construyen ACÁ (antes del factory)
                        // porque ahora EditorViewModel las recibe por constructor de
                        // verdad (Mesh3D→EliNer / Animation→EliNer: renderExtrude3D y
                        // step/computeOutputDurationMs/speedAt ya no llaman al motor
                        // directo, delegan acá) — se reusan las MISMAS instancias para
                        // el resto del wiring de EliNer API más abajo, en vez de crear
                        // una segunda de cada una (ninguna de las dos tiene estado,
                        // pero componer una sola instancia por composition root es el
                        // criterio ya establecido para el resto de los *ApiImpl).
                        val mesh3DApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.mesh3d.Mesh3DApiImpl()
                        }
                        val animationApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.animation.AnimationApiImpl()
                        }
                        // Mismo criterio que mesh3DApi/animationApi arriba, ahora
                        // para Distorsión (Liquify): se construye acá para que
                        // EditorViewModel la reciba por constructor de verdad, y se
                        // reusa esta MISMA instancia para el resto del wiring de
                        // EliNer API más abajo (eliNerDistortionApi).
                        val distortionApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.distortion.DistortionApiImpl()
                        }
                        // Ver ADR-005, Fase E: `initialAspect` ya no lleva
                        // el dato real del proyecto nuevo (queda en su
                        // default REELS, campo legacy separado — ver KDoc
                        // de `EditorUiState.exportAspect`); el Canvas real
                        // elegido en `CreateProjectDialog` viaja en
                        // `initialCanvas`.
                        val pendingProjectCanvas = remember(
                            pendingProjectCanvasWidthPx, pendingProjectCanvasHeightPx, pendingProjectCanvasOriginPresetId
                        ) {
                            CanvasSpec(
                                widthPx = pendingProjectCanvasWidthPx,
                                heightPx = pendingProjectCanvasHeightPx,
                                originPresetId = pendingProjectCanvasOriginPresetId
                            )
                        }
                        val factory = remember(projectId, pendingProjectCanvas) {
                            EditorViewModelFactory(
                                layerRepository, projectStorage, projectId,
                                pendingProjectName, initialCanvas = pendingProjectCanvas, initialFps = pendingProjectFps,
                                mesh3DApi = mesh3DApi, animationApi = animationApi, distortionApi = distortionApi
                            )
                        }
                        val viewModel: EditorViewModel = viewModel(factory = factory, key = projectId)

                        // --- EliNer API (Fase 1.4 + Mesh3D→EliNer + Animation→EliNer +
                        // Fase 4.1 Distortion→EliNer): wiring de los 8 dominios ya
                        // conectables de los 9 que define EliNerApi (todos menos
                        // `render`, que necesita una superficie GL viva — solo existe
                        // dentro de GLPreview.kt, capa de UI, fuera de alcance).
                        // `viewModel` ya implementa ActiveProjectReader/
                        // ActiveProjectMutator; acá solo se lo pasa como tal a cada
                        // *ApiImpl — MainActivity sigue siendo el único lugar que
                        // construye estas piezas (composition root), igual que ya hace
                        // con layerRepository/projectStorage.
                        // Animation, Export, Mesh3D y Distortion son, por ahora, los
                        // únicos 4 de los 8 dominios conectados que `EditorViewModel`
                        // realmente USA (vía los parámetros `mesh3DApi`/`animationApi`/
                        // `distortionApi` del factory, arriba, y el `ExportApiImpl`
                        // consumido por `exportVideo`) — Layer, Camera, Timeline y Audio
                        // siguen preparados pero sin consumidor externo real todavía.
                        val eliNerLayerApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.scene.LayerApiImpl(viewModel, viewModel, layerRepository)
                        }
                        val eliNerCameraApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.camera.CameraApiImpl(viewModel, viewModel)
                        }
                        val eliNerAnimationApi = animationApi
                        val eliNerTimelineApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.timeline.TimelineApiImpl(applicationContext, viewModel, viewModel)
                        }
                        val eliNerAudioApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.audio.AudioApiImpl(applicationContext, viewModel, viewModel, projectStorage)
                        }
                        // FASE 4.2 (AUDITORÍA — corrección real, ver punto 10 del
                        // prompt maestro): antes había acá una segunda instancia de
                        // `ExportApiImpl(applicationContext, viewModel)` (`eliNerExportApi`)
                        // que NUNCA se usaba — ni se pasaba a `EditorScreen`, ni a
                        // ningún otro lado; era pura ceremonia de "wiring uniforme
                        // de los 8 dominios" sin ningún consumidor real. El único
                        // camino de exportación real de la app sigue siendo
                        // `EditorViewModel.exportVideo()`, que ya construye —y
                        // cachea— su propia instancia de `ExportApiImpl` de forma
                        // perezosa (`cachedExportApi`, en EditorViewModel.kt) porque
                        // `ExportApiImpl` es stateless y esa es la única instancia que
                        // hace falta. Se elimina la duplicada de acá: no cambia
                        // ningún comportamiento (la variable no se leía en ningún
                        // lado), solo saca una fuente de ownership redundante.
                        val eliNerMesh3DApi = mesh3DApi
                        // Fase 4.1: DistortionApi ya es parte de la interfaz EliNerApi
                        // (ver api/EliNerApi.kt) — acá se sigue reusando la MISMA
                        // instancia que ya recibe EditorViewModel por constructor, igual
                        // que el resto de los dominios de esta sección.
                        val eliNerDistortionApi = distortionApi

                        // Se llama cada vez que se (re)entra a ESTE projectId. Como el
                        // ViewModel puede venir reciclado del ViewModelStore de la
                        // Activity, esto es lo que garantiza que el proyecto se vea
                        // siempre desde el principio y pausado al abrirlo, nunca a
                        // mitad de una reproducción que quedó corriendo en segundo
                        // plano — y, por el mismo motivo (ViewModel reciclado), que el
                        // NOMBRE mostrado y el que se autoguarda estén siempre al día
                        // con lo último escrito desde "Mis proyectos" (renombrar), en
                        // vez de con lo que este ViewModel tenía en memoria de una
                        // visita anterior — ver refreshProjectNameFromDisk().
                        LaunchedEffect(projectId) {
                            viewModel.resetPlaybackState()
                            viewModel.refreshProjectNameFromDisk()
                        }

                        // "Elige un fondo" (ver BackgroundPickerSection.kt): se
                        // aplica UNA sola vez, justo al abrir el proyecto recién
                        // creado, reutilizando tal cual `importAsBackground`
                        // (la misma función que consume el paso de ajuste del
                        // botón "Importar fondo" del editor — ver ADR-010, más
                        // abajo) — así el fondo elegido en "Nuevo proyecto"
                        // termina siendo una capa real, igual de editable que
                        // cualquier otra. Se limpia a null enseguida: si el
                        // usuario cierra y reabre este mismo proyecto más
                        // adelante, este efecto vuelve a correr (LaunchedEffect
                        // está atado a projectId, que no cambia), pero
                        // `pendingProjectBackgroundUri` ya es null en ese
                        // momento, así que no se reimporta el fondo de nuevo
                        // encima del que el usuario ya haya editado.
                        LaunchedEffect(projectId, pendingProjectBackgroundUri) {
                            val uri = pendingProjectBackgroundUri ?: return@LaunchedEffect
                            pendingProjectBackgroundUri = null
                            viewModel.importAsBackground(uri)
                        }

                        // ADR-010 — "Importar fondo" del editor (proyecto YA
                        // abierto, botón cableado más abajo en
                        // `onImportBackgroundClick`) tenía el MISMO bug que
                        // ADR-006 documentó y cerró para "Nuevo proyecto":
                        // el Uri crudo del picker llegaba directo a
                        // `importAsBackground` sin pasar por
                        // `BackgroundAdjustDialog`, así que cualquier imagen
                        // con una proporción distinta a la del lienzo
                        // dejaba asomando el chroma-key verde alrededor. Es
                        // una segunda puerta de entrada a `importAsBackground`
                        // que ADR-006 nunca llegó a cerrar (esa ronda solo
                        // tocó el flujo de "Nuevo proyecto", en
                        // `ProjectsScreen.kt`). `pendingLiveBackgroundUriToAdjust`
                        // reproduce, para este segundo camino, exactamente el
                        // mismo patrón que `pendingBackgroundUriToAdjust` ya
                        // usa en `CreateProjectDialog`: el Uri crudo queda acá
                        // en lo que `BackgroundAdjustDialog` (ver más abajo)
                        // lo ajusta contra `viewModel.uiState.value.canvas`.
                        var pendingLiveBackgroundUriToAdjust by remember(projectId) {
                            mutableStateOf<Uri?>(null)
                        }
                        val liveBackgroundAdjustScope = rememberCoroutineScope()

                        // Guardado final al pasar a segundo plano (Home, cambio de
                        // app, pantalla bloqueada) — igual que Google Docs, Notion o
                        // CapCut: no hace falta que el usuario toque "atrás" para que
                        // el proyecto quede guardado de verdad. Antes esto solo pasaba
                        // al volver a "Mis proyectos" (ver onBackToProjects más abajo);
                        // si el usuario apretaba Home a mitad de un cambio, quedaba a
                        // merced de que el autoguardado con debounce (900ms) alcanzara
                        // a correr antes de que el sistema pudiera matar el proceso.
                        // ON_STOP (no ON_PAUSE) porque es el punto real de "la app ya
                        // no se ve" — se vacía el título si estaba vacío (default
                        // "Project01") y se refleja en el campo, mismo comportamiento
                        // que un cierre normal (ver EditorViewModel.saveNow).
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(projectId, lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_STOP) {
                                    // FASE 2 — auditoría de lifecycle de
                                    // reproducción (hallazgo confirmado):
                                    // este camino (Home, cambio de app,
                                    // pantalla bloqueada) llamaba SOLO a
                                    // `saveNow()` — a diferencia de
                                    // `onBackToProjects` (ver más abajo),
                                    // que primero llama a
                                    // `resetPlaybackState()`. Si el
                                    // usuario apretaba Home con el preview
                                    // reproduciéndose, el loop de
                                    // reproducción seguía tickeando en
                                    // segundo plano — gastando batería/CPU
                                    // sin que nada se viera en pantalla, y
                                    // de forma inconsistente con el otro
                                    // camino de salida del editor. Mismo
                                    // criterio que `onBackToProjects`:
                                    // frenar reproducción ANTES de guardar.
                                    viewModel.resetPlaybackState()
                                    viewModel.saveNow()
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        EditorScreen(
                            viewModel = viewModel,
                            onBackToProjects = {
                                // Frena cualquier reproducción en curso ANTES de salir: al
                                // no destruirse el ViewModel (mismo motivo de arriba), si no
                                // se para acá el loop de reproducción sigue corriendo en
                                // segundo plano mientras se ve la lista de proyectos.
                                viewModel.resetPlaybackState()
                                // Guarda inmediatamente (sin esperar el debounce normal) antes
                                // de volver a la lista, para que la miniatura y el nombre ya
                                // estén al día apenas se ve "Mis proyectos" de nuevo.
                                viewModel.saveNow {
                                    projectsRefreshKey++
                                    openProjectId = null
                                }
                            },
                            onImportClick = {
                                onImagesPicked = { uris -> if (uris.isNotEmpty()) viewModel.importImages(uris) }
                                pickImagesLauncher.launch(arrayOf("image/png", "image/*"))
                            },
                            onImportBackgroundClick = {
                                // ADR-010: ya NO se llama a
                                // `importAsBackground` directo con el Uri
                                // crudo — se guarda como pendiente y se abre
                                // `BackgroundAdjustDialog` (ver el bloque
                                // junto a `EditorScreen`, más abajo), mismo
                                // criterio que ya usa "Nuevo proyecto"
                                // (ADR-006) para este mismo picker.
                                onBackgroundPicked = { uri -> if (uri != null) pendingLiveBackgroundUriToAdjust = uri }
                                pickBackgroundLauncher.launch(arrayOf("image/*"))
                            },
                            onReplaceImageClick = { layerId ->
                                onReplacementPicked = { uri -> if (uri != null) viewModel.replaceLayerImage(layerId, uri) }
                                pickReplacementLauncher.launch(arrayOf("image/*"))
                            },
                            onImportAudioClick = {
                                onAudioPicked = { uri -> if (uri != null) viewModel.importAudio(this@MainActivity, uri) }
                                pickAudioLauncher.launch(arrayOf("audio/*"))
                            },
                            onPickCastPhotoClick = { slotIndex ->
                                onCastPhotoPicked = { uri -> if (uri != null) viewModel.setCastPhoto(slotIndex, uri) }
                                pickCastPhotoLauncher.launch(arrayOf("image/*"))
                            },
                            // FASE 4.2-R5 (R5.7 del prompt maestro R5 — "API
                            // wiring real"): antes `eliNerLayerApi`/
                            // `eliNerCameraApi`/`eliNerAudioApi` se construían
                            // acá arriba pero NUNCA se le pasaban a
                            // `EditorScreen` — wiring puramente ceremonial,
                            // sin consumidor real. Ahora sí se entregan: son
                            // las mismas instancias, sin duplicar ownership
                            // (composition root sigue siendo este mismo
                            // bloque, como ya documenta el comentario grande
                            // de arriba).
                            layerApi = eliNerLayerApi,
                            cameraApi = eliNerCameraApi,
                            audioApi = eliNerAudioApi
                        )

                        // ADR-010 — paso de ajuste de encuadre para
                        // "Importar fondo" del editor (proyecto ya
                        // abierto): mismo patrón exacto que
                        // `BackgroundAdjustDialog` ya resuelve para "Nuevo
                        // proyecto" en `ProjectsScreen.kt` (ver el bloque
                        // `showBackgroundAdjustDialog` de ese archivo) — acá
                        // se repite a propósito en vez de compartir estado
                        // con aquel, porque son dos ciclos de vida
                        // completamente distintos (diálogo de creación,
                        // todavía sin proyecto, contra un proyecto ya
                        // persistido con su propio `viewModel`). El
                        // `canvas` viene del propio proyecto abierto — a
                        // diferencia de "Nuevo proyecto", acá ya es un
                        // valor fijo conocido (el formato no cambia después
                        // de crear el proyecto), así que se lee una sola
                        // vez de `viewModel.uiState.value` sin necesidad de
                        // observarlo con `collectAsState()`.
                        val liveBackgroundUriToAdjust = pendingLiveBackgroundUriToAdjust
                        if (liveBackgroundUriToAdjust != null) {
                            BackgroundAdjustDialog(
                                imageUri = liveBackgroundUriToAdjust,
                                canvas = viewModel.uiState.value.canvas,
                                onDismiss = { pendingLiveBackgroundUriToAdjust = null },
                                onConfirm = { bitmap ->
                                    // El bitmap ya viene recortado y
                                    // escalado EXACTAMENTE a
                                    // canvas.widthPx×canvas.heightPx (ver
                                    // BackgroundAdjustDialog/ImageFitDialog)
                                    // — acá solo hace falta persistirlo a
                                    // un archivo real, mismo mecanismo que
                                    // ya usa "Nuevo proyecto"
                                    // (`saveBitmapAsLocalUri`), antes de
                                    // seguir camino hacia
                                    // `importAsBackground` con un Uri local
                                    // ya definitivo.
                                    liveBackgroundAdjustScope.launch {
                                        val savedUri = withContext(Dispatchers.IO) {
                                            layerRepository.saveBitmapAsLocalUri(bitmap, "background_image")
                                                .also { bitmap.recycle() }
                                        }
                                        pendingLiveBackgroundUriToAdjust = null
                                        // savedUri == null: fallo de IO real
                                        // (disco lleno, permisos) — ya
                                        // queda logueado dentro de
                                        // saveBitmapAsLocalUri (ver
                                        // LayerRepository). El diálogo se
                                        // cierra igual; el proyecto se
                                        // queda sin fondo nuevo, y el
                                        // usuario puede volver a intentar
                                        // "Importar fondo".
                                        if (savedUri != null) {
                                            viewModel.importAsBackground(savedUri)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
