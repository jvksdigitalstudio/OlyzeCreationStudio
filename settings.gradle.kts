pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OlyzeCreationStudio"
include(":app")

// -----------------------------------------------------------------------
// Arquitectura futura (YeiViKas Digital Company — motor EliNer)
// -----------------------------------------------------------------------
// Los módulos del motor EliNer todavía no existen como código: esta etapa
// solo reorganiza identidad y nombres (ver README.md). Cuando se empiece
// a construir EliNer API/Core/Engine/Render/Audio, se crean los
// directorios hermanos de "app" (eliner-api, eliner-core, eliner-engine,
// eliner-render, eliner-audio) y se descomentan/agregan sus líneas de
// include(...) acá. La app NUNCA debe depender del motor interno
// directamente — solo de "eliner-api".
//
// include(":eliner-api")
// include(":eliner-core")
// include(":eliner-engine")
// include(":eliner-render")
// include(":eliner-audio")
