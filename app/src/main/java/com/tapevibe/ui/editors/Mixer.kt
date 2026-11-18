package com.tapevibe.ui.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.model.DELAY_DIVISIONS
import com.tapevibe.model.Project
import com.tapevibe.model.Track
import com.tapevibe.ui.LiveState
import com.tapevibe.ui.components.PeakMeter
import com.tapevibe.ui.components.ToggleChip
import com.tapevibe.ui.components.VerticalFader
import com.tapevibe.ui.theme.Tv
import kotlin.math.roundToInt

interface MixerActions {
    fun volume(id: Long, v: Float)
    fun pan(id: Long, v: Float)
    fun send(id: Long, v: Float)
    fun mute(id: Long)
    fun solo(id: Long)
    fun select(id: Long)
    fun master(v: Float)
    fun delayDivision(d: Int)
    fun delayFeedback(v: Float)
    fun delayDamping(v: Float)
    fun endGesture()
}

@Composable
fun Mixer(project: Project, selectedId: Long?, live: LiveState, actions: MixerActions) {
    Row(
        Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        project.tracks.forEachIndexed { slot, t ->
            Strip(t, slot, t.id == selectedId, live, actions)
        }
        DelayStrip(project, actions)
        MasterStrip(project, live, actions)
    }
}

@Composable
private fun MiniSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, color: Color, onChange: (Float) -> Unit, onEnd: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 10.sp, color = Tv.TextDim)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onEnd,
            valueRange = range,
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Tv.SurfaceHigh),
        )
    }
}

@Composable
private fun Strip(t: Track, slot: Int, selected: Boolean, live: LiveState, a: MixerActions) {
    val color = Tv.trackColor(t.colorIndex)
    Column(
        Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(Tv.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) color else Tv.Outline, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(color, RoundedCornerShape(2.dp)))
        Text(
            t.name, fontSize = 12.sp, color = Tv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(t.type.label, fontSize = 10.sp, color = Tv.TextDim)
        Row(Modifier.weight(1f).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PeakMeter({ live.trackLevel(slot, false) }, { live.trackLevel(slot, true) }, Modifier.width(10.dp).fillMaxHeight())
            VerticalFader(t.volume, { a.volume(t.id, it) }, a::endGesture, color, Modifier.width(36.dp).fillMaxHeight(), "${t.name} volume")
        }
        Text("${(t.volume * 100).roundToInt()}", fontSize = 10.sp, color = Tv.TextDim, fontFamily = Tv.Mono)
        MiniSlider("Pan ${panLabel(t.pan)}", t.pan, -1f..1f, color, { a.pan(t.id, if (kotlin.math.abs(it) < 0.04f) 0f else it) }, a::endGesture)
        MiniSlider("FX ${(t.send * 100).roundToInt()}", t.send, 0f..1f, Tv.Teal, { a.send(t.id, it) }, a::endGesture)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            ToggleChip("M", t.mute, Tv.Accent) { a.mute(t.id) }
            ToggleChip("S", t.solo, Tv.Yellow) { a.solo(t.id) }
        }
    }
}

private fun panLabel(p: Float): String = when {
    p < -0.02f -> "L${(-p * 100).roundToInt()}"
    p > 0.02f -> "R${(p * 100).roundToInt()}"
    else -> "C"
}

@Composable
private fun DelayStrip(p: Project, a: MixerActions) {
    Column(
        Modifier
            .width(112.dp)
            .fillMaxHeight()
            .background(Tv.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, Tv.Outline, RoundedCornerShape(8.dp))
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Tv.Teal, RoundedCornerShape(2.dp)))
        Text("Delay", fontSize = 12.sp, color = Tv.Text, modifier = Modifier.padding(top = 4.dp))
        Text("FX send bus", fontSize = 10.sp, color = Tv.TextDim)
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DELAY_DIVISIONS.forEachIndexed { i, label ->
                val on = i == p.delayDivision
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(if (on) Tv.Teal else Tv.SurfaceHigh, RoundedCornerShape(5.dp))
                        .clickable { a.delayDivision(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 12.sp, color = if (on) Tv.Background else Tv.TextDim)
                }
            }
        }
        MiniSlider("Feedback ${(p.delayFeedback * 100).roundToInt()}%", p.delayFeedback, 0f..0.92f, Tv.Teal, a::delayFeedback, a::endGesture)
        MiniSlider("Dark ${(p.delayDamping * 100).roundToInt()}%", p.delayDamping, 0f..1f, Tv.Teal, a::delayDamping, a::endGesture)
    }
}

@Composable
private fun MasterStrip(p: Project, live: LiveState, a: MixerActions) {
    Column(
        Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(Tv.SurfaceHigh, RoundedCornerShape(8.dp))
            .border(1.dp, Tv.Outline, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Tv.Text, RoundedCornerShape(2.dp)))
        Text("Master", fontSize = 12.sp, color = Tv.Text, modifier = Modifier.padding(top = 4.dp))
        Text("Out", fontSize = 10.sp, color = Tv.TextDim)
        Row(Modifier.weight(1f).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PeakMeter({ live.masterLevel(false) }, { live.masterLevel(true) }, Modifier.width(12.dp).fillMaxHeight())
            VerticalFader(p.masterVolume / 1.25f, { a.master(it * 1.25f) }, a::endGesture, Tv.Text, Modifier.width(36.dp).fillMaxHeight(), "Master volume")
        }
        Text("${(p.masterVolume * 100).roundToInt()}", fontSize = 10.sp, color = Tv.TextDim, fontFamily = Tv.Mono)
    }
}
