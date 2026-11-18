package com.tapevibe.audio

import com.tapevibe.model.Project
import com.tapevibe.model.Track

/**
 * Pushes the difference between the last pushed [Project] and a new one to the engine.
 * Engine track slots follow list order; when the track occupying a slot changes (insert,
 * delete, reorder) that slot is re-sent in full.
 *
 * Not thread-safe: call from a single thread.
 */
class EngineSync(
    private val engine: AudioEngine,
    /** Absolute path of a track's sample file, or null when it has none. */
    private val samplePath: (Track) -> String?,
) {
    private var last: Project? = null
    private val loadedSample = arrayOfNulls<String>(com.tapevibe.model.MAX_TRACKS)

    /** Slots whose sample changed during the last [push]. */
    var changedSampleSlots: List<Int> = emptyList()
        private set

    fun invalidate() {
        last = null
        loadedSample.fill(null)
    }

    /** Tell the sync that the engine already holds [path] in [slot] (e.g. after recording). */
    fun markSampleLoaded(slot: Int, path: String?) {
        if (slot in loadedSample.indices) loadedSample[slot] = path
    }

    fun push(p: Project) {
        val old = last
        if (old?.bpm != p.bpm) engine.setBpm(p.bpm)
        if (old?.swing != p.swing) engine.setSwing(p.swing)
        if (old?.loopTicks != p.loopTicks) engine.setLoopTicks(p.loopTicks)
        if (old?.metronome != p.metronome) engine.setMetronome(p.metronome)
        if (old?.masterVolume != p.masterVolume) engine.setMasterVolume(p.masterVolume)
        if (old == null || old.delayDivision != p.delayDivision || old.delayFeedback != p.delayFeedback ||
            old.delayDamping != p.delayDamping
        ) engine.setDelay(p.delayDivision, p.delayFeedback, p.delayDamping)
        if (old?.tracks?.size != p.tracks.size) engine.setTrackCount(p.tracks.size)

        val changed = mutableListOf<Int>()
        p.tracks.forEachIndexed { slot, t ->
            val prev = old?.tracks?.getOrNull(slot)?.takeIf { it.id == t.id }
            if (prev?.type != t.type) engine.setTrackType(slot, t.type.nativeId)
            t.params.forEachIndexed { i, v ->
                if (prev == null || prev.type != t.type || prev.params.getOrNull(i) != v) engine.setTrackParam(slot, i, v)
            }
            if (prev == null || prev.volume != t.volume || prev.pan != t.pan || prev.send != t.send ||
                prev.mute != t.mute || prev.solo != t.solo
            ) engine.setTrackMixer(slot, t.volume, t.pan, t.send, t.mute, t.solo)
            if (prev == null || prev.notes != t.notes) engine.setTrackNotes(slot, t.notes)

            val path = samplePath(t)
            if (loadedSample[slot] != path) {
                val ok = if (path != null) engine.loadTrackSample(slot, path) else { engine.clearTrackSample(slot); true }
                loadedSample[slot] = if (ok) path else null
                changed += slot
            }
        }
        for (slot in p.tracks.size until loadedSample.size) {
            if (loadedSample[slot] != null) {
                engine.clearTrackSample(slot)
                loadedSample[slot] = null
            }
        }
        changedSampleSlots = changed
        last = p
    }
}
