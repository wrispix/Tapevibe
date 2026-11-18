#pragma once

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace tv {

constexpr int kPPQ = 96;          // sequencer ticks per quarter note
constexpr int kMaxTracks = 16;
constexpr int kMaxParams = 16;
constexpr int kBlock = 32;        // internal processing block (frames)
constexpr float kPi = 3.14159265358979f;
constexpr float kTwoPi = 2.f * kPi;

enum TrackType : int { kSynth = 0, kDrums = 1, kSampler = 2, kAudio = 3 };

inline float clamp01(float v) { return std::clamp(v, 0.f, 1.f); }
inline float mtof(float note) { return 440.f * std::exp2((note - 69.f) / 12.f); }
// Normalised 0..1 -> 1 ms .. 5 s, exponential.
inline float timeParam(float v) { return 0.001f * std::pow(5000.f, clamp01(v)); }
// One-pole coefficient that decays ~60 dB over `seconds`.
inline float expCoef(float seconds, float sr) {
    return std::exp(-6.9f / std::max(1.f, seconds * sr));
}
// Cheap, smooth saturator (rational tanh approximation).
inline float softClip(float x) {
    x = std::clamp(x, -3.f, 3.f);
    return x * (27.f + x * x) / (27.f + 9.f * x * x);
}

struct Rng {
    uint32_t s = 0x9E3779B9u;
    float next() {  // white noise in [-1, 1)
        s ^= s << 13; s ^= s >> 17; s ^= s << 5;
        return static_cast<float>(static_cast<int32_t>(s)) * (1.f / 2147483648.f);
    }
};

// Topology-preserving-transform state variable filter (Zavalishin).
struct Svf {
    float ic1 = 0.f, ic2 = 0.f;
    void reset() { ic1 = ic2 = 0.f; }
    inline void process(float v0, float g, float k, float& lp, float& bp, float& hp) {
        const float a1 = 1.f / (1.f + g * (g + k));
        const float a2 = g * a1;
        const float a3 = g * a2;
        const float v3 = v0 - ic2;
        const float v1 = a1 * ic1 + a2 * v3;
        const float v2 = ic2 + a2 * ic1 + a3 * v3;
        ic1 = 2.f * v1 - ic1;
        ic2 = 2.f * v2 - ic2;
        lp = v2; bp = v1; hp = v0 - k * v1 - v2;
    }
};
inline float svfG(float fc, float sr) {
    fc = std::clamp(fc, 10.f, sr * 0.45f);
    return std::tan(kPi * fc / sr);
}

struct Adsr {
    enum Stage { Idle, Attack, Decay, Release };
    Stage stage = Idle;
    float level = 0.f;
    float aInc = 0.01f, dCoef = 0.999f, sus = 1.f, rCoef = 0.999f;

    void set(float a, float d, float s, float r, float sr) {
        aInc = 1.f / std::max(1.f, a * sr);
        dCoef = expCoef(d, sr);
        sus = s;
        rCoef = expCoef(r, sr);
    }
    void noteOn() { stage = Attack; }
    void noteOff() { if (stage != Idle) stage = Release; }
    void kill() { stage = Idle; level = 0.f; }
    bool active() const { return stage != Idle; }
    inline float next() {
        switch (stage) {
            case Attack:
                level += aInc;
                if (level >= 1.f) { level = 1.f; stage = Decay; }
                break;
            case Decay: level = sus + (level - sus) * dCoef; break;
            case Release:
                level *= rCoef;
                if (level < 1e-4f) { level = 0.f; stage = Idle; }
                break;
            case Idle: break;
        }
        return level;
    }
};

}  // namespace tv
