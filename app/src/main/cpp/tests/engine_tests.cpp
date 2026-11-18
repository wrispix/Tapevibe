// Host-side tests for the DSP/sequencer core. No Android dependencies.
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <thread>
#include <vector>

#include "Engine.h"

using namespace tv;

static int g_failures = 0;
#define CHECK(cond)                                                              \
    do {                                                                         \
        if (!(cond)) {                                                           \
            std::fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
            ++g_failures;                                                        \
        }                                                                        \
    } while (0)

static constexpr float kSr = 48000.f;

static double framesPerTick(float bpm) { return kSr * 60.0 / (bpm * kPPQ); }

static std::vector<float> renderFrames(Engine& e, size_t frames, size_t chunk = 192) {
    std::vector<float> out(frames * 2);
    size_t done = 0;
    while (done < frames) {
        int n = static_cast<int>(std::min(chunk, frames - done));
        e.render(out.data() + 2 * done, n);
        done += n;
    }
    return out;
}

static float rms(const std::vector<float>& b, size_t from, size_t to) {
    double acc = 0;
    for (size_t i = from; i < to; ++i) acc += double(b[2 * i]) * b[2 * i];
    return static_cast<float>(std::sqrt(acc / std::max<size_t>(1, to - from)));
}

static bool allFinite(const std::vector<float>& b, float limit) {
    for (float s : b) if (!std::isfinite(s) || std::fabs(s) > limit) return false;
    return true;
}

static void setupEngine(Engine& e) {
    e.setSampleRate(kSr);
    e.setBpm(120.f);
    e.setLoopTicks(4 * kPPQ);
}

static void testSilenceWhenStopped() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackNotes(0, {{0, kPPQ, 60, 1.f}});
    auto out = renderFrames(e, 4800);
    CHECK(rms(out, 0, 4800) == 0.f);
}

static void testDrumTiming() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackType(0, kDrums);
    // Kick on beat 3 only.
    e.setTrackNotes(0, {{2 * kPPQ, 24, kDrumBaseNote, 1.f}});
    e.setPlaying(true);
    const size_t loopFrames = static_cast<size_t>(4 * kPPQ * framesPerTick(120.f));
    auto out = renderFrames(e, loopFrames);
    const size_t hit = static_cast<size_t>(2 * kPPQ * framesPerTick(120.f));
    CHECK(rms(out, 0, hit - 64) < 1e-4f);              // nothing before the kick
    CHECK(rms(out, hit, hit + 2400) > 0.05f);          // kick present right on time
    CHECK(allFinite(out, 1.01f));
}

static void testSynthNoteOnAndOff() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackType(0, kSynth);
    e.setTrackParam(0, synthp::Release, 0.05f);  // short release
    e.setTrackNotes(0, {{0, kPPQ, 57, 1.f}});    // quarter note
    e.setPlaying(true);
    const double fpt = framesPerTick(120.f);
    auto out = renderFrames(e, static_cast<size_t>(4 * kPPQ * fpt));
    CHECK(rms(out, 1000, static_cast<size_t>(kPPQ * fpt)) > 0.01f);
    // well after note off + release the voice must be silent
    CHECK(rms(out, static_cast<size_t>(3 * kPPQ * fpt), static_cast<size_t>(4 * kPPQ * fpt) - 64) <
          1e-4f);
    CHECK(allFinite(out, 1.01f));
}

static void testLoopRetriggers() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackType(0, kDrums);
    e.setTrackNotes(0, {{0, 24, kDrumBaseNote + 1, 1.f}});  // snare at loop start
    e.setPlaying(true);
    const size_t loopFrames = static_cast<size_t>(4 * kPPQ * framesPerTick(120.f));
    auto out = renderFrames(e, loopFrames * 2 + 4800);
    CHECK(rms(out, loopFrames, loopFrames + 2400) > 0.02f);
    CHECK(rms(out, 2 * loopFrames, 2 * loopFrames + 2400) > 0.02f);
}

