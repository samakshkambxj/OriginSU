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
) {
    val isBuiltIn: Boolean get() = isLoaded && !isLkm

    fun supportsKmi(kmi: String?): Boolean {
        val normalized = kmi?.trim().orEmpty()
        if (normalized.isEmpty() || supportedKmis.isEmpty()) return true
        return supportedKmis.any { it.equals(normalized, ignoreCase = true) }
    }
}

data class SuSFSLkmActionResult(
    val success: Boolean,
    val error: String = "",
)
