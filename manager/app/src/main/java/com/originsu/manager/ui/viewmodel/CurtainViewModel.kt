package com.originsu.manager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.curtain.CurtainAppConfig
import com.originsu.manager.data.curtain.CurtainConfigRepository
import com.originsu.manager.data.curtain.CurtainGlobalConfig
import com.originsu.manager.data.curtain.CurtainInstalledApp
import com.originsu.manager.data.curtain.CurtainLoadError
import com.originsu.manager.data.curtain.CurtainLoadResult
import com.originsu.manager.data.curtain.CurtainSnapshot
import com.originsu.manager.data.curtain.CurtainTemplate
import com.originsu.manager.data.curtain.OriginCurtainRepository
import com.originsu.manager.data.packageinfo.InstalledPackageRepository
import com.originsu.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CurtainUiEvent {
    data object SaveFailed : CurtainUiEvent
    data object ConfigMissing : CurtainUiEvent
}

data class CurtainAppRow(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val config: CurtainAppConfig?,
) {
    val hidingEnabled: Boolean get() = config != null
}

data class CurtainUiState(
    val loading: Boolean = true,
    val error: CurtainLoadError? = null,
    val global: CurtainGlobalConfig? = null,
    val apps: List<CurtainAppRow> = emptyList(),
    val installedApps: List<CurtainInstalledApp> = emptyList(),
    val templates: List<CurtainTemplate> = emptyList(),
    val search: String = "",
    val showSystemApps: Boolean = false,
    val filterCount: Int? = null,
    val workMode: Int? = null,
    val upstreamInstalled: Boolean = false,
    val moduleVersion: String = "",
    val moduleInstalled: Boolean = false,
    val moduleLoaded: Boolean = false,
    /** A fresh config was seeded; one reboot is needed before the module reads it. */
    val configCreated: Boolean = false,
    val busy: Boolean = false,
) {
    val visibleApps: List<CurtainAppRow>
        get() {
            val query = search.trim()
            return apps.filter { app ->
                (showSystemApps || !app.isSystem) &&
                    (query.isEmpty() ||
                        app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true))
            }
        }

    val configuredCount: Int get() = apps.count { it.hidingEnabled }
}

/**
 * Origin Curtain's built-in configuration UI: it drives the upstream HMA-OSS
 * Zygisk module by editing its config file, so the upstream config app is not
 * required.
 */
class CurtainViewModel(
    private val configRepository: CurtainConfigRepository,
    private val installedPackageRepository: InstalledPackageRepository,
    private val originCurtainRepository: OriginCurtainRepository,
    private val ksuCliRepository: KsuCliRepository,
) : ViewModel() {

    private companion object {
        const val TAG = "CurtainViewModel"
    }

    private val mutableState = MutableStateFlow(CurtainUiState())
    val uiState: StateFlow<CurtainUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<CurtainUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CurtainUiEvent> = mutableEvents.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (mutableState.value.apps.isEmpty()) {
                mutableState.update { it.copy(loading = true) }
            }
            val installed = loadInstalledApps()
            val status = runCatching { originCurtainRepository.getStatus() }.getOrNull()
            val result = configRepository.load()
            mutableState.update { state ->
                val base = state.copy(
                    installedApps = installed,
                    upstreamInstalled = status?.managerInstalled == true,
                    moduleVersion = status?.moduleVersion.orEmpty(),
                    moduleInstalled = status?.moduleInstalled == true,
                    moduleLoaded = status?.moduleLoaded == true,
                )
                when (result) {
                    is CurtainLoadResult.Ready -> base
                        .withSnapshot(result.snapshot)
                        .copy(loading = false)
                    is CurtainLoadResult.Failure -> base.copy(
                        loading = false,
                        error = result.error,
                        configCreated = false,
                    )
                }
            }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(search = query) }
    }

    fun setShowSystemApps(show: Boolean) {
        mutableState.update { it.copy(showSystemApps = show) }
    }

    fun setGlobal(transform: (CurtainGlobalConfig) -> CurtainGlobalConfig) {
        val current = mutableState.value.global ?: return
        save { configRepository.setGlobal(transform(current)) }
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        save { configRepository.setAppEnabled(packageName, enabled) }
    }

    fun reboot() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ksuCliRepository.reboot() }
                .onFailure { Log.w(TAG, "Could not reboot the device", it) }
        }
    }

    private fun save(block: suspend () -> CurtainLoadResult) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            when (val result = block()) {
                is CurtainLoadResult.Ready -> mutableState.update { state ->
                    state.withSnapshot(result.snapshot).copy(busy = false)
                }
                is CurtainLoadResult.Failure -> {
                    Log.w(TAG, "Origin Curtain write failed: ${result.error}")
                    mutableState.update { it.copy(busy = false, error = result.error) }
                    mutableEvents.tryEmit(
                        if (result.error == CurtainLoadError.NOT_INITIALIZED) {
                            CurtainUiEvent.ConfigMissing
                        } else {
                            CurtainUiEvent.SaveFailed
                        }
                    )
                }
            }
        }
    }

    private suspend fun loadInstalledApps(): List<CurtainInstalledApp> {
        runCatching { installedPackageRepository.refresh() }
        return installedPackageRepository.packages.value.map { pkg ->
            CurtainInstalledApp(
                packageName = pkg.packageName,
                label = pkg.appLabel.ifBlank { pkg.packageName },
                isSystem = pkg.isSystem,
            )
        }
    }

    private fun CurtainUiState.withSnapshot(snapshot: CurtainSnapshot): CurtainUiState =
        copy(
            error = null,
            // Sticky for the session: the flag only clears on the next reboot,
            // when the module finally reads the config we seeded.
            configCreated = snapshot.configCreated || this.configCreated,
            global = snapshot.config.global,
            templates = snapshot.config.templates.values.sortedBy { it.name.lowercase() },
            apps = buildRows(installedApps, snapshot.config.apps),
            filterCount = snapshot.filterCount,
            workMode = snapshot.workMode,
        )

    private fun buildRows(
        installed: List<CurtainInstalledApp>,
        configured: Map<String, CurtainAppConfig>,
    ): List<CurtainAppRow> {
        val known = installed.map {
            CurtainAppRow(it.packageName, it.label, it.isSystem, configured[it.packageName])
        }
        val missing = configured.keys
            .filterNot { packageName -> installed.any { it.packageName == packageName } }
            .map { CurtainAppRow(it, it, false, configured[it]) }
        return (known + missing).sortedBy { it.label.lowercase() }
    }
}

