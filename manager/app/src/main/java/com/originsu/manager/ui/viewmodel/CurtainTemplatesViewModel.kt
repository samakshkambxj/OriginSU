package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.curtain.CurtainConfigRepository
import com.originsu.manager.data.curtain.CurtainInstalledApp
import com.originsu.manager.data.curtain.CurtainLoadError
import com.originsu.manager.data.curtain.CurtainLoadResult
import com.originsu.manager.data.curtain.CurtainTemplate
import com.originsu.manager.data.packageinfo.InstalledPackageRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CurtainTemplatesUiState(
    val loading: Boolean = true,
    val error: CurtainLoadError? = null,
    val templates: List<CurtainTemplate> = emptyList(),
    val busy: Boolean = false,
)

/** Reusable app-list templates shared by any number of target apps. */
class CurtainTemplatesViewModel(
    private val configRepository: CurtainConfigRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(CurtainTemplatesUiState())
    val uiState: StateFlow<CurtainTemplatesUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = configRepository.load()) {
                is CurtainLoadResult.Ready -> mutableState.update {
                    it.copy(
                        loading = false,
                        error = null,
                        templates = result.snapshot.config.templates.values
                            .sortedBy { template -> template.name.lowercase() },
                    )
                }
                is CurtainLoadResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }

    fun create(name: String, isWhitelist: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        save { configRepository.saveTemplate(CurtainTemplate(trimmed, isWhitelist)) }
    }

    fun delete(name: String) {
        save { configRepository.deleteTemplate(name) }
    }

    private fun save(block: suspend () -> CurtainLoadResult) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            when (val result = block()) {
                is CurtainLoadResult.Ready -> mutableState.update {
                    it.copy(
                        busy = false,
                        error = null,
                        templates = result.snapshot.config.templates.values
                            .sortedBy { template -> template.name.lowercase() },
                    )
                }
                is CurtainLoadResult.Failure -> mutableState.update {
                    it.copy(busy = false, error = result.error)
                }
            }
        }
    }
}

sealed interface CurtainTemplateAppsEvent {
    data object Saved : CurtainTemplateAppsEvent
    data object SaveFailed : CurtainTemplateAppsEvent
    data object Deleted : CurtainTemplateAppsEvent
}

data class CurtainTemplateAppsUiState(
    val loading: Boolean = true,
    val error: CurtainLoadError? = null,
    val template: CurtainTemplate? = null,
    val installedApps: List<CurtainInstalledApp> = emptyList(),
    val selection: Set<String> = emptySet(),
    val search: String = "",
    val busy: Boolean = false,
) {
    val visibleApps: List<CurtainInstalledApp>
        get() {
            val query = search.trim()
            if (query.isEmpty()) return installedApps
            return installedApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
}

/** Which apps a reusable template hides (blacklist) or shows (whitelist). */
class CurtainTemplateAppsViewModel(
    private val templateName: String,
    private val configRepository: CurtainConfigRepository,
    private val installedPackageRepository: InstalledPackageRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(CurtainTemplateAppsUiState())
    val uiState: StateFlow<CurtainTemplateAppsUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<CurtainTemplateAppsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CurtainTemplateAppsEvent> = mutableEvents.asSharedFlow()

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
            }.sortedBy { it.label.lowercase() }
            when (val result = configRepository.load()) {
                is CurtainLoadResult.Ready -> {
                    val template = result.snapshot.config.templates[templateName]
                    mutableState.update {
                        it.copy(
                            loading = false,
                            error = null,
                            installedApps = installed,
                            template = template,
                            selection = template?.appList.orEmpty(),
                        )
                    }
                }
                is CurtainLoadResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(search = query) }
    }

    fun toggle(packageName: String, selected: Boolean) {
        mutableState.update { state ->
            state.copy(
                selection = if (selected) {
                    state.selection + packageName
                } else {
                    state.selection - packageName
                },
            )
        }
    }

    fun setMode(isWhitelist: Boolean) {
        val template = mutableState.value.template ?: return
        save(template.copy(isWhitelist = isWhitelist), deleted = false, notify = false)
    }

    fun saveApps() {
        val template = mutableState.value.template ?: return
        save(template.copy(appList = mutableState.value.selection), deleted = false, notify = true)
    }

    fun delete() {
        val template = mutableState.value.template ?: return
        save(template, deleted = true, notify = true)
    }

    private fun save(template: CurtainTemplate, deleted: Boolean, notify: Boolean) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val result = if (deleted) {
                configRepository.deleteTemplate(template.name)
            } else {
                configRepository.saveTemplate(template)
            }
            when (result) {
                is CurtainLoadResult.Ready -> {
                    val saved = result.snapshot.config.templates[template.name]
                    mutableState.update {
                        it.copy(
                            busy = false,
                            error = null,
                            template = saved,
                            selection = saved?.appList.orEmpty(),
                        )
                    }
                    if (notify) {
                        mutableEvents.tryEmit(
                            if (deleted) {
                                CurtainTemplateAppsEvent.Deleted
                            } else {
                                CurtainTemplateAppsEvent.Saved
                            }
                        )
                    }
                }
                is CurtainLoadResult.Failure -> {
                    mutableState.update { it.copy(busy = false, error = result.error) }
                    if (notify) mutableEvents.tryEmit(CurtainTemplateAppsEvent.SaveFailed)
                }
            }
        }
    }
}
