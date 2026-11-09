package com.mybalance.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pagina di sinistra: aggiungi un'entrata o un'uscita (stessa maschera) e vedi i movimenti. */
@Composable
fun TransactionsScreen(vm: MainViewModel) {
    val l = LocalL.current
    val p = LocalPalette.current
    val profile = vm.profile ?: return
    val cur = profile.currency
    val focus = LocalFocusManager.current

    var typeIdx by rememberSaveable { mutableStateOf(0) } // 0 = entrata, 1 = uscita
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var periodIdx by rememberSaveable { mutableStateOf(Period.ONCE.ordinal) }
    var tagIdx by rememberSaveable { mutableStateOf(Tag.OTHER.ordinal) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Tx?>(null) }

    val isIncome = typeIdx == 0
    val cents = parseCents(amount)
    val nameErr = if (tried && name.isBlank()) l("Enter a name", "Inserisci un nome") else null
    val amountErr = if (tried && (cents == null || cents <= 0L)) {
        l("Enter an amount greater than zero", "Inserisci un importo maggiore di zero")
    } else null

    val sorted = vm.txs.sortedByDescending { it.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    l("Income / Expense", "Entrata / Uscita"),
                    color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(l("Income", "Entrata"), isIncome, { typeIdx = 0 })
                    Chip(l("Expense", "Uscita"), !isIncome, { typeIdx = 1 })
                }

                AppField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = if (isIncome) l("Income name", "Nome entrata") else l("Expense name", "Nome uscita"),
                    error = nameErr
                )
                AppField(
                    value = amount,
                    onValueChange = { amount = sanitizeAmount(it) },
                    label = l("Amount", "Importo"),
                    keyboardType = KeyboardType.Decimal,
                    suffix = currencySymbol(cur),
                    imeAction = ImeAction.Done,
                    error = amountErr
                )

                SectionLabel(l("Period", "Periodo"))
                ChipFlow {
                    for (pr in Period.values()) {
                        Chip(pr.label(l), periodIdx == pr.ordinal, { periodIdx = pr.ordinal })
                    }
                }

                SectionLabel(l("Tag", "Tag"))
                ChipFlow {
                    for (tg in Tag.values()) {
                        Chip(tg.label(l), tagIdx == tg.ordinal, { tagIdx = tg.ordinal })
                    }
                }

                Spacer(Modifier.height(4.dp))
                PrimaryButton(
                    if (isIncome) l("Add income", "Aggiungi entrata") else l("Add expense", "Aggiungi uscita")
                ) {
                    tried = true
                    if (name.isNotBlank() && cents != null && cents > 0L) {
                        vm.addTx(
                            type = if (isIncome) TxType.INCOME else TxType.EXPENSE,
                            name = name.trim(),
                            cents = cents,
                            period = Period.values()[periodIdx],
                            tag = Tag.values()[tagIdx]
                        )
                        name = ""
                        amount = ""
                        periodIdx = Period.ONCE.ordinal
                        tagIdx = Tag.OTHER.ordinal
                        tried = false
                        focus.clearFocus()
                    }
                }

                Text(
                    l("Transactions", "Movimenti"),
                    color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (sorted.isEmpty()) {
                    Text(l("Nothing here yet.", "Ancora nessun movimento."), color = p.muted, fontSize = 14.sp)
                }
            }
        }

        items(sorted, key = { it.id }) { tx ->
            TxRow(tx, cur, onDelete = { pendingDelete = tx })
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDialog(
            title = l("Delete this transaction?", "Eliminare questo movimento?"),
            message = toDelete.name,
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
