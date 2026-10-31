package com.originsu.manager.ui

import com.originsu.manager.domain.model.StartupState

internal fun shouldKeepStartupSplash(
    startupState: StartupState,
): Boolean = when (startupState) {
    StartupState.Loading -> true
    StartupState.Ready -> false
    is StartupState.Failed -> false
}
