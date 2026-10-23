package com.songpad.feature.capture.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.feature.capture.CaptureIntent

/**
 * §8.2 keyboard UI styled after Capture.html: rounded ivory white keys (`#F4F1EC`) with bottom note labels
 * (`C3`, `D3`...), rounded dark black keys (`neutral900`), and `KeySize` zoom support (`Small / Medium / Large`).
 */
enum class PianoKeySize(val label: String, val whiteKeyWidth: Dp) {
    SMALL("Small", 36.dp),
    MEDIUM("Medium", 46.dp),
    LARGE("Large", 58.dp),
}

private val WHITE_PITCH_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11)
private val PITCH_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private const val DEFAULT_VELOCITY = 100

@Composable
fun PianoKeyboard(
    dispatch: (CaptureIntent) -> Unit,
    startNote: Int = 48,
    octaves: Int = 2,
    keySize: PianoKeySize = PianoKeySize.MEDIUM,
    modifier: Modifier = Modifier,
) {
    val c = SongPadTheme.colors
    val whiteKeyColor = Color(0xFFF4F1EC)
    val whitePressedFill = c.accent100
    val whitePressedBorder = c.accent
    val blackKeyColor = c.neutral900
    val blackPressedColor = c.accent
    val borderColor = c.divider
    val labelColor = c.textTertiary
    val labelPressedColor = c.accent800

    val endNote = startNote + 12 * octaves - 1
    val whiteNotes = remember(startNote, octaves) { (startNote..endNote).filter { it % 12 in WHITE_PITCH_CLASSES } }
    val blackNotes = remember(startNote, octaves) { (startNote..endNote).filter { it % 12 !in WHITE_PITCH_CLASSES } }

    val activeNotes: SnapshotStateList<Int> = remember { mutableStateListOf() }
    val pointerNotes = remember { mutableMapOf<PointerId, Int>() }

    DisposableEffect(startNote, octaves) {
        onDispose {
            for (note in activeNotes) {
                dispatch(CaptureIntent.KeyUp(note, System.nanoTime()))
            }
            activeNotes.clear()
            pointerNotes.clear()
        }
    }

    val totalWidth = keySize.whiteKeyWidth * whiteNotes.size
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        Canvas(
            Modifier
                .width(totalWidth)
                .height(148.dp)
                .pointerInput(startNote, octaves, keySize) {
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
            drawKeyboard(
                whiteNotes = whiteNotes,
                blackNotes = blackNotes,
                activeNotes = activeNotes,
                whiteKeyColor = whiteKeyColor,
                whitePressedFill = whitePressedFill,
                whitePressedBorder = whitePressedBorder,
                blackKeyColor = blackKeyColor,
                blackPressedColor = blackPressedColor,
                borderColor = borderColor,
                labelColor = labelColor,
                labelPressedColor = labelPressedColor,
            )
        }
    }
}

private fun noteAt(position: Offset, width: Float, height: Float, whiteNotes: List<Int>, blackNotes: List<Int>): Int {
    val whiteKeyWidth = width / whiteNotes.size
    val blackKeyWidth = whiteKeyWidth * 0.62f
    val blackKeyHeight = height * 0.60f
    if (position.y <= blackKeyHeight) {
        for (note in blackNotes) {
            val centerX = blackKeyCenterX(note, whiteNotes, whiteKeyWidth)
            if (position.x >= centerX - blackKeyWidth / 2 && position.x <= centerX + blackKeyWidth / 2) return note
        }
    }
    val index = (position.x / whiteKeyWidth).toInt().coerceIn(0, whiteNotes.size - 1)
    return whiteNotes[index]
}

private fun blackKeyCenterX(note: Int, whiteNotes: List<Int>, whiteKeyWidth: Float): Float {
    val precedingWhiteIndex = whiteNotes.indexOfLast { it < note }
    return (precedingWhiteIndex + 1) * whiteKeyWidth
}

private fun noteLabel(midiNote: Int): String {
    val pitch = PITCH_NAMES[midiNote % 12]
    val octave = (midiNote / 12) - 1
    return "$pitch$octave"
}

private fun DrawScope.drawKeyboard(
    whiteNotes: List<Int>,
    blackNotes: List<Int>,
    activeNotes: List<Int>,
    whiteKeyColor: Color,
    whitePressedFill: Color,
    whitePressedBorder: Color,
    blackKeyColor: Color,
    blackPressedColor: Color,
    borderColor: Color,
    labelColor: Color,
    labelPressedColor: Color,
) {
    val whiteKeyWidth = size.width / whiteNotes.size
    val blackKeyWidth = whiteKeyWidth * 0.62f
    val blackKeyHeight = size.height * 0.60f
    val gap = 1.dp.toPx()
    val whiteRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    val blackRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9.sp.toPx()
        isFakeBoldText = true
    }

    whiteNotes.forEachIndexed { index, note ->
        val x = index * whiteKeyWidth + gap / 2f
        val w = whiteKeyWidth - gap
        val isPressed = note in activeNotes
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset(x, 0f), Size(w, size.height)),
                    topLeft = CornerRadius.Zero,
                    topRight = CornerRadius.Zero,
                    bottomLeft = whiteRadius,
                    bottomRight = whiteRadius,
                )
            )
        }
        drawPath(
            path = path,
            color = if (isPressed) whitePressedFill else whiteKeyColor,
        )
        drawPath(
            path = path,
            color = if (isPressed) whitePressedBorder else borderColor,
            style = Stroke(width = 1.dp.toPx()),
        )

        textPaint.color = (if (isPressed) labelPressedColor else labelColor).toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            noteLabel(note),
            x + w / 2f,
            size.height - 8.dp.toPx(),
            textPaint,
        )
    }

    blackNotes.forEach { note ->
        val centerX = blackKeyCenterX(note, whiteNotes, whiteKeyWidth)
        val isPressed = note in activeNotes
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset(centerX - blackKeyWidth / 2f, 0f), Size(blackKeyWidth, blackKeyHeight)),
                    topLeft = CornerRadius.Zero,
                    topRight = CornerRadius.Zero,
                    bottomLeft = blackRadius,
                    bottomRight = blackRadius,
                )
            )
        }
        drawPath(
            path = path,
            color = if (isPressed) blackPressedColor else blackKeyColor,
        )
    }
}
