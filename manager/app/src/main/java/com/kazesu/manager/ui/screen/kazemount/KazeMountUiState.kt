package com.kazesu.manager.ui.screen.kazemount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.kazesu.manager.R
import com.kazesu.manager.data.mount.KazeMountMode

@Immutable
data class KazeMountActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onModeChange: (KazeMountMode) -> Unit = {},
    val onMountNow: () -> Unit = {},
    val onSaveUids: (String) -> Unit = {},
    val onIsolatedChange: (Boolean) -> Unit = {},
    val onClearRules: () -> Unit = {},
)

@Composable
fun kazeMountModeLabel(mode: KazeMountMode): String = stringResource(
    when (mode) {
        KazeMountMode.Auto -> R.string.kaze_mount_mode_auto
        KazeMountMode.OverlayFs -> R.string.kaze_mount_mode_overlay
        KazeMountMode.BindMount -> R.string.kaze_mount_mode_bind
        KazeMountMode.Kagami -> R.string.kaze_mount_mode_kagami
        KazeMountMode.Metamodule -> R.string.kaze_mount_mode_metamodule
        KazeMountMode.ZeroMount -> R.string.kaze_mount_mode_zeromount
        KazeMountMode.NoMount -> R.string.kaze_mount_mode_nomount
        KazeMountMode.Off -> R.string.kaze_mount_mode_off
    }
)

@Composable
fun kazeMountProviderLabel(provider: String): String = when (provider) {
    "overlay" -> stringResource(R.string.kaze_mount_mode_overlay)
    "bind" -> stringResource(R.string.kaze_mount_mode_bind)
    "kagami" -> stringResource(R.string.kaze_mount_mode_kagami)
    "metamodule" -> stringResource(R.string.kaze_mount_mode_metamodule)
    "zeromount" -> stringResource(R.string.kaze_mount_mode_zeromount)
    "nomount" -> stringResource(R.string.kaze_mount_mode_nomount)
    "none" -> stringResource(R.string.kaze_mount_mode_off)
    else -> provider.ifBlank { stringResource(R.string.kaze_mount_unavailable) }
}
