@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private const val GLOW_ALPHA = 0.22f
private const val OUTER_RING_ALPHA = 0.15f
private const val INNER_RING_ALPHA = 0.45f
private const val OUTER_RADIUS = 0.84f
private const val INNER_RADIUS = 0.61f
private const val ARC_START = -80f
private const val ARC_SWEEP = 105f

/** Static decorative light keeps the workspace calm and accessible. */
@Composable
internal fun OrbitalEmblem(compact: Boolean) {
    val orange = MaterialTheme.colorScheme.primary
    Box(Modifier.size(if (compact) 112.dp else 164.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(listOf(orange.copy(alpha = GLOW_ALPHA), Color.Transparent), radius = radius),
            )
            drawCircle(orange.copy(alpha = OUTER_RING_ALPHA), radius * OUTER_RADIUS, style = Stroke(1.dp.toPx()))
            drawCircle(orange.copy(alpha = INNER_RING_ALPHA), radius * INNER_RADIUS, style = Stroke(1.dp.toPx()))
            drawArc(
                orange,
                ARC_START,
                ARC_SWEEP,
                false,
                Offset(radius * (1 - OUTER_RADIUS), radius * (1 - OUTER_RADIUS)),
                Size(radius * OUTER_RADIUS * 2, radius * OUTER_RADIUS * 2),
                style = Stroke(2.dp.toPx()),
            )
            drawCircle(orange, 3.dp.toPx(), Offset(radius * (1 + OUTER_RADIUS), radius))
        }
        BlowhardMark(if (compact) 36.dp else 48.dp)
    }
}
