package com.yeivikas.olyzecs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yeivikas.olyzecs.api.animation.AnimationApi
import com.yeivikas.olyzecs.api.animation.AnimationApiImpl
import com.yeivikas.olyzecs.api.distortion.DistortionApi
import com.yeivikas.olyzecs.api.distortion.DistortionApiImpl
import com.yeivikas.olyzecs.api.mesh3d.Mesh3DApi
import com.yeivikas.olyzecs.api.mesh3d.Mesh3DApiImpl
import com.yeivikas.olyzecs.data.DEFAULT_PROJECT_NAME
import com.yeivikas.olyzecs.data.LayerRepository
import com.yeivikas.olyzecs.data.ProjectStorage
import com.yeivikas.olyzecs.engine.scene.AspectRatioPreset
import com.yeivikas.olyzecs.engine.timeline.TimelineLimits

/**
 * [projectId] identifica QUÉ proyecto abre este ViewModel — se usa como
 * `key` en `viewModel(factory = ..., key = projectId)` para que Compose
 * cree una instancia nueva del ViewModel por cada proyecto distinto, en
 * vez de reusar el estado del proyecto anterior al navegar entre ellos.
 *
 * [initialAspect]/[initialFps] solo se usan la primera vez que se abre un
 * proyecto recién creado (todavía sin nada guardado en disco): son los
 * valores elegidos en el diálogo "Nuevo proyecto" de
 * [com.yeivikas.olyzecs.ui.ProjectsScreen]. Si el proyecto ya
 * existía, [EditorViewModel] los ignora y usa los que ya estaban guardados.
 *
 * [initialDurationMs] se mantiene solo por compatibilidad de firma: la
 * duración de un proyecto nuevo ya no se elige desde ningún lado, siempre
 * arranca en [TimelineLimits.INITIAL_DURATION_MS] — ver
 * [com.yeivikas.olyzecs.engine.timeline.TimelineDurationManager].
 *
 * [mesh3DApi]/[animationApi] con default propio (`Mesh3DApiImpl()`/
 * `AnimationApiImpl()`, sin estado, sin dependencias) para no obligar a
 * quien construya este factory sin conocer `EliNer API` a pasarlos —
 * pero `MainActivity` (composition root) pasa las MISMAS instancias que
 * usa para el resto del wiring de `EliNer API`, en vez de dejar que se
 * creen instancias separadas.
 */
class EditorViewModelFactory(
    private val layerRepository: LayerRepository,
    private val projectStorage: ProjectStorage,
    private val projectId: String,
    private val initialName: String = DEFAULT_PROJECT_NAME,
    private val initialAspect: AspectRatioPreset = AspectRatioPreset.REELS,
    private val initialDurationMs: Long = TimelineLimits.INITIAL_DURATION_MS,
    private val initialFps: Int = 30,
    private val mesh3DApi: Mesh3DApi = Mesh3DApiImpl(),
    private val animationApi: AnimationApi = AnimationApiImpl(),
    private val distortionApi: DistortionApi = DistortionApiImpl()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EditorViewModel(
            layerRepository, projectStorage, projectId,
            initialName, initialAspect, initialDurationMs, initialFps, mesh3DApi, animationApi, distortionApi
        ) as T
    }
}
