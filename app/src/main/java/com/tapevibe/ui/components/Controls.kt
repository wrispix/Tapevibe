package com.tapevibe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.ui.theme.Tv

/** Small toggle button used for mute/solo/metronome style switches. */
@Composable
fun ToggleChip(
    label: String,
    on: Boolean,
    onColor: Color,
    modifier: Modifier = Modifier,
    width: Dp = 28.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(width, 24.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (on) onColor else Tv.SurfaceHigh)
            .border(1.dp, if (on) onColor else Tv.Outline, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (on) Tv.Background else Tv.TextDim,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** Stereo peak meter. [levels] is read during draw so only this canvas invalidates. */
@Composable
fun PeakMeter(levelL: () -> Float, levelR: () -> Float, modifier: Modifier = Modifier, vertical: Boolean = true) {
    Canvas(modifier) {
        val brush = if (vertical) Brush.verticalGradient(listOf(Tv.Record, Tv.Yellow, Tv.Teal, Tv.Teal))
        else Brush.horizontalGradient(listOf(Tv.Teal, Tv.Teal, Tv.Yellow, Tv.Record))
        val levels = listOf(levelL(), levelR())
        drawRect(Tv.GridDark)
        levels.forEachIndexed { i, raw ->
            val v = meterScale(raw)
            if (vertical) {
                val w = size.width / 2f
                val h = size.height * v
                clipRectSafe(i * w, size.height - h, w - 1f, h) {
                    drawRect(brush, topLeft = Offset(i * w, 0f), size = Size(w - 1f, size.height))
                }
            } else {
                val h = size.height / 2f
                val w = size.width * v
                clipRectSafe(0f, i * h, w, h - 1f) {
                    drawRect(brush, topLeft = Offset(0f, i * h), size = Size(size.width, h - 1f))
                }
            }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectSafe(
    l: Float, t: Float, w: Float, h: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    if (w <= 0f || h <= 0f) return
    drawContext.canvas.save()
    drawContext.canvas.clipRect(l, t, l + w, t + h)
    block()
    drawContext.canvas.restore()
}

/** Maps linear peak to a 0..1 meter position over a -48..+3 dB range. */
fun meterScale(peak: Float): Float {
    if (peak <= 1e-5f) return 0f
    val db = 20f * kotlin.math.log10(peak)
    return ((db + 48f) / 51f).coerceIn(0f, 1f)
}

/** Vertical fader: drag or tap to set 0..1. */
@Composable
fun VerticalFader(
    value: Float,
    onChange: (Float) -> Unit,
    onEnd: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    description: String = "Volume",
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val end by rememberUpdatedState(onEnd)
    Canvas(
        modifier
            .semantics { contentDescription = "$description ${(value * 100).toInt()}%" }
            .pointerInput(Unit) {
                detectTapGestures { change((1f - it.y / size.height).coerceIn(0f, 1f)); end() }
            }
            .pointerInput(Unit) {
                var v = 0f
                detectDragGestures(
                    onDragStart = { v = current },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                ) { c, drag ->
                    c.consume()
                    v = (v - drag.y / size.height).coerceIn(0f, 1f)
                    change(v)
                }
            },
    ) {
        val trackW = 6.dp.toPx()
        val cx = size.width / 2f
        drawRoundRect(Tv.GridDark, Offset(cx - trackW / 2, 0f), Size(trackW, size.height), CornerRadius(trackW / 2))
        val y = size.height * (1f - current)
        drawRoundRect(color.copy(alpha = 0.55f), Offset(cx - trackW / 2, y), Size(trackW, size.height - y), CornerRadius(trackW / 2))
        val capH = 14.dp.toPx()
        val capW = size.width * 0.8f
        val capY = (y - capH / 2).coerceIn(0f, size.height - capH)
        drawRoundRect(Tv.Text, Offset(cx - capW / 2, capY), Size(capW, capH), CornerRadius(3.dp.toPx()))
        drawLine(Tv.Background, Offset(cx - capW / 2 + 3f, capY + capH / 2), Offset(cx + capW / 2 - 3f, capY + capH / 2), 2f)
    }
}

/** Labelled slider with a value readout. */
@Composable
fun ParamSlider(
    name: String,
    value: Float,
    display: String,
    onChange: (Float) -> Unit,
    onEnd: () -> Unit,
    color: Color = Tv.Accent,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = Tv.TextDim)
            Text(display, style = MaterialTheme.typography.labelMedium, color = Tv.Text, fontFamily = Tv.Mono)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onEnd,
            modifier = Modifier.height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Tv.SurfaceHigh,
            ),
        )
    }
}

/** Row of mutually exclusive options. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Tv.Accent,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Tv.Outline, RoundedCornerShape(8.dp)),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(if (on) color else Tv.Surface)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Tv.Background else Tv.TextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Tv.Accent,
    )
}

@Composable
fun Waveform(peaks: FloatArray?, color: Color, modifier: Modifier = Modifier, progress: () -> Float = { -1f }) {
    Canvas(modifier.background(Tv.GridDark, RoundedCornerShape(6.dp))) {
        val mid = size.height / 2f
        if (peaks == null || peaks.isEmpty()) {
            drawLine(Tv.Outline, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        } else {
            val step = size.width / peaks.size
            val w = maxOf(1f, step - 0.5f)
            peaks.forEachIndexed { i, p ->
                val h = (p.coerceIn(0f, 1f) * (size.height / 2f - 2f)).coerceAtLeast(0.5f)
                drawRect(color, Offset(i * step, mid - h), Size(w, h * 2))
            }
        }
        val pr = progress()
        if (pr in 0f..1f) {
            drawLine(Tv.Text, Offset(size.width * pr, 0f), Offset(size.width * pr, size.height), 2.dp.toPx())
        }
    }
}

@Composable
fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
