package com.mybalance.app

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Sposta il confine tra il segmento k e k+1 a `newCum` (somma cumulativa): cambiano solo quei due fondi. */
fun moveBoundary(ids: List<Long>, shares: Map<Long, Long>, k: Int, newCum: Long): Map<Long, Long> {
    if (k < 0 || k >= ids.size - 1) return shares
    var before = 0L
    for (i in 0 until k) before += shares[ids[i]] ?: 0L
    val pair = (shares[ids[k]] ?: 0L) + (shares[ids[k + 1]] ?: 0L)
    val a = (newCum - before).coerceIn(0L, pair)
    val out = LinkedHashMap<Long, Long>(shares)
    out[ids[k]] = a
    out[ids[k + 1]] = pair - a
    return out
}

/** Imposta la quota del fondo in posizione i; la differenza passa solo al fondo adiacente. */
fun setShareAdjacent(ids: List<Long>, shares: Map<Long, Long>, i: Int, v: Long): Map<Long, Long> {
    if (ids.size < 2 || i < 0 || i >= ids.size) return shares
    val j = if (i < ids.size - 1) i + 1 else i - 1
    val pair = (shares[ids[i]] ?: 0L) + (shares[ids[j]] ?: 0L)
    val a = v.coerceIn(0L, pair)
    val out = LinkedHashMap<Long, Long>(shares)
    out[ids[i]] = a
    out[ids[j]] = pair - a
    return out
}

private val SPLIT_COLORS = listOf(
    Color(0xFF2DD4BF), Color(0xFFF59E0B), Color(0xFF60A5FA),
    Color(0xFFC084FC), Color(0xFFF472B6), Color(0xFFA3E635)
)

fun splitColor(i: Int): Color = SPLIT_COLORS[i % SPLIT_COLORS.size]

// ---------------------------------------------------------------------------
// Editor: UNA barra divisa in sezioni (maniglie trascinabili) + una casella per fondo
// ---------------------------------------------------------------------------

@Composable
private fun SplitBar(
    ids: List<Long>,
    shares: Map<Long, Long>,
    total: Long,
    onChange: (Map<Long, Long>) -> Unit
) {
    val p = LocalPalette.current
    val curShares = rememberUpdatedState(shares)
    val curIds = rememberUpdatedState(ids)
    val curTotal = rememberUpdatedState(total)
    val curChange = rememberUpdatedState(onChange)
    var widthPx by remember { mutableStateOf(1) }
    var active by remember { mutableStateOf(0) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .onSizeChanged { widthPx = maxOf(1, it.width) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val idl = curIds.value
                        val t = curTotal.value
                        var best = 0
                        var bestD = Float.MAX_VALUE
                        var cum = 0L
                        for (k in 0 until idl.size - 1) {
                            cum += curShares.value[idl[k]] ?: 0L
                            val x = if (t > 0L) cum.toFloat() / t.toFloat() * widthPx else 0f
                            val dist = Math.abs(x - pos.x)
                            if (dist < bestD) { bestD = dist; best = k }
                        }
                        active = best
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val t = curTotal.value
                        if (t > 0L) {
                            val c = Math.round(change.position.x.toDouble() / widthPx.toDouble() * t.toDouble())
                            curChange.value(moveBoundary(curIds.value, curShares.value, active, c))
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        drawRect(color = p.track, size = size)
        var cum = 0L
        ids.forEachIndexed { i, id ->
            val s = shares[id] ?: 0L
            val x0 = if (total > 0L) cum.toFloat() / total.toFloat() * w else 0f
            cum += s
            val x1 = if (total > 0L) cum.toFloat() / total.toFloat() * w else 0f
            if (x1 > x0) drawRect(color = splitColor(i), topLeft = Offset(x0, 0f), size = Size(x1 - x0, h))
        }
        cum = 0L
        for (k in 0 until ids.size - 1) {
            cum += shares[ids[k]] ?: 0L
            val x = if (total > 0L) cum.toFloat() / total.toFloat() * w else 0f
            val hw = 5.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x - hw / 2f, 6.dp.toPx()),
                size = Size(hw, h - 12.dp.toPx()),
                cornerRadius = CornerRadius(hw / 2f, hw / 2f)
            )
        }
    }
}

@Composable
fun SplitEditor(
    funds: List<Fund>,
    total: Long,
    shares: Map<Long, Long>,
    currency: String,
    onChange: (Map<Long, Long>) -> Unit
) {
    val l = LocalL.current
    val p = LocalPalette.current
    val texts = remember { mutableStateMapOf<Long, String>() }
    val ids = funds.map { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            l("Split of ", "Ripartizione di ") + money(total, currency) +
                l(" - drag the handles", " - trascina le maniglie"),
            color = p.muted, fontSize = 13.sp
        )
        SplitBar(ids, shares, total, onChange)
        funds.forEachIndexed { i, f ->
            val sv = shares[f.id] ?: 0L
            val typed = texts[f.id]
            val shown = if (typed != null && (parseCents(typed) ?: 0L) == sv) typed else plainAmount(sv)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(splitColor(i))
                )
                AppIcon(fundIcon(f.icon), p.accent, iconSize = 18.dp, modifier = Modifier.padding(start = 8.dp))
                Text(
                    f.name,
                    color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            AppField(
                value = shown,
                onValueChange = { raw ->
                    val clean = sanitizeAmount(raw)
                    texts[f.id] = clean
                    onChange(setShareAdjacent(ids, shares, i, parseCents(clean) ?: 0L))
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
