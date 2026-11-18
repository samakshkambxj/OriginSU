package com.kazesu.manager.data.repository

import com.kazesu.manager.data.model.Module
import com.kazesu.manager.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
