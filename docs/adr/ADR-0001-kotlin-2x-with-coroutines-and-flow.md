# ADR-0001: Kotlin 2.x with coroutines and Flow

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

SongPad is an Android-only, single-process app whose core problems are stateful streams (amplitudes, playback position, reactive queries) and structured shutdown of native resources.

## Decision

Kotlin 2.x is the only application language. Concurrency is coroutines + Flow; every §15 contract exposes `StateFlow`/`SharedFlow` and `suspend` functions. The JVM tier (`:core:model`, `:core:data:testing`, `:spikes:s4-fts5`) is pure Kotlin with no Android dependency.

## Rejected alternatives

- Java + RxJava — boilerplate, no Compose compiler benefit, two concurrency models in one codebase.

## Consequences

Structured concurrency makes engine teardown (§7.5) and worker cancellation (§12.2) explicit. The capture loop is exempt from coroutines entirely (see ADR-0009).

## References

- spec §4.1 Language
