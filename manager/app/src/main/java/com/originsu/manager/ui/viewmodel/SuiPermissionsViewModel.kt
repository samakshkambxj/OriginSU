package com.originsu.manager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.model.SuiApp
import com.originsu.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SuiPermissionsUiState(
    val apps: List<SuiApp> = emptyList(),
    val search: String = "",
    /** Sui's fallback permission for apps that have no rule of their own; null while unknown. */
    val defaultMode: Int? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
) {
    val visible: List<SuiApp>
        get() {
            val query = search.trim()
            if (query.isEmpty()) return apps
            return apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
}

/** Per-app Shizuku permissions stored by Sui, plus the default Sui falls back to. */
class SuiPermissionsViewModel(
    private val ksuCliRepository: KsuCliRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SuiPermissionsUiState())
    val uiState: StateFlow<SuiPermissionsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val apps = ksuCliRepository.suiAppPermissions()
            val default = ksuCliRepository.suiDefaultMode()
            mutableState.update {
                it.copy(apps = apps, defaultMode = default, loading = false)
            }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(search = query) }
    }

    fun setAppMode(uid: Int, mode: Int) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val ok = ksuCliRepository.setSuiAppMode(uid, mode)
            if (!ok) {
                Log.w("SuiPermissions", "Sui rejected the permission for uid $uid")
            }
            // Re-read from Sui so the row reflects what is actually stored.
            mutableState.update {
                it.copy(
                    apps = it.apps.map { app -> if (app.uid == uid) app.copy(mode = mode) else app },
                    busy = false,
                )
            }
        }
    }

    fun setDefaultMode(mode: Int) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val ok = ksuCliRepository.setSuiDefaultMode(mode)
            mutableState.update {
                it.copy(
                    defaultMode = if (ok) mode else it.defaultMode,
                    busy = false,
                )
            }
        }
    }
}
