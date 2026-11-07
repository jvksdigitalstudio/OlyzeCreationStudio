package com.yeivikas.olyzecs.engine.timeline

/**
 * Fotografía mínima del estado de reproducción del editor: lo único que
 * cambian las transiciones de [paused] y [rewound].
 *
 * Existe separada de `EditorUiState` a propósito: ese estado arrastra capas,
 * URIs y otros tipos de Android, mientras que las reglas de "pausar" y
 * "rebobinar" son lógica pura que debe poder verificarse con JUnit en JVM
 * (ver `PlaybackTransitionsTest`).
 */
internal data class PlaybackSnapshot(
    val playheadMs: Long,
    val isPlaying: Boolean,
    val isRecording: Boolean,
    val isCapturing: Boolean
)

/**
 * PAUSAR: detiene reproducción, grabación y captura SIN mover el cabezal.
 *
 * Es la transición correcta para todo evento que solo "saca de escena" el
 * editor sin cerrarlo: la Activity pasa a segundo plano porque se abrió el
 * selector de archivos del sistema (importar audio/imágenes/fondo), el
 * usuario fue a Home, cambió de app o bloqueó la pantalla. En todos esos
 * casos el usuario vuelve y espera encontrar el cabezal donde lo dejó.
 *
 * Caso real que motivó esta separación: para cargar un audio en el cursor el
 * usuario primero ubica el cursor y luego abre el selector de archivos, que
 * llevaba la Activity a `ON_STOP`; rebobinar ahí devolvía el cursor a 0 al
 * volver, perdiendo el punto que el usuario había elegido.
 */
internal fun PlaybackSnapshot.paused(): PlaybackSnapshot =
    copy(isPlaying = false, isRecording = false, isCapturing = false)

/**
 * REBOBINAR: [paused] + cabezal en 0. Solo para ENTRAR al editor o SALIR de
 * él hacia "Mis proyectos" — nunca para un simple paso a segundo plano.
 */
internal fun PlaybackSnapshot.rewound(): PlaybackSnapshot =
    paused().copy(playheadMs = 0L)
