package com.chainreaction.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.chainreaction.app.Playback
import com.chainreaction.app.Sounds
import com.chainreaction.app.game.Board
import com.chainreaction.app.game.Pos
import com.chainreaction.app.game.Wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2 * PI).toFloat()

/** Orb size and how far apart orbs in one cell sit (fractions of a cell). */
private const val ORB_R = 0.16f
private const val CLUSTER_SPREAD = 0.11f

/**
 * Draws the board and plays back [playback] wave by wave. An explosion is calm on purpose:
 * the orbs simply glide into the neighbouring cells at a steady pace, and consecutive waves
 * follow each other with the same rhythm, so a long chain reads as one flowing motion.
 */
@Composable
fun BoardView(
    board: Board,
    playback: Playback?,
    turnColor: Color,
    onTap: (Int, Int) -> Unit,
    onPlaybackDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Board being animated, tagged with the playback it belongs to.
    var shown by remember { mutableStateOf<Pair<Int, Board>?>(null) }
    var wave by remember { mutableStateOf<Wave?>(null) }
    var popCell by remember { mutableStateOf<Pos?>(null) }
    val waveProgress = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val done by rememberUpdatedState(onPlaybackDone)
    val tap by rememberUpdatedState(onTap)

    LaunchedEffect(playback?.id) {
        val pb = playback
        if (pb == null) { shown = null; wave = null; popCell = null; return@LaunchedEffect }
        val res = pb.result
        // 1. the new orb grows in gently
        shown = pb.id to res.placed
        Sounds.tap()
        popCell = res.pos
        pop.snapTo(0.55f)
        pop.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
        popCell = null
        // 2. waves at a steady tempo; very long chains ease slightly faster, never abruptly
        val last = res.waves.lastIndex
        res.waves.forEachIndexed { i, w ->
            shown = pb.id to w.mid
            Sounds.burst(i)
            wave = w
            waveProgress.snapTo(0f)
            val ms = (270 - (i - 8).coerceAtLeast(0) * 5).coerceAtLeast(190)
            waveProgress.animateTo(1f, tween(ms, easing = if (i == last) LinearOutSlowInEasing else LinearEasing))
            shown = pb.id to w.after
            wave = null
        }
        done()
    }

    // While a move plays, never show the final board early: until the animation has
    // produced its first frame, show the board as it was right after the tap.
    val current = when {
        playback == null -> board
        shown?.first == playback.id -> shown!!.second
        else -> playback.result.placed
    }
    val time = rememberInfiniteTransition(label = "orbs").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "time",
    )

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cellDp = min(maxWidth.value / current.cols, maxHeight.value / current.rows)
        Canvas(
            Modifier
                .size(Dp(cellDp * current.cols), Dp(cellDp * current.rows))
                .pointerInput(current.rows, current.cols) {
                    detectTapGestures { o ->
                        val cs = size.width.toFloat() / current.cols
                        val c = (o.x / cs).toInt()
                        val r = (o.y / cs).toInt()
                        if (r in 0 until current.rows && c in 0 until current.cols) tap(r, c)
                    }
                },
        ) {
            val cs = size.width / current.cols
            drawGrid(current, cs, turnColor)
            val t = time.value
            for (r in 0 until current.rows) for (c in 0 until current.cols) {
                val n = current.count(r, c)
                if (n == 0) continue
                drawCluster(
                    center = Offset((c + 0.5f) * cs, (r + 0.5f) * cs),
                    count = n,
                    color = playerColor(current.owner(r, c)),
                    critical = n >= current.capacity(r, c) - 1,
                    t = t,
                    phase = (r * 7 + c * 13) * 0.37f,
                    cs = cs,
                    scale = if (popCell == Pos(r, c)) pop.value else 1f,
                )
            }
            wave?.let { drawWave(it, current, waveProgress.value, cs) }
        }
    }
}

