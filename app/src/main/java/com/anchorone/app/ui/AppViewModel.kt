package com.anchorone.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.anchorone.app.AnchorApp
import com.anchorone.app.data.HistoryEntry
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.AnchorCore
import com.anchorone.app.engine.Density
import com.anchorone.app.engine.DensityAdvisor
import com.anchorone.app.engine.Energy
import com.anchorone.app.engine.Experience
import com.anchorone.app.engine.Phase
import com.anchorone.app.engine.Rating
import com.anchorone.app.engine.SessionCodec
import com.anchorone.app.engine.SessionEvent
import com.anchorone.app.engine.SessionState
import com.anchorone.app.guidance.StillpointController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { HOME, TRIAGE, SESSION, TOOLKIT, STILLPOINT, SETTINGS, SUPPORT_CARD, SAFETY, HISTORY, PLAN, QUIET }

/** Launch requests from shortcuts, the widget and notifications. */
enum class LaunchAction { HELP_NOW, JUST_START, QUIET, THOUGHT_SPIRAL, COMPANION }

class AppViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {

    private val app = application as AnchorApp
    private val repo = app.prefs
    val stillpoint: StillpointController = app.stillpoint

    val prefs: StateFlow<UserPrefs?> = repo.prefs.map<UserPrefs, UserPrefs?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun currentPrefs(): UserPrefs = prefs.value ?: UserPrefs()

    private val _stack = MutableStateFlow(
        saved.get<String>(KEY_STACK)?.split(",")?.mapNotNull { n -> Screen.entries.firstOrNull { it.name == n } }
            ?.ifEmpty { null } ?: listOf(Screen.HOME),
    )
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _session = MutableStateFlow(SessionCodec.decode(saved.get<String>(KEY_SESSION)))
    val session: StateFlow<SessionState?> = _session.asStateFlow()

    private val _energy = MutableStateFlow(Energy.OK)
    val energy: StateFlow<Energy> = _energy.asStateFlow()

    /** A session the user exited, kept briefly so an accidental tap doesn't lose their place. */
    private val _resumable = MutableStateFlow<Pair<SessionState, Long>?>(null)
    val resumable: StateFlow<Pair<SessionState, Long>?> = _resumable.asStateFlow()

    /** Cognitive-load suggestion shown once per session, never applied automatically. */
    private val _densitySuggestion = MutableStateFlow<DensityAdvisor.Suggestion?>(null)
    val densitySuggestion: StateFlow<DensityAdvisor.Suggestion?> = _densitySuggestion.asStateFlow()

    // ---------- navigation ----------

    fun navigate(screen: Screen) {
        val s = _stack.value
        if (s.lastOrNull() == screen) return
        setStack(s.filterNot { it == screen } + screen)
    }

    fun back(): Boolean {
        val s = _stack.value
        if (s.size <= 1) return false
        if (s.last() == Screen.SESSION) {
            // Back from a session exits it but keeps it resumable.
            exitSession()
            return true
        }
        if (s.last() == Screen.STILLPOINT) {
            if (_session.value == null) stillpoint.stop() else stillpoint.hold()
        }
        setStack(s.dropLast(1))
        return true
    }

    fun home() = setStack(listOf(Screen.HOME))

    private fun setStack(s: List<Screen>) {
        _stack.value = s
        saved[KEY_STACK] = s.joinToString(",") { it.name }
    }

    private fun setSession(s: SessionState?) {
        _session.value = s
        saved[KEY_SESSION] = s?.let { SessionCodec.encode(it) }
    }

    // ---------- session ----------

    fun setEnergy(e: Energy) {
        _energy.value = e
    }

    fun helpNow() {
        _energy.value = Energy.OK
        navigate(Screen.TRIAGE)
    }

    fun startSession(experience: Experience) {
        val p = currentPrefs()
        val state = AnchorCore.start(experience, p.engineContext(_energy.value), System.currentTimeMillis())
        _resumable.value = null
        _densitySuggestion.value = DensityAdvisor.recommend(experience, _energy.value, p.density)
        setSession(state)
        setStack(listOf(Screen.HOME, Screen.SESSION))
    }

    fun startSingle(activity: ActivityId) {
        val p = currentPrefs()
        _resumable.value = null
        _densitySuggestion.value = null
        setSession(AnchorCore.startSingle(activity, p.engineContext(_energy.value), System.currentTimeMillis()))
        navigate(Screen.SESSION)
    }

    /** Re-entry Ramp straight from the companion or elsewhere, without a running session. */
    fun startReEntry() {
        val now = System.currentTimeMillis()
        val existing = _session.value
        if (existing != null && !existing.isOver) {
            dispatch(SessionEvent.ReadyToMoveOn)
        } else {
            setSession(SessionState(Experience.JUST_START, Phase.TRANSITION, startedAt = now))
        }
        setStack(listOf(Screen.HOME, Screen.SESSION))
    }

