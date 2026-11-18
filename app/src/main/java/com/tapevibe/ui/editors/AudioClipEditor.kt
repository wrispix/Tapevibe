package com.tapevibe.ui.editors

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.tapevibe.audio.RecordState
import com.tapevibe.model.PPQ
import com.tapevibe.model.Project
import com.tapevibe.model.Track
import com.tapevibe.ui.ClipInfo
import com.tapevibe.ui.LiveState
import com.tapevibe.ui.components.Waveform
import com.tapevibe.ui.theme.Tv

/** Audio track view: one loop-aligned clip, recorded from the mic or imported. */
@Composable
fun AudioClipEditor(
    track: Track,
    project: Project,
    clip: ClipInfo?,
    live: LiveState,
    playing: Boolean,
    recordState: RecordState,
    recordingTrack: Boolean,
    onRecord: () -> Unit,
    onCancel: () -> Unit,
    onStopTransport: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    val color = Tv.trackColor(track.colorIndex)
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse",
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "Clip · ${project.bars} bar${if (project.bars > 1) "s" else ""} loop",
            style = MaterialTheme.typography.titleSmall,
            color = Tv.Text,
        )
        Spacer(Modifier.height(8.dp))
        Waveform(
            clip?.peaks,
            color,
            Modifier.fillMaxWidth().height(140.dp),
            progress = {
                if (!playing || clip == null) -1f
                else (live.ticks / PPQ * 60f / project.bpm) / clip.seconds
            },
        )
        Spacer(Modifier.height(16.dp))

        val busy = recordState != RecordState.IDLE
        when {
            recordingTrack && recordState == RecordState.ARMED -> {
                Text("Armed — recording starts at the top of the loop…", color = Tv.Yellow)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
            recordingTrack && recordState == RecordState.RECORDING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FiberManualRecord, null, tint = Tv.Record, modifier = Modifier.size(18.dp).alpha(pulse))
                    Spacer(Modifier.width(6.dp))
                    Text("Recording one pass of the loop", color = Tv.Record)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onStopTransport) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop early")
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRecord,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Tv.Record, contentColor = Tv.Text),
                    ) {
                        Icon(Icons.Default.FiberManualRecord, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (clip == null) "Record" else "Re-record")
                    }
                    OutlinedButton(onClick = onImport, enabled = !busy) { Text("Import WAV") }
                    if (track.sampleFile != null) OutlinedButton(onClick = onClear, enabled = !busy) { Text("Clear") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Recording captures exactly one pass of the loop from the microphone, starting at bar 1. " +
                "Turn on the metronome for a click and use headphones so the speaker doesn't bleed into the take. " +
                "The clip always plays from the loop start; change the loop length or tempo after recording and it won't stretch.",
            style = MaterialTheme.typography.bodySmall,
            color = Tv.TextDim,
        )
    }
}
