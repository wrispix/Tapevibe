#include "Wav.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

namespace tv {
namespace {

uint16_t rd16(const uint8_t* p) { return static_cast<uint16_t>(p[0] | (p[1] << 8)); }
uint32_t rd32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}
void fail(std::string* e, const char* msg) { if (e) *e = msg; }

void put16(std::vector<uint8_t>& v, uint16_t x) { v.push_back(x & 0xff); v.push_back(x >> 8); }
void put32(std::vector<uint8_t>& v, uint32_t x) {
    for (int i = 0; i < 4; ++i) v.push_back(static_cast<uint8_t>(x >> (8 * i)));
}

}  // namespace

std::unique_ptr<SampleBuffer> decodeWav(const uint8_t* d, size_t size, std::string* err) {
    if (size < 12 || std::memcmp(d, "RIFF", 4) != 0 || std::memcmp(d + 8, "WAVE", 4) != 0) {
        fail(err, "not a RIFF/WAVE file");
        return nullptr;
    }
    int format = 0, channels = 0, bits = 0;
    uint32_t rate = 0;
    const uint8_t* data = nullptr;
    size_t dataSize = 0;
    size_t pos = 12;
    while (pos + 8 <= size) {
        const uint8_t* ck = d + pos;
        size_t len = rd32(ck + 4);
        const uint8_t* body = ck + 8;
        size_t avail = size - pos - 8;
        if (std::memcmp(ck, "fmt ", 4) == 0 && len >= 16 && avail >= 16) {
            format = rd16(body);
            channels = rd16(body + 2);
            rate = rd32(body + 4);
            bits = rd16(body + 14);
            if (format == 0xFFFE && len >= 26 && avail >= 26) format = rd16(body + 24);
        } else if (std::memcmp(ck, "data", 4) == 0) {
            data = body;
            dataSize = std::min(len, avail);
        }
        pos += 8 + len + (len & 1);
    }
    if (!data || channels <= 0 || rate == 0) {
        fail(err, "missing fmt or data chunk");
        return nullptr;
    }
    const bool isFloat = format == 3;
    if (!(format == 1 || isFloat) || (isFloat && bits != 32) ||
        (!isFloat && bits != 8 && bits != 16 && bits != 24 && bits != 32)) {
        fail(err, "unsupported WAV encoding");
        return nullptr;
    }
    const size_t bps = bits / 8;
    const size_t frameBytes = bps * channels;
    const size_t frames = dataSize / frameBytes;
    auto buf = std::make_unique<SampleBuffer>();
    buf->sampleRate = static_cast<float>(rate);
    buf->l.resize(frames);
    if (channels >= 2) buf->r.resize(frames);

    auto sample = [&](const uint8_t* p) -> float {
        if (isFloat) { float f; std::memcpy(&f, p, 4); return std::isfinite(f) ? f : 0.f; }
        switch (bits) {
            case 8: return (static_cast<int>(p[0]) - 128) / 128.f;
            case 16: return static_cast<int16_t>(rd16(p)) / 32768.f;
            case 24: {
                int32_t v = static_cast<int32_t>((p[0] << 8) | (p[1] << 16) | (p[2] << 24)) >> 8;
                return v / 8388608.f;
            }
            default: return static_cast<int32_t>(rd32(p)) / 2147483648.f;
        }
    };
    for (size_t i = 0; i < frames; ++i) {
        const uint8_t* f = data + i * frameBytes;
        buf->l[i] = sample(f);
        if (channels >= 2) buf->r[i] = sample(f + bps);
    }
    return buf;
}

std::unique_ptr<SampleBuffer> readWavFile(const std::string& path, std::string* err) {
    FILE* f = std::fopen(path.c_str(), "rb");
    if (!f) { fail(err, "cannot open file"); return nullptr; }
    std::vector<uint8_t> bytes;
    uint8_t chunk[65536];
    size_t n;
    while ((n = std::fread(chunk, 1, sizeof chunk, f)) > 0) bytes.insert(bytes.end(), chunk, chunk + n);
    std::fclose(f);
    return decodeWav(bytes.data(), bytes.size(), err);
}

bool writeWavFile(const std::string& path, const float* in, size_t frames, int channels,
                  int sampleRate) {
    const uint32_t dataBytes = static_cast<uint32_t>(frames * channels * 2);
    std::vector<uint8_t> v;
    v.reserve(44 + dataBytes);
    v.insert(v.end(), {'R', 'I', 'F', 'F'});
    put32(v, 36 + dataBytes);
    v.insert(v.end(), {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
    put32(v, 16);
    put16(v, 1);
    put16(v, static_cast<uint16_t>(channels));
    put32(v, static_cast<uint32_t>(sampleRate));
    put32(v, static_cast<uint32_t>(sampleRate * channels * 2));
    put16(v, static_cast<uint16_t>(channels * 2));
    put16(v, 16);
    v.insert(v.end(), {'d', 'a', 't', 'a'});
    put32(v, dataBytes);
    for (size_t i = 0; i < frames * channels; ++i) {
        float s = std::clamp(in[i], -1.f, 1.f);
        put16(v, static_cast<uint16_t>(static_cast<int16_t>(std::lrint(s * 32767.f))));
    }
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f) return false;
    bool ok = std::fwrite(v.data(), 1, v.size(), f) == v.size();
    ok = (std::fclose(f) == 0) && ok;
    return ok;
}

bool writeWavFile(const std::string& path, const SampleBuffer& b) {
    const int ch = b.stereo() ? 2 : 1;
    std::vector<float> inter(b.frames() * ch);
    for (size_t i = 0; i < b.frames(); ++i) {
        inter[i * ch] = b.l[i];
        if (ch == 2) inter[i * 2 + 1] = b.r[i];
    }
    return writeWavFile(path, inter.data(), b.frames(), ch, static_cast<int>(b.sampleRate));
}

}  // namespace tv
