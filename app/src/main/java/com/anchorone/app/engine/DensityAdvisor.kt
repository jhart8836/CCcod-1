package com.anchorone.app.engine

/**
 * Cognitive Load Engine recommendation. Only uses what the person explicitly said
 * (their chosen experience and stated energy). It suggests; it never switches by itself.
 */
object DensityAdvisor {

    data class Suggestion(val density: Density, val message: String)

    fun recommend(experience: Experience?, energy: Energy, current: Density): Suggestion? {
        if (current == Density.MINIMAL) return null
        val wantsLess = energy == Energy.LOW || experience == Experience.OVERWHELMED
        if (!wantsLess) return null
        return if (current == Density.NORMAL) {
            if (energy == Energy.LOW && experience == Experience.OVERWHELMED) {
                Suggestion(Density.MINIMAL, "Switch to one thing at a time?")
            } else {
                Suggestion(Density.SIMPLIFIED, "Make the screen simpler?")
            }
        } else {
            if (energy == Energy.LOW) Suggestion(Density.MINIMAL, "Switch to one thing at a time?") else null
        }
    }
}
