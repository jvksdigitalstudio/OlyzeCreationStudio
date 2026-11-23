package com.yeivikas.olyzecs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeivikas.olyzecs.data.ProjectStorage
import com.yeivikas.olyzecs.data.TrashedProjectEntry
import com.yeivikas.olyzecs.data.TrashedProjectSummary
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de "Papelera de proyectos" — dos vistas en una sola pantalla
 * (ver ProjectsTrashScreen.kt):
 *
 * 1. Lista de proyectos en la papelera ([projects]), cuando
 *    [browsingProjectId] es `null` — cada fila con Restaurar/Eliminar
 *    para siempre, más "Vaciar papelera" para todo el conjunto.
 * 2. Navegador de archivos DENTRO de un proyecto puntual de la papelera
 *    ([currentEntries], acotado a [currentPath]), cuando
 *    [browsingProjectId] no es `null` — a pedido explícito de diseño (con
 *    referencia visual de "Recently Deleted"): se puede entrar a la
 *    carpeta de un proyecto eliminado, navegar sus subcarpetas
 *    (`images/`, `audio/`, `cast/`, `project.json`, miniatura, portada) y
 *    borrar una entrada puntual sin restaurar el proyecto entero.
 */
data class ProjectsTrashUiState(
    val projects: List<TrashedProjectSummary> = emptyList(),
    val isLoading: Boolean = true,

    val browsingProjectId: String? = null,
    val browsingProjectName: String = "",
    // "" = raíz de la carpeta del proyecto en la papelera. Segmentos
    // separados por '/', nunca con separadores del sistema operativo.
    val currentPath: String = "",
    val currentEntries: List<TrashedProjectEntry> = emptyList(),
    val isLoadingEntries: Boolean = false
) {
    /** Migas de pan para la barra superior del navegador — vacío si se está en la raíz. */
    val pathSegments: List<String> get() = if (currentPath.isBlank()) emptyList() else currentPath.split('/')
}

/**
 * Dueño de "Papelera de proyectos" (ver AppDrawerContent/ProjectsTrashScreen.kt).
 * Sigue el mismo patrón `UI → ViewModel → Data` que [ProjectsViewModel]:
 * la UI no conoce [ProjectStorage] directamente, solo lee [uiState] y
 * llama a estas funciones.
 *
 * A propósito NO refresca por sí solo la lista de "Mis proyectos"
 * ([ProjectsViewModel.uiState]) al restaurar un proyecto — eso queda del
 * lado de [com.yeivikas.olyzecs.ui.ProjectsScreen], que orquesta ambos
 * ViewModels y decide cuándo refrescar cada uno (ver `onProjectRestored`
 * en ProjectsTrashScreen.kt). Este ViewModel no necesita conocer que
 * `ProjectsViewModel` existe.
 */
