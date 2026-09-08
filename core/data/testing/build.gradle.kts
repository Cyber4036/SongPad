plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :core:data:testing — pure Kotlin fakes for every §15 interface (and the non-frozen repositories),
// so feature work starts against fakes before implementations land (Prompt 0-A, item 5).

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
