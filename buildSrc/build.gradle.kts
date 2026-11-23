/*
 * buildSrc — lógica de build reutilizable en todo el proyecto.
 *
 * Hoy solo contiene el catálogo de arquitecturas (ABI) soportadas por
 * Olyze — ver src/main/kotlin/olyze/abi/AbiCatalog.kt.
 *
 * Por qué acá y no en un script aplicado con apply(from = ...): Gradle NO
 * comparte las clases de un plugin declarado vía `plugins {}` con scripts
 * aplicados por `apply(from = ...)` — eso rompía la compilación al
 * intentar importar tipos de la Android Gradle Plugin ahí. buildSrc no
 * tiene ese problema: todo lo que se compila acá queda disponible
 * automáticamente, sin configuración extra, para CUALQUIER build.gradle.kts
 * del proyecto — el de :app hoy, y el de cualquier módulo que se sume el
 * día de mañana. Es el mecanismo de Gradle diseñado específicamente para
 * esto.
 */
plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}
