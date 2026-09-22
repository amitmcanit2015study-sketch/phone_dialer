package com.amitbharat.phonedialer.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.simpleScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f)
): Modifier = drawWithContent {
    drawContent()
    val totalItemsCount = state.layoutInfo.totalItemsCount
    if (totalItemsCount > 0) {
        val firstVisibleItem = state.layoutInfo.visibleItemsInfo.firstOrNull()
        if (firstVisibleItem != null) {
            val viewportHeight = size.height
            val elementHeight = viewportHeight / totalItemsCount
            val scrollbarHeight = (state.layoutInfo.visibleItemsInfo.size * elementHeight).coerceAtLeast(20.dp.toPx())
            
            val scrollOffset = state.firstVisibleItemScrollOffset
            val firstItemSize = firstVisibleItem.size
            val fraction = if (firstItemSize > 0) scrollOffset.toFloat() / firstItemSize else 0f
            
            val scrollbarY = ((firstVisibleItem.index + fraction) * elementHeight)
                .coerceAtMost(viewportHeight - scrollbarHeight)

            drawRect(
                color = color,
                topLeft = Offset(this.size.width - width.toPx(), scrollbarY),
                size = Size(width.toPx(), scrollbarHeight)
            )
        }
    }
}
