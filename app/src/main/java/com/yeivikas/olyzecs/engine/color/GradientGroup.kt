package com.yeivikas.olyzecs.engine.color

/**
 * Espacio de color en el que se interpola un degradado repartido entre
 * varias capas (ver [GroupGradientEngine]).
 *
 * - [RGB]: mezcla directa canal a canal. Es el valor por defecto porque es
 *   lo que hace el "Color selected → Gradient" de FL Studio: medido sobre
 *   capturas de referencia (magenta → menta en 23 pistas) reproduce los
 *   colores con un error medio de ~4 niveles, frente a ~72 del recorrido por
 *   matiz y ~38 de Oklab. A mitad de recorrido entre dos colores muy distintos
 *   sale un tono apagado (p. ej. magenta → menta pasa por un gris azulado),
 *   y justo eso es lo que se ve en FL. La luminosidad avanza de forma pareja.
 * - [HUE]: ruta corta del matiz (rueda de color) con saturación y brillo
 *   interpolados linealmente. Mantiene los colores vivos (arcoíris), pero la
 *   luminosidad percibida NO avanza pareja: con azul → lima salta de golpe en
 *   las primeras filas y queda casi plana en las del medio.
 * - [PERCEPTUAL]: mezcla en Oklab. Es el aspecto que tenía el degradado
 *   multicapa antes de existir el selector (ver [LegacyGradientMigration]).
 */
enum class GradientColorSpace {
    HUE,
    RGB,
    PERCEPTUAL;

    companion object {
        val DEFAULT: GradientColorSpace = RGB

        /** Lectura tolerante desde el guardado: un valor desconocido o ausente cae en [DEFAULT]. */
        fun fromStorage(name: String?): GradientColorSpace =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Pertenencia de una capa (o carril de audio) a un degradado repartido
 * entre varias filas.
 *
 * Es solo METADATO DE EDICIÓN: permite reabrir el diálogo de color con el
 * degradado original (extremos y espacio de color) sobre las mismas filas.
 * NO afecta a lo que se dibuja — cada fila dibuja su propio color sólido
 * guardado — así que mover, insertar o borrar filas nunca cambia colores.
 *
 * @property id identificador del grupo, compartido por todas las filas
 *   que recibieron el mismo reparto.
 * @property space espacio de color con el que se calculó el reparto.
 */
data class GradientGroupRef(
    val id: String,
    val space: GradientColorSpace
)
