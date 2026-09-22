package com.originsu.manager.domain.usecase

import com.originsu.manager.data.module.ModuleCatalogRepository
import com.originsu.manager.data.module.ModuleRepoSourceRepository
import com.originsu.manager.domain.model.RepoSchema

class ObserveCatalogModulesUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke() = repository.modules
}

class ObserveModuleCatalogRefreshingUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke() = repository.refreshing
}

class ObserveModuleCatalogOfflineUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke() = repository.offline
}

class RefreshModuleCatalogUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class GetCatalogModuleUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(moduleId: String) = repository.get(moduleId)
}

class ObserveRepoSourcesUseCase(private val repository: ModuleRepoSourceRepository) {
    operator fun invoke() = repository.sources
}

class ProbeRepoSourceUseCase(private val repository: ModuleRepoSourceRepository) {
    suspend operator fun invoke(url: String) = repository.probe(url)
}

class AddRepoSourceUseCase(private val repository: ModuleRepoSourceRepository) {
    operator fun invoke(name: String, url: String, schema: RepoSchema) =
        repository.addSource(name, url, schema)
}

class RemoveRepoSourceUseCase(private val repository: ModuleRepoSourceRepository) {
    operator fun invoke(id: String) = repository.removeSource(id)
}

class SetRepoSourceEnabledUseCase(private val repository: ModuleRepoSourceRepository) {
    operator fun invoke(id: String, enabled: Boolean) = repository.setEnabled(id, enabled)
}

class ResolveQueueDownloadsUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(moduleIds: List<String>) =
        repository.resolveDownloadUrls(moduleIds)
}

