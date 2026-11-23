package com.yeivikas.olyzecs.ui.effects

import kotlinx.coroutines.CompletableDeferred

/**
 * Las tres versiones de la imagen de una capa con las que trabaja una ventana de efectos, de la más rápida a la
 * más fiel. Es solo un contenedor: no decodifica ni renderiza (eso es de [EffectSourceLoader] y de
 * [ProgressiveRenderScheduler]).
 *
 * | Versión   | Para qué                                                | Tamaño                       |
 * |-----------|---------------------------------------------------------|------------------------------|
 * | [quick]   | Vista previa MIENTRAS se arrastra un control            | Chica (≈ 260 px)             |
 * | [display] | Vista previa al soltar: tan nítida como la pantalla     | Resolución de pantalla       |
 * | full      | Lo ÚNICO desde lo que se guarda y exporta               | Original, sin reducir        |
 *
 * ## Invariante que protege la calidad
 * [awaitFull] es la única forma de obtener la imagen completa y SUSPENDE hasta que exista. No hay ninguna
 * propiedad "completa o, si no, la chica": quien confirma un cambio no puede, ni por descuido, guardar la copia
 * pequeña como si fuera la definitiva (el defecto original, repetido en 13 sitios).
 *
 * [B] es el tipo de bitmap (en la app, `android.graphics.Bitmap`); es genérico para que la lógica se pruebe en JVM.
 */
class EffectSources<B : Any> {

    @Volatile
    var quick: B? = null
        private set

    @Volatile
    var display: B? = null
        private set

    private val full = CompletableDeferred<B?>()

    fun publishQuick(value: B?) {
        quick = value
    }

    fun publishDisplay(value: B?) {
        display = value
    }

    /**
     * Fija la imagen completa; `null` si no se pudo decodificar (nadie debe confirmar nada entonces). Solo cuenta la
     * primera llamada: completar de nuevo no cambia el resultado.
     */
    fun completeFull(value: B?) {
        full.complete(value)
    }

    /** La imagen completa, esperando si hace falta; `null` si su decodificación falló o se canceló. */
    suspend fun awaitFull(): B? = full.await()
}
