package com.anchorone.app.guidance

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thin wrapper over the on-device Android TextToSpeech engine.
 * No network voices are requested. If no engine is installed, [available] stays false
 * and the UI falls back to text-only guidance.
 */
class Speaker(context: Context) {

    enum class Status { INITIALISING, READY, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.INITIALISING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val counter = AtomicInteger()
    private var rate = 0.92f

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { result ->
        if (result == TextToSpeech.SUCCESS) configure() else _status.value = Status.UNAVAILABLE
    }

    private fun configure() {
        fun unusable(code: Int) = code == TextToSpeech.LANG_MISSING_DATA || code == TextToSpeech.LANG_NOT_SUPPORTED
        var lang = runCatching { tts.setLanguage(Locale.getDefault()) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (unusable(lang)) lang = runCatching { tts.setLanguage(Locale.US) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (unusable(lang)) {
            _status.value = Status.UNAVAILABLE
            return
        }
        // Prefer an offline voice for the chosen language when the engine exposes one.
        runCatching {
            tts.voices?.filter { it.locale.language == tts.voice?.locale?.language && !it.isNetworkConnectionRequired }
                ?.minByOrNull { it.latency }
                ?.let { tts.voice = it }
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setSpeechRate(rate)
        tts.setPitch(0.98f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
        _status.value = Status.READY
    }

    private fun finish(id: String?) {
        _speaking.value = false
        id?.let { pending.remove(it)?.complete(Unit) }
    }

    val available: Boolean get() = _status.value == Status.READY

    fun setRate(value: Float) {
        rate = value.coerceIn(0.6f, 1.3f)
        if (available) tts.setSpeechRate(rate)
    }

    /** Speaks [text], replacing anything queued. Returns immediately. */
    fun say(text: String): String? {
        if (!available) return null
        val id = "a1-${counter.incrementAndGet()}"
        pending.values.forEach { it.complete(Unit) }
        pending.clear()
        pending[id] = CompletableDeferred()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        return id
    }

    /** Speaks and suspends until finished (or a generous timeout). */
    suspend fun sayAndWait(text: String) {
        val id = say(text) ?: return
        val deferred = pending[id] ?: return
        withTimeoutOrNull(Companion.estimatedSpeechMillis(text) * 3 + 3_000L) { deferred.await() }
    }

    fun stop() {
        if (_status.value == Status.READY) tts.stop()
        pending.values.forEach { it.complete(Unit) }
        pending.clear()
        _speaking.value = false
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
