package com.originsu.manager.domain.model

import com.originsu.manager.R

data class TempGrantRecord(
    val uid: Int,
    val packageName: String,
    val expiresAtEpoch: Long,
)

data class TempGrantInfo(
    val uid: Int,
    val packageName: String,
    val label: String?,
    val remainingSecs: Long,
)

data class TempGrantState(
    val grants: List<TempGrantInfo> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

enum class TempGrantDuration(val seconds: Long, val titleRes: Int) {
    Minutes10(600L, R.string.temp_grant_10m),
    Minutes30(1800L, R.string.temp_grant_30m),
    Hour1(3600L, R.string.temp_grant_1h),
    Hours8(28800L, R.string.temp_grant_8h),
    Day1(86400L, R.string.temp_grant_24h),
}
