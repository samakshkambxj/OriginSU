package com.kazesu.manager.ui.screen.settings.tools

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kazesu.manager.R
import com.kazesu.manager.ui.component.KsuIsValid
import com.kazesu.manager.ui.component.WarningLevel
import com.kazesu.manager.ui.component.miuix.AppCard
import com.kazesu.manager.ui.component.miuix.AppScaffold
import com.kazesu.manager.ui.component.miuix.WarningCard
import com.kazesu.manager.ui.theme.LocalBackgroundImagePath
import com.kazesu.manager.ui.theme.LocalEnableBlur
import com.kazesu.manager.ui.util.BlurredBar
import com.kazesu.manager.ui.util.CustomTextKey
import com.kazesu.manager.ui.util.customText
import com.kazesu.manager.ui.util.getSELinuxStatusRaw
import com.kazesu.manager.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ToolsMiuix(
    state: ToolsUiState,
    actions: ToolsActions
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val barColor = if (blurActive || hasCustomBackground) Color.Transparent else colorScheme.surface

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = customText(CustomTextKey.PAGE_TOOLS),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item {
                KsuIsValid {
                    SelinuxToggleSectionMiuix(
                        selinuxEnforcing = state.selinuxEnforcing,
                        selinuxLoading = state.selinuxLoading,
                        onSelinuxToggle = actions.onSelinuxToggle
                    )

                    BootloopSectionMiuix(
                        enabled = state.bootloopEnabled,
                        maxFailed = state.bootloopMaxFailed,
                        loading = state.bootloopLoading,
                        onEnabledChange = actions.onBootloopToggle,
                        onThresholdChange = actions.onBootloopThresholdChange,
                    )

                    if (state.bootloopRescued) {
                        BootloopRescuedCardMiuix(
                            failedBoots = state.bootloopRescuedFailedBoots
                                ?: state.bootloopMaxFailed,
                            onDismiss = actions.onBootloopDismissRescued,
                        )
                    }

                    GuardSectionMiuix(
                        enabled = state.guardEnabled,
                        loading = state.guardLoading,
                        updating = state.guardUpdating,
                        onEnabledChange = actions.onGuardToggle,
                        onUpdate = actions.onGuardUpdate,
                    )

                    KazeMountSectionMiuix(
                        onOpen = actions.onOpenKazeMount
                    )

                    KernelModulesSectionMiuix(
                        onOpen = actions.onOpenKernelModules
                    )

                    AllowlistBackupSectionMiuix(
                        onBackup = actions.onBackupAllowlist,
                        onRestore = actions.onRestoreAllowlist
                    )

                    ModuleBackupSectionMiuix(
                        onBackupModules = actions.onBackupModules,
                        onRestoreModules = actions.onRestoreModules,
                        onBackupAllowlistTar = actions.onBackupAllowlistTar,
                        onRestoreAllowlistTar = actions.onRestoreAllowlistTar,
                        onDisableAllModules = actions.onDisableAllModules,
                        onUninstallAllModules = actions.onUninstallAllModules
                    )
                }
            }
        }
    }
}

@Composable
private fun SelinuxToggleSectionMiuix(
    selinuxEnforcing: Boolean,
    selinuxLoading: Boolean,
    onSelinuxToggle: (Boolean) -> Unit
) {
    AppCard(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        val statusLabel = getSELinuxStatusRaw()
        SwitchPreference(
            title = stringResource(R.string.tools_selinux_toggle),
            summary = stringResource(R.string.tools_selinux_summary, statusLabel),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.tools_selinux_toggle),
                    tint = colorScheme.onBackground
                )
            },
            checked = selinuxEnforcing,
            enabled = !selinuxLoading,
            onCheckedChange = onSelinuxToggle
        )
    }
}

@Composable
private fun BootloopSectionMiuix(
    enabled: Boolean,
    maxFailed: Int,
    loading: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
) {
    val thresholdItems = remember { (2..10).map { it.toString() } }
    AppCard(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        SwitchPreference(
            title = stringResource(R.string.settings_bootloop_protection),
            summary = stringResource(
                R.string.settings_bootloop_protection_summary,
                maxFailed
            ),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground
                )
            },
            checked = enabled,
            enabled = !loading,
            onCheckedChange = onEnabledChange
        )

        OverlayDropdownPreference(
            title = stringResource(R.string.settings_bootloop_threshold),
            summary = stringResource(R.string.settings_bootloop_threshold_summary, maxFailed),
            items = thresholdItems,
            selectedIndex = (maxFailed - 2).coerceIn(0, thresholdItems.lastIndex),
            enabled = !loading && enabled,
            onSelectedIndexChange = { onThresholdChange(it + 2) },
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground
                )
            }
        )
    }
}

