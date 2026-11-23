# FASE 16 — Visor inmersivo en una sola `Window`: eliminación de raíz de la franja morada/azul

> Origen: reporte del usuario/product owner con capturas de dos imágenes
> de prueba distintas, ambas mostrando la misma franja fija arriba y
> abajo del visor de imágenes en modo inmersivo, después de que FASE 15
> ya había endurecido exhaustivamente la `Window` del `Dialog` del visor
> (seis pasos independientes, cada uno con su propio `runCatching` +
> log). El reporte fue explícito: "en pantalla completa no debería verse
> ninguna franja" — la franja seguía presente pese a ese endurecimiento.
>
> Este documento cubre lo que se **implementó por inspección de código**
> en esta sesión — mismo criterio de honestidad que FASE 14/15: sin
> compilador disponible en este entorno de trabajo (sin red para
> resolver dependencias Gradle ni `gradlew` presente en el repositorio
> entregado), todo lo de acá está razonado a mano contra la API
> documentada de Compose/Android — balance de llaves y de funciones
> verificado por script, no hay verificación de tipos real. **Falta
> compilar y probar en dispositivo real** antes de dar esto por cerrado
> — ver "Estado real / pendiente" al final.

## Resumen ejecutivo

| # | Decisión / problema | Resolución | Archivo(s) |
|---|---|---|---|
| 1 | Causa raíz real de la franja (no diagnosticada en FASE 15): al abrir una imagen desde la papelera había **tres** `Window` de Android apiladas al mismo tiempo — la de `MainActivity`, la de `ProjectsTrashScreen` (que se abría con su propio `Dialog`) y la de `FilePreviewDialog` (otro `Dialog` más, encima de la anterior). Con `Window`s superpuestas, el compositor del sistema puede seguir pintando restos del `statusBarColor`/`navigationBarColor` de una `Window` de más abajo en la fila de píxeles de las barras, sin importar qué tan bien configurada esté la de más arriba — exactamente la franja reportada, y la razón por la que ningún ajuste adicional sobre la `Window` del visor (FASE 15) podía garantizarlo al 100% | Eliminadas las **dos** `Window` de diálogo de en medio: `ProjectsTrashScreen` y `FilePreviewDialog` ya no llaman a `Dialog(...)` — ambos son ahora `Surface(Modifier.fillMaxSize())` normales, compuestos dentro de la única `Window` real de `MainActivity`, mismo patrón que ya usa esa `Activity` para alternar entre "Mis proyectos" y el editor | `FilePreviewDialog.kt`, `ProjectsTrashScreen.kt` |
| 2 | El modo inmersivo (barras ocultas, fondo negro de borde a borde, transparencia real) apuntaba a la `Window` del `Dialog` del visor (vía `DialogWindowProvider`), que ya no existe | Nueva función `ImmersiveActivityWindow`, que resuelve la `Window` real con `Context.findActivity()` (nuevo helper, recorre la cadena de `ContextWrapper`) y le aplica exactamente los mismos seis pasos que FASE 15 ya había probado (fondo negro, `decorFitsSystemWindows(false)`, barras transparentes, `isStatusBarContrastEnforced = false`, `layoutInDisplayCutoutMode = ALWAYS`, insets consumidos en el `decorView`) | `FilePreviewDialog.kt` |
| 3 | Esa `Window` real es la MISMA que usa el resto de la app (`ProjectsScreen`, `EditorScreen`, etc.) — a diferencia de la `Window` de un `Dialog`, que se descarta al cerrarse, esta persiste durante toda la vida de la `Activity`: dejarla en modo inmersivo (barras ocultas, transparente, fondo negro) al cerrar el visor rompería el resto de la app | `onDispose` ahora restaura EXPLÍCITAMENTE cada propiedad tocada a su valor real de `Theme.Olyze` (`brand_purple_deep` en `windowBackground`/`statusBarColor`/`navigationBarColor`, `isStatusBarContrastEnforced = true`, `layoutInDisplayCutoutMode` a `DEFAULT`, listener de insets a `null`) — no alcanza con "mostrar las barras", cada paso de la ida tiene su paso de vuelta simétrico | `FilePreviewDialog.kt` |
| 4 | Un `Dialog` de Compose manejaba el botón/gesto "atrás" del sistema automáticamente vía `onDismissRequest`; al dejar de ser un `Dialog`, ese mecanismo desaparece | `BackHandler(onBack = onDismiss)` explícito agregado al nivel superior de `FilePreviewDialog` — mismo mecanismo que `ProjectsTrashScreen` ya usaba para su propio "atrás" de tres niveles; por ser el `BackHandler` compuesto más recientemente (LIFO de `OnBackPressedDispatcher`), sigue cerrando primero el visor y recién después, si se vuelve a presionar, el navegador de la papelera — mismo comportamiento observable que antes | `FilePreviewDialog.kt` |
| 5 | `ProjectsTrashScreen` perdía, al sacar el `Dialog`, un nivel de indentación en ~130 líneas de UI | Bloque completo reindentado con script determinístico (resta de 4 espacios línea por línea en el rango exacto), sin tocar ni una palabra del contenido — verificado después por conteo de llaves del archivo completo | `ProjectsTrashScreen.kt` |
| 6 | Imports que quedaban sin uso tras sacar `Dialog`/`DialogProperties`/`DialogWindowProvider`/`LocalView`/`android.view.Window` de `FilePreviewDialog.kt`, y `Dialog`/`DialogProperties` de `ProjectsTrashScreen.kt` | Limpiados; agregados los que sí pasan a usarse (`android.app.Activity`, `android.content.ContextWrapper`, `androidx.activity.compose.BackHandler`, `androidx.core.content.ContextCompat`) | `FilePreviewDialog.kt`, `ProjectsTrashScreen.kt` |

