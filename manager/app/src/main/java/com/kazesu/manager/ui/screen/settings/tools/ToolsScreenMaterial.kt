package com.kazesu.manager.ui.screen.settings.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kazesu.manager.R
import com.kazesu.manager.ui.component.KsuIsValid
import com.kazesu.manager.ui.component.material.ExpressiveScaffold
import com.kazesu.manager.ui.component.material.IconTile
import com.kazesu.manager.ui.component.material.SegmentedColumn
import com.kazesu.manager.ui.component.material.SegmentedDropdownItem
import com.kazesu.manager.ui.component.material.SegmentedListItem
import com.kazesu.manager.ui.component.material.SegmentedSwitchItem
import com.kazesu.manager.ui.component.material.TonalCard
import com.kazesu.manager.ui.component.material.TopBarBackButton
import com.kazesu.manager.ui.component.material.expressiveTopAppBarColors
import com.kazesu.manager.ui.util.CustomTextKey
import com.kazesu.manager.ui.util.customText
import com.kazesu.manager.ui.util.getSELinuxStatusRaw

@Composable
fun ToolsMaterial(
    state: ToolsUiState,
    actions: ToolsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(customText(CustomTextKey.PAGE_TOOLS)) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                scrollBehavior = scrollBehavior,
                colors = expressiveTopAppBarColors(),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            contentPadding = innerPadding,
        ) {
            item {
                KsuIsValid {
                    SelinuxToggleSectionMaterial(
                        selinuxEnforcing = state.selinuxEnforcing,
                        selinuxLoading = state.selinuxLoading,
                        onSelinuxToggle = actions.onSelinuxToggle
                    )

                    BootloopSectionMaterial(
                        enabled = state.bootloopEnabled,
                        maxFailed = state.bootloopMaxFailed,
                        loading = state.bootloopLoading,
                        onEnabledChange = actions.onBootloopToggle,
                        onThresholdChange = actions.onBootloopThresholdChange,
                    )

                    if (state.bootloopRescued) {
                        BootloopRescuedCardMaterial(
                            failedBoots = state.bootloopRescuedFailedBoots
                                ?: state.bootloopMaxFailed,
                            onDismiss = actions.onBootloopDismissRescued,
                        )
                    }

                    GuardSectionMaterial(
                        enabled = state.guardEnabled,
                        loading = state.guardLoading,
                        updating = state.guardUpdating,
                        onEnabledChange = actions.onGuardToggle,
                        onUpdate = actions.onGuardUpdate,
                    )

                    KazeMountSectionMaterial(
                        onOpen = actions.onOpenKazeMount
                    )

                    KernelModulesSectionMaterial(
                        onOpen = actions.onOpenKernelModules
                    )

                    AllowlistBackupSectionMaterial(
                        onBackup = actions.onBackupAllowlist,
                        onRestore = actions.onRestoreAllowlist
                    )

                    ModuleBackupSectionMaterial(
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
private fun SelinuxToggleSectionMaterial(
    selinuxEnforcing: Boolean,
    selinuxLoading: Boolean,
    onSelinuxToggle: (Boolean) -> Unit
) {
    SegmentedColumn(
        modifier = Modifier.padding(top = 12.dp),
        content = listOf({
            val statusLabel = getSELinuxStatusRaw()
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.tools_selinux_toggle)) },
                supportingContent = { Text(stringResource(R.string.tools_selinux_summary, statusLabel)) },
                leadingContent = {
                    IconTile(Icons.Rounded.Security, stringResource(id = R.string.tools_selinux_toggle))
                },
                trailingContent = {
                    Switch(
                        checked = selinuxEnforcing,
                        enabled = !selinuxLoading,
                        onCheckedChange = onSelinuxToggle
                    )
                }
            )
        })
    )
}

@Composable
private fun BootloopSectionMaterial(
    enabled: Boolean,
    maxFailed: Int,
    loading: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
) {
    val thresholdItems = remember { (2..10).map { it.toString() } }
    SegmentedColumn(
        modifier = Modifier.padding(top = 12.dp),
        content = listOf({
            SegmentedSwitchItem(
                icon = Icons.Rounded.RestartAlt,
                title = stringResource(R.string.settings_bootloop_protection),
                summary = stringResource(
                    R.string.settings_bootloop_protection_summary,
                    maxFailed
                ),
                enabled = !loading,
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }, {
            SegmentedDropdownItem(
                icon = Icons.Rounded.Timer,
                title = stringResource(R.string.settings_bootloop_threshold),
                summary = stringResource(R.string.settings_bootloop_threshold_summary, maxFailed),
                items = thresholdItems,
                enabled = !loading && enabled,
                selectedIndex = (maxFailed - 2).coerceIn(0, thresholdItems.lastIndex),
                onItemSelected = { onThresholdChange(it + 2) },
            )
        })
    )
}

@Composable
private fun BootloopRescuedCardMaterial(
    failedBoots: Int,
    onDismiss: () -> Unit,
) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_bootloop_rescued_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_bootloop_rescued_summary, failedBoots),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.settings_bootloop_dismiss))
            }
        }
    }
}

