package com.yeivikas.olyzecs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeivikas.olyzecs.data.DEFAULT_PROJECT_NAME
import com.yeivikas.olyzecs.data.LayerRepository
import com.yeivikas.olyzecs.data.ProjectStorage
import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset
import com.yeivikas.olyzecs.ui.EditorScreen
import com.yeivikas.olyzecs.ui.ProjectsScreen
import com.yeivikas.olyzecs.ui.theme.OlyzeGradient
import com.yeivikas.olyzecs.ui.theme.OlyzeTheme
import com.yeivikas.olyzecs.viewmodel.EditorViewModel
import com.yeivikas.olyzecs.viewmodel.EditorViewModelFactory
import com.yeivikas.olyzecs.viewmodel.ProjectsViewModel
import com.yeivikas.olyzecs.viewmodel.ProjectsViewModelFactory
import kotlinx.coroutines.launch

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
            var pendingProjectAspect by rememberSaveable { mutableStateOf(AspectRatioPreset.REELS) }
            // La duración ya no se elige al crear el proyecto — arranca
            // fija en 1 minuto y crece sola (ver TimelineDurationManager),
            // así que ya no hace falta guardar ningún valor "pendiente" acá.
            var pendingProjectFps by rememberSaveable { mutableStateOf(30) }

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
                            onCreateProject = { id, name, aspect, fps ->
                                pendingProjectAspect = aspect
                                pendingProjectFps = fps
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
                        val factory = remember(projectId) {
                            EditorViewModelFactory(
                                layerRepository, projectStorage, projectId,
                                pendingProjectName, pendingProjectAspect, initialFps = pendingProjectFps,
                                mesh3DApi = mesh3DApi, animationApi = animationApi, distortionApi = distortionApi
                            )
                        }
                        val viewModel: EditorViewModel = viewModel(factory = factory, key = projectId)

                        // --- EliNer API (Fase 1.4 + Mesh3D→EliNer + Animation→EliNer):
                        // wiring de los 7 dominios ya conectables (todos menos `render`,
                        // que necesita una superficie GL viva — solo existe dentro de
                        // GLPreview.kt, capa de UI, fuera de alcance). `viewModel` ya
                        // implementa ActiveProjectReader/ActiveProjectMutator; acá solo
                        // se lo pasa como tal a cada *ApiImpl — MainActivity sigue
                        // siendo el único lugar que construye estas piezas (composition
                        // root), igual que ya hace con layerRepository/projectStorage.
                        // Mesh3D y Animation son, por ahora, los únicos 2 de los 7 que
                        // `EditorViewModel` realmente USA (vía los parámetros
                        // `mesh3DApi`/`animationApi` del factory, arriba) — el resto
                        // sigue preparado pero sin consumidor real todavía.
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
                        val eliNerExportApi = remember(projectId) {
                            com.yeivikas.olyzecs.api.export.ExportApiImpl(applicationContext, viewModel)
                        }
                        val eliNerMesh3DApi = mesh3DApi
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
                                onBackgroundPicked = { uri -> if (uri != null) viewModel.importAsBackground(uri) }
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
                            }
                        )
                    }
                }
            }
        }
    }
}
