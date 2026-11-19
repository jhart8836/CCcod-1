package com.anchorone.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.Category
import com.anchorone.app.engine.Density
import com.anchorone.app.engine.Experience
import com.anchorone.app.engine.PatternGenerator
import com.anchorone.app.engine.SessionLength
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * All persistent state. Everything stays on the device in a DataStore file;
 * nothing is synced or sent anywhere. Room was deliberately not used: the data
 * is a handful of settings plus a short capped history list.
 */
class PrefsRepository(private val store: DataStore<Preferences>) {

    val prefs: Flow<UserPrefs> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { PrefsMapper.read(it) }

    suspend fun current(): UserPrefs = prefs.first()

    /** Atomic read-modify-write of the whole preference set. */
    suspend fun update(transform: (UserPrefs) -> UserPrefs) {
        store.edit { p ->
            val next = transform(PrefsMapper.read(p))
            PrefsMapper.write(p, next)
        }
    }

    suspend fun recordSession(entry: HistoryEntry) = update { cur ->
        val counted = cur.copy(sessionCount = cur.sessionCount + 1)
        if (!cur.historyEnabled) counted
        else counted.copy(history = (listOf(entry) + cur.history).take(UserPrefs.MAX_HISTORY))
    }

    suspend fun clearHistory() = update { it.copy(history = emptyList()) }
}

/** Pure mapping between DataStore preferences and [UserPrefs]; unit-tested on the JVM. */
object PrefsMapper {
    private const val ITEM = "\u001F"
    private const val FIELD = "\u001E"

    object Keys {
        val density = stringPreferencesKey("density")
        val theme = stringPreferencesKey("theme")
        val motion = stringPreferencesKey("motion")
        val haptics = booleanPreferencesKey("haptics")
        val companionVoice = booleanPreferencesKey("companion_voice")
        val voiceInSessions = booleanPreferencesKey("voice_in_sessions")
        val speechRate = floatPreferencesKey("speech_rate")
        val screenOffAudio = booleanPreferencesKey("screen_off_audio")
        val quietMode = booleanPreferencesKey("quiet_mode")
        val sessionLength = stringPreferencesKey("session_length")
        val patternLevel = stringPreferencesKey("pattern_level")
        val preferred = stringPreferencesKey("preferred_categories")
        val disabled = stringPreferencesKey("disabled_categories")
        val favorites = stringPreferencesKey("favorites")
        val ratings = stringPreferencesKey("ratings")
        val askRatings = booleanPreferencesKey("ask_ratings")
        val historyEnabled = booleanPreferencesKey("history_enabled")
        val history = stringPreferencesKey("history")
        val cardReminders = stringPreferencesKey("card_reminders")
        val cardSteps = stringPreferencesKey("card_steps")
        val cardMessage = stringPreferencesKey("card_message")
        val cardContactName = stringPreferencesKey("card_contact_name")
        val cardContactPhone = stringPreferencesKey("card_contact_phone")
        val plan = stringPreferencesKey("plan")
        val planEnabled = booleanPreferencesKey("plan_enabled")
        val anchors = stringPreferencesKey("reentry_anchors")
        val emergency = stringPreferencesKey("emergency_number")
        val sessionCount = intPreferencesKey("session_count")
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private inline fun <reified E : Enum<E>> enumSet(value: String?): Set<E> =
        value.orEmpty().split(ITEM).filter { it.isNotBlank() }
            .mapNotNull { v -> enumValues<E>().firstOrNull { it.name == v } }.toSet()

    private fun activityList(value: String?): List<ActivityId> =
        value.orEmpty().split(ITEM).filter { it.isNotBlank() }
            .mapNotNull { v -> ActivityId.entries.firstOrNull { it.name == v } }

    fun encodeRatings(r: Map<ActivityId, Int>): String =
        r.entries.sortedBy { it.key.ordinal }.joinToString(ITEM) { "${it.key.name}=${it.value}" }

    fun decodeRatings(s: String?): Map<ActivityId, Int> =
        s.orEmpty().split(ITEM).mapNotNull { pair ->
            val parts = pair.split("=")
            if (parts.size != 2) return@mapNotNull null
            val id = ActivityId.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val v = parts[1].toIntOrNull() ?: return@mapNotNull null
            id to v
        }.toMap()

    fun encodeHistory(h: List<HistoryEntry>): String = h.joinToString(ITEM) { e ->
        listOf(e.timestamp.toString(), e.experience.name, e.activities.joinToString(",") { it.name }, e.durationSeconds.toString())
            .joinToString(FIELD)
    }

