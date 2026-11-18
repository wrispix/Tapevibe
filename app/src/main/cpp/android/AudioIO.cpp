#include "AudioIO.h"

#include <android/log.h>

#define LOG_TAG "TapevibeAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tv {

AudioIO::~AudioIO() {
    stopInput();
    stop();
}

bool AudioIO::openOutputLocked() {
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result r = b.openStream(out_);
    if (r != oboe::Result::OK) {
        LOGE("open output failed: %s", oboe::convertToText(r));
        out_.reset();
        return false;
    }
    engine_.setSampleRate(static_cast<float>(out_->getSampleRate()));
    out_->setBufferSizeInFrames(out_->getFramesPerBurst() * 2);
    engine_.setStreamActive(true);
    r = out_->requestStart();
    if (r != oboe::Result::OK) {
        LOGE("start output failed: %s", oboe::convertToText(r));
        out_->close();
        out_.reset();
        engine_.setStreamActive(false);
        return false;
    }
    LOGI("output: %d Hz, burst %d, api %d", out_->getSampleRate(), out_->getFramesPerBurst(),
         static_cast<int>(out_->getAudioApi()));
    return true;
}

bool AudioIO::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (out_) return true;
    running_.store(openOutputLocked());
    return running_.load();
}

void AudioIO::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    running_.store(false);
    if (out_) {
        out_->stop();
        out_->close();
        out_.reset();
    }
    engine_.setStreamActive(false);
}

bool AudioIO::startInput() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (in_) return true;
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(static_cast<int32_t>(engine_.sampleRate()))
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setInputPreset(oboe::InputPreset::Unprocessed)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    if (b.openStream(in_) != oboe::Result::OK) {
        // Some devices reject the Unprocessed preset.
        b.setInputPreset(oboe::InputPreset::Generic);
        if (b.openStream(in_) != oboe::Result::OK) {
            LOGE("open input failed");
            in_.reset();
            return false;
        }
    }
    if (in_->requestStart() != oboe::Result::OK) {
        in_->close();
        in_.reset();
        return false;
    }
    return true;
}

void AudioIO::stopInput() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (in_) {
        in_->stop();
        in_->close();
        in_.reset();
    }
}

int AudioIO::outputLatencyMs() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!out_) return -1;
    auto r = out_->calculateLatencyMillis();
    return r ? static_cast<int>(r.value()) : -1;
}

oboe::DataCallbackResult AudioIO::onAudioReady(oboe::AudioStream* stream, void* data,
                                               int32_t frames) {
    if (stream->getDirection() == oboe::Direction::Input) {
        engine_.pushInput(static_cast<const float*>(data), frames, stream->getChannelCount());
    } else {
        engine_.render(static_cast<float*>(data), frames);
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioIO::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("stream error: %s", oboe::convertToText(error));
    if (stream->getDirection() == oboe::Direction::Output && error == oboe::Result::ErrorDisconnected) {
        // Headphones plugged/unplugged etc.: reopen on the new default device.
        std::lock_guard<std::mutex> lock(mutex_);
        out_.reset();
        engine_.setStreamActive(false);
        if (running_.load()) openOutputLocked();
    } else if (stream->getDirection() == oboe::Direction::Input) {
        std::lock_guard<std::mutex> lock(mutex_);
        in_.reset();
    }
}

}  // namespace tv