static void testMuteSolo() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(2);
    e.setTrackType(0, kDrums);
    e.setTrackType(1, kDrums);
    e.setTrackNotes(0, {{0, 24, kDrumBaseNote, 1.f}});
    e.setTrackNotes(1, {{0, 24, kDrumBaseNote, 1.f}});
    e.setTrackMixer(0, 0.8f, 0.f, 0.f, false, true);  // solo track 0
    e.setTrackMixer(1, 0.8f, 0.f, 0.f, false, false);
    e.setPlaying(true);
    renderFrames(e, 2400);
    float peaks[2 * (kMaxTracks + 1)];
    e.readPeaks(peaks);
    CHECK(peaks[0] > 0.05f);
    CHECK(peaks[2] == 0.f);  // non-soloed track is silent

    e.setTrackMixer(0, 0.8f, -1.f, 0.f, false, false);  // hard left
    e.setTrackMixer(1, 0.8f, 0.f, 0.f, true, false);    // muted
    e.rewind();
    auto out = renderFrames(e, 2400);
    double l = 0, r = 0;
    for (size_t i = 0; i < 2400; ++i) { l += std::fabs(out[2 * i]); r += std::fabs(out[2 * i + 1]); }
    CHECK(l > 1.0);
    CHECK(r < 1e-3);
}

static void testSamplerPitch() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackType(0, kSampler);
    auto buf = std::make_unique<SampleBuffer>();
    buf->sampleRate = kSr;
    buf->l.resize(24000);
    for (size_t i = 0; i < buf->l.size(); ++i) buf->l[i] = std::sin(kTwoPi * 440.f * i / kSr) * 0.5f;
    e.setTrackSample(0, std::move(buf));
    e.setTrackParam(0, samplerp::RootNote, 60.f / 127.f);
    e.setTrackParam(0, samplerp::Tune, 0.5f);
    e.setTrackParam(0, samplerp::Level, 1.f);
    e.setTrackNotes(0, {{0, 2 * kPPQ, 72, 1.f}});  // one octave up -> 880 Hz, half length
    e.setTrackMixer(0, 0.8f, 0.f, 0.f, false, false);
    e.setPlaying(true);
    auto out = renderFrames(e, 9600);
    // count zero crossings over 0.1 s after attack: 880 Hz -> ~176
    int zc = 0;
    for (size_t i = 2401; i < 7200; ++i) if ((out[2 * (i - 1)] < 0) != (out[2 * i] < 0)) ++zc;
    std::printf("zero crossings: %d\n", zc);
    CHECK(zc > 165 && zc < 185);
}

static void testWavRoundTrip() {
    std::vector<float> data(2000);
    for (size_t i = 0; i < 1000; ++i) {
        data[2 * i] = std::sin(i * 0.05f) * 0.7f;
        data[2 * i + 1] = -data[2 * i];
    }
    const std::string path = "/tmp/tapevibe_test.wav";
    CHECK(writeWavFile(path, data.data(), 1000, 2, 44100));
    std::string err;
    auto buf = readWavFile(path, &err);
    CHECK(buf != nullptr);
    if (buf) {
        CHECK(buf->frames() == 1000);
        CHECK(buf->stereo());
        CHECK(buf->sampleRate == 44100.f);
        CHECK(std::fabs(buf->l[123] - data[246]) < 1e-3f);
        CHECK(std::fabs(buf->r[123] - data[247]) < 1e-3f);
    }
    std::remove(path.c_str());
    const uint8_t junk[16] = {'n', 'o', 'p', 'e'};
    CHECK(decodeWav(junk, sizeof junk, &err) == nullptr);
}

