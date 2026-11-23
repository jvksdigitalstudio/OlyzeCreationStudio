# ADR-016 — Modo de trabajo: vertical u horizontal (elegido por el usuario)

- **Fecha:** 2026-11-03
- **Estado:** **APLAZADO (2026-11-03)** por decisión del dueño del producto hasta que el editor esté más maduro. Plan y decisiones D1–D3 vigentes; sin código. Disparadores para retomarlo en `PENDIENTES.md` §7. Las preguntas P1–P3 (§9) siguen abiertas.
- **Origen:** Fase 3 de `docs/MIGRACION_API36.md` (Android 16 ignora el bloqueo vertical en tablets; la salida temporal caduca con `targetSdk 37`). Referencia de diseño pedida por el dueño del producto: captura de CapCut en tablet (biblioteca/herramientas a la izquierda, preview al centro, panel de detalles a la derecha, línea de tiempo abajo a todo el ancho).

## 1. Decisiones ya tomadas por el dueño del producto
| # | Decisión |
|---|---|
| D1 | El modo NO sigue solo a la tablet: lo **elige el usuario**. Al abrir la app por primera vez se detecta si el dispositivo es tablet o teléfono y se muestra un aviso inicial para elegir **Vertical** u **Horizontal**. Se puede cambiar después en Ajustes. |
| D2 | Si el usuario elige Horizontal con la tablet en vertical, **la app gira la pantalla sola** (mientras Android lo permita). |
| D3 | La primera entrega horizontal es **completa**: preview + cajón de herramientas lateral + línea de tiempo ancha + **panel de detalles a la derecha**. |

