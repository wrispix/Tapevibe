#include "Engine.h"

#include <cstring>

namespace tv {

namespace {
constexpr float kDefaultParams[4][kMaxParams] = {
    // synth
    {0.f, 0.f, 0.5f, 0.56f, 0.4f, 0.55f, 0.3f, 0.35f, 0.05f, 0.45f, 0.6f, 0.4f, 0.45f, 0.8f, 0.4f, 0.f},
    // drums
    {0.4f, 0.45f, 0.5f, 0.4f, 0.3f, 0.5f, 0.8f, 0.2f},
    // sampler
    {60.f / 127.f, 0.f, 0.35f, 0.5f, 0.8f},
    // audio
    {0.8f},
};
}  // namespace

Engine::Engine() {
    for (auto& p : peaks_) p.store(0.f);
    for (auto& t : tracks_)
        for (int i = 0; i < kMaxParams; ++i) t.params[i].store(kDefaultParams[0][i]);
    setSampleRate(48000.f);
}

Engine::~Engine() {
    for (auto& t : tracks_) {
        delete t.notes.exchange(nullptr);
        delete t.sample.exchange(nullptr);
    }
    collectGarbage(true);
}

void Engine::setSampleRate(float sr) {
    sr_ = sr;
    for (auto& t : tracks_) {
        t.synth.setSampleRate(sr);
        t.drums.setSampleRate(sr);
        t.sampler.setSampleRate(sr);
    }
    delayL_.assign(static_cast<size_t>(sr * 4.f), 0.f);
    delayR_.assign(delayL_.size(), 0.f);
    delayW_ = 0;
}

void Engine::setDelay(int division, float feedback, float damping) {
    delayDiv_.store(std::clamp(division, 0, 5));
    delayFb_.store(std::clamp(feedback, 0.f, 0.92f));
    delayDamp_.store(clamp01(damping));
}

void Engine::setTrackType(int t, int type) {
    if (t < 0 || t >= kMaxTracks) return;
    tracks_[t].type.store(std::clamp(type, 0, 3));
}

void Engine::setTrackParam(int t, int i, float v) {
    if (t < 0 || t >= kMaxTracks || i < 0 || i >= kMaxParams) return;
    tracks_[t].params[i].store(clamp01(v));
}

void Engine::setTrackMixer(int t, float volume, float pan, float send, bool mute, bool solo) {
    if (t < 0 || t >= kMaxTracks) return;
    Track& tr = tracks_[t];
    tr.volume.store(clamp01(volume));
    tr.pan.store(std::clamp(pan, -1.f, 1.f));
    tr.send.store(clamp01(send));
    tr.mute.store(mute);
    tr.solo.store(solo);
}

void Engine::setTrackNotes(int t, std::vector<NoteEvent> notes) {
    if (t < 0 || t >= kMaxTracks) return;
    std::sort(notes.begin(), notes.end(),
              [](const NoteEvent& a, const NoteEvent& b) { return a.start < b.start; });
    auto* nl = new NoteList{std::move(notes)};
    NoteList* old = tracks_[t].notes.exchange(nl, std::memory_order_acq_rel);
    if (old) retire([old] { delete old; });
}

void Engine::setTrackSample(int t, std::unique_ptr<SampleBuffer> sample) {
    if (t < 0 || t >= kMaxTracks) return;
    SampleBuffer* old = tracks_[t].sample.exchange(sample.release(), std::memory_order_acq_rel);
    if (old) retire([old] { delete old; });
}

const SampleBuffer* Engine::trackSample(int t) const {
    if (t < 0 || t >= kMaxTracks) return nullptr;
    return tracks_[t].sample.load(std::memory_order_acquire);
}

void Engine::retire(std::function<void()> del) {
    std::lock_guard<std::mutex> lock(gcMutex_);
    retired_.emplace_back(epoch_.load(std::memory_order_acquire), std::move(del));
}

void Engine::collectGarbage(bool force) {
    const bool idle = !streamActive_.load() && !offline_.load();
    std::vector<std::function<void()>> toFree;
    {
        std::lock_guard<std::mutex> lock(gcMutex_);
        const uint64_t now = epoch_.load(std::memory_order_acquire);
        auto it = std::remove_if(retired_.begin(), retired_.end(), [&](auto& e) {
            if (force || idle || now >= e.first + 2) {
                toFree.push_back(std::move(e.second));
                return true;
            }
            return false;
        });
        retired_.erase(it, retired_.end());
    }
    for (auto& f : toFree) f();
}

void Engine::pushCommand(const Command& c) {
    std::lock_guard<std::mutex> lock(cmdMutex_);
    commands_.push(c);
}

void Engine::rewind() {
    pushCommand({kCmdRewind, 0, 0, 0.f});
    posPublished_.store(0.0);
}

void Engine::noteOn(int t, int pitch, float vel) {
    if (t >= 0 && t < kMaxTracks) pushCommand({kCmdNoteOn, t, pitch, vel});
}

void Engine::noteOff(int t, int pitch) {
    if (t >= 0 && t < kMaxTracks) pushCommand({kCmdNoteOff, t, pitch, 0.f});
}

bool Engine::armRecord(int t, float maxSeconds) {
    if (t < 0 || t >= kMaxTracks || recState_.load() != kRecIdle) return false;
    recBuf_.assign(static_cast<size_t>(std::max(1.f, maxSeconds) * sr_), 0.f);
    recFrames_ = 0;
    recTrack_ = t;
    recState_.store(kRecArmed);
    return true;
}

void Engine::cancelRecord() {
    int expected = kRecArmed;
    recState_.compare_exchange_strong(expected, kRecIdle);
    expected = kRecDone;
    recState_.compare_exchange_strong(expected, kRecIdle);
}

std::unique_ptr<SampleBuffer> Engine::takeRecording() {
    if (recState_.load(std::memory_order_acquire) != kRecDone) return nullptr;
    auto buf = std::make_unique<SampleBuffer>();
    buf->sampleRate = sr_;
    buf->l.assign(recBuf_.begin(), recBuf_.begin() + static_cast<long>(recFrames_));
    recBuf_.clear();
    recBuf_.shrink_to_fit();
    recState_.store(kRecIdle);
    return buf;
}

void Engine::pushInput(const float* data, int frames, int channels) {
    if (channels <= 0) return;
    for (int i = 0; i < frames; ++i) {
        float s = 0.f;
        for (int c = 0; c < channels; ++c) s += data[i * channels + c];
        if (!input_.push(s / channels)) break;
    }
}

void Engine::readPeaks(float* out) {
    for (int i = 0; i < 2 * (kMaxTracks + 1); ++i) out[i] = peaks_[i].exchange(0.f);
}

// ---------------------------------------------------------------------------------------
// Audio thread
// ---------------------------------------------------------------------------------------

void Engine::instNoteOn(Track& tr, int pitch, float vel) {
    switch (tr.lastType) {
        case kSynth: tr.synth.noteOn(pitch, vel); break;
        case kDrums: tr.drums.noteOn(pitch, vel); break;
        case kSampler: tr.sampler.noteOn(pitch, vel); break;
        default: break;
    }
}

void Engine::instNoteOff(Track& tr, int pitch) {
    switch (tr.lastType) {
        case kSynth: tr.synth.noteOff(pitch); break;
        case kSampler: tr.sampler.noteOff(pitch); break;
        default: break;
    }
}

void Engine::instRelease(Track& tr) {
    tr.synth.releaseAll();
    tr.sampler.releaseAll();
}

void Engine::instReset(Track& tr, int type) {
    switch (type) {
        case kSynth: tr.synth.reset(); break;
        case kDrums: tr.drums.reset(); break;
        case kSampler: tr.sampler.reset(); break;
        default: break;
    }
}

void Engine::triggerNote(Track& tr, int pitch, float vel, double offTick) {
    for (int i = 0; i < tr.numOffs; ++i) {
        if (tr.offs[i].pitch == pitch) {  // retrigger: end the previous note first
            instNoteOff(tr, pitch);
            tr.offs[i] = tr.offs[--tr.numOffs];
            break;
        }
    }
    instNoteOn(tr, pitch, vel);
    if (tr.numOffs < kMaxOffs) tr.offs[tr.numOffs++] = {pitch, offTick};
}

void Engine::stopSequencedNotes() {
    for (auto& tr : tracks_) {
        instRelease(tr);
        tr.numOffs = 0;
    }
}

void Engine::drainCommands() {
    Command c;
    while (commands_.pop(c)) {
        switch (c.type) {
            case kCmdNoteOn: {
                Track& tr = tracks_[c.track];
                if (tr.lastType < 0) tr.lastType = tr.type.load();
                instNoteOn(tr, c.pitch, c.vel);
                break;
            }
            case kCmdNoteOff: instNoteOff(tracks_[c.track], c.pitch); break;
            case kCmdRewind:
                stopSequencedNotes();
                pos_ = 0.0;
                absTick_ = 0.0;
                break;
        }
    }
}

void Engine::render(float* out, int frames) {
    drainCommands();
    int done = 0;
    while (done < frames) {
        const int n = std::min(kBlock, frames - done);
        renderBlock(out + 2 * done, n);
        done += n;
    }
    posPublished_.store(pos_, std::memory_order_relaxed);
    epoch_.fetch_add(1, std::memory_order_release);
}

void Engine::renderBlock(float* out, int n) {
    float L[kBlock] = {}, R[kBlock] = {}, sL[kBlock] = {}, sR[kBlock] = {};
    float tL[kBlock], tR[kBlock];
    float p[kMaxParams];

    const double spt = sr_ * 60.0 / (bpm_.load() * kPPQ);  // samples per tick
    const int loopLen = loopTicks_.load();
    const bool playing = playing_.load();
    const int nt = numTracks_.load();
    const double blockTicks = n / spt;

    if (playing != wasPlaying_) {
        if (!playing) {
            stopSequencedNotes();
            if (recState_.load() == kRecRecording) recState_.store(kRecDone, std::memory_order_release);
        }
        wasPlaying_ = playing;
    }
    if (pos_ >= loopLen) pos_ = std::fmod(pos_, static_cast<double>(loopLen));

    bool anySolo = false;
    for (int t = 0; t < nt; ++t) anySolo |= tracks_[t].solo.load(std::memory_order_relaxed);

    // Track type changes and tracks that went away.
    for (int t = 0; t < kMaxTracks; ++t) {
        Track& tr = tracks_[t];
        const int type = t < nt ? tr.type.load(std::memory_order_relaxed) : -1;
        if (type != tr.lastType) {
            instReset(tr, tr.lastType);
            tr.numOffs = 0;
            tr.lastType = type;
        }
    }

    const double t0 = pos_;
    const double clipStartFrame = t0 * spt;  // audio clips play loop-aligned
    bool wrapped = false;

    if (playing) {
        if (recState_.load() == kRecArmed && t0 == 0.0) {
            float dump;
            while (input_.pop(dump)) {}
            recFrames_ = 0;
            recState_.store(kRecRecording);
        }

        const double absEnd = absTick_ + blockTicks;
        const double tEnd = t0 + blockTicks;
        const float swingTicks = swing_.load() * (kPPQ / 4);

        for (int t = 0; t < nt; ++t) {
            Track& tr = tracks_[t];
            // Note offs due in this block.
            for (int i = 0; i < tr.numOffs;) {
                if (tr.offs[i].tick < absEnd) {
                    instNoteOff(tr, tr.offs[i].pitch);
                    tr.offs[i] = tr.offs[--tr.numOffs];
                } else {
                    ++i;
                }
            }
            if (tr.lastType == kAudio) continue;
            NoteList* nl = tr.notes.load(std::memory_order_acquire);
            if (!nl) continue;
            for (const NoteEvent& e : nl->notes) {
                if (e.start >= loopLen) break;
                double s = e.start;
                if (e.start % (kPPQ / 2) == kPPQ / 4) s += swingTicks;  // off-beat 16ths
                double delta = -1.0;
                if (s >= t0 && s < tEnd) delta = s - t0;
                else if (tEnd > loopLen && s < tEnd - loopLen) delta = s + loopLen - t0;
                if (delta >= 0.0)
                    triggerNote(tr, e.pitch, e.velocity, absTick_ + delta + std::max(1, e.length));
            }
        }

        if (metronome_.load()) {
            double beat = std::ceil(t0 / kPPQ) * kPPQ;
            bool hit = beat < loopLen && beat < tEnd;
            if (!hit && tEnd > loopLen) { beat = 0.0; hit = true; }  // wraps to loop start
            if (hit) {
                clickT_ = 0.f;
                clickPhase_ = 0.f;
                clickFreq_ = (static_cast<int>(beat) % (4 * kPPQ) == 0) ? 1600.f : 1000.f;
            }
        }

        pos_ = tEnd;
        absTick_ = absEnd;
        if (pos_ >= loopLen) {
            pos_ -= loopLen;
            wrapped = true;
        }
    }

    // Render tracks.
    for (int t = 0; t < nt; ++t) {
        Track& tr = tracks_[t];
        for (int i = 0; i < kMaxParams; ++i) p[i] = tr.params[i].load(std::memory_order_relaxed);
        std::memset(tL, 0, sizeof(float) * n);
        std::memset(tR, 0, sizeof(float) * n);
        switch (tr.lastType) {
            case kSynth: tr.synth.render(tL, tR, n, p); break;
            case kDrums: tr.drums.render(tL, tR, n, p); break;
            case kSampler:
                tr.sampler.render(tL, tR, n, p, tr.sample.load(std::memory_order_acquire));
                break;
            case kAudio: {
                const SampleBuffer* clip = tr.sample.load(std::memory_order_acquire);
                if (!playing || !clip || clip->frames() < 2) break;
                const double ratio = clip->sampleRate / sr_;
                const float* cl = clip->l.data();
                const float* cr = clip->stereo() ? clip->r.data() : cl;
                const size_t last = clip->frames() - 1;
                const float g = p[audiop::Level];
                for (int i = 0; i < n; ++i) {
                    const double src = (clipStartFrame + i) * ratio;
                    const size_t idx = static_cast<size_t>(src);
                    if (idx >= last) break;
                    const float fr = static_cast<float>(src - idx);
                    tL[i] = (cl[idx] + (cl[idx + 1] - cl[idx]) * fr) * g;
                    tR[i] = (cr[idx] + (cr[idx + 1] - cr[idx]) * fr) * g;
                }
                break;
            }
            default: break;
        }

        const bool audible = !tr.mute.load(std::memory_order_relaxed) &&
                             (!anySolo || tr.solo.load(std::memory_order_relaxed));
        const float vol = tr.volume.load(std::memory_order_relaxed);
        const float gain = audible ? vol * vol * 1.5f : 0.f;
        const float angle = (tr.pan.load(std::memory_order_relaxed) + 1.f) * kPi * 0.25f;
        const float gl = std::cos(angle) * 1.41421356f * gain;
        const float gr = std::sin(angle) * 1.41421356f * gain;
        const float send = tr.send.load(std::memory_order_relaxed);
        float pkL = 0.f, pkR = 0.f;
        for (int i = 0; i < n; ++i) {
            const float l = tL[i] * gl, r = tR[i] * gr;
            L[i] += l;
            R[i] += r;
            sL[i] += l * send;
            sR[i] += r * send;
            pkL = std::max(pkL, std::fabs(l));
            pkR = std::max(pkR, std::fabs(r));
        }
        if (pkL > peaks_[2 * t].load(std::memory_order_relaxed)) peaks_[2 * t].store(pkL);
        if (pkR > peaks_[2 * t + 1].load(std::memory_order_relaxed)) peaks_[2 * t + 1].store(pkR);
    }

    // Tempo-synced ping-pong delay on the send bus.
    {
        static constexpr float kDivBeats[6] = {0.25f, 0.5f, 0.75f, 1.f, 1.5f, 2.f};
        const size_t size = delayL_.size();
        const size_t d = std::clamp<size_t>(
            static_cast<size_t>(kDivBeats[delayDiv_.load()] * kPPQ * spt), 1, size - 1);
        const float fb = delayFb_.load();
        const float damp = 1.f - 0.85f * delayDamp_.load();
        for (int i = 0; i < n; ++i) {
            const size_t r = (delayW_ + size - d) % size;
            const float dl = delayL_[r], dr = delayR_[r];
            delayLpL_ += (dl - delayLpL_) * damp;
            delayLpR_ += (dr - delayLpR_) * damp;
            delayL_[delayW_] = (sL[i] + sR[i]) * 0.5f + fb * delayLpR_;
            delayR_[delayW_] = fb * delayLpL_;
            delayW_ = (delayW_ + 1) % size;
            L[i] += dl;
            R[i] += dr;
        }
    }

    // Metronome click.
    if (clickT_ < 0.03f) {
        for (int i = 0; i < n && clickT_ < 0.03f; ++i) {
            const float c = std::sin(kTwoPi * clickPhase_) * std::exp(-clickT_ * 150.f) * 0.35f;
            clickPhase_ += clickFreq_ / sr_;
            clickT_ += 1.f / sr_;
            L[i] += c;
            R[i] += c;
        }
    }

    // Recording (input drained in sync with output blocks).
    if (recState_.load(std::memory_order_relaxed) == kRecRecording) {
        float s;
        int got = 0;
        while (got < n && input_.pop(s)) {
            if (recFrames_ < recBuf_.size()) recBuf_[recFrames_++] = s;
            ++got;
        }
        if (wrapped || recFrames_ >= recBuf_.size())
            recState_.store(kRecDone, std::memory_order_release);
    } else if (wrapped && recState_.load(std::memory_order_relaxed) == kRecArmed) {
        float dump;
        while (input_.pop(dump)) {}
        recFrames_ = 0;
        recState_.store(kRecRecording);
    }

    // Master.
    const float mv = master_.load(std::memory_order_relaxed);
    float pkL = 0.f, pkR = 0.f;
    for (int i = 0; i < n; ++i) {
        const float l = softClip(L[i] * mv), r = softClip(R[i] * mv);
        out[2 * i] = l;
        out[2 * i + 1] = r;
        pkL = std::max(pkL, std::fabs(l));
        pkR = std::max(pkR, std::fabs(r));
    }
    auto& mL = peaks_[2 * kMaxTracks];
    auto& mR = peaks_[2 * kMaxTracks + 1];
    if (pkL > mL.load(std::memory_order_relaxed)) mL.store(pkL);
    if (pkR > mR.load(std::memory_order_relaxed)) mR.store(pkR);
}

void Engine::resetAll() {
    Command c;
    while (commands_.pop(c)) {}
    for (auto& tr : tracks_) {
        tr.synth.reset();
        tr.drums.reset();
        tr.sampler.reset();
        tr.numOffs = 0;
    }
    std::fill(delayL_.begin(), delayL_.end(), 0.f);
    std::fill(delayR_.begin(), delayR_.end(), 0.f);
    delayLpL_ = delayLpR_ = 0.f;
    clickT_ = 1.f;
    pos_ = absTick_ = 0.0;
    posPublished_.store(0.0);
    wasPlaying_ = false;
}

bool Engine::renderOffline(std::vector<float>& out, int loops, float tailSeconds) {
    offline_.store(true);
    const bool metro = metronome_.exchange(false);
    const bool wasPlaying = playing_.exchange(false);
    resetAll();
    const double spt = sr_ * 60.0 / (bpm_.load() * kPPQ);
    const size_t body = static_cast<size_t>(std::max(1, loops) * loopTicks_.load() * spt);
    const size_t tail = static_cast<size_t>(std::max(0.f, tailSeconds) * sr_);
    out.assign((body + tail) * 2, 0.f);
    playing_.store(true);
    size_t done = 0;
    while (done < body + tail) {
        if (done >= body) playing_.store(false);
        const size_t limit = done < body ? body : body + tail;
        const int n = static_cast<int>(std::min<size_t>(512, limit - done));
        render(out.data() + 2 * done, n);
        done += n;
    }
    playing_.store(false);
    resetAll();
    metronome_.store(metro);
    (void)wasPlaying;  // transport stays stopped after a bounce
    offline_.store(false);
    return true;
}

bool Engine::exportWav(const std::string& path, int loops, float tailSeconds) {
    std::vector<float> buf;
    renderOffline(buf, loops, tailSeconds);
    return writeWavFile(path, buf.data(), buf.size() / 2, 2, static_cast<int>(sr_));
}

}  // namespace tv
