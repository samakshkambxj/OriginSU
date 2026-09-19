package com.originsu.manager.domain.model

enum class ManagerUpdateChannel {
    STABLE,
    BETA,
}

enum class ManagerVariant {
    NORMAL,
    SPOOFED,
}

sealed interface ManagerApkSource {
    val url: String

    data class DirectApk(override val url: String) : ManagerApkSource

    data class NightlyArtifact(
        override val url: String,
        val preferredAbi: String,
        val expectedVersionCode: Int,
    ) : ManagerApkSource
}

data class ManagerUpdateInfo(
    val channel: ManagerUpdateChannel,
    val variant: ManagerVariant = ManagerVariant.NORMAL,
    val versionCode: Int,
    val versionName: String,
    val abi: String,
    val fileName: String,
    val source: ManagerApkSource,
    val changelog: String = "",
)

