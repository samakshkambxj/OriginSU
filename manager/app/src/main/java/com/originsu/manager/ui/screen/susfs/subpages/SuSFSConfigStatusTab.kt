package com.originsu.manager.ui.screen.susfs.subpages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.originsu.manager.R
import com.originsu.manager.domain.model.SuSFSStatusInfo
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.screen.susfs.RegisterSuSFSRefresh
import com.originsu.manager.ui.screen.susfs.SuSFSRefreshRegistrar
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.renderBackgroundBlur
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.SuSFSUiAction
import com.originsu.manager.ui.viewmodel.SuSFSViewModel
import com.originsu.manager.ui.viewmodel.awaitSuSFSBoolean
import com.originsu.manager.ui.viewmodel.awaitSuSFSStatusInfo
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusTab(
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    onRegisterRefresh: SuSFSRefreshRegistrar,
    configEnabled: Boolean,
    configEnabledLoaded: Boolean,
    onConfigEnabledChange: (Boolean) -> Unit,
    onConfigRestored: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    val navigator = LocalNavigator.current
    var statusInfo by remember { mutableStateOf(SuSFSStatusInfo("", "", "")) }

    RegisterSuSFSRefresh(onRegisterRefresh) { _, forceRefresh ->
        statusInfo = awaitSuSFSStatusInfo(configHelper, forceRefresh)
            ?: SuSFSStatusInfo("", "", "")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))
        }

        item {
            SusfsManagerHero(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                configEnabled = configEnabled,
                configEnabledLoaded = configEnabledLoaded,
                version = statusInfo.version,
                variant = statusInfo.variant,
            )
        }

        item {
            val noData = stringResource(R.string.susfs_status_no_data)
            val features = statusInfo.enabledFeatures
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .ifBlank { noData }

            SegmentedColumn(title = stringResource(R.string.susfs_manager_section)) {
                item {
                    SettingsSwitchWidget(
                        icon = Icons.TwoTone.Save,
                        title = stringResource(R.string.susfs_enable_config),
                        description = stringResource(R.string.susfs_enable_config_summary),
                        checked = configEnabled,
                        enabled = configEnabledLoaded,
                        onCheckedChange = onConfigEnabledChange,
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.TaskAlt,
                        title = stringResource(R.string.susfs_status_enabled_features),
                        description = features,
                    )
                }
            }
        }

        if (statusInfo.enabledFeatures.contains("SUSFS_LKM_MODULES")) {
            item {
                SegmentedColumn(title = stringResource(R.string.susfs_lkm_title)) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Extension,
                            title = stringResource(R.string.susfs_lkm_open_from_status),
                            description = stringResource(R.string.susfs_lkm_open_from_status_summary),
                            onClick = {
                                navigator.push(Route.SuSFSLkm)
                            },
                        )
                    }
                }
            }
        }

        item {
            BackupRestoreSection(onConfigRestored = onConfigRestored)
        }

        item {
            Spacer(Modifier.height(innerPadding.calculateBottomPadding()))
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SusfsManagerHero(
    configEnabled: Boolean,
    configEnabledLoaded: Boolean,
    version: String,
    variant: String,
    modifier: Modifier = Modifier,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val loading = !configEnabledLoaded
    val enabled = configEnabledLoaded && configEnabled
    val containerColor = when {
        loading -> MaterialTheme.colorScheme.surfaceContainerHigh
        enabled -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        loading -> MaterialTheme.colorScheme.onSurface
        enabled -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val title = when {
        loading -> stringResource(R.string.susfs_lkm_checking)
        enabled -> stringResource(R.string.susfs_manager_enabled)
        else -> stringResource(R.string.susfs_manager_disabled)
    }
    val subtitle = when {
        loading -> null
        enabled -> null
        else -> stringResource(R.string.susfs_config_disable_warning)
    }
    val noData = stringResource(R.string.susfs_status_no_data)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .renderBackgroundBlur(),
        color = if (themeConfig.isEnableBlurExp) Color.Transparent else containerColor.copy(cardConfig.cardAlpha),
        contentColor = contentColor,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Icon(
                        imageVector = if (enabled) Icons.TwoTone.Security else Icons.TwoTone.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
            if (!loading && (enabled || version.isNotBlank() || variant.isNotBlank())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ManagerMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.susfs_status_version),
                        value = version.ifBlank { noData },
                    )
                    ManagerMetric(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.susfs_status_variant),
                        value = variant.ifBlank { noData },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagerMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSection(
    onConfigRestored: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportTitle = stringResource(R.string.susfs_backup_export)
    val exportDesc = stringResource(R.string.susfs_backup_description)
    val importTitle = stringResource(R.string.susfs_backup_import)
    val importDesc = stringResource(R.string.susfs_restore_description)
    val exportSuccessMsg = stringResource(R.string.susfs_backup_export_success)
    val exportFailedMsg = stringResource(R.string.susfs_backup_export_failed)
    val importSuccessMsg = stringResource(R.string.susfs_backup_import_success)
    val importFailedMsg = stringResource(R.string.susfs_backup_import_failed)
    val restoreDefaultTitle = stringResource(R.string.susfs_backup_restore_default)
    val restoreDefaultDesc = stringResource(R.string.susfs_backup_restore_default_desc)
    val presetTitle = stringResource(R.string.susfs_preset_strong)
    val presetDesc = stringResource(R.string.susfs_preset_strong_desc)
    val confirmTitle = stringResource(R.string.susfs_backup_import_confirm_title)
    val confirmMsg = stringResource(R.string.susfs_backup_import_confirm_message)
    val importLabel = stringResource(R.string.susfs_backup_import_label)
    val cancelLabel = stringResource(R.string.susfs_entry_cancel)
    val operationSuccessMsg = stringResource(R.string.susfs_operation_success)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = awaitSuSFSBoolean(configHelper) { reply ->
                SuSFSUiAction.ExportConfig(uri.toString(), reply)
            }
            scope.launch {
                snackbarHost.showReplacingSnackbar(if (ok) exportSuccessMsg else exportFailedMsg)
            }
        }
    }

    val importDialog = rememberConfirmDialog(
        onConfirm = {
            val uri = pendingImportUri
            pendingImportUri = null
            if (uri != null) {
                scope.launch {
                    val ok = awaitSuSFSBoolean(configHelper) { reply ->
                        SuSFSUiAction.ImportConfig(uri.toString(), reply)
                    }
                    if (ok) {
                        onConfigRestored()
                    }
                    snackbarHost.showReplacingSnackbar(if (ok) importSuccessMsg else importFailedMsg)
                }
            }
        },
        onDismiss = { pendingImportUri = null },
    )

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImportUri = uri
        importDialog.showConfirm(
            title = confirmTitle,
            content = confirmMsg,
            confirm = importLabel,
            dismiss = cancelLabel,
        )
    }

    Column {
        SegmentedColumn(title = stringResource(R.string.susfs_backup_section)) {
            item {
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.Save,
                    title = exportTitle,
                    description = exportDesc,
                    onClick = {
                        exportLauncher.launch("susfs_backup.json")
                    }
                )
            }
            item {
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.Restore,
                    title = importTitle,
                    description = importDesc,
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                )
            }
        }

        SegmentedColumn(title = stringResource(R.string.susfs_presets_section)) {
            item {
                ApplyPresetRow(
                    title = presetTitle,
                    description = presetDesc,
                    snackbarHost = snackbarHost,
                    operationSuccessMsg = operationSuccessMsg,
                    operationFailedMsg = operationFailedMsg,
                    onConfigRestored = onConfigRestored,
                )
            }
            item {
                RestoreDefaultRow(
                    title = restoreDefaultTitle,
                    description = restoreDefaultDesc,
                    snackbarHost = snackbarHost,
                    operationSuccessMsg = operationSuccessMsg,
                    operationFailedMsg = operationFailedMsg,
                    onConfigRestored = onConfigRestored,
                )
            }
        }
    }
}

