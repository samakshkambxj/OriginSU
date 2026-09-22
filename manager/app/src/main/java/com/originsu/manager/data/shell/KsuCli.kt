package com.originsu.manager.data.shell

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.core.net.toUri
import com.originsu.manager.BuildConfig
import com.originsu.manager.Natives
import com.originsu.manager.R
import com.originsu.manager.data.kernel.VeilManageRepository
import com.originsu.manager.domain.model.LkmSelection
import com.originsu.manager.domain.model.TempGrantRecord
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile

/**
 * @author weishu
 * @date 2023/1/1.
 */
class KsuCliRepository(context: Context) {
    companion object {
        const val TAG = "KsuCli"
        private const val BUSYBOX = "/data/adb/ksu/bin/busybox"
        private const val SU_NOTIFY_FLAG = "/data/adb/ksu/su_notify_enabled"
        private const val SU_NOTIFY_PACKAGE_FILE = "/data/adb/ksu/manager_package"

        // Cache of observed BasebandGuard versions, keyed by kernel release.
        // BBG only logs its version once at boot, so the dmesg line may have
        // rotated out by the time the manager queries it.
        private const val PREFS_BBG_VERSION = "bbg_version"
        private const val KEY_BBG_VERSION = "version_%s"

        // OriginZygisk deploy locations (built-in engine, not a module).
        const val ORIGIN_ZYGISK_DIR = "/data/adb/ksu/originzygisk"
        const val ORIGIN_ZYGISK_HOOK = "/data/adb/post-fs-data.d/originzygisk.sh"

        // Magic Mount backend ids accepted by `ksud module magic-mount backend`.
        val MAGIC_MOUNT_BACKENDS = listOf("auto", "overlay", "bind")

        // Zygisk *provider* module ids that conflict with the built-in
        // engine. Zygisk *modules* (e.g. LSPosed) are NOT blocked.
        val BLOCKED_ZYGISK_IMPL_IDS = setOf(
            "zygisksu",
            "rezygisk",
            "brezygisk",
            "shirokozygisk",
            "zygisk_next",
            "zygisknext",
            "neozygisk",
        )

        // Kernel image names searched inside an AnyKernel zip, in order.
        // .gz is decompressed on the fly; other compression is rejected.
        private val AKERNEL_CANDIDATES = listOf(
            "Image",
            "Image.gz",
            "zImage",
            "zImage-dtb",
            "kernel",
        )

        // Official manager signing certificates (size in bytes + SHA-256 of the
        // APK v2 signing cert). Compared numerically: `ksud debug get-sign`
        // prints sizes like `0x51c` (Rust {:#x}, no zero-padding), so a raw
        // string comparison against `0x051c` would never match.
        private val OFFICIAL_SIGNS = setOf(
            // OriginSU release signing certificate (primary for this fork)
            0x051c to "d91ed440459ff575f9bbdb0b294f8d679b1591a37538aafcf1d0073da5dbe968",
            // ReSukiSU upstream certificate (kept for compatibility)
            0x377 to "d3469712b6214462764a1d8d3e5cbe1d6819a0b629791b9f4101867821f1df64",
        )

        private val SIGN_PATTERN =
            Regex("""size:\s*(0[xX][0-9a-fA-F]+|\d+)\s*,\s*hash:\s*([0-9a-fA-F]{64})""")

        private val PACKAGE_PATTERN = Regex("""[A-Za-z0-9_.]+""")

        fun parseSign(raw: String): Pair<Int, String>? {
            val match = SIGN_PATTERN.find(raw.trim()) ?: return null
            val sizeToken = match.groupValues[1]
            val size = if (sizeToken.startsWith("0x", ignoreCase = true)) {
                sizeToken.substring(2).toIntOrNull(16)
            } else {
                sizeToken.toIntOrNull()
            } ?: return null
            return size to match.groupValues[2].lowercase()
        }
    }

    private val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
    private val appContext = context.applicationContext

    private fun getNativeLibraryPath(name: String): String {
        val library = File(nativeLibraryDir, System.mapLibraryName(name))
        require(library.isFile) {
            "${library.name} was not found in the application native library path"
        }
        return library.absolutePath
    }

    fun getKsuDaemonPath(): String = getNativeLibraryPath("ksud")

    /**
     * Shared root shells, one per mount-namespace flavor. libsu multiplexes
     * concurrent jobs over a single shell process, so sharing avoids a `su`
     * fork per call and stops leaked shells from accumulating. Dead shells
     * are detected via [Shell.isAlive] and transparently replaced.
     *
     * Callers needing an isolated, self-closed shell (e.g. the kernel-tuning
     * repository) should use [createRootShell] with `use {}` instead.
     */
    @Volatile
    private var cachedShell: Shell? = null

    @Volatile
    private var cachedGlobalShell: Shell? = null

    @Synchronized
    fun getRootShell(globalMnt: Boolean = false): Shell {
        if (!globalMnt) {
            cachedShell?.takeIf { it.isAliveQuietly() }?.let { return it }
            return createRootShell(false).also { cachedShell = it }
        }
        cachedGlobalShell?.takeIf { it.isAliveQuietly() }?.let { return it }
        return createRootShell(true).also { cachedGlobalShell = it }
    }

    private fun Shell.isAliveQuietly(): Boolean =
        runCatching { isAlive }.getOrDefault(false)

    fun generateMainShellBuilder(): Shell.Builder {
        val builder = Shell.Builder.create()
        try {
            builder.setCommands(getKsuDaemonPath(), "debug", "su")
            builder.build()
        } catch (e: Throwable) {
            Log.w(TAG, "ksu failed: ", e)
            try {
                builder.setCommands("su")
                builder.build()
            } catch (e: Throwable) {
                Log.e(TAG, "su failed: ", e)
                builder.setCommands("sh")
                builder.build()
            }
        }

        return builder
    }

