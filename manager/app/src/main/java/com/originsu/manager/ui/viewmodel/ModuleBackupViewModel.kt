package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.domain.model.BundleImportResult
import com.originsu.manager.domain.model.BundleScriptModule
import com.originsu.manager.domain.model.ModuleBackupEntry
import com.originsu.manager.domain.model.ModuleBackupInfo
import com.originsu.manager.domain.model.ModuleBackupOutcome
import com.originsu.manager.domain.model.ScriptParseResult
import com.originsu.manager.domain.usecase.BackupModulesUseCase
import com.originsu.manager.domain.usecase.BuildBundleScriptUseCase
import com.originsu.manager.domain.usecase.BuildModuleBundleUseCase
import com.originsu.manager.domain.usecase.DeleteModuleBackupUseCase
import com.originsu.manager.domain.usecase.ImportModuleBundleUseCase
import com.originsu.manager.domain.usecase.ListModuleBackupsUseCase
import com.originsu.manager.domain.usecase.ParseBundleScriptUseCase
import com.originsu.manager.domain.usecase.RestoreModuleBackupUseCase
import com.originsu.manager.domain.usecase.WriteCacheFileToUriUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ModuleBackupUiState(
    val backups: List<ModuleBackupInfo> = emptyList(),
    val isWorking: Boolean = false,
)

/**
 * Internal module backups, shareable bundle zips and download-scripts.
 * Long shell/IO work runs here; the Modules page only renders state,
 * launches SAF pickers and shows snackbars from returned outcomes.
 */
class ModuleBackupViewModel(
    private val backupModules: BackupModulesUseCase,
    private val listBackups: ListModuleBackupsUseCase,
    private val restoreBackup: RestoreModuleBackupUseCase,
    private val deleteBackup: DeleteModuleBackupUseCase,
    private val buildBundle: BuildModuleBundleUseCase,
    private val importBundle: ImportModuleBundleUseCase,
    private val writeToUri: WriteCacheFileToUriUseCase,
    private val parseBundleScript: ParseBundleScriptUseCase,
    private val buildBundleScript: BuildBundleScriptUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModuleBackupUiState())
    val state: StateFlow<ModuleBackupUiState> = mutableState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(backups = runCatching { listBackups() }.getOrDefault(emptyList())) }
        }
    }

    suspend fun backup(entries: List<ModuleBackupEntry>): ModuleBackupOutcome {
        setWorking(true)
        try {
            return runCatching { backupModules(entries) }
                .getOrDefault(ModuleBackupOutcome(failed = entries.map { it.dirId }))
        } finally {
            mutableState.update { it.copy(backups = runCatching { listBackups() }.getOrDefault(emptyList())) }
            setWorking(false)
        }
    }

    /** Returns restored/failed counts; refreshes the backup list. */
    suspend fun restoreAll(fileNames: List<String>): Pair<Int, Int> {
        setWorking(true)
        try {
            var ok = 0
            for (name in fileNames) {
                if (runCatching { restoreBackup(name) }.getOrDefault(false)) ok++
            }
            return ok to (fileNames.size - ok)
        } finally {
            setWorking(false)
        }
    }

    suspend fun restore(fileName: String): Boolean {
        setWorking(true)
        try {
            return runCatching { restoreBackup(fileName) }.getOrDefault(false)
        } finally {
            setWorking(false)
        }
    }

    suspend fun delete(fileName: String): Boolean {
        val ok = runCatching { deleteBackup(fileName) }.getOrDefault(false)
        if (ok) {
            mutableState.update { it.copy(backups = runCatching { listBackups() }.getOrDefault(emptyList())) }
        }
        return ok
    }

    suspend fun buildBundleZip(backupFileNames: List<String>, bundleName: String): Result<File> {
        setWorking(true)
        try {
            return buildBundle(backupFileNames, bundleName)
        } finally {
            setWorking(false)
        }
    }

    suspend fun exportToUri(file: File, uri: String): Boolean = writeToUri(file, uri)

    suspend fun importBundleZip(uri: String): Result<BundleImportResult> {
        setWorking(true)
        try {
            return importBundle(uri)
        } finally {
            mutableState.update { it.copy(backups = runCatching { listBackups() }.getOrDefault(emptyList())) }
            setWorking(false)
        }
    }

    suspend fun parseScript(uri: String): Result<ScriptParseResult> = parseBundleScript(uri)

    suspend fun buildScript(modules: List<BundleScriptModule>, name: String): Result<File> =
        buildBundleScript(modules, name)

    private fun setWorking(working: Boolean) {
        mutableState.update { it.copy(isWorking = working) }
    }
}
