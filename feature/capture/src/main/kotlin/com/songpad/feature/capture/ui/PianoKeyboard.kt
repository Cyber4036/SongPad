package com.songpad.feature.capture.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.feature.capture.CaptureIntent

/**
 * §8.2 keyboard UI: one Canvas with raw pointerInput handling, not one composable per key — a
 * per-key composable puts 20+ recomposing nodes in the touch path of a musical instrument.
 * Multi-touch pointer tracking (a mutable map keyed by [PointerId], not Compose state) supports
 * glissando and polyphonic chords with no allocation on the touch path itself.
 */
private val WHITE_PITCH_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11)
private const val DEFAULT_VELOCITY = 100  // touch pressure reporting is unreliable across devices; see below

@Composable
fun PianoKeyboard(
    dispatch: (CaptureIntent) -> Unit,
    startNote: Int = 48,
    octaves: Int = 2,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    val whiteKeyColor = c.surface
    val blackKeyColor = Color(0xFF1A1A1A)
    val pressedColor = c.accent
    val borderColor = c.divider

    val endNote = startNote + 12 * octaves
    val whiteNotes = remember(startNote, octaves) { (startNote..endNote).filter { it % 12 in WHITE_PITCH_CLASSES } }
    val blackNotes = remember(startNote, octaves) { (startNote..endNote).filter { it % 12 !in WHITE_PITCH_CLASSES } }

    // Notes currently sounding, for visual feedback only — drives redraw of this one Canvas, not
    // per-key composables (the thing §8.2 actually warns against).
    val activeNotes: SnapshotStateList<Int> = remember { mutableStateListOf() }
    // Which note each active pointer is currently holding — plain mutable state, not Compose
    // state, read/written only from the gesture coroutine below.
    val pointerNotes = remember { mutableMapOf<PointerId, Int>() }

    // When range or octave shifts, release any sounding notes so none remain stuck
    DisposableEffect(startNote, octaves) {
        onDispose {
            for (note in activeNotes) {
                dispatch(CaptureIntent.KeyUp(note, System.nanoTime()))
            }
            activeNotes.clear()
            pointerNotes.clear()
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(startNote, octaves) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        for (change in event.changes) {
                            when {
                                change.changedToDown() -> {
                                    val note = noteAt(change.position, width, height, whiteNotes, blackNotes)
                                    pointerNotes[change.id] = note
                                    activeNotes.add(note)
                                    dispatch(CaptureIntent.KeyDown(note, DEFAULT_VELOCITY, System.nanoTime()))
                                    change.consume()
                                }
                                change.changedToUp() -> {
                                    pointerNotes.remove(change.id)?.let { note ->
                                        activeNotes.remove(note)
                                        dispatch(CaptureIntent.KeyUp(note, System.nanoTime()))
                                    }
                                    change.consume()
                                }
                                change.pressed -> {
                                    // Glissando: a finger sliding across keys re-triggers on every key boundary crossed.
                                    val note = noteAt(change.position, width, height, whiteNotes, blackNotes)
                                    val previous = pointerNotes[change.id]
                                    if (previous != null && previous != note) {
                                        activeNotes.remove(previous)
                                        dispatch(CaptureIntent.KeyUp(previous, System.nanoTime()))
                                        pointerNotes[change.id] = note
                                        activeNotes.add(note)
                                        dispatch(CaptureIntent.KeyDown(note, DEFAULT_VELOCITY, System.nanoTime()))
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            },
    ) {
        drawKeyboard(whiteNotes, blackNotes, activeNotes, whiteKeyColor, blackKeyColor, pressedColor, borderColor)
    }
}

/** Black keys are drawn on top and hit-tested first — they occupy the top ~60% of the key height. */
private fun noteAt(position: Offset, width: Float, height: Float, whiteNotes: List<Int>, blackNotes: List<Int>): Int {
    val whiteKeyWidth = width / whiteNotes.size
    val blackKeyWidth = whiteKeyWidth * 0.6f
    val blackKeyHeight = height * 0.6f
    if (position.y <= blackKeyHeight) {
        for (note in blackNotes) {
            val centerX = blackKeyCenterX(note, whiteNotes, whiteKeyWidth)
            if (position.x >= centerX - blackKeyWidth / 2 && position.x <= centerX + blackKeyWidth / 2) return note
        }
    }
    val index = (position.x / whiteKeyWidth).toInt().coerceIn(0, whiteNotes.size - 1)
    return whiteNotes[index]
}

/** A black key sits at the boundary between the white key below its pitch class and the next one. */
private fun blackKeyCenterX(note: Int, whiteNotes: List<Int>, whiteKeyWidth: Float): Float {
    val precedingWhiteIndex = whiteNotes.indexOfLast { it < note }
    return (precedingWhiteIndex + 1) * whiteKeyWidth
}

private fun DrawScope.drawKeyboard(
    whiteNotes: List<Int>, blackNotes: List<Int>, activeNotes: List<Int>,
    whiteKeyColor: Color, blackKeyColor: Color, pressedColor: Color, borderColor: Color,
) {
    val whiteKeyWidth = size.width / whiteNotes.size
    val blackKeyWidth = whiteKeyWidth * 0.6f
    val blackKeyHeight = size.height * 0.6f

    whiteNotes.forEachIndexed { index, note ->
        val x = index * whiteKeyWidth
        drawRect(
            color = if (note in activeNotes) pressedColor else whiteKeyColor,
            topLeft = Offset(x, 0f),
            size = Size(whiteKeyWidth, size.height),
        )
        drawRect(
            color = borderColor,
            topLeft = Offset(x, 0f),
            size = Size(whiteKeyWidth, size.height),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
    blackNotes.forEach { note ->
        val centerX = blackKeyCenterX(note, whiteNotes, whiteKeyWidth)
        drawRect(
            color = if (note in activeNotes) pressedColor else blackKeyColor,
            topLeft = Offset(centerX - blackKeyWidth / 2, 0f),
            size = Size(blackKeyWidth, blackKeyHeight),
        )
    }
}