    fun decodeHistory(s: String?): List<HistoryEntry> =
        s.orEmpty().split(ITEM).filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split(FIELD)
            if (f.size != 4) return@mapNotNull null
            val ts = f[0].toLongOrNull() ?: return@mapNotNull null
            val exp = Experience.entries.firstOrNull { it.name == f[1] } ?: return@mapNotNull null
            val acts = f[2].split(",").mapNotNull { n -> ActivityId.entries.firstOrNull { it.name == n } }
            HistoryEntry(ts, exp, acts, f[3].toLongOrNull() ?: 0L)
        }

    private fun strings(s: String?): List<String> = s.orEmpty().split(ITEM).map { it.trim() }.filter { it.isNotEmpty() }

    fun read(p: Preferences): UserPrefs {
        val d = UserPrefs()
        return UserPrefs(
            density = enumOr(p[Keys.density], d.density),
            theme = enumOr(p[Keys.theme], d.theme),
            motion = enumOr(p[Keys.motion], d.motion),
            haptics = p[Keys.haptics] ?: d.haptics,
            companionVoice = p[Keys.companionVoice] ?: d.companionVoice,
            voiceInSessions = p[Keys.voiceInSessions] ?: d.voiceInSessions,
            speechRate = p[Keys.speechRate] ?: d.speechRate,
            screenOffAudio = p[Keys.screenOffAudio] ?: d.screenOffAudio,
            quietMode = p[Keys.quietMode] ?: d.quietMode,
            sessionLength = enumOr(p[Keys.sessionLength], d.sessionLength),
            patternLevel = enumOr(p[Keys.patternLevel], d.patternLevel),
            preferredCategories = enumSet<Category>(p[Keys.preferred]),
            disabledCategories = enumSet<Category>(p[Keys.disabled]),
            favorites = enumSet<ActivityId>(p[Keys.favorites]),
            ratings = decodeRatings(p[Keys.ratings]),
            askForRatings = p[Keys.askRatings] ?: d.askForRatings,
            historyEnabled = p[Keys.historyEnabled] ?: d.historyEnabled,
            history = decodeHistory(p[Keys.history]),
            supportCard = SupportCard(
                reminders = p[Keys.cardReminders].orEmpty(),
                steps = p[Keys.cardSteps].orEmpty(),
                message = p[Keys.cardMessage].orEmpty(),
                contactName = p[Keys.cardContactName].orEmpty(),
                contactPhone = p[Keys.cardContactPhone].orEmpty(),
            ),
            plan = activityList(p[Keys.plan]),
            planEnabled = p[Keys.planEnabled] ?: d.planEnabled,
            reEntryAnchors = strings(p[Keys.anchors]),
            emergencyNumber = p[Keys.emergency].orEmpty(),
            sessionCount = p[Keys.sessionCount] ?: 0,
        )
    }

    fun write(p: MutablePreferences, u: UserPrefs) {
        p[Keys.density] = u.density.name
        p[Keys.theme] = u.theme.name
        p[Keys.motion] = u.motion.name
        p[Keys.haptics] = u.haptics
        p[Keys.companionVoice] = u.companionVoice
        p[Keys.voiceInSessions] = u.voiceInSessions
        p[Keys.speechRate] = u.speechRate
        p[Keys.screenOffAudio] = u.screenOffAudio
        p[Keys.quietMode] = u.quietMode
        p[Keys.sessionLength] = u.sessionLength.name
        p[Keys.patternLevel] = u.patternLevel.name
        p[Keys.preferred] = u.preferredCategories.sortedBy { it.ordinal }.joinToString(ITEM) { it.name }
        p[Keys.disabled] = u.disabledCategories.sortedBy { it.ordinal }.joinToString(ITEM) { it.name }
        p[Keys.favorites] = u.favorites.sortedBy { it.ordinal }.joinToString(ITEM) { it.name }
        p[Keys.ratings] = encodeRatings(u.ratings)
        p[Keys.askRatings] = u.askForRatings
        p[Keys.historyEnabled] = u.historyEnabled
        p[Keys.history] = if (u.historyEnabled) encodeHistory(u.history.take(UserPrefs.MAX_HISTORY)) else ""
        p[Keys.cardReminders] = u.supportCard.reminders
        p[Keys.cardSteps] = u.supportCard.steps
        p[Keys.cardMessage] = u.supportCard.message
        p[Keys.cardContactName] = u.supportCard.contactName
        p[Keys.cardContactPhone] = u.supportCard.contactPhone
        p[Keys.plan] = u.plan.joinToString(ITEM) { it.name }
        p[Keys.planEnabled] = u.planEnabled
        p[Keys.anchors] = u.reEntryAnchors.joinToString(ITEM) { it.replace(ITEM, " ") }
        p[Keys.emergency] = u.emergencyNumber
        p[Keys.sessionCount] = u.sessionCount
    }
}
