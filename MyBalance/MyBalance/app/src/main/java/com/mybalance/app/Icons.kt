package com.mybalance.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Ic {
    SWAP, GAUGE, USER, PENCIL, LOCK, THEME, GLOBE, LOGOUT, TRASH,
    ARROW_UP, ARROW_DOWN, CHEVRON_RIGHT, CHEVRON_DOWN, PLUS, BACK, CHECK
}

/** Icone minimal a tratto, disegnate su una griglia 24x24 (nessuna emoji, nessuna libreria). */
@Composable
fun AppIcon(ic: Ic, tint: Color, modifier: Modifier = Modifier, iconSize: Dp = 24.dp) {
    Canvas(modifier.size(iconSize)) {
        val k = this.size.minDimension / 24f
        val sw = 1.9f * k
        val style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun o(x: Float, y: Float) = Offset(x * k, y * k)

        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(color = tint, start = o(x1, y1), end = o(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        }

        fun poly(vararg v: Float, close: Boolean = false) {
            val path = Path()
            path.moveTo(v[0] * k, v[1] * k)
            var i = 2
            while (i + 1 < v.size) {
                path.lineTo(v[i] * k, v[i + 1] * k)
                i += 2
            }
            if (close) path.close()
            drawPath(path = path, color = tint, style = style)
        }

        when (ic) {
            Ic.ARROW_UP -> {
                seg(12f, 19f, 12f, 5f); seg(6f, 11f, 12f, 5f); seg(18f, 11f, 12f, 5f)
            }
            Ic.ARROW_DOWN -> {
                seg(12f, 5f, 12f, 19f); seg(6f, 13f, 12f, 19f); seg(18f, 13f, 12f, 19f)
            }
            Ic.SWAP -> {
                seg(8f, 19f, 8f, 5f); seg(4f, 9f, 8f, 5f); seg(12f, 9f, 8f, 5f)
                seg(16f, 5f, 16f, 19f); seg(12f, 15f, 16f, 19f); seg(20f, 15f, 16f, 19f)
            }
            Ic.GAUGE -> {
                drawArc(
                    color = tint, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = o(4f, 5f), size = Size(16f * k, 16f * k), style = style
                )
                seg(12f, 13f, 16f, 8.5f)
                drawCircle(color = tint, radius = 1.5f * k, center = o(12f, 13f))
            }
            Ic.USER -> {
                drawCircle(color = tint, radius = 4f * k, center = o(12f, 8f), style = style)
                drawArc(
                    color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = o(4f, 14f), size = Size(16f * k, 12f * k), style = style
                )
            }
            Ic.PENCIL -> {
                poly(15f, 5f, 19f, 9f, 9f, 19f, 4.5f, 19.5f, 5f, 15f, close = true)
                seg(13f, 7f, 17f, 11f)
            }
            Ic.LOCK -> {
                drawRoundRect(
                    color = tint, topLeft = o(6f, 11f), size = Size(12f * k, 9f * k),
                    cornerRadius = CornerRadius(2f * k, 2f * k), style = style
                )
                drawArc(
                    color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = o(8.5f, 4.5f), size = Size(7f * k, 7f * k), style = style
                )
                seg(8.5f, 8f, 8.5f, 11f); seg(15.5f, 8f, 15.5f, 11f)
            }
            Ic.THEME -> {
                drawCircle(color = tint, radius = 8f * k, center = o(12f, 12f), style = style)
                drawArc(
                    color = tint, startAngle = -90f, sweepAngle = 180f, useCenter = true,
                    topLeft = o(4f, 4f), size = Size(16f * k, 16f * k)
                )
            }
            Ic.GLOBE -> {
                drawCircle(color = tint, radius = 8f * k, center = o(12f, 12f), style = style)
                drawOval(color = tint, topLeft = o(8.5f, 4f), size = Size(7f * k, 16f * k), style = style)
                seg(4f, 12f, 20f, 12f)
            }
            Ic.LOGOUT -> {
                poly(10f, 4f, 5f, 4f, 5f, 20f, 10f, 20f)
                seg(9f, 12f, 20f, 12f); seg(16f, 8f, 20f, 12f); seg(16f, 16f, 20f, 12f)
            }
            Ic.TRASH -> {
                seg(5f, 7f, 19f, 7f)
                poly(9f, 7f, 9f, 4.5f, 15f, 4.5f, 15f, 7f)
                poly(7f, 7f, 8f, 20f, 16f, 20f, 17f, 7f)
                seg(10.5f, 10.5f, 10.5f, 16.5f); seg(13.5f, 10.5f, 13.5f, 16.5f)
            }
            Ic.CHEVRON_RIGHT -> poly(9f, 6f, 15f, 12f, 9f, 18f)
            Ic.CHEVRON_DOWN -> poly(6f, 9f, 12f, 15f, 18f, 9f)
            Ic.PLUS -> {
                seg(12f, 5f, 12f, 19f); seg(5f, 12f, 19f, 12f)
            }
            Ic.BACK -> poly(15f, 5f, 8f, 12f, 15f, 19f)
            Ic.CHECK -> poly(5f, 12.5f, 10f, 17.5f, 19f, 7f)
        }
    }
}
