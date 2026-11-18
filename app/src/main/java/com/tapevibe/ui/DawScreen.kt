package com.tapevibe.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tapevibe.audio.RecordState
import com.tapevibe.model.Note
import com.tapevibe.model.TrackType
import com.tapevibe.ui.editors.AudioClipEditor
import com.tapevibe.ui.editors.EmptyHint
import com.tapevibe.ui.editors.Mixer
import com.tapevibe.ui.editors.MixerActions
import com.tapevibe.ui.editors.PianoRoll
import com.tapevibe.ui.editors.PianoRollActions
import com.tapevibe.ui.editors.SoundEditor
import com.tapevibe.ui.editors.StepSequencer
import com.tapevibe.ui.theme.Tv

private sealed interface DialogState {
    data object None : DialogState
    data object AddTrack : DialogState
    data object Tempo : DialogState
    data object Projects : DialogState
    data object RenameProject : DialogState
    data object Export : DialogState
    data class RenameTrack(val id: Long) : DialogState
    data class DeleteTrack(val id: Long) : DialogState
}

@Composable
fun DawScreen(vm: DawViewModel) {
    val project by vm.project.collectAsState()
    val selectedId by vm.selectedTrackId.collectAsState()
    val tab by vm.tab.collectAsState()
    val playing by vm.playing.collectAsState()
    val recordState by vm.recordState.collectAsState()
    val waveforms by vm.waveforms.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    val busy by vm.busy.collectAsState()
    val audioOk by vm.audioOk.collectAsState()
    val projects by vm.projects.collectAsState()
    val live = rememberLiveState(vm)
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<DialogState>(DialogState.None) }
    var importTarget by remember { mutableStateOf<Long?>(null) }
    var recordTarget by remember { mutableStateOf<Long?>(null) }
    var recordingTrackId by remember { mutableStateOf<Long?>(null) }
    var exportLoops by remember { mutableIntStateOf(4) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(audioOk) { if (!audioOk) snackbar.showSnackbar("Audio output couldn't start") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = importTarget
        if (uri != null && id != null) vm.importSample(id) { context.contentResolver.openInputStream(uri) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if (uri != null) vm.export(exportLoops) { context.contentResolver.openOutputStream(uri) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = recordTarget
        if (granted && id != null) {
            recordingTrackId = id
            vm.armRecord(id)
        } else if (!granted) {
            vm.selectTab(EditorTab.EDIT)
        }
    }
    fun record(id: Long) {
        recordTarget = id
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recordingTrackId = id
            vm.armRecord(id)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun import(id: Long) {
        importTarget = id
        importLauncher.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"))
    }

    val track = project.track(selectedId ?: -1)

    Scaffold(
        containerColor = Tv.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Tv.Surface) {
                listOf(
                    Triple(EditorTab.EDIT, "Edit", Icons.Default.Edit),
                    Triple(EditorTab.SOUND, "Sound", Icons.Default.GraphicEq),
                    Triple(EditorTab.MIX, "Mix", Icons.Default.Tune),
                ).forEach { (t, label, icon) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { vm.selectTab(t) },
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Tv.Background,
                            indicatorColor = Tv.Accent,
                            selectedTextColor = Tv.Accent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Tv.Background)) {
            TopBar(
                name = project.name,
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = vm::undo,
                onRedo = vm::redo,
                onRename = { dialog = DialogState.RenameProject },
            ) { dismiss ->
                MenuItem("Projects…") { dismiss(); vm.refreshProjects(); dialog = DialogState.Projects }
                MenuItem("Rename project") { dismiss(); dialog = DialogState.RenameProject }
                MenuItem("Duplicate project") { dismiss(); vm.duplicateProject() }
                MenuItem("Export WAV…") { dismiss(); dialog = DialogState.Export }
            }
            TransportBar(
                project = project,
                playing = playing,
                live = live,
                onPlay = vm::togglePlay,
                onStop = vm::stop,
                onTempo = { dialog = DialogState.Tempo },
                onBars = vm::setBars,
                onMetronome = vm::toggleMetronome,
            )
            TrackBar(project.tracks, selectedId, live, object : TrackActions {
                override fun select(id: Long) = vm.selectTrack(id)
                override fun mute(id: Long) = vm.toggleMute(id)
                override fun solo(id: Long) = vm.toggleSolo(id)
                override fun rename(id: Long) { dialog = DialogState.RenameTrack(id) }
                override fun duplicate(id: Long) = vm.duplicateTrack(id)
                override fun move(id: Long, delta: Int) = vm.moveTrack(id, delta)
                override fun recolor(id: Long) {
                    project.track(id)?.let { vm.setTrackColor(id, (it.colorIndex + 1) % Tv.trackColors.size) }
                }
                override fun delete(id: Long) { dialog = DialogState.DeleteTrack(id) }
                override fun add() { dialog = DialogState.AddTrack }
            })

            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
                when {
                    tab == EditorTab.MIX -> Mixer(project, selectedId, live, object : MixerActions {
                        override fun volume(id: Long, v: Float) = vm.setVolume(id, v)
                        override fun pan(id: Long, v: Float) = vm.setPan(id, v)
                        override fun send(id: Long, v: Float) = vm.setSend(id, v)
                        override fun mute(id: Long) = vm.toggleMute(id)
                        override fun solo(id: Long) = vm.toggleSolo(id)
                        override fun select(id: Long) = vm.selectTrack(id)
                        override fun master(v: Float) = vm.setMasterVolume(v)
                        override fun delayDivision(d: Int) = vm.setDelayDivision(d)
                        override fun delayFeedback(v: Float) = vm.setDelayFeedback(v)
                        override fun delayDamping(v: Float) = vm.setDelayDamping(v)
                        override fun endGesture() = vm.endGesture()
                    })
                    track == null -> EmptyHint("Add a track with the + button to start.")
                    tab == EditorTab.SOUND -> SoundEditor(
                        track = track,
                        clip = waveforms[track.id],
                        onParam = { i, v -> vm.setParam(track.id, i, v) },
                        onEndGesture = vm::endGesture,
                        onPreset = { vm.applyPreset(track.id, it) },
                        onReset = { vm.resetParams(track.id) },
                        onPreviewOn = { vm.previewOn(track.id, it) },
                        onPreviewOff = { vm.previewOff(track.id, it) },
                        onImportSample = { import(track.id) },
                        onClearSample = { vm.clearSample(track.id) },
                    )
                    track.type == TrackType.DRUMS -> StepSequencer(
                        track = track,
                        bars = project.bars,
                        live = live,
                        playing = playing,
                        onToggle = { s, l -> vm.toggleStep(track.id, s, l) },
                        onVelocity = { s, l, v -> vm.setStepVelocity(track.id, s, l, v) },
                        onPreview = { l -> vm.previewOn(track.id, com.tapevibe.model.DRUM_BASE_NOTE + l) },
                    )
                    track.type == TrackType.AUDIO -> AudioClipEditor(
                        track = track,
                        project = project,
                        clip = waveforms[track.id],
                        live = live,
                        playing = playing,
                        recordState = recordState,
                        recordingTrack = recordingTrackId == track.id,
                        onRecord = { record(track.id) },
                        onCancel = vm::cancelRecord,
                        onStopTransport = vm::stop,
                        onImport = { import(track.id) },
                        onClear = { vm.clearSample(track.id) },
                    )
                    else -> Column {
                        if (track.type == TrackType.SAMPLER && waveforms[track.id] == null) {
                            EmptyHint("No sample loaded — import a WAV in the Sound tab.")
                        }
                        PianoRoll(track, project.loopTicks, live, playing, object : PianoRollActions {
                            override fun toggle(tick: Int, pitch: Int, length: Int) = vm.toggleNote(track.id, tick, pitch, length)
                            override fun resize(note: Note, length: Int, gesture: Int) = vm.resizeNote(track.id, note, length, gesture)
                            override fun move(note: Note, start: Int, pitch: Int, gesture: Int) = vm.moveNote(track.id, note, start, pitch, gesture)
                            override fun endGesture() = vm.endGesture()
                            override fun previewOn(pitch: Int) = vm.previewOn(track.id, pitch)
                            override fun previewOff(pitch: Int) = vm.previewOff(track.id, pitch)
                            override fun transpose(semitones: Int) = vm.transpose(track.id, semitones)
                            override fun clear() = vm.clearNotes(track.id)
                        })
                    }
                }
                busy?.let {
                    Box(Modifier.fillMaxSize().background(Tv.Background.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Tv.Accent)
                            Text(it, color = Tv.Text, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }

    if (recordState == RecordState.IDLE && recordingTrackId != null) {
        LaunchedEffect(Unit) { recordingTrackId = null }
    }

    when (val d = dialog) {
        DialogState.None -> {}
        DialogState.AddTrack -> AddTrackDialog({ dialog = DialogState.None }, vm::addTrack)
        DialogState.Tempo -> TempoDialog(project.bpm, project.swing, vm::setBpm, vm::setSwing, vm::endGesture) { dialog = DialogState.None }
        DialogState.Projects -> ProjectsDialog(
            projects, vm.projectId, vm::openProject, vm::deleteProject, vm::newProject,
        ) { dialog = DialogState.None }
        DialogState.RenameProject -> TextInputDialog("Project name", project.name, { dialog = DialogState.None }, vm::renameProject)
        DialogState.Export -> ExportDialog(project.loopSeconds, { dialog = DialogState.None }) { loops ->
            exportLoops = loops
            exportLauncher.launch("${project.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "tapevibe" }}.wav")
        }
        is DialogState.RenameTrack -> TextInputDialog(
            "Track name", project.track(d.id)?.name ?: "", { dialog = DialogState.None },
        ) { vm.renameTrack(d.id, it) }
        is DialogState.DeleteTrack -> ConfirmDialog(
            "Delete track?", "“${project.track(d.id)?.name}” and its notes will be removed. You can undo this.",
            "Delete", { dialog = DialogState.None },
        ) { vm.removeTrack(d.id) }
    }
}
