package com.anchorone.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anchorone.app.content.ReEntryContent
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.ActivityCatalog
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.Density
import com.anchorone.app.engine.Phase
import com.anchorone.app.engine.Rating
import com.anchorone.app.engine.SessionEvent
import com.anchorone.app.engine.SessionState
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.activities.ActivityHost
import com.anchorone.app.ui.components.AnchorBackground
import com.anchorone.app.ui.components.BodyText
import com.anchorone.app.ui.components.Chip
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.CloseAction
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.components.SecondaryButton
import com.anchorone.app.ui.components.SectionLabel
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics

@Composable
fun SessionScreen(vm: AppViewModel, prefs: UserPrefs, onSetDown: () -> Unit) {
    val state by vm.session.collectAsStateWithLifecycle()
    val s = state
    if (s == null) {
        LaunchedEffect(Unit) { vm.home() }
        AnchorBackground {}
        return
    }
    val env = Anchor.env
    val suggestion by vm.densitySuggestion.collectAsStateWithLifecycle()
    var showSwitcher by remember { mutableStateOf(false) }

    LaunchedEffect(s.phase, s.activity, s.immediate?.id) {
        vm.speakIfEnabled(spokenText(s))
    }

    AnchorBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            SessionTopBar(
                s = s,
                onExit = { vm.stopSpeaking(); vm.exitSession() },
                onPause = { vm.dispatch(SessionEvent.Pause) },
                onDensity = {
                    vm.setDensity(
                        when (prefs.density) {
                            Density.NORMAL -> Density.SIMPLIFIED
                            Density.SIMPLIFIED -> Density.MINIMAL
                            Density.MINIMAL -> Density.NORMAL
                        },
                    )
                },
                onCompanion = { vm.openCompanion() },
            )

            val sug = suggestion
            AnimatedVisibility(sug != null && s.phase != Phase.RATING && s.phase != Phase.TRANSITION) {
                if (sug != null) DensityBanner(sug.message, { vm.setDensity(sug.density) }, { vm.dismissDensitySuggestion() })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = phaseKey(s),
                    transitionSpec = {
                        if (env.reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(320)) togetherWith fadeOut(tween(180))
                    },
                    label = "phase",
                ) { key ->
                    // Always render from the live state; the key only drives the transition.
                    val live = s
                    if (phaseKey(live) != key) return@AnimatedContent
                    when (live.phase) {
                        Phase.IMMEDIATE -> ImmediateStep(live) { vm.dispatch(SessionEvent.CompleteImmediate) }
                        Phase.STABILIZE, Phase.FOLLOW_UP -> live.activity?.let { act ->
                            ActivityHost(
                                activity = act,
                                paused = live.paused,
                                prefs = prefs,
                                vm = vm,
                                onDone = { vm.dispatch(SessionEvent.CompleteActivity) },
                                onMoveOn = { vm.dispatch(SessionEvent.ReadyToMoveOn) },
                                onSwitchTo = { vm.dispatch(SessionEvent.SwitchTo(it)) },
                            )
                        }
                        Phase.CHECKPOINT -> Checkpoint(live, vm)
                        Phase.RATING -> RatingStep(live, vm)
                        Phase.TRANSITION -> ReEntryRamp(vm, prefs, onSetDown)
                        Phase.ENDED -> Unit
                    }
                }
            }

            if (s.isActivityPhase) {
                SessionBottomBar(
                    s = s,
                    onDifferent = { vm.dispatch(SessionEvent.SomethingDifferent) },
                    onChoose = { showSwitcher = true },
                    onMoveOn = { vm.dispatch(SessionEvent.ReadyToMoveOn) },
                )
            }
        }

        if (s.paused) PausedOverlay({ vm.dispatch(SessionEvent.Resume) }, { vm.exitSession() })
        if (showSwitcher) {
            ActivitySwitcher(
                s = s,
                onPick = { showSwitcher = false; vm.dispatch(SessionEvent.SwitchTo(it)) },
                onDismiss = { showSwitcher = false },
            )
        }
    }
}

private fun phaseKey(s: SessionState): String = "${s.phase}:${s.activity}:${s.immediate?.id}"

private fun spokenText(s: SessionState): String = when (s.phase) {
    Phase.IMMEDIATE -> s.immediate?.instruction.orEmpty()
    Phase.STABILIZE, Phase.FOLLOW_UP -> s.activity?.let { "${it.title}. ${it.tagline}." }.orEmpty()
    Phase.CHECKPOINT -> "What would help next?"
    Phase.RATING -> ""
    Phase.TRANSITION -> "What's next for you?"
    Phase.ENDED -> ""
}

