package com.originsu.manager.data.flash

import android.content.Context
import android.net.Uri
import android.util.Log
import com.originsu.manager.R
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.FlashProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
class HorizonKernelState {
    private val _state = MutableStateFlow(FlashProgress())
    private val fullLogs = ConcurrentLinkedQueue<String>()
    val state: StateFlow<FlashProgress> = _state.asStateFlow()

    fun updateProgress(progress: Float) {
        _state.update { it.copy(progress = progress) }
    }

    fun updateStep(step: String) {
        _state.update { it.copy(currentStep = step) }
    }

    fun addLog(log: String) {
        fullLogs.add(log)
        _state.update {
            it.copy(logs = it.logs + log)
        }
    }

    fun addConsoleLog(log: String) {
        fullLogs.add(log)
    }

    fun getFullLog(): String = fullLogs.joinToString("\n")

    fun setError(error: String) {
        _state.update { it.copy(isFlashing = false, error = error) }
    }

    fun startFlashing() {
        fullLogs.clear()
        _state.update {
            it.copy(
                isFlashing = true,
                isCompleted = false,
                progress = 0f,
                currentStep = "",
                logs = emptyList(),
                error = ""
            )
        }
    }

    fun completeFlashing() {
        _state.update {
            it.copy(
                isFlashing = false,
                isCompleted = true,
                progress = 1f
            )
        }
    }

    fun reset() {
        fullLogs.clear()
        _state.value = FlashProgress()
    }
}

