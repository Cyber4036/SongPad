# ADR-0020: Export via SAF CreateDocument and FileProvider + ACTION_SEND

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

"Save to my files" and "send to someone" are different intents and both are required.

## Decision

`ExportScreen` offers both: `ActivityResultContracts.CreateDocument("application/zip")` copying the staged bundle through `contentResolver.openOutputStream`, and `FileProvider.getUriForFile` + `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`. The bundle is built once by `ExportWorker` into `files/tmp/`.

## Rejected alternatives

- `file://` URIs — `FileUriExposedException` since Android 7.
- Putting bytes in Intent extras — hits the Binder transaction limit.

## Consequences

Large bundles are fine because receivers stream through `ContentResolver`; per-app attachment caps are theirs to surface.

## References

- spec §4.1 Export
- spec §12.3
