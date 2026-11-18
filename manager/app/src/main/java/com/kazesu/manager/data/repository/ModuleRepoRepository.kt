package com.kazesu.manager.data.repository

import com.kazesu.manager.data.model.RepoModuleDetail
import com.kazesu.manager.data.model.RepoModuleList
import com.kazesu.manager.data.registry.UpdateVerification

interface ModuleRepoRepository {
    
    suspend fun fetchModules(): Result<RepoModuleList>

    /**
     * Detail (and readme) of a module in the current index. Fails with
     * [com.kazesu.manager.data.registry.ModuleNotInIndexException] if it is not listed.
     */
    suspend fun fetchDetail(moduleId: String): Result<RepoModuleDetail>

    
    suspend fun fetchChangelogHtml(moduleId: String, tag: String?): String?

    /**
     * How an `updateJson` update of an installed module must be verified: against the matching
     * registry release, rejected, or legacy (not a registry update).
     */
    suspend fun resolveUpdate(moduleId: String, updateJsonUrl: String, zipUrl: String, versionCode: Long): UpdateVerification

    
    suspend fun fetchRegistryText(url: String): String?

    
    fun isRegistryUrl(url: String): Boolean
}
