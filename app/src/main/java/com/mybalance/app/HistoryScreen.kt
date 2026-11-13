package com.mybalance.app

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

    val recurring = vm.txs.filter { it.period != Period.ONCE }.sortedByDescending { it.id }
    val history = remember(vm.txs, today) { buildHistory(vm.txs, today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(l("History", "Cronologia"), color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
                    l(
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
                Text(l("Nothing here yet.", "Ancora nessun movimento."), color = p.muted, fontSize = 14.sp)
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
