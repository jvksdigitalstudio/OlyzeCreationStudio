# ADR-020 — Ingeniería de release (Fase 1)

- **Estado:** Implementado (2026-11-22). **Pendiente de validar en dispositivo y en CI** (ver §5).
- **Alcance:** firma, versionado, R8, AAB, verificación del release en CI.
- **Fuera de alcance:** refactor de `EditorScreen` (Fase 3), tests instrumentados (Fase 2), Baseline Profiles (Fase 4).

## 1. Contexto — hechos verificados en el código

| # | Hecho | Dónde |
|---|-------|-------|
| 1 | El release **no tenía `signingConfig`**; `perf` firmaba con la clave de debug. Publicar era imposible sin editar el build. | `app/build.gradle.kts` |
| 2 | `versionCode = 1` y `versionName` clavados en el script. | `app/build.gradle.kts` |
| 3 | R8 con `-keep class com.yeivikas.olyzecs.engine.** { *; }` y `data.** { *; }`: **la mayor parte del código quedaba sin reducir ni ofuscar**, y R8 nunca se ejercitaba de verdad. | `proguard-rules.pro` |
| 4 | El paso de release en CI era **informativo** (`continue-on-error`): un fallo del minificador no bloqueaba nada. | `android-build.yml` |
| 5 | La única superficie JNI es `NativeEngineBindings` (17 métodos); C++ **no** llama a Kotlin (sin `FindClass`/`GetMethodID`). | `jni_bridge.cpp` |
| 6 | Los modelos persistidos usan `kotlinx.serialization`; no hay reflexión (`Class.forName`) ni lógica sobre `simpleName`. | búsqueda en `src/main` |
| 7 | No existía AAB, ni archivo de `mapping.txt`, ni símbolos nativos conservados. | CI |

## 2. Decisión

1. **Firma y versión viven en `buildSrc/olyze/release/ReleaseConfig.kt`**, no en el script: entorno → `keystore.properties` (ignorado por git). Sin credenciales el release compila sin firmar (los forks siguen compilando); con `requireReleaseSigning=true` es un error; una configuración **parcial siempre falla**.
2. **R8 por enumeración**: solo JNI, serialización y atributos de línea, cada regla con su motivo. Se prohíben los `keep` amplios (lo vigila una prueba y el verificador de mapping).
3. **AAB** como artefacto de publicación; los `splits` de ABI se desactivan solo cuando se pide un bundle.
4. **CI bloqueante**: release firmado (si hay secreto) + `check_release_mapping.py` + `check_release_dex.py` + `apksigner verify` + `zipalign -P 16` + archivo de `mapping.txt`/símbolos 90 días.

## 3. Alternativas descartadas
- *Mantener los `keep` amplios por seguridad:* es esconder el problema; el código nativo no necesita más que el nombre de una clase.
- *Fallar siempre sin firma:* rompería PR de forks y a cualquier colaborador sin claves.
- *Guardar el `.jks` cifrado en el repositorio:* el secreto no debe vivir en el historial de git.

## 4. Consecuencias
- Release más pequeño y ofuscado de verdad; los fallos del minificador aparecen en el CI, no el día de publicar.
- Las trazas del release salen **ofuscadas**: hay que conservar el `mapping.txt` de cada versión publicada.
- Perder el `.jks` sin Play App Signing es irrecuperable → ver `docs/RELEASE_ENGINEERING.md` §3.1.

## 4.1 Primera ejecución real del CI (2026-11-22) — lección
El release con R8 **compiló** (3 min 13 s). El paso `check_release_mapping.py` falló con falsos positivos: asumí que `mapping.txt` lista todos los métodos de una clase conservada, pero R8 omite los miembros que no cambian de nombre y no tienen cuerpo (los métodos nativos). Mi prueba sintética repetía esa suposición y por eso no lo detectó. Corrección: la existencia de los métodos se verifica en el DEX real (`check_release_dex.py`); el mapping solo se usa para lo que sí contiene (clase intacta, métodos renombrados, ratio de ofuscación). El ratio de ofuscación y la clase del puente **sí pasaron** en esa ejecución.

## 4.2 Segunda ejecución real del CI — defecto latente de la caché
El paso "Paridad del reverb nativo con Kotlin" falló (`engine_tests.cpp:1571 f.good()`) sin relación con el release. Causa: `org.gradle.caching=true` + `reverb_golden.f32` era un efecto secundario NO declarado de `testDebugUnitTest`; cuando la tarea se restaura de la caché sin ejecutarse (ninguna entrada cambió), el archivo no existe en el runner limpio. Pasaba "a veces" según tocara o no código que invalide la tarea. Corrección: se declara `build/native-golden` como salida de `testDebugUnitTest` (`outputs.dir`), el paso del CI diagnostica explícitamente si falta, y una prueba vigila la declaración. **Inferido del código y del comportamiento de Gradle; no se vio el log del paso de Gradle** (se confirmaría buscando `FROM-CACHE` en `:app:testDebugUnitTest`).

## 5. Lo que NO se ha verificado (y por qué importa)
- Al escribir este cambio no pude compilar con Gradle (sin red); el CI real lo compiló después (ver §4.1). Verificado en local: sintaxis del workflow (YAML), el verificador de mapping con un `mapping.txt` sintético (acepta el correcto, rechaza el JNI renombrado), la prueba estructural contra las reglas antiguas y nuevas, y el chequeo estático del repositorio.
- **El comportamiento del release en dispositivo.** Quitar los `keep` amplios es correcto según el código (ver §1.5–1.6) pero solo un APK release en un teléfono lo confirma: abrir proyecto, importar audio, reverb, exportar, abrir `.olycs`.
- Los flags `zipalign -P 16` y `apksigner` con build-tools 36.0.0 se tomaron de la documentación; si el CI los rechaza, ajustar el paso (no el diseño).