class HorizonKernelWorker(
    private val context: Context,
    private val state: HorizonKernelState,
    private val ksuCliRepository: KsuCliRepository,
    private val slot: String? = null,
    private val kpmPatchEnabled: Boolean = false,
    private val kpmUndoPatch: Boolean = false,
) : Thread() {
    var uri: Uri? = null

    override fun run() {
        state.startFlashing()
        state.updateStep(context.getString(R.string.horizon_preparing))

        val zipFile = File(context.cacheDir, "anykernel3.zip")
        try {
            if (!ksuCliRepository.rootAvailable()) {
                state.setError(context.getString(R.string.root_required))
                return
            }

            state.updateStep(context.getString(R.string.horizon_copying_files))
            state.updateProgress(0.2f)
            copyToCache(zipFile)

            if (kpmPatchEnabled || kpmUndoPatch) {
                state.updateStep(context.getString(R.string.kpm_preparing_tools))
                state.updateProgress(0.4f)
                performKpmPatch(zipFile)
            }

            state.updateStep(context.getString(R.string.horizon_flashing))
            state.updateProgress(0.7f)
            val succeeded = ksuCliRepository.flashAnyKernel(
                zipFile = zipFile,
                slot = slot,
                onStdout = ::handleOutput,
                onStderr = ::handleConsoleOutput
            )
            if (!succeeded) {
                state.setError(context.getString(R.string.flash_failed_message))
                return
            }

            runCatching { ksuCliRepository.install() }.onFailure { error ->
                Log.w(TAG, "Failed to refresh ksud after a successful kernel flash", error)
            }
            state.updateStep(context.getString(R.string.horizon_flash_complete_status))
            state.completeFlashing()
        } catch (error: Exception) {
            state.setError(
                error.message ?: context.getString(R.string.horizon_unknown_error)
            )
        } finally {
            if (zipFile.exists()) {
                zipFile.delete()
            }
        }
    }

    private fun copyToCache(zipFile: File) {
        zipFile.delete()
        val source = uri
            ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        input.use {
            zipFile.outputStream().use { output ->
                it.copyTo(output)
            }
        }
        if (!zipFile.isFile) {
            throw IOException(context.getString(R.string.horizon_copy_failed))
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

    private fun exportAsset(name: String, dest: File) {
        context.assets.open(name).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (!dest.isFile) {
            throw IOException(context.getString(R.string.kpm_patch_operation_failed, name))
        }
    }

    private fun performKpmPatch(zipFile: File) {
        val workDir = File(context.cacheDir, "kpm_patch_${System.currentTimeMillis()}")
        try {
            if (!workDir.mkdirs()) {
                throw IOException(context.getString(R.string.kpm_patch_operation_failed, workDir.absolutePath))
            }
            exportAsset("kptools", File(workDir, "kptools"))
            exportAsset("kpimg", File(workDir, "kpimg"))
            shellCmd("chmod a+rx ${workDir.absolutePath}/kptools")

            val extractDir = File(workDir, "extracted")
            if (!extractDir.mkdirs()) {
                throw IOException(context.getString(R.string.kpm_patch_operation_failed, extractDir.absolutePath))
            }
            state.updateStep(
                if (kpmUndoPatch) {
                    context.getString(R.string.kpm_undoing_patch)
                } else {
                    context.getString(R.string.kpm_applying_patch)
                }
            )
            state.updateProgress(0.5f)

            if (!shellCmd("cd ${extractDir.absolutePath} && unzip -o \"${zipFile.absolutePath}\"")) {
                throw IOException(context.getString(R.string.kpm_extract_zip_failed))
            }
            val imageFile = shellOutput(
                "find ${extractDir.absolutePath} -name '*Image*' -type f"
            ).lines().map { it.trim() }.firstOrNull { it.isNotBlank() }
                ?: throw IOException(context.getString(R.string.kpm_image_file_not_found))
            state.addLog(context.getString(R.string.kpm_found_image_file, imageFile))

            val imageDir = File(imageFile).parent ?: extractDir.absolutePath
            val imageName = File(imageFile).name
            shellCmd("cp ${workDir.absolutePath}/kptools $imageDir/")
            shellCmd("cp ${workDir.absolutePath}/kpimg $imageDir/")
            val patchCommand = if (kpmUndoPatch) {
                "cd $imageDir && chmod a+rx kptools && ./kptools -u -s 123 -i $imageName -k kpimg -o oImage && mv oImage $imageName"
            } else {
                "cd $imageDir && chmod a+rx kptools && ./kptools -p -s 123 -i $imageName -k kpimg -o oImage && mv oImage $imageName"
            }
            if (!shellCmd(patchCommand)) {
                throw IOException(
                    if (kpmUndoPatch) {
                        context.getString(R.string.kpm_undo_patch_failed)
                    } else {
                        context.getString(R.string.kpm_patch_failed)
                    }
                )
            }
            state.addLog(
                if (kpmUndoPatch) {
                    context.getString(R.string.kpm_undo_patch_success)
                } else {
                    context.getString(R.string.kpm_patch_success)
                }
            )
            shellCmd("rm -f $imageDir/kptools $imageDir/kpimg $imageDir/oImage")

            val patchedFile = File(context.cacheDir, "anykernel3-kpm-patched.zip")
            if (patchedFile.exists()) patchedFile.delete()
            repackZipFolder(extractDir, patchedFile)
            if (!patchedFile.isFile) {
                throw IOException(context.getString(R.string.kpm_patch_operation_failed, patchedFile.absolutePath))
            }
            if (!patchedFile.renameTo(zipFile)) {
                shellCmd("mv \"${patchedFile.absolutePath}\" \"${zipFile.absolutePath}\"")
            }
            state.addLog(context.getString(R.string.kpm_file_repacked))
        } catch (e: Exception) {
            if (e is IOException) {
                state.addLog(context.getString(R.string.kpm_patch_operation_failed, e.message.orEmpty()))
                throw e
            }
            state.addLog(context.getString(R.string.kpm_patch_operation_failed, e.message.orEmpty()))
            throw IOException(context.getString(R.string.kpm_patch_operation_failed, e.message.orEmpty()), e)
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun repackZipFolder(sourceDir: File, zipFile: File) {
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

    private fun handleOutput(line: String) {
        Log.i(TAG, line)
        state.addLog(line)

        when {
            line.contains("extracting", ignoreCase = true) -> {
                state.updateProgress(0.75f)
            }

            line.contains("installing", ignoreCase = true) -> {
                state.updateProgress(0.85f)
            }

            line.contains("complete", ignoreCase = true) -> {
                state.updateProgress(0.95f)
            }
        }
    }

    private fun handleConsoleOutput(line: String) {
        Log.i(TAG, line)
        state.addConsoleLog(line)
    }

    private companion object {
        const val TAG = "HorizonKernelWorker"
    }
}