private fun DrawScope.drawGrid(board: Board, cs: Float, turnColor: Color) {
    val line = turnColor.copy(alpha = 0.45f)
    val stroke = (cs * 0.015f).coerceAtLeast(1f)
    for (c in 0..board.cols) drawLine(line, Offset(c * cs, 0f), Offset(c * cs, size.height), stroke)
    for (r in 0..board.rows) drawLine(line, Offset(0f, r * cs), Offset(size.width, r * cs), stroke)
}

// ---- orb clusters: small 3D "molecules" that slowly tumble --------------------------------

private class P3(val x: Float, val y: Float, val z: Float)

private val inv3 = 1f / sqrt(3f)
private val shapes: Map<Int, List<P3>> = mapOf(
    1 to listOf(P3(0f, 0f, 0f)),
    2 to listOf(P3(-1f, 0f, 0f), P3(1f, 0f, 0f)),
    3 to List(3) { i -> val a = i * TWO_PI / 3 - PI.toFloat() / 2; P3(cos(a), sin(a), 0f) },
    4 to listOf(P3(inv3, inv3, inv3), P3(inv3, -inv3, -inv3), P3(-inv3, inv3, -inv3), P3(-inv3, -inv3, inv3)),
)

private fun shapeFor(n: Int): List<P3> =
    shapes[n] ?: List(n) { i -> val a = i * TWO_PI / n; P3(cos(a), sin(a), 0f) }

private fun DrawScope.drawCluster(
    center: Offset, count: Int, color: Color, critical: Boolean,
    t: Float, phase: Float, cs: Float, scale: Float,
) {
    // Spin around the vertical axis with a slow wobbling tilt. Fuller cells turn a bit faster.
    val yaw = t * TWO_PI * (if (critical) 3 else 1) + phase
    val tilt = 0.55f * sin(t * TWO_PI + phase)
    val cy = cos(yaw); val sy = sin(yaw); val ct = cos(tilt); val st = sin(tilt)
    val spread = cs * CLUSTER_SPREAD * scale
    val rad = cs * ORB_R * scale

    val projected = shapeFor(count).map { p ->
        val x1 = p.x * cy + p.z * sy
        val z1 = -p.x * sy + p.z * cy
        val y2 = p.y * ct - z1 * st
        val z2 = p.y * st + z1 * ct
        Triple(x1 * spread, y2 * spread, z2)
    }.sortedBy { it.third } // far orbs first, near ones drawn on top

    for ((x, y, z) in projected) drawOrb(center + Offset(x, y), rad * (1f + 0.08f * z), color)
}

fun DrawScope.drawOrb(center: Offset, rad: Float, color: Color, alpha: Float = 1f) {
    drawCircle(color.copy(alpha = 0.12f * alpha), rad * 1.4f, center)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(lerp(color, Color.White, 0.45f), color, lerp(color, Color.Black, 0.5f)),
            center = center + Offset(-rad * 0.35f, -rad * 0.35f),
            radius = rad * 1.4f,
        ),
        radius = rad, center = center, alpha = alpha,
    )
    drawCircle(Color.White.copy(alpha = 0.45f * alpha), rad * 0.25f, center + Offset(-rad * 0.38f, -rad * 0.38f))
}

/** Orbs leaving every bursting cell glide in a straight line to each neighbour. */
private fun DrawScope.drawWave(w: Wave, board: Board, p: Float, cs: Float) {
    val color = playerColor(w.player)
    val rad = cs * ORB_R
    for (src in w.sources) {
        val from = Offset((src.c + 0.5f) * cs, (src.r + 0.5f) * cs)
        for (n in board.neighbors(src.r, src.c)) {
            val to = Offset((n.c + 0.5f) * cs, (n.r + 0.5f) * cs)
            drawOrb(Offset(from.x + (to.x - from.x) * p, from.y + (to.y - from.y) * p), rad, color)
        }
    }
}
