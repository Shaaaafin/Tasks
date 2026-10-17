package dev.quiettasks.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

internal class FingerMenuPosition(private val point: IntOffset, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
        point.x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
        point.y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)),
    )
}

@Composable internal fun FingerTaskMenu(point: IntOffset, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(point, margin) { FingerMenuPosition(point, margin) }
    Popup(popupPositionProvider = position, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Surface(Modifier.testTag("task-menu").widthIn(min = 196.dp, max = 280.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp) { Column(content = content) }
    }
}
