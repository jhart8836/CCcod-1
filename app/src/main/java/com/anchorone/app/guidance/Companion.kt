package com.anchorone.app.guidance

import com.anchorone.app.content.rotatedShuffle
import com.anchorone.app.engine.Experience
import com.anchorone.app.guidance.GuidanceLibrary.Kind

/**
 * Pure STILLPOINT dialogue state machine. It decides *what* to say next in response to
 * explicit commands; [StillpointController] decides *when* and speaks it.
 * Lines cycle through a shuffled order per kind, so nothing repeats until the pool is exhausted,
 * and the same line is never said twice in a row.
 */
class Companion(private val experience: Experience?, private val seed: Int) {

    enum class Verbosity { FULL, LESS }
    enum class Mode { GUIDING, TASK, CLOSING }

    data class Utterance(val text: String, val mode: Mode, val awaitingDone: Boolean = false)

    var verbosity: Verbosity = Verbosity.FULL
        private set
    var mode: Mode = Mode.GUIDING
        private set

    private val cursors = mutableMapOf<Kind, Int>()
    private val orders = mutableMapOf<Kind, List<GuidanceLibrary.Line>>()
    private val lastSaid = mutableMapOf<Kind, GuidanceLibrary.Line>()
    private var cycle = 0

    private fun nextLine(kind: Kind): GuidanceLibrary.Line {
        val pool = GuidanceLibrary.lines(kind, experience)
        var order = orders.getOrPut(kind) { pool.rotatedShuffle(seed + kind.ordinal * 31) }
        var cursor = cursors[kind] ?: 0
        if (cursor >= order.size) {
            cycle++
            order = pool.rotatedShuffle(seed + kind.ordinal * 31 + cycle * 101)
            if (order.size > 1 && order.first() == lastSaid[kind]) {
                order = order.drop(1) + order.first()
            }
            orders[kind] = order
            cursor = 0
        }
        val line = order[cursor]
        cursors[kind] = cursor + 1
        lastSaid[kind] = line
        return line
    }

    private fun text(line: GuidanceLibrary.Line) = if (verbosity == Verbosity.LESS) line.short else line.full

    fun open(): Utterance {
        mode = Mode.GUIDING
        return Utterance(text(nextLine(Kind.OPEN)), mode)
    }

    /** "Keep guiding me." */
    fun keepGuiding(): Utterance {
        mode = Mode.GUIDING
        return Utterance(text(nextLine(Kind.GUIDE)), mode)
    }

    /** "Give me something to do." Waits for Done before guiding continues. */
    fun somethingToDo(): Utterance {
        mode = Mode.TASK
        return Utterance(text(nextLine(Kind.TASK)), mode, awaitingDone = true)
    }

    fun taskDone(): Utterance {
        mode = Mode.GUIDING
        return Utterance(text(nextLine(Kind.ACK_DONE)), mode)
    }

    /** "Less talking." Toggles to short lines and longer gaps. */
    fun lessTalking(): Utterance {
        verbosity = Verbosity.LESS
        return Utterance(text(nextLine(Kind.LESS)), mode, awaitingDone = mode == Mode.TASK)
    }

    fun moreTalking(): Utterance {
        verbosity = Verbosity.FULL
        return Utterance(text(nextLine(Kind.MORE)), mode, awaitingDone = mode == Mode.TASK)
    }

    /** "Switch activity." */
    fun switchActivity(): Utterance {
        mode = Mode.GUIDING
        return Utterance(text(nextLine(Kind.SWITCH)), mode)
    }

    /** "I'm ready to continue my day." */
    fun ready(): Utterance {
        mode = Mode.CLOSING
        return Utterance(text(nextLine(Kind.CLOSE)), mode)
    }

    /** Silence between automatic guidance lines, after speech has finished. */
    fun gapMillis(): Long = if (verbosity == Verbosity.LESS) 22_000L else 9_000L

    companion object {
        /** Rough reading/speaking time when TTS is unavailable or can't report progress. */
        fun estimatedSpeechMillis(text: String): Long =
            (text.split(' ').count { it.isNotBlank() } * 420L).coerceIn(1_200L, 12_000L)
    }
}
