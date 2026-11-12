package com.mybalance.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ---------------------------------------------------------------------------
// Testi e formati
// ---------------------------------------------------------------------------

fun RepeatUnit.label(l: L, plural: Boolean): String = when (this) {
    RepeatUnit.WEEK -> if (plural) l("weeks", "settimane") else l("week", "settimana")
    RepeatUnit.MONTH -> if (plural) l("months", "mesi") else l("month", "mese")
    RepeatUnit.YEAR -> if (plural) l("years", "anni") else l("year", "anno")
}

/** Iniziale del giorno della settimana: 0 = lunedi' ... 6 = domenica. */
fun dayInitial(i: Int, l: L): String {
    val itDays = arrayOf("L", "M", "M", "G", "V", "S", "D")
    val enDays = arrayOf("M", "T", "W", "T", "F", "S", "S")
    return if (l.italian) itDays[i] else enDays[i]
}

fun dayShort(i: Int, l: L): String {
    val itDays = arrayOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    val enDays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return if (l.italian) itDays[i] else enDays[i]
}

fun formatDay(epochDay: Long, locale: Locale): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

/** Descrizione breve della ripetizione, ad esempio "Ogni 2 settimane - Lun Ven - fino al ...". */
fun scheduleSummary(tx: Tx, l: L, locale: Locale): String {
    val r = tx.rule
    if (tx.period != Period.CUSTOM || r == null) return tx.period.label(l)
    val sb = StringBuilder()
    sb.append(l("Every ", "Ogni "))
    if (r.every == 1) {
        sb.append(r.unit.label(l, false))
    } else {
        sb.append(r.every).append(" ").append(r.unit.label(l, true))
    }
    if (r.unit == RepeatUnit.WEEK) {
        val days = ArrayList<String>()
        for (i in 0..6) {
            if ((r.weekdays and (1 shl i)) != 0) days.add(dayShort(i, l))
        }
        if (days.isNotEmpty()) sb.append(" · ").append(days.joinToString(" "))
    }
    when (r.endType) {
        EndType.NEVER -> {}
        EndType.DATE -> sb.append(" · ").append(l("until ", "fino al ")).append(formatDay(r.endDay, locale))
        EndType.COUNT -> sb.append(" · ").append(r.endCount).append(l(" times", " volte"))
    }
    return sb.toString()
}

// ---------------------------------------------------------------------------
// Componenti del pannello "Personalizzata"
// ---------------------------------------------------------------------------

@Composable
private fun RadioRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.muted)
        )
        Spacer(Modifier.width(4.dp))
        content()
    }
}

