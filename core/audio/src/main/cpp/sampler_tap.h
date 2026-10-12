// §8.1 as amended by ADR-0035: a process-wide tap carrying the piano sampler's rendered output into the
// capture stream, so a Piano / Voice+Piano take recorded on headphones still contains the piano.
//
// Producer: PianoOutputStream::onAudioReady (the piano audio thread) — writes each rendered buffer.
// Consumer: CaptureEngine::onAudioReady (the capture audio thread) — mixes into the mic buffer.
// Both sides are real-time callbacks: no allocation, no locks, no logging, no JNI.
//
// The two callbacks run on independent schedules with different burst sizes (an Exclusive piano stream may
// deliver ~96 frames per call while a Shared capture fallback asks for 960, and Bluetooth A2DP output often
// renders 1024–2048 at a time). So the tap is a small jitter buffer: the consumer waits until it holds a target
// fill before mixing, re-primes after an underrun, and trims anything beyond target + two bursts so the piano
// never drifts late in the file. The target follows the largest bursts actually observed on each side, not
// getFramesPerBurst(), which does not reflect A2DP chunking.
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
    static constexpr uint32_t kCapacitySamples = 16384;      // ~340 ms at 48 kHz mono; ring rounds to pow2
    static constexpr uint32_t kMinTargetSamples = 480;       // 10 ms floor
    static constexpr uint32_t kMaxTargetSamples = 8192;      // never buffer more than ~170 ms
    static constexpr uint32_t kScratchSamples = 4096;

    SamplerTap() : ring_(kCapacitySamples), enabled_(false), replaceMic_(false), resetPending_(false), maxProducerBurst_(0) {}

    static SamplerTap& instance() {
        static SamplerTap tap;
        return tap;
    }

    // Any thread. Enabling discards whatever is buffered so a stale backlog is never mixed in.
    void setEnabled(bool enabled, bool replaceMic = false) {
        if (enabled && !enabled_.load(std::memory_order_acquire)) {
            maxProducerBurst_.store(0, std::memory_order_relaxed);  // the new route may chunk differently
            resetPending_.store(true, std::memory_order_release);
        }
        replaceMic_.store(replaceMic, std::memory_order_release);
        enabled_.store(enabled, std::memory_order_release);
    }

    bool enabled() const { return enabled_.load(std::memory_order_acquire); }

    // Piano audio thread.
    void write(const int16_t* src, uint32_t count) {
        if (!enabled()) return;
        if (count > maxProducerBurst_.load(std::memory_order_relaxed)) maxProducerBurst_.store(count, std::memory_order_relaxed);
        ring_.write(src, count);
    }

    // Capture audio thread: saturating-adds `count` tapped samples onto `dst` once the tap is primed.
    // When replaceMic is true (PIANO mode), mic input is muted so the take contains only clean digital piano.
    void mixInto(int16_t* dst, uint32_t count) {
        if (resetPending_.exchange(false, std::memory_order_acq_rel)) {
            ring_.clear();
            primed_ = false;
            maxConsumerBurst_ = 0;
        }
        if (!enabled()) return;
        if (replaceMic_.load(std::memory_order_acquire)) {
            std::fill_n(dst, count, static_cast<int16_t>(0));
        }
        maxConsumerBurst_ = std::max(maxConsumerBurst_, count);
        const uint32_t burst = std::max(maxProducerBurst_.load(std::memory_order_relaxed), maxConsumerBurst_);
        const uint32_t target = std::clamp(2 * burst, kMinTargetSamples, kMaxTargetSamples);

        uint32_t avail = ring_.available();
        if (!primed_) {
            if (avail < target + count) return;
            primed_ = true;
        }
        // Trim drift/backlog. Two bursts of headroom, because jitter can land two producer bursts back to back;
        // anything beyond that is real backlog (a stall, clock drift) and is dropped back to target.
        if (avail > count + target + 2 * burst) {
            discard(avail - count - target);
            avail = ring_.available();
        }
        if (avail < count) primed_ = false;  // underrun: mix what there is, then re-prime

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
    void discard(uint32_t n) {
        while (n > 0) {
            const uint32_t got = ring_.read(scratch_, std::min(n, kScratchSamples));
            if (got == 0) break;
            n -= got;
        }
    }

    RingBuffer ring_;
    std::atomic<bool> enabled_;
    std::atomic<bool> replaceMic_;
    std::atomic<bool> resetPending_;
    std::atomic<uint32_t> maxProducerBurst_;
    // Capture thread only.
    bool primed_ = false;
    uint32_t maxConsumerBurst_ = 0;
    int16_t scratch_[kScratchSamples] = {};
};

}  // namespace songpad
