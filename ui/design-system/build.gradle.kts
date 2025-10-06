plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
    // Hilt & KSP needed because this module defines a @Module for ImageLoader
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "info.lwb.ui.designsystem"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)

    // Shared model types (Article) used by image prefetch helper
    implementation(project(":core:model"))

    // Coil (compose + svg decoder) for image loading infrastructure
    implementation(libs.coil)
    implementation("io.coil-kt:coil-svg:${libs.versions.coil.get()}")

    // Hilt DI for ImageLoaderModule
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Coroutines for prefetch logic
    implementation(libs.coroutines.core)

    // Compose previews
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(project(":core:test-fixtures"))
    debugImplementation(libs.androidx.ui.tooling)
}

// Run all tests, including Paparazzi, on CI
