package com.kazesu.manager.ui.util

import android.content.Context
import android.net.Uri
import com.kazesu.manager.R
import com.kazesu.manager.getKsuDaemonPath
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/** A boot image patched from an AnyKernel zip, ready to be flashed or saved. */
data class PatchedBootImage(
    val uri: Uri,
    val displayName: String,
)

/**
 * Replace the kernel of a stock boot image with the kernel carried by an
 * AnyKernel zip.
 *
 * AnyKernel zips ship the compiled kernel as `Image`, `Image.gz`, `zImage`,
 * `zImage-dtb` or `kernel` at the archive root. The kernel is copied into the
 * boot image's kernel block by `ksud boot-kernel repack`, which also verifies
 * that the result still contains a valid ARM64 kernel. The patched image is
 * written to Downloads and returned as a content [Uri] that can be read back
 * by the normal flash flow.
 *
 * This is local file work: it needs no root and never touches a partition.
 */
fun patchBootWithAnyKernel(
    context: Context,
    bootUri: Uri,
    zipUri: Uri,
    onLog: (String) -> Unit = {},
): Result<PatchedBootImage> {
    val workDir = File(context.cacheDir, "anykernel-boot-${System.currentTimeMillis()}")
    if (!workDir.mkdirs()) {
        return Result.failure(IOException(context.getString(R.string.patch_workdir_failed)))
    }
    return try {
        val boot = File(workDir, "boot.img").also { copyUri(context, bootUri, it) }
        val zip = File(workDir, "anykernel.zip").also { copyUri(context, zipUri, it) }
        val kernel = File(workDir, "kernel")

        val entry = extractKernel(zip, kernel)
            ?: throw IOException(context.getString(R.string.invalid_anykernel_kernel))
        onLog("Kernel from zip: $entry")

        val patched = File(workDir, "patched-boot.img")
        runKsud(
            listOf(
                "boot-kernel", "repack",
                "--boot", boot.absolutePath,
                "--kernel", kernel.absolutePath,
                "--output", patched.absolutePath,
            ),
            onLog,
        )
        if (!patched.isFile || patched.length() == 0L) {
            throw IOException(context.getString(R.string.patch_repack_failed))
        }

        val destination = DownloadDestination.create(
            context = context,
            displayName = "KazeSU_anykernel_boot_${System.currentTimeMillis()}.img",
            mimeType = "application/octet-stream",
        )
        try {
            destination.openOutputStream().use { output ->
                patched.inputStream().use { it.copyTo(output) }
            }
            destination.publish()
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
        onLog(context.getString(R.string.patch_boot_saved, destination.displayName))
        Result.success(PatchedBootImage(destination.uri, destination.displayName))
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        workDir.deleteRecursively()
    }
}

private fun copyUri(context: Context, uri: Uri, destination: File) {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Cannot open ${uri.lastPathSegment ?: uri}")
    input.use { source ->
        destination.outputStream().use { source.copyTo(it) }
    }
}

/** Kernel entry names inside an AnyKernel zip, in priority order. */
private val KERNEL_ENTRIES = listOf(
    "Image",
    "Image.gz",
    "zImage",
    "zImage-dtb",
    "kernel",
)

/**
 * Copy the first known kernel entry from [zip] to [output], decompressing
 * gzip entries. Returns the entry name, or null when the zip has no kernel.
 */
private fun extractKernel(zip: File, output: File): String? {
    ZipFile(zip).use { archive ->
        val (name, entry) = KERNEL_ENTRIES.firstNotNullOfOrNull { candidate ->
            archive.getEntry(candidate)?.let { candidate to it }
        } ?: return null
        archive.getInputStream(entry).use { raw ->
            val stream = if (name.endsWith(".gz")) GZIPInputStream(raw) else raw
            stream.use { input ->
                output.outputStream().use { input.copyTo(it) }
            }
        }
        return name
    }
}

/**
 * Run a `ksud` subcommand directly, without a root shell. Boot repacking is
 * pure file work, so it works even on devices where the manager has no root.
 */
private fun runKsud(args: List<String>, onLog: (String) -> Unit) {
    val process = ProcessBuilder(listOf(getKsuDaemonPath()) + args)
        .redirectErrorStream(true)
        .start()
    val output = ArrayList<String>()
    process.inputStream.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            if (line.isNotBlank()) {
                output += line
                onLog(line)
            }
        }
    }
    val code = process.waitFor()
    if (code != 0) {
        val detail = output.takeLast(3).joinToString(" ").ifBlank { "exit $code" }
        throw IOException(detail)
    }
}
