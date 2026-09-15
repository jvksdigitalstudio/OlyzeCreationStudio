package com.yeivikas.olyzecs.engine.scene

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID
import com.yeivikas.olyzecs.engine.camera.CameraTrack
import com.yeivikas.olyzecs.engine.effects.LookSettings
import com.yeivikas.olyzecs.engine.render.SingleResourceHandoff
import com.yeivikas.olyzecs.engine.render.TextureInvalidationRequest

/**
 * Una capa independiente dentro del proyecto: una imagen PNG (con o sin
 * transparencia) más su propia pista de cámara. El orden en [zIndex]
 * determina qué capa se dibuja encima de cuál (para el efecto parallax:
 * el fondo tiene zIndex menor y se mueve más lento que el personaje).
 *
 * El bitmap se mantiene en memoria de forma perezosa; el motor GL sube
 * la textura una sola vez y libera el bitmap de CPU tras subirla, para
 * no duplicar memoria en imágenes grandes.
 */

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    // FASE 3.1.3-R3.1 — OBJETIVO 5 (encapsulación), NOTA DE CORRECCIÓN:
    // Kotlin no permite `private set` en un parámetro del constructor
    // primario (los accessors solo se pueden declarar en propiedades del
    // CUERPO de la clase — de ahí el error real de compilación:
    // `Expecting comma or ')'`). Como este campo tiene que seguir siendo
    // un parámetro del constructor primario (para que
    // `.copy(sourceUri = ...)`, usado en decenas de sitios de
    // EditorViewModel, siga funcionando sin tocar un solo call site), el
    // bloqueo real se logra con `@set:Deprecated(level = ERROR)`: sigue
    // siendo, a nivel de bytecode, un setter público (por eso
    // `LayerGpuOwnershipStructureTest` verifica la anotación, no
    // `Modifier.isPublic`), pero cualquier `layer.sourceUri = x` fuera de
    // esta clase deja de COMPILAR — el mismo bloqueo real que buscaba
    // `private set`, logrado por una vía que Kotlin sí permite en un
    // parámetro de constructor. El único punto que queda habilitado
    // (con `@Suppress`) es [updateContentIdentity] (más abajo en esta
    // misma clase), el único lugar legal para cambiar este campo en una
    // capa YA VIVA.
    @Volatile
    @set:Deprecated(
        message = "No asignar sourceUri directamente — usa Layer.updateContentIdentity() " +
            "para mantener sourceUri/contentRevision/contentIdentity sincronizados.",
        level = DeprecationLevel.ERROR
    )
    var sourceUri: Uri,
    var name: String,
    // FASE 2 — auditoría de concurrencia: [zIndex], [visible] y
    // [parallaxFactor] los escribe el hilo principal (edición/undo-redo)
    // y los lee, cada frame, el hilo de GL (orden de dibujo, visibilidad,
    // encuadre) en GLRenderer.onDrawFrame. Son valores simples (Int/
    // Boolean/Float) que no pueden quedar "a medio escribir" — el riesgo
    // acá NO es corrupción del valor sino VISIBILIDAD: sin `@Volatile`,
    // nada garantiza que el hilo de GL vea la escritura nueva en algún
    // momento determinado (podría seguir viendo el valor viejo cacheado
    // un tiempo indefinido). `@Volatile` es la herramienta mínima
    // correcta acá — no hace falta un lock para un valor simple con un
    // único escritor.
    @Volatile var zIndex: Int,
    @Volatile var parallaxFactor: Float = 1f, // 1 = movimiento normal, <1 = se mueve más lento (fondo)
    var locked: Boolean = false,    // true = no se puede mover/editar desde el preview ni sliders
    // --- Candado INDEPENDIENTE del de arriba (ver pedido del usuario):
    // [locked] bloquea el CANVAS (mover/transformar/sliders) y este otro
    // bloquea únicamente el REORDENAMIENTO por arrastre en la columna de
    // capas del timeline. Se pueden combinar: una capa puede estar
    // bloqueada en el canvas pero libre para reordenar, libre en el
    // canvas pero fija en su lugar del orden, ambas cosas a la vez, o
    // ninguna — cuatro combinaciones reales, no un solo interruptor que
    // hace las dos cosas. Ver TimelineView.kt (ícono propio, distinto
    // diseño al candado de arriba, a su izquierda) y el gesto de arrastre
    // de la miniatura, que ahora chequea `locked || orderLocked` antes de
    // dejar reordenar.
    var orderLocked: Boolean = false,
    @Volatile var visible: Boolean = true,    // false = oculta del preview, la reproducción y la exportación
    @Volatile var lookSettings: LookSettings = LookSettings(), // grading, viñeta, grano, glow — independiente por capa
    val cameraTrack: CameraTrack = CameraTrack(),
    // widthPx/heightPx tienen DOS escritores: el editor (al importar o
    // reemplazar la imagen de una capa) Y el propio hilo de GL (al subir
    // la textura, ver GLRenderer.uploadTextureIfNeeded — la GPU puede
    // clampear el tamaño real de la textura a un límite de hardware
    // distinto al que se decodificó). `@Volatile` acá protege la lectura
    // que hace el editor/UI de un valor que el hilo de GL puede haber
    // actualizado recién.
    @Volatile var widthPx: Int = 0,
    @Volatile var heightPx: Int = 0,
    // Índice fijo asignado UNA vez al crear la capa (ver LayerRepository),
    // que determina de qué color de la paleta (ver Theme.kt/LayerTrackColors)
    // se pinta su fila en el timeline — así cada capa se distingue de un
    // vistazo, al estilo de los canales de FL Studio Mobile. No cambia
    // solo o al reordenar/mover la capa: el color viaja CON la capa, no
    // con la posición que ocupa.
    var colorIndex: Int = 0,
    // --- Color elegido a mano por el usuario en la rueda de color (ver
    // ColorWheelPicker en TimelineView.kt), como valor ARGB de
    // android.graphics.Color. null = todavía no personalizó el color de
    // esta capa, así que se sigue usando el color cíclico de la paleta
    // fija según [colorIndex] (ver Theme.kt/effectiveLayerColor). En
    // cuanto el usuario elige un color en la rueda, ESTE campo manda y
    // colorIndex queda ignorado para efectos visuales (pero se conserva
    // por si se restablece el color a "automático").
    var customColorArgb: Int? = null,
    // --- Color POR DEFECTO real de esta capa: el color dominante extraído
    // de la propia imagen/medio en el momento en que se importó (ver
    // ColorExtraction.dominantColor + LayerRepository.importAsLayers).
    // A diferencia de [customColorArgb] (que cambia cada vez que el
    // usuario elige un color nuevo en la rueda), ESTE campo NUNCA se pisa
    // después de la importación — es la "identidad de fábrica" de la capa.
    //
    // BUG REAL que esto corrige: "Restablecer" en el diálogo de color
    // (ver EditorViewModel.resetLayerColor/resetLayersColor) ponía
    // customColorArgb = null, lo que hacía caer el color en el cíclico
    // automático de la paleta fija por [colorIndex] — un color CUALQUIERA,
    // sin relación con la imagen real de la capa. Con este campo,
    // "Restablecer" vuelve al color que la imagen tenía al cargarse, tal
    // como se espera de un botón "Restablecer" (volver al valor original),
    // no a un color genérico distinto cada vez.
    //
    // Nullable por compatibilidad con capas de proyectos guardados ANTES
    // de este campo: en ese caso ProjectStorage lo recalcula en el momento
    // de abrir el proyecto (mismo cálculo que al importar), así que en la
    // práctica solo queda null para capas creadas fuera del flujo normal
    // de importación/carga.
    var importedDefaultColorArgb: Int? = null,
    // --- Degradado de identidad (opcional): dos colores ARGB, A (arriba)
    // y B (abajo). Solo tienen efecto visual si [useGradientColor] es
    // true — si no, se ignoran y manda [customColorArgb] (o la paleta
    // automática). Se guardan igual aunque el degradado esté apagado,
    // para que el usuario pueda prenderlo/apagarlo sin perder lo que ya
    // había armado.
    var customGradientStartArgb: Int? = null,
    var customGradientEndArgb: Int? = null,
    var useGradientColor: Boolean = false,
    // --- Dirección del degradado: por defecto 90° (de arriba hacia abajo,
    // igual que el comportamiento original A-arriba/B-abajo). El ángulo
    // se mide en grados con el mismo criterio que un reloj de coordenadas
    // de pantalla: 0°=izquierda→derecha, 90°=arriba→abajo. Se ignora por
    // completo si [gradientIsRadial] es true. ---
    var gradientAngleDegrees: Float = 90f,
    var gradientIsRadial: Boolean = false,
    // --- Modo Negro & Blanco elegido en la rueda de color (ver
    // LayerColorPickerDialog en LayerDialogs.kt): true si el usuario armó
    // el color/degradado ACTUAL con ese modo prendido (la rueda dibujada
    // en escala de grises). Es independiente de useGradientColor — se
    // puede combinar con un degradado gris. NO afecta el color real
    // guardado (customColorArgb/customGradientStart/EndArgb ya quedan en
    // gris puro si el modo estaba prendido); esto solo existe para que el
    // switch de la ventanita de color se muestre en el mismo estado en
    // que quedó la última vez que se aplicó, en vez de resetearse a
    // apagado cada vez que se reabre — el bug real que se reportó.
    var useBlackAndWhiteMode: Boolean = false,
    // FASE 3.1.2 — Cierre definitivo de ownership/concurrencia GPU.
    //
    // Identidad de VERSIÓN del contenido visual real (los píxeles) de
    // esta capa — distinta de [id] (identidad de la CAPA, estable de por
    // vida, ver el comentario de `id` más arriba) y de cualquier handle
    // GPU (que ni siquiera vive acá, ver GLRenderer.layerTextures). Se
    // incrementa EXPLÍCITAMENTE, nunca en forma automática, cada vez que
    // `sourceUri` pasa a apuntar a una imagen REALMENTE distinta —
    // auditoría completa de todos los sitios que la tocan en
    // `docs/fases/FASE_3_1_2_CONTENT_REVISION.md`.
    //
    // BUG REAL que esto cierra (ver ese documento para el detalle
    // completo): un decode asíncrono — el usuario reemplaza la imagen de
    // una capa, o deshace/rehace, dos veces seguidas y rápido — puede
    // terminar DESPUÉS de que la capa ya representa otro contenido. Sin
    // una forma de detectar "esto ya no corresponde", ese resultado
    // tardío puede terminar subiéndose a GPU igual, mostrando la imagen
    // EQUIVOCADA. [contentRevision] es la versión que se captura ANTES
    // de empezar cualquier decode y se vuelve a comparar DESPUÉS, justo
    // antes de publicar/subir el resultado — ver
    // [com.yeivikas.olyzecs.engine.render.SingleResourceHandoff.takeIfCurrent]
    // y `GLRenderer.uploadTextureIfNeeded`/`onSurfaceCreated`.
    //
    // Es un parámetro NORMAL del constructor (ni `@Transient` ni
    // calculado) A PROPÓSITO: así TODO `.copy()` ya existente en el
    // proyecto (la inmensa mayoría, que no toca `sourceUri`) preserva la
    // revisión automáticamente, sin tocar un solo call site — mismo
    // criterio que ya usan `id`/`colorIndex`. Solo los sitios que
    // realmente cambian `sourceUri` necesitan (y ya fueron auditados
    // para) incrementarla a mano.
    //
    // `@Volatile`: lo escribe el hilo principal (cualquier cambio real de
    // `sourceUri`) y lo lee el hilo de GL en cada frame — misma garantía
    // de visibilidad entre hilos que `zIndex`/`parallaxFactor` más
    // arriba, por el mismo motivo (un único escritor real, nunca el
    // propio hilo de GL).
    //
    // FASE 3.1.3-R3.1 — mismo caso y misma solución que [sourceUri] más
    // arriba (ver ese comentario para el detalle completo): `private
    // set` no es válido en un parámetro de constructor, así que el
    // bloqueo real de `layer.contentRevision = x` desde fuera de esta
    // clase se logra con `@set:Deprecated(level = ERROR)`.
    @Volatile
    @set:Deprecated(
        message = "No asignar contentRevision directamente — usa Layer.updateContentIdentity().",
        level = DeprecationLevel.ERROR
    )
    var contentRevision: Int = 0
) {
    // FASE 3.1 — Cierre y hardening de ownership de GPU/bitmaps.
    //
    // BUG REAL DE OWNERSHIP corregido en esta fase: hasta la Fase 3, acá
    // vivía `var glTextureId: Int = -1`, un campo público y mutable que
    // GLRenderer (hilo de GL) escribía cada frame, PERO que
    // EditorViewModel también escribía directamente en varios lugares
    // (`layer.glTextureId = -1` en replaceLayer/restoreSnapshot/
    // discardChangesAndExit) — la documentación de Fase 3 afirmaba "GL
    // thread ownership" para este campo, pero el código real permitía
    // que el hilo principal lo tocara sin ninguna disciplina. `@Volatile`
    // solo resuelve VISIBILIDAD entre hilos, nunca ownership: no impide
    // que dos hilos distintos decidan, cada uno por su cuenta, qué valor
    // "correcto" debería tener.
    //
    // La corrección real es estructural, no un modificador nuevo: el
    // texture id GL YA NO EXISTE como campo de `Layer`. Vive
    // exclusivamente dentro de `GLRenderer` (un `Map<layerId, Int>`
    // privado de esa clase, nunca expuesto) — ningún código fuera de
    // `GLRenderer` tiene, siquiera, una referencia a un texture id real.
    // Esto es literalmente la arquitectura que pide el informe de esta
    // fase: "GPU RESOURCE REGISTRY / CACHE", vivo del lado del GL thread,
    // en vez de un campo de estado del proyecto/capa.
    //
    // El hilo principal SIGUE necesitando poder decir "esta capa cambió
    // de fuente, olvidate de la textura vieja" (p. ej. al reemplazar la
    // imagen, o al deshacer/rehacer un efecto) — para eso existe
    // [requestTextureInvalidation]/[consumeTextureInvalidationRequest]
    // más abajo: una bandera atómica de PEDIDO, nunca una escritura
    // directa del recurso GPU en sí.

    @Transient
    private val textureInvalidationRequest = TextureInvalidationRequest()

    /**
     * Productor (UI/ViewModel, cualquier hilo): pide que el hilo de GL
     * descarte, en el próximo frame, la textura GPU vigente de esta capa
     * (ver `GLRenderer.onDrawFrame`). NO borra nada acá mismo — el hilo
     * de GL es el único que decide cuándo y cómo liberar el recurso real
     * (`glDeleteTextures`), que solo es seguro llamar desde ese hilo.
     */
    fun requestTextureInvalidation() {
        textureInvalidationRequest.request()
    }

    /**
     * Consumidor único (pensado para `GLRenderer`, hilo de GL): toma el
     * pedido de invalidación de forma atómica — un pedido se consume
     * exactamente una vez, sin importar cuántas veces se haya llamado a
     * [requestTextureInvalidation] mientras tanto (da igual pedirlo una
     * vez o varias: lo único que importa es "sí, hay que invalidar").
     */
    fun consumeTextureInvalidationRequest(): Boolean = textureInvalidationRequest.consume()

    // Referencia temporal al bitmap recién decodificado, en tránsito
    // hacia GL, hasta que se sube a GPU.
    //
    // BUG REAL DE CONCURRENCIA corregido en esta fase: con el `var`
    // simple original, `GLRenderer.uploadTextureIfNeeded` leía
    // `pendingBitmap` y, en un paso SEPARADO (un `finally` más abajo),
    // lo ponía en `null` — dos operaciones, no una. Si el hilo principal
    // publicaba un bitmap NUEVO justo entre esas dos operaciones (p. ej.
    // el usuario suelta el dedo de la rueda de color justo cuando GL
    // está subiendo el frame anterior), ese bitmap nuevo quedaba pisado
    // por el `null` sin haberse subido nunca — un *lost update* real. Ver
    // [SingleResourceHandoff] para el detalle completo del arreglo:
    // ahora "tomar" es una única operación atómica
    // (`AtomicReference.getAndSet`/compare-and-set), sin ninguna ventana
    // donde eso pueda pasar. El productor (UI/ViewModel/ProjectStorage/
    // LayerRepository, al decodificar una imagen nueva) llama a
    // `.publish(bitmap, contentRevision)`, etiquetando el resultado con
    // la versión de contenido que representa; el único consumidor real
    // (GLRenderer, hilo de GL) llama a `.takeIfCurrent(contentRevision)`,
    // que además RECHAZA (Fase 3.1.2) cualquier resultado cuya etiqueta
    // ya no coincida con la revisión vigente — ver el comentario de
    // [contentRevision] más abajo y [SingleResourceHandoff] para el
    // detalle completo.
    @Transient
    val pendingBitmap: SingleResourceHandoff<Bitmap> = SingleResourceHandoff()

    // FASE 3.1.3-R2 — Cierre de la última ventana de TOCTOU real
    // detectada en el commit atómico de GPU: lectura ENTRECORTADA de
    // [sourceUri] y [contentRevision] como dos campos `@Volatile`
    // INDEPENDIENTES.
    //
    // BUG REAL (no hipotético) que esto cierra: `LayerContentState.applyTo`
    // y `EditorViewModel.restoreSnapshot` actualizan el contenido de una
    // capa VIVA con DOS asignaciones separadas:
    //
    //     layer.sourceUri = nuevoUri
    //     layer.contentRevision = nuevaRevision
    //
    // Cada una de esas dos escrituras es, por separado, inmediatamente
    // visible entre hilos (`@Volatile` de cada campo lo garantiza) — pero
    // NADA impide que el hilo de GL, corriendo en paralelo real, lea
    // `sourceUri` DESPUÉS de la primera escritura pero `contentRevision`
    // ANTES de la segunda (o viceversa): un PAR INCONSISTENTE que nunca
    // existió del lado del hilo principal, pero que el hilo de GL sí
    // podía llegar a observar si sus dos lecturas caían justo en esa
    // ventana entre ambas asignaciones.
    //
    // [ContentIdentity] agrupa ambos valores en UN SOLO objeto inmutable,
    // publicado como una ÚNICA referencia `@Volatile`
    // ([contentIdentity]) — nunca como dos campos sueltos. Gracias a la
    // semántica de "publicación segura" del Java Memory Model (una
    // escritura `volatile` establece happens-before con una lectura
    // `volatile` posterior de la MISMA variable en otro hilo, y arrastra
    // consigo todas las escrituras previas del hilo escritor), cualquier
    // lector que solo lea [contentIdentity] obtiene, en una única
    // operación atómica (una referencia de objeto no puede leerse "a
    // medias"), un par `(sourceUri, revision)` que SÍ existió como una
    // unidad lógica coherente en algún instante — nunca una mezcla
    // inventada de dos instantes distintos.
    //
    // [sourceUri]/[contentRevision] SIGUEN existiendo como antes (no se
    // eliminan ni se convierten en propiedades derivadas): son
    // parámetros del constructor primario, imprescindibles para que
    // `.copy(sourceUri = ..., contentRevision = ...)` — usado en
    // decenas de sitios de `EditorViewModel` — siga funcionando sin
    // tocar ni un solo call site. [contentIdentity] es un campo NUEVO,
    // adicional, que actúa como "cerrojo de consistencia" solo para los
    // caminos que mutan una capa VIVA en el lugar (no vía `.copy()`) —
    // los únicos dos, auditados, son los mencionados arriba. Los
    // caminos basados en `.copy()` (`replaceLayerImage`, `commitLayerRecolor`,
    // `revertLayerEditSession`, `revertLayerToUri`, undo/redo de otros
    // campos, etc.) no necesitan este mecanismo: cada `.copy()` produce
    // un objeto `Layer` NUEVO cuyo `contentIdentity` (ver el
    // inicializador de abajo, mismo patrón que `pendingBitmap`: un
    // campo `@Transient` fuera del constructor primario se recalcula
    // fresco en CADA instancia nueva) ya arranca correctamente
    // sincronizado con el `sourceUri`/`contentRevision` de ESA instancia
    // — y la identidad REFERENCIAL de la instancia (ver
    // `GLRenderer.currentLayerIfStillRequested`, `expectedInstance`) ya
    // detecta ese reemplazo de objeto de todas formas.
    data class ContentIdentity(val sourceUri: Uri, val revision: Int)

    @Transient
    @Volatile
    var contentIdentity: ContentIdentity = ContentIdentity(sourceUri, contentRevision)
        private set

    /**
     * Único punto permitido para cambiar el contenido de una capa VIVA
     * en el lugar (sin pasar por `.copy()`). Actualiza [sourceUri],
     * [contentRevision] y [contentIdentity] como una única operación
     * lógica — `contentIdentity` se escribe EN ÚLTIMO LUGAR a propósito
     * (ver el comentario grande de arriba: es la escritura `volatile`
     * que publica, de forma segura, las dos anteriores).
     *
     * `private set` en [contentIdentity] obliga a que CUALQUIER cambio
     * futuro pase por acá — no hay forma de actualizar `sourceUri`/
     * `contentRevision` en el lugar sin mantener [contentIdentity]
     * sincronizado, ni por descuido.
     *
     * `@Suppress("DEPRECATION_ERROR")`: único punto autorizado a saltarse
     * el `@set:Deprecated(level = ERROR)` de [sourceUri]/[contentRevision]
     * (ver esos comentarios) — a propósito, porque esta función ES la vía
     * legal para mutarlos.
     */
    @Suppress("DEPRECATION_ERROR")
    fun updateContentIdentity(newSourceUri: Uri, newRevision: Int) {
        sourceUri = newSourceUri
        contentRevision = newRevision
        contentIdentity = ContentIdentity(newSourceUri, newRevision)
    }
}
