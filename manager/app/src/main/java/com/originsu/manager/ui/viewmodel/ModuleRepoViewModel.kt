package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.domain.model.CatalogModule
import com.originsu.manager.domain.model.ModuleCatalogFailure
import com.originsu.manager.domain.model.ModuleCatalogResult
import com.originsu.manager.domain.model.ModuleCategory
import com.originsu.manager.domain.model.RepoSchema
import com.originsu.manager.domain.model.RepoSource
import com.originsu.manager.domain.usecase.AddRepoSourceUseCase
import com.originsu.manager.domain.usecase.GetBooleanPreferenceUseCase
import com.originsu.manager.domain.usecase.ObserveCatalogModulesUseCase
import com.originsu.manager.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.originsu.manager.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.originsu.manager.domain.usecase.ObserveRepoSourcesUseCase
import com.originsu.manager.domain.usecase.ProbeRepoSourceUseCase
import com.originsu.manager.domain.usecase.RefreshModuleCatalogUseCase
import com.originsu.manager.domain.usecase.RemoveRepoSourceUseCase
import com.originsu.manager.domain.usecase.ResolveQueueDownloadsUseCase
import com.originsu.manager.domain.usecase.SetBooleanPreferenceUseCase
import com.originsu.manager.domain.usecase.SetRepoSourceEnabledUseCase
import com.originsu.manager.domain.usecase.TransliterateTextUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModuleRepoUiState(
    val modules: List<CatalogModule> = emptyList(),
    val sortStargazerCountFirst: Boolean = false,
    val isRefreshing: Boolean = false,
    val offline: Boolean = false,
    val search: String = "",
    val category: ModuleCategory? = null,
    val sources: List<RepoSource> = emptyList(),
    val selectedModuleIds: Set<String> = emptySet(),
)

sealed interface ModuleRepoUiAction {
    data object Refresh : ModuleRepoUiAction
    data class Search(val query: String) : ModuleRepoUiAction
    data class SetStarsFirst(val enabled: Boolean) : ModuleRepoUiAction
    data class SetCategory(val category: ModuleCategory?) : ModuleRepoUiAction
    data class ToggleSelect(val moduleId: String) : ModuleRepoUiAction
    data object ClearSelection : ModuleRepoUiAction
}

sealed interface ModuleRepoUiEvent {
    data object Offline : ModuleRepoUiEvent
    data class Error(val message: String) : ModuleRepoUiEvent
}

private data class RepoListSource(
    val modules: List<CatalogModule>,
    val refreshing: Boolean,
    val offline: Boolean,
    val sources: List<RepoSource>,
    val query: String,
)

private data class RepoListControls(
    val category: ModuleCategory?,
    val selected: Set<String>,
    val starsFirst: Boolean,
)

class ModuleRepoViewModel(
    observeModules: ObserveCatalogModulesUseCase,
    observeRefreshing: ObserveModuleCatalogRefreshingUseCase,
    observeOffline: ObserveModuleCatalogOfflineUseCase,
    observeSources: ObserveRepoSourcesUseCase,
    private val refreshCatalog: RefreshModuleCatalogUseCase,
    getBooleanPreference: GetBooleanPreferenceUseCase,
    private val setBooleanPreference: SetBooleanPreferenceUseCase,
    private val transliterateText: TransliterateTextUseCase,
    private val probeRepoSource: ProbeRepoSourceUseCase,
    private val addRepoSource: AddRepoSourceUseCase,
    private val removeRepoSource: RemoveRepoSourceUseCase,
    private val setRepoSourceEnabled: SetRepoSourceEnabledUseCase,
    private val resolveQueueDownloads: ResolveQueueDownloadsUseCase,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val category = MutableStateFlow<ModuleCategory?>(null)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val sortStarsFirst = MutableStateFlow(
        getBooleanPreference("module_repo_sort_star_first", false)
    )
    private val mutableEvents = MutableSharedFlow<ModuleRepoUiEvent>(extraBufferCapacity = 1)

    val events: SharedFlow<ModuleRepoUiEvent> = mutableEvents.asSharedFlow()
    val state: StateFlow<ModuleRepoUiState> = combine(
        combine(
            observeModules(),
            observeRefreshing(),
            observeOffline(),
            observeSources(),
            search,
        ) { modules, refreshing, offline, sources, query ->
            RepoListSource(modules, refreshing, offline, sources, query)
        },
        combine(
            category,
            selectedIds,
            sortStarsFirst,
        ) { selectedCategory, selected, starsFirst ->
            RepoListControls(selectedCategory, selected, starsFirst)
        },
    ) { source, controls ->
        ModuleRepoUiState(
            modules = source.modules.filter { module ->
                (controls.category == null || module.category == controls.category) &&
                    (module.moduleId.contains(source.query, true) ||
                        module.moduleName.contains(source.query, true) ||
                        transliterateText(module.moduleName).contains(source.query, true))
            }.sortedWith(
                compareByDescending<CatalogModule> { it.installed }
                    .thenByDescending { if (controls.starsFirst) it.stargazerCount else 0 }
            ),
            sortStargazerCountFirst = controls.starsFirst,
            isRefreshing = source.refreshing,
            offline = source.offline,
            search = source.query,
            category = controls.category,
            sources = source.sources,
            selectedModuleIds = controls.selected,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ModuleRepoUiState(),
    )
    val uiState: StateFlow<ModuleRepoUiState> = state

    fun updateSearch(value: String) {
        search.value = value
    }

    fun setSortStargazerCountFirst(enabled: Boolean) {
        setBooleanPreference("module_repo_sort_star_first", enabled)
        sortStarsFirst.value = enabled
    }

    fun refresh(onFailure: (() -> Unit)? = null) {
        viewModelScope.launch {
            when (val result = refreshCatalog()) {
                is ModuleCatalogResult.Success -> Unit
                is ModuleCatalogResult.Failure -> {
                    onFailure?.invoke()
                    when (val reason = result.reason) {
                        ModuleCatalogFailure.Offline -> mutableEvents.emit(ModuleRepoUiEvent.Offline)
                        ModuleCatalogFailure.NotFound -> mutableEvents.emit(
                            ModuleRepoUiEvent.Error("Module not found")
                        )

                        is ModuleCatalogFailure.Network -> mutableEvents.emit(
                            ModuleRepoUiEvent.Error(reason.message)
                        )
                    }
                }
            }
        }
    }

    fun dispatch(action: ModuleRepoUiAction) {
        when (action) {
            ModuleRepoUiAction.Refresh -> refresh()
            is ModuleRepoUiAction.Search -> updateSearch(action.query)
            is ModuleRepoUiAction.SetStarsFirst -> setSortStargazerCountFirst(action.enabled)
            is ModuleRepoUiAction.SetCategory -> category.value = action.category
            is ModuleRepoUiAction.ToggleSelect -> selectedIds.update { current ->
                val next = current.toMutableSet()
                if (!next.add(action.moduleId)) next.remove(action.moduleId)
                next
            }
            ModuleRepoUiAction.ClearSelection -> selectedIds.value = emptySet()
        }
    }

    suspend fun probeSource(url: String): RepoSchema? = probeRepoSource(url)

    fun addSource(name: String, url: String, schema: RepoSchema): Boolean {
        if (addRepoSource(name, url, schema) == null) return false
        refresh()
        return true
    }

    fun removeSource(id: String) {
        removeRepoSource(id)
        refresh()
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        setRepoSourceEnabled(id, enabled)
        refresh()
    }

    suspend fun resolveQueue(moduleIds: List<String>): Map<String, String> =
        resolveQueueDownloads(moduleIds)
}
