plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    id("com.onesignal.androidsdk.onesignal-gradle-plugin")
}

android {
    namespace = "com.nexiplay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexiplay.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase config - these will be read from local.properties or BuildConfig
        buildConfigField("String", "SUPABASE_URL", "\"https://ttgpplyunomwtqbgsupw.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InR0Z3BwbHl1bm9td3RxYmdzdXB3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU3MjA5MTQsImV4cCI6MjA5MTI5NjkxNH0.8EVuBI_pN0dfpMFamh0szRqONSmDfWm4BNY5MGxL02g\"")
        buildConfigField("String", "SUPABASE_NOVELS_URL", "\"https://lnpkqcvqsppaiafjtzpt.supabase.co\"")
        buildConfigField("String", "SUPABASE_NOVELS_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxucGtxY3Zxc3BwYWlhZmp0enB0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU3MjE0NjEsImV4cCI6MjA5MTI5NzQ2MX0.KjxXOCBnS5vQ7bt8_7tFIAC3dnbN7KCZjmF9qFI-YmE\"")
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
        isCoreLibraryDesugaringEnabled = true
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
}

dependencies {
    // ── Core Android ──
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ── Jetpack Compose ──
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Navigation ──
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ── Supabase ──
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    // ── Image Loading ──
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // ── Video Player ──
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // ── Ad Networks (Unity LevelPlay & Start.io) ──
    implementation("com.startapp:inapp-sdk:5.1.0")
    implementation("com.ironsource.sdk:mediationsdk:8.6.0")
    implementation("com.google.android.gms:play-services-appset:16.0.2")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")

    // ── OneSignal (Push Notifications) ──
    implementation("com.onesignal:OneSignal:5.1.11")

    // ── Datastore (local preferences) ──
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // ── Kotlin Serialization ──
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── Accompanist (System UI Controller) ──
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")

    // ── Java 8+ API Desugaring ──
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // ── Testing ──
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
