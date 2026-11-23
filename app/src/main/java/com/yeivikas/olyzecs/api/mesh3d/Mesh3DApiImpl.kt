package com.yeivikas.olyzecs.api.mesh3d

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.mesh3d.Extrude3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación real de [Mesh3DApi]: delegación directa hacia
 * [Extrude3D.render], con el mismo `Dispatchers.Default` que antes
 * usaba `EditorViewModel.renderExtrude3D` directo — ahora es esta clase
 * la que decide el dispatcher, tal como establece la estrategia de
 * asincronía del diseño aprobado (sección 9: "la API no expone
 * Dispatchers en su firma en ningún caso"). Cero lógica propia, cero
 * duplicación del algoritmo de extrusión.
 *
 * Consumidor real: `EditorViewModel.renderExtrude3D` (viewmodel/
 * EditorViewModel.kt), inyectada por constructor desde
 * `EditorViewModelFactory`/`MainActivity` (ver tarea "Mesh3D → EliNer").
 */
class Mesh3DApiImpl : Mesh3DApi {

    override suspend fun extrude(source: Bitmap, params: Extrude3D.Params, highQuality: Boolean): Bitmap =
        withContext(Dispatchers.Default) {
            Extrude3D.render(source, params, highQuality)
        }
}
