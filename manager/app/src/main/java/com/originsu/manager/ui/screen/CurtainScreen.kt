package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Groups
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.OpenInNew
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.curtain.CurtainConfigRepository
import com.originsu.manager.data.curtain.CurtainLoadError
import com.originsu.manager.data.curtain.OriginCurtainRepository
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.PackageIcon
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SearchNoMatch
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.component.settings.lazySegmentColumn
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.CurtainUiEvent
import com.originsu.manager.ui.viewmodel.CurtainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Origin Curtain's built-in app-list hiding screen.
 *
 * It edits the upstream HMA-OSS module's config directly, so users get a
 * native, Origin-styled UI instead of the separate HMA-OSS config app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtainScreen(viewModel: CurtainViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackBarHost = LocalSnackbarHost.current
    val originCurtainRepository = koinInject<OriginCurtainRepository>()

    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val saveFailed = stringResource(R.string.origin_curtain_error_write)
    val configMissing = stringResource(R.string.origin_curtain_error_not_initialized)
    val rebootTitle = stringResource(R.string.origin_curtain_setup_reboot)
    val rebootSummary = stringResource(R.string.origin_curtain_setup_reboot_summary)
    val rebootConfirmTitle = stringResource(R.string.origin_curtain_setup_reboot_confirm_title)
    val rebootConfirmContent = stringResource(R.string.origin_curtain_setup_reboot_confirm_content)
    val requestReboot: () -> Unit = {
        scope.launch {
            if (confirmDialog.awaitConfirm(
                    title = rebootConfirmTitle,
                    content = rebootConfirmContent,
                ) == ConfirmResult.Confirmed
            ) {
                viewModel.reboot()
            }
        }
        Unit
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            snackBarHost.showReplacingSnackbar(
                when (event) {
                    CurtainUiEvent.SaveFailed -> saveFailed
                    CurtainUiEvent.ConfigMissing -> configMissing
                }
            )
        }
    }

    ActivityResumeEffect {
        viewModel.refresh()
    }

    val setupText = when {
        !uiState.moduleInstalled ->
            stringResource(R.string.origin_curtain_setup_not_installed)
        !uiState.moduleLoaded ->
            stringResource(R.string.origin_curtain_setup_reboot_module)
        else -> stringResource(R.string.origin_curtain_setup_config_missing)
    }

    val errorText = when (uiState.error) {
        CurtainLoadError.NO_ROOT -> stringResource(R.string.origin_curtain_error_no_root)
        CurtainLoadError.UNREADABLE -> stringResource(R.string.origin_curtain_error_unreadable)
        CurtainLoadError.WRITE_FAILED -> stringResource(R.string.origin_curtain_error_write)
        CurtainLoadError.NOT_INITIALIZED, null -> null
    }

    val appsSummary = stringResource(
        R.string.origin_curtain_apps_summary,
        uiState.configuredCount,
        uiState.apps.size,
    )

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.origin_curtain),
                searchText = uiState.search,
                onSearchTextChange = viewModel::search,
                onBackClick = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.origin_curtain_search_apps),
            )
        },
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
    ) { innerPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (uiState.loading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    }
                }
                return@LazyColumn
            }

            item(key = "status") {
                SegmentedColumn(title = stringResource(R.string.origin_curtain_status)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Visibility,
                            title = stringResource(R.string.origin_curtain),
                            description = if (uiState.moduleVersion.isBlank()) {
                                stringResource(R.string.origin_curtain_status_missing)
                            } else {
                                stringResource(
                                    R.string.origin_curtain_status_version,
                                    uiState.moduleVersion,
                                )
                            },
                        )
                    }
                    item(visible = uiState.workMode != null) {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Info,
                            title = stringResource(R.string.origin_curtain_work_mode),
                            description = workModeText(uiState.workMode),
                            isError = uiState.workMode == CurtainConfigRepository.WORK_MODE_NO_HOOKS ||
                                uiState.workMode == CurtainConfigRepository.WORK_MODE_CRASHED,
                        )
                    }
                    item(visible = uiState.filterCount != null) {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.BugReport,
                            title = stringResource(R.string.origin_curtain_filter_count),
                            description = stringResource(
                                R.string.origin_curtain_filter_count_value,
                                uiState.filterCount ?: 0,
                            ),
                        )
                    }
                }
            }

            if (uiState.error == CurtainLoadError.NOT_INITIALIZED) {
                item(key = "setup") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_setup)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.origin_curtain_setup_title),
                                description = setupText,
                            )
                        }
                        item(visible = uiState.moduleInstalled) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.RestartAlt,
                                title = rebootTitle,
                                description = rebootSummary,
                                onClick = { requestReboot() },
                            )
                        }
                    }
                }
            }

            if (uiState.configCreated) {
                item(key = "config-created") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_setup)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.origin_curtain_config_created),
                                description = stringResource(
                                    R.string.origin_curtain_config_created_summary,
                                ),
                            )
                        }
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.RestartAlt,
                                title = rebootTitle,
                                description = rebootSummary,
                                onClick = { requestReboot() },
                            )
                        }
                    }
                }
            }

            if (errorText != null) {
                item(key = "error") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.origin_curtain_error_title),
                                description = errorText,
                                isError = true,
                            )
                        }
                    }
                }
            }

            if (uiState.upstreamInstalled) {
                item(key = "upstream-warning") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_advanced)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.origin_curtain_upstream_conflict),
                                description = stringResource(
                                    R.string.origin_curtain_upstream_conflict_summary,
                                ),
                                isError = true,
                            )
                        }
                    }
                }
            }

            val global = uiState.global
            if (global != null) {
                item(key = "global") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_global)) {
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.origin_curtain_force_mount),
                                description = stringResource(R.string.origin_curtain_force_mount_summary),
                                enabled = !uiState.busy,
                                checked = global.forceMountData,
                                onCheckedChange = { enabled ->
                                    viewModel.setGlobal { it.copy(forceMountData = enabled) }
                                },
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.origin_curtain_disable_activity_launch),
                                description = stringResource(
                                    R.string.origin_curtain_disable_activity_launch_summary,
                                ),
                                enabled = !uiState.busy,
                                checked = global.disableActivityLaunchProtection,
                                onCheckedChange = { enabled ->
                                    viewModel.setGlobal {
                                        it.copy(disableActivityLaunchProtection = enabled)
                                    }
                                },
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.origin_curtain_alt_app_data),
                                description = stringResource(R.string.origin_curtain_alt_app_data_summary),
                                enabled = !uiState.busy,
                                checked = global.altAppDataIsolation,
                                onCheckedChange = { enabled ->
                                    viewModel.setGlobal { it.copy(altAppDataIsolation = enabled) }
                                },
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.origin_curtain_alt_vold_data),
                                description = stringResource(R.string.origin_curtain_alt_vold_data_summary),
                                enabled = !uiState.busy,
                                checked = global.altVoldAppDataIsolation,
                                onCheckedChange = { enabled ->
                                    viewModel.setGlobal { it.copy(altVoldAppDataIsolation = enabled) }
                                },
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.origin_curtain_skip_system_data),
                                description = stringResource(
                                    R.string.origin_curtain_skip_system_data_summary,
                                ),
                                enabled = !uiState.busy,
                                checked = global.skipSystemAppDataIsolation,
                                onCheckedChange = { enabled ->
                                    viewModel.setGlobal {
                                        it.copy(skipSystemAppDataIsolation = enabled)
                                    }
                                },
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.origin_curtain_detail_log),
                                description = stringResource(R.string.origin_curtain_detail_log_summary),
                                enabled = !uiState.busy,
                                checked = global.detailLog,
                                onCheckedChange = { enabled ->
                                    viewModel.setGlobal { it.copy(detailLog = enabled) }
                                },
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.origin_curtain_error_only_log),
                                description = stringResource(
                                    R.string.origin_curtain_error_only_log_summary,
                                ),
                                enabled = !uiState.busy,
                                checked = global.errorOnlyLog,
                                onCheckedChange = { enabled ->
                                    viewModel.setGlobal { it.copy(errorOnlyLog = enabled) }
                                },
                            )
                        }
                    }
                }
            }

            if (uiState.visibleApps.isEmpty()) {
                item(key = "no-apps") { SearchNoMatch() }
            } else {
                lazySegmentColumn(
                    items = uiState.visibleApps,
                    title = appsSummary,
                    key = { _, app -> app.packageName },
                ) { _, app ->
                    SettingsBaseWidget(
                        title = app.label,
                        description = app.packageName,
                        leadingContent = {
                            PackageIcon(
                                packageName = app.packageName,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        onClick = { navigator.push(Route.CurtainApp(app.packageName)) },
                    ) { _ ->
                        Switch(
                            checked = app.hidingEnabled,
                            enabled = !uiState.busy,
                            onCheckedChange = { enabled ->
                                viewModel.setAppEnabled(app.packageName, enabled)
                            },
                        )
                    }
                }
            }

            item(key = "templates") {
                SegmentedColumn(title = stringResource(R.string.origin_curtain_tools)) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Groups,
                            title = stringResource(R.string.origin_curtain_templates),
                            description = stringResource(R.string.origin_curtain_templates_summary),
                            onClick = { navigator.push(Route.CurtainTemplates) },
                        )
                    }
                    item(visible = uiState.upstreamInstalled) {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.OpenInNew,
                            title = stringResource(R.string.origin_curtain_open_upstream),
                            description = stringResource(
                                R.string.origin_curtain_open_upstream_summary,
                            ),
                            onClick = {
                                originCurtainRepository.managerLaunchIntent()?.let { intent ->
                                    runCatching { context.startActivity(intent) }
                                }
                            },
                        )
                    }
                }
            }

            item(key = "footer") {
                Text(
                    text = stringResource(R.string.origin_curtain_third_party_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun workModeText(workMode: Int?): String = stringResource(
    when (workMode) {
        CurtainConfigRepository.WORK_MODE_OK -> R.string.origin_curtain_work_mode_ok
        CurtainConfigRepository.WORK_MODE_NO_HOOKS -> R.string.origin_curtain_work_mode_no_hooks
        CurtainConfigRepository.WORK_MODE_LOADING -> R.string.origin_curtain_work_mode_loading
        CurtainConfigRepository.WORK_MODE_CRASHED -> R.string.origin_curtain_work_mode_crashed
        else -> R.string.origin_curtain_work_mode_unknown
    },
)
