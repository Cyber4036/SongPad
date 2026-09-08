package com.songpad.core.model.lyrics

import com.songpad.core.model.sync.SyncMeta

/** Phase 0 §8: standard major sections plus Custom. */
enum class SectionTag(val label: String) {
    INTRO("Intro"),
    VERSE("Verse"),
    PRE_CHORUS("Pre-Chorus"),
    CHORUS("Chorus"),
    POST_CHORUS("Post-Chorus"),
    BRIDGE("Bridge"),
    OUTRO("Outro"),
    CUSTOM("Custom"),
    ;

    companion object {
        fun fromStored(value: String): SectionTag = entries.firstOrNull { it.name == value } ?: CUSTOM
    }
}

/**
 * A named/numbered lyric context. `bodyText` may be empty — a section is metadata, not a required
 * text container (Phase 0 §8). `customLabel` is only meaningful for [SectionTag.CUSTOM].
 */
data class Section(
    val id: String,
    val documentId: String,
    val tag: SectionTag,
    val customLabel: String?,
    val number: Int?,
    val bodyText: String,
    val sortOrder: Int,
    val meta: SyncMeta,
) {
    /** "Verse 2", "Chorus", or the custom label. */
    val displayLabel: String
        get() = when {
            tag == SectionTag.CUSTOM -> customLabel?.takeIf { it.isNotBlank() } ?: "Custom"
            number != null -> "${tag.label} $number"
            else -> tag.label
        }
}

/** Exactly one primary document per Song (§6.1). */
data class LyricsDocument(
    val id: String,
    val songId: String,
    val sections: List<Section>,
    val meta: SyncMeta,
) {
    val plainText: String
        get() = sections.sortedBy { it.sortOrder }.joinToString("\n\n") { "[${it.displayLabel}]\n${it.bodyText}" }
}

/** Section ordering and numbering rules — pure, tested on the JVM (§18). */
object SectionOrdering {
    /** Reassigns contiguous sort orders after an insert/move/delete. */
    fun normalize(sections: List<Section>): List<Section> =
        sections.sortedBy { it.sortOrder }.mapIndexed { i, s -> if (s.sortOrder == i) s else s.copy(sortOrder = i) }

    /** Inserts [section] at [index] (clamped) and renormalizes. */
    fun insert(sections: List<Section>, section: Section, index: Int): List<Section> {
        val ordered = sections.sortedBy { it.sortOrder }.toMutableList()
        ordered.add(index.coerceIn(0, ordered.size), section)
        return ordered.mapIndexed { i, s -> s.copy(sortOrder = i) }
    }

    fun move(sections: List<Section>, sectionId: String, toIndex: Int): List<Section> {
        val ordered = sections.sortedBy { it.sortOrder }.toMutableList()
        val from = ordered.indexOfFirst { it.id == sectionId }
        if (from < 0) return ordered
        val item = ordered.removeAt(from)
        ordered.add(toIndex.coerceIn(0, ordered.size), item)
        return ordered.mapIndexed { i, s -> s.copy(sortOrder = i) }
    }

    /**
     * Next number for a tag: "Verse 1", "Verse 2" (Phase 0 §8). Numbering applies once a tag appears
     * more than once; the first Chorus stays "Chorus" until a second one is added, at which point the
     * caller renumbers via [renumber].
     */
    fun nextNumber(sections: List<Section>, tag: SectionTag): Int? {
        if (tag == SectionTag.CUSTOM) return null
        val same = sections.count { it.tag == tag }
        return if (same == 0) null else same + 1
    }

    /** Applies "Verse 1, Verse 2" numbering to every tag that occurs more than once; single occurrences carry no number. */
    fun renumber(sections: List<Section>): List<Section> {
        val counts = sections.groupingBy { it.tag }.eachCount()
        val seen = mutableMapOf<SectionTag, Int>()
        return sections.sortedBy { it.sortOrder }.map { s ->
            if (s.tag == SectionTag.CUSTOM) return@map s.copy(number = null)
            val n = (seen[s.tag] ?: 0) + 1
            seen[s.tag] = n
            val number = if ((counts[s.tag] ?: 0) > 1) n else null
            if (s.number == number) s else s.copy(number = number)
        }
    }
}
