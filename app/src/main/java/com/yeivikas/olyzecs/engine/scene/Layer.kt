package com.yeivikas.olyzecs.engine.scene

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID
import com.yeivikas.olyzecs.engine.camera.CameraTrack
import com.yeivikas.olyzecs.engine.effects.LookSettings

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
    @Volatile var sourceUri: Uri,
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
    var useBlackAndWhiteMode: Boolean = false
) {
    // Textura GL asignada en tiempo de render; -1 = no subida todavía.
    // El hilo de GL la escribe cada vez que sube/borra una textura; el
    // hilo principal la LEE en varios puntos (p. ej. para decidir si hay
    // que borrar la textura vieja antes de reemplazar la imagen) y
    // ocasionalmente la RESETEA a -1 desde el hilo principal como señal
    // de "esta capa cambió de fuente, GL: volvé a decodificar/subir" (ver
    // EditorViewModel.replaceLayer/restoreSnapshot). `@Volatile` asegura
    // que esa señal cruce de un hilo al otro sin demora indefinida.
    @Transient
    @Volatile
    var glTextureId: Int = -1

    // Referencia temporal al bitmap decodificado, solo hasta que se sube a GL.
    // Mismo razonamiento que glTextureId: el hilo de GL la consume y la
    // limpia (pendingBitmap = null) tras subirla, pero el hilo principal
    // es quien la ESCRIBE por primera vez (tras decodificar una imagen
    // nueva en un import/reemplazo) — `@Volatile` para visibilidad segura
    // en ambos sentidos.
    @Transient
    @Volatile
    var pendingBitmap: Bitmap? = null
}
