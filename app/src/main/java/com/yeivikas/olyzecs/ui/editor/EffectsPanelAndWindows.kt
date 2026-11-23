package com.yeivikas.olyzecs.ui

import android.graphics.Bitmap
import com.yeivikas.olyzecs.engine.audio.label
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.engine.scene.Layer
import com.yeivikas.olyzecs.viewmodel.EditorViewModel
import com.yeivikas.olyzecs.ui.effects.EffectSources
import com.yeivikas.olyzecs.ui.effects.rememberProgressiveRenderScheduler
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sin

// ============================================================================
//  EffectsPanelAndWindows.kt — Panel de efectos y sus ventanas flotantes (Contorno, Resplandor, Sombra, Reflejo, Distortion)
// ============================================================================
//  ORIGEN: movido TAL CUAL desde `EditorScreen.kt` (Fase 3 del plan de ingeniería; ADR-022). Sin cambios de lógica.
//  Visibilidad: BelowAnchorCenteredPopupPositionProvider, ContornoFloatingWindow, DistortionFloatingWindow,
//      DistortionGestureBridge, DistortionPanel, EffectMenuDistortionIcon, EffectMenuGlowIcon, EffectMenuOutlineIcon,
//      EffectMenuReflectionIcon, EffectMenuShadowIcon, EffectsPanel, ReflejoFloatingWindow, ResplandorFloatingWindow,
//      SombraFloatingWindow pasan de `private` a `internal` (se usan entre archivos del paquete).
//  El paquete SIGUE siendo `com.yeivikas.olyzecs.ui` (la carpeta `editor/` es solo organización).
// ============================================================================

/**
 * Ventana flotante dedicada de "Contorno" — A PEDIDO EXPLÍCITO DEL
 * USUARIO: "Contorno" y "Resplandor" (ver [ResplandorFloatingWindow])
 * pasan a tener cada una su PROPIA ventana, con el mismo patrón "plugin
 * de audio flotante" que ya usan [RecolorFloatingWindow]/
 * [ColorBasicoFloatingWindow]/[Basico3DFloatingWindow] — arrastrable,
 * redimensionable, minimizable, con su propio zIndex. ACLARACIÓN
 * EXPLÍCITA DEL USUARIO, para no repetir el error de "Recolor"/"Básico"
 * con la pestaña "Color": esto NO saca "Contorno" del menú desplegable
 * "Efecto" de la barra superior — sigue siendo un ítem de ESE menú (ver
 * [EditImageEffectsMenu]), tal cual "Recolor"/"Básico" lo son del menú
 * "Color". Tocarlo simplemente dispara ESTA ventana en vez de la
 * compartida (que, a esta altura, ya no cubre ninguna categoría
 * accesible desde este menú).
 *
 * BUG REAL corregido acá (reportado con captura: la ventana abría pero
 * se veía VACÍA, solo la cabecera) — la primera versión envolvía
 * [EffectsPanel] entero como contenido, y EffectsPanel arma por dentro
 * su propia columna con `Modifier.weight(1f)`. Eso funciona en
 * [LayerColorEditPanel] (la ventana compartida) porque ahí el padre es
 * un `Column(Modifier.fillMaxSize())` liso, de altura ACOTADA. Pero acá
 * el slot `content` de [FloatingToolWindow] YA es, él mismo, una columna
 * con scroll — altura NO acotada a propósito. `weight()` sin una altura
 * acotada de la que tomar parte se resuelve en CERO: la ventana "vacía".
 *
 * La corrección real, siguiendo EXACTAMENTE el mismo patrón que
 * [ColorBasicoFloatingWindow] (que nunca tuvo este problema): en vez de
 * envolver el panel completo, esta ventana llama DIRECTO al composable
 * hoja de la categoría ([EffectsCategoryContorno]) como contenido — sin
 * ninguna columna ni `weight()` de por medio, tal cual
 * [ColorBasicoFloatingWindow] llama a `EffectsCategoryColor` — y trae su
 * PROPIO `commitJob`/`liveRenderJob` locales, igual que las otras tres
 * ventanas ya hacían. La única diferencia real con
 * [ColorBasicoFloatingWindow] es que acá el commit renderiza el stack
 * COMPLETO de "Efecto" (`buildFullEffectsParams`) y no solo 5 campos —
 * necesario porque "Contorno" comparte el mismo `ctrl`/`liveBitmap`/
 * `fullBitmap` "de base congelada" que "Resplandor" y la ventana
 * compartida (ver el comentario grande en la firma de [EffectsPanel]):
 * si acá se aplicara solo el contorno, el próximo commit apagaría
 * cualquier resplandor/sombra ya puesto por las otras dos.
 */
@Composable
internal fun ContornoFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    sources: EffectSources<Bitmap>,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ -> viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl)) },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "contorno") }
    )

    FloatingToolWindow(
        title = "Contorno",
        // Mismo glifo que ya usa "Contorno" en EditImageEffectsMenu —
        // ver [EffectMenuOutlineIcon].
        titleIcon = { tint -> EffectMenuOutlineIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // BUG REAL corregido acá (reportado con captura: arrastrar el
        // ícono minimizado hasta la zona de "Eliminar" y soltar no hacía
        // nada) — a esta ventana y a [ResplandorFloatingWindow] les
        // faltaba directamente el parámetro `onDeleteEffect`; sin él,
        // [FloatingToolWindow] igual muestra el pulso de confirmación al
        // soltar (por eso SE VEÍA como si funcionara) pero
        // `onDeleteEffect?.invoke()` no tiene nada que invocar.
        //
        // El mecanismo de [ColorBasicoFloatingWindow] (volver la CAPA
        // ENTERA a `originalSourceUriBeforeSession`) no sirve tal cual
        // acá: esta ventana comparte un solo `ctrl`/stack de efectos con
        // [ResplandorFloatingWindow]/[SombraFloatingWindow]/
        // [ReflejoFloatingWindow] y la ventana compartida (que a esta
        // altura ya no cubre ninguna categoría accesible desde el menú
        // "Efecto", ver el comentario grande en la firma de
        // [EffectsPanel]) — revertir TODA la capa borraría también
        // cualquier resplandor/sombra ya aplicado, no solo el contorno.
        // En vez de eso: se resetean a su valor por defecto SOLO los
        // campos de "Contorno" dentro del `ctrl` compartido, y se
        // vuelve a renderizar+guardar con el stack COMPLETO — así el
        // contorno desaparece del archivo persistido sin tocar nada más.
        //
        // `viewModel.viewModelScope`, NO el `coroutineScope` de acá
        // arriba, a propósito: ese muere apenas `onClose()` saca esta
        // ventana de composición, y `applyImageEffects` es `suspend` —
        // lanzado en el scope local, el commit nunca llegaría a
        // ejecutarse porque la corrutina se cancela a mitad de camino.
        onDeleteEffect = {
            scheduler.cancelPending()
            ctrl.outlineIntensity = 0f
            ctrl.outlineColor = android.graphics.Color.WHITE
            ctrl.outlineColor2 = android.graphics.Color.WHITE
            ctrl.outlineGradientEnabled = false
            ctrl.outlineFeather = 0f
            ctrl.outlinePosition = com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE
            viewModel.viewModelScope.launch { scheduler.commitNow() }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        // `EffectsCategoryContorno` es el mismo composable hoja que
        // usaba EffectsPanel para esta categoría — llamado DIRECTO como
        // contenido, sin ninguna columna ni `weight()` de por medio,
        // igual que [ColorBasicoFloatingWindow] hace con
        // `EffectsCategoryColor`. Ver el KDoc de esta función para el
        // porqué completo.
        EffectsCategoryContorno(ctrl, onChanged = { scheduler.onParametersChanged() })
    }
}

/**
 * Ventana flotante dedicada de "Resplandor" — hermana exacta de
 * [ContornoFloatingWindow] de acá arriba, mismo patrón y mismas
 * aclaraciones (ver su KDoc para el detalle completo). Único cambio
 * real: usa [EffectsCategoryResplandor] y su propio glifo
 * ([EffectMenuGlowIcon]).
 */
@Composable
internal fun ResplandorFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    sources: EffectSources<Bitmap>,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ -> viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl)) },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "resplandor") }
    )

    FloatingToolWindow(
        title = "Resplandor",
        titleIcon = { tint -> EffectMenuGlowIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // Ver el comentario grande sobre `onDeleteEffect` en
        // [ContornoFloatingWindow] — mismo bug, misma corrección, acá
        // con los campos de "Resplandor" en vez de "Contorno".
        onDeleteEffect = {
            scheduler.cancelPending()
            ctrl.glowIntensity = 0f
            ctrl.glowBlur = 0.5f
            ctrl.glowColor = android.graphics.Color.WHITE
            ctrl.glowColor2 = android.graphics.Color.WHITE
            ctrl.glowGradientEnabled = false
            ctrl.glowBlendMode = com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL
            ctrl.glowSpread = 0f
            ctrl.glowDistance = 0f
            ctrl.glowAngle = 135f
            viewModel.viewModelScope.launch { scheduler.commitNow() }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        EffectsCategoryResplandor(ctrl, onChanged = { scheduler.onParametersChanged() })
    }
}

/**
 * Ventana flotante dedicada de "Sombra" — hermana de [ContornoFloatingWindow]/
 * [ResplandorFloatingWindow] de acá arriba, MISMO patrón de fondo (ver su
 * KDoc para el detalle completo de por qué existe cada pieza). Única
 * diferencia estructural real: "Sombra" agrupa 3 variantes (Sombra /
 * Sombra relleno / Sombra contacto — antes 3 chips sueltos, después 3
 * sub-pestañas dentro de la ventana compartida, ver el comentario grande
 * en [EffectsPanel]) — así que esta ventana trae su PROPIO segundo nivel
 * de sub-pestañas adentro (`sombraSubCategories`/`selectedSombraSub`,
 * antes vivían en `EffectsPanel`, ver ahí por qué se sacaron de esa
 * función), en vez de una sola categoría hoja como Contorno/Resplandor.
 */
@Composable
internal fun SombraFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    sources: EffectSources<Bitmap>,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val sombraSubCategories = remember { listOf("Sombra", "Sombra relleno", "Sombra contacto") }
    var selectedSombraSub by remember(layer.id) { mutableStateOf(0) }

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ -> viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl)) },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "sombra") }
    )

    FloatingToolWindow(
        title = "Sombra",
        titleIcon = { tint -> EffectMenuShadowIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // Ver el comentario grande sobre `onDeleteEffect` en
        // [ContornoFloatingWindow] — mismo bug, misma corrección. Acá con
        // una diferencia real respecto a Contorno/Resplandor: "Sombra"
        // agrupa TRES variantes (Sombra / Sombra relleno / Sombra
        // contacto), así que "Eliminar" resetea los campos de las TRES —
        // no solo la sub-pestaña que estuviera visible en ese momento —
        // porque el ícono que se arrastra a la papelera representa la
        // categoría "Sombra" completa (así la ve/la eligió el usuario
        // desde `EditImageEffectsMenu`), no una sola de sus variantes.
        //
        // EXCEPCIÓN DELIBERADA: `groundWallBreak` NO se resetea acá, a
        // pesar de pertenecer a `EffectsCategorySombra`, porque ese mismo
        // campo (mismo nombre, mismo estado subyacente en
        // `EffectsControlsState`) también lo usa `EffectsCategoryReflejo`
        // — resetearlo desde acá cambiaría en silencio algo de la ventana
        // de Reflejo sin que el usuario lo haya tocado ni pedido. Si algún
        // día se quiere borrar del todo, que sea una decisión explícita
        // del usuario sobre CUÁL de las dos ventanas, no un efecto
        // colateral de la otra.
        onDeleteEffect = {
            scheduler.cancelPending()
            // Sombra (base)
            ctrl.shadowIntensity = 0f
            ctrl.shadowBlur = 0.5f
            ctrl.shadowSpread = 0f
            ctrl.shadowScale = 1f
            ctrl.shadowNoise = 0f
            ctrl.shadowDistance = 0.35f
            ctrl.shadowAngle = 135f
            ctrl.shadowColor = android.graphics.Color.BLACK
            ctrl.shadowSkew = 0f
            ctrl.shadowPerspectiveAmount = 0f
            ctrl.shadowFadeByDistance = 0f
            ctrl.shadowOpacityCurve = 0.5f
            ctrl.shadowBlendMultiply = true
            ctrl.shadowContactHardening = 0f
            ctrl.linkShadowToGlobalLight = false
            ctrl.linkShadowColorToGlobalLight = false
            // Sombra relleno
            ctrl.fillShadowIntensity = 0f
            ctrl.fillShadowBlur = 0.7f
            ctrl.fillShadowDistance = 0.2f
            ctrl.fillShadowAngle = 315f
            ctrl.fillShadowColor = android.graphics.Color.rgb(30, 34, 48)
            ctrl.fillShadowScale = 1f
            ctrl.linkFillShadowToGlobalLight = false
            // Sombra contacto
            ctrl.contactShadowIntensity = 0f
            ctrl.contactShadowSize = 0.5f
            ctrl.contactShadowBlur = 0.4f
            ctrl.contactShadowColor = android.graphics.Color.BLACK
            ctrl.contactShadowFalloff = 0.5f
            ctrl.contactShadowNoise = 0f
            ctrl.groundOcclusionIntensity = 0f
            viewModel.viewModelScope.launch { scheduler.commitNow() }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EffectsCategoryTabs(
                categories = sombraSubCategories,
                selected = selectedSombraSub,
                onSelected = { selectedSombraSub = it }
            )
            when (selectedSombraSub) {
                0 -> EffectsCategorySombra(ctrl, onChanged = { scheduler.onParametersChanged() })
                1 -> EffectsCategorySombraRelleno(ctrl, onChanged = { scheduler.onParametersChanged() })
                2 -> EffectsCategorySombraContacto(ctrl, onChanged = { scheduler.onParametersChanged() })
            }
        }
    }
}

