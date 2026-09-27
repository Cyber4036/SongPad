// §8.1 as amended by ADR-0035: a process-wide tap carrying the piano sampler's rendered output into the
// capture stream, so a Piano / Voice+Piano take recorded on headphones still contains the piano.
//
// Producer: PianoOutputStream::onAudioReady (the piano audio thread) — writes each rendered buffer.
// Consumer: CaptureEngine::onAudioReady (the capture audio thread) — mixes into the mic buffer.
// Both sides are real-time callbacks: no allocation, no locks, no logging, no JNI.
//
// The tap is only enabled while the output route is headphones. On the speaker the mic already picks
// the piano up acoustically (the original Phase 0 model); mixing digitally as well would double it.
#pragma once

#include <algorithm>
#include <atomic>
#include <cstdint>

#include "ring_buffer.h"

namespace songpad {

class SamplerTap {
public:
    static constexpr uint32_t kCapacitySamples = 48000 / 2;  // 500 ms at 48 kHz mono
    // The capture side never lets the tap run further behind the piano than this; any excess (a
    // capture stall, a pause) is dropped rather than played late into the file.
    static constexpr uint32_t kMaxLagSamples = 48000 / 50;   // 20 ms
    static constexpr uint32_t kScratchSamples = 4096;

    static SamplerTap& instance() {
        static SamplerTap tap;
        return tap;
    }

    // Any thread. Enabling discards whatever is buffered so a stale backlog is never mixed in.
    void setEnabled(bool enabled) {
        if (enabled && !enabled_.load(std::memory_order_acquire)) resetPending_.store(true, std::memory_order_release);
        enabled_.store(enabled, std::memory_order_release);
    }

    bool enabled() const { return enabled_.load(std::memory_order_acquire); }

    // Piano audio thread.
    void write(const int16_t* src, uint32_t count) {
        if (!enabled()) return;
        ring_.write(src, count);
    }

    // Capture audio thread: saturating-adds up to `count` tapped samples onto `dst`. Where the tap has
    // fewer samples than asked (the piano stream not started yet, a short underrun), the rest is mic only.
    void mixInto(int16_t* dst, uint32_t count) {
        if (resetPending_.exchange(false, std::memory_order_acq_rel)) ring_.clear();
        if (!enabled()) return;
        const uint32_t avail = ring_.available();
        if (avail > count + kMaxLagSamples) discard(avail - count - kMaxLagSamples);
        uint32_t done = 0;
        while (done < count) {
            const uint32_t want = std::min(count - done, kScratchSamples);
            const uint32_t got = ring_.read(scratch_, want);
            for (uint32_t i = 0; i < got; ++i) {
                const int32_t s = static_cast<int32_t>(dst[done + i]) + scratch_[i];
                dst[done + i] = static_cast<int16_t>(std::clamp(s, -32768, 32767));
            }
            done += got;
            if (got < want) break;
        }
    }

private:
    SamplerTap() : ring_(kCapacitySamples), enabled_(false), resetPending_(false) {}

    void discard(uint32_t n) {
        while (n > 0) {
            const uint32_t got = ring_.read(scratch_, std::min(n, kScratchSamples));
            if (got == 0) break;
            n -= got;
        }
    }

    RingBuffer ring_;
    std::atomic<bool> enabled_;
    std::atomic<bool> resetPending_;
    int16_t scratch_[kScratchSamples] = {};  // capture thread only
};

}  // namespace songpad
