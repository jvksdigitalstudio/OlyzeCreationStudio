package olyze.abi

/*
 * ============================================================================
 *  AbiCatalog.kt
 * ============================================================================
 *
 * PROPÓSITO
 * ---------
 * Única fuente de verdad de qué arquitecturas de CPU (ABI) soporta
 * Olyze. Todo lo demás relacionado con multiarquitectura (splits de
 * APK, ndk.abiFilters, versionCode por ABI) lee esta lista — nadie más en
 * el proyecto debería tener una lista de ABIs escrita a mano en otro
 * lado.
 *
 * RESPONSABILIDAD
 * ----------------
 * Exclusivamente declarar el catálogo (`SupportedAbis`) y las funciones
 * mínimas para consultarlo. Cero lógica de Gradle/Android Gradle Plugin
 * acá a propósito — por eso es un archivo Kotlin plano, sin ningún
 * `import com.android.*`: así compila siempre, sin depender de qué
 * classpath tenga disponible el script que lo use.
 *
 * QUÉ CONSUME ESTA INFORMACIÓN
 * -----------------------------
 * `app/build.gradle.kts` — en la sección "Soporte multiarquitectura (ABI)"
 * (buscar ese título): usa `SupportedAbis.abiNames` para `ndk.abiFilters`
 * y `splits.abi.include(...)`, y `SupportedAbis.versionCodeBitFor(...)`
 * para el versionCode de cada APK dividido. Ver
 * `build-config/abi/README.md` para el detalle completo de la
 * arquitectura de este módulo.
 *
 * CÓMO AGREGAR UNA ARQUITECTURA NUEVA (x86_64, riscv64, ...)
 * -------------------------------------------------------------
 * Una línea en `SupportedAbis`, con un `versionCodeBit` que no se repita
 * con los existentes, y sumarla a `all`. Nada más en todo el proyecto
 * necesita tocarse.
 */

/**
 * Representa una arquitectura de CPU soportada por la app.
 *
 * @param abiName nombre exacto de la ABI tal como lo espera Android
 *   (`android.defaultConfig.ndk.abiFilters` / `android.splits.abi.include`).
 * @param versionCodeBit multiplicador único usado para calcular el
 *   versionCode del APK específico de esta ABI. Debe ser un entero
 *   positivo distinto para cada ABI del catálogo; una vez publicada una
 *   ABI con un bit determinado, ese bit no debería cambiar entre releases
 *   (rompería el orden de versionCode en Play Store).
 */
data class AbiTarget(val abiName: String, val versionCodeBit: Int)

/**
 * Catálogo único y central de arquitecturas soportadas por Olyze.
 */
object SupportedAbis {

    /** ARM de 32 bits — equipos Android más antiguos o de gama muy baja. */
    val armeabiV7a = AbiTarget(abiName = "armeabi-v7a", versionCodeBit = 1)

    /** ARM de 64 bits — la inmensa mayoría de equipos Android actuales. */
    val arm64V8a = AbiTarget(abiName = "arm64-v8a", versionCodeBit = 2)

    // Para sumar una arquitectura nueva (ej. x86_64 para emuladores/
    // Chromebooks, o una futura riscv64), agregar acá una línea más:
    //   val x86_64 = AbiTarget(abiName = "x86_64", versionCodeBit = 3)
    // y agregarla a `all` — nada más en todo el proyecto necesita cambiar.
    val all: List<AbiTarget> = listOf(armeabiV7a, arm64V8a)

    /** Nombres de ABI en formato plano, tal como los pide la API de AGP. */
    val abiNames: List<String> = all.map { it.abiName }

    private val bitByAbiName: Map<String, Int> = all.associate { it.abiName to it.versionCodeBit }

    /**
     * Multiplicador de versionCode para una ABI dada, o `null` si la ABI no
     * está en el catálogo (por ejemplo el APK "universal", que no tiene
     * filtro de ABI y por lo tanto debe conservar el versionCode base sin
     * override).
     */
    fun versionCodeBitFor(abiName: String?): Int? = abiName?.let { bitByAbiName[it] }
}
