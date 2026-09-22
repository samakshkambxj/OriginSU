package com.originsu.manager.data.module

import android.app.Application
import androidx.core.net.toUri
import com.originsu.manager.data.network.NetworkRequestRepository
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.BundleImportResult
import com.originsu.manager.domain.model.BundleScriptModule
import com.originsu.manager.domain.model.ModuleBackupEntry
import com.originsu.manager.domain.model.ModuleBackupInfo
import com.originsu.manager.domain.model.ModuleBackupOutcome
import com.originsu.manager.domain.model.ScriptParseResult
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Internal module backups plus shareable bundles.
 *
 * Installed modules live in the root-only `/data/adb/modules`, so every
 * operation shells out: per-module `tar.gz` archives under [BACKUP_DIR],
 * bundle zips (tars + manifest) staged in the app cache for SAF export,
 * and JSON download-scripts resolved to direct zip URLs.
 */
class ModuleBackupRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
    private val networkRequestRepository: NetworkRequestRepository,
) {
    suspend fun backupModules(entries: List<ModuleBackupEntry>): ModuleBackupOutcome =
        withContext(Dispatchers.IO) {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<String>()
            if (entries.isEmpty()) return@withContext ModuleBackupOutcome()
            execRoot("mkdir -p ${q(BACKUP_DIR)}")
            val timestamp = System.currentTimeMillis()
            for (entry in entries) {
                val dirId = entry.dirId
                if (!isSafeId(dirId)) {
                    failed.add(dirId)
                    continue
                }
                val fileName = "${sanitize(dirId)}__${entry.versionCode}__$timestamp.tar.gz"
                val ok = execRoot(
                    "$BUSYBOX tar -czf ${q("$BACKUP_DIR/$fileName")} " +
                        "-C ${q(MODULES_DIR)} ${q(dirId)}"
                ).isSuccess
                if (ok) succeeded.add(fileName) else failed.add(dirId)
            }
            ModuleBackupOutcome(succeeded, failed)
        }

    suspend fun listBackups(): List<ModuleBackupInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                execRoot("mkdir -p ${q(BACKUP_DIR)}")
                val out = execRoot("$BUSYBOX ls -1 ${q(BACKUP_DIR)}")
                if (!out.isSuccess) return@runCatching emptyList<ModuleBackupInfo>()
                out.out.mapNotNull { raw ->
                    val fileName = raw.trim().takeIf { it.endsWith(".tar.gz") } ?: return@mapNotNull null
                    if (!isSafeFileName(fileName)) return@mapNotNull null
                    val parsed = parseBackupName(fileName) ?: return@mapNotNull null
                    val stat = execRoot("$BUSYBOX stat -c '%s %Y' ${q("$BACKUP_DIR/$fileName")}")
                    var size = 0L
                    var mtime = 0L
                    if (stat.isSuccess) {
                        val parts = stat.out.firstOrNull()?.trim()?.split(" ")
                        size = parts?.getOrNull(0)?.toLongOrNull() ?: 0L
                        // Prefer the timestamp embedded in the file name; fall back to mtime.
                        mtime = parts?.getOrNull(1)?.let { (it.toLongOrNull() ?: 0L) * 1000L } ?: 0L
                    }
                    ModuleBackupInfo(
                        fileName = fileName,
                        moduleId = parsed.first,
                        versionCode = parsed.second,
                        createdAtMillis = parsed.third.takeIf { it > 0 } ?: mtime,
                        sizeBytes = size,
                    )
                }.sortedWith(
                    compareByDescending<ModuleBackupInfo> { it.createdAtMillis }
                        .thenByDescending { it.fileName }
                )
            }.getOrDefault(emptyList())
        }

    suspend fun restoreBackup(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isSafeFileName(fileName)) return@withContext false
            execRoot(
                "mkdir -p ${q(MODULES_DIR)} && " +
                    "$BUSYBOX tar -xzf ${q("$BACKUP_DIR/$fileName")} -C ${q(MODULES_DIR)}"
            ).isSuccess
        }

    suspend fun deleteBackup(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!isSafeFileName(fileName)) return@withContext false
            execRoot("$BUSYBOX rm -f ${q("$BACKUP_DIR/$fileName")}").isSuccess
        }

    /**
     * Zip validated backup tars plus a `bundle.json` manifest into the app
     * cache for SAF export. Returns the staged file.
     */
    suspend fun buildBundleZip(backupFileNames: List<String>, bundleName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val names = backupFileNames.filter(::isSafeFileName).distinct().take(MAX_BUNDLE_ENTRIES)
                require(names.isNotEmpty()) { "empty bundle" }
                val createdAt = System.currentTimeMillis()
                val dir = File(application.cacheDir, BUNDLE_CACHE_DIR).apply { mkdirs() }
                val outFile = File(dir, "bundle_${sanitize(bundleName.ifBlank { "modules" })}_$createdAt.zip")
                val manifest = JSONObject()
                    .put("version", 1)
                    .put("name", bundleName)
                    .put("createdAt", createdAt)
                    .put(
                        "modules",
                        JSONArray().apply {
                            names.forEach { fileName ->
                                val parsed = parseBackupName(fileName)
                                put(
                                    JSONObject()
                                        .put("id", parsed?.first.orEmpty())
                                        .put("versionCode", parsed?.second ?: 0)
                                        .put("backup", fileName)
                                )
                            }
                        }
                    )
                ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(BUNDLE_MANIFEST))
                    zip.write(manifest.toString(2).toByteArray())
                    zip.closeEntry()
                    for (fileName in names) {
                        val src = SuFile("$BACKUP_DIR/$fileName")
                        require(src.exists()) { "missing $fileName" }
                        zip.putNextEntry(ZipEntry(fileName))
                        SuFileInputStream.open(src).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                outFile
            }
        }

    /**
     * Validate a bundle zip from [uri] and copy its module tars into the
     * internal backup store (no auto-restore; restore via the backups UI).
     */
    suspend fun importBundleZip(uri: String): Result<BundleImportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(application.cacheDir, "bundle_import_${System.currentTimeMillis()}.zip")
                try {
                    application.contentResolver.openInputStream(uri.toUri())?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: throw IllegalArgumentException("unreadable bundle")
                    ZipFile(tmp).use { zip ->
                        val entries = java.util.Collections.list(zip.entries())
                        require(entries.size <= MAX_BUNDLE_ENTRIES + 1) { "too many entries" }
                        for (entry in entries) {
                            require(isSafeZipEntry(entry.name)) { "bad entry ${entry.name}" }
                            require((entry.size.takeIf { it >= 0 } ?: 0L) <= MAX_TAR_BYTES) { "entry too large" }
                        }
                        val manifestEntry = zip.getEntry(BUNDLE_MANIFEST)
                            ?: throw IllegalArgumentException("missing manifest")
                        val manifest = JSONObject(zip.getInputStream(manifestEntry).bufferedReader().readText())
                        require(manifest.optInt("version", 1) <= 1) { "unsupported bundle" }
                        val wanted = manifest.optJSONArray("modules")?.let { array ->
                            (0 until array.length()).mapNotNull {
                                array.optJSONObject(it)?.optString("backup")?.takeIf(::isSafeFileName)
                            }
                        }.orEmpty()
                        require(wanted.isNotEmpty()) { "empty bundle" }
                        execRoot("mkdir -p ${q(BACKUP_DIR)}")
                        val stageDir = File(application.cacheDir, "bundle_stage_${System.currentTimeMillis()}")
                        try {
                            stageDir.mkdirs()
                            var imported = 0
                            var skipped = 0
                            for (fileName in wanted.distinct()) {
                                val entry = zip.getEntry(fileName)
                                if (entry == null) {
                                    skipped++
                                    continue
                                }
                                val staged = File(stageDir, fileName)
                                zip.getInputStream(entry).use { input ->
                                    staged.outputStream().use { input.copyTo(it) }
                                }
                                val ok = execRoot(
                                    "$BUSYBOX cp -f ${q(staged.absolutePath)} ${q("$BACKUP_DIR/$fileName")} && " +
                                        "$BUSYBOX chmod 600 ${q("$BACKUP_DIR/$fileName")}"
                                ).isSuccess
                                if (ok) imported++ else skipped++
                            }
                            require(imported > 0) { "nothing imported" }
                            BundleImportResult(imported = imported, skipped = skipped)
                        } finally {
                            runCatching { stageDir.deleteRecursively() }
                        }
                    }
                } finally {
                    runCatching { tmp.delete() }
                }
            }
        }

    /** Write a staged cache file (bundle zip / script json) to a SAF [uri]. */
    suspend fun writeCacheFileToUri(src: File, uri: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                require(src.exists() && src.isFile) { "missing file" }
                application.contentResolver.openOutputStream(uri.toUri())?.use { out ->
                    FileInputStream(src).use { it.copyTo(out) }
                } ?: throw IllegalStateException("unwritable target")
                Unit
            }.isSuccess
        }

    suspend fun buildBundleScript(modules: List<BundleScriptModule>, name: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val usable = modules.filter { it.name.isNotBlank() && it.zipUrl.isNotBlank() }.take(MAX_BUNDLE_ENTRIES)
                require(usable.isNotEmpty()) { "no downloadable modules" }
                val dir = File(application.cacheDir, BUNDLE_CACHE_DIR).apply { mkdirs() }
                val outFile = File(dir, "script_${sanitize(name.ifBlank { "modules" })}_${System.currentTimeMillis()}.json")
                val json = JSONObject()
                    .put("version", 1)
                    .put("type", SCRIPT_TYPE)
                    .put("name", name)
                    .put(
                        "modules",
                        JSONArray().apply {
                            usable.forEach {
                                put(
                                    JSONObject()
                                        .put("id", it.id)
                                        .put("name", it.name)
                                        .put("zipUrl", it.zipUrl)
                                        .put("updateJson", it.updateJson)
                                )
                            }
                        }
                    )
                outFile.writeText(json.toString(2))
                outFile
            }
        }

    /**
     * Parse a download-script JSON file; entries carrying only `updateJson`
     * are resolved to a direct `zipUrl` on a best-effort basis.
     */
    suspend fun parseBundleScript(uri: String): Result<ScriptParseResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = application.contentResolver.openInputStream(uri.toUri())?.use { input ->
                    val capped = input.readCapped(MAX_SCRIPT_BYTES + 1)
                    require(capped.size <= MAX_SCRIPT_BYTES) { "script too large" }
                    capped.toString(Charsets.UTF_8)
                } ?: throw IllegalArgumentException("unreadable script")
                val json = JSONObject(text)
                require(json.optInt("version", 1) <= 1) { "unsupported script" }
                val array = json.optJSONArray("modules") ?: throw IllegalArgumentException("no modules")
                val modules = mutableListOf<BundleScriptModule>()
                var skipped = 0
                for (i in 0 until minOf(array.length(), MAX_BUNDLE_ENTRIES)) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    var zipUrl = obj.optString("zipUrl").trim()
                    val updateJson = obj.optString("updateJson").trim()
                    if (zipUrl.isBlank() && updateJson.isNotBlank()) {
                        zipUrl = runCatching {
                            JSONObject(networkRequestRepository.fetch(updateJson).getOrThrow())
                                .optString("zipUrl").trim()
                        }.getOrDefault("")
                    }
                    if (name.isBlank() || !isHttpUrl(zipUrl)) {
                        skipped++
                        continue
                    }
                    modules.add(
                        BundleScriptModule(
                            id = obj.optString("id").trim(),
                            name = name,
                            zipUrl = zipUrl,
                            updateJson = updateJson,
                        )
                    )
                }
                require(modules.isNotEmpty()) { "no downloadable modules" }
                ScriptParseResult(
                    name = json.optString("name").trim(),
                    modules = modules,
                    skippedNoUrl = skipped,
                )
            }
        }

    private fun execRoot(cmd: String) =
        ksuCliRepository.getRootShell().newJob().add(cmd).to(ArrayList(), null).exec()

    private fun q(value: String) = "'${value.replace("'", "'\"'\"'")}'"

    private fun sanitize(value: String) =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "module" }

    private fun isSafeId(value: String) =
        value.isNotBlank() && value.length <= 128 &&
            value.none { it == '/' || it == '\u0000' } && value != "." && value != ".."

    private fun isSafeFileName(value: String) =
        value.endsWith(".tar.gz") && value.length <= 160 &&
            value.none { it == '/' || it == '\\' || it == '\u0000' } &&
            value.all { it.isLetterOrDigit() || it in "._-()" }

    private fun isSafeZipEntry(name: String) =
        name.isNotBlank() && !name.contains("..") && !name.contains("\\") &&
            !name.startsWith("/") && !name.contains("/") &&
            (name == BUNDLE_MANIFEST || name.endsWith(".tar.gz"))

    private fun isHttpUrl(value: String) =
        value.startsWith("http://") || value.startsWith("https://")

    /** dirId, versionCode, timestamp from `<id>__<versionCode>__<millis>.tar.gz`. */
    private fun parseBackupName(fileName: String): Triple<String, Int, Long>? {
        val base = fileName.removeSuffix(".tar.gz")
        val parts = base.split("__")
        if (parts.size < 3) return null
        val timestamp = parts.last().toLongOrNull() ?: return null
        val versionCode = parts[parts.size - 2].toIntOrNull() ?: return null
        val id = parts.dropLast(2).joinToString("__").ifBlank { return null }
        return Triple(id, versionCode, timestamp)
    }

    companion object {
        const val BACKUP_DIR = "/data/adb/ksu/module_backup"
        private const val MODULES_DIR = "/data/adb/modules"
        private const val BUSYBOX = "/data/adb/ksu/bin/busybox"
        private const val BUNDLE_CACHE_DIR = "module_bundles"
        private const val BUNDLE_MANIFEST = "bundle.json"
        private const val SCRIPT_TYPE = "originsu-module-bundle"
        private const val MAX_BUNDLE_ENTRIES = 64
        private const val MAX_SCRIPT_BYTES = 262144
        private const val MAX_TAR_BYTES = 512L * 1024L * 1024L
        private const val TAG = "ModuleBackup"
    }
}

private fun java.io.InputStream.readCapped(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var remaining = max
    while (remaining > 0) {
        val read = read(buf, 0, minOf(buf.size, remaining))
        if (read < 0) break
        out.write(buf, 0, read)
        remaining -= read
    }
    return out.toByteArray()
}
