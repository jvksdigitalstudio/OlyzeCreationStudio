package com.yeivikas.olyzecs.engine.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Un punto de apoyo adicional para la sombra de contacto — ver el
 * comentario grande sobre [ImageEffectsParams.contactShadowPoints] para
 * el porqué. Todos los campos son RELATIVOS a los valores base
 * ([ImageEffectsParams.contactShadowSize] / [ImageEffectsParams.contactShadowIntensity]),
 * nunca valores absolutos — así ajustar el tamaño/intensidad "global" de
 * la sombra de contacto sigue afectando a TODOS los puntos
 * proporcionalmente, en vez de que cada uno quede "congelado" con su
 * propio valor absoluto y haya que reajustarlos uno por uno.
 */
data class ContactShadowPoint(
    // Desplazamiento horizontal del punto de apoyo respecto al centro
    // del sujeto, como fracción de la MITAD del ancho del sujeto — por
    // ejemplo, -0.6 pone el punto al 60% del camino hacia el borde
    // izquierdo, +0.6 al 60% hacia el derecho, 0 lo centra (igual que la
    // mancha clásica única). No está estrictamente acotado a -1..1
    // (ver [sanitized]) porque un punto de apoyo real a veces cae
    // apenas afuera de la silueta (una pata que se abre más que el
    // cuerpo del objeto que la sostiene).
    val xOffset: Float = 0f,
    // Multiplicador sobre [ImageEffectsParams.contactShadowSize] — 1 =
    // mismo tamaño que la mancha base, <1 más chica (pata más lejos de
    // cámara/más fina), >1 más grande.
    val sizeScale: Float = 1f,
    // Multiplicador sobre [ImageEffectsParams.contactShadowIntensity] —
    // 1 = misma opacidad que la base, <1 más tenue (p.ej. una pata más
    // lejos de la fuente de luz recibe menos oclusión de contacto).
    val intensityScale: Float = 1f
) {
    fun sanitized(): ContactShadowPoint = copy(
        xOffset = xOffset.coerceIn(-1.5f, 1.5f),
        sizeScale = sizeScale.coerceIn(0.2f, 2f),
        intensityScale = intensityScale.coerceIn(0f, 1.5f)
    )
}

/**
 * Parámetros de la pestaña "Efectos" del modo "Editando imagen" (ver
 * EditorScreen.EditImageToolsHeader / EffectsPanel): vive al lado de
 * "Recolor" y "3D", con nivel de control "premium" — no solo intensidad
 * de la sombra, sino también su suavizado, distancia y ángulo, como en
 * cualquier herramienta de edición profesional (estilo "Drop Shadow" de
 * Photoshop/Affinity).
 *
 * Todos los campos están normalizados 0..1 (o 0..360 para el ángulo) para
 * que la UI trabaje siempre con sliders "de porcentaje" simples, y sea
 * [ImageEffects] quien traduce eso a píxeles/unidades reales según el
 * tamaño de cada imagen — así el mismo ajuste se ve proporcional tanto en
 * una capa chica como en una grande.
 */
/**
 * Posición del trazo de "Contorno" respecto de la silueta real del
 * sujeto — mismo concepto que el "Position" de cualquier Stroke
 * profesional (Photoshop/Affinity/Figma):
 *  - [OUTSIDE]: la silueta se agranda y el anillo sobresale hacia
 *    afuera del sujeto (comportamiento clásico, el único que existía
 *    antes).
 *  - [INSIDE]: el anillo queda completamente adentro del sujeto,
 *    pintado sobre su propio borde interior — no cambia el tamaño
 *    aparente del sujeto.
 *  - [CENTER]: mitad afuera, mitad adentro — el trazo queda centrado
 *    justo sobre el borde real, ni agranda ni "come" la silueta.
 */
enum class OutlineStrokePosition { OUTSIDE, CENTER, INSIDE }

/**
 * Modo de mezcla del "Resplandor" contra lo que ya está pintado debajo
 * — control "Blend Mode" del "Outer Glow" de cualquier compositor
 * profesional (Photoshop/After Effects/Affinity), donde el default de
 * fábrica de un glow casi nunca es "Normal": un halo de luz que se
 * superpone opaco tapa lo que hay debajo en vez de sumarse a la luz ya
 * existente, que es como se comporta un brillo real.
 *  - [NORMAL]: el resplandor se pinta encima tal cual, sin mezclarse con
 *    el color de fondo — el único comportamiento que existía antes de
 *    este control. Sigue siendo el default para no romper proyectos
 *    guardados.
 *  - [SCREEN]: "Screen" clásico — `1-(1-Cs)*(1-Cd)`. Nunca oscurece,
 *    solo aclara; es el modo más usado para halos de luz porque preserva
 *    el color de fondo en vez de reemplazarlo, el mismo look que
 *    "Screen" en Photoshop.
 *  - [ADD] ("Linear Dodge"/"Add"): `Cs+Cd`, sin tope hasta clamping a
 *    blanco — más intenso/quemado que [SCREEN], ideal para energía o
 *    brillos tipo neón/plasma que necesitan "explotar" a blanco en el
 *    núcleo.
 *  - [LIGHTEN]: `max(Cs,Cd)` — conserva el más claro de los dos colores
 *    canal por canal; más sutil que [SCREEN]/[ADD], útil cuando el
 *    resplandor debe integrarse sin sobre-exponer el fondo.
 */
enum class GlowBlendMode { NORMAL, SCREEN, ADD, LIGHTEN }

