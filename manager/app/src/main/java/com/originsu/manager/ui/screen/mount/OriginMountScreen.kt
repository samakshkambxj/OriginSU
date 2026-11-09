package com.originsu.manager.ui.screen.mount

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Dashboard
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.domain.model.InstalledPackageInfo
import com.originsu.manager.domain.model.OriginMountMode
import com.originsu.manager.domain.model.OriginMountNoMountStatus
import com.originsu.manager.domain.model.OriginMountRuntime
import com.originsu.manager.domain.model.OriginMountStatus
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberCustomDialog
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.popupBlur
import com.originsu.manager.ui.component.popupContainerColor
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.BentoCard
import com.originsu.manager.ui.component.settings.BentoStatusChip
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.OriginMountUiAction
import com.originsu.manager.ui.viewmodel.OriginMountUiEvent
import com.originsu.manager.ui.viewmodel.OriginMountViewModel
import com.originsu.manager.ui.webui.WebUIActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OriginMountScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<OriginMountViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullToRefreshState = rememberPullToRefreshState()
    val loadingDialog = rememberLoadingDialog()
    val clearDialog = rememberConfirmDialog()
    val coroutineScope = rememberCoroutineScope()
    val uidDialog = rememberCustomDialog { dismiss ->
        NomountUidDialog(
            apps = uiState.apps,
            loading = uiState.loadingApps,
            stored = uiState.uidState.stored,
            onSave = { uids ->
                viewModel.dispatch(OriginMountUiAction.SetUids(uids))
                dismiss()
            },
            onDismiss = dismiss,
        )
    }

    val status = uiState.status
    val mode = status?.mode ?: OriginMountMode.Auto
    val nomount = status?.nomount
    val runtime = status?.runtime
    val knownValue = stringResource(R.string.origin_mount_value_unknown)

    val modeOptions = OriginMountMode.entries
    val modeLabels = listOf(
        stringResource(R.string.origin_mount_mode_auto),
        stringResource(R.string.origin_mount_mode_builtin),
        stringResource(R.string.origin_mount_mode_metamodule),
        stringResource(R.string.origin_mount_mode_zeromount),
        stringResource(R.string.origin_mount_mode_nomount),
        stringResource(R.string.origin_mount_mode_off),
    )
    val modeSummary = when (mode) {
        OriginMountMode.Auto -> stringResource(R.string.origin_mount_mode_summary_auto)
        OriginMountMode.Builtin -> stringResource(R.string.origin_mount_mode_summary_builtin)
        OriginMountMode.Metamodule -> stringResource(R.string.origin_mount_mode_summary_metamodule)
        OriginMountMode.Zeromount -> stringResource(R.string.origin_mount_mode_summary_zeromount)
        OriginMountMode.Nomount -> stringResource(R.string.origin_mount_mode_summary_nomount)
        OriginMountMode.Off -> stringResource(R.string.origin_mount_mode_summary_off)
    }
    val resolvedProviderLabel = providerLabel(status?.provider.orEmpty(), knownValue)
    val metamoduleOwnsMounting = status?.provider == "metamodule" ||
        status?.provider == "zeromount"
    val magicMountHandoffOwner = status?.metamoduleId
        ?.takeIf { it.isNotBlank() }
        ?: resolvedProviderLabel
    // Magic Mount only runs for Auto/Builtin, or as the NoMount fallback. The
    // enable flag only influences Auto; Builtin forces it on and Nomount only
    // uses the engine when its own provider fails.
    val showMagicMountSection = mode == OriginMountMode.Auto ||
        mode == OriginMountMode.Builtin ||
        mode == OriginMountMode.Nomount ||
        status?.provider == "builtin"
    val showMagicMountSwitch = mode == OriginMountMode.Auto ||
        mode == OriginMountMode.Builtin
    val magicMountForced = mode == OriginMountMode.Builtin
    val magicMountFallbackOnly = mode == OriginMountMode.Nomount
    val runtimeText = runtimeSummary(runtime, status, knownValue)
    val nomountLabel = stringResource(R.string.origin_mount_provider_nomount)
    val zeromountLabel = stringResource(R.string.origin_mount_provider_zeromount)
    val kernelLabels = status?.kernelEngines.orEmpty().map { engine ->
        when (engine) {
            "nomount" -> nomountLabel
            "zeromount" -> zeromountLabel
            "kasumi" -> "Kasumi"
            else -> engine
        }
    }
    val kernelText = if (kernelLabels.isEmpty()) {
        stringResource(R.string.origin_mount_kernel_none)
    } else {
        kernelLabels.joinToString(", ")
    }
    val driverText = if (nomount != null) {
        nomountDriverSummary(nomount, knownValue)
    } else {
        knownValue
    }
    val rulesText = if (nomount != null) {
        stringResource(
            R.string.origin_mount_nomount_rules_summary,
            nomount.rulesApplied,
            nomount.rulesExpected,
        )
    } else {
        knownValue
    }
    val exclusionsText = stringResource(
        R.string.origin_mount_nomount_uids_summary,
        uiState.uidState.stored.size,
    )
    val runtimeFailed = runtime?.result == "failed"
    val modeLabel = modeLabels[modeOptions.indexOf(mode).coerceAtLeast(0)]
    val backendLabel = when (status?.backend) {
        "overlay" -> stringResource(R.string.magic_mount_backend_overlay)
        "bind" -> stringResource(R.string.magic_mount_backend_bind)
        else -> stringResource(R.string.magic_mount_backend_auto)
    }
    val runtimeResultLabel = when (runtime?.result) {
        "disabled" -> stringResource(R.string.origin_mount_runtime_disabled)
        "delegated" -> stringResource(R.string.origin_mount_runtime_delegated)
        "mounted" -> stringResource(R.string.origin_mount_runtime_mounted)
        "noop" -> stringResource(R.string.origin_mount_runtime_noop)
        "failed" -> stringResource(R.string.origin_mount_runtime_failed)
        else -> null
    }
    val runtimeChipActive = runtime?.result == "mounted" ||
        runtime?.result == "noop" ||
        runtime?.result == "delegated"
    val runtimeChipWarning = runtime?.result == "failed"
    val nomountUnavailable = nomount != null && !nomount.isReady && !nomount.isDeferred
    val showNomount = nomount != null &&
        (mode == OriginMountMode.Nomount || nomount.isReady || nomount.isDeferred ||
            nomount.ownsInjection)
    val zeromountModuleId = status?.metamoduleId
        ?.takeIf { it == "meta-zeromount" || it == "zeromount" }
    val zeromountActive = status?.provider == "zeromount"
    val showZeromount = mode == OriginMountMode.Zeromount || zeromountActive ||
        zeromountModuleId != null || uiState.zeromountVersion.isNotBlank() ||
        uiState.zeromountDriverPresent
    val zeromountDriverText = if (uiState.zeromountDriverPresent) {
        stringResource(R.string.origin_mount_zeromount_driver_ready)
    } else {
        stringResource(R.string.origin_mount_zeromount_driver_absent)
    }
    val zeromountVersionText = uiState.zeromountVersion.ifBlank { knownValue }
    val zeromountModuleText = zeromountModuleId ?: knownValue
    val zeromountWebUiModuleId = zeromountModuleId ?: "meta-zeromount"

    val refreshFailed = stringResource(R.string.origin_mount_refresh_failed)
    val modeChanged = stringResource(R.string.origin_mount_mode_changed)
    val magicMountChanged = stringResource(R.string.origin_mount_magic_mount_changed)
    val magicMountFailed = stringResource(R.string.magic_mount_failed)
    val mountSuccess = stringResource(R.string.origin_mount_mount_success)
    val mountFailed = stringResource(R.string.origin_mount_mount_failed)
    val uidsUpdated = stringResource(R.string.origin_mount_uids_updated)
    val uidsFailed = stringResource(R.string.origin_mount_uids_failed)
    val actionFailed = stringResource(R.string.origin_mount_action_failed)
    val rulesCleared = stringResource(R.string.origin_mount_nomount_clear_success)
    val clearTitle = stringResource(R.string.origin_mount_nomount_clear_confirm_title)
    val clearMessage = stringResource(R.string.origin_mount_nomount_clear_confirm_message)
    val clearAction = stringResource(R.string.origin_mount_nomount_clear_confirm_action)
    val cancel = stringResource(R.string.cancel)

    ActivityResumeEffect {
        viewModel.dispatch(OriginMountUiAction.Refresh)
    }

    LaunchedEffect(uiState.isWorking) {
        if (uiState.isWorking) loadingDialog.showLoading() else loadingDialog.hide()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                OriginMountUiEvent.RefreshFailed ->
                    snackBarHost.showReplacingSnackbar(refreshFailed)

                OriginMountUiEvent.ModeChanged ->
                    snackBarHost.showReplacingSnackbar(modeChanged)

                OriginMountUiEvent.MagicMountChanged ->
                    snackBarHost.showReplacingSnackbar(magicMountChanged)

                is OriginMountUiEvent.MagicMountFailed ->
                    snackBarHost.showReplacingSnackbar(event.message.ifBlank { magicMountFailed })

                OriginMountUiEvent.MountSucceeded ->
                    snackBarHost.showReplacingSnackbar(mountSuccess)

                is OriginMountUiEvent.MountFailed ->
                    snackBarHost.showReplacingSnackbar(event.message.ifBlank { mountFailed })

                OriginMountUiEvent.UidsUpdated ->
                    snackBarHost.showReplacingSnackbar(uidsUpdated)

                is OriginMountUiEvent.UidsFailed ->
                    snackBarHost.showReplacingSnackbar(event.message.ifBlank { uidsFailed })

                OriginMountUiEvent.RulesCleared ->
                    snackBarHost.showReplacingSnackbar(rulesCleared)

                is OriginMountUiEvent.ActionFailed ->
                    snackBarHost.showReplacingSnackbar(event.message.ifBlank { actionFailed })
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.origin_mount_title)) },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.dispatch(OriginMountUiAction.Refresh) },
                        enabled = !uiState.isWorking,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = stringResource(R.string.origin_mount_refresh),
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
            onRefresh = { viewModel.dispatch(OriginMountUiAction.Refresh) },
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
                item(key = "origin-mount-hero") {
                    OriginMountHeroCard(
                        eyebrow = stringResource(R.string.origin_mount_hero_eyebrow),
                        provider = resolvedProviderLabel,
                        modeSummary = modeSummary,
                        modeLabel = modeLabel,
                        showBackend = showMagicMountSection,
                        backendLabel = backendLabel,
                        backendActive = status?.magicMountEnabled == true,
                        runtimeLabel = runtimeResultLabel ?: knownValue,
                        runtimeDetail = runtimeText,
                        runtimeActive = runtimeChipActive,
                        runtimeWarning = runtimeChipWarning,
                        lastBootLabel = stringResource(R.string.origin_mount_runtime),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                item {
                    SegmentedColumn(title = stringResource(R.string.origin_mount_provider_section)) {
                        item {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Extension,
                                title = stringResource(R.string.origin_mount_mode),
                                description = modeSummary,
                                enabled = !uiState.isWorking,
                                items = modeLabels,
                                selectedIndex = modeOptions.indexOf(mode).coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    modeOptions.getOrNull(index)?.let { selected ->
                                        viewModel.dispatch(OriginMountUiAction.SetMode(selected))
                                    }
                                },
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Dashboard,
                                title = stringResource(R.string.origin_mount_provider),
                                description = resolvedProviderLabel,
                                isError = status != null && status.provider == "none" &&
                                    mode != OriginMountMode.Off,
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.RestartAlt,
                                title = stringResource(R.string.origin_mount_runtime),
                                description = runtimeText,
                                isError = runtimeFailed,
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Memory,
                                title = stringResource(R.string.origin_mount_kernel),
                                description = kernelText,
                            )
                        }
                    }
                }

                if (showMagicMountSection) {
                    item {
                        SegmentedColumn(
                            title = stringResource(R.string.origin_mount_magic_mount_section)
                        ) {
                            item(visible = showMagicMountSwitch) {
                                val magicMountSummary = when {
                                    metamoduleOwnsMounting -> stringResource(
                                        R.string.settings_magic_mount_handoff_summary,
                                        magicMountHandoffOwner,
                                    )

                                    magicMountForced -> stringResource(
                                        R.string.origin_mount_magic_mount_forced_summary
                                    )

                                    else -> stringResource(R.string.settings_magic_mount_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = stringResource(R.string.settings_magic_mount),
                                    description = magicMountSummary,
                                    enabled = !uiState.isWorking &&
                                        !metamoduleOwnsMounting &&
                                        !magicMountForced,
                                    checked = status?.magicMountEnabled == true,
                                    onCheckedChange = { enabled ->
                                        viewModel.dispatch(
                                            OriginMountUiAction.SetMagicMountEnabled(enabled)
                                        )
                                    },
                                )
                            }
                            item {
                                val backendItems = listOf(
                                    stringResource(R.string.magic_mount_backend_auto),
                                    stringResource(R.string.magic_mount_backend_overlay),
                                    stringResource(R.string.magic_mount_backend_bind),
                                )
                                val backendIds = listOf("auto", "overlay", "bind")
                                val selectedBackend = backendIds
                                    .indexOf(status?.backend ?: "auto")
                                    .takeIf { it >= 0 } ?: 0
                                val backendSummary = if (magicMountFallbackOnly) {
                                    stringResource(
                                        R.string.origin_mount_magic_mount_backend_fallback_summary
                                    )
                                } else {
                                    stringResource(
                                        R.string.settings_magic_mount_backend_summary
                                    )
                                }
                                SettingsChooseWidget(
                                    icon = Icons.TwoTone.Dashboard,
                                    title = stringResource(R.string.settings_magic_mount_backend),
                                    description = backendSummary,
                                    enabled = !uiState.isWorking && !metamoduleOwnsMounting,
                                    items = backendItems,
                                    selectedIndex = selectedBackend,
                                    onSelectedIndexChange = { index ->
                                        backendIds.getOrNull(index)?.let { backend ->
                                            viewModel.dispatch(
                                                OriginMountUiAction.SetMagicMountBackend(backend)
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                if (showNomount) {
                    item {
                        SegmentedColumn(
                            title = stringResource(R.string.origin_mount_nomount_section)
                        ) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Security,
                                    title = stringResource(R.string.origin_mount_nomount_driver),
                                    description = driverText,
                                    isError = nomountUnavailable,
                                )
                            }
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Info,
                                    title = stringResource(R.string.origin_mount_nomount_rules),
                                    description = rulesText,
                                )
                            }
                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = stringResource(R.string.origin_mount_nomount_uids),
                                    description = exclusionsText,
                                    enabled = !uiState.isWorking && nomount != null,
                                    onClick = {
                                        viewModel.dispatch(OriginMountUiAction.LoadApps)
                                        uidDialog.show()
                                    },
                                )
                            }
                            item {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Security,
                                    title = stringResource(R.string.origin_mount_nomount_isolated),
                                    description = stringResource(
                                        R.string.origin_mount_nomount_isolated_summary
                                    ),
                                    enabled = !uiState.isWorking && nomount != null,
                                    checked = uiState.uidState.isolatedBlocked,
                                    onCheckedChange = { checked ->
                                        viewModel.dispatch(
                                            OriginMountUiAction.SetIsolated(stockVisible = checked)
                                        )
                                    },
                                )
                            }
                            item(visible = nomountUnavailable) {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Warning,
                                    title = stringResource(
                                        R.string.origin_mount_nomount_unavailable
                                    ),
                                    description = nomountUnavailableSummary(nomount, knownValue),
                                    isError = true,
                                )
                            }
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Delete,
                                    title = stringResource(R.string.origin_mount_nomount_clear),
                                    description = stringResource(
                                        R.string.origin_mount_nomount_clear_summary
                                    ),
                                    enabled = !uiState.isWorking && nomount?.isReady == true,
                                    onClick = {
                                        coroutineScope.launch {
                                            val result = clearDialog.awaitConfirm(
                                                title = clearTitle,
                                                content = clearMessage,
                                                confirm = clearAction,
                                                dismiss = cancel,
                                            )
                                            if (result == ConfirmResult.Confirmed) {
                                                viewModel.dispatch(OriginMountUiAction.ClearRules)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                if (showZeromount) {
                    item {
                        SegmentedColumn(
                            title = stringResource(R.string.origin_mount_zeromount_section)
                        ) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Security,
                                    title = stringResource(R.string.origin_mount_zeromount_driver),
                                    description = zeromountDriverText,
                                    isError = !uiState.zeromountDriverPresent,
                                )
                            }
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Info,
                                    title = stringResource(R.string.origin_mount_zeromount_version),
                                    description = zeromountVersionText,
                                )
                            }
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = stringResource(R.string.origin_mount_zeromount_metamodule),
                                    description = zeromountModuleText,
                                )
                            }
                            item(visible = mode == OriginMountMode.Zeromount && !zeromountActive) {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Warning,
                                    title = stringResource(
                                        R.string.origin_mount_zeromount_unavailable
                                    ),
                                    description = if (!uiState.zeromountDriverPresent) {
                                        stringResource(
                                            R.string.origin_mount_zeromount_driver_absent_summary
                                        )
                                    } else {
                                        stringResource(R.string.origin_mount_zeromount_not_active)
                                    },
                                    isError = true,
                                )
                            }
                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Dashboard,
                                    title = stringResource(R.string.origin_mount_zeromount_webui),
                                    description = stringResource(
                                        R.string.origin_mount_zeromount_webui_summary
                                    ),
                                    enabled = !uiState.isWorking &&
                                        uiState.zeromountVersion.isNotBlank(),
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(context, WebUIActivity::class.java)
                                                    .setData(
                                                        "kernelsu://webui/$zeromountWebUiModuleId"
                                                            .toUri()
                                                    )
                                                    .putExtra("id", zeromountWebUiModuleId)
                                                    .putExtra("name", zeromountLabel)
                                            )
                                        }.onFailure { error ->
                                            snackBarHost.showReplacingSnackbar(
                                                error.message.ifBlank { actionFailed }
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    SegmentedColumn(title = stringResource(R.string.origin_mount_actions_section)) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.RestartAlt,
                                title = stringResource(R.string.origin_mount_mount_now),
                                description =
                                    stringResource(R.string.origin_mount_mount_now_summary),
                                enabled = !uiState.isWorking,
                                onClick = { viewModel.dispatch(OriginMountUiAction.MountNow) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginMountHeroCard(
    eyebrow: String,
    provider: String,
    modeSummary: String,
    modeLabel: String,
    showBackend: Boolean,
    backendLabel: String,
    backendActive: Boolean,
    runtimeLabel: String,
    runtimeDetail: String,
    runtimeActive: Boolean,
    runtimeWarning: Boolean,
    lastBootLabel: String,
    modifier: Modifier = Modifier,
) {
    BentoCard(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Icon(
                painter = painterResource(R.drawable.topbar_leaf),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 34.dp)
                    .size(150.dp),
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = eyebrow,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = provider,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = modeSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BentoStatusChip(text = modeLabel, active = true)
                    if (showBackend) {
                        BentoStatusChip(text = backendLabel, active = backendActive)
                    }
                    BentoStatusChip(
                        text = runtimeLabel,
                        active = runtimeActive,
                        isWarning = runtimeWarning,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = lastBootLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = runtimeDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun providerLabel(provider: String, unknown: String): String = when (provider) {
    "builtin" -> stringResource(R.string.origin_mount_provider_builtin)
    "metamodule" -> stringResource(R.string.origin_mount_provider_metamodule)
    "zeromount" -> stringResource(R.string.origin_mount_provider_zeromount)
    "nomount" -> stringResource(R.string.origin_mount_provider_nomount)
    "none" -> stringResource(R.string.origin_mount_provider_none)
    else -> unknown
}

@Composable
private fun runtimeSummary(
    runtime: OriginMountRuntime?,
    status: OriginMountStatus?,
    unknown: String,
): String {
    if (runtime == null) return unknown
    val provider = providerLabel(runtime.provider.ifBlank { status?.provider.orEmpty() }, unknown)
    val result = when (runtime.result) {
        "disabled" -> stringResource(R.string.origin_mount_runtime_disabled)
        "delegated" -> stringResource(R.string.origin_mount_runtime_delegated)
        "mounted" -> stringResource(R.string.origin_mount_runtime_mounted)
        "noop" -> stringResource(R.string.origin_mount_runtime_noop)
        "failed" -> stringResource(R.string.origin_mount_runtime_failed)
        else -> runtime.result.ifBlank { unknown }
    }
    val targetCount = runtime.targetCount
    val targets = if (targetCount != null) {
        " · " + stringResource(R.string.origin_mount_runtime_targets, targetCount)
    } else {
        ""
    }
    val warning = if (runtime.warning.isNotBlank()) " · ${runtime.warning}" else ""
    return "$result · $provider$targets$warning"
}

@Composable
private fun nomountDriverSummary(nomount: OriginMountNoMountStatus, unknown: String): String {
    val state = when (nomount.driverState) {
        "ready" -> stringResource(R.string.origin_mount_nomount_driver_ready)
        "outdated" -> stringResource(R.string.origin_mount_nomount_driver_outdated)
        "absent" -> stringResource(R.string.origin_mount_nomount_driver_absent)
        else -> nomount.driverState.ifBlank { unknown }
    }
    val version = if (nomount.driverVersion.isNotBlank()) {
        " · " + stringResource(R.string.origin_mount_nomount_api, nomount.driverVersion)
    } else {
        ""
    }
    val deferred = if (nomount.deferredTo.isNotBlank()) {
        " · " + stringResource(R.string.origin_mount_nomount_deferred, nomount.deferredTo)
    } else {
        ""
    }
    return "$state$version$deferred"
}

@Composable
private fun nomountUnavailableSummary(
    nomount: OriginMountNoMountStatus?,
    unknown: String,
): String {
    if (nomount == null) return unknown
    val detail = nomount.unavailableDetail.ifBlank { nomount.error }.ifBlank { unknown }
    return when (nomount.unavailableCode) {
        "no_asset_for_kmi" ->
            stringResource(R.string.origin_mount_nomount_unavailable_no_asset, detail)
        "outdated" -> stringResource(R.string.origin_mount_nomount_unavailable_outdated)
        "not_loaded" -> stringResource(R.string.origin_mount_nomount_unavailable_not_loaded)
        "unknown_kmi" -> stringResource(R.string.origin_mount_nomount_unavailable_unknown_kmi)
        else -> detail
    }
}

@Composable
private fun NomountUidDialog(
    apps: List<InstalledPackageInfo>,
    loading: Boolean,
    stored: List<Int>,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selected by remember(stored) { mutableStateOf(stored.toSet()) }
    val filtered = remember(apps, search) {
        val query = search.trim()
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appLabel.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.popupBlur(),
        containerColor = popupContainerColor(),
        title = { Text(stringResource(R.string.origin_mount_uids_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.origin_mount_uids_search)) },
                )
                if (loading) {
                    Text(
                        stringResource(R.string.origin_mount_uids_loading),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.origin_mount_uids_empty),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(filtered, key = { it.uid }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.appLabel.ifBlank { app.packageName },
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.origin_mount_uids_uid,
                                            app.uid,
                                        ) + " · " + app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = selected.contains(app.uid),
                                    onCheckedChange = { checked ->
                                        selected = if (checked) {
                                            selected + app.uid
                                        } else {
                                            selected - app.uid
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toList()) }) {
                Text(stringResource(R.string.origin_mount_uids_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.origin_mount_uids_cancel))
            }
        },
    )
}
