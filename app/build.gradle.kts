plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.dhruvar"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.dhruvar"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Global free OpenStreetMap-based style (works in India and worldwide).
        // Previous Alps-only demo: https://demotiles.maplibre.org/pmtiles/raster/style-imagery.json
        // Override via product flavors / CI; site-specific PMTiles still supported — see docs/PMTiles-Configuration.md
        buildConfigField(
            "String",
            "MAP_STYLE_URL",
            "\"https://tiles.openfreemap.org/styles/liberty\""
        )
        // Optional direct PMTiles archive URL (empty = use style JSON above). Example:
        // "pmtiles://https://example.com/region.pmtiles"
        buildConfigField("String", "PMTILES_URL", "\"\"")
        // Self-hosted India map server (tiles + glyphs + /search). Empty = fall back to
        // MAP_STYLE_URL above and disable place search. See docs/Map-Server-Configuration.md.
        // No trailing slash. Cleartext hosts must also be listed in res/xml/network_security_config.xml.
        buildConfigField("String", "MAP_SERVER_BASE", "\"http://localhost:8000/map\"")
        // Dashboard server the app signs in to and sends plans for approval to. Empty = the user
        // types it on the sign-in screen. Release builds only accept https:// addresses.
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")
    }

    buildTypes {
        debug {
            // USB test: `adb reverse tcp:8000 tcp:8000` makes the phone's localhost:8000 the PC's
            // stack. Plain HTTP is permitted for debug builds only: see
            // src/debug/res/xml/network_security_config.xml.
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://localhost:8000\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("com.google.ar:core:1.47.0")
    // MapLibre Native Android — PMTiles via pmtiles:// requires >= 11.7.0 (pinned 11.8.0)
    implementation(libs.maplibre.android.sdk)
    // Map server style fetch + place search
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    // A fake server inside the unit tests, to check what the picture uploader really sends.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.json:json:20240303")
    // Virtual time for the search debounce, so the tests do not actually wait 250 ms.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}