@Composable
private fun GuardSectionMiuix(
    enabled: Boolean,
    loading: Boolean,
    updating: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onUpdate: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        SwitchPreference(
            title = stringResource(R.string.settings_guard_title),
            summary = stringResource(R.string.settings_guard_summary),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground
                )
            },
            checked = enabled,
            enabled = !loading,
            onCheckedChange = onEnabledChange
        )

        ArrowPreference(
            title = stringResource(R.string.settings_guard_update),
            summary = stringResource(R.string.settings_guard_update_summary),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground
                )
            },
            onClick = {
                if (!updating) {
                    onUpdate()
                }
            }
        )
    }
}

@Composable
private fun BootloopRescuedCardMiuix(
    failedBoots: Int,
    onDismiss: () -> Unit,
) {
    val message = stringResource(R.string.settings_bootloop_rescued_title) + "\n" +
        stringResource(R.string.settings_bootloop_rescued_summary, failedBoots)
    WarningCard(
        message = message,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        level = WarningLevel.Error,
        action = {
            TextButton(
                text = stringResource(R.string.settings_bootloop_dismiss),
                onClick = onDismiss,
            )
        }
    )
}

@Composable
private fun KazeMountSectionMiuix(
    onOpen: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        ArrowPreference(
            title = stringResource(R.string.kaze_mount_title),
            summary = stringResource(R.string.kaze_mount_tools_summary),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.kaze_mount_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onOpen
        )
    }
}

@Composable
private fun KernelModulesSectionMiuix(
    onOpen: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        val title = customText(CustomTextKey.PAGE_KERNEL_MODULES)
        ArrowPreference(
            title = title,
            summary = stringResource(R.string.tools_kernel_modules_summary),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = title,
                    tint = colorScheme.onBackground
                )
            },
            onClick = onOpen
        )
    }
}

@Composable
private fun AllowlistBackupSectionMiuix(
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    AppCard(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        ArrowPreference(
            title = stringResource(R.string.allowlist_backup_title),
            summary = stringResource(R.string.allowlist_backup_summary_picker),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Backup,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.allowlist_backup_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onBackup
        )

        ArrowPreference(
            title = stringResource(R.string.allowlist_restore_title),
            summary = stringResource(R.string.allowlist_restore_summary_picker),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.allowlist_restore_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onRestore
        )
    }
}

@Composable
private fun ModuleBackupSectionMiuix(
    onBackupModules: () -> Unit,
    onRestoreModules: () -> Unit,
    onBackupAllowlistTar: () -> Unit,
    onRestoreAllowlistTar: () -> Unit,
    onDisableAllModules: () -> Unit,
    onUninstallAllModules: () -> Unit
) {
    AppCard(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        ArrowPreference(
            title = stringResource(R.string.modules_backup_title),
            summary = stringResource(R.string.modules_backup_summary_tar),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Backup,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.modules_backup_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onBackupModules
        )

        ArrowPreference(
            title = stringResource(R.string.modules_restore_title),
            summary = stringResource(R.string.modules_restore_summary_tar),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.modules_restore_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onRestoreModules
        )

        ArrowPreference(
            title = stringResource(R.string.allowlist_tar_backup_title),
            summary = stringResource(R.string.allowlist_tar_backup_summary_picker),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Backup,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.allowlist_tar_backup_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onBackupAllowlistTar
        )

        ArrowPreference(
            title = stringResource(R.string.allowlist_tar_restore_title),
            summary = stringResource(R.string.allowlist_tar_restore_summary_picker),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.allowlist_tar_restore_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onRestoreAllowlistTar
        )

        ArrowPreference(
            title = stringResource(R.string.module_disable_all_title),
            summary = stringResource(R.string.module_disable_all_summary),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.module_disable_all_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onDisableAllModules
        )

        ArrowPreference(
            title = stringResource(R.string.module_uninstall_all_title),
            summary = stringResource(R.string.module_uninstall_all_summary),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.module_uninstall_all_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onUninstallAllModules
        )
    }
}
