# engine/future

Reservado para la futura migración a C++ (JNI). Por ahora, deliberadamente
vacío — la Etapa 6 del plan de refactorización es explícita:

> NO IMPLEMENTAR JNI. NO IMPLEMENTAR C++. NO MIGRAR CÓDIGO.
> Solamente preparar la arquitectura para que en el futuro ocurra:
> Kotlin UI → EliNer API → JNI → EliNer Engine C++

Cuando llegue esa etapa, este paquete es el lugar previsto para:
- Las declaraciones `external fun` (bindings JNI) del lado Kotlin.
- Wrappers Kotlin que adapten los tipos del motor C++ a los tipos que
  hoy expone `engine/` (para no romper a quien consuma `EliNer API`
  mientras el motor migra módulo por módulo).

No se agrega código aquí en la Etapa 2.

---

## Plan de migración (Etapa 6 — documentado, no implementado)

### Qué ya está listo, de una etapa anterior a esta refactorización

El soporte de arquitecturas de CPU (ABI) para código nativo **ya existe**
y no se tocó en esta etapa — ver `build-config/abi/README.md` y
`buildSrc/src/main/kotlin/olyze/abi/AbiCatalog.kt`. `armeabi-v7a` y
`arm64-v8a` ya están declaradas en `ndk.abiFilters` y en los `splits.abi`
de `app/build.gradle.kts`, listas para cuando el proyecto sume su primera
librería `.so` — hoy no tiene ninguna, y sigue sin tenerla después de
esta etapa.

### Candidato a migrar primero: `engine/render`

El propio `README.md` de la raíz del proyecto ya señala `eliner-render`
como el módulo pensado para C++ ("Pipeline de render (OpenGL hoy, JNI/C++
a futuro)"). Después de la Etapa 2, ese módulo ya es un paquete Kotlin
autocontenido (`engine/render/`: `GLRenderer`, `LayerDrawer`,
`ShaderProgram`, `DisplayRefreshRate`) con una frontera clara — es el
candidato natural para ser el primero en cruzar a C++, por ser el que más
se beneficia de control de memoria/GPU de bajo nivel (dibuja cada capa,
cada frame, tanto en el preview en vivo como en cada frame exportado).

`engine/export` (el loop de `VideoExporter`, que ya reusa `LayerDrawer`)
es candidato secundario natural una vez migrado `render` — comparten la
misma lógica de dibujo.

### Lo que hay que resolver ANTES de escribir una sola línea de JNI (sin resolverlo todavía)

JNI cruza mejor con datos **planos** (primitivos, arrays, buffers) que con
grafos de objetos Kotlin anidados. Los tipos que hoy atraviesan
`render/`/`export/` son:

- **`LookSettings`** (`engine/effects/`) — 18 campos `Float`. Es,
  literalmente, la forma de un `FloatArray(18)` o un uniform buffer de
  shader — el candidato más directo y de menor riesgo para marshaling.
- **`CameraFrame`** (`engine/camera/`) — 9 campos `Float`, mismo caso.
- **`Layer`** (`engine/scene/`) — más complejo: contiene un `Bitmap`
  (la textura ya decodificada) y un `CameraTrack` completo (lista de
  `Keyframe`). El bitmap NO debería cruzar a C++ como objeto Kotlin —
  cruzaría como un puntero a los bytes ya subidos a GPU (la textura GL ya
  vive en la GPU hoy; con un motor C++ seguiría viviendo ahí, JNI ni se
  entera). La lista de keyframes sí necesitaría aplanarse a un buffer
  antes de cruzar, en vez de pasar objetos `Keyframe` uno por uno.

Esto es guía para cuando llegue la implementación real — **nada de esto
se construye en la Etapa 6**, es el análisis que evita descubrir el
problema recién a mitad de la migración.

### Riesgo a tener en cuenta (detectado en la Etapa 5)

`layer.cameraTrack.frameAt(playheadMs)` hoy se llama directo desde la UI
(`EditorScreen.kt`) para hit-testing de gestos táctiles, a alta
frecuencia (cada movimiento del dedo). Si `CameraTrack` migra a C++, cada
una de esas llamadas se convertiría en una llamada JNI por movimiento de
dedo. El overhead de JNI por llamada es bajo (microsegundos), así que en
principio no es un problema de rendimiento real — pero si el día de
mañana se decide resolver ese acoplamiento (ver Etapa 5, §2, primer caso
límite) ANTES de migrar cámara a C++, la migración queda más simple: la
UI pasaría a pedirle la posición a `EditorViewModel`, que sí puede cachear
o batchear esas llamadas del lado Kotlin sin que la UI se entere de nada.

