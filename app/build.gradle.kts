plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vedicastrology.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vedicastrology.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 217
        versionName = "1.8.37"

        vectorDrawables { useSupportLibrary = true }
    }

    // Stable debug keystore (committed as debug.keystore at the repo
    // root): every CI build signs with the same debug key, so each
    // version installs over the previous one. The key that signed
    // v1.8.14 locally is lost (VM replacement), so v1.8.15 needs one
    // uninstall first; from v1.8.15 on, upgrades are clean. This is a
    // debug key with the public default password, never a release key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep debug simple; no minification for faster iteration.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Swiss Ephemeris Java port (Thomas Mack, 2.01.00) — vendored JAR, no download needed.
    // Moshier mode: no ephemeris data files, ~1 arcsec accuracy.
    implementation(files("libs/swisseph-2.01.00.jar"))

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
