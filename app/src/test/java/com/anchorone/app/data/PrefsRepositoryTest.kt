package com.anchorone.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.Category
import com.anchorone.app.engine.Density
import com.anchorone.app.engine.Energy
import com.anchorone.app.engine.Experience
import com.anchorone.app.engine.PatternGenerator
import com.anchorone.app.engine.Rating
import com.anchorone.app.engine.SessionLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrefsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = scope.cancel()

    private fun repo(name: String = "prefs") = PrefsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("$name.preferences_pb") },
    )

    private val full = UserPrefs(
        density = Density.MINIMAL,
        theme = ThemeMode.LIGHT,
        motion = MotionLevel.REDUCED,
        haptics = false,
        companionVoice = false,
        voiceInSessions = true,
        speechRate = 1.1f,
        screenOffAudio = true,
        quietMode = true,
        sessionLength = SessionLength.LONGER,
        patternLevel = PatternGenerator.Level.DETAILED,
        preferredCategories = setOf(Category.SENSORY),
        disabledCategories = setOf(Category.THINKING),
        favorites = setOf(ActivityId.ORBIT, ActivityId.NEXT_OBJECT),
        ratings = mapOf(ActivityId.ORBIT to 4, ActivityId.FLOW_FIELD to -3),
        askForRatings = false,
        historyEnabled = true,
        history = listOf(HistoryEntry(1000L, Experience.DISCONNECTED, listOf(ActivityId.NEXT_OBJECT, ActivityId.ORBIT), 95)),
        supportCard = SupportCard("Line one\nLine two", "Step outside", "You've done this before.", "Sam", "+44 7000 000000"),
        plan = listOf(ActivityId.SINGLE_THREAD, ActivityId.PATTERN_RESTORE),
        planEnabled = true,
        reEntryAnchors = listOf("Call Mum back", "Finish the email"),
        emergencyNumber = "999",
        sessionCount = 7,
    )

    @Test
    fun `defaults when nothing stored`() = runBlocking {
        assertEquals(UserPrefs(), repo().current())
    }

    @Test
    fun `every field persists across repository instances`() = runBlocking {
        val file = tmp.root.resolve("restart.preferences_pb")
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        PrefsRepository(PreferenceDataStoreFactory.create(scope = first) { file }).update { full }
        first.coroutineContext[Job]!!.cancelAndJoin()
        // A fresh DataStore on the same file simulates an app restart.
        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertEquals(full, PrefsRepository(PreferenceDataStoreFactory.create(scope = second) { file }).current())
        } finally {
            second.cancel()
        }
    }

    @Test
    fun `mapper round trip is lossless`() {
        val p = mutablePreferencesOf()
        PrefsMapper.write(p, full)
        assertEquals(full, PrefsMapper.read(p))
    }

    @Test
    fun `unknown enum values fall back to defaults`() {
        val p = mutablePreferencesOf()
        p[PrefsMapper.Keys.density] = "GIANT"
        p[PrefsMapper.Keys.favorites] = "ORBIT\u001FNOT_A_THING"
        val u = PrefsMapper.read(p)
        assertEquals(Density.NORMAL, u.density)
        assertEquals(setOf(ActivityId.ORBIT), u.favorites)
    }

    @Test
    fun `ratings accumulate and clamp`() {
        var u = UserPrefs()
        repeat(10) { u = u.withRating(ActivityId.ORBIT, Rating.USEFUL) }
        assertEquals(9, u.ratings[ActivityId.ORBIT])
        repeat(10) { u = u.withRating(ActivityId.ORBIT, Rating.UNHELPFUL) }
        assertEquals(-9, u.ratings[ActivityId.ORBIT])
        assertEquals(0, u.withRating(ActivityId.FLOW_FIELD, Rating.NEUTRAL).ratings[ActivityId.FLOW_FIELD])
    }

    @Test
    fun `record session respects history toggle and caps length`() = runBlocking {
        val r = repo()
        repeat(UserPrefs.MAX_HISTORY + 5) { i -> r.recordSession(HistoryEntry(i.toLong(), Experience.JUST_START, emptyList(), 10)) }
        val cur = r.current()
        assertEquals(UserPrefs.MAX_HISTORY, cur.history.size)
        assertEquals((UserPrefs.MAX_HISTORY + 4).toLong(), cur.history.first().timestamp)
        assertEquals(UserPrefs.MAX_HISTORY + 5, cur.sessionCount)

        r.update { it.copy(historyEnabled = false, history = emptyList()) }
        r.recordSession(HistoryEntry(1L, Experience.JUST_START, emptyList(), 10))
        assertTrue(r.current().history.isEmpty())

        r.update { it.copy(historyEnabled = true) }
        r.recordSession(HistoryEntry(2L, Experience.JUST_START, emptyList(), 10))
        r.clearHistory()
        assertTrue(r.current().history.isEmpty())
    }

    @Test
    fun `quiet mode overrides voice and haptics`() {
        val q = UserPrefs(haptics = true, companionVoice = true, voiceInSessions = true, quietMode = true)
        assertFalse(q.effectiveHaptics)
        assertFalse(q.effectiveCompanionVoice)
        assertFalse(q.effectiveSessionVoice)
    }

    @Test
    fun `engine context reflects preferences`() {
        val ctx = full.engineContext(Energy.LOW)
        assertEquals(full.ratings, ctx.ratings)
        assertEquals(full.favorites, ctx.favorites)
        assertEquals(Energy.LOW, ctx.energy)
        assertEquals(full.plan, ctx.plan)
        assertTrue(ctx.planEnabled)
        assertTrue(ctx.reducedMotion)
        assertFalse(ctx.askForRatings)
        assertEquals(7, ctx.rotation)
    }

    @Test
    fun `history codec survives odd text`() {
        val h = listOf(HistoryEntry(5L, Experience.THOUGHT_SPIRAL, emptyList(), 0))
        assertEquals(h, PrefsMapper.decodeHistory(PrefsMapper.encodeHistory(h)))
        assertTrue(PrefsMapper.decodeHistory("garbage").isEmpty())
        assertTrue(PrefsMapper.decodeRatings("ORBIT=x\u001F=3").isEmpty())
    }
}
