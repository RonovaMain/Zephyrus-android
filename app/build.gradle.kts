plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Подпись релиза: если заданы переменные окружения (так делает GitHub Actions) — подписываем своим ключом,
// иначе используем отладочный ключ, чтобы APK всё равно устанавливался.
val keystorePath: String? = System.getenv("ZEPHYRUS_KEYSTORE")

android {
    namespace = "dev.ronova.zephyrus"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.ronova.zephyrus"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ZEPHYRUS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ZEPHYRUS_KEY_ALIAS")
                keyPassword = System.getenv("ZEPHYRUS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 не включаем: библиотека yt-dlp использует рефлексию, а основной размер APK — это Python и FFmpeg
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Отдельные APK под каждую архитектуру + универсальный
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // yt-dlp/ffmpeg запускаются как нативные файлы — их нужно извлекать из APK
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // yt-dlp (+ Python) и FFmpeg для Android
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
