package org.adaway.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role

/**
 * Clickable wrapper that answers a press the way the system does, with the usual highlight.
 *
 * The highlight is drawn within the bounds it is given, so pass the [shape] of the surface being
 * pressed whenever the modifier is not already applied after a clip: without it, a rounded surface
 * gets a square highlight overflowing its corners.
 */
fun Modifier.safeClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    shape: Shape? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val clipped = if (shape == null) Modifier else Modifier.clip(shape)
    clipped.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = interactionSource,
        indication = ripple(),
        onClick = onClick
    )
}

/**
 * Combined clickable wrapper that answers a press the way the system does, with the usual
 * highlight. See [safeClickable] about [shape].
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.safeCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    shape: Shape? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val clipped = if (shape == null) Modifier else Modifier.clip(shape)
    clipped.combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        interactionSource = interactionSource,
        indication = ripple(),
        onClick = onClick
    )
}
