package com.originsu.manager.ui.screen.susfs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.component.settings.SettingsTextFieldWidget
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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuSFSLkmScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<SuSFSLkmViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val loadingDialog = rememberLoadingDialog()
    val kmiState = rememberTextFieldState()
    var kmiInitialized by remember { mutableStateOf(false) }

    val status = uiState.status
    val resolvedKmi = kmiState.text.toString().trim()
        .ifBlank { status?.currentKmi.orEmpty().trim() }
        .takeIf { it.isNotEmpty() }

    val loadSuccessMsg = stringResource(R.string.susfs_lkm_load_success)
    val loadFailedMsg = stringResource(R.string.susfs_lkm_load_failed)
    val autoloadEnabledMsg = stringResource(R.string.susfs_lkm_autoload_enable_success)
    val autoloadDisabledMsg = stringResource(R.string.susfs_lkm_autoload_disable_success)
    val autoloadFailedMsg = stringResource(R.string.susfs_lkm_autoload_failed)
    val refreshFailedMsg = stringResource(R.string.susfs_lkm_refresh_failed)
    val noData = stringResource(R.string.susfs_status_no_data)
    val unknownKmi = stringResource(R.string.susfs_lkm_kmi_unknown)

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(status?.currentKmi) {
        val detectedKmi = status?.currentKmi.orEmpty()
        if (!kmiInitialized && detectedKmi.isNotBlank()) {
            kmiState.setTextAndPlaceCursorAtEnd(detectedKmi)
            kmiInitialized = true
        }
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

                is SuSFSLkmUiEvent.AutoloadFailed ->
                    snackBarHost.showReplacingSnackbar(
                        event.message.ifBlank { autoloadFailedMsg }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item {
                SegmentedColumn(title = stringResource(R.string.susfs_lkm_status_section)) {
                    item {
                        SettingsBaseWidget(
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
                        SettingsTextFieldWidget(
                            state = kmiState,
                            title = stringResource(R.string.susfs_lkm_kmi_override),
                            useLabelAsPlaceholder = true,
                            enabled = !uiState.isWorking && status?.isArm64 != false,
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
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
                                stringResource(R.string.susfs_lkm_load_now_summary, resolvedKmi)
                            } else {
                                stringResource(R.string.susfs_lkm_load_now_no_kmi)
                            },
                            enabled = status?.isArm64 == true && !uiState.isWorking,
                            onClick = {
                                viewModel.dispatch(SuSFSLkmUiAction.Load(resolvedKmi))
                            },
                        )
                    }
                    item(visible = status != null && !status.isBuiltIn) {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Sync,
                            title = stringResource(R.string.susfs_lkm_autoload),
                            description = autoloadSummary,
                            checked = status?.autoloadEnabled == true,
                            enabled = status?.isArm64 == true && !uiState.isWorking,
                            onCheckedChange = { enabled ->
                                viewModel.dispatch(
                                    SuSFSLkmUiAction.SetAutoload(enabled, resolvedKmi)
                                )
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
