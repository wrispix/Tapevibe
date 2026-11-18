package com.tapevibe.model

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Parameter layout for each instrument. Indexes and defaults mirror Instruments.h /
 * Engine.cpp in the native engine. All stored values are normalised to 0..1.
 */
data class ParamSpec(
    val index: Int,
    val name: String,
    val default: Float,
    /** Number of discrete choices, or 0 for a continuous parameter. */
    val choices: List<String> = emptyList(),
    val format: (Float) -> String = { "${(it * 100).roundToInt()}%" },
    val group: String = "",
)

object InstrumentParams {
    private val waves = listOf("Saw", "Square", "Tri", "Sine")

    fun timeLabel(v: Float): String {
        val s = 0.001f * 5000f.pow(v)
        return if (s < 1f) "${(s * 1000).roundToInt()} ms" else "%.2f s".format(s)
    }

    private fun hz(v: Float): String {
        val f = 20f * 900f.pow(v)
        return if (f < 1000f) "${f.roundToInt()} Hz" else "%.1f kHz".format(f / 1000f)
    }

    val synth = listOf(
        ParamSpec(0, "Osc 1", 0f, waves, group = "Oscillators"),
        ParamSpec(1, "Osc 2", 0f, waves, group = "Oscillators"),
        ParamSpec(2, "Osc 2 pitch", 0.5f, format = { "${(it * 48 - 24).roundToInt()} st" }, group = "Oscillators"),
        ParamSpec(3, "Osc 2 fine", 0.56f, format = { "${(it * 100 - 50).roundToInt()} ct" }, group = "Oscillators"),
        ParamSpec(4, "Osc mix", 0.4f, group = "Oscillators"),
        ParamSpec(5, "Cutoff", 0.55f, format = ::hz, group = "Filter"),
        ParamSpec(6, "Resonance", 0.3f, group = "Filter"),
        ParamSpec(7, "Env amount", 0.35f, format = { "%.1f oct".format(it * 6) }, group = "Filter"),
        ParamSpec(12, "Filter decay", 0.45f, format = ::timeLabel, group = "Filter"),
        ParamSpec(8, "Attack", 0.05f, format = ::timeLabel, group = "Amp"),
        ParamSpec(9, "Decay", 0.45f, format = ::timeLabel, group = "Amp"),
        ParamSpec(10, "Sustain", 0.6f, group = "Amp"),
        ParamSpec(11, "Release", 0.4f, format = ::timeLabel, group = "Amp"),
        ParamSpec(13, "Level", 0.8f, group = "Amp"),
        ParamSpec(14, "LFO rate", 0.4f, format = { "%.2f Hz".format(0.05f * 400f.pow(it)) }, group = "LFO"),
        ParamSpec(15, "LFO → cutoff", 0f, format = { "%.1f oct".format(it * 3) }, group = "LFO"),
    )

    val drums = listOf(
        ParamSpec(0, "Kick tune", 0.4f, group = "Kick"),
        ParamSpec(1, "Kick decay", 0.45f, group = "Kick"),
        ParamSpec(2, "Snare tone", 0.5f, group = "Snare"),
        ParamSpec(3, "Snare decay", 0.4f, group = "Snare"),
        ParamSpec(4, "Hat decay", 0.3f, group = "Hats & toms"),
        ParamSpec(5, "Tom tune", 0.5f, group = "Hats & toms"),
        ParamSpec(6, "Level", 0.8f, group = "Output"),
        ParamSpec(7, "Drive", 0.2f, group = "Output"),
    )

    val sampler = listOf(
        ParamSpec(0, "Root note", 60f / 127f, format = { noteName((it * 127).roundToInt()) }, group = "Pitch"),
        ParamSpec(3, "Tune", 0.5f, format = { "${(it * 24 - 12).roundToInt()} st" }, group = "Pitch"),
        ParamSpec(1, "Attack", 0f, format = { timeLabel(it * 0.85f) }, group = "Envelope"),
        ParamSpec(2, "Release", 0.35f, format = ::timeLabel, group = "Envelope"),
        ParamSpec(4, "Level", 0.8f, group = "Envelope"),
    )

    val audio = listOf(
        ParamSpec(0, "Clip level", 0.8f, group = "Clip"),
    )

    fun specs(type: TrackType): List<ParamSpec> = when (type) {
        TrackType.SYNTH -> synth
        TrackType.DRUMS -> drums
        TrackType.SAMPLER -> sampler
        TrackType.AUDIO -> audio
    }

    /** Dense list indexed by native parameter index. */
    fun defaults(type: TrackType): List<Float> {
        val specs = specs(type)
        val size = (specs.maxOf { it.index } + 1)
        return List(size) { i -> specs.firstOrNull { it.index == i }?.default ?: 0f }
    }

    val drumLaneNames = listOf("Kick", "Snare", "Clap", "Closed hat", "Open hat", "Low tom", "High tom", "Rim")

    private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    fun noteName(pitch: Int): String = "${names[((pitch % 12) + 12) % 12]}${pitch / 12 - 1}"
    fun isBlackKey(pitch: Int): Boolean = names[((pitch % 12) + 12) % 12].length > 1

    /** Synth starting points; indexes follow the native layout (0..15). */
    val synthPresets: Map<String, List<Float>> = mapOf(
        "Init" to defaults(TrackType.SYNTH),
        "Fat bass" to listOf(0f, 1f, 0.25f, 0.53f, 0.5f, 0.32f, 0.35f, 0.5f, 0f, 0.4f, 0.5f, 0.25f, 0.4f, 0.85f, 0.4f, 0f),
        "Acid" to listOf(0f, 0f, 0.5f, 0.5f, 0f, 0.28f, 0.85f, 0.7f, 0f, 0.4f, 0.3f, 0.2f, 0.42f, 0.75f, 0.4f, 0f),
        "Lead" to listOf(1f, 0f, 0.75f, 0.54f, 0.5f, 0.62f, 0.4f, 0.3f, 0.1f, 0.5f, 0.75f, 0.42f, 0.5f, 0.7f, 0.55f, 0.05f),
        "Pad" to listOf(0f, 0f, 0.5f, 0.6f, 0.5f, 0.5f, 0.2f, 0.15f, 0.72f, 0.7f, 0.85f, 0.75f, 0.7f, 0.65f, 0.25f, 0.2f),
        "Pluck" to listOf(0f, 1f, 0.75f, 0.52f, 0.3f, 0.35f, 0.4f, 0.65f, 0f, 0.42f, 0f, 0.42f, 0.38f, 0.8f, 0.4f, 0f),
        "Sub" to listOf(3f / 3f, 2f / 3f, 0.25f, 0.5f, 0.2f, 0.4f, 0.1f, 0f, 0.02f, 0.5f, 1f, 0.3f, 0.4f, 0.9f, 0.4f, 0f),
    )
}
