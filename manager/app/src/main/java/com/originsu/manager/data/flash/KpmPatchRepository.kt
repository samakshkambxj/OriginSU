package com.originsu.manager.data.flash

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.originsu.manager.R
import com.originsu.manager.data.shell.KsuCliRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone KPM (KernelPatch Module) binary injection.
 *
 * The same kptools/kpimg asset flow the Horizon kernel flash uses, factored
 * out so AnyKernel zips and raw boot images can be patched on their own:
 * - AnyKernel zip: patch the `*Image*` inside the zip, repack in place.
 * - boot.img: dump the kernel via `ksud boot-patch --dump-kernel`, patch it
 *   with kptools, reinsert with `ksud boot-patch --kernel --no-install`.
 *
 * kptools must run as root (app-private files are not executable otherwise),
 * so every entry point here requires root.
 */
class KpmPatchRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    /**
     * Patch (or unpatch with [undo]) the kernel image inside [zipFile] in
     * place. Returns the same file on success.
     */
    suspend fun patchAnyKernelZip(
        context: Context,
        zipFile: File,
        undo: Boolean,
        onLog: (String) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(zipFile.isFile) { context.getString(R.string.kpm_image_file_not_found) }
            performKpmPatch(context, zipFile, undo, onLog)
            zipFile
        }
    }

    /**
     * Copy [bootUri] to the cache, KPM-patch its kernel and return a patched
     * boot.img in the cache dir. The caller decides whether to flash it or
     * save it to Downloads.
     */
    suspend fun patchBootImage(
        context: Context,
        bootUri: Uri,
        undo: Boolean,
        onLog: (String) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ksuCliRepository.rootAvailable()) {
                throw IOException(context.getString(R.string.root_required))
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val workDir = File(context.cacheDir, "kpm_boot_$timestamp")
            if (!workDir.mkdirs()) {
                throw IOException(context.getString(R.string.patch_workdir_failed))
            }
            try {
                val bootImg = File(workDir, "boot.img")
                context.contentResolver.openInputStream(bootUri)?.use { input ->
                    bootImg.outputStream().use { input.copyTo(it) }
                } ?: throw IOException(context.getString(R.string.horizon_copy_failed))
                if (!bootImg.isFile) {
                    throw IOException(context.getString(R.string.horizon_copy_failed))
                }

                // Rootless: dump the raw kernel with ksud's own boot parser.
                onLog(context.getString(R.string.kpm_dumping_kernel))
                val kernel = File(workDir, "kernel")
                runSh(
                    context,
                    "${ksuCliRepository.getKsuDaemonPath()} boot-patch" +
                        " -b ${q(bootImg.absolutePath)}" +
                        " --dump-kernel ${q(kernel.absolutePath)}",
                    onLog
                )
                if (!kernel.isFile) {
                    throw IOException(context.getString(R.string.kpm_image_file_not_found))
                }

                // Rooted: kptools patch the kernel in place.
                patchKernelFile(context, workDir, kernel, undo, onLog)

                // Rootless: reinsert the patched kernel, no KSU install.
                onLog(context.getString(R.string.kpm_repacking_boot))
                val outDir = File(workDir, "out").apply { mkdirs() }
                runSh(
                    context,
                    "${ksuCliRepository.getKsuDaemonPath()} boot-patch" +
                        " -b ${q(bootImg.absolutePath)}" +
                        " -k ${q(kernel.absolutePath)}" +
                        " --no-install -o ${q(outDir.absolutePath)}",
                    onLog
                )
                val patched = outDir.listFiles { file -> file.isFile && file.extension == "img" }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IOException(context.getString(R.string.patch_repack_failed))
                // Move out of the work dir before cleanup.
                val result = File(context.cacheDir, "kpm-boot-$timestamp.img")
                if (result.exists()) result.delete()
                if (!patched.renameTo(result)) {
                    patched.copyTo(result, overwrite = true)
                }
                result
            } finally {
                runCatching { workDir.deleteRecursively() }
            }
        }
    }

    /** Save [file] to Downloads/OriginSU and return its content URI. */
    suspend fun saveToDownloads(context: Context, file: File, displayName: String): Uri =
        withContext(Dispatchers.IO) {
            require(file.isFile) { "missing file" }
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
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "OriginSU"
                )
                if (!dir.exists() && !dir.mkdirs()) {
                    throw IllegalStateException("Downloads dir unavailable")
                }
                val dest = File(dir, displayName)
                file.copyTo(dest, overwrite = true)
                android.net.Uri.fromFile(dest)
            }
        }

    private fun performKpmPatch(
        context: Context,
        zipFile: File,
        undo: Boolean,
        onLog: (String) -> Unit,
    ) {
        val workDir = File(context.cacheDir, "kpm_patch_${System.currentTimeMillis()}")
        try {
            if (!workDir.mkdirs()) {
                throw IOException(context.getString(R.string.kpm_patch_operation_failed, workDir.absolutePath))
            }
            exportAsset(context, "kptools", File(workDir, "kptools"))
            exportAsset(context, "kpimg", File(workDir, "kpimg"))
            shellCmd("chmod a+rx ${workDir.absolutePath}/kptools")

            val extractDir = File(workDir, "extracted")
            if (!extractDir.mkdirs()) {
                throw IOException(context.getString(R.string.kpm_patch_operation_failed, extractDir.absolutePath))
            }
            onLog(
                if (undo) {
                    context.getString(R.string.kpm_undoing_patch)
                } else {
                    context.getString(R.string.kpm_applying_patch)
                }
            )

            if (!shellCmd("cd ${extractDir.absolutePath} && unzip -o \"${zipFile.absolutePath}\"")) {
                throw IOException(context.getString(R.string.kpm_extract_zip_failed))
            }
            val imageFile = shellOutput(
                "find ${extractDir.absolutePath} -name '*Image*' -type f"
            ).lines().map { it.trim() }.firstOrNull { it.isNotBlank() }
                ?: throw IOException(context.getString(R.string.kpm_image_file_not_found))
            onLog(context.getString(R.string.kpm_found_image_file, imageFile))

            patchKernelFile(context, workDir, File(imageFile), undo, onLog)

            val patchedFile = File(context.cacheDir, "anykernel3-kpm-patched.zip")
            if (patchedFile.exists()) patchedFile.delete()
            repackZipFolder(context, extractDir, patchedFile)
            if (!patchedFile.isFile) {
                throw IOException(context.getString(R.string.kpm_patch_operation_failed, patchedFile.absolutePath))
            }
            if (!patchedFile.renameTo(zipFile)) {
                shellCmd("mv \"${patchedFile.absolutePath}\" \"${zipFile.absolutePath}\"")
            }
            onLog(context.getString(R.string.kpm_file_repacked))
        } catch (e: Exception) {
            if (e is IOException) {
                onLog(context.getString(R.string.kpm_patch_operation_failed, e.message.orEmpty()))
                throw e
            }
            onLog(context.getString(R.string.kpm_patch_operation_failed, e.message.orEmpty()))
            throw IOException(context.getString(R.string.kpm_patch_operation_failed, e.message.orEmpty()), e)
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun patchKernelFile(
        context: Context,
        workDir: File,
        kernelFile: File,
        undo: Boolean,
        onLog: (String) -> Unit,
    ) {
        exportAsset(context, "kptools", File(workDir, "kptools"))
        exportAsset(context, "kpimg", File(workDir, "kpimg"))
        val imageDir = kernelFile.parent ?: workDir.absolutePath
        val imageName = kernelFile.name
        shellCmd("cp ${q(workDir.absolutePath + "/kptools")} ${q(imageDir + "/")}")
        shellCmd("cp ${q(workDir.absolutePath + "/kpimg")} ${q(imageDir + "/")}")
        // kptools honors the inherited umask, and the root shell here is forked
        // from the app (umask 077), so oImage comes out as a 600 root-owned
        // file. The repack step runs rootless as the app UID and would then
        // fail to open it, so restore world-readability after the move.
        val patchCommand = if (undo) {
            "cd ${q(imageDir)} && chmod a+rx kptools && ./kptools -u -s 123 -i ${q(imageName)} -k kpimg -o oImage && mv oImage ${q(imageName)} && chmod 644 ${q(imageName)}"
        } else {
            "cd ${q(imageDir)} && chmod a+rx kptools && ./kptools -p -s 123 -i ${q(imageName)} -k kpimg -o oImage && mv oImage ${q(imageName)} && chmod 644 ${q(imageName)}"
        }
        if (!shellCmd(patchCommand)) {
            throw IOException(
                if (undo) {
                    context.getString(R.string.kpm_undo_patch_failed)
                } else {
                    context.getString(R.string.kpm_patch_failed)
                }
            )
        }
        onLog(
            if (undo) {
                context.getString(R.string.kpm_undo_patch_success)
            } else {
                context.getString(R.string.kpm_patch_success)
            }
        )
        shellCmd("rm -f ${q(imageDir + "/kptools")} ${q(imageDir + "/kpimg")} ${q(imageDir + "/oImage")}")
    }

    private fun exportAsset(context: Context, name: String, dest: File) {
        context.assets.open(name).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (!dest.isFile) {
            throw IOException(context.getString(R.string.kpm_patch_operation_failed, name))
        }
    }

    private fun shellCmd(cmd: String): Boolean {
        return try {
            ksuCliRepository.getRootShell().newJob().add(cmd).exec().isSuccess
        } catch (_: Exception) {
            false
        }
    }

    private fun shellOutput(cmd: String): String {
        return try {
            ksuCliRepository.getRootShell().newJob().add(cmd)
                .to(ArrayList<String>(), null).exec().out.joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }

    /** Rootless `sh` invocation for ksud's own subcommands. */
    private fun runSh(context: Context, cmd: String, onLog: (String) -> Unit) {
        val shell = runCatching { Shell.Builder.create().build("sh") }.getOrNull()
            ?: throw IOException(context.getString(R.string.patch_shell_failed))
        shell.use {
            val result = it.newJob().add(cmd)
                .to(ArrayList<String>(), ArrayList<String>())
                .exec()
            result.out.forEach(onLog)
            if (!result.isSuccess) {
                throw IOException(result.err.joinToString("\n").ifBlank { cmd })
            }
        }
    }

    private fun repackZipFolder(context: Context, sourceDir: File, zipFile: File) {
        try {
            val buffer = ByteArray(8192)
            java.io.FileOutputStream(zipFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    sourceDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val relativePath = file.relativeTo(sourceDir).path
                            zos.putNextEntry(java.util.zip.ZipEntry(relativePath))
                            file.inputStream().use { fis ->
                                var length: Int
                                while (fis.read(buffer).also { length = it } > 0) {
                                    zos.write(buffer, 0, length)
                                }
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException(
                context.getString(R.string.kpm_patch_operation_failed, e.message.orEmpty()),
                e
            )
        }
    }

    private fun q(value: String) = "'${value.replace("'", "'\"'\"'")}'"
}