@Composable
private fun SessionTopBar(s: SessionState, onExit: () -> Unit, onPause: () -> Unit, onDensity: () -> Unit, onCompanion: () -> Unit) {
    val env = Anchor.env
    val c = Anchor.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        CloseAction(onExit)
        val title = when (s.phase) {
            Phase.IMMEDIATE -> "First step"
            Phase.STABILIZE, Phase.FOLLOW_UP -> if (env.minimal) "" else s.activity?.title.orEmpty()
            Phase.CHECKPOINT -> ""
            Phase.RATING -> ""
            Phase.TRANSITION -> "Moving on"
            Phase.ENDED -> ""
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = c.textDim,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (!env.minimal && s.isActivityPhase) GhostButton("Pause", onPause, compact = true)
        if (env.density == Density.NORMAL && !env.quiet) GhostButton("Voice", onCompanion, compact = true)
        GhostButton(
            when (env.density) {
                Density.NORMAL -> "Simpler"
                Density.SIMPLIFIED -> "Simplest"
                Density.MINIMAL -> "Fuller"
            },
            onDensity,
            color = c.accent,
            compact = true,
        )
    }
}

@Composable
private fun DensityBanner(message: String, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val c = Anchor.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.accentSoft)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
        GhostButton("Yes", onAccept, color = c.accent)
        GhostButton("No", onDismiss)
    }
}

/**
 * Innovation — Anywhere Tap: the entire screen is the button, so shaky hands
 * can't miss it. Works identically in every density mode.
 */
@Composable
private fun ImmediateStep(s: SessionState, onDone: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    val action = s.immediate ?: return
    Column(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Done") {
                haptics.confirm()
                onDone()
            }
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            action.instruction,
            style = if (env.simplified) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
            color = c.text,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (!env.minimal) {
            Spacer(Modifier.height(16.dp))
            Text(action.note, style = MaterialTheme.typography.bodyLarge, color = c.textDim)
        }
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.padding(start = 10.dp))
            Text("Tap anywhere to continue", style = MaterialTheme.typography.labelLarge, color = c.accent)
        }
    }
}

@Composable
private fun SessionBottomBar(s: SessionState, onDifferent: () -> Unit, onChoose: () -> Unit, onMoveOn: () -> Unit) {
    val env = Anchor.env
    val c = Anchor.colors
    var why by rememberSaveable(s.activity) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (env.density == Density.NORMAL && why && s.activity != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Why this?", style = MaterialTheme.typography.titleSmall, color = c.text)
                s.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.textDim) }
                Text(s.activity.rationale, style = MaterialTheme.typography.bodySmall, color = c.textDim)
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val w = Modifier.weight(1f)
            when (env.density) {
                Density.MINIMAL -> GhostButton("Something else", onDifferent, w)
                Density.SIMPLIFIED -> {
                    GhostButton("Something else", onDifferent, w, compact = true)
                    GhostButton("Move on", onMoveOn, w, compact = true)
                }
                Density.NORMAL -> {
                    GhostButton("Why this?", { why = !why }, w, compact = true)
                    GhostButton("Different", onDifferent, w, compact = true)
                    GhostButton("Choose", onChoose, w, compact = true)
                    GhostButton("Move on", onMoveOn, w, compact = true)
                }
            }
        }
    }
}

