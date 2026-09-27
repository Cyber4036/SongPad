# ADR-0036: Beta crash reporting is Play Console Android vitals — no SDK, no network

**Status:** Accepted (decided by the product owner)
**Date:** 2026-09-27

## Context

Spec §19's Beta row (Dev A: "Release variants, crash reporting") and Wave 6 of the delegation prompts both call
for crash reporting. `docs/play/README.md`'s Data Safety answers and privacy-policy draft state the opposite:
no analytics, **no crash reporting** and no network access, and the app declares no `INTERNET` permission.
Phase 0 also requires core capture, editing, organization, playback and export to be offline and local-first.
That is a real contradiction, so it was raised instead of settled in code.

## Decision

Crash and ANR reporting for beta and V1 comes from **Google Play Console Android vitals**. Play collects it for
any build installed from a Play track (internal, closed or open testing, and production). No SDK is added, the
manifest gains no `INTERNET` permission, and the Data Safety form and privacy policy stay true as written. Play
itself collects vitals under the user's device-level diagnostics consent, not the app.

Beta testers must therefore install from the Play **internal or closed testing track**, not by sideloading an
APK. Sideloaded builds report nothing. For crashes a tester reproduces, the fallback is a manual
`adb bugreport` or a screen recording (`docs/release/BETA_RELEASE.md`).

Debug builds enable `StrictMode` (log-only) in `SongPadApplication` to surface main-thread I/O and leaked
resources during device sessions. That is a development aid, not reporting.

## Rejected alternatives

- **Local-only crash log** (an uncaught-exception handler writing to app-private storage, which testers share
  via the share sheet). It keeps Data Safety valid, but vitals covers the beta questions without new UI or code.
  It remains the next step if vitals turns out too coarse, since it needs no policy change.
- **Network crash SDK (Crashlytics, Sentry).** Rejected: it needs `INTERNET`, a rewritten Data Safety form and
  privacy policy, and it breaks the "audio never leaves the device / no network" posture the product has
  committed to publicly.

## Consequences

Vitals gives stack traces with R8 deobfuscation only if the mapping file is uploaded with each bundle. The
release runbook makes uploading `app/build/outputs/mapping/release/mapping.txt` a required step.
