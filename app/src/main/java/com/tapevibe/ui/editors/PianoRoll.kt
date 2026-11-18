package com.tapevibe.ui.editors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.model.InstrumentParams
import com.tapevibe.model.Note
import com.tapevibe.model.PPQ
import com.tapevibe.model.ProjectEdits
import com.tapevibe.model.STEP_TICKS
import com.tapevibe.model.TICKS_PER_BAR
import com.tapevibe.model.Track
import com.tapevibe.ui.LiveState
import com.tapevibe.ui.components.Segmented
import com.tapevibe.ui.theme.Tv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TOP_PITCH = 96   // C7
private const val BOTTOM_PITCH = 24 // C1
private const val ROWS = TOP_PITCH - BOTTOM_PITCH + 1
private val RowH = 22.dp
private val StepW = 30.dp
private val KeyW = 48.dp

private val lengthOptions = listOf("1/16" to STEP_TICKS, "1/8" to PPQ / 2, "1/4" to PPQ, "1/2" to PPQ * 2, "1 bar" to TICKS_PER_BAR)

interface PianoRollActions {
    fun toggle(tick: Int, pitch: Int, length: Int)
    fun resize(note: Note, length: Int, gesture: Int)
    fun move(note: Note, start: Int, pitch: Int, gesture: Int)
    fun endGesture()
    fun previewOn(pitch: Int)
    fun previewOff(pitch: Int)
    fun transpose(semitones: Int)
    fun clear()
}

/**
 * Piano roll. Tap empty space to add a note, tap a note to delete it.
 * Long-press a note then drag: from its right end resizes, elsewhere moves it.
 */
