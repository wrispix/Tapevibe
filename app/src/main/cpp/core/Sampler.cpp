#include "Instruments.h"

namespace tv {

void Sampler::noteOn(int pitch, float vel) {
    Voice* v = nullptr;
    for (auto& x : voices_) if (!x.active) { v = &x; break; }
    if (!v) for (auto& x : voices_) if (!v || x.age < v->age) v = &x;
    v->active = true;
    v->gate = true;
    v->pitch = pitch;
    v->vel = vel;
    v->pos = 0.0;
    v->age = ++counter_;
    v->env.kill();
    v->env.noteOn();
}

void Sampler::noteOff(int pitch) {
    for (auto& v : voices_) if (v.gate && v.pitch == pitch) { v.gate = false; v.env.noteOff(); }
}

void Sampler::releaseAll() {
    for (auto& v : voices_) { v.gate = false; v.env.noteOff(); }
}

void Sampler::reset() {
    for (auto& v : voices_) { v.active = v.gate = false; v.env.kill(); }
}

void Sampler::render(float* L, float* R, int n, const float* p, const SampleBuffer* buf) {
    using namespace samplerp;
    if (buf != last_) {  // sample swapped: voices point into the old buffer
        if (last_) reset();
        last_ = buf;
    }
    if (!buf || buf->frames() < 2) { reset(); return; }
    const float root = std::round(p[RootNote] * 127.f);
    const float tune = p[Tune] * 24.f - 12.f;
    const float level = p[Level];
    const double srRatio = buf->sampleRate / sr_;
    const size_t last = buf->frames() - 1;
    const float* bl = buf->l.data();
    const float* br = buf->stereo() ? buf->r.data() : bl;

    for (auto& v : voices_) {
        if (!v.active) continue;
        v.env.set(timeParam(p[Attack]) * 0.5f, 1.f, 1.f, timeParam(p[Release]), sr_);
        const double inc = std::exp2((v.pitch - root + tune) / 12.0) * srRatio;
        const float gain = v.vel * level;
        for (int i = 0; i < n; ++i) {
            const size_t idx = static_cast<size_t>(v.pos);
            if (idx >= last) { v.active = false; break; }
            const float frac = static_cast<float>(v.pos - idx);
            const float e = v.env.next() * gain;
            L[i] += (bl[idx] + (bl[idx + 1] - bl[idx]) * frac) * e;
            R[i] += (br[idx] + (br[idx + 1] - br[idx]) * frac) * e;
            v.pos += inc;
            if (!v.env.active()) { v.active = false; break; }
        }
    }
}

}  // namespace tv
