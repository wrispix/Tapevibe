package com.tapevibe

import com.tapevibe.data.ProjectStore
import com.tapevibe.model.DRUM_BASE_NOTE
import com.tapevibe.model.DemoProject
import com.tapevibe.model.History
import com.tapevibe.model.InstrumentParams
import com.tapevibe.model.MAX_TRACKS
import com.tapevibe.model.Note
import com.tapevibe.model.PPQ
import com.tapevibe.model.Project
import com.tapevibe.model.ProjectEdits
import com.tapevibe.model.STEP_TICKS
import com.tapevibe.model.TICKS_PER_BAR
import com.tapevibe.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelTest {

    @Test fun toggleStepAddsAndRemoves() {
        val on = ProjectEdits.toggleStep(emptyList(), step = 4, lane = 1)
        assertEquals(listOf(Note(4 * STEP_TICKS, STEP_TICKS, DRUM_BASE_NOTE + 1, 0.8f)), on)
        assertTrue(ProjectEdits.toggleStep(on, 4, 1).isEmpty())
    }

    @Test fun toggleNoteSnapsAndClampsToLoop() {
        val loop = TICKS_PER_BAR
        val added = ProjectEdits.toggleNote(emptyList(), tick = 30, pitch = 60, length = PPQ, loopTicks = loop)
        assertEquals(Note(STEP_TICKS, PPQ, 60), added.single())
        // tapping inside the existing note removes it
        assertTrue(ProjectEdits.toggleNote(added, tick = STEP_TICKS + 50, pitch = 60, length = PPQ, loopTicks = loop).isEmpty())
        // a whole-bar note near the end is clipped to the loop
        val clipped = ProjectEdits.toggleNote(emptyList(), tick = loop - STEP_TICKS, pitch = 60, length = TICKS_PER_BAR, loopTicks = loop)
        assertEquals(STEP_TICKS, clipped.single().length)
        // beyond the loop: ignored
        assertTrue(ProjectEdits.toggleNote(emptyList(), loop + 10, 60, PPQ, loop).isEmpty())
    }

    @Test fun resizeAndMoveStayInsideLoop() {
        val n = Note(TICKS_PER_BAR - PPQ, PPQ, 60)
        assertEquals(PPQ, ProjectEdits.resized(n, PPQ * 8, TICKS_PER_BAR).length)
        assertEquals(STEP_TICKS, ProjectEdits.resized(n, 1, TICKS_PER_BAR).length)
        val moved = ProjectEdits.moved(Note(0, PPQ * 2, 60), TICKS_PER_BAR, 200, TICKS_PER_BAR)
        assertEquals(TICKS_PER_BAR - STEP_TICKS, moved.start)
        assertEquals(127, moved.pitch)
        assertEquals(STEP_TICKS, moved.length)
    }

    @Test fun repeatToFillCopiesLoop() {
        val notes = listOf(Note(0, STEP_TICKS, 36), Note(PPQ, STEP_TICKS, 37))
        val filled = ProjectEdits.repeatToFill(notes, 1, 4)
        assertEquals(8, filled.size)
        assertEquals(listOf(0, PPQ, TICKS_PER_BAR, TICKS_PER_BAR + PPQ), filled.take(4).map { it.start })
    }

    @Test fun trackLimitAndIds() {
        var p = Project()
        repeat(MAX_TRACKS + 3) { p = ProjectEdits.addTrack(p, TrackType.SYNTH) }
        assertEquals(MAX_TRACKS, p.tracks.size)
        assertEquals(p.tracks.size, p.tracks.map { it.id }.toSet().size)
        val dup = ProjectEdits.duplicateTrack(ProjectEdits.removeTrack(p, p.tracks[0].id), p.tracks[1].id)
        assertEquals(MAX_TRACKS, dup.tracks.size)
        assertEquals("${p.tracks[1].name} copy", dup.tracks[1].name)
    }

    @Test fun moveTrackReorders() {
        val p = DemoProject.create()
        val ids = p.tracks.map { it.id }
        val moved = ProjectEdits.moveTrack(p, ids[0], 1)
        assertEquals(listOf(ids[1], ids[0], ids[2]), moved.tracks.map { it.id })
        assertEquals(p, ProjectEdits.moveTrack(p, ids[0], -1))
    }

    @Test fun defaultsMatchNativeLayout() {
        assertEquals(16, InstrumentParams.defaults(TrackType.SYNTH).size)
        assertEquals(8, InstrumentParams.defaults(TrackType.DRUMS).size)
        assertEquals(5, InstrumentParams.defaults(TrackType.SAMPLER).size)
        assertEquals(1, InstrumentParams.defaults(TrackType.AUDIO).size)
        InstrumentParams.synthPresets.values.forEach { preset ->
            assertEquals(16, preset.size)
            assertTrue(preset.all { it in 0f..1f })
        }
        assertEquals("C4", InstrumentParams.noteName(60))
        assertTrue(InstrumentParams.isBlackKey(61))
        assertFalse(InstrumentParams.isBlackKey(64))
    }

    @Test fun historyMergesAndRedoes() {
        val h = History<Int>()
        h.record(0, "slider")
        h.record(1, "slider")  // merged
        h.record(2, null)
        assertEquals(2, h.undo(3))
        assertEquals(0, h.undo(2))
        assertNull(h.undo(0))
        assertEquals(2, h.redo(0))
        h.record(2, null)
        assertFalse(h.canRedo)
    }

    @Test fun storeRoundTripAndList() {
        val dir = Files.createTempDirectory("tv").toFile()
        val store = ProjectStore(dir)
        val p = DemoProject.create()
        val id = store.newId(p.name)
        assertTrue(id.startsWith("demo-groove-"))
        store.save(id, p)
        assertEquals(p, store.load(id))
        assertEquals(id, store.lastProjectId())
        assertEquals(listOf(p.name), store.list().map { it.name })

        java.io.File(store.samplesDir(id), "keep.wav").writeText("x")
        java.io.File(store.samplesDir(id), "orphan.wav").writeText("x")
        val withSample = ProjectEdits.updateTrack(p, p.tracks[0].id) { it.copy(sampleFile = "keep.wav") }
        store.pruneSamples(id, withSample)
        assertEquals(listOf("keep.wav"), store.samplesDir(id).list()!!.toList())
        assertTrue(store.samplePath(id, "keep.wav")!!.endsWith("keep.wav"))
        assertNull(store.samplePath(id, "missing.wav"))

        val copy = store.duplicate(id, "Copy")!!
        assertEquals("Copy", store.load(copy)!!.name)
        store.delete(id)
        assertNull(store.load(id))
        dir.deleteRecursively()
    }

    @Test fun decodeToleratesUnknownFields() {
        val store = ProjectStore(Files.createTempDirectory("tv").toFile())
        val text = store.encode(DemoProject.blank()).replaceFirst("{", "{\"futureField\":42,")
        assertEquals(DemoProject.blank(), store.decode(text))
    }
}
