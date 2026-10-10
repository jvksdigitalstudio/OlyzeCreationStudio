# Auditoría de efectos implementados

**Estado:** Auditoría de código completada. Sin acciones de código todavía
— este documento es el inventario de qué se revisó, qué se confirmó
correcto y qué queda con hallazgos abiertos.

**Alcance:** todos los efectos vigentes al momento de esta auditoría,
según el propio inventario de categorías de la app:

- Pestaña **Efecto**: Contorno, Resplandor, Sombra, Reflejo, Distorsión
- Pestaña **Color**: Básico, Recolor
- Pestaña **3D**: Básico

**Método:** revisión estática de código fuente completo —
`ImageEffects.kt` (Contorno/Resplandor/Sombra/Reflejo),
`DistortionRasterizer.kt`/`DistortionField.kt`/`DistortionFreezeMask.kt`
(Distorsión), `Extrude3D.kt`/`Mesh3D.kt` (3D Básico), y el flujo de
Recolor/Color Básico en `EditorViewModel.kt`. Se buscó específicamente la
misma clase de problema ya confirmada en Resplandor: un control que la UI
expone como si tuviera efecto real, pero que — por cómo está compuesto el
pipeline (bake destructivo sobre buffer aislado, sin acceso al fondo real)
— matemáticamente no puede producir el resultado que promete. También se
barrió el motor completo (`engine/`) buscando cualquier otro uso de
`PorterDuff`/`Xfermode`/`BlendMode`, ya que ese es el patrón concreto que
delató el bug del Resplandor.

**Límite honesto de este método:** es revisión de código, no verificación
en dispositivo real. Confirma o descarta problemas de lógica/matemática
del pipeline (como el del Resplandor), pero no reemplaza una pasada manual
de QA tocando cada slider en la app — eso queda pendiente y se recomienda
al final.

---

## Resplandor (Outer Glow)

**Estado: con hallazgo confirmado — ya documentado aparte.**

Ver `docs/pending-work/GLOW_BLEND_MODE.md`. Resumen: el "Modo de mezcla"
(Normal/Screen/Add/Lighten) no tiene efecto visual porque se aplica dentro
de un bake destructivo sobre un buffer que está transparente en el punto
donde se pinta el glow — no hay destino real contra el cual mezclar. El
resto del panel (Intensidad, Difuminado, Spread, Distancia, Ángulo,
Degradado de 2 colores) es correcto y no depende de este problema.

## Contorno (Outline/Stroke)

**Estado: sin hallazgos.**

Revisado: `outlineIntensity`, `outlineColor`/`outlineColor2` +
`outlineGradientEnabled`, `outlineFeather`, `outlinePosition`
(`OUTSIDE`/`CENTER`/`INSIDE`).

- No tiene control de "modo de mezcla" — no aplica el patrón de riesgo
  del Resplandor.
- El trazo se construye por dilatación/erosión de la silueta a umbral
  (`buildDilatedOutline`) y se pinta en dos pasadas según
  `outlinePosition`: la porción **exterior** se pinta ANTES del sujeto
  (queda detrás, solo asoma lo que sobresale del borde real) y la porción
  **interior** se pinta DESPUÉS del sujeto (línea 2731 en adelante de
  `ImageEffects.kt`), a propósito, para quedar por encima del color del
  sujeto en vez de taparse detrás de él. El orden está resuelto
  correctamente para los tres modos.
- El degradado de 2 colores interpola sobre el propio trazo ya
  construido, sin depender de ningún fondo externo — no tiene el
  problema del Resplandor porque nunca prometió "mezclarse contra algo
  detrás".

## Sombra (proyectada + relleno + contacto)

**Estado: sin hallazgos.**

Revisado: sombra proyectada (`shadowIntensity/Blur/Spread/Scale/Noise/
Distance/AngleDeg/Color/SkewDegrees/PerspectiveAmount/FadeByDistance/
OpacityCurve/ContactHardening`), sombra de relleno (`fillShadow*`),
sombra de contacto (`contactShadow*`, multi-punto vía
`ContactShadowPoint`), y `groundWallBreak` (quiebre piso/pared,
compartido con Reflejo).

- Sí existe un control de mezcla acá (`shadowBlendMultiply`, Normal vs.
  Multiplicar) — a diferencia del Resplandor, este **funciona
  correctamente**: mezcla la sombra proyectada + relleno + contacto
  **entre sí, dentro de su propio buffer** (`shadowGroupPixels`, vía
  `blitBlend`) ANTES de pintarse sobre el canvas principal — nunca
  promete mezclarse contra el fondo real de la escena, así que no
  necesita ese destino y no tiene el problema del Resplandor. El propio
  comentario del código (línea ~2412) es explícito sobre esta diferencia.
- El quiebre piso/pared (`groundWallBreak`) parte la sombra en dos tramos
  con pivotes distintos (`buildPiecewiseSkew`) — revisado el cálculo de
  `seamOffset`/`nearScaleAtSeam`, la costura entre tramos usa la escala
  ya aplicada al tramo cercano, evitando el salto visual que daría usar
  la altura cruda sin comprimir.
