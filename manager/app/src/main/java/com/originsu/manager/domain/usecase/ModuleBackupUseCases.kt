package com.originsu.manager.domain.usecase

import com.originsu.manager.data.module.ModuleBackupRepository
import com.originsu.manager.domain.model.BundleScriptModule
import com.originsu.manager.domain.model.ModuleBackupEntry

class BackupModulesUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(entries: List<ModuleBackupEntry>) =
        repository.backupModules(entries)
}

class ListModuleBackupsUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke() = repository.listBackups()
}

class RestoreModuleBackupUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(fileName: String) = repository.restoreBackup(fileName)
}

class DeleteModuleBackupUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(fileName: String) = repository.deleteBackup(fileName)
}

class BuildModuleBundleUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(backupFileNames: List<String>, bundleName: String) =
        repository.buildBundleZip(backupFileNames, bundleName)
}

class ImportModuleBundleUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(uri: String) = repository.importBundleZip(uri)
}

class WriteCacheFileToUriUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(src: java.io.File, uri: String) =
        repository.writeCacheFileToUri(src, uri)
}

class ParseBundleScriptUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(uri: String) = repository.parseBundleScript(uri)
}

class BuildBundleScriptUseCase(private val repository: ModuleBackupRepository) {
    suspend operator fun invoke(modules: List<BundleScriptModule>, name: String) =
        repository.buildBundleScript(modules, name)
}
