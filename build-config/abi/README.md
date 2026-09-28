# Soporte multiarquitectura (ABI)

## Qué es esto

Catálogo único y central de las **arquitecturas de CPU (ABI)** que
Olyze soporta, más la configuración que lo conecta con la Android
Gradle Plugin.

## Arquitecturas soportadas hoy

| ABI            | Descripción                                          |
|----------------|-------------------------------------------------------|
| `armeabi-v7a`  | ARM de 32 bits — equipos antiguos / gama muy baja.    |
| `arm64-v8a`    | ARM de 64 bits — la mayoría de equipos Android hoy.   |

## Dónde vive cada parte, y por qué

**`buildSrc/src/main/kotlin/olyze/abi/AbiCatalog.kt`** — el catálogo en
sí (`SupportedAbis`, `AbiTarget`). Archivo Kotlin plano, sin ningún import
de Android/Gradle — solo datos. Esta es la única fuente de verdad de qué
ABIs soporta la app; toda la config real lee de acá.

**`app/build.gradle.kts`**, sección "Soporte multiarquitectura (ABI)"
(buscar ese título — hay dos bloques: `ndk.abiFilters` dentro de
`defaultConfig`, y `splits.abi` dentro de `android`): conecta el catálogo
con la Android Gradle Plugin.

### ¿Por qué no todo en un archivo `.gradle.kts` exclusivo, separado del
módulo `:app`?

Se intentó así primero (`build-config/abi/AbiSupport.gradle.kts`,
aplicado con `apply(from = ...)`) y **rompió el build en CI**: ese
archivo necesita importar tipos de la Android Gradle Plugin
(`AppExtension`, etc.), y Gradle **no comparte las clases de un plugin
declarado vía `plugins {}` con los scripts aplicados por
`apply(from = ...)`** — es una limitación real y documentada de Gradle,
no un descuido de sintaxis.

La solución correcta y garantizada por el propio diseño de Gradle es
**buildSrc**: todo lo que se compila ahí queda disponible automáticamente,
sin configuración extra, para cualquier `build.gradle.kts` del proyecto.
Por eso el catálogo (la parte reutilizable y sin dependencias de Android)
vive en buildSrc, y la parte que sí necesita tipos de la Android Gradle
Plugin vive directamente en `app/build.gradle.kts` — el único lugar donde
esos tipos están garantizados disponibles sin trucos, porque ese archivo
es el que aplica el plugin.

Sigue habiendo una separación real de responsabilidades (que era el
objetivo original): el catálogo — la única parte que alguien va a tocar
para agregar una arquitectura nueva — está en su propio archivo exclusivo,
aislado de toda lógica de Android. Lo que quedó en `app/build.gradle.kts`
es pura conexión (dos bloques cortos, cada uno claramente marcado con
comentarios), no una redefinición del catálogo.

## Qué genera esto en la práctica

Con este módulo activo, cada `assembleDebug` / `assembleRelease` produce:

- `app-armeabi-v7a-<buildType>.apk`
- `app-arm64-v8a-<buildType>.apk`
- `app-universal-<buildType>.apk` (contiene ambas ABIs — útil para
  sideload/testing sin depender de qué arquitectura tiene el equipo)

## Pendiente, a propósito: versionCode por ABI

El catálogo (`AbiTarget.versionCodeBit`) ya trae el dato listo para esto,
pero la conexión real con `output.versionCodeOverride` se sacó del build
después de fallar tres veces seguidas en CI por firmas de la API vieja de
variant outputs de AGP que no pude verificar sin compilar localmente. No
hace falta hoy — el workflow de CI solo genera APKs debug para pruebas,
no builds firmados para Play Store — así que se prioriza tener un build
verde antes que esa pieza. Si el día de mañana se necesita publicar APKs
sueltos (no Android App Bundle) en Play Store, ahí sí hay que retomarla,
idealmente probándola en Android Studio antes de subirla a CI.

## Cómo agregar una arquitectura nueva

1. Abrir `buildSrc/src/main/kotlin/olyze/abi/AbiCatalog.kt`.
2. Agregar una línea al objeto `SupportedAbis`, por ejemplo:
   ```kotlin
   val x86_64 = AbiTarget(abiName = "x86_64", versionCodeBit = 3)
   ```
3. Sumarla a la lista `all`.

Nada más en todo el proyecto necesita tocarse — ni `app/build.gradle.kts`,
ni el workflow de CI, ni ningún otro archivo.