data class ImageEffectsParams(
    // BUG REAL corregido acá (reporte del usuario: "difuminar... está al
    // revés, afecta a la imagen y su sombra"): `blur` YA NO difumina el
    // sujeto entero — difumina el FONDO alrededor de él (estilo "retrato"/
    // profundidad de campo de una cámara real), protegiendo por completo
    // el sujeto y dejando su sombra (que ya se genera aparte, ver
    // [compositeWithEffects]) totalmente intacta. Ver [applyBackgroundBlur].
    val blur: Float = 0f,             // 0..1 — difuminado del FONDO (protege sujeto y sombra)
    val edgeFeather: Float = 0f,      // 0..1 — suaviza la transición sujeto/fondo del difuminado de fondo
    val sharpen: Float = 0f,          // 0..1 — nitidez del sujeto (realce de bordes/detalle, "unsharp mask")
    val saturation: Float = 1f,       // 0..2 — 0 = blanco y negro, 1 = original, 2 = saturación máxima
    val brightness: Float = 0f,       // -1..1 — brillo aditivo por canal RGB (-100%..+100%)
    val contrast: Float = 1f,         // 0..2 — 1 = neutro, fórmula estándar (color-128)*contrast+128
    val hue: Float = 0f,              // 0..360 — rotación de matiz sobre TODO el sujeto (0 = sin cambio)
    val outlineIntensity: Float = 0f, // 0..1 — grosor/intensidad del contorno (0 = sin contorno)
    val outlineColor: Int = Color.WHITE,
    // Segundo color del contorno, usado únicamente cuando
    // [outlineGradientEnabled] está activo — ver el KDoc de
    // [ImageEffects.buildDilatedOutline] para el criterio exacto de
    // interpolación. En `false` (default) [outlineColor] sigue siendo el
    // ÚNICO color, comportamiento idéntico al de siempre.
    val outlineColor2: Int = Color.WHITE,
    // Degradado del contorno (dos colores) — A PEDIDO DEL USUARIO. En
    // `false` (default) el contorno sale sólido de [outlineColor], EXACTO
    // como siempre. En `true`, el trazo pasa de [outlineColor] (pegado al
    // borde real del sujeto) a [outlineColor2] (en la punta del trazo —
    // afuera para OUTSIDE, adentro para INSIDE; en CENTER cada mitad
    // hace su propio degradado hacia esa misma punta).
    val outlineGradientEnabled: Boolean = false,
    // Difuminado propio del contorno — hasta acá el anillo salía
    // SIEMPRE con borde duro (ver [buildDilatedOutline], que trabaja a
    // umbral); esto agrega un difuminado posterior sobre ese anillo ya
    // construido (mismo criterio que [glowBlur]/[shadowBlur]: un
    // segundo radio de blur aplicado ENCIMA del resultado base), así
    // "Contorno" puede dar tanto una línea nítida tipo cómic (0, de
    // siempre) como un borde suave tipo halo delgado.
    val outlineFeather: Float = 0f, // 0..1 — difuminado del borde del contorno (0 = borde duro, clásico)
    // Posición del trazo respecto de la silueta real — el mismo control
    // "Position" (Outside/Center/Inside) de cualquier Stroke/Contorno
    // profesional (Photoshop, Affinity, Figma). Antes SOLO existía
    // "afuera" (la silueta se agranda y el anillo sobresale del
    // sujeto). En 0/[OutlineStrokePosition.OUTSIDE] el comportamiento es
    // EXACTAMENTE el de siempre.
    val outlinePosition: OutlineStrokePosition = OutlineStrokePosition.OUTSIDE,
    val glowIntensity: Float = 0f,    // 0..1 — opacidad del resplandor (0 = sin resplandor)
    val glowBlur: Float = 0.5f,       // 0..1 — difuminado propio del resplandor (radio)
    val glowColor: Int = Color.WHITE,
    // Spread/Choke del resplandor — mismo concepto exacto que ya existe
    // en "Sombra" ([shadowSpread]): dilata el núcleo de la silueta
    // teñida ANTES de difuminarla, y reduce proporcionalmente el radio
    // de difuminado efectivo. El resultado es un halo con un núcleo más
    // sólido/definido pegado al sujeto y una caída más corta, en vez de
    // un halo parejo de punta a punta — el control "Spread" del "Outer
    // Glow" de Photoshop. En 0 (default) el comportamiento es
    // EXACTAMENTE el de antes.
    val glowSpread: Float = 0f,       // 0..1 — expansión del núcleo del resplandor antes del difuminado
    // Distancia/ángulo del resplandor — hasta acá el halo salía SIEMPRE
    // centrado parejo alrededor del sujeto; esto lo puede correr hacia
    // un lado, mismo mecanismo exacto que [shadowDistance]/
    // [shadowAngleDeg] (mismo `angleDegToOffsetUnitVector`), útil para
    // simular una fuente de luz de color que pega más de un lado que
    // del otro. En 0 (default) sigue centrado, sin cambios.
    val glowDistance: Float = 0f,     // 0..1 — qué tan lejos del centro se corre el resplandor
    val glowAngleDeg: Float = 135f,   // 0..360 — dirección hacia la que se corre (solo importa si glowDistance > 0)
    // Segundo color del resplandor, usado únicamente cuando
    // [glowGradientEnabled] está activo — mismo criterio EXACTO que
    // [outlineColor2]/[outlineGradientEnabled] (ver su KDoc), aplicado acá
    // al halo en vez del anillo: [glowColor] queda pegado al núcleo (junto
    // al sujeto, donde el halo todavía está más opaco) y el degradado pasa
    // a [glowColor2] hacia la punta exterior, más difusa, del resplandor.
    // En `false` (default) [glowColor] sigue siendo el ÚNICO color, EXACTO
    // como siempre — ver [ImageEffects.buildGlowGradientTint].
    val glowColor2: Int = Color.WHITE,
    // Degradado del resplandor (dos colores) — A PEDIDO DEL USUARIO,
    // mismo patrón que [outlineGradientEnabled]. En `false` (default) el
    // resplandor sale sólido de [glowColor], sin cambios respecto de
    // antes.
    val glowGradientEnabled: Boolean = false,
    // Modo de mezcla del resplandor contra el fondo — ver
    // [GlowBlendMode] para el detalle de cada modo. En
    // [GlowBlendMode.NORMAL] (default) el comportamiento es EXACTAMENTE
    // el de siempre (composición simple, sin mezcla).
    val glowBlendMode: GlowBlendMode = GlowBlendMode.NORMAL,
    val shadowIntensity: Float = 0f,  // 0..1 — opacidad de la sombra proyectada (0 = sin sombra)
    val shadowBlur: Float = 0.5f,     // 0..1 — suavizado/difuminado propio de la sombra
    // "Spread"/Expansión — el control "Spread" clásico de Photoshop:
    // agranda la silueta de la sombra ANTES de difuminarla (dilatación
    // dura) y, a la vez, reduce proporcionalmente el radio de difuminado
    // final — el resultado es una sombra con un núcleo más grande y
    // sólido y un borde más ajustado/definido, en vez de simplemente
    // "más grande y más difusa" (que es lo único que shadowBlur/
    // shadowDistance ya lograban). En 0 el comportamiento es
    // exactamente el de antes (sin cambios).
    val shadowSpread: Float = 0f,     // 0..1 — expansión del núcleo de la sombra antes del difuminado
    // Escala independiente de la sombra proyectada — en una sombra real
    // de piso, el tamaño de la sombra casi nunca es 1:1 con el objeto
    // (varía con la altura de la fuente de luz y el ángulo), así que
    // fijarla siempre al mismo tamaño del sujeto es la limitación más
    // notoria de cualquier dropshadow "básico". Pivotea desde el punto
    // de apoyo (el pie del sujeto, ver [compositeWithEffects]), nunca
    // desde el centro, para que escalar no "levante" la sombra del piso.
    val shadowScale: Float = 1f,      // 0.4..2 — 1 = mismo tamaño que el sujeto (comportamiento clásico)
    // Ruido/grano sobre la silueta de la sombra — una sombra 100% lisa
    // se lee como "digital"/vectorial; una pizca de ruido de grano fino
    // rompe esa uniformidad perfecta y ayuda a que se sienta proyectada
    // sobre una superficie real (concreto, tela, tierra), en vez de
    // flotar como una silueta perfecta. Se aplica sobre el ALFA de la
    // silueta antes del difuminado (ver [applyShadowGrain]), así el
    // propio difuminado lo suaviza a una textura sutil en vez de ruido
    // "crudo" de píxel a píxel.
    val shadowNoise: Float = 0f,      // 0..1 — cantidad de grano (0 = sombra lisa, comportamiento clásico)
    val shadowDistance: Float = 0.35f,// 0..1 — qué tan lejos se proyecta la sombra
    val shadowAngleDeg: Float = 135f, // 0..360 — dirección de la sombra (135° = clásica abajo-derecha)
    val shadowColor: Int = Color.BLACK, // color ARGB de la silueta de la sombra (antes fijo en negro)
    val shadowSkewDegrees: Float = 0f,     // -45..45 — inclinación/perspectiva de la sombra proyectada (look "piso en ángulo", 0 = sombra recta)
    // Perspectiva REAL de punto de fuga — [shadowSkewDegrees] de arriba
    // es un shear afín puro: inclina la sombra entera pero mantiene sus
    // líneas paralelas, así que en ángulos extremos se sigue leyendo
    // "estirada" en vez de "apoyada sobre un piso real". Esto agrega,
    // ENCIMA del shear (no en su lugar), una transformación proyectiva
    // de verdad: el extremo más lejano del punto de apoyo converge hacia
    // un punto de fuga, mientras el extremo pegado al pie del sujeto se
    // mantiene sin cambios — el mismo principio de una cámara real
    // mirando un plano en perspectiva, en vez de una simple inclinación
    // 2D. En 0 (default) el comportamiento es EXACTAMENTE el de antes
    // (shear puro, sin convergencia). Ver [buildPerspectiveMatrix].
    val shadowPerspectiveAmount: Float = 0f, // 0..1 — 0 = sin convergencia (shear puro clásico), 1 = convergencia máxima hacia el punto de fuga
    val shadowFadeByDistance: Float = 0f,  // 0..1 — cuánto se desvanece la sombra en su extremo más lejano del punto de apoyo (0 = opacidad pareja, comportamiento clásico)
    // Curva del desvanecimiento por distancia — hasta acá
    // [shadowFadeByDistance] solo controlaba CUÁNTO se desvanece, con
    // una única pendiente LINEAL fija (mismo límite que tenía el
    // reflejo antes de [reflectionOpacityCurve] y la mancha de contacto
    // antes de [contactShadowFalloff]). Mismo criterio de gamma acá:
    // 0.5 (default) reproduce EXACTAMENTE la caída lineal de siempre;
    // por debajo, la sombra se apaga rápido cerca del pie y se
    // extiende tenue hacia el final; por encima, se sostiene fuerte
    // más tiempo y corta de golpe cerca del extremo lejano. Solo tiene
    // efecto visible mientras [shadowFadeByDistance] > 0.
    val shadowOpacityCurve: Float = 0.5f, // 0..1 — forma de la curva de desvanecimiento (0.5 = lineal, clásico)
    // QUIEBRE PISO/PARED — el recurso de fondo "infinito" de cualquier
    // set de estudio real: un piso que se curva hacia arriba y se
    // convierte en pared/fondo vertical, sin una línea de horizonte
    // dura. Hasta acá TODO el motor asume un piso plano infinito, así
    // que la sombra/reflejo se estiran para siempre en la misma
    // dirección — correcto para exteriores, pero se nota "raro" en
    // cualquier composición sobre un fondo de estudio. Este control
    // parte la sombra proyectada en DOS tramos con su propio pivote,
    // en vez de una única inclinación/perspectiva pareja de punta a
    // punta: el tramo cercano al pie sigue recibiendo la inclinación/
    // perspectiva completa (el "piso"), y el tramo lejano recibe solo
    // una fracción de esa inclinación (retenida en [groundWallBreak],
    // ver [buildPiecewiseSkew]) — como si, al llegar a la pared, la
    // sombra dejara de "alejarse" hacia el punto de fuga y empezara a
    // "subir" derecho. Comparte el mismo control con la sección
    // Reflejo ([reflectionSkewDegrees]/[reflectionPerspective]) porque
    // conceptualmente es LA MISMA superficie física para ambos
    // efectos. En 0 (default) el comportamiento es EXACTAMENTE el de
    // antes (piso infinito, sin quiebre). Ver [buildPiecewiseSkew].
    val groundWallBreak: Float = 0f, // 0..1 — 0 = piso infinito (clásico), mayor valor = el quiebre ocurre más cerca del punto de apoyo (más "pared", menos "piso")
    // "Contact-hardening shadow" — la asimetría que faltaba entre
    // sombra y reflejo: [reflectionProgressiveBlur] ya hace que el
    // reflejo se vea nítido cerca del pie y difuso lejos (luz de área,
    // no puntual), pero la sombra proyectada seguía usando un único
    // radio de blur parejo de punta a punta. Es EL efecto que distingue
    // una sombra fotorrealista de una "CGI genérica": bajo luz de área
    // (no un foco puntual ideal), la sombra siempre está más definida
    // pegada al punto de contacto y se va emborronando (penumbra)
    // cuanto más se aleja. Se SUMA encima del difuminado parejo de
    // [shadowBlur], igual criterio que ya usa [reflectionProgressiveBlur]
    // con [reflectionBlur]. En 0 (default) no hay cambio de
    // comportamiento.
    val shadowContactHardening: Float = 0f, // 0..1 — cuánto crece el difuminado hacia el extremo lejano del punto de apoyo (0 = radio parejo, clásico)
    // Modo de mezcla MULTIPLICAR entre toda la "familia de sombras"
    // (proyectada + relleno + contacto) — true por defecto porque es el
    // criterio profesional estándar: dos sombras que se superponen
    // deben OSCURECERSE naturalmente entre sí (como luz bloqueada dos
    // veces), no mezclarse por transparencia normal (que da un gris
    // "sucio"/lechoso donde se tocan). Ver el comentario grande en
    // [blitBlend] para el porqué de la implementación manual píxel a
    // píxel en vez del Xfermode nativo de Android.
    val shadowBlendMultiply: Boolean = true,
    // --- Sombra de RELLENO: una segunda sombra, más suave y corta, en
    // un ángulo distinto a la proyectada principal — representa la luz
    // rebotada/ambiente que en una escena real SIEMPRE ilumina algo el
    // lado "de sombra" del sujeto, evitando que se vea como una silueta
    // con una sola sombra dura y plana. Mismo recurso de "luz de
    // relleno" (fill light) que cualquier set de fotografía o render 3D
    // usa junto a la luz clave. No tiene inclinación ni desvanecimiento
    // propios (esos quedan como el toque extra exclusivo de la sombra
    // principal) para no duplicar controles — ya es, en sí misma, el
    // control "extra" sobre la sombra base.
    val fillShadowIntensity: Float = 0f,   // 0..1 — opacidad de la sombra de relleno (0 = sin ella)
    val fillShadowBlur: Float = 0.7f,      // 0..1 — difuminado propio (más alto que el de la principal por defecto: la luz rebotada es más difusa)
    val fillShadowDistance: Float = 0.2f,  // 0..1 — qué tan lejos se proyecta (más corta que la principal por defecto)
    val fillShadowAngleDeg: Float = 315f,  // 0..360 — dirección (315° por defecto: aprox. opuesta a la principal en 135°, como rebote de luz)
    // BUG DE INFRAESTRUCTURA DE TESTS corregido acá: antes este default
    // llamaba a `Color.rgb(30, 34, 48)` — un MÉTODO real de Android, no
    // una constante (a diferencia de `Color.WHITE`/`Color.BLACK`, que sí
    // son campos `const` y el compilador los inlinea). Bajo JUnit puro
    // (sin Robolectric, que es como corre `testDebugUnitTest` en este
    // proyecto) el .jar "stub" de Android tira `RuntimeException("Stub!")`
    // en cualquier método real invocado — así que CUALQUIER test que
    // construyera un `ImageEffectsParams()` (incluso sin tocar este
    // campo) reventaba en tiempo de ejecución, no por un assert fallido.
    // El entero de abajo es exactamente el mismo valor que devolvía
    // `Color.rgb(30, 34, 48)` (ARGB empaquetado, alfa=0xFF) — cero
    // cambio de comportamiento, solo deja de depender del runtime real
    // de Android para poder testearse con JUnit normal.
    val fillShadowColor: Int = 0xFF1E2230.toInt(), // gris-azulado frío == Color.rgb(30, 34, 48)
    // Escala independiente de la sombra de relleno — mismo criterio que
    // [shadowScale]: pivotea desde el punto de apoyo del sujeto, nunca
    // desde el centro.
    val fillShadowScale: Float = 1f,       // 0.4..2 — 1 = mismo tamaño que el sujeto
    val reflectionIntensity: Float = 0f, // 0..1 — opacidad del reflejo (copia volteada, degradada hacia abajo)
    val reflectionGap: Float = 0f,      // 0..1 — separación entre el pie del sujeto y su reflejo (0 = pegado, estilo clásico)
    val reflectionLength: Float = 1f,   // 0.1..1 — qué porción del alto del sujeto llega a reflejarse (1 = reflejo completo, <1 = reflejo corto que se corta antes de desvanecer del todo)
    val reflectionBlur: Float = 0f,     // 0..1 — difuminado propio del reflejo (look "piso pulido"/vidrio esmerilado), independiente del resto
    // Grano/textura del reflejo — [shadowNoise] rompe la uniformidad
    // perfecta de la sombra, pero el reflejo no tenía un equivalente:
    // un piso pulido real (mármol, vidrio, metal) casi nunca es un
    // espejo perfecto, tiene micro-imperfecciones, polvo y rayones
    // sutiles propios DE LA SUPERFICIE — algo distinto de
    // [reflectionRippleIntensity] (que distorsiona la FORMA del reflejo,
    // como ondas de agua) y de [reflectionBlur] (que difumina la imagen
    // reflejada entera). Este es una TEXTURA FIJA superpuesta sobre el
    // resultado final del reflejo — no se mueve ni se deforma junto con
    // la ondulación, porque conceptualmente vive en el vidrio/piso, no
    // en la imagen que se refleja en él. Ver [applyReflectionGrain].
    val reflectionNoise: Float = 0f,    // 0..1 — cantidad de grano/imperfecciones del reflejo (0 = superficie perfectamente limpia, comportamiento clásico)
    val reflectionSkewDegrees: Float = 0f, // -45..45 — inclinación horizontal del reflejo (look "piso en perspectiva", 0 = reflejo recto)
    val reflectionTintIntensity: Float = 0f, // 0..1 — cuánto del color del reflejo se reemplaza por [reflectionTintColor] (look "reflejo en agua/vidrio de color")
    // Mismo motivo que [fillShadowColor] arriba: entero equivalente a
    // `Color.rgb(58, 110, 150)`, sin depender del runtime de Android.
    val reflectionTintColor: Int = 0xFF3A6E96.toInt(), // color del tinte del reflejo (solo aplica si reflectionTintIntensity > 0)
    val reflectionEdgeFade: Float = 0f, // 0..1 — desvanece los bordes izquierdo/derecho del reflejo (evita el look "recortado" al ras de los costados)
    val reflectionRippleIntensity: Float = 0f, // 0..1 — amplitud de la ondulación tipo "agua" del reflejo (0 = espejo perfectamente plano)
    val reflectionRippleScale: Float = 0.5f,   // 0..1 — densidad de las ondas (0 = pocas y anchas, 1 = muchas y finas)
    // Curva de opacidad del reflejo — antes era un único degradado
    // LINEAL fijo (100%→0% parejo); con esto se puede elegir una caída
    // más lenta al principio y brusca al final, o al revés, en vez de
    // una única pendiente recta — el mismo control de curva de
    // opacidad que ya se sumó a la sombra de contacto
    // ([contactShadowFalloff]), aplicado ahora al reflejo. 0.5 (default)
    // reproduce EXACTAMENTE el degradado lineal de siempre.
    val reflectionOpacityCurve: Float = 0.5f, // 0..1 — 0 = caída lenta al inicio/brusca al final, 0.5 = lineal (clásico), 1 = caída brusca al inicio/lenta al final
    // Efecto FRESNEL — cualquier superficie reflectante real (agua,
    // vidrio, piso pulido) refleja MÁS en ángulo rasante (mirando "a lo
    // lejos", cerca del horizonte) que mirando casi perpendicular
    // (justo debajo, cerca del punto de apoyo) — es la razón física por
    // la que un lago se ve como espejo perfecto en el horizonte pero
    // transparente/tenue justo a los pies de quien mira. El degradado
    // de opacidad de arriba ([reflectionOpacityCurve]) va sin excepción
    // de más fuerte (pegado al pie) a más débil (lejos); esto SUMA un
    // refuerzo de opacidad que crece hacia el extremo lejano,
    // contrarrestando parcialmente ese desvanecimiento — el resultado
    // es un reflejo que nunca desaparece del todo hacia el horizonte,
    // como una superficie reflectante real. En 0 (default) no hay
    // cambio de comportamiento.
    val reflectionFresnel: Float = 0f, // 0..1 — 0 = sin refuerzo (clásico), 1 = refuerzo máximo hacia el extremo lejano/horizonte
    // Perspectiva/compresión del reflejo — un reflejo real sobre un piso
    // visto en ángulo casi nunca tiene la misma altura 1:1 que el
    // sujeto: se ve "aplastado" verticalmente por la perspectiva. Se
    // aplica como una escala vertical pura pivotando desde la fila
    // pegada al pie del sujeto (nunca cambia el ancho — para eso ya
    // existe [reflectionSkewDegrees]). En 1 (default) no hay cambio,
    // comportamiento idéntico al de siempre.
    val reflectionPerspective: Float = 1f, // 0.3..1.5 — 1 = reflejo sin comprimir/estirar (clásico)
    // Difuminado PROGRESIVO por distancia — [reflectionBlur] de arriba
    // aplica un único radio parejo a todo el reflejo; en un piso pulido
    // real, el reflejo se ve cada vez MÁS difuso cuanto más lejos está
    // del punto de apoyo (la luz reflejada rebota más veces / se dispersa
    // más en la zona más alejada). Esto SUMA un difuminado adicional que
    // crece del 0% (fila pegada al pie, nítida) al 100% (fila más
    // lejana) — se COMBINA con [reflectionBlur] (que sigue siendo la
    // base pareja), no lo reemplaza. En 0 (default) el comportamiento es
    // exactamente el de antes. Ver [applyProgressiveBlur].
    val reflectionProgressiveBlur: Float = 0f, // 0..1 — 0 = difuminado parejo (solo el de reflectionBlur), 1 = difuminado creciente máximo hacia el extremo lejano
    // --- Sombra de CONTACTO: mancha corta y suave pegada exactamente al
    // punto de apoyo del sujeto, independiente de la sombra proyectada
    // larga de arriba (shadowIntensity/Distance/Angle) — el mismo
    // recurso "doble sombra" que usa cualquier motor de render 3D o
    // compositing profesional para que el sujeto se sienta "anclado" al
    // piso, además de proyectar sombra lejos. No tiene ángulo ni
    // distancia propios porque, a diferencia de la sombra proyectada,
    // SIEMPRE va justo debajo del sujeto — solo varía su tamaño e
    // intensidad. Reutiliza [shadowColor] a propósito (misma fuente de
    // luz = mismo color de sombra en ambas).
    val contactShadowIntensity: Float = 0f, // 0..1 — opacidad de la sombra de contacto (0 = sin ella)
    val contactShadowSize: Float = 0.5f,    // 0.1..1 — ancho de la mancha, relativo al ancho del sujeto
    val contactShadowBlur: Float = 0.4f,    // 0..1 — difuminado extra sobre el degradado radial base
    // Color propio de la sombra de contacto — antes reutilizaba
    // [shadowColor] a la fuerza ("misma fuente de luz"); en compositing
    // profesional real la mancha de contacto casi siempre queda MÁS
    // oscura/densa que la sombra proyectada larga (la luz está más
    // bloqueada justo en el punto de apoyo), así que necesita su propio
    // control independiente para lograr ese matiz sin arrastrar el color
    // de la otra sombra.
    val contactShadowColor: Int = Color.BLACK,
    // Curva de caída (falloff) del degradado radial — antes era un
    // degradado lineal fijo (0% en el centro → 100% transparente en el
    // borde); con esto se puede elegir entre una mancha de núcleo denso
    // que corta rápido (falloff alto, look "contacto duro") o una que se
    // desvanece de forma más gradual y extendida (falloff bajo, look
    // "apoyo suave") — el mismo control de curva de opacidad que trae
    // cualquier herramienta de sombra de contacto/AO de un motor de
    // render profesional, en vez de un único degradado lineal para todos
    // los casos.
    val contactShadowFalloff: Float = 0.5f, // 0..1 — 0 = caída gradual y extendida, 1 = caída rápida y concentrada
    // Grano/textura sobre la mancha de contacto — asimetría real que
    // faltaba: [shadowNoise] rompe la uniformidad de la sombra
    // proyectada y [reflectionNoise] la del reflejo, pero la mancha de
    // contacto (el punto de apoyo, la más "pegada" y densa de las tres)
    // quedaba perfectamente lisa. Reutiliza el mismo ruido determinístico
    // de un octava que [shadowNoise] (misma familia "sombra", no
    // "superficie reflectante" como el del reflejo) — ver
    // [applyShadowGrain], aplicado ANTES del blur propio de la mancha
    // para que ese blur lo suavice a una textura sutil.
    val contactShadowNoise: Float = 0f, // 0..1 — cantidad de grano en la mancha de contacto (0 = lisa, comportamiento clásico)
    // Oclusión ambiental SOBRE EL PROPIO SUJETO — todo lo de arriba
    // oscurece la SOMBRA o el REFLEJO, pero nunca al sujeto en sí. En
    // compositing profesional real, donde un objeto "toca" el piso, la
    // parte de ABAJO del propio objeto también se oscurece un poco (luz
    // rebotada del entorno bloqueada por el propio piso) — sin esto, el
    // sujeto se sigue viendo un poco "flotado"/pegado con Photoshop
    // incluso con la mejor sombra de contacto del mundo debajo. Se
    // resuelve por COLUMNA: para cada columna del sujeto se detecta su
    // propio punto de apoyo (el píxel opaco más bajo de esa columna, no
    // un borde inferior parejo del rectángulo) y se oscurece un alcance
    // fijo hacia arriba desde ahí — así funciona igual de bien con una
    // silueta de contorno irregular (patas de una silla, dedos de un
    // pie) que con un bloque rectangular. Ver [applyContactOcclusion].
    val groundOcclusionIntensity: Float = 0f, // 0..1 — 0 = sin oclusión (clásico), 1 = oscurecimiento máximo pegado al punto de apoyo
    // Múltiples puntos de apoyo — hasta acá [contactShadowIntensity]/
    // [contactShadowSize]/etc. describen UNA sola mancha, siempre
    // centrada horizontalmente bajo el sujeto: correcto para una figura
    // parada sobre sus dos pies (que ópticamente caen cerca del centro),
    // pero insuficiente para cualquier objeto con varios puntos de apoyo
    // separados (una silla con 4 patas, un trípode, una mesa) — ahí un
    // render/compositing profesional necesita una mancha de contacto
    // POR pata, no una única mancha promedio en el medio. Cada
    // [ContactShadowPoint] es un punto de apoyo adicional, descrito como
    // desplazamiento horizontal relativo al centro del sujeto (nunca
    // vertical: por definición un punto de apoyo siempre está sobre la
    // línea del piso, en el borde inferior) más multiplicadores propios
    // de tamaño/intensidad sobre los valores base de arriba — así cada
    // pata puede ser más chica/tenue que otra (p.ej. una pata más
    // lejana de la fuente de luz) sin necesitar controles totalmente
    // duplicados. Lista VACÍA (default) = comportamiento clásico exacto
    // de siempre: una única mancha centrada usando los valores base sin
    // modificar — cero cambio de comportamiento para cualquier proyecto
    // existente. En cuanto la lista tiene 1+ elementos, REEMPLAZA por
    // completo a la mancha centrada clásica (para tener una mancha en el
    // centro con esta lista, hay que agregar explícitamente un punto con
    // xOffset=0) — así no hay ambigüedad de "¿la clásica se suma o no
    // además de las nuevas?". Ver [buildContactShadow] y su uso en
    // [compositeWithEffects].
    val contactShadowPoints: List<ContactShadowPoint> = emptyList(),
    // --- LIGHT WRAP: envoltura de luz alrededor del borde del sujeto —
    // el recurso que hace que un recorte se sienta "integrado" a su
    // entorno en vez de una pegatina plana encima de él, usado en
    // prácticamente todo compositing profesional de cine/VFX. Como en
    // esta etapa del pipeline el sujeto todavía no tiene un fondo final
    // detrás (es una capa recortada con alfa transparente, el fondo
    // definitivo se decide en otra parte de la app), no hay un color de
    // "escena real" para tomar prestado — así que en vez de simularlo
    // mal, se usa un color elegido a propósito ([lightWrapColor],
    // pensado como la luz ambiente/fondo que va a rodear al sujeto) y se
    // filtra ÚNICAMENTE hacia el anillo interior del borde, nunca hacia
    // afuera de la silueta. Ver [applyLightWrap].
    val lightWrapIntensity: Float = 0f, // 0..1 — cuánto se mezcla el color de envoltura en el borde (0 = sin envoltura)
    // Mismo motivo que [fillShadowColor]/[reflectionTintColor] arriba:
    // entero equivalente a `Color.rgb(255, 244, 214)`.
    val lightWrapColor: Int = 0xFFFFF4D6.toInt(), // color de la luz que "envuelve" el borde (default: luz cálida neutra)
    val lightWrapWidth: Float = 0.4f,    // 0..1 — qué tan ancho hacia adentro llega el anillo de envoltura
    // Ángulo/dirección de la envoltura — hasta acá el anillo envuelve
    // TODO el borde por igual, sin importar hacia dónde "mira" cada
    // parte del contorno; en un compositing real, el light wrap solo
    // tiene sentido físico del lado del sujeto que efectivamente da
    // hacia la fuente de luz/fondo (el otro lado está en sombra propia,
    // no debería recibir el mismo rebote). [lightWrapDirectionality]
    // en 0 (default) mantiene el comportamiento parejo de siempre; al
    // subirlo, la envoltura se concentra en el lado del contorno cuya
    // normal (calculada a partir del gradiente del alfa, ver
    // [applyLightWrap]) apunta hacia [lightWrapAngleDeg], y se atenúa
    // en el lado opuesto — mismo criterio de ángulo/convención que
    // [shadowAngleDeg].
    val lightWrapAngleDeg: Float = 90f, // 0..360 — dirección desde la que "llega" la luz de envoltura (solo importa si lightWrapDirectionality > 0)
    val lightWrapDirectionality: Float = 0f // 0..1 — 0 = envuelve todo el borde por igual (clásico), 1 = solo el lado que mira hacia lightWrapAngleDeg
) {
    /**
     * true si ningún control se movió del neutro — evita reprocesar de
     * más.
     *
     * BUG REAL encontrado y corregido acá: [groundOcclusionIntensity]
     * faltaba en esta lista. A diferencia de sub-parámetros como
     * [shadowBlur] o [reflectionGap] (que solo importan si
     * [shadowIntensity]/[reflectionIntensity] ya están arriba de 0, y
     * por eso no hace falta chequearlos acá aparte), la oclusión
     * ambiental es un control INDEPENDIENTE: se aplica directo sobre el
     * sujeto sin depender de ningún otro slider. Como faltaba en esta
     * lista, activar SOLO "Oclusión ambiental" (con todo lo demás en su
     * valor por defecto) hacía que [isNeutral] diera `true` igual, así
     * que [apply] cortaba camino en la primera línea y nunca llegaba a
     * ejecutar [applyContactOcclusion] — el slider no hacía nada, en
     * silencio, sin ningún error visible.
     */
    val isNeutral: Boolean
        get() = blur <= 0.001f &&
            sharpen <= 0.001f &&
            abs(saturation - 1f) <= 0.001f &&
            abs(brightness) <= 0.001f &&
            abs(contrast - 1f) <= 0.001f &&
            (((hue % 360f) + 360f) % 360f) <= 0.001f &&
            outlineIntensity <= 0.001f &&
            glowIntensity <= 0.001f &&
            shadowIntensity <= 0.001f &&
            fillShadowIntensity <= 0.001f &&
            reflectionIntensity <= 0.001f &&
            contactShadowIntensity <= 0.001f &&
            groundOcclusionIntensity <= 0.001f &&
            lightWrapIntensity <= 0.001f

    fun sanitized(): ImageEffectsParams = copy(
        blur = blur.coerceIn(0f, 1f),
        edgeFeather = edgeFeather.coerceIn(0f, 1f),
        sharpen = sharpen.coerceIn(0f, 1f),
        saturation = saturation.coerceIn(0f, 2f),
        brightness = brightness.coerceIn(-1f, 1f),
        contrast = contrast.coerceIn(0f, 2f),
        hue = ((hue % 360f) + 360f) % 360f,
        outlineIntensity = outlineIntensity.coerceIn(0f, 1f),
        outlineFeather = outlineFeather.coerceIn(0f, 1f),
        glowIntensity = glowIntensity.coerceIn(0f, 1f),
        glowBlur = glowBlur.coerceIn(0f, 1f),
        glowSpread = glowSpread.coerceIn(0f, 1f),
        glowDistance = glowDistance.coerceIn(0f, 1f),
        glowAngleDeg = ((glowAngleDeg % 360f) + 360f) % 360f,
        shadowIntensity = shadowIntensity.coerceIn(0f, 1f),
        shadowBlur = shadowBlur.coerceIn(0f, 1f),
        shadowSpread = shadowSpread.coerceIn(0f, 1f),
        shadowScale = shadowScale.coerceIn(0.4f, 2f),
        shadowNoise = shadowNoise.coerceIn(0f, 1f),
        shadowDistance = shadowDistance.coerceIn(0f, 1f),
        shadowAngleDeg = ((shadowAngleDeg % 360f) + 360f) % 360f,
        shadowOpacityCurve = shadowOpacityCurve.coerceIn(0f, 1f),
        groundWallBreak = groundWallBreak.coerceIn(0f, 1f),
        shadowContactHardening = shadowContactHardening.coerceIn(0f, 1f),
        shadowSkewDegrees = shadowSkewDegrees.coerceIn(-45f, 45f),
        shadowPerspectiveAmount = shadowPerspectiveAmount.coerceIn(0f, 1f),
        shadowFadeByDistance = shadowFadeByDistance.coerceIn(0f, 1f),
        fillShadowIntensity = fillShadowIntensity.coerceIn(0f, 1f),
        fillShadowBlur = fillShadowBlur.coerceIn(0f, 1f),
        fillShadowDistance = fillShadowDistance.coerceIn(0f, 1f),
        fillShadowAngleDeg = ((fillShadowAngleDeg % 360f) + 360f) % 360f,
        fillShadowScale = fillShadowScale.coerceIn(0.4f, 2f),
        reflectionIntensity = reflectionIntensity.coerceIn(0f, 1f),
        reflectionGap = reflectionGap.coerceIn(0f, 1f),
        reflectionLength = reflectionLength.coerceIn(0.1f, 1f),
        reflectionBlur = reflectionBlur.coerceIn(0f, 1f),
        reflectionNoise = reflectionNoise.coerceIn(0f, 1f),
        reflectionSkewDegrees = reflectionSkewDegrees.coerceIn(-45f, 45f),
        reflectionTintIntensity = reflectionTintIntensity.coerceIn(0f, 1f),
        reflectionEdgeFade = reflectionEdgeFade.coerceIn(0f, 1f),
        reflectionRippleIntensity = reflectionRippleIntensity.coerceIn(0f, 1f),
        reflectionRippleScale = reflectionRippleScale.coerceIn(0f, 1f),
        reflectionOpacityCurve = reflectionOpacityCurve.coerceIn(0f, 1f),
        reflectionPerspective = reflectionPerspective.coerceIn(0.3f, 1.5f),
        reflectionProgressiveBlur = reflectionProgressiveBlur.coerceIn(0f, 1f),
        reflectionFresnel = reflectionFresnel.coerceIn(0f, 1f),
        contactShadowIntensity = contactShadowIntensity.coerceIn(0f, 1f),
        contactShadowSize = contactShadowSize.coerceIn(0.1f, 1f),
        contactShadowBlur = contactShadowBlur.coerceIn(0f, 1f),
        contactShadowFalloff = contactShadowFalloff.coerceIn(0f, 1f),
        contactShadowNoise = contactShadowNoise.coerceIn(0f, 1f),
        groundOcclusionIntensity = groundOcclusionIntensity.coerceIn(0f, 1f),
        contactShadowPoints = contactShadowPoints.map { it.sanitized() },
        lightWrapIntensity = lightWrapIntensity.coerceIn(0f, 1f),
        lightWrapWidth = lightWrapWidth.coerceIn(0f, 1f),
        lightWrapAngleDeg = ((lightWrapAngleDeg % 360f) + 360f) % 360f,
        lightWrapDirectionality = lightWrapDirectionality.coerceIn(0f, 1f)
    )
}

