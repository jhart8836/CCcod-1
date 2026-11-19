package com.anchorone.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anchorone.app.data.SupportCard
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.components.BodyText
import com.anchorone.app.ui.components.Card
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.components.ScreenScaffold
import com.anchorone.app.ui.components.SecondaryButton
import com.anchorone.app.ui.components.SectionLabel
import com.anchorone.app.ui.components.ToggleRow
import com.anchorone.app.ui.theme.Anchor

private const val DEFAULT_TEXT = "Hi, I'm having a hard moment. Could you call or text me when you can?"

@Composable
fun SupportCardScreen(vm: AppViewModel, prefs: UserPrefs) {
    val c = Anchor.colors
    val context = LocalContext.current
    val card = prefs.supportCard
    var editing by rememberSaveable { mutableStateOf(card.isEmpty) }
    var reminders by rememberSaveable { mutableStateOf(card.reminders) }
    var steps by rememberSaveable { mutableStateOf(card.steps) }
    var message by rememberSaveable { mutableStateOf(card.message) }
    var name by rememberSaveable { mutableStateOf(card.contactName) }
    var phone by rememberSaveable { mutableStateOf(card.contactPhone) }

    ScreenScaffold(
        "My support card",
        onBack = { vm.back() },
        action = if (!editing) ({ GhostButton("Edit", { editing = true }, color = c.accent) }) else null,
    ) {
        if (editing) {
            BodyText("Write this when you're feeling steadier. Short lines work best. Everything stays on this phone.")
            OutlinedTextField(
                reminders, { reminders = it.take(600) },
                label = { Text("Reminders that help me (one per line)") },
                placeholder = { Text("e.g. I've got through this before.") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                steps, { steps = it.take(600) },
                label = { Text("Steps that help me (one per line)") },
                placeholder = { Text("e.g. Step outside for a few minutes") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                message, { message = it.take(300) },
                label = { Text("A message from steady me") },
                placeholder = { Text("Something you'd want to hear in a hard moment") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel("Trusted contact (optional)")
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                phone, { phone = it.filter { ch -> ch.isDigit() || ch in "+ -()" }.take(24) },
                label = { Text("Phone number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "ANCHOR never calls or messages anyone by itself. Buttons only open your phone or messages app with the details filled in.",
                style = MaterialTheme.typography.bodySmall, color = c.textDim,
            )
            PrimaryButton("Save card", {
                vm.updatePrefs { it.copy(supportCard = SupportCard(reminders.trim(), steps.trim(), message.trim(), name.trim(), phone.trim())) }
                editing = false
            })
            if (!card.isEmpty) GhostButton("Cancel", {
                reminders = card.reminders; steps = card.steps; message = card.message; name = card.contactName; phone = card.contactPhone
                editing = false
            })
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(c.surfaceHigh, c.surface)))
                    .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (card.message.isNotBlank()) {
                    Text("“${card.message}”", style = MaterialTheme.typography.headlineSmall, color = c.text)
                }
                if (card.reminderLines().isNotEmpty()) {
                    SectionLabel("Reminders")
                    card.reminderLines().forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.text) }
                }
                if (card.stepLines().isNotEmpty()) {
                    SectionLabel("What helps me")
                    card.stepLines().forEachIndexed { i, s -> Text("${i + 1}.  $s", style = MaterialTheme.typography.bodyLarge, color = c.text) }
                }
            }
            if (card.hasContact) {
                SectionLabel(card.contactName.ifBlank { "Trusted contact" })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Call", { ContactIntents.dial(context, card.contactPhone) }, Modifier.weight(1f))
                    SecondaryButton("Text", { ContactIntents.text(context, card.contactPhone, DEFAULT_TEXT) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SafetyScreen(vm: AppViewModel, prefs: UserPrefs) {
    val c = Anchor.colors
    val context = LocalContext.current
    val number = EmergencyNumbers.detect(context, prefs.emergencyNumber)
    ScreenScaffold("Safety & emergency", onBack = { vm.back() }) {
        PrimaryButton("Call emergency services ($number)", { ContactIntents.dial(context, number) })
        Text("Opens your phone app with $number ready. You press call.", style = MaterialTheme.typography.bodySmall, color = c.textDim)
        val card = prefs.supportCard
        if (card.hasContact) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Call ${card.contactName.ifBlank { "contact" }}", { ContactIntents.dial(context, card.contactPhone) }, Modifier.weight(1f))
                SecondaryButton("Text", { ContactIntents.text(context, card.contactPhone, DEFAULT_TEXT) }, Modifier.weight(1f))
            }
        } else {
            GhostButton("Add a trusted contact", { vm.navigate(com.anchorone.app.ui.Screen.SUPPORT_CARD) }, color = c.accent)
        }

        SectionLabel("Don't assume it's anxiety")
        Card {
            BodyText("Get urgent medical help if symptoms are new, severe, different from your usual, or you're not sure. For example:", color = c.text)
            listOf(
                "Chest pain or pressure that is new, different, or spreads to the arm, jaw or back",
                "Fainting, or trouble breathing that doesn't ease",
                "Sudden weakness, numbness, confusion, or trouble speaking",
                "You've taken something harmful, or you've been injured",
                "Thoughts of ending your life or harming yourself",
            ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = c.textDim) }
        }

        SectionLabel("About this app")
        Card {
            BodyText(
                "ANCHOR ONE is a self-help tool. It isn't a medical device or an emergency service, it can't check your body, " +
                    "and it can't tell you whether symptoms are harmless. It doesn't replace professional care.",
            )
            BodyText("Crisis lines differ by country. You can save one as your trusted contact on your support card.")
        }
    }
}

@Composable
fun QuietScreen(vm: AppViewModel, prefs: UserPrefs) {
    val c = Anchor.colors
    ScreenScaffold("Quiet mode", onBack = { vm.back() }) {
        ToggleRow("Quiet mode", "No voice, no vibration, discreet visuals", prefs.quietMode) { v -> vm.updatePrefs { it.copy(quietMode = v) } }
        BodyText("For work, transport or anywhere you'd rather not draw attention. These activities are silent and look ordinary on screen:")
        listOf(ActivityId.SINGLE_THREAD, ActivityId.NEXT_OBJECT, ActivityId.PATTERN_RESTORE, ActivityId.THOUGHT_UNHOOKING, ActivityId.ATTENTION_RELOCATION)
            .forEach { a -> ChoiceCard(a.title, a.tagline, { if (!prefs.quietMode) vm.updatePrefs { it.copy(quietMode = true) }; vm.startSingle(a) }) }
        Text("Tip: long-press the app icon and choose \"Quiet mode\" to open straight here.", style = MaterialTheme.typography.bodySmall, color = c.textFaint)
    }
}
