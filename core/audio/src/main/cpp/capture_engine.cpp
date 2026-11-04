#include "capture_engine.h"

#include "sampler_tap.h"

#include <android/log.h>
#include <cmath>
#include <cstdlib>

#define LOG_TAG "songpad_audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace songpad {

CaptureEngine::CaptureEngine()
    : ring_(new RingBuffer(kSampleRate * kChannels * kRingSeconds)),
      mixBuffer_(new int16_t[kMixBufferSamples]()),
      framesCaptured_(0), capturing_(false), errorFlag_(false) {}

CaptureEngine::~CaptureEngine() { close(); }

int32_t CaptureEngine::open() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    return openLocked(true);
}

int32_t CaptureEngine::openLocked(bool allowExclusive) {
    if (stream_) return static_cast<int32_t>(oboe::Result::OK);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(allowExclusive ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::I16)
        ->setFormatConversionAllowed(true)
        ->setSampleRate(kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setChannelCount(kChannels)
        ->setInputPreset(allowExclusive ? oboe::InputPreset::Generic : oboe::InputPreset::VoiceRecognition)
        ->setUsage(oboe::Usage::Media)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    openedExclusive_ = allowExclusive;
    oboe::Result r = builder.openStream(stream_);
    if (r != oboe::Result::OK && allowExclusive) {
        // Retry with the shared path some low-end HALs insist on.
        openedExclusive_ = false;
        builder.setSharingMode(oboe::SharingMode::Shared)->setInputPreset(oboe::InputPreset::VoiceRecognition);
        r = builder.openStream(stream_);
    }
    if (r != oboe::Result::OK) {
        // Final fallback without LowLatency flag for HALs that reject simultaneous fast input + fast output.
        openedExclusive_ = false;
        builder.setSharingMode(oboe::SharingMode::Shared)
            ->setPerformanceMode(oboe::PerformanceMode::None)
            ->setInputPreset(oboe::InputPreset::Generic);
        r = builder.openStream(stream_);
    }
    if (r != oboe::Result::OK) {
        LOGW("openStream failed: %s", oboe::convertToText(r));
        stream_.reset();
        return static_cast<int32_t>(r);
    }
    framesPerBurst_ = stream_->getFramesPerBurst();
    bufferCapacity_ = stream_->getBufferCapacityInFrames();
    lowLatency_ = stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency;
    aaudio_ = stream_->getAudioApi() == oboe::AudioApi::AAudio;
    // Buffer sizing (§8.1 / docs/audio/BUFFER_SIZING.md): two bursts is the low-latency default; the ring
    // buffer behind it absorbs everything the disk path does, so the stream buffer stays small.
    if (framesPerBurst_ > 0) {
        stream_->setBufferSizeInFrames(framesPerBurst_ * 2);
    }
    bufferSize_ = stream_->getBufferSizeInFrames();
    framesCaptured_.store(0, std::memory_order_release);
    ring_->clear();
    errorFlag_.store(false, std::memory_order_release);
    LOGI("input stream open: rate=%d burst=%d cap=%d size=%d api=%s lowLatency=%d exclusive=%d",
         stream_->getSampleRate(), framesPerBurst_, bufferCapacity_, bufferSize_,
         aaudio_ ? "AAudio" : "OpenSLES", lowLatency_ ? 1 : 0, openedExclusive_ ? 1 : 0);
    return static_cast<int32_t>(oboe::Result::OK);
}

int32_t CaptureEngine::start() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (!stream_) return static_cast<int32_t>(oboe::Result::ErrorClosed);
    capturing_.store(true, std::memory_order_release);
    auto st = stream_->getState();
    if (st == oboe::StreamState::Started || st == oboe::StreamState::Starting)
        return static_cast<int32_t>(oboe::Result::OK);
    oboe::Result r = stream_->requestStart();
    if (r != oboe::Result::OK) {
        LOGW("input requestStart failed (%s); reopening in Shared fallback mode", oboe::convertToText(r));
        closeLocked();
        int32_t openRc = openLocked(false);
        if (openRc == static_cast<int32_t>(oboe::Result::OK) && stream_) {
            capturing_.store(true, std::memory_order_release);
            r = stream_->requestStart();
        }
    }
    if (r != oboe::Result::OK) {
        capturing_.store(false, std::memory_order_release);
    }
    return static_cast<int32_t>(r);
}

int32_t CaptureEngine::pause() {
    capturing_.store(false, std::memory_order_release);
    return static_cast<int32_t>(oboe::Result::OK);
}

void CaptureEngine::close() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    closeLocked();
}

void CaptureEngine::closeLocked() {
    capturing_.store(false, std::memory_order_release);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

uint32_t CaptureEngine::drain(int16_t* dst, uint32_t maxSamples) { return ring_->read(dst, maxSamples); }

TimestampAnchor CaptureEngine::timestamp() {
    TimestampAnchor a{0, 0, false};
    std::shared_ptr<oboe::AudioStream> s;
    {
        std::lock_guard<std::mutex> lock(streamMutex_);
        s = stream_;
    }
    if (!s) return a;
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    const int64_t nowNanos = static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;

    int64_t frames = 0, nanos = 0;
    oboe::Result r = s->getTimestamp(CLOCK_MONOTONIC, &frames, &nanos);
    // Validate that hardware timestamp is non-negative and within 5 seconds of CLOCK_MONOTONIC
    // (some budget HALs erroneously report CLOCK_BOOTTIME, which diverges by total sleep duration).
    const int64_t diff = nanos > nowNanos ? (nanos - nowNanos) : (nowNanos - nanos);
    if (r == oboe::Result::OK && frames >= 0 && diff <= 5000000000LL) {
        a.framePosition = frames;
        a.presentationTimeNanos = nanos;
        a.valid = true;
    } else {
        // Fallback for budget HAL drivers (e.g. MediaTek ALSA) where input hardware timestamping is unimplemented or skewed.
        a.framePosition = framesCaptured_.load(std::memory_order_acquire);
        a.presentationTimeNanos = nowNanos;
        a.valid = true;
    }
    return a;
}

int32_t CaptureEngine::xRunCount() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (!stream_) return 0;
    auto r = stream_->getXRunCount();
    return r ? r.value() : 0;
}

oboe::DataCallbackResult CaptureEngine::onAudioReady(oboe::AudioStream*, void* audioData, int32_t numFrames) {
    // Runs on Oboe's high-priority callback thread. Copy and return — nothing else (§7.2 rule 3).
    // Never mutate `audioData` in-place: on some AAudio MMAP HALs the input buffer is mapped PROT_READ.
    if (capturing_.load(std::memory_order_acquire) && numFrames > 0) {
        const auto* samples = static_cast<const int16_t*>(audioData);
        uint32_t remaining = static_cast<uint32_t>(numFrames * kChannels);
        uint32_t offset = 0;
        while (remaining > 0) {
            const uint32_t chunk = std::min(remaining, kMixBufferSamples);
            const int16_t* chunkSrc = samples != nullptr ? (samples + offset) : nullptr;
            SamplerTap::instance().copyAndMix(chunkSrc, mixBuffer_.get(), chunk);
            ring_->write(mixBuffer_.get(), chunk);
            offset += chunk;
            remaining -= chunk;
        }
        framesCaptured_.fetch_add(numFrames, std::memory_order_acq_rel);
    }
    return oboe::DataCallbackResult::Continue;
}

void CaptureEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    LOGW("stream error after close: %s", oboe::convertToText(error));
    errorFlag_.store(true, std::memory_order_release);
    capturing_.store(false, std::memory_order_release);
}

}  // namespace songpad