/**
 * Pipeline de efectos "en bitmap" (CPU) para la pestaña "Efectos": a
 * diferencia de [LookSettings] (que es un grading en tiempo real dentro
 * del shader GL, ver ShaderProgram), estos efectos se hornean sobre una
 * copia de la imagen — mismo enfoque que ya usan `ColorExtraction.recolor`
 * (pestaña "Recolor") y `Extrude3D.render` (pestaña "3D"): se calcula
 * sobre una copia chica para la vista previa en vivo y sobre la copia
 * grande para el guardado final con debounce (ver EffectsPanel en
 * EditorScreen.kt). Se eligió CPU en vez de shader acá a propósito: la
 * sombra proyectada necesita AGRANDAR el lienzo (el bitmap resultante es
 * más grande que el original, con margen alrededor para que la sombra no
 * quede recortada) — igual que ya hace Extrude3D con su cuerpo extruido —
 * algo que un uniform de shader sobre el mismo tamaño de textura no puede
 * hacer sin cambiar toda la tubería de capas.
 */
object ImageEffects {

    // Fracción de la inclinación (shear) original que retiene el tramo
    // "pared" del quiebre piso/pared ([ImageEffectsParams.groundWallBreak]),
    // compartida entre sombra y reflejo: baja a propósito — una
    // superficie vertical real casi no diverge más hacia los costados a
    // medida que sube, a diferencia de un piso que sí lo hace sin
    // límite. Ver [ImageEffectsParams.groundWallBreak].
    private const val GROUND_WALL_SKEW_RETENTION = 0.15f

    // Alcance (fracción de la altura del sujeto) de la oclusión
    // ambiental de [applyContactOcclusion] — cuánto se extiende el
    // oscurecimiento hacia arriba desde el punto de apoyo. Fijo (no
    // depende del slider de intensidad) a propósito: el slider controla
    // CUÁNTO oscurece, no HASTA DÓNDE llega, mismo criterio de
    // separación de controles que el resto del motor.
    private const val CONTACT_OCCLUSION_REACH_FRACTION = 0.1f

    /**
     * Aplica [params] sobre [source] y devuelve un bitmap nuevo (no muta
     * [source]). Orden del pipeline (igual criterio que cualquier editor
     * profesional: primero se corrige el color del sujeto en sí, después
     * se realza detalle, después se trabaja el fondo, y por último se
     * arman las capas compuestas alrededor del sujeto de atrás hacia
     * adelante): Brillo/Contraste → Saturación → Tono → Nitidez →
     * Difuminar fondo → (Contorno + Resplandor + Sombra, compuestas
     * detrás del sujeto, + Reflejo, compuesto debajo de todo el
     * conjunto).
     */
    /**
     * Deriva un color de sombra "profesional" a partir del color de la
     * LUZ (p.ej. [lightWrapColor] o un futuro color de luz global): el
     * mismo recurso de "temperatura complementaria" que usa cualquier
     * set de fotografía/cine real — una luz clave cálida (naranja/
     * amarilla) siempre produce sombras que se leen FRÍAS (azuladas),
     * y viceversa, porque el ojo humano percibe el contraste de
     * temperatura como más "natural"/cinematográfico que una sombra
     * gris neutra sin relación con la luz que la proyecta (el look
     * "naranja y teal" de cualquier grading profesional es este mismo
     * principio llevado a color completo).
     *
     * Implementación: convierte [lightColor] a HSV, ROTA el matiz 180°
     * (el complementario real en la rueda de color, no una aproximación
     * por canal RGB) y baja mucho el brillo (las sombras son oscuras
     * por definición) mientras reduce algo la saturación (una sombra
     * 100% saturada se ve "de dibujo animado", no realista). [darkness]
     * (0..1) controla cuánto se oscurece: 1 = prácticamente negro con
     * solo un tinte de color en el borde de percepción (look sutil,
     * recomendado), valores más bajos dejan una sombra más "coloreada"
     * y menos oscura (look más estilizado/artístico).
     *
     * Pensado para usarse desde la UI cuando el usuario activa un
     * "vincular color de sombra a la luz global" — análogo a como ya
     * existe el vínculo de ÁNGULO ([EditorScreen.linkShadowToGlobalLight])
     * pero para color, recalculando [ImageEffectsParams.shadowColor]/
     * [ImageEffectsParams.fillShadowColor] cada vez que cambia el color
     * de luz, en vez de guardar un booleano dentro del motor.
     */
    /**
     * BUG REAL encontrado y corregido acá: convierte un ángulo 0..360 en
     * el vector de desplazamiento (dx,dy) que usan la sombra proyectada,
     * la sombra de relleno y el light wrap.
     *
     * Antes esto se calculaba inline con `cos(ángulo)` para dx y
     * `sin(ángulo)` para dy — la convención "de trigonometría cruda"
     * donde 0° apunta a la DERECHA y el ángulo crece en sentido horario
     * (porque dy positivo es "abajo" en coordenadas de Bitmap/Canvas).
     * Con esa convención, el valor por defecto de [ImageEffectsParams.
     * shadowAngleDeg] (135°, documentado ahí mismo como "la sombra
     * clásica abajo-derecha") en realidad calculaba abajo-IZQUIERDA —
     * se puede verificar: cos(135°)≈-0.71 (izquierda), sin(135°)≈+0.71
     * (abajo). Cualquier capa nueva, sin tocar ningún slider, salía con
     * la sombra por defecto apuntando al lado contrario del que dice el
     * propio comentario del código.
     *
     * Además, el selector "Luz global" (ver `EffectsCategoryLuzGlobal`
     * en EditorScreen.kt) muestra un punto que gira con
     * `graphicsLayer { rotationZ = globalLightAngle }` — convención de
     * Compose donde 0° es ARRIBA y el giro es horario (como las agujas
     * de un reloj). Esa es una convención DISTINTA a "0°=derecha", así
     * que aunque el comentario del dial dice ser "solo una referencia
     * visual", en 135° el punto del dial y el desplazamiento real de la
     * sombra apuntaban a dos direcciones que ni siquiera comparten el
     * mismo cero — el dial no era una referencia fiel de hacia dónde
     * iba a cambiar realmente la sombra.
     *
     * Esta función usa la MISMA convención que el dial de Compose (0° =
     * arriba, sentido horario, como un reloj — la más intuitiva para un
     * slider de ángulo sin más contexto): `dx = sin(θ)`, `dy = -cos(θ)`.
     * Con esto, 135° da dx>0 (derecha) y dy>0 (abajo) — abajo-derecha,
     * exactamente lo que el comentario original prometía — sin tener
     * que tocar el valor por defecto de 135° en ningún lado.
     */
    fun angleDegToOffsetUnitVector(angleDeg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        return dx to dy
    }

