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
enum class Period { DAILY, MONTHLY, WEEKLY, ONCE }
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

data class Tx(
    val id: Long,
    val type: TxType,
    val name: String,
    val cents: Long,
    val period: Period,
    val tag: Tag,
    val startDay: Long
)

// ---------------------------------------------------------------------------
// Calcolo del bilancio
// ---------------------------------------------------------------------------

/** Quante volte il movimento si e' ripetuto dal giorno di inizio fino a `day` (incluso). */
fun occurrencesUntil(tx: Tx, day: LocalDate): Long {
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
    }
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
