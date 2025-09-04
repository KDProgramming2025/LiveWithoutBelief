plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "info.lwb.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Only androidTest source is relevant for macrobenchmarks
    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("androidTest") { java.srcDirs("src/androidTest/kotlin") }
    }

    packaging.resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}")
}

dependencies {
    implementation(project(":app"))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

// Enable macrobenchmark to run without animations for stable metrics
android {
    testOptions.managedDevices.devices.create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
        device = "Pixel 6"
        apiLevel = 34
        systemImageSource = "aosp"
    }
}
