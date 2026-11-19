package com.anchorone.app.engine

/**
 * Pathway definitions. Each experience lists activities in priority order for the
 * stabilise and follow-up phases. Order is the starting point; ratings,
 * favourites, preferences and energy then adjust it transparently.
 */
object ActivityCatalog {

    data class Pathway(
        val experience: Experience,
        val stabilize: List<ActivityId>,
        val followUp: List<ActivityId>,
        val summary: String,
    )

    val pathways: Map<Experience, Pathway> = listOf(
        Pathway(
            Experience.PHYSICAL_ALARM,
            stabilize = listOf(ActivityId.ORBIT, ActivityId.FLOW_FIELD, ActivityId.NEXT_OBJECT, ActivityId.SINGLE_THREAD),
            followUp = listOf(ActivityId.ACCEPTANCE_PRACTICE, ActivityId.NEXT_OBJECT, ActivityId.PATTERN_RESTORE),
            summary = "Give your hands and eyes something steady to do while the alarm runs its course.",
        ),
        Pathway(
            Experience.THOUGHT_SPIRAL,
            stabilize = listOf(ActivityId.THOUGHT_UNHOOKING, ActivityId.PATTERN_RESTORE, ActivityId.ORBIT, ActivityId.NEXT_OBJECT),
            followUp = listOf(ActivityId.ATTENTION_RELOCATION, ActivityId.PRACTICAL_NEXT_STEP, ActivityId.PATTERN_RESTORE),
            summary = "Step back from the thoughts instead of arguing with them, then pick where attention goes.",
        ),
        Pathway(
            Experience.DISCONNECTED,
            stabilize = listOf(ActivityId.NEXT_OBJECT, ActivityId.ORBIT, ActivityId.SINGLE_THREAD, ActivityId.ATTENTION_RELOCATION),
            followUp = listOf(ActivityId.PATTERN_RESTORE, ActivityId.SINGLE_THREAD, ActivityId.ATTENTION_RELOCATION),
            summary = "Make contact with real, ordinary things around you, one at a time.",
        ),
        Pathway(
            Experience.OVERWHELMED,
            stabilize = listOf(ActivityId.SINGLE_THREAD, ActivityId.FLOW_FIELD, ActivityId.NEXT_OBJECT),
            followUp = listOf(ActivityId.PRACTICAL_NEXT_STEP, ActivityId.PATTERN_RESTORE, ActivityId.SINGLE_THREAD),
            summary = "Shrink everything down to one small thing at a time.",
        ),
        Pathway(
            Experience.JUST_START,
            stabilize = listOf(ActivityId.FLOW_FIELD, ActivityId.ORBIT, ActivityId.SINGLE_THREAD, ActivityId.NEXT_OBJECT),
            followUp = listOf(ActivityId.NEXT_OBJECT, ActivityId.PATTERN_RESTORE, ActivityId.PRACTICAL_NEXT_STEP),
            summary = "Start with something that asks almost nothing of you.",
        ),
    ).associateBy { it.experience }

    fun pathway(experience: Experience): Pathway = pathways.getValue(experience)

    /** Activities offered as alternatives when switching — pathway first, then everything else. */
    fun switchCandidates(experience: Experience): List<ActivityId> {
        val p = pathway(experience)
        return (p.stabilize + p.followUp + ActivityId.entries).distinct()
    }
}