@Composable
private fun ApplyPresetRow(
    title: String,
    description: String,
    snackbarHost: SnackbarHostState,
    operationSuccessMsg: String,
    operationFailedMsg: String,
    onConfigRestored: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    val scope = rememberCoroutineScope()
    SettingsJumpPageWidget(
        icon = Icons.TwoTone.Security,
        title = title,
        description = description,
        onClick = {
            scope.launch {
                val ok = awaitSuSFSBoolean(configHelper) { reply ->
                    SuSFSUiAction.ApplyStrongPreset(reply)
                }
                if (ok) {
                    onConfigRestored()
                }
                snackbarHost.showReplacingSnackbar(if (ok) operationSuccessMsg else operationFailedMsg)
            }
        }
    )
}
@Composable
private fun RestoreDefaultRow(
    title: String,
    description: String,
    snackbarHost: SnackbarHostState,
    operationSuccessMsg: String,
    operationFailedMsg: String,
    onConfigRestored: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    val scope = rememberCoroutineScope()
    SettingsJumpPageWidget(
        icon = Icons.TwoTone.RestartAlt,
        title = title,
        description = description,
        onClick = {
            scope.launch {
                val ok = awaitSuSFSBoolean(configHelper) { reply ->
                    SuSFSUiAction.RestoreDefault(reply)
                }
                if (ok) {
                    onConfigRestored()
                }
                snackbarHost.showReplacingSnackbar(if (ok) operationSuccessMsg else operationFailedMsg)
            }
        }
    )
}
