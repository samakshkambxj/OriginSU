package com.kazesu.manager.ui.screen.settings.tools

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kazesu.manager.R
import com.kazesu.manager.data.bootloop.BootloopRepository
import com.kazesu.manager.data.bootloop.BootloopStatus
import com.kazesu.manager.data.guard.GuardRepository
import com.kazesu.manager.ui.LocalUiMode
import com.kazesu.manager.ui.UiMode
import com.kazesu.manager.ui.navigation3.LocalNavigator
import com.kazesu.manager.ui.navigation3.Route
import com.kazesu.manager.ui.util.disableAllModules
import com.kazesu.manager.ui.util.uninstallAllModules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("LocalContextGetResourceValueCall", "StringFormatInvalid")
@Composable
fun ToolsScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selinuxEnforcing by remember { mutableStateOf(true) }
    var selinuxLoading by remember { mutableStateOf(true) }

    val bootloopRepository = remember { BootloopRepository() }
    var bootloopStatus by remember { mutableStateOf(BootloopStatus()) }
    var bootloopLoading by remember { mutableStateOf(true) }

    val guardRepository = remember { GuardRepository() }
    var guardEnabled by remember { mutableStateOf(true) }
    var guardLoading by remember { mutableStateOf(true) }
    var guardUpdating by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = backupAllowlistToUri(context, uri)
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.allowlist_backup_success else R.string.allowlist_backup_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val backupModulesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = backupModulesToUri(context, uri)
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.modules_backup_success else R.string.modules_backup_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val backupAllowlistTarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = backupAllowlistToUriTar(context, uri)
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.allowlist_tar_backup_success else R.string.allowlist_tar_backup_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = restoreAllowlistFromUri(context, uri)
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.allowlist_restore_success else R.string.allowlist_restore_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val restoreModulesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = restoreModulesFromUri(context, uri)
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.modules_restore_success else R.string.modules_restore_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val restoreAllowlistTarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = restoreAllowlistFromUriTar(context, uri)
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.allowlist_tar_restore_success else R.string.allowlist_tar_restore_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val current = withContext(Dispatchers.IO) { !isSelinuxPermissive() }
        selinuxEnforcing = current
        selinuxLoading = false
    }

    LaunchedEffect(Unit) {
        bootloopStatus = bootloopRepository.getStatus()
        bootloopLoading = false
    }

    LaunchedEffect(Unit) {
        guardEnabled = guardRepository.isEnabled()
        guardLoading = false
    }

    val actions = ToolsActions(
        onBack = { navigator.pop() },
        onSelinuxToggle = { target ->
            selinuxLoading = true
            scope.launch(Dispatchers.IO) {
                val success = if (target) setSelinuxPermissive(false) else setSelinuxPermissive(true)
                val actual = !isSelinuxPermissive()
                withContext(Dispatchers.Main) {
                    selinuxEnforcing = actual
                    selinuxLoading = false
                    Toast.makeText(
                        context,
                        if (success && actual == target) {
                            context.getString(
                                R.string.tools_selinux_apply_success,
                                context.getString(if (actual) R.string.selinux_status_enforcing else R.string.selinux_status_permissive)
                            )
                        } else {
                            context.getString(R.string.tools_selinux_apply_failed)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onBackupAllowlist = {
            backupLauncher.launch("ksu_allowlist_backup.bin")
        },
        onRestoreAllowlist = {
            restoreLauncher.launch(arrayOf("*/*"))
        },
        onBackupModules = {
            backupModulesLauncher.launch(modulesBackupSuggestedName())
        },
        onRestoreModules = {
            restoreModulesLauncher.launch(arrayOf("*/*"))
        },
        onBackupAllowlistTar = {
            backupAllowlistTarLauncher.launch(allowlistBackupSuggestedName())
        },
        onRestoreAllowlistTar = {
            restoreAllowlistTarLauncher.launch(arrayOf("*/*"))
        },
        onDisableAllModules = {
            scope.launch {
                val success = disableAllModules()
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.module_disable_all_success else R.string.module_disable_all_failed
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onUninstallAllModules = {
            scope.launch {
                val success = uninstallAllModules()
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.module_uninstall_all_success else R.string.module_uninstall_all_failed
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onOpenKazeMount = {
            navigator.push(Route.KazeMount)
        },
        onOpenKernelModules = {
            navigator.push(Route.KernelModules)
        },
        onBootloopToggle = { enabled ->
            val previous = bootloopStatus
            bootloopStatus = bootloopStatus.copy(
                enabled = enabled,
                failedCount = if (enabled) bootloopStatus.failedCount else 0,
            )
            scope.launch {
                if (!bootloopRepository.setEnabled(enabled)) {
                    bootloopStatus = previous
                    Toast.makeText(
                        context,
                        R.string.settings_bootloop_apply_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onBootloopThresholdChange = { count ->
            val previous = bootloopStatus.maxFailed
            bootloopStatus = bootloopStatus.copy(maxFailed = count)
            scope.launch {
                if (!bootloopRepository.setMaxFailed(count)) {
                    bootloopStatus = bootloopStatus.copy(maxFailed = previous)
                    Toast.makeText(
                        context,
                        R.string.settings_bootloop_apply_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onBootloopDismissRescued = {
            scope.launch {
                if (bootloopRepository.clearRescued()) {
                    bootloopStatus = bootloopStatus.copy(
                        rescued = false,
                        rescuedFailedBoots = null,
                    )
                }
            }
        },
        onGuardToggle = { enabled ->
            val previous = guardEnabled
            guardEnabled = enabled
            scope.launch {
                if (!guardRepository.setEnabled(enabled)) {
                    guardEnabled = previous
                    Toast.makeText(
                        context,
                        R.string.settings_guard_apply_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onGuardUpdate = {
            if (!guardUpdating) {
                guardUpdating = true
                scope.launch {
                    val success = guardRepository.updateRules()
                    guardUpdating = false
                    Toast.makeText(
                        context,
                        if (success) {
                            R.string.settings_guard_update_success
                        } else {
                            R.string.settings_guard_update_failed
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
    )

    val state = ToolsUiState(
        selinuxEnforcing = selinuxEnforcing,
        selinuxLoading = selinuxLoading,
        bootloopEnabled = bootloopStatus.enabled,
        bootloopMaxFailed = bootloopStatus.maxFailed,
        bootloopFailedCount = bootloopStatus.failedCount,
        bootloopRescued = bootloopStatus.rescued,
        bootloopRescuedFailedBoots = bootloopStatus.rescuedFailedBoots,
        bootloopLoading = bootloopLoading,
        guardEnabled = guardEnabled,
        guardLoading = guardLoading,
        guardUpdating = guardUpdating,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> ToolsMiuix(
            state = state,
            actions = actions
        )
        UiMode.Material -> ToolsMaterial(
            state = state,
            actions = actions
        )
    }
}
