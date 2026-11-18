package com.kazesu.manager.ui.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.kazesu.manager.R
import com.kazesu.manager.getKsuDaemonPath
import com.kazesu.manager.ksuApp
import com.topjohnwu.superuser.CallbackList
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Patch a boot image with the kernel from an AnyKernel zip.
 *
 * The user supplies a stock boot image file and an AnyKernel zip. The kernel
 * (Image / Image.gz / zImage / zImage-dtb / kernel) is extracted from the zip,
 * decompressed if needed, and injected into the boot image using
 * `ksud boot-kernel repack`. The patched image is saved to Downloads and its
 * [Uri] is returned via [onFinished] so the caller can flash it.
 */
fun patchBootWithAnyKernel(
    context: Context,
    bootUri: Uri,
    zipUri: Uri,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
    onFinished: (Boolean, Uri?) -> Unit,
) {
    fun q(value: String) = "'${value.replace("'", "'\\''")}'"
    fun command(cmd: String) {
        val out = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let(onStdout) }
        }
        val err = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let(onStderr) }
        }
        val result = withNewRootShell { newJob().add(cmd).to(out, err).exec() }
        if (!result.isSuccess) {
            onStderr("Command failed (${result.code}): $cmd")
        }
    }

    val timestamp = System.currentTimeMillis()
    val workDir = File(ksuApp.cacheDir, "akpatch_$timestamp")
    if (!workDir.mkdirs()) {
        onStderr("Cannot create work directory")
        onFinished(false, null)
        return
    }

    try {
        // --- Copy the boot image into the work dir ---
        val bootImg = File(workDir, "boot.img")
        copyUriToFile(context, bootUri, bootImg)

        // --- Copy the AnyKernel zip into the work dir ---
        val zipFile = File(workDir, "ak.zip")
        copyUriToFile(context, zipUri, zipFile)

        // --- Extract the kernel from the AnyKernel zip ---
        val newKernel = File(workDir, "kernel-new")
        val kernelName = extractKernelFromZip(zipFile, newKernel) ?: run {
            onStderr(context.getString(R.string.invalid_anykernel_kernel))
            onFinished(false, null)
            return
        }
        onStdout("- Found kernel in zip: $kernelName")

        // --- Repack the boot image with the new kernel ---
        val patched = File(workDir, "new-boot.img")
        val daemon = q(getKsuDaemonPath())
        onStdout("- Repacking boot image with AnyKernel kernel")
        command(
            "$daemon boot-kernel repack --boot ${q(bootImg.absolutePath)} " +
                "--kernel ${q(newKernel.absolutePath)} --output ${q(patched.absolutePath)}"
        )
        if (!patched.isFile || patched.length() == 0L) {
            onStderr(context.getString(R.string.patch_boot_failed, "repack produced no image"))
            onFinished(false, null)
            return
        }

        // --- Save to Downloads ---
        val savedUri = saveToDownloads(context, patched, "KazeSU_akpatched_boot_$timestamp.img")
        if (savedUri == null) {
            onStderr("Cannot save patched boot image")
            onFinished(false, null)
            return
        }
        onStdout(context.getString(R.string.patch_boot_saved, savedUri.toString()))
        onFinished(true, savedUri)
    } catch (e: Exception) {
        onStderr(e.message ?: context.getString(R.string.patch_boot_failed, "unknown error"))
        onFinished(false, null)
    } finally {
        runCatching { workDir.deleteRecursively() }
    }
}

private fun copyUriToFile(context: Context, uri: Uri, dest: File) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("Cannot open ${uri.toString()}")
}

/**
 * Kernel image names searched inside an AnyKernel zip, in order.
 * .gz is decompressed on the fly; other compression is rejected.
 */
private val AKERNEL_CANDIDATES = listOf(
    "Image",
    "Image.gz",
    "zImage",
    "zImage-dtb",
    "kernel",
)

/**
 * Find a kernel inside [zip], write it to [out], and return the entry name.
 * Returns null if no known kernel entry exists.
 */
private fun extractKernelFromZip(zip: File, out: File): String? {
    ZipFile(zip).use { zipFile ->
        val entry = AKERNEL_CANDIDATES.firstNotNullOfOrNull { name ->
            zipFile.getEntry(name)?.let { name to it }
        } ?: return null
        val (name, zipEntry) = entry
        zipFile.getInputStream(zipEntry).use { input ->
            val stream = if (name.endsWith(".gz")) {
                GZIPInputStream(input)
            } else {
                input
            }
            stream.use { it.copyTo(FileOutputStream(out)) }
        }
        return name
    }
}

/**
 * Save [file] to Downloads using MediaStore (Android Q+) or FileProvider
 * (pre-Q). Returns a content [Uri] that can be read back via
 * [ContentResolver.openInputStream], or null on failure.
 */
private fun saveToDownloads(context: Context, file: File, displayName: String): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: return null
        uri
    } else {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: return null
        uri
    }
}
