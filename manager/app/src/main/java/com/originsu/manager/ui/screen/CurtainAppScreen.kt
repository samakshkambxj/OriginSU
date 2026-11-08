package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.ShoppingCart
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material.icons.twotone.SwapHoriz
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.curtain.CurtainLoadError
import com.originsu.manager.ui.component.CurtainPickerEntry
import com.originsu.manager.ui.component.rememberCurtainPickerDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.CurtainAppViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Per-app HMA-OSS rule editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtainAppScreen(packageName: String) {
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<CurtainAppViewModel>(
        parameters = { parametersOf(packageName) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val picker = rememberCurtainPickerDialog()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val config = uiState.config
    val blacklist = stringResource(R.string.origin_curtain_mode_blacklist)
    val whitelist = stringResource(R.string.origin_curtain_mode_whitelist)
    val errorText = when (uiState.error) {
        CurtainLoadError.NO_ROOT -> stringResource(R.string.origin_curtain_error_no_root)
        CurtainLoadError.NOT_INITIALIZED ->
            stringResource(R.string.origin_curtain_error_not_initialized)
        CurtainLoadError.UNREADABLE -> stringResource(R.string.origin_curtain_error_unreadable)
        CurtainLoadError.WRITE_FAILED -> stringResource(R.string.origin_curtain_error_write)
        null -> null
    }

    val appEntries = remember(uiState.installedApps) {
        uiState.installedApps.map {
            CurtainPickerEntry(
                id = it.packageName,
                label = it.label,
                description = it.packageName,
                iconPackage = it.packageName,
            )
        }
    }
    val templateEntries = remember(uiState.templates) {
        uiState.templates.map {
            CurtainPickerEntry(
                id = it.name,
                label = it.name,
                description = if (it.isWhitelist) whitelist else blacklist,
            )
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(uiState.label) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (themeConfig.isEnableBlur) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                    },
                    scrolledContainerColor = if (themeConfig.isEnableBlur) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                    },
                ),
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (errorText != null) {
                item(key = "error") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_app_config)) {
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

            item(key = "enable") {
                SegmentedColumn(title = stringResource(R.string.origin_curtain_app_config)) {
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Visibility,
                            title = stringResource(R.string.origin_curtain_app_hide),
                            description = stringResource(R.string.origin_curtain_app_hide_summary),
                            enabled = !uiState.busy,
                            checked = config != null,
                            onCheckedChange = viewModel::setEnabled,
                        )
                    }
                }
            }

            if (config != null) {
                item(key = "mode") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_mode)) {
                        item {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Lock,
                                title = stringResource(R.string.origin_curtain_mode),
                                description = stringResource(R.string.origin_curtain_mode_summary),
                                items = listOf(blacklist, whitelist),
                                selectedIndex = if (config.useWhitelist) 1 else 0,
                                enabled = !uiState.busy,
                                onSelectedIndexChange = { index ->
                                    viewModel.setMode(index == 1)
                                },
                            )
                        }
                    }
                }

                item(key = "lists") {
                    val listTitle = if (config.useWhitelist) {
                        stringResource(R.string.origin_curtain_visible_apps)
                    } else {
                        stringResource(R.string.origin_curtain_hidden_apps)
                    }
                    val listDescription = if (config.useWhitelist) {
                        stringResource(R.string.origin_curtain_visible_apps_summary)
                    } else {
                        stringResource(R.string.origin_curtain_hidden_apps_summary)
                    }
                    val exceptionTitle = stringResource(R.string.origin_curtain_excluded_apps)
                    val exceptionDescription =
                        stringResource(R.string.origin_curtain_excluded_apps_summary)
                    val templatesTitle = stringResource(R.string.origin_curtain_applied_templates)
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_apps_section)) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Lock,
                                title = listTitle,
                                description = stringResource(
                                    R.string.origin_curtain_selected_count_summary,
                                    config.extraAppList.size,
                                    listDescription,
                                ),
                                enabled = !uiState.busy,
                                onClick = {
                                    picker.show(
                                        title = listTitle,
                                        entries = appEntries,
                                        selected = config.extraAppList,
                                        onConfirm = viewModel::setExtraAppList,
                                    )
                                },
                            )
                        }
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Info,
                                title = exceptionTitle,
                                description = stringResource(
                                    R.string.origin_curtain_selected_count_summary,
                                    config.extraOppositeAppList.size,
                                    exceptionDescription,
                                ),
                                enabled = !uiState.busy,
                                onClick = {
                                    picker.show(
                                        title = exceptionTitle,
                                        entries = appEntries,
                                        selected = config.extraOppositeAppList,
                                        onConfirm = viewModel::setExceptionList,
                                    )
                                },
                            )
                        }
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Settings,
                                title = templatesTitle,
                                description = stringResource(
                                    R.string.origin_curtain_selected_count,
                                    config.applyTemplates.size,
                                ),
                                enabled = !uiState.busy && templateEntries.isNotEmpty(),
                                onClick = {
                                    picker.show(
                                        title = templatesTitle,
                                        entries = templateEntries,
                                        selected = config.applyTemplates,
                                        onConfirm = viewModel::setAppliedTemplates,
                                    )
                                },
                            )
                        }
                    }
                }

                item(key = "spoofing") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_spoof)) {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.ShoppingCart,
                                title = stringResource(R.string.origin_curtain_spoof_source),
                                description = stringResource(
                                    R.string.origin_curtain_spoof_source_summary,
                                ),
                                enabled = !uiState.busy,
                                checked = config.hideInstallationSource,
                                onCheckedChange = viewModel::setSpoofInstallationSource,
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Android,
                                title = stringResource(R.string.origin_curtain_spoof_source_system),
                                description = stringResource(
                                    R.string.origin_curtain_spoof_source_system_summary,
                                ),
                                enabled = !uiState.busy && config.hideInstallationSource,
                                checked = config.hideSystemInstallationSource,
                                onCheckedChange = viewModel::setSpoofSystemInstallationSource,
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Block,
                                title = stringResource(R.string.origin_curtain_spoof_exclude_target),
                                description = stringResource(
                                    R.string.origin_curtain_spoof_exclude_target_summary,
                                ),
                                enabled = !uiState.busy && config.hideInstallationSource,
                                checked = config.excludeTargetInstallationSource,
                                onCheckedChange = viewModel::setExcludeTargetInstallationSource,
                            )
                        }
                    }
                }

                item(key = "advanced") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_advanced)) {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Android,
                                title = stringResource(R.string.origin_curtain_exclude_system),
                                description = stringResource(
                                    R.string.origin_curtain_exclude_system_summary,
                                ),
                                enabled = !uiState.busy && config.useWhitelist,
                                checked = config.excludeSystemApps,
                                onCheckedChange = viewModel::setExcludeSystemApps,
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.SwapHoriz,
                                title = stringResource(R.string.origin_curtain_invert_launch),
                                description = stringResource(
                                    R.string.origin_curtain_invert_launch_summary,
                                ),
                                enabled = !uiState.busy,
                                checked = config.invertActivityLaunchProtection,
                                onCheckedChange = viewModel::setInvertActivityLaunchProtection,
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Storage,
                                title = stringResource(R.string.origin_curtain_exclude_vold),
                                description = stringResource(
                                    R.string.origin_curtain_exclude_vold_summary,
                                ),
                                enabled = !uiState.busy,
                                checked = config.excludeVoldIsolation,
                                onCheckedChange = viewModel::setExcludeVoldIsolation,
                            )
                        }
                    }
                }
            }
        }
    }
}
