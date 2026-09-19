#include "piano_engine.h"

#include <algorithm>
#include <cmath>

namespace songpad {

namespace {
// Headroom for chords: 32 fully-active voices summing linearly could otherwise clip constantly.
// A fixed attenuation is a simple, disclosed choice for Phase 1 — not a limiter/compressor.
constexpr float kMasterGain = 0.28f;
}  // namespace

PianoEngine::PianoEngine(int32_t sampleRate) : sampleRate_(sampleRate) {}

void PianoEngine::loadRegion(PianoRegion region) { regions_.push_back(std::move(region)); }

int32_t PianoEngine::findRegionForNote(int32_t note) const {
    int32_t best = -1;
    int32_t bestDistance = INT32_MAX;
    for (size_t i = 0; i < regions_.size(); ++i) {
        const auto& r = regions_[i];
        if (note >= r.lokey && note <= r.hikey) return static_cast<int32_t>(i);
        int32_t distance = std::min(std::abs(note - r.lokey), std::abs(note - r.hikey));
        if (distance < bestDistance) { bestDistance = distance; best = static_cast<int32_t>(i); }
    }
    return best;  // defensive fallback if note falls outside every region's key range
}

float PianoEngine::velocityToGain(int32_t velocity) {
    float normalized = std::clamp(velocity, 0, 127) / 127.0f;
    // A single velocity layer means dynamics come entirely from amplitude; a gentle curve (rather
    // than linear) keeps soft taps audible instead of feeling dead.
    return std::pow(normalized, 0.6f);
}

PianoEngine::Voice* PianoEngine::allocateVoice() {
    for (auto& v : voices_) if (!v.active) return &v;
    // Steal: oldest first (§8.1), quietest as the tie-break.
    Voice* victim = &voices_[0];
    for (auto& v : voices_) {
        if (v.startFrame < victim->startFrame) { victim = &v; continue; }
        if (v.startFrame == victim->startFrame) {
            float vGain = v.velocityGain * (v.releasing ? v.releaseGain : 1.0f);
            float victimGain = victim->velocityGain * (victim->releasing ? victim->releaseGain : 1.0f);
            if (vGain < victimGain) victim = &v;
        }
    }
    return victim;
}

void PianoEngine::startVoice(Voice* voice, int32_t note, int32_t velocity) {
    int32_t regionIndex = findRegionForNote(note);
    if (regionIndex < 0) return;  // no regions loaded yet
    const auto& region = regions_[regionIndex];
    voice->regionIndex = regionIndex;
    voice->note = note;
    voice->position = 0.0;
    voice->increment = std::pow(2.0, (note - region.rootNote) / 12.0);
    voice->velocityGain = velocityToGain(velocity);
    voice->releasing = false;
    voice->releaseGain = 1.0f;
    voice->releaseStep = 1.0f / (kReleaseSeconds * static_cast<float>(sampleRate_));
    voice->active = true;
    voice->startFrame = frameCounter_;
}

void PianoEngine::handleEvent(const NoteEventQueue::Event& e) {
    using Type = NoteEventQueue::Event::Type;
    switch (e.type) {
        case Type::NoteOn:
            if (!regions_.empty()) startVoice(allocateVoice(), e.note, e.velocity);
            break;
        case Type::NoteOff:
            for (auto& v : voices_) {
                if (v.active && !v.releasing && v.note == e.note) { v.releasing = true; break; }
            }
            break;
        case Type::AllNotesOff:
            for (auto& v : voices_) if (v.active) v.releasing = true;
            break;
    }
}

void PianoEngine::render(int16_t* out, int32_t numFrames) {
    NoteEventQueue::Event e{};
    while (events_.pop(&e)) handleEvent(e);

    for (int32_t i = 0; i < numFrames; ++i) {
        float sum = 0.0f;
        for (auto& v : voices_) {
            if (!v.active) continue;
            const auto& region = regions_[v.regionIndex];
            const int64_t total = static_cast<int64_t>(region.pcm.size());

            if (region.looped && v.position >= static_cast<double>(region.loopEndFrame)) {
                double loopLength = static_cast<double>(region.loopEndFrame - region.loopStartFrame);
                if (loopLength > 0.0) {
                    while (v.position >= static_cast<double>(region.loopEndFrame)) v.position -= loopLength;
                }
            }

            int64_t idx0 = static_cast<int64_t>(v.position);
            if (idx0 >= total) { v.active = false; continue; }
            double frac = v.position - static_cast<double>(idx0);
            int64_t idx1 = idx0 + 1;
            if (region.looped && idx1 >= region.loopEndFrame) idx1 = region.loopStartFrame;
            else if (idx1 >= total) idx1 = idx0;

            float s0 = static_cast<float>(region.pcm[idx0]);
            float s1 = static_cast<float>(region.pcm[idx1]);
            float sample = s0 + static_cast<float>(s1 - s0) * static_cast<float>(frac);

            float gain = v.velocityGain * (v.releasing ? v.releaseGain : 1.0f);
            sum += sample * gain;

            v.position += v.increment;
            if (v.releasing) {
                v.releaseGain -= v.releaseStep;
                if (v.releaseGain <= 0.0f) { v.active = false; continue; }
            }
            if (!region.looped && static_cast<int64_t>(v.position) >= total) v.active = false;
        }

        float mixed = sum * kMasterGain;
        mixed = std::clamp(mixed, -32768.0f, 32767.0f);
        out[i] = static_cast<int16_t>(mixed);
    }

    frameCounter_ += numFrames;
}

int32_t PianoEngine::activeVoiceCount() const {
    int32_t n = 0;
    for (const auto& v : voices_) if (v.active) ++n;
    return n;
}

}  // namespace songpad
