package com.songpad.core.midi.capture

import com.google.common.truth.Truth.assertThat
import com.songpad.core.data.testing.FakeAudioRecorderEngine
import com.songpad.core.data.testing.SequentialIdGenerator
import com.songpad.core.model.take.TakeFileStore
import com.songpad.core.midi.smf.Vlq
import com.songpad.core.model.midi.MidiEvent
import com.songpad.core.model.take.TakePaths
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EventsLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `log survives truncation and recovery truncates to the audio frame count`() {
        val f = File(tmp.root, "events.log")
        val log = EventsLog(f)
        log.open()
        log.append(MidiEvent(1_000, 60, 100, true)); log.append(MidiEvent(5_000, 60, 0, false))
        log.append(MidiEvent(9_000, 64, 90, true)); log.append(MidiEvent(20_000, 64, 0, false))
        log.close()
        // Simulate a kill mid-record: chop 5 bytes off the end.
        val bytes = f.readBytes()
        f.writeBytes(bytes.copyOf(bytes.size - 5))
        val all = EventsLog.read(f)
        assertThat(all).hasSize(3 + 1) // 3 whole records + synthesized note-off for the dangling 64
        assertThat(all.last().isNoteOn).isFalse(); assertThat(all.last().note).isEqualTo(64)

        val truncated = EventsLog.read(f, maxFrameExclusive = 6_000)
        assertThat(truncated.map { it.frameIndex }).containsExactly(1_000L, 5_000L).inOrder()
    }

    @Test
    fun `engine commits one take with a midi sidecar from frame-indexed events`() = runTest {
        val files = TakeFileStore(tmp.root)
        val recorder = FakeAudioRecorderEngine()
        val engine = MidiCaptureEngineImpl(recorder, files, SequentialIdGenerator("m"))
        engine.beginTake("take1")
        engine.onKeyDown(60, 100, 1_000_000_000L)  // fake maps 1 s → 48,000 frames
        engine.onKeyUp(60, 1_500_000_000L)
        engine.flushLog()
        assertThat(files.exists(TakePaths.eventsLog("take1"))).isTrue()

        val asset = engine.commit("take1").getOrThrow()!!
        assertThat(asset.relativePath).isEqualTo("takes/take1/performance.mid")
        assertThat(asset.eventCount).isEqualTo(2)
        assertThat(files.exists(TakePaths.eventsLog("take1"))).isFalse()
        val bytes = files.file(asset.relativePath).readBytes()
        assertThat(String(bytes, 0, 4, Charsets.US_ASCII)).isEqualTo("MThd")
        val (firstDelta, _) = Vlq.decode(bytes, 0x16 + 7)
        assertThat(firstDelta).isEqualTo(48_000 / 50)
    }

    @Test
    fun `a voice-only take commits null and leaves no files`() = runTest {
        val engine = MidiCaptureEngineImpl(FakeAudioRecorderEngine(), TakeFileStore(tmp.root), SequentialIdGenerator())
        engine.beginTake("t")
        assertThat(engine.commit("t").getOrThrow()).isNull()
        assertThat(File(tmp.root, "takes/t").listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `recovery converts a surviving log truncated to the recovered pcm`() {
        val files = TakeFileStore(tmp.root)
        val log = EventsLog(files.file(TakePaths.eventsLog("r"))); log.open()
        log.append(MidiEvent(100, 60, 80, true)); log.append(MidiEvent(96_000, 60, 0, false)); log.close()
        val engine = MidiCaptureEngineImpl(FakeAudioRecorderEngine(), files, SequentialIdGenerator())
        val asset = engine.recover("r", frameCount = 48_000)!!
        assertThat(asset.eventCount).isEqualTo(2) // on @100 + synthesized off @47,999
        assertThat(files.exists(TakePaths.eventsLog("r"))).isFalse()
        assertThat(files.exists(TakePaths.midi("r"))).isTrue()
    }
}
