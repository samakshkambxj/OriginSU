package com.originsu.manager.data.repository

import com.originsu.manager.data.model.AppInfo

interface SuperUserRepository {
    suspend fun getAppList(force: Boolean = false): Result<Pair<List<AppInfo>, List<Int>>>
    suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>>
}
