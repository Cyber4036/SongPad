# ADR-0032: Toolchain pins: AGP 8.13 / Kotlin 2.3 / compileSdk 36 and the AndroidX ceiling that implies

**Status:** Accepted (engineering, revisit after Milestone 1)
**Date:** 2026-09-07

## Context

The newest AndroidX trains (Compose 1.12, core 1.19, lifecycle 2.11, hilt-androidx 1.4) require AGP 9.1 and compileSdk 37; Hilt 2.60+ requires AGP 9.0.

## Decision

Stay on AGP 8.13.2, Gradle 8.14.3, Kotlin 2.3.21, KSP 2.3.11, JDK 17, compileSdk/targetSdk 36, and pin AndroidX to the newest versions whose AAR metadata accepts that: Compose BOM 2026.06.01 (Compose 1.11.4, Material3 1.4.0), core 1.18.0, lifecycle 2.10.0, activity 1.13.0, hilt-androidx 1.3.0, Navigation 3 1.1.7, lifecycle-viewmodel-navigation3 2.10.0, Room 2.8.4, Media3 1.11.0, WorkManager 2.11.2, Hilt 2.58.

## Rejected alternatives

- AGP 9.1 + compileSdk 37 now — a large migration (built-in Kotlin, plugin changes) in the middle of Phase 1 with no feature benefit.

## Consequences

Plan the AGP 9 move as its own task after the vertical slice is validated on hardware. Versions are recorded in gradle/libs.versions.toml with this ADR as the reason not to bump blindly.

## References

- gradle/libs.versions.toml