    inline fun <T> withNewRootShell(
        globalMnt: Boolean = false,
        block: Shell.() -> T
    ): T {
        return createRootShell(globalMnt).use(block)
    }

    fun createRootShell(globalMnt: Boolean = false): Shell {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        val builder = Shell.Builder.create()
        return try {
            if (globalMnt) {
                builder.build(getKsuDaemonPath(), "debug", "su", "-g")
            } else {
                builder.build(getKsuDaemonPath(), "debug", "su")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ksu failed: ", e)
            try {
                if (globalMnt) {
                    builder.build("su", "-mm")
                } else {
                    builder.build("su")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "su failed: ", e)
                builder.build("sh")
            }
        }
    }

    fun execKsud(args: String, newShell: Boolean = false, globalMnt: Boolean = false): Boolean {
        return if (newShell) {
            withNewRootShell(globalMnt = globalMnt) {
                ShellUtils.fastCmdResult(this, "${getKsuDaemonPath()} $args")
            }
        } else {
            ShellUtils.fastCmdResult(getRootShell(globalMnt), "${getKsuDaemonPath()} $args")
        }
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }

    data class DynamicManagerCliConfig(
        val size: Int = 0,
        val hash: String = ""
    ) {
        fun isValid(): Boolean {
            return size > 0 && hash.length == 64
        }
    }

    suspend fun getDynamicManagerConfig(): DynamicManagerCliConfig? = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val result = shell.newJob()
            .add("${getKsuDaemonPath()} kernel dynamic-manager get --internal true")
            .to(ArrayList<String>(), null)
            .exec()
        if (!result.isSuccess) return@withContext null

        runCatching {
            val obj = JSONObject(result.out.joinToString("\n"))
            DynamicManagerCliConfig(
                size = obj.optInt("size", 0),
                hash = obj.optString("hash", "")
            )
        }.getOrNull()
    }

    fun setDynamicManager(size: Int, hash: String): Boolean {
        val result = execKsud("kernel dynamic-manager set $size $hash", true)
        Log.i(TAG, "set dynamic manager result: $result")
        return result
    }

    fun setDynamicManagerApk(apkPath: String): Boolean {
        val result = execKsud("kernel dynamic-manager set-apk ${shellQuote(apkPath)}", true)
        Log.i(TAG, "set dynamic manager apk result: $result")
        return result
    }

    fun clearDynamicManager(): Boolean {
        val result = execKsud("kernel dynamic-manager clear", true)
        Log.i(TAG, "clear dynamic manager result: $result")
        return result
    }

    suspend fun isOfficialSignature(packageResourcePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val shell = getRootShell()
            val out = shell.newJob()
                .add("${getKsuDaemonPath()} debug get-sign ${shellQuote(packageResourcePath)}")
                .to(ArrayList<String>(), null).exec().out
            out.firstOrNull()?.let { parseSign(it) } in OFFICIAL_SIGNS
        }

