package com.originsu.manager.ui.screen.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Dashboard
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteForever
import androidx.compose.material.icons.twotone.ElectricalServices
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Fence
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.Psychology
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.RemoveCircle
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.BuildConfig
import com.originsu.manager.R
import com.originsu.manager.domain.usecase.GenerateBugreportUseCase
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.popupBlur
import com.originsu.manager.ui.component.popupContainerColor
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.canAuthenticateSecureRoot
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.HomeViewModel
import com.originsu.manager.ui.viewmodel.SettingsUiAction
import com.originsu.manager.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.originsu.manager.ui.util.ActivityResumeEffect
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */

private val SPACING_MEDIUM = 8.dp
private val SPACING_LARGE = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(bottomPadding: Dp) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
        settingsViewModel.dispatch(SettingsUiAction.RefreshOriginZygisk)
        settingsViewModel.dispatch(SettingsUiAction.RefreshBootloop)
    }

    ActivityResumeEffect {
        settingsViewModel.dispatch(SettingsUiAction.RefreshOverlayPermission)
    }

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = snackBarHost
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(includeBottom = false)
    ) { innerPadding ->
        val loadingDialog = rememberLoadingDialog()
        var showBottomsheet by remember { mutableStateOf(false) }
        val logSaved = stringResource(R.string.log_saved)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val exportBugreportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gzip")
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                loadingDialog.show()
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    generateBugreport().inputStream().use {
                        it.copyTo(output)
                    }
                }
                loadingDialog.hide()
                snackBarHost.showReplacingSnackbar(logSaved)
            }
        }

        LazyColumn(
            modifier =
                Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .blurSource(),
            overscrollEffect = null,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 15.dp
            )
        ) {
            // 配置卡片
            if (homeState.systemStatus.isFullFeatured) {
                item {
                    val modeItems = listOf(
                        stringResource(id = R.string.settings_mode_default),
                        stringResource(id = R.string.settings_mode_disable_until_reboot),
                        stringResource(id = R.string.settings_mode_disable_always),
                    )

                    SegmentedColumn(
                        title = stringResource(R.string.configuration),
                        content = {
                            item {
                                // 配置文件模板入口
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Fence,
                                    title = stringResource(R.string.settings_profile_template),
                                    description = stringResource(R.string.settings_profile_template_summary),
                                    onClick = {
                                        navigator.push(Route.AppProfileTemplate)
                                    }
                                )
                            }

                            item {
                                val suSummary = when (uiState.suStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sucompat_summary)
                                }
                                SettingsChooseWidget(
                                    icon = Icons.TwoTone.RemoveModerator,
                                    title = stringResource(id = R.string.settings_sucompat),
                                    description = suSummary,
                                    items = modeItems,
                                    enabled = uiState.suStatus == "supported",
                                    selectedIndex = uiState.suCompatMode,
                                    onSelectedIndexChange = { index ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetSuCompatMode(
                                                index
                                            )
                                        )
                                    },
                                )
                            }

                            item {
                                val umountSummary = when (uiState.kernelUmountStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_kernel_umount_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.RemoveCircle,
                                    title = stringResource(id = R.string.settings_kernel_umount),
                                    description = umountSummary,
                                    enabled = uiState.kernelUmountStatus == "supported",
                                    checked = uiState.isKernelUmountEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetKernelUmount(
                                                enabled
                                            )
                                        )
                                    },
                                )
                            }

                            item(
                                visible = homeState.systemStatus.isLateLoadMode
                            ) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.ElectricalServices,
                                    title = stringResource(id = R.string.settings_auto_jailbreak),
                                    description = stringResource(id = R.string.settings_auto_jailbreak_summary),
                                    checked = uiState.autoJailbreakEnabled,
                                    onCheckedChange = { value ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetAutoJailbreak(
                                                value
                                            )
                                        )
                                    }
                                )
                            }

                            item(
                                visible = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                            ) {
                                val adbRootSummary = when (uiState.adbRootStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_adb_root_summary)
                                }

                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Adb,
                                    title = stringResource(id = R.string.settings_adb_root),
                                    description = adbRootSummary,
                                    checked = uiState.isAdbRootEnabled,
                                    enabled = uiState.adbRootStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetAdbRoot(
                                                enabled
                                            )
                                        )
                                    },
                                )
                            }

                            item {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.RestartAlt,
                                    title = stringResource(id = R.string.settings_soft_reboot),
                                    description = stringResource(id = R.string.settings_soft_reboot_summary),
                                    enabled = homeState.systemStatus.isFullFeatured &&
                                        !homeState.systemStatus.isLateLoadMode,
                                    checked = homeState.systemStatus.isLateLoadMode || uiState.useSoftReboot,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetUseSoftReboot(
                                                enabled
                                            )
                                        )
                                    },
                                )
                            }

                            item {
                                val sulogSummary = when (uiState.sulogStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sulog_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.AutoMirrored.TwoTone.Article,
                                    title = stringResource(id = R.string.settings_sulog),
                                    description = sulogSummary,
                                    enabled = uiState.sulogStatus == "supported",
                                    checked = uiState.isSuLogEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetSuLog(enabled))
                                    },
                                )
                            }

                            item {
                                val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_selinux_hide_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Policy,
                                    title = stringResource(id = R.string.settings_selinux_hide),
                                    description = selinuxHideSummary,
                                    enabled = uiState.selinuxHideStatus == "supported",
                                    checked = uiState.isSelinuxHideEnabled,
                                    onCheckedChange = { checked ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetSelinuxHide(
                                                checked
                                            )
                                        )
                                    },
                                )
                            }

                            item {
                                // 卸载模块开关
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.FolderDelete,
                                    title = stringResource(id = R.string.settings_umount_modules_default),
                                    description = stringResource(id = R.string.settings_umount_modules_default_summary),
                                    checked = uiState.defaultUmountModules,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetDefaultUmountModules(
                                                enabled
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    )
                }
            }

            item {
                // Origin features. Without root/driver the whole group becomes
                // a blurred teaser that routes to the installer.
                val labLocked = !homeState.systemStatus.isRootAvailable
                Box(modifier = Modifier.fillMaxWidth()) {
                    SegmentedColumn(
                        modifier = if (!labLocked) {
                            Modifier
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(14.dp)
                        } else {
                            Modifier.alpha(0.35f)
                        },
                        title = stringResource(R.string.origin_section_title),
                    content = {
                        item {
                            val zygiskSummary = when {
                                uiState.isOriginZygiskRunning -> stringResource(
                                    id = R.string.origin_zygisk_running
                                )

                                uiState.isOriginZygiskEnabled -> stringResource(
                                    id = R.string.origin_zygisk_needs_reboot
                                )

                                else -> stringResource(
                                    id = R.string.settings_origin_zygisk_summary
                                )
                            }
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Psychology,
                                title = stringResource(id = R.string.settings_origin_zygisk),
                                description = zygiskSummary,
                                checked = uiState.isOriginZygiskEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetOriginZygiskEnabled(
                                            enabled
                                        )
                                    )
                                },
                            )
                        }

                        item {
                            val veilSummary = when (uiState.veilStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_veil_summary)
                            }
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Shield,
                                title = stringResource(id = R.string.settings_veil),
                                description = veilSummary,
                                enabled = uiState.veilStatus == "supported",
                                checked = uiState.isVeilEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(SettingsUiAction.SetVeil(enabled))
                                },
                            )
                        }

                        item(
                            visible = uiState.veilStatus == "supported" && uiState.isVeilEnabled
                        ) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Visibility,
                                title = stringResource(id = R.string.veil),
                                description = stringResource(id = R.string.veil_manage_summary),
                                onClick = {
                                    navigator.push(Route.Veil)
                                }
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Notifications,
                                title = stringResource(id = R.string.settings_grant_toast),
                                description = stringResource(id = R.string.settings_grant_toast_summary),
                                checked = uiState.isGrantToastEnabled,
                                enabled = uiState.sulogStatus == "supported",
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetGrantToast(enabled)
                                    )
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Security,
                                title = stringResource(id = R.string.settings_su_prompt),
                                description = stringResource(
                                    id = if (uiState.isSuPromptSupported)
                                        R.string.settings_su_prompt_summary
                                    else
                                        R.string.settings_su_prompt_unsupported
                                ),
                                checked = uiState.isSuPromptEnabled,
                                enabled = uiState.isSuPromptSupported,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetSuPrompt(enabled)
                                    )
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Timer,
                                title = stringResource(id = R.string.settings_temp_grant),
                                description = stringResource(id = R.string.settings_temp_grant_summary),
                                checked = uiState.isTempGrantEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetTempGrant(enabled)
                                    )
                                },
                            )
                        }

                        item(visible = uiState.bootloopRescued) {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.RestartAlt,
                                title = stringResource(R.string.settings_bootloop_rescued_title),
                                description = stringResource(
                                    R.string.settings_bootloop_rescued_summary,
                                    uiState.bootloopRescuedBoots ?: uiState.bootloopMax,
                                ),
                                isError = true,
                                onClick = {
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.DismissBootloopNotice
                                    )
                                },
                            )
                        }

                        item(visible = homeState.systemStatus.isFullFeatured) {
                            // 自动 bootloop 保护
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.RestartAlt,
                                title = stringResource(id = R.string.settings_bootloop_protection),
                                description = stringResource(
                                    id = R.string.settings_bootloop_protection_summary,
                                    uiState.bootloopMax,
                                ),
                                checked = uiState.isBootloopEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetBootloopEnabled(
                                            enabled
                                        )
                                    )
                                },
                            )
                        }

                        item(
                            visible = homeState.systemStatus.isFullFeatured &&
                                uiState.isBootloopEnabled
                        ) {
                            val thresholdItems = (2..10).map { it.toString() }
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Timer,
                                title = stringResource(id = R.string.settings_bootloop_threshold),
                                description = stringResource(
                                    id = R.string.settings_bootloop_threshold_summary,
                                    uiState.bootloopMax,
                                ),
                                items = thresholdItems,
                                selectedIndex = (uiState.bootloopMax - 2)
                                    .coerceIn(0, thresholdItems.lastIndex),
                                onSelectedIndexChange = { index ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetBootloopMax(index + 2)
                                    )
                                },
                            )
                        }

                        item {
                            val biometricsAvailable = remember {
                                canAuthenticateSecureRoot(context)
                            }
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Fingerprint,
                                title = stringResource(id = R.string.settings_secure_root),
                                description = stringResource(id = R.string.settings_secure_root_summary),
                                enabled = biometricsAvailable,
                                checked = uiState.isSecureRootEnabled && biometricsAvailable,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetSecureRootEnabled(
                                            enabled
                                        )
                                    )
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Policy,
                                title = stringResource(R.string.settings_originguard),
                                description = stringResource(R.string.settings_originguard_summary),
                                checked = uiState.isOriginGuardEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetOriginGuardEnabled(
                                            enabled
                                        )
                                    )
                                }
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Extension,
                                title = stringResource(R.string.settings_magic_mount),
                                description = stringResource(R.string.settings_magic_mount_summary),
                                // Magic Mount is currently unstable: keep the switch visible but
                                // disabled so it cannot be turned on.
                                enabled = false,
                                checked = uiState.isMagicMountEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetMagicMountEnabled(
                                            enabled
                                        )
                                    )
                                }
                            )
                        }

                        item {
                            val backendItems = listOf(
                                stringResource(R.string.magic_mount_backend_auto),
                                stringResource(R.string.magic_mount_backend_overlay),
                                stringResource(R.string.magic_mount_backend_bind),
                            )
                            val backendIds = listOf("auto", "overlay", "bind")
                            val selectedBackend =
                                backendIds.indexOf(uiState.magicMountBackend).takeIf { it >= 0 } ?: 0
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Dashboard,
                                title = stringResource(R.string.settings_magic_mount_backend),
                                description = stringResource(R.string.settings_magic_mount_backend_summary),
                                items = backendItems,
                                selectedIndex = selectedBackend,
                                onSelectedIndexChange = { index ->
                                    backendIds.getOrNull(index)?.let { backend ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetMagicMountBackend(
                                                backend
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                    )
                    if (labLocked) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    navigator.push(Route.Install(preselectedKernelUri = null))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 24.dp,
                                    vertical = 12.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.origin_section_title),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.origin_lab_locked_summary),
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // 应用设置卡片
                SegmentedColumn(
                    title = stringResource(R.string.app_settings),
                    content = {
                        expandableItem(
                            expanded = uiState.checkManagerUpdate,
                            topContent = {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Update,
                                    title = stringResource(R.string.settings_check_manager_update),
                                    description = stringResource(R.string.settings_check_manager_update_summary),
                                    checked = uiState.checkManagerUpdate,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetManagerUpdateCheck(
                                                enabled
                                            )
                                        )
                                    }
                                )
                            }
                        ) {
                            item(
                                topPadding = 1.dp
                            ) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Science,
                                    title = stringResource(R.string.settings_check_beta_update),
                                    description = stringResource(R.string.settings_check_beta_update_summary),
                                    checked = uiState.checkBetaUpdate,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetBetaUpdateCheck(
                                                enabled
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Extension,
                                title = stringResource(R.string.settings_check_module_update),
                                description = stringResource(R.string.settings_check_module_update_summary),
                                checked = uiState.checkModuleUpdate,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetModuleUpdateCheck(
                                            enabled
                                        )
                                    )
                                }
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Update,
                                title = stringResource(R.string.updater),
                                description = stringResource(R.string.updater_summary),
                                onClick = {
                                    navigator.push(Route.Updater())
                                }
                            )
                        }



                        item {
                            // 更多设置
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Settings,
                                title = stringResource(R.string.theme_settings),
                                description = stringResource(R.string.theme_settings),
                                onClick = {
                                    navigator.push(Route.ThemeSettings)
                                }
                            )
                        }
                    }
                )
            }

            item {
                // 工具卡片
                SegmentedColumn(
                    title = stringResource(R.string.tools),
                    content = {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.BugReport,
                                title = stringResource(R.string.send_log),
                                onClick = {
                                    showBottomsheet = true
                                }
                            )
                        }

                        if (homeState.systemStatus.isFullFeatured) {
                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Security,
                                    title = stringResource(R.string.dynamic_manager_title),
                                    description = stringResource(R.string.dynamic_manager_settings_summary),
                                    onClick = {
                                        navigator.push(Route.DynamicManager)
                                    }
                                )
                            }

                            item(visible = uiState.isKernelUmountEnabled) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.FolderOff,
                                    title = stringResource(R.string.umount_path_manager),
                                    description = stringResource(R.string.umount_path_manager_summary),
                                    onClick = {
                                        navigator.push(Route.UmountManager)
                                    }
                                )
                            }

                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Tune,
                                    title = stringResource(R.string.kernel_tuning),
                                    description = stringResource(R.string.kernel_tuning_summary),
                                    onClick = {
                                        navigator.push(Route.KernelTuning)
                                    }
                                )
                            }
                        }
                        item(visible = homeState.systemStatus.lkmMode == true && !homeState.systemStatus.isLateLoadMode) {
                            UninstallItem {
                                loadingDialog.withLoading(it)
                            }
                        }
                    }
                )
            }

            if (showBottomsheet) {
                item {
                    val sendLog = stringResource(R.string.send_log)
                    LogBottomSheet(
                        onDismiss = { showBottomsheet = false },
                        onSaveLog = {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                            val current = LocalDateTime.now().format(formatter)
                            exportBugreportLauncher.launch("KernelSU_bugreport_${current}.tar.gz")
                            showBottomsheet = false
                        },
                        onShareLog = {
                            scope.launch {
                                val bugreport = loadingDialog.withLoading {
                                    withContext(Dispatchers.IO) {
                                        generateBugreport()
                                    }
                                }

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    bugreport
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    setDataAndType(uri, "application/gzip")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        sendLog
                                    )
                                )

                                showBottomsheet = false
                            }
                        }
                    )
                }
            }

            // 关于卡片
            item {
                SegmentedColumn(
                    title = stringResource(R.string.about),
                    content = {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.about),
                                onClick = {
                                    navigator.push(Route.About)
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogBottomSheet(
    onDismiss: () -> Unit,
    onSaveLog: () -> Unit,
    onShareLog: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.popupBlur(),
        containerColor = popupContainerColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SPACING_LARGE),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LogActionButton(
                icon = Icons.TwoTone.Save,
                text = stringResource(R.string.save_log),
                onClick = onSaveLog
            )

            LogActionButton(
                icon = Icons.TwoTone.Share,
                text = stringResource(R.string.send_log),
                onClick = onShareLog
            )
        }
        Spacer(modifier = Modifier.height(SPACING_LARGE))
    }
}

