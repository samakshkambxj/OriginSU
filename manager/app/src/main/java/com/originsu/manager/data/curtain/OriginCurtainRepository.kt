package com.originsu.manager.data.curtain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.data.shell.OriginZygiskStatus
import com.originsu.manager.domain.model.OriginCurtainRelease
import com.originsu.manager.domain.model.OriginCurtainStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Origin Curtain: first-class integration for the upstream HMA-OSS Zygisk
 * module.
 *
 * The module and its config app are **not** bundled with OriginSU. This
 * repository only reports whether they are present and downloads the
 * unmodified release zip from frknkrc44/HMA-OSS when the user asks for it.
 * Installation itself goes through the normal module pipeline so OriginGuard
 * and the standard reboot handling still apply.
 */
class OriginCurtainRepository(
    context: Context,
    httpClient: OkHttpClient,
    private val ksuCliRepository: KsuCliRepository,
) {
    companion object {
        const val MODULE_ID = "hma_oss_zygisk"
        const val MANAGER_PACKAGE = "org.frknkrc44.hma_oss"

        private const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
        private const val RELEASES_API =
            "https://api.github.com/repos/frknkrc44/HMA-OSS/releases?per_page=10"
        private const val USER_AGENT = "OriginSU-OriginCurtain"
        private const val CACHE_DIR = "origin-curtain"
    }

    private val appContext = context.applicationContext

    // The shared client uses a short read timeout for API calls; a module zip
    // needs more headroom on slow mobile networks.
    private val downloadClient = httpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun getStatus(): OriginCurtainStatus = withContext(Dispatchers.IO) {
        val zygisk = runCatching { ksuCliRepository.getOriginZygiskStatus() }
            .getOrDefault(OriginZygiskStatus())

        // One root round-trip keeps the resume refresh cheap, and skipping it
        // entirely avoids spawning a shell when root is unavailable.
        val rawStatus = if (ksuCliRepository.rootAvailable()) {
            ksuCliRepository.runCmd(
                ksuCliRepository.getRootShell(),
                "if [ -f $MODULE_DIR/module.prop ]; then " +
                    "echo CURTAIN_INSTALLED=1; " +
                    "sed -n 's/^version=//p' $MODULE_DIR/module.prop 2>/dev/null | head -n 1; " +
                    "[ -f $MODULE_DIR/disable ] && echo CURTAIN_DISABLED=1; " +
                    "else echo CURTAIN_INSTALLED=0; fi",
            )
        } else {
            ""
        }
        val statusLines = rawStatus.lineSequence().map { it.trim() }.toList()
        val moduleInstalled = statusLines.contains("CURTAIN_INSTALLED=1")
        val moduleDisabled = statusLines.contains("CURTAIN_DISABLED=1")
        val moduleVersion = statusLines.firstOrNull {
            it.isNotBlank() && !it.startsWith("CURTAIN_")
        }.orEmpty()
        val (managerInstalled, managerVersion) = managerInfo()
        val skippedByZygisk = zygisk.disabledModules.contains(MODULE_ID) ||
            zygisk.quarantinedModules.contains(MODULE_ID)

        OriginCurtainStatus(
            moduleInstalled = moduleInstalled,
            moduleEnabled = moduleInstalled && !moduleDisabled && !skippedByZygisk,
            moduleLoaded = zygisk.installedModules.any {
                it.id == MODULE_ID && it.loaded
            },
            moduleVersion = moduleVersion,
            managerInstalled = managerInstalled,
            managerVersion = managerVersion,
            zygiskEnabled = zygisk.deployed,
        )
    }

    /**
     * Resolve and download the newest stable upstream release. Returns null
     * when the release cannot be resolved, the download fails, or the archive
     * is not the expected HMA-OSS Zygisk module.
     */
    suspend fun downloadLatestRelease(): File? = withContext(Dispatchers.IO) {
        val release = resolveLatestRelease() ?: return@withContext null
        val directory = File(appContext.cacheDir, CACHE_DIR).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }

        val fileName = release.fileName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "hma-oss.zip" }
        val target = File(directory, fileName)

        val downloaded = runCatching {
            val request = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                val expected = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                expected <= 0L || target.length() == expected
            }
        }.getOrDefault(false)

        if (!downloaded || !isHmaOssModule(target)) {
            target.delete()
            return@withContext null
        }
        target
    }

    /** Intent that opens the HMA-OSS config app, or null when unavailable. */
    fun managerLaunchIntent(): Intent? = runCatching {
        appContext.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }.getOrNull()

    private fun managerInfo(): Pair<Boolean, String> {
        val packageManager = appContext.packageManager
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    MANAGER_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
            }
            true to info.versionName.orEmpty()
        }.getOrDefault(false to "")
    }

    private fun resolveLatestRelease(): OriginCurtainRelease? {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        val body = runCatching {
            downloadClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return null
        val releases = runCatching { JSONArray(body) }.getOrNull() ?: return null
        return (0 until releases.length())
            .asSequence()
            .mapNotNull { releases.optJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .firstNotNullOfOrNull { release ->
                preferredAsset(release.optJSONArray("assets") ?: JSONArray())?.let { asset ->
                    OriginCurtainRelease(
                        version = release.optString("tag_name"),
                        fileName = asset.optString("name"),
                        downloadUrl = asset.optString("browser_download_url"),
                    )
                }
            }
    }

    /**
     * Prefer the signed release artifact. HMA-OSS publishes a debug zip first
     * in its asset list; installing it would work but pulls in the debug
     * manager and a much larger payload.
     */
    private fun preferredAsset(assets: JSONArray): JSONObject? {
        val zips = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".zip", ignoreCase = true) }
            .filter { it.optString("browser_download_url").isNotBlank() }
        return zips.firstOrNull {
            it.optString("name").contains("release", ignoreCase = true) &&
                !it.optString("name").contains("debug", ignoreCase = true)
        } ?: zips.firstOrNull {
            !it.optString("name").contains("debug", ignoreCase = true)
        } ?: zips.firstOrNull()
    }

    private fun isHmaOssModule(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() == 0L) return@runCatching false
        ZipFile(file).use { zip ->
            val prop = zip.getEntry("module.prop") ?: return@use false
            val id = zip.getInputStream(prop).bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("id=") }
                    ?.substringAfter('=')
                    ?.trim()
            }
            id == MODULE_ID && zip.getEntry("customize.sh") != null
        }
    }.getOrDefault(false)
}
