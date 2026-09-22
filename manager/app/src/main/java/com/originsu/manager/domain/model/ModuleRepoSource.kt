package com.originsu.manager.domain.model

enum class RepoSchema {
    /** Legacy kernelsu.org index: moduleId/moduleName/authors + per-module detail files. */
    LEGACY,

    /** KernelSU-Next style index: name/description/author/repoUrl/license (+bannerUrl). */
    KSUN,
}

enum class ModuleCategory {
    ARCHIVE,
    OSS,
    NON_FREE,
    META,
}

/** One module index a repo browser page can pull from. */
data class RepoSource(
    val id: String,
    val name: String,
    val url: String,
    val schema: RepoSchema,
    val category: ModuleCategory,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
)
