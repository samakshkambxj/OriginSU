package com.originsu.manager.data.module

import android.util.Log
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.KpmModule
import com.originsu.manager.domain.model.KpmState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class KpmRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    companion object {
        private const val TAG = "KpmRepository"
    }

    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(KpmState())
    val state: StateFlow<KpmState> = mutableState.asStateFlow()

    suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(refreshing = true) }
            runCatching {
                val modules = loadAllModules()
                val version = runCatching { ksuCliRepository.getKpmVersion() }.getOrDefault("")
                mutableState.value = KpmState(
                    modules = modules,
                    refreshing = false,
                    version = version,
                )
            }.onFailure { e ->
                Log.e(TAG, "refresh KPM modules failed", e)
                mutableState.update { it.copy(refreshing = false) }
            }
        }
    }

    suspend fun loadModule(path: String, args: String? = null): String =
        withContext(Dispatchers.IO) {
            ksuCliRepository.loadKpmModule(path, args)
        }

    suspend fun unloadModule(name: String): String =
        withContext(Dispatchers.IO) {
            ksuCliRepository.unloadKpmModule(name)
        }

    suspend fun getModuleInfo(name: String): String =
        withContext(Dispatchers.IO) {
            ksuCliRepository.getKpmModuleInfo(name)
        }

    suspend fun controlModule(name: String, args: String? = null): Int =
        withContext(Dispatchers.IO) {
            ksuCliRepository.controlKpmModule(name, args)
        }

    fun parseModuleInfo(name: String, info: String): KpmModule? {
        if (info.isBlank()) return null
        val properties = info.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                when (parts.size) {
                    2 -> parts[0].trim() to parts[1].trim()
                    1 -> parts[0].trim() to ""
                    else -> null
                }
            }
            .toMap()
        return KpmModule(
            id = name,
            name = properties["name"] ?: name,
            version = properties["version"].orEmpty(),
            author = properties["author"].orEmpty(),
            description = properties["description"].orEmpty(),
            args = properties["args"].orEmpty(),
        )
    }

    private fun loadAllModules(): List<KpmModule> {
        val result = mutableListOf<KpmModule>()
        val raw = runCatching { ksuCliRepository.listKpmModules() }.getOrDefault("")
        val names = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        for (name in names) {
            runCatching {
                val info = ksuCliRepository.getKpmModuleInfo(name)
                parseModuleInfo(name, info)?.let { result.add(it) }
            }.onFailure { e ->
                Log.e(TAG, "Error processing KPM module $name", e)
            }
        }
        return result
    }
}
