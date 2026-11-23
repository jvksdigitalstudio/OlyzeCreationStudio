// Las versiones viven en gradle/libs.versions.toml (catálogo). Aquí solo se DECLARAN los plugins para que todos los
// módulos usen la misma versión; cada módulo los aplica sin número (`id("...")`).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
