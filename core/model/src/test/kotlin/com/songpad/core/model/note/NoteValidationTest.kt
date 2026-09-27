package com.songpad.core.model.note

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteValidationTest {
    @Test
    fun `empty note is rejected, any content is accepted`() {
        assertThat(NoteValidation.validate(NoteDraft("s"))).isNotNull()
        assertThat(NoteValidation.validate(NoteDraft("s", bodyText = "   "))).isNotNull()
        assertThat(NoteValidation.validate(NoteDraft("s", bodyText = "idea"))).isNull()
        assertThat(NoteValidation.validate(NoteDraft("s", voiceTakeId = "t"))).isNull()
        assertThat(NoteValidation.validate(NoteDraft("s", mediaUri = "content://x"))).isNull()
    }

    @Test
    fun `mentions are parsed from composer text`() {
        assertThat(NoteValidation.mentions("try @Chorus louder, ref @Verse 2 and @Take 3")).containsExactly("Chorus", "Verse 2", "Take 3")
        assertThat(NoteValidation.mentions("no mentions here")).isEmpty()
    }

    @Test
    fun `mentionMatches ranges line up with the same tokens mentions returns, for tappable-span rendering`() {
        val text = "try @Chorus louder, ref @Verse 2"
        val matches = NoteValidation.mentionMatches(text)
        assertThat(matches.map { it.groupValues[1].trim() }).containsExactly("Chorus", "Verse 2").inOrder()
        assertThat(matches.map { text.substring(it.range.first, it.range.last + 1) }).containsExactly("@Chorus", "@Verse 2").inOrder()
    }
}
