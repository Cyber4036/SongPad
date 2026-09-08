// Oboe input stream → lock-free ring buffer (§7.1, §7.2). One instance per process.
#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <cstdint>
#include <memory>

#include "ring_buffer.h"

namespace songpad {

struct TimestampAnchor {
    int64_t framePosition;
    int64_t presentationTimeNanos;
    bool valid;
};

class CaptureEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int32_t kSampleRate = 48000;
    static constexpr int32_t kChannels = 1;
    static constexpr uint32_t kRingSeconds = 2;  // >= 2 s of audio (§7.2 rule 6)

    CaptureEngine();
    ~CaptureEngine() override;

    // Opens the low-latency mono I16 input stream and starts it. Returns an oboe::Result code (0 = OK).
    int32_t open();
    int32_t start();
    int32_t pause();  // stops reads; keeps the stream open so resume is cheap
    void close();

    // Writer-thread side: drains up to maxSamples into dst. Never blocks.
    uint32_t drain(int16_t* dst, uint32_t maxSamples);
    uint32_t available() const { return ring_->available(); }
    uint64_t overruns() const { return ring_->overruns(); }

    // Frames the callback has delivered since open() — the raw hardware count feeding framesWritten (§8.3).
    int64_t framesCaptured() const { return framesCaptured_.load(std::memory_order_acquire); }

    // Samples AudioTimestamp from the stream: (framePosition, presentationTimeNanos) tying the DMA clock to CLOCK_MONOTONIC.
    TimestampAnchor timestamp();

    // Measured stream properties for the buffer-sizing report (docs/audio/BUFFER_SIZING.md).
    int32_t framesPerBurst() const { return framesPerBurst_; }
    int32_t bufferCapacityFrames() const { return bufferCapacity_; }
    int32_t bufferSizeFrames() const { return bufferSize_; }
    bool isLowLatency() const { return lowLatency_; }
    bool isAAudio() const { return aaudio_; }
    int32_t xRunCount();

    // oboe::AudioStreamDataCallback — THE CAPTURE THREAD. No allocation, no locks, no logging, no JNI.
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    // oboe::AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;
    bool errorOccurred() const { return errorFlag_.load(std::memory_order_acquire); }

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<RingBuffer> ring_;
    std::atomic<int64_t> framesCaptured_;
    std::atomic<bool> capturing_;
    std::atomic<bool> errorFlag_;
    int32_t framesPerBurst_ = 0;
    int32_t bufferCapacity_ = 0;
    int32_t bufferSize_ = 0;
    bool lowLatency_ = false;
    bool aaudio_ = false;
};

}  // namespace songpad
