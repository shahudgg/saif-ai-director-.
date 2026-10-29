package com.example.ui.components

import java.util.LinkedList

/**
 * Robust history manager for code editor undo and redo operations.
 * Tracks text modifications with intelligent debounce and change-boundary detection
 * to provide a natural, responsive IDE editing experience.
 */
class EditorHistory(
    initialText: String,
    private val maxHistorySize: Int = 100
) {
    private val undoStack = LinkedList<String>()
    private val redoStack = LinkedList<String>()
    private var lastRecordedTime: Long = 0L

    init {
        // Initialize with base state
        undoStack.add(initialText)
    }

    /**
     * Check if an undo operation is available.
     * We require at least 2 items in undoStack because the bottom-most item is the base state.
     */
    fun canUndo(): Boolean = undoStack.size > 1

    /**
     * Check if a redo operation is available.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Records an incremental text change.
     * Determines whether to create a new snapshot based on word boundaries,
     * large edits (paste/cut), or elapsed typing delay.
     */
    fun recordChange(previousText: String, newText: String) {
        if (previousText == newText) return

        val now = System.currentTimeMillis()
        val lengthDiff = Math.abs(newText.length - previousText.length)
        val isSignificant = lengthDiff > 1 ||
                newText.endsWith("\n") ||
                newText.endsWith(" ") ||
                newText.endsWith(";") ||
                newText.endsWith("}") ||
                (now - lastRecordedTime > 750L) ||
                undoStack.size <= 1

        if (isSignificant) {
            // Push previousText if it's different from the top of the stack
            if (undoStack.isEmpty() || undoStack.lastOrNull() != previousText) {
                undoStack.addLast(previousText)
                if (undoStack.size > maxHistorySize) {
                    undoStack.removeFirst()
                }
            }
            lastRecordedTime = now
        }
        // Any new user modification invalidates redo history
        redoStack.clear()
    }

    /**
     * Force a snapshot checkpoint immediately (e.g., before pasting, formatting, or AI assistance).
     */
    fun pushCheckpoint(currentText: String) {
        if (undoStack.isEmpty() || undoStack.lastOrNull() != currentText) {
            undoStack.addLast(currentText)
            if (undoStack.size > maxHistorySize) {
                undoStack.removeFirst()
            }
        }
        redoStack.clear()
        lastRecordedTime = System.currentTimeMillis()
    }

    /**
     * Undo the last change.
     * Pushes [currentText] to redoStack and returns the previous text state.
     */
    fun undo(currentText: String): String? {
        if (!canUndo()) return null

        val previous = undoStack.removeLast()
        redoStack.addLast(currentText)
        return previous
    }

    /**
     * Redo the previously undone change.
     * Pushes [currentText] to undoStack and returns the next text state.
     */
    fun redo(currentText: String): String? {
        if (!canRedo()) return null

        val next = redoStack.removeLast()
        undoStack.addLast(currentText)
        return next
    }

    /**
     * Reset history with new text (e.g., when loading a new file).
     */
    fun reset(newInitialText: String) {
        undoStack.clear()
        redoStack.clear()
        undoStack.add(newInitialText)
        lastRecordedTime = 0L
    }
}
