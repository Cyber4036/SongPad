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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.SongPadTheme
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.sin

/**
 * Live waveform for the recording screen and inline transport bar.
 * Draws the mirrored organic wave silhouette from Capture.html (`wavePath`).
 */
@Composable
fun LiveWaveform(
    amplitudes: SharedFlow<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 64,
) {
    val c = SongPadTheme.colors
    val ring = remember(barCount) {
        FloatArray(barCount) { i ->
            // Gentle idle wave silhouette matching seedWave in Capture.html before samples arrive
            (0.08f + 0.14f * (0.55f + 0.45f * sin(i / 5f)))
        }
    }
    var head by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(amplitudes) {
        amplitudes.collect { a ->
            ring[head] = a.coerceIn(0.08f, 1f)
            head = (head + 1) % barCount
            tick++
        }
    }
    val color = if (isRecording) c.accent.copy(alpha = 0.65f) else c.accent.copy(alpha = 0.45f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Dimens.waveformHeight)
            .semantics { contentDescription = if (isRecording) "Live waveform" else "Waveform" },
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        drawOrganicWave(barCount, color) { i -> ring[(head + i) % barCount] }
    }
}

/**
 * Static waveform over precomputed peaks with progress clipping and seek gestures —
 * used in both the Record screen's GlassPill transport and the floating RibbonPlayer.
 */
@Composable
fun SeekableWaveform(
    peaks: FloatArray,
    progress: Float,
    onSeek: (fraction: Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 64,
    active: Boolean = true,
    showPlayhead: Boolean = true,
) {
    val c = SongPadTheme.colors
    val reduced = remember(peaks, barCount) {
        val raw = reducePeaks(peaks, barCount)
        if (raw.all { it == 0f }) {
            FloatArray(barCount) { i -> (0.20f + 0.45f * (0.55f + 0.45f * sin(i / 4.5f))).coerceIn(0.12f, 0.9f) }
        } else {
            raw
        }
    }
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
        val unplayed = c.text.copy(alpha = 0.22f)
        val played = if (active) c.accent else c.textTertiary
        drawOrganicWave(barCount, unplayed) { i -> reduced[i] }
        val cutoffX = (size.width * shown).coerceIn(0f, size.width)
        clipRect(left = 0f, top = 0f, right = cutoffX, bottom = size.height) {
            drawOrganicWave(barCount, played) { i -> reduced[i] }
        }
        if (showPlayhead) {
            drawLine(
                color = c.accent700,
                start = Offset(cutoffX, 3.dp.toPx()),
                end = Offset(cutoffX, size.height - 3.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawOrganicWave(count: Int, color: Color, amplitudeAt: (Int) -> Float) {
    if (count <= 1 || size.width <= 0f || size.height <= 0f) return
    val midY = size.height / 2f
    val maxHalf = (size.height / 2f - 2.dp.toPx()).coerceAtLeast(2f)
    val stepX = size.width / (count - 1).coerceAtLeast(1)
    val path = Path()
    for (i in 0 until count) {
        val x = i * stepX
        val a = amplitudeAt(i).coerceIn(0.06f, 1f)
        val y = midY - a * maxHalf
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in count - 1 downTo 0) {
        val x = i * stepX
        val a = amplitudeAt(i).coerceIn(0.06f, 1f)
        val y = midY + a * maxHalf
        path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
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
