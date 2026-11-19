package com.kazesu.manager.ui.screen.settings.tools

import android.content.Context
import android.net.Uri
import com.kazesu.manager.R
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun isSelinuxPermissive(): Boolean {
    val result = Shell.cmd("getenforce").exec()
    val output = result.out.joinToString("\n").trim().lowercase()
    return output == "permissive"
}

fun setSelinuxPermissive(permissive: Boolean): Boolean {
    val target = if (permissive) "0" else "1"
    val result = Shell.cmd("setenforce $target").exec()
    return result.isSuccess
}

suspend fun backupAllowlistToUri(context: Context, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "allowlist_backup_tmp.bin")
    try {
        return@withContext copyAllowlistToFile(tempFile) && runCatching {
            context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                true
            } ?: false
        }.getOrElse { false }
    } finally {
        tempFile.delete()
    }
}

suspend fun restoreAllowlistFromUri(context: Context, sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "allowlist_restore_tmp.bin")
    try {
        val downloaded = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
        }.getOrElse { false }
        return@withContext downloaded && copyFileToAllowlist(tempFile)
    } finally {
        tempFile.delete()
    }
}

private suspend fun copyAllowlistToFile(targetFile: File): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        targetFile.parentFile?.mkdirs()
        val result = Shell.cmd(
            "cp /data/adb/ksu/.allowlist \"${targetFile.absolutePath}\"",
            "chmod 0644 \"${targetFile.absolutePath}\""
        ).exec()
        result.isSuccess
    }.getOrDefault(false)
}

private suspend fun copyFileToAllowlist(sourceFile: File): Boolean = withContext(Dispatchers.IO) {
    if (!sourceFile.exists()) return@withContext false
    runCatching {
        val result = Shell.cmd(
            "cp \"${sourceFile.absolutePath}\" /data/adb/ksu/.allowlist",
            "chmod 0644 /data/adb/ksu/.allowlist"
        ).exec()
        result.isSuccess
    }.getOrDefault(false)
}

data class CpuInfo(
    val midrHex: String,
    val bogomips: Int,
    val coreCount: Int,
    val hwcap: String,
    val hwcap2: String
)

data class CpuPreset(
    val id: String,
    val midrHex: String,
    val model: String,
    val coreTypeRes: Int? = null
)

