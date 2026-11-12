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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Icone dei fondi e testi
// ---------------------------------------------------------------------------

val FUND_ICONS = listOf(Ic.BANK, Ic.WALLET, Ic.CARD, Ic.COIN, Ic.SAFE, Ic.BAG, Ic.HOME, Ic.STAR)

fun fundIcon(i: Int): Ic = FUND_ICONS[i.coerceIn(0, FUND_ICONS.size - 1)]

fun fundIconName(i: Int, l: L): String = when (i) {
    0 -> l("Bank", "Banca")
    1 -> l("Wallet", "Portafoglio")
    2 -> l("Card", "Carta")
    3 -> l("Cash", "Contanti")
    4 -> l("Safe", "Cassaforte")
    5 -> l("Bag", "Borsa")
    6 -> l("Home", "Casa")
    else -> l("Other", "Altro")
}

/** Dicitura "Da: Banca" (uscita) o "In: Banca" (entrata); null se il movimento non ha un fondo. */
fun fundNote(tx: Tx, funds: List<Fund>, l: L): String? {
    val id = tx.fundId ?: return null
    val f = funds.firstOrNull { it.id == id } ?: return null
    val prefix = if (tx.type == TxType.INCOME) l("To: ", "In: ") else l("From: ", "Da: ")
    return prefix + f.name
}

// ---------------------------------------------------------------------------
// Componenti
// ---------------------------------------------------------------------------

