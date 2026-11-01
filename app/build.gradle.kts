plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versión: en GitHub Actions es el número de compilación, así cada actualización es mayor que la anterior
val buildNumber = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val updateRepo = System.getenv("UPDATE_REPO") ?: "Criptobox/IgnisStudio"
val keystorePath: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.ignis.studio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ignis.studio"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Repositorio donde la app busca sus actualizaciones (GitHub Releases)
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")

        // Motor local (llama.cpp): solo teléfonos de 64 bits ARM, que son los que pueden mover un modelo
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
                cppFlags += listOf("-O3")
            }
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    // Las variantes del motor (libggml-cpu-*.so) se buscan en la carpeta de bibliotecas: hay que extraerlas
    packaging { jniLibs { useLegacyPackaging = true } }

    buildFeatures { buildConfig = true }

    // Firma fija: imprescindible para que cada versión pueda instalarse encima de la anterior
    signingConfigs {
        create("ignis") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: quita el código y los recursos que no se usan (APK más pequeña)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Con la clave de Ignis si está configurada; si no, con la de depuración (no actualizable)
            signingConfig = if (keystorePath != null) signingConfigs.getByName("ignis") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
}
