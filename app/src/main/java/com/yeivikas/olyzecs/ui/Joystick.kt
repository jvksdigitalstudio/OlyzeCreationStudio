package com.yeivikas.olyzecs.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.ui.theme.BrandPurple
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

// --- Geometría del ARO/mando visibles (no de la zona de activación, que es
// "todo lo que mida el `modifier` que le pasen" — ver doc más abajo). ---
private val JOYSTICK_OUTER_SIZE = 108.dp
private val JOYSTICK_INNER_SIZE = 64.dp

// --- Margen de "respiro" alrededor del aro dentro del lienzo que lo dibuja.
// CLAVE del arreglo del recorte: antes, el mando vivía en un Box hijo
// anidado DENTRO de un Box padre con tamaño fijo exacto (108dp) — cualquier
// límite de arrastre mal calculado que empujara al mando más allá de esos
// 108dp lo cortaba en seco contra el borde de ese padre (el corte plano que
// se veía en la captura). Ahora TODO —aro, marcas, sombra y mando— se pinta
// en un único [Canvas] bastante más grande que el aro en sí, así que aunque
// algo se acerque al límite jamás choca contra el borde de su propio
// contenedor. Es cinturón Y tirantes: acá abajo también se corrigió el
// cálculo de [maxDragPx] para que el mando ni siquiera necesite ese
// respiro — nunca debería salirse del aro — pero el margen se deja igual,
// a propósito, como red de seguridad ante cualquier cálculo futuro que se
// vuelva a pasar.
//
// BUG #4 (encontrado y corregido acá) — "el joystick se sale de su propia
// zona de Control": el arreglo de arriba resuelve el recorte del MANDO
// contra el ARO, pero dejó un descuido distinto en el otro extremo: dónde
// puede "nacer" el aro dentro de la ZONA (el rectángulo de Control que
// define quien llama, ver [EditorBottomBar]). Ese nacimiento se empuja
// hacia adentro con `coerceIn(outerRadiusPx, ...)` — es decir, usando el
// radio del ARO VISIBLE como margen de seguridad respecto del borde de la
// zona. Pero lo que en verdad se dibuja no es un círculo de radio
// `outerRadiusPx`: es este [Canvas], de radio `canvasHalfPx` (aro +
// [JOYSTICK_CANVAS_MARGIN] a cada lado, para el halo). Como `Box` no
// recorta a sus hijos por defecto en Compose, ese excedente — hasta
// [JOYSTICK_CANVAS_MARGIN] de verdad, y el halo puede llegar a
// sobresalir aún más cerca del borde — se pintaba fuera de la zona de
// Control sin que nada lo detuviera, exactamente lo reportado ("el
// joystick sale de su ventana de control"). El `coerceIn` de más abajo
// ahora usa `canvasHalfPx` (el radio real de lo que se dibuja), no
// `outerRadiusPx`, así el nacimiento del joystick deja el margen correcto
// para que TODO lo pintado —halo incluido— quepa dentro de la zona. Y,
// como red de seguridad adicional ante cualquier zona demasiado angosta
// o cualquier cálculo futuro que se vuelva a pasar, el `Box` de la zona
// ahora también lleva `Modifier.clipToBounds()` (ver más abajo): recorte
// físico y duro a los propios límites del composable, así nada de lo que
// se dibuje acá adentro puede escapar de su zona de Control pase lo que
// pase con la aritmética de arriba.
private val JOYSTICK_CANVAS_MARGIN = 22.dp
private val JOYSTICK_CANVAS_SIZE = JOYSTICK_OUTER_SIZE + JOYSTICK_CANVAS_MARGIN * 2

