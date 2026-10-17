package com.safekey.authenticator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star

/**
 * Material 3 Expressive style loading indicator: instead of a spinning arc it
 * morphs continuously between rounded polygons (circle → six-point star →
 * circle) while the whole glyph slowly rotates.
 *
 * Implementation notes:
 *  - shape maths come from `androidx.graphics:graphics-shapes` (Apache-2.0),
 *    a standalone library with no Compose dependency, so this does NOT force a
 *    Compose/material3 upgrade.
 *  - the phase is read inside the draw block, so each frame only invalidates
 *    drawing — no recomposition, no layout.
 *  - the [Path] is reused frame to frame; only the cubic list from the morph is
 *    allocated per frame (same approach as material3's own Morph shape).
 *
 * Use it wherever the app used to show `CircularProgressIndicator`.
 */
@Composable
fun MorphLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp
) {
    val morphs = remember { loadingMorphs() }
    val path = remember { Path() }
    val transition = rememberInfiniteTransition(label = "morphLoader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SEGMENT_MS * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphPhase"
    )

    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        if (radius <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)

        // Two morphs per cycle: 0 → 0.5 shape A→B, 0.5 → 1 shape B→A.
        val half = phase * 2f
        val morph = if (half < 1f) morphs[0] else morphs[1]
        val local = easeWithHold(if (half < 1f) half else half - 1f)

        path.reset()
        val cubics = morph.asCubics(local)
        if (cubics.isNotEmpty()) {
            val first = cubics[0]
            path.moveTo(
                center.x + first.anchor0X * radius,
                center.y + first.anchor0Y * radius
            )
            for (c in cubics) {
                path.cubicTo(
                    center.x + c.control0X * radius,
                    center.y + c.control0Y * radius,
                    center.x + c.control1X * radius,
                    center.y + c.control1Y * radius,
                    center.x + c.anchor1X * radius,
                    center.y + c.anchor1Y * radius
                )
            }
            path.close()
        }

        rotate(degrees = 180f * phase, pivot = center) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private const val SEGMENT_MS = 620

/**
 * Holds the endpoint shapes for ~18% of each segment so the eye registers the
 * shape before it transforms again, then smoothsteps through the middle.
 */
private fun easeWithHold(t: Float): Float {
    val hold = 0.18f
    return when {
        t <= hold -> 0f
        t >= 1f - hold -> 1f
        else -> {
            val x = (t - hold) / (1f - 2f * hold)
            x * x * (3f - 2f * x)
        }
    }
}

/** Circle ⇄ rounded six-point star, normalised to the unit circle. */
private fun loadingMorphs(): List<Morph> {
    val circle = RoundedPolygon.circle(numVertices = 12).normalized()
    val star = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        radius = 1f,
        innerRadius = 0.78f,
        rounding = CornerRounding(radius = 0.2f),
        innerRounding = CornerRounding(radius = 0.2f)
    ).normalized()
    return listOf(Morph(circle, star), Morph(star, circle))
}
