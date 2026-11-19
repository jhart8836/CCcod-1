package com.anchorone.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.AnchorCore
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.components.BodyText
import com.anchorone.app.ui.components.Card
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.IconAction
import com.anchorone.app.ui.components.ScreenScaffold
import com.anchorone.app.ui.components.SectionLabel
import com.anchorone.app.ui.components.ToggleRow
import com.anchorone.app.ui.theme.Anchor
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(vm: AppViewModel, prefs: UserPrefs) {
    val c = Anchor.colors
    var confirm by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold("History", onBack = { vm.back() }) {
        ToggleRow("Keep history", "Only on this phone. Turning off deletes it.", prefs.historyEnabled) { v ->
            vm.updatePrefs { it.copy(historyEnabled = v, history = if (v) it.history else emptyList()) }
        }
        BodyText("A plain record of what you used — no scores, no streaks.")
        if (prefs.history.isEmpty()) {
            Text("Nothing here yet.", style = MaterialTheme.typography.titleMedium, color = c.textDim, modifier = Modifier.padding(top = 12.dp))
        } else {
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            prefs.history.forEach { e ->
                Card {
                    Text(fmt.format(Date(e.timestamp)), style = MaterialTheme.typography.labelMedium, color = c.textFaint)
                    Text(e.experience.label, style = MaterialTheme.typography.titleMedium, color = c.text)
                    if (e.activities.isNotEmpty()) {
                        Text(e.activities.joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                    }
                    val mins = e.durationSeconds / 60
                    Text(if (mins < 1) "Under a minute" else "$mins min", style = MaterialTheme.typography.bodySmall, color = c.textFaint)
                }
            }
            if (!confirm) GhostButton("Clear history", { confirm = true }, color = c.alert)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Yes, clear it", { vm.clearHistory(); confirm = false }, color = c.alert)
                GhostButton("Cancel", { confirm = false })
            }
        }
    }
}

/**
 * Innovation — Steady-Self Plan: while calm, the person chooses up to four activities.
 * "Just help me start" then runs their own plan in order — no decisions needed in the moment.
 */
@Composable
fun PlanScreen(vm: AppViewModel, prefs: UserPrefs) {
    val c = Anchor.colors
    ScreenScaffold("My steady-self plan", onBack = { vm.back() }) {
        BodyText(
            "Decide now, while things are calmer, what helps you most. When you tap \"Just help me start\", " +
                "ANCHOR runs these in order — no choosing in the moment. You can still skip or switch at any time.",
        )
        ToggleRow("Use my plan for \"Just help me start\"", null, prefs.planEnabled) { v -> vm.updatePrefs { it.copy(planEnabled = v) } }

        SectionLabel("Your plan (${prefs.plan.size}/${AnchorCore.MAX_PLAN_STEPS})")
        if (prefs.plan.isEmpty()) {
            Text("No steps yet. Add some below.", style = MaterialTheme.typography.bodyMedium, color = c.textDim)
        }
        prefs.plan.forEachIndexed { i, a ->
            Card(padding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = c.accent, modifier = Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(a.tagline, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                    }
                    if (i > 0) IconAction(Icons.Filled.KeyboardArrowUp, "Move ${a.title} up", {
                        vm.updatePrefs { p ->
                            val l = p.plan.toMutableList(); val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t; p.copy(plan = l)
                        }
                    })
                    IconAction(Icons.Filled.Close, "Remove ${a.title}", { vm.updatePrefs { p -> p.copy(plan = p.plan.filterIndexed { j, _ -> j != i }) } })
                }
            }
        }
        if (prefs.plan.size < AnchorCore.MAX_PLAN_STEPS) {
            SectionLabel("Add a step")
            ActivityId.entries.filter { it !in prefs.plan }.forEach { a ->
                ChoiceCard(a.title, a.tagline, {
                    vm.updatePrefs { p ->
                        val plan = (p.plan + a).distinct().take(AnchorCore.MAX_PLAN_STEPS)
                        p.copy(plan = plan, planEnabled = if (p.plan.isEmpty()) true else p.planEnabled)
                    }
                })
            }
        }
    }
}
