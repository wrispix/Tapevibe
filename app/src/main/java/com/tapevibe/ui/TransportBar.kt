package com.tapevibe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.model.BAR_OPTIONS
import com.tapevibe.model.PPQ
import com.tapevibe.model.Project
import com.tapevibe.model.TICKS_PER_BAR
import com.tapevibe.ui.components.ToggleChip
import com.tapevibe.ui.theme.MonoDisplay
import com.tapevibe.ui.theme.Tv
import kotlin.math.roundToInt

@Composable
fun TopBar(
    name: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRename: () -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onRename)) {
            Text("TAPEVIBE", fontSize = 10.sp, color = Tv.Accent, letterSpacing = 2.sp)
            Text(name, fontSize = 16.sp, color = Tv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
        IconButton(onClick = onRedo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Project menu") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(text) }, onClick = onClick)

@Composable
fun TransportBar(
    project: Project,
    playing: Boolean,
    live: LiveState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onTempo: () -> Unit,
    onBars: (Int) -> Unit,
    onMetronome: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(Tv.Surface, RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SkipPrevious, "Stop and rewind", tint = Tv.Text)
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (playing) Tv.Teal else Tv.Accent)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Pause" else "Play",
                tint = Tv.Background,
                modifier = Modifier.size(30.dp),
            )
        }
        PositionReadout(live, playing, Modifier.weight(1f))
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Tv.Outline, RoundedCornerShape(8.dp))
                .clickable(onClick = onTempo)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${project.bpm.roundToInt()}", style = MonoDisplay, color = Tv.Text)
            Text(if (project.swing > 0.01f) "BPM · sw" else "BPM", fontSize = 9.sp, color = Tv.TextDim)
        }
        BarsPicker(project.bars, onBars)
        ToggleChip("Click", project.metronome, Tv.Yellow, width = 44.dp, onClick = onMetronome)
    }
}

@Composable
private fun PositionReadout(live: LiveState, playing: Boolean, modifier: Modifier) {
    Column(modifier.padding(start = 4.dp)) {
        // Reading live.ticks here recomposes only this small text each frame.
        val t = live.ticks.toInt()
        val bar = t / TICKS_PER_BAR + 1
        val beat = (t % TICKS_PER_BAR) / PPQ + 1
        val sixteenth = (t % PPQ) / (PPQ / 4) + 1
        Text("$bar.$beat.$sixteenth", style = MonoDisplay, color = if (playing) Tv.Teal else Tv.Text, maxLines = 1)
        Text("BAR.BEAT.16", fontSize = 9.sp, color = Tv.TextDim)
    }
}

@Composable
private fun BarsPicker(bars: Int, onBars: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Tv.Outline, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$bars", style = MonoDisplay, color = Tv.Text)
            Text("BARS", fontSize = 9.sp, color = Tv.TextDim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BAR_OPTIONS.forEach { b ->
                DropdownMenuItem(
                    text = { Text("$b bar${if (b > 1) "s" else ""}" + if (b > bars) " (repeat loop)" else "") },
                    onClick = { open = false; onBars(b) },
                )
            }
        }
    }
}

