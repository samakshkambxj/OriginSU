package com.originsu.manager.domain.model

/** Minimal module identity needed to back up an installed module directory. */
data class ModuleBackupEntry(
    val dirId: String,
    val versionCode: Int,
)

data class ModuleBackupInfo(
    val fileName: String,
    val moduleId: String,
    val versionCode: Int,
    val createdAtMillis: Long,
    val sizeBytes: Long,
)

data class ModuleBackupOutcome(
    val succeeded: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
)

/** One downloadable module inside a bundle script. */
data class BundleScriptModule(
    val id: String = "",
    val name: String = "",
    val zipUrl: String = "",
    val updateJson: String = "",
)

data class BundleScript(
    val name: String = "",
    val modules: List<BundleScriptModule> = emptyList(),
)

data class ScriptParseResult(
    val name: String,
    val modules: List<BundleScriptModule>,
    val skippedNoUrl: Int,
)

data class BundleImportResult(
    val imported: Int,
    val skipped: Int,
)
