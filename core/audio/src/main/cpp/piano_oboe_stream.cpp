#include "piano_oboe_stream.h"

#include <android/log.h>

#define LOG_TAG "songpad_piano"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace songpad {

PianoOutputStream::PianoOutputStream() : engine_(PianoEngine::kSampleRate) {}

PianoOutputStream::~PianoOutputStream() { close(); }

int32_t PianoOutputStream::open() {
    if (stream_) return static_cast<int32_t>(oboe::Result::OK);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setFormatConversionAllowed(true)
        ->setSampleRate(PianoEngine::kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setChannelCount(1)
        ->setUsage(oboe::Usage::Media)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result r = builder.openStream(stream_);
    if (r != oboe::Result::OK) {
        // Same fallback CaptureEngine uses for HALs that refuse Exclusive mode.
        builder.setSharingMode(oboe::SharingMode::Shared);
        r = builder.openStream(stream_);
    }
    if (r != oboe::Result::OK) {
        LOGW("piano openStream failed: %s", oboe::convertToText(r));
        stream_.reset();
        return static_cast<int32_t>(r);
    }
    // §8.1: buffer sized to ~2 bursts — the lowest latency the low-latency path can offer.
    stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);
    LOGI("piano output stream open: rate=%d burst=%d api=%s", stream_->getSampleRate(),
         stream_->getFramesPerBurst(), stream_->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES");
    return static_cast<int32_t>(oboe::Result::OK);
}

int32_t PianoOutputStream::start() {
    if (!stream_) return static_cast<int32_t>(oboe::Result::ErrorClosed);
    return static_cast<int32_t>(stream_->requestStart());
}

int32_t PianoOutputStream::stop() {
    if (!stream_) return static_cast<int32_t>(oboe::Result::OK);
    return static_cast<int32_t>(stream_->requestStop());
}

void PianoOutputStream::close() {
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

oboe::DataCallbackResult PianoOutputStream::onAudioReady(oboe::AudioStream*, void* audioData, int32_t numFrames) {
    // THE PIANO AUDIO THREAD. render() is lock-free on its consumer side (§8.1) — no allocation,
    // no locks, no logging, no JNI, matching the rule already documented on CaptureEngine.
    engine_.render(static_cast<int16_t*>(audioData), numFrames);
    return oboe::DataCallbackResult::Continue;
}

void PianoOutputStream::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    LOGW("piano stream error after close: %s", oboe::convertToText(error));
}

}  // namespace songpad
