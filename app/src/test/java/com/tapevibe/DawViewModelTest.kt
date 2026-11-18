package com.tapevibe

import com.tapevibe.audio.RecordState
import com.tapevibe.data.ProjectStore
import com.tapevibe.model.DRUM_BASE_NOTE
import com.tapevibe.model.STEP_TICKS
import com.tapevibe.model.TrackType
import com.tapevibe.ui.DawViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DawViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var dir: File
    private lateinit var engine: FakeEngine
    private lateinit var vm: DawViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dir = Files.createTempDirectory("tvvm").toFile()
        engine = FakeEngine()
        vm = DawViewModel(engine, ProjectStore(File(dir, "projects")), dir, dispatcher, dispatcher)
        scope.runCurrent()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun idle() = scope.runCurrent()

    @Test fun startsWithDemoAndPushesIt() {
        assertTrue("start" in engine.calls)
        assertEquals(3, engine.trackCountValue)
        assertEquals(vm.project.value.tracks[0].notes, engine.notes[0])
        assertEquals(vm.project.value.tracks[0].id, vm.selectedTrackId.value)
    }

    @Test fun editsReachEngineAndUndo() {
        val drums = vm.project.value.tracks[0]
        vm.toggleStep(drums.id, step = 1, lane = 7)
        idle()
        assertTrue(engine.notes[0]!!.any { it.start == STEP_TICKS && it.pitch == DRUM_BASE_NOTE + 7 })
        assertTrue(vm.canUndo.value)
        vm.undo()
        idle()
        assertEquals(drums.notes, engine.notes[0])
        assertTrue(vm.canRedo.value)
    }

    @Test fun sliderDragIsOneUndoStep() {
        val id = vm.project.value.tracks[1].id
        val before = vm.project.value
        listOf(0.1f, 0.2f, 0.3f).forEach { vm.setParam(id, 5, it) }
        vm.endGesture()
        vm.setParam(id, 5, 0.9f)
        vm.undo()
        assertEquals(0.3f, vm.project.value.tracks[1].params[5])
        vm.undo()
        assertEquals(before, vm.project.value)
    }

    @Test fun lengtheningLoopRepeatsAndShorteningTrims() {
        vm.setBars(1)
        val oneBar = vm.project.value.tracks[0].notes
        vm.setBars(4)
        assertEquals(oneBar.size * 4, vm.project.value.tracks[0].notes.size)
        idle()
        assertEquals(4 * 4 * 96, engine.loopTicksValue)
        vm.setBars(1)
        assertEquals(oneBar, vm.project.value.tracks[0].notes)
    }

    @Test fun transportAndPreview() {
        vm.togglePlay()
        assertTrue(engine.playingNow)
        assertTrue(vm.playing.value)
        vm.stop()
        assertFalse(engine.playingNow)
        assertTrue("rewind" in engine.calls)
        val id = vm.project.value.tracks[2].id
        vm.previewOn(id, 64)
        vm.previewOff(id, 64)
        assertTrue("on2:64" in engine.calls && "off2:64" in engine.calls)
    }

    @Test fun addAndRemoveTrackKeepsSelectionValid() {
        vm.addTrack(TrackType.AUDIO)
        val added = vm.project.value.tracks.last()
        assertEquals(added.id, vm.selectedTrackId.value)
        vm.removeTrack(added.id)
        assertNotNull(vm.project.value.track(vm.selectedTrackId.value!!))
    }

    @Test fun recordingFlowSetsSampleFile() {
        vm.addTrack(TrackType.AUDIO)
        val id = vm.project.value.tracks.last().id
        idle()
        vm.armRecord(id)
        idle()
        assertEquals(RecordState.ARMED, vm.recordState.value)
        assertTrue(engine.playingNow)
        engine.record = RecordState.DONE
        scope.advanceTimeBy(100)
        idle()
        val file = vm.project.value.track(id)!!.sampleFile
        assertNotNull(file)
        assertTrue(file!!.startsWith("rec-$id-"))
        assertEquals(RecordState.IDLE, vm.recordState.value)
        assertFalse("recorded clip should not be reloaded from disk", engine.calls.contains("load3"))
    }

    @Test fun importRejectsNonWav() {
        val id = vm.project.value.tracks[0].id
        vm.importSample(id) { ByteArrayInputStream("hello".toByteArray()) }
        scope.advanceUntilIdle()
        assertEquals(null, vm.project.value.track(id)!!.sampleFile)
        vm.importSample(id) { ByteArrayInputStream("RIFF\u0000\u0000\u0000\u0000WAVEdata".toByteArray()) }
        scope.advanceUntilIdle()
        assertNotNull(vm.project.value.track(id)!!.sampleFile)
    }

    @Test fun exportWritesToOutput() {
        val out = ByteArrayOutputStream()
        vm.export(2) { out }
        idle()
        assertTrue("export2" in engine.calls)
        assertEquals(64, out.size())
        assertEquals(null, vm.busy.value)
    }

    @Test fun autosaveAndSwitchProjects() {
        vm.renameProject("Night drive")
        scope.advanceTimeBy(2000)
        idle()
        vm.refreshProjects()
        idle()
        assertEquals(listOf("Night drive"), vm.projects.value.map { it.name })
        val firstId = vm.projectId
        vm.newProject(demo = false)
        scope.advanceUntilIdle()
        assertEquals(1, vm.project.value.tracks.size)
        assertFalse(vm.canUndo.value)
        vm.openProject(firstId)
        scope.advanceUntilIdle()
        assertEquals("Night drive", vm.project.value.name)
        assertEquals(firstId, vm.projectId)
    }
}
