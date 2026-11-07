# Auditoría profesional completa — 2026-10-29

Base auditada: el proyecto subido (387 archivos Kotlin: 231 de producción ≈ 79 400 líneas y 156 de pruebas ≈ 19 700; 24 archivos C++/h de
producción ≈ 2 900 líneas más ≈ 1 800 de pruebas; 85 archivos de recursos; 98 documentos)
más los cambios de las fases A y B del planificador de fuentes y las correcciones de deshacer de esta misma sesión.

## 1. Alcance y método (qué significa "completa" aquí)

| Qué | Cobertura |
|---|---|
| Análisis automático sobre **todos** los archivos | Imports sin uso · declaraciones `private` sin referencias · declaraciones públicas sin referencias o solo de pruebas · clasificación de los 83 `catch` · bloques duplicados · funciones más grandes · E/S en el hilo principal (UI y ViewModel) · recursos sin uso · paridad es/en · referencias rotas en documentos · pruebas sin aserciones · avisos estrictos del compilador C++ |
| Ejecutado | Pruebas C++ (2385 comprobaciones) y sanitizadores ASan/UBSan/TSan · verificador estático del proyecto · validación de XML y YAML |
| Lectura dirigida | Manifiesto, Gradle, ProGuard, CI, ciclo de vida de hilos, `ProjectStorage`/`EditorViewModel` en los puntos señalados por los analizadores, y cada hallazgo antes de afirmarlo |
| **No hecho** | Compilar el Kotlin (no hay compilador aquí) · ejecutar JUnit · medir en dispositivo (recomposiciones, latencia, memoria) · leer a mano las ≈ 99 000 líneas de Kotlin. "Completa" significa cobertura mecánica total más lectura dirigida; **no** una revisión humana de cada línea |

Los analizadores son heurísticos y están en `tools/audit/` (`bash tools/audit/run_all.sh`): señalan candidatos que hay que verificar. Cuatro
de los candidatos que señalaron resultaron **no** ser basura al verificarlos (ver §4), por eso ninguno se borró sin comprobarlo.

## 2. Veredicto

La base es sólida: higiene de código por encima de lo habitual y un motor nativo bien probado. Lo que más riesgo concentra **no** es un
bug oculto sino tres cosas estructurales: (1) no se puede publicar en Google Play tal como está (`targetSdk 34`), (2) `EditorScreen` es una
función de 5 952 líneas, y (3) la compilación `release` con R8 nunca se había ejercitado.

## 3. Hallazgos corregidos en esta auditoría

| ID | Gravedad | Hallazgo | Corrección |
|---|---|---|---|
| A-01 | Media | **Números con coma colgando.** El texto del campo de edición manual se generaba con el idioma del dispositivo: con coma decimal (es, pt, de, fr…) `83` salía como `83,`, `0` como `0,`, y `-0.00001` como `-0,`. En árabe/hindi `%f` escribe dígitos no ASCII que no se leen de vuelta | `formatEditableNumber` (nuevo `ui/EditableNumberFormat.kt`): siempre punto y dígitos ASCII, igual que el filtro de entrada; `-0` → `0`. Prueba con 8 idiomas (`EditableNumberFormatTest`) |
| A-02 | Media | Si `libolyzeaudio.so` no carga, la app caía al reproductor de respaldo **sin dejar rastro**: imposible diagnosticar "¿por qué no usa el motor nativo?" en una tablet | `NativeEngineBindings` registra el motivo con `AppLogger.w` |
| A-03 | Media | El CI nunca ejercitaba la variante `release`: `debug` no minimiza y `perf` desactiva R8 a propósito | Paso informativo `assembleRelease` (no bloquea: `continue-on-error`) |
| A-04 | Media | Los dos retratos de una capa que se guardan y se deshacen (`LayerSaveSnapshot`, `LayerContentState`) repiten los 23 campos de `Layer`; olvidar uno al añadir un campo **no da error de compilación** | `LayerSnapshotParityTest`: falla con el nombre del campo olvidado. Hoy coinciden (verificado) |
| A-05 | Baja | `README.md` afirmaba "Kotlin 100 %" y "(código nativo) hoy no tiene ninguno", y presentaba el motor C++ como futuro: el audio ya es C++/Oboe | Tabla de stack y arquitectura corregidas |
| A-06 | Baja | Tres comentarios decían que la barra inferior "no dispara nada todavía"; los botones sí abren su cajón | Comentarios corregidos (Keyframes sigue siendo un marcador "Próximamente") |
| A-07 | Baja | Permiso `MODIFY_AUDIO_SETTINGS` sin justificación en el manifiesto | **Se mantiene** (lo exige `LoudnessEnhancer` del reproductor de respaldo); documentado con un comentario |
| A-08 | Baja | 1 import sin uso (`ByteBuffer` en `AudioProcessor`) | Eliminado |
| A-09 | Baja | Mi propia prueba `una carpeta inexistente no lanza` no afirmaba nada | Ahora comprueba que no se crea la carpeta |

