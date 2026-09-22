package com.originsu.manager.domain.usecase

import android.content.Context
import android.net.Uri
import com.originsu.manager.data.flash.KpmPatchRepository
import java.io.File

class PatchAnyKernelWithKpmUseCase(private val repository: KpmPatchRepository) {
    suspend operator fun invoke(
        context: Context,
        zipFile: File,
        undo: Boolean,
        onLog: (String) -> Unit = {},
    ) = repository.patchAnyKernelZip(context, zipFile, undo, onLog)
}

class PatchBootImageWithKpmUseCase(private val repository: KpmPatchRepository) {
    suspend operator fun invoke(
        context: Context,
        bootUri: Uri,
        undo: Boolean,
        onLog: (String) -> Unit = {},
    ) = repository.patchBootImage(context, bootUri, undo, onLog)
}

class SaveKpmPatchedFileUseCase(private val repository: KpmPatchRepository) {
    suspend operator fun invoke(context: Context, file: File, displayName: String) =
        repository.saveToDownloads(context, file, displayName)
}
