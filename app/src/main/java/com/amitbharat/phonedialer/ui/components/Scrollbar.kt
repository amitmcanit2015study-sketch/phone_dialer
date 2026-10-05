package com.amitbharat.phonedialer.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.simpleScrollbar(
    state: LazyListState,
    width: Dp = 5.dp,
    color: Color = Color.Unspecified,
    showTrack: Boolean = true,
    paddingEnd: Dp = 2.dp
): Modifier = composed {
    val thumbColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    drawWithContent {
        drawContent()
        val totalItemsCount = state.layoutInfo.totalItemsCount
        val visibleItems = state.layoutInfo.visibleItemsInfo
        if (totalItemsCount > 0 && visibleItems.isNotEmpty()) {
            val visibleCount = visibleItems.size
            if (visibleCount < totalItemsCount) {
                val firstVisibleItem = visibleItems.first()
                val viewportHeight = size.height
                val elementHeight = viewportHeight / totalItemsCount
                val scrollbarHeight = (visibleCount * elementHeight).coerceIn(28.dp.toPx(), viewportHeight * 0.75f)
                
                val scrollOffset = state.firstVisibleItemScrollOffset
                val firstItemSize = firstVisibleItem.size
                val fraction = if (firstItemSize > 0) scrollOffset.toFloat() / firstItemSize else 0f
                
                val scrollbarY = ((firstVisibleItem.index + fraction) * elementHeight)
                    .coerceIn(0f, (viewportHeight - scrollbarHeight).coerceAtLeast(0f))

                val x = (this.size.width - width.toPx() - paddingEnd.toPx()).coerceAtLeast(0f)
                val radius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)

                if (showTrack) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x, 0f),
                        size = Size(width.toPx(), viewportHeight),
                        cornerRadius = radius
                    )
                }

                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(x, scrollbarY),
                    size = Size(width.toPx(), scrollbarHeight),
                    cornerRadius = radius
                )
            }
        }
    }
}
