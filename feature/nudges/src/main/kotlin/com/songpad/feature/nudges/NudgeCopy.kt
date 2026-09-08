package com.songpad.feature.nudges

import com.songpad.core.model.nudge.NudgeCandidate
import com.songpad.core.model.nudge.NudgeKind

/**
 * §11.2: "Copy is gentle and never guilt-inducing. Copy strings are reviewed as a product artifact, not written
 * ad hoc in the worker." This object IS that artifact — edit here, review here. No deadlines, no streaks, no
 * "you haven't…" framing (Phase 0 §12).
 */
object NudgeCopy {
    data class Text(val title: String, val body: String)

    fun forCandidate(c: NudgeCandidate): Text = when (c.kind) {
        NudgeKind.EMPTY_SECTION -> Text(
            title = c.songName?.let { "A blank space in \"$it\"" } ?: "A section is waiting for words",
            body = "There's a section with no lyrics yet. Even one line is a start.",
        )
        NudgeKind.UNASSIGNED_TAKE -> Text(
            title = "A loose idea is waiting",
            body = "A take from a couple of days ago isn't in a song yet. Give it a home when you have a minute.",
        )
        NudgeKind.STALE_INVESTED_SONG -> Text(
            title = c.songName?.let { "\"$it\" might like a listen" } ?: "A song might like a listen",
            body = "It has lyrics and takes. Fresh ears sometimes find the next line.",
        )
        NudgeKind.USER_REMINDER -> Text(title = "Reminder", body = "You asked to come back to this.")
    }
}