- El `blitBlend` con `MULTIPLY` está implementado a mano en vez de usar
  `PorterDuff.Mode.MULTIPLY` nativo — el comentario del código (línea
  ~3042) documenta que la versión nativa de Android borra la sombra en
  vez de oscurecerla, y que la implementación a mano replica la fórmula
  correcta del estándar. Verificado que es la fórmula correcta.

## Reflejo

**Estado: sin hallazgos.**

Revisado: `reflectionIntensity/Gap/Length/Blur/Noise/SkewDegrees/
TintIntensity/TintColor/EdgeFade/RippleIntensity/RippleScale/
OpacityCurve/Fresnel/Perspective/ProgressiveBlur`.

- No tiene control de modo de mezcla — el tinte (`reflectionTintColor`)
  reemplaza color directamente sobre los píxeles del reflejo, no
  "mezcla" contra nada externo, así que no aplica el patrón de riesgo.
- Efecto Fresnel (refuerzo de opacidad hacia el extremo lejano) y curva
  de opacidad (`reflectionOpacityCurve`) operan sobre el propio degradado
  del reflejo — coherente, sin dependencia de fondo.
- El desdoblamiento piso/pared usa el mismo mecanismo ya verificado en
  Sombra (comparten `groundWallBreak`).

## Distorsión

**Estado: sin hallazgos en la revisión estática.**

Revisado: `DistortionRasterizer.kt`, `DistortionField.kt`,
`DistortionFreezeMask.kt`, `DistortionModels.kt`, `DistortionTools.kt`.

- No usa `PorterDuff`/`Xfermode`/`BlendMode` en ningún punto — es
  remuestreo geométrico de píxeles (warping por malla), no compositing
  por capas, así que la clase de bug del Resplandor no aplica
  estructuralmente acá.
- Sin marcadores `TODO`/`FIXME`/comentarios de limitación conocida sin
  resolver.
- No se encontró documentación de "BUG REAL corregido" pendiente de
  verificar — a diferencia de otras zonas del motor, esta parece no
  haber tenido reportes de regresión todavía.

## Color — Básico y Recolor

**Estado: sin hallazgos en la revisión estática; fuera del alcance
profundo de esta auditoría.**

- Ninguna de las dos herramientas expone un control de "modo de mezcla"
  en la UI (confirmado en las capturas de categorías) — no hay una
  promesa de blend-mode que verificar.
- `commitLayerRecolor`/`previewLayerRecolor` en `EditorViewModel.kt`
  siguen el mismo patrón de bake-y-reemplazo que el resto del motor
  (`sourceUri` actualizado, consistente con Contorno/Sombra/Reflejo).
- La lógica de pintado del color/degradado en sí (rueda de color, modo
  Negro & Blanco) vive en `EditorScreen.kt`/`LayerDialogs.kt`, no en
  `ImageEffects.kt` — no se auditó línea por línea en esta pasada por
  volumen; queda como pendiente de una revisión dedicada si se quiere el
  mismo nivel de detalle que el resto.

## 3D — Básico

**Estado: sin hallazgos en la revisión estática.**

Revisado: `Extrude3D.kt`, `Mesh3D.kt`.

- Sin uso de `PorterDuff`/`Xfermode`/`BlendMode`.
- Sin marcadores `TODO`/`FIXME` ni comentarios de limitación conocida sin
  resolver.
- Es geometría/malla + render propio, dominio separado del pipeline de
  compositing 2D — no comparte la superficie de riesgo del Resplandor.

---

## Conclusión de la auditoría

De los 8 efectos revisados, **el único con un control que no cumple lo
que promete es el Resplandor** (Modo de mezcla) — ya diagnosticado y con
las dos opciones de solución documentadas en `GLOW_BLEND_MODE.md`. El
resto (Contorno, Sombra, Reflejo, Distorsión, Color Básico, Recolor, 3D
Básico) no muestra el mismo patrón de riesgo ni otro equivalente
detectable por revisión de código: no tienen controles de "modo de
mezcla" contra un fondo que no pueden ver, y donde sí existe un control
de mezcla (`shadowBlendMultiply`), está resuelto correctamente porque
mezcla contra un destino real (la propia familia de sombras), no contra
algo fuera de su alcance.

## Pendiente recomendado

- **QA manual en dispositivo** de cada slider/control de las 8
  categorías — esta auditoría es de código, no de comportamiento en
  runtime; puede haber casos borde (valores extremos combinados,
  overflow de color, orden de capas con muchos efectos activos a la vez)
  que solo aparecen probando la app real.
- **Recolor / Color Básico** quedó fuera del nivel de detalle del resto
  por vivir en `EditorScreen.kt`/`LayerDialogs.kt` en vez de
  `ImageEffects.kt` — si se quiere el mismo nivel de auditoría, conviene
  una pasada dedicada a esos dos archivos.
