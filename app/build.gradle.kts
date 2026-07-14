plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pdfmerger.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brewcreativestudio.brewpdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "2.4"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.jks")
            storePassword = "brewpdf2026"
            keyAlias = "brewpdf"
            keyPassword = "brewpdf2026"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
            // BouncyCastle multi-release JAR conflicts
            excludes += "META-INF/versions/9/module-info.class"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Reorderable drag-and-drop
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // iText 7 for PDF merging (AGPL)
    implementation("com.itextpdf:itext-core:8.0.5")
    implementation("com.itextpdf:bouncy-castle-adapter:8.0.5")
    implementation("com.itextpdf:cleanup:4.0.3")

    // ML Kit Document Scanner (on-device, via Play Services)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")



    // AI Summarizer — Gemma 4 on-device inference via LiteRT-LM
    // NOTE: Add this dependency when internet is available for Gradle sync:
    // implementation("com.google.ai.edge.litertlm:litertlm-android:0.1.0")
    // The LlmManager uses reflection, so the app compiles and runs without it.
    // Without it, the built-in extractive summarizer is used as a fallback.

    // WorkManager for reliable model download (future use)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
