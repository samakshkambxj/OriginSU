package com.kazesu.manager.data.repository

import com.kazesu.manager.data.model.AppInfo

interface SuperUserRepository {
    suspend fun getAppList(force: Boolean = false): Result<Pair<List<AppInfo>, List<Int>>>
    suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>>
}
