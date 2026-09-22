package com.originsu.manager.data.module

import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.data.network.NetworkRequestRepository
import com.originsu.manager.domain.model.ModuleCategory
import com.originsu.manager.domain.model.RepoSchema
import com.originsu.manager.domain.model.RepoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Built-in + user-added module indexes for the repo browser.
 * Custom sources persist as JSON; enable-state (built-in and custom) lives
 * in a disabled-id set so built-ins stay defined in code.
 */
class ModuleRepoSourceRepository(
    private val appSettingsRepository: AppSettingsRepository,
    private val networkRequestRepository: NetworkRequestRepository,
) {
    companion object {
        const val PREF_CUSTOM_SOURCES = "module_repo_custom_sources"
        const val PREF_DISABLED_SOURCES = "module_repo_disabled_sources"

        val BUILT_IN = listOf(
            RepoSource(
                id = "builtin-archive",
                name = "KernelSU Archive",
                url = "https://web.archive.org/web/20260104051318id_/https://modules.kernelsu.org/modules.json",
                schema = RepoSchema.LEGACY,
                category = ModuleCategory.ARCHIVE,
                builtIn = true,
            ),
            RepoSource(
                id = "builtin-oss",
                name = "KSU-Next OSS",
                url = "https://raw.githubusercontent.com/KernelSU-Next/KernelSU-Next-Modules-Repo/refs/heads/main/modules.json",
                schema = RepoSchema.KSUN,
                category = ModuleCategory.OSS,
                builtIn = true,
            ),
            RepoSource(
                id = "builtin-nonfree",
                name = "KSU-Next Non-free",
                url = "https://raw.githubusercontent.com/KernelSU-Next/KernelSU-Next-Modules-Repo/refs/heads/main/non_free_modules.json",
                schema = RepoSchema.KSUN,
                category = ModuleCategory.NON_FREE,
                builtIn = true,
            ),
            RepoSource(
                id = "builtin-meta",
                name = "KSU-Next Meta",
                url = "https://raw.githubusercontent.com/KernelSU-Next/KernelSU-Next-Modules-Repo/refs/heads/main/meta_modules.json",
                schema = RepoSchema.KSUN,
                category = ModuleCategory.META,
                builtIn = true,
            ),
        )
    }

    private val mutableSources = MutableStateFlow(loadAll())
    val sources: StateFlow<List<RepoSource>> = mutableSources.asStateFlow()

    fun enabledSources(): List<RepoSource> = mutableSources.value.filter { it.enabled }

    /** Fetch [url] and detect which index schema it serves, or null. */
    suspend fun probe(url: String): RepoSchema? = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = url.trim()
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "bad url" }
            val body = networkRequestRepository.fetch(trimmed).getOrThrow()
            val first = JSONArray(body).optJSONObject(0) ?: return@runCatching null
            when {
                first.has("moduleId") -> RepoSchema.LEGACY
                first.has("repoUrl") -> RepoSchema.KSUN
                else -> null
            }
        }.getOrNull()
    }

    fun addSource(name: String, url: String, schema: RepoSchema): RepoSource? {
        if (name.isBlank()) return null
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return null
        if (mutableSources.value.any { it.url == trimmedUrl }) return null
        val source = RepoSource(
            id = "custom-${System.currentTimeMillis()}",
            name = name.trim().take(48),
            url = trimmedUrl,
            schema = schema,
            category = if (schema == RepoSchema.KSUN) ModuleCategory.OSS else ModuleCategory.ARCHIVE,
        )
        persistCustom(mutableSources.value.filter { !it.builtIn } + source)
        return source
    }

    fun removeSource(id: String) {
        if (BUILT_IN.any { it.id == id }) return
        persistCustom(mutableSources.value.filter { !it.builtIn && it.id != id })
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val disabled = appSettingsRepository.getStringSet(PREF_DISABLED_SOURCES).toMutableSet()
        if (enabled) disabled.remove(id) else disabled.add(id)
        appSettingsRepository.putStringSet(PREF_DISABLED_SOURCES, disabled)
        mutableSources.update { sources ->
            sources.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
    }

    private fun loadAll(): List<RepoSource> {
        val disabled = runCatching {
            appSettingsRepository.getStringSet(PREF_DISABLED_SOURCES)
        }.getOrDefault(emptySet())
        val customs = runCatching {
            val raw = appSettingsRepository.getString(PREF_CUSTOM_SOURCES).orEmpty()
            if (raw.isBlank()) emptyList() else parseCustom(raw)
        }.getOrDefault(emptyList())
        return BUILT_IN.map { it.copy(enabled = it.id !in disabled) } +
            customs.map { it.copy(enabled = it.id !in disabled) }
    }

    private fun persistCustom(customs: List<RepoSource>) {
        val array = JSONArray().apply {
            customs.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("url", it.url)
                        .put("schema", it.schema.name)
                        .put("category", it.category.name)
                )
            }
        }
        appSettingsRepository.putString(PREF_CUSTOM_SOURCES, array.toString())
        mutableSources.update { sources ->
            sources.filter { it.builtIn } + customs
        }
    }

    private fun parseCustom(raw: String): List<RepoSource> {
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val url = obj.optString("url").trim()
                if (url.isBlank()) return@mapNotNull null
                RepoSource(
                    id = obj.optString("id").ifBlank { "custom-$index" },
                    name = obj.optString("name").ifBlank { url },
                    url = url,
                    schema = runCatching { RepoSchema.valueOf(obj.optString("schema")) }
                        .getOrDefault(RepoSchema.LEGACY),
                    category = runCatching { ModuleCategory.valueOf(obj.optString("category")) }
                        .getOrDefault(ModuleCategory.ARCHIVE),
                )
            }
        }.getOrDefault(emptyList())
    }
}