data class CurtainAppUiState(
    val loading: Boolean = true,
    val error: CurtainLoadError? = null,
    val label: String = "",
    val config: CurtainAppConfig? = null,
    val templates: List<CurtainTemplate> = emptyList(),
    val installedApps: List<CurtainInstalledApp> = emptyList(),
    val busy: Boolean = false,
)

/** Editor for one app's HMA-OSS rule. */
class CurtainAppViewModel(
    private val packageName: String,
    private val configRepository: CurtainConfigRepository,
    private val installedPackageRepository: InstalledPackageRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(CurtainAppUiState(label = packageName))
    val uiState: StateFlow<CurtainAppUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { installedPackageRepository.refresh() }
            val installed = installedPackageRepository.packages.value.map { pkg ->
                CurtainInstalledApp(
                    packageName = pkg.packageName,
                    label = pkg.appLabel.ifBlank { pkg.packageName },
                    isSystem = pkg.isSystem,
                )
            }
            val label = installed.firstOrNull { it.packageName == packageName }?.label
                ?: packageName
            mutableState.update {
                it.copy(installedApps = installed, label = label)
            }
            when (val result = configRepository.load()) {
                is CurtainLoadResult.Ready -> mutableState.update {
                    it.withSnapshot(result.snapshot)
                }
                is CurtainLoadResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        save { configRepository.setAppEnabled(packageName, enabled) }
    }

    fun setMode(useWhitelist: Boolean) {
        update { it.copy(useWhitelist = useWhitelist) }
    }

    fun setExtraAppList(selection: Set<String>) {
        update { it.copy(extraAppList = selection) }
    }

    fun setExceptionList(selection: Set<String>) {
        update { it.copy(extraOppositeAppList = selection) }
    }

    fun setAppliedTemplates(selection: Set<String>) {
        update { it.copy(applyTemplates = selection) }
    }

    fun setSpoofInstallationSource(enabled: Boolean) {
        update { it.copy(hideInstallationSource = enabled) }
    }

    fun setSpoofSystemInstallationSource(enabled: Boolean) {
        update { it.copy(hideSystemInstallationSource = enabled) }
    }

    fun setExcludeTargetInstallationSource(enabled: Boolean) {
        update { it.copy(excludeTargetInstallationSource = enabled) }
    }

    fun setExcludeSystemApps(enabled: Boolean) {
        update { it.copy(excludeSystemApps = enabled) }
    }

    fun setInvertActivityLaunchProtection(enabled: Boolean) {
        update { it.copy(invertActivityLaunchProtection = enabled) }
    }

    fun setExcludeVoldIsolation(enabled: Boolean) {
        update { it.copy(excludeVoldIsolation = enabled) }
    }

    private fun update(transform: (CurtainAppConfig) -> CurtainAppConfig) {
        val current = mutableState.value.config ?: return
        save { configRepository.setApp(packageName, transform(current)) }
    }

    private fun save(block: suspend () -> CurtainLoadResult) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            when (val result = block()) {
                is CurtainLoadResult.Ready ->
                    mutableState.update { it.withSnapshot(result.snapshot).copy(busy = false) }
                is CurtainLoadResult.Failure ->
                    mutableState.update { it.copy(busy = false, error = result.error) }
            }
        }
    }

    private fun CurtainAppUiState.withSnapshot(snapshot: CurtainSnapshot): CurtainAppUiState =
        copy(
            loading = false,
            error = null,
            config = snapshot.config.apps[packageName],
            templates = snapshot.config.templates.values.sortedBy { it.name.lowercase() },
        )
}
