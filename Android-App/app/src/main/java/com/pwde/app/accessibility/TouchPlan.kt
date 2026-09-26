package com.pwde.app.accessibility

/**
 * Which fingers one dispatch of the touch chain carries. Pure, so the sequencing rules that broke on
 * device are unit-tested instead of hoped for ([TouchPlanTest]).
 *
 * The rules, each learned from a real failure on MLBB (2026-09-26):
 * 1. **A segment never lifts a finger and presses one at the same time.** The framework refuses
 *    exactly that gesture (`Segment cancelled by the system`) and delivers nothing from it.
 * 2. **Every finger that is down is in the next segment**, either continued or lifted: a stroke
 *    dispatched with `willContinue` must be continued by the very next gesture, or the chain is dead.
 * 3. **A finger only ever starts into an empty segment.** That is what makes a button press go down
 *    as a plain `ACTION_DOWN` on pointer 0 — the shape a tap has in cursor mode, which is the only
 *    one the game acts on. This is also why nothing new starts in a segment that lifts something.
 * 4. **A queued finger counts as work** even with nothing down (`hasWork`): forgetting it left the
 *    chain idle forever once, which stopped every gesture, the joystick included.
 */
internal object TouchPlan {

    /** A finger that is currently down. */
    data class Down(val name: String, val releasing: Boolean, val expired: Boolean)

    /** One segment: [lifted] and [continued] together cover [down]; [start] are the new fingers. */
    data class Segment(val start: List<String>, val continued: List<String>, val lifted: List<String>) {
        val isEmpty: Boolean get() = start.isEmpty() && continued.isEmpty() && lifted.isEmpty()
    }

    /** True when there is anything at all for the chain to do. */
    fun hasWork(down: List<Down>, waiting: List<String>): Boolean =
        down.isNotEmpty() || waiting.isNotEmpty()

    fun forSegment(down: List<Down>, waiting: List<String>, maxFingers: Int): Segment {
        val lifted = down.filter { it.releasing || it.expired }.map { it.name }
        val continued = down.filterNot { it.releasing || it.expired }.map { it.name }
        // Rule 1 + 3: while anything is down, this segment only continues and lifts it.
        if (down.isNotEmpty()) return Segment(start = emptyList(), continued = continued, lifted = lifted)
        return Segment(start = waiting.take(maxFingers), continued = emptyList(), lifted = emptyList())
    }
}
