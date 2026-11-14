package com.mybalance.app

import java.time.LocalDate

// Formato CSV di MyBalance (UTF-8, una riga per voce):
//   MyBalance,1
//   PROFILE,<obiettivo>,<valuta>,<soldi posseduti>,<somma/soglia>
//   TX,<id>,<INCOME|EXPENSE>,<nome>,<importo>,<periodo>,<tag>,<data inizio>,<ogni>,<unita'>,<giorni>,<fine>,<data fine>,<volte>

private fun esc(s: String): String {
    val t = s.replace("\r", " ").replace("\n", " ")
    return if (t.contains(',') || t.contains('"')) "\"" + t.replace("\"", "\"\"") + "\"" else t
}

private fun day(epoch: Long): String = LocalDate.ofEpochDay(epoch).toString()

fun buildCsv(profile: Profile, txs: List<Tx>, funds: List<Fund> = emptyList()): String {
    val sb = StringBuilder("﻿")
    sb.append("MyBalance,1\n")
    sb.append("section,goal,currency,money_owned,target_or_threshold\n")
    sb.append("PROFILE,").append(profile.goal.name).append(',').append(profile.currency).append(',')
        .append(plainAmount(profile.initialCents)).append(',').append(plainAmount(profile.targetCents)).append('\n')
    if (funds.isNotEmpty()) {
        sb.append("section,id,name,icon,limited,amount\n")
        for (f in funds) {
            sb.append("FUND,").append(f.id).append(',').append(esc(f.name)).append(',').append(f.icon).append(',')
                .append(f.limited).append(',').append(plainAmount(f.cents)).append('\n')
        }
    }
    sb.append("section,id,kind,name,amount,period,tag,start_date,every,unit,weekdays_mask,end_type,end_date,end_count,skipped,fund_id,splits\n")
    for (t in txs) {
        sb.append("TX,").append(t.id).append(',').append(t.type.name).append(',').append(esc(t.name)).append(',')
            .append(plainAmount(t.cents)).append(',').append(t.period.name).append(',').append(t.tag.name).append(',')
            .append(day(t.startDay)).append(',')
        val r = t.rule
        if (r != null) {
            sb.append(r.every).append(',').append(r.unit.name).append(',').append(r.weekdays).append(',')
                .append(r.endType.name).append(',').append(if (r.endDay > 0L) day(r.endDay) else "").append(',')
                .append(r.endCount)
        } else {
            sb.append(",,,,,")
        }
        sb.append(',').append(t.skipped.joinToString(";") { day(it) })
        sb.append(',').append(t.fundId?.toString() ?: "")
        sb.append(',').append(t.splits.joinToString(";") { it.fundId.toString() + ":" + plainAmount(it.cents) })
        sb.append('\n')
    }
    return sb.toString()
}

private fun parseLine(line: String, delim: Char): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (quoted) {
            if (c == '"') {
                if (i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    quoted = false
                }
            } else {
                sb.append(c)
            }
        } else if (c == '"') {
            quoted = true
        } else if (c == delim) {
            out.add(sb.toString())
            sb.setLength(0)
        } else {
            sb.append(c)
        }
        i++
    }
    out.add(sb.toString())
    return out
}

/** Legge un CSV di MyBalance. Restituisce null se il file non e' valido (nulla viene importato a meta'). */
fun parseCsv(text: String): Triple<Profile, List<Tx>, List<Fund>>? {
    return try {
        val lines = text.removePrefix("﻿").split("\n").map { it.trim('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty() || !lines[0].startsWith("MyBalance")) return null
        val delim = if (lines[0].startsWith("MyBalance;")) ';' else ','
        var profile: Profile? = null
        val txs = ArrayList<Tx>()
        val funds = ArrayList<Fund>()
        val base = System.currentTimeMillis()
        for (line in lines.drop(1)) {
            val f = parseLine(line, delim)
            when (f[0].trim()) {
                "PROFILE" -> {
                    val currency = f[2].trim().uppercase()
                    if (currency !in CURRENCIES) return null
                    profile = Profile(
                        goal = GoalType.valueOf(f[1].trim()),
                        initialCents = parseCents(f[3]) ?: return null,
                        currency = currency,
                        targetCents = parseCents(f[4]) ?: 0L
                    )
                }
                "FUND" -> {
                    val fid = f[1].trim().toLong()
                    if (funds.any { it.id == fid } || f[2].isBlank()) return null
                    funds.add(
                        Fund(
                            id = fid,
                            name = f[2].trim(),
                            icon = f[3].trim().toInt().coerceIn(0, FUND_ICON_COUNT - 1),
                            limited = f[4].trim().equals("true", ignoreCase = true),
                            cents = parseCents(f[5]) ?: return null
                        )
                    )
                }
                "TX" -> {
                    val period = Period.valueOf(f[5].trim())
                    val cents = parseCents(f[4]) ?: return null
                    if (cents <= 0L || f[3].isBlank()) return null
                    val rule: Recurrence? = if (period == Period.CUSTOM) {
                        Recurrence(
                            every = f[8].trim().toInt().coerceAtLeast(1),
                            unit = RepeatUnit.valueOf(f[9].trim()),
                            weekdays = f[10].trim().toInt(),
                            endType = EndType.valueOf(f[11].trim()),
                            endDay = if (f[12].isBlank()) 0L else LocalDate.parse(f[12].trim()).toEpochDay(),
                            endCount = f[13].trim().toInt().coerceAtLeast(1)
                        )
                    } else null
                    txs.add(
                        Tx(
                            id = base + txs.size,
                            type = TxType.valueOf(f[2].trim()),
                            name = f[3].trim(),
                            cents = cents,
                            period = period,
                            tag = Tag.valueOf(f[6].trim()),
                            startDay = LocalDate.parse(f[7].trim()).toEpochDay(),
                            rule = rule,
                            skipped = f.getOrNull(14)?.trim()?.takeIf { it.isNotEmpty() }?.split(';')?.map { LocalDate.parse(it.trim()).toEpochDay() } ?: emptyList(),
                            fundId = f.getOrNull(15)?.trim()?.toLongOrNull(),
                            splits = f.getOrNull(16)?.trim()?.takeIf { it.isNotEmpty() }?.split(';')?.map {
                                val kv = it.split(':')
                                FundShare(kv[0].trim().toLong(), parseCents(kv[1]) ?: return null)
                            } ?: emptyList()
                        )
                    )
                }
            }
        }
        val p = profile ?: return null
        val ids = funds.map { it.id }.toSet()
        val fixed = txs.map { if (it.fundId != null && it.fundId !in ids) it.copy(fundId = null) else it }.map { t -> t.copy(splits = t.splits.filter { s -> s.fundId in ids }) }
        Triple(p, fixed, funds.toList())
    } catch (e: Exception) {
        null
    }
}

/** Punto di accesso usato da Backup.kt. */
object Csv {
    fun export(profile: Profile, txs: List<Tx>, funds: List<Fund> = emptyList()): String = buildCsv(profile, txs, funds)
    fun parse(text: String): Triple<Profile, List<Tx>, List<Fund>>? = parseCsv(text)
}
