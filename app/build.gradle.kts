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
        // completo de por qué vive ahí). Restringe cualquier librería nativa
        // (.so) que el proyecto agregue en el futuro a esas ABIs — hoy
        // Olyze no tiene código nativo, así que esto no cambia nada del
        // comportamiento actual, es preparación para cuando lo tenga.
        ndk {
            abiFilters.clear()
            abiFilters.addAll(SupportedAbis.abiNames)
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
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
