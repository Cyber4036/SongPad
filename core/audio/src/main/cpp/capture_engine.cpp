#include "capture_engine.h"

#include "sampler_tap.h"

#include <android/log.h>

#define LOG_TAG "songpad_audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace songpad {

CaptureEngine::CaptureEngine()
    : ring_(new RingBuffer(kSampleRate * kChannels * kRingSeconds)),
      framesCaptured_(0), capturing_(false), errorFlag_(false) {}

CaptureEngine::~CaptureEngine() { close(); }

int32_t CaptureEngine::open() {
    if (stream_) return static_cast<int32_t>(oboe::Result::OK);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setFormatConversionAllowed(true)
        ->setSampleRate(kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setChannelCount(kChannels)
        ->setInputPreset(oboe::InputPreset::Unprocessed)
        ->setUsage(oboe::Usage::Media)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result r = builder.openStream(stream_);
    if (r != oboe::Result::OK) {
        // Retry with the shared, non-Unprocessed path some low-end HALs insist on.
        builder.setSharingMode(oboe::SharingMode::Shared)->setInputPreset(oboe::InputPreset::VoiceRecognition);
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
    stream_->setBufferSizeInFrames(framesPerBurst_ * 2);
    bufferSize_ = stream_->getBufferSizeInFrames();
    framesCaptured_.store(0, std::memory_order_release);
    ring_->clear();
    errorFlag_.store(false, std::memory_order_release);
    LOGI("input stream open: rate=%d burst=%d cap=%d size=%d api=%s lowLatency=%d", stream_->getSampleRate(),
         framesPerBurst_, bufferCapacity_, bufferSize_, aaudio_ ? "AAudio" : "OpenSLES", lowLatency_ ? 1 : 0);
    return static_cast<int32_t>(oboe::Result::OK);
}

int32_t CaptureEngine::start() {
    if (!stream_) return static_cast<int32_t>(oboe::Result::ErrorClosed);
    capturing_.store(true, std::memory_order_release);
    auto st = stream_->getState();
    if (st == oboe::StreamState::Started || st == oboe::StreamState::Starting)
        return static_cast<int32_t>(oboe::Result::OK);
    return static_cast<int32_t>(stream_->requestStart());
}

int32_t CaptureEngine::pause() {
    capturing_.store(false, std::memory_order_release);
    return static_cast<int32_t>(oboe::Result::OK);
}

void CaptureEngine::close() {
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
    if (!stream_) return a;
    int64_t frames = 0, nanos = 0;
    oboe::Result r = stream_->getTimestamp(CLOCK_MONOTONIC, &frames, &nanos);
    if (r == oboe::Result::OK) {
        a.framePosition = frames;
        a.presentationTimeNanos = nanos;
        a.valid = true;
    } else {
        // Fallback for budget HAL drivers (e.g. MediaTek ALSA) where input hardware timestamping is unimplemented
        a.framePosition = framesCaptured_.load(std::memory_order_acquire);
        timespec ts{};
        clock_gettime(CLOCK_MONOTONIC, &ts);
        a.presentationTimeNanos = static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
        a.valid = true;
    }
    return a;
}

int32_t CaptureEngine::xRunCount() {
    if (!stream_) return 0;
    auto r = stream_->getXRunCount();
    return r ? r.value() : 0;
}

oboe::DataCallbackResult CaptureEngine::onAudioReady(oboe::AudioStream*, void* audioData, int32_t numFrames) {
    // Runs on Oboe's high-priority callback thread. Copy and return — nothing else (§7.2 rule 3).
    if (capturing_.load(std::memory_order_acquire)) {
        auto* samples = static_cast<int16_t*>(audioData);
        // ADR-0035: on headphones, the piano sampler's output is mixed in here — it has no acoustic path.
        SamplerTap::instance().mixInto(samples, static_cast<uint32_t>(numFrames * kChannels));
        ring_->write(samples, static_cast<uint32_t>(numFrames * kChannels));
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
