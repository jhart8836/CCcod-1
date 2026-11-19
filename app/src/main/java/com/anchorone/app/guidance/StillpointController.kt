package com.anchorone.app.guidance

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.anchorone.app.engine.Experience
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the STILLPOINT companion: owns the [Companion] script state, speaks lines through
 * [Speaker] (or shows text only), and paces automatic guidance. Lives in application scope so
 * guidance can continue with the screen off when the user enables it.
 */
class StillpointController(
    private val context: Context,
    private val speakerProvider: () -> Speaker,
    private val scope: CoroutineScope,
) {
    enum class Command { KEEP_GUIDING, SOMETHING_TO_DO, TASK_DONE, LESS_TALKING, MORE_TALKING, SWITCH, READY }

    data class Ui(
        val active: Boolean = false,
        val text: String = "",
        val awaitingDone: Boolean = false,
        val lessTalking: Boolean = false,
        val closing: Boolean = false,
        val voiceOn: Boolean = true,
        val voiceUnavailable: Boolean = false,
        val held: Boolean = false,
        val experience: Experience? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private var companion: Companion? = null
    private var loop: Job? = null
    private var screenOff = false
    private var speaker: Speaker? = null

    fun start(experience: Experience?, seed: Int, voice: Boolean, screenOffAudio: Boolean, speechRate: Float) {
        stopLoop()
        companion = Companion(experience, seed)
        screenOff = screenOffAudio
        if (voice) speaker = speakerProvider().also { it.setRate(speechRate) }
        _ui.value = Ui(active = true, voiceOn = voice, experience = experience)
        if (voice && screenOffAudio) startService()
        deliver(companion!!.open())
    }

    fun command(command: Command) {
        val c = companion ?: return
        val utterance = when (command) {
            Command.KEEP_GUIDING -> c.keepGuiding()
            Command.SOMETHING_TO_DO -> c.somethingToDo()
            Command.TASK_DONE -> c.taskDone()
            Command.LESS_TALKING -> c.lessTalking()
            Command.MORE_TALKING -> c.moreTalking()
            Command.SWITCH -> c.switchActivity()
            Command.READY -> c.ready()
        }
        _ui.update { it.copy(held = false) }
        deliver(utterance)
    }

    /** Pause automatic progression (e.g. while the user is in an activity) without ending. */
    fun hold() {
        stopLoop()
        speaker?.stop()
        _ui.update { it.copy(held = true) }
    }

    fun setVoice(on: Boolean, speechRate: Float) {
        if (on && speaker == null) speaker = speakerProvider().also { it.setRate(speechRate) }
        if (!on) speaker?.stop()
        _ui.update { it.copy(voiceOn = on) }
        if (on && screenOff) startService() else if (!on) stopService()
    }

    fun stop() {
        stopLoop()
        speaker?.stop()
        stopService()
        companion = null
        _ui.value = Ui()
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    private fun deliver(u: Companion.Utterance) {
        stopLoop()
        _ui.update {
            it.copy(
                text = u.text,
                awaitingDone = u.awaitingDone,
                lessTalking = companion?.verbosity == Companion.Verbosity.LESS,
                closing = u.mode == Companion.Mode.CLOSING,
            )
        }
        loop = scope.launch {
            speak(u.text)
            if (u.mode != Companion.Mode.GUIDING || u.awaitingDone) return@launch
            while (true) {
                val c = companion ?: return@launch
                delay(c.gapMillis())
                val next = c.keepGuiding()
                _ui.update { it.copy(text = next.text, awaitingDone = false) }
                speak(next.text)
            }
        }
    }

    private suspend fun speak(text: String) {
        val s = speaker
        if (!_ui.value.voiceOn || s == null) {
            delay(Companion.estimatedSpeechMillis(text))
            return
        }
        if (s.status.value == Speaker.Status.INITIALISING) {
            withTimeoutOrNull(3_000L) { s.status.first { it != Speaker.Status.INITIALISING } }
        }
        if (s.available) {
            s.sayAndWait(text)
        } else {
            _ui.update { it.copy(voiceUnavailable = true) }
            delay(Companion.estimatedSpeechMillis(text))
        }
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, StillpointService::class.java))
        }
    }

    private fun stopService() {
        runCatching { context.stopService(Intent(context, StillpointService::class.java)) }
    }
}
