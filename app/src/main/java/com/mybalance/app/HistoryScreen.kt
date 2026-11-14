package com.mybalance.app

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Pagina "Cronologia": pagamenti periodici e storico dei movimenti. */
@Composable
fun HistoryScreen(vm: MainViewModel, onEdit: (Tx) -> Unit) {
    val l = LocalL.current
    val p = LocalPalette.current
    val profile = vm.profile ?: return
    val cur = profile.currency
    val today = LocalDate.now()
    var pendingDelete by remember { mutableStateOf<Tx?>(null) }

    var query by rememberSaveable { mutableStateOf("") }
    var typeIdx by rememberSaveable { mutableStateOf(0) } // 0 tutti, 1 incassi, 2 spese
    var atLeast by rememberSaveable { mutableStateOf(true) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var panelOpen by rememberSaveable { mutableStateOf(false) }

    val typeFilter: TxType? = when (typeIdx) {
        1 -> TxType.INCOME
        2 -> TxType.EXPENSE
        else -> null
    }
    val amountFilter: Long? = parseCents(amountText)
    val filtersOn = typeFilter != null || amountFilter != null
    val searching = filtersOn || query.isNotBlank()
    val shown = vm.txs.filter { txMatches(it, query, typeFilter, amountFilter, atLeast) }
    val recurring = shown.filter { it.period != Period.ONCE }.sortedByDescending { it.id }
    val history = remember(vm.txs, today, query, typeIdx, atLeast, amountText) { buildHistory(shown, today) }
    val maxCents = maxOf(100L, vm.txs.maxOfOrNull { it.cents } ?: 0L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(l("History", "Cronologia"), color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    label = l("Name", "Nome"),
                    modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Search
                )
                val fshape = RoundedCornerShape(18.dp)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(fshape)
                        .background(if (filtersOn) p.accent else p.surface)
                        .border(1.dp, if (filtersOn) p.accent else p.outline, fshape)
                        .clickable { panelOpen = !panelOpen },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(Ic.FUNNEL, if (filtersOn) p.onAccent else p.text, iconSize = 24.dp)
                }
            }
        }
        item {
            AnimatedVisibility(visible = panelOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionLabel(l("Type", "Tipo"))
                    ChipFlow {
                        Chip(l("All", "Tutti"), typeIdx == 0, { typeIdx = 0 })
                        Chip(l("Income", "Incasso"), typeIdx == 1, { typeIdx = 1 }, tint = p.positive)
                        Chip(l("Expense", "Spesa"), typeIdx == 2, { typeIdx = 2 }, tint = p.negative)
                    }
                    SectionLabel(l("Amount", "Importo"))
                    ChipFlow {
                        Chip(l("From", "A partire da"), atLeast, { atLeast = true })
                        Chip(l("Up to", "Fino a"), !atLeast, { atLeast = false })
                    }
                    AppField(
                        value = amountText,
                        onValueChange = { amountText = sanitizeAmount(it) },
                        label = l("Amount", "Importo"),
                        keyboardType = KeyboardType.Decimal,
                        suffix = currencySymbol(cur),
                        imeAction = ImeAction.Done
                    )
                    Slider(
                        value = ((amountFilter ?: 0L).toDouble() / maxCents.toDouble()).coerceIn(0.0, 1.0).toFloat(),
                        onValueChange = { f ->
                            val step = if (maxCents >= 10000L) 100L else 1L
                            val c = Math.round(f.toDouble() * maxCents.toDouble() / step.toDouble()) * step
                            amountText = plainAmount(c)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = p.accent,
                            activeTrackColor = p.accent,
                            inactiveTrackColor = p.track
                        )
                    )
                    LinkButton(l("Reset filters", "Azzera filtri")) {
                        query = ""
                        typeIdx = 0
                        atLeast = true
                        amountText = ""
                    }
                }
            }
        }

        item {
            Text(
                l("Recurring", "Periodiche"),
                color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (recurring.isEmpty()) {
            item {
                Text(
                    if (searching) l("No results.", "Nessun risultato.") else l(
                        "No recurring income or expenses yet. Add one from the Income/Expense page.",
                        "Nessuna entrata o uscita periodica. Aggiungine una dalla pagina Entrata/Uscita."
                    ),
                    color = p.muted, fontSize = 14.sp
                )
            }
        }
        items(recurring, key = { it.id }) { tx ->
            RecurringRow(
                modifier = Modifier.animateItem(),
                tx = tx,
                currency = cur,
                today = today,
                editing = false,
                onEdit = { onEdit(tx) },
                onDelete = { pendingDelete = tx },
                note = fundNote(tx, vm.funds, l)
            )
        }

        item {
            Text(
                l("Transactions", "Movimenti"),
                color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item {
            Text(
                l(
                    "Last 30 days. Cancel a payment that did not happen.",
                    "Ultimi 30 giorni. Annulla un pagamento che non è avvenuto."
                ),
                color = p.muted, fontSize = 13.sp
            )
        }
        if (history.isEmpty()) {
            item {
                Text(if (searching) l("No results.", "Nessun risultato.") else l("Nothing here yet.", "Ancora nessun movimento."), color = p.muted, fontSize = 14.sp)
            }
        }
        items(history, key = { it.key }) { h ->
            if (h.occurrence) {
                OccurrenceRow(
                    tx = h.tx,
                    currency = cur,
                    date = h.date,
                    cancelled = h.cancelled,
                    onCancel = { vm.skipOccurrence(h.tx.id, h.date) },
                    onRestore = { vm.restoreOccurrence(h.tx.id, h.date) },
                    modifier = Modifier.animateItem(),
                    note = fundNote(h.tx, vm.funds, l)
                )
            } else {
                TxRow(h.tx, cur, onDelete = { pendingDelete = h.tx }, note = fundNote(h.tx, vm.funds, l), modifier = Modifier.animateItem())
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDialog(
            title = l("Delete this transaction?", "Eliminare questo movimento?"),
            message = if (toDelete.period == Period.ONCE) {
                toDelete.name
            } else {
                toDelete.name + "\n\n" + l(
                    "Its past occurrences will also be removed from your balance.",
                    "Anche le ripetizioni passate verranno tolte dal bilancio."
                )
            },
            confirm = l("Delete", "Elimina"),
            danger = true,
            onConfirm = {
                vm.deleteTx(toDelete.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
