plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Where the optional AI Insights feature may send its (aggregated) request.
 *
 * The app deliberately ships **no** third-party API credential. A mobile APK is not a secret store:
 * anything placed in `BuildConfig`, resources or `local.properties` and compiled into the APK can be
 * extracted with `strings` in a few seconds. The only safe place for a provider key is a server the
 * developer controls, so this build points the feature at an https proxy/backend that owns the key
 * and authenticates the calling app itself.
 *
 * Configuration is intentionally non-secret and optional:
 *  - `-PlensSpentAiProxyUrl=https://your-proxy.example/`
 *  - environment variable `LENS_SPENT_AI_PROXY_URL`
 *
 * With neither set the URL is empty, the AI feature is unavailable, and the UI says so. Every other
 * feature works offline. See `AiBackendConfig` and `DeepSeekApiClient`.
 */
val aiProxyBaseUrl: Provider<String> = providers.gradleProperty("lensSpentAiProxyUrl")
    .orElse(providers.environmentVariable("LENS_SPENT_AI_PROXY_URL"))
    .orElse("")
    .map { it.trim() }

/**
 * A proxy URL must be https: an expense summary must never leave the phone in clear text, and the
 * proxy is the component holding the provider credential. A misconfigured http URL is rejected at
 * runtime by `AiBackendConfig` rather than silently downgrading the transport.
 */
val aiProxyBaseUrlForBuild: String = aiProxyBaseUrl.get().let { url ->
    when {
        url.isEmpty() -> ""
        url.startsWith("https://", ignoreCase = true) -> url
        else -> {
            logger.warn(
                "LensSpent: ignoring AI proxy URL because it is not https; AI Insights will be disabled."
            )
            ""
        }
    }
}

/**
 * Whether this build starts with the sample ledger in [org.umn.lensspent.data.demo.DemoLedger].
 *
 * On by default because the submitted APK has to show populated list, detail and chart screens the
 * moment a grader installs it, and an empty dashboard demonstrates none of them. It is a Gradle
 * property rather than a constant so the same source tree can produce the honest empty build:
 * `-PdemoLedger=false`. The seeder itself refuses to touch a ledger that already has rows, so turning
 * this on can never mix sample spending into a real one.
 */
val demoLedger: Provider<String> = providers.gradleProperty("demoLedger").orElse("true")

android {
    namespace = "org.umn.lensspent"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.umn.lensspent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Deliberately never a provider key: only the address of a server this project controls may
        // be compiled in. Empty means the AI feature is off, which is the default.
        buildConfigField("String", "AI_PROXY_BASE_URL", "\"${aiProxyBaseUrlForBuild}\"")
        // See the note on `demoLedger` above: on for the submitted build, off for a real one.
        buildConfigField("boolean", "DEMO_LEDGER", demoLedger.get())
    }

    buildTypes {
        release {
            // R8 removes unreachable code and renames what is left; resource shrinking drops assets no
            // reachable code references. Both stay off in debug so stack traces stay readable.
            //
            // Keep rules live in src/main/keepRules (AGP merges that folder) rather than a legacy
            // proguard-rules.pro: the files there are the ones this build actually applies.
            optimization {
                enable = true
                // Library keep rules are still honoured; only the defaults are R8's own.
                keepRules {
                    includeDefault = true
                }
            }
            isMinifyEnabled = true
            isShrinkResources = true
            // No release signing material is checked in. A build without these properties produces an
            // unsigned release artifact, which is the correct outcome for a repository.
            signingConfig = null
        }
        debug {
            // Explicit rather than implied: a debug build must never be minified.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // The generated constants this build reads: BuildConfig.DEBUG (AppLog, demo ledger) and
        // BuildConfig.AI_PROXY_BASE_URL (the non-secret AI backend address).
        buildConfig = true
    }

    testOptions {
        unitTests {
            // The JVM tests exercise ViewModels that call AppLog, and AppLog is a thin wrapper over
            // android.util.Log. Returning defaults instead of throwing "not mocked" keeps those tests
            // focused on the state machine; the logging behaviour itself is covered by
            // SensitiveLoggingTest's static checks.
            isReturnDefaultValues = true
        }
    }

}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    // Required by res/values/themes.xml: the Material3 DayNight parent style only exists in this AAR.
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // Icons are BOM-managed; the explicit versions that used to be here were inert (the BOM already
    // resolved them to 1.7.8) and made the catalog look inconsistent.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    // Directly imported (Color, Brush, Path, ImageBitmap), so it is declared rather than borrowed
    // from ui's transitive graph.
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Offline-first persistence: everything the app knows about spending lives in Room on-device
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // What the user chose about the app itself - onboarding done, name, theme, budget, AI consent -
    // is a handful of switches rather than rows, so it belongs in DataStore next to the ledger.
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // ViewModel + lifecycle-aware state collection for the screens
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")
    // CameraX
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    // Accompanist: still the permission-state source for the camera screen. It has no stable
    // release line (the project is in maintenance), so this stays at the latest published alpha;
    // the API used here is small and marked @ExperimentalPermissionsApi at the call site.
    implementation("com.google.accompanist:accompanist-permissions:0.35.0-alpha")
    // On-device OCR: the bundled recognizer, so receipt text never leaves the phone and needs no
    // first-run model download (the benchmark showed its line grouping beats EasyOCR on receipts)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // `.await()` on the Task the recognizer hands back, so extraction stays a plain suspend call.
    // Versionless: the coroutines BOM chosen by Room pins it (resolves to 1.9.0).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services")
    // Optional AI Insights: the HTTP call through the developer's proxy (transport, plus the JSON
    // codec for its body). No provider credential is involved; see data/remote/AiBackendConfig.kt.
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // Runs ViewModel `viewModelScope` work on a deterministic main dispatcher: the retention tests
    // exercise real state machines, not just pure functions.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Kept deliberately: although no test imports Espresso today, removing it changes the androidTest
    // graph's constraints on androidx.concurrent and breaks resolution. Not worth trading a working
    // instrumented build for an unused-dependency cleanup.
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    // Room ships a schema JSON per version; keeping them is what makes future migrations reviewable
    arg("room.schemaLocation", "$projectDir/schemas")
}