@Composable
private fun IconPicker(selected: Int, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    val l = LocalL.current
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            AppIcon(fundIcon(selected), p.accent, iconSize = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(fundIconName(selected, l), color = p.text, modifier = Modifier.weight(1f))
            AppIcon(Ic.CHEVRON_DOWN, p.muted, iconSize = 18.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (i in FUND_ICONS.indices) {
                DropdownMenuItem(
                    text = { Text(fundIconName(i, l)) },
                    leadingIcon = { AppIcon(FUND_ICONS[i], p.accent, iconSize = 22.dp) },
                    onClick = {
                        onChange(i)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PctChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) p.accent else p.surface)
            .border(1.dp, if (selected) p.accent else p.outline, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) p.onAccent else if (enabled) p.text else p.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------------------------------------------------------------------------
// Creazione / modifica di un fondo
// ---------------------------------------------------------------------------

@Composable
private fun FundForm(vm: MainViewModel, editId: Long, available: Long, currency: String, onClose: () -> Unit) {
    val l = LocalL.current
    val p = LocalPalette.current
    val today = LocalDate.now()
    val existing = vm.funds.firstOrNull { it.id == editId }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var iconIdx by rememberSaveable { mutableStateOf(existing?.icon ?: 0) }
    var limited by rememberSaveable { mutableStateOf(existing?.limited ?: true) }
    // In modifica l'importo mostrato e' il SALDO attuale del fondo (quota iniziale + movimenti).
    val startBalance = if (existing != null) fundBalanceCents(existing, vm.txs, LocalDate.now()).coerceIn(0L, available) else 0L
    var cents by rememberSaveable { mutableStateOf(startBalance) }
    var amountText by rememberSaveable { mutableStateOf(if (existing != null) plainAmount(startBalance) else "") }
    var tried by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val fraction = if (available > 0L) (cents.toDouble() / available.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (existing == null) l("New fund", "Nuovo fondo") else l("Edit fund", "Modifica fondo"),
            color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold
        )

        AppField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = l("Fund name", "Nome fondo"),
            error = if (tried && name.isBlank()) l("Enter a name", "Inserisci un nome") else null
        )

        SectionLabel(l("Icon", "Icona"))
        IconPicker(iconIdx) { iconIdx = it }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { limited = !limited },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = limited,
                onCheckedChange = { limited = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = p.accent,
                    uncheckedColor = p.muted,
                    checkmarkColor = p.onAccent
                )
            )
            Column {
                Text(l("Limited fund", "Fondo limitato"), color = p.text, fontSize = 16.sp)
                Text(
                    if (limited) l("It cannot go below zero", "Non può scendere sotto zero")
                    else l("It can go negative", "Può andare in negativo"),
                    color = p.muted, fontSize = 13.sp
                )
            }
        }

        SectionLabel(l("Money in this fund", "Denaro nel fondo"))
        Text(
            l("Available to allocate: ", "Disponibile da assegnare: ") + money(available, currency),
            color = p.muted, fontSize = 13.sp
        )
        Slider(
            value = fraction,
            onValueChange = { f ->
                var c = Math.round(f.toDouble() * available.toDouble())
                if (c > available) c = available
                if (c < 0L) c = 0L
                cents = c
                amountText = plainAmount(c)
            },
            enabled = available > 0L,
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.track
            )
        )
        AppField(
            value = amountText,
            onValueChange = { v ->
                val s = sanitizeAmount(v)
                val c = parseCents(s) ?: 0L
                if (c > available) {
                    cents = available
                    amountText = plainAmount(available)
                } else {
                    cents = c
                    amountText = s
                }
            },
            label = l("Amount", "Importo"),
            keyboardType = KeyboardType.Decimal,
            suffix = currencySymbol(currency),
            imeAction = ImeAction.Done
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            for (pc in listOf(10, 25, 50, 75, 100)) {
                val target = Math.round(available.toDouble() * pc / 100.0)
                PctChip(
                    text = "$pc%",
                    selected = available > 0L && cents == target,
                    enabled = available > 0L,
                    onClick = {
                        cents = target
                        amountText = plainAmount(target)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val err = error
        if (err != null) {
            Text(err, color = p.negative, fontSize = 14.sp)
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(if (existing == null) l("Create fund", "Crea fondo") else l("Save changes", "Salva modifiche")) {
            tried = true
            if (name.isNotBlank()) {
                if (existing == null) {
                    vm.addFund(name.trim(), iconIdx, limited, cents)
                    onClose()
                } else {
                    val profile = vm.profile
                    // nuova quota iniziale = saldo voluto - effetto dei movimenti gia' assegnati al fondo
                    val moves = fundBalanceCents(existing, vm.txs, today) - existing.cents
                    if (profile != null) {
                        vm.updateFund(existing.copy(name = name.trim(), icon = iconIdx, limited = limited, cents = cents - moves))
                        onClose()
                    }
                }
            }
        }
        LinkButton(l("Cancel", "Annulla"), onClose)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Pagina "Fondo cassa"
// ---------------------------------------------------------------------------

@Composable
fun FundsScreen(vm: MainViewModel) {
    val profile = vm.profile ?: return
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf(-1L) }
    val today = LocalDate.now()
    val unallocated = unallocatedCents(profile, vm.funds, vm.txs, today)

    BackHandler(enabled = formOpen) { formOpen = false }

    if (formOpen) {
        val existing = vm.funds.firstOrNull { it.id == editId }
        val available = maxOf(0L, unallocated + (if (existing != null) fundBalanceCents(existing, vm.txs, LocalDate.now()) else 0L))
        FundForm(vm, editId, available, profile.currency) { formOpen = false }
    } else {
        FundList(
            vm = vm,
            profile = profile,
            unallocated = unallocated,
            onNew = {
                editId = -1L
                formOpen = true
            },
            onEdit = { f ->
                editId = f.id
                formOpen = true
            }
        )
    }
}

@Composable
private fun FundList(vm: MainViewModel, profile: Profile, unallocated: Long, onNew: () -> Unit, onEdit: (Fund) -> Unit) {
    val l = LocalL.current
    val p = LocalPalette.current
    val cur = profile.currency
    val today = LocalDate.now()
    var pendingDelete by remember { mutableStateOf<Fund?>(null) }
    val total = balanceCents(profile, vm.txs, today)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(l("Cash funds", "Fondo cassa"), color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(l("Not allocated", "Non assegnato"), color = p.muted, fontSize = 13.sp)
                    Text(
                        money(unallocated, cur),
                        color = if (unallocated < 0L) p.negative else p.text,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(l("Total", "Totale"), color = p.muted, fontSize = 13.sp)
                    Text(
                        money(total, cur),
                        color = if (total < 0L) p.negative else p.text,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                l(
                    "Funds split the money you already have. Nothing new is created.",
                    "I fondi dividono il denaro che hai già. Non viene generato altro denaro."
                ),
                color = p.muted, fontSize = 13.sp
            )
        }

        PrimaryButton(l("New fund", "Nuovo fondo"), onClick = onNew)

        if (vm.funds.isEmpty()) {
            Text(
                l("No funds yet. Create one to place part of your money in it.", "Nessun fondo. Creane uno per assegnargli una parte del tuo denaro."),
                color = p.muted, fontSize = 14.sp
            )
        }

        for (f in vm.funds) {
            val balance = fundBalanceCents(f, vm.txs, today)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(p.surface)
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(p.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(fundIcon(f.icon), p.accent, iconSize = 22.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(f.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        if (f.limited) l("Limited", "Limitato") else l("Can go negative", "Può andare in negativo"),
                        color = p.muted, fontSize = 13.sp, maxLines = 1
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    money(balance, cur),
                    color = if (balance < 0L) p.negative else p.text,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onEdit(f) },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(Ic.PENCIL, p.muted, iconSize = 20.dp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { pendingDelete = f },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(Ic.CLOSE, p.negative, iconSize = 20.dp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDialog(
            title = l("Delete this fund?", "Eliminare questo fondo?"),
            message = toDelete.name + "\n\n" + l(
                "Its money goes back to \"Not allocated\". Linked transactions stay, without a fund.",
                "Il suo denaro torna in \"Non assegnato\". I movimenti collegati restano, senza fondo."
            ),
            confirm = l("Delete", "Elimina"),
            danger = true,
            onConfirm = {
                vm.deleteFund(toDelete.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
