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
        versionCode = 15
        versionName = "1.6.0"
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
        // api-football.com free key (100 req/day, events+lineups+stats).
        // Empty -> deep stats stay hidden on real data (demo mode shows a sample).
        buildConfigField(
            "String",
            "API_FOOTBALL_KEY",
            "\"${localProps.getProperty("API_FOOTBALL_KEY") ?: ""}\""
        )
        // Optional: free YouTube Data API key -> exact per-match highlight video.
        // Empty -> official league channels' RSS feeds are used (no key needed).
        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${localProps.getProperty("YOUTUBE_API_KEY") ?: ""}\""
        )
    }

    signingConfigs {
        create("release") {
            // Stable self-signed key, kept OUT of git: untracked locally and
            // restored in CI from the encrypted KOORA_KEYSTORE secret, so
            // EVERY build signs with the SAME signature — new versions
            // install over old ones without uninstalling.
            val ks = rootProject.file("keystore/koora-release.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "KooraRelease2026"
                keyAlias = "koora"
                keyPassword = "KooraRelease2026"
            }
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources/manifest on the JVM
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Lint-vital is a Play-prep step; enable it together with the R8 rules
        // before the Play release. Keeps CI release builds fast and deterministic.
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            // R8/minify stays OFF for now — it needs ProGuard rules for
            // Retrofit/Gson/ads. Re-enable together with those rules before
            // the Play Store release.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    // Internal testing: Robolectric boots the REAL app (Activity + Compose UI)
    // on the JVM in CI — if startup ever crashes, the build fails.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.espresso.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
