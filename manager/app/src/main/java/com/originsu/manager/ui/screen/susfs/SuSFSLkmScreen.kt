package com.originsu.manager.ui.screen.susfs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.domain.model.SuSFSLkmCheck
import com.originsu.manager.domain.model.SuSFSLkmCheckResult
import com.originsu.manager.domain.model.SuSFSLkmCheckState
import com.originsu.manager.domain.model.buildSuSFSLkmDiagnosticsReport
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.DialogHandle
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberCustomDialog
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseDialog
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.SuSFSLkmUiAction
import com.originsu.manager.ui.viewmodel.SuSFSLkmUiEvent
import com.originsu.manager.ui.viewmodel.SuSFSLkmViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuSFSLkmScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<SuSFSLkmViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullToRefreshState = rememberPullToRefreshState()
    val loadingDialog = rememberLoadingDialog()
    val removeConfirmDialog = rememberConfirmDialog()
    val loadConfirmDialog = rememberConfirmDialog()
    val autoloadConfirmDialog = rememberConfirmDialog()
    val safetyConfirmDialog = rememberConfirmDialog()
    val coroutineScope = rememberCoroutineScope()
    var selectedKmi by rememberSaveable { mutableStateOf<String?>(null) }

    val status = uiState.status
    val currentKmi = status?.currentKmi.orEmpty().trim()
    val resolvedKmi = selectedKmi?.trim()?.takeIf { it.isNotEmpty() }
        ?: currentKmi.takeIf { it.isNotEmpty() }
    val pickerKmis = remember(status?.supportedKmis, currentKmi) {
        status?.supportedKmis.orEmpty().ifEmpty {
            listOfNotNull(currentKmi.takeIf { it.isNotEmpty() })
        }
    }
    val kmiDialog = rememberLkmKmiDialog(
        kmis = pickerKmis,
        detectedKmi = currentKmi,
        selectedKmi = resolvedKmi,
        onSelected = { selectedKmi = it },
    )
    val selectedKmiSupported = status?.supportsKmi(resolvedKmi) != false
    val canLoad = status?.isArm64 == true && selectedKmiSupported && !uiState.isWorking
    val preflight = status?.preflight(resolvedKmi).orEmpty()

    val loadSuccessMsg = stringResource(R.string.susfs_lkm_load_success)
    val loadFailedMsg = stringResource(R.string.susfs_lkm_load_failed)
    val autoloadEnabledMsg = stringResource(R.string.susfs_lkm_autoload_enable_success)
    val autoloadDisabledMsg = stringResource(R.string.susfs_lkm_autoload_disable_success)
    val autoloadUpdatedMsg = stringResource(R.string.susfs_lkm_autoload_update_success)
    val autoloadFailedMsg = stringResource(R.string.susfs_lkm_autoload_failed)
    val removeSuccessMsg = stringResource(R.string.susfs_lkm_remove_success)
    val removeFailedMsg = stringResource(R.string.susfs_lkm_remove_failed)
    val removeConfirmTitle = stringResource(R.string.susfs_lkm_remove_confirm_title)
    val removeConfirmMessage = stringResource(R.string.susfs_lkm_remove_confirm_message)
    val removeConfirmAction = stringResource(R.string.susfs_lkm_remove_confirm_action)
    val loadConfirmTitle = stringResource(R.string.susfs_lkm_load_confirm_title)
    val loadConfirmMessage = stringResource(R.string.susfs_lkm_load_confirm_message)
    val loadConfirmAction = stringResource(R.string.susfs_lkm_load_confirm_action)
    val autoloadEnableTitle = stringResource(R.string.susfs_lkm_autoload_enable_confirm_title)
    val autoloadEnableMessage = stringResource(
        R.string.susfs_lkm_autoload_enable_confirm_message
    )
    val autoloadEnableAction = stringResource(R.string.susfs_lkm_autoload_enable_confirm_action)
    val autoloadDisableTitle = stringResource(R.string.susfs_lkm_autoload_disable_confirm_title)
    val autoloadDisableMessage = stringResource(
        R.string.susfs_lkm_autoload_disable_confirm_message
    )
    val autoloadDisableAction = stringResource(R.string.susfs_lkm_autoload_disable_confirm_action)
    val autoloadUpdateTitle = stringResource(R.string.susfs_lkm_autoload_update_confirm_title)
    val autoloadUpdateMessage = stringResource(R.string.susfs_lkm_autoload_update_confirm_message)
    val autoloadUpdateAction = stringResource(R.string.susfs_lkm_autoload_update_confirm_action)
    val safetyResetSuccess = stringResource(R.string.susfs_lkm_safety_reset_success)
    val safetyResetFailed = stringResource(R.string.susfs_lkm_safety_reset_failed)
    val safetyResetTitle = stringResource(R.string.susfs_lkm_safety_reset_confirm_title)
    val safetyResetMessage = stringResource(R.string.susfs_lkm_safety_reset_confirm_message)
    val safetyResetAction = stringResource(R.string.susfs_lkm_safety_reset_confirm_action)
    val cancel = stringResource(R.string.cancel)
    val refreshFailedMsg = stringResource(R.string.susfs_lkm_refresh_failed)
    val noData = stringResource(R.string.susfs_status_no_data)
    val unknownKmi = stringResource(R.string.susfs_lkm_kmi_unknown)
    val unsupportedKmi = stringResource(R.string.susfs_lkm_kmi_unavailable)
    val noGuardAssets = stringResource(R.string.susfs_lkm_available_kmis_none)
    val diagnosticsCopied = stringResource(R.string.susfs_lkm_diagnostics_copied)
    val diagnosticsLabel = stringResource(R.string.susfs_lkm_diagnostics_section)
    val loadedAtText = remember(status?.loadedAt) {
        status?.loadedAt
            ?.takeIf { it > 0 }
            ?.let { seconds ->
                java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT,
                ).format(Date(seconds * 1000))
            }
            .orEmpty()
    }
    val assetSizeText = remember(status?.assetSize) {
        status?.assetSize
            ?.takeIf { it > 0 }
            ?.let { Formatter.formatShortFileSize(context, it) }
            .orEmpty()
    }
    val lastCheckedText = remember(uiState.lastCheckedAt) {
        uiState.lastCheckedAt
            ?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
            .orEmpty()
    }
    val lastAction = uiState.lastAction

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(uiState.isWorking) {
        if (uiState.isWorking) loadingDialog.showLoading() else loadingDialog.hide()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SuSFSLkmUiEvent.LoadSucceeded ->
                    snackBarHost.showReplacingSnackbar(loadSuccessMsg)

                is SuSFSLkmUiEvent.LoadFailed ->
                    snackBarHost.showReplacingSnackbar(
                        event.message.ifBlank { loadFailedMsg }
                    )

                is SuSFSLkmUiEvent.AutoloadEnabled ->
                    snackBarHost.showReplacingSnackbar(autoloadEnabledMsg)

                is SuSFSLkmUiEvent.AutoloadDisabled ->
                    snackBarHost.showReplacingSnackbar(autoloadDisabledMsg)

                is SuSFSLkmUiEvent.AutoloadUpdated ->
                    snackBarHost.showReplacingSnackbar(autoloadUpdatedMsg)

                is SuSFSLkmUiEvent.AutoloadFailed ->
                    snackBarHost.showReplacingSnackbar(
                        event.message.ifBlank { autoloadFailedMsg }
                    )

                is SuSFSLkmUiEvent.SafetyReset ->
                    snackBarHost.showReplacingSnackbar(safetyResetSuccess)

                is SuSFSLkmUiEvent.SafetyResetFailed ->
                    snackBarHost.showReplacingSnackbar(
                        event.message.ifBlank { safetyResetFailed }
                    )

                is SuSFSLkmUiEvent.RemoveSucceeded ->
                    snackBarHost.showReplacingSnackbar(removeSuccessMsg)

                is SuSFSLkmUiEvent.RemoveFailed ->
                    snackBarHost.showReplacingSnackbar(
                        event.message.ifBlank { removeFailedMsg }
                    )

                is SuSFSLkmUiEvent.RefreshFailed ->
                    snackBarHost.showReplacingSnackbar(refreshFailedMsg)
            }
        }
    }

    ActivityResumeEffect {
        viewModel.dispatch(SuSFSLkmUiAction.Refresh)
    }

    val statusTitle = when {
        uiState.isLoading && status == null -> stringResource(R.string.susfs_lkm_checking)
        status == null -> stringResource(R.string.susfs_lkm_status_unknown)
        status.isBuiltIn -> stringResource(R.string.susfs_lkm_builtin)
        status.isLkm -> stringResource(R.string.susfs_lkm_loaded)
        else -> stringResource(R.string.susfs_lkm_not_loaded)
    }
    val statusDescription = when {
        uiState.isLoading && status == null ->
            stringResource(R.string.susfs_lkm_checking_summary)

        status == null -> stringResource(R.string.susfs_lkm_status_unknown_summary)
        status.isBuiltIn -> stringResource(R.string.susfs_lkm_builtin_summary)
        status.isLkm -> stringResource(R.string.susfs_lkm_loaded_summary)
        else -> stringResource(R.string.susfs_lkm_not_loaded_summary)
    }
    val statusIcon = when {
        status?.isLoaded == true -> Icons.TwoTone.CheckCircle
        status == null && !uiState.isLoading -> Icons.TwoTone.Error
        else -> Icons.TwoTone.Extension
    }
    val autoloadSummary = status?.let { currentStatus ->
        if (currentStatus.autoloadEnabled) {
            val autoloadKmi = currentStatus.autoloadKmi.ifBlank { currentStatus.currentKmi }
            if (autoloadKmi.isNotBlank()) {
                stringResource(R.string.susfs_lkm_autoload_enabled, autoloadKmi)
            } else {
                stringResource(R.string.susfs_lkm_autoload_enabled_default)
            }
        } else {
            stringResource(R.string.susfs_lkm_autoload_disabled)
        }
    } ?: stringResource(R.string.susfs_lkm_autoload_disabled)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.susfs_lkm_title)) },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.dispatch(SuSFSLkmUiAction.Refresh) },
                        enabled = !uiState.isWorking,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = stringResource(R.string.susfs_lkm_refresh),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                        },
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                        },
                ),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.dispatch(SuSFSLkmUiAction.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
            item {
                SegmentedColumn(title = stringResource(R.string.susfs_lkm_status_section)) {
                    item {
                        SettingsBaseWidget(
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                            icon = statusIcon,
                            title = statusTitle,
                            description = statusDescription,
                            isError = status != null && !status.isLoaded,
                            selected = status?.isLoaded == true,
                        )
                    }
                    item(visible = status?.isLoaded == true) {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Info,
                            title = stringResource(R.string.susfs_status_version),
                            description = status?.version?.ifBlank { noData } ?: noData,
                        )
                    }
                    item(visible = status?.isLoaded == true) {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Settings,
                            title = stringResource(R.string.susfs_status_variant),
                            description = status?.variant?.ifBlank { noData } ?: noData,
                        )
                    }
                    item(visible = lastCheckedText.isNotBlank()) {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Refresh,
                            title = stringResource(R.string.susfs_lkm_last_checked),
                            description = lastCheckedText,
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.susfs_lkm_kmi_section)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Memory,
                            title = stringResource(R.string.susfs_lkm_current_kmi),
                            description = status?.currentKmi?.ifBlank { unknownKmi } ?: unknownKmi,
                        )
                    }
                    item(visible = status != null && !status.isBuiltIn) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Memory,
                            title = stringResource(R.string.susfs_lkm_load_kmi),
                            description = when {
                                resolvedKmi == null -> unknownKmi
                                !selectedKmiSupported -> unsupportedKmi
                                else -> resolvedKmi
                            },
                            enabled = !uiState.isWorking && pickerKmis.isNotEmpty(),
                            isError = !selectedKmiSupported,
                            onClick = { kmiDialog.show() },
                        )
                    }
                    item(visible = status != null && !status.isBuiltIn) {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Extension,
                            title = stringResource(R.string.susfs_lkm_available_kmis),
                            description = if (pickerKmis.isEmpty()) {
                                noGuardAssets
                            } else {
                                pickerKmis.joinToString(", ")
                            },
                            isError = pickerKmis.isEmpty(),
                        )
                    }
                }
            }

            if (status != null && !status.isBuiltIn) {
                item {
                    SegmentedColumn(
                        title = stringResource(R.string.susfs_lkm_preflight_section)
                    ) {
                        preflight.forEach { result ->
                            item {
                                PreflightRow(
                                    result = result,
                                    targetKmi = resolvedKmi.orEmpty(),
                                )
                            }
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.susfs_lkm_actions_section)) {
                    item(visible = status != null && !status.isLoaded) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Download,
                            title = stringResource(R.string.susfs_lkm_load_now),
                            description = if (resolvedKmi != null) {
                                if (selectedKmiSupported) {
                                    stringResource(R.string.susfs_lkm_load_now_summary, resolvedKmi)
                                } else {
                                    unsupportedKmi
                                }
                            } else {
                                stringResource(R.string.susfs_lkm_load_now_no_kmi)
                            },
                            enabled = canLoad,
                            isError = !selectedKmiSupported,
                            onClick = {
                                coroutineScope.launch {
                                    val result = loadConfirmDialog.awaitConfirm(
                                        title = loadConfirmTitle,
                                        content = loadConfirmMessage,
                                        confirm = loadConfirmAction,
                                        dismiss = cancel,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        viewModel.dispatch(
                                            SuSFSLkmUiAction.Load(resolvedKmi)
                                        )
                                    }
                                }
                            },
                        )
                    }
                    item(visible = status != null && !status.isBuiltIn) {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Sync,
                            title = stringResource(R.string.susfs_lkm_autoload),
                            description = autoloadSummary,
                            checked = status?.autoloadEnabled == true,
                            enabled = status?.isArm64 == true && selectedKmiSupported &&
                                !uiState.isWorking,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    val result = autoloadConfirmDialog.awaitConfirm(
                                        title = if (enabled) {
                                            autoloadEnableTitle
                                        } else {
                                            autoloadDisableTitle
                                        },
                                        content = if (enabled) {
                                            autoloadEnableMessage
                                        } else {
                                            autoloadDisableMessage
                                        },
                                        confirm = if (enabled) {
                                            autoloadEnableAction
                                        } else {
                                            autoloadDisableAction
                                        },
                                        dismiss = cancel,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        viewModel.dispatch(
                                            SuSFSLkmUiAction.SetAutoload(
                                                enabled,
                                                resolvedKmi,
                                            )
                                        )
                                    }
                                }
                            },
                        )
                    }
                    item(
                        visible = status?.autoloadEnabled == true &&
                            resolvedKmi != null &&
                            !resolvedKmi.equals(status.autoloadKmi, ignoreCase = true)
                    ) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Sync,
                            title = stringResource(R.string.susfs_lkm_autoload_update),
                            description = if (selectedKmiSupported) {
                                stringResource(
                                    R.string.susfs_lkm_autoload_update_summary,
                                    resolvedKmi.orEmpty(),
                                )
                            } else {
                                unsupportedKmi
                            },
                            enabled = canLoad,
                            isError = !selectedKmiSupported,
                            onClick = {
                                coroutineScope.launch {
                                    val result = autoloadConfirmDialog.awaitConfirm(
                                        title = autoloadUpdateTitle,
                                        content = autoloadUpdateMessage,
                                        confirm = autoloadUpdateAction,
                                        dismiss = cancel,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        viewModel.dispatch(
                                            SuSFSLkmUiAction.UpdateAutoloadKmi(resolvedKmi)
                                        )
                                    }
                                }
                            },
                        )
                    }
                    item(visible = status == null && !uiState.isLoading) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Refresh,
                            title = stringResource(R.string.susfs_lkm_retry),
                            description = stringResource(R.string.susfs_lkm_retry_summary),
                            onClick = { viewModel.dispatch(SuSFSLkmUiAction.Refresh) },
                        )
                    }
                    item(visible = status?.isLoaded == true) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Settings,
                            title = stringResource(R.string.susfs_lkm_open_manager),
                            description = stringResource(R.string.susfs_lkm_open_manager_summary),
                            onClick = { navigator.push(Route.SuSFSConfig) },
                        )
                    }
                    item(visible = status?.isLkm == true) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Delete,
                            title = stringResource(R.string.susfs_lkm_remove_temp),
                            description = stringResource(R.string.susfs_lkm_remove_temp_summary),
                            enabled = !uiState.isWorking,
                            onClick = {
                                coroutineScope.launch {
                                    val result = removeConfirmDialog.awaitConfirm(
                                        title = removeConfirmTitle,
                                        content = removeConfirmMessage,
                                        confirm = removeConfirmAction,
                                        dismiss = cancel,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        viewModel.dispatch(
                                            SuSFSLkmUiAction.RemoveForThisBoot
                                        )
                                    }
                                }
                            },
                        )
                    }
                    item(visible = status?.autoloadDisabledReason?.isNotBlank() == true) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Warning,
                            title = stringResource(R.string.susfs_lkm_reset_safety),
                            description = stringResource(R.string.susfs_lkm_reset_safety_summary),
                            enabled = !uiState.isWorking,
                            isError = true,
                            onClick = {
                                coroutineScope.launch {
                                    val result = safetyConfirmDialog.awaitConfirm(
                                        title = safetyResetTitle,
                                        content = safetyResetMessage,
                                        confirm = safetyResetAction,
                                        dismiss = cancel,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        viewModel.dispatch(
                                            SuSFSLkmUiAction.ResetAutoloadSafety
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            if (status != null && !status.isArm64 && !status.isBuiltIn) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.susfs_lkm_unsupported_summary),
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Warning,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            if (status != null && !status.isBuiltIn && !selectedKmiSupported) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = unsupportedKmi,
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Warning,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            if (status != null && status.autoloadDisabledReason.isNotBlank()) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = if (status.autoloadAttempts > 0) {
                            stringResource(
                                R.string.susfs_lkm_bootloop_summary,
                                status.autoloadAttempts,
                            )
                        } else {
                            stringResource(R.string.susfs_lkm_bootloop_summary_generic)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Warning,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            if (status != null && status.isLkm) {
                item {
                    SegmentedColumn(
                        title = stringResource(R.string.susfs_lkm_loaded_module_section)
                    ) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Memory,
                                title = stringResource(R.string.susfs_lkm_loaded_kmi),
                                description = status.loadedKmi.ifBlank {
                                    status.currentKmi
                                }.ifBlank { noData },
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Extension,
                                title = stringResource(R.string.susfs_lkm_asset_name),
                                description = status.assetName.ifBlank { noData },
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Security,
                                title = stringResource(R.string.susfs_lkm_asset_hash),
                                description = status.assetSha256.ifBlank { noData },
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.susfs_lkm_asset_size),
                                description = assetSizeText.ifBlank { noData },
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.susfs_lkm_loaded_at),
                                description = loadedAtText.ifBlank { noData },
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.susfs_lkm_diagnostics_section)) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.ContentCopy,
                            title = stringResource(R.string.susfs_lkm_copy_diagnostics),
                            description = stringResource(
                                R.string.susfs_lkm_copy_diagnostics_summary
                            ),
                            onClick = {
                                val report = buildSuSFSLkmDiagnosticsReport(status, lastAction)
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(diagnosticsLabel, report)
                                )
                                coroutineScope.launch {
                                    snackBarHost.showReplacingSnackbar(diagnosticsCopied)
                                }
                            },
                        )
                    }
                    if (lastAction != null) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.susfs_lkm_last_command),
                                description = lastAction.command.ifBlank { noData },
                                isError = !lastAction.success,
                            )
                        }
                        if (lastAction.stdout.isNotBlank()) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Info,
                                    title = stringResource(R.string.susfs_lkm_command_stdout),
                                    description = lastAction.stdout,
                                    selected = lastAction.success,
                                )
                            }
                        }
                        if (lastAction.stderr.isNotBlank()) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Error,
                                    title = stringResource(R.string.susfs_lkm_command_stderr),
                                    description = lastAction.stderr,
                                    isError = true,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.susfs_lkm_notes_section)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Security,
                            title = stringResource(R.string.susfs_lkm_compat_title),
                            description = stringResource(R.string.susfs_lkm_compat_summary),
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Warning,
                            title = stringResource(R.string.susfs_lkm_safety_title),
                            description = stringResource(R.string.susfs_lkm_safety_summary),
                            isError = status?.isLkm == true,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PreflightRow(
    result: SuSFSLkmCheckResult,
    targetKmi: String,
) {
    val unknownKmi = stringResource(R.string.susfs_lkm_kmi_unknown)
    val checkTitle = when (result.check) {
        SuSFSLkmCheck.Arm64 -> stringResource(R.string.susfs_lkm_check_arm64)
        SuSFSLkmCheck.LkmMode -> stringResource(R.string.susfs_lkm_check_lkm_mode)
        SuSFSLkmCheck.Root -> stringResource(R.string.susfs_lkm_check_root)
        SuSFSLkmCheck.Kmi -> stringResource(R.string.susfs_lkm_check_kmi)
        SuSFSLkmCheck.GuardAsset -> stringResource(R.string.susfs_lkm_check_asset)
        SuSFSLkmCheck.Active -> stringResource(R.string.susfs_lkm_check_active)
    }
    val checkDescription = when (result.check) {
        SuSFSLkmCheck.Arm64 -> stringResource(
            if (result.state == SuSFSLkmCheckState.Ok) {
                R.string.susfs_lkm_check_arm64_ok
            } else {
                R.string.susfs_lkm_check_arm64_error
            }
        )

        SuSFSLkmCheck.LkmMode -> stringResource(
            if (result.state == SuSFSLkmCheckState.Ok) {
                R.string.susfs_lkm_check_lkm_mode_ok
            } else {
                R.string.susfs_lkm_check_lkm_mode_error
            }
        )

        SuSFSLkmCheck.Root -> stringResource(
            if (result.state == SuSFSLkmCheckState.Ok) {
                R.string.susfs_lkm_check_root_ok
            } else {
                R.string.susfs_lkm_check_root_error
            }
        )

        SuSFSLkmCheck.Kmi -> if (result.state == SuSFSLkmCheckState.Ok) {
            stringResource(R.string.susfs_lkm_check_kmi_ok, targetKmi)
        } else {
            stringResource(R.string.susfs_lkm_check_kmi_error)
        }

        SuSFSLkmCheck.GuardAsset -> when (result.state) {
            SuSFSLkmCheckState.Ok -> stringResource(
                R.string.susfs_lkm_check_asset_ok,
                targetKmi,
            )

            SuSFSLkmCheckState.Unknown -> stringResource(
                R.string.susfs_lkm_check_asset_unknown
            )

            else -> stringResource(
                R.string.susfs_lkm_check_asset_error,
                targetKmi.ifBlank { unknownKmi },
            )
        }

        SuSFSLkmCheck.Active -> stringResource(
            if (result.state == SuSFSLkmCheckState.Ok) {
                R.string.susfs_lkm_check_active_ok
            } else {
                R.string.susfs_lkm_check_active_warning
            }
        )
    }
    val checkIcon = when (result.state) {
        SuSFSLkmCheckState.Ok -> Icons.TwoTone.CheckCircle
        SuSFSLkmCheckState.Warning -> Icons.TwoTone.Warning
        SuSFSLkmCheckState.Error -> Icons.TwoTone.Error
        SuSFSLkmCheckState.Unknown -> Icons.TwoTone.Info
    }
    val stateLabel = stringResource(
        when (result.state) {
            SuSFSLkmCheckState.Ok -> R.string.susfs_lkm_check_state_ok
            SuSFSLkmCheckState.Warning -> R.string.susfs_lkm_check_state_warning
            SuSFSLkmCheckState.Error -> R.string.susfs_lkm_check_state_error
            SuSFSLkmCheckState.Unknown -> R.string.susfs_lkm_check_state_unknown
        }
    )

    SettingsBaseWidget(
        icon = checkIcon,
        title = checkTitle,
        description = checkDescription,
        isError = result.state == SuSFSLkmCheckState.Error,
        selected = result.state == SuSFSLkmCheckState.Ok,
        descriptionColumnContent = {
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberLkmKmiDialog(
    kmis: List<String>,
    detectedKmi: String,
    selectedKmi: String?,
    onSelected: (String) -> Unit,
): DialogHandle {
    return rememberCustomDialog { dismiss ->
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.surfaceBright,
            )
        ) {
            val detectedLabel = stringResource(R.string.susfs_lkm_kmi_detected)
            SettingsChooseDialog(
                show = true,
                title = stringResource(R.string.susfs_lkm_kmi_picker_title),
                items = kmis,
                itemDescriptions = kmis.map { kmi ->
                    if (kmi.equals(detectedKmi, ignoreCase = true)) {
                        detectedLabel
                    } else {
                        null
                    }
                },
                selectedIndex = kmis.indexOfFirst {
                    it.equals(selectedKmi, ignoreCase = true)
                },
                onDismiss = dismiss,
                onSelectedIndexChange = { index ->
                    kmis.getOrNull(index)?.let(onSelected)
                },
            )
        }
    }
}
