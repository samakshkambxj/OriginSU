package com.originsu.manager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.module.KpmRepository
import com.originsu.manager.domain.model.KpmModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KpmUiState(
    val modules: List<KpmModule> = emptyList(),
    val isRefreshing: Boolean = false,
    val search: String = "",
    val version: String = "",
    val detail: String = "",
    val showControlDialog: Boolean = false,
    val selectedModuleId: String? = null,
    val controlArgs: String = "",
)

class KpmViewModel(
    private val kpmRepository: KpmRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "KpmViewModel"
    }

    private val mutableSearch = MutableStateFlow("")
    private val mutableDetail = MutableStateFlow("")
    private val mutableDialog = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val mutableArgs = MutableStateFlow("")

    val uiState: StateFlow<KpmUiState> = combine(
        kpmRepository.state,
        mutableSearch,
        mutableDetail,
        mutableDialog,
        mutableArgs,
    ) { kpmState, search, detail, dialog, args ->
        val query = search.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            kpmState.modules
        } else {
            kpmState.modules.filter {
                it.id.lowercase().contains(query) ||
                    it.name.lowercase().contains(query) ||
                    it.author.lowercase().contains(query) ||
                    it.description.lowercase().contains(query)
            }
        }
        KpmUiState(
            modules = filtered,
            isRefreshing = kpmState.refreshing,
            search = search,
            version = kpmState.version,
            detail = detail,
            showControlDialog = dialog.second,
            selectedModuleId = dialog.first,
            controlArgs = args,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KpmUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            kpmRepository.refresh()
        }
    }

    fun setSearch(query: String) {
        mutableSearch.value = query
    }

    fun loadDetail(moduleId: String) {
        viewModelScope.launch {
            mutableDetail.value = try {
                withContext(Dispatchers.IO) {
                    kpmRepository.getModuleInfo(moduleId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load KPM module detail", e)
                ""
            }
        }
    }

    fun showControlDialog(moduleId: String) {
        mutableDialog.value = moduleId to true
        mutableArgs.value = ""
    }

    fun hideControlDialog() {
        mutableDialog.value = null to false
        mutableArgs.value = ""
    }

    fun setControlArgs(args: String) {
        mutableArgs.value = args
    }

    suspend fun executeControl(): Int {
        val moduleId = mutableDialog.value.first ?: return -1
        val args = mutableArgs.value
        val result = kpmRepository.controlModule(moduleId, args)
        hideControlDialog()
        return result
    }

    suspend fun unloadModule(moduleId: String): String {
        val result = kpmRepository.unloadModule(moduleId)
        refresh()
        return result
    }

    suspend fun loadModule(path: String, args: String? = null): String {
        val result = kpmRepository.loadModule(path, args)
        refresh()
        return result
    }
}
