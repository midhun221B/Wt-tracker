package wt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** The app's name. */
const val APP_NAME = "Downhill"

/** The launcher icon's road in its 108 × 108 box (same curve as `ic_launcher_foreground.xml`). */
private const val ROAD = "M14 26 C48 24 36 58 60 62 C84 66 92 78 94 98"

/** The app's mark, [size] wide: the icon's orange road on a dark rounded square. Decorative. */
@Composable
fun RoadMark(size: Dp, modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(ROAD).toPath() }
    Canvas(modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(Palette.Card)) {
        scale(this.size.width / 108f, pivot = Offset.Zero) {
            drawPath(path, Palette.Accent, style = Stroke(15f, cap = StrokeCap.Round))
        }
    }
}

/** The mark and "Downhill" side by side, for the welcome screen and the share card. */
@Composable
fun Wordmark(markSize: Dp, textSize: TextUnit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(markSize * 0.35f)) {
        RoadMark(markSize)
        Text(APP_NAME, style = numberStyle(textSize, Palette.Text))
    }
}
