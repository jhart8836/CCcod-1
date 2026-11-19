package com.anchorone.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anchorone.app.data.MotionLevel
import com.anchorone.app.data.ThemeMode
import com.anchorone.app.ui.components.AnchorBackground
import com.anchorone.app.ui.screens.HistoryScreen
import com.anchorone.app.ui.screens.HomeScreen
import com.anchorone.app.ui.screens.PlanScreen
import com.anchorone.app.ui.screens.QuietScreen
import com.anchorone.app.ui.screens.SafetyScreen
import com.anchorone.app.ui.screens.SessionScreen
import com.anchorone.app.ui.screens.SettingsScreen
import com.anchorone.app.ui.screens.StillpointScreen
import com.anchorone.app.ui.screens.SupportCardScreen
import com.anchorone.app.ui.screens.ToolkitScreen
import com.anchorone.app.ui.screens.TriageScreen
import com.anchorone.app.ui.theme.AnchorTheme
import com.anchorone.app.ui.theme.UiEnv

@Composable
fun AppRoot(vm: AppViewModel, systemAnimationsOff: Boolean, onSetDown: () -> Unit, onSystemBars: (dark: Boolean) -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()

    val p = prefs
    if (p == null) {
        // Preferences load in a few milliseconds; show the plain backdrop meanwhile (no flash of wrong theme).
        AnchorTheme(ThemeMode.DARK, UiEnv()) { AnchorBackground {} }
        return
    }

    val env = UiEnv(
        density = p.density,
        motion = if (systemAnimationsOff) MotionLevel.REDUCED else p.motion,
        haptics = p.effectiveHaptics,
        quiet = p.quietMode,
    )

    val dark = when (p.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    // Status/navigation bar icons follow the app's theme, not just the system's.
    androidx.compose.runtime.LaunchedEffect(dark) { onSystemBars(dark) }

    AnchorTheme(p.theme, env) {
        BackHandler(enabled = stack.size > 1) { vm.back() }
        val top = stack.lastOrNull() ?: Screen.HOME
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                if (env.reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween((260 / env.motionScale.coerceAtLeast(0.5f)).toInt())) togetherWith
                    fadeOut(tween(160))
            },
            label = "screens",
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(vm, p)
                Screen.TRIAGE -> TriageScreen(vm, p)
                Screen.SESSION -> SessionScreen(vm, p, onSetDown)
                Screen.TOOLKIT -> ToolkitScreen(vm, p)
                Screen.STILLPOINT -> StillpointScreen(vm, p)
                Screen.SETTINGS -> SettingsScreen(vm, p)
                Screen.SUPPORT_CARD -> SupportCardScreen(vm, p)
                Screen.SAFETY -> SafetyScreen(vm, p)
                Screen.HISTORY -> HistoryScreen(vm, p)
                Screen.PLAN -> PlanScreen(vm, p)
                Screen.QUIET -> QuietScreen(vm, p)
            }
        }
    }
}