@Composable
private fun GuardSectionMaterial(
    enabled: Boolean,
    loading: Boolean,
    updating: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onUpdate: () -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.padding(top = 12.dp),
        content = listOf({
            SegmentedSwitchItem(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.settings_guard_title),
                summary = stringResource(R.string.settings_guard_summary),
                enabled = !loading,
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }, {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.settings_guard_update)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_guard_update_summary))
                },
                leadingContent = {
                    IconTile(
                        Icons.Rounded.Security,
                        stringResource(R.string.settings_guard_update)
                    )
                },
                trailingContent = {
                    TextButton(onClick = onUpdate, enabled = !updating) {
                        Text(stringResource(R.string.settings_guard_update_action))
                    }
                }
            )
        })
    )
}

@Composable
private fun KazeMountSectionMaterial(
    onOpen: () -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.padding(top = 12.dp),
        content = listOf({
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.kaze_mount_title)) },
                supportingContent = { Text(stringResource(R.string.kaze_mount_tools_summary)) },
                leadingContent = {
                    IconTile(
                        Icons.Rounded.RestartAlt,
                        stringResource(R.string.kaze_mount_title),
                    )
                },
                onClick = onOpen,
            )
        })
    )
}

@Composable
private fun KernelModulesSectionMaterial(
    onOpen: () -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.padding(top = 12.dp),
        content = listOf({
            val title = customText(CustomTextKey.PAGE_KERNEL_MODULES)
            SegmentedListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(stringResource(R.string.tools_kernel_modules_summary)) },
                leadingContent = {
                    IconTile(
                        Icons.Rounded.Memory,
                        title,
                    )
                },
                onClick = onOpen,
            )
        })
    )
}

@Composable
private fun AllowlistBackupSectionMaterial(
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    SegmentedColumn(
        modifier = Modifier.padding(vertical = 12.dp),
        content = listOf(
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.allowlist_backup_title)) },
                    supportingContent = { Text(stringResource(R.string.allowlist_backup_summary_picker)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.Backup, stringResource(R.string.allowlist_backup_title))
                    },
                    onClick = onBackup
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.allowlist_restore_title)) },
                    supportingContent = { Text(stringResource(R.string.allowlist_restore_summary_picker)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.Restore, stringResource(R.string.allowlist_restore_title))
                    },
                    onClick = onRestore
                )
            }
        )
    )
}

@Composable
private fun ModuleBackupSectionMaterial(
    onBackupModules: () -> Unit,
    onRestoreModules: () -> Unit,
    onBackupAllowlistTar: () -> Unit,
    onRestoreAllowlistTar: () -> Unit,
    onDisableAllModules: () -> Unit,
    onUninstallAllModules: () -> Unit
) {
    SegmentedColumn(
        modifier = Modifier.padding(vertical = 12.dp),
        content = listOf(
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.modules_backup_title)) },
                    supportingContent = { Text(stringResource(R.string.modules_backup_summary_tar)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.Backup, stringResource(R.string.modules_backup_title))
                    },
                    onClick = onBackupModules
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.modules_restore_title)) },
                    supportingContent = { Text(stringResource(R.string.modules_restore_summary_tar)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.Restore, stringResource(R.string.modules_restore_title))
                    },
                    onClick = onRestoreModules
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.allowlist_tar_backup_title)) },
                    supportingContent = { Text(stringResource(R.string.allowlist_tar_backup_summary_picker)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.Backup, stringResource(R.string.allowlist_tar_backup_title))
                    },
                    onClick = onBackupAllowlistTar
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.allowlist_tar_restore_title)) },
                    supportingContent = { Text(stringResource(R.string.allowlist_tar_restore_summary_picker)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.Restore, stringResource(R.string.allowlist_tar_restore_title))
                    },
                    onClick = onRestoreAllowlistTar
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.module_disable_all_title)) },
                    supportingContent = { Text(stringResource(R.string.module_disable_all_summary)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.Block, stringResource(R.string.module_disable_all_title))
                    },
                    onClick = onDisableAllModules
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.module_uninstall_all_title)) },
                    supportingContent = { Text(stringResource(R.string.module_uninstall_all_summary)) },
                    leadingContent = {
                        IconTile(Icons.Rounded.DeleteForever, stringResource(R.string.module_uninstall_all_title))
                    },
                    onClick = onUninstallAllModules
                )
            }
        )
    )
}
