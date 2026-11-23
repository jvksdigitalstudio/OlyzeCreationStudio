# Ingeniería de release

Cómo se firma, versiona, minimiza y verifica Olyze Creation Studio. Decisión y contexto: [ADR-020](adr/ADR-020-ingenieria-de-release.md).

## 1. Artefactos

| Artefacto | Comando | Para qué |
|---|---|---|
| APK debug / perf | `gradle assembleDebug assemblePerf` | Pruebas en dispositivo (perf = sin depurar, para medir fluidez) |
| APK release por ABI + universal | `gradle assembleRelease` | Distribución directa / pruebas del release real (R8) |
| **AAB release** | `gradle bundleRelease` | **Publicación en Google Play** |
| `mapping.txt` | `app/build/outputs/mapping/release/` | Desofuscar fallos. **Guardar uno por cada versión publicada** |
| Símbolos nativos | `app/build/outputs/native-debug-symbols/release/` | Simbolizar fallos de `libolyzeaudio.so` |

`assembleRelease` y `bundleRelease` se ejecutan en invocaciones **separadas**: con un bundle en la invocación, los `splits` de ABI se desactivan (Google Play genera los APK por ABI a partir del AAB).

## 2. Versionado

- `versionCode`: `-Polyze.versionCode=N` o `OLYZE_VERSION_CODE`. CI usa `github.run_number` (monótono creciente). Local: 1. Máximo 2 100 000 000 (límite de Play). **Nunca puede bajar entre versiones publicadas.**
- `versionName`: `-Polyze.versionName=X` o `OLYZE_VERSION_NAME`; por defecto `0.1.0-alpha`.

## 3. Firma

Los secretos **nunca** están en el repositorio. Orden de resolución: variables de entorno → `keystore.properties` (local, ignorado por git).

### 3.1 Crear la clave de subida (una sola vez)
```bash
keytool -genkeypair -v -keystore olyze-upload.jks -alias olyze-upload \
        -keyalg RSA -keysize 4096 -validity 10000
```
Guarda el `.jks` y sus contraseñas en un gestor de secretos con **copia de seguridad fuera del repositorio**. Se recomienda **Play App Signing**: Google custodia la clave de firma final y tú solo conservas la de subida (recuperable si se pierde; la de firma no lo es).

### 3.2 Local
Copia `keystore.properties.example` a `keystore.properties` y rellénalo. Después `gradle assembleRelease` sale firmado.

### 3.3 CI (GitHub Actions)
Secretos del repositorio: `OLYZE_KEYSTORE_BASE64` (`base64 -w0 olyze-upload.jks`; en macOS `base64 -i olyze-upload.jks`), `OLYZE_KEYSTORE_PASSWORD`, `OLYZE_KEY_ALIAS`, `OLYZE_KEY_PASSWORD`.
Sin el secreto el release se compila **sin firmar** y se avisa (así compilan los PR de forks). Con él, el build corre con `OLYZE_REQUIRE_RELEASE_SIGNING=true`: un release sin firma **rompe** el CI.

Una configuración **parcial** (p. ej. archivo sin alias) siempre falla; nunca se degrada en silencio a "sin firmar".

## 4. R8

Política en `app/proguard-rules.pro`: **solo reglas con motivo escrito; prohibidos los `-keep class paquete.** { *; }`.** Lo conservado es: atributos de línea (trazas legibles), el puente JNI (enlaza por nombre) y los serializadores de `kotlinx.serialization`. C++ no llama de vuelta a Kotlin, por eso no hay más.

Verificado automáticamente:
- `tools/check_release_mapping.py` → la clase del puente JNI conserva su nombre, ningún método nativo aparece renombrado y al menos el 30 % de las clases de la app está ofuscado.
- `tools/check_release_dex.py` → en el **DEX real del APK** (con `dexdump`) existen los 17 métodos nativos, con su nombre y marcados `native`. Es la comprobación decisiva: R8 **no lista en `mapping.txt` los miembros que no cambian de nombre**, así que el mapping no puede probar que existen.
- `ReleaseEngineeringStructureTest` → no reaparecen `keep` amplios ni secretos en el build.
- CI: `apksigner verify` (si hay firma) y `zipalign -c -P 16` (bibliotecas nativas alineadas a 16 KB).

### Desofuscar un fallo
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/retrace mapping.txt traza.txt
```
Usa el `mapping.txt` **de la misma versión** (artefacto `olyze-release-diagnostics`, 90 días). Las trazas del `ErrorLogScreen` del release salen ofuscadas.

## 5. Lista de comprobación antes de publicar
1. CI en verde (incluye release firmado + verificaciones).
2. Instalar el APK release **firmado** en un teléfono real y recorrer: abrir proyecto, importar audio, reproducir con reverb, exportar vídeo, abrir un `.olycs`. *(R8 puede romper lo que solo existe en ejecución; ningún test de JVM lo cubre — ver Fase 2.)*
3. Subir el AAB y **archivar `mapping.txt` y los símbolos nativos** de ese `versionCode`.

## 6. Pendiente / no cubierto aún
- `build-config/abi/README.md` describe un `versionCode` por ABI (`versionCodeBitFor`) que **`app/build.gradle.kts` no aplica**. Con AAB no hace falta; solo importaría para distribuir APK por ABI a mano. Decidir: eliminar el catálogo de bits o implementarlo.
- Sin Baseline Profiles ni Macrobenchmark (Fase 4) y sin pruebas instrumentadas del release (Fase 2).
- `perf` sigue firmándose con la clave de debug (intencional: es solo para medir).
