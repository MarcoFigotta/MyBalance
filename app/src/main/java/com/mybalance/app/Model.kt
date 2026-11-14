package com.mybalance.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class GoalType { REACH, FLOOR, TRACK }
enum class TxType { INCOME, EXPENSE }
enum class Period { DAILY, MONTHLY, WEEKLY, ONCE, CUSTOM }
enum class RepeatUnit { WEEK, MONTH, YEAR }
enum class EndType { NEVER, DATE, COUNT }
enum class Tag { SCHOOL, WORK, HEALTH, FOOD, LEISURE, OTHER }
enum class ThemeMode { LIGHT, DARK }
enum class LangMode { SYSTEM, IT, EN }

val CURRENCIES = listOf("EUR", "USD")

data class Account(val username: String, val email: String, val salt: String, val hash: String)

data class Profile(
    val goal: GoalType,
    val initialCents: Long,
    val currency: String,
    val targetCents: Long
)

/**
 * Regola di ripetizione per il periodo "Personalizzata".
 * - every/unit: ogni quante settimane, mesi o anni
 * - weekdays: giorni della settimana (bit 0 = lunedi' ... bit 6 = domenica), usato solo con unit = WEEK
 * - endType: mai / in un giorno preciso (endDay) / dopo un certo numero di volte (endCount)
 */
data class Recurrence(
    val every: Int,
    val unit: RepeatUnit,
    val weekdays: Int,
    val endType: EndType,
    val endDay: Long,
    val endCount: Int
)

data class Tx(
    val id: Long,
    val type: TxType,
    val name: String,
    val cents: Long,
    val period: Period,
    val tag: Tag,
    val startDay: Long,
    val rule: Recurrence? = null,
    val skipped: List<Long> = emptyList(),
    val fundId: Long? = null,
    val splits: List<FundShare> = emptyList()
)

const val FUND_ICON_COUNT = 8

/** Quota di un movimento (per ogni ripetizione) presa da / messa in un fondo. */
data class FundShare(val fundId: Long, val cents: Long)

/** Fondo: una "tasca" in cui e' allocata una parte del denaro gia' posseduto. */
data class Fund(
    val id: Long,
    val name: String,
    val icon: Int,
    val limited: Boolean,
    val cents: Long
)

// ---------------------------------------------------------------------------
// Calcolo del bilancio
// ---------------------------------------------------------------------------

/** Ripetizioni "grezze" (anche quelle annullate) dal giorno di inizio fino a `day` incluso. */
fun rawOccurrences(tx: Tx, day: LocalDate): Long {
    val end = day.toEpochDay()
    if (end < tx.startDay) return 0L
    val gap = end - tx.startDay
    return when (tx.period) {
        Period.ONCE -> 1L
        Period.DAILY -> gap + 1
        Period.WEEKLY -> gap / 7 + 1
        Period.MONTHLY -> {
            val start = LocalDate.ofEpochDay(tx.startDay)
            var n = ChronoUnit.MONTHS.between(start, day)
            while (!start.plusMonths(n + 1).isAfter(day)) n++
            n + 1
        }
        Period.CUSTOM -> customOccurrences(tx, day)
    }
}

/** Ripetizioni di un movimento "Personalizzata" dal giorno di inizio fino a `day` (incluso). */
private fun customOccurrences(tx: Tx, day: LocalDate): Long {
    val r = tx.rule
    if (r == null) return if (day.toEpochDay() >= tx.startDay) 1L else 0L
    val start = LocalDate.ofEpochDay(tx.startDay)
    var last = day
    if (r.endType == EndType.DATE) {
        val limit = LocalDate.ofEpochDay(r.endDay)
        if (limit.isBefore(last)) last = limit
    }
    if (last.isBefore(start)) return 0L
    val every = maxOf(1, r.every)
    var count = 0L
    when (r.unit) {
        RepeatUnit.WEEK -> {
            val mask = if (r.weekdays == 0) (1 shl (start.dayOfWeek.value - 1)) else r.weekdays
            var week = start.minusDays((start.dayOfWeek.value - 1).toLong())
            while (!week.isAfter(last)) {
                for (d in 0..6) {
                    if ((mask and (1 shl d)) != 0) {
                        val date = week.plusDays(d.toLong())
                        if (!date.isBefore(start) && !date.isAfter(last)) count++
                    }
                }
                week = week.plusWeeks(every.toLong())
            }
        }
        RepeatUnit.MONTH -> count = stepCount(start, last, every, ChronoUnit.MONTHS)
        RepeatUnit.YEAR -> count = stepCount(start, last, every, ChronoUnit.YEARS)
    }
    if (r.endType == EndType.COUNT) count = minOf(count, r.endCount.toLong())
    return count
}

