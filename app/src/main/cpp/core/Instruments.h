#pragma once

#include "Common.h"
#include "Wav.h"

namespace tv {

// Parameter layouts (all values normalised 0..1; mirrored in Kotlin InstrumentParams.kt).
namespace synthp {
enum { Osc1Wave, Osc2Wave, Osc2Semi, Osc2Fine, OscMix, Cutoff, Resonance, EnvAmount,
       Attack, Decay, Sustain, Release, FilterDecay, Level, LfoRate, LfoAmount };
}
namespace drump {
enum { KickTune, KickDecay, SnareTone, SnareDecay, HatDecay, TomTune, Level, Drive };
}
namespace samplerp {
enum { RootNote, Attack, Release, Tune, Level };
}
namespace audiop {
enum { Level };
}

constexpr int kDrumBaseNote = 36;  // drum pieces are notes 36..43

// Polyphonic two-oscillator subtractive synth.
class SubSynth {
public:
    static constexpr int kVoices = 8;
    void setSampleRate(float sr) { sr_ = sr; }
    void noteOn(int pitch, float vel);
    void noteOff(int pitch);
    void releaseAll();
    void reset();
    void render(float* L, float* R, int n, const float* p);

private:
    struct Voice {
        int pitch = -1;
        float vel = 0.f, ph1 = 0.f, ph2 = 0.f;
        bool gate = false;
        uint32_t age = 0;
        Adsr amp, filt;
        Svf svf;
    };
    Voice voices_[kVoices];
    float sr_ = 48000.f;
    float lfoPhase_ = 0.f;
    uint32_t counter_ = 0;
};

// Eight synthesised drum pieces: kick, snare, clap, closed hat, open hat, low tom, high tom, rim.
class DrumKit {
public:
    static constexpr int kPieces = 8;
    void setSampleRate(float sr) { sr_ = sr; }
    void noteOn(int pitch, float vel);
    void noteOff(int) {}
    void releaseAll() {}
    void reset();
    void render(float* L, float* R, int n, const float* p);

private:
    struct Piece {
        bool active = false;
        float t = 0.f, vel = 0.f, phase = 0.f;
        Svf f;
    };
    Piece pc_[kPieces];
    Rng rng_;
    float sr_ = 48000.f;
};

// Pitched sample player (gated, linear interpolation).
class Sampler {
public:
    static constexpr int kVoices = 8;
    void setSampleRate(float sr) { sr_ = sr; }
    void noteOn(int pitch, float vel);
    void noteOff(int pitch);
    void releaseAll();
    void reset();
    void render(float* L, float* R, int n, const float* p, const SampleBuffer* buf);

private:
    struct Voice {
        bool active = false, gate = false;
        int pitch = 60;
        float vel = 0.f;
        double pos = 0.0;
        uint32_t age = 0;
        Adsr env;
    };
    Voice voices_[kVoices];
    const SampleBuffer* last_ = nullptr;
    float sr_ = 48000.f;
    uint32_t counter_ = 0;
};

}  // namespace tv