class ProjectsTrashViewModel(
    private val projectStorage: ProjectStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsTrashUiState())
    val uiState: StateFlow<ProjectsTrashUiState> = _uiState.asStateFlow()

    /** Refresca la lista de proyectos en la papelera. Se llama al abrir la pantalla y tras cada acción que la modifique. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = projectStorage.listTrashedProjects()
            _uiState.value = _uiState.value.copy(projects = list, isLoading = false)
        }
    }

    /** Abre el navegador de archivos de un proyecto puntual de la papelera, empezando en su raíz. */
    fun openProject(projectId: String, projectName: String) {
        _uiState.value = _uiState.value.copy(
            browsingProjectId = projectId,
            browsingProjectName = projectName,
            currentPath = "",
            currentEntries = emptyList()
        )
        loadEntries(projectId, "")
    }

    /** Cierra el navegador de archivos y vuelve a la lista de proyectos en la papelera. */
    fun closeBrowser() {
        _uiState.value = _uiState.value.copy(
            browsingProjectId = null,
            browsingProjectName = "",
            currentPath = "",
            currentEntries = emptyList()
        )
    }

    /** Entra a una subcarpeta del proyecto que se está navegando — no hace nada si [entry] es un archivo. */
    fun navigateInto(entry: TrashedProjectEntry) {
        if (!entry.isDirectory) return
        val projectId = _uiState.value.browsingProjectId ?: return
        _uiState.value = _uiState.value.copy(currentPath = entry.relativePath)
        loadEntries(projectId, entry.relativePath)
    }

    /** Sube un nivel dentro del navegador — no hace nada si ya se está en la raíz del proyecto. */
    fun navigateUp() {
        val projectId = _uiState.value.browsingProjectId ?: return
        val segments = _uiState.value.pathSegments
        if (segments.isEmpty()) return
        val parentPath = segments.dropLast(1).joinToString("/")
        _uiState.value = _uiState.value.copy(currentPath = parentPath)
        loadEntries(projectId, parentPath)
    }

    /** Vuelve a la raíz de la carpeta del proyecto que se está navegando (equivalente a tocar la miga "Proyecto"). */
    fun navigateToRoot() {
        val projectId = _uiState.value.browsingProjectId ?: return
        _uiState.value = _uiState.value.copy(currentPath = "")
        loadEntries(projectId, "")
    }

    /** Salta directo a un nivel de la miga de pan (0 = primera subcarpeta bajo la raíz). */
    fun navigateToBreadcrumb(index: Int) {
        val projectId = _uiState.value.browsingProjectId ?: return
        val segments = _uiState.value.pathSegments
        if (index < 0 || index >= segments.size) return
        val path = segments.take(index + 1).joinToString("/")
        _uiState.value = _uiState.value.copy(currentPath = path)
        loadEntries(projectId, path)
    }

    private fun loadEntries(projectId: String, path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingEntries = true)
            val entries = projectStorage.listTrashedProjectEntries(projectId, path)
            // Un cambio de carpeta más reciente (el usuario navegó de
            // nuevo mientras esta carga todavía estaba en vuelo) no debe
            // pisarse por una respuesta vieja llegando tarde.
            if (_uiState.value.browsingProjectId == projectId && _uiState.value.currentPath == path) {
                _uiState.value = _uiState.value.copy(currentEntries = entries, isLoadingEntries = false)
            }
        }
    }

    /**
     * Borra una entrada puntual (archivo o subcarpeta) dentro del proyecto
     * que se está navegando, y recarga la carpeta actual — el "ver y
     * borrar lo que uno quiera" pedido explícitamente. También refresca
     * [uiState].projects: el tamaño en disco de ese proyecto en la lista
     * cambió.
     */
    fun deleteEntry(entry: TrashedProjectEntry) {
        val projectId = _uiState.value.browsingProjectId ?: return
        val path = _uiState.value.currentPath
        viewModelScope.launch {
            projectStorage.deleteTrashedProjectEntry(projectId, entry.relativePath)
            loadEntries(projectId, path)
            refresh()
        }
    }

    /**
     * Restaura un proyecto de la papelera de vuelta a "Mis proyectos".
     * Si el navegador está abierto sobre ESE proyecto, se cierra
     * automáticamente (ya no queda nada que navegar ahí). [onRestored] se
     * invoca solo si la restauración tuvo éxito — el call site
     * (ProjectsTrashScreen.kt) lo usa para refrescar "Mis proyectos" y/o
     * cerrar la papelera entera.
     */
    fun restoreProject(projectId: String, onRestored: () -> Unit = {}) {
        viewModelScope.launch {
            val success = projectStorage.restoreProjectFromTrash(projectId)
            if (success) {
                if (_uiState.value.browsingProjectId == projectId) closeBrowser()
                refresh()
                onRestored()
            }
        }
    }

    /** Elimina un proyecto de la papelera para siempre. Si el navegador está abierto sobre ese proyecto, se cierra. */
    fun deleteProjectPermanently(projectId: String) {
        viewModelScope.launch {
            projectStorage.deleteProjectFromTrashPermanently(projectId)
            if (_uiState.value.browsingProjectId == projectId) closeBrowser()
            refresh()
        }
    }

    /** Vacía la papelera entera — borra para siempre cada proyecto que contiene. Cierra el navegador si estaba abierto. */
    fun emptyTrash() {
        viewModelScope.launch {
            projectStorage.emptyProjectsTrash()
            closeBrowser()
            refresh()
        }
    }

    /**
     * Copia [entry] a una carpeta expuesta al `FileProvider` para que el
     * visor de archivos ([com.yeivikas.olyzecs.ui.FilePreviewDialog])
     * pueda armar un Intent de "Compartir" o "Abrir con…" sobre ella — ver
     * [ProjectStorage.prepareFileForSharing]. `suspend` directo (sin pasar
     * por [uiState]): es una operación de un solo uso pedida por el propio
     * diálogo, no un estado que el resto de la pantalla necesite observar.
     */
    suspend fun prepareEntryForSharing(entry: TrashedProjectEntry): File? =
        projectStorage.prepareFileForSharing(entry.file, entry.name)
}
