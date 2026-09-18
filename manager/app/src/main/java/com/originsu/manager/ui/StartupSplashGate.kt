package com.originsu.manager.ui

import com.originsu.manager.domain.model.StartupState

internal fun shouldKeepStartupSplash(
    startupState: StartupState,
    homeInitialDataLoaded: Boolean,
): Boolean = when (startupState) {
    StartupState.Loading -> true
    StartupState.Ready -> !homeInitialDataLoaded
    is StartupState.Failed -> false
}
