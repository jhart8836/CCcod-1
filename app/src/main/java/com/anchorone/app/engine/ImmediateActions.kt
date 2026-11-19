package com.anchorone.app.engine

/**
 * The very first step of a pathway: a physical, minimal-thought action.
 * Each is one short sentence plus an optional short note. Progression is by tap.
 */
data class ImmediateAction(val id: String, val instruction: String, val note: String)

object ImmediateActions {

    private val byExperience: Map<Experience, List<ImmediateAction>> = mapOf(
        Experience.PHYSICAL_ALARM to listOf(
            ImmediateAction("pa_feet", "Press your feet into the floor.", "Feel the floor push back. Tap when you've done it."),
            ImmediateAction("pa_hands", "Put one hand flat on something solid.", "A table, a wall, your knee. Tap when it's there."),
            ImmediateAction("pa_cold", "Hold something cool, if it's nearby.", "A glass, a metal surface, a window. Or just tap to go on."),
            ImmediateAction("pa_drop", "Let your shoulders drop a little.", "Nothing more than that. Tap when ready."),
        ),
        Experience.THOUGHT_SPIRAL to listOf(
            ImmediateAction("ts_name", "Say to yourself: \"This is a thought spiral.\"", "Naming it is enough for now. Tap to go on."),
            ImmediateAction("ts_look", "Look up from the screen at the furthest point you can see.", "Stay there for a moment, then tap."),
            ImmediateAction("ts_count", "Name three colours you can see right now.", "Out loud or in your head. Tap when done."),
        ),
        Experience.DISCONNECTED to listOf(
            ImmediateAction("dc_edges", "Feel the edges and corners of your phone.", "Run a finger along them. Tap when you've felt all four."),
            ImmediateAction("dc_name", "Say where you are, out loud or silently.", "The room, the street, the building. Tap to go on."),
            ImmediateAction("dc_press", "Press your palms together firmly for a moment.", "Then let go. Tap when done."),
        ),
        Experience.OVERWHELMED to listOf(
            ImmediateAction("ov_putdown", "Put down anything you're holding except this phone.", "Tap when your hands are free."),
            ImmediateAction("ov_one", "You only need to do one thing right now: tap this screen.", "That's it."),
            ImmediateAction("ov_turn", "If you can, turn away from whatever is loudest.", "Or just tap to go on."),
        ),
        Experience.JUST_START to listOf(
            ImmediateAction("js_tap", "Tap anywhere to begin.", "Nothing to decide."),
        ),
    )

    fun forExperience(experience: Experience): List<ImmediateAction> = byExperience.getValue(experience)

    /** Deterministic rotation so repeated sessions don't always open with the same words. */
    fun pick(experience: Experience, rotation: Int): ImmediateAction {
        val list = forExperience(experience)
        return list[Math.floorMod(rotation, list.size)]
    }
}
