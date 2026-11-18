package com.tapevibe.audio

/** Raw JNI surface of libtapevibe.so (see app/src/main/cpp/android/jni_bridge.cpp). */
object NativeBridge {
    init {
        System.loadLibrary("tapevibe")
    }

    @JvmStatic external fun create(): Boolean
    @JvmStatic external fun destroy()
    @JvmStatic external fun startAudio(): Boolean
    @JvmStatic external fun stopAudio()
    @JvmStatic external fun latencyMs(): Int
    @JvmStatic external fun sampleRate(): Float

    @JvmStatic external fun setPlaying(playing: Boolean)
    @JvmStatic external fun rewind()
    @JvmStatic external fun positionTicks(): Double
    @JvmStatic external fun setBpm(bpm: Float)
    @JvmStatic external fun setSwing(swing: Float)
    @JvmStatic external fun setLoopTicks(ticks: Int)
    @JvmStatic external fun setMetronome(on: Boolean)
    @JvmStatic external fun setMasterVolume(volume: Float)
    @JvmStatic external fun setDelay(division: Int, feedback: Float, damping: Float)

    @JvmStatic external fun setTrackCount(count: Int)
    @JvmStatic external fun setTrackType(track: Int, type: Int)
    @JvmStatic external fun setTrackParam(track: Int, index: Int, value: Float)
    @JvmStatic external fun setTrackMixer(track: Int, volume: Float, pan: Float, send: Float, mute: Boolean, solo: Boolean)
    @JvmStatic external fun setTrackNotes(track: Int, packed: IntArray, velocities: FloatArray)
    @JvmStatic external fun loadTrackSample(track: Int, path: String): Boolean
    @JvmStatic external fun clearTrackSample(track: Int)
    @JvmStatic external fun samplePeaks(track: Int, buckets: Int): FloatArray
    @JvmStatic external fun sampleSeconds(track: Int): Float

    @JvmStatic external fun noteOn(track: Int, pitch: Int, velocity: Float)
    @JvmStatic external fun noteOff(track: Int, pitch: Int)

    @JvmStatic external fun armRecord(track: Int, maxSeconds: Float): Boolean
    @JvmStatic external fun cancelRecord()
    @JvmStatic external fun recordState(): Int
    @JvmStatic external fun recordTrack(): Int
    @JvmStatic external fun finishRecording(path: String): Boolean

    @JvmStatic external fun readPeaks(out: FloatArray)
    @JvmStatic external fun collectGarbage()
    @JvmStatic external fun exportWav(path: String, loops: Int, tailSeconds: Float): Boolean
}

class NativeAudioEngine : AudioEngine {
    private var created = false

    override fun start(): Boolean {
        if (!created) {
            created = true
            return NativeBridge.create()
        }
        return NativeBridge.startAudio()
    }

    override fun stop() = NativeBridge.stopAudio()
    override fun latencyMs() = NativeBridge.latencyMs()
    override fun setPlaying(playing: Boolean) = NativeBridge.setPlaying(playing)
    override fun rewind() = NativeBridge.rewind()
    override fun positionTicks() = NativeBridge.positionTicks()
    override fun setBpm(bpm: Float) = NativeBridge.setBpm(bpm)
    override fun setSwing(swing: Float) = NativeBridge.setSwing(swing)
    override fun setLoopTicks(ticks: Int) = NativeBridge.setLoopTicks(ticks)
    override fun setMetronome(on: Boolean) = NativeBridge.setMetronome(on)
    override fun setMasterVolume(volume: Float) = NativeBridge.setMasterVolume(volume)
    override fun setDelay(division: Int, feedback: Float, damping: Float) =
        NativeBridge.setDelay(division, feedback, damping)

    override fun setTrackCount(count: Int) = NativeBridge.setTrackCount(count)
    override fun setTrackType(track: Int, type: Int) = NativeBridge.setTrackType(track, type)
    override fun setTrackParam(track: Int, index: Int, value: Float) = NativeBridge.setTrackParam(track, index, value)
    override fun setTrackMixer(track: Int, volume: Float, pan: Float, send: Float, mute: Boolean, solo: Boolean) =
        NativeBridge.setTrackMixer(track, volume, pan, send, mute, solo)

    override fun setTrackNotes(track: Int, notes: List<com.tapevibe.model.Note>) {
        val packed = IntArray(notes.size * 3)
        val vels = FloatArray(notes.size)
        notes.forEachIndexed { i, n ->
            packed[3 * i] = n.start
            packed[3 * i + 1] = n.length
            packed[3 * i + 2] = n.pitch
            vels[i] = n.velocity
        }
        NativeBridge.setTrackNotes(track, packed, vels)
    }

    override fun loadTrackSample(track: Int, path: String) = NativeBridge.loadTrackSample(track, path)
    override fun clearTrackSample(track: Int) = NativeBridge.clearTrackSample(track)
    override fun samplePeaks(track: Int, buckets: Int): FloatArray = NativeBridge.samplePeaks(track, buckets)
    override fun sampleSeconds(track: Int) = NativeBridge.sampleSeconds(track)
    override fun noteOn(track: Int, pitch: Int, velocity: Float) = NativeBridge.noteOn(track, pitch, velocity)
    override fun noteOff(track: Int, pitch: Int) = NativeBridge.noteOff(track, pitch)
    override fun armRecord(track: Int, maxSeconds: Float) = NativeBridge.armRecord(track, maxSeconds)
    override fun cancelRecord() = NativeBridge.cancelRecord()
    override fun recordState() = RecordState.of(NativeBridge.recordState())
    override fun finishRecording(path: String) = NativeBridge.finishRecording(path)
    override fun readPeaks(out: FloatArray) {
        NativeBridge.readPeaks(out)
        NativeBridge.collectGarbage()
    }
    override fun exportWav(path: String, loops: Int, tailSeconds: Float) =
        NativeBridge.exportWav(path, loops, tailSeconds)
}
