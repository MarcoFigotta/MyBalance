package com.mybalance.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

/**
 * Grafico circolare aperto in basso. Si "carica" con un'animazione ogni volta che la pagina appare.
 * `fraction` va da 0 (minimo) a 1 (massimo).
 */
@Composable
fun BalanceGauge(
    fraction: Float,
    minLabel: String,
    maxLabel: String,
    caption: String,
    centerText: String,
    centerSize: TextUnit,
    subText: String,
    alert: Boolean,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "gauge"
    )
    val arcColor: Color = if (alert) p.negative else p.accent

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val side: Dp = if (maxWidth < 330.dp) maxWidth else 330.dp
        val strokeDp = 20.dp
        val radiusDp = side / 2 - strokeDp / 2 - 8.dp
        val endOffset = radiusDp * 0.7071f

        Box(modifier = Modifier.width(side).height(side * 0.93f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeDp.toPx()
                val cx = size.width / 2f
                val cy = size.width / 2f
                val r = size.width / 2f - stroke / 2f - 8.dp.toPx()
                val topLeft = Offset(cx - r, cy - r)
                val arcSize = Size(2f * r, 2f * r)

                // traccia
                drawArc(
                    color = p.track, startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // avanzamento
                if (progress > 0.002f) {
                    drawArc(
                        color = arcColor, startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE * progress,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                // tacche
                val ticks = 54
                val outer = r - stroke / 2f - 10.dp.toPx()
                for (i in 0..ticks) {
                    val major = i % 6 == 0
                    val t = i.toFloat() / ticks.toFloat()
                    val a = (START_ANGLE + SWEEP_ANGLE * t) * (Math.PI.toFloat() / 180f)
                    val inner = outer - (if (major) 12.dp.toPx() else 6.dp.toPx())
                    val reached = t <= progress && progress > 0.002f
                    val tickColor = if (reached) arcColor else p.muted.copy(alpha = if (major) 0.55f else 0.30f)
                    drawLine(
                        color = tickColor,
                        start = Offset(cx + outer * cos(a), cy + outer * sin(a)),
                        end = Offset(cx + inner * cos(a), cy + inner * sin(a)),
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                // pallino sulla punta dell'avanzamento
                if (progress > 0.002f) {
                    val a = (START_ANGLE + SWEEP_ANGLE * progress) * (Math.PI.toFloat() / 180f)
                    drawCircle(
                        color = p.bg, radius = 4.dp.toPx(),
                        center = Offset(cx + r * cos(a), cy + r * sin(a))
                    )
                }
            }

            // testo al centro
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(caption, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        centerText,
                        color = if (alert) p.negative else p.text,
                        fontSize = centerSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    Text(subText, color = p.muted, fontSize = 13.sp)
                }
            }

            // minimo e massimo sotto le due estremita' dell'arco
            Box(
                modifier = Modifier
                    .offset(x = side / 2 - endOffset - 40.dp, y = side / 2 + endOffset + 14.dp)
                    .width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(minLabel, color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
            Box(
                modifier = Modifier
                    .offset(x = side / 2 + endOffset - 40.dp, y = side / 2 + endOffset + 14.dp)
                    .width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(maxLabel, color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
