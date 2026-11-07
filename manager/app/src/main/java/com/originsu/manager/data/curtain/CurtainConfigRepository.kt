package com.originsu.manager.data.curtain

import android.util.Log
import com.originsu.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class CurtainLoadError {
    NO_ROOT,
    NOT_INITIALIZED,
    UNREADABLE,
    WRITE_FAILED,
}

sealed interface CurtainLoadResult {
    data class Ready(val snapshot: CurtainSnapshot) : CurtainLoadResult
    data class Failure(val error: CurtainLoadError) : CurtainLoadResult
}

data class CurtainSnapshot(
    val configDir: String,
    val config: CurtainConfig,
    val filterCount: Int? = null,
    val workMode: Int? = null,
)

/**
 * Reads and writes the HMA-OSS module's `config.json` directly.
 *
 * The upstream app talks to the module over a binder service because an
 * ordinary app cannot read `/data/misc`. Origin has root, so it edits the file
 * itself; the module watches it with a [java.io.FileObserver] and reloads on
 * write, which means changes apply without a reboot.
 *
 * Writes go through a sibling temporary file and then `cat > config.json`, so
 * the original inode — and with it the file's owner, mode and SELinux label —
 * is preserved for system_server.
 */
class CurtainConfigRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    companion object {
        private const val TAG = "CurtainConfig"

        private const val CONFIG_MARKER = "@@ORIGIN_CURTAIN_CONFIG@@"
        private const val STATUS_MARKER = "@@ORIGIN_CURTAIN_STATUS@@"
        private const val FILTER_MARKER = "@@ORIGIN_CURTAIN_FILTER@@"

