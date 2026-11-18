package com.tapevibe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.tapevibe.model.MAX_TRACKS

/**
 * Per-frame engine readouts (playhead and meters). Composables should read these inside
 * draw lambdas so that only drawing, not composition, runs every frame.
 */
class LiveState {
    var ticks by mutableFloatStateOf(0f)
    var frame by mutableIntStateOf(0)
    val levels = FloatArray(2 * (MAX_TRACKS + 1))

    fun level(index: Int): Float {
        @Suppress("UNUSED_VARIABLE") val f = frame  // subscribe reader to frame updates
        return levels.getOrElse(index) { 0f }
    }

    fun trackLevel(slot: Int, right: Boolean) = level(2 * slot + if (right) 1 else 0)
    fun masterLevel(right: Boolean) = level(2 * MAX_TRACKS + if (right) 1 else 0)
}

@Composable
fun rememberLiveState(vm: DawViewModel): LiveState {
    val state = remember { LiveState() }
    LaunchedEffect(vm) {
        while (true) {
            withFrameNanos { }
            state.ticks = vm.positionTicks().toFloat()
            val peaks = vm.readPeaks()
            for (i in state.levels.indices) state.levels[i] = maxOf(peaks[i], state.levels[i] * 0.86f)
            state.frame++
        }
    }
    return state
}
