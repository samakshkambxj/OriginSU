package com.originsu.manager.ui.screen.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Dashboard
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteForever
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.Psychology
import androidx.compose.material.icons.twotone.RemoveCircle
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Update
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.usecase.GenerateBugreportUseCase
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SearchNoMatch
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.popupBlur
import com.originsu.manager.ui.component.popupContainerColor
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.BentoCaption
import com.originsu.manager.ui.component.settings.BentoCard
import com.originsu.manager.ui.component.settings.BentoGrid
import com.originsu.manager.ui.component.settings.BentoHeader
import com.originsu.manager.ui.component.settings.BentoJumpRow
import com.originsu.manager.ui.component.settings.BentoStatusChip
import com.originsu.manager.ui.component.settings.BentoToggleRow
import com.originsu.manager.ui.component.settings.SettingsChooseDialog
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.HomeViewModel
import com.originsu.manager.ui.viewmodel.SettingsUiAction
import com.originsu.manager.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.originsu.manager.ui.util.ActivityResumeEffect
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */

private val SPACING_MEDIUM = 8.dp
private val SPACING_LARGE = 16.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(bottomPadding: Dp) {
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    )
    var searchQuery by remember { mutableStateOf("") }
    val snackBarHost = LocalSnackbarHost.current
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    var showBottomsheet by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    val query = searchQuery.trim()
    fun matches(vararg texts: String): Boolean {
        if (query.isBlank()) return true
        return texts.any { it.contains(query, ignoreCase = true) }
    }

    // ----- Tile labels --------------------------------------------------------
    val sectionOrigin = stringResource(R.string.origin_section_title)
    val originTileSummary = stringResource(R.string.settings_bento_origin_summary)
    val coreTileTitle = stringResource(R.string.settings_bento_core_title)
    val updatesTileTitle = stringResource(R.string.settings_bento_updates_title)
    val toolsTileTitle = stringResource(R.string.tools)
    val titleTerminal = stringResource(R.string.terminal_title)
    val aboutTitle = stringResource(R.string.about)
    val advancedConfigTitle = stringResource(R.string.settings_advanced_config)
    val themeTitle = stringResource(R.string.theme_settings)
    val themeSummary = stringResource(R.string.settings_bento_theme_summary)
    val updaterTitle = stringResource(R.string.updater)
    val updaterSummary = stringResource(R.string.updater_summary)

    // ----- Search keywords (all rows, including subpage rows) -----------------
    val titleProfileTemplate = stringResource(R.string.settings_profile_template)
    val descProfileTemplate = stringResource(R.string.settings_profile_template_summary)
    val titleSuCompat = stringResource(R.string.settings_sucompat)
    val titleUmount = stringResource(R.string.settings_kernel_umount)
    val descUmount = stringResource(R.string.settings_kernel_umount_summary)
    val titleAutoJailbreak = stringResource(R.string.settings_auto_jailbreak)
    val descAutoJailbreak = stringResource(R.string.settings_auto_jailbreak_summary)
    val titleAdbRoot = stringResource(R.string.settings_adb_root)
    val titleShellSuAdbOnly = stringResource(R.string.settings_shell_su_adb_only)
    val titleSelinux = stringResource(R.string.settings_selinux_permissive)
    val titleSoftReboot = stringResource(R.string.settings_soft_reboot)
    val titleSulog = stringResource(R.string.settings_sulog)
    val titleSelinuxHide = stringResource(R.string.settings_selinux_hide)
    val titleUmountDefault = stringResource(R.string.settings_umount_modules_default)
    val titleZygisk = stringResource(R.string.settings_origin_zygisk)
    val titleZygiskIntegrity = stringResource(R.string.origin_zygisk_integrity)
    val titleZygiskModules = stringResource(R.string.origin_zygisk_modules)
    val titleZygiskSkipApps = stringResource(R.string.origin_zygisk_skip_apps)
    val titleZygiskTelemetry = stringResource(R.string.origin_zygisk_telemetry)
    val titleZygiskLog = stringResource(R.string.origin_zygisk_log)
    val titleZygiskDiagnostics = stringResource(R.string.origin_zygisk_diagnostics)
    val titleVeilSwitch = stringResource(R.string.settings_veil)
    val titleVeilManage = stringResource(R.string.veil)
    val titleGrantToast = stringResource(R.string.settings_grant_toast)
    val titleSuPrompt = stringResource(R.string.settings_su_prompt)
    val titleTempGrant = stringResource(R.string.settings_temp_grant)
    val titleBootloop = stringResource(R.string.settings_bootloop_protection)
    val titleBootloopThreshold = stringResource(R.string.settings_bootloop_threshold)
    val titleSecureRoot = stringResource(R.string.settings_secure_root)
    val titleAppLock = stringResource(R.string.settings_app_lock)
    val titleRootGate = stringResource(R.string.settings_root_gate)
    val titleLockBoundRoot = stringResource(R.string.settings_lock_bound_root)
    val titleRootGatePanic = stringResource(R.string.settings_root_gate_panic)
    val titleOriginGuard = stringResource(R.string.settings_originguard)
    val titleMagicMount = stringResource(R.string.settings_magic_mount)
    val titleMountEngine = stringResource(R.string.settings_mount_engine)
    val titleMagicBackend = stringResource(R.string.settings_magic_mount_backend)
    val titleSusfsLkm = stringResource(R.string.settings_susfs_lkm)
    val titleCheckManagerUpdate = stringResource(R.string.settings_check_manager_update)
    val descCheckManagerUpdate = stringResource(R.string.settings_check_manager_update_summary)
    val titleCheckBeta = stringResource(R.string.settings_check_beta_update)
    val descCheckBeta = stringResource(R.string.settings_check_beta_update_summary)
    val titleCheckModule = stringResource(R.string.settings_check_module_update)
    val descCheckModule = stringResource(R.string.settings_check_module_update_summary)
    val titleHaptics = stringResource(R.string.settings_haptics)
    val descHaptics = stringResource(R.string.settings_haptics_summary)
    val titleSendLog = stringResource(R.string.send_log)
    val titleDynamicManager = stringResource(R.string.dynamic_manager_title)
    val descDynamicManager = stringResource(R.string.dynamic_manager_settings_summary)
    val titleUmountManager = stringResource(R.string.umount_path_manager)
    val descUmountManager = stringResource(R.string.umount_path_manager_summary)
    val titleTuning = stringResource(R.string.kernel_tuning)
    val descTuning = stringResource(R.string.kernel_tuning_summary)
    val titleUninstall = stringResource(R.string.settings_uninstall)

    // Description-only keywords keep search parity with the pre-bento page.
    val originSearchKeywords = listOf(
        stringResource(R.string.settings_veil_summary),
        stringResource(R.string.veil_manage_summary),
        stringResource(R.string.settings_grant_toast_summary),
        stringResource(R.string.settings_su_prompt_summary),
        stringResource(R.string.settings_temp_grant_summary),
        stringResource(R.string.settings_secure_root_summary),
        stringResource(R.string.settings_app_lock_summary),
        stringResource(R.string.settings_root_gate_summary),
        stringResource(R.string.settings_lock_bound_root_summary),
        stringResource(R.string.settings_root_gate_panic_summary),
        stringResource(R.string.settings_originguard_summary),
        stringResource(R.string.settings_magic_mount_summary),
        stringResource(R.string.settings_magic_mount_backend_summary),
        stringResource(R.string.settings_susfs_lkm_summary),
    )
    val coreSearchKeywords = listOf(
        stringResource(R.string.settings_sucompat_summary),
        stringResource(R.string.settings_adb_root_summary),
        stringResource(R.string.settings_selinux_permissive_summary),
        stringResource(R.string.settings_shell_su_adb_only_summary),
        stringResource(R.string.settings_soft_reboot_summary),
        stringResource(R.string.settings_sulog_summary),
        stringResource(R.string.settings_selinux_hide_summary),
        stringResource(R.string.settings_umount_modules_default_summary),
    )

    val matchOriginTile = matches(
        sectionOrigin, originTileSummary, titleZygisk, titleZygiskIntegrity, titleZygiskModules,
        titleZygiskSkipApps, titleZygiskTelemetry, titleZygiskLog, titleZygiskDiagnostics,
        titleVeilSwitch, titleVeilManage, titleGrantToast, titleSuPrompt, titleTempGrant,
        titleBootloop, titleBootloopThreshold, titleSecureRoot, titleAppLock, titleRootGate,
        titleLockBoundRoot, titleRootGatePanic, titleOriginGuard, titleMagicMount,
        titleMountEngine, titleMagicBackend, titleSusfsLkm, *originSearchKeywords.toTypedArray(),
    )
    val matchCoreTile = matches(
        coreTileTitle, titleProfileTemplate, descProfileTemplate, titleSuCompat, titleUmount,
        descUmount, titleAutoJailbreak, descAutoJailbreak, titleAdbRoot, titleShellSuAdbOnly,
        titleSelinux, titleSoftReboot, titleSulog, titleSelinuxHide, titleUmountDefault,
        advancedConfigTitle, *coreSearchKeywords.toTypedArray(),
    )
    val matchUpdatesTile = matches(
        updatesTileTitle, titleCheckManagerUpdate, descCheckManagerUpdate, titleCheckBeta,
        descCheckBeta, titleCheckModule, descCheckModule, updaterTitle, updaterSummary,
        themeTitle, themeSummary, titleHaptics, descHaptics,
    )
    val matchToolsTile = matches(
        toolsTileTitle, titleSendLog, titleDynamicManager, descDynamicManager, titleUmountManager,
        descUmountManager, titleTuning, descTuning, titleUninstall, titleTerminal,
    )
    val matchAboutTile = matches(aboutTitle)
    val matchBootloopAlert = matches(
        stringResource(R.string.settings_bootloop_rescued_title),
        sectionOrigin,
    )

    val hasAnyMatch = matchOriginTile ||
        (homeState.systemStatus.isFullFeatured && matchCoreTile) ||
        matchUpdatesTile || matchToolsTile || matchAboutTile ||
        (uiState.bootloopRescued && matchBootloopAlert)

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
        settingsViewModel.dispatch(SettingsUiAction.RefreshMagicMount)
        settingsViewModel.dispatch(SettingsUiAction.RefreshOriginZygisk)
        settingsViewModel.dispatch(SettingsUiAction.RefreshBootloop)
    }

    LaunchedEffect(uiState.rootGatePanicUntil) {
        val remaining = uiState.rootGatePanicUntil - System.currentTimeMillis()
        if (remaining > 0L) {
            delay(remaining)
            settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
        }
    }

    ActivityResumeEffect {
        settingsViewModel.dispatch(SettingsUiAction.RefreshOverlayPermission)
        settingsViewModel.dispatch(SettingsUiAction.RefreshMagicMount)
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.settings),
                searchText = searchQuery,
                onSearchTextChange = { searchQuery = it },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_settings),
            )
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
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val logSaved = stringResource(R.string.log_saved)
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

        Box {
            BentoGrid(
                modifier = Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .blurSource(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 5.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + bottomPadding + 15.dp,
                ),
            ) {
                // ----- Origin Lab hub --------------------------------------------
                if (matchOriginTile) {
                    item(
                        key = "origin-lab",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        OriginLabTile(
                            uiState = uiState,
                            isRootAvailable = homeState.systemStatus.isRootAvailable,
                            labLocked = !homeState.systemStatus.isRootAvailable && query.isBlank(),
                            onToggleZygisk = { enabled ->
                                settingsViewModel.dispatch(
                                    SettingsUiAction.SetOriginZygiskEnabled(enabled)
                                )
                            },
                            onOpen = { navigator.push(Route.OriginLab(query)) },
                            onUnlock = {
                                navigator.push(Route.Install(preselectedKernelUri = null))
                            },
                        )
                    }
                }

                // ----- Compact tiles ---------------------------------------------
                if (matchUpdatesTile) {
                    item(key = "updater") {
                        BentoCard(
                            modifier = Modifier.heightIn(min = 118.dp),
                            onClick = { navigator.push(Route.Updater()) },
                        ) {
                            BentoHeader(
                                icon = Icons.TwoTone.Update,
                                title = updaterTitle,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            BentoCaption(updaterSummary)
                        }
                    }
                }

                if (matchUpdatesTile) {
                    item(key = "appearance") {
                        BentoCard(
                            modifier = Modifier.heightIn(min = 118.dp),
                            onClick = { navigator.push(Route.ThemeSettings) },
                        ) {
                            BentoHeader(
                                icon = Icons.TwoTone.Tune,
                                title = themeTitle,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            BentoCaption(themeSummary)
                        }
                    }
                }

                // ----- SU & kernel band ------------------------------------------
                if (homeState.systemStatus.isFullFeatured && matchCoreTile) {
                    item(
                        key = "core",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        CoreBandTile(
                            uiState = uiState,
                            onSetSuCompatMode = { index ->
                                settingsViewModel.dispatch(SettingsUiAction.SetSuCompatMode(index))
                            },
                            onSetKernelUmount = { enabled ->
                                settingsViewModel.dispatch(SettingsUiAction.SetKernelUmount(enabled))
                            },
                            onSetAdbRoot = { enabled ->
                                settingsViewModel.dispatch(SettingsUiAction.SetAdbRoot(enabled))
                            },
                            onSetSelinux = { enabled ->
                                settingsViewModel.dispatch(
                                    SettingsUiAction.SetSelinuxPermissive(enabled)
                                )
                            },
                            onOpenAdvanced = { navigator.push(Route.AdvancedConfig(query)) },
                        )
                    }
                }

                // ----- Updates & app band ----------------------------------------
                if (matchUpdatesTile) {
                    item(
                        key = "updates-app",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        UpdatesBandTile(
                            uiState = uiState,
                            showBeta = uiState.checkManagerUpdate || query.isNotBlank(),
                            onSetManagerUpdate = { enabled ->
                                settingsViewModel.dispatch(
                                    SettingsUiAction.SetManagerUpdateCheck(enabled)
                                )
                            },
                            onSetBetaUpdate = { enabled ->
                                settingsViewModel.dispatch(
                                    SettingsUiAction.SetBetaUpdateCheck(enabled)
                                )
                            },
                            onSetModuleUpdate = { enabled ->
                                settingsViewModel.dispatch(
                                    SettingsUiAction.SetModuleUpdateCheck(enabled)
                                )
                            },
                            onSetHaptics = { enabled ->
                                settingsViewModel.dispatch(
                                    SettingsUiAction.SetHapticsEnabled(enabled)
                                )
                            },
                        )
                    }
                }

                // ----- Tools band -------------------------------------------------
                if (matchToolsTile) {
                    item(
                        key = "tools",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        ToolsBandTile(
                            showFullFeatured = homeState.systemStatus.isFullFeatured,
                            isKernelUmountEnabled = uiState.isKernelUmountEnabled,
                            showUninstall = homeState.systemStatus.lkmMode == true &&
                                !homeState.systemStatus.isLateLoadMode,
                            onSendLog = { showBottomsheet = true },
                            onTerminal = { navigator.push(Route.Terminal) },
                            onDynamicManager = { navigator.push(Route.DynamicManager) },
                            onUmountManager = { navigator.push(Route.UmountManager) },
                            onKernelTuning = { navigator.push(Route.KernelTuning) },
                            onUninstall = { showUninstallDialog = true },
                        )
                    }
                }

                // ----- Bootloop rescue alert --------------------------------------
                if (uiState.bootloopRescued && matchBootloopAlert) {
                    item(
                        key = "bootloop-alert",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        BentoCard(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            onClick = {
                                settingsViewModel.dispatch(SettingsUiAction.DismissBootloopNotice)
                            },
                        ) {
                            BentoHeader(
                                icon = Icons.TwoTone.RestartAlt,
                                title = stringResource(R.string.settings_bootloop_rescued_title),
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.TwoTone.Close,
                                        contentDescription = stringResource(R.string.close),
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            BentoCaption(
                                stringResource(
                                    R.string.settings_bootloop_rescued_summary,
                                    uiState.bootloopRescuedBoots ?: uiState.bootloopMax,
                                )
                            )
                        }
                    }
                }

                // ----- About ------------------------------------------------------
                if (matchAboutTile) {
                    item(
                        key = "about",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        BentoCard(onClick = { navigator.push(Route.About) }) {
                            BentoHeader(
                                icon = Icons.TwoTone.Info,
                                title = aboutTitle,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            BentoCaption(
                                stringResource(
                                    R.string.settings_bento_version,
                                    BuildConfig.VERSION_NAME,
                                )
                            )
                        }
                    }
                }

                if (query.isNotBlank() && !hasAnyMatch) {
                    item(
                        key = "no-match",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        SearchNoMatch()
                    }
                }
            }

            if (showBottomsheet) {
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

        UninstallChooseDialog(
            show = showUninstallDialog,
            onDismiss = { showUninstallDialog = false },
            withLoading = { block -> loadingDialog.withLoading(block) },
        )
    }
}

/**
 * Hub tile for Origin-exclusive features. Shows the master Zygisk switch plus
 * live status chips and routes to the full [OriginLabScreen].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginLabTile(
    uiState: com.originsu.manager.ui.viewmodel.SettingsUiState,
    isRootAvailable: Boolean,
    labLocked: Boolean,
    onToggleZygisk: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onUnlock: () -> Unit,
) {
    val zygiskStatus = uiState.originZygiskStatus
    val zygiskChip = when {
        uiState.originZygiskError != null -> stringResource(R.string.settings_origin_zygisk)
        zygiskStatus.injectionPaused -> stringResource(R.string.origin_zygisk_paused)
        uiState.isOriginZygiskRunning && uiState.originZygiskModuleCount > 0 ->
            stringResource(
                R.string.origin_zygisk_running_modules,
                uiState.originZygiskModuleCount,
            )
        uiState.isOriginZygiskRunning -> stringResource(R.string.origin_zygisk_running)
        uiState.isOriginZygiskEnabled -> stringResource(R.string.origin_zygisk_needs_reboot)
        else -> stringResource(R.string.settings_origin_zygisk)
    }
    val veilChip = stringResource(R.string.settings_veil)
    val rootGateChip = stringResource(R.string.settings_bento_root_gate_chip)
    val panicRemainingMillis = uiState.rootGatePanicUntil - System.currentTimeMillis()
    val panicActive = panicRemainingMillis > 0L
    val panicMinutes = ((panicRemainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)

    Box(modifier = Modifier.fillMaxWidth()) {
        BentoCard(
            modifier = when {
                !labLocked -> Modifier
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Modifier.blur(14.dp)
                else -> Modifier.alpha(0.35f)
            },
            onClick = { if (labLocked) onUnlock() else onOpen() },
        ) {
            BentoHeader(
                icon = Icons.TwoTone.Psychology,
                title = stringResource(R.string.origin_section_title),
                trailingContent = {
                    Switch(
                        checked = uiState.isOriginZygiskEnabled,
                        enabled = isRootAvailable,
                        onCheckedChange = onToggleZygisk,
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            BentoCaption(stringResource(R.string.settings_bento_origin_summary))
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BentoStatusChip(
                    text = zygiskChip,
                    active = uiState.isOriginZygiskRunning,
                )
                BentoStatusChip(
                    text = veilChip,
                    active = uiState.isVeilEnabled,
                )
                BentoStatusChip(
                    text = if (panicActive) {
                        stringResource(R.string.settings_bento_panic_chip, panicMinutes)
                    } else {
                        rootGateChip
                    },
                    active = uiState.isRootGateEnabled || panicActive,
                    isWarning = panicActive,
                )
            }
        }

        if (labLocked) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.extraLarge),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(6.dp))
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

/** SU compatibility and kernel behavior controls, kept on the landing page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreBandTile(
    uiState: com.originsu.manager.ui.viewmodel.SettingsUiState,
    onSetSuCompatMode: (Int) -> Unit,
    onSetKernelUmount: (Boolean) -> Unit,
    onSetAdbRoot: (Boolean) -> Unit,
    onSetSelinux: (Boolean) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val modeItems = listOf(
        stringResource(R.string.settings_mode_default),
        stringResource(R.string.settings_mode_disable_until_reboot),
        stringResource(R.string.settings_mode_disable_always),
    )
    val suEnabled = uiState.suStatus == "supported"

    BentoCard {
        BentoHeader(
            icon = Icons.TwoTone.Tune,
            title = stringResource(R.string.settings_bento_core_title),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_sucompat),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modeItems.forEachIndexed { index, label ->
                FilterChip(
                    selected = uiState.suCompatMode == index,
                    enabled = suEnabled,
                    onClick = { onSetSuCompatMode(index) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        BentoToggleRow(
            icon = Icons.TwoTone.RemoveCircle,
            title = stringResource(R.string.settings_kernel_umount),
            enabled = uiState.kernelUmountStatus == "supported",
            checked = uiState.isKernelUmountEnabled,
            onCheckedChange = onSetKernelUmount,
        )
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            BentoToggleRow(
                icon = Icons.TwoTone.Adb,
                title = stringResource(R.string.settings_adb_root),
                enabled = uiState.adbRootStatus == "supported",
                checked = uiState.isAdbRootEnabled,
                onCheckedChange = onSetAdbRoot,
            )
        }
        BentoToggleRow(
            icon = Icons.TwoTone.Policy,
            title = stringResource(R.string.settings_selinux_permissive),
            enabled = uiState.selinuxStatus == "supported",
            checked = uiState.isSelinuxPermissive,
            onCheckedChange = onSetSelinux,
        )
        Spacer(Modifier.height(4.dp))
        BentoJumpRow(
            icon = Icons.TwoTone.Settings,
            title = stringResource(R.string.settings_advanced_config),
            onClick = onOpenAdvanced,
        )
    }
}

/** Update checks and haptics. */
@Composable
private fun UpdatesBandTile(
    uiState: com.originsu.manager.ui.viewmodel.SettingsUiState,
    showBeta: Boolean,
    onSetManagerUpdate: (Boolean) -> Unit,
    onSetBetaUpdate: (Boolean) -> Unit,
    onSetModuleUpdate: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
) {
    BentoCard {
        BentoHeader(
            icon = Icons.TwoTone.Update,
            title = stringResource(R.string.settings_bento_updates_title),
        )
        Spacer(Modifier.height(8.dp))
        BentoToggleRow(
            icon = Icons.TwoTone.Update,
            title = stringResource(R.string.settings_check_manager_update),
            checked = uiState.checkManagerUpdate,
            onCheckedChange = onSetManagerUpdate,
        )
        if (showBeta) {
            BentoToggleRow(
                icon = Icons.TwoTone.Science,
                title = stringResource(R.string.settings_check_beta_update),
                checked = uiState.checkBetaUpdate,
                onCheckedChange = onSetBetaUpdate,
            )
        }
        BentoToggleRow(
            icon = Icons.TwoTone.Extension,
            title = stringResource(R.string.settings_check_module_update),
            checked = uiState.checkModuleUpdate,
            onCheckedChange = onSetModuleUpdate,
        )
        BentoToggleRow(
            icon = Icons.Filled.Vibration,
            title = stringResource(R.string.settings_haptics),
            checked = uiState.isHapticsEnabled,
            onCheckedChange = onSetHaptics,
        )
    }
}

/** Diagnostics and maintenance actions. */
@Composable
private fun ToolsBandTile(
    showFullFeatured: Boolean,
    isKernelUmountEnabled: Boolean,
    showUninstall: Boolean,
    onSendLog: () -> Unit,
    onTerminal: () -> Unit,
    onDynamicManager: () -> Unit,
    onUmountManager: () -> Unit,
    onKernelTuning: () -> Unit,
    onUninstall: () -> Unit,
) {
    BentoCard {
        BentoHeader(
            icon = Icons.TwoTone.Extension,
            title = stringResource(R.string.tools),
        )
        Spacer(Modifier.height(8.dp))
        BentoJumpRow(
            icon = Icons.TwoTone.BugReport,
            title = stringResource(R.string.send_log),
            onClick = onSendLog,
        )
        BentoJumpRow(
            icon = Icons.TwoTone.Terminal,
            title = stringResource(R.string.terminal_title),
            onClick = onTerminal,
        )
        if (showFullFeatured) {
            BentoJumpRow(
                icon = Icons.TwoTone.Dashboard,
                title = stringResource(R.string.dynamic_manager_title),
                onClick = onDynamicManager,
            )
            if (isKernelUmountEnabled) {
                BentoJumpRow(
                    icon = Icons.TwoTone.FolderOff,
                    title = stringResource(R.string.umount_path_manager),
                    onClick = onUmountManager,
                )
            }
            BentoJumpRow(
                icon = Icons.TwoTone.Tune,
                title = stringResource(R.string.kernel_tuning),
                onClick = onKernelTuning,
            )
        }
        if (showUninstall) {
            BentoJumpRow(
                icon = Icons.TwoTone.Delete,
                title = stringResource(R.string.settings_uninstall),
                trailingIcon = Icons.TwoTone.DeleteForever,
                onClick = onUninstall,
            )
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
        androidx.compose.foundation.layout.Row(
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

/**
 * Uninstall chooser rendered from the Tools band. Mirrors the previous
 * in-list uninstall widget but uses the dialog form so it fits inside a tile.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun UninstallChooseDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    withLoading: suspend (suspend () -> Unit) -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ksuCliRepository = koinInject<KsuCliRepository>()
    val snackBarHost = LocalSnackbarHost.current
    val confirmDialog = rememberConfirmDialog()
    val options = remember {
        listOf(
            UninstallType.TEMPORARY,
            UninstallType.PERMANENT,
            UninstallType.RESTORE_STOCK_IMAGE,
        )
    }

    SettingsChooseDialog(
        show = show,
        title = stringResource(R.string.settings_uninstall),
        items = options.map { stringResource(it.title) },
        itemDescriptions = options.map {
            if (it.message != 0) stringResource(it.message) else null
        },
        selectedIndex = -1,
        onDismiss = onDismiss,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let { uninstallType ->
                scope.launch {
                    val result = uninstallConfirm(
                        confirmDialog = confirmDialog,
                        context = context,
                        uninstallType = uninstallType,
                    )
                    if (result == ConfirmResult.Confirmed) {
                        when (uninstallType) {
                            UninstallType.TEMPORARY -> {
                                var success = false
                                withLoading {
                                    success = ksuCliRepository.uninstallTemporarily()
                                }
                                snackBarHost.showReplacingSnackbar(
                                    context.getString(
                                        if (success) {
                                            R.string.settings_uninstall_temporary_done
                                        } else {
                                            R.string.settings_uninstall_temporary_failed
                                        }
                                    )
                                )
                            }
                            UninstallType.PERMANENT -> withLoading {
                                navigator.push(Route.Flash.uninstall())
                            }
                            UninstallType.RESTORE_STOCK_IMAGE -> withLoading {
                                navigator.push(Route.Flash.restore())
                            }
                            UninstallType.NONE -> Unit
                        }
                    }
                }
            }
        },
    )
}

private suspend fun uninstallConfirm(
    confirmDialog: com.originsu.manager.ui.component.ConfirmDialogHandle,
    context: android.content.Context,
    uninstallType: UninstallType,
): ConfirmResult = confirmDialog.awaitConfirm(
    title = context.getString(uninstallType.title),
    content = context.getString(uninstallType.message),
)

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
