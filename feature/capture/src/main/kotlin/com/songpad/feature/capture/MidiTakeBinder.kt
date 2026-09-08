package com.songpad.feature.capture

/**
 * The frozen `MidiCaptureEngine` contract (§15) has no "begin" call — it receives events and commits. The
 * concrete engine needs the take id to open `events.log` (§8.5) before the first key press, so the feature
 * asks for that through this seam rather than depending on the implementation class. Bound in [di.CaptureModule].
 */
fun interface MidiTakeBinder {
    fun beginTake(takeId: String)

    companion object {
        val NONE = MidiTakeBinder { }
    }
}
