plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.autovideo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.autovideo.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 9
        versionName = "0.8.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    val media3Version = "1.10.1"

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.webkit:webkit:1.15.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.videolan.android:libvlc-all:3.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// The connected CI workflow currently invokes assembleDebug only. Keeping the
// verification tasks as dependencies makes every CI APK build run unit tests
// and Android lint without weakening either check.
tasks.matching { it.name == "assembleDebug" }.configureEach {
    dependsOn("testDebugUnitTest", "lintDebug")
}
