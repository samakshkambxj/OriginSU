package com.kazesu.manager.ui.screen.settings.tools

import androidx.compose.runtime.Immutable

@Immutable
data class ToolsUiState(
    val selinuxEnforcing: Boolean = true,
    val selinuxLoading: Boolean = true,
    val bootloopEnabled: Boolean = true,
    val bootloopMaxFailed: Int = 3,
    val bootloopFailedCount: Int = 0,
    val bootloopRescued: Boolean = false,
    val bootloopRescuedFailedBoots: Int? = null,
    val bootloopLoading: Boolean = true,
    val guardEnabled: Boolean = true,
    val guardLoading: Boolean = true,
    val guardUpdating: Boolean = false,
)

@Immutable
data class ToolsActions(
    val onBack: () -> Unit,
    val onSelinuxToggle: (Boolean) -> Unit = {},
    val onBackupAllowlist: () -> Unit = {},
    val onRestoreAllowlist: () -> Unit = {},
    val onOpenKernelModules: () -> Unit = {},
    val onBootloopToggle: (Boolean) -> Unit = {},
    val onBootloopThresholdChange: (Int) -> Unit = {},
    val onBootloopDismissRescued: () -> Unit = {},
    val onGuardToggle: (Boolean) -> Unit = {},
    val onGuardUpdate: () -> Unit = {},
)

@Immutable
data class SpoofCpuParams(
    val cpuIndices: List<Int>,
    val midrHex: String,
    val bogomips: Int,
    val hwcapHex: String,
    val hwcap2Hex: String
)
