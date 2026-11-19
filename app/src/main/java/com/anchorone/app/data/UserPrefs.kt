package com.anchorone.app.data

import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.ActivitySelector
import com.anchorone.app.engine.Category
import com.anchorone.app.engine.Density
import com.anchorone.app.engine.EngineContext
import com.anchorone.app.engine.Energy
import com.anchorone.app.engine.Experience
import com.anchorone.app.engine.PatternGenerator
import com.anchorone.app.engine.Rating
import com.anchorone.app.engine.SessionLength

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

enum class MotionLevel(val label: String, val detail: String) {
    FULL("Full", "Fluid animation and light effects"),
    GENTLE("Gentle", "Slower, softer animation"),
    REDUCED("Reduced motion", "Static alternatives, no drifting or pulsing"),
}

data class SupportCard(
    val reminders: String = "",
    val steps: String = "",
    val message: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
) {
    val isEmpty: Boolean get() = reminders.isBlank() && steps.isBlank() && message.isBlank() && contactName.isBlank() && contactPhone.isBlank()
    val hasContact: Boolean get() = contactPhone.isNotBlank()
    fun reminderLines(): List<String> = reminders.lines().map { it.trim() }.filter { it.isNotEmpty() }
    fun stepLines(): List<String> = steps.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

data class HistoryEntry(
    val timestamp: Long,
    val experience: Experience,
    val activities: List<ActivityId>,
    val durationSeconds: Long,
)

data class UserPrefs(
    val density: Density = Density.NORMAL,
    val theme: ThemeMode = ThemeMode.DARK,
    val motion: MotionLevel = MotionLevel.FULL,
    val haptics: Boolean = true,
    val companionVoice: Boolean = true,
    val voiceInSessions: Boolean = false,
    val speechRate: Float = 0.92f,
    val screenOffAudio: Boolean = false,
    val quietMode: Boolean = false,
    val sessionLength: SessionLength = SessionLength.STANDARD,
    val patternLevel: PatternGenerator.Level = PatternGenerator.Level.GENTLE,
    val preferredCategories: Set<Category> = emptySet(),
    val disabledCategories: Set<Category> = emptySet(),
    val favorites: Set<ActivityId> = emptySet(),
    val ratings: Map<ActivityId, Int> = emptyMap(),
    val askForRatings: Boolean = true,
    val historyEnabled: Boolean = true,
    val history: List<HistoryEntry> = emptyList(),
    val supportCard: SupportCard = SupportCard(),
    val plan: List<ActivityId> = emptyList(),
    val planEnabled: Boolean = false,
    val reEntryAnchors: List<String> = emptyList(),
    val emergencyNumber: String = "",
    val sessionCount: Int = 0,
) {
    /** Reduced motion if chosen here; the system "remove animations" setting is applied by the UI on top. */
    val reducedMotion: Boolean get() = motion == MotionLevel.REDUCED

    /** Quiet Mode silences voice and haptics regardless of their individual settings. */
    val effectiveHaptics: Boolean get() = haptics && !quietMode
    val effectiveCompanionVoice: Boolean get() = companionVoice && !quietMode
    val effectiveSessionVoice: Boolean get() = voiceInSessions && !quietMode

    fun engineContext(energy: Energy): EngineContext = EngineContext(
        ratings = ratings,
        favorites = favorites,
        preferredCategories = preferredCategories,
        disabledCategories = disabledCategories,
        energy = energy,
        reducedMotion = reducedMotion,
        sessionLength = sessionLength,
        plan = plan,
        planEnabled = planEnabled,
        rotation = sessionCount,
        askForRatings = askForRatings,
    )

    fun withRating(activity: ActivityId, rating: Rating): UserPrefs {
        val next = ((ratings[activity] ?: 0) + rating.weight)
            .coerceIn(-ActivitySelector.RATING_CLAMP, ActivitySelector.RATING_CLAMP)
        return copy(ratings = ratings + (activity to next))
    }

    fun ratingLabel(activity: ActivityId): String {
        val r = ratings[activity] ?: 0
        return when {
            r >= 2 -> "Often useful to you"
            r > 0 -> "Useful to you"
            r <= -3 -> "Rarely useful to you"
            r < 0 -> "Less useful to you"
            else -> "Not rated yet"
        }
    }

    companion object {
        const val MAX_HISTORY = 60
    }
}