Corregidos antes en esta sesión (mismo informe de cambios): el deshacer de bordes de clip, rejilla y candado de orden (4 funciones, con
`AudioTrackUndoCoverageTest` que protege la clase de defecto), la importación de audio que fallaba en silencio, y el estado visible de
las fuentes de audio (ver `docs/adr/ADR-AUDIO-PREPARACION-DE-FUENTES.md`).

## 4. Candidatos que NO eran basura (verificados, no tocados)

- `@color/brand_blue`: sin uso en código, pero conservado a propósito como espejo de `Theme.kt` (`docs/fases/FASE_23_LIMPIEZA_CODIGO_MUERTO.md`).
- `MODIFY_AUDIO_SETTINGS`: ver A-07.
- `audioMiniSliderValueAt`, `audioMiniSliderInSnapZone`, `audioMiniSliderSnap`, `audioVolumeIsMuted`: producción ya no los usa, pero
  `MiniSliderTaperTest` los emplea como **oráculo** (la curva lineal debe reproducir exactamente las funciones lineales anteriores).
  Borrarlos habría eliminado pruebas válidas.
- Los avisos del compilador C++: el `lround` con `static_cast<int64_t>` hace falta en `armeabi-v7a`; el acumulador en `double` del reverb es deliberado y afecta a la paridad con Kotlin.
- `Vec3.cross/times`, `!!` de `mixSources`: ya revisados y conservados (FASE_23).

## 5. Hallazgos NO corregidos (decisión o riesgo; ordenados por prioridad)

| ID | Gravedad | Hallazgo | Por qué no se tocó / recomendación |
|---|---|---|---|
| B-01 | **Alta (publicación)** | `targetSdk`/`compileSdk` = 34. Google Play exige API 36 para apps nuevas y actualizaciones **desde el 2026-08-31** (verificado en la documentación de Android y fuentes del sector) | Hoy no se podría subir una versión a Play. Migrar implica AGP/Gradle/Compose más nuevos, *edge-to-edge* obligatorio y *predictive back*. Es una fase propia, con CI y pruebas en tablet; no se hace sin compilador |
| B-02 | **Alta (mantenimiento y posible rendimiento)** | `EditorScreen.kt`: 18 226 líneas, una función `EditorScreen` de **5 952**; otras 10 funciones pasan de 400 líneas (`TimelineView` 1 327, `FloatingToolWindow` 1 117…). Una composable así recompone en bloque y es casi imposible de razonar | Si hay lentitud de interfaz, este es el principal sospechoso, pero **no se puede afirmar sin medir**. Antes de refactorizar: activar los informes del compilador de Compose (omisión y estabilidad) y contar recomposiciones con el Layout Inspector durante la reproducción. Luego dividir por paneles. Los 4 paneles de efectos de `EditorScreen.kt` ~11 000–11 400 repiten el mismo bloque: extraerlo al dividir |
| B-03 | Media | ProGuard: `-keep class …engine.** { *; }` y `…data.** { *; }` mantienen casi toda la app sin minimizar ni ofuscar | Depende del resultado del nuevo paso `release` del CI; ajustar a reglas específicas después de verlo funcionar |
| B-04 | Media | Dependencias de mediados de 2024 (Compose BOM 2024.06.00, lifecycle 2.8.2, coroutines 1.8.1, coil 2.6.0). No se comprobaron las últimas versiones | Actualizar junto con B-01 |
| B-05 | Media | La capa `api/` (1 703 líneas) es una fachada sin consumidor externo (documentado: "por diseño") | Decidir: cablearla a algo real o retirarla. Coste de mantenimiento sin beneficio hoy |
| B-06 | Media | Rampas de velocidad: vista previa y exportación no coinciden | `docs/adr/ADR-AUDIO-RAMPAS-DE-VELOCIDAD.md` (espera tu decisión) |
| B-07 | Baja | El panel Keyframes muestra "Próximamente" al usuario | Decisión de producto: ocultarlo hasta tenerlo |
| B-08 | Baja | 44 referencias a archivos que ya no existen en 19 documentos (casi todos históricos en `docs/fases/` y ADR-012) | Moverlos a `docs/historico/` con una nota de "histórico" |
| B-09 | Baja | `trimClipToSelection` (singular), `decodeWavStream`, `depthCompensationFor` y los ayudantes del slider (§4) existen solo para pruebas; la documentación de `depthCompensationFor` dice que es "el único punto de entrada" y producción no lo llama | Mover a `src/test` y corregir la documentación |
| B-10 | Baja | `castPhotoFile` hace `mkdirs()`/`exists()` en el hilo principal; el ejecutor de `AppLogger` no es demonio; los archivos de imagen se nombran por milisegundo (colisión prácticamente imposible, una acción por vez); `ProjectsScreen.kt:768` fuerza `Locale("es")` en una interfaz bilingüe | Sin impacto práctico; corregir al tocar esos archivos |
| B-11 | Baja | `api.LayerSnapshot` no incluye `gradientGroup`, y su documentación enumera menos exclusiones de las reales | Alinear documento y código |
| B-12 | Info | No hay `gradlew` ni `gradle-wrapper.jar`: los builds dependen de la acción de CI (Gradle 8.6) | Añadir el wrapper para que cualquiera reproduzca el build |

