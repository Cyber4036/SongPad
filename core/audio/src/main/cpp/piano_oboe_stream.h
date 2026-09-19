// Oboe output stream driving a PianoEngine (§8.1). Mirrors CaptureEngine's shape (capture_engine.h)
// but for Direction::Output. One instance per process, created lazily when Piano mode is armed.
#pragma once

#include <oboe/Oboe.h>

#include <memory>

#include "piano_engine.h"

namespace songpad {

class PianoOutputStream : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    PianoOutputStream();
    ~PianoOutputStream() override;

    int32_t open();
    int32_t start();
    int32_t stop();
    void close();

    PianoEngine& engine() { return engine_; }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    PianoEngine engine_;
};

}  // namespace songpad
