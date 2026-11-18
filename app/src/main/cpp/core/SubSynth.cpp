#include "Instruments.h"

namespace tv {
namespace {

inline float polyBlep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.f; }
    if (t > 1.f - dt) { t = (t - 1.f) / dt; return t * t + t + t + 1.f; }
    return 0.f;
}

inline float osc(int wave, float& phase, float dt) {
    const float t = phase;
    float out;
    switch (wave) {
        case 0: out = 2.f * t - 1.f - polyBlep(t, dt); break;  // saw
        case 1: {                                               // square
            float t2 = t + 0.5f;
            if (t2 >= 1.f) t2 -= 1.f;
            out = (t < 0.5f ? 1.f : -1.f) + polyBlep(t, dt) - polyBlep(t2, dt);
            break;
        }
        case 2: out = 4.f * std::fabs(t - 0.5f) - 1.f; break;  // triangle
        default: out = std::sin(kTwoPi * t); break;            // sine
    }
    phase += dt;
    if (phase >= 1.f) phase -= 1.f;
    return out;
}

}  // namespace

void SubSynth::noteOn(int pitch, float vel) {
    Voice* v = nullptr;
    for (auto& x : voices_) if (x.amp.active() && x.pitch == pitch) { v = &x; break; }
    if (!v) for (auto& x : voices_) if (!x.amp.active()) { v = &x; break; }
    if (!v) {  // steal: oldest released voice, else oldest overall
        for (auto& x : voices_) if (!x.gate && (!v || x.age < v->age)) v = &x;
        if (!v) for (auto& x : voices_) if (!v || x.age < v->age) v = &x;
    }
    if (!v->amp.active()) {
        v->ph1 = 0.f;
        v->ph2 = 0.25f;
        v->svf.reset();
    }
    v->pitch = pitch;
    v->vel = vel;
    v->gate = true;
    v->age = ++counter_;
    v->amp.noteOn();
    v->filt.noteOn();
}

void SubSynth::noteOff(int pitch) {
    for (auto& v : voices_) {
        if (v.gate && v.pitch == pitch) {
            v.gate = false;
            v.amp.noteOff();
            v.filt.noteOff();
        }
    }
}

void SubSynth::releaseAll() {
    for (auto& v : voices_) { v.gate = false; v.amp.noteOff(); v.filt.noteOff(); }
}

void SubSynth::reset() {
    for (auto& v : voices_) { v.gate = false; v.amp.kill(); v.filt.kill(); v.svf.reset(); }
}

void SubSynth::render(float* L, float* R, int n, const float* p) {
    using namespace synthp;
    const int w1 = static_cast<int>(std::lround(p[Osc1Wave] * 3.f));
    const int w2 = static_cast<int>(std::lround(p[Osc2Wave] * 3.f));
    const float semi = std::round(p[Osc2Semi] * 48.f - 24.f);
    const float cents = p[Osc2Fine] * 100.f - 50.f;
    const float ratio2 = std::exp2((semi + cents / 100.f) / 12.f);
    const float mix = p[OscMix];
    const float cutoff = 20.f * std::pow(900.f, p[Cutoff]);
    const float k = 2.f - 1.95f * p[Resonance];
    const float envAmt = p[EnvAmount] * 6.f;
    const float a = timeParam(p[Attack]), d = timeParam(p[Decay]);
    const float s = p[Sustain], r = timeParam(p[Release]);
    const float fdec = timeParam(p[FilterDecay]);
    const float level = p[Level] * 0.3f;
    const float lfoRate = 0.05f * std::pow(400.f, p[LfoRate]);
    const float lfoAmt = p[LfoAmount] * 3.f;

    float lfo[kBlock];
    for (int i = 0; i < n; ++i) {
        lfo[i] = std::sin(kTwoPi * lfoPhase_) * lfoAmt;
        lfoPhase_ += lfoRate / sr_;
        if (lfoPhase_ >= 1.f) lfoPhase_ -= 1.f;
    }

    for (auto& v : voices_) {
        if (!v.amp.active()) continue;
        v.amp.set(a, d, s, r, sr_);
        v.filt.set(0.001f, fdec, 0.f, fdec, sr_);
        const float f1 = mtof(static_cast<float>(v.pitch));
        const float dt1 = std::min(f1 / sr_, 0.49f);
        const float dt2 = std::min(f1 * ratio2 / sr_, 0.49f);
        const float gain = level * v.vel;
        for (int i = 0; i < n; ++i) {
            const float o = osc(w1, v.ph1, dt1) * (1.f - mix) + osc(w2, v.ph2, dt2) * mix;
            const float fc = cutoff * std::exp2(envAmt * v.filt.next() + lfo[i]);
            float lp, bp, hp;
            v.svf.process(o, svfG(fc, sr_), k, lp, bp, hp);
            const float out = lp * v.amp.next() * gain;
            L[i] += out;
            R[i] += out;
            if (!v.amp.active()) break;
        }
    }
}

}  // namespace tv
