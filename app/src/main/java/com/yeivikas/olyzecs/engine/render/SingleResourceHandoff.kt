package com.yeivikas.olyzecs.engine.render

import java.util.concurrent.atomic.AtomicReference

/**
 * FASE 3.1.2 — Cierre definitivo de GPU/Bitmap ownership, content
 * revision y rechazo de resultados obsoletos.
 *
 * HISTORIA: la Fase 3.1 ya había resuelto el *lost update* de la
 * secuencia "leer, después escribir null" (ver `AtomicReference.getAndSet`
 * de la versión anterior de esta clase) con una sola operación atómica de
 * consumo. Eso resolvía la CORRUPCIÓN de la operación, pero dejaba
 * abiertos DOS problemas reales, distintos, encontrados en la auditoría
 * de esta fase:
 *
 * PROBLEMA 2 DEL INFORME — Bitmap sin dueño al reemplazar un pendiente:
 * `publish(value)` hacía `ref.set(value)` sin condiciones. Si ya había un
 * valor SIN CONSUMIR (p. ej. el usuario arrastra la rueda de color más
 * rápido de lo que el hilo de GL puede consumir+reciclar cada bitmap de
 * preview — ver `EditorViewModel.previewLayerRecolor`, llamado varias
 * veces por segundo), ese valor anterior quedaba reemplazado en silencio:
 * nadie lo consumía nunca, nadie lo reciclaba nunca. `Bitmap` es un
 * recurso nativo pesado — esto es un leak real, no solo trabajo
 * desperdiciado. `publish()` ahora DEVUELVE el valor anterior sin
 * consumir (si había) para que el llamador, que pasa a ser su único
 * dueño desde ese instante, lo disponga explícitamente
 * (`resultado?.recycle()`). Nunca hay un recurso "huérfano": siempre hay
 * un dueño identificable en cada instante — o el slot, o quien lo tomó,
 * o quien lo recibió de vuelta al reemplazarlo.
 *
 * PROBLEMA 1 DEL INFORME — Stale redecode/stale texture: un decode
 * ASÍNCRONO (en curso en otro hilo, o simplemente síncrono pero lento
 * mientras el hilo principal corre en paralelo real) puede terminar
 * DESPUÉS de que la capa ya cambió de contenido otra vez. Publicar ese
 * resultado tardío como si fuera válido deja la textura GPU mostrando la
 * imagen EQUIVOCADA — el bug real reportado ("reemplazo la imagen dos
 * veces seguido y a veces gana la vieja"). `@Volatile`/`synchronized` NO
 * alcanzan acá: el problema no es visibilidad ni atomicidad de UNA
 * operación, es "¿este resultado sigue correspondiendo a la versión
 * actual del contenido?" — una pregunta de IDENTIDAD que solo se puede
 * responder comparando una VERSIÓN capturada al pedir el trabajo contra
 * la versión vigente al momento de publicar el resultado (ver
 * `Layer.contentRevision`). [takeIfCurrent] es el único punto de la app
 * donde se hace esa comparación antes de dejar que un resultado se
 * convierta en el recurso GPU visible.
 *
 * Sigue siendo genérica (no depende de `android.graphics.Bitmap`) para
 * poder testear toda esta lógica con JUnit puro — ver
 * [SingleResourceHandoffTest].
 */
class SingleResourceHandoff<T : Any> {

    /**
     * Resultado de intentar tomar el recurso pendiente contra una
     * revisión de contenido vigente — ver [takeIfCurrent].
     */
    sealed class Consumption<out T> {
        /** Había un recurso pendiente y correspondía a la revisión vigente: listo para usar. */
        data class Ready<T>(val value: T) : Consumption<T>()

        /**
         * Había un recurso pendiente, pero etiquetado con una revisión
         * VIEJA — ya no corresponde al contenido actual de la capa
         * dueña. El llamador pasa a ser el ÚNICO dueño de [discarded]
         * desde este momento y ES SU RESPONSABILIDAD disponerlo
         * (`discarded.recycle()` para un `Bitmap`) — [takeIfCurrent]
         * jamás lo recicla ni lo descarta por su cuenta: esta clase es
         * genérica, no sabe qué significa "disponer" un `T` cualquiera.
         */
        data class Stale<T>(val discarded: T) : Consumption<T>()

