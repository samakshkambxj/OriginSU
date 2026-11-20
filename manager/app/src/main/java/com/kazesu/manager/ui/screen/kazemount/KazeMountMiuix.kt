package com.kazesu.manager.ui.screen.kazemount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazesu.manager.R
import com.kazesu.manager.data.mount.KazeMountMode
import com.kazesu.manager.ui.component.miuix.AppCard
import com.kazesu.manager.ui.component.miuix.AppScaffold
import com.kazesu.manager.ui.theme.LocalBackgroundImagePath
import com.kazesu.manager.ui.theme.LocalEnableBlur
import com.kazesu.manager.ui.util.BlurredBar
import com.kazesu.manager.ui.util.rememberBlurBackdrop
import com.kazesu.manager.ui.viewmodel.KazeMountUiState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun KazeMountMiuix(
    state: KazeMountUiState,
    actions: KazeMountActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null || hasCustomBackground) Color.Transparent else colorScheme.surface
    val status = state.status
    val modes = if (state.zeroMountDriverPresent || status.mode == KazeMountMode.ZeroMount) {
        KazeMountMode.entries
    } else {
        KazeMountMode.entries.filter { it != KazeMountMode.ZeroMount }
    }
    val modeLabels = modes.map { kazeMountModeLabel(it) }
    val backendIds = listOf("auto", "overlay", "bind")
    val backendLabels = backendIds.map { magicMountBackendLabel(it) }
    var uidInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.uidState.stored) {
        uidInput = state.uidState.stored.joinToString(", ")
    }

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = stringResource(R.string.kaze_mount_title),
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
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
                    actions = {
                        IconButton(onClick = { if (!state.isWorking) actions.onRefresh() }) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.kaze_mount_title),
                            style = textStyles.title4,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = if (status.available) {
                                kazeMountProviderLabel(status.provider)
                            } else {
                                stringResource(R.string.kaze_mount_unavailable)
                            },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                        Text(
                            text = stringResource(
                                R.string.kaze_mount_configured_mode,
                                kazeMountModeLabel(status.mode),
                            ),
                            style = textStyles.body2,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        status.runtime?.let { runtime ->
                            Text(
                                text = stringResource(
                                    R.string.kaze_mount_runtime_result,
                                    runtime.result.ifBlank { "—" },
                                    runtime.provider.ifBlank { "—" },
                                ),
                                style = textStyles.body2,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
            status.runtime?.warning?.takeIf { it.isNotBlank() }?.let { warning ->
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = warning,
                            modifier = Modifier.padding(16.dp),
                            color = colorScheme.error,
                        )
                    }
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.kaze_mount_provider_section),
                        summary = stringResource(R.string.kaze_mount_provider_summary),
                        items = modeLabels,
                        selectedIndex = modes.indexOf(status.mode).coerceAtLeast(0),
                        enabled = status.available && !state.isWorking,
                        onSelectedIndexChange = { index -> actions.onModeChange(modes[index]) },
                    )
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.kaze_mount_magic_enable),
                        summary = stringResource(R.string.kaze_mount_magic_enable_summary),
                        checked = status.magicMountEnabled,
                        enabled = status.available && !state.isWorking,
                        onCheckedChange = actions.onMagicEnabledChange,
                    )
                    OverlayDropdownPreference(
                        title = stringResource(R.string.kaze_mount_magic_backend),
                        summary = stringResource(R.string.kaze_mount_magic_backend_summary),
                        items = backendLabels,
                        selectedIndex = backendIds.indexOf(status.backend).coerceAtLeast(0),
                        enabled = status.available && !state.isWorking,
                        onSelectedIndexChange = { index -> actions.onBackendChange(backendIds[index]) },
                    )
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.kaze_mount_nomount_section),
                            style = textStyles.title4,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.kaze_mount_nomount_status,
                                status.nomount?.driverState ?: "absent",
                                status.nomount?.rulesApplied ?: 0,
                                status.nomount?.rulesExpected ?: 0,
                            ),
                            style = textStyles.body2,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = stringResource(
                                R.string.kaze_mount_nomount_active_uids,
                                state.uidState.active.joinToString(", ").ifBlank { "—" },
                            ),
                            style = textStyles.body2,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        TextField(
                            value = uidInput,
                            onValueChange = { uidInput = it },
                            label = stringResource(R.string.kaze_mount_nomount_uids),
                            modifier = Modifier.fillMaxWidth(),
                            useLabelAsPlaceholder = true,
                            enabled = !state.isWorking,
                        )
                        Text(
                            text = stringResource(R.string.kaze_mount_nomount_uid_hint),
                            style = textStyles.body2,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                text = stringResource(R.string.kaze_mount_nomount_save_uids),
                                onClick = { actions.onSaveUids(uidInput) },
                                enabled = !state.isWorking,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = stringResource(R.string.kaze_mount_nomount_clear),
                                onClick = actions.onClearRules,
                                enabled = !state.isWorking,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.kaze_mount_nomount_isolated),
                        summary = stringResource(R.string.kaze_mount_nomount_isolated_summary),
                        checked = state.uidState.isolatedBlocked,
                        enabled = !state.isWorking,
                        onCheckedChange = actions.onIsolatedChange,
                    )
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.kaze_mount_now),
                        summary = stringResource(R.string.kaze_mount_provider_summary),
                        enabled = status.available && !state.isWorking,
                        onClick = actions.onMountNow,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
