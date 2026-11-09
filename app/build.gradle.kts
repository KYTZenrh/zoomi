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
        versionCode = 4
        versionName = "0.4.0-d"
        // Contact shown to community server operators in the User-Agent (FOSSGIS / Transitous ask for one).
        // Set with -Pzoomi.contact=... or in gradle.properties; empty until the project is published.
        buildConfigField("String", "ZOOMI_CONTACT", "\"" + (project.findProperty("zoomi.contact") ?: "") + "\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signing is configured by you; see README "Signing your own APK".
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