/** Quanti multipli di `every` mesi/anni a partire da `start` cadono entro `last` (start incluso). */
private fun stepCount(start: LocalDate, last: LocalDate, every: Int, unit: ChronoUnit): Long {
    var k = unit.between(start, last) / every
    while (!start.plus(every * (k + 1), unit).isAfter(last)) k++
    while (k >= 0 && start.plus(every * k, unit).isAfter(last)) k--
    return k + 1
}

/** True se una ripetizione personalizzata ha gia' raggiunto la sua fine. */
fun isFinished(tx: Tx, today: LocalDate): Boolean {
    val r = tx.rule
    if (tx.period != Period.CUSTOM || r == null) return false
    return when (r.endType) {
        EndType.NEVER -> false
        EndType.DATE -> r.endDay < today.toEpochDay()
        EndType.COUNT -> rawOccurrences(tx, today) >= r.endCount.toLong()
    }
}

/** Ripetizioni effettive: quelle grezze meno le singole occorrenze annullate. */
fun occurrencesUntil(tx: Tx, day: LocalDate): Long {
    val raw = rawOccurrences(tx, day)
    val end = day.toEpochDay()
    val cancelled = tx.skipped.count { it <= end }
    return maxOf(0L, raw - cancelled)
}

/** Giorni in cui il movimento cade davvero (annullati inclusi) tra `from` e `to`. */
fun occurrenceDates(tx: Tx, from: LocalDate, to: LocalDate): List<LocalDate> {
    if (tx.period == Period.ONCE) return emptyList()
    val out = ArrayList<LocalDate>()
    var d = from
    while (!d.isAfter(to)) {
        if (rawOccurrences(tx, d) - rawOccurrences(tx, d.minusDays(1)) == 1L) out.add(d)
        d = d.plusDays(1)
    }
    return out
}

fun txTotal(tx: Tx, day: LocalDate): Long {
    val sign = if (tx.type == TxType.INCOME) 1L else -1L
    return occurrencesUntil(tx, day) * tx.cents * sign
}

fun balanceCents(profile: Profile, txs: List<Tx>, today: LocalDate): Long {
    var total = profile.initialCents
    for (t in txs) total += txTotal(t, today)
    return total
}

/** Saldo di un fondo: quota iniziale + movimenti assegnati al fondo. */
fun fundBalanceCents(fund: Fund, txs: List<Tx>, today: LocalDate): Long {
    var total = fund.cents
    for (t in txs) total += fundTxTotal(t, fund.id, today)
    return total
}

/** Quota (per ripetizione) di un movimento attribuita a un fondo. */
fun txShareCents(tx: Tx, fundId: Long): Long {
    if (tx.splits.isNotEmpty()) return tx.splits.filter { it.fundId == fundId }.sumOf { it.cents }
    return if (tx.fundId == fundId) tx.cents else 0L
}

/** Effetto totale (fino a oggi) di un movimento su un fondo. */
fun fundTxTotal(tx: Tx, fundId: Long, day: LocalDate): Long {
    val share = txShareCents(tx, fundId)
    if (share == 0L) return 0L
    val sign = if (tx.type == TxType.INCOME) 1L else -1L
    return occurrencesUntil(tx, day) * share * sign
}

/** Denaro non assegnato a nessun fondo. */
fun unallocatedCents(profile: Profile, funds: List<Fund>, txs: List<Tx>, today: LocalDate): Long {
    var total = profile.initialCents
    for (f in funds) total -= f.cents
    for (t in txs) {
        var attributed = 0L
        for (f in funds) attributed += txShareCents(t, f.id)
        val rest = t.cents - attributed
        if (rest != 0L) {
            val sign = if (t.type == TxType.INCOME) 1L else -1L
            total += occurrencesUntil(t, today) * rest * sign
        }
    }
    return total
}

/** Entrate e uscite del mese corrente (coppia: entrate, uscite). */
fun monthTotals(txs: List<Tx>, today: LocalDate): Pair<Long, Long> {
    val before = today.withDayOfMonth(1).minusDays(1)
    var income = 0L
    var expense = 0L
    for (t in txs) {
        val n = occurrencesUntil(t, today) - occurrencesUntil(t, before)
        if (t.type == TxType.INCOME) income += n * t.cents else expense += n * t.cents
    }
    return Pair(income, expense)
}

