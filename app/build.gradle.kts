import olyze.abi.SupportedAbis
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.yeivikas.olyzecs"
    compileSdk = 36

    // NDK fijado a r28c (28.2.13676358) a propósito, no "el que traiga AGP": desde r28 el enlazador alinea por defecto a 16 KB
    // TODOS los segmentos de las bibliotecas nativas (incluida `libc++_shared.so`, que viaja en el APK por `-DANDROID_STL=c++_shared`).
    // Con NDK r27 esa biblioteca seguía alineada a 4 KB y Google Play rechaza subidas con .so de 64 bits sin alinear a 16 KB.
    // Es también el NDK por defecto de AGP 9.1, así que no hay que volver a moverlo al subir de AGP.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.yeivikas.olyzecs"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha"

        vectorDrawables.useSupportLibrary = true

        // --- Soporte multiarquitectura (ABI) --------------------------------
        // Catálogo único de ABIs soportadas: buildSrc/src/main/kotlin/olyze/
        // abi/AbiCatalog.kt (ver build-config/abi/README.md para el detalle
        // completo de por qué vive ahí). Restringe las librerías nativas (.so)
        // — hoy el motor de audio EliNer (src/main/cpp) — a esas ABIs.
        ndk {
            abiFilters.clear()
            abiFilters.addAll(SupportedAbis.abiNames)
        }

        // --- Motor de audio nativo (EliNer Audio Engine) ---------------------
        // C++17 + Oboe. `c++_shared` es el STL que exige el paquete prefab de
        // Oboe (guía oficial de Oboe: "-DANDROID_STL=c++_shared").
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
                cppFlags += listOf("-std=c++17")
            }
        }
        // ---------------------------------------------------------------------
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
        // Variante para PROBAR RENDIMIENTO en dispositivo (reproducción, reverb en vivo, línea de tiempo).
        //
        // Un APK `debug` es "debuggable": ART lo ejecuta con optimizaciones reducidas (menos inlining, más intérprete),
        // lo que penaliza justo lo que más pesa aquí: el DSP del reverb en tiempo real (bucles por muestra en el hilo
        // de audio) y Compose. Medir fluidez con él da una idea MUY pesimista. `perf` conserva el resto de `debug`
        // (misma firma, mismo applicationId: se instala encima, sin R8 que pueda alterar el comportamiento) pero
        // NO es debuggable: es lo más parecido al rendimiento de la versión final sin depender de las reglas de R8.
        create("perf") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // --- Soporte multiarquitectura (ABI) ------------------------------------
    // Un APK liviano por arquitectura (armeabi-v7a, arm64-v8a) en vez de un
    // único APK "gordo" con las dos adentro, más un universal de respaldo
    // para sideload/testing. Catálogo de ABIs: buildSrc/.../AbiCatalog.kt.
    splits {
        abi {
            isEnable = true
            reset()
            include(*SupportedAbis.abiNames.toTypedArray())
            isUniversalApk = true
        }
    }
    // -------------------------------------------------------------------------

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        prefab = true  // Oboe se distribuye como paquete prefab (Google Maven)
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// `kotlinOptions {}` quedó obsoleto en Kotlin 2.2 y fue ELIMINADO en 2.4: el destino de bytecode se fija con
// `compilerOptions` (misma versión de JVM que `compileOptions` de arriba: Java 17).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // --- Core / Kotlin ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // `LocalLifecycleOwner` para Compose vive aquí desde lifecycle 2.8 (el de `compose.ui.platform` está obsoleto).
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Audio profesional de baja latencia (AAudio/OpenSL ES con una sola API). Versión y motivo: gradle/libs.versions.toml.
    implementation(libs.oboe)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // --- Compose (UI) --- las versiones salen del BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- Carga de imágenes (Coil 2.7.0, última de la serie 2.x) ---
    // Coil 3 cambia el paquete (`coil3.*`) y sus versiones recientes exigen compileSdk 37: queda para la Fase 3.
    implementation(libs.coil.compose)

    // Nota: la exportación de video NO usa Media3 — es un exportador manual
    // con MediaCodec + EGL (ver VideoExporter.kt), así que no hace falta
    // esa dependencia acá. Si en el futuro se migra a Media3 Transformer,
    // reagregarla.

    // --- Persistencia real del proyecto (capas, keyframes, look) ---
    implementation(libs.kotlinx.serialization.json)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