## 1. Por qué FASE 15 no alcanzaba — el diagnóstico que faltaba

FASE 15 asumió (razonablemente, dado lo que se veía) que la franja era
un problema de **configuración** de la `Window` del `Dialog` del visor:
alto mal calculado, `windowBackground` con relleno del tema de diálogo
flotante, scrim de contraste de Android 10+, etc. Todo eso era cierto y
necesario — pero no era la causa completa.

Lo que FASE 15 no consideró es que ese `Dialog` del visor **nunca
estaba solo**: `ProjectsTrashScreen` (el navegador de la papelera desde
donde se abre cualquier imagen) también se monta con su propio
`Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false))`.
Como `FilePreviewDialog` se invoca como una llamada más dentro del
cuerpo de `ProjectsTrashScreen` (hermana de ese `Dialog`, no anidada
dentro de su lambda de contenido — en Compose, una llamada a `Dialog`
solo mueve a una `Window` nueva el subárbol de SU PROPIO lambda de
contenido, no el resto de llamadas del componible que la invoca), al
tocar una imagen terminaban coexistiendo:

1. La `Window` real de `MainActivity` (abajo del todo).
2. La `Window` del `Dialog` de `ProjectsTrashScreen` (el navegador de
   archivos, pantalla completa, color `brand_purple_deep`).
3. La `Window` del `Dialog` de `FilePreviewDialog` (el visor, encima de
   todo — por ser la añadida más recientemente).

Con tres `Window`s de Android apiladas, el compositor del sistema puede
— documentado como comportamiento variable por fabricante/versión, no
un bug de una sola API puntual — seguir mostrando en la fila de píxeles
de las barras del sistema un resto del color estático de una `Window`
de más abajo en la pila, sin importar cuán bien configurada esté la de
más arriba. Esa es la causa raíz real: **la cantidad de `Window`s**, no
la configuración de una de ellas.

## 2. Por qué la solución es sacar `Window`s, no ajustar más la existente

La única forma de eliminar esa clase de bug por completo — no
reducirla, eliminarla — es no tener más de una `Window` en juego. Por
eso esta fase saca el `Dialog` tanto de `FilePreviewDialog` como de
`ProjectsTrashScreen`: con ambos convertidos en `Surface` normales,
compuestos directamente dentro de la única `Window` real de
`MainActivity`, no queda ninguna otra `Window` por debajo cuyo color
pueda "sangrar". El modo inmersivo del visor (barras ocultas,
transparencia, fondo negro) se aplica entonces sobre ESA `Window` —
la única que existe — y se restaura por completo al cerrar, para no
afectar al resto de la app (que sigue usando esa misma `Window`,
siempre, para todo).

