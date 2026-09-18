package com.originsu.manager.ui.component

import androidx.compose.runtime.Composable
import com.originsu.manager.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit
) {
    if (status.isFullFeatured)
        content()
}
