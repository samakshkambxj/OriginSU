package com.kazesu.manager.ui.screen.tune

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kazesu.manager.R
import com.kazesu.manager.ui.LocalUiMode
import com.kazesu.manager.ui.UiMode
import com.kazesu.manager.ui.component.material.ExpressiveScaffold
import com.kazesu.manager.ui.component.material.ExpressiveSwitch
import com.kazesu.manager.ui.component.material.TopBarBackButton
import com.kazesu.manager.ui.component.material.expressiveTopAppBarColors
import com.kazesu.manager.ui.component.miuix.AppCard
import com.kazesu.manager.ui.component.miuix.AppScaffold
import com.kazesu.manager.ui.theme.LocalBackgroundImagePath
import com.kazesu.manager.ui.theme.LocalEnableBlur
import com.kazesu.manager.ui.theme.backgroundAwareColor
import com.kazesu.manager.ui.util.BlurredBar
import com.kazesu.manager.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

/** One selectable option in [TuneChoiceDialog]. */
internal data class TuneChoice(
    val label: String,
    val description: String? = null,
)

/**
 * Page shell shared by both skins: a scrolling column with the mode's own
 * scaffold and top bar. The rest of KazeTune is written once against the
 * adaptive widgets below.
 */
@Composable
internal fun TunePage(
    title: String,
    loading: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> TunePageMaterial(title, loading, onBack, content)
        UiMode.Miuix -> TunePageMiuix(title, loading, onBack, content)
    }
}

@Composable
private fun TunePageMaterial(
    title: String,
    loading: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(scroll.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TunePageMiuix(
    title: String,
    loading: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = MiuixScrollBehavior()
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive || hasCustomBackground) Color.Transparent else colorScheme.surface

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .fillMaxSize()
                    .nestedScroll(scroll.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** A titled group of tunables. */
@Composable
internal fun TuneSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = backgroundAwareColor(MaterialTheme.colorScheme.surfaceContainerLow),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                content()
            }
        }

        UiMode.Miuix -> AppCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MiuixText(
                    text = title,
                    style = textStyles.title4,
                    color = colorScheme.primary,
                )
                content()
            }
        }
    }
}

/**
 * A tappable tunable row: title, optional explanation, and the current value.
 * A null [onClick] renders a read-only row.
 */
@Composable
internal fun TuneRow(
    title: String,
    description: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> Surface(
            shape = MaterialTheme.shapes.large,
            color = backgroundAwareColor(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null && enabled) Modifier.clickable(onClick = onClick)
                        else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        UiMode.Miuix -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                MiuixText(
                    text = title,
                    style = textStyles.body1,
                    color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariantActions,
                )
                if (!description.isNullOrBlank()) {
                    MiuixText(
                        text = description,
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceVariantActions,
                    )
                }
            }
            if (value != null) {
                MiuixText(
                    text = value,
                    style = textStyles.body2,
                    color = colorScheme.primary,
                )
            }
        }
    }
}

/** A labelled switch row. */
@Composable
internal fun TuneSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }

        UiMode.Miuix -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                MiuixText(
                    text = title,
                    style = textStyles.body1,
                    color = colorScheme.onSurface,
                )
                if (!description.isNullOrBlank()) {
                    MiuixText(
                        text = description,
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceVariantActions,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

/** A secondary action row such as a section reset. */
@Composable
internal fun TuneActionRow(
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        UiMode.Miuix -> MiuixTextButton(
            text = title,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

/** Small monospace status line used by diagnostics and snapshot rows. */
@Composable
internal fun TuneStatLine(text: String) {
    when (LocalUiMode.current) {
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )

        UiMode.Miuix -> MiuixText(
            text = text,
            style = textStyles.body2,
            color = colorScheme.onSurface,
        )
    }
}

/** Muted body copy inside a section. */
@Composable
internal fun TuneBodyText(text: String) {
    when (LocalUiMode.current) {
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        UiMode.Miuix -> MiuixText(
            text = text,
            style = textStyles.body2,
            color = colorScheme.onSurfaceVariantActions,
        )
    }
}

/**
 * Dialog shell. Each skin gets its native dialog; the body is written once
 * with the adaptive field/switch widgets below.
 */
@Composable
internal fun TuneDialog(
    title: String,
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )

        UiMode.Miuix -> OverlayDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    MiuixTextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = { if (confirmEnabled) onConfirm() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    }
}

/** A single-line text field inside [TuneDialog]. */
@Composable
internal fun TuneDialogField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        UiMode.Miuix -> TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A labelled switch inside [TuneDialog]. */
@Composable
internal fun TuneDialogSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    TuneSwitchRow(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

/** A one-of-many picker; [selectedIndex] below zero means "custom / none". */
@Composable
internal fun TuneChoiceDialog(
    title: String,
    choices: List<TuneChoice>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    TuneDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                choices.forEachIndexed { index, choice ->
                    val selected = index == selectedIndex
                    when (LocalUiMode.current) {
                        UiMode.Material -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(index)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                text = choice.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            choice.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        UiMode.Miuix -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(index)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            MiuixText(
                                text = choice.label,
                                style = textStyles.body1,
                                color = if (selected) colorScheme.primary else colorScheme.onSurface,
                            )
                            choice.description?.let {
                                MiuixText(
                                    text = it,
                                    style = textStyles.body2,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
