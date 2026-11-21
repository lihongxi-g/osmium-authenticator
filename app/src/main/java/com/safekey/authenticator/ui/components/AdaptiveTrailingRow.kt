package com.safekey.authenticator.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Keep short controls and labels at the end of a row. If that would leave the
 * main text too narrow, give both slots the full text width on separate lines.
 * The slots are composed and measured once so interactive controls keep their state.
 */
@Composable
internal fun AdaptiveTrailingRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    main: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            Box { leading?.invoke() }
            Box { main() }
            Box { trailing?.invoke() }
        }
    ) { (leadingSlot, mainSlot, trailingSlot), constraints ->
        val horizontalGap = 8.dp.roundToPx()
        val leadingGap = 16.dp.roundToPx()
        val verticalGap = 4.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val icon = leadingSlot.measure(loose)
        val textStart = if (icon.width == 0) 0 else {
            icon.width + leadingGap.coerceAtMost(constraints.maxWidth - icon.width)
        }
        val end = trailingSlot.measure(loose.copy(maxWidth = constraints.maxWidth - textStart))
        val gap = if (end.width == 0) 0 else horizontalGap
        val textWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth - textStart
        } else {
            maxOf(constraints.minWidth - textStart, mainSlot.maxIntrinsicWidth(Constraints.Infinity) + gap + end.width)
        }
        val stacked = end.width > 0 && shouldStackTrailing(
            width = textWidth,
            gap = gap,
            mainMinWidth = mainSlot.minIntrinsicWidth(Constraints.Infinity),
            trailingWidth = end.width
        )
        val mainWidth = if (stacked) textWidth else textWidth - gap - end.width
        val aboveHeight = if (stacked && constraints.hasBoundedHeight) {
            (constraints.maxHeight - end.height - verticalGap).coerceAtLeast(0)
        } else {
            constraints.maxHeight
        }
        val body = mainSlot.measure(Constraints(maxWidth = mainWidth, maxHeight = aboveHeight))
        val firstLineHeight = if (stacked) maxOf(icon.height, body.height) else {
            maxOf(icon.height, body.height, end.height)
        }
        val contentHeight = if (stacked) firstLineHeight + verticalGap + end.height else firstLineHeight
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val top = ((height - contentHeight) / 2).coerceAtLeast(0)

        layout(textStart + textWidth, height) {
            icon.placeRelative(0, top + (firstLineHeight - icon.height) / 2)
            body.placeRelative(textStart, top + (firstLineHeight - body.height) / 2)
            if (stacked) {
                end.placeRelative(textStart, top + firstLineHeight + verticalGap)
            } else {
                end.placeRelative(textStart + textWidth - end.width, top + (firstLineHeight - end.height) / 2)
            }
        }
    }
}

internal fun shouldStackTrailing(width: Int, gap: Int, mainMinWidth: Int, trailingWidth: Int): Boolean =
    trailingWidth > 0 && width - gap - trailingWidth < maxOf(width / 2, mainMinWidth)