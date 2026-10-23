import olyze.abi.SupportedAbis

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.yeivikas.olyzecs"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yeivikas.olyzecs"
        minSdk = 26
        targetSdk = 34
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

    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
    // --- Core / Kotlin ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    // Audio profesional de baja latencia (AAudio/OpenSL ES con una sola API). Versión 1.9.0: release estable
    // verificada en https://github.com/google/oboe/releases.
    implementation("com.google.oboe:oboe:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Compose (UI) ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.6.7")
    implementation("androidx.activity:activity-compose:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Carga de imágenes (Coil, Kotlin-first) ---
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Nota: la exportación de video NO usa Media3 — es un exportador manual
    // con MediaCodec + EGL (ver VideoExporter.kt), así que no hace falta
    // esa dependencia acá. Si en el futuro se migra a Media3 Transformer,
    // reagregarla.

    // --- Persistencia real del proyecto (capas, keyframes, look) ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
