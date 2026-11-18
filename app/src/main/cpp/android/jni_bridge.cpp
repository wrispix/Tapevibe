#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "AudioIO.h"
#include "Engine.h"

using tv::AudioIO;
using tv::Engine;

namespace {
Engine* g_engine = nullptr;
AudioIO* g_io = nullptr;

std::string str(JNIEnv* env, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

#define JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_tapevibe_audio_NativeBridge_##name

JNI(jboolean, create)(JNIEnv*, jclass) {
    if (!g_engine) {
        g_engine = new Engine();
        g_io = new AudioIO(*g_engine);
    }
    return g_io->start();
}

JNI(void, destroy)(JNIEnv*, jclass) {
    delete g_io;
    delete g_engine;
    g_io = nullptr;
    g_engine = nullptr;
}

JNI(jboolean, startAudio)(JNIEnv*, jclass) { return g_io && g_io->start(); }
JNI(void, stopAudio)(JNIEnv*, jclass) { if (g_io) g_io->stop(); }
JNI(jint, latencyMs)(JNIEnv*, jclass) { return g_io ? g_io->outputLatencyMs() : -1; }
JNI(jfloat, sampleRate)(JNIEnv*, jclass) { return g_engine ? g_engine->sampleRate() : 0.f; }

JNI(void, setPlaying)(JNIEnv*, jclass, jboolean p) { if (g_engine) g_engine->setPlaying(p); }
JNI(void, rewind)(JNIEnv*, jclass) { if (g_engine) g_engine->rewind(); }
JNI(jdouble, positionTicks)(JNIEnv*, jclass) { return g_engine ? g_engine->positionTicks() : 0.0; }
JNI(void, setBpm)(JNIEnv*, jclass, jfloat v) { if (g_engine) g_engine->setBpm(v); }
JNI(void, setSwing)(JNIEnv*, jclass, jfloat v) { if (g_engine) g_engine->setSwing(v); }
JNI(void, setLoopTicks)(JNIEnv*, jclass, jint v) { if (g_engine) g_engine->setLoopTicks(v); }
JNI(void, setMetronome)(JNIEnv*, jclass, jboolean v) { if (g_engine) g_engine->setMetronome(v); }
JNI(void, setMasterVolume)(JNIEnv*, jclass, jfloat v) { if (g_engine) g_engine->setMasterVolume(v); }
JNI(void, setDelay)(JNIEnv*, jclass, jint div, jfloat fb, jfloat damp) {
    if (g_engine) g_engine->setDelay(div, fb, damp);
}

JNI(void, setTrackCount)(JNIEnv*, jclass, jint n) { if (g_engine) g_engine->setTrackCount(n); }
JNI(void, setTrackType)(JNIEnv*, jclass, jint t, jint type) {
    if (g_engine) g_engine->setTrackType(t, type);
}
JNI(void, setTrackParam)(JNIEnv*, jclass, jint t, jint i, jfloat v) {
    if (g_engine) g_engine->setTrackParam(t, i, v);
}
JNI(void, setTrackMixer)(JNIEnv*, jclass, jint t, jfloat vol, jfloat pan, jfloat send, jboolean mute,
                         jboolean solo) {
    if (g_engine) g_engine->setTrackMixer(t, vol, pan, send, mute, solo);
}

// notes: packed [start, length, pitch] triples; velocities parallel.
JNI(void, setTrackNotes)(JNIEnv* env, jclass, jint t, jintArray packed, jfloatArray vels) {
    if (!g_engine) return;
    const jsize count = env->GetArrayLength(vels);
    if (env->GetArrayLength(packed) < count * 3) return;
    std::vector<tv::NoteEvent> notes(count);
    jint* p = env->GetIntArrayElements(packed, nullptr);
    jfloat* v = env->GetFloatArrayElements(vels, nullptr);
    for (jsize i = 0; i < count; ++i) notes[i] = {p[3 * i], p[3 * i + 1], p[3 * i + 2], v[i]};
    env->ReleaseIntArrayElements(packed, p, JNI_ABORT);
    env->ReleaseFloatArrayElements(vels, v, JNI_ABORT);
    g_engine->setTrackNotes(t, std::move(notes));
    g_engine->collectGarbage();
}

JNI(jboolean, loadTrackSample)(JNIEnv* env, jclass, jint t, jstring path) {
    if (!g_engine) return false;
    auto buf = tv::readWavFile(str(env, path));
    if (!buf) return false;
    g_engine->setTrackSample(t, std::move(buf));
    g_engine->collectGarbage();
    return true;
}

JNI(void, clearTrackSample)(JNIEnv*, jclass, jint t) {
    if (!g_engine) return;
    g_engine->setTrackSample(t, nullptr);
    g_engine->collectGarbage();
}

// Peak envelope of a track's sample for waveform display (max abs per bucket).
JNI(jfloatArray, samplePeaks)(JNIEnv* env, jclass, jint t, jint buckets) {
    jfloatArray arr = env->NewFloatArray(buckets);
    if (!g_engine || buckets <= 0) return arr;
    const tv::SampleBuffer* s = g_engine->trackSample(t);
    if (!s || s->frames() == 0) return arr;
    std::vector<float> out(buckets, 0.f);
    const size_t frames = s->frames();
    for (size_t i = 0; i < frames; ++i) {
        const size_t b = i * buckets / frames;
        float v = std::fabs(s->l[i]);
        if (s->stereo()) v = std::max(v, std::fabs(s->r[i]));
        out[b] = std::max(out[b], v);
    }
    env->SetFloatArrayRegion(arr, 0, buckets, out.data());
    return arr;
}

JNI(jfloat, sampleSeconds)(JNIEnv*, jclass, jint t) {
    if (!g_engine) return 0.f;
    const tv::SampleBuffer* s = g_engine->trackSample(t);
    return s ? s->frames() / s->sampleRate : 0.f;
}

JNI(void, noteOn)(JNIEnv*, jclass, jint t, jint pitch, jfloat vel) {
    if (g_engine) g_engine->noteOn(t, pitch, vel);
}
JNI(void, noteOff)(JNIEnv*, jclass, jint t, jint pitch) { if (g_engine) g_engine->noteOff(t, pitch); }

JNI(jboolean, armRecord)(JNIEnv*, jclass, jint t, jfloat maxSeconds) {
    if (!g_engine || !g_io) return false;
    if (!g_io->startInput()) return false;
    if (!g_engine->armRecord(t, maxSeconds)) {
        g_io->stopInput();
        return false;
    }
    return true;
}

JNI(void, cancelRecord)(JNIEnv*, jclass) {
    if (!g_engine) return;
    g_engine->cancelRecord();
    if (g_engine->recordState() == tv::kRecIdle && g_io) g_io->stopInput();
}

JNI(jint, recordState)(JNIEnv*, jclass) { return g_engine ? g_engine->recordState() : 0; }
JNI(jint, recordTrack)(JNIEnv*, jclass) { return g_engine ? g_engine->recordTrack() : -1; }

// Moves a finished recording into its track and writes it to `path`.
JNI(jboolean, finishRecording)(JNIEnv* env, jclass, jstring path) {
    if (!g_engine) return false;
    auto rec = g_engine->takeRecording();
    if (g_io) g_io->stopInput();
    if (!rec) return false;
    const bool ok = tv::writeWavFile(str(env, path), *rec);
    g_engine->setTrackSample(g_engine->recordTrack(), std::move(rec));
    g_engine->collectGarbage();
    return ok;
}

JNI(void, readPeaks)(JNIEnv* env, jclass, jfloatArray out) {
    if (!g_engine) return;
    float peaks[2 * (tv::kMaxTracks + 1)];
    g_engine->readPeaks(peaks);
    const jsize n = std::min<jsize>(env->GetArrayLength(out), 2 * (tv::kMaxTracks + 1));
    env->SetFloatArrayRegion(out, 0, n, peaks);
}

JNI(void, collectGarbage)(JNIEnv*, jclass) { if (g_engine) g_engine->collectGarbage(); }

// Stops the realtime stream, bounces to WAV, restarts the stream.
JNI(jboolean, exportWav)(JNIEnv* env, jclass, jstring path, jint loops, jfloat tail) {
    if (!g_engine || !g_io) return false;
    const bool wasRunning = g_io->running();
    g_io->stop();
    const bool ok = g_engine->exportWav(str(env, path), loops, tail);
    g_engine->collectGarbage();
    if (wasRunning) g_io->start();
    return ok;
}