// --- Tuning de "feel" profesional del stick, calibrado para sentirse
// como los joysticks táctiles de shooters móviles AAA (COD Mobile / PUBG
// Mobile) en vez de un mapeo lineal ingenuo dedo -> velocidad:
//
//  1) DEADZONE radial: el ruido normal de cualquier pantalla táctil (el
//     dedo "tiembla" unos px incluso quieto) se traducía antes en
//     micro-valores de dirección != 0 que hacían que la capa "vibrara"
//     sola con el stick aparentemente centrado. Se ignora todo lo que
//     caiga dentro de este % del radio, y el resto se REESCALA (no se
//     recorta) para que el rango útil siga yendo de 0 a 1 sin un salto
//     brusco justo después del borde de la deadzone.
//  2) CURVA de respuesta: sin curva, la mitad del recorrido del stick ya
//     manda la mitad de la velocidad máxima — demasiado sensible para
//     ajustes finos de encuadre y sin margen para "acelerar" hacia el
//     borde. Elevar la magnitud (0..1, YA normalizada y YA pasada por la
//     deadzone) a este exponente da más resolución cerca del centro
//     (precisión fina) y reserva la velocidad máxima real para cuando el
//     stick está a fondo — la misma idea que la curva de un stick
//     analógico de consola.
//
// Ninguno de los dos toca la posición VISUAL del mando dentro del aro —
// esa sigue 1:1 con el dedo, tal cual espera ver el usuario. Se aplican
// SOLO al vector que sale por [onDirectionChange], que es lo que
// efectivamente mueve la capa.
private const val JOYSTICK_DEADZONE = 0.05f
private const val JOYSTICK_CURVE_EXPONENT = 1.5f

/**
 * Joystick táctil DINÁMICO/FLOTANTE, estilo shooter móvil moderno (COD
 * Mobile / PUBG Mobile / Fortnite) — NO estilo joystick clásico fijo (el
 * viejo diseño tipo GTA San Andreas Android, con el aro siempre dibujado
 * quieto en un rincón de la pantalla, quedó descartado a pedido explícito
 * por sentirse "retro" frente a cómo funcionan los joysticks táctiles hoy).
 *
 * Comportamiento real:
 *  - El aro NO se dibuja en reposo — invisible hasta que el dedo toca.
 *  - La ZONA de activación es del tamaño que ocupe este composable en el
 *    layout (ver [modifier] — quien llama decide el tamaño: un rectángulo
 *    acotado, `fillMaxSize()`, etc.; acá no hay ningún tamaño de zona
 *    hardcodeado). En [EditorBottomBar] esto se usa con un rectángulo
 *    ACOTADO — no todo el panel "Control": aprox. la mitad izquierda/
 *    inferior, dejando libre arriba (el menú ☰) y a la derecha (reservado
 *    para futuros íconos del panel).
 *  - Cualquier toque que EMPIECE dentro de la zona hace aparecer el
 *    joystick centrado exactamente en el punto donde tocó el dedo (con un
 *    empuje mínimo hacia adentro si el toque cae pegadísimo a un borde de
 *    la zona, para que el aro se dibuje siempre completo).
 *  - Mientras el dedo se mantiene apretado, el mando interior se arrastra
 *    dentro de ese aro — el LÍMITE de recorrido es exactamente
 *    `radioExterior - radioMando` (ver [maxDragPx]): así el borde del
 *    mando queda tangente al borde interior del aro en la deflexión
 *    máxima, ni un píxel más allá — igual que cualquier joystick análogo
 *    real. (La versión anterior usaba un cálculo mal pensado que dejaba
 *    al mando viajar de más y terminaba recortado contra su propio
 *    contenedor — ver [JOYSTICK_CANVAS_MARGIN] para el detalle del bug.)
 *  - Al soltar: el mando vuelve al centro con resorte y todo el conjunto
 *    se desvanece — vuelve a quedar invisible, listo para el próximo toque.
 *  - Aparición con una animación de "pop" (los propios radios dibujados
 *    crecen desde ~72% hasta 100%, en vez de una escala de layout — ver
 *    comentario sobre por qué se evitó `Modifier.scale` acá abajo) y un
 *    pulso háptico corto al tocar.
 *
 * DISEÑO VISUAL — nivel "premium", en la paleta de marca de la app (mismo
 * morado que el resto de la UI, ver `ui/theme/Theme.kt`), no un blanco/negro
 * genérico:
 *  - Halo suave detrás del aro (varios círculos concéntricos con alpha
 *    decreciente — simula un glow/blur sin depender de `RenderEffect`,
 *    que recién existe desde API 31, para que se vea bien en cualquier
 *    versión mínima que soporte este proyecto).
 *  - Aro con relleno morado profundo translúcido + borde morado vivo +
 *    un anillo interior sutil para dar profundidad.
 *  - 4 marcas de dirección (N/E/S/O) pegadas por dentro del borde — detalle
 *    tipo HUD de juego, sutil, no compite visualmente con el mando.
 *  - Mando con sombra propia (óvalo oscuro levemente desplazado, ANTES de
 *    dibujarlo) para dar sensación de volumen, y gradiente radial con
 *    highlight arriba-izquierda (simula una fuente de luz) degradando de
 *    blanco a los morados de marca, con filo definido.
 *
 * IMPORTANTE — convivencia con otros controles del mismo panel (botón ✕,
 * menú "Control", futuros íconos, etc.): este composable debe declararse
 * PRIMERO (más "abajo" en el orden de un `Box`) respecto de cualquier otro
 * control interactivo que conviva en ese mismo `Box` — en Compose, los
 * hijos declarados DESPUÉS quedan por encima tanto en dibujo como en
 * prioridad de toque, así que esos otros controles siguen consumiendo sus
 * propios toques con normalidad. Ver el comentario en [EditorBottomBar]
 * donde se llama.
 *
 * Expone [onDirectionChange] con el vector normalizado (-1..1 en cada eje,
 * 0,0 = centro/soltado/oculto). Este composable SOLO reporta el vector — no
 * sabe nada de capas, velocidad ni física de movimiento; quien lo llama es
 * responsable de traducirlo en movimiento real. Este desacople es a
 * propósito: este archivo se puede leer, tocar y probar sin saber nada del
 * editor.
 *
 * Por qué NO se usa `Modifier.scale`/`Modifier.graphicsLayer` para la
 * animación de "pop": aunque su `clip` por defecto es `false`, meter el
 * mando en capas de layout separadas (Box hijo con su propio tamaño fijo,
 * offset y escala) fue justamente lo que produjo el bug del recorte — un
 * único `Canvas` que calcula sus propios radios a mano es más simple de
 * razonar, más barato de recomponer, y por construcción no puede recortar
 * nada contra un borde de layout que no existe.
 *
 * Manejo de puntero de bajo nivel (sin el "touch slop" de
 * [androidx.compose.foundation.gestures.detectDragGestures], que espera
 * a que el dedo supere un umbral de unos pocos dp antes de reportar el
 * primer movimiento — inaceptable en un joystick): se usa
 * `awaitEachGesture` + lectura directa de posiciones. Todas las escrituras
 * de `Animatable` (`snapTo`/`animateTo`) se despachan con
 * `scope.launch { }` porque el bloque de `awaitPointerEvent` corre bajo
 * `AwaitPointerEventScope` (`@RestrictsSuspension`): ahí adentro solo se
 * pueden invocar funciones `suspend` que sean miembro/extensión de ESE
 * receptor puntual, y `Animatable.snapTo`/`animateTo` no lo son. `launch`
 * en sí NO es `suspend`, así que llamarlo desde acá adentro es legal. Las
 * escrituras de `origin` (estado de Compose común, no `Animatable`) sí son
 * legales directo en ese bloque, por eso no llevan `launch`.
 */
