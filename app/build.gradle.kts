plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    // For JSON parsing in AuthFacade (kotlinx serialization)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "info.lwb"
    compileSdk = 36

    defaultConfig {
        applicationId = "info.lwb"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    testInstrumentationRunner = "info.lwb.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true
        // Resolve Google client ids (web/server & android). Prefer explicit env/gradle; else parse google-services.json if placeholders.
        var serverId = System.getenv("GOOGLE_SERVER_CLIENT_ID")
            ?: (project.findProperty("GOOGLE_SERVER_CLIENT_ID") as String?)
            ?: "CHANGE_ME_SERVER_CLIENT_ID"
        var androidClientId = System.getenv("GOOGLE_ANDROID_CLIENT_ID")
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
            if (project.hasProperty("org.gradle.logging.stacktrace") ) {
                println("[auth-config] Warning: failed to parse google-services.json: ${e.message}")
            }
        }
        // Fallback: if android id still placeholder, reuse server id (not ideal but non-fatal)
        if (androidClientId.startsWith("CHANGE_ME") && !serverId.startsWith("CHANGE_ME")) androidClientId = serverId
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", '"' + serverId + '"')
        buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", '"' + androidClientId + '"')
        val emailLinkContinue = System.getenv("EMAIL_LINK_CONTINUE_URL")
            ?: (project.findProperty("EMAIL_LINK_CONTINUE_URL") as String?)
            ?: "https://live-without-belief-app.firebaseapp.com/emailLink"
        buildConfigField("String", "EMAIL_LINK_CONTINUE_URL", '"' + emailLinkContinue + '"')
        val authBase = System.getenv("AUTH_BASE_URL")
            ?: (project.findProperty("AUTH_BASE_URL") as String?)
            ?: "https://aparat.feezor.net/lwb-api"
        buildConfigField("String", "AUTH_BASE_URL", '"' + authBase + '"')
        var recaptchaSiteKey = System.getenv("RECAPTCHA_SITE_KEY")
            ?: System.getenv("RECAPTCHA_KEY")
            ?: (project.findProperty("RECAPTCHA_SITE_KEY") as String?)
            ?: (project.findProperty("RECAPTCHA_KEY") as String?)
            ?: "CHANGE_ME_RECAPTCHA_SITE_KEY"
        // Fallback: parse root .env if placeholder still present (dev convenience only)
        if (recaptchaSiteKey.startsWith("CHANGE_ME")) {
            val envFile = rootProject.file(".env")
            if (envFile.exists()) {
                envFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (!trimmed.startsWith('#') && trimmed.startsWith("RECAPTCHA_KEY=")) {
                        recaptchaSiteKey = trimmed.substringAfter('=')
                    }
                }
            }
        }
        if (recaptchaSiteKey.startsWith("CHANGE_ME")) {
            println("[recaptcha-config] WARNING: Using placeholder RECAPTCHA site key. reCAPTCHA will be disabled (tokens null). Set RECAPTCHA_KEY in env or .env.")
        } else {
            println("[recaptcha-config] Using RECAPTCHA site key prefix=" + recaptchaSiteKey.take(8) + "…")
        }
        buildConfigField("String", "RECAPTCHA_SITE_KEY", '"' + recaptchaSiteKey + '"')
    // Optional tuning knobs (env / Gradle property override; fallback to sensible defaults)
    fun intCfg(key: String, def: Int) = (System.getenv(key) ?: (project.findProperty(key) as String?))?.toIntOrNull() ?: def
    fun longCfg(key: String, def: Long) = (System.getenv(key) ?: (project.findProperty(key) as String?))?.toLongOrNull() ?: def
    fun doubleCfg(key: String, def: Double) = (System.getenv(key) ?: (project.findProperty(key) as String?))?.toDoubleOrNull() ?: def
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    // Required for recaptcha beta library (core library desugaring)
    isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":data:repo"))
    implementation(project(":data:network"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:search"))
    implementation(project(":feature:bookmarks"))
    implementation(project(":feature:annotations"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)
    implementation(libs.credential.manager)
    implementation(libs.credential.manager.play.services)
    implementation(libs.androidx.security.crypto)
    implementation(libs.google.identity)
    implementation(libs.okhttp)
    // reCAPTCHA (modern API per documentation)
    implementation("com.google.android.recaptcha:recaptcha:18.8.0-beta03")
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
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
