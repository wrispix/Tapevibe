package com.tapevibe.model

import kotlinx.serialization.Serializable

/** Sequencer resolution; must match kPPQ in the native engine. */
const val PPQ = 96
const val STEP_TICKS = PPQ / 4
const val TICKS_PER_BAR = PPQ * 4
const val MAX_TRACKS = 16
const val DRUM_BASE_NOTE = 36
const val DRUM_LANES = 8

@Serializable
enum class TrackType(val nativeId: Int, val label: String) {
    SYNTH(0, "Synth"),
    DRUMS(1, "Drums"),
    SAMPLER(2, "Sampler"),
    AUDIO(3, "Audio"),
}

@Serializable
data class Note(
    val start: Int,
    val length: Int,
    val pitch: Int,
    val velocity: Float = 0.8f,
) {
    val end: Int get() = start + length
}

@Serializable
data class Track(
    val id: Long,
    val name: String,
    val type: TrackType,
    val colorIndex: Int = 0,
    val params: List<Float> = InstrumentParams.defaults(type),
    val notes: List<Note> = emptyList(),
    val volume: Float = 0.8f,
    val pan: Float = 0f,
    val send: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    /** File name inside the project's samples directory (sampler sample or audio clip). */
    val sampleFile: String? = null,
)

@Serializable
data class Project(
    val name: String = "Untitled",
    val bpm: Float = 120f,
    val swing: Float = 0f,
    val bars: Int = 1,
    val masterVolume: Float = 0.8f,
    val metronome: Boolean = false,
    val delayDivision: Int = 2,
    val delayFeedback: Float = 0.35f,
    val delayDamping: Float = 0.4f,
    val tracks: List<Track> = emptyList(),
    val nextTrackId: Long = 1,
    val formatVersion: Int = 1,
) {
    val loopTicks: Int get() = bars * TICKS_PER_BAR
    val loopSeconds: Float get() = bars * 4 * 60f / bpm

    fun track(id: Long): Track? = tracks.firstOrNull { it.id == id }
}

val DELAY_DIVISIONS = listOf("1/16", "1/8", "1/8.", "1/4", "1/4.", "1/2")
val BAR_OPTIONS = listOf(1, 2, 4, 8)
