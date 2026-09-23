# ADR-0034: Drop the Section→Take quick-capture journey; correct the Wave 4 prompt

**Status:** Accepted
**Date:** 2026-09-23

## Context

`SongPad_Phase_1_Delegation_Prompts.md`'s Wave 4 (Integration) test-journey list includes:

> `Song → Lyrics → Chorus → quick capture → Voice → Save → auto-associated with Chorus`

This describes recording a Take directly from a lyric Section and having that Take carry a
reference back to the Section — i.e. a Section-to-Take reference.

`SongPad_Phase_1_Development_Specification.md` §22 ("Deferred to V2") lists explicitly:

> Section-to-Take references.

and frames the whole list as "the Phase 0 §22 boundary as an engineering commitment, so nothing
is built speculatively." `Take` is also a frozen §15 contract living in `:core:model`
(ADR-0030); adding a `sectionId` field to it is a LOCKED-decision change, not an implementation
detail inside the contract.

The prompts file states its own precedence rule: "where this file and the spec disagree, **the
spec wins** and this file is wrong and should be corrected." This is exactly that case — the
prompt was written before, or without reconciling against, the §22 boundary.

A spec-compliant alternative exists that gets a similar user-facing outcome without touching the
Take contract or the V2 boundary: `NoteEntity` already carries independent, optional
`sectionRefId` and `takeRefId` (both settable at once) — a Note that references a Section and a
Take together is in-scope V1 work. This was raised to the user rather than built silently, per
the standing "raise, don't implement" rule for changes to LOCKED decisions.

## Decision

Drop the Section→Take quick-capture journey from V1 entirely. `Take` is not given a `sectionId`
field. No section-scoped capture route is built. The Wave 4 prompts-file line above is corrected
in place to point at this ADR instead of describing a journey that violates §22.

The real Section↔Take association (if wanted at all before V2) is a **separate, not-yet-decided**
question of whether to build the Note-mediated version (auto-create a linking Note with both refs
set on save) — not addressed by this ADR. No code for it exists as of this decision.

## Rejected alternatives

- **Build via linking Note now.** Technically spec-compliant (no frozen-contract change), but the
  user chose not to build any version of this journey in V1 — dropping it entirely was preferred
  over a partial, differently-shaped substitute for a journey the spec already defers.
- **Build it as originally scoped** (`Take.sectionId` + section-scoped capture route), accepting
  an explicit override of §22. Rejected — no product justification was raised for pulling a V2
  item into V1, and doing so would need its own ADR amending §22, not a silent implementation.

## Consequences

- Wave 4's cross-feature journey list no longer includes a Section→Take journey; the remaining two
  journeys in that list (search → original context, nudge → deep link into target) are unaffected
  and were independently verified as already built (2026-09-21 audit).
- If a future V1 need for Section↔Take linking emerges, start from the Note-mediated approach
  documented here as the rejected-but-viable alternative, not from a `Take` schema change.

## References

- `SongPad_Phase_1_Development_Specification.md` §22 (Deferred to V2)
- `SongPad_Phase_1_Delegation_Prompts.md`, Wave 3 Prompt 3-D4 ("Section-to-Take references are
  **V2** — do not build them.") and Wave 4 journey list (corrected by this ADR)
- ADR-0030 (frozen §15 contracts live in `:core:model`)
- `core/data/src/main/kotlin/com/songpad/core/data/db/entity/Entities.kt` (`NoteEntity.sectionRefId`,
  `NoteEntity.takeRefId`)
