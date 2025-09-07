plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
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
        // Resolve Google server client id from env or Gradle property; fallback to placeholder
        val serverId = System.getenv("GOOGLE_SERVER_CLIENT_ID")
            ?: (project.findProperty("GOOGLE_SERVER_CLIENT_ID") as String?)
            ?: "CHANGE_ME_SERVER_CLIENT_ID"
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", '"' + serverId + '"')
        val authBase = System.getenv("AUTH_BASE_URL")
            ?: (project.findProperty("AUTH_BASE_URL") as String?)
            ?: "https://aparat.feezor.net/lwb-api"
        buildConfigField("String", "AUTH_BASE_URL", '"' + authBase + '"')
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
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Hilt instrumentation testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:${libs.versions.hilt.get()}")
    kaptAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation("androidx.test:core:1.6.1")

    implementation(libs.navigation.compose)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