Esto además reduce, no aumenta, la complejidad del código: ya no hace
falta resolver `DialogWindowProvider` en cada frame hasta que aparezca
(mecanismo de FASE 15 para el caso en que el `ComposeView` del diálogo
todavía no tuviera padre en el primer frame) — la `Window` de la
`Activity` existe desde antes de que cualquier composable corra, así
que `context.findActivity()?.window` se resuelve una sola vez, sin
polling.

## 3. Por qué el `BackHandler` explícito no es un parche

Un `Dialog` de Compose intercepta el botón/gesto "atrás" del sistema
automáticamente a través de `onDismissRequest` — un mecanismo interno
a `Dialog`, no algo que el resto de la app tenga que replicar. Al sacar
el `Dialog`, ese mecanismo automático desaparece con él, así que hace
falta un `BackHandler` explícito para no perder la función. Esto no es
un ajuste puntual: es el MISMO patrón que `ProjectsTrashScreen` ya
usaba para su propio "atrás" de tres niveles (ver el `BackHandler` que
ya estaba ahí desde antes de esta fase) — ahora ambas pantallas usan
consistentemente el mecanismo estándar de Compose para pantallas que
no son diálogos nativos, en vez de dos criterios distintos conviviendo
en la misma app.

## 4. Qué NO se tocó

- `ErrorLogScreen.kt` sigue abriéndose con su propio `Dialog(...)` —
  mismo patrón que tenía `ProjectsTrashScreen` antes de esta fase. No
  se reportó ningún bug de franja ahí, y esa pantalla nunca convive en
  pantalla con el visor de imágenes (son mutuamente excluyentes desde
  el menú de `ProjectsScreen`), así que está fuera del alcance de lo
  reportado. Queda como deuda técnica conocida si en el futuro se le
  pidiera también un modo de borde a borde real.
- Los diálogos de confirmación (`DeleteTrashedProjectConfirmDialog`,
  `DeleteTrashedEntryConfirmDialog`, `EmptyProjectsTrashConfirmDialog`,
  y los `AlertDialog` equivalentes) siguen siendo diálogos flotantes de
  verdad — correcto: son confirmaciones puntuales, chicas, pensadas
  para verse como un diálogo sobre el contenido, no pantallas
  inmersivas de borde a borde.
- `SingleFilePreviewScaffold` (JSON/texto/audio/video/sin vista previa)
  sigue sin modo inmersivo — nunca lo tuvo, ese comportamiento es
  exclusivo de la galería de imágenes por pedido explícito de diseño
  (ver FASE 14).

## 5. Auditoría de limpieza posterior (código muerto y documentación desactualizada)

