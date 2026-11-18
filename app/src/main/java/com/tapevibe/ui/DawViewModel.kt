package com.tapevibe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapevibe.audio.AudioEngine
import com.tapevibe.audio.EngineSync
import com.tapevibe.audio.RecordState
import com.tapevibe.data.ProjectInfo
import com.tapevibe.data.ProjectStore
import com.tapevibe.model.BAR_OPTIONS
import com.tapevibe.model.DemoProject
import com.tapevibe.model.History
import com.tapevibe.model.InstrumentParams
import com.tapevibe.model.MAX_TRACKS
import com.tapevibe.model.Note
import com.tapevibe.model.Project
import com.tapevibe.model.ProjectEdits
import com.tapevibe.model.TICKS_PER_BAR
import com.tapevibe.model.Track
import com.tapevibe.model.TrackType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

enum class EditorTab { EDIT, SOUND, MIX }

/** Waveform overview of a track's sample. */
class ClipInfo(val peaks: FloatArray, val seconds: Float)

class DawViewModel(
    private val engine: AudioEngine,
    private val store: ProjectStore,
    private val cacheDir: File,
    /** Single thread that owns all engine control calls except previews and transport. */
    private val engineDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Volatile
    var projectId: String = ""
        private set

    private val _project = MutableStateFlow(Project())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _selectedTrackId = MutableStateFlow<Long?>(null)
    val selectedTrackId: StateFlow<Long?> = _selectedTrackId.asStateFlow()

    private val _tab = MutableStateFlow(EditorTab.EDIT)
    val tab: StateFlow<EditorTab> = _tab.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _recordState = MutableStateFlow(RecordState.IDLE)
    val recordState: StateFlow<RecordState> = _recordState.asStateFlow()

    private val _waveforms = MutableStateFlow<Map<Long, ClipInfo>>(emptyMap())
    val waveforms: StateFlow<Map<Long, ClipInfo>> = _waveforms.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _audioOk = MutableStateFlow(true)
    val audioOk: StateFlow<Boolean> = _audioOk.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val history = History<Project>()
    private val sync = EngineSync(engine) { t -> store.samplePath(projectId, t.sampleFile) }
    private var recordJob: Job? = null
    private val peakBuffer = FloatArray(2 * (MAX_TRACKS + 1))

    init {
        val lastId = store.lastProjectId()
        val loaded = lastId?.let { store.load(it) }
        if (lastId != null && loaded != null) {
            projectId = lastId
            _project.value = loaded
        } else {
            val demo = DemoProject.create()
            projectId = store.newId(demo.name)
            _project.value = demo
            store.save(projectId, demo)
        }
        _selectedTrackId.value = _project.value.tracks.firstOrNull()?.id

        viewModelScope.launch(engineDispatcher) {
            _audioOk.value = engine.start()
            _project.collect { p ->
                sync.push(p)
                if (sync.changedSampleSlots.isNotEmpty()) refreshWaveforms(p, sync.changedSampleSlots)
            }
        }
        @OptIn(FlowPreview::class)
        viewModelScope.launch(ioDispatcher) {
            _project.drop(1).debounce(800).collect { p -> runCatching { store.save(projectId, p) } }
        }
    }

    // ---------------------------------------------------------------- editing helpers

    private fun edit(mergeKey: String? = null, f: (Project) -> Project) {
        val cur = _project.value
        val next = f(cur)
        if (next == cur) return
        history.record(cur, mergeKey)
        _project.value = next
        updateUndoState()
    }

    private fun editTrack(id: Long, mergeKey: String? = null, f: (Track) -> Track) =
        edit(mergeKey) { ProjectEdits.updateTrack(it, id, f) }

    private fun updateUndoState() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    /** Ends slider/drag merging so the next gesture becomes its own undo step. */
    fun endGesture() = history.breakMerge()

    fun undo() {
        history.undo(_project.value)?.let { _project.value = it; fixSelection() }
        updateUndoState()
    }

    fun redo() {
        history.redo(_project.value)?.let { _project.value = it; fixSelection() }
        updateUndoState()
    }

    private fun fixSelection() {
        val p = _project.value
        if (p.track(_selectedTrackId.value ?: -1) == null) _selectedTrackId.value = p.tracks.firstOrNull()?.id
    }

    fun slotOf(trackId: Long): Int = _project.value.tracks.indexOfFirst { it.id == trackId }

    // ---------------------------------------------------------------- navigation

    fun selectTrack(id: Long) { _selectedTrackId.value = id }
    fun selectTab(tab: EditorTab) { _tab.value = tab }

    // ---------------------------------------------------------------- transport

    fun togglePlay() {
        val p = !_playing.value
        engine.setPlaying(p)
        _playing.value = p
        if (!p && _recordState.value == RecordState.ARMED) cancelRecord()
    }

    fun stop() {
        engine.setPlaying(false)
        engine.rewind()
        _playing.value = false
        if (_recordState.value == RecordState.ARMED) cancelRecord()
    }

    fun positionTicks(): Double = engine.positionTicks()

    /** Per-track peak levels since last call: [2*slot], [2*slot+1]; master at the end. */
    fun readPeaks(): FloatArray {
        engine.readPeaks(peakBuffer)
        return peakBuffer
    }

    fun setBpm(bpm: Float) = edit("bpm") { it.copy(bpm = bpm.coerceIn(40f, 240f)) }
    fun setSwing(swing: Float) = edit("swing") { it.copy(swing = swing.coerceIn(0f, 0.75f)) }
    fun toggleMetronome() = edit { it.copy(metronome = !it.metronome) }

    /** Lengthening repeats the current loop; shortening trims notes past the new end. */
    fun setBars(bars: Int) {
        if (bars !in BAR_OPTIONS) return
        edit { p ->
            if (bars == p.bars) return@edit p
            val newEnd = bars * TICKS_PER_BAR
            p.copy(
                bars = bars,
                tracks = p.tracks.map { t ->
                    val trimmed = t.notes.filter { it.start < minOf(p.loopTicks, newEnd) }
                        .map { if (it.end > newEnd) it.copy(length = newEnd - it.start) else it }
                    t.copy(notes = if (bars > p.bars) ProjectEdits.repeatToFill(trimmed, p.bars, bars) else trimmed)
                },
            )
        }
    }

    fun setMasterVolume(v: Float) = edit("master") { it.copy(masterVolume = v) }
    fun setDelayDivision(d: Int) = edit { it.copy(delayDivision = d) }
    fun setDelayFeedback(v: Float) = edit("delayFb") { it.copy(delayFeedback = v) }
    fun setDelayDamping(v: Float) = edit("delayDamp") { it.copy(delayDamping = v) }

    // ---------------------------------------------------------------- tracks

    fun addTrack(type: TrackType) {
        if (_project.value.tracks.size >= MAX_TRACKS) {
            _messages.tryEmit("Track limit reached ($MAX_TRACKS)")
            return
        }
        edit { ProjectEdits.addTrack(it, type) }
        _selectedTrackId.value = _project.value.tracks.last().id
    }

    fun removeTrack(id: Long) {
        edit { ProjectEdits.removeTrack(it, id) }
        fixSelection()
    }

    fun duplicateTrack(id: Long) = edit { ProjectEdits.duplicateTrack(it, id) }
    fun moveTrack(id: Long, delta: Int) = edit { ProjectEdits.moveTrack(it, id, delta) }
    fun renameTrack(id: Long, name: String) = editTrack(id) { it.copy(name = name.take(32)) }
    fun setTrackColor(id: Long, color: Int) = editTrack(id) { it.copy(colorIndex = color) }

    fun setVolume(id: Long, v: Float) = editTrack(id, "vol$id") { it.copy(volume = v.coerceIn(0f, 1f)) }
    fun setPan(id: Long, v: Float) = editTrack(id, "pan$id") { it.copy(pan = v.coerceIn(-1f, 1f)) }
    fun setSend(id: Long, v: Float) = editTrack(id, "send$id") { it.copy(send = v.coerceIn(0f, 1f)) }
    fun toggleMute(id: Long) = editTrack(id) { it.copy(mute = !it.mute) }
    fun toggleSolo(id: Long) = editTrack(id) { it.copy(solo = !it.solo) }

    fun setParam(id: Long, index: Int, v: Float) = editTrack(id, "param$id/$index") { t ->
        if (index !in t.params.indices) t
        else t.copy(params = t.params.toMutableList().also { it[index] = v.coerceIn(0f, 1f) })
    }

    fun applyPreset(id: Long, name: String) {
        val values = InstrumentParams.synthPresets[name] ?: return
        editTrack(id) { if (it.type == TrackType.SYNTH) it.copy(params = values) else it }
    }

    fun resetParams(id: Long) = editTrack(id) { it.copy(params = InstrumentParams.defaults(it.type)) }

    // ---------------------------------------------------------------- notes

    fun toggleStep(id: Long, step: Int, lane: Int) {
        editTrack(id) { it.copy(notes = ProjectEdits.toggleStep(it.notes, step, lane)) }
    }

    fun setStepVelocity(id: Long, step: Int, lane: Int, velocity: Float) {
        editTrack(id) { it.copy(notes = ProjectEdits.setStepVelocity(it.notes, step, lane, velocity)) }
    }

    fun toggleNote(id: Long, tick: Int, pitch: Int, length: Int) {
        val loop = _project.value.loopTicks
        editTrack(id) { it.copy(notes = ProjectEdits.toggleNote(it.notes, tick, pitch, length, loop)) }
    }

    fun resizeNote(id: Long, note: Note, length: Int, gesture: Int) {
        val loop = _project.value.loopTicks
        editTrack(id, "resize$id/$gesture") { it.copy(notes = ProjectEdits.resizeNote(it.notes, note, length, loop)) }
    }

    fun moveNote(id: Long, note: Note, start: Int, pitch: Int, gesture: Int) {
        val loop = _project.value.loopTicks
        editTrack(id, "move$id/$gesture") { it.copy(notes = ProjectEdits.moveNote(it.notes, note, start, pitch, loop)) }
    }

    fun transpose(id: Long, semitones: Int) = editTrack(id) { it.copy(notes = ProjectEdits.transpose(it.notes, semitones)) }
    fun clearNotes(id: Long) = editTrack(id) { it.copy(notes = emptyList()) }

    fun previewOn(id: Long, pitch: Int, velocity: Float = 0.85f) {
        val slot = slotOf(id)
        if (slot >= 0) engine.noteOn(slot, pitch, velocity)
    }

    fun previewOff(id: Long, pitch: Int) {
        val slot = slotOf(id)
        if (slot >= 0) engine.noteOff(slot, pitch)
    }

    // ---------------------------------------------------------------- samples & recording

    fun importSample(id: Long, input: () -> InputStream?) {
        viewModelScope.launch(ioDispatcher) {
            val name = "import-${System.currentTimeMillis().toString(36)}.wav"
            val file = File(store.samplesDir(projectId), name)
            val ok = runCatching {
                input()?.use { ins -> file.outputStream().use { ins.copyTo(it) } } != null && isWav(file)
            }.getOrDefault(false)
            if (!ok) {
                file.delete()
                _messages.emit("That file isn't a WAV this app can read")
                return@launch
            }
            withContext(Dispatchers.Main.immediate) { editTrack(id) { it.copy(sampleFile = name) } }
        }
    }

    fun clearSample(id: Long) = editTrack(id) { it.copy(sampleFile = null) }

    private fun isWav(f: File): Boolean = runCatching {
        f.inputStream().use { s ->
            val h = ByteArray(12)
            s.read(h) == 12 && String(h, 0, 4) == "RIFF" && String(h, 8, 4) == "WAVE"
        }
    }.getOrDefault(false)

    fun armRecord(id: Long) {
        if (_recordState.value != RecordState.IDLE) return
        val slot = slotOf(id)
        if (slot < 0) return
        val maxSeconds = _project.value.loopSeconds + 2f
        viewModelScope.launch(engineDispatcher) {
            if (!engine.armRecord(slot, maxSeconds)) {
                _messages.emit("Couldn't open the microphone")
                return@launch
            }
            _recordState.value = RecordState.ARMED
            if (!_playing.value) {
                engine.rewind()
                engine.setPlaying(true)
                _playing.value = true
            }
            pollRecording(id, slot)
        }
    }

    private fun pollRecording(id: Long, slot: Int) {
        recordJob?.cancel()
        recordJob = viewModelScope.launch(engineDispatcher) {
            while (true) {
                delay(40)
                val s = engine.recordState()
                _recordState.value = s
                if (s == RecordState.IDLE) break
                if (s == RecordState.DONE) {
                    val name = "rec-$id-${System.currentTimeMillis().toString(36)}.wav"
                    val file = File(store.samplesDir(projectId), name)
                    if (engine.finishRecording(file.absolutePath)) {
                        sync.markSampleLoaded(slot, file.absolutePath)
                        withContext(Dispatchers.Main.immediate) { editTrack(id) { it.copy(sampleFile = name) } }
                        refreshWaveforms(_project.value, listOf(slot))
                    } else {
                        _messages.emit("Recording failed to save")
                    }
                    _recordState.value = RecordState.IDLE
                    break
                }
            }
        }
    }

    fun cancelRecord() {
        viewModelScope.launch(engineDispatcher) {
            engine.cancelRecord()
            _recordState.value = engine.recordState()
        }
    }

    private fun refreshWaveforms(p: Project, slots: List<Int>) {
        val map = _waveforms.value.toMutableMap()
        slots.forEach { slot ->
            val t = p.tracks.getOrNull(slot) ?: return@forEach
            val peaks = engine.samplePeaks(slot, 512)
            val seconds = engine.sampleSeconds(slot)
            if (peaks.isEmpty() || seconds <= 0f) map.remove(t.id) else map[t.id] = ClipInfo(peaks, seconds)
        }
        val ids = p.tracks.map { it.id }.toSet()
        _waveforms.value = map.filterKeys { it in ids }
    }

    // ---------------------------------------------------------------- export

    fun export(loops: Int, output: () -> OutputStream?) {
        if (_busy.value != null) return
        stop()
        _busy.value = "Bouncing…"
        viewModelScope.launch(engineDispatcher) {
            val tmp = File(cacheDir, "bounce.wav")
            val ok = runCatching {
                engine.exportWav(tmp.absolutePath, loops, 2f) &&
                    withContext(ioDispatcher) {
                        output()?.use { out -> tmp.inputStream().use { it.copyTo(out) } } != null
                    }
            }.getOrDefault(false)
            tmp.delete()
            _busy.value = null
            _messages.emit(if (ok) "Exported WAV" else "Export failed")
        }
    }

    // ---------------------------------------------------------------- projects

    fun refreshProjects() {
        viewModelScope.launch(ioDispatcher) { _projects.value = store.list() }
    }

    fun renameProject(name: String) = edit { it.copy(name = name.ifBlank { "Untitled" }.take(40)) }

    fun newProject(demo: Boolean) = switchTo(if (demo) DemoProject.create() else DemoProject.blank(), null)

    fun openProject(id: String) {
        viewModelScope.launch(ioDispatcher) {
            val p = store.load(id)
            if (p == null) {
                _messages.emit("Couldn't open that project")
            } else {
                withContext(Dispatchers.Main.immediate) { switchTo(p, id) }
            }
        }
    }

    fun duplicateProject() {
        viewModelScope.launch(ioDispatcher) {
            store.save(projectId, _project.value)
            val newId = store.duplicate(projectId, "${_project.value.name} copy") ?: return@launch
            val p = store.load(newId) ?: return@launch
            withContext(Dispatchers.Main.immediate) { switchTo(p, newId) }
        }
    }

    fun deleteProject(id: String) {
        if (id == projectId) {
            _messages.tryEmit("Can't delete the open project")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            store.delete(id)
            _projects.value = store.list()
        }
    }

    private fun switchTo(p: Project, existingId: String?) {
        stop()
        cancelRecord()
        val oldId = projectId
        val oldProject = _project.value
        viewModelScope.launch(engineDispatcher) {
            withContext(ioDispatcher) {
                runCatching {
                    store.save(oldId, oldProject)
                    store.pruneSamples(oldId, oldProject)
                }
            }
            val id = existingId ?: store.newId(p.name)
            projectId = id
            sync.invalidate()
            if (existingId == null) withContext(ioDispatcher) { store.save(id, p) }
            withContext(Dispatchers.Main.immediate) {
                history.clear()
                updateUndoState()
                _waveforms.value = emptyMap()
                _project.value = p
                _selectedTrackId.value = p.tracks.firstOrNull()?.id
            }
        }
    }

    fun saveNow() {
        val p = _project.value
        viewModelScope.launch(ioDispatcher) { runCatching { store.save(projectId, p) } }
    }

    // ---------------------------------------------------------------- lifecycle

    fun onForeground() {
        viewModelScope.launch(engineDispatcher) { _audioOk.value = engine.start() }
    }

    fun onBackground() {
        stop()
        saveNow()
        viewModelScope.launch(engineDispatcher) { engine.stop() }
    }

    override fun onCleared() {
        engine.setPlaying(false)
        runCatching { store.save(projectId, _project.value) }
    }
}
