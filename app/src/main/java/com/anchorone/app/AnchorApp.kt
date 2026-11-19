package com.anchorone.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.anchorone.app.data.PrefsRepository
import com.anchorone.app.guidance.Speaker
import com.anchorone.app.guidance.StillpointController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.anchorStore by preferencesDataStore(name = "anchor_prefs")

/** Minimal manual dependency container — no DI framework needed at this size. */
class AnchorApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs: PrefsRepository by lazy { PrefsRepository(anchorStore) }

    /** Text-to-speech is created lazily, only once voice is actually used. */
    private var speakerInstance: Speaker? = null
    val speaker: Speaker
        get() = speakerInstance ?: Speaker(this).also { speakerInstance = it }

    val stillpoint: StillpointController by lazy { StillpointController(this, { speaker }, appScope) }
}