A pedido explícito de revisión ("que no quede ningún rastro de código
antiguo"), se auditó todo lo tocado en esta fase en busca de: imports
sin uso, variables/funciones huérfanas, símbolos duplicados, código
comentado (inerte) y comentarios/documentación que quedaran
describiendo el mecanismo VIEJO como si siguiera vigente. Método y
resultado, con evidencia verificable:

- **Imports sin uso** — verificado con un script que cuenta, para cada
  `import` de `FilePreviewDialog.kt` y `ProjectsTrashScreen.kt`, cuántas
  veces aparece ese identificador en el resto del archivo. Resultado:
  cero imports sin uso en ambos archivos.
- **Restos textuales del mecanismo viejo** (`dialogWindow`,
  `DialogWindowProvider` como código activo, `withFrameNanos` del
  polling que resolvía la `Window` del diálogo viejo,
  `usePlatformDefaultWidth` del `Dialog` que se sacó) — buscados en
  **todo** `app/src/main/java/`, no solo en los dos archivos tocados.
  Resultado: cero referencias activas; la única mención de
  `DialogWindowProvider` que queda es dentro de un comentario que
  explica el mecanismo histórico (por qué el bug pasaba), no una
  referencia a código real.
- **Funciones/composables duplicados o huérfanos** — `findActivity` e
  `ImmersiveActivityWindow` verificados como definidos una sola vez cada
  uno; cada composable de `FilePreviewDialog.kt` y `ProjectsTrashScreen.kt`
  verificado con al menos una llamada real (ninguno quedó sin usarse).
- **Dos comentarios con información ya incorrecta, corregidos** — al
  escribir el código de esta fase se afirmó, en dos lugares, que
  `ErrorLogScreen` "nunca usa `Dialog`" — afirmación falsa: esa pantalla
  sigue usando `Dialog` a propósito (ver sección 4, "Qué NO se tocó").
  Corregido en:
  - El comentario de cabecera de `FilePreviewDialog(...)`.
  - El KDoc de `ProjectsTrashScreen(...)`, que además describía la
    pantalla como "diálogo de pantalla completa, mismo criterio que
    `ErrorLogScreen`" — desactualizado desde el momento mismo en que se
    le sacó el `Dialog` a esta pantalla en esta fase. Reescrito para
    describir la arquitectura real actual (`Surface` dentro de la única
    `Window`), con la razón del cambio y el puntero a esta misma fase.
- **Balance de llaves** re-verificado después de las correcciones de
  comentarios de arriba: `FilePreviewDialog.kt` 255/255,
  `ProjectsTrashScreen.kt` 125/125.

No se tocó `docs/fases/FASE_13_VISOR_DE_ARCHIVOS_EN_PAPELERA.md` ni
`FASE_14_GALERIA_DE_IMAGENES_INMERSIVA.md`, que describen (correctamente,
para su momento) la arquitectura basada en `Dialog` que esta fase
reemplazó: son registros históricos de fases ya cerradas, mismo
criterio de "no reescribir el pasado" que ya sigue este mismo directorio
(p. ej. FASE 15 tampoco reescribió lo que decía FASE 14 al encontrar
casos nuevos, los documentó como fase nueva). El registro fiel de "qué
se creyó en su momento y por qué se corrigió después" es, en sí mismo,
valor de ingeniería — no deuda a limpiar.



- **No compilado.** Este entorno de trabajo no tiene `gradlew` en el
  zip entregado, ni Gradle instalado, ni red para resolverlo. Se
  verificó a mano: balance de llaves de ambos archivos completos
  (`FilePreviewDialog.kt`: 256/256; `ProjectsTrashScreen.kt`: 124/124),
  lista completa de funciones de ambos archivos (ninguna quedó cortada
  ni duplicada), e imports usados/no usados uno por uno. **Falta
  `:app:compileDebugKotlin` real** antes de dar esto por cerrado.
- **No probado en dispositivo.** El único criterio de aceptación real
  de este bug es visual, en pantalla física — instalar el APK y abrir
  una imagen desde la papelera de proyectos, en modo retrato, con
  chrome visible y luego oculto (tocando la foto), y confirmar que no
  aparece ninguna franja arriba ni abajo en ningún momento, incluidas
  las transiciones de mostrar/ocultar barras.
- **Casos a probar explícitamente**, dado el historial de esta franja
  (FASE 15 ya había documentado hallazgos específicos por fabricante):
  1. Abrir el visor, ocultar el chrome (tap), volver a mostrarlo (tap
     de nuevo) — la franja históricamente reaparecía en esta
     transición.
  2. Deslizar entre varias fotos de la misma carpeta con el chrome
     oculto.
  3. Rotar el dispositivo (aunque el visor fuerza retrato — confirmar
     que el forzado sigue vigente después de este cambio, ya que vivía
     en el Manifest, no tocado acá).
  4. Cerrar el visor y confirmar que "Mis proyectos"/el editor NO
     quedan con las barras del sistema transparentes, ocultas o de un
     color distinto al morado de marca — es la prueba de que la
     restauración de `onDispose` es realmente completa.
  5. Repetir en al menos un dispositivo con notch/cámara perforada y,
     si es posible, el OnePlus con Android 16 mencionado en FASE 15
     como el caso que motivó el consumo explícito de insets.
