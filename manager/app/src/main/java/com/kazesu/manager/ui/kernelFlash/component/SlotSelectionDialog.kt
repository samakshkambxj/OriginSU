package com.kazesu.manager.ui.kernelFlash.component

import androidx.compose.runtime.Composable
import com.kazesu.manager.ui.LocalUiMode
import com.kazesu.manager.ui.UiMode

@Composable
fun SlotSelectionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSlotSelected: (String) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SlotSelectionDialogMiuix(
            show = show,
            onDismiss = onDismiss,
            onSlotSelected = onSlotSelected
        )
        UiMode.Material -> SlotSelectionDialogMaterial(
            show = show,
            onDismiss = onDismiss,
            onSlotSelected = onSlotSelected
        )
    }
}
