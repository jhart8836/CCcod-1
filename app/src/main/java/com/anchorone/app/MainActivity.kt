package com.anchorone.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.anchorone.app.ui.AppRoot
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.LaunchAction

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(dark = true)
        if (savedInstanceState == null) vm.handleLaunch(parseLaunch(intent))
        setContent {
            AppRoot(
                vm = vm,
                systemAnimationsOff = systemAnimationsOff(),
                onSetDown = { moveTaskToBack(true) },
                onSystemBars = { dark -> applySystemBars(dark) },
            )
        }
    }

    private fun applySystemBars(dark: Boolean) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleLaunch(parseLaunch(intent))
    }

    private fun systemAnimationsOff(): Boolean =
        runCatching { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)

    companion object {
        const val ACTION_HELP_NOW = "com.anchorone.app.action.HELP_NOW"
        const val ACTION_JUST_START = "com.anchorone.app.action.JUST_START"
        const val ACTION_QUIET = "com.anchorone.app.action.QUIET"
        const val ACTION_THOUGHTS = "com.anchorone.app.action.THOUGHT_SPIRAL"
        const val ACTION_COMPANION = "com.anchorone.app.action.COMPANION"

        fun parseLaunch(intent: Intent?): LaunchAction? = when (intent?.action) {
            ACTION_HELP_NOW -> LaunchAction.HELP_NOW
            ACTION_JUST_START -> LaunchAction.JUST_START
            ACTION_QUIET -> LaunchAction.QUIET
            ACTION_THOUGHTS -> LaunchAction.THOUGHT_SPIRAL
            ACTION_COMPANION -> LaunchAction.COMPANION
            else -> null
        }
    }
}
