package com.originsu.manager.ui.kernelFlash

import androidx.compose.runtime.Immutable

@Immutable
data class KernelFlashActions(
    val onBack: () -> Unit = {},
    val onSaveLog: (String) -> Unit = {},
    val onReboot: () -> Unit = {},
)
