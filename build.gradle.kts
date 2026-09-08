import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// Top-level build file. Plugins are declared here (apply false) so every module shares one version set.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// One JVM target for every Kotlin module.
subprojects {
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Module boundary rules (Phase 1 spec §5). Enforced at configuration time so a
// violation fails EVERY build, not a code review.
//
//   :feature:*      → :core:* only. Never another :feature:* module, never :app.
//   :core:model     → nothing. Pure Kotlin, no Android plugin.
//   :core:ui        → nothing (components take primitives and lambdas).
//   :core:data      → :core:model
//   :core:data:testing → :core:model
//   :core:audio     → :core:model. The only module allowed to have CMake/NDK.
//   :core:midi      → :core:model, :core:audio
//   :spikes:*       → :core:model (+ the shared FTS DDL source directory)
//   :app            → anything
// ─────────────────────────────────────────────────────────────────────────────
val allowedProjectDependencies: Map<String, Set<String>> = mapOf(
    ":core:model" to emptySet(),
    ":core:ui" to emptySet(),
    ":core:data" to setOf(":core:model"),
    ":core:data:testing" to setOf(":core:model"),
    ":core:audio" to setOf(":core:model"),
    ":core:midi" to setOf(":core:model", ":core:audio"),
)

fun isFeature(path: String) = path.startsWith(":feature:")
fun isCore(path: String) = path.startsWith(":core:")
fun isSpike(path: String) = path.startsWith(":spikes:")

val dependencyConfigurations = setOf(
    "api", "implementation", "compileOnly", "runtimeOnly",
    "debugApi", "debugImplementation", "releaseApi", "releaseImplementation",
)
val testConfigurations = setOf(
    "testImplementation", "testApi", "androidTestImplementation", "androidTestApi",
)

gradle.projectsEvaluated {
    val violations = mutableListOf<String>()
    rootProject.subprojects.forEach { p ->
        val from = p.path
        if (from == ":app") return@forEach

        // Rule: only :core:audio may build native code.
        val android = p.extensions.findByName("android")
        if (android is com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>) {
            val cmakePath = android.externalNativeBuild.cmake.path
            if (cmakePath != null && from != ":core:audio") {
                violations += "$from configures CMake/NDK; only :core:audio may touch native code (§5)."
            }
        }
        if (from == ":core:model" && android != null) {
            violations += ":core:model must stay a pure Kotlin module with no Android plugin (§5)."
        }

        p.configurations.forEach { cfg ->
            val isMain = cfg.name in dependencyConfigurations
            val isTest = cfg.name in testConfigurations
            if (!isMain && !isTest) return@forEach
            cfg.dependencies.withType<ProjectDependency>().forEach { dep ->
                val to = dep.path
                if (to == from) return@forEach
                val ok = when {
                    isFeature(from) -> isCore(to) && (isMain || to != ":app")
                    from in allowedProjectDependencies -> to in allowedProjectDependencies.getValue(from) ||
                        (isTest && to == ":core:data:testing" && from != ":core:model")
                    isSpike(from) -> to == ":core:model"
                    else -> false
                }
                if (!ok) violations += "$from (${cfg.name}) → $to is not an allowed module dependency (§5)."
            }
        }
    }
    if (violations.isNotEmpty()) {
        throw GradleException(
            "Module boundary violation(s):\n" + violations.joinToString("\n") { "  • $it" } +
                "\nSee build.gradle.kts (root) and Phase 1 spec §5. A feature module never depends on another feature module.",
        )
    }
}

// Convenience aggregate for CI tier 1: every JVM unit test in the repo, once.
tasks.register("unitTests") {
    group = "verification"
    description = "Runs JVM unit tests for every module (debug variant for Android modules)."
    dependsOn(subprojects.map { sp ->
        sp.tasks.matching { t -> t.name == "testDebugUnitTest" || (t.name == "test" && sp.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) }
    })
}

tasks.register("checkModuleBoundaries") {
    group = "verification"
    description = "Module dependency rules (§5) are validated at configuration time; this task exists so CI can name the check."
    doLast { println("Module boundaries OK (validated at configuration time).") }
}
