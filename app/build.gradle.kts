plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    // Make plugin available on classpath but don't apply automatically
    alias(libs.plugins.google.services) apply false
    // For JSON parsing in AuthFacade (kotlinx serialization)
    alias(libs.plugins.kotlin.serialization)
}

// Apply Google Services plugin only if a google-services.json is present (CI-safe)
val hasGoogleServicesJson =
    listOf(
        file("google-services.json"),
        file("src/google-services.json"),
        file("src/debug/google-services.json"),
    ).any { it.exists() }
if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
    println("[google-services] google-services.json found; applying plugin")
} else {
    println("[google-services] google-services.json not found; skipping plugin (OK for CI)")
}

android {
    namespace = "info.lwb"
    compileSdk = 36

    defaultConfig {
        applicationId = "info.lwb"
        minSdk = 26
        targetSdk = 36
        // Versioning: derive from env GIT_TAG (e.g., v0.2.3) when available; else fallback
        val gitTag = System.getenv("GIT_TAG") ?: (project.findProperty("GIT_TAG") as String?)
        val parsed = gitTag?.removePrefix("v")
        val verName = parsed ?: "0.1.0"
        val verCode =
            verName.split('.').let { parts ->
                val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                // semantic version to code: MMMmmpp (e.g., 0.1.0 -> 001010)
                (major * 10000) + (minor * 100) + patch
            }
        versionCode = verCode
        versionName = verName
        testInstrumentationRunner = "info.lwb.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true
        // Resolve Google client ids (web/server & android). Prefer explicit env/gradle; else parse google-services.json if placeholders.
        var serverId =
            System.getenv("GOOGLE_SERVER_CLIENT_ID")
                ?: (project.findProperty("GOOGLE_SERVER_CLIENT_ID") as String?)
                ?: "CHANGE_ME_SERVER_CLIENT_ID"
        var androidClientId =
            System.getenv("GOOGLE_ANDROID_CLIENT_ID")
                ?: (project.findProperty("GOOGLE_ANDROID_CLIENT_ID") as String?)
                ?: "CHANGE_ME_ANDROID_CLIENT_ID"
        try {
            val gs = file("google-services.json")
            if (gs.exists()) {
                val txt = gs.readText()
                // client_type 3 = web client (used for requestIdToken)
                val webMatch = Regex(""""client_id"\s*:\s*"([^"]+)"\s*,\s*"client_type"\s*:\s*3""").find(txt)
                val androidMatch = Regex(""""client_id"\s*:\s*"([^"]+)"\s*,\s*"client_type"\s*:\s*1""").find(txt)
                if (serverId == "CHANGE_ME_SERVER_CLIENT_ID") webMatch?.groupValues?.get(1)?.let { serverId = it }
                if (androidClientId == "CHANGE_ME_ANDROID_CLIENT_ID") androidMatch?.groupValues?.get(1)?.let { androidClientId = it }
            }
        } catch (e: Exception) {
            if (project.hasProperty("org.gradle.logging.stacktrace")) {
                println("[auth-config] Warning: failed to parse google-services.json: ${e.message}")
            }
        }
        // Fallback: if android id still placeholder, reuse server id (not ideal but non-fatal)
        if (androidClientId.startsWith("CHANGE_ME") && !serverId.startsWith("CHANGE_ME")) androidClientId = serverId
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", '"' + serverId + '"')
        buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", '"' + androidClientId + '"')
        val emailLinkContinue =
            System.getenv("EMAIL_LINK_CONTINUE_URL")
                ?: (project.findProperty("EMAIL_LINK_CONTINUE_URL") as String?)
                ?: "https://live-without-belief-app.firebaseapp.com/emailLink"
        buildConfigField("String", "EMAIL_LINK_CONTINUE_URL", '"' + emailLinkContinue + '"')
    val authBase =
            System.getenv("AUTH_BASE_URL")
                ?: (project.findProperty("AUTH_BASE_URL") as String?)
        ?: "https://aparat.feezor.net/LWB/API"
        buildConfigField("String", "AUTH_BASE_URL", '"' + authBase + '"')
        // Central API base URL for all endpoints
    val apiBase =
            System.getenv("API_BASE_URL")
                ?: (project.findProperty("API_BASE_URL") as String?)
        ?: "https://aparat.feezor.net/LWB/API/"
        buildConfigField("String", "API_BASE_URL", '"' + apiBase + '"')
    val uploadsBase =
        System.getenv("UPLOADS_BASE_URL")
        ?: (project.findProperty("UPLOADS_BASE_URL") as String?)
        ?: "https://aparat.feezor.net/LWB/Admin/uploads/"
    buildConfigField("String", "UPLOADS_BASE_URL", '"' + uploadsBase + '"')
    // CAPTCHA note: using self-hosted ALTCHA; no Google reCAPTCHA BuildConfig needed

        // Optional tuning knobs (env / Gradle property override; fallback to sensible defaults)
        fun intCfg(
            key: String,
            def: Int,
        ) = (System.getenv(key) ?: (project.findProperty(key) as String?))?.toIntOrNull() ?: def

        fun longCfg(
            key: String,
            def: Long,
        ) = (System.getenv(key) ?: (project.findProperty(key) as String?))?.toLongOrNull() ?: def

        fun doubleCfg(
            key: String,
            def: Double,
        ) = (System.getenv(key) ?: (project.findProperty(key) as String?))?.toDoubleOrNull() ?: def
        val maxAttempts = intCfg("AUTH_VALIDATION_MAX_ATTEMPTS", 3)
        val baseDelay = longCfg("AUTH_VALIDATION_BASE_DELAY_MS", 50)
        val backoffMult = doubleCfg("AUTH_VALIDATION_BACKOFF_MULT", 2.0)
        val refreshLeadSec = longCfg("AUTH_REFRESH_LEAD_SECONDS", 300)
        val refreshPollSec = longCfg("AUTH_REFRESH_POLL_SECONDS", 30)
        val metricsSamplePermille = intCfg("AUTH_VALIDATION_METRICS_SAMPLE_PERMILLE", 1000)
        buildConfigField("int", "AUTH_VALIDATION_MAX_ATTEMPTS", maxAttempts.toString())
        buildConfigField("long", "AUTH_VALIDATION_BASE_DELAY_MS", baseDelay.toString() + 'L')
        buildConfigField("double", "AUTH_VALIDATION_BACKOFF_MULT", backoffMult.toString())
        buildConfigField("long", "AUTH_REFRESH_LEAD_SECONDS", refreshLeadSec.toString() + 'L')
        buildConfigField("long", "AUTH_REFRESH_POLL_SECONDS", refreshPollSec.toString() + 'L')
        buildConfigField("int", "AUTH_VALIDATION_METRICS_SAMPLE_PERMILLE", metricsSamplePermille.toString())
    }

    buildFeatures {
        // Compose UI toolkit
        compose = true
        // Required because we declare custom buildConfigField(s) (e.g. GOOGLE_SERVER_CLIENT_ID)
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging.resources.excludes +=
        setOf("META-INF/{AL2.0,LGPL2.1}")

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    // Optional release signing from environment (CI-friendly). If not provided, release stays unsigned.
    val ksPath = System.getenv("SIGNING_KEYSTORE_PATH") ?: (project.findProperty("SIGNING_KEYSTORE_PATH") as String?)
    val ksPass = System.getenv("SIGNING_KEYSTORE_PASSWORD") ?: (project.findProperty("SIGNING_KEYSTORE_PASSWORD") as String?)
    val keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: (project.findProperty("SIGNING_KEY_ALIAS") as String?)
    val keyPass = System.getenv("SIGNING_KEY_PASSWORD") ?: (project.findProperty("SIGNING_KEY_PASSWORD") as String?)
    if (!ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && !keyAlias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPass
                this.keyAlias = keyAlias
                keyPassword = keyPass
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
        println("[signing] Using provided release signing config")
    } else {
        println("[signing] No signing env provided; building unsigned release (OK for artifacts)")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    // Retain desugaring if other libs require it
    isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":ui:design-system"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":data:repo"))
    implementation(project(":data:network"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:search"))
    implementation(project(":feature:bookmarks"))
    implementation(project(":feature:annotations"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)
    // Crashlytics temporarily disabled until plugin onboarding
    // implementation(libs.firebase.crashlytics)
    // Firebase Performance temporarily disabled
    // implementation("com.google.firebase:firebase-perf-ktx")
    implementation(libs.play.services.auth)
    implementation(libs.credential.manager)
    implementation(libs.credential.manager.play.services)
    implementation(libs.androidx.security.crypto)
    implementation(libs.google.identity)
    implementation(libs.okhttp)
    // Using self-hosted ALTCHA via WebView asset
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Hilt instrumentation testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:${libs.versions.hilt.get()}")
    kaptAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation("androidx.test:core:1.6.1")

    implementation(libs.navigation.compose)
    // Background periodic sync
    implementation(libs.work.runtime)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("androidx.work:work-testing:${libs.versions.work.get()}")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
