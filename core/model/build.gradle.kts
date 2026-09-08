plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :core:model — pure Kotlin. Zero Android dependencies, JVM-unit-testable without Robolectric (§5).
// The §15 contract interfaces are declared here so use cases and :core:data:testing fakes stay pure JVM;
// implementations live in the module §15 names (see docs/adr/0024-contracts-live-in-core-model.md).

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
