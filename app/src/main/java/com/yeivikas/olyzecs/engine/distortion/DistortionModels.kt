package com.yeivikas.olyzecs.engine.distortion

/**
 * Las 9 herramientas de deformación de la pestaña "Distorsión" (ver
 * `ui/EditorScreen.kt` → `DistortionPanel`), todas respaldadas por el
 * mismo motor de malla ([DistortionField]) — ninguna tiene su propio
 * algoritmo de bitmap aparte, solo una función distinta que calcula
 * hacia dónde debería apuntar cada vértice afectado (ver
 * `distortionTargetUv` en DistortionTools.kt).
 */
enum class DistortionToolType(val label: String) {
    /** Arrastra los píxeles en la dirección del dedo, en tiempo real. */
    WARP("Cepillo de deformación"),

    /** Infla/hunde en forma de burbuja (proyección esférica) dentro del pincel. */
    SPHERE("Esferizar"),

    /** Remolino en sentido horario o antihorario, con velocidad ajustable. */
    TWIRL("Giro"),

    /** Empuja hacia afuera o hacia adentro desde el centro del pincel (dos modos en una sola herramienta, ver [DistortionBrush.bulgeOutward]). */
    BULGE_PINCH("Protuberancia & pellizco"),

    /** Ondas concéntricas tipo splash de agua. */
    CIRCLE_SPLASH("Chapoteo del círculo"),

    /** Estira una zona hacia un punto de anclaje (fijado al bajar el dedo). */
    STRETCH_ANCHOR("Tramo"),

    /** Estira solo en un eje (horizontal o vertical, ver [DistortionBrush.stretchAxis]) — útil para "afinar" o "alargar". */
    STRETCH_AXIS("Estiramiento"),

    /** Clona y refleja una zona sobre el eje que define la dirección del arrastre. */
    MIRROR("Espejo"),

    /** El pincel "inverso": borra la deformación solo donde se pasa, sin perder el resto del trabajo. */
    RECONSTRUCT("Reconstruir / Restaurar zona")
}

/** Eje de la herramienta "Estiramiento" ([DistortionToolType.STRETCH_AXIS]). */
enum class StretchAxis { HORIZONTAL, VERTICAL }

/**
 * Convierte el radio de pincel tal como lo ve el usuario en el slider
 * (0..100, "Tamaño del pincel", pensado como % del LADO MÁS CORTO de la
 * imagen — ver comentario de [DistortionBrush.radiusUv]) al valor real
 * que hay que pasarle a [DistortionBrush.radiusUv].
 *
 * El motor ([DistortionField.applyStroke]) mide distancia normalizada
 * por el ANCHO de la imagen, no por el lado más corto: `dv =
 * (gv-centerV)/aspect`, así que 1 unidad de radio ya representa "un
 * ancho completo" de distancia física. Eso es exactamente lo que hace
 * falta si el ancho ES el lado más corto (imagen vertical o cuadrada,
 * `aspect <= 1`) — pero si el ancho es el lado más LARGO (imagen
 * apaisada, `aspect > 1`), un radioUv crudo cubriría una fracción del
 * lado corto (la altura) más grande que la fracción nominal pedida —
 * hasta el doble en una foto 2:1. Acá se comprime en esos casos para
 * que "18%" sea siempre 18% del lado corto, sin importar la
 * orientación de la imagen.
 */
fun distortionBrushRadiusUv(rawPercent: Float, imageAspect: Float): Float {
    val percent = (rawPercent / 100f).coerceIn(0.01f, 0.9f)
    val aspect = imageAspect.takeIf { it.isFinite() && it > 0f } ?: 1f
    val shortSideCorrection = if (aspect > 1f) 1f / aspect else 1f
    return (percent * shortSideCorrection).coerceAtLeast(1e-4f)
}

/**
 * Ajustes de pincel vigentes en el panel en el momento de pintar — el
 * panel arma uno de estos por cada muestra que llega del dedo (ver
 * `DistortionPanel.currentBrush()` en EditorScreen.kt) y se lo pasa a
 * [DistortionField.applyStroke].
 *
 * [radiusUv] está expresado en unidades UV normalizadas por el LADO MÁS
 * CORTO de la imagen (no en píxeles de pantalla ni en UV crudo de cada
 * eje por separado) — así el pincel es un círculo real sobre la imagen,
 * sin importar el aspect ratio ni el zoom con el que se esté viendo.
 *
 * [feather] es la "dureza del borde" tal como se le muestra al usuario
 * (0 = borde muy suave y difuminado, 1 = borde duro/nítido) — ver
 * [distortionFalloff] para la curva exacta.
 *
 * [intensity] es la presión/intensidad del efecto por MUESTRA (0..1, un
 * pincel a intensidad baja necesita pasar varias veces por el mismo
 * lugar para llegar al mismo resultado que uno a intensidad alta en una
 * sola pasada) — no la fuerza total acumulada de la sesión.
 */
data class DistortionBrush(
    val tool: DistortionToolType,
    val radiusUv: Float,
    val feather: Float,
    val intensity: Float,
    /** [DistortionToolType.TWIRL]: sentido del remolino. */
    val twirlClockwise: Boolean = true,
    /** [DistortionToolType.STRETCH_AXIS]: en qué eje estira. */
    val stretchAxis: StretchAxis = StretchAxis.HORIZONTAL,
    /**
     * [DistortionToolType.SPHERE] y [DistortionToolType.BULGE_PINCH]:
     * true = protuberancia/inflar (empuja afuera, efecto lupa), false =
     * pellizco/hundir (empuja adentro, efecto succión). Mismo campo para
     * ambas herramientas — es el mismo concepto de signo, solo cambia la
     * curva de caída (ver [distortionTargetUv]).
     */
    val bulgeOutward: Boolean = true,
    /**
     * [DistortionToolType.STRETCH_AXIS]: true = alargar (empuja hacia
     * afuera del centro del pincel a lo largo del eje elegido), false =
     * afinar (empuja hacia adentro).
     */
    val stretchOutward: Boolean = true,
    /**
     * [DistortionToolType.STRETCH_ANCHOR]: punto de anclaje hacia el que
     * se estira, en UV — fijado al bajar el dedo para este trazo (ver
     * DistortionPanel, que lo captura del primer punto del trazo). Nulo
     * para cualquier otra herramienta.
     */
    val anchorU: Float? = null,
    val anchorV: Float? = null
)
