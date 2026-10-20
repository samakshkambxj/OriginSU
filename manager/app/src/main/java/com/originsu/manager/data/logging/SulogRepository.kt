package com.originsu.manager.data.logging

import android.os.SystemClock
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.SulogFile
import com.originsu.manager.domain.model.SulogState
import com.originsu.manager.domain.model.buildSulogDigest
import com.originsu.manager.domain.model.parseSulogLines
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.ArrayDeque

class SulogRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SulogState())
    val state: StateFlow<SulogState> = mutableState.asStateFlow()

    suspend fun refresh(
        preferredFilePath: String? = state.value.selectedFilePath,
    ): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update {
                it.copy(isRefreshing = !it.isLoading, errorMessage = null)
            }
            runCatching {
                val files = listFiles()
                currentCoroutineContext().ensureActive()
                val selectedFile = when {
                    files.isEmpty() -> null
                    preferredFilePath != null ->
                        files.firstOrNull { it.path == preferredFilePath } ?: files.first()

                    else -> files.first()
                }
                val lines = selectedFile?.let { readFile(it.path) }.orEmpty()
                currentCoroutineContext().ensureActive()
                val currentTimeMillis = System.currentTimeMillis()
                val uptimeMillis = SystemClock.uptimeMillis()
                val entries = parseSulogLines(
                    lines = lines,
                    currentTimeMillis = currentTimeMillis,
                    uptimeMillis = uptimeMillis,
                )
                val digestEntries = entries.toMutableList()
                var digestFileCount = if (selectedFile != null) 1 else 0
                var remainingDigestLines = DIGEST_LINE_BUDGET - lines.size
                for (file in files) {
                    if (file.path == selectedFile?.path || remainingDigestLines <= 0) continue
                    val extraLines = readFile(
                        file.path,
                        minOf(DIGEST_LINES_PER_FILE, remainingDigestLines),
                    )
                    if (extraLines.isEmpty()) continue
                    digestFileCount++
                    remainingDigestLines -= extraLines.size
                    digestEntries += parseSulogLines(
                        lines = extraLines,
                        currentTimeMillis = currentTimeMillis,
                        uptimeMillis = uptimeMillis,
                    )
                }
                mutableState.value = SulogState(
                    status = ksuCliRepository.getFeatureStatus("sulog"),
                    enabled = ksuCliRepository.getFeaturePersistValue("sulog") == 1L,
                    files = files,
                    selectedFilePath = selectedFile?.path,
                    entries = entries,
                    digest = buildSulogDigest(digestEntries, digestFileCount),
                    isLoading = false,
                )
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(ksuCliRepository.execKsud("feature set sulog ${if (enabled) 1 else 0}", true))
            check(ksuCliRepository.execKsud("feature save", true))
        }
    }

    suspend fun clean(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = SuFile(path)
            val success = if (mutableState.value.files.firstOrNull()?.path == path) {
                file.clear()
            } else {
                file.delete()
            }
            check(success)
        }
    }

    private fun listFiles(): List<SulogFile> {
        val names = SuFile(SULOG_DIR).list().orEmpty().mapNotNull { name ->
            val match = FILE_NAME_REGEX.matchEntire(name) ?: return@mapNotNull null
            val date = LocalDate.parse(match.groupValues[1])
            val rotation = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: 0
            Triple(name, date, rotation)
        }.sortedWith(
            compareByDescending<Triple<String, LocalDate, Int>> { it.second }
                .thenByDescending { it.third }
        ).map { it.first }
        return names.map { SulogFile(name = it, path = "$SULOG_DIR/$it") }
    }

    private fun readFile(path: String, limit: Int = LINE_LIMIT): List<String> {
        val file = SuFile(path)
        if (!file.isFile) return emptyList()
        val lines = ArrayDeque<String>(limit)
        SuFileInputStream.open(file).use { input ->
            InputStreamReader(input).buffered().useLines { sequence ->
                sequence.forEach { line ->
                    if (lines.size == limit) lines.removeFirst()
                    lines.addLast(line)
                }
            }
        }
        return lines.toList()
    }

    private companion object {
        const val SULOG_DIR = "/data/adb/ksu/log"
        const val LINE_LIMIT = 1000
        const val DIGEST_LINE_BUDGET = 4000
        const val DIGEST_LINES_PER_FILE = 1000
        val FILE_NAME_REGEX = Regex("""sulog-(\d{4}-\d{2}-\d{2})(?:-(\d+))?\.log""")
    }
}
