package com.tapevibe.model

object DemoProject {
    private fun drums(): List<Note> {
        val n = mutableListOf<Note>()
        fun hit(step: Int, lane: Int, v: Float = 0.8f) { n += Note(step * STEP_TICKS, STEP_TICKS, DRUM_BASE_NOTE + lane, v) }
        for (bar in 0 until 2) {
            val o = bar * 16
            listOf(0, 6, 10).forEach { hit(o + it, 0, 1f) }
            listOf(4, 12).forEach { hit(o + it, 1, 0.9f) }
            for (s in 0 until 16 step 2) hit(o + s, 3, if (s % 4 == 0) 0.7f else 0.45f)
            hit(o + 14, 4, 0.6f)
        }
        hit(31, 2, 0.8f)
        return n.sortedBy { it.start }
    }

    private fun bass(): List<Note> {
        // A minor groove: A1 . A1 A2 | C2 . G1 .
        val steps = listOf(0 to 45, 3 to 45, 6 to 57, 8 to 48, 11 to 48, 14 to 43,
            16 to 41, 19 to 41, 22 to 53, 24 to 43, 27 to 43, 30 to 55)
        return steps.map { (s, p) -> Note(s * STEP_TICKS, STEP_TICKS * 2 - 4, p, 0.85f) }
    }

    private fun chords(): List<Note> {
        val am = listOf(57, 60, 64)
        val f = listOf(53, 57, 60)
        val out = mutableListOf<Note>()
        am.forEach { out += Note(0, TICKS_PER_BAR, it, 0.6f) }
        f.forEach { out += Note(TICKS_PER_BAR, TICKS_PER_BAR, it, 0.6f) }
        return out
    }

    fun create(name: String = "Demo groove"): Project {
        var p = Project(name = name, bpm = 112f, bars = 2, swing = 0.15f)
        p = ProjectEdits.addTrack(p, TrackType.DRUMS, "Drums")
        p = ProjectEdits.addTrack(p, TrackType.SYNTH, "Bass")
        p = ProjectEdits.addTrack(p, TrackType.SYNTH, "Pad")
        val (d, b, pad) = p.tracks
        return p.copy(
            tracks = listOf(
                d.copy(notes = drums(), volume = 0.85f),
                b.copy(notes = bass(), params = InstrumentParams.synthPresets.getValue("Fat bass"), volume = 0.75f),
                pad.copy(notes = chords(), params = InstrumentParams.synthPresets.getValue("Pad"),
                    volume = 0.55f, send = 0.35f, pan = 0.1f),
            ),
        )
    }

    fun blank(name: String = "New project"): Project =
        ProjectEdits.addTrack(Project(name = name), TrackType.DRUMS, "Drums")
}
