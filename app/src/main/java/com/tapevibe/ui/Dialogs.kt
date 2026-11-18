package com.tapevibe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.data.ProjectInfo
import com.tapevibe.model.TrackType
import com.tapevibe.ui.components.ParamSlider
import com.tapevibe.ui.components.Segmented
import com.tapevibe.ui.theme.MonoDisplay
import com.tapevibe.ui.theme.Tv
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val typeDescriptions = mapOf(
    TrackType.DRUMS to "Eight synthesised drums on a step grid",
    TrackType.SYNTH to "Polyphonic two-oscillator synth with filter, envelopes and LFO",
    TrackType.SAMPLER to "Play an imported WAV chromatically from the piano roll",
    TrackType.AUDIO to "Record from the microphone or drop in a WAV loop",
)

@Composable
fun AddTrackDialog(onDismiss: () -> Unit, onAdd: (TrackType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add track") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(TrackType.DRUMS, TrackType.SYNTH, TrackType.SAMPLER, TrackType.AUDIO).forEach { type ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(type); onDismiss() }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(type.label, color = Tv.Text)
                        Text(typeDescriptions.getValue(type), color = Tv.TextDim, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TempoDialog(
    bpm: Float,
    swing: Float,
    onBpm: (Float) -> Unit,
    onSwing: (Float) -> Unit,
    onEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val taps = remember { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = { onEnd(); onDismiss() },
        title = { Text("Tempo & groove") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onBpm((bpm - 1).roundToInt().toFloat()); onEnd() }) { Text("−") }
                    Text("${bpm.roundToInt()} BPM", style = MonoDisplay, color = Tv.Text, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { onBpm((bpm + 1).roundToInt().toFloat()); onEnd() }) { Text("+") }
                }
                ParamSlider("Tempo", (bpm - 40f) / 200f, "${bpm.roundToInt()}", { onBpm((40f + it * 200f).roundToInt().toFloat()) }, onEnd)
                ParamSlider("Swing", swing / 0.75f, "${(swing / 0.75f * 100).roundToInt()}%", { onSwing(it * 0.75f) }, onEnd, Tv.Teal)
                OutlinedButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                        taps += now
                        while (taps.size > 6) taps.removeAt(0)
                        if (taps.size >= 3) {
                            val avg = (taps.last() - taps.first()).toFloat() / (taps.size - 1)
                            onBpm((60000f / avg).coerceIn(40f, 240f).roundToInt().toFloat())
                            onEnd()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (taps.size < 3) "Tap tempo" else "Tap tempo (${taps.size})") }
            }
        },
        confirmButton = { TextButton(onClick = { onEnd(); onDismiss() }) { Text("Done") } },
    )
}

@Composable
fun ProjectsDialog(
    projects: List<ProjectInfo>,
    currentId: String,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: (demo: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<ProjectInfo?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projects") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNew(false); onDismiss() }) { Text("New") }
                    OutlinedButton(onClick = { onNew(true); onDismiss() }) { Text("New from demo") }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(projects, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = p.id != currentId) { onOpen(p.id); onDismiss() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name + if (p.id == currentId) "  (open)" else "", color = Tv.Text)
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.modified)),
                                    fontSize = 11.sp, color = Tv.TextDim,
                                )
                            }
                            if (p.id != currentId) {
                                IconButton(onClick = { confirmDelete = p }) { Icon(Icons.Default.Delete, "Delete ${p.name}") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${p.name}”?") },
            text = { Text("Its recordings and samples are deleted too. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(p.id); confirmDelete = null }) { Text("Delete", color = Tv.Record) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ExportDialog(loopSeconds: Float, onDismiss: () -> Unit, onExport: (loops: Int) -> Unit) {
    val options = listOf(1, 2, 4, 8, 16)
    var idx by remember { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export WAV") },
        text = {
            Column {
                Text("Number of loop repeats", color = Tv.TextDim, fontSize = 12.sp)
                Segmented(options.map { "×$it" }, idx, { idx = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text(
                    "≈ %.1f s plus a 2 s tail for releases and echoes. 16-bit stereo at the device sample rate.".format(loopSeconds * options[idx]),
                    color = Tv.TextDim, fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onExport(options[idx]); onDismiss() }) { Text("Choose file…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Tv.Record) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

