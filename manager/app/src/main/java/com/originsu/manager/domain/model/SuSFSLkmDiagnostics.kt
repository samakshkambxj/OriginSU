package com.originsu.manager.domain.model

/**
 * Builds the technical report copied from the SuSFS LKM screen.
 *
 * The keys are deliberately stable and machine-readable so reports can be
 * compared between builds. They are not shown as ordinary settings copy.
 */
fun buildSuSFSLkmDiagnosticsReport(
    status: SuSFSLkmStatus?,
    action: SuSFSLkmActionResult?,
): String = buildString {
    appendLine("SuSFS LKM diagnostics")
    appendLine("version=${status?.version.orEmpty()}")
    appendLine("variant=${status?.variant.orEmpty()}")
    appendLine("loaded=${status?.isLoaded == true}")
    appendLine("built_in=${status?.isBuiltIn == true}")
    appendLine("lkm=${status?.isLkm == true}")
    appendLine("current_kmi=${status?.currentKmi.orEmpty()}")
    appendLine("loaded_kmi=${status?.loadedKmi.orEmpty()}")
    appendLine("asset_name=${status?.assetName.orEmpty()}")
    appendLine("asset_sha256=${status?.assetSha256.orEmpty()}")
    appendLine("asset_size=${status?.assetSize ?: 0L}")
    appendLine("loaded_at=${status?.loadedAt ?: 0L}")
    appendLine("autoload_enabled=${status?.autoloadEnabled == true}")
    appendLine("autoload_kmi=${status?.autoloadKmi.orEmpty()}")
    appendLine("autoload_attempts=${status?.autoloadAttempts ?: 0}")
    appendLine("autoload_disabled_reason=${status?.autoloadDisabledReason.orEmpty()}")
    appendLine("supported_kmis=${status?.supportedKmis?.joinToString(",").orEmpty()}")
    appendLine("is_arm64=${status?.isArm64 == true}")
    appendLine("is_lkm_mode=${status?.isLkmMode == true}")
    appendLine("root_available=${status?.rootAvailable == true}")
    appendLine("last_command=${action?.command.orEmpty()}")
    appendLine("last_success=${action?.success == true}")
    appendLine("last_stdout=${action?.stdout.orEmpty()}")
    appendLine("last_stderr=${action?.stderr.orEmpty()}")
}
