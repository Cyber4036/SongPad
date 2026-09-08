# ADR-0021: Audio and MIDI excluded from Auto Backup

**Status:** Accepted (LOCKED, spec §4.1)
**Date:** 2026-09-07

## Context

Auto Backup is capped at 25 MB per app; a single song exceeds it.

## Decision

`backup_rules.xml` and `data_extraction_rules.xml` exclude `takes/`, `tmp/` and `exports/`. Only the database and DataStore preferences back up. The consequence — uninstall loses recordings — is stated in Settings.

## Rejected alternatives

- Backing up everything — silently exceeds the quota and backs up nothing.

## Consequences

A manual-backup affordance beyond Export is an open product question (§21.3 #2).

## References

- spec §4.1 Cloud backup
- spec §9.5
