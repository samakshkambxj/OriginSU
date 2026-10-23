package com.originsu.manager.domain.model

data class SuSFSLkmStatus(
    val version: String = "",
    val variant: String = "",
    val enabledFeatures: String = "",
    val autoloadEnabled: Boolean = false,
    val autoloadKmi: String = "",
    val currentKmi: String = "",
    val supportedKmis: List<String> = emptyList(),
    val isLoaded: Boolean = false,
    val isLkm: Boolean = false,
    val isArm64: Boolean = true,
    val isLkmMode: Boolean = false,
    val rootAvailable: Boolean = false,
) {
    val isBuiltIn: Boolean get() = isLoaded && !isLkm

    fun supportsKmi(kmi: String?): Boolean {
        val normalized = kmi?.trim().orEmpty()
        if (normalized.isEmpty() || supportedKmis.isEmpty()) return true
        return supportedKmis.any { it.equals(normalized, ignoreCase = true) }
    }

    fun preflight(kmi: String? = null): List<SuSFSLkmCheckResult> {
        if (isBuiltIn) {
            return listOf(
                SuSFSLkmCheckResult(SuSFSLkmCheck.Active, SuSFSLkmCheckState.Ok),
            )
        }
        val target = kmi?.trim().orEmpty().ifBlank { currentKmi.trim() }
        return listOf(
            SuSFSLkmCheckResult(
                SuSFSLkmCheck.Arm64,
                if (isArm64) SuSFSLkmCheckState.Ok else SuSFSLkmCheckState.Error,
            ),
            SuSFSLkmCheckResult(
                SuSFSLkmCheck.LkmMode,
                if (isLkmMode) SuSFSLkmCheckState.Ok else SuSFSLkmCheckState.Error,
            ),
            SuSFSLkmCheckResult(
                SuSFSLkmCheck.Root,
                if (rootAvailable) SuSFSLkmCheckState.Ok else SuSFSLkmCheckState.Error,
            ),
            SuSFSLkmCheckResult(
                SuSFSLkmCheck.Kmi,
                if (target.isNotEmpty()) SuSFSLkmCheckState.Ok else SuSFSLkmCheckState.Error,
            ),
            SuSFSLkmCheckResult(
                SuSFSLkmCheck.GuardAsset,
                when {
                    supportedKmis.isEmpty() -> SuSFSLkmCheckState.Unknown
                    target.isNotEmpty() && supportsKmi(target) -> SuSFSLkmCheckState.Ok
                    else -> SuSFSLkmCheckState.Error
                },
            ),
            SuSFSLkmCheckResult(
                SuSFSLkmCheck.Active,
                if (isLoaded) SuSFSLkmCheckState.Ok else SuSFSLkmCheckState.Warning,
            ),
        )
    }
}

enum class SuSFSLkmCheck {
    Arm64,
    LkmMode,
    Root,
    Kmi,
    GuardAsset,
    Active,
}

enum class SuSFSLkmCheckState {
    Ok,
    Warning,
    Error,
    Unknown,
}

data class SuSFSLkmCheckResult(
    val check: SuSFSLkmCheck,
    val state: SuSFSLkmCheckState,
)

data class SuSFSLkmActionResult(
    val success: Boolean,
    val error: String = "",
)