static void testRecordingCapturesOneLoop() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackType(0, kAudio);
    e.setTrackParam(0, audiop::Level, 0.8f);
    e.setLoopTicks(2 * kPPQ);  // 1 s at 120 bpm
    CHECK(e.armRecord(0, 10.f));
    CHECK(e.recordState() == kRecArmed);
    e.setPlaying(true);
    std::vector<float> in(192), out(384);
    size_t phase = 0;
    int guard = 0;
    while (e.recordState() != kRecDone && guard++ < 2000) {
        for (auto& s : in) s = std::sin(kTwoPi * 220.f * (phase++) / kSr) * 0.5f;
        e.pushInput(in.data(), 192, 1);
        e.render(out.data(), 192);
    }
    CHECK(e.recordState() == kRecDone);
    auto rec = e.takeRecording();
    CHECK(rec != nullptr);
    if (rec) {
        CHECK(std::abs(static_cast<long>(rec->frames()) - 48000) < 400);
        e.setTrackSample(0, std::move(rec));
    }
    CHECK(e.recordState() == kRecIdle);
    // clip now plays back loop-aligned
    e.rewind();
    auto play = renderFrames(e, 9600);
    CHECK(rms(play, 0, 9600) > 0.1f);
}

static void testConcurrentEditsAndGc() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(2);
    e.setTrackType(0, kSynth);
    e.setTrackType(1, kDrums);
    e.setStreamActive(true);
    e.setPlaying(true);
    std::atomic<bool> stop{false};
    std::thread audio([&] {
        std::vector<float> out(512);
        while (!stop.load()) e.render(out.data(), 256);
    });
    for (int i = 0; i < 3000; ++i) {
        std::vector<NoteEvent> notes;
        for (int k = 0; k < 16; ++k) notes.push_back({k * 24, 20, 48 + (i + k) % 24, 0.8f});
        e.setTrackNotes(i % 2, notes);
        e.setTrackParam(0, synthp::Cutoff, (i % 100) / 100.f);
        if (i % 10 == 0) e.noteOn(0, 60, 1.f);
        if (i % 10 == 5) e.noteOff(0, 60);
        e.collectGarbage();
    }
    stop.store(true);
    audio.join();
    e.setStreamActive(false);
    e.collectGarbage();
    CHECK(true);  // reaching here without a crash (run under ASan/TSan for rigor)
}

static void testExport() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackType(0, kDrums);
    e.setTrackNotes(0, {{0, 24, kDrumBaseNote, 1.f}, {kPPQ, 24, kDrumBaseNote + 1, 1.f}});
    std::vector<float> buf;
    e.renderOffline(buf, 2, 1.f);
    const size_t frames = buf.size() / 2;
    CHECK(frames == static_cast<size_t>(2 * 4 * kPPQ * framesPerTick(120.f)) + 48000);
    CHECK(rms(buf, 0, 4800) > 0.05f);
    CHECK(allFinite(buf, 1.01f));
    CHECK(!e.playing());
    const std::string path = "/tmp/tapevibe_export.wav";
    CHECK(e.exportWav(path, 1, 0.5f));
    auto back = readWavFile(path);
    CHECK(back && back->frames() == static_cast<size_t>(4 * kPPQ * framesPerTick(120.f)) + 24000);
    std::remove(path.c_str());
}

static void testDelayAndSwingStable() {
    Engine e;
    setupEngine(e);
    e.setTrackCount(1);
    e.setTrackType(0, kSynth);
    e.setSwing(0.5f);
    e.setDelay(2, 0.92f, 0.f);
    e.setMetronome(true);
    std::vector<NoteEvent> notes;
    for (int k = 0; k < 16; ++k) notes.push_back({k * 24, 24, 36 + k * 3, 1.f});
    e.setTrackNotes(0, notes);
    e.setTrackMixer(0, 1.f, 0.f, 1.f, false, false);
    e.setTrackParam(0, synthp::Resonance, 1.f);
    e.setPlaying(true);
    auto out = renderFrames(e, 48000 * 6);
    CHECK(allFinite(out, 1.01f));
}

int main() {
    testSilenceWhenStopped();
    testDrumTiming();
    testSynthNoteOnAndOff();
    testLoopRetriggers();
    testMuteSolo();
    testSamplerPitch();
    testWavRoundTrip();
    testRecordingCapturesOneLoop();
    testConcurrentEditsAndGc();
    testExport();
    testDelayAndSwingStable();
    if (g_failures == 0) std::printf("All engine tests passed\n");
    return g_failures == 0 ? 0 : 1;
}