@Composable
private fun Checkpoint(s: SessionState, vm: AppViewModel) {
    val env = Anchor.env
    val c = Anchor.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("What would help next?", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
        if (!env.simplified) {
            BodyText("You don't need to feel fully settled to take the next step.")
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("I'm ready to move on", { vm.dispatch(SessionEvent.ReadyToMoveOn) })
        if (env.minimal) {
            SecondaryButton("One more activity", { vm.dispatch(SessionEvent.Continue) })
        } else {
            ChoiceCard("Another activity", if (env.simplified) null else "A follow-up chosen for you", { vm.dispatch(SessionEvent.Continue) })
            ChoiceCard("Something different", if (env.simplified) null else "Try another kind of activity", { vm.dispatch(SessionEvent.SomethingDifferent) })
            if (env.density == Density.NORMAL && !env.quiet) {
                ChoiceCard("Voice companion", "Short spoken guidance, minimal screen", { vm.openCompanion() })
            }
        }
        if (s.single && !env.minimal) {
            GhostButton("Back to toolkit", { vm.finishToToolkit() })
        }
    }
}

@Composable
private fun RatingStep(s: SessionState, vm: AppViewModel) {
    val c = Anchor.colors
    val choices = remember { mutableStateMapOf<ActivityId, Rating>() }
    val items = s.completed.distinct().takeLast(3)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Was this useful?", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
        BodyText("Optional. It only changes what ANCHOR suggests first, on this phone.")
        items.forEach { act ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(act.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rating.entries.forEach { r ->
                        Chip(r.label, choices[act] == r, { choices[act] = r }, Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Done", {
            choices.forEach { (a, r) -> vm.rate(a, r) }
            vm.dispatch(SessionEvent.FinishRating)
        })
        GhostButton("Skip", { vm.dispatch(SessionEvent.FinishRating) }, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/**
 * Innovation — Re-entry Ramp: instead of ending on "you did it", the session ends by
 * turning the person toward a real next activity in three graduated, concrete steps,
 * using options they've saved before.
 */
@Composable
private fun ReEntryRamp(vm: AppViewModel, prefs: UserPrefs, onSetDown: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var custom by rememberSaveable { mutableStateOf("") }
    var typing by rememberSaveable { mutableStateOf(false) }
    val checked = remember(chosen) { mutableStateMapOf<Int, Boolean>() }

    val anchors = prefs.reEntryAnchors.map { ReEntryContent.custom(it) } + ReEntryContent.defaults
    val anchor = chosen?.let { label -> anchors.firstOrNull { it.label == label } ?: ReEntryContent.custom(label) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        if (anchor == null) {
            Text("What's next for you?", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
            if (!env.simplified) BodyText("Pick the closest. You'll get three small steps to get going.")
            val shown = if (env.minimal) anchors.take(3) else anchors.take(7)
            shown.forEach { a -> ChoiceCard(a.label, null, { chosen = a.label }) }
            if (typing) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(60) },
                    label = { Text("Something else") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (custom.isNotBlank()) {
                            val label = custom.trim()
                            vm.updatePrefs { p -> p.copy(reEntryAnchors = (listOf(label) + p.reEntryAnchors).distinct().take(6)) }
                            chosen = label
                        }
                    }),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton("Use this", {
                    if (custom.isNotBlank()) {
                        val label = custom.trim()
                        vm.updatePrefs { p -> p.copy(reEntryAnchors = (listOf(label) + p.reEntryAnchors).distinct().take(6)) }
                        chosen = label
                    }
                }, enabled = custom.isNotBlank())
            } else if (!env.minimal) {
                GhostButton("Something else…", { typing = true })
            }
            GhostButton("Skip this", { vm.dispatch(SessionEvent.Finish) }, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Text(anchor.label, style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
            if (!env.simplified) BodyText("Three small steps. Tick them off if it helps — or don't.")
            anchor.steps.forEachIndexed { i, step ->
                val on = checked[i] == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) c.accentSoft else c.surface)
                        .clickable(role = Role.Checkbox) { checked[i] = !on }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (on) c.accent else c.surfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (on) "✓" else "${i + 1}", color = if (on) c.onAccent else c.textDim, style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.padding(start = 14.dp))
                    Text(step, style = MaterialTheme.typography.titleMedium, color = if (on) c.accent else c.text)
                }
            }
            val carry = prefs.supportCard.message.ifBlank { prefs.supportCard.reminderLines().firstOrNull().orEmpty() }
            if (carry.isNotBlank() && !env.minimal) {
                SectionLabel("From your card")
                Text("“$carry”", style = MaterialTheme.typography.bodyLarge, color = c.textDim)
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Set the phone down", {
                vm.dispatch(SessionEvent.Finish)
                onSetDown()
            })
            SecondaryButton("Finish", { vm.dispatch(SessionEvent.Finish) })
            if (!env.minimal) {
                GhostButton("Not yet — one more activity", { vm.dispatch(SessionEvent.SomethingDifferent) }, Modifier.align(Alignment.CenterHorizontally))
                GhostButton("Choose a different next thing", { chosen = null }, Modifier.align(Alignment.CenterHorizontally))
                if (!env.simplified) {
                    Text(
                        "Tip: long-press the app icon for instant shortcuts next time.",
                        style = MaterialTheme.typography.bodySmall, color = c.textFaint, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Anchor.colors.accent,
    unfocusedBorderColor = Anchor.colors.outline,
    focusedLabelColor = Anchor.colors.accent,
    unfocusedLabelColor = Anchor.colors.textDim,
    cursorColor = Anchor.colors.accent,
    focusedTextColor = Anchor.colors.text,
    unfocusedTextColor = Anchor.colors.text,
)

@Composable
private fun PausedOverlay(onResume: () -> Unit, onEnd: () -> Unit) {
    val c = Anchor.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.bgBottom.copy(alpha = 0.94f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Paused", style = MaterialTheme.typography.displayMedium, color = c.text)
            BodyText("Take whatever time you need.")
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Resume", onResume)
            GhostButton("End session", onEnd)
        }
    }
}

@Composable
private fun ActivitySwitcher(s: SessionState, onPick: (ActivityId) -> Unit, onDismiss: () -> Unit) {
    val c = Anchor.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.bgTop)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose an activity", style = MaterialTheme.typography.titleLarge, color = c.text)
            ActivityCatalog.switchCandidates(s.experience).filter { it != s.activity }.forEach { a ->
                ChoiceCard(a.title, a.tagline, { onPick(a) })
            }
            GhostButton("Cancel", onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
