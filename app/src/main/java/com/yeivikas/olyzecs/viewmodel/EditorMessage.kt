package com.yeivikas.olyzecs.viewmodel

/**
 * Avisos puntuales ("de un solo disparo") que el editor le da al usuario: algo que pasó y que no queda reflejado en ninguna pantalla de
 * forma permanente, así que la UI lo muestra una vez y lo olvida. Mismo patrón que `TimelineEvent`, para lo que no pertenece a la línea
 * de tiempo.
 *
 * Existen porque una operación que el usuario inició y que no se pudo completar NO puede fallar en silencio: sin aviso, no hay forma de
 * distinguir "no hizo nada" de "falló".
 */
sealed interface EditorMessage {
    /**
     * El archivo elegido no se pudo usar como audio (no se le pudo leer una duración: formato no soportado o archivo dañado). No se creó ni
     * se modificó ningún clip. [displayName] es el nombre del archivo tal como lo ve el usuario.
     */
    data class AudioImportFailed(val displayName: String) : EditorMessage
}