@Composable
private fun UnitPicker(unit: RepeatUnit, plural: Boolean, onChange: (RepeatUnit) -> Unit) {
    val p = LocalPalette.current
    val l = LocalL.current
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Text(unit.label(l, plural), color = p.text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            AppIcon(Ic.CHEVRON_DOWN, p.muted, iconSize = 18.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (u in RepeatUnit.values()) {
                DropdownMenuItem(
                    text = { Text(u.label(l, plural)) },
                    onClick = {
                        onChange(u)
                        open = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndDatePicker(initialDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val l = LocalL.current
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.ofEpochDay(initialDay)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) {
                    onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                } else {
                    onDismiss()
                }
            }) {
                Text("OK", color = p.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(l("Cancel", "Annulla"), color = p.muted) }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun CustomPanel(
    unit: RepeatUnit,
    onUnit: (RepeatUnit) -> Unit,
    everyText: String,
    onEvery: (String) -> Unit,
    everyErr: String?,
    weekMask: Int,
    onMask: (Int) -> Unit,
    weekErr: String?,
    endType: EndType,
    onEndType: (EndType) -> Unit,
    endDay: Long,
    onPickDate: () -> Unit,
    dateErr: String?,
    countText: String,
    onCount: (String) -> Unit,
    countErr: String?
) {
    val l = LocalL.current
    val p = LocalPalette.current
    val locale = LocalLocale.current
    val plural = (everyText.toIntOrNull() ?: 1) != 1

    SurfaceCard {
        // --- giorni della settimana (solo se si ripete ogni N settimane) ---
        if (unit == RepeatUnit.WEEK) {
            SectionLabel(l("Repeats on", "Si ripete nei giorni"))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0..6) {
                    val on = (weekMask and (1 shl i)) != 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (on) p.accent else p.bg)
                            .border(1.dp, if (on) p.accent else p.outline, CircleShape)
                            .clickable { onMask(weekMask xor (1 shl i)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            dayInitial(i, l),
                            color = if (on) p.onAccent else p.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (weekErr != null) {
                Text(weekErr, color = p.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
        }

        // --- ogni quanto si ripete ---
        SectionLabel(l("How often it repeats", "Ogni quanto si ripete"))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            AppField(
                value = everyText,
                onValueChange = { onEvery(it.filter { ch -> ch in '0'..'9' }.take(3)) },
                label = l("Every", "Ogni"),
                modifier = Modifier.width(110.dp),
                keyboardType = KeyboardType.Number
            )
            UnitPicker(unit, plural, onUnit)
        }
        if (everyErr != null) {
            Text(everyErr, color = p.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // --- quando finisce il ciclo ---
        Spacer(Modifier.height(14.dp))
        SectionLabel(l("Ends", "Fine del ciclo"))
        RadioRow(endType == EndType.NEVER, { onEndType(EndType.NEVER) }) {
            Text(l("Never", "Mai"), color = p.text, fontSize = 15.sp)
        }
        RadioRow(endType == EndType.DATE, { onEndType(EndType.DATE) }) {
            Text(
                l("On a date", "Giorno preciso"),
                color = p.text,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(10.dp))
            Chip(formatDay(endDay, locale), endType == EndType.DATE, onPickDate)
        }
        if (dateErr != null) {
            Text(dateErr, color = p.negative, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        }
        RadioRow(endType == EndType.COUNT, { onEndType(EndType.COUNT) }) {
            Text(l("After", "Dopo"), color = p.text, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
            AppField(
                value = countText,
                onValueChange = { onCount(it.filter { ch -> ch in '0'..'9' }.take(4)) },
                label = l("No.", "N."),
                modifier = Modifier.width(92.dp),
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.width(10.dp))
            Text(l("times", "volte"), color = p.text, fontSize = 15.sp)
        }
        if (countErr != null) {
            Text(countErr, color = p.negative, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Riga di una voce periodica, con matita (modifica) e X (elimina). */
@Composable
fun RecurringRow(
    tx: Tx,
    currency: String,
    today: LocalDate,
    editing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    note: String? = null
) {
    val l = LocalL.current
    val p = LocalPalette.current
    val locale = LocalLocale.current
    val income = tx.type == TxType.INCOME
    val tone = if (income) p.positive else p.negative
    val finished = isFinished(tx, today)
    val shape = RoundedCornerShape(18.dp)
    var detail = tx.tag.label(l) + " · " + scheduleSummary(tx, l, locale)
    if (finished) detail = detail + " · " + l("Ended", "Terminata")
    if (note != null) detail = detail + " · " + note

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .then(if (editing) Modifier.border(2.dp, p.accent, shape) else Modifier)
            .padding(start = 14.dp, top = 12.dp, bottom = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.Top
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
            Text(
                tx.name,
                color = if (finished) p.muted else p.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(detail, color = p.muted, fontSize = 13.sp, maxLines = 3, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (income) "+" else "-") + money(tx.cents, currency),
                color = tone,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp)
            )
            Row {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(Ic.PENCIL, p.muted, iconSize = 20.dp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(Ic.CLOSE, p.negative, iconSize = 20.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pagina di sinistra: Entrata / Uscita
// ---------------------------------------------------------------------------

@Composable
fun TransactionsScreen(vm: MainViewModel, pendingEdit: Tx?, onEditHandled: () -> Unit) {
    val l = LocalL.current
    val p = LocalPalette.current
    val profile = vm.profile ?: return
    val cur = profile.currency
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val today = LocalDate.now()
    val defaultMask = 1 shl (today.dayOfWeek.value - 1)
    val defaultEnd = today.plusMonths(1).toEpochDay()

    var editingId by rememberSaveable { mutableStateOf(-1L) }
    var typeIdx by rememberSaveable { mutableStateOf(0) } // 0 = entrata, 1 = uscita
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var periodIdx by rememberSaveable { mutableStateOf(Period.ONCE.ordinal) }
    var tagIdx by rememberSaveable { mutableStateOf(Tag.OTHER.ordinal) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var fundSel by rememberSaveable { mutableStateOf(-1L) }
    var fundErr by remember { mutableStateOf<String?>(null) }

    // opzioni di "Personalizzata"
    var everyText by rememberSaveable { mutableStateOf("1") }
    var unitIdx by rememberSaveable { mutableStateOf(RepeatUnit.WEEK.ordinal) }
    var weekMask by rememberSaveable { mutableStateOf(defaultMask) }
    var endIdx by rememberSaveable { mutableStateOf(EndType.NEVER.ordinal) }
    var endDay by rememberSaveable { mutableStateOf(defaultEnd) }
    var endCountText by rememberSaveable { mutableStateOf("10") }

    var showDate by remember { mutableStateOf(false) }
    var scrollTick by remember { mutableStateOf(0) }
    LaunchedEffect(scrollTick) {
        if (scrollTick > 0) listState.animateScrollToItem(0)
    }

    val isIncome = typeIdx == 0
    val period = Period.values()[periodIdx]
    val unit = RepeatUnit.values()[unitIdx]
    val endType = EndType.values()[endIdx]
    val cents = parseCents(amount)
    val everyN = everyText.toIntOrNull()
    val countN = endCountText.toIntOrNull()
    val isCustom = period == Period.CUSTOM

    val nameErr = if (tried && name.isBlank()) l("Enter a name", "Inserisci un nome") else null
    val amountErr = if (tried && (cents == null || cents <= 0L)) {
        l("Enter an amount greater than zero", "Inserisci un importo maggiore di zero")
    } else null
    val everyErr = if (tried && isCustom && (everyN == null || everyN < 1)) {
        l("Enter a number from 1", "Inserisci un numero da 1")
    } else null
    val weekErr = if (tried && isCustom && unit == RepeatUnit.WEEK && weekMask == 0) {
        l("Select at least one day", "Seleziona almeno un giorno")
    } else null
    val dateErr = if (tried && isCustom && endType == EndType.DATE && endDay < today.toEpochDay()) {
        l("Choose a date from today onwards", "Scegli una data da oggi in poi")
    } else null
    val countErr = if (tried && isCustom && endType == EndType.COUNT && (countN == null || countN < 1)) {
        l("Enter a number from 1", "Inserisci un numero da 1")
    } else null

    val customOk = !isCustom || (
        everyN != null && everyN >= 1 &&
            (unit != RepeatUnit.WEEK || weekMask != 0) &&
            (endType != EndType.DATE || endDay >= today.toEpochDay()) &&
            (endType != EndType.COUNT || (countN != null && countN >= 1))
        )

    fun resetCustomFields() {
        everyText = "1"
        unitIdx = RepeatUnit.WEEK.ordinal
        weekMask = defaultMask
        endIdx = EndType.NEVER.ordinal
        endDay = defaultEnd
        endCountText = "10"
    }

    fun resetForm() {
        editingId = -1L
        typeIdx = 0
        name = ""
        amount = ""
        periodIdx = Period.ONCE.ordinal
        tagIdx = Tag.OTHER.ordinal
        tried = false
        fundSel = -1L
        fundErr = null
        resetCustomFields()
    }

    fun startEdit(tx: Tx) {
        editingId = tx.id
        typeIdx = if (tx.type == TxType.INCOME) 0 else 1
        name = tx.name
        amount = plainAmount(tx.cents)
        periodIdx = tx.period.ordinal
        tagIdx = tx.tag.ordinal
        tried = false
        fundSel = tx.fundId ?: -1L
        fundErr = null
        val r = tx.rule
        if (r != null) {
            everyText = r.every.toString()
            unitIdx = r.unit.ordinal
            weekMask = if (r.weekdays == 0) defaultMask else r.weekdays
            endIdx = r.endType.ordinal
            endDay = if (r.endDay > 0L) r.endDay else defaultEnd
            endCountText = r.endCount.toString()
        } else {
            resetCustomFields()
        }
        scrollTick += 1
    }

    fun submit() {
        tried = true
        val c = cents
        if (name.isBlank() || c == null || c <= 0L || !customOk) return
        val rule: Recurrence? = if (isCustom) {
            Recurrence(
                every = everyN ?: 1,
                unit = unit,
                weekdays = if (unit == RepeatUnit.WEEK) weekMask else 0,
                endType = endType,
                endDay = endDay,
                endCount = countN ?: 1
            )
        } else null
        val type = if (isIncome) TxType.INCOME else TxType.EXPENSE
        val fid: Long? = if (fundSel >= 0L && vm.funds.any { it.id == fundSel }) fundSel else null
        val fund = vm.funds.firstOrNull { it.id == fid }
        if (fund != null && fund.limited && type == TxType.EXPENSE) {
            val others = vm.txs.filter { it.id != editingId }
            val startDay = vm.txs.firstOrNull { it.id == editingId }?.startDay ?: today.toEpochDay()
            val temp = Tx(0L, type, name.trim(), c, period, Tag.values()[tagIdx], startDay, rule, emptyList(), fid)
            val after = fundBalanceCents(fund, others, today) + txTotal(temp, today)
            if (after < 0L) {
                fundErr = l("Not enough money in this fund.", "Il fondo non ha abbastanza denaro.")
                return
            }
        }
        fundErr = null
        if (editingId >= 0L) {
            vm.updateTx(editingId, type, name.trim(), c, period, Tag.values()[tagIdx], rule, fid)
        } else {
            vm.addTx(type, name.trim(), c, period, Tag.values()[tagIdx], rule, fid)
        }
        resetForm()
        focus.clearFocus()
    }

    LaunchedEffect(pendingEdit) {
        val e = pendingEdit
        if (e != null) {
            startEdit(e)
            onEditHandled()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (editingId >= 0L) l("Edit transaction", "Modifica movimento")
                    else l("Income / Expense", "Entrata / Uscita"),
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

                if (isCustom) {
                    CustomPanel(
                        unit = unit,
                        onUnit = { unitIdx = it.ordinal },
                        everyText = everyText,
                        onEvery = { everyText = it },
                        everyErr = everyErr,
                        weekMask = weekMask,
                        onMask = { weekMask = it },
                        weekErr = weekErr,
                        endType = endType,
                        onEndType = { endIdx = it.ordinal },
                        endDay = endDay,
                        onPickDate = { showDate = true },
                        dateErr = dateErr,
                        countText = endCountText,
                        onCount = {
                            endCountText = it
                            endIdx = EndType.COUNT.ordinal
                        },
                        countErr = countErr
                    )
                }

                SectionLabel(l("Tag", "Tag"))
                ChipFlow {
                    for (tg in Tag.values()) {
                        Chip(tg.label(l), tagIdx == tg.ordinal, { tagIdx = tg.ordinal })
                    }
                }

                if (vm.funds.isNotEmpty()) {
                    SectionLabel(
                        if (isIncome) l("Add to fund", "Inserisci nel fondo")
                        else l("Take from fund", "Preleva dal fondo")
                    )
                    ChipFlow {
                        Chip(l("None", "Nessuno"), fundSel < 0L, { fundSel = -1L; fundErr = null })
                        for (f in vm.funds) {
                            Chip(f.name, fundSel == f.id, { fundSel = f.id; fundErr = null })
                        }
                    }
                    val fe = fundErr
                    if (fe != null) Text(fe, color = p.negative, fontSize = 13.sp)
                }

                Spacer(Modifier.height(4.dp))
                PrimaryButton(
                    if (editingId >= 0L) l("Save changes", "Salva modifiche")
                    else if (isIncome) l("Add income", "Aggiungi entrata")
                    else l("Add expense", "Aggiungi uscita")
                ) { submit() }
                if (editingId >= 0L) {
                    LinkButton(l("Cancel editing", "Annulla modifica")) {
                        resetForm()
                        focus.clearFocus()
                    }
                }

            }
        }
    }

    if (showDate) {
        EndDatePicker(
            initialDay = endDay,
            onPick = {
                endDay = it
                endIdx = EndType.DATE.ordinal
                showDate = false
            },
            onDismiss = { showDate = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Cronologia: movimenti singoli + ogni occorrenza delle ripetizioni (ultimi 30 giorni)
// ---------------------------------------------------------------------------

/** Una singola occorrenza di una ripetizione: si puo' annullare il giorno stesso, o ripristinare. */
@Composable
fun OccurrenceRow(
    tx: Tx,
    currency: String,
    date: Long,
    cancelled: Boolean,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    note: String? = null
) {
    val l = LocalL.current
    val p = LocalPalette.current
    val locale = LocalLocale.current
    val income = tx.type == TxType.INCOME
    val tone = if (income) p.positive else p.negative
    val shown = if (cancelled) p.muted else tone
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(shown.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(if (income) Ic.ARROW_DOWN else Ic.ARROW_UP, shown, iconSize = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.name,
                color = if (cancelled) p.muted else p.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null
            )
            Text(
                formatDay(date, locale) + " \u00B7 " + tx.tag.label(l) +
                    (if (cancelled) " \u00B7 " + l("Cancelled", "Annullata") else "") +
                    (if (note != null) " \u00B7 " + note else ""),
                color = p.muted, fontSize = 13.sp, maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (income) "+" else "-") + money(tx.cents, currency),
            color = shown,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null
        )
        Spacer(Modifier.width(6.dp))
        if (cancelled) {
            Chip(l("Restore", "Ripristina"), false, onRestore)
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(Ic.CLOSE, p.negative, iconSize = 20.dp)
            }
        }
    }
}
