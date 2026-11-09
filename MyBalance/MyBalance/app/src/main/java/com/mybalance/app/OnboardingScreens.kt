package com.mybalance.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun GoalCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) p.accent.copy(alpha = 0.12f) else p.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.outline, shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = p.muted, fontSize = 14.sp)
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            AppIcon(Ic.CHECK, p.accent)
        }
    }
}

/** Schermate iniziali: obiettivo -> denaro attuale -> (soglia o somma da raggiungere). */
@Composable
fun OnboardingFlow(vm: MainViewModel) {
    val l = LocalL.current
    val p = LocalPalette.current

    var step by rememberSaveable { mutableStateOf(0) }
    var goalName by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    var nowText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }

    val goal: GoalType? = if (goalName.isEmpty()) null else GoalType.valueOf(goalName)
    val totalSteps = if (goal == GoalType.TRACK) 2 else 3
    val symbol = currencySymbol(currency)

    BackHandler(enabled = step > 0) { step -= 1 }

    fun finish() {
        val g = goal ?: return
        val initial = parseCents(nowText) ?: return
        val target = if (g == GoalType.TRACK) 0L else (parseCents(targetText) ?: return)
        vm.completeOnboarding(Profile(g, initial, currency, target))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // barra in alto: indietro + indicatore di avanzamento
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { step -= 1 },
                    contentAlignment = Alignment.Center
                ) { AppIcon(Ic.BACK, p.text) }
            } else {
                Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until totalSteps) {
                    Box(
                        modifier = Modifier
                            .size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (i <= step) p.accent else p.track)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (step) {
                0 -> {
                    Text(
                        l("What is your goal?", "Qual è il tuo obiettivo?"),
                        color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    GoalCard(
                        l("Reach an amount of money", "Raggiungere una somma di denaro"),
                        l("Save up to a target you choose.", "Risparmia fino a un traguardo che scegli tu."),
                        goal == GoalType.REACH
                    ) { goalName = GoalType.REACH.name }
                    GoalCard(
                        l("Stay above a threshold", "Non scendere sotto una soglia di denaro"),
                        l("Get a warning when you get close.", "Controlla di restare sempre sopra un minimo."),
                        goal == GoalType.FLOOR
                    ) { goalName = GoalType.FLOOR.name }
                    GoalCard(
                        l("Just monitor your money", "Solo monitorare il tuo denaro"),
                        l("No target, just keep track.", "Nessun obiettivo, tieni solo traccia."),
                        goal == GoalType.TRACK
                    ) { goalName = GoalType.TRACK.name }
                }

                1 -> {
                    Text(
                        l("How much money do you have now?", "Quanto denaro hai adesso?"),
                        color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppField(
                            value = nowText,
                            onValueChange = { nowText = sanitizeAmount(it) },
                            label = l("Amount", "Importo"),
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        )
                        CurrencyPicker(currency, { currency = it })
                    }
                }

                else -> {
                    Text(
                        if (goal == GoalType.REACH) {
                            l("Which amount do you want to reach?", "Quale somma di denaro vuoi raggiungere?")
                        } else {
                            l("Below which threshold do you not want to go?", "Sotto quale soglia non vuoi scendere?")
                        },
                        color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    AppField(
                        value = targetText,
                        onValueChange = { targetText = sanitizeAmount(it) },
                        label = if (goal == GoalType.REACH) l("Target amount", "Somma obiettivo") else l("Threshold", "Soglia"),
                        keyboardType = KeyboardType.Decimal,
                        suffix = symbol,
                        imeAction = ImeAction.Done
                    )
                }
            }
        }

        // pulsante in basso
        val nowOk = parseCents(nowText) != null
        val targetCents = parseCents(targetText)
        val targetOk = targetCents != null && (goal != GoalType.REACH || targetCents > 0L)
        when (step) {
            0 -> PrimaryButton(l("Continue", "Continua"), enabled = goal != null) { step = 1 }
            1 -> {
                if (goal == GoalType.TRACK) {
                    PrimaryButton(l("Start", "Inizia"), enabled = nowOk) { finish() }
                } else {
                    PrimaryButton(l("Continue", "Continua"), enabled = nowOk) { step = 2 }
                }
            }
            else -> PrimaryButton(l("Start", "Inizia"), enabled = targetOk) { finish() }
        }
        Spacer(Modifier.height(8.dp))
    }
}
