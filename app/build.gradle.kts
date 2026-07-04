plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -PversionCode and -PversionName via the command line.
// Local builds fall back to defaults.
val ciVersionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
val ciVersionName = (project.findProperty("versionName") as? String) ?: "1.0"

// Release signing is driven entirely by environment variables so no
// keystore file ever lives in the repository.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "app.tellyfin.androidtv"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.tellyfin.androidtv"
        minSdk = 21
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "VERSION_NAME", "\"$ciVersionName\"")
    }

    buildFeatures {
        buildConfig = true
    }

    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        // Produce the HTML report; don't abort so lint issues show up as
        // annotations in PRs rather than hard build failures.
        abortOnError = false
        htmlReport = true
        htmlOutput = file("build/reports/lint/lint-results.html")
        sarifReport = true
        sarifOutput = file("build/reports/lint/lint-results.sarif")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose for TV
    implementation("androidx.tv:tv-material:1.0.0-beta01")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")

    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Jellyfin Kotlin SDK
    implementation("org.jellyfin.sdk:jellyfin-core:1.8.11")
    // SLF4J binding required by jellyfin-core's kotlin-logging dependency
    implementation("org.slf4j:slf4j-nop:2.0.16")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil for channel logos
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
