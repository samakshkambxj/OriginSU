package com.kazesu.manager.ai.host

import com.kazesu.manager.ai.api.AiModuleService
import com.kazesu.manager.ai.api.GithubModuleAsset


object AiModuleServiceHost : AiModuleService {

    override suspend fun installedModules(limit: Int): String =
        AiManagerData.installedModules(limit)

    override fun moduleCapabilities(): String = AiManagerData.moduleCapabilities()

    override suspend fun search(query: String?, limit: Int): String =
        AiModuleController.search(query, limit)

    override suspend fun install(
        moduleId: String,
        version: String?,
        onProgress: (String) -> Unit,
    ): String = AiModuleController.install(moduleId, version, onProgress)

    override suspend fun installGithubAsset(
        asset: GithubModuleAsset,
        onProgress: (String) -> Unit,
    ): String = AiModuleController.installGithubAsset(asset, onProgress)
}
