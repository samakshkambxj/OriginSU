package com.originsu.manager.data.module

import com.originsu.manager.data.network.NetworkRequestRepository
import com.originsu.manager.data.network.NetworkStatusRepository
import com.originsu.manager.domain.model.CatalogAuthor
import com.originsu.manager.domain.model.CatalogModule
import com.originsu.manager.domain.model.ModuleCategory
import com.originsu.manager.domain.model.ModuleCatalogFailure
import com.originsu.manager.domain.model.ModuleCatalogResult
import com.originsu.manager.domain.model.ModuleRelease
import com.originsu.manager.domain.model.ModuleReleaseAsset
import com.originsu.manager.domain.model.RepoSchema
import com.originsu.manager.domain.model.RepoSource
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Multi-source module repo (built-in archive + KSUN indexes + user-added).
class ModuleCatalogRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
    private val sourceRepository: ModuleRepoSourceRepository,
) {
    private val refreshMutex = Mutex()
    private val mutableModules = MutableStateFlow<List<CatalogModule>>(emptyList())
    private val mutableRefreshing = MutableStateFlow(false)
    private val mutableOffline = MutableStateFlow(false)

    val modules: StateFlow<List<CatalogModule>> = mutableModules.asStateFlow()
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()
    val offline: StateFlow<Boolean> = mutableOffline.asStateFlow()

    /** Per-module GitHub release cache for KSUN details (owner/repo -> releases). */
    private val githubReleasesCache = mutableMapOf<String, List<ModuleRelease>>()
    private val githubCacheMutex = Mutex()

    suspend fun refresh(): ModuleCatalogResult<List<CatalogModule>> = refreshMutex.withLock {
        if (!networkStatusRepository.isAvailable()) {
            mutableOffline.value = true
            return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.Offline)
        }
        mutableOffline.value = false
        mutableRefreshing.value = true
        try {
            withContext(Dispatchers.IO) {
                val sources = sourceRepository.enabledSources()
                if (sources.isEmpty()) {
                    return@withContext ModuleCatalogResult.Failure(
                        ModuleCatalogFailure.Network("")
                    )
                }
                val failures = mutableListOf<String>()
                val merged = coroutineScope {
                    sources.map { source ->
                        async(Dispatchers.IO) {
                            runCatching { fetchSource(source) }
                                .onFailure { failures.add(source.name) }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll().flatten()
                }
                // First source wins on id collisions.
                val deduped = merged.distinctBy { it.moduleId }
                mutableModules.value = deduped
                if (deduped.isEmpty()) {
                    ModuleCatalogResult.Failure(
                        ModuleCatalogFailure.Network(
                            failures.distinct().joinToString().ifBlank { "empty" }
                        )
                    )
                } else {
                    ModuleCatalogResult.Success(deduped)
                }
            }
        } finally {
            mutableRefreshing.value = false
        }
    }

    suspend fun get(moduleId: String): ModuleCatalogResult<CatalogModule> {
        mutableModules.value.firstOrNull { it.moduleId == moduleId }?.let {
            return ModuleCatalogResult.Success(enrich(it))
        }
        return when (val refreshed = refresh()) {
            is ModuleCatalogResult.Failure -> refreshed
            is ModuleCatalogResult.Success -> refreshed.value.firstOrNull { it.moduleId == moduleId }
                ?.let { ModuleCatalogResult.Success(enrich(it)) }
                ?: ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
        }
    }

    /**
     * Resolve installable zip URLs for [moduleIds] (latest release, first zip
     * asset). Modules that cannot be resolved are skipped.
     */
    suspend fun resolveDownloadUrls(moduleIds: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (mutableModules.value.isEmpty()) {
                runCatching { refresh() }
            }
            val result = mutableMapOf<String, String>()
            for (id in moduleIds.distinct()) {
                val module = mutableModules.value.firstOrNull { it.moduleId == id } ?: continue
                val detailed = runCatching { enrich(module) }.getOrNull() ?: module
                val url = detailed.releases.firstOrNull()
                    ?.assets?.firstOrNull { it.downloadUrl.endsWith(".zip", ignoreCase = true) }
                    ?.downloadUrl
                    ?: detailed.latestAsset?.assets
                        ?.firstOrNull { it.downloadUrl.endsWith(".zip", ignoreCase = true) }
                        ?.downloadUrl
                if (!url.isNullOrBlank()) result[id] = url
            }
            result
        }

    /** Fetch the detail for a single module; missing details degrade to index data. */
    private suspend fun enrich(candidate: CatalogModule): CatalogModule {
        return when (candidate.sourceSchema()) {
            RepoSchema.LEGACY -> {
                val detail = fetchLegacyDetail(candidate) ?: return candidate
                val releases = detail.releases
                candidate.copy(
                    readme = detail.readme,
                    sourceUrl = detail.sourceUrl,
                    releases = releases,
                    latestAsset = releases.firstOrNull { it.name == candidate.latestRelease },
                )
            }

            RepoSchema.KSUN -> {
                val releases = fetchGithubReleases(candidate.repoUrl)
                if (releases.isEmpty()) return candidate
                candidate.copy(
                    releases = releases,
                    latestAsset = releases.firstOrNull(),
                )
            }
        }
    }

    private suspend fun fetchSource(source: RepoSource): List<CatalogModule> {
        val body = networkRequestRepository.fetch(source.url).getOrThrow()
        val json = JSONArray(body)
        return coroutineScope {
            (0 until json.length()).map { index ->
                async(Dispatchers.IO) {
                    json.optJSONObject(index)?.let {
                        when (source.schema) {
                            RepoSchema.LEGACY -> parseLegacyModule(it, source)
                            RepoSchema.KSUN -> parseKsunModule(it, source)
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun parseLegacyModule(item: JSONObject, source: RepoSource): CatalogModule? {
        val moduleId = item.optString("moduleId").takeIf(String::isNotEmpty) ?: return null
        val authorList = item.optJSONArray("authors")?.let { authors ->
            (0 until authors.length()).mapNotNull { index ->
                authors.optJSONObject(index)?.let { author ->
                    val name = author.optString("name").trim()
                    name.takeIf(String::isNotEmpty)?.let {
                        CatalogAuthor(it, stripTicks(author.optString("link")))
                    }
                }
            }
        }.orEmpty()
        val latestReleaseObject = item.optJSONObject("latestRelease")
        val latestRelease = latestReleaseObject?.optString(
            "name",
            latestReleaseObject.optString("version"),
        ).orEmpty()

        return CatalogModule(
            moduleId = moduleId,
            moduleName = item.optString("moduleName"),
            authors = authorList.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.name }
                ?: item.optString("authors"),
            authorList = authorList,
            summary = item.optString("summary"),
            metamodule = item.optBoolean("metamodule"),
            stargazerCount = item.optInt("stargazerCount"),
            updatedAt = item.optString("updatedAt"),
            createdAt = item.optString("createdAt"),
            latestRelease = latestRelease,
            latestReleaseTime = latestReleaseObject?.optString("time").orEmpty(),
            latestVersionCode = latestReleaseObject?.opt("versionCode").toIntCompat(),
            latestAsset = null,
            installed = SuFile.open("/data/adb/modules/$moduleId/module.prop").exists(),
            readme = "",
            sourceUrl = "",
            releases = emptyList(),
            category = source.category,
            sourceId = source.id,
            sourceName = source.name,
            detailBase = source.url.substringBeforeLast("/"),
        )
    }

    private fun parseKsunModule(item: JSONObject, source: RepoSource): CatalogModule? {
        val repoUrl = stripTicks(item.optString("repoUrl")).trimEnd('/')
        val moduleId = item.optString("id").trim()
            .ifBlank { repoUrl.substringAfterLast('/').substringBefore(".git") }
            .takeIf(String::isNotEmpty) ?: return null
        val name = item.optString("name").trim().ifBlank { moduleId }
        val author = item.optString("author").trim()

        return CatalogModule(
            moduleId = moduleId,
            moduleName = name,
            authors = author,
            authorList = author.split(',', ';').map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { CatalogAuthor(it, "") },
            summary = item.optString("description"),
            metamodule = source.category == ModuleCategory.META,
            stargazerCount = 0,
            updatedAt = "",
            createdAt = "",
            latestRelease = "",
            latestReleaseTime = "",
            latestVersionCode = 0,
            latestAsset = null,
            installed = SuFile.open("/data/adb/modules/$moduleId/module.prop").exists(),
            readme = "",
            sourceUrl = repoUrl,
            releases = emptyList(),
            category = source.category,
            sourceId = source.id,
            sourceName = source.name,
            bannerUrl = stripTicks(item.optString("bannerUrl")),
            repoUrl = repoUrl,
        )
    }

    private suspend fun fetchLegacyDetail(candidate: CatalogModule): Detail? {
        // Index .../modules.json -> details at .../module/<id>.json.
        val base = candidate.detailBase.ifBlank { return null }
        return networkRequestRepository
            .fetch("$base/module/${candidate.moduleId}.json")
            .getOrNull()
            ?.let { body ->
                val json = JSONObject(body)
                val releases = json.optJSONArray("releases")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.toRelease()
                    }
                }.orEmpty()
                Detail(
                    readme = json.optString("readmeHTML"),
                    sourceUrl = stripTicks(json.optString("sourceUrl")),
                    releases = releases,
                )
            }
    }

    private suspend fun fetchGithubReleases(repoUrl: String): List<ModuleRelease> {
        val slug = repoUrl.substringAfter("github.com/", "").trim('/').substringBefore(".git")
        if (slug.isBlank() || '/' !in slug) return emptyList()
        githubCacheMutex.withLock {
            githubReleasesCache[slug]?.let { return it }
        }
        val releases = runCatching {
            val body = networkRequestRepository
                .fetch("https://api.github.com/repos/$slug/releases?per_page=20")
                .getOrThrow()
            JSONArray(body).let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.toGithubRelease()
                }
            }
        }.getOrDefault(emptyList())
        githubCacheMutex.withLock {
            githubReleasesCache[slug] = releases
        }
        return releases
    }

    private fun JSONObject.toGithubRelease(): ModuleRelease? {
        val tag = optString("tagName").ifBlank { return null }
        val assets = optJSONArray("assets")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { asset ->
                    val url = asset.optString("browser_download_url")
                    if (url.isBlank()) null else ModuleReleaseAsset(
                        name = asset.optString("name").ifBlank { url.substringAfterLast('/') },
                        downloadUrl = url,
                        size = asset.optLong("size"),
                        downloadCount = asset.opt("download_count").toIntCompat(),
                    )
                }
            }
        }.orEmpty()
        return ModuleRelease(
            name = optString("name").ifBlank { tag },
            tagName = tag,
            publishedAt = optString("published_at"),
            descriptionHTML = optString("body"),
            assets = assets,
        )
    }

    private fun JSONObject.toRelease(): ModuleRelease {
        val releaseName = optString("name", optString("tagName", optString("version")))
        val assets = optJSONArray("releaseAssets")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { asset ->
                    val name = asset.optString("name")
                    val url = stripTicks(asset.optString("downloadUrl"))
                    if (name.isBlank() || url.isBlank()) null else ModuleReleaseAsset(
                        name = name,
                        downloadUrl = url,
                        size = asset.optLong("size"),
                        downloadCount = asset.opt("downloadCount").toIntCompat(),
                    )
                }
            }
        }.orEmpty()
        return ModuleRelease(
            name = releaseName,
            tagName = optString("tagName", releaseName),
            publishedAt = optString("publishedAt"),
            descriptionHTML = optString("descriptionHTML"),
            assets = assets,
        )
    }

    private fun stripTicks(value: String): String = value.trim().let {
        if (it.startsWith('`') && it.endsWith('`') && it.length >= 2) {
            it.substring(1, it.length - 1)
        } else {
            it
        }
    }

    private fun Any?.toIntCompat(): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

    private data class Detail(
        val readme: String,
        val sourceUrl: String,
        val releases: List<ModuleRelease>,
    )
}

private fun CatalogModule.sourceSchema(): RepoSchema =
    if (repoUrl.isNotBlank()) RepoSchema.KSUN else RepoSchema.LEGACY
