package com.tapevibe.model

/** Pure editing operations on the project model (unit-tested, used by the ViewModel). */
object ProjectEdits {

    fun updateTrack(p: Project, id: Long, f: (Track) -> Track): Project =
        p.copy(tracks = p.tracks.map { if (it.id == id) f(it) else it })

    fun addTrack(p: Project, type: TrackType, name: String? = null): Project {
        if (p.tracks.size >= MAX_TRACKS) return p
        val id = p.nextTrackId
        val count = p.tracks.count { it.type == type } + 1
        val t = Track(
            id = id,
            name = name ?: "${type.label} $count",
            type = type,
            colorIndex = (p.tracks.size) % 8,
        )
        return p.copy(tracks = p.tracks + t, nextTrackId = id + 1)
    }

    fun removeTrack(p: Project, id: Long): Project = p.copy(tracks = p.tracks.filterNot { it.id == id })

    fun duplicateTrack(p: Project, id: Long): Project {
        if (p.tracks.size >= MAX_TRACKS) return p
        val src = p.track(id) ?: return p
        val idx = p.tracks.indexOf(src)
        val copy = src.copy(id = p.nextTrackId, name = "${src.name} copy", solo = false)
        val list = p.tracks.toMutableList().apply { add(idx + 1, copy) }
        return p.copy(tracks = list, nextTrackId = p.nextTrackId + 1)
    }

    fun moveTrack(p: Project, id: Long, delta: Int): Project {
        val idx = p.tracks.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in p.tracks.indices) return p
        val list = p.tracks.toMutableList()
        list.add(target, list.removeAt(idx))
        return p.copy(tracks = list)
    }

    /** Drum step toggle. Returns the new note list. */
    fun toggleStep(notes: List<Note>, step: Int, lane: Int, velocity: Float = 0.8f): List<Note> {
        val pitch = DRUM_BASE_NOTE + lane
        val start = step * STEP_TICKS
        val existing = notes.firstOrNull { it.pitch == pitch && it.start == start }
        return if (existing != null) notes - existing
        else (notes + Note(start, STEP_TICKS, pitch, velocity)).sortedBy { it.start }
    }

    fun setStepVelocity(notes: List<Note>, step: Int, lane: Int, velocity: Float): List<Note> {
        val pitch = DRUM_BASE_NOTE + lane
        val start = step * STEP_TICKS
        val existing = notes.firstOrNull { it.pitch == pitch && it.start == start }
            ?: return (notes + Note(start, STEP_TICKS, pitch, velocity)).sortedBy { it.start }
        return notes.map { if (it === existing) it.copy(velocity = velocity) else it }
    }

    fun noteAt(notes: List<Note>, tick: Int, pitch: Int): Note? =
        notes.lastOrNull { it.pitch == pitch && tick >= it.start && tick < it.end }

    /** Piano-roll tap: remove the note under the tick, or add one snapped to the step grid. */
    fun toggleNote(notes: List<Note>, tick: Int, pitch: Int, length: Int, loopTicks: Int): List<Note> {
        noteAt(notes, tick, pitch)?.let { return notes - it }
        val start = (tick / STEP_TICKS) * STEP_TICKS
        if (start >= loopTicks) return notes
        val len = length.coerceAtMost(loopTicks - start).coerceAtLeast(STEP_TICKS)
        return (notes + Note(start, len, pitch)).sortedBy { it.start }
    }

    fun resized(note: Note, newLength: Int, loopTicks: Int): Note =
        note.copy(length = newLength.coerceIn(STEP_TICKS, (loopTicks - note.start).coerceAtLeast(STEP_TICKS)))

    fun moved(note: Note, newStart: Int, newPitch: Int, loopTicks: Int): Note {
        val start = newStart.coerceIn(0, (loopTicks - STEP_TICKS).coerceAtLeast(0))
        return note.copy(
            start = start,
            pitch = newPitch.coerceIn(0, 127),
            length = note.length.coerceAtMost((loopTicks - start).coerceAtLeast(STEP_TICKS)),
        )
    }

    fun resizeNote(notes: List<Note>, note: Note, newLength: Int, loopTicks: Int): List<Note> {
        val r = resized(note, newLength, loopTicks)
        return notes.map { if (it == note) r else it }
    }

    fun moveNote(notes: List<Note>, note: Note, newStart: Int, newPitch: Int, loopTicks: Int): List<Note> {
        val m = moved(note, newStart, newPitch, loopTicks)
        return notes.map { if (it == note) m else it }.sortedBy { it.start }
    }

    fun transpose(notes: List<Note>, semitones: Int): List<Note> =
        notes.map { it.copy(pitch = (it.pitch + semitones).coerceIn(0, 127)) }

    /** Copies the first `fromBars` bars to fill `toBars` bars (used when the loop is lengthened). */
    fun repeatToFill(notes: List<Note>, fromBars: Int, toBars: Int): List<Note> {
        if (toBars <= fromBars || fromBars <= 0) return notes
        val src = notes.filter { it.start < fromBars * TICKS_PER_BAR }
        val out = src.toMutableList()
        var bar = fromBars
        while (bar < toBars) {
            val offset = bar * TICKS_PER_BAR
            src.forEach { if (it.start + offset < toBars * TICKS_PER_BAR) out += it.copy(start = it.start + offset) }
            bar += fromBars
        }
        return out.sortedBy { it.start }
    }
}