## 2. Restricciones técnicas (verificadas)
1. Android 16 con `targetSdk 36`, en pantallas de ancho mínimo ≥ 600 dp, **ignora** `screenOrientation`, `resizeableActivity=false` y también `setRequestedOrientation()`. Se verificó en la tablet del proyecto (CI #672/#673): sin la propiedad de §2.2 la orientación no se respetaría; con ella el bloqueo vertical se mantiene.
2. `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (ya declarada, `AndroidManifest.xml`) es la salida oficial y **caduca con `targetSdk 37`**. Mientras exista, se espera que `requestedOrientation` en tiempo de ejecución también se respete — **esto es una hipótesis hasta que el WP-2 la pruebe en la tablet** (§6, riesgo R1).
3. Después de `targetSdk 37` el sistema decide el giro. La app solo podrá elegir la DISPOSICIÓN (§4.3 define la degradación).
4. `EditorScreen.kt` es un único composable de ~6.000 líneas con cientos de variables locales capturadas por lambdas. Partirlo en archivos no es viable sin compilador/pruebas de UI; el cambio se hace **sin mover estado**.

## 3. Arquitectura

### 3.1 Modelo y persistencia (lógica pura, probable en JVM)
- `WorkspaceMode { VERTICAL, HORIZONTAL }`.
- `DeviceClass { PHONE, TABLET }` — se decide con `smallestScreenWidthDp ≥ 600` (el mismo corte que usa Android para la regla de §2.1). Función pura `deviceClassFor(smallestWidthDp)`.
- `WorkspaceModePrefs` — mismo patrón que `UserColorPrefs` (`SharedPreferences`), pero con un `interface KeyValueStore` inyectable para probarlo sin Android. Clave `workspace_mode_v1`; **ausente = el usuario aún no eligió** (dispara el aviso inicial). No se guarda "por defecto" para no confundir "no eligió" con "eligió vertical".
- `EditorLayoutPolicy.resolve(mode, widthDp, heightDp, …): EditorLayout` — decide `COMPACT` o `WIDE` y qué paneles caben (§4.2). Pura.

### 3.2 Orientación (`platform/OrientationController`)
| Modo | `requestedOrientation` |
|---|---|
| Sin elegir (aviso inicial) | `UNSPECIFIED` (el aviso se ve en la orientación del usuario) |
| VERTICAL | `SENSOR_PORTRAIT` (permite 0° y 180°) |
| HORIZONTAL | `SENSOR_LANDSCAPE` (permite 90° y 270°) |
- El manifest pasa de `screenOrientation="portrait"` a `unspecified`; la propiedad de §2.2 se conserva. `configChanges` ya evita recrear la Activity al girar.
- Se aplica al arrancar, al elegir en el aviso y al cambiar en Ajustes. **Un solo punto de entrada**, igual que `OlyzeSystemBars` (ADR/Fase 1).

### 3.3 Interfaz de elección
- **Aviso inicial** (D1): diálogo modal no descartable sin elegir, dos tarjetas seleccionables. Se muestra una sola vez (cuando `workspace_mode_v1` no existe). Propuesta de texto (ES):
  - Título: **«Elige tu modo de trabajo»**
  - Cuerpo: «Olyze Creation Studio se adapta a cómo prefieres editar. Puedes cambiarlo en cualquier momento desde Ajustes.»
  - **Vertical** — «Vista compacta: preview arriba y línea de tiempo abajo. Ideal para editar con una mano.»
  - **Horizontal** — «Espacio ampliado: preview, herramientas, detalles y línea de tiempo a la vez. Ideal para tablet.»
  - Insignia **«Recomendado para tu dispositivo»**: Horizontal en tablet, Vertical en teléfono.
- **Ajustes**: hoy NO existe pantalla de ajustes (el menú `AppMenuDrawer` solo tiene «Registro de errores» y «Papelera de proyectos»). Se añade la entrada **«Ajustes»** con una sección «Modo de trabajo» que reutiliza las mismas dos tarjetas. Una sola composable (`WorkspaceModePicker`) para ambos sitios.

## 4. Diseño del editor horizontal (WIDE)

### 4.1 Regla de oro: el árbol de composición no cambia de forma
La zona del preview vive **siempre dentro de la misma `Row`**, en ambos modos; solo cambian los modificadores y qué paneles laterales aparecen. Así, cambiar de modo no destruye el `GLPreview`, la selección ni el estado local de `EditorScreen` (lo contrario sí los reiniciaría). Se verifica con una prueba estructural.

### 4.2 Regiones
```
┌──────────────── barra superior (ya existente) ────────────────┐
│ [cajón de herramientas*] │      PREVIEW      │ DETALLES (der.) │  ← fila superior, ~50 % del alto
├──────────────────────────┴───────────────────┴─────────────────┤
│        línea de tiempo a todo el ancho + barra inferior         │
└─────────────────────────────────────────────────────────────────┘
 * el cajón Módulos/Control/Keyframes se abre a la izquierda en vez de tapar la línea de tiempo
```
- **Cajón de herramientas (izquierda):** el `SectionPlaceholderPanel` ya existente, que es autónomo (recibe su tamaño por `Modifier`); se construye en UN solo sitio (`moduleDrawer`) y se coloca a la izquierda del preview en WIDE o sobre la línea de tiempo en COMPACT. Ancho propuesto 360–460 dp.
- **Preview (centro):** el Box actual, con `weight(1f)`.
- **Detalles (derecha):** panel nuevo, siempre visible en WIDE. Ancho propuesto 280–340 dp. Contenido v1 en §4.4.
- **Línea de tiempo:** igual que hoy, a todo el ancho; la barra Módulos/Control/Keyframes sigue debajo.
- **Fracción del alto del preview:** 50 % en WIDE (46 % en COMPACT).
- **Ancho mínimo del preview:** 480 dp. Si al abrir el cajón el preview quedaría menor, el cajón se superpone a la línea de tiempo (comportamiento COMPACT) en lugar de apretar el preview. Se decide en `EditorLayoutPolicy`, con pruebas por tamaño de tablet (10″ ≈ 960 dp, 11″ ≈ 1.100 dp, 12,9″ ≈ 1.280 dp).
- **Ventanas flotantes** (Reverb, ZoomX, color…): sin cambios; su área ya se mide con `onSizeChanged` y se recorta sola al cambiar de tamaño.

### 4.3 Degradación
`HORIZONTAL` solo produce `WIDE` si la ventana realmente es ancha (`widthDp ≥ 840` y apaisada). Si no (p. ej. Android no permitió girar tras `targetSdk 37`, o pantalla dividida angosta), se muestra `COMPACT` y un aviso discreto «Gira el dispositivo para usar el modo horizontal». Nunca una disposición horizontal aplastada.

### 4.4 Panel de detalles (v1)
Misma idea que la captura de CapCut («Detalles»), con la información que la app ya tiene:
- **Sin selección:** datos del proyecto — nombre, relación de aspecto, resolución, fps (reutiliza la fuente de datos de `ProjectInfoPanel`).
- **Capa de imagen seleccionada:** nombre, posición X/Y, escala, rotación, opacidad, con los mismos campos numéricos del editor (`LabeledSlider`) — sin duplicar lógica de edición.
- **Pista de audio seleccionada:** nombre, volumen, panorama, duración.
- Atajos a «Control» y «Módulos» (abren el cajón izquierdo).
- *Queda fuera de v1:* edición avanzada de keyframes en este panel (sigue en la pestaña Keyframes).

## 5. Paquetes de trabajo (en orden; cada uno termina con CI en verde + prueba en la tablet)
| WP | Contenido | Toca UI | Riesgo | Se entrega para probar |
|---|---|---|---|---|
| **WP-1** Cimientos | `WorkspaceMode`, `DeviceClass`, `WorkspaceModePrefs` (+`KeyValueStore`), `EditorLayoutPolicy.resolve`, pruebas JVM (tamaños de tablet, teléfono, transiciones). | No | Bajo | CI verde. Nada visible. |
| **WP-2** Orientación + elección | `OrientationController`; manifest `unspecified`; aviso inicial; entrada «Ajustes» + sección «Modo de trabajo». | Poco (diálogos y menú) | **Medio (R1)** | Elegir Horizontal → ¿gira sola la tablet? Es la prueba de la hipótesis §2.2. El editor aún se ve como hoy. |
| **WP-3** Disposición WIDE | `moduleDrawer` único; `Row` del preview + cajón lateral; ancho mínimo del preview; aviso de degradación. Vertical intacto. | `EditorScreen` (3 puntos) | **Alto** | Horizontal: preview + cajón + timeline ancha. Vertical: idéntico a hoy. |
| **WP-4** Panel de detalles | `EditorDetailsPanel` y sus tres estados. | Pantalla nueva | Medio | Panel derecho completo. |
| **WP-5** Cierre | Teclado y gesto atrás en horizontal; exportación; rendimiento (APK perf); actualizar `MIGRACION_API36.md`, README, este ADR. | No | Bajo | Lista de verificación completa. |

Cada WP se entrega como **proyecto completo empaquetado** y no se pasa al siguiente sin tu confirmación en la tablet.

## 6. Riesgos
| # | Riesgo | Mitigación |
|---|---|---|
| R1 | `requestedOrientation` en ejecución podría no respetarse en la tablet pese a la propiedad de §2.2. | Se prueba en el WP-2, antes de invertir en el diseño. Si falla: el modo solo elige disposición y el giro queda al usuario (mismo camino que tras `targetSdk 37`, §4.3). El resto del plan no cambia. |
| R2 | `EditorScreen` monolítico: un error de llaves/ámbito en el WP-3 rompe el editor. | Edición solo en 3 puntos; verificación de llaves con `tools/check_kotlin_static.py` y un localizador de bloques propio; prueba estructural de «misma forma de árbol»; el camino COMPACT no cambia. |
| R3 | La caducidad de la propiedad (`targetSdk 37`) deja sin giro automático. | Ya cubierto por §4.3 y D2 («mientras Android lo permita»). Fecha de revisión: antes de subir `targetSdk`. |
| R4 | Teléfono en horizontal: el alto (~390 dp) no alcanza para preview + varias filas de línea de tiempo. | Ver pregunta P1. |
| R5 | Cambiar de modo a mitad de edición. | §4.1 (árbol estable) + `configChanges`; se prueba con proyecto abierto y reproducción en curso. |
| R6 | Sin compilador en este entorno. | Igual que Fases 1–2: toda entrega pasa por CI y por la tablet antes de seguir. |

## 7. Pruebas
- **JVM (WP-1):** `deviceClassFor`; `resolve` para teléfono vertical/horizontal, tablets de 10″/11″/12,9″ en ambos sentidos, pantalla dividida angosta, ancho mínimo del preview con y sin cajón; persistencia con `KeyValueStore` falso (sin valor → no eligió; valor corrupto → tratado como no eligió).
- **Estructurales (WP-3/4):** misma `Row` en ambos modos; `moduleDrawer` con un único punto de construcción; el camino COMPACT conserva `fillMaxHeight(0.46f)`; ningún `screenOrientation` fijo en el manifest.
- **Dispositivo:** lista de cada WP (§5) + la general de `MIGRACION_API36.md` §6.3 y §8.6.

## 8. Fuera de alcance
AGP 9 / `compileSdk 37` · Coil 3 · rediseño de las ventanas flotantes · edición de keyframes en el panel de detalles · temas claro/oscuro.

## 9. Preguntas abiertas
- **P1 — Teléfonos:** ¿el aviso inicial también se muestra en teléfono (con «Vertical» recomendado) y se permite Horizontal, o en teléfono se omite y queda siempre en vertical? Recomendación: **omitirlo en teléfono en v1** (R4) y habilitarlo cuando haya un diseño pensado para ~390 dp de alto.
- **P2 — Contenido del panel de detalles:** ¿bien lo propuesto en §4.4 para la v1?
- **P3 — Idioma del texto:** el aviso usa «Elige / Puedes» (neutro); el resto de la app usa voseo («Tenés», «Revisá»). ¿Cuál se mantiene?
