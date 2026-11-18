package com.tapevibe

import com.tapevibe.audio.AudioEngine
import com.tapevibe.audio.RecordState
import com.tapevibe.model.Note

/** Records engine calls for assertions. */
class FakeEngine : AudioEngine {
    val calls = mutableListOf<String>()
    val notes = mutableMapOf<Int, List<Note>>()
    val params = mutableMapOf<Pair<Int, Int>, Float>()
    val types = mutableMapOf<Int, Int>()
    val samples = mutableMapOf<Int, String?>()
    var trackCountValue = 0
    var playingNow = false
    var bpmValue = 0f
    var loopTicksValue = 0
    var record = RecordState.IDLE

    fun clear() = calls.clear()

    override fun start() = true.also { calls += "start" }
    override fun stop() { calls += "stop" }
    override fun latencyMs() = 10
    override fun setPlaying(playing: Boolean) { playingNow = playing; calls += "play=$playing" }
    override fun rewind() { calls += "rewind" }
    override fun positionTicks() = 0.0
    override fun setBpm(bpm: Float) { bpmValue = bpm; calls += "bpm" }
    override fun setSwing(swing: Float) { calls += "swing" }
    override fun setLoopTicks(ticks: Int) { loopTicksValue = ticks; calls += "loop" }
    override fun setMetronome(on: Boolean) { calls += "metro" }
    override fun setMasterVolume(volume: Float) { calls += "master" }
    override fun setDelay(division: Int, feedback: Float, damping: Float) { calls += "delay" }
    override fun setTrackCount(count: Int) { trackCountValue = count; calls += "count=$count" }
    override fun setTrackType(track: Int, type: Int) { types[track] = type; calls += "type$track" }
    override fun setTrackParam(track: Int, index: Int, value: Float) { params[track to index] = value; calls += "param$track/$index" }
    override fun setTrackMixer(track: Int, volume: Float, pan: Float, send: Float, mute: Boolean, solo: Boolean) { calls += "mixer$track" }
    override fun setTrackNotes(track: Int, notes: List<Note>) { this.notes[track] = notes; calls += "notes$track" }
    override fun loadTrackSample(track: Int, path: String): Boolean { samples[track] = path; calls += "load$track"; return true }
    override fun clearTrackSample(track: Int) { samples[track] = null; calls += "clearSample$track" }
    override fun samplePeaks(track: Int, buckets: Int) = if (samples[track] != null) FloatArray(buckets) { 0.5f } else FloatArray(0)
    override fun sampleSeconds(track: Int) = if (samples[track] != null) 2f else 0f
    override fun noteOn(track: Int, pitch: Int, velocity: Float) { calls += "on$track:$pitch" }
    override fun noteOff(track: Int, pitch: Int) { calls += "off$track:$pitch" }
    override fun armRecord(track: Int, maxSeconds: Float): Boolean { record = RecordState.ARMED; return true }
    override fun cancelRecord() { record = RecordState.IDLE }
    override fun recordState() = record
    override fun finishRecording(path: String): Boolean { java.io.File(path).writeText("RIFF0000WAVE"); record = RecordState.IDLE; return true }
    override fun readPeaks(out: FloatArray) {}
    override fun exportWav(path: String, loops: Int, tailSeconds: Float): Boolean {
        java.io.File(path).writeBytes(ByteArray(64)); calls += "export$loops"; return true
    }
}
