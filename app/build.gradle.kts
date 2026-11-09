import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// API key lives in local.properties (never committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.koora.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.koora.app"
        minSdk = 24
        targetSdk = 36 // Google Play: new apps must target API 36 since Aug 31, 2026
        versionCode = 5
        versionName = "1.4.0"
        buildConfigField(
            "String",
            "FOOTBALL_DATA_TOKEN",
            "\"${localProps.getProperty("FOOTBALL_DATA_TOKEN") ?: ""}\""
        )
        // Ad unit IDs default to Google's PUBLIC TEST units. Set real ones in
        // local.properties (or a GitHub secret -> local.properties) later — zero code change.
        buildConfigField(
            "String",
            "AD_UNIT_BANNER",
            "\"${localProps.getProperty("AD_UNIT_BANNER") ?: "ca-app-pub-3940256099942544/6300978111"}\""
        )
        buildConfigField(
            "String",
            "AD_UNIT_NATIVE",
            "\"${localProps.getProperty("AD_UNIT_NATIVE") ?: "ca-app-pub-3940256099942544/2247696110"}\""
        )
        buildConfigField(
            "String",
            "AD_UNIT_INTERSTITIAL",
            "\"${localProps.getProperty("AD_UNIT_INTERSTITIAL") ?: "ca-app-pub-3940256099942544/1033173712"}\""
        )
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
