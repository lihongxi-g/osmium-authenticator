package com.safekey.authenticator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.integrity.IntegrityLevel

/**
 * Post-scan verdict glyph for the report's level card.
 *
 *  - CLEAN: a smoothly drawn check mark (circle sweeps in, then the check
 *    stroke draws along its path). Auto-plays once whenever a new scan
 *    lands; tap to replay.
 *  - COMPROMISED: a cross — reserved for confirmed problems only.
 *  - Anything doubtful (weak findings, modified system, cannot verify):
 *    a thinking face. Finding a root-manager app etc. is doubt, not proof.
 */
@Composable
fun IntegrityVerdictGlyph(
    level: IntegrityLevel?,
    replayKey: Long,
    modifier: Modifier = Modifier
) {
    when (level) {
        IntegrityLevel.CLEAN -> AnimatedCheckGlyph(replayKey, modifier)
        IntegrityLevel.COMPROMISED -> EmojiGlyph("❌", modifier)
        IntegrityLevel.SUSPICIOUS, IntegrityLevel.UNVERIFIED, IntegrityLevel.UNKNOWN ->
            EmojiGlyph("🤔", modifier)
        null -> Unit
    }
}

@Composable
private fun EmojiGlyph(emoji: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 34.sp)
    }
}

/**
 * Hand-drawn success mark, pure Canvas (no assets): the circle sweeps in
 * during the first half of the animation, then the check is trimmed along
 * its path in the second half.
 */
@Composable
private fun AnimatedCheckGlyph(replayKey: Long, modifier: Modifier = Modifier) {
    val green = integrityClearColor()
    val progress = remember { Animatable(0f) }
    var replay by remember { mutableIntStateOf(0) }

    LaunchedEffect(replayKey, replay) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    val strokeWidth = 3.5.dp
    Canvas(
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { replay++ }
    ) {
        val p = progress.value
        val circleFraction = (p / 0.55f).coerceIn(0f, 1f)
        val checkFraction = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val strokePx = strokeWidth.toPx()

        if (circleFraction > 0f) {
            val inset = strokePx / 2f
            val diameter = size.minDimension - strokePx
            drawArc(
                color = green,
                startAngle = -90f,
                sweepAngle = 360f * circleFraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        if (checkFraction > 0f) {
            val checkPath = Path().apply {
                moveTo(size.width * 0.31f, size.height * 0.53f)
                lineTo(size.width * 0.44f, size.height * 0.66f)
                lineTo(size.width * 0.70f, size.height * 0.37f)
            }
            val measure = PathMeasure().apply { setPath(checkPath, false) }
            val length = measure.length
            drawPath(
                path = checkPath,
                color = green,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(length * checkFraction, length),
                        phase = 0f
                    )
                )
            )
        }
    }
}
