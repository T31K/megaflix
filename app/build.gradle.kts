plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.megaflix.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.megaflix.tv"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-phase1"
        // Phase 4 flips this to true. Phase 1 = DevMediaSource.
        buildConfigField("boolean", "USE_USB_SOURCE", "false")
        // Self-contained demo: bundled clip + poster cards, no media push needed.
        // The shareable GitHub APK ships with this true; real use flips it false.
        buildConfigField("boolean", "DEMO_MODE", "true")
        // Phase 2: TMDB metadata lookups.
        buildConfigField("String", "TMDB_API_KEY", "\"8b6f7e9a19bd57cca4cd213917274d13\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.material)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.coil)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    // Phase 4 (SafMediaSource fallback): implementation("androidx.documentfile:documentfile:1.0.1")
}
