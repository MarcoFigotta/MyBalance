package com.mybalance.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Logica pura della ripartizione tra piu' fondi
// ---------------------------------------------------------------------------

/** Ripartisce `total` tra `ids`: in proporzione a `prev`, oppure in parti uguali se `prev` e' vuoto/nullo. */
fun distribute(ids: List<Long>, total: Long, prev: Map<Long, Long>): Map<Long, Long> {
    val out = LinkedHashMap<Long, Long>()
    if (ids.isEmpty()) return out
    val t = maxOf(0L, total)
    val sumPrev = ids.sumOf { maxOf(0L, prev[it] ?: 0L) }
    if (sumPrev <= 0L) {
        val base = t / ids.size
        val rem = (t - base * ids.size).toInt()
        ids.forEachIndexed { i, id -> out[id] = base + (if (i < rem) 1L else 0L) }
        return out
    }
    var used = 0L
    for (id in ids) {
        val share = Math.floor(t.toDouble() * maxOf(0L, prev[id] ?: 0L).toDouble() / sumPrev.toDouble()).toLong()
        out[id] = share
        used += share
    }
    val first = ids[0]
    out[first] = (out[first] ?: 0L) + (t - used)
    return out
}

/** Imposta la quota di `id` a `v` e ridistribuisce il resto tra gli altri fondi. */
fun setShare(ids: List<Long>, total: Long, cur: Map<Long, Long>, id: Long, v: Long): Map<Long, Long> {
    val t = maxOf(0L, total)
    val value = v.coerceIn(0L, t)
    val others = ids.filter { it != id }
    if (others.isEmpty()) return mapOf(id to t)
    val rest = distribute(others, t - value, cur)
    val out = LinkedHashMap<Long, Long>()
    for (i in ids) out[i] = if (i == id) value else (rest[i] ?: 0L)
    return out
}

// ---------------------------------------------------------------------------
// Editor: uno slider + una casella per ogni fondo scelto
// ---------------------------------------------------------------------------

@Composable
fun SplitEditor(
    funds: List<Fund>,
    total: Long,
    shares: Map<Long, Long>,
    currency: String,
    onShare: (Long, Long) -> Unit
) {
    val l = LocalL.current
    val p = LocalPalette.current
    val texts = remember { mutableStateMapOf<Long, String>() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            l("Split of ", "Ripartizione di ") + money(total, currency),
            color = p.muted, fontSize = 13.sp
        )
        for (f in funds) {
            val sv = shares[f.id] ?: 0L
            val typed = texts[f.id]
            val shown = if (typed != null && (parseCents(typed) ?: 0L) == sv) typed else plainAmount(sv)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(fundIcon(f.icon), p.accent, iconSize = 18.dp)
                Text(
                    f.name,
                    color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Slider(
                value = if (total > 0L) (sv.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f,
                onValueChange = { fr ->
                    val c = Math.round(fr.toDouble() * total.toDouble())
                    texts.remove(f.id)
                    onShare(f.id, c)
                },
                enabled = total > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = p.accent,
                    activeTrackColor = p.accent,
                    inactiveTrackColor = p.track
                )
            )
            AppField(
                value = shown,
                onValueChange = { raw ->
                    val clean = sanitizeAmount(raw)
                    texts[f.id] = clean
                    onShare(f.id, parseCents(clean) ?: 0L)
                },
                label = f.name,
                keyboardType = KeyboardType.Decimal,
                suffix = currencySymbol(currency),
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