fun spoofCpuPresetList(): List<CpuPreset> = listOf(
    CpuPreset("custom", "custom", "", null),

    CpuPreset("a53", "0x410fd030", "Cortex-A53", R.string.spoof_cpu_core_little),
    CpuPreset("a55", "0x413fd050", "Cortex-A55", R.string.spoof_cpu_core_little),
    CpuPreset("a510", "0x413fd0fa", "Cortex-A510", R.string.spoof_cpu_core_little),
    CpuPreset("a520", "0x413fd0f4", "Cortex-A520", R.string.spoof_cpu_core_little),
    CpuPreset("a520ae", "0x413fd0f7", "Cortex-A520AE", R.string.spoof_cpu_core_little),

    CpuPreset("a57", "0x410fd070", "Cortex-A57", R.string.spoof_cpu_core_big),
    CpuPreset("a72", "0x410fd080", "Cortex-A72", R.string.spoof_cpu_core_big),
    CpuPreset("a73", "0x410fd090", "Cortex-A73", R.string.spoof_cpu_core_big),
    CpuPreset("a75", "0x411fd0a0", "Cortex-A75", R.string.spoof_cpu_core_big),
    CpuPreset("a76", "0x413fd0b1", "Cortex-A76", R.string.spoof_cpu_core_big),
    CpuPreset("a77", "0x410fd0d0", "Cortex-A77", R.string.spoof_cpu_core_big),
    CpuPreset("a78", "0x413fd0d2", "Cortex-A78", R.string.spoof_cpu_core_big),

    CpuPreset("a710", "0x413fd0d4", "Cortex-A710", R.string.spoof_cpu_core_middle),
    CpuPreset("a715", "0x413fd0d5", "Cortex-A715", R.string.spoof_cpu_core_middle),
    CpuPreset("a720", "0x413fd0e4", "Cortex-A720", R.string.spoof_cpu_core_middle),
    CpuPreset("a725", "0x413fd0f2", "Cortex-A725", R.string.spoof_cpu_core_middle),
    CpuPreset("a720ae", "0x413fd0f8", "Cortex-A720AE", R.string.spoof_cpu_core_middle),

    CpuPreset("x1", "0x413fd0c1", "Cortex-X1", R.string.spoof_cpu_core_prime),
    CpuPreset("x2", "0x413fd0e0", "Cortex-X2", R.string.spoof_cpu_core_prime),
    CpuPreset("x3", "0x413fd0e3", "Cortex-X3", R.string.spoof_cpu_core_prime),
    CpuPreset("x4", "0x413fd0f1", "Cortex-X4", R.string.spoof_cpu_core_prime),
    CpuPreset("x925", "0x413fd0f3", "Cortex-X925", R.string.spoof_cpu_core_prime),

    CpuPreset("n1", "0x413fd0c0", "Neoverse N1", R.string.spoof_cpu_core_big),
    CpuPreset("n2", "0x413fd0e1", "Neoverse N2", R.string.spoof_cpu_core_big),
    CpuPreset("v2", "0x413fd0f0", "Neoverse V2", R.string.spoof_cpu_core_big),
    CpuPreset("n3", "0x413fd101", "Neoverse N3", R.string.spoof_cpu_core_big),
    CpuPreset("v3", "0x413fd100", "Neoverse V3", R.string.spoof_cpu_core_big),

    CpuPreset("kryo4", "0x511f804d", "Kryo 4xx Gold", R.string.spoof_cpu_core_big),
    CpuPreset("kryo5", "0x511f805c", "Kryo 5xx Gold", R.string.spoof_cpu_core_big),
    CpuPreset("kryo6", "0x513f8050", "Kryo 6xx Gold+", R.string.spoof_cpu_core_big),
    CpuPreset("exynosm5", "0x553f1000", "Exynos M5", R.string.spoof_cpu_core_big),
)

suspend fun readCurrentCpuIdentity(): CpuInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val cpuInfoResult = Shell.cmd("cat /proc/cpuinfo").exec()
        if (!cpuInfoResult.isSuccess) return@runCatching null

        val lines = cpuInfoResult.out
        var implementer = ""
        var variant = ""
        var part = ""
        var revision = ""
        var bogomips = 0

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("CPU implementer") -> {
                    implementer = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("CPU variant") -> {
                    variant = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("CPU part") -> {
                    part = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("CPU revision") -> {
                    revision = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("BogoMIPS") -> {
                    val bogoStr = trimmed.substringAfter(":").trim()
                    bogomips = bogoStr.toFloatOrNull()?.toInt() ?: 0
                }
            }
            if (implementer.isNotEmpty() && part.isNotEmpty() && variant.isNotEmpty() && revision.isNotEmpty()) {
                break
            }
        }

        if (implementer.isEmpty() || part.isEmpty()) {
            return@runCatching null
        }

        val midr = buildString {
            append(implementer.removePrefix("0x"))
            append(variant.removePrefix("0x").padStart(1, '0'))
            append("0")
            append(part.removePrefix("0x").padStart(3, '0'))
            append(revision.removePrefix("0x").padStart(1, '0'))
        }
        val midrHex = "0x$midr"

        val coreCount = Runtime.getRuntime().availableProcessors()

        val hwcapResult = Shell.cmd("getprop ro.hwcap").exec()
        val hwcap = hwcapResult.out.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: "0x0"

        val hwcap2Result = Shell.cmd("getprop ro.hwcap2").exec()
        val hwcap2 = hwcap2Result.out.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: "0x0"

        CpuInfo(
            midrHex = midrHex,
            bogomips = bogomips,
            coreCount = coreCount,
            hwcap = hwcap,
            hwcap2 = hwcap2
        )
    }.getOrNull()
}

