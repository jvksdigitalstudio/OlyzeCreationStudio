package com.yeivikas.olyzecs.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeivikas.olyzecs.data.MoveDirection
import com.yeivikas.olyzecs.data.ProjectStorage
import com.yeivikas.olyzecs.data.ProjectSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Dueño de la biblioteca de "Mis proyectos". Antes, [com.yeivikas.olyzecs.ui.ProjectsScreen]
 * recibía [ProjectStorage] directo como parámetro y llamaba a sus métodos
 * (listar, duplicar, mover, renombrar, borrar...) desde dentro del
 * composable — la UI conocía y manejaba la capa de persistencia
 * directamente, saltándose el patrón `UI → ViewModel → Data` que sigue el
 * resto del proyecto (ver EditorViewModel).
 *
 * Ahora ProjectsScreen solo conoce este ViewModel: lee [uiState] para
 * mostrar la lista, y llama a estas funciones para pedir acciones. Ya no
 * sabe que existe `ProjectStorage`, ni tiene que acordarse de refrescar la
 * lista a mano después de cada acción (antes eso era el hack de
 * `localTick++` repetido en cada callback; acá cada función simplemente
 * actualiza [uiState] al terminar).
 *
 * *Fuera del alcance de `EliNer API` (Etapa 4):* a diferencia de
 * [EditorViewModel], esta clase no es candidata a pasar por la futura
 * API — gestiona la biblioteca de proyectos (listar/crear/duplicar/
 * borrar), no el motor de render/audio/animación. Sigue hablando con
 * [ProjectStorage] directo, ahora y después. Ver `api/README.md`.
 */
class ProjectsViewModel(
    private val projectStorage: ProjectStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    /** Se llama al entrar a la pantalla y cada vez que se vuelve del editor (ver `refreshKey` en ProjectsScreen). */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = projectStorage.listProjects()
            _uiState.value = _uiState.value.copy(projects = list, isLoading = false)
        }
    }

    /** Genera el id para un proyecto nuevo. Es solo un UUID en memoria, no toca disco, por eso no es suspend. */
    fun newProjectId(): String = projectStorage.newProjectId()

    fun duplicateProject(projectId: String, newName: String) {
        viewModelScope.launch {
            projectStorage.duplicateProject(projectId, newName)
            refresh()
        }
    }

    fun removeCoverImage(projectId: String) {
        viewModelScope.launch {
            projectStorage.removeCoverImage(projectId)
            refresh()
        }
    }

    fun moveProject(projectId: String, direction: MoveDirection) {
        viewModelScope.launch {
            projectStorage.moveProject(projectId, direction)
            refresh()
        }
    }

    fun renameProject(projectId: String, newName: String, newDescription: String?) {
        viewModelScope.launch {
            projectStorage.renameProject(projectId, newName, newDescription)
            refresh()
        }
    }

    fun setCoverImage(projectId: String, bitmap: Bitmap) {
        viewModelScope.launch {
            projectStorage.setCoverImageBitmap(projectId, bitmap)
            bitmap.recycle()
            refresh()
        }
    }

    /**
     * Ya NO borra el proyecto al instante: lo mueve a la papelera de
     * proyectos (ver [ProjectStorage.moveProjectToTrash] y el bloque de
     * comentario grande junto a `TrashedProjectSummary`). El borrado
     * definitivo vive del lado de [ProjectsTrashViewModel]
     * (restaurar/eliminar para siempre/vaciar), nunca acá.
     *
     * [onMoved] se invoca al terminar — este ViewModel no conoce
     * [ProjectsTrashViewModel] en absoluto, así que es el call site
     * (ProjectsScreen.kt, el único composable con ambos ViewModels en
     * alcance) el que lo usa para refrescar el badge de "Papelera de
     * proyectos" del Menú lateral EN EL MOMENTO en que el proyecto entra
     * a la papelera — no recién la próxima vez que alguien abra esa
     * pantalla. Corrige un bug real reportado en dispositivo: la bolita
     * roja del contador quedaba desactualizada hasta entrar y salir de
     * la papelera una vez.
     */
    fun moveProjectToTrash(projectId: String, onMoved: () -> Unit = {}) {
        viewModelScope.launch {
            projectStorage.moveProjectToTrash(projectId)
            refresh()
            onMoved()
        }
    }
}