    suspend fun getFeatureStatus(feature: String): String = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val out = shell.newJob()
            .add("${getKsuDaemonPath()} feature check $feature").to(ArrayList<String>(), null)
            .exec().out
        out.firstOrNull()?.trim().orEmpty()
    }

    suspend fun getFeaturePersistValue(feature: String): Long? = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val out = shell.newJob()
            .add("${getKsuDaemonPath()} feature get --config $feature")
            .to(ArrayList<String>(), null)
            .exec().out
        val valueLine =
            out.firstOrNull { it.trim().startsWith("Value:") } ?: return@withContext null
        valueLine.substringAfter("Value:").trim().toLongOrNull()
    }

    /**
     * Grant root to [packageName]/[uid] for [timeoutSecs] seconds. The grant
     * is revoked automatically by ksud; it also expires on reboot via sweep.
     */
    suspend fun grantTempRoot(packageName: String, uid: Int, timeoutSecs: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!PACKAGE_PATTERN.matches(packageName) || uid < 0 || timeoutSecs <= 0) return@withContext false
            execKsud("grant-temp --package $packageName --uid $uid --timeout $timeoutSecs", true)
        }

    suspend fun listTempGrants(): List<TempGrantRecord> = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val out = shell.newJob()
            .add("${getKsuDaemonPath()} grant-temp-list").to(ArrayList<String>(), null)
            .exec().out
        runCatching {
            JSONArray(out.joinToString("\n")).let { array ->
                (0 until array.length()).map { index ->
                    array.getJSONObject(index).let { entry ->
                        TempGrantRecord(
                            uid = entry.getInt("uid"),
                            packageName = entry.getString("package"),
                            expiresAtEpoch = entry.getLong("expires_at"),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun revokeTempGrant(uid: Int): Boolean = withContext(Dispatchers.IO) {
        if (uid < 0) return@withContext false
        execKsud("grant-temp-revoke --uid $uid", true)
    }

    fun install() {
        val start = SystemClock.elapsedRealtime()
        val libadbroot = getNativeLibraryPath("adbroot")
        val magiskbootArg = runCatching { getNativeLibraryPath("magiskboot") }
            .getOrNull()?.let { " --magiskboot $it" }.orEmpty()
        val result = execKsud("install --libadbroot $libadbroot$magiskbootArg", true)
        Log.w(TAG, "install result: $result, cost: ${SystemClock.elapsedRealtime() - start}ms")
    }

    fun listModules(): String {
        val shell = getRootShell()

        val out = shell.newJob()
            .add("${getKsuDaemonPath()} module list").to(ArrayList(), null).exec().out
        return out.joinToString("\n").ifBlank { "[]" }
    }

    fun getModuleCount(): Int {
        val result = listModules()
        runCatching {
            val array = JSONArray(result)
            return array.length()
        }.getOrElse { return 0 }
    }

    fun getSuperuserCount(): Int {
        return Natives.getSuperuserCount()
    }

    // ---- KPM (KernelPatch Module) management ----

    fun loadKpmModule(path: String, args: String? = null): String {
        val shell = getRootShell()
        val cmd = buildString {
            append("${getKsuDaemonPath()} kpm load $path")
            if (!args.isNullOrBlank()) append(" $args")
        }
        return ShellUtils.fastCmd(shell, cmd)
    }

    fun unloadKpmModule(name: String): String {
        val shell = getRootShell()
        return ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} kpm unload $name")
    }

    fun getKpmModuleCount(): Int {
        val shell = getRootShell()
        val result = ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} kpm num")
        return result.trim().toIntOrNull() ?: 0
    }

    fun listKpmModules(): String {
        val shell = getRootShell()
        return try {
            runCmd(shell, "${getKsuDaemonPath()} kpm list").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list KPM modules", e)
            ""
        }
    }

    fun getKpmModuleInfo(name: String): String {
        val shell = getRootShell()
        return try {
            runCmd(shell, "${getKsuDaemonPath()} kpm info $name").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get KPM module info: $name", e)
            ""
        }
    }

    fun controlKpmModule(name: String, args: String? = null): Int {
        val shell = getRootShell()
        val cmd = "${getKsuDaemonPath()} kpm control $name \"${args.orEmpty()}\""
        return runCmd(shell, cmd).trim().toIntOrNull() ?: -1
    }

    fun getKpmVersion(): String {
        val shell = getRootShell()
        return ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} kpm version").trim()
    }

    // ---- OriginGuard (module audit) ----

    /**
     * Statically audits a staged module ZIP without installing it.
     * @return raw JSON report, or "" when the audit could not run.
     */
    fun auditModuleZip(path: String): String {
        val shell = getRootShell()
        return try {
            runCmd(shell, "${getKsuDaemonPath()} module audit --json $path").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to audit module zip: $path", e)
            ""
        }
    }

    fun isKpmEnabled(): Boolean {
        return runCatching { Natives.isKPMEnabled() }.getOrDefault(false)
    }

    // ---- BasebandGuard (BBG) kernel LSM detection ----
    //
    // BBG bakes its version (upstream short commit SHA) into the kernel at
    // build time and prints `baseband_guard version: <sha>` to the kernel
    // log on init. Presence is detectable via the active LSM list or the
    // kernel config; the version itself is parsed from dmesg.

    fun isBbgEnabled(): Boolean {
        val shell = getRootShell()
        val result = ShellUtils.fastCmd(
            shell,
            "grep -q baseband_guard /sys/kernel/security/lsm 2>/dev/null" +
                " || gzip -dc /proc/config.gz 2>/dev/null | grep -q '^CONFIG_BBG=y'" +
                " || dmesg 2>/dev/null | grep -q 'baseband_guard';" +
                " echo $?"
        ).trim()
        return result == "0"
    }

    fun getBbgVersion(): String {
        val release = runCatching { Os.uname().release.orEmpty() }.getOrDefault("")
        val shell = getRootShell()
        val line = ShellUtils.fastCmd(
            shell,
            "dmesg 2>/dev/null | grep -m1 'baseband_guard version:'"
        )
        val live = line.substringAfter("baseband_guard version:", "").trim()
        if (live.isNotEmpty()) {
            if (release.isNotEmpty()) {
                bbgVersionPrefs().edit().putString(KEY_BBG_VERSION.format(release), live)
                    .apply()
            }
            return live
        }
        if (release.isNotEmpty()) {
            return bbgVersionPrefs().getString(KEY_BBG_VERSION.format(release), "").orEmpty()
        }
        return ""
    }

    private fun bbgVersionPrefs() =
        appContext.getSharedPreferences(PREFS_BBG_VERSION, Context.MODE_PRIVATE)

    // ---- ZeroMount detection ----
    //
    // The ZeroMount VFS kernel driver exposes /dev/zeromount, while the
    // userspace module version lives in its module.prop (module id
    // `meta-zeromount`).

    fun isZeromountDriverPresent(): Boolean {
        val shell = getRootShell()
        val result = ShellUtils.fastCmd(
            shell,
            "[ -e /dev/zeromount ] && echo true || echo false"
        ).trim()
        return result == "true"
    }

    fun getZeromountVersion(): String {
        val moduleIds = listOf("meta-zeromount", "zeromount")
        for (moduleId in moduleIds) {
            if (SuFile.open("/data/adb/modules/$moduleId/disable").isFile ||
                SuFile.open("/data/adb/modules/$moduleId/remove").isFile
            ) continue
            val propFile = SuFile.open("/data/adb/modules/$moduleId/module.prop")
            if (!propFile.isFile) continue
            val prop = Properties()
            runCatching { prop.load(propFile.newInputStream()) }.getOrNull() ?: continue
            val version = prop.getProperty("version").orEmpty().trim()
            if (version.isNotEmpty()) {
                Log.i(TAG, "ZeroMount implement: $version")
                return version
            }
        }
        return ""
    }

    fun toggleModule(id: String, enable: Boolean): Boolean {
        val cmd = if (enable) {
            "module enable $id"
        } else {
            "module disable $id"
        }
        val result = execKsud(cmd, true)
        Log.i(TAG, "$cmd result: $result")
        return result
    }

    fun uninstallModule(id: String): Boolean {
        val cmd = "module uninstall $id"
        val result = execKsud(cmd, true)
        Log.i(TAG, "uninstall module $id result: $result")
        return result
    }

    fun undoUninstallModule(id: String): Boolean {
        val cmd = "module undo-uninstall $id"
        val result = execKsud(cmd, true)
        Log.i(TAG, "undo uninstall module $id result: $result")
        return result
    }

    private fun flashWithIO(
        cmd: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit
    ): Shell.Result {

        val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStdout(s ?: "")
            }
        }

        val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStderr(s ?: "")
            }
        }

        return withNewRootShell {
            newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
        }
    }

    fun flashModule(
        context: Context,
        uri: Uri,
        onFinish: (Boolean, Int) -> Unit,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        auditConfirmed: Boolean = false,
        noAudit: Boolean = false,
    ): Boolean {
        val resolver = context.contentResolver
        with(resolver.openInputStream(uri)) {
            val file = File(context.cacheDir, "module.zip")
            file.outputStream().use { output ->
                this?.copyTo(output)
            }
            val cmd = buildString {
                append("module install ${file.absolutePath}")
                if (noAudit) append(" --no-audit")
                else if (auditConfirmed) append(" --audit-confirmed")
            }
            val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
            Log.i("KernelSU", "install module $uri result: $result")

            file.delete()

            onFinish(result.isSuccess, result.code)
            return result.isSuccess
        }
    }

    fun flashAnyKernel(
        zipFile: File,
        slot: String?,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit
    ): Boolean {
        val command = buildString {
            append("${getKsuDaemonPath()} anykernel3 ${shellQuote(zipFile.absolutePath)}")
            slot?.let {
                append(" --slot ${shellQuote(it)}")
            }
        }
        val result = flashWithIO(command, onStdout, onStderr)
        Log.i(TAG, "AnyKernel3 flash result: ${result.isSuccess}, code: ${result.code}")
        return result.isSuccess
    }

    /**
     * Offline patch: inject the kernel from an AnyKernel3 zip into a stock
     * boot.img using ksud's own boot parser. Pure file surgery, no root and
     * no external binaries required. Output goes to Downloads for manual
     * flashing.
     */
    fun patchBootWithAnyKernel(
        context: Context,
        bootUri: Uri,
        zipUri: Uri,
        onFinish: (Boolean, Int) -> Unit,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        kmi: String? = null
    ): Boolean {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val workDir = File(context.cacheDir, "akpatch_$timestamp")
        if (!workDir.mkdirs()) {
            onStderr(context.getString(R.string.patch_workdir_failed))
            onFinish(false, 1)
            return false
        }
        try {
            val resolver = context.contentResolver
            val bootImg = File(workDir, "boot.img")
            resolver.openInputStream(bootUri).use { input ->
                bootImg.outputStream().use { out -> input?.copyTo(out) }
            }
            val zipFile = File(workDir, "ak.zip")
            resolver.openInputStream(zipUri).use { input ->
                zipFile.outputStream().use { out -> input?.copyTo(out) }
            }

            val newKernel = File(workDir, "kernel-new")
            val found = runCatching {
                ZipFile(zipFile).use { zip ->
                    val entry = AKERNEL_CANDIDATES.firstNotNullOfOrNull { name ->
                        zip.getEntry(name)?.let { name to it }
                    } ?: return@runCatching false
                    zip.getInputStream(entry.second).use { input ->
                        val stream = if (entry.first.endsWith(".gz")) {
                            java.util.zip.GZIPInputStream(input)
                        } else {
                            input
                        }
                        stream.use { it.copyTo(newKernel.outputStream()) }
                    }
                    true
                }
            }.getOrDefault(false)
            if (!found || !newKernel.isFile) {
                onStderr(context.getString(R.string.invalid_anykernel_kernel))
                onFinish(false, 1)
                return false
            }

            val magiskbootLib = runCatching {
                File(nativeLibraryDir, System.mapLibraryName("magiskboot"))
                    .takeIf { it.isFile }
            }.getOrNull()
            if (magiskbootLib == null) {
                onStderr(context.getString(R.string.magiskboot_unavailable))
                onFinish(false, 1)
                return false
            }

            val outDir = File(workDir, "out")
            if (!outDir.mkdirs()) {
                onStderr(context.getString(R.string.patch_workdir_failed))
                onFinish(false, 1)
                return false
            }

            val shell = runCatching { Shell.Builder.create().build("sh") }.getOrNull()
            if (shell == null) {
                onStderr(context.getString(R.string.patch_shell_failed))
                onFinish(false, 1)
                return false
            }
            val patchResult = shell.use {
                val stdoutCallback = object : CallbackList<String?>() {
                    override fun onAddElement(s: String?) {
                        onStdout(s ?: "")
                    }
                }
                val stderrCallback = object : CallbackList<String?>() {
                    override fun onAddElement(s: String?) {
                        onStderr(s ?: "")
                    }
                }
                // App-private binaries are not directly executable on most
                // ROMs, so patch natively with ksud instead of magiskboot.
                val kmiArg =
                    kmi?.takeIf { it.isNotBlank() }?.let { " --kmi ${shellQuote(it.trim())}" }.orEmpty()
                it.newJob().add(
                    "${getKsuDaemonPath()} boot-patch" +
                        " -b ${shellQuote(bootImg.absolutePath)}" +
                        " -k ${shellQuote(newKernel.absolutePath)}" +
                        kmiArg +
                        " --no-install -o ${shellQuote(outDir.absolutePath)}"
                ).to(stdoutCallback, stderrCallback).exec()
            }
            if (!patchResult.isSuccess) {
                onFinish(false, patchResult.code)
                return false
            }

            val patched = outDir.listFiles { file -> file.isFile && file.extension == "img" }
                ?.maxByOrNull { it.lastModified() }
            if (patched == null) {
                onStderr(context.getString(R.string.patch_repack_failed))
                onFinish(false, 1)
                return false
            }
            saveToDownloads(context, patched, "originsu-patched-boot-$timestamp.img")
            Log.i(TAG, "boot patched with AnyKernel kernel, size: ${patched.length()}")
            onFinish(true, 0)
            return true
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun saveToDownloads(context: Context, file: File, displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(
                    android.provider.MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/OriginSU"
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw IllegalStateException("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "OriginSU"
            )
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Downloads dir unavailable")
            }
            file.copyTo(File(dir, displayName), overwrite = true)
        }
    }

    /**
     * Legacy AnyKernel3 flash: runs the zip's own update-binary via busybox
     * ash, like magiskboot-era flashing. Handles zips that [flashAnyKernel]
     * (ksud's parser, mkbootfs-marker only) cannot process.
     */
    fun flashAnyKernelZip(
        context: Context,
        uri: Uri,
        onFinish: (Boolean, Int) -> Unit,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit
    ): Boolean {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val tmpFile = File(context.cacheDir, "anykernel_$timestamp.zip")
        context.contentResolver.openInputStream(uri).use { input ->
            tmpFile.outputStream().use { out ->
                input?.copyTo(out)
            }
        }

        val destZip = tmpFile.absolutePath
        val destDir = File(context.cacheDir, "anykernel3_$timestamp").absolutePath
        val destZipName = File(destZip).name

        val hasInstaller = runCatching {
            ZipFile(tmpFile).use { zip ->
                zip.getEntry("META-INF/com/google/android/update-binary") != null
            }
        }.getOrDefault(false)

        if (!hasInstaller) {
            tmpFile.delete()
            val errMsg = context.getString(R.string.invalid_anykernel_zip)
            onStderr(errMsg)
            onFinish(false, 1)
            return false
        }

        val cmd = """
            mkdir -p '$destDir' && \
            $BUSYBOX unzip -p -o '$destZip' "META-INF/com/google/android/update-binary" > '$destDir/update-binary' 2>/dev/null && \
            cp '$destZip' '$destDir/$destZipName' 2>/dev/null || true && \
            $BUSYBOX chmod 755 '$destDir/update-binary' && \
            $BUSYBOX chown root:root '$destDir/update-binary' && \
            (cd '$destDir' && \
                if [ -f './update-binary' ]; then \
                    AKHOME='$destDir/tmp' $BUSYBOX ash '$destDir/update-binary' 3 1 '$destDir/$destZipName'; \
                else \
                    echo 'No installer script found' >&2; exit 1; \
                fi)
        """.trimIndent().replace(Regex("\\s+\\\\\\s*"), " ")

        return try {
            val result = flashWithIO(cmd, onStdout, onStderr)
            Log.i(TAG, "AnyKernel3 legacy flash result: $result")
            onFinish(result.isSuccess, result.code)
            result.isSuccess
        } finally {
            runCatching {
                withNewRootShell(true) {
                    newJob().add("rm -rf '$destDir' '$destZip'").exec()
                }
            }
        }
    }

    fun runModuleAction(
        moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
    ): Boolean {
        val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStdout(s ?: "")
            }
        }

        val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStderr(s ?: "")
            }
        }

        val result = withNewRootShell(true) {
            newJob().add("${getKsuDaemonPath()} module action $moduleId")
                .to(stdoutCallback, stderrCallback).exec()
        }

        Log.i("KernelSU", "Module runAction result: $result")

        return result.isSuccess
    }

    fun restoreBoot(
        onFinish: (Boolean, Int) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
    ): Boolean {
        val result = flashWithIO(
            "${getKsuDaemonPath()} boot-restore -f",
            onStdout,
            onStderr
        )
        onFinish(result.isSuccess, result.code)
        return result.isSuccess
    }

    fun uninstallPermanently(
        onFinish: (Boolean, Int) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
    ): Boolean {
        val result =
            flashWithIO(
                "${getKsuDaemonPath()} uninstall --package-name ${BuildConfig.APPLICATION_ID}",
                onStdout,
                onStderr
            )
        onFinish(result.isSuccess, result.code)
        return result.isSuccess
    }

    fun installBoot(
        context: Context,
        bootUri: Uri?,
        lkm: LkmSelection,
        ota: Boolean,
        partition: String?,
        onFinish: (Boolean, Int) -> Unit,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): Boolean {
        val resolver = context.contentResolver

        val bootFile = bootUri?.let { uri ->
            with(resolver.openInputStream(uri)) {
                val bootFile = File(context.cacheDir, "boot.img")
                bootFile.outputStream().use { output ->
                    this?.copyTo(output)
                }

                bootFile
            }
        }

        var cmd = "boot-patch"

        cmd += if (bootFile == null) {
            // no boot.img, use -f to flash
            " -f"
        } else {
            " -b ${bootFile.absolutePath}"
        }

        if (ota) {
            cmd += " -u"
        }

        var lkmFile: File? = null
        when (lkm) {
            is LkmSelection.LkmUri -> {
                lkmFile = with(resolver.openInputStream(lkm.uri.toUri())) {
                    val file = File(context.cacheDir, "kernelsu-tmp-lkm.ko")
                    file.outputStream().use { output ->
                        this?.copyTo(output)
                    }

                    file
                }
                cmd += " -m ${lkmFile.absolutePath}"
            }

            is LkmSelection.KmiString -> {
                cmd += " --kmi ${lkm.value}"
                if (lkm.hook == "tamper") {
                    cmd += " --hook tamper"
                }
            }

            LkmSelection.KmiNone -> {
                // do nothing
            }
        }

        // output dir
        if (bootFile != null) {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            cmd += " -o $downloadsDir"
        }

        partition?.let { part ->
            cmd += " --partition $part"
        }

        val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
        Log.i("KernelSU", "install boot result: ${result.isSuccess}")

        bootFile?.delete()
        lkmFile?.delete()

        // if boot uri is empty, it is direct install, when success, we should show reboot button
        onFinish(bootUri == null && result.isSuccess, result.code)

        if (bootUri == null && result.isSuccess) {
            install()
        }

        return result.isSuccess
    }

    fun reboot(reason: String = "") {
        if (reason == "soft_reboot") {
            execKsud("soft-reboot", newShell = true, globalMnt = true)
            return
        }
        val shell = getRootShell()
        if (reason == "recovery") {
            // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
            ShellUtils.fastCmd(shell, "/system/bin/input keyevent 26")
        }
        ShellUtils.fastCmd(
            shell,
            "/system/bin/svc power reboot $reason || /system/bin/reboot $reason"
        )
    }

    fun rootAvailable(): Boolean {
        val shell = getRootShell()
        return shell.isRoot
    }


    suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val cmd = "boot-info current-kmi"
        ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd")
    }

    suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val cmd = "boot-info supported-kmis"
        val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
        out.filter { it.isNotBlank() }.map { it.trim() }
    }

    suspend fun isAbDevice(): Boolean = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val cmd = "boot-info is-ab-device"
        ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim().toBoolean()
    }

    suspend fun getDefaultPartition(): String = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        if (shell.isRoot) {
            val cmd = "boot-info default-partition"
            ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
        } else {
            if (!Os.uname().release.contains("android12-")) "init_boot" else "boot"
        }
    }

    suspend fun getSlotSuffix(ota: Boolean): String = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val cmd = if (ota) {
            "boot-info slot-suffix --ota"
        } else {
            "boot-info slot-suffix"
        }
        ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
    }

    suspend fun getAvailablePartitions(): List<String> = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val cmd = "boot-info available-partitions"
        val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
        out.filter { it.isNotBlank() }.map { it.trim() }
    }

    fun hasMagisk(): Boolean {
        val shell = getRootShell(true)
        val result = shell.newJob().add("which magisk").exec()
        Log.i(TAG, "has magisk: ${result.isSuccess}")
        return result.isSuccess
    }

    fun isSepolicyValid(rules: String?): Boolean {
        if (rules == null) {
            return true
        }
        val shell = getRootShell()
        val result =
            shell.newJob().add("${getKsuDaemonPath()} sepolicy check '$rules'")
                .to(ArrayList(), null)
                .exec()
        return result.isSuccess
    }

    fun getSepolicy(pkg: String): String {
        val shell = getRootShell()
        val result =
            shell.newJob().add("${getKsuDaemonPath()} profile get-sepolicy $pkg")
                .to(ArrayList(), null)
                .exec()
        Log.i(TAG, "code: ${result.code}, out: ${result.out}, err: ${result.err}")
        return result.out.joinToString("\n")
    }

    fun setSepolicy(pkg: String, rules: String): Boolean {
        val shell = getRootShell()
        val result = shell.newJob().add("${getKsuDaemonPath()} profile set-sepolicy $pkg '$rules'")
            .to(ArrayList(), null).exec()
        Log.i(TAG, "set sepolicy result: ${result.code}")
        return result.isSuccess
    }

    fun listAppProfileTemplates(): List<String> {
        val shell = getRootShell()
        return shell.newJob().add("${getKsuDaemonPath()} profile list-templates")
            .to(ArrayList(), null)
            .exec().out
    }

    fun getAppProfileTemplate(id: String): String {
        val shell = getRootShell()
        return shell.newJob().add("${getKsuDaemonPath()} profile get-template '${id}'")
            .to(ArrayList(), null).exec().out.joinToString("\n")
    }

    fun setAppProfileTemplate(id: String, template: String): Boolean {
        val shell = getRootShell()
        val escapedTemplate = template.replace("\"", "\\\"")
        val cmd = """${getKsuDaemonPath()} profile set-template "$id" "$escapedTemplate""""
        return shell.newJob().add(cmd)
            .to(ArrayList(), null).exec().isSuccess
    }

    fun deleteAppProfileTemplate(id: String): Boolean {
        val shell = getRootShell()
        return shell.newJob().add("${getKsuDaemonPath()} profile delete-template '${id}'")
            .to(ArrayList(), null).exec().isSuccess
    }

    fun runCmd(shell: Shell, cmd: String): String {
        return shell.newJob()
            .add(cmd)
            .to(mutableListOf<String>(), null)
            .exec().out
            .joinToString("\n")
    }

    fun forceStopApp(packageName: String) {
        val shell = getRootShell()
        val result = shell.newJob().add("am force-stop $packageName").exec()
        Log.i(TAG, "force stop $packageName result: $result")
    }

    // ---- Origin Veil management ----

    /** Whether [uid] is currently cloaked (blocking check, call off the main thread). */
    fun isVeilCloaked(uid: Int): Boolean = runCatching {
        Natives.getVeilCloakedUids()?.contains(uid) == true
    }.getOrDefault(false)

    /** Cloak [uid] via direct ioctl (for the root-request receiver). */
    fun setVeilCloaked(uid: Int): Boolean =
        runCatching { Natives.setVeilCloaked(uid, true) }.getOrDefault(false)

    /** Snapshot kernel Veil state to veil.json so it survives reboot. */
    fun persistVeil() {
        runCatching { execKsud("veil save", true) }
    }

    /**
     * Enable/disable root-request notifications. Driven by Veil via the native
     * su-notifyd daemon. Publishes this manager's package + the enable flag,
     * then starts the daemon; on disable clears the flag (the daemon exits).
     */
    suspend fun setSuNotify(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        if (enable) {
            ShellUtils.fastCmdResult(
                shell,
                "echo ${appContext.packageName} > $SU_NOTIFY_PACKAGE_FILE; " +
                    "touch $SU_NOTIFY_FLAG; " +
                    "pkill -f 'ksud su-notifyd' 2>/dev/null; sleep 1; " +
                    "/data/adb/ksu/bin/ksud debug su-notifyd"
            )
        } else {
            ShellUtils.fastCmdResult(
                shell,
                "rm -f $SU_NOTIFY_FLAG; pkill -f 'ksud su-notifyd' 2>/dev/null; true"
            )
        }
    }

    /** Disable (freeze) or re-enable an app. */
    fun setAppEnabled(packageName: String, enabled: Boolean): Boolean {
        return ShellUtils.fastCmdResult(
            getRootShell(),
            if (enabled) "pm enable $packageName" else "pm disable-user --user 0 $packageName"
        )
    }

    /** Current appop modes for a package, op-name -> mode (allow/ignore/deny/…). */
    suspend fun getAppOpsModes(packageName: String): Map<String, String> = withContext(Dispatchers.IO) {
        val out = getRootShell().newJob()
            .add("cmd appops get $packageName").to(ArrayList<String>(), null).exec().out
        val map = mutableMapOf<String, String>()
        val re = Regex("([A-Z_]+):\\s*(allow|ignore|deny|default|foreground)")
        for (line in out) re.find(line)?.let { map[it.groupValues[1]] = it.groupValues[2] }
        map
    }

    /**
     * Block or allow a permission. `pm revoke` only works on runtime (dangerous)
     * perms, so also drive the appop (block -> ignore, allow -> allow) which covers
     * appop-backed perms (overlay, usage-stats, …).
     */
    fun setPermissionMode(packageName: String, perm: String, block: Boolean) {
        val shell = getRootShell()
        val op = VeilManageRepository.opForPermission(perm)
        if (block) {
            shell.newJob().add("pm revoke $packageName $perm").exec()
            if (op != null) shell.newJob().add("cmd appops set $packageName $op ignore").exec()
        } else {
            shell.newJob().add("pm grant $packageName $perm").exec()
            if (op != null) shell.newJob().add("cmd appops set $packageName $op allow").exec()
        }
    }

    /**
     * Spy log source. Apps log little under their own uid, and the interesting
     * activity lands elsewhere: Play Integrity in GMS, store calls in vending, and
     * hardware key attestation in the keystore/KeyMint HAL (system). So merge the
     * target app + GMS + Play Store (by uid) with the keystore/KeyMint HAL (by tag),
     * time-sorted into one stream.
     */
    suspend fun dumpAppLog(uid: Int, lines: Int = 300): List<String> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        fun uidOf(pkg: String) = runCatching { pm.getPackageUid(pkg, 0) }.getOrNull()
        // NOTE: this logcat rejects comma uid-lists, so run one --uid per uid. And -t
        // is applied BEFORE the tag filter, so the keystore tag stream uses no -t
        // (it's sparse anyway) to guarantee attestation logs are never dropped.
        val perUid = listOfNotNull(uid, uidOf("com.google.android.gms"), uidOf("com.android.vending"))
            .distinct()
            .joinToString("; ") { "logcat -d --uid=$it -v threadtime -t $lines" }
        val ksTags = "keystore2 KeyMintDevice KeyMasterHalDevice KeymasterUtils " +
            "Keymaster credstore DroidGuard"
        val cmd = "{ $perUid; logcat -d -s $ksTags -v threadtime; } | sort -k1,2 -s | uniq"
        getRootShell().newJob().add(cmd).to(ArrayList<String>(), null).exec().out
    }

    fun launchApp(packageName: String) {

        val shell = getRootShell()
        val result =
            shell.newJob()
                .add("cmd package resolve-activity --brief $packageName | tail -n 1 | xargs cmd activity start-activity -n")
                .exec()
        Log.i(TAG, "launch $packageName result: $result")
    }

    fun restartApp(packageName: String) {
        forceStopApp(packageName)
        launchApp(packageName)
    }

    fun getMetaModuleImplement(): String {
        try {
            val metaModuleProp = SuFile.open("/data/adb/metamodule/module.prop")
            if (!metaModuleProp.isFile) {
                Log.i(TAG, "Meta module implement: None")
                return "None"
            }

            val prop = Properties()
            prop.load(metaModuleProp.newInputStream())

            val name = prop.getProperty("name")
            Log.i(TAG, "Meta module implement: $name")
            return name
        } catch (_: Throwable) {
            Log.i(TAG, "Meta module implement: None")
            return "None"
        }
    }

    fun getZygiskImplement(): String {
        if (isOriginZygiskDeployed()) {
            Log.i(TAG, "Zygisk implement: OriginZygisk")
            return "OriginZygisk"
        }
        val zygiskModuleIds = listOf(
            "zygisksu",
            "rezygisk"
        )

        for (moduleId in zygiskModuleIds) {
            if (SuFile.open("/data/adb/modules/$moduleId/disable").isFile || SuFile.open("/data/adb/modules/$moduleId/remove").isFile) continue

            val propFile = SuFile.open("/data/adb/modules/$moduleId/module.prop")
            if (!propFile.isFile) continue

            val prop = Properties()
            prop.load(propFile.newInputStream())

            val name = prop.getProperty("name")
            Log.i(TAG, "Zygisk implement: $name")
            return name
        }

        Log.i(TAG, "Zygisk implement: None")
        return "None"
    }

    private fun isOriginZygiskDeployed(): Boolean {
        return runCatching {
            SuFile.open("$ORIGIN_ZYGISK_DIR/enable").isFile &&
                SuFile.open(ORIGIN_ZYGISK_HOOK).isFile
        }.getOrDefault(false)
    }

    suspend fun isOriginZygiskEnabled(): Boolean = withContext(Dispatchers.IO) {
        if (!rootAvailable()) {
            return@withContext isOriginZygiskDeployed()
        }
        val shell = getRootShell()
        ShellUtils.fastCmdResult(
            shell,
            "[ -f $ORIGIN_ZYGISK_DIR/enable ] && [ -f $ORIGIN_ZYGISK_HOOK ]"
        )
    }

    // ---- Magic Mount (userspace Magisk-style module mounting) ----
    // ksud owns the state (flag file); the manager only shells out.
    suspend fun isMagicMountEnabled(): Boolean = withContext(Dispatchers.IO) {
        if (!rootAvailable()) {
            return@withContext false
        }
        val shell = getRootShell()
        runCatching {
            runCmd(shell, "${getKsuDaemonPath()} module magic-mount status").trim()
        }.getOrDefault("disabled") == "enabled"
    }

    suspend fun setMagicMountEnabled(enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!rootAvailable()) {
                return@withContext false
            }
            val op = if (enabled) "enable" else "disable"
            runCatching { execKsud("module magic-mount $op") }.getOrDefault(false)
        }

    suspend fun getMagicMountBackend(): String = withContext(Dispatchers.IO) {
        if (!rootAvailable()) {
            return@withContext "auto"
        }
        val shell = getRootShell()
        runCatching {
            runCmd(shell, "${getKsuDaemonPath()} module magic-mount backend").trim()
        }.getOrDefault("auto").takeIf { it in MAGIC_MOUNT_BACKENDS } ?: "auto"
    }

    suspend fun setMagicMountBackend(backend: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!rootAvailable() || backend !in MAGIC_MOUNT_BACKENDS) {
                return@withContext false
            }
            runCatching { execKsud("module magic-mount backend $backend") }
                .getOrDefault(false)
        }

    suspend fun isOriginZygiskRunning(): Boolean = withContext(Dispatchers.IO) {
        if (!rootAvailable()) {
            return@withContext false
        }
        val shell = getRootShell()
        ShellUtils.fastCmdResult(shell, "pgrep -f zygisk-ptrace >/dev/null 2>&1")
    }

    private fun copyAssetToCacheDir(name: String): File? {
        return runCatching {
            val out = File(appContext.cacheDir, name.substringAfterLast('/'))
            appContext.assets.open(name).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        }.getOrNull()
    }

    suspend fun setOriginZygiskEnabled(enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val shell = getRootShell()
            if (!enabled) {
                return@withContext ShellUtils.fastCmdResult(
                    shell,
                    "rm -f $ORIGIN_ZYGISK_DIR/enable $ORIGIN_ZYGISK_HOOK; " +
                        "pkill -f zygisk-ptrace 2>/dev/null; " +
                        "pkill -f zygiskd 2>/dev/null; true"
                )
            }
            val payload = copyAssetToCacheDir("originzygisk/payload.zip")
            val setup = copyAssetToCacheDir("originzygisk/setup.sh")
            val launch = copyAssetToCacheDir("originzygisk/launch.sh")
            if (payload == null || setup == null || launch == null) {
                Log.w(TAG, "OriginZygisk assets missing in APK")
                payload?.delete()
                setup?.delete()
                launch?.delete()
                return@withContext false
            }
            val script = """
                set -e
                rm -rf $ORIGIN_ZYGISK_DIR
                mkdir -p $ORIGIN_ZYGISK_DIR/payload /data/adb/post-fs-data.d
                cp '${payload.absolutePath}' $ORIGIN_ZYGISK_DIR/payload.zip
                cp '${setup.absolutePath}' $ORIGIN_ZYGISK_DIR/setup.sh
                cp '${launch.absolutePath}' $ORIGIN_ZYGISK_DIR/launch.sh
                cd $ORIGIN_ZYGISK_DIR/payload && unzip -o $ORIGIN_ZYGISK_DIR/payload.zip >/dev/null
                cd $ORIGIN_ZYGISK_DIR && sh setup.sh $ORIGIN_ZYGISK_DIR
                ${getKsuDaemonPath()} sepolicy apply $ORIGIN_ZYGISK_DIR/payload/sepolicy.rule || true
                touch $ORIGIN_ZYGISK_DIR/enable
                cp $ORIGIN_ZYGISK_DIR/launch.sh $ORIGIN_ZYGISK_HOOK
                chmod 0755 $ORIGIN_ZYGISK_HOOK
            """.trimIndent()
            val ok = shell.newJob().add(script).exec().isSuccess
            payload.delete()
            setup.delete()
            launch.delete()
            Log.i(TAG, "set OriginZygisk enabled=$enabled result: $ok")
            ok
        }

    fun addKernelUmountPath(path: String, flags: Int): Boolean {
        val shell = getRootShell()
        val flagsArg = if (flags >= 0) "--flags $flags" else ""
        val cmd = "${getKsuDaemonPath()} kernel umount add $path $flagsArg"
        val result = ShellUtils.fastCmdResult(shell, cmd)
        Log.i(TAG, "add umount path $path result: $result")
        return result
    }

    fun removeKernelUmountPath(path: String): Boolean {
        val shell = getRootShell()
        val cmd = "${getKsuDaemonPath()} kernel umount del $path"
        val result = ShellUtils.fastCmdResult(shell, cmd)
        Log.i(TAG, "remove umount path $path result: $result")
        return result
    }

    fun listKernelUmountPaths(): String {
        val shell = getRootShell()
        val cmd = "${getKsuDaemonPath()} kernel umount list"
        return try {
            runCmd(shell, cmd).trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list umount paths", e)
            ""
        }
    }

    fun addUmountConfigUmountPath(path: String, flags: Int): Boolean {
        val shell = getRootShell()
        val flagsArg = if (flags >= 0) "--flags $flags" else ""
        val cmd = "${getKsuDaemonPath()} umount-config add $path $flagsArg"
        val result = ShellUtils.fastCmdResult(shell, cmd)
        Log.i(TAG, "add umount path $path result: $result")
        return result
    }

    fun removeUmountConfigUmountPath(path: String): Boolean {
        val shell = getRootShell()
        val cmd = "${getKsuDaemonPath()} umount-config del $path"
        val result = ShellUtils.fastCmdResult(shell, cmd)
        Log.i(TAG, "remove umount path $path result: $result")
        return result
    }

    fun listUmountConfigUmountPaths(): String {
        val shell = getRootShell()
        val cmd = "${getKsuDaemonPath()} umount-config list"
        return try {
            runCmd(shell, cmd).trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list umount paths", e)
            ""
        }
    }

}
