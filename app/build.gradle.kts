plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * The DeepSeek key for the Premium AI Insights tier, resolved once at configuration time.
 *
 * It is never committed: the three sources are, in order, `-PdeepseekApiKey=...` on the command line,
 * the `DEEPSEEK_API_KEY` environment variable CI sets, and `deepseek.api.key` in the developer's
 * `local.properties` (which is already git-ignored). A checkout with none of them still builds and
 * installs - the chat screen then says the key is missing instead of failing on a call that cannot
 * work. See DeepSeekApiClient for the header it ends up in.
 */
val deepSeekApiKey: Provider<String> = providers.gradleProperty("deepseekApiKey")
    .orElse(providers.environmentVariable("DEEPSEEK_API_KEY"))
    .orElse(
        providers.provider {
            val localProperties = rootProject.file("local.properties")
            localProperties.takeIf { it.exists() }
                ?.readLines()
                ?.firstOrNull { line -> line.trimStart().startsWith("deepseek.api.key") }
                ?.substringAfter('=')
                ?.trim()
                .orEmpty()
        }
    )

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

        // Baked into the APK as a constant: the app has no backend to hand out keys, so the key a
        // build ships with is the key the chat uses.
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${deepSeekApiKey.get()}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // DeepSeekApiClient reads the key from BuildConfig, which AGP no longer generates by default.
        buildConfig = true
    }

}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core:1.7.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.2")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Offline-first persistence: everything the app knows about spending lives in Room on-device
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // What the user chose about the app itself - onboarding done, name, theme, key - is a handful of
    // switches rather than rows, so it belongs in DataStore next to the ledger rather than inside it
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
    // Accompanist
    implementation("com.google.accompanist:accompanist-permissions:0.35.0-alpha")
    // On-device OCR: the bundled recognizer, so receipt text never leaves the phone and needs no
    // first-run model download (the benchmark showed its line grouping beats EasyOCR on receipts)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // `.await()` on the Task the recognizer hands back, so extraction stays a plain suspend call
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // Premium AI Insights: the DeepSeek HTTP call (transport, plus the JSON codec for its body)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    // Room ships a schema JSON per version; keeping them is what makes future migrations reviewable
    arg("room.schemaLocation", "$projectDir/schemas")
}