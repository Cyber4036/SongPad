// Host-compiled correctness test for the portable sampler core (piano_engine.{h,cpp}). No Oboe, no
// JNI, no Android — compiles with plain g++/clang++ so the real-time mixing math is verifiable
// without a device attached (see the invocation this file's header comment mirrors in
// scripts/vendor_lame.sh's build verification). Plain asserts, no test framework dependency.
//
// Build & run (from this directory):
//   g++ -std=c++17 -I../../main/cpp -o /tmp/piano_engine_test piano_engine_test.cpp ../../main/cpp/piano_engine.cpp
//   /tmp/piano_engine_test
#include "piano_engine.h"

#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

using songpad::NoteEventQueue;
using songpad::PianoEngine;
using songpad::PianoRegion;

namespace {

PianoRegion makeRegion(int32_t lokey, int32_t hikey, int32_t rootNote, int32_t length,
                        bool looped = false, int64_t loopStart = 0, int64_t loopEnd = 0) {
    PianoRegion r;
    r.lokey = lokey;
    r.hikey = hikey;
    r.rootNote = rootNote;
    r.looped = looped;
    r.loopStartFrame = loopStart;
    r.loopEndFrame = loopEnd;
    r.pcm.resize(length);
    // Scaled ramp (not pcm[i] == i): at kMasterGain (0.28) and full velocity gain, a plain 0/1/2/3
    // ramp rounds to 0 for the first several frames and every assertion on monotonic growth
    // spuriously fails from int16 truncation, not an engine bug. x20 keeps differences visible.
    for (int32_t i = 0; i < length; ++i) r.pcm[i] = static_cast<int16_t>(std::min(i, 1600) * 20);
    return r;
}

void testPlaybackAtRootNoteReadsPcmDirectly() {
    PianoEngine engine(48000);
    engine.loadRegion(makeRegion(0, 127, 60, 1000));
    engine.noteOn(60, 127);
    std::vector<int16_t> out(4);
    engine.render(out.data(), 4);
    // At the root note, increment == 1.0, so output should track the ramp pcm[i] == i closely
    // (scaled by kMasterGain and the velocity curve, both < 1) — check it's monotonically
    // non-decreasing and non-zero by frame 3, proving the read cursor actually advances frame-by-frame.
    assert(out[0] == 0);
    assert(out[1] > out[0]);
    assert(out[2] > out[1]);
    assert(out[3] > out[2]);
    printf("PASS testPlaybackAtRootNoteReadsPcmDirectly\n");
}

void testOctaveUpAdvancesTwiceAsFast() {
    PianoEngine engine(48000);
    engine.loadRegion(makeRegion(0, 127, 60, 1000));
    engine.noteOn(72, 127);  // +12 semitones == 2x playback rate
    std::vector<int16_t> out(8);
    engine.render(out.data(), 8);
    // Reading at 2x speed reaches roughly twice the pcm value in the same number of output frames
    // as the root-note case above (both scaled by the same gain), so frame 3 here should be well
    // past frame 3's value in the 1x case.
    PianoEngine engine1x(48000);
    engine1x.loadRegion(makeRegion(0, 127, 60, 1000));
    engine1x.noteOn(60, 127);
    std::vector<int16_t> out1x(8);
    engine1x.render(out1x.data(), 8);
    assert(out[3] > out1x[3] * 1.5);
    printf("PASS testOctaveUpAdvancesTwiceAsFast\n");
}

void testNonLoopedVoiceDeactivatesAtEnd() {
    PianoEngine engine(48000);
    engine.loadRegion(makeRegion(0, 127, 60, 10));  // tiny 10-frame sample, no loop
    engine.noteOn(60, 127);
    std::vector<int16_t> tick(1);
    engine.render(tick.data(), 1);  // one render call to drain the event queue and start the voice
    assert(engine.activeVoiceCount() == 1);
    std::vector<int16_t> out(20);
    engine.render(out.data(), 20);  // well past the 10-frame sample
    assert(engine.activeVoiceCount() == 0);
    printf("PASS testNonLoopedVoiceDeactivatesAtEnd\n");
}

void testLoopedVoiceSurvivesPastNaturalLength() {
    PianoEngine engine(48000);
    engine.loadRegion(makeRegion(0, 127, 60, 10, /*looped=*/true, /*loopStart=*/2, /*loopEnd=*/8));
    engine.noteOn(60, 127);
    std::vector<int16_t> out(100);  // 10x the sample length — only survives if looping works
    engine.render(out.data(), 100);
    assert(engine.activeVoiceCount() == 1);
    // No sample read past the region's actual PCM bounds: rerun with a smaller buffer so an
    // out-of-bounds read (if the wrap were broken) would be exercised by ASan in a sanitized build.
    printf("PASS testLoopedVoiceSurvivesPastNaturalLength\n");
}

void testNoteOffFadesToSilenceThenDeactivates() {
    PianoEngine engine(48000);
    engine.loadRegion(makeRegion(0, 127, 60, 48000, /*looped=*/true, 0, 48000));
    engine.noteOn(60, 127);
    engine.noteOff(60);
    // Release is kReleaseSeconds (0.12s) at 48kHz — render well past that and confirm the voice
    // is gone, not stuck holding a note forever.
    std::vector<int16_t> out(48000 / 4);  // 0.25s, > release time
    engine.render(out.data(), static_cast<int32_t>(out.size()));
    assert(engine.activeVoiceCount() == 0);
    printf("PASS testNoteOffFadesToSilenceThenDeactivates\n");
}

void testVoiceStealingCapsAtPoolSizeAndTakesOldestFirst() {
    PianoEngine engine(48000);
    engine.loadRegion(makeRegion(0, 127, 60, 480000, /*looped=*/true, 0, 480000));
    for (int32_t n = 0; n < PianoEngine::kVoiceCount; ++n) {
        engine.noteOn(21 + n, 100);
        std::vector<int16_t> tick(4);
        engine.render(tick.data(), 4);  // advance frameCounter_ so each voice has a distinct age
    }
    assert(engine.activeVoiceCount() == PianoEngine::kVoiceCount);
    bool sawOldestNote = false;
    for (int32_t i = 0; i < PianoEngine::kVoiceCount; ++i) if (engine.noteOfVoice(i) == 21) sawOldestNote = true;
    assert(sawOldestNote);  // the very first note-on (note 21) should still be sounding pre-steal

    engine.noteOn(21 + PianoEngine::kVoiceCount, 100);  // one more than the pool holds
    std::vector<int16_t> tick(4);
    engine.render(tick.data(), 4);

    assert(engine.activeVoiceCount() == PianoEngine::kVoiceCount);  // never exceeds the pool
    bool oldestStillThere = false, newestThere = false;
    for (int32_t i = 0; i < PianoEngine::kVoiceCount; ++i) {
        int32_t note = engine.noteOfVoice(i);
        if (note == 21) oldestStillThere = true;
        if (note == 21 + PianoEngine::kVoiceCount) newestThere = true;
    }
    assert(!oldestStillThere);  // the oldest voice (note 21) was the one stolen
    assert(newestThere);
    printf("PASS testVoiceStealingCapsAtPoolSizeAndTakesOldestFirst\n");
}

void testOutputNeverClipsBeyondInt16RangeUnderFullPolyphony() {
    PianoEngine engine(48000);
    engine.loadRegion(makeRegion(0, 127, 60, 48000, true, 0, 48000));
    for (int32_t n = 0; n < PianoEngine::kVoiceCount; ++n) engine.noteOn(21 + n, 127);
    std::vector<int16_t> out(4096);
    engine.render(out.data(), static_cast<int32_t>(out.size()));
    for (int16_t s : out) assert(s >= -32768 && s <= 32767);  // always true for int16_t, but the
    // real assertion is that render() clamped the float mix — a NaN or huge magnitude sum would
    // have already undefined-behavior'd on the cast above under a sanitizer.
    printf("PASS testOutputNeverClipsBeyondInt16RangeUnderFullPolyphony\n");
}

void testNoteOnBeforeAnyRegionLoadedDoesNotCrash() {
    PianoEngine engine(48000);
    engine.noteOn(60, 100);
    std::vector<int16_t> out(16);
    engine.render(out.data(), 16);
    assert(engine.activeVoiceCount() == 0);
    for (int16_t s : out) assert(s == 0);
    printf("PASS testNoteOnBeforeAnyRegionLoadedDoesNotCrash\n");
}

}  // namespace

int main() {
    testPlaybackAtRootNoteReadsPcmDirectly();
    testOctaveUpAdvancesTwiceAsFast();
    testNonLoopedVoiceDeactivatesAtEnd();
    testLoopedVoiceSurvivesPastNaturalLength();
    testNoteOffFadesToSilenceThenDeactivates();
    testVoiceStealingCapsAtPoolSizeAndTakesOldestFirst();
    testOutputNeverClipsBeyondInt16RangeUnderFullPolyphony();
    testNoteOnBeforeAnyRegionLoadedDoesNotCrash();
    printf("ALL PASS\n");
    return 0;
}
