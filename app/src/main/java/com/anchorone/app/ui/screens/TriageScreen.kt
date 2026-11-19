package com.anchorone.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.Energy
import com.anchorone.app.engine.Experience
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.components.AnchorBackground
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.Chip
import com.anchorone.app.ui.components.CloseAction
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.theme.Anchor

@Composable
fun TriageScreen(vm: AppViewModel, prefs: UserPrefs) {
    val env = Anchor.env
    val c = Anchor.colors
    val energy by vm.energy.collectAsStateWithLifecycle()
    var showChoices by rememberSaveable { mutableStateOf(!env.minimal) }
    val planNote = if (prefs.planEnabled && prefs.plan.isNotEmpty()) "Runs the plan you made" else "Skip choosing — begin with something simple"

    AnchorBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CloseAction({ vm.back() }, label = "Back")
                Spacer(Modifier.weight(1f))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showChoices) {
                    Text(
                        if (env.simplified) "What's closest?" else "What feels closest right now?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (!env.simplified) {
                        Text("No need to be exact. Or skip with the button at the bottom.", style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                    }
                    Spacer(Modifier.height(4.dp))
                    listOf(Experience.PHYSICAL_ALARM, Experience.THOUGHT_SPIRAL, Experience.DISCONNECTED, Experience.OVERWHELMED).forEach { e ->
                        ChoiceCard(
                            title = if (env.simplified) e.detail else e.label,
                            detail = if (env.simplified) null else e.detail,
                            onClick = { vm.startSession(e) },
                        )
                    }
                    if (!env.minimal) {
                        Spacer(Modifier.height(4.dp))
                        Chip(
                            text = if (energy == Energy.LOW) "✓ I can only manage very little" else "I can only manage very little",
                            selected = energy == Energy.LOW,
                            onClick = { vm.setEnergy(if (energy == Energy.LOW) Energy.OK else Energy.LOW) },
                        )
                    }
                } else {
                    Spacer(Modifier.height(40.dp))
                    Text("Tap the button below.", style = MaterialTheme.typography.headlineMedium, color = c.text)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton(
                    "JUST HELP ME START",
                    { vm.startSession(Experience.JUST_START) },
                    modifier = Modifier.heightIn(min = if (env.minimal) 120.dp else 68.dp),
                )
                if (!env.minimal || !showChoices) {
                    if (env.minimal) {
                        GhostButton("Choose what's happening instead", { showChoices = true })
                    } else {
                        Text(planNote, style = MaterialTheme.typography.bodySmall, color = c.textFaint, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
