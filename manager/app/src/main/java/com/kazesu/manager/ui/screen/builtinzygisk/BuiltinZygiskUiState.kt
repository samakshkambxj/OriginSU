package com.kazesu.manager.ui.screen.builtinzygisk

import androidx.compose.runtime.Immutable
import com.kazesu.manager.data.model.Module
import com.kazesu.manager.ui.util.BuiltinZygiskConfig
import com.kazesu.manager.ui.util.BuiltinZygiskStatus
import com.kazesu.manager.ui.util.InjectedZygiskModule
import com.kazesu.manager.ui.util.ZygiskHealth
import com.kazesu.manager.ui.util.ZygiskImplementationInfo

@Immutable
data class BuiltinZygiskUiState(
    val isLoading: Boolean = true,
    val status: BuiltinZygiskStatus = BuiltinZygiskStatus(),
    val implementation: ZygiskImplementationInfo = ZygiskImplementationInfo(),
    val config: BuiltinZygiskConfig = BuiltinZygiskConfig(),
    // Receipt of the last completed boot plus the guard counters.
    val health: ZygiskHealth = ZygiskHealth(),
    // Module ids the engine is told to skip.
    val quarantine: List<String> = emptyList(),
    val zygiskModules: List<Module> = emptyList(),
    // Modules the built-in is serving, with the processes each one reaches.
    val injectedModules: List<InjectedZygiskModule> = emptyList(),
    // Injected module whose process rows are open, if any.
    val expandedInjectedId: String? = null,
    // Whether the served modules are listed in the status card.
    val injectedGroupExpanded: Boolean = true,
)

/// The localized outcome of the last completed boot.
internal fun healthStatusText(status: String): Int = when (status) {
    "ok", "healthy" -> com.kazesu.manager.R.string.builtin_zygisk_health_healthy
    "failed" -> com.kazesu.manager.R.string.builtin_zygisk_health_failed
    "blocked" -> com.kazesu.manager.R.string.builtin_zygisk_health_blocked
    "disabled" -> com.kazesu.manager.R.string.builtin_zygisk_health_disabled
    "provider" -> com.kazesu.manager.R.string.builtin_zygisk_health_provider
    "safe_mode" -> com.kazesu.manager.R.string.builtin_zygisk_health_safe_mode
    else -> com.kazesu.manager.R.string.builtin_zygisk_health_unknown
}

/// The localized state ksud reported for an injected module.
internal fun injectedStateText(state: String): Int = when (state) {
    "injected" -> com.kazesu.manager.R.string.builtin_zygisk_state_injected
    "loaded" -> com.kazesu.manager.R.string.builtin_zygisk_state_loaded
    "failed" -> com.kazesu.manager.R.string.builtin_zygisk_state_failed
    else -> com.kazesu.manager.R.string.builtin_zygisk_state_unknown
}

/// The localized state of one zygote's injection, from `zygote_<abi>_status`.
internal fun zygoteStateText(status: String): Int = when (status) {
    "injected" -> com.kazesu.manager.R.string.builtin_zygisk_state_injected
    else -> com.kazesu.manager.R.string.builtin_zygisk_zygote_not_injected
}

/// Title for a Zygisk state that still needs a soft restart to finish.
internal fun problemTitle(problem: String): Int = when (problem) {
    "pending" -> com.kazesu.manager.R.string.builtin_zygisk_pending_title
    else -> com.kazesu.manager.R.string.builtin_zygisk_switch_failed
}

/// Body for the same state.
internal fun problemMessage(problem: String): Int = when (problem) {
    "pending" -> com.kazesu.manager.R.string.builtin_zygisk_pending_message
    else -> com.kazesu.manager.R.string.builtin_zygisk_switch_failed_message
}