private const val BUSYBOX = "/data/adb/ksu/bin/busybox"

private fun backupTimestamp(): String =
    java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        .format(java.util.Date())

/** Suggested filename for a modules backup tar (user-visible in the SAF picker). */
fun modulesBackupSuggestedName(): String =
    "kazesu_modules_backup_${backupTimestamp()}.tar"

/** Suggested filename for an allowlist backup tar (user-visible in the SAF picker). */
fun allowlistBackupSuggestedName(): String =
    "kazesu_allowlist_backup_${backupTimestamp()}.tar"

/** Run a shell command as root and report success/failure. */
private fun shellOk(cmd: String): Boolean =
    runCatching { Shell.cmd(cmd).exec().isSuccess }.getOrDefault(false)

/**
 * Back up /data/adb/modules as a tar archive, streamed to [destUri].
 * Skips when the modules directory is empty.
 */
suspend fun backupModulesToUri(context: Context, destUri: android.net.Uri): Boolean =
    withContext(Dispatchers.IO) {
        val modulesDir = java.io.File("/data/adb/modules")
        if (modulesDir.listFiles()?.isEmpty() != false) return@withContext false

        val tmpPath = "${context.cacheDir}/modules_backup_${backupTimestamp()}.tar"
        val tarCmd = "$BUSYBOX tar -cpf '$tmpPath' -C /data/adb/modules \$(ls /data/adb/modules)"
        if (!shellOk(tarCmd)) return@withContext false

        return@withContext try {
            java.io.File(tmpPath).inputStream().use { input ->
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            java.io.File(tmpPath).delete()
        }
    }

/**
 * Back up /data/adb/ksu/.allowlist as a tar archive, streamed to [destUri].
 */
suspend fun backupAllowlistToUriTar(context: Context, destUri: android.net.Uri): Boolean =
    withContext(Dispatchers.IO) {
        val allowlist = java.io.File("/data/adb/ksu/.allowlist")
        if (!allowlist.exists()) return@withContext false

        val tmpPath = "${context.cacheDir}/allowlist_backup_${backupTimestamp()}.tar"
        val tarCmd = "$BUSYBOX tar -cpf '$tmpPath' -C /data/adb/ksu .allowlist"
        if (!shellOk(tarCmd)) return@withContext false

        return@withContext try {
            java.io.File(tmpPath).inputStream().use { input ->
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            java.io.File(tmpPath).delete()
        }
    }

/**
 * Restore modules from a tar [srcUri] into /data/adb/modules_update (takes effect on reboot).
 */
suspend fun restoreModulesFromUri(context: Context, srcUri: android.net.Uri): Boolean =
    withContext(Dispatchers.IO) {
        val tmpPath = "${context.cacheDir}/modules_restore_${backupTimestamp()}.tar"
        try {
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                java.io.File(tmpPath).outputStream().use { out ->
                    input.copyTo(out)
                }
            }
        } catch (e: Exception) {
            return@withContext false
        }

        val extractCmd = "mkdir -p '/data/adb/modules_update' && $BUSYBOX tar -xpf '$tmpPath' -C /data/adb/modules_update"
        val result = shellOk(extractCmd)

        java.io.File(tmpPath).delete()
        return@withContext result
    }

/**
 * Restore allowlist from a tar [srcUri] into /data/adb/ksu.
 */
suspend fun restoreAllowlistFromUriTar(context: Context, srcUri: android.net.Uri): Boolean =
    withContext(Dispatchers.IO) {
        val tmpPath = "${context.cacheDir}/allowlist_restore_${backupTimestamp()}.tar"
        try {
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                java.io.File(tmpPath).outputStream().use { out ->
                    input.copyTo(out)
                }
            }
        } catch (e: Exception) {
            return@withContext false
        }

        val extractCmd = "$BUSYBOX tar -xpf '$tmpPath' -C /data/adb/ksu"
        val result = shellOk(extractCmd)

        java.io.File(tmpPath).delete()
        return@withContext result
    }