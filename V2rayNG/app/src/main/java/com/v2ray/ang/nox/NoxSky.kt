package com.v2ray.ang.nox

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class SkyMode { OFF, ON, PING }

private class Star(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

/** Фон: звёзды (ночь) или солнечные искры и облака (день). */
@Composable
fun NoxSky(mode: SkyMode, night: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = LocalNox.current
    val stars = remember { List(70) { Star(Random.nextFloat(), Random.nextFloat(), 1f + Random.nextFloat() * 2.2f, Random.nextFloat() * 6.28f, 0.6f + Random.nextFloat() * 2.4f) } }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
        if (!enabled) return@Canvas
        if (!night) drawClouds(t, mode)
        stars.forEachIndexed { i, s ->
            val pos = Offset(s.x * size.width, s.y * size.height)
            val (col, a) = when (mode) {
                SkyMode.OFF -> c.off to 0.45f
                SkyMode.PING -> (if (night) c.star else c.star) to 0.25f
                SkyMode.ON -> {
                    val w = 0.5f + 0.5f * sin(t * s.speed + s.phase)
                    val alpha = when (i % 3) {
                        0 -> 0.25f + 0.75f * w                       // плавное мерцание
                        1 -> if (w > 0.8f) 1f else 0.2f             // резкая вспышка
                        else -> if (((t * s.speed + s.phase).toInt() % 2) == 0) 0.9f else 0.3f // ступенчатое
                    }
                    (if (i % 4 == 0) c.accent else c.star) to alpha
                }
            }
            drawCircle(col.copy(alpha = a), s.size * (if (night) 1f else 1.3f), pos)
        }
        if (mode == SkyMode.PING) drawMeteors(t, c)
    }
}

/** Звёздопад: одновременно падает от 1 до 5 звёздочек, число меняется по циклу. */
private fun DrawScope.drawMeteors(t: Float, c: NoxColors) {
    val count = 1 + ((t / 1.6f).toInt() % 5)
    for (m in 0 until 5) {
        if (m >= count) continue
        val p = (t * 0.5f + m * 0.37f)
        val cycle = p.toInt()
        val y = (p - cycle) * (size.height + 120f) - 60f
        val seed = Random(cycle * 31 + m * 7)
        val x = (0.1f + seed.nextFloat() * 0.8f) * size.width
        val head = Offset(x, y)
        val tail = Offset(x - 14f, y - 110f)
        drawLine(Brush.linearGradient(listOf(Color.Transparent, c.accent), tail, head), tail, head, 4f, StrokeCap.Round)
        drawCircle(c.star, 5f, head)
    }
}

private fun DrawScope.drawClouds(t: Float, mode: SkyMode) {
    val a = if (mode == SkyMode.OFF) 0.25f else 0.55f
    for (i in 0 until 3) {
        val x = ((t * (8f + i * 4f) + i * 400f) % (size.width + 400f)) - 200f
        val y = size.height * (0.12f + i * 0.2f)
        val col = Color.White.copy(alpha = a)
        drawOval(col, Offset(x, y), androidx.compose.ui.geometry.Size(180f, 54f))
        drawOval(col, Offset(x + 40f, y - 26f), androidx.compose.ui.geometry.Size(110f, 60f))
    }
}

fun moonPath(center: Offset, r: Float): Path {
    val a = Path().apply { addOval(Rect(center, r)) }
    val b = Path().apply { addOval(Rect(Offset(center.x + r * 0.45f, center.y - r * 0.2f), r * 0.85f)) }
    return Path.combine(PathOperation.Difference, a, b)
}

/** Луна (ночь) или солнце (день). glow 0..1 — свечение, color — заливка. */
@Composable
fun NoxBody(night: Boolean, color: Color, glow: Float, modifier: Modifier = Modifier, spin: Boolean = false) {
    val rot by rememberInfiniteTransition(label = "sun").animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "r")
    Canvas(modifier) {
        val ctr = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.34f
        if (glow > 0f) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * glow), Color.Transparent), ctr, r * 2.2f), r * 2.2f, ctr)
        }
        if (night) {
            drawPath(moonPath(ctr, r), color)
            drawCircle(Color.White.copy(alpha = 0.9f * (0.4f + glow * 0.6f)), r * 0.05f, Offset(ctr.x - r * 0.45f, ctr.y - r * 0.4f))
        } else {
            rotate(if (spin) rot else 0f, ctr) {
                for (i in 0 until 12) {
                    val ang = (i * 30f) * PI.toFloat() / 180f
                    val s = Offset(ctr.x + kotlin.math.cos(ang) * r * 1.25f, ctr.y + sin(ang) * r * 1.25f)
                    val e = Offset(ctr.x + kotlin.math.cos(ang) * r * 1.65f, ctr.y + sin(ang) * r * 1.65f)
                    drawLine(color, s, e, 10f, StrokeCap.Round)
                }
            }
            drawCircle(color, r, ctr)
        }
    }
}
