package com.mybalance.app

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Riga di un movimento (usata sia nel Bilancio sia in Entrata/Uscita). */
@Composable
fun TxRow(tx: Tx, currency: String, onDelete: (() -> Unit)? = null, note: String? = null, modifier: Modifier = Modifier) {
    val l = LocalL.current
    val p = LocalPalette.current
    val income = tx.type == TxType.INCOME
    val tone = if (income) p.positive else p.negative
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(if (income) Ic.ARROW_DOWN else Ic.ARROW_UP, tone, iconSize = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${tx.tag.label(l)} · ${tx.period.label(l)}" + (if (note != null) " · " + note else ""),
                color = p.muted, fontSize = 13.sp, maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (income) "+" else "-") + money(tx.cents, currency),
            color = tone, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1
        )
        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(Ic.TRASH, p.muted, iconSize = 20.dp)
            }
        }
    }
}

/** Pagina centrale: Bilancio. */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val l = LocalL.current
    val p = LocalPalette.current
    val profile = vm.profile ?: return
    val name = vm.account?.username ?: ""
    val cur = profile.currency
    val today = LocalDate.now()
    val balance = balanceCents(profile, vm.txs, today)
    val gauge = gaugeFor(profile, balance)
    val totals = monthTotals(vm.txs, today)

    val balanceText = money(balance, cur)
    val numberSize = when {
        balanceText.length <= 9 -> 44
        balanceText.length <= 12 -> 36
        else -> 28
    }
    val sub = when (profile.goal) {
        GoalType.REACH -> l("Goal ", "Obiettivo ") + money(profile.targetCents, cur, alwaysDecimals = false)
        GoalType.FLOOR -> l("Threshold ", "Soglia ") + money(profile.targetCents, cur, alwaysDecimals = false)
        GoalType.TRACK -> l("Monitoring", "Monitoraggio")
    }
    val status: String? = when (profile.goal) {
        GoalType.REACH ->
            if (balance >= profile.targetCents) l("Goal reached!", "Obiettivo raggiunto!")
            else l("Missing ", "Mancano ") + money(profile.targetCents - balance, cur)
        GoalType.FLOOR ->
            if (balance < profile.targetCents) {
                l("Below the threshold by ", "Sotto la soglia di ") + money(profile.targetCents - balance, cur)
            } else {
                money(balance - profile.targetCents, cur) + l(" above the threshold", " sopra la soglia")
            }
        GoalType.TRACK -> null
    }
    val statusBad = profile.goal == GoalType.FLOOR && gauge.below

    val recent = vm.txs.sortedByDescending { it.id }.take(5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(l("Hi, ", "Ciao, ") + name, color = p.muted, fontSize = 15.sp)
            Text(l("Balance", "Bilancio"), color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        BalanceGauge(
            fraction = gauge.fraction,
            minLabel = money(gauge.minCents, cur, alwaysDecimals = false),
            maxLabel = money(gauge.maxCents, cur, alwaysDecimals = false),
            caption = l("Balance", "Bilancio"),
            centerText = balanceText,
            centerSize = numberSize.sp,
            subText = sub,
            alert = statusBad || balance < 0L
        )

        if (status != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    status,
                    color = if (statusBad) p.negative else p.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background((if (statusBad) p.negative else p.accent).copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = l("Income this month", "Entrate del mese"),
                value = money(totals.first, cur),
                tone = p.positive
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = l("Expenses this month", "Uscite del mese"),
                value = money(totals.second, cur),
                tone = p.negative
            )
        }

        Text(
            l("Recent activity", "Ultimi movimenti"),
            color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (recent.isEmpty()) {
            Text(
                l("No transactions yet. Add one from the left tab.", "Nessun movimento. Aggiungine uno dalla scheda a sinistra."),
                color = p.muted, fontSize = 14.sp
            )
        } else {
            recent.forEachIndexed { i, tx -> TxRow(tx, cur, note = fundNote(tx, vm.funds, l), modifier = Modifier.appear(i + 2)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, label: String, value: String, tone: androidx.compose.ui.graphics.Color) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(p.surface)
            .padding(14.dp)
    ) {
        Text(label, color = p.muted, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, color = tone, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
