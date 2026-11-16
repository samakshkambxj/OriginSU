package com.originsu.manager.ui.screen.susfs.content

import androidx.compose.runtime.Composable
import com.originsu.manager.ui.LocalUiMode
import com.originsu.manager.ui.UiMode
import com.originsu.manager.ui.screen.susfs.content.material.SusPathUidFilterContentMaterial
import com.originsu.manager.ui.screen.susfs.content.miuix.SusPathUidFilterContentMiuix

@Composable
fun SusPathUidFilterContent(
    packages: Set<String>,
    isLoading: Boolean,
    onSelectApps: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> SusPathUidFilterContentMaterial(packages, isLoading, onSelectApps, onApply, onClear)
        UiMode.Miuix -> SusPathUidFilterContentMiuix(packages, isLoading, onSelectApps, onApply, onClear)
    }
}
