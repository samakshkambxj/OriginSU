package com.originsu.manager.ui.component.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.renderBackgroundBlur
import org.koin.compose.koinInject

/**
 * The canonical container for the settings bento grid.
 *
 * A bento grid differs from [SegmentedColumn] in that tiles are independent
 * cards that may occupy different column spans and heights. Use it for
 * dashboard-like settings surfaces that mix high-level hubs, live status and
 * frequently used toggles. Keep [SegmentedColumn] / [LazySegmentedColumn] for
 * ordinary settings lists and subpages.
 *
 * The grid is itself scrollable. Full-width rows (section bands, alerts) use
 * `item(span = StaggeredGridItemSpan.FullLine)`; compact tiles use a plain
 * `item`.
 */
@Composable
fun BentoGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    columns: StaggeredGridCells = StaggeredGridCells.Adaptive(minSize = 150.dp),
    content: LazyStaggeredGridScope.() -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * A single bento tile: an independent, rounded card that follows the app's
 * card alpha / blur configuration.
 *
 * @param onClick When non-null the whole card becomes clickable.
 * @param containerColor Optional override for the surface color.
 */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val shape = RoundedCornerShape(24.dp)
    val baseColor = containerColor ?: MaterialTheme.colorScheme.surfaceBright
    val surfaceColor = if (themeConfig.isEnableBlurExp) {
        Color.Transparent
    } else {
        baseColor.copy(alpha = cardConfig.cardAlpha)
    }
    val contentColor = contentColorFor(baseColor)

    val tileModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .renderBackgroundBlur(baseColor)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = tileModifier,
            enabled = enabled,
            shape = shape,
            color = surfaceColor,
            contentColor = contentColor,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = tileModifier,
            shape = shape,
            color = surfaceColor,
            contentColor = contentColor,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** A small header row used at the top of bento tiles and section bands. */
@Composable
fun BentoHeader(
    icon: ImageVector,
    title: String,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailingContent?.invoke(this)
    }
}

/**
 * A compact status chip for bento tiles. Reflects a feature's live state with a
 * colored dot; the surrounding text is provided by the caller.
 */
@Composable
fun BentoStatusChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
) {
    val dotColor = when {
        isWarning -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container = if (active || isWarning) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Compact in-tile toggle row. This is the bento counterpart of
 * [SettingsSwitchWidget]: it omits the list-item surface (the tile provides it)
 * while keeping the same switch semantics and haptics.
 */
@Composable
fun BentoToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(
                            if (checked) {
                                HapticFeedbackType.ToggleOff
                            } else {
                                HapticFeedbackType.ToggleOn
                            }
                        )
                        onCheckedChange(!checked)
                    }
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            modifier = Modifier.clearAndSetSemantics {},
            enabled = enabled,
            checked = checked,
            colors = SwitchDefaults.colors(
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceBright,
            ),
            thumbContent = null,
            onCheckedChange = null,
        )
    }
}

/**
 * Compact in-tile row that navigates to another page or performs an action.
 * This is the bento counterpart of [SettingsJumpPageWidget].
 */
@Composable
fun BentoJumpRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
    trailingIcon: ImageVector = Icons.Filled.ChevronRight,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Compact in-tile row that opens a single-choice dialog. The current value is
 * rendered as the subtitle so the row keeps the same compact bento rhythm.
 */
@Composable
fun BentoChooseRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    BentoJumpRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        enabled = enabled,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        SettingsChooseDialog(
            show = true,
            title = title,
            items = items,
            selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            onDismiss = { showDialog = false },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

/** Small secondary line used under bento tile titles. */
@Composable
fun BentoCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
