package com.kazesu.manager.ui.screen.builtinzygisk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazesu.manager.data.repository.ModuleRepository
import com.kazesu.manager.data.repository.ModuleRepositoryImpl
import com.kazesu.manager.ui.util.clearZygiskGuard
import com.kazesu.manager.ui.util.createZygiskReport
import com.kazesu.manager.ui.util.getBuiltinZygiskConfig
import com.kazesu.manager.ui.util.getBuiltinZygiskStatus
import com.kazesu.manager.ui.util.getInjectedZygiskModules
import com.kazesu.manager.ui.util.getZygiskHealth
import com.kazesu.manager.ui.util.getZygiskImplementationInfo
import com.kazesu.manager.ui.util.getZygiskQuarantine
import com.kazesu.manager.ui.util.setBuiltinZygiskConfig
import com.kazesu.manager.ui.util.setZygiskQuarantine
import com.kazesu.manager.ui.util.switchToZygisk
import com.kazesu.manager.ui.util.verifyKazeZygisk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuiltinZygiskViewModel(
    private val moduleRepo: ModuleRepository = ModuleRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuiltinZygiskUiState())
    val uiState = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val status = withContext(Dispatchers.IO) { getBuiltinZygiskStatus() }
            val implementation = withContext(Dispatchers.IO) { getZygiskImplementationInfo() }
            val config = withContext(Dispatchers.IO) { getBuiltinZygiskConfig() }
            val health = withContext(Dispatchers.IO) { getZygiskHealth() }
            val quarantine = withContext(Dispatchers.IO) { getZygiskQuarantine() }
            val injectedModules = withContext(Dispatchers.IO) { getInjectedZygiskModules() }
            val modulesResult = moduleRepo.getModules()
            val zygiskModules = modulesResult.getOrDefault(emptyList()).filter {
                it.zygisk || it.suppressedByBuiltin
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    status = status,
                    implementation = implementation,
                    config = config,
                    health = health,
                    quarantine = quarantine,
                    injectedModules = injectedModules,
                    zygiskModules = zygiskModules,
                    expandedInjectedId = null
                )
            }
        }
    }

    /** Writes one option, then re-reads the file so the UI shows what ksud actually stored. */
    fun setOption(key: String, value: String, onFailure: () -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { setBuiltinZygiskConfig(key, value) }
            val config = withContext(Dispatchers.IO) { getBuiltinZygiskConfig() }
            _uiState.update { it.copy(config = config) }
            if (!ok) onFailure()
        }
    }

    /**
     * Hands Zygisk to `target` and leaves the panel to the caller.
     *
     * The switch runs in the background: selecting a provider closes this dashboard and
     * asks for a restart anyway, so waiting here would only keep a panel the user cannot
     * use any more on screen.
     */
    fun switchInBackground(target: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = switchToZygisk(target)
            // Toasts and Compose state belong to the main thread.
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    /** Opens or closes the process rows of an injected module. */
    fun toggleInjectedModule(id: String) {
        _uiState.update {
            it.copy(expandedInjectedId = if (it.expandedInjectedId == id) null else id)
        }
    }

    /** Opens or closes the served-module rows of the status card. */
    fun toggleInjectedGroup() {
        _uiState.update { it.copy(injectedGroupExpanded = !it.injectedGroupExpanded) }
    }

    /** Drops the boot guard latch and reloads the health receipt. */
    fun clearGuard(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { clearZygiskGuard() }
            val health = withContext(Dispatchers.IO) { getZygiskHealth() }
            _uiState.update { it.copy(health = health) }
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    /** Checks the staged payload against the manifest baked in at build time. */
    fun verifyPayload(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = verifyKazeZygisk()
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    /** Writes a diagnostics ZIP on the device and hands its path back to the UI. */
    fun createReport(onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = createZygiskReport()
            withContext(Dispatchers.Main) { onResult(path) }
        }
    }

    /** Adds or removes one module from the quarantine, then re-reads the list. */
    fun setQuarantine(id: String, quarantined: Boolean, onFailure: () -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { setZygiskQuarantine(id, quarantined) }
            val list = withContext(Dispatchers.IO) { getZygiskQuarantine() }
            _uiState.update { it.copy(quarantine = list) }
            if (!ok) withContext(Dispatchers.Main) { onFailure() }
        }
    }
}
