#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>

#include "Engine.h"

namespace tv {

// Owns the Oboe output (and optional input) streams and feeds the Engine.
class AudioIO : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    explicit AudioIO(Engine& engine) : engine_(engine) {}
    ~AudioIO() override;

    bool start();
    void stop();
    bool running() const { return running_.load(); }
    bool startInput();
    void stopInput();
    int outputLatencyMs();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* data,
                                          int32_t frames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openOutputLocked();
    Engine& engine_;
    std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> out_, in_;
    std::atomic<bool> running_{false};
};

}  // namespace tv