        /**
         * Work mode reported by the module in `status.json`.
         * Matches HMA-OSS `Constants.MANAGER_WORK_MODE_*`.
         */
        const val WORK_MODE_UNKNOWN = 0
        const val WORK_MODE_OK = 1
        const val WORK_MODE_NO_HOOKS = 2
        const val WORK_MODE_LOADING = 3
        const val WORK_MODE_CRASHED = 4
    }

    private val mutex = Mutex()

    @Volatile
    private var cachedDir: String? = null

    @Volatile
    private var cachedDocument: JSONObject? = null

    suspend fun load(): CurtainLoadResult = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    suspend fun setGlobal(global: CurtainGlobalConfig): CurtainLoadResult =
        mutate { document -> CurtainConfigCodec.writeGlobal(document, global) }

    suspend fun setAppEnabled(packageName: String, enabled: Boolean): CurtainLoadResult =
        mutate { document ->
            if (enabled) {
                CurtainConfigCodec.writeApp(document, packageName, CurtainAppConfig())
            } else {
                CurtainConfigCodec.removeApp(document, packageName)
            }
        }

    suspend fun setApp(packageName: String, app: CurtainAppConfig): CurtainLoadResult =
        mutate { document -> CurtainConfigCodec.writeApp(document, packageName, app) }

    suspend fun saveTemplate(
        template: CurtainTemplate,
        previousName: String? = null,
    ): CurtainLoadResult = mutate { document ->
        CurtainConfigCodec.writeTemplate(document, template, previousName)
    }

    suspend fun deleteTemplate(name: String): CurtainLoadResult =
        mutate { document -> CurtainConfigCodec.removeTemplate(document, name) }

    private suspend fun mutate(
        transform: (JSONObject) -> Unit,
    ): CurtainLoadResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (cachedDocument == null) {
                when (val loaded = read()) {
                    is CurtainLoadResult.Failure -> return@withLock loaded
                    is CurtainLoadResult.Ready -> Unit
                }
            }
            val current = cachedDocument ?: return@withLock CurtainLoadResult.Failure(
                CurtainLoadError.NOT_INITIALIZED
            )
            val candidate = runCatching { JSONObject(current.toString()) }.getOrNull()
                ?: return@withLock CurtainLoadResult.Failure(CurtainLoadError.UNREADABLE)
            runCatching { transform(candidate) }
                .onFailure { Log.w(TAG, "Could not apply config change", it) }
                .getOrElse { return@withLock CurtainLoadResult.Failure(CurtainLoadError.UNREADABLE) }
            if (!write(candidate)) {
                return@withLock CurtainLoadResult.Failure(CurtainLoadError.WRITE_FAILED)
            }
            // Re-read so the UI reflects what actually landed on disk.
            read()
        }
    }

    private fun read(): CurtainLoadResult {
        if (!ksuCliRepository.rootAvailable()) {
            return CurtainLoadResult.Failure(CurtainLoadError.NO_ROOT)
        }
        val directory = resolveDirectory() ?: return CurtainLoadResult.Failure(
            CurtainLoadError.NOT_INITIALIZED
        )
        val configPath = "$directory/config.json"
        val script = buildString {
            append("echo ").append(quote(CONFIG_MARKER)).append('\n')
            append("cat ").append(quote(configPath)).append(" 2>/dev/null\n")
            append("echo\n")
            append("echo ").append(quote(STATUS_MARKER)).append('\n')
            append("cat ").append(quote("$directory/status.json")).append(" 2>/dev/null\n")
            append("echo\n")
            append("echo ").append(quote(FILTER_MARKER)).append('\n')
            append("cat ").append(quote("$directory/filter_count.json")).append(" 2>/dev/null\n")
        }
        val output = runCatching { ksuCliRepository.runCmd(ksuCliRepository.getRootShell(), script) }
            .getOrElse {
                Log.w(TAG, "Could not read the Origin Curtain config", it)
                return CurtainLoadResult.Failure(CurtainLoadError.UNREADABLE)
            }
        val sections = splitSections(output)
        val configText = sections[CONFIG_MARKER].orEmpty()
        if (configText.isBlank()) {
            return CurtainLoadResult.Failure(CurtainLoadError.UNREADABLE)
        }
        val document = runCatching { JSONObject(configText) }.getOrElse {
            Log.w(TAG, "Origin Curtain config is not valid JSON", it)
            return CurtainLoadResult.Failure(CurtainLoadError.UNREADABLE)
        }
        cachedDir = directory
        cachedDocument = document
        return CurtainLoadResult.Ready(
            CurtainSnapshot(
                configDir = directory,
                config = CurtainConfigCodec.parse(document),
                filterCount = parseFilterCount(sections[FILTER_MARKER].orEmpty()),
                workMode = parseWorkMode(sections[STATUS_MARKER].orEmpty()),
            )
        )
    }

    private fun write(document: JSONObject): Boolean {
        val directory = cachedDir ?: return false
        val configPath = "$directory/config.json"
        val temporaryPath = "$directory/config.json.originsu"
        // The module writes this file as system_server. Truncating it in place
        // keeps owner/mode/SELinux label intact; the temporary file only holds
        // the payload until it is copied over.
        val command = "printf '%s' ${quote(document.toString())} > ${quote(temporaryPath)} && " +
            "cat ${quote(temporaryPath)} > ${quote(configPath)} && " +
            "rm -f ${quote(temporaryPath)}"
        return runCatching {
            ksuCliRepository.getRootShell().newJob().add(command).exec().isSuccess
        }.getOrElse {
            Log.w(TAG, "Could not write the Origin Curtain config", it)
            false
        }
    }

    private fun resolveDirectory(): String? {
        cachedDir?.let { directory ->
            val alive = runCatching {
                ksuCliRepository.runCmd(
                    ksuCliRepository.getRootShell(),
                    "[ -f ${quote("$directory/config.json")} ] && echo alive",
                )
            }.getOrDefault("")
            if (alive.contains("alive")) return directory
            cachedDir = null
        }
        val script = buildString {
            append("for d in /data/misc/hide_my_applist* /data/system/hide_my_applist*; do\n")
            append("  if [ -f \"\$d/config.json\" ]; then echo \"\$d\"; break; fi\n")
            append("done")
        }
        return runCatching {
            ksuCliRepository.runCmd(ksuCliRepository.getRootShell(), script)
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("/") }
        }.getOrNull()
    }

    private fun splitSections(output: String): Map<String, String> {
        val sections = linkedMapOf<String, String>()
        val buffer = StringBuilder()
        var current: String? = null
        output.lineSequence().forEach { line ->
            val marker = line.trim()
            if (marker == CONFIG_MARKER || marker == STATUS_MARKER || marker == FILTER_MARKER) {
                current?.let { sections[it] = buffer.toString().trim() }
                buffer.clear()
                current = marker
            } else if (current != null) {
                buffer.appendLine(line)
            }
        }
        current?.let { sections[it] = buffer.toString().trim() }
        return sections
    }

    private fun parseWorkMode(statusText: String): Int? {
        if (statusText.isBlank()) return null
        return runCatching { JSONObject(statusText).optInt("workMode", WORK_MODE_UNKNOWN) }
            .getOrNull()
    }

    private fun parseFilterCount(filterText: String): Int? {
        if (filterText.isBlank()) return null
        val root = runCatching { JSONObject(filterText) }.getOrNull() ?: return null
        val counts = root.optJSONObject("filterCounts") ?: return 0
        var total = 0
        counts.keys().forEach { packageName ->
            val entry = counts.optJSONObject(packageName) ?: return@forEach
            total += entry.optInt("packageManagerCount", 0)
            total += entry.optInt("activityLaunchCount", 0)
            total += entry.optInt("installerCount", 0)
            total += entry.optInt("settingsCount", 0)
            total += entry.optInt("othersCount", 0)
        }
        return total
    }

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}
