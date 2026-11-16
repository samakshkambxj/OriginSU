package com.originsu.manager.data.repository

import com.originsu.manager.data.model.Module
import com.originsu.manager.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
