package com.tapevibe.model

/**
 * Bounded undo/redo stack. Consecutive edits with the same non-null [mergeKey] (e.g. dragging
 * one slider) collapse into a single undo step.
 */
class History<T>(private val limit: Int = 100) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    private var lastKey: String? = null

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Record [before] as the state to return to; call before applying an edit. */
    fun record(before: T, mergeKey: String? = null) {
        if (mergeKey != null && mergeKey == lastKey && undo.isNotEmpty()) return
        undo.addLast(before)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
        lastKey = mergeKey
    }

    fun undo(current: T): T? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastKey = null
        return prev
    }

    fun redo(current: T): T? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastKey = null
        return next
    }

    fun breakMerge() { lastKey = null }

    fun clear() {
        undo.clear(); redo.clear(); lastKey = null
    }
}
