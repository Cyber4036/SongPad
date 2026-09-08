plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Spike S-4 (§21.1): UUIDv7 insert throughput at 2,000 rows and FTS5 external-content-style index + triggers
// under BundledSQLiteDriver. Runs on the JVM against the SAME bundled SQLite build the app ships, using the
// SAME DDL source file as :core:data (shared by source directory, not copied). `./gradlew :spikes:s4-fts5:run`
// writes docs/spikes/S-4-report.md. The functional checks also run as unit tests in CI tier 1.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        kotlin.srcDir("../../core/data/src/main/kotlin/com/songpad/core/data/db/fts")
    }
}

application {
    mainClass.set("com.songpad.spikes.s4.S4SpikeKt")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.sqlite.bundled.jvm)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
}

// The report path is relative to the repository root, not the module directory.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
