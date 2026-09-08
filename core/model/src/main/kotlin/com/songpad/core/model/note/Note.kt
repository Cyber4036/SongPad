package com.songpad.core.model.note

import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.DomainError
import com.songpad.core.model.sync.SyncMeta

/**
 * Phase 0 §10: a text, voice or media-backed note belonging to one Song, optionally referencing a
 * Section and/or a Take via `@`. Empty notes are rejected in the domain layer, not the DB (§6.1).
 */
data class Note(
    val id: String,
    val songId: String,
    val bodyText: String,
    val voiceTakeId: String?,
    val mediaUri: String?,
    val sectionRefId: String?,
    val takeRefId: String?,
    val meta: SyncMeta,
) {
    val hasContent: Boolean get() = NoteValidation.hasContent(bodyText, voiceTakeId, mediaUri)
}

/** What the composer holds before save (Phase 0 §24.5). */
data class NoteDraft(
    val songId: String,
    val bodyText: String = "",
    val voiceTakeId: String? = null,
    val mediaUri: String? = null,
    val sectionRefId: String? = null,
    val takeRefId: String? = null,
)

object NoteValidation {
    /** Save is enabled only when text exists, media is attached, or a voice note was recorded. */
    fun hasContent(bodyText: String, voiceTakeId: String?, mediaUri: String?): Boolean =
        bodyText.isNotBlank() || voiceTakeId != null || mediaUri != null

    fun validate(draft: NoteDraft): DomainError? =
        if (hasContent(draft.bodyText, draft.voiceTakeId, draft.mediaUri)) null
        else DataError.InvalidInput("A note needs text, a voice note, or an attachment.")

    /** Parses `@Chorus` / `@Verse 2` / `@Take name` mentions out of composer text. Resolution against real ids happens in the feature. */
    fun mentions(text: String): List<String> =
        MENTION.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

    /** A mention is one word (a tag, a name) optionally followed by a number: `@Chorus`, `@Verse 2`, `@Take 3`. */
    private val MENTION = Regex("@([\\p{L}\\p{N}_-]+(?: \\d+)?)")
}
