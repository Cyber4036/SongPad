package com.songpad.core.audio.capture

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct hardware & native layer tests for Oboe C++ CaptureEngine (`libsongpad_audio.so`).
 * Validates Oboe stream initialization, HAL burst/capacity negotiation, and lock-free ring buffer JNI draining.
 */
@RunWith(AndroidJUnit4::class)
class OboeNativeCaptureTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun nativeLibraryLoadsAndCreatesEngine() {
        NativeCapture().use { capture ->
            assertThat(capture.isOpen).isTrue()
        }
    }

    @Test
    fun oboeStreamOpensAndReportsHardwareParameters() {
        NativeCapture().use { capture ->
            val res = capture.open()
            // 0 is oboe::Result::OK
            assertThat(res).isEqualTo(0)

            val info = capture.streamInfo()
            println("--> Oboe Hardware Stream Info: burst=${info.framesPerBurst}, capacity=${info.bufferCapacity}, size=${info.bufferSize}, aaudio=${info.aaudio}, lowLatency=${info.lowLatency}, xruns=${info.xruns}")
            assertThat(info.framesPerBurst).isGreaterThan(0)
            assertThat(info.bufferCapacity).isGreaterThan(0)

            capture.closeStream()
        }
    }

    @Test
    fun oboeCapturesLiveAudioFramesToRingBuffer() {
        NativeCapture().use { capture ->
            assertThat(capture.open()).isEqualTo(0)
            val startRes = capture.start()
            assertThat(startRes).isEqualTo(0)

            // Let audio stream capture for 500 ms from physical microphone
            Thread.sleep(500)

            val available = capture.available()
            println("--> Available samples in C++ ring buffer after 500ms: $available")
            assertThat(available).isGreaterThan(0)

            val drainBuffer = NativeCapture.allocateDrainBuffer(available)
            val drained = capture.drain(drainBuffer, available)
            println("--> Drained samples from Oboe ring buffer: $drained")
            assertThat(drained).isGreaterThan(0)

            val totalCaptured = capture.framesCaptured()
            println("--> Total frames captured by Oboe: $totalCaptured")
            assertThat(totalCaptured).isGreaterThan(0L)
            assertThat(capture.errorOccurred()).isFalse()

            capture.pause()
            capture.closeStream()
        }
    }
}
