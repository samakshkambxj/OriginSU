package com.kazesu.manager.data.update

import android.os.Build
import android.util.Log
import com.kazesu.manager.BuildConfig
import com.kazesu.manager.data.model.ManagerApkSource
import com.kazesu.manager.data.model.ManagerUpdateChannel
import com.kazesu.manager.data.model.ManagerUpdateInfo
import com.kazesu.manager.data.model.ManagerVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ManagerUpdateRepository(
    private val client: OkHttpClient,
) {
    private companion object {
        const val TAG = "ManagerUpdateRepository"
        const val REPOSITORY = "KazeSU/KazeSU"
        const val WORKFLOW_FILE = "build-manager.yml"
        const val BRANCH = "dev"
        const val NORMAL_ARTIFACT = "Manager-release"
        const val SPOOFED_ARTIFACT = "Spoofed-Manager-release"

        // Keep in sync with getVersionCode() in manager/build.gradle.kts.
        const val CI_MANAGER_VERSION_CODE_OFFSET = 3 * 10000 + 996
        const val SHORT_SHA_LENGTH = 7
        const val UNIVERSAL_ABI = "universal"
    }

    private val updateClient = client.newBuilder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val managerApkPattern = Regex(
        "^KazeSU_(.+)_(\\d+)-(arm64-v8a|armeabi-v7a|x86_64|riscv64|universal)-release\\.apk$"
    )
    private val commitCountLinkPattern = Regex("""[?&]page=(\d+)>; rel="last"""")

    suspend fun checkStableUpdate(
        variant: ManagerVariant = ManagerVariant.NORMAL,
    ): ManagerUpdateInfo? = withContext(Dispatchers.IO) {
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val release = requestJson("https://api.github.com/repos/$REPOSITORY/releases/latest")
        val changelog = release.optString("body")

        if (variant == ManagerVariant.SPOOFED) {
            // Stable releases publish normal APKs. The matching spoofed APKs are
            // available from the workflow run that built the release tag.
            return@withContext checkTagNightlyUpdate(
                tag = release.optString("tag_name"),
                supportedAbis = supportedAbis,
                changelog = changelog,
            )
        }

        val assets = release.optJSONArray("assets") ?: return@withContext null
        val candidates = mutableListOf<ManagerApkCandidate>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val parsed = parseApkName(asset.optString("name")) ?: continue
            val downloadUrl = asset.optString("browser_download_url")
            if (downloadUrl.isBlank()) continue

            candidates += ManagerApkCandidate(
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                abi = parsed.abi,
                fileName = parsed.fileName,
                source = ManagerApkSource.DirectApk(downloadUrl),
            )
        }

        selectCandidate(candidates, supportedAbis)
            ?.toUpdateInfo(ManagerUpdateChannel.STABLE, changelog)
    }

    suspend fun checkBetaUpdate(
        variant: ManagerVariant = ManagerVariant.NORMAL,
    ): ManagerUpdateInfo? = withContext(Dispatchers.IO) {
        val workflowRuns = requestJson(
            "https://api.github.com/repos/$REPOSITORY/actions/workflows/$WORKFLOW_FILE/runs" +
                "?branch=$BRANCH&status=success&per_page=1&event=push"
        ).optJSONArray("workflow_runs") ?: return@withContext null
        val run = workflowRuns.optJSONObject(0) ?: return@withContext null
        nightlyUpdateFromRun(
            run = run,
            channel = ManagerUpdateChannel.BETA,
            variant = variant,
            changelog = run.optJSONObject("head_commit")?.optString("message").orEmpty(),
        )
    }

    private suspend fun checkTagNightlyUpdate(
        tag: String,
        supportedAbis: List<String>,
        changelog: String,
    ): ManagerUpdateInfo? {
        if (tag.isBlank()) return null
        val tagSha = requestJson("https://api.github.com/repos/$REPOSITORY/commits/$tag")
            .optString("sha")
        if (tagSha.isBlank()) return null
        val workflowRuns = requestJson(
            "https://api.github.com/repos/$REPOSITORY/actions/workflows/$WORKFLOW_FILE/runs" +
                "?head_sha=$tagSha&per_page=5"
        ).optJSONArray("workflow_runs") ?: return null
        for (index in 0 until workflowRuns.length()) {
            val run = workflowRuns.optJSONObject(index) ?: continue
            if (run.optString("status") != "completed") continue
            if (run.optString("conclusion") != "success") continue
            return nightlyUpdateFromRun(
                run = run,
                channel = ManagerUpdateChannel.STABLE,
                variant = ManagerVariant.SPOOFED,
                changelog = changelog,
                supportedAbis = supportedAbis,
                versionName = tag,
            )
        }
        return null
    }

    private suspend fun nightlyUpdateFromRun(
        run: JSONObject,
        channel: ManagerUpdateChannel,
        variant: ManagerVariant,
        changelog: String,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
        versionName: String? = null,
    ): ManagerUpdateInfo? {
        val runId = run.optLong("id", -1L)
        val headSha = run.optString("head_sha")
        if (runId <= 0L || headSha.isBlank()) return null

        val commitCount = requestCommitCount(headSha) ?: return null
        val versionCode = CI_MANAGER_VERSION_CODE_OFFSET + commitCount
        if (versionCode <= BuildConfig.VERSION_CODE) return null

        val artifact = if (variant == ManagerVariant.SPOOFED) SPOOFED_ARTIFACT else NORMAL_ARTIFACT
        val preferredAbi = supportedAbis.firstOrNull { it in SUPPORTED_ABIS } ?: UNIVERSAL_ABI
        val displayName = versionName?.takeIf { it.isNotBlank() } ?: headSha.take(SHORT_SHA_LENGTH)
        return ManagerUpdateInfo(
            channel = channel,
            variant = variant,
            versionCode = versionCode,
            versionName = displayName,
            abi = preferredAbi,
            fileName = (if (variant == ManagerVariant.SPOOFED) "KazeSU-Spoofed_" else "KazeSU_") +
                "${headSha.take(SHORT_SHA_LENGTH)}_$versionCode-$preferredAbi-release.apk",
            source = ManagerApkSource.NightlyArtifact(
                url = "https://nightly.link/$REPOSITORY/actions/runs/$runId/$artifact.zip",
                preferredAbi = preferredAbi,
                expectedVersionCode = versionCode,
            ),
            changelog = changelog,
        )
    }

    fun findNightlyApkEntry(
        entries: List<ZipEntryMetadata>,
        expectedVersionCode: Int,
        preferredAbi: String,
    ): ZipEntryMetadata? {
        val candidates = entries.mapNotNull { entry ->
            val parsed = parseApkName(entry.name.substringAfterLast('/')) ?: return@mapNotNull null
            if (parsed.versionCode == expectedVersionCode) entry to parsed else null
        }

        candidates.firstOrNull { (_, parsed) -> parsed.abi == preferredAbi }
            ?.first
            ?.let { return it }
        return candidates.firstOrNull { (_, parsed) -> parsed.abi == UNIVERSAL_ABI }?.first
    }

    private fun requestJson(url: String): JSONObject {
        val response = updateClient.newCall(newRequest(url)).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("GitHub update request failed: HTTP ${it.code}")
            return runCatching { JSONObject(it.body.string()) }.getOrElse { error ->
                throw IOException("Invalid GitHub update response", error)
            }
        }
    }

    private fun requestCommitCount(commitSha: String): Int? {
        val url = "https://api.github.com/repos/$REPOSITORY/commits?sha=$commitSha&per_page=1"
        return runCatching {
            updateClient.newCall(newRequest(url)).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                parseCommitCount(response.header("Link")) ?: 1
            }
        }.onFailure {
            Log.w(TAG, "GitHub commit history request failed", it)
        }.getOrNull()
    }

    private fun newRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "KazeSU-Manager/${BuildConfig.VERSION_NAME}")
        .build()

    internal fun parseCommitCount(linkHeader: String?): Int? =
        commitCountLinkPattern.find(linkHeader.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun parseApkName(fileName: String): ParsedApkName? {
        val match = managerApkPattern.matchEntire(fileName) ?: return null
        val versionCode = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedApkName(
            versionName = match.groupValues[1],
            versionCode = versionCode,
            abi = match.groupValues[3],
            fileName = fileName,
        )
    }

    private fun selectCandidate(
        candidates: List<ManagerApkCandidate>,
        supportedAbis: List<String>,
    ): ManagerApkCandidate? {
        val latestVersionCode = candidates.maxOfOrNull { it.versionCode } ?: return null
        if (latestVersionCode <= BuildConfig.VERSION_CODE) return null

        val latestCandidates = candidates.filter { it.versionCode == latestVersionCode }
        supportedAbis.forEach { abi ->
            latestCandidates.firstOrNull { it.abi == abi }?.let { return it }
        }
        return latestCandidates.firstOrNull { it.abi == UNIVERSAL_ABI }
    }

    private data class ParsedApkName(
        val versionName: String,
        val versionCode: Int,
        val abi: String,
        val fileName: String,
    )

    private data class ManagerApkCandidate(
        val versionName: String,
        val versionCode: Int,
        val abi: String,
        val fileName: String,
        val source: ManagerApkSource,
    ) {
        fun toUpdateInfo(
            channel: ManagerUpdateChannel,
            changelog: String = "",
        ) = ManagerUpdateInfo(
            channel = channel,
            versionCode = versionCode,
            versionName = versionName,
            abi = abi,
            fileName = fileName,
            source = source,
            changelog = changelog,
        )
    }

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "riscv64")
}
