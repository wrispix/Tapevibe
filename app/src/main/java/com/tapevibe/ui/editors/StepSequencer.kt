package com.tapevibe.ui.editors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.model.DRUM_BASE_NOTE
import com.tapevibe.model.DRUM_LANES
import com.tapevibe.model.InstrumentParams
import com.tapevibe.model.STEP_TICKS
import com.tapevibe.model.Track
import com.tapevibe.ui.LiveState
import com.tapevibe.ui.theme.Tv

private val CellW = 34.dp
private val CellH = 42.dp
private val LabelW = 76.dp

/** 8-lane drum step grid. Tap toggles a step; long-press cycles its velocity. */
@Composable
fun StepSequencer(
    track: Track,
    bars: Int,
    live: LiveState,
    playing: Boolean,
    onToggle: (step: Int, lane: Int) -> Unit,
    onVelocity: (step: Int, lane: Int, velocity: Float) -> Unit,
    onPreview: (lane: Int) -> Unit,
) {
    val steps = bars * 16
    val color = Tv.trackColor(track.colorIndex)
    val notes by rememberUpdatedState(track.notes)
    val toggle by rememberUpdatedState(onToggle)
    val velocity by rememberUpdatedState(onVelocity)
    val preview by rememberUpdatedState(onPreview)
    val isPlaying by rememberUpdatedState(playing)
    val playStep by remember(live) { derivedStateOf { (live.ticks / STEP_TICKS).toInt() } }
    val hScroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Tap a step to toggle · long-press for accent / soft · tap a name to audition",
            style = MaterialTheme.typography.labelSmall,
            color = Tv.TextDim,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.width(LabelW)) {
                Box(Modifier.height(18.dp))
                InstrumentParams.drumLaneNames.forEachIndexed { lane, name ->
                    Box(
                        Modifier
                            .size(LabelW, CellH)
                            .pointerInput(lane) { detectTapGestures { preview(lane) } }
                            .padding(start = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(name, fontSize = 12.sp, color = Tv.Text, maxLines = 1)
                    }
                }
            }
            Box(Modifier.horizontalScroll(hScroll)) {
                Column {
                    // Step numbers
                    Canvas(Modifier.size(CellW * steps, 18.dp)) {
                        val cw = CellW.toPx()
                        for (s in 0 until steps step 4) {
                            drawRect(
                                if ((s / 4) % 2 == 0) Tv.GridLight else Tv.GridDark,
                                Offset(s * cw, 0f), Size(cw * 4, size.height),
                            )
                        }
                        if (isPlaying) {
                            val ps = playStep % steps
                            drawRect(color.copy(alpha = 0.8f), Offset(ps * cw + 2f, size.height - 4.dp.toPx()), Size(cw - 4f, 3.dp.toPx()))
                        }
                    }
                    Canvas(
                        Modifier
                            .size(CellW * steps, CellH * DRUM_LANES)
                            .pointerInput(steps) {
                                fun cell(o: Offset) = Pair(
                                    (o.x / CellW.toPx()).toInt().coerceIn(0, steps - 1),
                                    (o.y / CellH.toPx()).toInt().coerceIn(0, DRUM_LANES - 1),
                                )
                                detectTapGestures(
                                    onTap = { o ->
                                        val (s, l) = cell(o)
                                        val adding = notes.none { it.start == s * STEP_TICKS && it.pitch == DRUM_BASE_NOTE + l }
                                        toggle(s, l)
                                        if (adding && !isPlaying) preview(l)
                                    },
                                    onLongPress = { o ->
                                        val (s, l) = cell(o)
                                        val n = notes.firstOrNull { it.start == s * STEP_TICKS && it.pitch == DRUM_BASE_NOTE + l }
                                        val next = when {
                                            n == null -> 1f
                                            n.velocity >= 0.95f -> 0.45f
                                            n.velocity < 0.6f -> 0.8f
                                            else -> 1f
                                        }
                                        velocity(s, l, next)
                                    },
                                )
                            },
                    ) {
                        val cw = CellW.toPx()
                        val ch = CellH.toPx()
                        val pad = 3.dp.toPx()
                        val r = CornerRadius(5.dp.toPx())
                        for (s in 0 until steps) {
                            val bg = if ((s / 4) % 2 == 0) Tv.GridLight else Tv.GridDark
                            drawRect(bg, Offset(s * cw, 0f), Size(cw, size.height))
                        }
                        for (s in 0 until steps) for (l in 0 until DRUM_LANES) {
                            drawRoundRect(Tv.SurfaceHigh, Offset(s * cw + pad, l * ch + pad), Size(cw - 2 * pad, ch - 2 * pad), r)
                        }
                        notes.forEach { n ->
                            val l = n.pitch - DRUM_BASE_NOTE
                            if (l !in 0 until DRUM_LANES || n.start % STEP_TICKS != 0) return@forEach
                            val s = n.start / STEP_TICKS
                            if (s >= steps) return@forEach
                            drawRoundRect(
                                color.copy(alpha = 0.3f + 0.7f * n.velocity.coerceIn(0f, 1f)),
                                Offset(s * cw + pad, l * ch + pad), Size(cw - 2 * pad, ch - 2 * pad), r,
                            )
                            if (n.velocity >= 0.95f) {
                                drawCircle(Tv.Background, 3.dp.toPx(), Offset(s * cw + cw / 2, l * ch + ch / 2))
                            }
                        }
                        for (bar in 1 until steps / 16) {
                            drawLine(Tv.Accent.copy(alpha = 0.5f), Offset(bar * 16 * cw, 0f), Offset(bar * 16 * cw, size.height), 2f)
                        }
                        if (isPlaying) {
                            val ps = playStep % steps
                            drawRect(Tv.Text.copy(alpha = 0.10f), Offset(ps * cw, 0f), Size(cw, size.height))
                        }
                    }
                }
            }
        }
    }
}
