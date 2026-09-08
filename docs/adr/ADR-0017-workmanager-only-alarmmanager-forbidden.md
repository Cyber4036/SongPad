# ADR-0017: WorkManager only — AlarmManager forbidden

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Nudges are a coarse cadence by product decision; maintenance and transcodes need reboot persistence and battery awareness, not exact timing.

## Decision

Every deferred job is a WorkManager worker: `TranscodeWorker`, `PurgeWorker`, `OrphanCleanupWorker`, `NudgeWorker`, `ExportWorker`. `NudgeScheduler` uses exactly the §11.1 configuration (24 h period, 6 h flex, battery-not-low, exponential backoff 30 min) plus in-worker jitter. See ADR-0028 for the ban itself.

## Rejected alternatives

- `AlarmManager` / `setExactAndAllowWhileIdle` — Play policy scrutiny for no product benefit; forbidden.

## Consequences

A PR importing `android.app.AlarmManager` is rejected by default and needs a superseding ADR. Delivery timing on aggressive OEMs remains unfixable in code; copy says so.

## References

- spec §4.1 Background scheduling
- spec §11.1
