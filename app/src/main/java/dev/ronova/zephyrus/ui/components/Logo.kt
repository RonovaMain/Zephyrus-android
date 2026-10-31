package dev.ronova.zephyrus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Фирменный знак Zephyrus: небесно-розовый перелив, символ загрузки и звезда-примогем. Тот же рисунок, что у значка приложения. */
@Composable
fun ZephyrusLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) { drawLogo(this.size.minDimension) }
}

private fun sparkle(cx: Float, cy: Float, r: Float, pinch: Float = 0.17f): Path {
    val k = r * pinch
    return Path().apply {
        moveTo(cx, cy - r)
        quadraticBezierTo(cx + k, cy - k, cx + r, cy)
        quadraticBezierTo(cx + k, cy + k, cx, cy + r)
        quadraticBezierTo(cx - k, cy + k, cx - r, cy)
        quadraticBezierTo(cx - k, cy - k, cx, cy - r)
        close()
    }
}

fun DrawScope.drawLogo(s: Float) {
    val sky = Color(0xFF86D4FF)
    val mid = Color(0xFFBFC9FF)
    val rose = Color(0xFFFFB3D3)
    val corner = CornerRadius(s * 0.27f, s * 0.27f)

    drawRoundRect(
        brush = Brush.linearGradient(listOf(sky, mid, rose), start = Offset.Zero, end = Offset(s, s)),
        size = Size(s, s), cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.37f), Color.Transparent), startY = 0f, endY = s * 0.6f),
        size = Size(s, s), cornerRadius = corner,
    )

    fun glyph(dy: Float): Path = Path().apply {
        moveTo(0.46f * s, 0.27f * s + dy); lineTo(0.46f * s, 0.60f * s + dy)
        moveTo(0.31f * s, 0.46f * s + dy); lineTo(0.46f * s, 0.61f * s + dy); lineTo(0.61f * s, 0.46f * s + dy)
        moveTo(0.25f * s, 0.70f * s + dy); lineTo(0.25f * s, 0.76f * s + dy)
        lineTo(0.67f * s, 0.76f * s + dy); lineTo(0.67f * s, 0.70f * s + dy)
    }

    val stroke = Stroke(width = s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(glyph(s * 0.014f), Color(0x4D6E5AAA), style = stroke)
    drawPath(glyph(0f), Color.White, style = stroke)

    // звезда-примогем: свечение, градиентная заливка, контур
    val cx = s * 0.745f
    val cy = s * 0.265f
    val r = s * 0.165f
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), center = Offset(cx, cy), radius = r * 1.9f),
        radius = r * 1.9f, center = Offset(cx, cy),
    )
    val star = sparkle(cx, cy, r)
    drawPath(
        star,
        brush = Brush.linearGradient(
            listOf(Color.White, Color(0xFFFFE6F2), Color(0xFFCFEAFF)),
            start = Offset(cx - r, cy - r), end = Offset(cx + r, cy + r),
        ),
        style = Fill,
    )
    drawPath(star, Color.White.copy(alpha = 0.9f), style = Stroke(width = r * 0.06f))
    drawPath(sparkle(s * 0.865f, s * 0.485f, s * 0.05f), Color.White.copy(alpha = 0.92f), style = Fill)
}
