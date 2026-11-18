package com.tapevibe

import com.tapevibe.audio.EngineSync
import com.tapevibe.model.DemoProject
import com.tapevibe.model.Note
import com.tapevibe.model.ProjectEdits
import com.tapevibe.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSyncTest {
    private val engine = FakeEngine()
    private val sync = EngineSync(engine) { t -> t.sampleFile?.let { "/samples/$it" } }

    @Test fun firstPushSendsEverything() {
        val p = DemoProject.create()
        sync.push(p)
        assertEquals(3, engine.trackCountValue)
        assertEquals(p.bpm, engine.bpmValue)
        assertEquals(p.loopTicks, engine.loopTicksValue)
        p.tracks.forEachIndexed { i, t ->
            assertEquals(t.type.nativeId, engine.types[i])
            assertEquals(t.notes, engine.notes[i])
            t.params.forEachIndexed { k, v -> assertEquals(v, engine.params[i to k]) }
        }
    }

    @Test fun pushesOnlyDifferences() {
        val p = DemoProject.create()
        sync.push(p)
        engine.clear()
        val id = p.tracks[1].id
        val p2 = ProjectEdits.updateTrack(p, id) { t -> t.copy(params = t.params.toMutableList().also { it[5] = 0.1f }) }
        sync.push(p2)
        assertEquals(listOf("param1/5"), engine.calls)

        engine.clear()
        sync.push(ProjectEdits.updateTrack(p2, id) { it.copy(notes = it.notes + Note(0, 24, 70)) })
        assertEquals(listOf("notes1"), engine.calls)

        engine.clear()
        sync.push(p2.copy(bpm = 99f))  // notes revert + bpm
        assertEquals(listOf("bpm", "notes1"), engine.calls)
    }

    @Test fun deletingATrackResendsShiftedSlots() {
        val p = DemoProject.create()
        sync.push(p)
        engine.clear()
        val p2 = ProjectEdits.removeTrack(p, p.tracks[0].id)
        sync.push(p2)
        assertTrue("count=2" in engine.calls)
        assertEquals(p2.tracks[0].notes, engine.notes[0])
        assertEquals(p2.tracks[1].notes, engine.notes[1])
        assertTrue("mixer0" in engine.calls && "mixer1" in engine.calls)
    }

    @Test fun samplesLoadOnceAndClear() {
        var p = ProjectEdits.addTrack(DemoProject.blank(), TrackType.SAMPLER)
        sync.push(p)
        assertFalse(engine.calls.any { it.startsWith("load") })
        val id = p.tracks[1].id
        p = ProjectEdits.updateTrack(p, id) { it.copy(sampleFile = "a.wav") }
        sync.push(p)
        assertEquals("/samples/a.wav", engine.samples[1])
        assertEquals(listOf(1), sync.changedSampleSlots)
        engine.clear()
        sync.push(p.copy(bpm = 130f))
        assertFalse(engine.calls.any { it.startsWith("load") })
        assertTrue(sync.changedSampleSlots.isEmpty())

        // sample already in the engine (recording) is not reloaded
        sync.markSampleLoaded(1, "/samples/b.wav")
        engine.clear()
        sync.push(ProjectEdits.updateTrack(p, id) { it.copy(sampleFile = "b.wav") })
        assertFalse(engine.calls.any { it.startsWith("load") })

        // removing the track clears its slot
        engine.clear()
        sync.push(ProjectEdits.removeTrack(p, id))
        assertTrue("clearSample1" in engine.calls)
        assertNull(engine.samples[1])
    }
}
