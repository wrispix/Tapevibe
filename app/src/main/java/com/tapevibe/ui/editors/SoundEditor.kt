package com.tapevibe.ui.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.model.DRUM_BASE_NOTE
import com.tapevibe.model.InstrumentParams
import com.tapevibe.model.ParamSpec
import com.tapevibe.model.Track
import com.tapevibe.model.TrackType
import com.tapevibe.ui.ClipInfo
import com.tapevibe.ui.components.ParamSlider
import com.tapevibe.ui.components.SectionLabel
import com.tapevibe.ui.components.Segmented
import com.tapevibe.ui.components.Waveform
import com.tapevibe.ui.theme.Tv
import kotlin.math.roundToInt

@Composable
fun SoundEditor(
    track: Track,
    clip: ClipInfo?,
    onParam: (index: Int, value: Float) -> Unit,
    onEndGesture: () -> Unit,
    onPreset: (String) -> Unit,
    onReset: () -> Unit,
    onPreviewOn: (pitch: Int) -> Unit,
    onPreviewOff: (pitch: Int) -> Unit,
    onImportSample: () -> Unit,
    onClearSample: () -> Unit,
) {
    val color = Tv.trackColor(track.colorIndex)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        when (track.type) {
            TrackType.SYNTH -> {
                SectionLabel("Presets")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstrumentParams.synthPresets.keys.forEach { name ->
                        AssistChip(
                            onClick = { onPreset(name) },
                            label = { Text(name) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = Tv.Text),
                        )
                    }
                }
            }
            TrackType.DRUMS -> {
                SectionLabel("Pads")
                DrumPads(color, onPreviewOn, onPreviewOff)
            }
            TrackType.SAMPLER, TrackType.AUDIO -> {
                SectionLabel(if (track.type == TrackType.SAMPLER) "Sample" else "Clip")
                Waveform(clip?.peaks, color, Modifier.fillMaxWidth().height(72.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onImportSample) { Text("Import WAV") }
                    if (track.sampleFile != null) OutlinedButton(onClick = onClearSample) { Text("Remove") }
                    clip?.let { Text("%.2f s".format(it.seconds), color = Tv.TextDim, fontSize = 12.sp) }
                }
                if (track.type == TrackType.SAMPLER) {
                    Text(
                        "The sample plays at its original pitch on the root note. Hold a key in the Edit tab to audition.",
                        color = Tv.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        InstrumentParams.specs(track.type).groupBy { it.group }.forEach { (group, specs) ->
            SectionLabel(group)
            specs.forEach { spec -> ParamControl(spec, track.params.getOrElse(spec.index) { spec.default }, color, onParam, onEndGesture) }
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
            Text("Reset to defaults")
        }
    }
}

@Composable
private fun ParamControl(
    spec: ParamSpec,
    value: Float,
    color: androidx.compose.ui.graphics.Color,
    onParam: (Int, Float) -> Unit,
    onEnd: () -> Unit,
) {
    if (spec.choices.isNotEmpty()) {
        val n = spec.choices.size - 1
        Text(spec.name, fontSize = 12.sp, color = Tv.TextDim, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        Segmented(
            spec.choices,
            (value * n).roundToInt().coerceIn(0, n),
            { onParam(spec.index, it.toFloat() / n); onEnd() },
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            color,
        )
    } else {
        ParamSlider(spec.name, value, spec.format(value), { onParam(spec.index, it) }, onEnd, color)
    }
}

@Composable
private fun DrumPads(color: androidx.compose.ui.graphics.Color, onOn: (Int) -> Unit, onOff: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    val lane = row * 4 + col
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.25f + 0.05f * col))
                            .pointerInput(lane) {
                                detectTapGestures(onPress = {
                                    onOn(DRUM_BASE_NOTE + lane)
                                    tryAwaitRelease()
                                    onOff(DRUM_BASE_NOTE + lane)
                                })
                            }
                            .clickable { },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(InstrumentParams.drumLaneNames[lane], fontSize = 12.sp, color = Tv.Text)
                    }
                }
            }
        }
    }
}
