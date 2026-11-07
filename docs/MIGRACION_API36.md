# Migración a API 36 (Android 16) — Olyze Creation Studio

**Fecha:** 2026-11-01 (Fase 1) / 2026-11-02 (Fase 2) · **Estado:** Fase 1 verificada en CI y tablet; **Fase 2 implementada, pendiente de CI** (ver §8).
**Motivo:** Google Play exige `targetSdk 36` a apps nuevas y actualizaciones desde el **2026-08-31**
(<https://support.google.com/googleplay/android-developer/answer/11926878>). Con `targetSdk 34` no se puede subir ninguna versión.
Origen: hallazgo B-01 de `AUDITORIA_COMPLETA_2026-10-29.md`.

> **Aviso de método.** Esta fase se escribió SIN compilador (entorno sin red ni Android SDK). Lo verificado aquí es lo que se
> puede verificar sin él: `tools/check_kotlin_static.py`, `tools/check_jni_signatures.py` y una simulación de las aserciones de
> `Api36MigrationStructureTest` contra los archivos reales. **Que compile y que las pruebas JUnit pasen lo decide CI.**

## 1. Estrategia por fases

| Fase | Contenido | Por qué separada |
|---|---|---|
| **1 (esta)** | Toolchain + `targetSdk 36` + cambios de comportamiento obligatorios (borde a borde, atrás, tablets, 16 KB) | Es lo mínimo para publicar. Pocas piezas móviles: si algo falla, la causa es una de las de §2. |
| **2** | Modernizar dependencias: Compose BOM (2024.06 → actual), Kotlin 2.0.21 → 2.2.x, activity/lifecycle/coil, Oboe 1.10.x, `LocalLifecycleOwner` → `androidx.lifecycle.compose` | El salto de Compose 1.6 → 1.12 trae deprecaciones y cambios de layout de Material 3. Mezclarlo con la Fase 1 haría imposible atribuir una regresión. Requiere compilador. |
| **3** | Diseño adaptable (horizontal / ventana redimensionable) y retirar la propiedad temporal de §2.4 | Es trabajo de producto/UX. Obligatorio antes de subir a `targetSdk 37`. |

## 2. Cambios de la Fase 1 y su porqué

### 2.1 Toolchain
| Pieza | Antes | Ahora | Razón (verificada en documentación oficial) |
|---|---|---|---|
| AGP | 8.3.2 | **8.13.0** | API 36 exige AGP ≥ 8.9.1; 8.13 es el último de la serie 8.x (máx. API 36) y evita los cambios de DSL de AGP 9. |
| Gradle | 8.6 | **8.13** | Mínimo de AGP 8.11–8.13. Actualizado en `gradle-wrapper.properties` **y** en CI (deben coincidir). |
| compile/targetSdk | 34 | **36** | Requisito de Play. |
| NDK | (el de AGP, r26/r27) | **28.2.13676358 (r28c)** | Desde r28 el enlazador alinea a 16 KB por defecto, incluida `libc++_shared.so` (viaja en el APK por `-DANDROID_STL=c++_shared`). |
| Oboe | 1.9.0 | **1.9.3** | Primera 1.9.x con el prefab enlazado a 16 KB (google/oboe#2126). Salto de parche, mismo API. |
| Kotlin / Compose BOM | 2.0.21 / 2024.06 | sin cambio | Fase 2 (§1). |

### 2.2 Borde a borde obligatorio (API 35, irreversible en 36)
- `OlyzeSystemBars.apply()` → `enableEdgeToEdge(...)` con iconos claros; se llama **antes** de `super.onCreate()`.
- `themes.xml`: se quitan `statusBarColor`/`navigationBarColor` (el sistema los ignora). Se conserva `windowBackground` (arranque en frío).
- `EditorScreen`: la barra superior propia (no es un `TopAppBar`) aplica `windowInsetsPadding(systemBars ∪ displayCutout)` dentro del `Surface`, así su color llega hasta detrás de la barra de estado. `ProjectsScreen` usa `TopAppBar`/`Scaffold` de M3, que ya aplican insets.
- Teclado: ya no redimensiona la ventana; llega como inset IME. `windowSoftInputMode=adjustResize` + `consumeWindowInsets(padding).imePadding()` en el contenido de ambas pantallas. Los `Dialog` de Compose tienen su propia ventana y mantienen su comportamiento.
- **`ImmersiveActivityWindow` (visor de archivos):** antes restauraba `decorFitsSystemWindows=true` y `statusBarColor` morado al cerrar. En Android 15+ eso no hace nada y en 8–14 habría **roto** el borde a borde del resto de la app tras abrir y cerrar el visor. Ahora restaura llamando a `OlyzeSystemBars.apply()` (misma ruta que el arranque).

### 2.3 "Atrás" predictivo
Con `targetSdk 36` el sistema deja de llamar a `onBackPressed()`/`KEYCODE_BACK`. Auditado: **no hay ningún uso** en el código; toda la navegación va por `BackHandler`. Se declara `enableOnBackInvokedCallback="true"` para no depender del valor por defecto de cada versión. `GuardedBackHandler`/`MultiTouchBackGuard` siguen siendo válidos: la guardia decide dentro de `onBack`.

### 2.4 Orientación y tamaño en tablets (decisión con fecha de caducidad)
Con `targetSdk 36`, en pantallas `sw ≥ 600 dp` Android 16 **ignora** `screenOrientation="portrait"` y `resizeableActivity="false"`. El editor solo está diseñado para vertical. Se usa `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (mecanismo oficial, por Activity) para conservar el comportamiento. **Caduca con `targetSdk 37`**; por eso existe la Fase 3. `configChanges` se amplía (`smallestScreenSize|screenLayout|density|uiMode|…`) para que cambiar el tamaño de ventana no recree la Activity.

### 2.5 16 KB de página
`libolyzeaudio.so` ya tenía `-Wl,-z,max-page-size=16384`. Con NDK r28 + Oboe 1.9.3 las otras dos `.so` del APK (`libc++_shared.so`, `liboboe.so`) quedan también alineadas. Se comprueba en CI con `zipalign -c -P 16 4` (§6.2).

## 3. Revisado y sin cambios necesarios
Permisos (solo `CAMERA`, `VIBRATE`, `MODIFY_AUDIO_SETTINGS`; sin almacenamiento amplio: SAF y Photo Picker) · `FileProvider` · filtros `VIEW`/`SEND` (no `BROWSABLE`) · `IntentCompat.getParcelableExtra` ya con la variante tipada · `DisplayRefreshRate` (`display` en API ≥ 30) · sin servicios en primer plano, `AlarmManager`, `JobScheduler` ni `WorkManager` (comprobado por búsqueda en el código) · único `BroadcastReceiver` (`AudioPlaybackGuard`, `ACTION_AUDIO_BECOMING_NOISY`) ya registrado con `RECEIVER_NOT_EXPORTED` · foco de audio con `AudioFocusRequest` mientras la app está en primer plano · `elegantTextHeight` (afecta a `TextView`; la UI es Compose).

## 4. Riesgos conocidos
1. **Compilación no ejecutada** (§Aviso). Riesgo bajo pero no nulo en `EditorScreen`: se tocaron 2 bloques (barra superior y contenedor).
2. **Barra de navegación en el editor.** El contenido ahora acaba sobre la barra de navegación (antes la ventana ya la dejaba fuera). Si en la tablet la barra inferior del editor (Módulos/Control/Keyframes) queda pegada o tapada, el ajuste está en el `padding` del `Scaffold` (EditorScreen, ~línea 2129).
3. **Visor inmersivo** (`ImmersiveActivityWindow`): el comportamiento al abrir/cerrar depende del fabricante; hay que probarlo.
4. **Pruebas estructurales** son de texto: fallan si se renombra una construcción aunque el comportamiento sea el mismo; es intencional (ver `Api36MigrationStructureTest`).

## 5. Qué NO se hizo (a propósito)
Subir Compose/Kotlin/AndroidX · migrar a AGP 9 · diseño horizontal · añadir `gradlew` (hallazgo B-12, independiente).

## 6. Verificación requerida
### 6.1 CI
1. Pasos de verificación estática, pruebas nativas y `testDebugUnitTest` verdes (incluye `Api36MigrationStructureTest`).
2. **`Compilar variante release con R8`** (informativo): su resultado es la primera información real sobre ProGuard/R8 con API 36.
3. Si CI falla instalando `ndk;28.2.13676358` o `platforms;android-36`, es el paso "Configurar Android SDK y NDK".

### 6.2 Alineación 16 KB (cuando haya APK de CI)
```bash
unzip -o app-arm64-v8a-debug.apk 'lib/*' -d /tmp/apk && for f in /tmp/apk/lib/arm64-v8a/*.so; do
  echo "$f"; readelf -lW "$f" | awk '/LOAD/ {print "  align:", $NF}'; done      # cada LOAD debe ser 0x4000
```

### 6.3 En el dispositivo (Android 16, tablet y, si es posible, teléfono)
- [ ] Arranque en frío sin franja de color distinta arriba/abajo.
- [ ] Barra superior del editor: título y botones **debajo** de la barra de estado y del recorte; no tapados.
- [ ] Barra inferior del editor (Módulos/Control/Keyframes) visible y tocable; no bajo la barra de gestos/navegación.
- [ ] Abrir y cerrar el visor de archivos (papelera): al volver, el editor se ve igual que antes de abrirlo.
- [ ] Gesto "atrás" dentro del editor, de la ventana ampliada de audio y de un diálogo: cierra solo un nivel. Pellizco con dos pulgares en el borde: no sale del editor (`MultiTouchBackGuard`).
- [ ] Teclado en un campo de texto fuera de diálogo: el campo queda visible.
- [ ] Girar la tablet: debe permanecer en vertical (propiedad §2.4). Anotar si no.
- [ ] Reproducir con varios clips y arrastrar en la línea de tiempo: sin cambios de fluidez (APK `perf`).

## 7. Registro de verificación (2026-11-02)
- CI #672 en verde: SDK/NDK, verificación estática, pruebas nativas, `testDebugUnitTest` (incluye `Api36MigrationStructureTest`), paridad del reverb y **release con R8**.
- En tablet (Android 16): el editor arranca, la barra superior queda bajo la barra de estado y la inferior es visible; permanece en vertical.
- CI: `checkout` v4→v6, `setup-java` v4→v5, `upload-artifact` v4→v6 (aviso de Node.js 20 / setup-java obsoleto). `setup-gradle` v4→v5 (Node 24; mismos parámetros). Se evita la v6: cambia los términos de uso del componente de caché y requiere aceptarlos expresamente.
- Pendiente en dispositivo: visor de la papelera, gesto atrás, teclado y giro.

## 8. Fase 2 — base de dependencias (2026-11-02)

> **Mismo aviso de método que la Fase 1:** escrita sin compilador. Verificado aquí: verificación estática del repo y la
> simulación de las aserciones de `DependencyBaselineStructureTest`. **Compilar y las pruebas JUnit lo decide CI.**

### 8.1 Por qué todo va junto (y no "primero Kotlin, luego Compose")
Kotlin, el plugin del compilador de Compose, Compose y AndroidX están acoplados: el compilador de Kotlin solo lee metadatos
de la versión siguiente a la suya (un AndroidX compilado con Kotlin 2.2+ no se puede usar con 2.0.21) y el plugin de Compose
exige un runtime reciente. Subir una pieza sin las demás no compila, así que se mueven en un solo paso, pero en el menor
número de versiones posible y con el código auditado antes (§8.4).

### 8.2 Versiones (todas en `gradle/libs.versions.toml`)
| Pieza | Antes | Ahora | Razón |
|---|---|---|---|
| Kotlin + plugin Compose + serialization | 2.0.21 | **2.3.21** | Última estable de la serie 2.3 que cita la documentación de AGP; `kotlinOptions` (usado aquí) se elimina en 2.4. |
| Compose BOM | 2024.06.00 | **2026.06.01** | Última familia verificada como compilable con `compileSdk 36` (la 2026.08 ya se acerca a la API 37). |
| core-ktx | 1.13.1 | **1.18.0** | 1.19.0 declara `minCompileSdk 37`. |
| lifecycle (runtime/viewmodel-compose) | 2.8.2 | **2.10.0** | 2.11.0 declara `minCompileSdk 37`. |
| `lifecycle-runtime-compose` | — | **2.10.0 (nuevo)** | Casa de `LocalLifecycleOwner` desde lifecycle 2.8. |
| activity-compose | 1.9.0 | **1.12.4** | Última que no pide `compileSdk 37`. |
| coroutines (android + test) | 1.8.1 | **1.10.2** | Compatible con Kotlin 2.3; la 1.11 no aporta nada que se use. |
| kotlinx-serialization-json | 1.7.1 | **1.10.0** | Mismo API para `@Serializable`/`Json`. |
| Coil | 2.6.0 | **2.7.0** | Última 2.x: mismo API, sin migrar paquete. Coil 3 (`coil3.*`) y sus versiones recientes piden `compileSdk 37` → Fase 3. |
| material-icons-core | 1.6.7 (clavada) | **la del BOM** | Estaba desalineada del resto de Compose. |
| androidx.test.ext:junit | 1.1.5 | **1.3.0** | Sin impacto en producción. |
| Oboe | 1.9.3 | **1.9.3 (sin cambio)** | La 1.10 solo añade *offload* y formatos comprimidos, que el motor no usa; tocar la base nativa de audio sin prueba en dispositivo no se justifica. |
| AGP / Gradle | 8.13.0 / 8.13 | **sin cambio** | `compileSdk 37` y AGP 9 son la Fase 3; `DependencyBaselineStructureTest` impide subir uno sin el otro. |

### 8.3 Cambios de código y de build
- **Catálogo de versiones** (`gradle/libs.versions.toml`): única fuente de verdad; el build raíz usa `alias(libs.plugins…)` y
  `app/build.gradle.kts` ya no tiene ninguna versión escrita a mano.
- **`kotlinOptions { jvmTarget }` → `kotlin { compilerOptions { jvmTarget.set(JVM_17) } }`**.
- **`LocalLifecycleOwner`**: `androidx.compose.ui.platform` → `androidx.lifecycle.compose` (`MainActivity.kt`, `GLPreview.kt`).
- **Ningún otro cambio de código fue necesario** (§8.4).

### 8.4 Auditoría del código real contra los cambios que rompen
Búsqueda en los 231 archivos de producción y las pruebas:
- Eliminados/obsoletos con error en Kotlin 2.1+ (`toLowerCase`, `capitalize`, `appendln`, `sumBy`, `Char.toInt`…): **0 usos**.
- Compose/Material 3 retirados (`rememberRipple`, `Divider(` — ya se usa `HorizontalDivider`—, `menuAnchor`, `animateItemPlacement`, `ClickableText`, `SwipeToDismiss`, `SliderPositions`, `TextFieldDefaults.textFieldColors`, `Modifier.composed` más allá de 1): **0 usos**; `clickable(indication = null)` sigue siendo válido.
- Colisión con Java 21 en `android.jar` 35+ (`removeFirst`, `removeLast`, `reversed()`): 3 llamadas a `removeFirst`, **sobre `kotlin.collections.ArrayDeque`** (miembro propio de Kotlin, sin riesgo en API < 35).
- Pruebas estructurales que dependan de las APIs tocadas: solo `AudioSourceStatusWiringTest` (`collectAsState`, que no cambia).

### 8.5 Riesgos y plan de retirada
1. **Primer fallo probable: resolución de dependencias.** Si CI dice que no encuentra `compose-bom:2026.06.01` (versión tomada de proyectos reales, no consultada en Maven), usar `2026.08.00` (documentada oficialmente) y comprobar que no pida `compileSdk 37`.
2. **AGP 8.13 + Kotlin 2.3.21:** no verificado contra la tabla oficial de compatibilidad. Si Gradle avisa de versión de AGP «no probada» solo es una advertencia; si falla, bajar `kotlin` a 2.2.21 en una línea del catálogo (nada más cambia).
3. **Aspecto de Material 3:** pasa de 1.2 a 1.4; los colores/formas por defecto de `Slider`, `Switch`, `Surface` pueden variar milimétricamente. Comparar con capturas de la Fase 1.
4. **Rendimiento:** Compose 1.11 trae otro *runtime* (strong skipping por defecto y otro planificador). Debería ser neutro o mejor; se mide con el APK `perf`.

### 8.6 Verificación requerida
- CI: verde con `testDebugUnitTest` (incluye `DependencyBaselineStructureTest`) y **release con R8** (es la primera vez que R8 minimiza con Kotlin 2.3 y Compose 1.11).
- Tablet: repetir la lista de §6.3 y además: arrastre de clips, reproducción con varios audios, ventanas flotantes (reverb), diálogo de color con sliders, miniaturas (Coil), visor de la papelera.

## 9. Fase 2 verificada y cierre de CI (2026-11-02)
- CI #673 en verde con la base de la Fase 2 (Kotlin 2.3.21, Compose BOM 2026.06.01), incluido R8.
- Tablet (Android 16), APK `perf`: editor, teclado (`imePadding`), papelera de capas, diálogos y paneles flotantes correctos.
- El giro de pantalla NO funciona a propósito (§2.4): la app es vertical hasta que exista el diseño horizontal (Fase 3).
- Pendiente en dispositivo: gesto atrás, visor de la papelera de proyectos y exportación de video completa.
