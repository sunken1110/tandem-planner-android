import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// Release signing key, read from local.properties (not committed to Git).
// When the entries are missing (e.g. a fresh clone), builds fall back to the
// debug key, so the project still builds.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val hasReleaseKey = listOf("keystore.path", "keystore.password", "key.alias", "key.password")
    .all { !localProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "io.github.sunken1110.tandemplanner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.sunken1110.tandemplanner"
        minSdk = 31
        targetSdk = 37
        versionCode = 8
        versionName = "3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(localProps.getProperty("keystore.path"))
                storePassword = localProps.getProperty("keystore.password")
                keyAlias = localProps.getProperty("key.alias")
                keyPassword = localProps.getProperty("key.password")
            }
        }
    }

    // Every build type uses the same key, so Run (debug) installs over the
    // released APK and keeps the account, the room and its data.
    val appSigning = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = appSigning
        }
        release {
            signingConfig = appSigning
//            optimization {
//                enable = true
//                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
//            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = appSigning
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}