    fun dispatch(event: SessionEvent) {
        val cur = _session.value ?: return
        val next = AnchorCore.reduce(cur, event, currentPrefs().engineContext(cur.energy))
        if (next.isOver) {
            finishSession(cur, next, exited = event == SessionEvent.Exit)
        } else {
            setSession(next)
        }
    }

    fun exitSession() = dispatch(SessionEvent.Exit)

    private fun finishSession(before: SessionState, after: SessionState, exited: Boolean) {
        val now = System.currentTimeMillis()
        if (after.completed.isNotEmpty() || !exited) {
            val entry = HistoryEntry(
                timestamp = after.startedAt.takeIf { it > 0 } ?: now,
                experience = after.experience,
                activities = after.completed,
                durationSeconds = ((now - after.startedAt) / 1000).coerceAtLeast(0),
            )
            viewModelScope.launch { repo.recordSession(entry) }
        }
        _resumable.value = if (exited && before.phase != Phase.TRANSITION) before.copy(paused = false) to now else null
        _densitySuggestion.value = null
        setSession(null)
        if (stillpoint.ui.value.active) stillpoint.stop()
        home()
    }

    fun resume() {
        val r = _resumable.value ?: return
        _resumable.value = null
        setSession(r.first)
        setStack(listOf(Screen.HOME, Screen.SESSION))
    }

    fun dismissResume() {
        _resumable.value = null
    }

    fun rate(activity: ActivityId, rating: Rating) {
        viewModelScope.launch { repo.update { it.withRating(activity, rating) } }
    }

    fun dismissDensitySuggestion() {
        _densitySuggestion.value = null
    }

    fun setDensity(d: Density) {
        _densitySuggestion.value = null
        updatePrefs { it.copy(density = d) }
    }

    fun updatePrefs(transform: (UserPrefs) -> UserPrefs) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearHistory() }
    }

    /** Speaks session instructions when the user enabled voice in sessions (and the companion is not talking). */
    fun speakIfEnabled(text: String) {
        val p = currentPrefs()
        if (!p.effectiveSessionVoice || stillpoint.ui.value.active || text.isBlank()) return
        val speaker = app.speaker
        speaker.setRate(p.speechRate)
        viewModelScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                speaker.status.first { it != com.anchorone.app.guidance.Speaker.Status.INITIALISING }
            }
            speaker.say(text)
        }
    }

    fun stopSpeaking() {
        if (!stillpoint.ui.value.active) app.speaker.stop()
    }

    // ---------- companion ----------

    fun openCompanion() {
        val p = currentPrefs()
        val ui = stillpoint.ui.value
        if (!ui.active) {
            stillpoint.start(
                experience = _session.value?.experience?.takeIf { it != Experience.JUST_START },
                seed = p.sessionCount + (System.currentTimeMillis() / 60_000L).toInt(),
                voice = p.effectiveCompanionVoice,
                screenOffAudio = p.screenOffAudio,
                speechRate = p.speechRate,
            )
        }
        navigate(Screen.STILLPOINT)
    }

    fun companionCommand(cmd: StillpointController.Command) {
        stillpoint.command(cmd)
        when (cmd) {
            StillpointController.Command.SWITCH -> {
                stillpoint.hold()
                val s = _session.value
                if (s != null && !s.isOver) {
                    dispatch(SessionEvent.SomethingDifferent)
                    setStack(listOf(Screen.HOME, Screen.SESSION))
                } else {
                    startSession(Experience.JUST_START)
                }
            }
            StillpointController.Command.READY -> Unit // UI shows the closing line, then continues to re-entry
            else -> Unit
        }
    }

    fun companionToReEntry() {
        stillpoint.stop()
        startReEntry()
    }

    fun closeCompanion() {
        stillpoint.stop()
        back()
    }

    // ---------- launch intents ----------

    fun handleLaunch(action: LaunchAction?) {
        when (action) {
            null -> Unit
            LaunchAction.HELP_NOW -> { home(); helpNow() }
            LaunchAction.JUST_START -> startSession(Experience.JUST_START)
            LaunchAction.THOUGHT_SPIRAL -> startSession(Experience.THOUGHT_SPIRAL)
            LaunchAction.QUIET -> {
                updatePrefs { it.copy(quietMode = true) }
                setStack(listOf(Screen.HOME, Screen.QUIET))
            }
            LaunchAction.COMPANION -> { home(); openCompanion() }
        }
    }

    companion object {
        private const val KEY_STACK = "nav_stack"
        private const val KEY_SESSION = "session"
        const val RESUME_WINDOW_MS = 15 * 60 * 1000L
    }
}
