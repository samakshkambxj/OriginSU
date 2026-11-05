package com.originsu.manager.ui.screen.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.ElectricalServices
import androidx.compose.material.icons.twotone.Fence
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SearchNoMatch
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.HomeViewModel
import com.originsu.manager.ui.viewmodel.SettingsUiAction
import com.originsu.manager.ui.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Long-tail configuration rows split out of the settings bento grid.
 *
 * The main tab keeps the most-used configuration controls in a band; the rest
 * live here so the landing page stays scannable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedConfigScreen(
    settingsViewModel: SettingsViewModel,
    initialQuery: String = "",
) {
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    )
    var searchQuery by remember { mutableStateOf(initialQuery) }
    val snackBarHost = LocalSnackbarHost.current
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    val query = searchQuery.trim()
    fun matches(vararg texts: String): Boolean {
        if (query.isBlank()) return true
        return texts.any { it.contains(query, ignoreCase = true) }
    }

    val titleProfileTemplate = stringResource(R.string.settings_profile_template)
    val descProfileTemplate = stringResource(R.string.settings_profile_template_summary)
    val titleAutoJailbreak = stringResource(R.string.settings_auto_jailbreak)
    val descAutoJailbreak = stringResource(R.string.settings_auto_jailbreak_summary)
    val titleShellSuAdbOnly = stringResource(R.string.settings_shell_su_adb_only)
    val descShellSuAdbOnlyDefault = stringResource(R.string.settings_shell_su_adb_only_summary)
    val titleSoftReboot = stringResource(R.string.settings_soft_reboot)
    val descSoftReboot = stringResource(R.string.settings_soft_reboot_summary)
    val titleSulog = stringResource(R.string.settings_sulog)
    val descSulogDefault = stringResource(R.string.settings_sulog_summary)
    val titleSelinuxHide = stringResource(R.string.settings_selinux_hide)
    val descSelinuxHideDefault = stringResource(R.string.settings_selinux_hide_summary)
    val titleUmountDefault = stringResource(R.string.settings_umount_modules_default)
    val descUmountDefaultSummary = stringResource(R.string.settings_umount_modules_default_summary)

    val suSummaryUnsupported = stringResource(R.string.feature_status_unsupported_summary)
    val suSummaryManaged = stringResource(R.string.feature_status_managed_summary)
    val shellSuAdbOnlySummary = when (uiState.shellSuAdbOnlyStatus) {
        "unsupported" -> suSummaryUnsupported
        "managed" -> suSummaryManaged
        else -> descShellSuAdbOnlyDefault
    }
    val sulogSummary = when (uiState.sulogStatus) {
        "unsupported" -> suSummaryUnsupported
        "managed" -> suSummaryManaged
        else -> descSulogDefault
    }
    val selinuxHideSummary = when (uiState.selinuxHideStatus) {
        "unsupported" -> suSummaryUnsupported
        "managed" -> suSummaryManaged
        else -> descSelinuxHideDefault
    }

    val matchProfileTemplate = matches(titleProfileTemplate, descProfileTemplate)
    val matchAutoJailbreak = matches(titleAutoJailbreak, descAutoJailbreak)
    val matchShellSuAdbOnly = matches(titleShellSuAdbOnly, shellSuAdbOnlySummary)
    val matchSoftReboot = matches(titleSoftReboot, descSoftReboot)
    val matchSulog = matches(titleSulog, sulogSummary)
    val matchSelinuxHide = matches(titleSelinuxHide, selinuxHideSummary)
    val matchUmountDefault = matches(titleUmountDefault, descUmountDefaultSummary)

    val hasAnyMatch = matchProfileTemplate ||
        (homeState.systemStatus.isLateLoadMode && matchAutoJailbreak) ||
        matchShellSuAdbOnly || matchSoftReboot || matchSulog || matchSelinuxHide ||
        matchUmountDefault

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.settings_advanced_config),
                searchText = searchQuery,
                onSearchTextChange = { searchQuery = it },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_advanced_config),
            )
        },
        snackbarHost = {
            SwipeableSnackbarHost(hostState = snackBarHost)
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(includeBottom = false)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .overScrollVertical()
                .blurSource(),
            overscrollEffect = null,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            )
        ) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.settings_advanced_config),
                    content = {
                        item(visible = matchProfileTemplate) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Fence,
                                title = titleProfileTemplate,
                                description = descProfileTemplate,
                                onClick = {
                                    navigator.push(Route.AppProfileTemplate)
                                }
                            )
                        }

                        item(
                            visible = homeState.systemStatus.isLateLoadMode && matchAutoJailbreak
                        ) {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.ElectricalServices,
                                title = titleAutoJailbreak,
                                description = descAutoJailbreak,
                                checked = uiState.autoJailbreakEnabled,
                                onCheckedChange = { value ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetAutoJailbreak(value)
                                    )
                                }
                            )
                        }

                        item(visible = matchShellSuAdbOnly) {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Security,
                                title = titleShellSuAdbOnly,
                                description = shellSuAdbOnlySummary,
                                checked = uiState.isShellSuAdbOnlyEnabled,
                                enabled = uiState.shellSuAdbOnlyStatus == "supported",
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetShellSuAdbOnly(enabled)
                                    )
                                },
                            )
                        }

                        item(visible = matchSoftReboot) {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.RestartAlt,
                                title = titleSoftReboot,
                                description = descSoftReboot,
                                enabled = homeState.systemStatus.isFullFeatured &&
                                    !homeState.systemStatus.isLateLoadMode,
                                checked = homeState.systemStatus.isLateLoadMode || uiState.useSoftReboot,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetUseSoftReboot(enabled)
                                    )
                                },
                            )
                        }

                        item(visible = matchSulog) {
                            SettingsSwitchWidget(
                                icon = Icons.AutoMirrored.TwoTone.Article,
                                title = titleSulog,
                                description = sulogSummary,
                                enabled = uiState.sulogStatus == "supported",
                                checked = uiState.isSuLogEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(SettingsUiAction.SetSuLog(enabled))
                                },
                            )
                        }

                        item(visible = matchSelinuxHide) {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Policy,
                                title = titleSelinuxHide,
                                description = selinuxHideSummary,
                                enabled = uiState.selinuxHideStatus == "supported",
                                checked = uiState.isSelinuxHideEnabled,
                                onCheckedChange = { checked ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetSelinuxHide(checked)
                                    )
                                },
                            )
                        }

                        item(visible = matchUmountDefault) {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.FolderDelete,
                                title = titleUmountDefault,
                                description = descUmountDefaultSummary,
                                checked = uiState.defaultUmountModules,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetDefaultUmountModules(enabled)
                                    )
                                },
                            )
                        }
                    }
                )
            }

            if (query.isNotBlank() && !hasAnyMatch) {
                item {
                    SearchNoMatch()
                }
            }
        }
    }
}