## 6. Lo que está bien (con evidencia)

- **Cero** `TODO/FIXME` reales, **cero** `!!` en código, **cero** `catch` vacíos, **cero** `GlobalScope/runBlocking/Thread.sleep/lateinit`; todos los registros pasan por `AppLogger`.
- 1 445 declaraciones `private` analizadas (540 funciones): ninguna muerta. 1 import sin uso en 387 archivos.
- Manejo de errores: de 83 `catch`, 63 registran y 3 relanzan; los "silenciosos" son el propio `AppLogger` y casos legítimos.
- E/S fuera del hilo principal: 1 sola llamada en toda la UI (en contexto de fondo); en el ViewModel, 25 de 26 candidatas son `suspend` con cambio de hilo interno.
- Duplicación baja: 32 bloques de ≥12 líneas en ≈ 79 400 líneas de producción. 85 archivos de recursos y todos los textos en uso; es/en con las mismas claves.
- C++: sin avisos relevantes con `-Wshadow -Wold-style-cast -Wnull-dereference -Wlogical-op…`; sanitizadores limpios; 0 asignaciones/mutex en el hilo de audio.
- 1 489 pruebas; 0 `@Ignore`; de las 1 417 que el analizador pudo inspeccionar, las 7 "sin aserción" son legítimas (`expected=` o "no lanza"). Sin secretos ni binarios en el repositorio; reglas de copia de seguridad presentes.

## 7. Verificación

- **Ejecutado:** `make test` (2385 comprobaciones, 0 fallos) y sanitizadores; `check_kotlin_static.py` (375 archivos, nombres de prueba válidos) y `check_jni_signatures.py` (17 funciones); XML del manifiesto y los textos; YAML del flujo de CI; los analizadores sobre el código ya corregido.
- **Sin ejecutar (hace falta tu CI y tu tablet):** compilación de Kotlin; las pruebas JUnit nuevas (`EditableNumberFormatTest`, `LayerSnapshotParityTest`) y las de las fases anteriores; el resultado del paso `release`; el comportamiento real en dispositivo.

## 8. Hoja de ruta propuesta

1. Subir este cambio y revisar el CI (incluido el paso `release`).
2. **B-02, medición:** informes de Compose y recomposiciones en la tablet (determina si hay lentitud y dónde).
3. **B-01 + B-04:** migración a API 36 con dependencias actuales, como fase propia.
4. **B-03** tras ver el `release` funcionando.
5. Decisiones tuyas: B-05 (API), B-06 (rampas), B-07 (Keyframes).
6. Limpieza de baja gravedad (B-08 a B-12) cuando se toquen esas zonas.
