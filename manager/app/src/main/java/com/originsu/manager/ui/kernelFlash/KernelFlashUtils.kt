package com.originsu.manager.ui.kernelFlash

import android.net.Uri
import com.originsu.manager.ui.kernelFlash.state.HorizonKernelState

object KernelFlashStateHolder {
    var currentState: HorizonKernelState? = null
    var currentUri: Uri? = null
    var currentSlot: String? = null
    var currentKpmPatchEnabled: Boolean = false
    var currentKpmUndoPatch: Boolean = false

    fun clear() {
        currentState = null
        currentUri = null
        currentSlot = null
        currentKpmPatchEnabled = false
        currentKpmUndoPatch = false
    }
}
