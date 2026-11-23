package com.yeivikas.olyzecs.ui

/**
 * A qué tipo de capa pertenece el cajón del panel "Módulos" (pestaña de abajo del timeline), según lo que esté
 * seleccionado: cada tipo de capa tiene SU lista de módulos, y el cajón solo muestra la del tipo seleccionado.
 *
 *  - [LAYER]: capas de imagen (y futuras de modelo 3D, etc.): Efectos (Sombra, Reflejo), Animación y Cámara (Zoom X).
 *    También es el destino cuando no hay nada seleccionado (comportamiento de siempre).
 *  - [AUDIO]: un carril de audio seleccionado. Los módulos de imagen/cámara no tienen sentido sobre audio, así que no se
 *    muestran; la lista de audio ([AudioModulesList]) crece con los efectos reales de audio, por categorías.
 *
 * Es solo QUÉ lista se muestra: ningún módulo de capa se elimina ni se modifica, y vuelven apenas se selecciona una capa.
 */
internal enum class ModulesTarget { LAYER, AUDIO }

/**
 * Destino del cajón de Módulos, derivado del estado REAL de selección (no de un solo flag de la UI).
 *
 * La selección de un carril de audio vive en la UI (`selectedAudioTrackId` en `EditorScreen`) y la de capa en el
 * ViewModel (`selectedLayerId`). Se mantienen excluyentes al tocar, pero hay caminos que cambian solo una de las dos
 * (deshacer/rehacer restaura `selectedLayerId` desde un snapshot; un carril puede desaparecer). Por eso el destino es
 * [ModulesTarget.AUDIO] únicamente cuando hay un carril elegido QUE EXISTE y NO hay ninguna capa seleccionada; en
 * cualquier otro caso (capa de imagen elegida, nada elegido, id de carril ya inexistente) es [ModulesTarget.LAYER].
 *
 * Pura, para probarla sin Compose.
 */
internal fun modulesTargetFor(
    selectedAudioTrackId: String?,
    selectedLayerId: String?,
    existingAudioTrackIds: Collection<String>
): ModulesTarget =
    if (selectedLayerId == null && selectedAudioTrackId != null && selectedAudioTrackId in existingAudioTrackIds) {
        ModulesTarget.AUDIO
    } else {
        ModulesTarget.LAYER
    }