        /** No había ningún recurso pendiente. */
        data object Empty : Consumption<Nothing>()
    }

    private data class Pending<T>(val value: T, val revision: Int)

    private val ref = AtomicReference<Pending<T>?>(null)

    /**
     * Productor (cualquier hilo): publica [value] como el próximo
     * recurso pendiente, etiquetado con [revision] — la versión de
     * contenido (ver `Layer.contentRevision`) que este [value] representa
     * en el momento de publicarlo. Un solo slot: la publicación más
     * reciente siempre gana sobre cualquier valor sin consumir.
     *
     * Devuelve el valor anterior SIN CONSUMIR, si había uno — nunca lo
     * descarta en silencio (ver PROBLEMA 2 en el KDoc de la clase). El
     * llamador es responsable de disponerlo.
     */
    fun publish(value: T, revision: Int): T? = ref.getAndSet(Pending(value, revision))?.value

    /**
     * Limpia cualquier recurso pendiente sin publicar uno nuevo,
     * devolviéndolo (sin consumir previamente) para que el llamador lo
     * disponga. Equivalente a "publicar nada" — reemplaza el viejo
     * `publish(null)`, que ya no es una sobrecarga válida a propósito
     * (forzar un `revision` en una publicación real evita que se use
     * este método donde en realidad hacía falta [publish]).
     */
    fun clear(): T? = ref.getAndSet(null)?.value

    /**
     * Consumidor único (pensado para el hilo de GL, ver
     * `GLRenderer.uploadTextureIfNeeded`): toma el recurso pendiente EN
     * UNA SOLA operación atómica (bucle compare-and-set — nunca una
     * ventana separada de "leer" y "limpiar") y lo clasifica contra
     * [currentRevision]:
     *
     * - Si no había nada pendiente: [Consumption.Empty].
     * - Si había algo pendiente Y coincide con [currentRevision]: se
     *   retira del slot y se devuelve como [Consumption.Ready] — es
     *   seguro subirlo a GPU.
     * - Si había algo pendiente pero de una revisión distinta (VIEJA,
     *   por construcción: ver `Layer.contentRevision`, que solo crece
     *   con cambios de contenido reales — nunca puede haber un pendiente
     *   etiquetado con una revisión MÁS NUEVA que la vigente): se retira
     *   igual del slot (nunca se deja ahí dando vueltas para siempre) y
     *   se devuelve como [Consumption.Stale] — el llamador debe
     *   disponerlo, JAMÁS subirlo a GPU.
     */
    fun takeIfCurrent(currentRevision: Int): Consumption<T> {
        while (true) {
            val prev = ref.get() ?: return Consumption.Empty
            if (ref.compareAndSet(prev, null)) {
                return if (prev.revision == currentRevision) {
                    Consumption.Ready(prev.value)
                } else {
                    Consumption.Stale(prev.value)
                }
            }
            // El CAS falló porque otro publish()/clear() tocó el slot justo
            // entre el get() y el compareAndSet() — se reintenta leyendo el
            // valor fresco. Nunca se pierde ni se duplica un recurso: o
            // este hilo termina tomando lo que hay ahora, o encuentra el
            // slot vacío y devuelve Empty.
        }
    }

    /**
     * Toma el recurso pendiente SIN VALIDAR revisión — solo para
     * traspasos internos de ownership entre dos instancias de [Layer]
     * que representan, por construcción, EXACTAMENTE el mismo contenido
     * (ver `EditorViewModel.transferPendingResourceOwnership`, usado por
     * `replaceLayer`/`replaceLayers` cuando `preserveRenderState = true`
     * — un `.copy()` que NO toca `sourceUri`/`contentRevision`). Nunca
     * usar esto en el límite de render real (GLRenderer): ahí SIEMPRE
     * corresponde [takeIfCurrent].
     */
    fun takeRaw(): T? = ref.getAndSet(null)?.value

    /**
     * Lectura sin consumir — solo para decisiones de tipo "¿hay algo
     * pendiente?" (p. ej. deduplicar un re-decode), nunca para tomar
     * posesión del recurso.
     */
    fun peek(): T? = ref.get()?.value
}
