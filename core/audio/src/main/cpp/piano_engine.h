// Portable polyphonic sampler core (§8.1). No Oboe/JNI/Android dependency on purpose: this file
// and piano_engine.cpp compile and are unit-tested on the host (see
// core/audio/src/test/cpp/piano_engine_test.cpp), which is the only way real-time mixing math
// gets verified without a device attached.
#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <vector>

namespace songpad {

// One sampled note. PCM is mono, pre-resampled to the engine's output sample rate at vendor time
// (scripts/vendor_piano_samples.sh) so playback only ever needs a semitone-ratio pitch shift, never
// a sample-rate conversion.
struct PianoRegion {
    int32_t lokey = 0;
    int32_t hikey = 0;
    int32_t rootNote = 60;
    bool looped = false;
    int64_t loopStartFrame = 0;
    int64_t loopEndFrame = 0;
    std::vector<int16_t> pcm;  // owns the decoded samples
};

// A tiny single-producer/single-consumer event queue: noteOn/noteOff push from any (non-realtime)
// caller thread under a mutex; PianoEngine::render (the Oboe callback thread) drains it with plain
// atomics and never blocks. Capacity is a power of two so index wraparound is a mask, not a modulo.
class NoteEventQueue {
public:
    struct Event {
        enum class Type : uint8_t { NoteOn, NoteOff, AllNotesOff } type;
        int32_t note = 0;
        int32_t velocity = 0;
    };

    static constexpr uint32_t kCapacity = 128;

    void push(Event e) {
        std::lock_guard<std::mutex> lock(pushMutex_);
        uint32_t head = head_.load(std::memory_order_relaxed);
        uint32_t nextHead = (head + 1) & (kCapacity - 1);
        if (nextHead == tail_.load(std::memory_order_acquire)) return;  // full: drop, never block
        buffer_[head] = e;
        head_.store(nextHead, std::memory_order_release);
    }

    // Consumer side only — call from a single thread (the audio callback).
    bool pop(Event* out) {
        uint32_t tail = tail_.load(std::memory_order_relaxed);
        if (tail == head_.load(std::memory_order_acquire)) return false;  // empty
        *out = buffer_[tail];
        tail_.store((tail + 1) & (kCapacity - 1), std::memory_order_release);
        return true;
    }

private:
    std::array<Event, kCapacity> buffer_{};
    std::atomic<uint32_t> head_{0};
    std::atomic<uint32_t> tail_{0};
    std::mutex pushMutex_;
};

class PianoEngine {
public:
    static constexpr int32_t kVoiceCount = 32;      // §8.1 voice pool of 32
    static constexpr int32_t kSampleRate = 48000;
    static constexpr float kReleaseSeconds = 0.12f;  // fade-out after note-off; avoids a click

    explicit PianoEngine(int32_t sampleRate = kSampleRate);

    // Called during async load (before playback starts); not real-time safe, not called concurrently
    // with render(). Takes ownership of pcm's contents by moving them in.
    void loadRegion(PianoRegion region);
    bool hasRegions() const { return !regions_.empty(); }

    // Producer side (any thread): enqueue a note event for the next render() to pick up.
    void noteOn(int32_t note, int32_t velocity) { events_.push({NoteEventQueue::Event::Type::NoteOn, note, velocity}); }
    void noteOff(int32_t note) { events_.push({NoteEventQueue::Event::Type::NoteOff, note, 0}); }
    void allNotesOff() { events_.push({NoteEventQueue::Event::Type::AllNotesOff, 0, 0}); }

    // Consumer side — THE AUDIO CALLBACK. Drains queued events, then mixes kVoiceCount voices into
    // out (mono int16, numFrames long). No allocation, no locks (drain of the queue's consumer side
    // is lock-free), matching the real-time rules already documented on CaptureEngine.
    void render(int16_t* out, int32_t numFrames);

    // Introspection for tests — not used by the audio path. Reflects state as of the last
    // render() call, not the queued-but-not-yet-drained events from noteOn/noteOff.
    int32_t activeVoiceCount() const;
    int32_t noteOfVoice(int32_t index) const { return voices_[index].active ? voices_[index].note : -1; }

private:
    struct Voice {
        int32_t regionIndex = -1;
        int32_t note = -1;
        double position = 0.0;    // fractional read cursor into the region's PCM, in frames
        double increment = 1.0;   // playback rate: 2^((note-rootNote)/12)
        float velocityGain = 1.0f;
        float releaseGain = 1.0f;
        float releaseStep = 0.0f;
        bool releasing = false;
        bool active = false;
        int64_t startFrame = 0;   // engine frame counter at note-on, for oldest-voice stealing
    };

    int32_t findRegionForNote(int32_t note) const;
    Voice* allocateVoice();
    void handleEvent(const NoteEventQueue::Event& e);
    void startVoice(Voice* voice, int32_t note, int32_t velocity);
    static float velocityToGain(int32_t velocity);

    int32_t sampleRate_;
    std::vector<PianoRegion> regions_;
    std::array<Voice, kVoiceCount> voices_;
    NoteEventQueue events_;
    int64_t frameCounter_ = 0;
};

}  // namespace songpad
