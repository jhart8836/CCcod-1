package com.anchorone.app

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.anchorone.app.data.PrefsRepository
import com.anchorone.app.guidance.Speaker
import com.anchorone.app.guidance.StillpointController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Minimal manual dependency container — no DI framework needed at this size. */
class AnchorApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One DataStore per process (owned by the Application), stored in app-private files. */
    val prefs: PrefsRepository by lazy {
        PrefsRepository(PreferenceDataStoreFactory.create(produceFile = { preferencesDataStoreFile("anchor_prefs") }))
    }

    /** Text-to-speech is created lazily, only once voice is actually used. */
    private var speakerInstance: Speaker? = null
    val speaker: Speaker
        get() = speakerInstance ?: Speaker(this).also { speakerInstance = it }

    val stillpoint: StillpointController by lazy { StillpointController(this, { speaker }, appScope) }
}
