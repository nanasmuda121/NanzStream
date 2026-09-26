plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nanzstream.nanas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nanzstream.nanas"
        minSdk = 24
        targetSdk = 35
        versionCode = 46
        versionName = "1.3.16"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val localKey = file("nanzstream-release.jks")
            val rootKey = file("../nanzstream-release.jks")
            storeFile = if (localKey.exists()) localKey else rootKey
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "nanzstream123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "nanzstream"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "nanzstream123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX & Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose BOM & UI
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Media3 / ExoPlayer for Streaming Video, HLS & DASH
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-extractor:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // Coil for asynchronous image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Networking & JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jsoup:jsoup:1.18.1")

    // Core Library Desugaring (required for NewPipeExtractor java.time on minSdk < 33)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")

    // NewPipeExtractor for native YouTube stream & HLS resolution
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.google.protobuf:protobuf-javalite:4.28.2")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.mozilla:rhino-engine:1.7.15")
}