// ---------------------------------------------------------------------------
// Grafico circolare
// ---------------------------------------------------------------------------

data class GaugeInfo(
    val fraction: Float,
    val minCents: Long,
    val maxCents: Long,
    val below: Boolean
)

private fun fractionOf(value: Long, total: Long): Float {
    if (total <= 0L) return 0f
    return (value.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

/** Massimo "tondo" per la modalita' solo monitoraggio. */
fun niceMax(balance: Long): Long {
    val major = maxOf(balance, 10_000L) * 1.25 / 100.0
    val p = 10.0.pow(floor(log10(major)))
    for (m in doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)) {
        val c = m * p
        if (c >= major) return Math.round(c * 100.0)
    }
    return Math.round(10.0 * p * 100.0)
}

fun gaugeFor(profile: Profile, balance: Long): GaugeInfo {
    return when (profile.goal) {
        GoalType.REACH -> {
            val max = if (profile.targetCents > 0L) profile.targetCents else 1L
            GaugeInfo(fractionOf(balance, max), 0L, max, false)
        }
        GoalType.FLOOR -> {
            val min = profile.targetCents
            val max = min * 2L + 2000L // circa il doppio della soglia + 20 (EUR/USD)
            GaugeInfo(fractionOf(balance - min, max - min), min, max, balance < min)
        }
        GoalType.TRACK -> {
            val max = niceMax(balance)
            GaugeInfo(fractionOf(balance, max), 0L, max, balance < 0L)
        }
    }
}

// ---------------------------------------------------------------------------
// Importi
// ---------------------------------------------------------------------------

fun currencySymbol(code: String): String = if (code == "USD") "\$" else "€"

fun formatMoney(cents: Long, currency: String, locale: Locale, alwaysDecimals: Boolean = true): String {
    val nf = NumberFormat.getCurrencyInstance(locale)
    nf.currency = Currency.getInstance(currency)
    val whole = cents % 100L == 0L
    nf.minimumFractionDigits = if (whole && !alwaysDecimals) 0 else 2
    nf.maximumFractionDigits = 2
    if (nf is DecimalFormat) {
        val symbols = nf.decimalFormatSymbols
        symbols.currencySymbol = currencySymbol(currency)
        nf.decimalFormatSymbols = symbols
    }
    return nf.format(BigDecimal.valueOf(cents, 2))
}

/** Importo in centesimi -> testo modificabile (es. 1250 -> "12.5"). */
fun plainAmount(cents: Long): String {
    val whole = cents / 100L
    val frac = cents % 100L
    return if (frac == 0L) whole.toString() else whole.toString() + "." + frac.toString().padStart(2, '0')
}

/** Lascia solo cifre e un separatore decimale (max 2 decimali). */
fun sanitizeAmount(raw: String): String {
    val sb = StringBuilder()
    var hasSeparator = false
    var decimals = 0
    for (ch in raw) {
        if (ch in '0'..'9') {
            if (!hasSeparator || decimals < 2) {
                sb.append(ch)
                if (hasSeparator) decimals++
            }
        } else if ((ch == '.' || ch == ',') && !hasSeparator) {
            if (sb.isEmpty()) sb.append('0')
            sb.append(ch)
            hasSeparator = true
        }
        if (sb.length >= 13) break
    }
    return sb.toString()
}

fun parseCents(input: String): Long? {
    val t = input.trim().replace(',', '.')
    if (t.isEmpty() || t == ".") return null
    return try {
        BigDecimal(t).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    } catch (e: NumberFormatException) {
        null
    }
}

// ---------------------------------------------------------------------------
// Cronologia (logica pura, usata dalla schermata)
// ---------------------------------------------------------------------------

data class HistItem(
    val key: String,
    val tx: Tx,
    val date: Long,
    val occurrence: Boolean,
    val cancelled: Boolean
)

fun buildHistory(txs: List<Tx>, today: LocalDate): List<HistItem> {
    val from = today.minusDays(29)
    val out = ArrayList<HistItem>()
    for (t in txs) {
        if (t.period == Period.ONCE) {
            out.add(HistItem("s" + t.id, t, t.startDay, false, false))
        } else {
            for (d in occurrenceDates(t, from, today)) {
                val e = d.toEpochDay()
                out.add(HistItem("o" + t.id + "_" + e, t, e, true, t.skipped.contains(e)))
            }
        }
    }
    return out.sortedWith(compareByDescending<HistItem> { it.date }.thenByDescending { it.tx.id })
}
