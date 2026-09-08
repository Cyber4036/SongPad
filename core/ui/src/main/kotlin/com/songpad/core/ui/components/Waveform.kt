package com.songpad.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.SongPadTheme
import kotlinx.coroutines.flow.SharedFlow

/**
 * Live waveform for the recording control group. Consumes the 10 Hz normalized `SharedFlow<Float>` from
 * `AudioRecorderEngine` (§15). Draws with `record` while [isRecording], `accent` otherwise — read from
 * the token layer, so there is no per-theme drawing code (§14). Keeps a fixed ring of bars; a slow UI
 * simply misses samples (DROP_OLDEST upstream), it never back-pressures audio.
 */
@Composable
fun LiveWaveform(
    amplitudes: SharedFlow<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 64,
) {
    val c = SongPadTheme.colors
    val ring = remember(barCount) { FloatArray(barCount) }
    var head by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(amplitudes) {
        amplitudes.collect { a ->
            ring[head] = a.coerceIn(0f, 1f)
            head = (head + 1) % barCount
            tick++
        }
    }
    val color = if (isRecording) c.record else c.accent
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Dimens.waveformHeight)
            .semantics { contentDescription = if (isRecording) "Live waveform" else "Waveform" },
    ) {
        @Suppress("UNUSED_EXPRESSION") tick // read so the canvas redraws on new samples
        drawBars(barCount, color, c.divider) { i -> ring[(head + i) % barCount] }
    }
}

/**
 * Static waveform over precomputed peaks with a progress cursor and seek gestures — the paused-review
 * scrubber (Phase 0 §6) and the ribbon player's seek control (Phase 0 §9). Progress is 0f..1f.
 */
@Composable
fun SeekableWaveform(
    peaks: FloatArray,
    progress: Float,
    onSeek: (fraction: Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 64,
    active: Boolean = true,
) {
    val c = SongPadTheme.colors
    val reduced = remember(peaks, barCount) { reducePeaks(peaks, barCount) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Dimens.waveformHeight)
            .semantics { contentDescription = "Seek waveform" }
            .pointerInput(Unit) {
                detectTapGestures { pos -> onSeek((pos.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> dragging = (pos.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let(onSeek); dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val played = if (active) c.accent else c.textTertiary
        drawBars(barCount, c.divider.copy(alpha = 0.6f), c.divider) { i -> reduced[i] }
        val cutoff = (barCount * shown).toInt()
        drawBars(cutoff, played, Color.Transparent) { i -> reduced[i] }
        val x = shown * size.width
        drawLine(c.record, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawBars(count: Int, color: Color, baseline: Color, amplitudeAt: (Int) -> Float) {
    if (count <= 0) return
    val totalBars = maxOf(count, 1)
    val slot = size.width / 64f
    val barWidth = (slot * 0.55f).coerceAtLeast(1.5f)
    val midY = size.height / 2f
    val minHalf = 1.5f
    if (baseline != Color.Transparent) {
        drawLine(baseline, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1f)
    }
    for (i in 0 until totalBars) {
        val a = amplitudeAt(i).coerceIn(0f, 1f)
        val half = maxOf(minHalf, a * (size.height / 2f - 4f))
        val x = i * slot + slot / 2f
        drawLine(color, Offset(x, midY - half), Offset(x, midY + half), strokeWidth = barWidth, cap = StrokeCap.Round)
    }
}

/** Buckets an arbitrary-length peaks array (the `peaks.wave` file) into [barCount] max values. */
fun reducePeaks(peaks: FloatArray, barCount: Int): FloatArray {
    if (peaks.isEmpty()) return FloatArray(barCount)
    val out = FloatArray(barCount)
    val per = peaks.size.toFloat() / barCount
    for (i in 0 until barCount) {
        val start = (i * per).toInt()
        val end = minOf(peaks.size, maxOf(start + 1, ((i + 1) * per).toInt()))
        var m = 0f
        for (j in start until end) if (peaks[j] > m) m = peaks[j]
        out[i] = m
    }
    return out
}

@Suppress("unused")
private val sizeSanity = Size.Zero
