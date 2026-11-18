#include "Instruments.h"

namespace tv {

void DrumKit::noteOn(int pitch, float vel) {
    const int idx = pitch - kDrumBaseNote;
    if (idx < 0 || idx >= kPieces) return;
    Piece& p = pc_[idx];
    p.active = true;
    p.t = 0.f;
    p.vel = vel;
    p.phase = 0.f;
    p.f.reset();
    if (idx == 3) pc_[4].active = false;  // closed hat chokes open hat
}

void DrumKit::reset() {
    for (auto& p : pc_) p.active = false;
}

void DrumKit::render(float* L, float* R, int n, const float* prm) {
    using namespace drump;
    const float dt = 1.f / sr_;
    const float level = prm[Level] * 1.5f;
    const float drive = 1.f + prm[Drive] * 4.f;
    const float driveNorm = 1.f / softClip(drive * 0.7f) * 0.7f;
    const float kickBase = 40.f + 50.f * prm[KickTune];
    const float kickDecay = 0.1f + 0.9f * prm[KickDecay];
    const float snareTone = 160.f + 120.f * prm[SnareTone];
    const float snareDecay = 0.08f + 0.4f * prm[SnareDecay];
    const float chDecay = 0.02f + 0.12f * prm[HatDecay];
    const float ohDecay = 0.15f + 0.6f * prm[HatDecay];
    const float tomBase = prm[TomTune];
    const float gHat = svfG(7000.f, sr_), gSnare = svfG(3500.f, sr_);
    const float gClap = svfG(1200.f, sr_), gRim = svfG(2500.f, sr_);

    for (int idx = 0; idx < kPieces; ++idx) {
        Piece& p = pc_[idx];
        if (!p.active) continue;
        for (int i = 0; i < n; ++i) {
            const float t = p.t;
            float s = 0.f, lp, bp, hp;
            switch (idx) {
                case 0: {  // kick
                    const float f = kickBase + kickBase * 3.f * std::exp(-t * 30.f);
                    p.phase += f * dt;
                    s = std::sin(kTwoPi * p.phase) * std::exp(-t * 4.6f / kickDecay) +
                        rng_.next() * std::exp(-t * 800.f) * 0.3f;
                    break;
                }
                case 1: {  // snare
                    p.phase += snareTone * dt;
                    p.f.process(rng_.next(), gSnare, 1.4f, lp, bp, hp);
                    s = std::sin(kTwoPi * p.phase) * std::exp(-t * 25.f) * 0.6f +
                        hp * std::exp(-t * 4.6f / snareDecay) * 0.8f;
                    break;
                }
                case 2: {  // clap: three quick bursts then a tail
                    p.f.process(rng_.next(), gClap, 0.6f, lp, bp, hp);
                    const float env = t < 0.03f ? std::exp(-std::fmod(t, 0.01f) * 300.f)
                                                : std::exp(-(t - 0.03f) * 18.f);
                    s = bp * env * 1.6f;
                    break;
                }
                case 3:
                case 4: {  // closed / open hat
                    p.f.process(rng_.next(), gHat, 1.2f, lp, bp, hp);
                    s = hp * std::exp(-t * 4.6f / (idx == 3 ? chDecay : ohDecay)) * 0.55f;
                    break;
                }
                case 5:
                case 6: {  // toms
                    const float base = idx == 5 ? 80.f + 60.f * tomBase : 140.f + 90.f * tomBase;
                    p.phase += base * (1.f + 0.5f * std::exp(-t * 20.f)) * dt;
                    s = std::sin(kTwoPi * p.phase) * std::exp(-t * 4.6f / (idx == 5 ? 0.45f : 0.3f));
                    break;
                }
                default: {  // rim
                    p.phase += 1700.f * dt;
                    p.f.process(rng_.next(), gRim, 1.0f, lp, bp, hp);
                    s = (std::sin(kTwoPi * p.phase) * 0.7f + bp * 0.4f) * std::exp(-t * 4.6f / 0.03f);
                    break;
                }
            }
            if (p.phase >= 1.f) p.phase -= std::floor(p.phase);
            const float out = softClip(s * p.vel * drive * 0.7f) * driveNorm * level;
            L[i] += out;
            R[i] += out;
            p.t += dt;
        }
        if (p.t > 2.5f) p.active = false;
    }
}

}  // namespace tv
