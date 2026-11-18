#pragma once

#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "Common.h"
#include "Instruments.h"
#include "SpscRing.h"
#include "Wav.h"

namespace tv {

struct NoteEvent {
    int start;   // ticks from loop start
    int length;  // ticks
    int pitch;
    float velocity;
};

enum RecordState : int { kRecIdle = 0, kRecArmed = 1, kRecRecording = 2, kRecDone = 3 };

// Real-time audio engine. Threading model:
//  * render() runs on the audio thread and never locks or allocates.
//  * every other public method is called from one control (UI) thread, except pushInput()
//    which belongs to the input-stream thread.
//  * Scalar settings are atomics. Note lists and sample buffers are published by atomic
//    pointer swap; the old object is retired and freed by collectGarbage() once the audio
//    thread has finished at least two callbacks since the swap.
class Engine {
public:
    Engine();
    ~Engine();

    void setSampleRate(float sr);  // only while not rendering
    float sampleRate() const { return sr_; }

    // Transport
    void setPlaying(bool p) { playing_.store(p); }
    bool playing() const { return playing_.load(); }
    void rewind();
    double positionTicks() const { return posPublished_.load(std::memory_order_relaxed); }
    void setBpm(float bpm) { bpm_.store(std::clamp(bpm, 30.f, 300.f)); }
    void setSwing(float s) { swing_.store(std::clamp(s, 0.f, 0.75f)); }
    void setLoopTicks(int t) { loopTicks_.store(std::max(kPPQ, t)); }
    void setMetronome(bool on) { metronome_.store(on); }
    void setMasterVolume(float v) { master_.store(std::clamp(v, 0.f, 1.5f)); }
    // division: 0=1/16 1=1/8 2=dotted 1/8 3=1/4 4=dotted 1/4 5=1/2
    void setDelay(int division, float feedback, float damping);

    // Tracks
    void setTrackCount(int n) { numTracks_.store(std::clamp(n, 0, kMaxTracks)); }
    int trackCount() const { return numTracks_.load(); }
    void setTrackType(int t, int type);
    void setTrackParam(int t, int index, float value);
    void setTrackMixer(int t, float volume, float pan, float send, bool mute, bool solo);
    void setTrackNotes(int t, std::vector<NoteEvent> notes);
    void setTrackSample(int t, std::unique_ptr<SampleBuffer> sample);
    const SampleBuffer* trackSample(int t) const;  // control thread only

    // Live (preview) notes
    void noteOn(int t, int pitch, float velocity);
    void noteOff(int t, int pitch);

    // Recording: arm, then recording starts at the next loop start and captures one loop.
    bool armRecord(int t, float maxSeconds);
    void cancelRecord();
    int recordState() const { return recState_.load(); }
    int recordTrack() const { return recTrack_; }
    std::unique_ptr<SampleBuffer> takeRecording();
    void pushInput(const float* data, int frames, int channels);

    // Peak meters since last read: out[2*t], out[2*t+1] for tracks, then master L/R at
    // index 2*kMaxTracks. `out` must hold 2*(kMaxTracks+1) floats.
    void readPeaks(float* out);

    void render(float* outStereo, int frames);
    void setStreamActive(bool a) { streamActive_.store(a); }
    void collectGarbage(bool force = false);

    // Offline bounce of `loops` passes plus a tail. Audio stream must be stopped.
    bool renderOffline(std::vector<float>& out, int loops, float tailSeconds);
    bool exportWav(const std::string& path, int loops, float tailSeconds);
    void resetAll();  // kill all sound; only while not rendering

private:
    struct NoteList { std::vector<NoteEvent> notes; };
    struct PendingOff { int pitch; double tick; };
    static constexpr int kMaxOffs = 64;

    struct Track {
        std::atomic<int> type{kSynth};
        std::atomic<float> params[kMaxParams];
        std::atomic<float> volume{0.8f}, pan{0.f}, send{0.f};
        std::atomic<bool> mute{false}, solo{false};
        std::atomic<NoteList*> notes{nullptr};
        std::atomic<SampleBuffer*> sample{nullptr};
        // audio thread only
        SubSynth synth;
        DrumKit drums;
        Sampler sampler;
        int lastType = -1;
        PendingOff offs[kMaxOffs];
        int numOffs = 0;
    };

    struct Command { int type, track, pitch; float vel; };
    enum { kCmdNoteOn, kCmdNoteOff, kCmdRewind };

    void pushCommand(const Command& c);
    void drainCommands();
    void renderBlock(float* out, int n);
    void instNoteOn(Track& tr, int pitch, float vel);
    void instNoteOff(Track& tr, int pitch);
    void instRelease(Track& tr);
    void instReset(Track& tr, int type);
    void triggerNote(Track& tr, int pitch, float vel, double offTick);
    void retire(std::function<void()> del);
    void stopSequencedNotes();

    float sr_ = 48000.f;
    std::atomic<bool> playing_{false};
    std::atomic<float> bpm_{120.f}, swing_{0.f}, master_{0.8f};
    std::atomic<int> loopTicks_{4 * kPPQ};
    std::atomic<bool> metronome_{false};
    std::atomic<int> delayDiv_{2};
    std::atomic<float> delayFb_{0.35f}, delayDamp_{0.4f};
    std::atomic<int> numTracks_{0};
    std::atomic<double> posPublished_{0.0};
    std::atomic<bool> streamActive_{false}, offline_{false};
    std::atomic<uint64_t> epoch_{0};

    Track tracks_[kMaxTracks];
    std::atomic<float> peaks_[2 * (kMaxTracks + 1)];

    std::mutex cmdMutex_;
    SpscRing<Command, 256> commands_;

    // audio-thread state
    double pos_ = 0.0;      // ticks within loop
    double absTick_ = 0.0;  // monotonic ticks since play start
    bool wasPlaying_ = false;
    std::vector<float> delayL_, delayR_;
    size_t delayW_ = 0;
    float delayLpL_ = 0.f, delayLpR_ = 0.f;
    float clickPhase_ = 0.f, clickT_ = 1.f, clickFreq_ = 1000.f;

    // recording
    std::atomic<int> recState_{kRecIdle};
    int recTrack_ = -1;
    std::vector<float> recBuf_;
    size_t recFrames_ = 0;
    SpscRing<float, 1 << 16> input_;

    std::mutex gcMutex_;
    std::vector<std::pair<uint64_t, std::function<void()>>> retired_;
};

}  // namespace tv
