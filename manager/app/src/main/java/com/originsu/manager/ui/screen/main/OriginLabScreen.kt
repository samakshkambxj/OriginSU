package com.originsu.manager.ui.screen.main

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Dashboard
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.OpenInNew
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.Psychology
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.model.SuiStatus
import com.originsu.manager.data.packageinfo.InstalledPackageRepository
import com.originsu.manager.data.curtain.OriginCurtainRepository
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.data.shell.OriginZygiskAppInfo
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SearchNoMatch
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.BentoCaption
import com.originsu.manager.ui.component.settings.BentoCard
import com.originsu.manager.ui.component.settings.BentoGrid
import com.originsu.manager.ui.component.settings.BentoStatusChip
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.canAuthenticateSecureRoot
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
import java.io.File
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Origin Lab settings, split out of the main settings bento grid.
 *
 * Everything here is an Origin-exclusive feature that only makes sense once the
 * OriginSU driver is available. The main settings tab exposes a single hub tile
 * for these rows and routes here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OriginLabScreen(
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
    val ksuCliRepository = koinInject<KsuCliRepository>()
    val originCurtainRepository = koinInject<OriginCurtainRepository>()
    val installedPackageRepository = koinInject<InstalledPackageRepository>()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()

    var showZygiskModules by remember { mutableStateOf(false) }
    var showZygiskTelemetry by remember { mutableStateOf(false) }
    var showZygiskLog by remember { mutableStateOf(false) }
    var showZygiskSkip by remember { mutableStateOf(false) }
    var zygiskApps by remember { mutableStateOf<List<OriginZygiskAppInfo>>(emptyList()) }
    var pendingDiagnosticsFile by remember { mutableStateOf<File?>(null) }

    val query = searchQuery.trim()
    fun matches(vararg texts: String): Boolean {
        if (query.isBlank()) return true
        return texts.any { it.contains(query, ignoreCase = true) }
    }

    val titleZygisk = stringResource(R.string.settings_origin_zygisk)
    val zygiskRunning = stringResource(R.string.origin_zygisk_running)
    val zygiskRunningModules = stringResource(
        R.string.origin_zygisk_running_modules,
        uiState.originZygiskModuleCount,
    )
    val zygiskRunningRoot = if (uiState.originZygiskRoot.isNotBlank()) {
        stringResource(R.string.origin_zygisk_running_root, uiState.originZygiskRoot)
    } else {
        null
    }
    val zygiskRunningRootModules = if (
        uiState.originZygiskRoot.isNotBlank() && uiState.originZygiskModuleCount > 0
    ) {
        stringResource(
            R.string.origin_zygisk_running_root_modules,
            uiState.originZygiskRoot,
            uiState.originZygiskModuleCount,
        )
    } else {
        null
    }
    val zygiskError = uiState.originZygiskError
    val zygiskIssue = if (zygiskError != null) {
        stringResource(R.string.origin_zygisk_issue, zygiskError)
    } else {
        null
    }
    val zygiskNeedsReboot = stringResource(R.string.origin_zygisk_needs_reboot)
    val zygiskDefault = stringResource(R.string.settings_origin_zygisk_summary)
    val zygiskStatus = uiState.originZygiskStatus
    val titleZygiskIntegrity = stringResource(R.string.origin_zygisk_integrity)
    val titleZygiskUmount = stringResource(R.string.origin_zygisk_umount)
    val descZygiskUmount = stringResource(R.string.origin_zygisk_umount_summary)
    val titleZygiskModules = stringResource(R.string.origin_zygisk_modules)
    val titleZygiskSkipApps = stringResource(R.string.origin_zygisk_skip_apps)
    val titleZygiskPause = stringResource(R.string.origin_zygisk_pause)
    val descZygiskPause = stringResource(R.string.origin_zygisk_pause_summary)
    val titleZygiskTelemetry = stringResource(R.string.origin_zygisk_telemetry)
    val titleZygiskLog = stringResource(R.string.origin_zygisk_log)
    val descZygiskLog = stringResource(R.string.origin_zygisk_log_summary)
    val titleZygiskDiagnostics = stringResource(R.string.origin_zygisk_diagnostics)
    val descZygiskDiagnostics = stringResource(R.string.origin_zygisk_diagnostics_summary)
    val diagnosticsFailed = stringResource(R.string.origin_zygisk_diagnostics_failed)
    val diagnosticsSaved = stringResource(R.string.origin_zygisk_diagnostics_saved)

    // Origin Sui: built-in Shizuku API provider. Its switch stays off until ksud
    // reports both a usable Zygisk provider and the engine, mirroring ksud's gate.
    val titleSui = stringResource(R.string.settings_sui)
    val suiStatus = uiState.suiStatus
    val suiProviderReady = suiStatus?.let { it.zygiskEnabled && it.engineAvailable } == true
    val suiReasonText = when (suiStatus?.reason) {
        "zygisk_disabled" -> stringResource(R.string.sui_reason_zygisk_disabled)
        "zygisk_engine_missing" -> stringResource(R.string.sui_reason_zygisk_engine_missing)
        "zygisk_injection_failed" -> stringResource(R.string.sui_reason_zygisk_injection_failed)
        "zygisk_startup_protection" ->
            stringResource(R.string.sui_reason_zygisk_startup_protection)
        "payload_missing" -> stringResource(R.string.sui_reason_payload_missing)
        "module_disabled" -> stringResource(R.string.sui_reason_module_disabled)
        "conflict_existing_sui" -> stringResource(R.string.sui_reason_conflict_existing_sui)
        "enabled_externally" -> stringResource(R.string.sui_reason_enabled_externally)
        "not_installed" -> stringResource(R.string.sui_reason_not_installed)
        else -> suiStatus?.detail?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.settings_sui_summary)
    }
    val suiSummary = when {
        suiStatus == null -> stringResource(R.string.sui_unavailable)
        suiStatus.state == SuiStatus.STATE_ENABLED -> stringResource(
            R.string.sui_active,
            suiStatus.payloadVersion,
        )
        suiStatus.state == SuiStatus.STATE_PENDING_REBOOT ->
            stringResource(R.string.sui_pending_reboot)
        suiStatus.state == SuiStatus.STATE_EXTERNAL_UNVERIFIED ->
            stringResource(R.string.sui_external_provider)
        else -> suiReasonText
    }
    val suiHeadline = when (suiStatus?.state) {
        SuiStatus.STATE_ENABLED, SuiStatus.STATE_EXTERNAL_UNVERIFIED ->
            stringResource(R.string.origin_lab_value_active)
        SuiStatus.STATE_PENDING_REBOOT -> stringResource(R.string.origin_lab_value_reboot)
        SuiStatus.STATE_ERROR, SuiStatus.STATE_UNAVAILABLE ->
            stringResource(R.string.origin_lab_value_attention)
        else -> stringResource(R.string.origin_lab_value_off)
    }
    val titleSuiPermissions = stringResource(R.string.sui_manage_permissions)
    val descSuiPermissions = stringResource(R.string.sui_manage_permissions_summary)
    val titleSuiRepair = stringResource(R.string.sui_repair)
    val descSuiRepair = stringResource(R.string.sui_repair_summary)
    val titleSuiConflicts = stringResource(R.string.sui_conflicts)
    val suiConflicts = suiStatus?.conflicts.orEmpty()


    val zygiskIntegritySummary = when {
        !zygiskStatus.integrityOk -> stringResource(
            R.string.origin_zygisk_integrity_repair,
            zygiskStatus.integrityError ?: stringResource(R.string.origin_zygisk_integrity_failed),
        )
        zygiskStatus.expectedVersion.isNotBlank() &&
            zygiskStatus.payloadVersion != zygiskStatus.expectedVersion -> stringResource(
            R.string.origin_zygisk_payload_mismatch,
            zygiskStatus.payloadVersion.ifBlank { "?" },
            zygiskStatus.expectedVersion,
        )
        else -> stringResource(
            R.string.origin_zygisk_integrity_ok,
            zygiskStatus.payloadVersion.ifBlank { "?" },
        )
    }
    val zygiskModulesSummary = stringResource(
        R.string.origin_zygisk_modules_summary,
        zygiskStatus.installedModules.count { it.loaded },
        zygiskStatus.installedModules.size,
        zygiskStatus.installedModules.count { it.quarantined || it.zygiskDisabled },
    )
    val zygiskSkipSummary = stringResource(
        R.string.origin_zygisk_skip_apps_summary,
        zygiskStatus.skippedPackages.size,
    )
    val zygiskTelemetrySummary = stringResource(
        R.string.origin_zygisk_telemetry_summary,
        zygiskStatus.telemetry.size,
    )
    val zygiskSummary = when {
        zygiskIssue != null -> zygiskIssue
        zygiskStatus.injectionPaused -> stringResource(R.string.origin_zygisk_paused)
        uiState.isOriginZygiskRunning -> zygiskRunningRootModules
            ?: zygiskRunningRoot
            ?: zygiskRunningModules.takeIf { uiState.originZygiskModuleCount > 0 }
            ?: zygiskRunning
        uiState.isOriginZygiskEnabled -> zygiskNeedsReboot
        else -> zygiskDefault
    }
    val zygiskHeadline = when {
        zygiskError != null -> stringResource(R.string.origin_lab_value_attention)
        uiState.isOriginZygiskRunning -> stringResource(R.string.origin_lab_value_active)
        uiState.isOriginZygiskEnabled -> stringResource(R.string.origin_lab_value_reboot)
        else -> stringResource(R.string.origin_lab_value_off)
    }
    val titleZygiskBootGuard = stringResource(R.string.origin_zygisk_bootguard_reset)
    val bootGuardResetDone = stringResource(R.string.origin_zygisk_bootguard_reset_done)
    val bootGuardResetFailed = stringResource(R.string.origin_zygisk_bootguard_reset_failed)
    val zygiskBootGuardSummary = if (zygiskStatus.bootGuardLatched) {
        stringResource(R.string.origin_zygisk_bootguard_latched)
    } else {
        stringResource(
            R.string.origin_zygisk_bootguard_failures,
            zygiskStatus.bootGuardFailures,
        )
    }

    val titleCurtain = stringResource(R.string.origin_curtain)
    val curtainStatus = uiState.originCurtainStatus
    val curtainSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val curtainVersion = curtainStatus.moduleVersion.ifBlank { "?" }
    val curtainSummary = when {
        !curtainSupported -> stringResource(R.string.origin_curtain_unsupported)
        !curtainStatus.zygiskEnabled -> stringResource(R.string.origin_curtain_requires_zygisk)
        !curtainStatus.moduleInstalled -> stringResource(R.string.origin_curtain_not_installed)
        !curtainStatus.moduleEnabled -> stringResource(R.string.origin_curtain_disabled)
        curtainStatus.moduleLoaded ->
            stringResource(R.string.origin_curtain_active, curtainVersion)
        else -> stringResource(R.string.origin_curtain_needs_reboot)
    }
    val curtainHeadline = when {
        !curtainSupported || !curtainStatus.zygiskEnabled -> stringResource(
            R.string.origin_lab_value_off
        )
        !curtainStatus.moduleInstalled -> stringResource(R.string.origin_lab_value_not_installed)
        !curtainStatus.moduleEnabled -> stringResource(R.string.origin_lab_value_off)
        curtainStatus.moduleLoaded -> stringResource(R.string.origin_lab_value_active)
        else -> stringResource(R.string.origin_lab_value_reboot)
    }
    val curtainTileCaption = when {
        !curtainSupported -> stringResource(R.string.origin_curtain_unsupported)
        !curtainStatus.moduleInstalled -> stringResource(R.string.origin_curtain_not_installed)
        else -> stringResource(R.string.origin_lab_caption_curtain, curtainVersion)
    }
    val curtainUnsupported = stringResource(R.string.origin_curtain_unsupported)
    val curtainDownloadFailed = stringResource(R.string.origin_curtain_download_failed)
    val curtainOpenFailed = stringResource(R.string.origin_curtain_open_failed)
    val curtainMissing = stringResource(R.string.origin_curtain_status_missing)
    val curtainZygiskStatus = when {
        !curtainStatus.zygiskEnabled -> stringResource(R.string.origin_curtain_zygisk_off)
        zygiskStatus.running -> stringResource(R.string.origin_curtain_zygisk_running)
        else -> stringResource(R.string.origin_curtain_zygisk_pending)
    }
    val curtainDetail = stringResource(
        R.string.origin_curtain_status_detail,
        if (curtainStatus.moduleInstalled) {
            stringResource(R.string.origin_curtain_status_version, curtainVersion)
        } else {
            curtainMissing
        },
        if (curtainStatus.managerInstalled) {
            stringResource(
                R.string.origin_curtain_status_version,
                curtainStatus.managerVersion.ifBlank { "?" },
            )
        } else {
            curtainMissing
        },
        curtainZygiskStatus,
    )

    val titleVeilSwitch = stringResource(R.string.settings_veil)
    val descVeilDefault = stringResource(R.string.settings_veil_summary)
    val titleVeilManage = stringResource(R.string.veil)
    val descVeilManage = stringResource(R.string.veil_manage_summary)
    val veilSummary = when (uiState.veilStatus) {
        "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
        "managed" -> stringResource(R.string.feature_status_managed_summary)
        else -> descVeilDefault
    }
    val veilHeadline = when {
        uiState.veilStatus == "managed" -> stringResource(R.string.origin_lab_value_active)
        uiState.veilStatus == "supported" && uiState.isVeilEnabled ->
            stringResource(R.string.origin_lab_value_on)
        else -> stringResource(R.string.origin_lab_value_off)
    }
    val titleGrantToast = stringResource(R.string.settings_grant_toast)
    val descGrantToast = stringResource(R.string.settings_grant_toast_summary)
    val titleSuPrompt = stringResource(R.string.settings_su_prompt)
    val descSuPrompt = stringResource(R.string.settings_su_prompt_summary)
    val descSuPromptUnsupported = stringResource(R.string.settings_su_prompt_unsupported)
    val suPromptSummary =
        if (uiState.isSuPromptSupported) descSuPrompt else descSuPromptUnsupported
    val titleTempGrant = stringResource(R.string.settings_temp_grant)
    val descTempGrant = stringResource(R.string.settings_temp_grant_summary)
    val titleBootloop = stringResource(R.string.settings_bootloop_protection)
    val titleBootloopThreshold = stringResource(R.string.settings_bootloop_threshold)
    val titleSecureRoot = stringResource(R.string.settings_secure_root)
    val descSecureRoot = stringResource(R.string.settings_secure_root_summary)
    val titleAppLock = stringResource(R.string.settings_app_lock)
    val descAppLock = stringResource(R.string.settings_app_lock_summary)
    val titleAppLockTimeout = stringResource(R.string.settings_app_lock_timeout)
    val titleRootGate = stringResource(R.string.settings_root_gate)
    val descRootGate = stringResource(R.string.settings_root_gate_summary)
    val titleLockBoundRoot = stringResource(R.string.settings_lock_bound_root)
    val descLockBoundRoot = stringResource(R.string.settings_lock_bound_root_summary)
    val titleRootGatePanic = stringResource(R.string.settings_root_gate_panic)
    val descRootGatePanic = stringResource(R.string.settings_root_gate_panic_summary)
    val titleOriginGuard = stringResource(R.string.settings_originguard)
    val descOriginGuard = stringResource(R.string.settings_originguard_summary)
    val titleOriginMount = stringResource(R.string.origin_mount_title)
    val descOriginMount = stringResource(R.string.origin_lab_caption_origin_mount)
    val titleTerminal = stringResource(R.string.terminal_title)
    val descTerminal = stringResource(R.string.origin_lab_caption_terminal)
    val titleMountEngine = stringResource(R.string.settings_mount_engine)
    val titleSusfsLkm = stringResource(R.string.settings_susfs_lkm)
    val descSusfsLkm = stringResource(R.string.settings_susfs_lkm_summary)

    val matchZygiskPanel = matches(
        titleZygisk,
        titleZygiskIntegrity,
        titleZygiskUmount,
        titleZygiskModules,
        titleZygiskSkipApps,
        titleZygiskPause,
        titleZygiskTelemetry,
        titleZygiskLog,
        titleZygiskDiagnostics,
        titleZygiskBootGuard,
    )
    val matchZygisk = matches(titleZygisk, zygiskSummary)
    val matchVeilSwitch = matches(titleVeilSwitch, veilSummary)
    val matchVeilManage = matches(titleVeilManage, descVeilManage)
    val matchGrantToast = matches(titleGrantToast, descGrantToast)
    val matchSuPrompt = matches(titleSuPrompt, suPromptSummary)
    val matchTempGrant = matches(titleTempGrant, descTempGrant)
    val matchBootloop = matches(titleBootloop)
    val matchBootloopThreshold = matches(titleBootloopThreshold)
    val matchSecureRoot = matches(titleSecureRoot, descSecureRoot)
    val matchAppLock = matches(titleAppLock, descAppLock, titleAppLockTimeout)
    val matchRootGate = matches(titleRootGate, descRootGate)
    val matchLockBoundRoot = matches(titleLockBoundRoot, descLockBoundRoot)
    val matchRootGatePanic = matches(titleRootGatePanic, descRootGatePanic)
    val matchOriginGuard = matches(titleOriginGuard, descOriginGuard)
    val matchOriginMount = matches(titleOriginMount, descOriginMount)
    val matchTerminal = matches(titleTerminal, descTerminal)
    val matchMountEngine = matches(titleMountEngine)
    val matchSusfsLkm = matches(titleSusfsLkm, descSusfsLkm)
    val matchCurtain = matches(titleCurtain, curtainSummary)
    val matchSui = matches(titleSui, suiSummary)
    val matchSuiPermissions = matches(titleSuiPermissions, descSuiPermissions)
    val matchSuiRepair = matches(titleSuiRepair, descSuiRepair)

    val hasAnyMatch = listOf(
        matchZygisk, matchZygiskPanel, matchVeilSwitch, matchVeilManage, matchGrantToast,
        matchSuPrompt, matchTempGrant, matchBootloop, matchBootloopThreshold, matchSecureRoot,
        matchAppLock, matchRootGate, matchLockBoundRoot, matchRootGatePanic, matchOriginGuard,
        matchOriginMount, matchMountEngine, matchSusfsLkm,
        matchCurtain, matchSui, matchSuiPermissions, matchSuiRepair, matchTerminal,
    ).any { it }

    LaunchedEffect(Unit) {
        settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
        settingsViewModel.dispatch(SettingsUiAction.RefreshMagicMount)
        settingsViewModel.dispatch(SettingsUiAction.RefreshOriginZygisk)
        settingsViewModel.dispatch(SettingsUiAction.RefreshOriginCurtain)
        settingsViewModel.dispatch(SettingsUiAction.RefreshSui)
        settingsViewModel.dispatch(SettingsUiAction.RefreshBootloop)
    }

    LaunchedEffect(showZygiskSkip) {
        if (showZygiskSkip) {
            val packages = runCatching {
                installedPackageRepository.refresh()
                installedPackageRepository.packages.value
            }.getOrDefault(emptyList())
            zygiskApps = packages.map { pkg ->
                OriginZygiskAppInfo(
                    packageName = pkg.packageName,
                    label = pkg.appLabel.ifBlank { pkg.packageName },
                    uid = pkg.uid,
                    isSystem = pkg.isSystem,
                )
            }
        }
    }

    LaunchedEffect(uiState.rootGatePanicUntil) {
        val remaining = uiState.rootGatePanicUntil - System.currentTimeMillis()
        if (remaining > 0L) {
            delay(remaining)
            settingsViewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
        }
    }

    ActivityResumeEffect {
        settingsViewModel.dispatch(SettingsUiAction.RefreshMagicMount)
        settingsViewModel.dispatch(SettingsUiAction.RefreshOriginCurtain)
    }

    val installCurtain: () -> Unit = {
        scope.launch {
            val file = loadingDialog.withLoading {
                originCurtainRepository.downloadLatestRelease()
            }
            if (file == null) {
                snackBarHost.showReplacingSnackbar(curtainDownloadFailed)
            } else {
                navigator.push(Route.Flash.module(Uri.fromFile(file).toString()))
            }
        }
        Unit
    }

    val exportDiagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val file = pendingDiagnosticsFile
        pendingDiagnosticsFile = null
        if (uri == null || file == null) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { it.copyTo(output) }
                }
            }
            file.delete()
            withContext(Dispatchers.Main) {
                snackBarHost.showReplacingSnackbar(diagnosticsSaved)
            }
        }
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.origin_section_title),
                searchText = searchQuery,
                onSearchTextChange = { searchQuery = it },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_origin_lab),
            )
        },
        snackbarHost = {
            SwipeableSnackbarHost(hostState = snackBarHost)
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(includeBottom = false)
    ) { innerPadding ->
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
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            // ----- OriginZygisk hero ------------------------------------------
            if (matchZygisk || matchZygiskPanel) {
                item(key = "origin-zygisk-hero", span = StaggeredGridItemSpan.FullLine) {
                    BentoCard {
                        Row(verticalAlignment = Alignment.Top) {
                            LabIconBadge(
                                icon = Icons.TwoTone.Psychology,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 52.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleZygisk,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = zygiskHeadline,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = zygiskSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = uiState.isOriginZygiskEnabled,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.dispatch(
                                        SettingsUiAction.SetOriginZygiskEnabled(enabled)
                                    )
                                },
                            )
                        }
                        if (zygiskStatus.installed) {
                            Spacer(Modifier.height(14.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BentoStatusChip(
                                    text = zygiskHeadline,
                                    active = uiState.isOriginZygiskRunning,
                                    isWarning = zygiskError != null,
                                )
                                BentoStatusChip(
                                    text = if (zygiskStatus.integrityOk) {
                                        stringResource(R.string.origin_lab_chip_integrity_ok)
                                    } else {
                                        stringResource(R.string.origin_lab_chip_integrity_bad)
                                    },
                                    active = zygiskStatus.integrityOk,
                                    isWarning = !zygiskStatus.integrityOk,
                                )
                                BentoStatusChip(
                                    text = stringResource(
                                        R.string.origin_lab_chip_modules,
                                        zygiskStatus.installedModules.size,
                                    ),
                                    active = zygiskStatus.installedModules.any { it.loaded },
                                )
                            }
                        }
                    }
                }
            }

            // ----- Feature tiles ---------------------------------------------
            if (matchSui) {
                item(key = "tile-sui") {
                    LabFeatureTile(
                        icon = Icons.TwoTone.WaterDrop,
                        accent = MaterialTheme.colorScheme.tertiary,
                        label = stringResource(R.string.origin_lab_label_sui),
                        value = suiHeadline,
                        caption = suiSummary,
                    )
                }
            }
            if (matchCurtain) {
                item(key = "tile-curtain") {
                    LabFeatureTile(
                        icon = Icons.TwoTone.Visibility,
                        accent = MaterialTheme.colorScheme.secondary,
                        label = stringResource(R.string.origin_lab_label_curtain),
                        value = curtainHeadline,
                        caption = curtainTileCaption,
                    )
                }
            }
            if (matchVeilSwitch || matchVeilManage) {
                item(key = "tile-veil") {
                    LabFeatureTile(
                        icon = Icons.TwoTone.Shield,
                        accent = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.origin_lab_label_veil),
                        value = veilHeadline,
                        caption = veilSummary,
                    )
                }
            }
            if (matchOriginGuard) {
                item(key = "tile-guard") {
                    LabFeatureTile(
                        icon = Icons.TwoTone.Policy,
                        accent = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.origin_lab_label_guard),
                        value = if (uiState.isOriginGuardEnabled) {
                            stringResource(R.string.origin_lab_value_on)
                        } else {
                            stringResource(R.string.origin_lab_value_off)
                        },
                        caption = stringResource(R.string.origin_lab_caption_guard),
                    )
                }
            }
            if (matchSusfsLkm) {
                item(key = "tile-kernel") {
                    LabFeatureTile(
                        icon = Icons.TwoTone.Extension,
                        accent = MaterialTheme.colorScheme.tertiary,
                        label = stringResource(R.string.origin_lab_label_kernel),
                        value = stringResource(R.string.origin_lab_value_susfs),
                        caption = stringResource(R.string.origin_lab_caption_kernel),
                    )
                }
            }
            if (matchBootloop || matchBootloopThreshold) {
                item(key = "tile-boot") {
                    LabFeatureTile(
                        icon = Icons.TwoTone.RestartAlt,
                        accent = MaterialTheme.colorScheme.secondary,
                        label = stringResource(R.string.origin_lab_label_boot),
                        value = if (uiState.isBootloopEnabled) {
                            stringResource(R.string.origin_lab_value_on)
                        } else {
                            stringResource(R.string.origin_lab_value_off)
                        },
                        caption = stringResource(R.string.origin_lab_caption_boot, uiState.bootloopMax),
                    )
                }
            }

            // ----- OriginZygisk controls -------------------------------------
            if (matchZygiskPanel && zygiskStatus.installed) {
                item(key = "section-zygisk", span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        LabSectionHeader(stringResource(R.string.origin_lab_group_zygisk))
                        SegmentedColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            item(visible = matchZygiskPanel) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Tune,
                                    title = titleZygiskIntegrity,
                                    description = zygiskIntegritySummary,
                                    onClick = {
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.RepairOriginZygisk
                                        )
                                    },
                                )
                            }

                            item(visible = matchZygiskPanel) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.FolderOff,
                                    title = titleZygiskUmount,
                                    description = descZygiskUmount,
                                    checked = zygiskStatus.umountEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetOriginZygiskUmount(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchZygiskPanel) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = titleZygiskModules,
                                    description = zygiskModulesSummary,
                                    onClick = { showZygiskModules = true },
                                )
                            }

                            item(visible = matchZygiskPanel) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Policy,
                                    title = titleZygiskSkipApps,
                                    description = zygiskSkipSummary,
                                    onClick = { showZygiskSkip = true },
                                )
                            }

                            item(
                                visible = matchZygiskPanel && uiState.isOriginZygiskEnabled
                            ) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Visibility,
                                    title = titleZygiskPause,
                                    description = descZygiskPause,
                                    checked = zygiskStatus.injectionPaused,
                                    enabled = zygiskStatus.running,
                                    onCheckedChange = { paused ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetOriginZygiskInjectionPaused(paused)
                                        )
                                    },
                                )
                            }

                            item(visible = matchZygiskPanel) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Dashboard,
                                    title = titleZygiskTelemetry,
                                    description = zygiskTelemetrySummary,
                                    onClick = { showZygiskTelemetry = true },
                                )
                            }

                            item(visible = matchZygiskPanel) {
                                SettingsJumpPageWidget(
                                    icon = Icons.AutoMirrored.TwoTone.Article,
                                    title = titleZygiskLog,
                                    description = descZygiskLog,
                                    onClick = { showZygiskLog = true },
                                )
                            }

                            item(visible = matchZygiskPanel) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.BugReport,
                                    title = titleZygiskDiagnostics,
                                    description = descZygiskDiagnostics,
                                    onClick = {
                                        scope.launch {
                                            val file = loadingDialog.withLoading {
                                                withContext(Dispatchers.IO) {
                                                    ksuCliRepository.buildOriginZygiskDiagnostics()
                                                }
                                            }
                                            if (file == null) {
                                                snackBarHost.showReplacingSnackbar(diagnosticsFailed)
                                            } else {
                                                pendingDiagnosticsFile = file
                                                exportDiagnosticsLauncher.launch(file.name)
                                            }
                                        }
                                    },
                                )
                            }

                            item(
                                visible = matchZygiskPanel &&
                                    (zygiskStatus.bootGuardLatched || zygiskStatus.bootGuardFailures > 0)
                            ) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.RestartAlt,
                                    title = titleZygiskBootGuard,
                                    description = zygiskBootGuardSummary,
                                    onClick = {
                                        scope.launch {
                                            val reset = withContext(Dispatchers.IO) {
                                                ksuCliRepository.resetOriginZygiskBootGuard()
                                            }
                                            if (reset) {
                                                settingsViewModel.dispatch(
                                                    SettingsUiAction.RefreshOriginZygisk
                                                )
                                                snackBarHost.showReplacingSnackbar(
                                                    bootGuardResetDone
                                                )
                                            } else {
                                                snackBarHost.showReplacingSnackbar(
                                                    bootGuardResetFailed
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ----- Sui --------------------------------------------------------
            if (matchSui || matchSuiPermissions || matchSuiRepair) {
                item(key = "section-sui", span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        LabSectionHeader(stringResource(R.string.origin_lab_group_sui))
                        SegmentedColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            item(visible = matchSui) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.WaterDrop,
                                    title = titleSui,
                                    description = suiSummary,
                                    enabled = suiStatus != null,
                                    checked = suiStatus?.selected == true,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetSuiEnabled(enabled)
                                        )
                                    },
                                )
                            }

                            item(
                                visible = matchSuiPermissions &&
                                    suiStatus != null && suiStatus.installed && suiProviderReady
                            ) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Policy,
                                    title = titleSuiPermissions,
                                    description = descSuiPermissions,
                                    onClick = {
                                        navigator.push(Route.SuiPermissions)
                                    },
                                )
                            }

                            item(
                                visible = matchSuiRepair && suiStatus != null &&
                                    (!suiStatus.payloadCurrent || suiStatus.moduleDisabled)
                            ) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.RestartAlt,
                                    title = titleSuiRepair,
                                    description = descSuiRepair,
                                    onClick = {
                                        settingsViewModel.dispatch(SettingsUiAction.RepairSui)
                                    },
                                )
                            }

                            item(
                                visible = matchSui && suiStatus != null &&
                                    suiConflicts.isNotEmpty()
                            ) {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.BugReport,
                                    title = titleSuiConflicts,
                                    description = suiConflicts.joinToString(", "),
                                )
                            }

                            item(visible = matchSui && suiStatus?.needsDiagnostics == true) {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Info,
                                    title = titleSui,
                                    description = suiSummary,
                                    isError = true,
                                )
                            }

                            item(visible = matchSui && suiStatus != null) {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Dashboard,
                                    title = stringResource(R.string.sui_default_permission),
                                    description = stringResource(
                                        R.string.sui_detail_zygisk_injection,
                                        suiStatus?.injectionState.orEmpty().ifBlank { "?" },
                                    ) + " · " + stringResource(
                                        R.string.sui_detail_daemon,
                                        if (suiStatus?.daemonHealthy == true) {
                                            stringResource(R.string.sui_detail_daemon_ok)
                                        } else {
                                            stringResource(R.string.sui_detail_daemon_bad)
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // ----- Origin Curtain ---------------------------------------------
            if (matchCurtain) {
                item(key = "section-curtain", span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        LabSectionHeader(stringResource(R.string.origin_lab_group_curtain))
                        SegmentedColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            item(visible = matchCurtain) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Visibility,
                                    title = titleCurtain,
                                    description = curtainSummary,
                                    onClick = {
                                        when {
                                            !curtainSupported -> scope.launch {
                                                snackBarHost.showReplacingSnackbar(
                                                    curtainUnsupported
                                                )
                                            }

                                            !curtainStatus.zygiskEnabled -> {
                                                settingsViewModel.dispatch(
                                                    SettingsUiAction.SetOriginZygiskEnabled(true)
                                                )
                                            }

                                            !curtainStatus.moduleInstalled -> installCurtain()

                                            !curtainStatus.moduleEnabled ->
                                                navigator.push(Route.Module)

                                            else -> navigator.push(Route.Curtain)
                                        }
                                    },
                                )
                            }

                            item(visible = matchCurtain) {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Info,
                                    title = stringResource(R.string.origin_curtain_status),
                                    description = curtainDetail,
                                )
                            }

                            item(
                                visible = matchCurtain && curtainSupported &&
                                    curtainStatus.zygiskEnabled && curtainStatus.moduleInstalled
                            ) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Download,
                                    title = stringResource(R.string.origin_curtain_update),
                                    description = stringResource(
                                        R.string.origin_curtain_update_summary,
                                        curtainVersion,
                                    ),
                                    onClick = { installCurtain() },
                                )
                            }

                            item(visible = matchCurtain && curtainStatus.moduleInstalled) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = stringResource(R.string.origin_curtain_manage),
                                    description = stringResource(R.string.origin_curtain_manage_summary),
                                    onClick = { navigator.push(Route.Module) },
                                )
                            }

                            item(visible = matchCurtain && curtainStatus.managerInstalled) {
                                val managerIntent = remember(curtainStatus.managerInstalled) {
                                    originCurtainRepository.managerLaunchIntent()
                                }
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.OpenInNew,
                                    title = stringResource(R.string.origin_curtain_open_upstream),
                                    description = stringResource(
                                        R.string.origin_curtain_open_upstream_summary,
                                    ),
                                    onClick = {
                                        val launched = managerIntent?.let { intent ->
                                            runCatching { context.startActivity(intent) }.isSuccess
                                        } == true
                                        if (!launched) {
                                            scope.launch {
                                                snackBarHost.showReplacingSnackbar(curtainOpenFailed)
                                            }
                                        }
                                    },
                                )
                            }

                            item(visible = matchCurtain && curtainStatus.moduleInstalled) {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Info,
                                    title = stringResource(R.string.origin_curtain_third_party),
                                    description = stringResource(
                                        R.string.origin_curtain_third_party_summary
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // ----- Privacy & root security ------------------------------------
            if (matchVeilSwitch || matchVeilManage || matchGrantToast || matchSuPrompt ||
                matchTempGrant || matchSecureRoot || matchAppLock || matchRootGate ||
                matchLockBoundRoot || matchRootGatePanic || matchOriginGuard
            ) {
                item(key = "section-privacy", span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        LabSectionHeader(stringResource(R.string.origin_lab_group_privacy))
                        SegmentedColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            item(visible = matchVeilSwitch) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Shield,
                                    title = titleVeilSwitch,
                                    description = veilSummary,
                                    enabled = uiState.veilStatus == "supported",
                                    checked = uiState.isVeilEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(SettingsUiAction.SetVeil(enabled))
                                    },
                                )
                            }

                            item(
                                visible = uiState.veilStatus == "supported" &&
                                    uiState.isVeilEnabled && matchVeilManage
                            ) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Visibility,
                                    title = titleVeilManage,
                                    description = descVeilManage,
                                    onClick = {
                                        navigator.push(Route.Veil)
                                    }
                                )
                            }

                            item(visible = matchGrantToast) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Notifications,
                                    title = titleGrantToast,
                                    description = descGrantToast,
                                    checked = uiState.isGrantToastEnabled,
                                    enabled = uiState.sulogStatus == "supported",
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetGrantToast(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchSuPrompt) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Security,
                                    title = titleSuPrompt,
                                    description = suPromptSummary,
                                    checked = uiState.isSuPromptEnabled,
                                    enabled = uiState.isSuPromptSupported,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetSuPrompt(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchTempGrant) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Timer,
                                    title = titleTempGrant,
                                    description = descTempGrant,
                                    checked = uiState.isTempGrantEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetTempGrant(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchSecureRoot) {
                                val biometricsAvailable = remember {
                                    canAuthenticateSecureRoot(context)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Fingerprint,
                                    title = titleSecureRoot,
                                    description = descSecureRoot,
                                    enabled = biometricsAvailable,
                                    checked = uiState.isSecureRootEnabled && biometricsAvailable,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetSecureRootEnabled(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchAppLock) {
                                val biometricsAvailable = remember {
                                    canAuthenticateSecureRoot(context)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Lock,
                                    title = titleAppLock,
                                    description = descAppLock,
                                    enabled = biometricsAvailable,
                                    checked = uiState.isAppLockEnabled && biometricsAvailable,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetAppLockEnabled(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchAppLock && uiState.isAppLockEnabled) {
                                val timeoutValues = listOf(0L, 60000L, 300000L)
                                val timeoutItems = listOf(
                                    stringResource(R.string.settings_app_lock_timeout_immediate),
                                    stringResource(R.string.settings_app_lock_timeout_1m),
                                    stringResource(R.string.settings_app_lock_timeout_5m),
                                )
                                SettingsChooseWidget(
                                    icon = Icons.TwoTone.Timer,
                                    title = titleAppLockTimeout,
                                    description = timeoutItems[
                                        timeoutValues.indexOf(uiState.appLockTimeout)
                                            .takeIf { it >= 0 } ?: 1
                                    ],
                                    items = timeoutItems,
                                    selectedIndex = timeoutValues.indexOf(uiState.appLockTimeout)
                                        .takeIf { it >= 0 } ?: 1,
                                    onSelectedIndexChange = { index ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetAppLockTimeout(timeoutValues[index])
                                        )
                                    },
                                )
                            }

                            item(visible = matchRootGate) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Security,
                                    title = titleRootGate,
                                    description = descRootGate,
                                    checked = uiState.isRootGateEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetRootGateEnabled(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchLockBoundRoot) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Lock,
                                    title = titleLockBoundRoot,
                                    description = descLockBoundRoot,
                                    checked = uiState.isLockBoundRootEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetLockBoundRootEnabled(enabled)
                                        )
                                    },
                                )
                            }

                            item(visible = matchRootGatePanic) {
                                val panicRemainingMillis =
                                    uiState.rootGatePanicUntil - System.currentTimeMillis()
                                val panicActive = panicRemainingMillis > 0L
                                val panicMinutes =
                                    ((panicRemainingMillis + 59_999L) / 60_000L)
                                        .coerceAtLeast(1L)
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Timer,
                                    title = if (panicActive) {
                                        stringResource(R.string.settings_root_gate_panic_active)
                                    } else {
                                        titleRootGatePanic
                                    },
                                    description = if (panicActive) {
                                        stringResource(
                                            R.string.settings_root_gate_panic_remaining,
                                            panicMinutes,
                                        )
                                    } else {
                                        descRootGatePanic
                                    },
                                    enabled = panicActive || uiState.isRootGateEnabled,
                                    onClick = {
                                        settingsViewModel.dispatch(
                                            if (panicActive) {
                                                SettingsUiAction.EndRootGatePanic
                                            } else {
                                                SettingsUiAction.StartRootGatePanic
                                            }
                                        )
                                    },
                                )
                            }

                            item(visible = matchOriginGuard) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Policy,
                                    title = titleOriginGuard,
                                    description = descOriginGuard,
                                    checked = uiState.isOriginGuardEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetOriginGuardEnabled(enabled)
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ----- System & boot ----------------------------------------------
            if (matchSusfsLkm || matchBootloop || matchBootloopThreshold ||
                matchOriginMount || matchTerminal || matchMountEngine
            ) {
                item(key = "section-system", span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        LabSectionHeader(stringResource(R.string.origin_lab_group_system))
                        SegmentedColumn(
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            item(visible = matchSusfsLkm) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = titleSusfsLkm,
                                    description = descSusfsLkm,
                                    onClick = {
                                        navigator.push(Route.SuSFSLkm)
                                    },
                                )
                            }

                            item(visible = homeState.systemStatus.isFullFeatured && matchBootloop) {
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.RestartAlt,
                                    title = titleBootloop,
                                    description = stringResource(
                                        id = R.string.settings_bootloop_protection_summary,
                                        uiState.bootloopMax,
                                    ),
                                    checked = uiState.isBootloopEnabled,
                                    onCheckedChange = { enabled ->
                                        settingsViewModel.dispatch(
                                            SettingsUiAction.SetBootloopEnabled(enabled)
                                        )
                                    },
                                )
                            }

                            item(
                                visible = homeState.systemStatus.isFullFeatured &&
                                    uiState.isBootloopEnabled && matchBootloopThreshold
                            ) {
                                val thresholdItems = (2..10).map { it.toString() }
                                SettingsChooseWidget(
                                    icon = Icons.TwoTone.Timer,
                                    title = titleBootloopThreshold,
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

                            item(visible = matchOriginMount) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = titleOriginMount,
                                    description = descOriginMount,
                                    onClick = {
                                        navigator.push(Route.OriginMount)
                                    },
                                )
                            }

                            item(visible = matchTerminal) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Terminal,
                                    title = titleTerminal,
                                    description = descTerminal,
                                    onClick = {
                                        navigator.push(Route.Terminal)
                                    },
                                )
                            }

                            item(visible = matchMountEngine) {
                                val mountEngine = uiState.mountEngine
                                val backendLabel = when (uiState.magicMountBackend) {
                                    "overlay" -> stringResource(R.string.magic_mount_backend_overlay)
                                    "bind" -> stringResource(R.string.magic_mount_backend_bind)
                                    else -> stringResource(R.string.magic_mount_backend_auto)
                                }
                                val engineSummary = when (mountEngine.owner) {
                                    "metamodule" -> stringResource(
                                        R.string.settings_mount_engine_metamodule,
                                        mountEngine.name.ifBlank {
                                            mountEngine.metamoduleId.ifBlank { mountEngine.engine }
                                        },
                                        mountEngine.metamoduleId,
                                    )
                                    "magic_mount" -> stringResource(
                                        R.string.settings_mount_engine_magic_mount,
                                        backendLabel,
                                    )
                                    "none" -> stringResource(R.string.settings_mount_engine_none)
                                    else -> stringResource(R.string.settings_mount_engine_unknown)
                                }
                                val kernelLabels = mountEngine.kernelEngines.map { engine ->
                                    when (engine) {
                                        "zeromount" -> "ZeroMount"
                                        "kasumi" -> "Kasumi"
                                        "nomount" -> "NoMount"
                                        else -> engine
                                    }
                                }
                                val engineDescription =
                                    if (kernelLabels.isEmpty()) {
                                        engineSummary
                                    } else {
                                        engineSummary + "\n" + stringResource(
                                            R.string.settings_mount_engine_kernel_support,
                                            kernelLabels.joinToString(", "),
                                        )
                                    }
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Dashboard,
                                    title = titleMountEngine,
                                    description = engineDescription,
                                )
                            }
                        }
                    }
                }
            }

            if (query.isNotBlank() && !hasAnyMatch) {
                item(key = "no-match", span = StaggeredGridItemSpan.FullLine) {
                    SearchNoMatch()
                }
            }
        }
    }

    if (showZygiskModules) {
        OriginZygiskModulesDialog(
            status = zygiskStatus,
            onToggle = { id, enabled ->
                settingsViewModel.dispatch(
                    SettingsUiAction.SetOriginZygiskModuleEnabled(id, enabled)
                )
            },
            onQuarantine = { id, quarantined ->
                settingsViewModel.dispatch(
                    SettingsUiAction.SetOriginZygiskModuleQuarantined(id, quarantined)
                )
            },
            onDismiss = { showZygiskModules = false },
        )
    }

    if (showZygiskTelemetry) {
        OriginZygiskTelemetryDialog(
            events = zygiskStatus.telemetry,
            onDismiss = { showZygiskTelemetry = false },
        )
    }

    if (showZygiskLog) {
        OriginZygiskLogDialog(
            log = zygiskStatus.bootLog,
            onDismiss = { showZygiskLog = false },
        )
    }

    if (showZygiskSkip) {
        OriginZygiskSkipDialog(
            apps = zygiskApps,
            selected = zygiskStatus.skippedPackages,
            onToggle = { packageName, skip ->
                settingsViewModel.dispatch(
                    SettingsUiAction.SetOriginZygiskSkip(packageName, skip)
                )
            },
            onDismiss = { showZygiskSkip = false },
        )
    }
}

/** Section heading used between the bento tiles and the grouped control lists. */
@Composable
private fun LabSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp),
    )
}

/** Rounded, tinted icon container matching the bento tile rhythm. */
@Composable
private fun LabIconBadge(
    icon: ImageVector,
    tint: Color,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.5f),
            tint = tint,
        )
    }
}

/** Compact bento tile: tinted icon, small label, large value and a caption. */
@Composable
private fun LabFeatureTile(
    icon: ImageVector,
    accent: Color,
    label: String,
    value: String,
    caption: String,
) {
    BentoCard(modifier = Modifier.heightIn(min = 132.dp)) {
        LabIconBadge(icon = icon, tint = accent, size = 44.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        BentoCaption(caption)
    }
}
