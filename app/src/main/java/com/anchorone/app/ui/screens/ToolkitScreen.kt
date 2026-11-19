package com.anchorone.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.Category
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.Screen
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.IconAction
import com.anchorone.app.ui.components.Pressable
import com.anchorone.app.ui.components.ScreenScaffold
import com.anchorone.app.ui.components.SectionLabel
import com.anchorone.app.ui.theme.Anchor

@Composable
fun ToolkitScreen(vm: AppViewModel, prefs: UserPrefs) {
    val env = Anchor.env
    ScreenScaffold("Toolkit", onBack = { vm.back() }) {
        if (!env.simplified) {
            Text(
                "Every activity works offline. Tap to start one directly; the star marks favourites, which ANCHOR suggests first.",
                style = MaterialTheme.typography.bodyMedium, color = Anchor.colors.textDim,
            )
        }
        val favs = ActivityId.entries.filter { it in prefs.favorites }
        if (favs.isNotEmpty() && !env.minimal) {
            SectionLabel("Favourites")
            favs.forEach { ActivityRow(it, prefs, vm) }
        }
        Category.entries.forEach { cat ->
            val items = ActivityId.entries.filter { it.category == cat }
            SectionLabel(cat.label)
            items.forEach { ActivityRow(it, prefs, vm) }
        }
        SectionLabel("Support")
        ChoiceCard("Personal support card", if (env.simplified) null else "Your reminders, steps and trusted contact", { vm.navigate(Screen.SUPPORT_CARD) })
        if (!prefs.quietMode) ChoiceCard("Stillpoint companion", if (env.simplified) null else "Short spoken guidance", { vm.openCompanion() })
        ChoiceCard("Quiet mode", if (env.simplified) null else "Discreet and silent, for work or public places", { vm.navigate(Screen.QUIET) })
        ChoiceCard("My steady-self plan", if (env.simplified) null else "Choose in advance what \"Just help me start\" runs", { vm.navigate(Screen.PLAN) })
        ChoiceCard("Safety & emergency", null, { vm.navigate(Screen.SAFETY) })
    }
}

@Composable
private fun ActivityRow(a: ActivityId, prefs: UserPrefs, vm: AppViewModel) {
    val c = Anchor.colors
    val env = Anchor.env
    val fav = a in prefs.favorites
    Pressable(
        onClick = { vm.startSingle(a) },
        modifier = Modifier.fillMaxWidth().heightIn(min = if (env.simplified) 84.dp else 76.dp),
        border = BorderStroke(1.dp, c.outline),
        label = "Start ${a.title}",
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                if (!env.minimal) {
                    Spacer(Modifier.height(2.dp))
                    Text(a.tagline, style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                }
                if (!env.simplified && (prefs.ratings[a] ?: 0) != 0) {
                    Text(prefs.ratingLabel(a), style = MaterialTheme.typography.bodySmall, color = c.textFaint)
                }
            }
            IconAction(
                Icons.Filled.Star,
                if (fav) "Remove ${a.title} from favourites" else "Add ${a.title} to favourites",
                { vm.updatePrefs { p -> p.copy(favorites = if (fav) p.favorites - a else p.favorites + a) } },
                tint = if (fav) c.accent else c.outline,
            )
        }
    }
}