@Composable
fun PianoRoll(
    track: Track,
    loopTicks: Int,
    live: LiveState,
    playing: Boolean,
    actions: PianoRollActions,
) {
    val color = Tv.trackColor(track.colorIndex)
    var lengthIdx by rememberSaveable { mutableIntStateOf(0) }
    val notes by rememberUpdatedState(track.notes)
    val noteLength by rememberUpdatedState(lengthOptions[lengthIdx].second)
    val loop by rememberUpdatedState(loopTicks)
    val act by rememberUpdatedState(actions)
    val isPlaying by rememberUpdatedState(playing)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val vScroll = remember(track.id) {
        val focus = track.notes.map { it.pitch }.sorted().let { if (it.isEmpty()) 60 else it[it.size / 2] }
        val px = with(density) { ((TOP_PITCH - focus) * RowH.toPx() - 220.dp.toPx()).roundToInt() }
        ScrollState(px.coerceAtLeast(0))
    }
    val hScroll = rememberScrollState()
    val steps = loopTicks / STEP_TICKS
    var gestureId by remember { mutableIntStateOf(0) }

    fun audition(pitch: Int) {
        act.previewOn(pitch)
        scope.launch { delay(220); act.previewOff(pitch) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Segmented(lengthOptions.map { it.first }, lengthIdx, { lengthIdx = it }, Modifier.weight(1f), color)
            IconButton(onClick = { act.transpose(12) }) { Icon(Icons.Default.KeyboardArrowUp, "Octave up") }
            IconButton(onClick = { act.transpose(-12) }) { Icon(Icons.Default.KeyboardArrowDown, "Octave down") }
            IconButton(onClick = { act.clear() }) { Icon(Icons.Default.DeleteSweep, "Clear notes") }
        }

        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
                // Keyboard
                Column(Modifier.width(KeyW)) {
                    for (pitch in TOP_PITCH downTo BOTTOM_PITCH) {
                        val black = InstrumentParams.isBlackKey(pitch)
                        Box(
                            Modifier
                                .size(KeyW, RowH)
                                .background(if (black) Tv.Background else Tv.Text.copy(alpha = 0.88f))
                                .border(0.5.dp, Tv.Outline)
                                .pointerInput(pitch) {
                                    detectTapGestures(onPress = {
                                        act.previewOn(pitch)
                                        tryAwaitRelease()
                                        act.previewOff(pitch)
                                    })
                                },
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            if (pitch % 12 == 0) {
                                Text(
                                    InstrumentParams.noteName(pitch),
                                    fontSize = 10.sp,
                                    color = Tv.Background,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.horizontalScroll(hScroll)) {
                    Canvas(
                        Modifier
                            .size(StepW * steps, RowH * ROWS)
                            .pointerInput(Unit) {
                                detectTapGestures { o ->
                                    val tick = (o.x / StepW.toPx() * STEP_TICKS).toInt()
                                    val pitch = TOP_PITCH - (o.y / RowH.toPx()).toInt()
                                    val adding = ProjectEdits.noteAt(notes, tick, pitch) == null
                                    act.toggle(tick, pitch, noteLength)
                                    act.endGesture()
                                    if (adding && !isPlaying) audition(pitch)
                                }
                            }
                            .pointerInput(Unit) {
                                var target: Note? = null
                                var resize = false
                                var origin = Offset.Zero
                                var acc = Offset.Zero
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { o ->
                                        val tick = (o.x / StepW.toPx() * STEP_TICKS).toInt()
                                        val pitch = TOP_PITCH - (o.y / RowH.toPx()).toInt()
                                        target = ProjectEdits.noteAt(notes, tick, pitch)
                                        target?.let { n ->
                                            val endX = n.end / STEP_TICKS.toFloat() * StepW.toPx()
                                            resize = o.x > endX - maxOf(StepW.toPx(), (endX - n.start / STEP_TICKS.toFloat() * StepW.toPx()) * 0.35f)
                                            gestureId++
                                            audition(n.pitch)
                                        }
                                        origin = o
                                        acc = Offset.Zero
                                    },
                                    onDragEnd = { target = null; act.endGesture() },
                                    onDragCancel = { target = null; act.endGesture() },
                                ) { change, drag ->
                                    val n = target ?: return@detectDragGesturesAfterLongPress
                                    change.consume()
                                    acc += drag
                                    val stepPx = StepW.toPx()
                                    if (resize) {
                                        val endTick = ((origin.x + acc.x) / stepPx).roundToInt() * STEP_TICKS
                                        val len = endTick - n.start
                                        val updated = ProjectEdits.resized(n, len, loop)
                                        if (updated != n) {
                                            act.resize(n, len, gestureId)
                                            target = updated
                                        }
                                    } else {
                                        val dSteps = (acc.x / stepPx).roundToInt()
                                        val dRows = (acc.y / RowH.toPx()).roundToInt()
                                        if (dSteps != 0 || dRows != 0) {
                                            val start = n.start + dSteps * STEP_TICKS
                                            val pitch = n.pitch - dRows
                                            val updated = ProjectEdits.moved(n, start, pitch, loop)
                                            if (updated != n) {
                                                act.move(n, start, pitch, gestureId)
                                                if (updated.pitch != n.pitch) audition(updated.pitch)
                                                target = updated
                                            }
                                            acc = Offset(acc.x - dSteps * stepPx, acc.y - dRows * RowH.toPx())
                                        }
                                    }
                                }
                            },
                    ) {
                        val sw = StepW.toPx()
                        val rh = RowH.toPx()
                        for (r in 0 until ROWS) {
                            val pitch = TOP_PITCH - r
                            drawRect(
                                if (InstrumentParams.isBlackKey(pitch)) Tv.GridDark else Tv.GridLight,
                                Offset(0f, r * rh), Size(size.width, rh),
                            )
                            if (pitch % 12 == 0) drawLine(Tv.Outline, Offset(0f, (r + 1) * rh), Offset(size.width, (r + 1) * rh), 1.5f)
                        }
                        for (s in 0..steps) {
                            val x = s * sw
                            val (c, w) = when {
                                s % 16 == 0 -> Tv.Accent.copy(alpha = 0.45f) to 2f
                                s % 4 == 0 -> Tv.Outline to 1.5f
                                else -> Tv.Outline.copy(alpha = 0.4f) to 1f
                            }
                            drawLine(c, Offset(x, 0f), Offset(x, size.height), w)
                        }
                        val radius = CornerRadius(4.dp.toPx())
                        notes.forEach { n ->
                            if (n.pitch !in BOTTOM_PITCH..TOP_PITCH) return@forEach
                            val x = n.start / STEP_TICKS.toFloat() * sw
                            val w = n.length / STEP_TICKS.toFloat() * sw
                            val y = (TOP_PITCH - n.pitch) * rh
                            drawRoundRect(color.copy(alpha = 0.45f + 0.55f * n.velocity), Offset(x + 1f, y + 1f), Size(w - 2f, rh - 2f), radius)
                            drawRoundRect(Tv.Background.copy(alpha = 0.5f), Offset(x + 1f, y + 1f), Size(w - 2f, rh - 2f), radius, style = Stroke(1.5f))
                            drawRect(Tv.Background.copy(alpha = 0.35f), Offset(x + w - 5.dp.toPx(), y + 5.dp.toPx()), Size(2.dp.toPx(), rh - 10.dp.toPx()))
                        }
                        if (isPlaying) {
                            val px = live.ticks / STEP_TICKS * sw
                            drawLine(Tv.Text, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Tv.TextDim)
    }
}