/**
 * Ventana flotante dedicada de "Reflejo" — hermana de [SombraFloatingWindow]
 * de acá arriba, MISMO patrón (ver su KDoc para el detalle completo):
 * agrupa 3 variantes (Reflejo / Light wrap / Luz global — antes 3 chips
 * sueltos, después 3 sub-pestañas dentro de la ventana compartida), así
 * que trae su propio segundo nivel de sub-pestañas adentro
 * (`reflejoSubCategories`/`selectedReflejoSub`, antes vivían en
 * `EffectsPanel`, ver ahí por qué se sacaron de esa función).
 *
 * DECISIÓN DELIBERADA sobre `onDeleteEffect` — mismo espíritu que la
 * excepción de `groundWallBreak` en [SombraFloatingWindow], acá va un
 * paso más allá: "Eliminar Reflejo" SOLO resetea los campos de
 * [EffectsCategoryReflejo] (sin `groundWallBreak`, compartido con
 * Sombra — mismo motivo exacto) y de [EffectsCategoryLightWrap]. NO
 * resetea nada de la sub-pestaña "Luz global" — reviné cada uno de sus
 * campos (`globalLightAngle`, `linkShadowToGlobalLight`,
 * `linkFillShadowToGlobalLight`, `shadowAngle`, `fillShadowAngle`,
 * `globalLightColor`, `linkShadowColorToGlobalLight`, `shadowColor`) y
 * NINGUNO tiene prefijo `reflection*` — son todos campos de SOMBRA (ya
 * cubiertos por el `onDeleteEffect` de [SombraFloatingWindow]) o
 * genuinamente globales (`globalLightAngle`/`globalLightColor`, sin
 * dueño de un solo efecto). "Luz global" vive como sub-pestaña de esta
 * ventana por comodidad de edición, pero borrar "Reflejo" no puede
 * andar tocando en silencio configuración que es, en los hechos, de
 * "Sombra" — la misma regla de no pisar entre ventanas que ya se aplicó
 * con `groundWallBreak`, llevada hasta el final.
 */
@Composable
internal fun ReflejoFloatingWindow(
    layer: Layer,
    viewModel: EditorViewModel,
    ctrl: EffectsControlsState,
    sources: EffectSources<Bitmap>,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val reflejoSubCategories = remember { listOf("Reflejo", "Light wrap", "Luz global") }
    var selectedReflejoSub by remember(layer.id) { mutableStateOf(0) }

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ -> viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl)) },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "reflejo") }
    )

    FloatingToolWindow(
        title = "Reflejo",
        titleIcon = { tint -> EffectMenuReflectionIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        // Ver el comentario grande sobre `onDeleteEffect` en
        // [ContornoFloatingWindow] (por qué existe) y el KDoc de esta
        // misma función (por qué NO incluye "Luz global" ni
        // `groundWallBreak`).
        onDeleteEffect = {
            scheduler.cancelPending()
            // Reflejo (base) — SIN groundWallBreak, ver KDoc de la función.
            ctrl.reflectionIntensity = 0f
            ctrl.reflectionGap = 0f
            ctrl.reflectionLength = 1f
            ctrl.reflectionBlur = 0f
            ctrl.reflectionNoise = 0f
            ctrl.reflectionSkew = 0f
            ctrl.reflectionTintIntensity = 0f
            ctrl.reflectionTintColor = android.graphics.Color.rgb(58, 110, 150)
            ctrl.reflectionEdgeFade = 0f
            ctrl.reflectionRippleIntensity = 0f
            ctrl.reflectionRippleScale = 0.5f
            ctrl.reflectionOpacityCurve = 0.5f
            ctrl.reflectionPerspective = 1f
            ctrl.reflectionProgressiveBlur = 0f
            ctrl.reflectionFresnel = 0f
            // Light wrap
            ctrl.lightWrapIntensity = 0f
            ctrl.lightWrapColor = android.graphics.Color.rgb(255, 244, 214)
            ctrl.lightWrapWidth = 0.4f
            ctrl.lightWrapAngle = 90f
            ctrl.lightWrapDirectionality = 0f
            viewModel.viewModelScope.launch { scheduler.commitNow() }
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EffectsCategoryTabs(
                categories = reflejoSubCategories,
                selected = selectedReflejoSub,
                onSelected = { selectedReflejoSub = it }
            )
            when (selectedReflejoSub) {
                0 -> EffectsCategoryReflejo(ctrl, onChanged = { scheduler.onParametersChanged() })
                1 -> EffectsCategoryLightWrap(ctrl, onChanged = { scheduler.onParametersChanged() })
                2 -> EffectsCategoryLuzGlobal(ctrl, onChanged = { scheduler.onParametersChanged() })
            }
        }
    }
}

/**
 * Ventana flotante dedicada de "Distorsión" — hermana de
 * [SombraFloatingWindow]/[ReflejoFloatingWindow] de acá arriba, MISMO
 * patrón de chrome (título/cerrar/eliminar/arrastre/minimizado, ver el
 * KDoc de [ContornoFloatingWindow] para el detalle completo de fondo),
 * pero MÁS SIMPLE por dentro: a diferencia de las otras cuatro,
 * "Distorsión" no es un grupo de sliders sobre el `ctrl`/
 * `EffectsControlsState` compartido — es un motor de deformación de
 * malla completo y autocontenido ([DistortionPanel], con su propio
 * `liveBitmap`/`fullBitmap`, su propia malla, su propio historial de
 * deshacer/rehacer por trazo). Por eso esta ventana NO recibe `ctrl` ni
 * bitmaps — solo envuelve [DistortionPanel] tal cual, sin tocar su
 * interior en absoluto.
 *
 * `onDeleteEffect` no puede llamar a `resetAll()` directo — es una
 * función LOCAL adentro de [DistortionPanel], y esta ventana vive
 * afuera de ese composable. Se dispara a través de
 * `distortionBridge.onResetRequested` (ver el comentario grande en
 * [DistortionGestureBridge]), el mismo mecanismo de "bridge" que ya usa
 * esta pantalla para deshacer/rehacer — no un mecanismo nuevo inventado
 * para este caso puntual.
 */
