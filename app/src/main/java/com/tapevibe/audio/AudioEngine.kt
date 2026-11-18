package com.tapevibe.audio

import com.tapevibe.model.Note

/** Control surface of the audio engine; implemented natively and by fakes in tests. */
interface AudioEngine {
    fun start(): Boolean
    fun stop()
    fun latencyMs(): Int

    fun setPlaying(playing: Boolean)
    fun rewind()
    fun positionTicks(): Double
    fun setBpm(bpm: Float)
    fun setSwing(swing: Float)
    fun setLoopTicks(ticks: Int)
    fun setMetronome(on: Boolean)
    fun setMasterVolume(volume: Float)
    fun setDelay(division: Int, feedback: Float, damping: Float)

    fun setTrackCount(count: Int)
    fun setTrackType(track: Int, type: Int)
    fun setTrackParam(track: Int, index: Int, value: Float)
    fun setTrackMixer(track: Int, volume: Float, pan: Float, send: Float, mute: Boolean, solo: Boolean)
    fun setTrackNotes(track: Int, notes: List<Note>)
    fun loadTrackSample(track: Int, path: String): Boolean
    fun clearTrackSample(track: Int)
    fun samplePeaks(track: Int, buckets: Int): FloatArray
    fun sampleSeconds(track: Int): Float

    fun noteOn(track: Int, pitch: Int, velocity: Float)
    fun noteOff(track: Int, pitch: Int)

    fun armRecord(track: Int, maxSeconds: Float): Boolean
    fun cancelRecord()
    fun recordState(): RecordState
    fun finishRecording(path: String): Boolean

    /** Fills [out] (size 2 * (MAX_TRACKS + 1)) with peaks since the last call. */
    fun readPeaks(out: FloatArray)
    fun exportWav(path: String, loops: Int, tailSeconds: Float): Boolean
}

enum class RecordState { IDLE, ARMED, RECORDING, DONE;
    companion object { fun of(v: Int) = entries.getOrElse(v) { IDLE } }
}
