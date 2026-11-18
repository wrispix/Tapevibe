#pragma once

#include <memory>
#include <string>
#include <vector>

namespace tv {

struct SampleBuffer {
    std::vector<float> l, r;  // r empty => mono
    float sampleRate = 48000.f;
    bool stereo() const { return !r.empty(); }
    size_t frames() const { return l.size(); }
};

// Reads PCM 8/16/24/32-bit and 32-bit float WAV (incl. WAVE_FORMAT_EXTENSIBLE).
std::unique_ptr<SampleBuffer> readWavFile(const std::string& path, std::string* error = nullptr);
std::unique_ptr<SampleBuffer> decodeWav(const uint8_t* data, size_t size, std::string* error = nullptr);

// Writes 16-bit PCM. `interleaved` holds frames * channels samples.
bool writeWavFile(const std::string& path, const float* interleaved, size_t frames, int channels,
                  int sampleRate);
bool writeWavFile(const std::string& path, const SampleBuffer& buffer);

}  // namespace tv
