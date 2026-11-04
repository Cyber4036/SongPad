#include "piano_oboe_stream.h"

#include "sampler_tap.h"

#include <android/log.h>

#define LOG_TAG "songpad_piano"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace songpad {

PianoOutputStream::PianoOutputStream() : engine_(PianoEngine::kSampleRate) {}

PianoOutputStream::~PianoOutputStream() { close(); }

int32_t PianoOutputStream::open() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    return openLocked(true);
}

int32_t PianoOutputStream::openLocked(bool allowExclusive) {
    if (stream_) {
        auto st = stream_->getState();
        if (!disconnected_.load(std::memory_order_acquire) &&
            st != oboe::StreamState::Closed &&
            st != oboe::StreamState::Disconnected &&
            st != oboe::StreamState::Unknown) {
            return static_cast<int32_t>(oboe::Result::OK);
        }
        closeLocked();
    }
    disconnected_.store(false, std::memory_order_release);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(allowExclusive ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::I16)
        ->setFormatConversionAllowed(true)
        ->setSampleRate(PianoEngine::kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setChannelCount(1)
        ->setUsage(oboe::Usage::Media)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result r = builder.openStream(stream_);
    if (r != oboe::Result::OK && allowExclusive) {
        // Same fallback CaptureEngine uses for HALs that refuse Exclusive mode.
        builder.setSharingMode(oboe::SharingMode::Shared);
        r = builder.openStream(stream_);
    }
    if (r != oboe::Result::OK) {
        builder.setSharingMode(oboe::SharingMode::Shared)
            ->setPerformanceMode(oboe::PerformanceMode::None);
        r = builder.openStream(stream_);
    }
    if (r != oboe::Result::OK) {
        LOGW("piano openStream failed: %s", oboe::convertToText(r));
        stream_.reset();
        return static_cast<int32_t>(r);
    }
    // §8.1: buffer sized to ~2 bursts — the lowest latency the low-latency path can offer.
    int32_t burst = stream_->getFramesPerBurst();
    if (burst > 0) {
        stream_->setBufferSizeInFrames(burst * 2);
    }
    LOGI("piano output stream open: rate=%d burst=%d api=%s", stream_->getSampleRate(),
         burst, stream_->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES");
    return static_cast<int32_t>(oboe::Result::OK);
}

int32_t PianoOutputStream::start() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    armed_.store(true, std::memory_order_release);
    if (!stream_ || disconnected_.load(std::memory_order_acquire)) {
        int32_t openRc = openLocked(true);
        if (openRc != static_cast<int32_t>(oboe::Result::OK)) return openRc;
    }
    auto st = stream_->getState();
    if (st == oboe::StreamState::Started || st == oboe::StreamState::Starting) {
        return static_cast<int32_t>(oboe::Result::OK);
    }
    oboe::Result r = stream_->requestStart();
    if (r != oboe::Result::OK) {
        LOGW("piano requestStart failed (%s); reopening in Shared mode", oboe::convertToText(r));
        closeLocked();
        int32_t openRc = openLocked(false);
        if (openRc == static_cast<int32_t>(oboe::Result::OK) && stream_) {
            r = stream_->requestStart();
        }
    }
    return static_cast<int32_t>(r);
}

void PianoOutputStream::ensureRunningIfArmed() {
    if (!armed_.load(std::memory_order_acquire)) return;
    if (!disconnected_.load(std::memory_order_acquire)) return;
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (!armed_.load(std::memory_order_acquire) || !disconnected_.load(std::memory_order_acquire)) return;
    closeLocked();
    if (openLocked(false) == static_cast<int32_t>(oboe::Result::OK) && stream_) {
        stream_->requestStart();
    }
}

int32_t PianoOutputStream::stop() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    armed_.store(false, std::memory_order_release);
    if (!stream_) return static_cast<int32_t>(oboe::Result::OK);
    if (disconnected_.load(std::memory_order_acquire)) {
        closeLocked();
        return static_cast<int32_t>(oboe::Result::OK);
    }
    return static_cast<int32_t>(stream_->requestStop());
}

void PianoOutputStream::close() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    armed_.store(false, std::memory_order_release);
    closeLocked();
}

void PianoOutputStream::closeLocked() {
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
    disconnected_.store(false, std::memory_order_release);
}

oboe::DataCallbackResult PianoOutputStream::onAudioReady(oboe::AudioStream*, void* audioData, int32_t numFrames) {
    // THE PIANO AUDIO THREAD. render() is lock-free on its consumer side (§8.1) — no allocation,
    // no locks, no logging, no JNI, matching the rule already documented on CaptureEngine.
    if (audioData != nullptr && numFrames > 0) {
        auto* out = static_cast<int16_t*>(audioData);
        engine_.render(out, numFrames);
        SamplerTap::instance().write(out, static_cast<uint32_t>(numFrames));  // ADR-0035; no-op unless enabled
    }
    return oboe::DataCallbackResult::Continue;
}

void PianoOutputStream::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    LOGW("piano stream error after close: %s", oboe::convertToText(error));
    disconnected_.store(true, std::memory_order_release);
}

}  // namespace songpad
