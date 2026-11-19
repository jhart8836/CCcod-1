package com.anchorone.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.guidance.StillpointController.Command
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.components.AnchorBackground
import com.anchorone.app.ui.components.CloseAction
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.components.SecondaryButton
import com.anchorone.app.ui.theme.Anchor

/** STILLPOINT — audio-first companion. Deliberately sparse: one line of text, a few commands. */
@Composable
fun StillpointScreen(vm: AppViewModel, prefs: UserPrefs) {
    val c = Anchor.colors
    val env = Anchor.env
    val ui by vm.stillpoint.ui.collectAsStateWithLifecycle()

    // Without the screen-off option, guidance pauses when the app leaves the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, prefs.screenOffAudio) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !prefs.screenOffAudio) vm.stillpoint.hold()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AnchorBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CloseAction({ vm.closeCompanion() }, label = "Stop companion")
                Text("Stillpoint", style = MaterialTheme.typography.titleMedium, color = c.textDim, modifier = Modifier.weight(1f))
                if (!prefs.quietMode) {
                    GhostButton(if (ui.voiceOn) "Voice on" else "Voice off", {
                        val on = !ui.voiceOn
                        vm.stillpoint.setVoice(on, prefs.speechRate)
                        vm.updatePrefs { it.copy(companionVoice = on) }
                    }, color = if (ui.voiceOn) c.accent else c.textDim)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val animate = !env.reducedMotion && !ui.held
                val t = rememberInfiniteTransition(label = "still")
                val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "p")
                Canvas(Modifier.size(300.dp)) {
                    val p = if (animate) phase else 0.5f
                    for (i in 0 until 2) {
                        val q = (p + i * 0.5f) % 1f
                        drawCircle(c.accent.copy(alpha = (if (animate) (1f - q) else 0.5f) * 0.18f), radius = size.minDimension / 2 * (0.55f + 0.4f * q), style = Stroke(1.dp.toPx()))
                    }
                }
                AnimatedContent(
                    targetState = ui.text,
                    transitionSpec = {
                        if (env.reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else fadeIn(tween(600)) togetherWith fadeOut(tween(300))
                    },
                    label = "line",
                ) { text ->
                    Text(
                        text.ifBlank { "Paused. Tap \"Keep guiding me\" to continue." },
                        style = if (env.simplified) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
                        color = c.text,
                        modifier = Modifier.padding(horizontal = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            if (ui.voiceUnavailable && ui.voiceOn) {
                Text(
                    "Voice isn't available on this device, so guidance is shown as text.",
                    style = MaterialTheme.typography.bodySmall, color = c.textFaint,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (ui.held && ui.text.isNotBlank()) {
                Text("Paused.", style = MaterialTheme.typography.bodySmall, color = c.textFaint, modifier = Modifier.padding(bottom = 6.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                when {
                    ui.closing -> PrimaryButton("Continue to my day", { vm.companionToReEntry() })
                    ui.awaitingDone -> PrimaryButton("Done", { vm.companionCommand(Command.TASK_DONE) })
                    else -> Unit
                }
                if (!ui.closing) {
                    if (env.minimal) {
                        SecondaryButton("Keep guiding me", { vm.companionCommand(Command.KEEP_GUIDING) })
                        GhostButton("I'm ready to continue my day", { vm.companionCommand(Command.READY) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("Keep guiding me", { vm.companionCommand(Command.KEEP_GUIDING) }, Modifier.weight(1f))
                            SecondaryButton("Give me something to do", { vm.companionCommand(Command.SOMETHING_TO_DO) }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton(
                                if (ui.lessTalking) "More talking" else "Less talking",
                                { vm.companionCommand(if (ui.lessTalking) Command.MORE_TALKING else Command.LESS_TALKING) },
                                Modifier.weight(1f),
                            )
                            SecondaryButton("Switch activity", { vm.companionCommand(Command.SWITCH) }, Modifier.weight(1f))
                        }
                        PrimaryButton("I'm ready to continue my day", { vm.companionCommand(Command.READY) })
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
