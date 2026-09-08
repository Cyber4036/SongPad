# ADR-0031: TakeFileStore lives in :core:model

**Status:** Accepted (placement decision, raised)
**Date:** 2026-09-07

## Context

`TakeFileStore` (path layout and atomic PCM commit) is needed by `:core:audio`, `:core:midi`, `:core:data` and workers. In `:core:data` it would force `:core:audio` → `:core:data`, which the module boundary forbids (audio → model only).

## Decision

`TakeFileStore` is a plain `java.io.File` wrapper in `:core:model` (`take/TakeFileStore.kt`), provided by `DataProvidersModule` with `context.filesDir`. It knows the §9.1 layout and nothing about Android.

## Rejected alternatives

- Duplicating path logic per module — drift between writer and cleaner is the worst failure mode in the app (§21.2).
- Relaxing the boundary — audio must never depend on the database module.

## Consequences

`:core:model` gains a `java.io` dependency, which is fine on the JVM tier.

## References

- spec §5
- spec §9.1