@Composable
internal fun DistortionFloatingWindow(
    layer: Layer,
    context: android.content.Context,
    viewModel: EditorViewModel,
    distortionBridge: DistortionGestureBridge,
    initialOffset: Offset,
    onClose: () -> Unit,
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    FloatingToolWindow(
        title = "Distorsión",
        titleIcon = { tint -> EffectMenuDistortionIcon(tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE,
        onClose = onClose,
        onDeleteEffect = {
            distortionBridge.onResetRequested?.invoke()
            onClose()
        },
        onInteracted = onInteracted,
        minimizedRegistry = minimizedRegistry,
        containerSizePx = containerSizePx,
        modifier = modifier
    ) {
        DistortionPanel(
            layer = layer,
            context = context,
            viewModel = viewModel,
            distortionBridge = distortionBridge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Pestaña "Efectos" del modo "Editando imagen" (al lado de "3D", ver
 * EditImageToolsHeader): difuminado de fondo, nitidez, saturación,
 * brillo, contraste, tono, contorno, resplandor, sombra proyectada y
 * reflejo — nivel "premium": ni el difuminado, ni el contorno, ni el
 * resplandor, ni la sombra, ni el reflejo son un slider suelto, cada uno
 * trae sus propios sub-controles (suavizado de contorno para el
 * difuminado; grosor+color para el contorno; intensidad+difuminado+color
 * para el resplandor; intensidad+suavizado+distancia+ángulo+color para
 * la sombra), como en cualquier herramienta de edición profesional
 * (estilo Photoshop/Affinity).
 *
 * "Difuminar" difumina el FONDO alrededor del sujeto (estilo retrato/
 * profundidad de campo), NUNCA al sujeto ni a su sombra — ver el
 * comentario grande de [com.yeivikas.olyzecs.engine.effects.ImageEffects.
 * applyBackgroundBlur] para el detalle técnico completo de por qué (y
 * cómo) se protege al sujeto. El contorno y el resplandor comparten el
 * mismo criterio de independencia: ambos se calculan siempre a partir
 * del alfa del bitmap ORIGINAL (ver [com.yeivikas.olyzecs.engine.effects.
 * ImageEffects.compositeWithEffects]), nunca del resultado de otro
 * control, así que no se "acumulan" pases entre sí.
 *
 * Los colores (sombra/contorno/resplandor) usan [ColorSwatchPickerButton]
 * — una muestra circular que abre un mini-popup con [ColorWheelPicker],
 * la MISMA rueda de color que ya usa la pestaña "Recolor" (ver
 * [LayerColorEditPanel]), reutilizada tal cual en vez de crear un
 * selector nuevo.
 *
 * Mismo patrón de vista previa en vivo + guardado con debounce de 500ms
 * que [Extrude3DPanel]/[LayerColorEditPanel]: los sliders "normales"
 * se aplican sobre una copia CHICA en cada tick (liviano, throttleado a
 * ~16/s para no saturar el hilo de UI) y sobre la copia GRANDE 500ms
 * después de la última pausa, que es la que de verdad se persiste como
 * archivo nuevo (mismo previewLayerRecolor/commitLayerRecolor genéricos
 * que ya usan las otras dos pestañas — no hacen nada específico de
 * "recolorear", solo suben/guardan el bitmap que se les pase).
 */
internal class EffectsControlsState {
    var blur by mutableStateOf(0f)
    // BUG REAL corregido acá: este default estaba en 0.3f, mientras que
    // el motor ([ImageEffectsParams.edgeFeather]) usa 0f como neutro —
    // única inconsistencia encontrada entre los 66 campos de la UI y el
    // motor (todos los demás coinciden exactamente). Efecto práctico:
    // cualquier capa nueva que solo tocara "Difuminar (fondo)" sin tocar
    // "Suavizado de contorno" salía con un 30% de suavizado no pedido en
    // vez del comportamiento neutro documentado (transición dura,
    // protección exacta al alfa del sujeto). Alineado a 0f para que el
    // estado inicial del panel reproduzca fielmente `ImageEffectsParams()`.
    var edgeFeather by mutableStateOf(0f)
    var sharpen by mutableStateOf(0f)
    var saturation by mutableStateOf(1f)
    var brightness by mutableStateOf(0f)
    var contrast by mutableStateOf(1f)
    var hue by mutableStateOf(0f)
    var outlineIntensity by mutableStateOf(0f)
    var outlineColor by mutableStateOf(android.graphics.Color.WHITE)
    var outlineColor2 by mutableStateOf(android.graphics.Color.WHITE)
    var outlineGradientEnabled by mutableStateOf(false)
    var outlineFeather by mutableStateOf(0f)
    var outlinePosition by mutableStateOf(com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE)
    var glowIntensity by mutableStateOf(0f)
    var glowBlur by mutableStateOf(0.5f)
    var glowColor by mutableStateOf(android.graphics.Color.WHITE)
    var glowColor2 by mutableStateOf(android.graphics.Color.WHITE)
    var glowGradientEnabled by mutableStateOf(false)
    var glowBlendMode by mutableStateOf(com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL)
    var glowSpread by mutableStateOf(0f)
    var glowDistance by mutableStateOf(0f)
    var glowAngle by mutableStateOf(135f)
    var shadowIntensity by mutableStateOf(0f)
    var shadowBlur by mutableStateOf(0.5f)
    var shadowSpread by mutableStateOf(0f)
    var shadowScale by mutableStateOf(1f)
    var shadowNoise by mutableStateOf(0f)
    var shadowDistance by mutableStateOf(0.35f)
    var shadowAngle by mutableStateOf(135f)
    var shadowColor by mutableStateOf(android.graphics.Color.BLACK)
    var shadowSkew by mutableStateOf(0f)
    var shadowPerspectiveAmount by mutableStateOf(0f)
    var shadowFadeByDistance by mutableStateOf(0f)
    var shadowOpacityCurve by mutableStateOf(0.5f)
    var shadowBlendMultiply by mutableStateOf(true)
    var groundWallBreak by mutableStateOf(0f)
    var shadowContactHardening by mutableStateOf(0f)
    var fillShadowIntensity by mutableStateOf(0f)
    var fillShadowBlur by mutableStateOf(0.7f)
    var fillShadowDistance by mutableStateOf(0.2f)
    var fillShadowAngle by mutableStateOf(315f)
    var fillShadowColor by mutableStateOf(android.graphics.Color.rgb(30, 34, 48))
    var fillShadowScale by mutableStateOf(1f)
    var reflectionIntensity by mutableStateOf(0f)
    var reflectionGap by mutableStateOf(0f)
    var reflectionLength by mutableStateOf(1f)
    var reflectionBlur by mutableStateOf(0f)
    var reflectionNoise by mutableStateOf(0f)
    var reflectionSkew by mutableStateOf(0f)
    var reflectionTintIntensity by mutableStateOf(0f)
    var reflectionTintColor by mutableStateOf(android.graphics.Color.rgb(58, 110, 150))
    var reflectionEdgeFade by mutableStateOf(0f)
    var reflectionRippleIntensity by mutableStateOf(0f)
    var reflectionRippleScale by mutableStateOf(0.5f)
    var reflectionOpacityCurve by mutableStateOf(0.5f)
    var reflectionPerspective by mutableStateOf(1f)
    var reflectionProgressiveBlur by mutableStateOf(0f)
    var reflectionFresnel by mutableStateOf(0f)
    var globalLightAngle by mutableStateOf(135f)
    var linkShadowToGlobalLight by mutableStateOf(false)
    var linkFillShadowToGlobalLight by mutableStateOf(false)
    // Vínculo de COLOR con la luz global: cuando está activo, el color
    // de la sombra proyectada se recalcula automáticamente como el
    // complementario de [globalLightColor] — ver
    // [com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor].
    var globalLightColor by mutableStateOf(android.graphics.Color.rgb(255, 244, 214))
    var linkShadowColorToGlobalLight by mutableStateOf(false)
    var contactShadowIntensity by mutableStateOf(0f)
    var contactShadowSize by mutableStateOf(0.5f)
    var contactShadowBlur by mutableStateOf(0.4f)
    var contactShadowColor by mutableStateOf(android.graphics.Color.BLACK)
    var contactShadowFalloff by mutableStateOf(0.5f)
    var contactShadowNoise by mutableStateOf(0f)
    var groundOcclusionIntensity by mutableStateOf(0f)
    // Puntos de apoyo adicionales para la sombra de contacto — lista
    // vacía = una sola mancha centrada (clásico). Ver
    // [com.yeivikas.olyzecs.engine.effects.ImageEffectsParams.contactShadowPoints].
    val contactShadowPoints = mutableStateListOf<com.yeivikas.olyzecs.engine.effects.ContactShadowPoint>()
    var lightWrapIntensity by mutableStateOf(0f)
    var lightWrapColor by mutableStateOf(android.graphics.Color.rgb(255, 244, 214))
    var lightWrapWidth by mutableStateOf(0.4f)
    var lightWrapAngle by mutableStateOf(90f)
    var lightWrapDirectionality by mutableStateOf(0f)
}

/**
 * Arma el `ImageEffectsParams` COMPLETO (blur/color/contorno/resplandor/
 * sombra/reflejo — todo el stack no-destructivo de "Efecto") a partir de
 * un [EffectsControlsState] — extraído de lo que antes era el
 * `currentParams()` local de [EffectsPanel] para que TAMBIÉN lo puedan
 * usar [ContornoFloatingWindow]/[ResplandorFloatingWindow]: las tres
 * comparten el mismo `ctrl` (ver el comentario grande en la firma de
 * [EffectsPanel]) y tienen que re-renderizar el stack COMPLETO en cada
 * commit, no solo su propia categoría — si cada una renderizara nada
 * más que "su" pedazo, el commit de una apagaría lo que la otra ya tenía
 * aplicado. Una sola función, un solo lugar para tocar si mañana se
 * agrega un campo nuevo.
 */
private fun buildFullEffectsParams(ctrl: EffectsControlsState) =
    com.yeivikas.olyzecs.engine.effects.ImageEffectsParams(
        blur = ctrl.blur,
        edgeFeather = ctrl.edgeFeather,
        sharpen = ctrl.sharpen,
        saturation = ctrl.saturation,
        brightness = ctrl.brightness,
        contrast = ctrl.contrast,
        hue = ctrl.hue,
        outlineIntensity = ctrl.outlineIntensity,
        outlineColor = ctrl.outlineColor,
        outlineColor2 = ctrl.outlineColor2,
        outlineGradientEnabled = ctrl.outlineGradientEnabled,
        outlineFeather = ctrl.outlineFeather,
        outlinePosition = ctrl.outlinePosition,
        glowIntensity = ctrl.glowIntensity,
        glowBlur = ctrl.glowBlur,
        glowColor = ctrl.glowColor,
        glowColor2 = ctrl.glowColor2,
        glowGradientEnabled = ctrl.glowGradientEnabled,
        glowBlendMode = ctrl.glowBlendMode,
        glowSpread = ctrl.glowSpread,
        glowDistance = ctrl.glowDistance,
        glowAngleDeg = ctrl.glowAngle,
        shadowIntensity = ctrl.shadowIntensity,
        shadowBlur = ctrl.shadowBlur,
        shadowSpread = ctrl.shadowSpread,
        shadowScale = ctrl.shadowScale,
        shadowNoise = ctrl.shadowNoise,
        shadowDistance = ctrl.shadowDistance,
        shadowAngleDeg = ctrl.shadowAngle,
        shadowColor = ctrl.shadowColor,
        shadowSkewDegrees = ctrl.shadowSkew,
        shadowPerspectiveAmount = ctrl.shadowPerspectiveAmount,
        shadowFadeByDistance = ctrl.shadowFadeByDistance,
        shadowOpacityCurve = ctrl.shadowOpacityCurve,
        shadowBlendMultiply = ctrl.shadowBlendMultiply,
        groundWallBreak = ctrl.groundWallBreak,
        shadowContactHardening = ctrl.shadowContactHardening,
        fillShadowIntensity = ctrl.fillShadowIntensity,
        fillShadowBlur = ctrl.fillShadowBlur,
        fillShadowDistance = ctrl.fillShadowDistance,
        fillShadowAngleDeg = ctrl.fillShadowAngle,
        fillShadowColor = ctrl.fillShadowColor,
        fillShadowScale = ctrl.fillShadowScale,
        reflectionIntensity = ctrl.reflectionIntensity,
        reflectionGap = ctrl.reflectionGap,
        reflectionLength = ctrl.reflectionLength,
        reflectionBlur = ctrl.reflectionBlur,
        reflectionNoise = ctrl.reflectionNoise,
        reflectionSkewDegrees = ctrl.reflectionSkew,
        reflectionTintIntensity = ctrl.reflectionTintIntensity,
        reflectionTintColor = ctrl.reflectionTintColor,
        reflectionEdgeFade = ctrl.reflectionEdgeFade,
        reflectionRippleIntensity = ctrl.reflectionRippleIntensity,
        reflectionRippleScale = ctrl.reflectionRippleScale,
        reflectionOpacityCurve = ctrl.reflectionOpacityCurve,
        reflectionPerspective = ctrl.reflectionPerspective,
        reflectionProgressiveBlur = ctrl.reflectionProgressiveBlur,
        reflectionFresnel = ctrl.reflectionFresnel,
        contactShadowIntensity = ctrl.contactShadowIntensity,
        contactShadowSize = ctrl.contactShadowSize,
        contactShadowBlur = ctrl.contactShadowBlur,
        contactShadowColor = ctrl.contactShadowColor,
        contactShadowFalloff = ctrl.contactShadowFalloff,
        contactShadowNoise = ctrl.contactShadowNoise,
        groundOcclusionIntensity = ctrl.groundOcclusionIntensity,
        contactShadowPoints = ctrl.contactShadowPoints.toList(),
        lightWrapIntensity = ctrl.lightWrapIntensity,
        lightWrapColor = ctrl.lightWrapColor,
        lightWrapWidth = ctrl.lightWrapWidth,
        lightWrapAngleDeg = ctrl.lightWrapAngle,
        lightWrapDirectionality = ctrl.lightWrapDirectionality
    )

@Composable
internal fun rememberEffectsControlsState(key: Any?): EffectsControlsState =
    remember(key) { EffectsControlsState() }

@Composable
internal fun EffectsPanel(
    layer: Layer,
    viewModel: EditorViewModel,
    // A PEDIDO DEL USUARIO: la categoría activa ("Fondo"/"Color"/
    // "Contorno"/... — ver el mapa de índices de categorías acá abajo) ya NO es
    // local a este panel: se elige desde EditImageEffectsMenu, el menú
    // que cuelga del texto "Efecto" de la barra superior (ver topBar en
    // EditorScreen). Sube como parámetro por la misma razón que
    // `selectedTab` subió en LayerColorEditPanel — el botón que la
    // cambia y el panel que la consume viven en composables distintos.
    selectedTopCategory: Int,
    onSelectedTopCategoryChange: (Int) -> Unit,
    // A PEDIDO DEL USUARIO — MULTI-VENTANA ("Contorno"/"Resplandor"/
    // "Sombra"/"Reflejo" con ventana propia, ver
    // [ContornoFloatingWindow]/[ResplandorFloatingWindow]/
    // [SombraFloatingWindow]/[ReflejoFloatingWindow]; "Distorsión"
    // también tiene la suya, [DistortionFloatingWindow], pero esa no
    // toca `ctrl` en absoluto — es un motor de malla autocontenido, ver
    // su propio KDoc): `ctrl`, `liveBitmap` y `fullBitmap` suben como
    // parámetros en vez de crearse acá adentro con `remember(layer.id)`.
    // Motivo real, no solo prolijidad: aunque EffectsPanel en sí ya solo
    // se monta en UN lugar (la ventana compartida, que a esta altura ya
    // no cubre ninguna categoría accesible desde el menú "Efecto" — solo
    // Fondo/Color/Presets, inalcanzables desde ahí — las otras cuatro
    // llaman DIRECTO a sus composables hoja,
    // [EffectsCategoryContorno]/[EffectsCategoryResplandor]/etc., sin
    // pasar por acá, ver el KDoc de esas cuatro ventanas para el
    // porqué), las CINCO siguen leyendo y escribiendo el MISMO
    // `EffectsControlsState`/bitmaps de base — levantados una sola vez
    // por capa en EditorScreen y reenviados tal cual a las cinco. Si
    // cada una tuviera su propia copia, (a) cada una arrancaría con sus
    // propios sliders en 0 aunque OTRA ventana ya tenga ese efecto
    // aplicado, y el próximo commit de esa ventana
    // "borraría" el efecto ajeno sin que el usuario tocara nada de esa
    // categoría; y (b) cada una decodificaría su "base plana" en un
    // momento distinto — si para entonces `layer.sourceUri` ya cambió
    // por el commit de otra ventana, esa nueva base ya NO sería plana, y
    // el próximo commit la re-renderizaría por encima, duplicando el
    // efecto. Levantar los tres (ctrl/liveBitmap/fullBitmap) en el
    // llamador común elimina ambos problemas de raíz, sin importar
    // cuántas ventanas terminen compartiéndolos.
    ctrl: EffectsControlsState,
    sources: EffectSources<Bitmap>,
    modifier: Modifier = Modifier
) {

    // Sub-categorías de la pestaña "Efectos" — antes todos los
    // controles vivían apilados en una sola columna larga con scroll;
    // ahora se agrupan por categoría y solo se muestran los controles
    // de la categoría seleccionada. Ningún control, valor por defecto
    // ni lógica de aplicación se modifica acá: es puramente una
    // reorganización visual de los mismos sliders/botones de siempre.
    //
    // A PEDIDO DEL USUARIO — reordenado en dos niveles, definido charla
    // por charla, categoría por categoría, ANTES de tocar código:
    //  - "Básicos" se separó en dos categorías reales sin relación entre
    //    sí: "Fondo" (difuminar/suavizado — solo toca el fondo) y "Color"
    //    (nitidez/saturación/brillo/contraste/tono — solo toca el
    //    sujeto). Antes vivían mezcladas bajo un solo botón engañoso.
    //    ACTUALIZACIÓN posterior: "Color" ya no vive acá — se mudó
    //    entera a la pestaña "Color" de arriba como su opción "Básico"
    //    (ver [ColorBasicoFloatingWindow]); "Fondo" sigue siendo una
    //    categoría de "Efecto", tal cual quedó descrito acá.
    //  - "Contorno" y "Resplandor" quedan cada una como categoría propia,
    //    al mismo nivel que el resto — NO son sub-ítems de "Sombra" (se
    //    descartó esa idea en la charla: contorno es un borde sólido,
    //    resplandor es un halo de luz hacia afuera, ninguno de los dos
    //    es una sombra).
    //  - "Sombra" (antes 3 chips sueltos: Sombra / Sombra relleno /
    //    Sombra contacto) y "Reflejo" (antes 3 chips sueltos: Reflejo /
    //    Light wrap / Luz global) SÍ agrupan de verdad varias variantes
    //    de un mismo concepto, así que esas dos quedan con un segundo
    //    nivel de sub-pestañas propio — ACTUALIZACIÓN posterior: ese
    //    segundo nivel ya no vive acá abajo, "anidado" dentro de esta
    //    función; se mudó junto con cada una a su propia ventana (ver
    //    [SombraFloatingWindow]/[ReflejoFloatingWindow]) cuando pasaron a
    //    tener ventana propia, en vez de ocupar 3 lugares cada una en la
    //    fila de arriba.
    //  - "Presets" queda tal cual está, al final — pendiente de una
    //    revisión propia que todavía no se hizo.
    //  - "Distorsión" (las 9 herramientas de deformación de malla,
    //    ver DistortionPanel) se agrega acá como la ÚLTIMA categoría de
    //    la cadena, no como pestaña propia al lado de "Efectos" — es un
    //    efecto de imagen más, así que vive adentro de "Efectos" igual
    //    que el resto, no como ventana independiente.
    // Ni un slider, ni un valor por defecto, ni una función de cálculo
    // cambiaron con este reordenamiento — es 100% reorganización visual.
    // Mapa de índices de categoría (inmutable, es contrato con el `when` de
    // más abajo y con EditImageEffectsMenu): Fondo=0, Color=1, Contorno=2,
    // Resplandor=3, Sombra=4, Reflejo=5, Presets=6, Distorsión=7.
    // A PEDIDO DEL USUARIO: ya no es un `remember` local — viene de
    // arriba (ver el parámetro `selectedTopCategory` de esta función).
    // Se mantienen los MISMOS índices de siempre dentro del
    // mapa de índices de categorías (Fondo=0 ... Distorsión=7): el menú de
    // arriba (EditImageEffectsMenu) solo deja de OFRECER Fondo/Color/
    // Presets como opciones, pero el "when" de acá abajo que rutea cada
    // índice a sus sliders no cambió ni un poco.
    val selectedEffectsTopCategory = selectedTopCategory

    // "Sombra" y "Reflejo" ya no tienen sub-pestañas acá — se mudaron
    // adentro de [SombraFloatingWindow]/[ReflejoFloatingWindow] junto con
    // sus propias ventanas dedicadas (ver el comentario grande más abajo,
    // donde antes vivía el `when` de índices 4 y 5).

    var blur by ctrl::blur
    var edgeFeather by ctrl::edgeFeather
    var sharpen by ctrl::sharpen
    var saturation by ctrl::saturation
    var brightness by ctrl::brightness
    var contrast by ctrl::contrast
    var hue by ctrl::hue
    var outlineIntensity by ctrl::outlineIntensity
    var outlineColor by ctrl::outlineColor
    var outlineFeather by ctrl::outlineFeather
    var outlinePosition by ctrl::outlinePosition
    var glowIntensity by ctrl::glowIntensity
    var glowBlur by ctrl::glowBlur
    var glowColor by ctrl::glowColor
    var glowSpread by ctrl::glowSpread
    var glowDistance by ctrl::glowDistance
    var glowAngle by ctrl::glowAngle
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowSpread by ctrl::shadowSpread
    var shadowScale by ctrl::shadowScale
    var shadowNoise by ctrl::shadowNoise
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowColor by ctrl::shadowColor
    var shadowSkew by ctrl::shadowSkew
    var shadowPerspectiveAmount by ctrl::shadowPerspectiveAmount
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowOpacityCurve by ctrl::shadowOpacityCurve
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var groundWallBreak by ctrl::groundWallBreak
    var shadowContactHardening by ctrl::shadowContactHardening
    var fillShadowIntensity by ctrl::fillShadowIntensity
    var fillShadowBlur by ctrl::fillShadowBlur
    var fillShadowDistance by ctrl::fillShadowDistance
    var fillShadowAngle by ctrl::fillShadowAngle
    var fillShadowColor by ctrl::fillShadowColor
    var fillShadowScale by ctrl::fillShadowScale
    var reflectionIntensity by ctrl::reflectionIntensity
    var reflectionGap by ctrl::reflectionGap
    var reflectionLength by ctrl::reflectionLength
    var reflectionBlur by ctrl::reflectionBlur
    var reflectionNoise by ctrl::reflectionNoise
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective
    var reflectionProgressiveBlur by ctrl::reflectionProgressiveBlur
    var reflectionFresnel by ctrl::reflectionFresnel
    // "Luz global" — posición del sol/fuente de luz única que puede
    // gobernar el ángulo de la sombra proyectada y de la sombra de
    // relleno a la vez, para que nunca queden mirando para lados
    // distintos por error (el problema típico de tener ángulos
    // totalmente sueltos por sombra). Por defecto DESVINCULADO (false):
    // el comportamiento de cada slider de ángulo sigue siendo 100% el
    // de siempre hasta que el usuario decide activarlo a propósito.
    var globalLightAngle by ctrl::globalLightAngle
    var linkShadowToGlobalLight by ctrl::linkShadowToGlobalLight
    var linkFillShadowToGlobalLight by ctrl::linkFillShadowToGlobalLight
    var globalLightColor by ctrl::globalLightColor
    var linkShadowColorToGlobalLight by ctrl::linkShadowColorToGlobalLight
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var contactShadowColor by ctrl::contactShadowColor
    var contactShadowFalloff by ctrl::contactShadowFalloff
    var contactShadowNoise by ctrl::contactShadowNoise
    var groundOcclusionIntensity by ctrl::groundOcclusionIntensity
    // Alias directo a la lista mutable de `ctrl` (no un delegado `var`:
    // una `SnapshotStateList` ya es la referencia mutable en sí misma,
    // así que no hace falta — y no se puede — envolverla en `by`).
    val contactShadowPoints = ctrl.contactShadowPoints
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapColor by ctrl::lightWrapColor
    var lightWrapWidth by ctrl::lightWrapWidth
    var lightWrapAngle by ctrl::lightWrapAngle
    var lightWrapDirectionality by ctrl::lightWrapDirectionality

    // Throttle de la vista previa en vivo — mismo motivo/valores que en
    // Extrude3DPanel.applyLivePreviewAndScheduleCommit: sin esto, cada
    // tick crudo del Slider dispararía un box-blur de 3 pasadas en el
    // hilo de UI y se vería entrecortado mientras se arrastra.

    // La carga de la base "plana" (sin efectos todavía) ya NO vive acá
    // — ver el comentario grande junto a los parámetros `liveBitmap`/
    // `fullBitmap`, arriba: se hace UNA sola vez por capa en
    // EditorScreen (ver `effectsLiveBitmap`/`effectsFullBitmap` +
    // su `LaunchedEffect(editModeLayerId)`), y se reenvía tal cual acá.
    // Ningún slider, valor por defecto ni lógica de render cambió con
    // este movimiento.
    fun currentParams() = buildFullEffectsParams(ctrl)

    // Vista previa progresiva y guardado de ESTA ventana (ver [ProgressiveRenderScheduler]): arrastre sobre la copia
    // chica, reposo sobre la de pantalla y guardado SOLO desde la imagen completa.
    val scheduler = rememberProgressiveRenderScheduler(
        sources = sources,
        persistScope = viewModel.viewModelScope,
        render = { source, _ -> viewModel.applyImageEffects(source, buildFullEffectsParams(ctrl)) },
        onPreview = { viewModel.previewLayerRecolor(layer.id, it) },
        onFinal = { viewModel.commitLayerRecolor(layer.id, it, source = "effects") }
    )

    // "Luz global" — mientras cada vínculo esté activo, el ángulo de
    // esa sombra sigue en vivo a [globalLightAngle]; la sombra de
    // relleno se calcula como el opuesto (+180°), imitando luz
    // rebotada desde el lado contrario a la fuente principal, un
    // criterio de iluminación de 3 puntos estándar en compositing
    // profesional. Declarado DESPUÉS de [applyLivePreviewAndScheduleCommit]
    // a propósito, para no depender de una referencia hacia adelante a
    // una función local.
    LaunchedEffect(globalLightAngle, linkShadowToGlobalLight) {
        if (linkShadowToGlobalLight && shadowAngle != globalLightAngle) {
            shadowAngle = globalLightAngle
            scheduler.onParametersChanged()
        }
    }
    LaunchedEffect(globalLightAngle, linkFillShadowToGlobalLight) {
        val opposite = (globalLightAngle + 180f) % 360f
        if (linkFillShadowToGlobalLight && fillShadowAngle != opposite) {
            fillShadowAngle = opposite
            scheduler.onParametersChanged()
        }
    }
    // Vínculo de COLOR: mientras esté activo, el color de la sombra
    // proyectada se recalcula como el complementario de
    // [globalLightColor] cada vez que ese color cambia — ver
    // [ImageEffects.deriveComplementaryShadowColor]. Igual criterio que
    // los vínculos de ángulo de arriba: el usuario puede seguir
    // moviendo el color de luz libremente y la sombra lo sigue en vivo.
    LaunchedEffect(globalLightColor, linkShadowColorToGlobalLight) {
        if (linkShadowColorToGlobalLight) {
            val derived = com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor(globalLightColor)
            if (shadowColor != derived) {
                shadowColor = derived
                scheduler.onParametersChanged()
            }
        }
    }

    Column(modifier = modifier) {
            // BUG REAL corregido acá — mismo caso que [ColorBasicoFloatingWindow]/
            // [Basico3DFloatingWindow] (ver el comentario grande en esa
            // primera función para el detalle completo): TODA esta
            // pestaña ("Fondo"/"Color"/"Contorno"/"Resplandor"/"Sombra"/
            // "Reflejo"/"Presets") vivía atrás de este mismo `isLoading`
            // de `liveBitmap`, aunque ninguna de esas categorías dibuja
            // ni depende de ese bitmap — todas leen/escriben `ctrl`, en
            // memoria, disponible desde el primer frame. Resultado: cada
            // vez que se abría "Efecto" se veía el mismo salto de tamaño
            // en dos pasos (spinner chico → panel real unos milisegundos
            // después) que el usuario reportó como "se expande por
            // partes/con lag". `liveBitmap`/`fullBitmap` se siguen
            // cargando igual en el `LaunchedEffect` de arriba;
            // `scheduler.onParametersChanged()` ya trata ese bitmap
            // como opcional. "Distorsión" (índice 7, más abajo) tenía el
            // mismo `isLoading` propio y se corrigió igual — ver
            // [DistortionPanel], mismo motivo exacto: sus chips de
            // herramienta y sliders tampoco dependen del bitmap.
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // A PEDIDO DEL USUARIO: la fila de chips de categoría
                // (Fondo/Color/Contorno/.../Distorsión) que vivía ACÁ se
                // sacó por completo — la categoría ahora se elige desde
                // EditImageEffectsMenu, el menú que cuelga del texto
                // "Efecto" de la barra superior. El mapa de índices de categorías
                // sigue existiendo, pero a esta altura es solo
                // documentación de índices (Fondo=0 ... Distorsión=7,
                // ver su declaración más arriba) para no romper los
                // índices de siempre en el "when" de esta función — ya
                // no se renderiza como fila de chips acá, y tampoco
                // respalda ningún segundo nivel de sub-pestañas (las de
                // Sombra/Reflejo se mudaron junto con esas dos a sus
                // propias ventanas, ver [SombraFloatingWindow]/
                // [ReflejoFloatingWindow]).
                //
                // "Distorsión" (índice 7) también se mudó a su propia
                // ventana (ver [DistortionFloatingWindow]) — esta función
                // ya NO monta [DistortionPanel] en absoluto, así que el
                // `if/else` que antes elegía entre "el panel completo de
                // Distorsión" y "la columna con scroll de sliders sobre
                // `ctrl`" (necesario en su momento porque anidar un
                // `verticalScroll` dentro de otro rompe el layout) dejó
                // de tener sentido: ya no hay dos casos que elegir entre
                // — todo lo que queda acá (Fondo/Color/Presets) SIEMPRE
                // usa el molde de sliders con scroll.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                when (selectedEffectsTopCategory) {
                    0 -> EffectsCategoryFondo(ctrl, onChanged = { scheduler.onParametersChanged() })
                    1 -> EffectsCategoryColor(ctrl, onChanged = { scheduler.onParametersChanged() })
                    // "Contorno" (2), "Resplandor" (3), "Sombra" (4),
                    // "Reflejo" (5) y "Distorsión" (7) YA NO se rutean
                    // acá — [EditImageEffectsMenu] dejó de abrir esta
                    // ventana compartida para esos cinco índices; cada
                    // una llama directo a su propia función/ventana
                    // dedicada ([ContornoFloatingWindow]/
                    // [ResplandorFloatingWindow]/[SombraFloatingWindow]/
                    // [ReflejoFloatingWindow]/[DistortionFloatingWindow]).
                    // `selectedTopCategory` solo llega acá con 0, 1 o 6 —
                    // de ahí que estos cinco casos ya no tengan sentido en
                    // este `when` y se sacaron, junto con el segundo
                    // nivel de sub-pestañas que ya no lo necesita ninguna
                    // categoría que quede acá.
                    6 -> EffectsCategoryPresets(ctrl, onChanged = { scheduler.onParametersChanged() })
                }
                }
            }
    }
}
@Composable
private fun EffectsCategorySombra(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowSpread by ctrl::shadowSpread
    var shadowScale by ctrl::shadowScale
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowColor by ctrl::shadowColor
    var shadowSkew by ctrl::shadowSkew
    var shadowPerspectiveAmount by ctrl::shadowPerspectiveAmount
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowOpacityCurve by ctrl::shadowOpacityCurve
    var shadowNoise by ctrl::shadowNoise
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var groundWallBreak by ctrl::groundWallBreak
    var shadowContactHardening by ctrl::shadowContactHardening
    var globalLightAngle by ctrl::globalLightAngle
    var linkShadowToGlobalLight by ctrl::linkShadowToGlobalLight
    var linkShadowColorToGlobalLight by ctrl::linkShadowColorToGlobalLight

                Text(
                    "Sombra",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LabeledSlider(
                    label = "Intensidad",
                    value = shadowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowIntensity = it; onChanged() }

                // Los siguientes tres controles "premium" de la sombra solo
                // importan mientras la sombra es visible — deshabilitados
                // (en vez de ocultos) mientras Intensidad está en 0, así el
                // usuario siempre ve que existen sin que parezcan hacer
                // nada todavía.
                val shadowControlsEnabled = shadowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado de la sombra",
                    value = shadowBlur,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Expansión (Spread)",
                    value = shadowSpread,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowSpread = it; onChanged() }
                Text(
                    "Agranda el núcleo de la sombra y ajusta su borde — el control \"Spread\" clásico de Photoshop, distinto de solo difuminar más",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Distancia de la sombra",
                    value = shadowDistance,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Escala de la sombra",
                    value = shadowScale,
                    range = 0.4f..2f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowScale = it; onChanged() }
                Text(
                    "Agranda o achica la sombra de forma independiente del sujeto, pivotando siempre desde su punto de apoyo — sombras reales rara vez son 1:1 con el objeto",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Ángulo de la sombra",
                    value = shadowAngle,
                    range = 0f..360f,
                    enabled = shadowControlsEnabled && !linkShadowToGlobalLight,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { shadowAngle = it; onChanged() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vincular a Luz global",
                        color = if (shadowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkShadowToGlobalLight,
                        onCheckedChange = {
                            linkShadowToGlobalLight = it
                            if (it) { shadowAngle = globalLightAngle }
                            onChanged()
                        },
                        enabled = shadowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Con esto activo, el ángulo lo maneja la sub-categoría \"Luz global\" — para que esta sombra y la de relleno nunca miren para lados distintos",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ColorSwatchPickerButton(
                    label = "Color de la sombra",
                    colorArgb = shadowColor,
                    enabled = shadowControlsEnabled && !linkShadowColorToGlobalLight
                ) { c -> shadowColor = c; onChanged() }
                if (linkShadowColorToGlobalLight) {
                    Text(
                        "Vinculado al color de la Luz global (complementario) — desactivá el vínculo en esa sub-categoría para elegirlo manualmente",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                LabeledSlider(
                    label = "Inclinación (perspectiva)",
                    value = shadowSkew,
                    range = -45f..45f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { shadowSkew = it; onChanged() }

                LabeledSlider(
                    label = "Perspectiva real (punto de fuga)",
                    value = shadowPerspectiveAmount,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowPerspectiveAmount = it; onChanged() }
                Text(
                    "Se suma a la inclinación de arriba: en vez de solo inclinar la sombra, la hace converger de verdad hacia un punto de fuga — el pie queda fijo y el extremo lejano se angosta, como proyectada sobre un piso real en ángulo",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Desvanecer con la distancia",
                    value = shadowFadeByDistance,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowFadeByDistance = it; onChanged() }

                LabeledSlider(
                    label = "Curva del desvanecimiento",
                    value = shadowOpacityCurve,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled && shadowFadeByDistance > 0.001f,
                    valueLabel = { if (abs(it - 0.5f) < 0.02f) "Lineal" else if (it < 0.5f) "Rápida" else "Sostenida" }
                ) { shadowOpacityCurve = it; onChanged() }
                Text(
                    "Solo afecta con \"Desvanecer con la distancia\" activo: cambia la FORMA de la caída, no cuánto se desvanece",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Grano / textura",
                    value = shadowNoise,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowNoise = it; onChanged() }
                Text(
                    "Rompe la uniformidad perfecta de la sombra con una textura fina — evita el look \"digital\"/plano sobre superficies reales",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Penumbra progresiva (contact-hardening)",
                    value = shadowContactHardening,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { shadowContactHardening = it; onChanged() }
                Text(
                    "Se suma al Difuminado de arriba: crece hacia el extremo lejano del punto de apoyo, quedando nítida junto al sujeto — bajo luz de área (no un foco puntual ideal), EL efecto que distingue una sombra fotorrealista de una plana",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Quiebre piso/pared",
                    value = groundWallBreak,
                    range = 0f..1f,
                    enabled = shadowControlsEnabled,
                    valueLabel = { if (it < 0.02f) "Piso infinito" else "${(it * 100).roundToInt()}%" }
                ) { groundWallBreak = it; onChanged() }
                Text(
                    "Simula un fondo de estudio \"infinito\": el piso se dobla hacia una pared vertical en vez de estirarse para siempre — comparte este mismo control con la sección Reflejo. Mientras está activo, reemplaza a \"Perspectiva real (punto de fuga)\" de arriba",
                    color = Color.White.copy(alpha = if (shadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Modo multiplicar",
                        color = if (shadowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = shadowBlendMultiply,
                        onCheckedChange = { shadowBlendMultiply = it; onChanged() },
                        enabled = shadowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Oscurece de forma natural donde la sombra proyectada, la de relleno y la de contacto se superponen entre sí, en vez de mezclarse como un gris translúcido — el estándar profesional",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
}
@Composable
private fun EffectsCategoryReflejo(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var reflectionIntensity by ctrl::reflectionIntensity
    var reflectionGap by ctrl::reflectionGap
    var reflectionLength by ctrl::reflectionLength
    var reflectionBlur by ctrl::reflectionBlur
    var reflectionNoise by ctrl::reflectionNoise
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective
    var reflectionProgressiveBlur by ctrl::reflectionProgressiveBlur
    var reflectionFresnel by ctrl::reflectionFresnel
    var groundWallBreak by ctrl::groundWallBreak

                Text(
                    "Reflejo",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Copia volteada del sujeto, con opacidad decreciente hacia abajo",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = reflectionIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionIntensity = it; onChanged() }

                // Controles "premium" del reflejo: solo importan mientras
                // el reflejo es visible — deshabilitados (no ocultos)
                // mientras Intensidad está en 0, mismo criterio que ya
                // usan los controles extra de la sombra.
                val reflectionControlsEnabled = reflectionIntensity > 0.001f

                LabeledSlider(
                    label = "Distancia (separación del pie)",
                    value = reflectionGap,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionGap = it; onChanged() }

                LabeledSlider(
                    label = "Largo del reflejo",
                    value = reflectionLength,
                    range = 0.1f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionLength = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado del reflejo",
                    value = reflectionBlur,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionBlur = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado progresivo por distancia",
                    value = reflectionProgressiveBlur,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionProgressiveBlur = it; onChanged() }
                Text(
                    "Se suma al difuminado de arriba: en vez de un desenfoque parejo, crece cuanto más lejos del pie — como un piso pulido real, más nítido cerca y más difuso hacia el fondo",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Inclinación (perspectiva)",
                    value = reflectionSkew,
                    range = -45f..45f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { reflectionSkew = it; onChanged() }

                LabeledSlider(
                    label = "Tinte del reflejo",
                    value = reflectionTintIntensity,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionTintIntensity = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del tinte",
                    colorArgb = reflectionTintColor,
                    enabled = reflectionControlsEnabled && reflectionTintIntensity > 0.001f
                ) { c -> reflectionTintColor = c; onChanged() }

                LabeledSlider(
                    label = "Desvanecer bordes",
                    value = reflectionEdgeFade,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionEdgeFade = it; onChanged() }

                LabeledSlider(
                    label = "Ondulación (agua)",
                    value = reflectionRippleIntensity,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionRippleIntensity = it; onChanged() }

                LabeledSlider(
                    label = "Densidad de las ondas",
                    value = reflectionRippleScale,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled && reflectionRippleIntensity > 0.001f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionRippleScale = it; onChanged() }

                LabeledSlider(
                    label = "Grano / textura",
                    value = reflectionNoise,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionNoise = it; onChanged() }
                Text(
                    "Micro-imperfecciones de la superficie (polvo, rayones) — un piso pulido real casi nunca es un espejo perfecto. Es una textura fija, no se mueve con la ondulación de arriba",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Curva de opacidad",
                    value = reflectionOpacityCurve,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionOpacityCurve = it; onChanged() }
                Text(
                    "50% = degradado lineal clásico. Bajo = se apaga casi enseguida, alto = se sostiene fuerte y corta de golpe al final",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Perspectiva (compresión)",
                    value = reflectionPerspective,
                    range = 0.3f..1.5f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionPerspective = it; onChanged() }
                Text(
                    "Aplana o estira el reflejo verticalmente sin tocar su ancho — un reflejo real sobre un piso en ángulo casi nunca es 1:1 con el sujeto",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Fresnel (refuerzo hacia el horizonte)",
                    value = reflectionFresnel,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { reflectionFresnel = it; onChanged() }
                Text(
                    "Física real de reflexión: cualquier superficie refleja MÁS en ángulo rasante (lejos, cerca del horizonte) que justo debajo del pie. Se suma sobre la curva de opacidad de arriba, contrarrestándola hacia el extremo lejano",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Quiebre piso/pared",
                    value = groundWallBreak,
                    range = 0f..1f,
                    enabled = reflectionControlsEnabled,
                    valueLabel = { if (it < 0.02f) "Piso infinito" else "${(it * 100).roundToInt()}%" }
                ) { groundWallBreak = it; onChanged() }
                Text(
                    "Mismo control que en la sección Sombra (fondo de estudio \"infinito\"): el reflejo deja de estirarse en línea recta y \"sube\" hacia una pared vertical a partir de este punto",
                    color = Color.White.copy(alpha = if (reflectionControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )
}
@Composable
private fun EffectsCategoryLuzGlobal(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var globalLightAngle by ctrl::globalLightAngle
    var linkShadowToGlobalLight by ctrl::linkShadowToGlobalLight
    var linkFillShadowToGlobalLight by ctrl::linkFillShadowToGlobalLight
    var shadowAngle by ctrl::shadowAngle
    var fillShadowAngle by ctrl::fillShadowAngle
    var globalLightColor by ctrl::globalLightColor
    var linkShadowColorToGlobalLight by ctrl::linkShadowColorToGlobalLight
    var shadowColor by ctrl::shadowColor

                Text(
                    "Luz global",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Una sola \"posición del sol\" que puede gobernar el ángulo de la sombra proyectada y de la sombra de relleno a la vez — evita el error típico de tener cada sombra mirando hacia un lado distinto. No afecta nada mientras ambos vínculos abajo estén apagados.",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )

                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Indicador visual de "posición del sol": un punto
                    // que gira alrededor del centro siguiendo
                    // [globalLightAngle] — puramente decorativo/de
                    // referencia, no participa del cálculo.
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer { rotationZ = globalLightAngle }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BrandPurpleLight)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }

                LabeledSlider(
                    label = "Ángulo de la luz global",
                    value = globalLightAngle,
                    range = 0f..360f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) {
                    globalLightAngle = it
                    if (linkShadowToGlobalLight) shadowAngle = it
                    if (linkFillShadowToGlobalLight) fillShadowAngle = (it + 180f) % 360f
                    onChanged()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "Vincular sombra proyectada",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkShadowToGlobalLight,
                        onCheckedChange = {
                            linkShadowToGlobalLight = it
                            if (it) { shadowAngle = globalLightAngle }
                            onChanged()
                        },
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Text(
                        "Vincular sombra de relleno (opuesto)",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkFillShadowToGlobalLight,
                        onCheckedChange = {
                            linkFillShadowToGlobalLight = it
                            if (it) { fillShadowAngle = (globalLightAngle + 180f) % 360f }
                            onChanged()
                        },
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "La sombra de contacto no tiene ángulo propio (siempre queda centrada bajo el pie), así que no participa de este vínculo",
                    color = Color.White.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                ColorSwatchPickerButton(
                    label = "Color de la luz",
                    colorArgb = globalLightColor,
                    enabled = true
                ) { c ->
                    globalLightColor = c
                    if (linkShadowColorToGlobalLight) {
                        shadowColor = com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor(c)
                    }
                    onChanged()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        "Vincular color de sombra (complementario)",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkShadowColorToGlobalLight,
                        onCheckedChange = {
                            linkShadowColorToGlobalLight = it
                            if (it) {
                                shadowColor = com.yeivikas.olyzecs.engine.effects.ImageEffects.deriveComplementaryShadowColor(globalLightColor)
                            }
                            onChanged()
                        },
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Recalcula el color de la sombra proyectada como el complementario de \"Color de la luz\" — una luz cálida siempre produce sombras que se leen frías, y viceversa: el mismo contraste de temperatura de cualquier set de fotografía real",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
}
@Composable
private fun EffectsCategorySombraRelleno(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var fillShadowIntensity by ctrl::fillShadowIntensity
    var fillShadowBlur by ctrl::fillShadowBlur
    var fillShadowDistance by ctrl::fillShadowDistance
    var fillShadowScale by ctrl::fillShadowScale
    var fillShadowAngle by ctrl::fillShadowAngle
    var fillShadowColor by ctrl::fillShadowColor
    var linkFillShadowToGlobalLight by ctrl::linkFillShadowToGlobalLight
    var globalLightAngle by ctrl::globalLightAngle

                Text(
                    "Sombra de relleno",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Una segunda sombra, más suave y en otro ángulo, que simula la luz rebotada/ambiente — evita que el sujeto se vea con una sola sombra dura y plana",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = fillShadowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowIntensity = it; onChanged() }

                val fillShadowControlsEnabled = fillShadowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado",
                    value = fillShadowBlur,
                    range = 0f..1f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Distancia",
                    value = fillShadowDistance,
                    range = 0f..1f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Escala",
                    value = fillShadowScale,
                    range = 0.4f..2f,
                    enabled = fillShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { fillShadowScale = it; onChanged() }

                LabeledSlider(
                    label = "Ángulo",
                    value = fillShadowAngle,
                    range = 0f..360f,
                    enabled = fillShadowControlsEnabled && !linkFillShadowToGlobalLight,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { fillShadowAngle = it; onChanged() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vincular a Luz global (opuesto)",
                        color = if (fillShadowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = linkFillShadowToGlobalLight,
                        onCheckedChange = {
                            linkFillShadowToGlobalLight = it
                            if (it) { fillShadowAngle = (globalLightAngle + 180f) % 360f }
                            onChanged()
                        },
                        enabled = fillShadowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                Text(
                    "Sigue automáticamente el lado opuesto a la Luz global (+180°) — simula luz rebotada, criterio de iluminación de 3 puntos",
                    color = Color.White.copy(alpha = if (fillShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ColorSwatchPickerButton(
                    label = "Color de relleno",
                    colorArgb = fillShadowColor,
                    enabled = fillShadowControlsEnabled
                ) { c -> fillShadowColor = c; onChanged() }
}
@Composable
private fun EffectsCategorySombraContacto(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var contactShadowFalloff by ctrl::contactShadowFalloff
    var contactShadowColor by ctrl::contactShadowColor
    var contactShadowNoise by ctrl::contactShadowNoise
    var groundOcclusionIntensity by ctrl::groundOcclusionIntensity

                Text(
                    "Sombra de contacto",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Mancha corta y suave justo en el pie del sujeto, para \"anclarlo\" al piso — independiente de la sombra proyectada de arriba",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = contactShadowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowIntensity = it; onChanged() }

                val contactShadowControlsEnabled = contactShadowIntensity > 0.001f

                LabeledSlider(
                    label = "Tamaño",
                    value = contactShadowSize,
                    range = 0.1f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowSize = it; onChanged() }

                LabeledSlider(
                    label = "Difuminado",
                    value = contactShadowBlur,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowBlur = it; onChanged() }

                LabeledSlider(
                    label = "Curva de caída (falloff)",
                    value = contactShadowFalloff,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowFalloff = it; onChanged() }
                Text(
                    "Bajo = caída lenta y extendida, alto = núcleo denso que corta rápido — controla la FORMA del degradado, no solo su difuminado",
                    color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Grano / textura",
                    value = contactShadowNoise,
                    range = 0f..1f,
                    enabled = contactShadowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contactShadowNoise = it; onChanged() }
                Text(
                    "Misma idea que el grano de la sombra proyectada y del reflejo, aplicada acá: rompe la mancha de contacto perfectamente lisa",
                    color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ColorSwatchPickerButton(
                    label = "Color de contacto",
                    colorArgb = contactShadowColor,
                    enabled = contactShadowControlsEnabled
                ) { c -> contactShadowColor = c; onChanged() }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Oclusión ambiental (sobre el sujeto)",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "A diferencia de todo lo de arriba, esto oscurece al SUJETO mismo (no a su sombra) justo donde toca el piso — lo \"ancla\" en vez de dejarlo flotando",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = groundOcclusionIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { groundOcclusionIntensity = it; onChanged() }
                Text(
                    "Detecta el punto de apoyo real de cada columna del sujeto (no un borde parejo) — funciona igual de bien con un contorno irregular",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Puntos de apoyo múltiples",
                        color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.55f else 0.3f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = contactShadowControlsEnabled) {
                                ctrl.contactShadowPoints.add(
                                    com.yeivikas.olyzecs.engine.effects.ContactShadowPoint(xOffset = 0f)
                                )
                                onChanged()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.75f else 0.3f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Agregar punto",
                            color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.75f else 0.3f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    "Para objetos con varios puntos de apoyo (una silla, un trípode) — cada punto agrega SU PROPIA mancha, con posición, tamaño e intensidad independientes. Sin puntos: una única mancha centrada (comportamiento clásico)",
                    color = Color.White.copy(alpha = if (contactShadowControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                ctrl.contactShadowPoints.forEachIndexed { index, point ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Punto ${index + 1}",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = {
                                    ctrl.contactShadowPoints.removeAt(index)
                                    onChanged()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Quitar punto",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        LabeledSlider(
                            label = "Posición horizontal",
                            value = point.xOffset,
                            range = -1.5f..1.5f,
                            enabled = contactShadowControlsEnabled,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                            // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                            // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                            // `displayScale` acá, escribir a mano "83" en un control interno
                            // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                            // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                            // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                            // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                            // `displayScale = 100f` hace que el campo de edición manual (y su
                            // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                            // y tipea (0-100, o -100..100), no en la escala cruda interna.
                            displayScale = 100f
                        ) { v ->
                            ctrl.contactShadowPoints[index] = point.copy(xOffset = v)
                            onChanged()
                        }
                        LabeledSlider(
                            label = "Tamaño (relativo)",
                            value = point.sizeScale,
                            range = 0.2f..2f,
                            enabled = contactShadowControlsEnabled,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                            // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                            // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                            // `displayScale` acá, escribir a mano "83" en un control interno
                            // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                            // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                            // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                            // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                            // `displayScale = 100f` hace que el campo de edición manual (y su
                            // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                            // y tipea (0-100, o -100..100), no en la escala cruda interna.
                            displayScale = 100f
                        ) { v ->
                            ctrl.contactShadowPoints[index] = point.copy(sizeScale = v)
                            onChanged()
                        }
                        LabeledSlider(
                            label = "Intensidad (relativa)",
                            value = point.intensityScale,
                            range = 0f..1.5f,
                            enabled = contactShadowControlsEnabled,
                            valueLabel = { "${(it * 100).roundToInt()}%" },
                            // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                            // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                            // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                            // `displayScale` acá, escribir a mano "83" en un control interno
                            // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                            // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                            // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                            // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                            // `displayScale = 100f` hace que el campo de edición manual (y su
                            // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                            // y tipea (0-100, o -100..100), no en la escala cruda interna.
                            displayScale = 100f
                        ) { v ->
                            ctrl.contactShadowPoints[index] = point.copy(intensityScale = v)
                            onChanged()
                        }
                    }
                }
}
@Composable
private fun EffectsCategoryLightWrap(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapWidth by ctrl::lightWrapWidth
    var lightWrapAngle by ctrl::lightWrapAngle
    var lightWrapDirectionality by ctrl::lightWrapDirectionality
    var lightWrapColor by ctrl::lightWrapColor

                Text(
                    "Light wrap",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Filtra un color de luz ambiente hacia adentro del borde del sujeto, para que se sienta integrado y no \"pegado\" encima del fondo",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Intensidad",
                    value = lightWrapIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { lightWrapIntensity = it; onChanged() }

                val lightWrapControlsEnabled = lightWrapIntensity > 0.001f

                LabeledSlider(
                    label = "Ancho",
                    value = lightWrapWidth,
                    range = 0f..1f,
                    enabled = lightWrapControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { lightWrapWidth = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color de la luz",
                    colorArgb = lightWrapColor,
                    enabled = lightWrapControlsEnabled
                ) { c -> lightWrapColor = c; onChanged() }

                LabeledSlider(
                    label = "Direccionalidad",
                    value = lightWrapDirectionality,
                    range = 0f..1f,
                    enabled = lightWrapControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { lightWrapDirectionality = it; onChanged() }
                Text(
                    "0% = envuelve todo el contorno por igual (clásico). Al subirlo, se concentra en el lado que mira hacia el ángulo de abajo y se atenúa del lado contrario — como una luz de ambiente real, que solo ilumina el lado que la enfrenta",
                    color = Color.White.copy(alpha = if (lightWrapControlsEnabled) 0.4f else 0.2f),
                    style = MaterialTheme.typography.labelSmall
                )

                LabeledSlider(
                    label = "Ángulo de la luz",
                    value = lightWrapAngle,
                    range = 0f..360f,
                    enabled = lightWrapControlsEnabled && lightWrapDirectionality > 0.001f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { lightWrapAngle = it; onChanged() }
}
@Composable
private fun EffectsCategoryPresets(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var shadowIntensity by ctrl::shadowIntensity
    var shadowBlur by ctrl::shadowBlur
    var shadowScale by ctrl::shadowScale
    var shadowNoise by ctrl::shadowNoise
    var shadowDistance by ctrl::shadowDistance
    var shadowAngle by ctrl::shadowAngle
    var shadowSkew by ctrl::shadowSkew
    var shadowPerspectiveAmount by ctrl::shadowPerspectiveAmount
    var shadowFadeByDistance by ctrl::shadowFadeByDistance
    var shadowBlendMultiply by ctrl::shadowBlendMultiply
    var fillShadowIntensity by ctrl::fillShadowIntensity
    var fillShadowBlur by ctrl::fillShadowBlur
    var fillShadowDistance by ctrl::fillShadowDistance
    var fillShadowAngle by ctrl::fillShadowAngle
    var fillShadowColor by ctrl::fillShadowColor
    var fillShadowScale by ctrl::fillShadowScale
    var contactShadowIntensity by ctrl::contactShadowIntensity
    var contactShadowSize by ctrl::contactShadowSize
    var contactShadowBlur by ctrl::contactShadowBlur
    var contactShadowColor by ctrl::contactShadowColor
    var contactShadowFalloff by ctrl::contactShadowFalloff
    var reflectionIntensity by ctrl::reflectionIntensity
    var reflectionGap by ctrl::reflectionGap
    var reflectionLength by ctrl::reflectionLength
    var reflectionBlur by ctrl::reflectionBlur
    var reflectionNoise by ctrl::reflectionNoise
    var reflectionSkew by ctrl::reflectionSkew
    var reflectionTintIntensity by ctrl::reflectionTintIntensity
    var reflectionTintColor by ctrl::reflectionTintColor
    var reflectionEdgeFade by ctrl::reflectionEdgeFade
    var reflectionRippleIntensity by ctrl::reflectionRippleIntensity
    var reflectionRippleScale by ctrl::reflectionRippleScale
    var reflectionOpacityCurve by ctrl::reflectionOpacityCurve
    var reflectionPerspective by ctrl::reflectionPerspective
    var reflectionProgressiveBlur by ctrl::reflectionProgressiveBlur
    var lightWrapIntensity by ctrl::lightWrapIntensity
    var lightWrapWidth by ctrl::lightWrapWidth
    var lightWrapColor by ctrl::lightWrapColor
    var lightWrapAngle by ctrl::lightWrapAngle
    var lightWrapDirectionality by ctrl::lightWrapDirectionality

                Text(
                    "Preset profesional",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Combina sombra proyectada + sombra de contacto + reflejo + light wrap con valores balanceados de estudio, en un toque",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        shadowIntensity = 0.55f
                        shadowBlur = 0.5f
                        shadowDistance = 0.15f
                        shadowAngle = 90f
                        shadowSkew = 0f
                        shadowFadeByDistance = 0.25f
                        shadowBlendMultiply = true
                        fillShadowIntensity = 0.25f
                        fillShadowBlur = 0.7f
                        fillShadowDistance = 0.2f
                        fillShadowAngle = 315f
                        fillShadowColor = android.graphics.Color.rgb(30, 34, 48)
                        contactShadowIntensity = 0.6f
                        contactShadowSize = 0.42f
                        contactShadowBlur = 0.35f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0.45f
                        reflectionGap = 0.02f
                        reflectionLength = 0.55f
                        reflectionBlur = 0.22f
                        reflectionSkew = 0f
                        reflectionTintIntensity = 0.18f
                        reflectionTintColor = android.graphics.Color.rgb(58, 110, 150)
                        reflectionEdgeFade = 0.3f
                        reflectionRippleIntensity = 0f
                        reflectionRippleScale = 0.5f
                        lightWrapIntensity = 0.35f
                        lightWrapWidth = 0.4f
                        lightWrapColor = android.graphics.Color.rgb(255, 244, 214)
                        onChanged()
                    }) {
                        Text("Piso reflectante ✨")
                    }
                    OutlinedButton(onClick = {
                        // Estudio dramático — luz de contraste alto, estilo
                        // "rim light" de un solo lado. Sombra proyectada
                        // larga y dura con perspectiva de punto de fuga
                        // real (no shear), fill mínimo para maximizar el
                        // contraste, sombra de contacto concentrada y sin
                        // reflejo de piso (un estudio dramático suele
                        // tener piso mate, no pulido). El light wrap se
                        // concentra en un solo borde (direccionalidad
                        // alta) simulando un solo foco detrás/lateral del
                        // sujeto, en tono frío para reforzar el drama.
                        shadowIntensity = 0.78f
                        shadowBlur = 0.32f
                        shadowScale = 1.08f
                        shadowNoise = 0f
                        shadowDistance = 0.3f
                        shadowAngle = 115f
                        shadowSkew = 0f
                        shadowPerspectiveAmount = 0.6f
                        shadowFadeByDistance = 0.15f
                        shadowBlendMultiply = true
                        fillShadowIntensity = 0.08f
                        fillShadowBlur = 0.85f
                        fillShadowDistance = 0.14f
                        fillShadowAngle = 295f
                        fillShadowColor = android.graphics.Color.rgb(18, 20, 32)
                        fillShadowScale = 1f
                        contactShadowIntensity = 0.72f
                        contactShadowSize = 0.32f
                        contactShadowBlur = 0.22f
                        contactShadowColor = android.graphics.Color.BLACK
                        contactShadowFalloff = 0.8f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0f
                        lightWrapIntensity = 0.55f
                        lightWrapWidth = 0.22f
                        lightWrapColor = android.graphics.Color.rgb(198, 218, 255)
                        lightWrapAngle = 45f
                        lightWrapDirectionality = 0.85f
                        onChanged()
                    }) {
                        Text("Estudio dramático 🎭")
                    }
                    OutlinedButton(onClick = {
                        // Luz suave de producto — envolvente y pareja,
                        // sin sombras duras ni ángulos marcados. Pensado
                        // para fotografía de producto tipo softbox: sombra
                        // corta y muy difusa con leve perspectiva de piso,
                        // fill cálido generoso para levantar las sombras,
                        // sombra de contacto ancha y gradual (nunca
                        // concentrada), reflejo sutil con difuminado
                        // progresivo (más nítido cerca del pie, más suave
                        // lejos, como una mesa de estudio) y light wrap
                        // parejo en todo el contorno, sin direccionalidad,
                        // en tono cálido neutro.
                        shadowIntensity = 0.32f
                        shadowBlur = 0.8f
                        shadowScale = 1f
                        shadowNoise = 0f
                        shadowDistance = 0.09f
                        shadowAngle = 90f
                        shadowSkew = 0f
                        shadowPerspectiveAmount = 0.25f
                        shadowFadeByDistance = 0.35f
                        shadowBlendMultiply = true
                        fillShadowIntensity = 0.3f
                        fillShadowBlur = 0.9f
                        fillShadowDistance = 0.1f
                        fillShadowAngle = 270f
                        fillShadowColor = android.graphics.Color.rgb(58, 52, 44)
                        fillShadowScale = 1f
                        contactShadowIntensity = 0.4f
                        contactShadowSize = 0.58f
                        contactShadowBlur = 0.62f
                        contactShadowColor = android.graphics.Color.BLACK
                        contactShadowFalloff = 0.22f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0.28f
                        reflectionGap = 0f
                        reflectionLength = 0.4f
                        reflectionBlur = 0.5f
                        reflectionNoise = 0.35f
                        reflectionSkew = 0f
                        reflectionTintIntensity = 0f
                        reflectionEdgeFade = 0.5f
                        reflectionRippleIntensity = 0f
                        reflectionRippleScale = 0.5f
                        reflectionOpacityCurve = 0.5f
                        reflectionPerspective = 1f
                        reflectionProgressiveBlur = 0.6f
                        lightWrapIntensity = 0.4f
                        lightWrapWidth = 0.5f
                        lightWrapColor = android.graphics.Color.rgb(255, 248, 235)
                        lightWrapAngle = 90f
                        lightWrapDirectionality = 0f
                        onChanged()
                    }) {
                        Text("Luz suave de producto 💡")
                    }
                    OutlinedButton(onClick = {
                        shadowIntensity = 0f
                        fillShadowIntensity = 0f
                        contactShadowIntensity = 0f
                        ctrl.contactShadowPoints.clear()
                        reflectionIntensity = 0f
                        reflectionNoise = 0f
                        lightWrapIntensity = 0f
                        onChanged()
                    }) {
                        Text("Quitar sombra/reflejo")
                    }
                }
}
@Composable
private fun EffectsCategoryFondo(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var blur by ctrl::blur
    var edgeFeather by ctrl::edgeFeather

                // "Difuminar": difumina el FONDO alrededor del sujeto
                // (estilo retrato/profundidad de campo) — el sujeto y su
                // sombra quedan siempre nítidos, sin importar el valor de
                // este slider. Ver el comentario grande de
                // ImageEffects.applyBackgroundBlur para el detalle
                // técnico completo.
                Text(
                    "Difuminar afecta solo el fondo — el sujeto y su sombra quedan siempre nítidos",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
                LabeledSlider(
                    label = "Difuminar (fondo)",
                    value = blur,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { blur = it; onChanged() }

                LabeledSlider(
                    label = "Suavizado de contorno",
                    value = edgeFeather,
                    range = 0f..1f,
                    enabled = blur > 0.001f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { edgeFeather = it; onChanged() }
}

@Composable
// A PEDIDO DEL USUARIO — "Básicos" mezclaba dos funciones sin relación
// entre sí en la misma ventana: difuminar el FONDO (arriba, en
// [EffectsCategoryFondo]) y corrección de color del SUJETO (acá). Se
// separaron en dos categorías propias porque, tal como quedó claro en la
// charla, no tienen nada que ver una con la otra — ninguno de estos 5
// sliders toca el fondo, los 5 son ajustes sobre el sujeto mismo (ver los
// comentarios de `sharpen`/`hue` en `ImageEffectsParams`: "nitidez del
// sujeto", "rotación de matiz sobre TODO el sujeto").
//
// ACTUALIZACIÓN posterior: esta función YA NO se llama solo desde
// EffectsPanel (pestaña "Efecto", donde quedó inalcanzable — ver
// EditImageEffectsMenu) — ahora vive principalmente en
// [ColorBasicoFloatingWindow], colgada de la opción "Básico" del menú
// "Color". Sigue siendo el MISMO composable, sin un solo cambio acá
// adentro: solo cambió quién la llama y desde dónde.
internal fun EffectsCategoryColor(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var sharpen by ctrl::sharpen
    var saturation by ctrl::saturation
    var brightness by ctrl::brightness
    var contrast by ctrl::contrast
    var hue by ctrl::hue

                LabeledSlider(
                    label = "Nitidez",
                    value = sharpen,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { sharpen = it; onChanged() }

                LabeledSlider(
                    label = "Saturación",
                    value = saturation,
                    range = 0f..2f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { saturation = it; onChanged() }

                LabeledSlider(
                    label = "Brillo",
                    value = brightness,
                    range = -1f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%".let { s -> if (it > 0f) "+$s" else s } },
                    displayScale = 100f
                ) { brightness = it; onChanged() }

                LabeledSlider(
                    label = "Contraste",
                    value = contrast,
                    range = 0f..2f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { contrast = it; onChanged() }

                LabeledSlider(
                    label = "Tono",
                    value = hue,
                    range = 0f..360f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { hue = it; onChanged() }
}

@Composable
private fun EffectsCategoryContorno(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var outlineIntensity by ctrl::outlineIntensity
    var outlineColor by ctrl::outlineColor
    var outlineColor2 by ctrl::outlineColor2
    var outlineGradientEnabled by ctrl::outlineGradientEnabled
    var outlineFeather by ctrl::outlineFeather
    var outlinePosition by ctrl::outlinePosition

                Text(
                    "Contorno",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LabeledSlider(
                    label = "Grosor",
                    value = outlineIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { outlineIntensity = it; onChanged() }

                val outlineControlsEnabled = outlineIntensity > 0.001f

                // Posición del trazo — mismo control "Position" de
                // cualquier Stroke profesional (Photoshop/Affinity/
                // Figma): antes SOLO existía "Afuera".
                Text(
                    "Posición",
                    color = Color.White.copy(alpha = if (outlineControlsEnabled) 0.55f else 0.25f),
                    style = MaterialTheme.typography.labelSmall
                )
                ThreeWayToggle(
                    labels = listOf("Afuera", "Centro", "Adentro"),
                    selectedIndex = when (outlinePosition) {
                        com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE -> 0
                        com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.CENTER -> 1
                        com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.INSIDE -> 2
                    },
                    enabled = outlineControlsEnabled
                ) { index ->
                    outlinePosition = when (index) {
                        0 -> com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.OUTSIDE
                        1 -> com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.CENTER
                        else -> com.yeivikas.olyzecs.engine.effects.OutlineStrokePosition.INSIDE
                    }
                    onChanged()
                }

                LabeledSlider(
                    label = "Difuminado del borde",
                    value = outlineFeather,
                    range = 0f..1f,
                    enabled = outlineControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    displayScale = 100f
                ) { outlineFeather = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del contorno",
                    colorArgb = outlineColor,
                    enabled = outlineControlsEnabled
                ) { c -> outlineColor = c; onChanged() }

                // Degradado del contorno (dos colores) — A PEDIDO DEL
                // USUARIO. Mismo patrón de `Switch` que "Vincular a Luz
                // global" en Sombra: en apagado (default) el segundo
                // color queda oculto y el contorno sale sólido de
                // [outlineColor], EXACTO como antes.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Degradado (2 colores)",
                        color = if (outlineControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = outlineGradientEnabled,
                        onCheckedChange = {
                            outlineGradientEnabled = it
                            onChanged()
                        },
                        enabled = outlineControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                if (outlineGradientEnabled) {
                    ColorSwatchPickerButton(
                        label = "Segundo color (punta del trazo)",
                        colorArgb = outlineColor2,
                        enabled = outlineControlsEnabled
                    ) { c -> outlineColor2 = c; onChanged() }
                }
}

/**
 * Selector de 3 opciones en una sola fila (mismo estilo visual que
 * [DistortionDirectionToggle], generalizado a N etiquetas) — usado por
 * "Posición" del contorno (Afuera/Centro/Adentro).
 */
@Composable
private fun ThreeWayToggle(
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active && enabled) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = when {
                        !enabled -> Color.White.copy(alpha = 0.25f)
                        active -> Color.White
                        else -> Color.White.copy(alpha = 0.5f)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EffectsCategoryResplandor(ctrl: EffectsControlsState, onChanged: () -> Unit) {
    var glowIntensity by ctrl::glowIntensity
    var glowBlur by ctrl::glowBlur
    var glowColor by ctrl::glowColor
    var glowColor2 by ctrl::glowColor2
    var glowGradientEnabled by ctrl::glowGradientEnabled
    var glowBlendMode by ctrl::glowBlendMode
    var glowSpread by ctrl::glowSpread
    var glowDistance by ctrl::glowDistance
    var glowAngle by ctrl::glowAngle

                Text(
                    "Resplandor",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LabeledSlider(
                    label = "Intensidad",
                    value = glowIntensity,
                    range = 0f..1f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { glowIntensity = it; onChanged() }

                val glowControlsEnabled = glowIntensity > 0.001f

                LabeledSlider(
                    label = "Difuminado del resplandor",
                    value = glowBlur,
                    range = 0f..1f,
                    enabled = glowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                    // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                    // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                    // `displayScale` acá, escribir a mano "83" en un control interno
                    // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                    // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                    // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                    // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                    // `displayScale = 100f` hace que el campo de edición manual (y su
                    // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                    // y tipea (0-100, o -100..100), no en la escala cruda interna.
                    displayScale = 100f
                ) { glowBlur = it; onChanged() }

                // Spread/Choke — mismo control "Spread" que ya tiene
                // "Sombra" (ver ImageEffectsParams.shadowSpread), ahora
                // también acá: núcleo más sólido/definido y caída más
                // corta, en vez de un halo parejo de punta a punta.
                LabeledSlider(
                    label = "Expansión (spread)",
                    value = glowSpread,
                    range = 0f..1f,
                    enabled = glowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    displayScale = 100f
                ) { glowSpread = it; onChanged() }

                // Distancia/ángulo — antes el halo salía SIEMPRE
                // centrado; con esto se puede correr hacia un lado
                // (mismo mecanismo que la sombra proyectada).
                LabeledSlider(
                    label = "Distancia",
                    value = glowDistance,
                    range = 0f..1f,
                    enabled = glowControlsEnabled,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    displayScale = 100f
                ) { glowDistance = it; onChanged() }

                LabeledSlider(
                    label = "Ángulo",
                    value = glowAngle,
                    range = 0f..360f,
                    enabled = glowControlsEnabled && glowDistance > 0.001f,
                    valueLabel = { "${it.roundToInt()}°" }
                ) { glowAngle = it; onChanged() }

                ColorSwatchPickerButton(
                    label = "Color del resplandor",
                    colorArgb = glowColor,
                    enabled = glowControlsEnabled
                ) { c -> glowColor = c; onChanged() }

                // Degradado del resplandor (dos colores) — A PEDIDO DEL
                // USUARIO. Mismo patrón EXACTO de `Switch` que "Degradado
                // (2 colores)" en Contorno (ver [EffectsCategoryContorno]):
                // apagado (default) = un solo color sólido, sin cambios.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Degradado (2 colores)",
                        color = if (glowControlsEnabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = glowGradientEnabled,
                        onCheckedChange = {
                            glowGradientEnabled = it
                            onChanged()
                        },
                        enabled = glowControlsEnabled,
                        modifier = Modifier.height(20.dp).scale(0.7f),
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandPurpleLight)
                    )
                }
                if (glowGradientEnabled) {
                    ColorSwatchPickerButton(
                        label = "Segundo color (punta del halo)",
                        colorArgb = glowColor2,
                        enabled = glowControlsEnabled
                    ) { c -> glowColor2 = c; onChanged() }
                }

                // Modo de mezcla — A PEDIDO DEL USUARIO ("le falta... una
                // opción de blend mode (Screen/Add/Lighten) — hoy solo
                // hay un modo de mezcla"). "Normal" preserva el
                // comportamiento de siempre; los otros tres son los
                // modos estándar de cualquier "Outer Glow" profesional
                // (ver [com.yeivikas.olyzecs.engine.effects.GlowBlendMode]
                // para el detalle matemático de cada uno). Reutiliza
                // [ThreeWayToggle] (ya generalizado a N etiquetas, pese al
                // nombre) para no duplicar el selector segmentado que ya
                // usa "Posición" en Contorno.
                Text(
                    "Modo de mezcla",
                    color = Color.White.copy(alpha = if (glowControlsEnabled) 0.55f else 0.25f),
                    style = MaterialTheme.typography.labelSmall
                )
                ThreeWayToggle(
                    labels = listOf("Normal", "Screen", "Add", "Lighten"),
                    selectedIndex = when (glowBlendMode) {
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL -> 0
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.SCREEN -> 1
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.ADD -> 2
                        com.yeivikas.olyzecs.engine.effects.GlowBlendMode.LIGHTEN -> 3
                    },
                    enabled = glowControlsEnabled
                ) { index ->
                    glowBlendMode = when (index) {
                        0 -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.NORMAL
                        1 -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.SCREEN
                        2 -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.ADD
                        else -> com.yeivikas.olyzecs.engine.effects.GlowBlendMode.LIGHTEN
                    }
                    onChanged()
                }
}









/**
 * Fila de sub-pestañas (chips) de la pestaña "Efectos" — una por cada
 * categoría de controles (Básicos, Contorno, Resplandor, Presets,
 * Sombra, Sombra relleno, Sombra contacto, Reflejo, Light wrap).
 * Puramente de navegación: no toca ningún valor de efecto, solo decide
 * qué grupo de controles se muestra debajo. Deslizable horizontalmente
 * porque no entran todas las categorías en el ancho de pantalla.
 */
@Composable
private fun EffectsCategoryTabs(
    categories: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEachIndexed { index, label ->
            val isActive = index == selected
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        if (isActive) BrandPurpleLight.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isActive) BrandPurpleLight else Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(17.dp)
                    )
                    .clickable { onSelected(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Botón compacto de "muestra de color" (swatch circular + etiqueta) que
 * abre, al tocarlo, un mini-popup con [ColorWheelPicker] — mismo
 * componente que ya usa la pestaña "Recolor" (ver [LayerColorEditPanel]),
 * reutilizado tal cual acá para no duplicar la rueda de color. Pensado
 * para colores "secundarios" de un efecto (sombra, contorno, resplandor)
 * donde una rueda grande siempre visible ocuparía demasiado espacio del
 * panel — a diferencia de "Recolor", donde el color ES el efecto
 * principal y por eso ahí la rueda queda fija y grande.
 *
 * El HSV interno del popup se deriva de [colorArgb] cada vez que ese
 * color cambia desde afuera (`remember(colorArgb)`) — la fuente de
 * verdad para el resto de la app sigue siendo siempre el Int ARGB en
 * [ImageEffectsParams], nunca un HSV propio de este botón que pudiera
 * desincronizarse.
 */
@Composable
private fun ColorSwatchPickerButton(
    label: String,
    colorArgb: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onColorChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val initialHsv = remember(colorArgb) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(colorArgb, out)
        out
    }
    var wheelHue by remember(colorArgb) { mutableStateOf(initialHsv[0]) }
    var wheelSat by remember(colorArgb) { mutableStateOf(initialHsv[1]) }
    var wheelVal by remember(colorArgb) { mutableStateOf(initialHsv[2]) }

    fun applyCurrentColor() {
        onColorChange(android.graphics.Color.HSVToColor(floatArrayOf(wheelHue, wheelSat, wheelVal)))
    }

    // BUG REAL corregido acá: la mayoría de estos colores arrancan en
    // NEGRO por defecto (shadowColor, contactShadowColor, etc.), y negro
    // en HSV es Value=0 — con V=0, HSVToColor(h, s, 0) da negro sin
    // importar qué matiz/saturación se elija (V=0 anula todo lo demás).
    // Resultado: tocar la rueda de color no cambiaba nada visualmente
    // hasta que el usuario ADEMÁS se acordaba de subir "Brillo" a mano,
    // cada vez, en cada uno de estos selectores. Cualquier selector de
    // color nativo (iOS/Android) evita esto: si el brillo está "atascado"
    // en el piso cuando se toca el anillo de matiz/saturación, lo levanta
    // solo a un valor utilizable — acá se replica ese comportamiento.
    fun onWheelHueSatChange(h: Float, s: Float) {
        wheelHue = h
        wheelSat = s
        if (wheelVal < 0.05f) {
            wheelVal = 1f
        }
        applyCurrentColor()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled) 0.85f else 0.35f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(colorArgb))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .alpha(if (enabled) 1f else 0.35f)
                .then(
                    if (enabled) Modifier.clickable { expanded = true } else Modifier
                )
        )
    }

    if (expanded && enabled) {
        Popup(
            popupPositionProvider = BelowAnchorCenteredPopupPositionProvider(gapPx = 8),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                modifier = Modifier
                    .width(216.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp)),
                color = SurfaceTintedElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ColorWheelPicker(
                            hue = wheelHue,
                            saturation = wheelSat,
                            brightness = wheelVal,
                            onColorChange = { h, s -> onWheelHueSatChange(h, s) },
                            modifier = Modifier.size(160.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LabeledSlider(
                        label = "Brillo",
                        value = wheelVal,
                        range = 0f..1f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        // A PEDIDO DEL USUARIO — cada opción tiene su propio valor máximo
                        // interno distinto (algunas 0f..1f, otras -1f..1f, etc.), pero todas
                        // estas se MUESTRAN como porcentaje (x100) en pantalla. Sin
                        // `displayScale` acá, escribir a mano "83" en un control interno
                        // 0f..1f se comparaba contra ese 0..1 crudo (no contra el 0..100 que
                        // el usuario ve en la etiqueta) — por eso alcanzaba con tipear un
                        // solo dígito mayor a 1 para que el campo se autocorrigiera a "1" de
                        // una, sin dejar escribir el resto ("apenas se puede poner el 1").
                        // `displayScale = 100f` hace que el campo de edición manual (y su
                        // límite máximo/mínimo) trabajen en la MISMA escala que el usuario ve
                        // y tipea (0-100, o -100..100), no en la escala cruda interna.
                        displayScale = 100f
                    ) { v ->
                        wheelVal = v
                        applyCurrentColor()
                    }
                }
            }
        }
    }
}
