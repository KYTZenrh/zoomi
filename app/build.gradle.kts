import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zoomi.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zoomi.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        // Contact shown to community server operators in the User-Agent (FOSSGIS / Transitous ask for one).
        // Set with -Pzoomi.contact=... or in gradle.properties; empty until the project is published.
        buildConfigField("String", "ZOOMI_CONTACT", "\"" + (project.findProperty("zoomi.contact") ?: "") + "\"")
        // Optional personal MapTiler key for a private build: -Pzoomi.maptilerKey=... (never commit it).
        // Builds published on GitHub are made WITHOUT it.
        buildConfigField("String", "MAPTILER_KEY", "\"" + (project.findProperty("zoomi.maptilerKey") ?: "") + "\"")
    }

    // Release signing from keystore.properties (git-ignored): storeFile, storePassword, keyAlias, keyPassword.
    val signingProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (signingProps != null) create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingProps != null) signingConfig = signingConfigs.getByName("release")
            // Phones only (ARM); leaves out the emulator-only x86 libraries to make the APK smaller.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // required by Ferrostar
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android)
    implementation(libs.ferrostar.core)
    implementation(libs.okhttp)
    implementation(libs.valhalla.mobile)
    implementation(libs.valhalla.models.config)
    implementation(libs.kotlinx.serialization.json)
}
