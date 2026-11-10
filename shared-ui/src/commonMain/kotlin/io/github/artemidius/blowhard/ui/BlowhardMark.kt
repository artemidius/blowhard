@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Three offset wind bars form Blowhard's own mark. */
@Composable
@Suppress("MagicNumber") // Coordinates on the mark's eight-unit drawing grid.
internal fun BlowhardMark(size: Dp = 36.dp) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(size)) {
        val unit = this.size.width / 8f
        drawRect(color, Offset(0f, unit), Size(unit * 6f, unit))
        drawRect(color, Offset(unit * 2f, unit * 3f), Size(unit * 6f, unit))
        drawRect(color, Offset(unit, unit * 5f), Size(unit * 5f, unit))
    }
}