@Composable
fun LogActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(SPACING_MEDIUM)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(SPACING_MEDIUM))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun UninstallItem(
    withLoading: suspend (suspend () -> Unit) -> Unit
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uninstallConfirmDialog = rememberConfirmDialog()
    val showTodo = {
        Toast.makeText(context, "TODO", Toast.LENGTH_SHORT).show()
    }
    val options = remember {
        listOf(
            UninstallType.PERMANENT,
            UninstallType.RESTORE_STOCK_IMAGE
        )
    }

    SettingsChooseWidget(
        icon = Icons.TwoTone.Delete,
        title = stringResource(id = R.string.settings_uninstall),
        items = options.map { stringResource(it.title) },
        itemDescriptions = options.map {
            if (it.message != 0) stringResource(it.message) else null
        },
        selectedIndex = -1,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let { uninstallType ->
                scope.launch {
                    val result = uninstallConfirmDialog.awaitConfirm(
                        title = context.getString(uninstallType.title),
                        content = context.getString(uninstallType.message)
                    )
                    if (result == ConfirmResult.Confirmed) {
                        withLoading {
                            when (uninstallType) {
                                UninstallType.TEMPORARY -> showTodo()
                                UninstallType.PERMANENT -> navigator.push(Route.Flash.uninstall())
                                UninstallType.RESTORE_STOCK_IMAGE -> navigator.push(Route.Flash.restore())
                                UninstallType.NONE -> Unit
                            }
                        }
                    }
                }
            }
        }
    )
}

enum class UninstallType(val title: Int, val message: Int, val icon: ImageVector) {
    TEMPORARY(
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message,
        Icons.TwoTone.Delete
    ),
    PERMANENT(
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message,
        Icons.TwoTone.DeleteForever
    ),
    RESTORE_STOCK_IMAGE(
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message,
        Icons.AutoMirrored.TwoTone.Undo
    ),
    NONE(0, 0, Icons.TwoTone.Delete)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(text = stringResource(R.string.settings))
        },
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
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}
