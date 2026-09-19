package com.originsu.manager.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.domain.model.VeilKind
import com.originsu.manager.domain.model.veilTimestampText
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.component.settings.lazySegmentColumn
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.VeilUiAction
import com.originsu.manager.ui.viewmodel.VeilUiEvent
import com.originsu.manager.ui.viewmodel.VeilViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VeilScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<VeilViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val pullToRefreshState = rememberPullToRefreshState()
    var showOverflow by remember { mutableStateOf(false) }

    val confirmUncloakAllSummary = stringResource(R.string.veil_uncloak_all_summary)
    val confirmClearHistorySummary = stringResource(R.string.veil_clear_history_summary)
    val confirmTitle = stringResource(R.string.confirm_delete)

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset =
            scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is VeilUiEvent.Error ->
                    snackBarHost.showReplacingSnackbar(event.message)
            }
        }
    }

    ActivityResumeEffect {
        viewModel.dispatch(VeilUiAction.Refresh)
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier
                    .blurEffect(),
                title = { Text(stringResource(R.string.veil)) },
                navigationIcon = {
                    val navigator = LocalNavigator.current
                    AppBackButton(
                        onClick = {
                            navigator.pop()
                        }
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                imageVector = Icons.TwoTone.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.veil_uncloak_all)) },
                                onClick = {
                                    showOverflow = false
                                    scope.launch {
                                        val result = confirmDialog.awaitConfirm(
                                            title = confirmTitle,
                                            content = confirmUncloakAllSummary,
                                        )
                                        if (result == ConfirmResult.Confirmed) {
                                            viewModel.dispatch(VeilUiAction.ClearCloaked)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.veil_clear_history)) },
                                onClick = {
                                    showOverflow = false
                                    scope.launch {
                                        val result = confirmDialog.awaitConfirm(
                                            title = confirmTitle,
                                            content = confirmClearHistorySummary,
                                        )
                                        if (result == ConfirmResult.Confirmed) {
                                            viewModel.dispatch(VeilUiAction.ClearHistory)
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    viewModel.dispatch(VeilUiAction.Refresh)
                },
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = uiState.isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 5.dp,
                        start = 0.dp,
                        end = 0.dp,
                        bottom = paddingValues.calculateBottomPadding() + 5.dp,
                    )
                ) {
                    if (uiState.status != "supported") {
                        item {
                            WarningCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                message = when (uiState.status) {
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    else -> stringResource(R.string.veil_unsupported_title)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    item {
                        SegmentedColumn {
                            item {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Visibility,
                                    title = stringResource(R.string.settings_veil),
                                    description = stringResource(R.string.settings_veil_summary),
                                    enabled = uiState.status == "supported",
                                    checked = uiState.enabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.dispatch(VeilUiAction.SetEnabled(enabled))
                                    },
                                )
                            }
                            item {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Shield,
                                    title = stringResource(R.string.veil_auto_cloak),
                                    description = stringResource(R.string.veil_auto_cloak_summary),
                                    enabled = uiState.status == "supported" && uiState.enabled,
                                    checked = uiState.autoCloak,
                                    onCheckedChange = { enabled ->
                                        viewModel.dispatch(VeilUiAction.SetAutoCloak(enabled))
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Text(
                            text = stringResource(R.string.veil_cloaked_apps),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }

                    if (uiState.cloakedUids.isEmpty()) {
                        item {
                            WarningCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                message = stringResource(R.string.veil_no_cloaked_apps),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    lazySegmentColumn(
                        uiState.cloakedUids,
                        key = { _, it -> it.uid }) { _, entry ->
                        val cloakedUids = uiState.cloakedUids.map { it.uid }.toSet()
                        val uncloakTitle = stringResource(R.string.veil_uncloak_action)
                        val uncloakSummary =
                            stringResource(R.string.veil_uncloak_uid_summary, entry.uid)
                        VeilUidRow(
                            uid = entry.uid,
                            userName = entry.userName,
                            cloaked = cloakedUids,
                            onCloak = { viewModel.dispatch(VeilUiAction.CloakUid(entry.uid)) },
                            onUncloak = {
                                scope.launch {
                                    val result = confirmDialog.awaitConfirm(
                                        title = uncloakTitle,
                                        content = uncloakSummary,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        viewModel.dispatch(VeilUiAction.UncloakUid(entry.uid))
                                    }
                                }
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.veil_probe_history),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }

                    if (uiState.history.isEmpty()) {
                        item {
                            WarningCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                message = stringResource(R.string.veil_no_probe_history),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    lazySegmentColumn(
                        uiState.history,
                        key = { _, it -> it.uid }) { _, entry ->
                        val cloakedUids = uiState.cloakedUids.map { it.uid }.toSet()
                        val lastSeen = veilTimestampText(
                            lastNs = entry.lastNs,
                            currentTimeMillis = System.currentTimeMillis(),
                            uptimeMillis = SystemClock.uptimeMillis(),
                        )
                        VeilHistoryRow(
                            uid = entry.uid,
                            userName = entry.userName,
                            icon = entry.kinds.firstOrNull()?.toIcon()
                                ?: Icons.TwoTone.Info,
                            kinds = entry.kinds.map { it.toLabel() },
                            summary = if (lastSeen != null) {
                                stringResource(
                                    R.string.veil_probe_count_at,
                                    entry.count,
                                    lastSeen,
                                )
                            } else {
                                stringResource(R.string.veil_probe_count, entry.count)
                            },
                            cloaked = cloakedUids,
                            onCloak = { viewModel.dispatch(VeilUiAction.CloakUid(entry.uid)) },
                            onUncloak = {
                                viewModel.dispatch(VeilUiAction.UncloakUid(entry.uid))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VeilUidRow(
    uid: Int,
    userName: String?,
    cloaked: Set<Int>,
    onCloak: () -> Unit,
    onUncloak: () -> Unit,
) {
    val isCloaked = cloaked.contains(uid)
    SettingsBaseWidget(
        icon = Icons.TwoTone.Shield,
        title = stringResource(R.string.veil_uid_title, uid),
        description = userName,
    ) {
        if (isCloaked) {
            IconButton(onClick = onUncloak) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = stringResource(R.string.veil_uncloak_action),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            IconButton(onClick = onCloak) {
                Icon(
                    imageVector = Icons.TwoTone.Shield,
                    contentDescription = stringResource(R.string.veil_cloak_action),
                )
            }
        }
    }
}

@Composable
private fun VeilHistoryRow(
    uid: Int,
    userName: String?,
    icon: ImageVector,
    kinds: List<String>,
    summary: String,
    cloaked: Set<Int>,
    onCloak: () -> Unit,
    onUncloak: () -> Unit,
) {
    val isCloaked = cloaked.contains(uid)
    SettingsBaseWidget(
        icon = icon,
        title = userName?.let { "$it · UID $uid" } ?: stringResource(R.string.veil_uid_title, uid),
        descriptionColumnContent = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
            ) {
                kinds.forEach { kind ->
                    LabelText(label = kind)
                }
                if (isCloaked) {
                    LabelText(
                        label = stringResource(R.string.veil_cloaked),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }
        }
    ) {
        if (!isCloaked) {
            IconButton(onClick = onCloak) {
                Icon(
                    imageVector = Icons.TwoTone.Shield,
                    contentDescription = stringResource(R.string.veil_cloak_action),
                )
            }
        }
    }
}

@Composable
private fun VeilKind.toIcon(): ImageVector = when (this) {
    VeilKind.Su, VeilKind.SuExec, VeilKind.Busybox -> Icons.TwoTone.Terminal
    VeilKind.Magisk, VeilKind.Ksu -> Icons.TwoTone.Security
    VeilKind.Modules, VeilKind.PkgList -> Icons.TwoTone.Apps
    VeilKind.Unknown -> Icons.TwoTone.Info
}

@Composable
private fun VeilKind.toLabel(): String = when (this) {
    VeilKind.Su -> stringResource(R.string.veil_kind_su)
    VeilKind.Magisk -> stringResource(R.string.veil_kind_magisk)
    VeilKind.Ksu -> stringResource(R.string.veil_kind_ksu)
    VeilKind.Modules -> stringResource(R.string.veil_kind_modules)
    VeilKind.PkgList -> stringResource(R.string.veil_kind_pkglist)
    VeilKind.Busybox -> stringResource(R.string.veil_kind_busybox)
    VeilKind.SuExec -> stringResource(R.string.veil_kind_su_exec)
    VeilKind.Unknown -> stringResource(R.string.veil_kind_unknown)
}