@Composable
fun GtaStyleJoystick(
    modifier: Modifier = Modifier,
    onDirectionChange: (x: Float, y: Float) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    val outerRadiusPx = with(density) { (JOYSTICK_OUTER_SIZE / 2).toPx() }
    val innerRadiusPx = with(density) { (JOYSTICK_INNER_SIZE / 2).toPx() }
    val canvasSizePx = with(density) { JOYSTICK_CANVAS_SIZE.toPx() }
    val canvasHalfPx = canvasSizePx / 2f
    // Tope de recorrido del mando: EXACTO hasta que su propio borde toque
    // el borde interior del aro — ni un píxel más. Ver el comentario
    // grande de la función para el porqué de este cálculo (y del bug
    // anterior que usaba `innerRadiusPx * 0.35f`, dejando margen de sobra
    // para que el mando se saliera del aro y se recortara).
    val maxDragPx = (outerRadiusPx - innerRadiusPx).coerceAtLeast(1f)

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    // Punto (relativo a la zona) donde nació el joystick en este toque —
    // `null` = no hay ningún toque activo ahora mismo, así que no se dibuja
    // nada.
    var origin by remember { mutableStateOf<Offset?>(null) }
    // "Presencia" del conjunto — impulsa tanto el fade (vía
    // `Modifier.alpha`, seguro: no recorta nada) como el "pop" de tamaño,
    // que acá se aplica multiplicando los RADIOS dentro del propio
    // `Canvas`, no vía escala de layout.
    val presence = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    fun hide() {
        scope.launch { offsetX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        scope.launch { offsetY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        scope.launch {
            presence.animateTo(0f, tween(durationMillis = 140))
            // Recién al terminar el fade se limpia `origin` — así no hay un
            // "salto" visible del aro a otra posición antes de desvanecerse.
            origin = null
        }
        onDirectionChange(0f, 0f)
    }

    Box(
        modifier = modifier
            // Red de seguridad final (ver "BUG #4" arriba): recorta CUALQUIER
            // dibujo de este composable a sus propios límites de layout —
            // que son, exactamente, la zona de Control que define quien
            // llama. Con el `coerceIn` de acá abajo ya corregido esto no
            // debería activarse nunca en el uso normal, pero si alguna vez
            // la zona termina siendo más angosta que `JOYSTICK_CANVAS_SIZE`,
            // o algún cálculo futuro se vuelve a pasar, esto garantiza que
            // el joystick jamás vuelva a pintarse fuera de su panel.
            .clipToBounds()
            .pointerInput(Unit) {
                // Sigue UN dedo por vez desde que toca hasta que se levanta
                // o cancela; cualquier otro dedo que toque mientras tanto
                // se ignora.
                awaitEachGesture {
                    var down: PointerInputChange? = null
                    while (down == null) {
                        val firstEvent = awaitPointerEvent(pass = PointerEventPass.Main)
                        down = firstEvent.changes.firstOrNull { it.pressed }
                    }
                    down.consume()

                    // `size` = tamaño en px de ESTE composable (la ZONA de
                    // activación, medida por Compose). El joystick nace
                    // centrado en el punto exacto donde tocó el dedo;
                    // coerceIn solo empuja ese centro hacia adentro lo
                    // justo y necesario si el toque cayó pegadísimo a un
                    // borde de la zona, así el aro se dibuja SIEMPRE
                    // completo, nunca recortado por el borde del panel.
                    //
                    // El margen usado acá es `canvasHalfPx` — el radio real
                    // de TODO lo que se pinta (aro + halo), no
                    // `outerRadiusPx` (solo el aro visible) — ver el
                    // comentario de "BUG #4" en [JOYSTICK_CANVAS_MARGIN]
                    // más arriba para el porqué: usar un margen más chico
                    // que lo que en verdad se dibuja es justo lo que dejaba
                    // que el joystick naciera pegado al borde de la zona y
                    // sobresaliera fuera de ella.
                    origin = Offset(
                        down.position.x.coerceIn(canvasHalfPx, (size.width - canvasHalfPx).coerceAtLeast(canvasHalfPx)),
                        down.position.y.coerceIn(canvasHalfPx, (size.height - canvasHalfPx).coerceAtLeast(canvasHalfPx))
                    )
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        // BUG #3 (corregido) — "mando nace descentrado en un
                        // re-toque rápido": si el dedo suelta y vuelve a
                        // tocar ANTES de que termine el resorte de
                        // `hide()` que lleva offsetX/offsetY de vuelta a
                        // 0, este nuevo gesto arrancaba sumando
                        // `dragAmount` sobre lo que quedara de esa
                        // animación a mitad de camino (`offsetX.value` /
                        // `offsetY.value` todavía != 0) — el mando
                        // aparecía ya desplazado del centro del aro nuevo,
                        // sin que el dedo se hubiera movido un solo px.
                        // `snapTo(0f)` es la corrección: al ser el mismo
                        // `Animatable` que usa `hide()`, este `snapTo`
                        // cancela automáticamente cualquier
                        // `animateTo(0f, ...)` de un `hide()` anterior
                        // todavía en vuelo (semántica estándar de
                        // `Animatable`: una nueva escritura interrumpe la
                        // animación en curso) y deja el valor en 0 de
                        // forma inmediata y determinística, sin depender
                        // de si esa animación anterior llegó a tiempo.
                        offsetX.snapTo(0f)
                        offsetY.snapTo(0f)
                        presence.snapTo(0f)
                        presence.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f))
                    }

                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null) {
                            // BUG #1 (corregido) — "joystick atascado en
                            // pantalla": el puntero que veníamos siguiendo
                            // puede desaparecer de `event.changes` sin
                            // pasar nunca por `pressed = false` — pasa
                            // cuando otro detector de gestos más arriba en
                            // el árbol (scroll, otro `pointerInput`, un
                            // diálogo del sistema, etc.) cancela o "roba"
                            // el puntero a mitad de gesto. El código
                            // anterior hacía `?: break` acá mismo — salía
                            // del loop SIN llamar a `hide()`, dejando
                            // `origin` != null y `presence` en 1 para
                            // siempre: el aro y el mando quedaban
                            // dibujados, congelados, en el último punto
                            // conocido, y el composable ya no reaccionaba
                            // a nada hasta recomponerse desde cero. Ahora
                            // una desaparición del puntero se trata
                            // EXACTAMENTE igual que un soltar real.
                            hide()
                            break
                        }
                        // BUG #2 (corregido) — "el joystick puede perder
                        // el dedo en pleno gesto": antes solo se
                        // consumía el evento cuando había movimiento
                        // (`dragAmount != Offset.Zero`) o al soltar. Un
                        // toque sostenido SIN mover el dedo ni un solo
                        // frame dejaba ese evento sin consumir, libre
                        // para que un ancestro (por ejemplo un
                        // `Modifier.scrollable`/`draggable` del panel que
                        // contiene este joystick) lo interceptara a mitad
                        // de gesto — y eso es precisamente lo que dispara
                        // el BUG #1 de arriba en la práctica. Mientras
                        // este puntero sea "nuestro" (llegamos hasta acá
                        // porque el `id` coincide), se consume SIEMPRE,
                        // se mueva o no.
                        //
                        // REGRESIÓN CRÍTICA introducida en la vuelta
                        // anterior (y ya corregida acá) — `change.consume()`
                        // estaba llamándose ACÁ, ANTES de leer
                        // `positionChange()`. `positionChange()` en Compose
                        // devuelve `Offset.Zero` si el `change` YA está
                        // marcado como consumido en el momento en que se lo
                        // consulta (es la forma que tiene Compose de decir
                        // "este movimiento ya fue manejado, no lo proceses
                        // de nuevo") — así que consumir primero dejaba
                        // CIEGO a todo el resto del bloque: `dragAmount`
                        // daba `Offset.Zero` SIEMPRE, sin importar cuánto
                        // se moviera el dedo de verdad. Resultado exacto de
                        // lo reportado: el aro aparecía al tocar (eso no
                        // depende de `positionChange()`) pero ni el mando
                        // visual ni la capa se movían jamás, porque el
                        // delta que alimenta a ambos ya llegaba muerto. El
                        // orden correcto es: LEER el delta primero, y
                        // consumir DESPUÉS.
                        val dragAmount = change.positionChange()
                        change.consume()
                        if (!change.pressed) {
                            hide()
                            break
                        }
                        if (dragAmount != Offset.Zero) {
                            val nextX = offsetX.value + dragAmount.x
                            val nextY = offsetY.value + dragAmount.y
                            val dist = sqrt(nextX * nextX + nextY * nextY)
                            val scaleToLimit = if (dist > maxDragPx) maxDragPx / dist else 1f
                            val clampedX = nextX * scaleToLimit
                            val clampedY = nextY * scaleToLimit
                            // El mando VISUAL sigue siempre 1:1 al dedo — es
                            // el vector que reportamos el que recibe el
                            // tratamiento profesional (deadzone + curva),
                            // ver comentario de [JOYSTICK_DEADZONE] arriba.
                            scope.launch {
                                offsetX.snapTo(clampedX)
                                offsetY.snapTo(clampedY)
                            }

                            // `clampedDist` es la magnitud ya recortada al
                            // aro (== dist si no llegó al borde). Se separa
                            // dirección (unitaria) de magnitud para poder
                            // curvar SOLO la magnitud sin distorsionar el
                            // ángulo del empuje.
                            val clampedDist = dist.coerceAtMost(maxDragPx)
                            val rawMagnitude = (clampedDist / maxDragPx).coerceIn(0f, 1f)
                            val dirX = if (clampedDist > 0.0001f) clampedX / clampedDist else 0f
                            val dirY = if (clampedDist > 0.0001f) clampedY / clampedDist else 0f

                            // 1) Deadzone radial reescalada: todo lo que cae
                            // dentro del [JOYSTICK_DEADZONE] reporta 0 en
                            // limpio (sin "fantasmas" de dirección con el
                            // stick aparentemente centrado); lo que queda
                            // por fuera se re-mapea a 0..1 para no perder
                            // rango útil.
                            val afterDeadzone = if (rawMagnitude <= JOYSTICK_DEADZONE) {
                                0f
                            } else {
                                ((rawMagnitude - JOYSTICK_DEADZONE) / (1f - JOYSTICK_DEADZONE))
                                    .coerceIn(0f, 1f)
                            }

                            // 2) Curva de respuesta: más precisión cerca del
                            // centro, velocidad máxima reservada para el
                            // stick a fondo.
                            val curvedMagnitude = afterDeadzone.pow(JOYSTICK_CURVE_EXPONENT)

                            onDirectionChange(
                                (dirX * curvedMagnitude).coerceIn(-1f, 1f),
                                (dirY * curvedMagnitude).coerceIn(-1f, 1f)
                            )
                        }
                        pointerId = change.id
                    }
                }
            }
    ) {
        val currentOrigin = origin ?: return@Box

        Canvas(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (currentOrigin.x - canvasHalfPx).roundToInt(),
                        (currentOrigin.y - canvasHalfPx).roundToInt()
                    )
                }
                .size(JOYSTICK_CANVAS_SIZE)
                .alpha(presence.value)
        ) {
            val cx = canvasHalfPx
            val cy = canvasHalfPx
            // "Pop" de aparición: los radios arrancan un poco más chicos y
            // crecen hasta su tamaño real — calculado a mano acá, no vía
            // escala de layout (ver comentario grande de la función).
            val pop = 0.72f + 0.28f * presence.value
            val ringRadius = outerRadiusPx * pop
            val knobRadius = innerRadiusPx * pop
            val knobCenter = Offset(cx + offsetX.value * pop, cy + offsetY.value * pop)

            // Halo suave detrás del aro — círculos concéntricos con alpha
            // decreciente, simulan un glow sin RenderEffect.
            for (step in 4 downTo 1) {
                drawCircle(
                    color = BrandPurpleLight.copy(alpha = 0.05f * step),
                    radius = ringRadius + step * 5.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }

            // Base del aro — morado profundo de marca, no negro genérico.
            drawCircle(
                color = BrandPurpleDeep.copy(alpha = 0.62f),
                radius = ringRadius,
                center = Offset(cx, cy)
            )
            // Borde principal, morado vivo de marca.
            drawCircle(
                color = BrandPurpleLight.copy(alpha = 0.85f),
                radius = ringRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Anillo interior sutil, un paso más adentro, para profundidad.
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = ringRadius - 6.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )

            // Marcas de dirección N/E/S/O pegadas por dentro del borde —
            // detalle tipo HUD, sutil.
            val tickLen = 6.dp.toPx()
            val tickInset = ringRadius - 10.dp.toPx()
            for (angleDeg in intArrayOf(0, 90, 180, 270)) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val dirX = sin(rad).toFloat()
                val dirY = -cos(rad).toFloat()
                drawLine(
                    color = BrandPurpleLight.copy(alpha = 0.4f),
                    start = Offset(cx + dirX * tickInset, cy + dirY * tickInset),
                    end = Offset(cx + dirX * (tickInset + tickLen), cy + dirY * (tickInset + tickLen)),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Sombra del mando — óvalo oscuro levemente desplazado, pintado
            // ANTES del mando en sí: da sensación de volumen/elevación.
            drawCircle(
                color = Color.Black.copy(alpha = 0.30f),
                radius = knobRadius * 0.94f,
                center = Offset(knobCenter.x + 1.5.dp.toPx(), knobCenter.y + 2.5.dp.toPx())
            )

            // Mando — gradiente radial con highlight arriba-izquierda
            // (simula una fuente de luz), degradando de blanco a los
            // morados de marca, con filo definido. Mucho más "premium"
            // que el blanco liso de la versión anterior.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, BrandPurpleLight, BrandPurple),
                    center = Offset(knobCenter.x - knobRadius * 0.35f, knobCenter.y - knobRadius * 0.35f),
                    radius = knobRadius * 1.6f
                ),
                radius = knobRadius,
                center = knobCenter
            )
            drawCircle(
                color = BrandPurpleDeep.copy(alpha = 0.55f),
                radius = knobRadius,
                center = knobCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