    fun deriveComplementaryShadowColor(lightColor: Int, darkness: Float = 0.82f): Int {
        val r = ((lightColor ushr 16) and 0xFF) / 255f
        val g = ((lightColor ushr 8) and 0xFF) / 255f
        val b = (lightColor and 0xFF) / 255f

        val hsv = FloatArray(3)
        Color.RGBToHSV((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt(), hsv)

        // Complementario real: +180° en la rueda de matiz.
        hsv[0] = (hsv[0] + 180f) % 360f
        // Sombra menos saturada que la luz que la origina (evita el
        // look "de dibujo animado" de una sombra 100% saturada) y
        // bastante más oscura (multiplicador de value, no un valor
        // fijo, para que una luz ya oscura no termine casi negra del
        // todo y una luz muy clara sí se sienta bien oscurecida).
        hsv[1] = (hsv[1] * 0.6f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * (1f - darkness.coerceIn(0f, 1f))).coerceIn(0.02f, 1f)

        return Color.HSVToColor(hsv)
    }

    fun apply(source: Bitmap, rawParams: ImageEffectsParams): Bitmap {
        val params = rawParams.sanitized()
        if (params.isNeutral) return source.copy(Bitmap.Config.ARGB_8888, false)

        val colorAdjusted = if (abs(params.brightness) > 0.001f || abs(params.contrast - 1f) > 0.001f) {
            applyBrightnessContrast(source, params.brightness, params.contrast)
        } else {
            source
        }

        val saturated = if (abs(params.saturation - 1f) > 0.001f) {
            applySaturation(colorAdjusted, params.saturation)
        } else {
            colorAdjusted
        }
        if (saturated !== colorAdjusted && colorAdjusted !== source) colorAdjusted.recycle()

        val hued = if (params.hue > 0.001f) {
            applyHueRotation(saturated, params.hue)
        } else {
            saturated
        }
        if (hued !== saturated && saturated !== source) saturated.recycle()

        val sharpened = if (params.sharpen > 0.001f) {
            applySharpen(hued, params.sharpen)
        } else {
            hued
        }
        if (sharpened !== hued && hued !== source) hued.recycle()

        // "Difuminar" (fondo): usa el alfa del ORIGINAL como máscara de
        // protección — el sujeto (alfa alto) sale intacto de acá, solo se
        // difumina alrededor de él. Ver comentario grande en
        // [applyBackgroundBlur] para el porqué completo.
        val foreground = if (params.blur > 0.001f) {
            applyBackgroundBlur(source, sharpened, params.blur, params.edgeFeather)
        } else {
            sharpened
        }
        if (foreground !== sharpened && sharpened !== source) sharpened.recycle()

        // Light wrap: se aplica sobre el sujeto YA terminado (color,
        // nitidez y difuminado de fondo horneados), como último retoque
        // al sujeto en sí antes de armar las capas compuestas alrededor
        // — mismo criterio que el resto del pipeline: cada paso trabaja
        // sobre el resultado del anterior, de adentro (color) hacia
        // afuera (composición).
        val wrapped = if (params.lightWrapIntensity > 0.001f) {
            applyLightWrap(
                foreground,
                params.lightWrapIntensity,
                params.lightWrapColor,
                params.lightWrapWidth,
                params.lightWrapAngleDeg,
                params.lightWrapDirectionality
            )
        } else {
            foreground
        }
        if (wrapped !== foreground && foreground !== source) foreground.recycle()

        // BUG REAL corregido acá también (mismo motivo que en
        // [ImageEffectsParams.isNeutral]): [groundOcclusionIntensity]
        // faltaba en esta lista. Sin esto, aunque el fix de arriba ya
        // deja pasar `isNeutral=false`, esta segunda compuerta seguía
        // devolviendo `wrapped` sin pasar por [compositeWithEffects] —
        // que es donde realmente vive [applyContactOcclusion] — así que
        // el slider de oclusión ambiental TODAVÍA no hacía nada aunque
        // se lo activara solo. Las dos compuertas tenían que arreglarse
        // juntas.
        val needsComposite = params.shadowIntensity > 0.001f ||
            params.fillShadowIntensity > 0.001f ||
            params.glowIntensity > 0.001f ||
            params.outlineIntensity > 0.001f ||
            params.reflectionIntensity > 0.001f ||
            params.contactShadowIntensity > 0.001f ||
            params.groundOcclusionIntensity > 0.001f
        if (!needsComposite) {
            return if (wrapped === source) source.copy(Bitmap.Config.ARGB_8888, false) else wrapped
        }

        // Contorno, resplandor, sombra y reflejo se calculan SIEMPRE a
        // partir del alfa/píxeles del [source] ORIGINAL o de [wrapped]
        // ya terminado (nunca reprocesando el resultado de OTRO de estos
        // cuatro) — así quedan totalmente independientes entre sí. Ver
        // [compositeWithEffects].
        return compositeWithEffects(source, wrapped, params)
    }

    /** Radio de difuminado en píxeles, proporcional al lado mayor de la imagen (máx. razonable ~5%). */
    private fun blurRadiusPx(amount: Float, width: Int, height: Int): Int {
        val maxRadius = (max(width, height) * 0.05f).coerceIn(2f, 48f)
        return (amount.coerceIn(0f, 1f) * maxRadius).roundToInt().coerceAtLeast(1)
    }

    /**
     * Difuminado de FONDO estilo "retrato"/profundidad de campo: en vez de
     * emborronar toda la imagen (lo que antes también volvía borroso al
     * sujeto y, visualmente, la zona donde se apoya su sombra — el reporte
     * real que motivó este cambio), esto difumina [sharpFg] entero y
     * después mezcla, píxel a píxel, entre esa versión difuminada y la
     * versión nítida — usando el alfa del [original] como "mapa de
     * protección": donde el alfa es alto (adentro del sujeto) se queda con
     * el píxel nítido tal cual vino; donde el alfa es bajo (fondo,
     * transparente) se usa el píxel difuminado.
     *
     * El ALFA del resultado se conserva siempre igual al de [sharpFg] —
     * nunca se difumina el alfa acá — así el contorno/silueta del sujeto
     * (y por lo tanto la sombra, que se genera aparte a partir del alfa
     * del original, ver [compositeWithDropShadow]) no cambia de forma ni
     * un píxel por mover este slider.
     *
     * Nota honesta: sobre un recorte "duro" con fondo 100% transparente,
     * esto no se ve — no hay nada ahí que difuminar, y es lo correcto (no
     * se puede desenfocar lo que no existe). El efecto se nota en el
     * halo/borde suavizado del recorte (más marcado cuanto más alto esté
     * [feather]) y en imágenes con fondo real (no recortadas a
     * transparencia), que es el caso de uso principal de un difuminado de
     * fondo tipo "retrato".
     */
    private fun applyBackgroundBlur(original: Bitmap, sharpFg: Bitmap, amount: Float, feather: Float): Bitmap {
        val w = sharpFg.width
        val h = sharpFg.height

        val blurred = boxBlur(sharpFg, blurRadiusPx(amount, w, h))
        val protectionMask = buildFeatheredProtectionMask(original, feather)

        val sharpPixels = IntArray(w * h)
        sharpFg.getPixels(sharpPixels, 0, w, 0, 0, w, h)
        val blurPixels = IntArray(w * h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        if (blurred !== sharpFg) blurred.recycle()

        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val protect = protectionMask[i] / 255f
            val sharp = sharpPixels[i]
            val blur = blurPixels[i]
            val a = (sharp ushr 24) and 0xFF // alfa SIEMPRE del nítido — la silueta no se toca
            val sr = (sharp ushr 16) and 0xFF
            val sg = (sharp ushr 8) and 0xFF
            val sb = sharp and 0xFF
            val br = (blur ushr 16) and 0xFF
            val bg = (blur ushr 8) and 0xFF
            val bb = blur and 0xFF
            val r = (sr * protect + br * (1f - protect)).roundToInt().coerceIn(0, 255)
            val g = (sg * protect + bg * (1f - protect)).roundToInt().coerceIn(0, 255)
            val b = (sb * protect + bb * (1f - protect)).roundToInt().coerceIn(0, 255)
            outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Light wrap: mezcla [wrapColor] hacia el ANILLO INTERIOR del borde
     * del sujeto (nunca hacia afuera de su silueta), simulando la luz
     * ambiente de un fondo "envolviendo" el contorno — el detalle que
     * distingue un compuesto integrado de un recorte pegado encima.
     *
     * Técnica: se toma el alfa del sujeto, se INVIERTE (255-alfa, así
     * "afuera" queda en blanco y "adentro" en negro) y se difumina esa
     * máscara invertida con [boxBlur] — el difuminado hace que el
     * blanco de "afuera" sangre hacia adentro, más fuerte cerca del
     * borde y decayendo hacia el centro. Multiplicar ese resultado por
     * el alfa ORIGINAL (sin invertir) recorta esa sangría exactamente a
     * la silueta del sujeto: lo que queda es un anillo que vive
     * ÚNICAMENTE por dentro del contorno, más intenso pegado al borde y
     * desvaneciéndose hacia el centro — exactamente el patrón de un
     * light wrap real, sin necesitar un fondo de verdad detrás para
     * tomarlo prestado.
     *
     * [directionality] > 0 agrega una segunda capa de realismo:
     * físicamente, la envoltura de luz solo debería notarse del lado del
     * contorno que efectivamente "mira" hacia la fuente de luz/fondo — el
     * lado opuesto está en sombra propia y no recibe ese rebote. Para
     * lograrlo sin necesitar un fondo real, se estima la NORMAL del borde
     * en cada píxel con un gradiente de diferencias finitas sobre el
     * alfa ORIGINAL (mismo principio que un normal-map en compositing:
     * el gradiente de alfa apunta hacia adentro del sujeto, así que su
     * negativo apunta hacia afuera, "mirando" al fondo) y se pesa la
     * fuerza del anillo por el coseno del ángulo entre esa normal y
     * [angleDeg] — 1 cuando el borde mira directo a la luz, 0 cuando
     * mira directo para el lado contrario. En `directionality = 0` este
     * cálculo se salta por completo y el resultado es IDÉNTICO al de
     * siempre (envoltura pareja en todo el contorno).
     */
    private fun applyLightWrap(
        foreground: Bitmap,
        intensity: Float,
        wrapColor: Int,
        width: Float,
        angleDeg: Float = 90f,
        directionality: Float = 0f
    ): Bitmap {
        val w = foreground.width
        val h = foreground.height
        val radiusPx = blurRadiusPx(width, w, h)

        val alphaMask = foreground.extractAlpha()
        val alphaPixels = IntArray(w * h)
        // extractAlpha() entrega un bitmap ALPHA_8 — hay que pasarlo por
        // un ARGB intermedio (mismo patrón que ya usa el resto del
        // archivo, ver [buildFeatheredProtectionMask]) para poder leerlo
        // con getPixels() y tener el valor de alfa accesible por canal.
        val alphaArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(alphaArgb).drawBitmap(alphaMask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        alphaMask.recycle()
        alphaArgb.getPixels(alphaPixels, 0, w, 0, 0, w, h)

        val invertedArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val invertedPixels = IntArray(w * h) { i ->
            val a = (alphaPixels[i] ushr 24) and 0xFF
            val inv = 255 - a
            (inv shl 24) or (inv shl 16) or (inv shl 8) or inv
        }
        invertedArgb.setPixels(invertedPixels, 0, w, 0, 0, w, h)

        val bled = if (radiusPx >= 1) boxBlur(invertedArgb, radiusPx) else invertedArgb
        if (bled !== invertedArgb) invertedArgb.recycle()
        val bledPixels = IntArray(w * h)
        bled.getPixels(bledPixels, 0, w, 0, 0, w, h)
        bled.recycle()

        val fgPixels = IntArray(w * h)
        foreground.getPixels(fgPixels, 0, w, 0, 0, w, h)

        val wr = (wrapColor ushr 16) and 0xFF
        val wg = (wrapColor ushr 8) and 0xFF
        val wb = wrapColor and 0xFF

        val useDirection = directionality > 0.001f
        // Vector unitario de la dirección de luz — MISMA convención que
        // [angleDegToOffsetUnitVector] (0° = arriba, sentido horario,
        // como el dial de "Luz global"): antes acá se usaba
        // `cos`/`sin` crudo (0°=derecha), una convención DISTINTA a la
        // que terminó usando la sombra después del fix — quedaba
        // inconsistente pese a que el comentario original decía
        // explícitamente que debía compartir la misma convención que
        // [shadowAngleDeg]/[fillShadowAngleDeg].
        val (lightX, lightY) = angleDegToOffsetUnitVector(angleDeg)

        fun alphaAt(x: Int, y: Int): Int {
            val xc = x.coerceIn(0, w - 1)
            val yc = y.coerceIn(0, h - 1)
            return (alphaPixels[yc * w + xc] ushr 24) and 0xFF
        }

        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowStart = y * w
            for (x in 0 until w) {
                val i = rowStart + x
                val p = fgPixels[i]
                val a = (p ushr 24) and 0xFF
                if (a == 0) {
                    outPixels[i] = p
                    continue
                }
                // El anillo solo existe adentro de la silueta: se multiplica
                // la sangría difuminada por el alfa ORIGINAL (no el
                // invertido) para recortarla exactamente al sujeto.
                var ringStrength = ((bledPixels[i] ushr 24) and 0xFF) * (a / 255f) / 255f

                if (useDirection && ringStrength > 0.001f) {
                    // Gradiente de alfa por diferencias finitas: apunta
                    // hacia ADENTRO del sujeto (donde el alfa crece), así
                    // que su negativo es la normal que apunta hacia
                    // afuera, hacia el fondo/la luz.
                    val gx = alphaAt(x + 1, y) - alphaAt(x - 1, y)
                    val gy = alphaAt(x, y + 1) - alphaAt(x, y - 1)
                    val len = kotlin.math.sqrt((gx * gx + gy * gy).toFloat())
                    if (len > 0.01f) {
                        val nx = -gx / len
                        val ny = -gy / len
                        // Coseno del ángulo entre la normal externa y la
                        // dirección de luz, remapeado de -1..1 a 0..1.
                        val dot = (nx * lightX + ny * lightY).coerceIn(-1f, 1f)
                        val directionalFactor = (dot + 1f) * 0.5f
                        // Interpola entre "parejo" (factor 1) y
                        // "totalmente direccional" según [directionality]
                        // — así en valores intermedios el lado opuesto a
                        // la luz se atenúa gradualmente en vez de
                        // cortarse de golpe.
                        val factor = 1f - directionality * (1f - directionalFactor)
                        ringStrength *= factor
                    }
                }

                val mix = (ringStrength * intensity).coerceIn(0f, 1f)
                if (mix <= 0.001f) {
                    outPixels[i] = p
                    continue
                }
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val outR = (r * (1f - mix) + wr * mix).roundToInt().coerceIn(0, 255)
                val outG = (g * (1f - mix) + wg * mix).roundToInt().coerceIn(0, 255)
                val outB = (b * (1f - mix) + wb * mix).roundToInt().coerceIn(0, 255)
                outPixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Mapa de protección 0..255 por píxel (255 = sujeto, protegido del
     * difuminado; 0 = fondo, se difumina a pleno) a partir del alfa del
     * [original]. [feather] suaviza la transición entre ambos con el
     * mismo [boxBlur] de siempre — así el borde del recorte no queda con
     * un límite duro de "difuminado / no difuminado" pegado al pixel
     * exacto del contorno, sino una zona de transición gradual (más ancha
     * cuanto más alto el slider "Suavizado de contorno").
     */
    private fun buildFeatheredProtectionMask(original: Bitmap, feather: Float): IntArray {
        val w = original.width
        val h = original.height
        val alphaMask = original.extractAlpha()
        val maskArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(maskArgb).drawBitmap(alphaMask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        alphaMask.recycle()

        val featherPx = (feather.coerceIn(0f, 1f) * (max(w, h) * 0.03f).coerceIn(1f, 24f)).roundToInt()
        val featheredMask = if (featherPx >= 1) boxBlur(maskArgb, featherPx) else maskArgb
        if (featheredMask !== maskArgb) maskArgb.recycle()

        val pixels = IntArray(w * h)
        featheredMask.getPixels(pixels, 0, w, 0, 0, w, h)
        featheredMask.recycle()

        return IntArray(w * h) { (pixels[it] ushr 24) and 0xFF }
    }

    /**
     * Nitidez (realce de detalle): "unsharp mask" clásico — se resta una
     * versión levemente difuminada de [source] al [source] mismo,
     * amplificando esa diferencia (los bordes/detalle fino) y sumándola
     * de vuelta. Radio de difuminado interno chico y fijo (no
     * configurable desde afuera) porque lo que hace "nítida" a una
     * imagen es realzar detalle FINO — un radio grande se acercaría más a
     * un efecto de contraste local que a nitidez real.
     */
    private fun applySharpen(source: Bitmap, amount: Float): Bitmap {
        val w = source.width
        val h = source.height
        val innerRadius = (max(w, h) * 0.01f).roundToInt().coerceIn(1, 6)
        val blurred = boxBlur(source, innerRadius)

        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val blurPixels = IntArray(w * h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        if (blurred !== source) blurred.recycle()

        val strength = amount.coerceIn(0f, 1f) * 1.5f
        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val s = srcPixels[i]
            val b = blurPixels[i]
            val a = (s ushr 24) and 0xFF
            val r = sharpenChannel(s, b, 16, strength)
            val g = sharpenChannel(s, b, 8, strength)
            val bl = sharpenChannel(s, b, 0, strength)
            outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or bl
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun sharpenChannel(src: Int, blurred: Int, shift: Int, strength: Float): Int {
        val sv = (src ushr shift) and 0xFF
        val bv = (blurred ushr shift) and 0xFF
        return (sv + (sv - bv) * strength).roundToInt().coerceIn(0, 255)
    }

    /**
     * Brillo (ajuste aditivo simple por canal RGB, clamp 0..255) +
     * Contraste (fórmula estándar `(color-128) * factor + 128`) en un
     * solo pase por píxel — ambos son ajustes lineales por canal así que
     * combinarlos en una sola pasada evita un bitmap intermedio extra sin
     * cambiar el resultado visual de aplicarlos por separado. El brillo
     * se aplica ANTES que el contraste (mismo orden que Photoshop/
     * Lightroom: contraste sobre un punto medio ya desplazado por el
     * brillo, no al revés). El alfa nunca se toca acá.
     */
    private fun applyBrightnessContrast(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val brightAdd = (brightness.coerceIn(-1f, 1f) * 255f).roundToInt()
        val contrastFactor = contrast.coerceIn(0f, 2f)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = adjustBrightnessContrastChannel((p ushr 16) and 0xFF, brightAdd, contrastFactor)
            val g = adjustBrightnessContrastChannel((p ushr 8) and 0xFF, brightAdd, contrastFactor)
            val b = adjustBrightnessContrastChannel(p and 0xFF, brightAdd, contrastFactor)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun adjustBrightnessContrastChannel(value: Int, brightAdd: Int, contrastFactor: Float): Int {
        val brightened = (value + brightAdd).coerceIn(0, 255)
        val contrasted = ((brightened - 128) * contrastFactor + 128f).roundToInt()
        return contrasted.coerceIn(0, 255)
    }

    /**
     * Tono (hue): rotación de matiz sobre TODO el sujeto, [hueDeg] grados
     * (0..360). Implementación vía `Color.RGBToHSV`/`Color.HSVToColor`
     * (convertir cada píxel a HSV, sumar el matiz, reconvertir) en vez de
     * una ColorMatrix con senos/cosenos: más lento píxel a píxel, pero
     * mucho más simple de mantener y sin las distorsiones de saturación
     * que puede introducir una matriz de rotación de hue aproximada sobre
     * el espacio RGB. Los píxeles totalmente transparentes (alfa 0, muy
     * comunes en recortes) se copian tal cual: no tienen color "real" que
     * rotar y `RGBToHSV` sobre un RGB de relleno arbitrario podría teñir
     * de forma visible el borde del recorte si alguna vez se compone
     * sobre algo que no sea 100% opaco.
     */
    private fun applyHueRotation(source: Bitmap, hueDeg: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val shift = ((hueDeg % 360f) + 360f) % 360f
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) continue
            Color.RGBToHSV((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF, hsv)
            hsv[0] = (hsv[0] + shift) % 360f
            pixels[i] = Color.HSVToColor(a, hsv)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applySaturation(source: Bitmap, saturation: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /**
     * Silueta del [original] tiñida 100% con [color] (SRC_IN reemplaza el
     * color por el elegido pero conserva la forma/alfa de la máscara) —
     * pieza compartida entre la sombra y el resplandor, que son
     * conceptualmente el mismo elemento (silueta teñida + difuminada),
     * solo con distinto offset/color/intensidad. Devuelve un bitmap
     * nuevo del mismo tamaño que [original]; el llamador es dueño de
     * reciclarlo.
     */
    private fun tintedSilhouette(original: Bitmap, color: Int): Bitmap {
        val w = original.width
        val h = original.height
        val alphaMask = original.extractAlpha()
        val silhouette = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(silhouette).drawBitmap(
            alphaMask,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        )
        alphaMask.recycle()
        return silhouette
    }

    /**
     * Recolorea [glowLayer] (un halo ya difuminado — mismo bitmap que
     * arma el bloque de "Resplandor" de [compositeWithEffects], montado
     * sobre [tintedSilhouette] + [dilateSilhouette]/[boxBlur]) en un
     * degradado de dos colores en vez de un color sólido, A PEDIDO DEL
     * USUARIO — mismo concepto que el degradado de "Contorno" (ver
     * [buildDilatedOutline]) pero aplicado a un halo difuminado en vez de
     * a un anillo de borde duro.
     *
     * BUG REAL encontrado y corregido acá (reporte visual del usuario: el
     * halo salía todo dorado/naranja parejo, sin blanco visible cerca del
     * sujeto pese a tener [color1] blanco y [color2] rojo): la primera
     * versión de esta función asumía que el núcleo sólido (alfa 255,
     * pegado a la silueta original, incluso con [ImageEffectsParams.glowSpread]
     * > 0) era "visible" y por eso normalizaba contra 255 fijo — pero ESE
     * núcleo queda SIEMPRE tapado por el sujeto opaco, que se pinta
     * encima del resplandor (ver el orden de dibujado en
     * [compositeWithEffects]). Lo único que en verdad se VE del halo es
     * la parte que sobresale del borde real del sujeto, que — igual que
     * el anillo de [buildDilatedOutline] — nunca vuelve a acercarse a 255
     * de alfa. Normalizar contra 255 fijo comprimía TODO el degradado
     * visible dentro de la mitad "roja" del rango, exactamente el mismo
     * bug que ya se había encontrado y corregido ahí. La corrección es
     * la MISMA: se pasa [original] (el sujeto sin el halo) para poder
     * excluir los píxeles tapados, se busca el alfa máximo REAL entre los
     * píxeles VISIBLES del halo (afuera del sujeto) y se normaliza contra
     * ESE máximo — así el píxel visible más cercano al sujeto (el más
     * alto de ese grupo) siempre queda en t=0 (color1 puro).
     *
     * [glowLayer] debe ser el resultado de teñir con un color NEUTRO
     * (blanco sólido, ver el llamador) antes de esta función — así el
     * único dato que se usa de cada píxel es su ALFA (qué tan "adentro"
     * del halo está), nunca su RGB ya teñido, evitando que el resultado
     * salga con una mezcla accidental del color viejo con el nuevo
     * degradado.
     */
    private fun buildGlowGradientTint(glowLayer: Bitmap, original: Bitmap, color1: Int, color2: Int): Bitmap {
        val w = glowLayer.width
        val h = glowLayer.height
        val pixels = IntArray(w * h)
        glowLayer.getPixels(pixels, 0, w, 0, 0, w, h)

        // Alfa del sujeto ORIGINAL — mismo criterio EXACTO que
        // [buildDilatedOutline]: un píxel del halo tapado por el sujeto
        // real no es visible, así que no debe contar para encontrar el
        // máximo alfa "real" del halo visible.
        val originalAlphaPixels = IntArray(w * h)
        original.getPixels(originalAlphaPixels, 0, w, 0, 0, w, h)

        val threshold = 6
        var visibleMaxAlpha = 1
        for (i in pixels.indices) {
            val originalA = (originalAlphaPixels[i] ushr 24) and 0xFF
            if (originalA > threshold) continue // tapado por el sujeto real, no cuenta
            val a = (pixels[i] ushr 24) and 0xFF
            if (a > visibleMaxAlpha) visibleMaxAlpha = a
        }

        val cr1 = (color1 ushr 16) and 0xFF
        val cg1 = (color1 ushr 8) and 0xFF
        val cb1 = color1 and 0xFF
        val cr2 = (color2 ushr 16) and 0xFF
        val cg2 = (color2 ushr 8) and 0xFF
        val cb2 = color2 and 0xFF

        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            if (a == 0) continue
            // t=0 en el punto VISIBLE más cercano al sujeto (el de mayor
            // alfa fuera de la silueta real), t=1 en la punta más
            // difusa/transparente del halo (alfa cercano a 0) — mismo
            // sentido exacto que el degradado del contorno.
            val t = 1f - (a.toFloat() / visibleMaxAlpha).coerceIn(0f, 1f)
            val r = (cr1 + (cr2 - cr1) * t).roundToInt().coerceIn(0, 255)
            val g = (cg1 + (cg2 - cg1) * t).roundToInt().coerceIn(0, 255)
            val b = (cb1 + (cb2 - cb1) * t).roundToInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Traduce [GlowBlendMode] al `PorterDuff.Mode` nativo de Android que
     * implementa la misma fórmula — `null` para [GlowBlendMode.NORMAL]
     * (sin `Xfermode`, comportamiento EXACTO al de siempre: composición
     * simple sobre lo que haya debajo).
     *
     * POR QUÉ SÍ USAR EL `PorterDuffXfermode` NATIVO ACÁ (a diferencia de
     * [blitBlend], que reimplementa MULTIPLY a mano): el bug documentado
     * en el KDoc de [blitBlend] es específico de MULTIPLY, donde Android
     * define el alfa de salida como `Sa*Da` — sobre un destino
     * transparente (`Da=0`) el resultado da SIEMPRE invisible. SCREEN,
     * ADD (`DARKEN`/`LIGHTEN` sufren lo mismo por construcción, pero acá
     * no se usan) están definidos en el motor de composición de Android
     * (Skia) con la fórmula "over" estándar aplicada al resultado del
     * blend — exactamente igual que la fórmula "moderna" de
     * `BlendMode.SCREEN`/`ADD`/`LIGHTEN` (API 29+), incluso para
     * `Canvas`/`Bitmap` de software como el de este archivo — por lo que
     * NO hace falta reimplementarlos a mano ni depender de esa API 29+:
     * dan el resultado correcto ya desde API 26 (el `minSdk` real de este
     * proyecto) usando `Paint.xfermode` con el `PorterDuff.Mode` clásico.
     */
    private fun glowPorterDuffMode(mode: GlowBlendMode): PorterDuff.Mode? = when (mode) {
        GlowBlendMode.NORMAL -> null
        GlowBlendMode.SCREEN -> PorterDuff.Mode.SCREEN
        GlowBlendMode.ADD -> PorterDuff.Mode.ADD
        GlowBlendMode.LIGHTEN -> PorterDuff.Mode.LIGHTEN
    }

    /**
     * Desvanece [silhouette] verticalmente: opacidad completa en la fila
     * inferior (y = h-1, el punto de apoyo/pie del sujeto — "distancia
     * cero") y una opacidad reducida en la fila superior (y = 0, el
     * punto más lejano del apoyo dentro de la silueta), con [amount]
     * controlando cuánto se reduce ese extremo lejano (0 = sin cambio,
     * 1 = se desvanece casi del todo). Se usa para que la sombra
     * proyectada "se pierda" gradualmente en vez de tener opacidad
     * pareja de punta a punta — el mismo recurso que un dropshadow de
     * piso profesional, sin necesitar rehacer la sombra como un
     * degradado en la dirección exacta del offset (que además cambiaría
     * de eje cada vez que se mueve el ángulo).
     */
    private fun applyVerticalFade(silhouette: Bitmap, amount: Float, curve: Float = 0.5f): Bitmap {
        val w = silhouette.width
        val h = silhouette.height
        val pixels = IntArray(w * h)
        silhouette.getPixels(pixels, 0, w, 0, 0, w, h)

        val lastRow = (h - 1).coerceAtLeast(1)
        val fadeAmount = amount.coerceIn(0f, 1f)
        // Mismo mapeo gamma que [buildReflection]/[buildContactShadow]:
        // curve=0.5 -> gamma=1 (lineal, EXACTAMENTE el comportamiento de
        // siempre); <0.5 gamma<1 (cae rápido, se extiende tenue);
        // >0.5 gamma>1 (se sostiene fuerte, corta de golpe).
        val gamma = Math.pow(2.0, ((curve.coerceIn(0f, 1f) - 0.5f) * 4f).toDouble())
        for (y in 0 until h) {
            val t = y.toFloat() / lastRow // 0 = fila superior, 1 = fila inferior (pie)
            val shapedT = Math.pow((1.0 - t).toDouble(), gamma).toFloat() // 0 en el pie -> 1 en el extremo lejano, con curva gamma
            val fade = 1f - fadeAmount * shapedT
            val rowStart = y * w
            for (x in 0 until w) {
                val idx = rowStart + x
                val p = pixels[idx]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                val newAlpha = (a * fade).roundToInt().coerceIn(0, 255)
                pixels[idx] = (newAlpha shl 24) or (p and 0x00FFFFFF)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Erosiona [alphaPixels] (una máscara ARGB donde el canal alfa
     * representa la silueta, 0..255) hacia ADENTRO por [radiusPx] —
     * la contraparte exacta de la dilatación que ya usan
     * [buildDilatedOutline]/[dilateSilhouette]. Implementada como
     * "dilatar el complemento y complementar de nuevo"
     * (`erode(A) = NOT(dilate(NOT A))`), el mismo truco estándar de
     * morfología binaria — así reutiliza [boxBlur] en vez de necesitar
     * una erosión píxel a píxel real.
     *
     * Devuelve un `Pair` de dos `IntArray`: el primero es el alfa
     * erosionado YA umbralado (0 o 255) por índice — lo que
     * [buildInnerOutline] cruza contra el alfa ORIGINAL para quedarse
     * solo con la banda que la erosión "comió". El segundo es ese mismo
     * alfa de fondo dilatado pero SIN umbralar (0..255 suave) — antes se
     * descartaba; ahora [buildInnerOutline] lo reusa como factor de
     * degradado cuando [ImageEffectsParams.outlineGradientEnabled] está
     * activo, en vez de recalcular el mismo `boxBlur` dos veces.
     */
    private fun erodeAlphaSmooth(alphaPixels: IntArray, w: Int, h: Int, radiusPx: Int): Pair<IntArray, IntArray> {
        if (radiusPx < 1) {
            val hard = IntArray(alphaPixels.size) { (alphaPixels[it] ushr 24) and 0xFF }
            return hard to hard
        }

        val invPixels = IntArray(w * h)
        for (i in invPixels.indices) {
            val a = (alphaPixels[i] ushr 24) and 0xFF
            invPixels[i] = (255 - a) shl 24
        }
        val invBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        invBitmap.setPixels(invPixels, 0, w, 0, 0, w, h)

        val dilatedInv = boxBlur(invBitmap, radiusPx)
        if (dilatedInv !== invBitmap) invBitmap.recycle()
        val dilatedInvPixels = IntArray(w * h)
        dilatedInv.getPixels(dilatedInvPixels, 0, w, 0, 0, w, h)
        dilatedInv.recycle()

        val threshold = 6
        val smoothInvAlpha = IntArray(w * h)
        val out = IntArray(w * h)
        for (i in out.indices) {
            val invAlpha = (dilatedInvPixels[i] ushr 24) and 0xFF
            smoothInvAlpha[i] = invAlpha
            // El fondo dilatado NO llegó hasta acá -> seguía "bien
            // adentro" del sujeto -> sobrevive a la erosión.
            out[i] = if (invAlpha <= threshold) 255 else 0
        }
        return out to smoothInvAlpha
    }

    /**
     * Versión "hacia adentro" de [buildDilatedOutline]: en vez de
     * agrandar la silueta hacia afuera, la erosiona por
     * [thicknessNormalized] (ver [erodeAlpha]) y se queda con la banda
     * que la erosión se "comió" — exactamente el mismo contorno de
     * grosor uniforme, pero completamente ADENTRO del sujeto en vez de
     * sobresalir. Usada por [OutlineStrokePosition.INSIDE] y (a mitad
     * de grosor) por [OutlineStrokePosition.CENTER] — ver
     * [compositeWithEffects].
     */
    private fun buildInnerOutline(
        original: Bitmap,
        color: Int,
        thicknessNormalized: Float,
        color2: Int = color,
        gradientEnabled: Boolean = false,
        thicknessPxOverride: Int = -1
    ): Bitmap {
        val w = original.width
        val h = original.height
        // [thicknessPxOverride] permite a quien llama pasar un radio en
        // píxeles YA compensado (ver [compositeWithEffects] y el KDoc de
        // [buildDilatedOutline] sobre grosor independiente del feather)
        // en vez del que saldría de [thicknessNormalized] a secas — con
        // -1 (default) el comportamiento es EXACTO al de siempre.
        val ringPx = if (thicknessPxOverride >= 0) thicknessPxOverride else blurRadiusPx(thicknessNormalized, w, h)

        val alphaMask = original.extractAlpha()
        val maskArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(maskArgb).drawBitmap(alphaMask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        alphaMask.recycle()
        val originalPixels = IntArray(w * h)
        maskArgb.getPixels(originalPixels, 0, w, 0, 0, w, h)
        maskArgb.recycle()

        val (erodedAlpha, smoothInvAlpha) = erodeAlphaSmooth(originalPixels, w, h, ringPx)

        val threshold = 6
        val cr1 = (color ushr 16) and 0xFF
        val cg1 = (color ushr 8) and 0xFF
        val cb1 = color and 0xFF
        val cr2 = (color2 ushr 16) and 0xFF
        val cg2 = (color2 ushr 8) and 0xFF
        val cb2 = color2 and 0xFF
        val solidColor = (0xFF shl 24) or (cr1 shl 16) or (cg1 shl 8) or cb1

        // Mismo bug y misma corrección que en [buildDilatedOutline]: la
        // banda del trazo interior es, por definición, la mitad "de
        // adentro" del difuminado de erosión — ahí [smoothInvAlpha] nunca
        // vuelve a acercarse a 255 (como mucho ronda la mitad pegado al
        // borde real, bajando hacia el umbral de erosión en la punta
        // interior). Normalizar contra 255 fijo comprimía el degradado
        // entero del lado de color2. Se busca el máximo real DENTRO de la
        // banda y se normaliza contra ESE valor.
        var bandMaxInvAlpha = 1
        if (gradientEnabled) {
            for (i in originalPixels.indices) {
                val originalAlpha = (originalPixels[i] ushr 24) and 0xFF
                if (originalAlpha > threshold && erodedAlpha[i] == 0) {
                    if (smoothInvAlpha[i] > bandMaxInvAlpha) bandMaxInvAlpha = smoothInvAlpha[i]
                }
            }
        }

        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val originalAlpha = (originalPixels[i] ushr 24) and 0xFF
            // Banda = sujeto original SÍ, sujeto erosionado NO — el
            // anillo de [ringPx] de ancho pegado al borde interior.
            if (originalAlpha > threshold && erodedAlpha[i] == 0) {
                if (gradientEnabled) {
                    // t=0 pegado al borde (color 1, el píxel de banda con
                    // más [smoothInvAlpha]), t=1 en la punta interior
                    // (color 2) — ver el comentario grande de arriba sobre
                    // por qué normalizar contra [bandMaxInvAlpha] y no
                    // contra 255 fijo.
                    val t = (1f - smoothInvAlpha[i].toFloat() / bandMaxInvAlpha).coerceIn(0f, 1f)
                    val r = (cr1 + (cr2 - cr1) * t).roundToInt().coerceIn(0, 255)
                    val g = (cg1 + (cg2 - cg1) * t).roundToInt().coerceIn(0, 255)
                    val b = (cb1 + (cb2 - cb1) * t).roundToInt().coerceIn(0, 255)
                    outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    outPixels[i] = solidColor
                }
            } else {
                outPixels[i] = 0
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Difuminado posterior sobre un anillo de contorno ya construido
     * (afuera o adentro) — ver [ImageEffectsParams.outlineFeather].
     * Reusa [boxBlur] tal cual, mismo criterio que cualquier otro
     * "blur secundario" de este archivo (p. ej. [glowBlur] sobre la
     * silueta teñida).
     */
    private fun featherOutlineRing(ring: Bitmap, featherNormalized: Float): Bitmap {
        if (featherNormalized <= 0.001f) return ring
        val featherPx = blurRadiusPx(featherNormalized, ring.width, ring.height)
        if (featherPx < 1) return ring
        val blurred = boxBlur(ring, featherPx)
        if (blurred !== ring) ring.recycle()
        return blurred
    }

    /**
     * Contorno sólido de [thicknessNormalized] (0..1, misma escala que
     * [blurRadiusPx]) alrededor de la silueta del [original], teñido de
     * [color]. Implementación por dilatación aproximada: se difumina la
     * máscara de alfa con un radio igual al grosor pedido (reusando
     * [boxBlur], como pide mantener el mismo algoritmo de difuminado en
     * toda la app) y después se aplica un umbral bajo — cualquier píxel
     * que, tras ese difuminado, conservó algo de alfa (es decir, estaba a
     * `thicknessNormalized`-píxeles o menos del sujeto original) pasa a
     * alfa 255 sólido. El resultado es una silueta "agrandada" con borde
     * duro: al componerla DETRÁS del sujeto (ver [compositeWithEffects]),
     * lo único que queda visible de ella es el anillo que sobresale del
     * contorno real — un contorno de grosor uniforme, sin necesidad de
     * una dilatación morfológica exacta.
     */
    private fun buildDilatedOutline(
        original: Bitmap,
        color: Int,
        thicknessNormalized: Float,
        color2: Int = color,
        gradientEnabled: Boolean = false,
        thicknessPxOverride: Int = -1
    ): Bitmap {
        val w = original.width
        val h = original.height
        // Ver el mismo parámetro en [buildInnerOutline]: -1 (default)
        // reproduce EXACTO el comportamiento de siempre; un valor >= 0
        // deja que [compositeWithEffects] pase un radio ya compensado
        // por el feather posterior (ver [ImageEffectsParams.outlineFeather]
        // y el comentario grande sobre "grosor independiente del feather"
        // ahí mismo).
        val outlinePx = if (thicknessPxOverride >= 0) thicknessPxOverride else blurRadiusPx(thicknessNormalized, w, h)

        val alphaMask = original.extractAlpha()
        val maskArgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(maskArgb).drawBitmap(alphaMask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        alphaMask.recycle()
        // Alfa del sujeto ORIGINAL (sin dilatar) — necesario para saber
        // qué píxeles del anillo dilatado son realmente VISIBLES (afuera
        // del sujeto real, no tapados por él al componer) antes de
        // reciclar [maskArgb]. Ver el uso en el degradado más abajo.
        val originalAlphaPixels = IntArray(w * h)
        maskArgb.getPixels(originalAlphaPixels, 0, w, 0, 0, w, h)

        val dilatedGray = boxBlur(maskArgb, outlinePx)
        if (dilatedGray !== maskArgb) maskArgb.recycle()

        val pixels = IntArray(w * h)
        dilatedGray.getPixels(pixels, 0, w, 0, 0, w, h)
        // Recicla SIEMPRE (no condicional): si boxBlur devolvió el mismo
        // objeto que maskArgb (radio < 1), la línea de arriba lo dejó sin
        // reciclar a propósito para evitar un doble-free — hay que
        // reciclarlo acá, una sola vez, para no filtrarlo. Si son objetos
        // distintos, maskArgb ya se recicló arriba y esto libera el otro.
        // Mismo patrón exacto que [buildFeatheredProtectionMask].
        dilatedGray.recycle()

        // Umbral bajo a propósito: casi cualquier resto de alfa después
        // del difuminado significa "adentro del radio de dilatación" —
        // un umbral alto encogería visiblemente el anillo resultante.
        val threshold = 6
        val cr1 = (color ushr 16) and 0xFF
        val cg1 = (color ushr 8) and 0xFF
        val cb1 = color and 0xFF
        val cr2 = (color2 ushr 16) and 0xFF
        val cg2 = (color2 ushr 8) and 0xFF
        val cb2 = color2 and 0xFF
        val solidColor = (0xFF shl 24) or (cr1 shl 16) or (cg1 shl 8) or cb1

        // BUG REAL encontrado y corregido acá (reporte del usuario: el
        // degradado salía prácticamente todo rojo/color2, casi sin nada
        // de color1 visible): la parte VISIBLE del anillo (la que sobresale
        // del sujeto — la única que se ve, ver [compositeWithEffects]) es
        // por definición la mitad "de afuera" del difuminado del borde, y
        // ahí un `boxBlur` de un borde recto nunca vuelve a acercarse a
        // 255 — como mucho ronda la mitad (~127, justo pegado al borde
        // real) y cae a 0 en la punta. Normalizar contra 255 fijo (como
        // hacía antes) comprimía TODO el degradado dentro de la mitad
        // "roja" del rango 0..1, sin llegar nunca cerca de color1. La
        // corrección: se busca el máximo alfa REAL entre los píxeles
        // VISIBLES del anillo (afuera del sujeto original) y se normaliza
        // contra ESE máximo — así el píxel más cercano al sujeto (el más
        // alto de ese grupo) siempre queda en t=0 (color1 puro),
        // sea cual sea el valor absoluto que le tocó según la geometría
        // real del borde (recto, convexo o cóncavo).
        var visibleRingMaxAlpha = 1
        if (gradientEnabled) {
            for (i in pixels.indices) {
                val originalA = (originalAlphaPixels[i] ushr 24) and 0xFF
                if (originalA > threshold) continue // tapado por el sujeto real, no cuenta
                val a = (pixels[i] ushr 24) and 0xFF
                if (a > visibleRingMaxAlpha) visibleRingMaxAlpha = a
            }
        }

        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            if (a > threshold) {
                if (gradientEnabled) {
                    // t=0 pegado al borde real (color 1, el píxel visible
                    // con más alfa), t=1 en la punta exterior (color 2,
                    // alfa cercano a 0) — ver el comentario grande de
                    // arriba sobre por qué normalizar contra
                    // [visibleRingMaxAlpha] y no contra 255 fijo.
                    val t = 1f - (a.toFloat() / visibleRingMaxAlpha).coerceIn(0f, 1f)
                    val r = (cr1 + (cr2 - cr1) * t).roundToInt().coerceIn(0, 255)
                    val g = (cg1 + (cg2 - cg1) * t).roundToInt().coerceIn(0, 255)
                    val b = (cb1 + (cb2 - cb1) * t).roundToInt().coerceIn(0, 255)
                    outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    outPixels[i] = solidColor
                }
            } else {
                outPixels[i] = 0
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Dilatación aproximada de [silhouette] por [radiusPx]: mismo truco
     * que ya usa [buildDilatedOutline] (difuminar la máscara con
     * [boxBlur] y después aplicar un umbral bajo para volver cualquier
     * resto de alfa a 255 sólido) pero conservando el color de
     * [silhouette] en vez de forzar uno nuevo — así sirve como paso
     * previo genérico de "agrandar el núcleo, con borde duro" para
     * cualquier silueta ya teñida, reutilizado por [shadowSpread]/
     * [glowSpread].
     *
     * BUG REAL encontrado y corregido acá: esta función usaba el RGB
     * de la copia YA DIFUMINADA (`p and 0x00FFFFFF` tras el [boxBlur])
     * en vez del RGB ORIGINAL de [silhouette]. [boxBlur] difumina los 4
     * canales por separado sobre valores SIN premultiplicar (ver su
     * propio comentario) — así que, en el anillo nuevo que crea la
     * dilatación, ese RGB difuminado es un promedio entre el color
     * sólido de la silueta y el RGB "negro" (0,0,0) de los píxeles
     * totalmente transparentes vecinos. El resultado visible: un halo
     * oscurecido/ennegrecido justo en el borde exterior de la Expansión
     * de Sombra o Resplandor, en vez del color puro elegido por el
     * usuario — literalmente lo contrario de lo que promete el
     * comentario de arriba ("conservando el color de [silhouette]").
     * [buildDilatedOutline] no tenía este problema porque ahí el RGB
     * difuminado se descarta por completo y se fuerza un color sólido
     * — mismo criterio que se aplica acá ahora: se toma el color real
     * (siempre uniforme, [silhouette] viene de [tintedSilhouette]) de
     * cualquier píxel totalmente opaco del ORIGINAL, antes del blur, y
     * se usa ese color puro para todo el resultado — solo la MÁSCARA
     * (el umbral de alfa) sale del difuminado, nunca el color.
     */
    private fun dilateSilhouette(silhouette: Bitmap, radiusPx: Int): Bitmap {
        if (radiusPx < 1) return silhouette
        val w = silhouette.width
        val h = silhouette.height

        val srcPixels = IntArray(w * h)
        silhouette.getPixels(srcPixels, 0, w, 0, 0, w, h)
        var solidColor = 0
        for (p in srcPixels) {
            if ((p ushr 24) and 0xFF >= 250) {
                solidColor = (0xFF shl 24) or (p and 0x00FFFFFF)
                break
            }
        }

        val blurred = boxBlur(silhouette, radiusPx)
        val pixels = IntArray(w * h)
        blurred.getPixels(pixels, 0, w, 0, 0, w, h)
        if (blurred !== silhouette) blurred.recycle()

        val threshold = 6
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            pixels[i] = if (a > threshold) solidColor else 0
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Oscurece [source] cerca de su propio punto de apoyo, POR COLUMNA:
     * para cada columna x, encuentra el píxel opaco más bajo (su
     * "contacto" real con el piso — no asume que todo el borde inferior
     * del rectángulo está tocando el suelo, así funciona igual de bien
     * con un contorno irregular) y aplica un oscurecimiento que es
     * máximo justo en ese contacto y se desvanece a 0 a
     * [CONTACT_OCCLUSION_REACH_FRACTION] de la altura hacia arriba —
     * el mismo recurso que la oclusión ambiental (AO) de cualquier
     * motor de render 3D o compositor profesional: luz de entorno
     * bloqueada por la propia superficie donde el objeto se apoya,
     * SOBRE el objeto mismo, no sobre su sombra.
     */
    private fun applyContactOcclusion(source: Bitmap, intensity: Float): Bitmap {
        val w = source.width
        val h = source.height
        if (intensity <= 0.001f || h < 3 || w < 1) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val reachPx = (h * CONTACT_OCCLUSION_REACH_FRACTION).roundToInt().coerceAtLeast(1)
        val outPixels = pixels.copyOf()
        for (x in 0 until w) {
            var contactY = -1
            var y = h - 1
            while (y >= 0) {
                val a = (pixels[y * w + x] ushr 24) and 0xFF
                if (a > 8) {
                    contactY = y
                    break
                }
                y--
            }
            if (contactY < 0) continue
            val topY = (contactY - reachPx).coerceAtLeast(0)
            for (yy in topY..contactY) {
                val idx = yy * w + x
                val p = pixels[idx]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                val distFromContact = contactY - yy
                val t = 1f - (distFromContact.toFloat() / reachPx) // 1 en el contacto exacto, 0 en el borde superior del alcance
                // Hasta 65% de oscurecimiento con la intensidad al
                // máximo y justo en el punto de contacto — sutil por
                // default, nunca "quema" el sujeto a negro puro.
                val darken = (t * intensity * 0.65f).coerceIn(0f, 1f)
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val nr = (r * (1f - darken)).roundToInt().coerceIn(0, 255)
                val ng = (g * (1f - darken)).roundToInt().coerceIn(0, 255)
                val nb = (b * (1f - darken)).roundToInt().coerceIn(0, 255)
                outPixels[idx] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Grano/ruido determinístico sobre el ALFA de [silhouette]: sin esto,
     * una sombra queda perfectamente lisa/uniforme, algo que casi nunca
     * pasa sobre una superficie real (concreto, tela, tierra, siempre
     * tiene micro-variación). Se aplica ANTES del difuminado final (ver
     * llamada en el pipeline de la sombra) para que ese blur lo suavice
     * a una textura sutil de grano fino, no a ruido "crudo" píxel a
     * píxel. El ruido se calcula con un hash 2D determinístico de (x, y)
     * — NO con `Random` de estado global — a propósito: así el patrón de
     * grano es siempre idéntico para el mismo píxel en cada render, sin
     * "hormiguear"/parpadear entre fotogramas mientras el usuario mueve
     * otro slider cualquiera.
     */
    private fun applyShadowGrain(silhouette: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.001f) return silhouette
        val w = silhouette.width
        val h = silhouette.height
        val pixels = IntArray(w * h)
        silhouette.getPixels(pixels, 0, w, 0, 0, w, h)

        val strength = amount.coerceIn(0f, 1f) * 90f // variación máxima de alfa en el pico del ruido
        for (y in 0 until h) {
            val rowStart = y * w
            for (x in 0 until w) {
                val idx = rowStart + x
                val p = pixels[idx]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                // Hash entero determinístico de (x, y) — variante simple
                // de un hash de mezcla de bits (estilo MurmurHash),
                // suficiente para ruido visual sin necesitar una tabla de
                // permutación tipo Perlin.
                var hash = x * 374761393 + y * 668265263
                hash = (hash xor (hash ushr 13)) * 1274126177
                hash = hash xor (hash ushr 16)
                val n = (hash and 0xFFFF) / 65535f // 0..1
                val delta = ((n - 0.5f) * 2f * strength).roundToInt()
                val newAlpha = (a + delta).coerceIn(0, 255)
                pixels[idx] = (newAlpha shl 24) or (p and 0x00FFFFFF)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Textura fija de "superficie" para el reflejo — a diferencia de
     * [applyShadowGrain] (ruido de UNA sola escala sobre el alfa de una
     * silueta plana), acá se combinan DOS octavas de ruido para que el
     * resultado se lea como una superficie pulida real en vez de
     * "estática de TV":
     *
     * - Una octava FINA y de baja amplitud → polvo/micro-imperfección
     *   pareja, presente en casi todo el reflejo.
     * - Una octava GRUESA (celdas más grandes) que solo deja pasar sus
     *   picos más extremos (vía un umbral alto) → manchas/rayones
     *   puntuales dispersos, ocasionales, no un ruido uniforme — el
     *   mismo criterio visual que separa "vidrio con polvo" de "vidrio
     *   esmerilado".
     *
     * Se aplica sobre el ALFA del reflejo YA resuelto (después de
     * ondulación/difuminado, ver el pipeline en [compositeWithEffects])
     * a propósito: es una textura de LA SUPERFICIE reflectante misma
     * (vidrio, mármol pulido), no de la imagen que se refleja en ella —
     * por eso no debe moverse ni deformarse junto con la ondulación tipo
     * agua ([applyRipple]), que sí distorsiona la imagen reflejada. Ruido
     * determinístico (mismo hash 2D que [applyShadowGrain]), nunca
     * `Random` de estado global, para que el patrón sea idéntico en cada
     * render del mismo píxel.
     */
    private fun applyReflectionGrain(reflection: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.001f) return reflection
        val w = reflection.width
        val h = reflection.height
        val pixels = IntArray(w * h)
        reflection.getPixels(pixels, 0, w, 0, 0, w, h)

        val a01 = amount.coerceIn(0f, 1f)
        // Octava fina: variación pareja de baja amplitud (polvo/grano
        // sutil), máximo ~10% de opacidad de pico.
        val fineStrength = a01 * 26f
        // Octava gruesa: celdas ~14px de lado (independiente de la
        // resolución de la capa chica/grande, ya que ambas se procesan
        // por separado con el mismo criterio que el resto del motor),
        // con umbral alto para que solo sus picos extremos generen una
        // mancha/rayón puntual visible — el resto de la celda queda sin
        // efecto.
        val cellSize = 14
        val coarseStrength = a01 * 70f
        val coarseThreshold = 0.82f // solo el ~18% superior del ruido gana visibilidad

        for (y in 0 until h) {
            val rowStart = y * w
            val cy = y / cellSize
            for (x in 0 until w) {
                val idx = rowStart + x
                val p = pixels[idx]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue

                var hash = x * 374761393 + y * 668265263
                hash = (hash xor (hash ushr 13)) * 1274126177
                hash = hash xor (hash ushr 16)
                val nFine = (hash and 0xFFFF) / 65535f
                val fineDelta = (nFine - 0.5f) * 2f * fineStrength

                val cx = x / cellSize
                var chash = cx * 668265263 + cy * 374761393
                chash = (chash xor (chash ushr 13)) * 1274126177
                chash = chash xor (chash ushr 16)
                val nCoarse = (chash and 0xFFFF) / 65535f
                val coarseDelta = if (nCoarse > coarseThreshold) {
                    // Reescala el rango que supera el umbral a 0..1 antes
                    // de aplicar la fuerza, así el pico más alto posible
                    // sigue llegando a [coarseStrength] completo.
                    ((nCoarse - coarseThreshold) / (1f - coarseThreshold)) * -coarseStrength
                } else {
                    0f
                }

                val newAlpha = (a + fineDelta + coarseDelta).roundToInt().coerceIn(0, 255)
                pixels[idx] = (newAlpha shl 24) or (p and 0x00FFFFFF)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Compone, de atrás hacia adelante, las capas que rodean al sujeto:
     * sombra proyectada (con offset por distancia+ángulo) → resplandor
     * (silueta teñida y difuminada, SIN offset, 360° alrededor) →
     * contorno sólido → [foreground] (el sujeto ya procesado por
     * saturación/tono/nitidez/difuminado de fondo) → reflejo (debajo de
     * TODO el conjunto anterior, ver [buildReflection]). Sombra/
     * resplandor/contorno se calculan siempre a partir del alfa del
     * [original] SIN procesar — nunca del resultado de otro efecto —
     * para que queden totalmente independientes entre sí (mover un
     * slider nunca "acumula" un difuminado extra sobre el de otro
     * control); el reflejo, en cambio, sí espeja [foreground] tal cual
     * (con sus propios ajustes de color/nitidez/difuminado de fondo ya
     * horneados), porque conceptualmente es "lo mismo que se ve, dado
     * vuelta" — no un efecto adicional sobre el sujeto. El lienzo final
     * se agranda con margen ([pad], simétrico en los 4 lados para
     * sombra/resplandor/contorno) más una franja extra SOLO abajo
     * ([reflectionExtraHeight]) del alto exacto del sujeto para que el
     * reflejo entero entre sin recortarse — igual criterio que ya usa
     * Extrude3D para su cuerpo extruido.
     */
    /**
     * Una sombra de contacto ya resuelta a bitmap, lista para
     * compositar — [centerOffsetPx] es el desplazamiento horizontal en
     * píxeles respecto al centro del sujeto (0 = centrada, el caso
     * clásico), e [intensityScale] el multiplicador propio de este
     * punto sobre [ImageEffectsParams.contactShadowIntensity]. Ver
     * [ImageEffectsParams.contactShadowPoints].
     */
    private data class ContactShadowInstance(
        val bitmap: Bitmap,
        val centerOffsetPx: Float,
        val intensityScale: Float
    )

    private fun compositeWithEffects(
        original: Bitmap,
        foreground: Bitmap,
        params: ImageEffectsParams
    ): Bitmap {
        val w = original.width
        val h = original.height

        // Oclusión ambiental sobre el propio sujeto — se resuelve ACÁ,
        // antes de cualquier otro uso de [foreground] en esta función,
        // para que tanto el reflejo (que espeja [occludedForeground])
        // como el dibujo final del sujeto muestren el mismo
        // oscurecimiento en su punto de apoyo — un reflejo real
        // reflejaría esa sombra de contacto propia del objeto también.
        val occludedForeground = if (params.groundOcclusionIntensity > 0.001f) {
            applyContactOcclusion(foreground, params.groundOcclusionIntensity)
        } else {
            foreground
        }

        var shadowLayer: Bitmap? = null
        var shadowDx = 0f
        var shadowDy = 0f
        var shadowBlurPx = 0
        var shadowSpreadPx = 0
        if (params.shadowIntensity > 0.001f) {
            // shadowBlur nunca en 0 puro: una sombra sin NADA de
            // difuminado se ve como un recorte duro pegado a la imagen,
            // no como una sombra — se garantiza un mínimo sutil para que
            // siempre "lea" como sombra incluso con el slider al piso.
            shadowBlurPx = blurRadiusPx(params.shadowBlur.coerceIn(0.08f, 1f), w, h)
            val silhouette = tintedSilhouette(original, params.shadowColor)

            // Spread: dilata el núcleo ANTES de desvanecer/difuminar —
            // el radio de dilatación se escala respecto al blur elegido
            // (así "Expansión" siempre se siente proporcional al
            // "Difuminado" actual, nunca desconectado de él).
            shadowSpreadPx = (params.shadowSpread * shadowBlurPx).roundToInt()
            val spread = if (shadowSpreadPx >= 1) {
                val d = dilateSilhouette(silhouette, shadowSpreadPx)
                if (d !== silhouette) silhouette.recycle()
                d
            } else {
                silhouette
            }

            // Desvanecimiento por distancia: se aplica ANTES del blur
            // (sobre la silueta recién teñida/expandida) para que el
            // difuminado final suavice también la transición del
            // degradado, en vez de quedar como un corte aparte encima de
            // una sombra ya difuminada.
            val faded = if (params.shadowFadeByDistance > 0.001f) {
                val f = applyVerticalFade(spread, params.shadowFadeByDistance, params.shadowOpacityCurve)
                if (f !== spread) spread.recycle()
                f
            } else {
                spread
            }
            val grained = if (params.shadowNoise > 0.001f) {
                val g = applyShadowGrain(faded, params.shadowNoise)
                if (g !== faded) faded.recycle()
                g
            } else {
                faded
            }
            // El radio de difuminado EFECTIVO se reduce a medida que
            // sube el Spread — mismo trade-off que el "Spread" de
            // Photoshop: a más núcleo sólido, borde relativamente más
            // ajustado, en vez de simplemente sumar tamaño de más al
            // difuminado ya existente.
            val effectiveBlurPx = (shadowBlurPx * (1f - params.shadowSpread * 0.6f)).roundToInt().coerceAtLeast(0)
            val blurred = boxBlur(grained, effectiveBlurPx)
            if (blurred !== grained) grained.recycle()
            // "Contact-hardening": se SUMA encima del difuminado parejo
            // de arriba, creciendo hacia el extremo lejano del punto de
            // apoyo — [nearAtBottomRow] = true porque en la silueta de
            // la sombra (sin voltear) el pie está en la ÚLTIMA fila, al
            // revés que en [buildReflection]. Mismo criterio exacto que
            // ya usa [reflectionProgressiveBlur] con el reflejo.
            shadowLayer = if (params.shadowContactHardening > 0.001f) {
                val hardened = applyProgressiveBlur(blurred, params.shadowContactHardening, nearAtBottomRow = true)
                if (hardened !== blurred) blurred.recycle()
                hardened
            } else {
                blurred
            }

            val maxOffset = max(w, h) * 0.18f
            val distancePx = params.shadowDistance * maxOffset
            val (ux, uy) = angleDegToOffsetUnitVector(params.shadowAngleDeg)
            shadowDx = ux * distancePx
            shadowDy = uy * distancePx
        }

        // Sombra de RELLENO: misma receta que la principal (silueta
        // teñida + difuminada + offset por distancia/ángulo) pero con
        // sus PROPIOS color/difuminado/distancia/ángulo — sin
        // inclinación ni desvanecimiento propios, para mantenerla como
        // un segundo "golpe" simple de luz ambiente en vez de duplicar
        // todos los controles de la principal.
        var fillShadowLayer: Bitmap? = null
        var fillShadowDx = 0f
        var fillShadowDy = 0f
        var fillShadowBlurPx = 0
        if (params.fillShadowIntensity > 0.001f) {
            fillShadowBlurPx = blurRadiusPx(params.fillShadowBlur.coerceIn(0.08f, 1f), w, h)
            val silhouette = tintedSilhouette(original, params.fillShadowColor)
            val blurred = boxBlur(silhouette, fillShadowBlurPx)
            if (blurred !== silhouette) silhouette.recycle()
            fillShadowLayer = blurred

            val maxOffset = max(w, h) * 0.18f
            val distancePx = params.fillShadowDistance * maxOffset
            val (ux, uy) = angleDegToOffsetUnitVector(params.fillShadowAngleDeg)
            fillShadowDx = ux * distancePx
            fillShadowDy = uy * distancePx
        }

        var glowLayer: Bitmap? = null
        var glowBlurPx = 0
        var glowSpreadPx = 0
        var glowDx = 0f
        var glowDy = 0f
        if (params.glowIntensity > 0.001f) {
            glowBlurPx = blurRadiusPx(params.glowBlur, w, h)
            // Degradado (dos colores) — A PEDIDO DEL USUARIO: cuando está
            // activo, todo el pipeline de abajo (spread + blur) se arma
            // sobre una silueta NEUTRA (blanco sólido) en vez de teñida
            // con [glowColor] de una — así el único dato que sobrevive al
            // difuminado es el ALFA (la "forma" del halo), y el color
            // final se resuelve recién al final, en un solo paso, con
            // [buildGlowGradientTint]. Con el degradado apagado (default)
            // esto es IDÉNTICO a [tintedSilhouette(original, params.glowColor)]
            // de siempre.
            val silhouette = if (params.glowGradientEnabled) {
                tintedSilhouette(original, Color.WHITE)
            } else {
                tintedSilhouette(original, params.glowColor)
            }

            // Spread/Choke — mismo criterio EXACTO que [shadowSpread]:
            // dilata el núcleo teñido ANTES de difuminar, y reduce
            // proporcionalmente el radio de difuminado efectivo. El
            // "Outer Glow" de Photoshop llama a esto justamente
            // "Spread".
            glowSpreadPx = (params.glowSpread * glowBlurPx).roundToInt()
            val spread = if (glowSpreadPx >= 1) {
                val d = dilateSilhouette(silhouette, glowSpreadPx)
                if (d !== silhouette) silhouette.recycle()
                d
            } else {
                silhouette
            }

            val effectiveBlurPx = (glowBlurPx * (1f - params.glowSpread * 0.6f)).roundToInt().coerceAtLeast(0)
            val blurred = if (effectiveBlurPx >= 1) boxBlur(spread, effectiveBlurPx) else spread
            if (blurred !== spread) spread.recycle()

            glowLayer = if (params.glowGradientEnabled) {
                val tinted = buildGlowGradientTint(blurred, original, params.glowColor, params.glowColor2)
                blurred.recycle()
                tinted
            } else {
                blurred
            }

            // Distancia/ángulo — mismo mecanismo que [shadowDistance]/
            // [shadowAngleDeg], en 0 sigue centrado como siempre.
            val maxOffset = max(w, h) * 0.18f
            val distancePx = params.glowDistance * maxOffset
            val (ux, uy) = angleDegToOffsetUnitVector(params.glowAngleDeg)
            glowDx = ux * distancePx
            glowDy = uy * distancePx
        }

        // Contorno: según [ImageEffectsParams.outlinePosition], el
        // anillo puede vivir AFUERA de la silueta (capa aparte, se pinta
        // ANTES que el sujeto — ver más abajo), ADENTRO (otra capa
        // aparte, se pinta DESPUÉS del sujeto para quedar por encima de
        // su propio color) o repartido mitad y mitad entre las dos —
        // por eso son dos variables separadas en vez de una sola capa
        // de contorno como antes.
        var outlineLayerOutside: Bitmap? = null
        var outlineLayerInside: Bitmap? = null
        var outlinePx = 0
        if (params.outlineIntensity > 0.001f) {
            outlinePx = blurRadiusPx(params.outlineIntensity, w, h)

            // Grosor independiente del feather — A PEDIDO DEL USUARIO.
            // ANTES: [featherOutlineRing] difuminaba el anillo YA
            // umbralado a [outlinePx] de ancho; con un feather alto
            // respecto al grosor pedido, las dos rampas del difuminado
            // (borde interior y exterior del anillo) se solapaban y el
            // alfa nunca volvía a llegar a 255 en el medio — el núcleo
            // sólido se veía más fino Y más transparente a la vez, como
            // si "Grosor" y "Difuminado" estuvieran pisándose entre sí.
            // AHORA: el anillo se construye [featherOutlinePx] más ancho
            // de lo pedido (exactamente lo que el blur posterior va a
            // "comer" de cada lado), así el núcleo opaco que sobrevive al
            // difuminado vuelve a medir lo que el usuario pidió en
            // "Grosor", y el feather queda como una caída suave ADICIONAL
            // alrededor de ese núcleo en vez de restarle ancho. En
            // feather=0 esto no cambia NADA (compensación = 0, idéntico a
            // como era antes).
            val featherOutlinePx = if (params.outlineFeather > 0.001f) blurRadiusPx(params.outlineFeather, w, h) else 0

            when (params.outlinePosition) {
                OutlineStrokePosition.OUTSIDE -> {
                    outlineLayerOutside = featherOutlineRing(
                        buildDilatedOutline(
                            original, params.outlineColor, params.outlineIntensity,
                            color2 = params.outlineColor2,
                            gradientEnabled = params.outlineGradientEnabled,
                            thicknessPxOverride = outlinePx + featherOutlinePx
                        ),
                        params.outlineFeather
                    )
                }
                OutlineStrokePosition.INSIDE -> {
                    outlineLayerInside = featherOutlineRing(
                        buildInnerOutline(
                            original, params.outlineColor, params.outlineIntensity,
                            color2 = params.outlineColor2,
                            gradientEnabled = params.outlineGradientEnabled,
                            thicknessPxOverride = outlinePx + featherOutlinePx
                        ),
                        params.outlineFeather
                    )
                }
                OutlineStrokePosition.CENTER -> {
                    // Mitad del grosor pedido afuera, mitad adentro — el
                    // trazo total queda centrado justo sobre el borde
                    // real, ni agranda ni "come" la silueta.
                    val halfThickness = params.outlineIntensity * 0.5f
                    val halfPx = blurRadiusPx(halfThickness, w, h)
                    outlineLayerOutside = featherOutlineRing(
                        buildDilatedOutline(
                            original, params.outlineColor, halfThickness,
                            color2 = params.outlineColor2,
                            gradientEnabled = params.outlineGradientEnabled,
                            thicknessPxOverride = halfPx + featherOutlinePx
                        ),
                        params.outlineFeather
                    )
                    outlineLayerInside = featherOutlineRing(
                        buildInnerOutline(
                            original, params.outlineColor, halfThickness,
                            color2 = params.outlineColor2,
                            gradientEnabled = params.outlineGradientEnabled,
                            thicknessPxOverride = halfPx + featherOutlinePx
                        ),
                        params.outlineFeather
                    )
                }
            }
        }

        // Sombra de CONTACTO: mancha corta con caída radial suave,
        // pegada al punto donde el sujeto "pisa" — totalmente aparte de
        // la sombra proyectada de arriba (esa sí se mueve con distancia/
        // ángulo; esta siempre queda pegada al pie). Se calcula ANTES
        // del cómputo de [pad] porque también necesita margen propio
        // para no recortarse.
        //
        // Soporta MÚLTIPLES puntos de apoyo (ver
        // [ImageEffectsParams.contactShadowPoints]): lista vacía = una
        // sola mancha centrada (comportamiento clásico); lista con 1+
        // elementos = una mancha independiente por punto, cada una con
        // su propio desplazamiento horizontal y multiplicadores de
        // tamaño/intensidad sobre los valores base.
        val contactShadowInstances = mutableListOf<ContactShadowInstance>()
        if (params.contactShadowIntensity > 0.001f) {
            val points = if (params.contactShadowPoints.isEmpty()) {
                listOf(ContactShadowPoint())
            } else {
                params.contactShadowPoints
            }
            for (point in points) {
                val pointSize = (params.contactShadowSize * point.sizeScale).coerceIn(0.05f, 2f)
                val built = buildContactShadow(w, pointSize, params.contactShadowColor, params.contactShadowFalloff)
                val grained = if (params.contactShadowNoise > 0.001f) {
                    val g = applyShadowGrain(built, params.contactShadowNoise)
                    if (g !== built) built.recycle()
                    g
                } else {
                    built
                }
                val blurred = if (params.contactShadowBlur > 0.001f) {
                    val cBlurPx = blurRadiusPx(params.contactShadowBlur, w, h)
                    val b = boxBlur(grained, cBlurPx)
                    if (b !== grained) grained.recycle()
                    b
                } else {
                    grained
                }
                contactShadowInstances.add(
                    ContactShadowInstance(
                        bitmap = blurred,
                        centerOffsetPx = point.xOffset * (w / 2f),
                        intensityScale = point.intensityScale
                    )
                )
            }
        }

        var reflectionLayer: Bitmap? = null
        var reflectionGapPx = 0
        if (params.reflectionIntensity > 0.001f) {
            val built = buildReflection(
                occludedForeground,
                params.reflectionIntensity,
                params.reflectionLength,
                params.reflectionTintIntensity,
                params.reflectionTintColor,
                params.reflectionEdgeFade,
                params.reflectionOpacityCurve,
                params.reflectionFresnel
            )
            // Ondulación tipo "agua": se aplica ANTES del difuminado
            // propio del reflejo (si lo hay) para que ese blur suavice
            // también los bordes de la onda — el mismo orden lógico que
            // el desvanecimiento por distancia de la sombra: primero se
            // distorsiona la forma, después se difumina el resultado.
            val rippled = if (params.reflectionRippleIntensity > 0.001f) {
                applyRipple(built, params.reflectionRippleIntensity, params.reflectionRippleScale)
            } else {
                built
            }
            if (rippled !== built) built.recycle()
            val uniformBlurred = if (params.reflectionBlur > 0.001f) {
                // Difuminado propio del reflejo (look "piso pulido"/vidrio
                // esmerilado): usa el mismo box-blur de 3 pasadas que ya
                // usan sombra/resplandor, con su propio radio — totalmente
                // independiente del difuminado de fondo o de la sombra.
                val reflBlurPx = blurRadiusPx(params.reflectionBlur, w, h)
                val blurred = boxBlur(rippled, reflBlurPx)
                if (blurred !== rippled) rippled.recycle()
                blurred
            } else {
                rippled
            }
            // Difuminado PROGRESIVO: se SUMA encima del difuminado
            // parejo de arriba, creciendo con la distancia al pie — un
            // piso pulido real refleja con más nitidez lo que está cerca
            // y se va desenfocando hacia el fondo del reflejo.
            val progressiveBlurred = if (params.reflectionProgressiveBlur > 0.001f) {
                val progressive = applyProgressiveBlur(uniformBlurred, params.reflectionProgressiveBlur)
                if (progressive !== uniformBlurred) uniformBlurred.recycle()
                progressive
            } else {
                uniformBlurred
            }
            // Grano/textura de superficie: SIEMPRE el último paso del
            // reflejo — ver el porqué en [applyReflectionGrain] (es una
            // textura de la superficie reflectante, no de la imagen
            // reflejada, así que no debe pasar por ninguno de los
            // difuminados/distorsiones de arriba).
            reflectionLayer = if (params.reflectionNoise > 0.001f) {
                val grained = applyReflectionGrain(progressiveBlurred, params.reflectionNoise)
                if (grained !== progressiveBlurred) progressiveBlurred.recycle()
                grained
            } else {
                progressiveBlurred
            }
            // Distancia/separación entre el pie del sujeto y su reflejo:
            // 0 = pegado (comportamiento clásico, igual que antes), hasta
            // un 25% del alto del sujeto para el máximo del slider — así
            // el "despegue" se ve proporcional tanto en una capa chica
            // como en una grande, mismo criterio que shadowDistance.
            reflectionGapPx = (params.reflectionGap * h * 0.25f).roundToInt()
        }
        val reflectionLayerHeight = reflectionLayer?.height ?: 0
        // Si hay compresión/estiramiento por perspectiva, la altura
        // ocupada en el lienzo final crece o se achica en la misma
        // proporción (pivota desde la fila 0, pegada al pie, así que
        // TODO el estiramiento extra cae hacia abajo).
        val reflectionScaledHeight = if (reflectionLayer != null) {
            (reflectionLayerHeight * params.reflectionPerspective).roundToInt()
        } else 0
        val reflectionExtraHeight = if (reflectionLayer != null) reflectionScaledHeight + reflectionGapPx else 0

        // Inclinación (perspectiva) del reflejo: un shear horizontal que
        // pivotea desde la fila pegada al pie del sujeto (ahí nunca se
        // mueve) y abre hacia el extremo lejano del reflejo — igual look
        // que un piso visto en ángulo. Como esto puede correr el reflejo
        // hacia un costado, el lienzo necesita margen horizontal EXTRA
        // (más allá de [pad]) del lado que corresponda.
        val reflectionSkewFactor = if (reflectionLayer != null && abs(params.reflectionSkewDegrees) > 0.01f) {
            tan(Math.toRadians(params.reflectionSkewDegrees.toDouble())).toFloat()
        } else {
            0f
        }
        val reflectionSkewPad = (abs(reflectionSkewFactor) * reflectionScaledHeight).roundToInt()

        // Inclinación (perspectiva) de la sombra proyectada: mismo shear
        // horizontal que el reflejo, pero pivotando desde la fila
        // INFERIOR de la silueta completa (el pie, el punto de apoyo del
        // sujeto sobre el "piso") en vez de la fila 0 — tiene sentido
        // físico distinto: acá lo que se inclina es todo el cuerpo
        // proyectado, no una copia espejada que ya arranca pegada al pie.
        val shadowSkewFactor = if (shadowLayer != null && abs(params.shadowSkewDegrees) > 0.01f) {
            tan(Math.toRadians(params.shadowSkewDegrees.toDouble())).toFloat()
        } else {
            0f
        }
        val shadowSkewPad = (abs(shadowSkewFactor) * h).roundToInt()

        val shadowReach = if (shadowLayer != null) {
            val scaleExtra = max(0f, params.shadowScale - 1f) * max(w, h)
            shadowBlurPx * 2f + shadowSpreadPx + scaleExtra + max(abs(shadowDx), abs(shadowDy))
        } else 0f
        val fillShadowReach = if (fillShadowLayer != null) {
            val scaleExtra = max(0f, params.fillShadowScale - 1f) * max(w, h)
            fillShadowBlurPx * 2f + scaleExtra + max(abs(fillShadowDx), abs(fillShadowDy))
        } else 0f
        val glowReach = if (glowLayer != null) {
            glowBlurPx * 2f + glowSpreadPx + max(abs(glowDx), abs(glowDy))
        } else 0f
        val outlineOutwardPx = when {
            outlineLayerOutside == null -> 0
            params.outlinePosition == OutlineStrokePosition.CENTER -> (outlinePx * 0.5f).roundToInt()
            else -> outlinePx
        }
        val outlineFeatherPx = if (params.outlineFeather > 0.001f) blurRadiusPx(params.outlineFeather, w, h) else 0
        // x2 en el feather: desde que el anillo se construye ya
        // compensado ([featherOutlinePx] extra de radio, ver el
        // comentario grande sobre "grosor independiente del feather" en
        // [compositeWithEffects]), el borde exterior real del anillo
        // sólido ya vive [outlineFeatherPx] más lejos de lo que antes —
        // y el difuminado posterior todavía suma otro tanto de caída más
        // allá de ESE borde. Sin este ajuste, el margen del lienzo se
        // quedaba corto y un feather alto recortaba la punta del halo.
        val outlineReach = (outlineOutwardPx + outlineFeatherPx * 2).toFloat()
        // Alcance de la sombra de contacto: vertical (cuánto sobresale
        // por debajo del pie, igual criterio que antes) y HORIZONTAL —
        // este último es nuevo, necesario porque un punto de apoyo con
        // [ContactShadowPoint.xOffset] puede quedar más allá del ancho
        // del sujeto (p.ej. una pata de silla más ancha que el cuerpo
        // que la sostiene); con un único punto centrado (el caso de
        // siempre) esto siempre da 0, así que no cambia el margen de
        // ningún proyecto existente.
        val contactShadowReach = contactShadowInstances.maxOfOrNull { it.bitmap.height / 2f } ?: 0f
        val contactShadowHorizontalReach = contactShadowInstances.maxOfOrNull {
            max(0f, abs(it.centerOffsetPx) + it.bitmap.width / 2f - w / 2f)
        } ?: 0f
        val pad = max(shadowReach, max(fillShadowReach, max(glowReach, max(outlineReach, contactShadowReach)))).roundToInt() + 4
        val padX = pad + max(reflectionSkewPad, max(shadowSkewPad, contactShadowHorizontalReach.roundToInt()))
        val padY = pad

        val outW = w + padX * 2
        val outH = h + padY * 2 + reflectionExtraHeight
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Familia de sombras (relleno + proyectada + contacto): se
        // componen juntas en un búfer de píxeles PROPIO (no directamente
        // sobre [canvas]) usando [blitBlend], que sabe mezclar en modo
        // NORMAL o MULTIPLICAR según [shadowBlendMultiply] — así, donde
        // la sombra de contacto se superpone con la proyectada (el caso
        // más común, justo en el pie), el resultado se OSCURECE de forma
        // natural en vez de mezclarse como un gris translúcido. Orden
        // "de atrás hacia adelante" dentro de la familia: relleno
        // (la más ambiental/difusa) → proyectada (la principal) →
        // contacto (la más pegada/definida) — mismo criterio pictórico
        // que el resto de esta función.
        if (shadowLayer != null || fillShadowLayer != null || contactShadowInstances.isNotEmpty()) {
            val shadowGroupPixels = IntArray(outW * outH)

            fillShadowLayer?.let {
                val matrix = Matrix()
                if (abs(params.fillShadowScale - 1f) > 0.001f) {
                    matrix.postScale(params.fillShadowScale, params.fillShadowScale, w / 2f, (h - 1).toFloat())
                }
                matrix.postTranslate(padX + fillShadowDx, padY + fillShadowDy)
                blitBlend(shadowGroupPixels, outW, outH, it, matrix, params.fillShadowIntensity, params.shadowBlendMultiply)
                it.recycle()
            }

            shadowLayer?.let {
                if (params.groundWallBreak > 0.001f && shadowSkewFactor != 0f && h > 3) {
                    // QUIEBRE PISO/PARED: en vez de UNA transformación
                    // pareja de punta a punta, la silueta se parte en
                    // dos tramos con su propio pivote — ver el
                    // comentario grande de [ImageEffectsParams.groundWallBreak].
                    // Simplificación consciente: con el quiebre activo
                    // se prioriza el shear piecewise por sobre la
                    // homografía de [shadowPerspectiveAmount] (ambos
                    // resuelven el mismo problema visual — "la sombra
                    // deja de estirarse en línea recta" — combinarlos
                    // llevaría a una doble distorsión que se pisa a sí
                    // misma en el quiebre, así que mientras
                    // [groundWallBreak] > 0 la perspectiva de punto de
                    // fuga queda en pausa).
                    val nearHeight = (params.groundWallBreak.coerceIn(0.05f, 0.95f) * (h - 1))
                        .roundToInt().coerceIn(1, h - 1)
                    val farHeight = h - nearHeight
                    // Tramo PISO: igual fórmula que el shear clásico de
                    // siempre, solo que acotada a [nearHeight] filas en
                    // vez de las [h] originales — mismo pivote relativo
                    // (su propia fila inferior).
                    val nearCrop = Bitmap.createBitmap(it, 0, h - nearHeight, w, nearHeight)
                    val nearMatrix = Matrix()
                    if (abs(params.shadowScale - 1f) > 0.001f) {
                        nearMatrix.postScale(params.shadowScale, params.shadowScale, w / 2f, (nearHeight - 1).toFloat())
                    }
                    nearMatrix.postConcat(
                        Matrix().apply {
                            setValues(
                                floatArrayOf(
                                    1f, shadowSkewFactor, -shadowSkewFactor * (nearHeight - 1),
                                    0f, 1f, 0f,
                                    0f, 0f, 1f
                                )
                            )
                        }
                    )
                    nearMatrix.postTranslate(padX + shadowDx, padY + shadowDy + (h - nearHeight))
                    blitBlend(shadowGroupPixels, outW, outH, nearCrop, nearMatrix, params.shadowIntensity, params.shadowBlendMultiply)
                    nearCrop.recycle()

                    if (farHeight > 0) {
                        // Tramo PARED: retiene solo una fracción chica
                        // de la inclinación (una pared vertical casi no
                        // diverge más hacia los costados) y arranca
                        // EXACTAMENTE del offset horizontal donde
                        // terminó el tramo piso — [seamOffset] — para
                        // que no haya un salto visible en el quiebre.
                        // IMPORTANTE: [seamOffset] tiene que incluir
                        // [params.shadowScale] — el shear del tramo piso
                        // se calcula DESPUÉS de escalar (la escala se
                        // aplica primero en la matriz), así que el
                        // offset que de verdad queda en su fila superior
                        // (la que toca la costura) ya viene multiplicado
                        // por la escala. Si acá se usara el offset "sin
                        // escalar" quedaría un salto visible en la
                        // costura apenas "Escala de la sombra" ≠ 100%.
                        val farSkewFactor = shadowSkewFactor * GROUND_WALL_SKEW_RETENTION
                        val seamOffset = -shadowSkewFactor * params.shadowScale * (nearHeight - 1)
                        val farCrop = Bitmap.createBitmap(it, 0, 0, w, farHeight)
                        val farMatrix = Matrix()
                        if (abs(params.shadowScale - 1f) > 0.001f) {
                            // Pivote en la propia costura (la fila
                            // inferior de ESTE recorte), NO en el pie
                            // global — con el pivote en la costura, el
                            // shear que viene después sigue viendo esa
                            // fila en la misma posición sin importar
                            // cuánto escale, así [seamOffset] de arriba
                            // sigue siendo válido tal cual.
                            farMatrix.postScale(params.shadowScale, params.shadowScale, w / 2f, (farHeight - 1).toFloat())
                        }
                        farMatrix.postConcat(
                            Matrix().apply {
                                setValues(
                                    floatArrayOf(
                                        1f, farSkewFactor, seamOffset - farSkewFactor * (farHeight - 1),
                                        0f, 1f, 0f,
                                        0f, 0f, 1f
                                    )
                                )
                            }
                        )
                        farMatrix.postTranslate(padX + shadowDx, padY + shadowDy)
                        blitBlend(shadowGroupPixels, outW, outH, farCrop, farMatrix, params.shadowIntensity, params.shadowBlendMultiply)
                        farCrop.recycle()
                    }
                    it.recycle()
                } else {
                    // Camino CLÁSICO (sin quiebre): idéntico de punta a
                    // punta al comportamiento de siempre. Orden de
                    // composición del transform: ESCALA primero (pivote
                    // en el pie del sujeto, x=w/2, y=h-1, para que
                    // escalar nunca "levante" la sombra del piso) →
                    // PERSPECTIVA de punto de fuga (convergencia real) →
                    // INCLINACIÓN (shear) → TRASLACIÓN (offset por
                    // distancia/ángulo + posición en el lienzo) al
                    // final — mismo criterio de pivote que ya usa la
                    // inclinación acá abajo, aplicado ahora también a la
                    // escala y a la perspectiva.
                    val matrix = Matrix()
                    if (abs(params.shadowScale - 1f) > 0.001f) {
                        matrix.postScale(params.shadowScale, params.shadowScale, w / 2f, (h - 1).toFloat())
                    }
                    if (params.shadowPerspectiveAmount > 0.001f) {
                        // Homografía real (no un shear): mapea las 4
                        // esquinas de la silueta a un trapecio que converge
                        // hacia un punto de fuga — la fila pegada al pie
                        // (y=h-1, el punto de apoyo) mantiene su ancho
                        // completo, mientras la fila más lejana (y=0) se
                        // angosta hacia el centro en proporción a
                        // [shadowPerspectiveAmount]. Es la diferencia entre
                        // "inclinar" una silueta y proyectarla de verdad
                        // sobre un plano de piso visto en perspectiva. Se
                        // aplica ANTES del shear de [shadowSkewDegrees] para
                        // que ambos controles se combinen sin pisarse: el
                        // shear después sigue pivotando en la misma fila
                        // (y=h-1), que la perspectiva nunca mueve.
                        matrix.postConcat(buildPerspectiveMatrix(w, h, params.shadowPerspectiveAmount))
                    }
                    if (shadowSkewFactor != 0f) {
                        // x' = x + shadowSkewFactor * (y - (h-1)) — pivota
                        // en la fila inferior (el pie, y=h-1), donde el
                        // shift es 0, y abre hacia arriba a medida que
                        // crece la silueta — el mismo shear que el
                        // reflejo, pero anclado al punto de apoyo en vez
                        // de a la fila 0.
                        val skewMatrix = Matrix().apply {
                            setValues(
                                floatArrayOf(
                                    1f, shadowSkewFactor, -shadowSkewFactor * (h - 1),
                                    0f, 1f, 0f,
                                    0f, 0f, 1f
                                )
                            )
                        }
                        matrix.postConcat(skewMatrix)
                    }
                    matrix.postTranslate(padX + shadowDx, padY + shadowDy)
                    blitBlend(shadowGroupPixels, outW, outH, it, matrix, params.shadowIntensity, params.shadowBlendMultiply)
                    it.recycle()
                }
            }

            // Cada sombra de contacto va centrada en su propio punto de
            // apoyo ([ContactShadowInstance.centerOffsetPx] desde el
            // centro del sujeto, 0 = centrada = el caso clásico) y con
            // su centro vertical apoyado justo en el borde inferior del
            // sujeto — ahí es donde "toca el piso".
            contactShadowInstances.forEach { instance ->
                val dx = padX + w / 2f + instance.centerOffsetPx - instance.bitmap.width / 2f
                val dy = padY + h - instance.bitmap.height / 2f
                val matrix = Matrix().apply { postTranslate(dx, dy) }
                val effectiveIntensity = (params.contactShadowIntensity * instance.intensityScale).coerceIn(0f, 1f)
                blitBlend(shadowGroupPixels, outW, outH, instance.bitmap, matrix, effectiveIntensity, params.shadowBlendMultiply)
                instance.bitmap.recycle()
            }

            val shadowGroupBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            shadowGroupBitmap.setPixels(shadowGroupPixels, 0, outW, 0, 0, outW, outH)
            // El grupo ya resuelto (con su mezcla interna correcta) se
            // pinta sobre [canvas] con composición NORMAL: sigue siendo
            // matemáticamente correcto porque [canvas] está vacío en
            // este punto — mezclar en "multiplicar" contra transparencia
            // pura da exactamente el mismo resultado que mezclar normal
            // (ver comentario grande en [blitBlend]).
            canvas.drawBitmap(shadowGroupBitmap, 0f, 0f, drawPaint)
            shadowGroupBitmap.recycle()
        }

        glowLayer?.let {
            val glowPaint = Paint(drawPaint).apply {
                alpha = (params.glowIntensity * 255f).roundToInt().coerceIn(0, 255)
                // Blend mode — A PEDIDO DEL USUARIO ("le falta blend mode,
                // hoy solo hay un modo de mezcla"). `null` (NORMAL, el
                // default) deja el `Paint` EXACTO como antes de este
                // control: sin `Xfermode`, composición simple. Ver el KDoc
                // de [glowPorterDuffMode] para por qué SCREEN/ADD/LIGHTEN
                // se pueden resolver con el `PorterDuffXfermode` nativo de
                // Android sin reimplementarlos a mano.
                glowPorterDuffMode(params.glowBlendMode)?.let { mode ->
                    xfermode = android.graphics.PorterDuffXfermode(mode)
                }
            }
            canvas.drawBitmap(it, padX + glowDx, padY + glowDy, glowPaint)
            it.recycle()
        }

        // Contorno EXTERIOR: se pinta ANTES que el sujeto (queda
        // "detrás", solo se asoma el anillo que sobresale del borde
        // real) — ver [ImageEffectsParams.outlinePosition]. El contorno
        // INTERIOR (misma variable [outlineLayerInside]) se pinta más
        // abajo, DESPUÉS del sujeto, para quedar por encima de su color.
        outlineLayerOutside?.let {
            canvas.drawBitmap(it, padX.toFloat(), padY.toFloat(), drawPaint)
            it.recycle()
        }

        // El reflejo se dibuja ANTES que el sujeto en el orden de
        // pintado (queda "debajo" en el z-order) pero su POSICIÓN es
        // relativa al borde inferior del sujeto (padY + h), corrida hacia
        // abajo por [reflectionGapPx] cuando el usuario pide separación
        // — con el slider "Distancia" en 0 queda pegada, igual que antes.
        // Cuando hay inclinación y/o perspectiva, se dibuja con una
        // matriz (drawBitmap con Matrix) en vez de una posición simple,
        // para no tener que rehacer los píxeles del bitmap.
        reflectionLayer?.let {
            val hasSkew = reflectionSkewFactor != 0f
            val hasPerspective = abs(params.reflectionPerspective - 1f) > 0.001f
            val reflH = it.height
            if (params.groundWallBreak > 0.001f && hasSkew && reflH > 3) {
                // QUIEBRE PISO/PARED aplicado al reflejo: misma idea
                // piecewise que en la sombra (ver ese bloque para el
                // detalle), pero con la orientación propia del reflejo
                // — fila 0 = pegada al pie (pivote), filas altas = el
                // extremo lejano/horizonte.
                val nearHeight = (params.groundWallBreak.coerceIn(0.05f, 0.95f) * (reflH - 1))
                    .roundToInt().coerceIn(1, reflH - 1)
                val farHeight = reflH - nearHeight

                val nearCrop = Bitmap.createBitmap(it, 0, 0, w, nearHeight)
                val nearMatrix = Matrix()
                if (hasPerspective) {
                    nearMatrix.postScale(1f, params.reflectionPerspective, 0f, 0f)
                }
                nearMatrix.postConcat(
                    Matrix().apply {
                        setValues(floatArrayOf(1f, reflectionSkewFactor, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
                    }
                )
                nearMatrix.postTranslate(padX.toFloat(), (padY + h + reflectionGapPx).toFloat())
                canvas.drawBitmap(nearCrop, nearMatrix, drawPaint)
                nearCrop.recycle()

                if (farHeight > 0) {
                    // [seamOffset] tiene que incluir
                    // [params.reflectionPerspective] cuando está activa
                    // — el shear del tramo cercano se calcula DESPUÉS de
                    // escalar verticalmente, así que el valor que
                    // realmente queda en su fila más lejana (la que toca
                    // la costura) ya viene multiplicado por esa escala.
                    val nearScaleAtSeam = if (hasPerspective) params.reflectionPerspective else 1f
                    val farSkewFactor = reflectionSkewFactor * GROUND_WALL_SKEW_RETENTION
                    val seamOffset = reflectionSkewFactor * nearScaleAtSeam * (nearHeight - 1)
                    val farCrop = Bitmap.createBitmap(it, 0, nearHeight, w, farHeight)
                    val farMatrix = Matrix()
                    if (hasPerspective) {
                        // Pivote en la propia costura (fila 0 de ESTE
                        // recorte, no la fila 0 global) — con el pivote
                        // ahí, el shear que viene después sigue viendo
                        // esa fila fija sin importar cuánto comprima la
                        // perspectiva, así [seamOffset] de arriba sigue
                        // siendo válido tal cual.
                        farMatrix.postScale(1f, params.reflectionPerspective, 0f, 0f)
                    }
                    farMatrix.postConcat(
                        Matrix().apply {
                            setValues(floatArrayOf(1f, farSkewFactor, seamOffset, 0f, 1f, 0f, 0f, 0f, 1f))
                        }
                    )
                    // Traslación vertical: usa [nearScaleAtSeam] * [nearHeight],
                    // NO [nearHeight] a secas — con perspectiva activa,
                    // el tramo cercano ya terminó COMPRIMIDO
                    // verticalmente en el canvas antes de llegar a esta
                    // fila, así que el tramo lejano tiene que arrancar
                    // desde esa altura YA comprimida, no desde la altura
                    // cruda en píxeles originales.
                    farMatrix.postTranslate(padX.toFloat(), (padY + h + reflectionGapPx + nearScaleAtSeam * nearHeight).toFloat())
                    canvas.drawBitmap(farCrop, farMatrix, drawPaint)
                    farCrop.recycle()
                }
            } else if (hasSkew || hasPerspective) {
                val matrix = Matrix()
                if (hasPerspective) {
                    // Escala vertical pura, pivotando en y=0 (la fila
                    // pegada al pie) — nunca cambia el ancho, para eso
                    // ya está reflectionSkewFactor.
                    matrix.postScale(1f, params.reflectionPerspective, 0f, 0f)
                }
                if (hasSkew) {
                    // x' = x + reflectionSkewFactor * y (pivota en y=0,
                    // la fila pegada al pie); y' = y sin cambios.
                    val skewMatrix = Matrix().apply {
                        setValues(
                            floatArrayOf(
                                1f, reflectionSkewFactor, 0f,
                                0f, 1f, 0f,
                                0f, 0f, 1f
                            )
                        )
                    }
                    matrix.postConcat(skewMatrix)
                }
                matrix.postTranslate(padX.toFloat(), (padY + h + reflectionGapPx).toFloat())
                canvas.drawBitmap(it, matrix, drawPaint)
            } else {
                canvas.drawBitmap(it, padX.toFloat(), (padY + h + reflectionGapPx).toFloat(), drawPaint)
            }
            it.recycle()
        }

        canvas.drawBitmap(occludedForeground, padX.toFloat(), padY.toFloat(), drawPaint)
        if (occludedForeground !== foreground) occludedForeground.recycle()

        // Contorno INTERIOR: se pinta DESPUÉS del sujeto a propósito
        // (ver comentario grande junto a [outlineLayerOutside]) — así
        // queda por encima del color del sujeto en vez de taparse por
        // detrás de él.
        outlineLayerInside?.let {
            canvas.drawBitmap(it, padX.toFloat(), padY.toFloat(), drawPaint)
            it.recycle()
        }

        return result
    }

    /**
     * Homografía de "punto de fuga" para la sombra proyectada: pivota en
     * la fila inferior `y = h-1` (el punto de apoyo del sujeto, que
     * queda SIEMPRE fija, sin importar cuánta perspectiva se pida) y
     * angosta la fila superior `y = 0` (el extremo más lejano de la
     * sombra) hacia el centro horizontal — el mismo look que un piso
     * real visto en perspectiva de un punto de fuga, calculado con
     * [Matrix.setPolyToPoly] (que resuelve una transformación
     * proyectiva de 4 puntos real, a diferencia de un shear afín que
     * solo puede inclinar sin converger).
     *
     * [amount] 0..1: en 0, el trapecio de destino es idéntico al
     * rectángulo de origen (matriz identidad, sin cambios); en 1, la
     * fila superior se angosta hasta el 35% de su ancho original —
     * convergencia fuerte pero sin llegar a colapsar en un punto, que
     * se vería artificial.
     */
    private fun buildPerspectiveMatrix(w: Int, h: Int, amount: Float): Matrix {
        val amt = amount.coerceIn(0f, 1f)
        val topNarrow = 1f - amt * 0.65f // 1 = ancho completo, 0.35 = convergencia máxima
        val halfW = w / 2f
        val footY = (h - 1).toFloat()
        val src = floatArrayOf(
            0f, 0f,
            w.toFloat(), 0f,
            w.toFloat(), footY,
            0f, footY
        )
        val dst = floatArrayOf(
            halfW - halfW * topNarrow, 0f,
            halfW + halfW * topNarrow, 0f,
            w.toFloat(), footY,
            0f, footY
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return matrix
    }

    /**
     * Copia de [foreground] volteada verticalmente ("espejo"), con un
     * degradado de opacidad lineal: 100% de [intensity] justo pegado al
     * borde inferior del sujeto (fila 0 del reflejo, que es la fila MÁS
     * BAJA del sujeto original, ya que está volteado) y 0% en el borde
     * más lejano — el efecto clásico de "reflejo sobre una superficie
     * brillante" de cualquier editor profesional. Se refleja
     * [foreground] (el sujeto YA con sus ajustes de color/nitidez/
     * difuminado de fondo horneados), no el bitmap original sin
     * procesar, porque el reflejo tiene que verse como el sujeto
     * final, dado vuelta — no como una versión sin editar de él.
     *
     * [length] (0.1..1) controla qué porción del alto del sujeto llega a
     * reflejarse: con 1 (por defecto, comportamiento original) el reflejo
     * ocupa el alto completo del sujeto y el degradado de opacidad recorre
     * ese alto entero; con un valor menor, el reflejo se corta antes —
     * queda más corto y compacto, como el reflejo de un piso pequeño en
     * vez de un espejo de cuerpo entero — pero el degradado sigue yendo
     * de 100% a 0% dentro de ese tramo más corto, así nunca termina en un
     * borde duro.
     *
     * [tintIntensity]/[tintColor]: mezcla lineal del color de cada píxel
     * del reflejo hacia [tintColor], look "reflejo en agua/vidrio de
     * color" de cualquier render profesional (piso de mármol, agua,
     * metal pulido con temperatura de color propia). En 0 el reflejo
     * conserva el color exacto del sujeto, igual que antes.
     */
    private fun buildReflection(
        foreground: Bitmap,
        intensity: Float,
        length: Float = 1f,
        tintIntensity: Float = 0f,
        tintColor: Int = Color.WHITE,
        edgeFade: Float = 0f,
        opacityCurve: Float = 0.5f,
        fresnel: Float = 0f
    ): Bitmap {
        val w = foreground.width
        val fullH = foreground.height
        val outH = (fullH * length.coerceIn(0.1f, 1f)).roundToInt().coerceIn(1, fullH)
        val pixels = IntArray(w * fullH)
        foreground.getPixels(pixels, 0, w, 0, 0, w, fullH)

        val applyTint = tintIntensity > 0.001f
        val tR = (tintColor ushr 16) and 0xFF
        val tG = (tintColor ushr 8) and 0xFF
        val tB = tintColor and 0xFF

        // Desvanecimiento de bordes izquierdo/derecho: un ancho de
        // rampa proporcional al ancho del sujeto (hasta 35% de cada
        // lado con el slider al máximo, así ambos lados nunca se llegan
        // a superponer ni cortan por dentro un reflejo angosto) —
        // precalculado una sola vez por columna, fuera del loop de filas,
        // porque no depende de [y].
        val edgeFadeAmount = edgeFade.coerceIn(0f, 1f)
        val rampPx = (edgeFadeAmount * w * 0.35f).roundToInt()
        val edgeFactors = if (rampPx >= 1) {
            FloatArray(w) { x ->
                when {
                    x < rampPx -> x.toFloat() / rampPx
                    x >= w - rampPx -> (w - 1 - x).toFloat() / rampPx
                    else -> 1f
                }.coerceIn(0f, 1f)
            }
        } else {
            null
        }

        val outPixels = IntArray(w * outH)
        val lastRow = (outH - 1).coerceAtLeast(1)
        // Gamma de la curva de opacidad: mapeo exponencial centrado en
        // opacityCurve=0.5 → gamma=1 (degradado lineal, EXACTAMENTE el
        // comportamiento de siempre). Por debajo de 0.5 la curva se
        // "acuesta" (gamma < 1: cae rápido apenas empieza y se estira
        // lenta hacia el final — reflejo que se apaga casi enseguida);
        // por encima de 0.5 se "empina" (gamma > 1: se mantiene fuerte
        // más tiempo y cae de golpe al final — reflejo que se sostiene
        // largo y corta abrupto).
        val gamma = Math.pow(2.0, ((opacityCurve.coerceIn(0f, 1f) - 0.5f) * 4f).toDouble())
        for (y in 0 until outH) {
            // fade: 1f en la fila pegada al sujeto (y=0), 0f en la última
            // fila del reflejo (y=outH-1, que puede ser antes de llegar a
            // la punta del sujeto si [length] < 1) — con curva gamma
            // configurable en vez de degradado lineal fijo.
            val t = y.toFloat() / lastRow
            val baseFade = Math.pow((1.0 - t), gamma).toFloat()
            // Refuerzo Fresnel: crece con t^2 (suave cerca del pie,
            // fuerte hacia el extremo lejano) — se SUMA sobre la curva
            // base y se recorta a 1 al final, así nunca "sobre-expone"
            // más allá de opacidad completa.
            val fresnelBoost = fresnel.coerceIn(0f, 1f) * (t * t) * 0.75f
            val fade = (baseFade + fresnelBoost).coerceIn(0f, 1f)
            val srcY = fullH - 1 - y // voltea verticalmente
            val srcRowStart = srcY * w
            val outRowStart = y * w
            for (x in 0 until w) {
                val p = pixels[srcRowStart + x]
                val a = (p ushr 24) and 0xFF
                val edgeFactor = edgeFactors?.get(x) ?: 1f
                val newAlpha = (a * fade * intensity * edgeFactor).roundToInt().coerceIn(0, 255)
                val rgb = if (applyTint) {
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val outR = (r * (1f - tintIntensity) + tR * tintIntensity).roundToInt().coerceIn(0, 255)
                    val outG = (g * (1f - tintIntensity) + tG * tintIntensity).roundToInt().coerceIn(0, 255)
                    val outB = (b * (1f - tintIntensity) + tB * tintIntensity).roundToInt().coerceIn(0, 255)
                    (outR shl 16) or (outG shl 8) or outB
                } else {
                    p and 0x00FFFFFF
                }
                outPixels[outRowStart + x] = (newAlpha shl 24) or rgb
            }
        }

        val result = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, outH)
        return result
    }

    /**
     * Ondulación tipo "agua": desplaza cada FILA del reflejo
     * horizontalmente según una onda seno cuya fase depende de [y] —
     * `offset(y) = amplitud * sin(y * frecuencia)` — el mismo principio
     * que un filtro de distorsión de agua de cualquier editor de imagen
     * profesional. El desplazamiento se resuelve con interpolación
     * lineal entre los dos píxeles vecinos más cercanos (sub-píxel), no
     * con un corrimiento entero, para que la onda se vea suave y no
     * "escalonada" — mismo estándar de calidad que el resto del motor
     * (ver [buildContactShadow], que usa el mismo criterio de suavidad
     * vía degradado radial real en vez de una aproximación).
     *
     * [intensity] (0..1) controla la AMPLITUD (hasta ~5% del ancho del
     * reflejo en el máximo). [scale] (0..1) controla la DENSIDAD de las
     * ondas: de 2 ondas completas a lo largo del alto (0, ondas anchas y
     * lentas) hasta 10 (1, ondas finas y rápidas).
     */
    private fun applyRipple(source: Bitmap, intensity: Float, scale: Float): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 2 || h < 1) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val amplitudePx = intensity.coerceIn(0f, 1f) * w * 0.05f
        val wavesCount = 2f + scale.coerceIn(0f, 1f) * 8f
        val freq = (2f * Math.PI.toFloat() * wavesCount) / h.toFloat()

        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = amplitudePx * sin(y * freq)
            val rowStart = y * w
            for (x in 0 until w) {
                val srcXf = x - offset
                val x0 = floor(srcXf).toInt()
                val x1 = x0 + 1
                val t = srcXf - x0
                val p0 = if (x0 in 0 until w) pixels[rowStart + x0] else 0
                val p1 = if (x1 in 0 until w) pixels[rowStart + x1] else 0
                outPixels[rowStart + x] = lerpArgb(p0, p1, t)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /** Interpolación lineal ARGB canal a canal, [t] en 0..1. */
    private fun lerpArgb(p0: Int, p1: Int, t: Float): Int {
        val tc = t.coerceIn(0f, 1f)
        val a0 = (p0 ushr 24) and 0xFF
        val r0 = (p0 ushr 16) and 0xFF
        val g0 = (p0 ushr 8) and 0xFF
        val b0 = p0 and 0xFF
        val a1 = (p1 ushr 24) and 0xFF
        val r1 = (p1 ushr 16) and 0xFF
        val g1 = (p1 ushr 8) and 0xFF
        val b1 = p1 and 0xFF
        val a = (a0 + (a1 - a0) * tc).roundToInt().coerceIn(0, 255)
        val r = (r0 + (r1 - r0) * tc).roundToInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * tc).roundToInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * tc).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Difuminado que CRECE con la distancia a la fila 0 (la fila pegada
     * al pie del sujeto, siempre la más nítida) — en vez de un radio de
     * blur único parejo para todo el bitmap, precalcula [levels] copias
     * de [source] con radios crecientes (0, 1/3, 2/3, radio completo) y
     * mezcla, fila a fila, entre las dos copias más cercanas al nivel de
     * blur que le toca a esa fila según su distancia — el mismo
     * resultado percibido que difuminar cada fila con SU PROPIO radio
     * exacto, pero a una fracción del costo (solo [levels] pasadas de
     * [boxBlur] en vez de una por fila).
     *
     * Pensado originalmente para [buildReflection] ([nearAtBottomRow] =
     * false, default): ahí [source] ya viene volteado, así que la fila 0
     * (índice bajo) es la pegada al sujeto y la última fila es el
     * extremo más lejano. La sombra proyectada principal usa la
     * orientación CONTRARIA (silueta sin voltear: el pie/punto de apoyo
     * está en la ÚLTIMA fila, no en la 0) — [nearAtBottomRow] = true
     * invierte qué extremo se toma como "pegado al sujeto" sin
     * duplicar toda la función, para reusarla tal cual también en el
     * pipeline de la sombra proyectada (ver [ImageEffectsParams.shadowContactHardening]).
     */
    private fun applyProgressiveBlur(source: Bitmap, amount: Float, nearAtBottomRow: Boolean = false): Bitmap {
        val w = source.width
        val h = source.height
        if (amount <= 0.001f || h < 2 || w < 2) return source
        val maxRadius = blurRadiusPx(amount, w, h)
        if (maxRadius < 1) return source

        val levels = 4
        val pixelsByLevel = Array(levels) { lvl ->
            val r = (maxRadius * lvl / (levels - 1f)).roundToInt()
            val bmp = if (r < 1) source else boxBlur(source, r)
            val arr = IntArray(w * h)
            bmp.getPixels(arr, 0, w, 0, 0, w, h)
            if (bmp !== source) bmp.recycle()
            arr
        }

        val outPixels = IntArray(w * h)
        val lastRow = (h - 1).coerceAtLeast(1)
        for (y in 0 until h) {
            // t=0 en la fila pegada al sujeto (nítida, nivel 0), t=1 en
            // la fila más lejana (blur máximo, último nivel) — con
            // [nearAtBottomRow] el "pegado al sujeto" es la ÚLTIMA fila
            // en vez de la primera.
            val rawT = y.toFloat() / lastRow
            val t = if (nearAtBottomRow) 1f - rawT else rawT
            val levelPos = t * (levels - 1)
            val lo = levelPos.toInt().coerceIn(0, levels - 1)
            val hi = (lo + 1).coerceAtMost(levels - 1)
            val frac = levelPos - lo
            val rowStart = y * w
            for (x in 0 until w) {
                val idx = rowStart + x
                outPixels[idx] = lerpArgb(pixelsByLevel[lo][idx], pixelsByLevel[hi][idx], frac)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Compone (mezcla) [src], transformado por [matrix], sobre el búfer
     * de píxeles [dst] (tamaño [dstW]x[dstH]) — a mano, píxel a píxel,
     * en vez de usar `Canvas.drawBitmap` + `Xfermode` nativo de Android.
     *
     * POR QUÉ A MANO: el modo `PorterDuff.Mode.MULTIPLY` "clásico" de
     * Android define alfa de salida como `Sa * Da` — es decir, donde el
     * destino todavía es transparente (alfa 0, exactamente la situación
     * de este lienzo, que arranca en blanco antes de pintar nada), el
     * resultado sería SIEMPRE invisible (alfa 0 * cualquier cosa = 0),
     * borrando la sombra en vez de oscurecerla. El `BlendMode.MULTIPLY`
     * "moderno" (el correcto, del estándar de composición de W3C) recién
     * está disponible desde Android 10 (API 29) vía `Paint.blendMode` —
     * pero este proyecto soporta desde API 26 (ver `minSdk` en
     * `app/build.gradle.kts`), así que depender de esa API rompería la
     * compatibilidad hacia atrás. La solución: implementar la fórmula
     * CORRECTA del estándar (la misma que usa `BlendMode.MULTIPLY`) a
     * mano con aritmética de píxeles simple, que YA es el patrón que usa
     * el resto de este archivo (ver [boxBlur], [tintedSilhouette]) — sin
     * depender de ninguna versión de Android en particular.
     *
     * La fórmula (con colores SIN premultiplicar, alfas 0..1):
     * ```
     * outA = Sa + Da*(1-Sa)
     * blend(Cs,Cd) = Cs      (modo NORMAL)
     *              = Cs * Cd (modo MULTIPLICAR)
     * Co = Sa*(1-Da)*Cs + Da*(1-Sa)*Cd + Sa*Da*blend(Cs,Cd)
     * Cout = Co / outA   (des-premultiplicar para guardar en ARGB recto)
     * ```
     * Esta fórmula, con `Da=0` (destino vacío), se reduce exactamente a
     * `Cout = Cs`, `outA = Sa` — o sea, el resultado es IDÉNTICO al modo
     * NORMAL cuando no hay nada debajo, sin importar si `multiply` está
     * activo o no. La diferencia solo aparece (a propósito) donde dos
     * capas de esta misma familia de sombras se superponen entre sí.
     */
    private fun blitBlend(
        dst: IntArray,
        dstW: Int,
        dstH: Int,
        src: Bitmap,
        matrix: Matrix,
        globalAlpha: Float,
        multiply: Boolean
    ) {
        val ga = globalAlpha.coerceIn(0f, 1f)
        if (ga <= 0.001f) return
        val srcW = src.width
        val srcH = src.height
        if (srcW < 1 || srcH < 1) return
        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val inverse = Matrix()
        if (!matrix.invert(inverse)) return

        // Caja delimitadora del [src] YA transformado, en espacio de
        // destino — así el recorrido solo toca los píxeles que
        // realmente puede afectar, en vez de barrer TODO [dst].
        val corners = floatArrayOf(0f, 0f, srcW.toFloat(), 0f, srcW.toFloat(), srcH.toFloat(), 0f, srcH.toFloat())
        matrix.mapPoints(corners)
        var minX = corners[0]
        var maxX = corners[0]
        var minY = corners[1]
        var maxY = corners[1]
        for (i in 1 until 4) {
            val x = corners[i * 2]
            val y = corners[i * 2 + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val startX = kotlin.math.floor(minX).toInt().coerceIn(0, dstW)
        val endX = kotlin.math.ceil(maxX).toInt().coerceIn(0, dstW)
        val startY = kotlin.math.floor(minY).toInt().coerceIn(0, dstH)
        val endY = kotlin.math.ceil(maxY).toInt().coerceIn(0, dstH)
        if (startX >= endX || startY >= endY) return

        val srcPt = FloatArray(2)
        for (dy in startY until endY) {
            for (dx in startX until endX) {
                srcPt[0] = dx + 0.5f
                srcPt[1] = dy + 0.5f
                inverse.mapPoints(srcPt)
                val sx = srcPt[0].toInt()
                val sy = srcPt[1].toInt()
                if (sx < 0 || sx >= srcW || sy < 0 || sy >= srcH) continue
                val sp = srcPixels[sy * srcW + sx]
                val sA = ((sp ushr 24) and 0xFF) / 255f * ga
                if (sA <= 0.001f) continue
                val sR = ((sp ushr 16) and 0xFF) / 255f
                val sG = ((sp ushr 8) and 0xFF) / 255f
                val sB = (sp and 0xFF) / 255f

                val dstIdx = dy * dstW + dx
                val dp = dst[dstIdx]
                val dA = ((dp ushr 24) and 0xFF) / 255f
                val dR = ((dp ushr 16) and 0xFF) / 255f
                val dG = ((dp ushr 8) and 0xFF) / 255f
                val dB = (dp and 0xFF) / 255f

                val outA = sA + dA * (1f - sA)
                if (outA <= 0.001f) {
                    dst[dstIdx] = 0
                    continue
                }

                val coR: Float
                val coG: Float
                val coB: Float
                if (multiply) {
                    coR = sA * (1f - dA) * sR + dA * (1f - sA) * dR + sA * dA * (sR * dR)
                    coG = sA * (1f - dA) * sG + dA * (1f - sA) * dG + sA * dA * (sG * dG)
                    coB = sA * (1f - dA) * sB + dA * (1f - sA) * dB + sA * dA * (sB * dB)
                } else {
                    coR = sA * sR + dA * (1f - sA) * dR
                    coG = sA * sG + dA * (1f - sA) * dG
                    coB = sA * sB + dA * (1f - sA) * dB
                }

                val outR = (coR / outA).coerceIn(0f, 1f)
                val outG = (coG / outA).coerceIn(0f, 1f)
                val outB = (coB / outA).coerceIn(0f, 1f)

                val outAlphaInt = (outA * 255f).roundToInt().coerceIn(0, 255)
                val outRInt = (outR * 255f).roundToInt().coerceIn(0, 255)
                val outGInt = (outG * 255f).roundToInt().coerceIn(0, 255)
                val outBInt = (outB * 255f).roundToInt().coerceIn(0, 255)
                dst[dstIdx] = (outAlphaInt shl 24) or (outRInt shl 16) or (outGInt shl 8) or outBInt
            }
        }
    }

    /**
     * Mancha de sombra de CONTACTO: un óvalo aplanado con caída radial
     * suave (centro opaco → borde transparente), pensado para pegarse
     * justo debajo del pie del sujeto — el recurso que usa cualquier
     * motor de render o compositing profesional para "anclar" un objeto
     * al piso además de su sombra proyectada larga. Se resuelve con un
     * `RadialGradient` circular y luego se aplana verticalmente con
     * `Canvas.scale` (en vez de dibujar una elipse con degradado propio,
     * que Android no soporta nativamente) — resultado idéntico a una
     * elipse con degradado radial real, sin aproximaciones visibles.
     *
     * [size] (0.1..1) es el ancho de la mancha relativo al ancho del
     * sujeto ([subjectWidth]); el alto queda fijo en 32% del ancho
     * (proporción "sombra de piso" estándar). [color] es el color propio
     * de esta sombra (independiente de la proyectada principal).
     *
     * [falloff] (0..1) controla la CURVA del degradado, no solo sus dos
     * puntas: en vez del único degradado lineal 0%→100% de antes, se
     * arma un `RadialGradient` de múltiples paradas con una curva
     * gamma (`alpha(t) = (1-t)^gamma`), donde `gamma` sube con
     * [falloff] — 0 da una caída lenta y extendida (gamma bajo, mancha
     * "difusa" de punta a punta) y 1 da una caída rápida y concentrada
     * (gamma alto, núcleo denso que corta antes) — el control de curva
     * de opacidad estándar de cualquier herramienta de sombra de
     * contacto/oclusión ambiental profesional.
     */
    private fun buildContactShadow(subjectWidth: Int, size: Float, color: Int, falloff: Float = 0.5f): Bitmap {
        // Rango ampliado a 0.05..2 (antes 0.1..1): con la llegada de
        // [ContactShadowPoint.sizeScale] (hasta 2x) un punto individual
        // puede necesitar una mancha bastante más grande — o más chica —
        // que el 100% del ancho del sujeto (p.ej. la pata de una silla
        // más ancha que el propio objeto que la sostiene), así que el
        // límite ya no puede quedar atado al 1.0 "clásico" de una única
        // mancha centrada.
        val ovalWidth = (subjectWidth * size.coerceIn(0.05f, 2f)).roundToInt().coerceAtLeast(4)
        val ovalHeight = (ovalWidth * 0.32f).roundToInt().coerceAtLeast(2)
        val pad = (ovalHeight * 0.6f).roundToInt() + 2
        val bw = ovalWidth + pad * 2
        val bh = ovalHeight + pad * 2

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = bw / 2f
        val cy = bh / 2f
        val radius = ovalWidth / 2f

        // BUG REAL encontrado y corregido acá: esta línea usaba una
        // interpolación LINEAL de falloff -> gamma (`0.4 + falloff*2.8`),
        // que va de 0.4 (falloff=0) a 3.2 (falloff=1). El propio
        // comentario de arriba prometía que el valor por defecto
        // (falloff=0.5) daba "gamma ≈ 1, equivalente al degradado lineal
        // simple que tenía esta función antes" — pero con esa fórmula
        // lineal, falloff=0.5 en realidad da gamma=0.4+0.5*2.8=1.8, NO 1
        // (1 no es el punto medio aritmético de [0.4, 3.2]: hay 0.6 de
        // distancia hacia abajo y 2.2 hacia arriba). El resultado real:
        // cualquier mancha de sombra de contacto con el falloff en su
        // valor por defecto salía con una caída notoriamente más
        // concentrada/dura que el degradado lineal clásico que
        // supuestamente reemplazaba, rompiendo en silencio el mismo
        // criterio de "el valor por defecto reproduce el comportamiento
        // de siempre" que se respeta en TODO el resto del archivo (ver
        // p.ej. [applyVerticalFade] o el gamma de [buildReflection], que
        // sí usan el mapeo exponencial de abajo y sí dan gamma=1 exacto
        // en su punto medio). Se reemplaza por el MISMO mapeo gamma
        // (potencia de 2 centrada en 0.5) que ya usan esas otras curvas
        // de opacidad de este archivo — así falloff=0.5 vuelve a dar
        // gamma=1 exacto (degradado lineal, comportamiento clásico real),
        // por debajo cae más lento/extendido y por encima más rápido/
        // concentrado, tal como describe el comentario original.
        val gamma = Math.pow(2.0, ((falloff.coerceIn(0f, 1f) - 0.5f) * 4f).toDouble())
        val stopCount = 10
        val colors = IntArray(stopCount + 1)
        val positions = FloatArray(stopCount + 1)
        val cr = (color ushr 16) and 0xFF
        val cg = (color ushr 8) and 0xFF
        val cb = color and 0xFF
        for (i in 0..stopCount) {
            val t = i / stopCount.toFloat()
            positions[i] = t
            val alpha = (255f * Math.pow((1.0 - t).toDouble(), gamma.toDouble())).roundToInt().coerceIn(0, 255)
            colors[i] = (alpha shl 24) or (cr shl 16) or (cg shl 8) or cb
        }
        val shader = RadialGradient(cx, cy, radius, colors, positions, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }

        canvas.save()
        canvas.scale(1f, ovalHeight / ovalWidth.toFloat(), cx, cy)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()

        return bmp
    }

    /**
     * Desenfoque de caja (box blur) de 3 pasadas horizontal+vertical —
     * aproxima muy bien un desenfoque gaussiano real a una fracción del
     * costo, con ventana deslizante O(ancho*alto) por pasada (no
     * O(ancho*alto*radio)), así que sigue siendo rápido incluso con
     * radios grandes sobre la copia en alta resolución. Se eligió esto en
     * vez de RenderScript (deprecado/retirado en Android moderno) para no
     * atar el efecto a una API que ya no tiene soporte a futuro.
     *
     * Cada canal (incluido alfa) se difumina por separado sobre valores
     * NO premultiplicados (así entrega/recibe `Bitmap.getPixels`), que es
     * el mismo enfoque que ya usa el resto de este archivo.
     */
    fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return source
        val w = source.width
        val h = source.height
        if (w < 2 || h < 2) return source

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // 3 pasadas con radios que se reparten el radio total pedido —
        // el resultado final se percibe como un desenfoque más suave/
        // gaussiano que una única pasada de caja con el radio completo.
        val passRadius = max(1, radius / 3)
        repeat(3) {
            boxBlurHorizontal(pixels, w, h, passRadius)
            boxBlurVertical(pixels, w, h, passRadius)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlurHorizontal(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        val rowOut = IntArray(w)
        for (y in 0 until h) {
            val rowStart = y * w
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (dx in -radius..radius) {
                val xx = dx.coerceIn(0, w - 1)
                val p = pixels[rowStart + xx]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            for (x in 0 until w) {
                rowOut[x] = ((sumA / div) shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div)
                val addX = (x + radius + 1).coerceIn(0, w - 1)
                val subX = (x - radius).coerceIn(0, w - 1)
                val addP = pixels[rowStart + addX]
                val subP = pixels[rowStart + subX]
                sumA += ((addP ushr 24) and 0xFF) - ((subP ushr 24) and 0xFF)
                sumR += ((addP ushr 16) and 0xFF) - ((subP ushr 16) and 0xFF)
                sumG += ((addP ushr 8) and 0xFF) - ((subP ushr 8) and 0xFF)
                sumB += (addP and 0xFF) - (subP and 0xFF)
            }
            System.arraycopy(rowOut, 0, pixels, rowStart, w)
        }
    }

    private fun boxBlurVertical(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        val colOut = IntArray(h)
        for (x in 0 until w) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (dy in -radius..radius) {
                val yy = dy.coerceIn(0, h - 1)
                val p = pixels[yy * w + x]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }
            for (y in 0 until h) {
                colOut[y] = ((sumA / div) shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div)
                val addY = (y + radius + 1).coerceIn(0, h - 1)
                val subY = (y - radius).coerceIn(0, h - 1)
                val addP = pixels[addY * w + x]
                val subP = pixels[subY * w + x]
                sumA += ((addP ushr 24) and 0xFF) - ((subP ushr 24) and 0xFF)
                sumR += ((addP ushr 16) and 0xFF) - ((subP ushr 16) and 0xFF)
                sumG += ((addP ushr 8) and 0xFF) - ((subP ushr 8) and 0xFF)
                sumB += (addP and 0xFF) - (subP and 0xFF)
            }
            for (y in 0 until h) {
                pixels[y * w + x] = colOut[y]
            }
        }
    }
}
