package com.yeivikas.olyzecs.api.distortion

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.distortion.DistortionField
import com.yeivikas.olyzecs.engine.distortion.DistortionRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación real de [DistortionApi]: delegación directa hacia
 * [DistortionRasterizer.render], con `Dispatchers.Default` — mismo
 * criterio que [com.yeivikas.olyzecs.api.mesh3d.Mesh3DApiImpl]: es esta
 * clase la que decide el dispatcher, no quien la llama (la API no expone
 * Dispatchers en su firma). Cero lógica propia, cero duplicación del
 * algoritmo de deformación.
 *
 * Consumidor real: `EditorViewModel.renderDistortion`
 * (`viewmodel/EditorViewModel.kt`), inyectada por constructor desde
 * `EditorViewModelFactory`/`MainActivity`.
 */
class DistortionApiImpl : DistortionApi {

    override suspend fun render(
        source: Bitmap,
        field: DistortionField,
        outWidth: Int,
        outHeight: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        DistortionRasterizer.render(source, field, outWidth, outHeight)
    }
}
