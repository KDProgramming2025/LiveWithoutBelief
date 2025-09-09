plugins {
    `java-library`
    kotlin("jvm")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    api(libs.junit)
    api(libs.coroutines.test)
    api(libs.mockk)
    api(libs.okhttp.mockwebserver)
    api(libs.kotlinx.serialization.json)
}

// Expose fixtures to other modules' tests via testFixtures-like pattern
configurations.create("testFixtures")
artifacts.add("testFixtures", tasks.getByName("jar"))
