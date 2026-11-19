package com.anchorone.app.engine

/**
 * Everything ANCHOR CORE is allowed to know when choosing an activity.
 * All of it is either explicitly stated by the user or set in Settings.
 * Nothing here is inferred from sensors or behaviour.
 */
data class EngineContext(
    /** Net rating score per activity (sum of rating weights, clamped). */
    val ratings: Map<ActivityId, Int> = emptyMap(),
    val favorites: Set<ActivityId> = emptySet(),
    val preferredCategories: Set<Category> = emptySet(),
    val disabledCategories: Set<Category> = emptySet(),
    val energy: Energy = Energy.OK,
    val reducedMotion: Boolean = false,
    val sessionLength: SessionLength = SessionLength.STANDARD,
    /** Steady-Self Plan: activities the user chose in advance, in order. */
    val plan: List<ActivityId> = emptyList(),
    val planEnabled: Boolean = false,
    /** Rotates immediate actions and content so sessions don't feel identical. */
    val rotation: Int = 0,
    val askForRatings: Boolean = true,
)

/** A scored choice with a human-readable explanation, shown under "Why this?". */
data class Pick(val activity: ActivityId, val score: Int, val reasons: List<String>)

object ActivitySelector {

    const val RATING_CLAMP = 9

    /**
     * Ranks [candidates] (already in pathway priority order). Deterministic: same inputs, same output.
     * Activities in [exclude] are dropped. Disabled categories are dropped unless that would leave
     * nothing, in which case they are allowed back — the engine never returns an empty list.
     */
    fun rank(
        candidates: List<ActivityId>,
        context: EngineContext,
        exclude: Set<ActivityId> = emptySet(),
    ): List<Pick> {
        val base = candidates.distinct().filterNot { it in exclude }
        val pool = base.filterNot { it.category in context.disabledCategories }.ifEmpty { base }
            .ifEmpty { ActivityId.entries.filterNot { it in exclude } }
            .ifEmpty { candidates.distinct().ifEmpty { ActivityId.entries } }

        return pool.mapIndexed { index, activity -> score(activity, index, context) }
            .sortedWith(compareByDescending<Pick> { it.score }.thenBy { pool.indexOf(it.activity) })
    }

    fun best(candidates: List<ActivityId>, context: EngineContext, exclude: Set<ActivityId> = emptySet()): Pick =
        rank(candidates, context, exclude).first()

    private fun score(activity: ActivityId, index: Int, context: EngineContext): Pick {
        val reasons = mutableListOf<String>()
        var score = 40 - index * 8
        if (index == 0) reasons += "A first choice for this situation"

        val rating = (context.ratings[activity] ?: 0).coerceIn(-RATING_CLAMP, RATING_CLAMP)
        if (rating > 0) {
            score += rating * 6
            reasons += "You rated it useful before"
        } else if (rating < 0) {
            score += rating * 8
            reasons += "You rated it less useful, so it's lower down"
        }
        if (activity in context.favorites) {
            score += 18
            reasons += "One of your favourites"
        }
        if (activity.category in context.preferredCategories) {
            score += 10
            reasons += "Matches your preferred ${activity.category.label.lowercase()} activities"
        }
        if (context.energy == Energy.LOW) {
            when (activity.demand) {
                1 -> { score += 20; reasons += "Asks very little of you" }
                3 -> score -= 30
                else -> score -= 12
            }
        }
        if (reasons.isEmpty()) reasons += "Suits this situation"
        return Pick(activity, score, reasons)
    }

    /** The least demanding general-purpose interactive exercise (used by JUST_START). */
    fun leastDemanding(context: EngineContext, exclude: Set<ActivityId> = emptySet()): Pick {
        val candidates = ActivityCatalog.pathway(Experience.JUST_START).stabilize
        val lowDemand = candidates.filter { it.demand == 1 }
        return best(lowDemand.ifEmpty { candidates }, context.copy(energy = Energy.LOW), exclude)
    }
}
