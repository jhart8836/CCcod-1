package com.anchorone.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anchorone.app.BuildConfig
import com.anchorone.app.data.MotionLevel
import com.anchorone.app.data.ThemeMode
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.Category
import com.anchorone.app.engine.Density
import com.anchorone.app.engine.PatternGenerator
import com.anchorone.app.engine.SessionLength
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.Screen
import com.anchorone.app.ui.components.BodyText
import com.anchorone.app.ui.components.Card
import com.anchorone.app.ui.components.Chip
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.ScreenScaffold
import com.anchorone.app.ui.components.SectionLabel
import com.anchorone.app.ui.components.Segmented
import com.anchorone.app.ui.components.ToggleRow
import com.anchorone.app.ui.theme.Anchor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, prefs: UserPrefs) {
    val c = Anchor.colors
    val context = LocalContext.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val app = context.applicationContext as com.anchorone.app.AnchorApp

    ScreenScaffold("Settings", onBack = { vm.back() }) {
        SectionLabel("Interface density")
        Segmented(Density.entries, prefs.density, { it.label }) { vm.setDensity(it) }
        Text(prefs.density.detail, style = MaterialTheme.typography.bodySmall, color = c.textDim)

        SectionLabel("Appearance")
        Segmented(ThemeMode.entries, prefs.theme, { it.label }) { t -> vm.updatePrefs { it.copy(theme = t) } }
        Segmented(MotionLevel.entries, prefs.motion, { it.label }) { m -> vm.updatePrefs { it.copy(motion = m) } }
        Text(prefs.motion.detail + ". The system \"remove animations\" setting is always respected.", style = MaterialTheme.typography.bodySmall, color = c.textDim)
        ToggleRow("Haptic feedback", "Subtle ticks on touch. Off in Quiet mode.", prefs.haptics) { v -> vm.updatePrefs { it.copy(haptics = v) } }
        ToggleRow("Quiet mode", "Discreet look, no voice, no vibration", prefs.quietMode) { v -> vm.updatePrefs { it.copy(quietMode = v) } }

        SectionLabel("Voice")
        ToggleRow("Companion voice", "Stillpoint speaks its guidance using this phone's offline voice", prefs.companionVoice) { v -> vm.updatePrefs { it.copy(companionVoice = v) } }
        ToggleRow("Speak instructions in sessions", "Reads each step aloud", prefs.voiceInSessions) { v -> vm.updatePrefs { it.copy(voiceInSessions = v) } }
        ToggleRow(
            "Keep guiding with screen off",
            "Companion continues when the screen is off. Shows a silent notification while active.",
            prefs.screenOffAudio,
        ) { v -> vm.updatePrefs { it.copy(screenOffAudio = v) } }
        Text("Speaking pace", style = MaterialTheme.typography.titleSmall, color = c.text)
        Slider(
            value = prefs.speechRate,
            onValueChange = { v -> vm.updatePrefs { it.copy(speechRate = v) } },
            valueRange = 0.6f..1.3f,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surfaceHigh),
            modifier = Modifier.semantics { contentDescription = "Speaking pace" },
        )
        GhostButton("Test voice", {
            val s = app.speaker
            s.setRate(prefs.speechRate)
            if (s.say("This is how guidance will sound.") == null) {
                android.widget.Toast.makeText(context, "Voice is starting up or not available on this device.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }, color = c.accent)

        SectionLabel("Sessions")
        Segmented(SessionLength.entries, prefs.sessionLength, { it.label }) { l -> vm.updatePrefs { it.copy(sessionLength = l) } }
        Text(prefs.sessionLength.detail, style = MaterialTheme.typography.bodySmall, color = c.textDim)
        ToggleRow("Ask me to rate activities", "Ratings only reorder suggestions on this phone", prefs.askForRatings) { v -> vm.updatePrefs { it.copy(askForRatings = v) } }
        Text("Pattern Restore detail", style = MaterialTheme.typography.titleSmall, color = c.text)
        Segmented(PatternGenerator.Level.entries, prefs.patternLevel, { it.label }) { l -> vm.updatePrefs { it.copy(patternLevel = l) } }

        SectionLabel("Activity preferences")
        Text("Prefer", style = MaterialTheme.typography.titleSmall, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { cat ->
                val on = cat in prefs.preferredCategories
                Chip(cat.label, on, {
                    vm.updatePrefs { it.copy(preferredCategories = if (on) it.preferredCategories - cat else it.preferredCategories + cat, disabledCategories = it.disabledCategories - cat) }
                })
            }
        }
        Text("Don't suggest", style = MaterialTheme.typography.titleSmall, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { cat ->
                val on = cat in prefs.disabledCategories
                Chip(cat.label, on, {
                    vm.updatePrefs { it.copy(disabledCategories = if (on) it.disabledCategories - cat else it.disabledCategories + cat, preferredCategories = it.preferredCategories - cat) }
                })
            }
        }
        Text("Turned-off kinds are still available from the Toolkit. If everything in a pathway is off, ANCHOR will still offer something.", style = MaterialTheme.typography.bodySmall, color = c.textDim)
        ChoiceCard("Favourites", "Star activities in the Toolkit", { vm.navigate(Screen.TOOLKIT) })
        ChoiceCard("My steady-self plan", if (prefs.planEnabled && prefs.plan.isNotEmpty()) "On — ${prefs.plan.size} steps" else "Off", { vm.navigate(Screen.PLAN) })
        ChoiceCard("Personal support card", if (prefs.supportCard.isEmpty) "Not set up yet" else "Set up", { vm.navigate(Screen.SUPPORT_CARD) })

        SectionLabel("Emergency number")
        val detected = EmergencyNumbers.detect(context, "")
        OutlinedTextField(
            value = prefs.emergencyNumber,
            onValueChange = { v -> vm.updatePrefs { it.copy(emergencyNumber = v.filter { ch -> ch.isDigit() || ch == '+' }.take(8)) } },
            label = { Text("Leave empty to use $detected") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Privacy & history")
        ToggleRow("Keep a local session history", "Stored only on this phone. Turning off deletes it.", prefs.historyEnabled) { v ->
            vm.updatePrefs { it.copy(historyEnabled = v, history = if (v) it.history else emptyList()) }
        }
        ChoiceCard("View history", "${prefs.history.size} sessions", { vm.navigate(Screen.HISTORY) })
        if (!confirmReset) {
            GhostButton("Reset ratings and favourites", { confirmReset = true }, color = c.alert)
        } else {
            Card {
                BodyText("Reset all ratings and favourites?")
                GhostButton("Yes, reset", { vm.updatePrefs { it.copy(ratings = emptyMap(), favorites = emptySet()) }; confirmReset = false }, color = c.alert)
                GhostButton("Cancel", { confirmReset = false })
            }
        }

        SectionLabel("About")
        Card {
            Text("ANCHOR ONE ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = c.text)
            BodyText(
                "A self-help tool for difficult moments, drawing on ideas from CBT and ACT. It is not a medical device, " +
                    "does not diagnose or treat anything, and has not been clinically evaluated.",
            )
            BodyText("No accounts, ads, analytics or trackers. It works fully offline and never sends your data anywhere.")
            GhostButton("Safety information", { vm.navigate(Screen.SAFETY) }, color = c.accent)
        }
    }
}
