plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.sentry.android.gradle")
}

// CI passes -PversionCode and -PversionName via the command line.
// Local builds fall back to defaults.
val ciVersionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
val ciVersionName = (project.findProperty("versionName") as? String) ?: "1.0"

// DSNs are ingest endpoints, not secrets, so a working default ships in the repo.
// SENTRY_DSN lets CI/forks point at a different project without editing this file.
val sentryDsn = System.getenv("SENTRY_DSN")
    ?: "https://d82ee917522c3dfd60bd2738553123a2@o470302.ingest.us.sentry.io/4512139886526464"

// Release signing is driven entirely by environment variables so no
// keystore file ever lives in the repository.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

// The auth token authorizes uploading proguard mappings so release-build stack traces
// symbolicate in Sentry. Like the keystore, it only ever comes from the environment —
// its absence (e.g. local debug builds) just turns the upload off rather than failing.
// An unset GitHub Actions secret expands to an empty string here, not a missing variable,
// so a plain null-check alone would still "find" a token and fail uploading with it.
val sentryAuthToken: String? = System.getenv("SENTRY_AUTH_TOKEN")?.takeIf { it.isNotBlank() }

android {
    namespace = "app.tellyfin.androidtv"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tellyfin.androidtv"
        minSdk = 21
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "VERSION_NAME", "\"$ciVersionName\"")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        // Pre-release versions (e.g. 2.0.0-beta.2) are internal test builds: they get the same
        // verbose diagnostics as debug builds, since that's the only way to see them on a TV.
        buildConfigField("boolean", "PRERELEASE", "${ciVersionName.contains('-')}")
    }

    buildFeatures {
        buildConfig = true
    }

    // Two variants for CI: `sentry` has the crash-reporting SDK as a dependency at all;
    // `noSentry` doesn't — its APK contains zero Sentry code, not just a disabled flag. Same
    // applicationId on purpose — installing one is meant to replace the other, matching normal
    // "pick a build and go" expectations, not run side by side as separate apps.
    //
    // `distribution` is orthogonal: `direct` is today's sideload build with the in-app
    // updater active; `store` is for Play/Amazon, where self-updating outside the store's
    // own mechanism is against policy, so the updater is compiled in but never triggers.
    flavorDimensions += listOf("telemetry", "distribution")
    productFlavors {
        create("sentry") {
            dimension = "telemetry"
        }
        create("noSentry") {
            dimension = "telemetry"
        }
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
        }
        create("store") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
        }
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
            // Bundles native debug symbols (Media3/ExoPlayer, Sentry's native crash handler)
            // into the AAB so Play can symbolicate native crashes/ANRs without a manual upload.
            ndk {
                debugSymbolLevel = "FULL"
            }
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

sentry {
    org.set("msitpro-development")
    projectName.set("tellyfin")
    authToken.set(sentryAuthToken)
    autoUploadProguardMapping.set(sentryAuthToken != null)
    // The noSentry flavor doesn't depend on the Sentry SDK at all — nothing to instrument
    // or upload mappings for.
    ignoredFlavors = listOf("noSentry")
    // We init manually and don't use Fragments or Compose performance tracing. Auto-installation
    // was also pulling in companion modules (sentry-compose, sentry-android-fragment) pinned to
    // a different version (8.56.0) than our explicit sentry-android (8.58.0), which crashed the
    // app on launch — Sentry refuses to start with mismatched module versions.
    autoInstallation {
        enabled = false
    }
    // Bytecode-level instrumentation (a separate mechanism from autoInstallation's dependency
    // adding) still wove in a reference to io.sentry.okhttp.SentryOkHttpEventListener despite
    // its dependency being disabled above, crashing with NoClassDefFoundError the moment any
    // OkHttpClient was built. We don't use Sentry performance tracing at all, so turn this off.
    tracingInstrumentation {
        enabled = false
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
    implementation("androidx.compose.material:material-icons-core")
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

    // Crash/error reporting — only the `sentry` flavor depends on this at all
    "sentryImplementation"("io.sentry:sentry-android:8.58.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
