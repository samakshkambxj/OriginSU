package com.originsu.manager.data.kernel

import android.app.Application
import android.content.Context
import android.util.Log
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.BoreKnob
import com.originsu.manager.domain.model.KernelTuningState
import com.originsu.manager.domain.model.SysctlEntry
import com.originsu.manager.domain.model.ZramState
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kernel tunables: TCP congestion control, BORE scheduler, ZRAM and a
 * generic sysctl editor.
 *
 * Live values are read/written as root via `sysctl` (and zram sysfs).
 * Entries flagged [SysctlEntry.persist] (plus the TCP choice when
 * [KernelTuningState.tcpPersist] is set, plus BORE values when
 * [KernelTuningState.borePersist] is set, plus the ZRAM setup when
 * [ZramState.persist] is set) are re-applied at every boot through a
 * script in `/data/adb/boot-completed.d`, which ksud executes at
 * boot-completed.
 */
class KernelTuningRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    companion object {
        private const val TAG = "KernelTuning"
        private const val PREFS = "kernel_tuning"
        private const val KEY_SYSCTLS = "sysctls_json"
        private const val KEY_TCP_PERSIST = "tcp_persist"
        private const val KEY_TCP_CHOICE = "tcp_choice"
        private const val KEY_BORE_PERSIST = "bore_persist"
        private const val KEY_ZRAM_PERSIST = "zram_persist"
        private const val KEY_ZRAM_SIZE = "zram_size"
        private const val KEY_ZRAM_ALGO = "zram_algo"
        private const val KEY_ZRAM_STREAMS = "zram_streams"

        private const val SWAPPINESS_KEY = "vm.swappiness"

        private const val ZRAM_SYSFS = "/sys/block/zram0"
        private const val ZRAM_DISKSIZE = "$ZRAM_SYSFS/disksize"
        private const val ZRAM_COMP_ALGO = "$ZRAM_SYSFS/comp_algorithm"
        private const val ZRAM_MAX_STREAMS = "$ZRAM_SYSFS/max_comp_streams"
        private const val ZRAM_MM_STAT = "$ZRAM_SYSFS/mm_stat"

        private const val ZRAM_MIN_SIZE = 64L * 1024 * 1024
        private const val ZRAM_MAX_STREAMS_LIMIT = 64L

        private val ALGO_PATTERN = Regex("^[A-Za-z0-9_-]+$")

        private const val TCP_AVAILABLE = "/proc/sys/net/ipv4/tcp_available_congestion_control"
        private const val TCP_CURRENT = "/proc/sys/net/ipv4/tcp_congestion_control"
        private const val TCP_KEY = "net.ipv4.tcp_congestion_control"

        private const val BORE_ENABLE_KEY = "kernel.sched_bore"

        /**
         * Known BORE tunables across patchset generations (BORE 4.x/5.x,
         * CachyOS variants): key to (min, max, default). Only knobs readable
         * on the running kernel are surfaced; the rest are silently skipped.
         */
        private val BORE_KNOBS = linkedMapOf(
            "kernel.sched_burst_penalty_offset" to Triple(0L, 63L, 24L),
            "kernel.sched_burst_penalty_scale" to Triple(0L, 4095L, 1536L),
            "kernel.sched_burst_smoothness" to Triple(0L, 3L, 1L),
            "kernel.sched_burst_inherit_type" to Triple(0L, 2L, 2L),
            "kernel.sched_burst_cache_lifetime" to Triple(0L, 4294967295L, 75000000L),
            "kernel.sched_burst_protect_slice_lv" to Triple(0L, 3L, 1L),
            "kernel.sched_burst_fork_atavistic" to Triple(0L, 2L, 2L),
        )

        /** Display order of BORE preset profiles. */
        val BORE_PROFILE_ORDER = listOf("balanced", "responsive", "throughput", "battery")

        /**
         * Curated BORE presets: knob key to value (plus [BORE_ENABLE_KEY]).
         *
         * - balanced: upstream defaults, even split of responsiveness/throughput.
         * - responsive: stronger boost for short bursts, harder penalty for CPU
         *   hogs, no score smoothing, no slice protection — lowest input latency.
         * - throughput: milder interactive boost, fairer to CPU-bound tasks,
         *   longer protected slices — best for builds and batch jobs.
         * - battery: calmest scheduling, maximum smoothing and slice
         *   protection — fewer preemptions and migrations.
         */
        val BORE_PROFILES: Map<String, Map<String, Long>> = mapOf(
            "balanced" to mapOf(
                BORE_ENABLE_KEY to 1,
                "kernel.sched_burst_penalty_offset" to 24,
                "kernel.sched_burst_penalty_scale" to 1536,
                "kernel.sched_burst_smoothness" to 1,
                "kernel.sched_burst_inherit_type" to 2,
                "kernel.sched_burst_cache_lifetime" to 75000000,
                "kernel.sched_burst_protect_slice_lv" to 1,
                "kernel.sched_burst_fork_atavistic" to 2,
            ),
            "responsive" to mapOf(
                BORE_ENABLE_KEY to 1,
                "kernel.sched_burst_penalty_offset" to 20,
                "kernel.sched_burst_penalty_scale" to 2048,
                "kernel.sched_burst_smoothness" to 0,
                "kernel.sched_burst_inherit_type" to 2,
                "kernel.sched_burst_cache_lifetime" to 60000000,
                "kernel.sched_burst_protect_slice_lv" to 0,
                "kernel.sched_burst_fork_atavistic" to 2,
            ),
            "throughput" to mapOf(
                BORE_ENABLE_KEY to 1,
                "kernel.sched_burst_penalty_offset" to 28,
                "kernel.sched_burst_penalty_scale" to 1024,
                "kernel.sched_burst_smoothness" to 2,
                "kernel.sched_burst_inherit_type" to 2,
                "kernel.sched_burst_cache_lifetime" to 100000000,
                "kernel.sched_burst_protect_slice_lv" to 2,
                "kernel.sched_burst_fork_atavistic" to 2,
            ),
            "battery" to mapOf(
                BORE_ENABLE_KEY to 1,
                "kernel.sched_burst_penalty_offset" to 28,
                "kernel.sched_burst_penalty_scale" to 896,
                "kernel.sched_burst_smoothness" to 3,
                "kernel.sched_burst_inherit_type" to 2,
                "kernel.sched_burst_cache_lifetime" to 150000000,
                "kernel.sched_burst_protect_slice_lv" to 3,
                "kernel.sched_burst_fork_atavistic" to 2,
            ),
        )

        private const val BOOT_DIR = "/data/adb/boot-completed.d"
        private const val BOOT_SCRIPT = "$BOOT_DIR/origin_tuning.sh"

        private val KEY_PATTERN = Regex("^[A-Za-z0-9_.-]+$")
    }

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(KernelTuningState())
    val state: StateFlow<KernelTuningState> = mutableState.asStateFlow()

    private fun prefs() = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun refresh(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(isRefreshing = !it.isLoading) }
            runCatching {
                val shell = ksuCliRepository.getRootShell()
                val available = ShellUtils.fastCmd(shell, "cat $TCP_AVAILABLE 2>/dev/null")
                    .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val current = ShellUtils.fastCmd(shell, "cat $TCP_CURRENT 2>/dev/null").trim()
                val stored = loadStored()
                // Re-read live values so the UI shows actual kernel state.
                val live = stored.map { entry ->
                    entry.copy(value = readSysctl(entry.key).getOrDefault(entry.value))
                }
                val p = prefs()
                val boreEnabledRaw = readSysctl(BORE_ENABLE_KEY).getOrNull()
                val boreKnobs = if (boreEnabledRaw == null) {
                    emptyList()
                } else {
                    BORE_KNOBS.mapNotNull { (key, range) ->
                        readSysctl(key).getOrNull()?.let { value ->
                            BoreKnob(key, value, range.first, range.second, range.third)
                        }
                    }
                }
                val boreProfileId = if (boreEnabledRaw == null || boreKnobs.isEmpty()) {
                    ""
                } else {
                    val live = boreKnobs.associate { it.key to it.value.trim() } +
                            (BORE_ENABLE_KEY to boreEnabledRaw.trim())
                    BORE_PROFILE_ORDER.firstOrNull { id ->
                        BORE_PROFILES[id]?.all { (key, want) ->
                            live[key]?.let { it == want.toString() } ?: true
                        } == true
                    }.orEmpty()
                }
                val zram = readZramState(p)
                mutableState.value = KernelTuningState(
                    tcpAvailable = available,
                    tcpCurrent = current,
                    tcpPersist = p.getBoolean(KEY_TCP_PERSIST, false),
                    boreSupported = boreEnabledRaw != null,
                    boreEnabled = boreEnabledRaw?.trim() != "0",
                    borePersist = p.getBoolean(KEY_BORE_PERSIST, false),
                    boreKnobs = boreKnobs,
                    boreProfileId = boreProfileId,
                    zram = zram,
                    sysctls = live,
                    isLoading = false,
                    isRefreshing = false,
                )
            }.onFailure {
                Log.w(TAG, "refresh failed", it)
                mutableState.update { current ->
                    current.copy(isLoading = false, isRefreshing = false)
                }
            }
        }
    }

    suspend fun setTcp(name: String, persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val available = mutableState.value.tcpAvailable
                check(name.isNotEmpty()) { "empty algorithm" }
                check(available.isEmpty() || name in available) { "unknown algorithm" }
                check(writeSysctl(TCP_KEY, name)) { "sysctl write failed" }
                prefs().edit()
                    .putBoolean(KEY_TCP_PERSIST, persist)
                    .putString(KEY_TCP_CHOICE, name)
                    .apply()
                syncBootScript()
                mutableState.update {
                    it.copy(tcpCurrent = name, tcpPersist = persist)
                }
            }
        }
    }

    suspend fun setTcpPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                prefs().edit().putBoolean(KEY_TCP_PERSIST, persist).apply()
                if (persist) {
                    prefs().edit()
                        .putString(KEY_TCP_CHOICE, mutableState.value.tcpCurrent)
                        .apply()
                }
                syncBootScript()
                mutableState.update { it.copy(tcpPersist = persist) }
            }
        }
    }

    suspend fun setBoreEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                check(mutableState.value.boreSupported) { "BORE not supported" }
                val value = if (enabled) "1" else "0"
                check(writeSysctl(BORE_ENABLE_KEY, value)) { "sysctl write failed" }
                storeBoreValue(BORE_ENABLE_KEY, value)
                mutableState.update { it.copy(boreEnabled = enabled, boreProfileId = "") }
            }
        }
    }

    suspend fun setBoreKnob(key: String, value: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val range = BORE_KNOBS[key.trim()]
                check(range != null) { "unknown BORE knob" }
                val numeric = value.trim().toLongOrNull()
                check(numeric != null && numeric in range.first..range.second) {
                    "value out of range"
                }
                check(writeSysctl(key, numeric.toString())) { "sysctl write failed" }
                storeBoreValue(key, numeric.toString())
                mutableState.update {
                    it.copy(
                        boreKnobs = it.boreKnobs.map { knob ->
                            if (knob.key == key) knob.copy(value = numeric.toString()) else knob
                        },
                        boreProfileId = "",
                    )
                }
            }
        }
    }

    suspend fun setBorePersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                prefs().edit().putBoolean(KEY_BORE_PERSIST, persist).apply()
                val boreKeys = mutableState.value.boreKnobs.map { it.key } + BORE_ENABLE_KEY
                val stored = loadStored()
                // Backfill live values so newly-enabled persistence captures
                // the whole BORE setup, not just knobs touched this session.
                val backfill = if (persist) {
                    boreKeys.filter { key -> stored.none { it.key == key } }.mapNotNull { key ->
                        readSysctl(key).getOrNull()?.let { SysctlEntry(key, it, true) }
                    }
                } else {
                    emptyList()
                }
                val updated = stored.map {
                    if (it.key in boreKeys) it.copy(persist = persist) else it
                } + backfill
                saveStored(updated)
                syncBootScript()
                mutableState.update { it.copy(borePersist = persist) }
            }
        }
    }

    suspend fun resetBoreDefaults(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                check(mutableState.value.boreSupported) { "BORE not supported" }
                check(writeSysctl(BORE_ENABLE_KEY, "1")) { "sysctl write failed" }
                storeBoreValue(BORE_ENABLE_KEY, "1")
                val knobs = mutableState.value.boreKnobs.map { knob ->
                    val range = BORE_KNOBS[knob.key] ?: return@map knob
                    val def = range.third.toString()
                    check(writeSysctl(knob.key, def)) { "sysctl write failed: ${knob.key}" }
                    storeBoreValue(knob.key, def)
                    knob.copy(value = def)
                }
                mutableState.update { it.copy(boreEnabled = true, boreKnobs = knobs, boreProfileId = "balanced") }
            }
        }
    }

    /**
     * Apply a curated preset: enables BORE and writes every preset value for
     * knobs present on this kernel. Knobs the kernel doesn't expose are
     * skipped so one preset works across BORE generations.
     */
    suspend fun applyBoreProfile(id: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                check(mutableState.value.boreSupported) { "BORE not supported" }
                val profile = BORE_PROFILES[id]
                check(profile != null) { "unknown profile" }
                check(writeSysctl(BORE_ENABLE_KEY, "1")) { "sysctl write failed" }
                storeBoreValue(BORE_ENABLE_KEY, "1")
                val knobs = mutableState.value.boreKnobs.map { knob ->
                    val want = profile[knob.key] ?: return@map knob
                    check(writeSysctl(knob.key, want.toString())) {
                        "sysctl write failed: ${knob.key}"
                    }
                    storeBoreValue(knob.key, want.toString())
                    knob.copy(value = want.toString())
                }
                mutableState.update {
                    it.copy(boreEnabled = true, boreKnobs = knobs, boreProfileId = id)
                }
            }
        }
    }

    /**
     * Reconfigure ZRAM: swapoff, reset, then compression streams, algorithm
     * and size (in that order — the kernel only accepts them on a fresh
     * device), followed by mkswap + swapon. The device node differs per ROM
     * (/dev/block/zram0 vs /dev/zram0), so it is resolved at runtime.
     */
    suspend fun configureZram(sizeBytes: Long, algo: String, streams: Long): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val zram = mutableState.value.zram
                    check(zram.supported) { "ZRAM not supported" }
                    val cleanAlgo = algo.trim()
                    check(cleanAlgo.matches(ALGO_PATTERN)) { "invalid algorithm" }
                    check(zram.algos.isEmpty() || cleanAlgo in zram.algos) {
                        "unknown algorithm"
                    }
                    check(streams in 1..ZRAM_MAX_STREAMS_LIMIT) { "streams out of range" }
                    val maxSize = if (zram.totalRamBytes > 0) zram.totalRamBytes else Long.MAX_VALUE
                    check(sizeBytes in ZRAM_MIN_SIZE..maxSize) { "size out of range" }
                    check(reinitZram(sizeBytes, cleanAlgo, streams)) { "zram reinit failed" }
                    prefs().edit()
                        .putLong(KEY_ZRAM_SIZE, sizeBytes)
                        .putString(KEY_ZRAM_ALGO, cleanAlgo)
                        .putLong(KEY_ZRAM_STREAMS, streams)
                        .apply()
                    syncBootScript()
                    mutableState.update { it.copy(zram = readZramState(prefs())) }
                }
            }
        }

    suspend fun setZramSwappiness(value: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                check(mutableState.value.zram.supported) { "ZRAM not supported" }
                val numeric = value.trim().toLongOrNull()
                check(numeric != null && numeric in 0..200) { "value out of range" }
                upsertSysctl(
                    SWAPPINESS_KEY,
                    numeric.toString(),
                    prefs().getBoolean(KEY_ZRAM_PERSIST, false),
                ).getOrThrow()
                mutableState.update {
                    it.copy(zram = it.zram.copy(swappiness = numeric.toString()))
                }
            }
        }
    }

    suspend fun setZramPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val p = prefs()
                p.edit().putBoolean(KEY_ZRAM_PERSIST, persist).apply()
                if (persist && !p.contains(KEY_ZRAM_SIZE)) {
                    // Backfill live config so enabling persistence captures the
                    // current setup, not just values changed afterwards.
                    val live = mutableState.value.zram
                    if (live.supported && live.disksizeBytes > 0) {
                        p.edit()
                            .putLong(KEY_ZRAM_SIZE, live.disksizeBytes)
                            .putString(KEY_ZRAM_ALGO, live.currentAlgo)
                            .putLong(KEY_ZRAM_STREAMS, live.maxStreams)
                            .apply()
                    }
                }
                val updated = loadStored().map {
                    if (it.key == SWAPPINESS_KEY) it.copy(persist = persist) else it
                }
                saveStored(updated)
                syncBootScript()
                mutableState.update { it.copy(zram = it.zram.copy(persist = persist)) }
            }
        }
    }

    private fun readZramState(prefs: android.content.SharedPreferences): ZramState {
        val shell = ksuCliRepository.getRootShell()
        val disksize = ShellUtils.fastCmd(shell, "cat $ZRAM_DISKSIZE 2>/dev/null")
            .trim().toLongOrNull() ?: return ZramState()
        val algoRaw = ShellUtils.fastCmd(shell, "cat $ZRAM_COMP_ALGO 2>/dev/null").trim()
        if (algoRaw.isEmpty()) return ZramState()
        val tokens = algoRaw.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val current = tokens.firstOrNull { it.startsWith("[") && it.endsWith("]") }
            ?.removeSurrounding("[", "]").orEmpty()
        val algos = tokens.map { it.removeSurrounding("[", "]") }
        val streams = ShellUtils.fastCmd(shell, "cat $ZRAM_MAX_STREAMS 2>/dev/null")
            .trim().toLongOrNull() ?: 0L
        val mmStat = ShellUtils.fastCmd(shell, "cat $ZRAM_MM_STAT 2>/dev/null")
            .trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        val memTotalKb = ShellUtils.fastCmd(
            shell,
            "awk '/^MemTotal:/{print \$2}' /proc/meminfo 2>/dev/null",
        ).trim().toLongOrNull() ?: 0L
        val swappiness = readSysctl(SWAPPINESS_KEY).getOrDefault("")
        return ZramState(
            supported = true,
            disksizeBytes = disksize,
            totalRamBytes = memTotalKb * 1024,
            currentAlgo = current,
            algos = algos,
            maxStreams = streams,
            origBytes = mmStat.getOrElse(0) { 0L },
            comprBytes = mmStat.getOrElse(1) { 0L },
            memUsedBytes = mmStat.getOrElse(2) { 0L },
            swappiness = swappiness,
            persist = prefs.getBoolean(KEY_ZRAM_PERSIST, false),
        )
    }

    /** Runs the full swapoff/reset/setup/mkswap/swapon cycle; true on success. */
    private fun reinitZram(sizeBytes: Long, algo: String, streams: Long): Boolean {
        return runCatching {
            val shell = ksuCliRepository.getRootShell()
            val detect = "if [ -e /dev/block/zram0 ]; then Z=/dev/block/zram0; " +
                    "else Z=/dev/zram0; fi"
            val script = "$detect && " +
                    "swapoff \"\$Z\" 2>/dev/null; " +
                    "echo 1 > $ZRAM_SYSFS/reset && " +
                    "echo '$streams' > $ZRAM_MAX_STREAMS && " +
                    "echo '$algo' > $ZRAM_COMP_ALGO && " +
                    "echo '$sizeBytes' > $ZRAM_DISKSIZE && " +
                    "mkswap \"\$Z\" >/dev/null 2>&1 && " +
                    "swapon \"\$Z\" >/dev/null 2>&1"
            if (!ShellUtils.fastCmdResult(shell, script)) return false
            // Verify the kernel actually accepted the new configuration.
            val liveSize = ShellUtils.fastCmd(shell, "cat $ZRAM_DISKSIZE 2>/dev/null")
                .trim().toLongOrNull()
            val liveAlgo = ShellUtils.fastCmd(shell, "cat $ZRAM_COMP_ALGO 2>/dev/null").trim()
            liveSize == sizeBytes && liveAlgo.contains("[$algo]")
        }.getOrDefault(false)
    }

    /**
     * Mirror a BORE value into the stored sysctl list (honoring the BORE
     * persist flag) and regenerate the boot script.
     */
    private fun storeBoreValue(key: String, value: String) {
        val persist = prefs().getBoolean(KEY_BORE_PERSIST, false)
        val updated = loadStored().filterNot { it.key == key } +
                SysctlEntry(key, value, persist)
        saveStored(updated)
        syncBootScript()
    }

    suspend fun addOrUpdateSysctl(key: String, value: String, persist: Boolean): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                upsertSysctl(key, value, persist)
            }
        }

    /**
     * Same as [addOrUpdateSysctl] but assumes [mutex] is already held, so ZRAM
     * and other composite operations can reuse it without deadlocking.
     */
    private suspend fun upsertSysctl(key: String, value: String, persist: Boolean): Result<Unit> =
        runCatching {
            val cleanKey = key.trim()
            check(cleanKey.matches(KEY_PATTERN)) { "invalid key" }
            check(!value.contains('\n') && !value.contains('\'')) { "invalid value" }
            check(writeSysctl(cleanKey, value)) { "sysctl write failed" }
            val updated = loadStored()
                .filterNot { it.key == cleanKey } + SysctlEntry(cleanKey, value, persist)
            saveStored(updated)
            syncBootScript()
            mutableState.update {
                it.copy(sysctls = it.sysctls.filterNot { e -> e.key == cleanKey } +
                        SysctlEntry(cleanKey, value, persist))
            }
        }

    suspend fun removeSysctl(key: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                saveStored(loadStored().filterNot { it.key == key })
                syncBootScript()
                mutableState.update { it.copy(sysctls = it.sysctls.filterNot { e -> e.key == key }) }
            }
        }
    }

    suspend fun setSysctlPersist(key: String, persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val updated = loadStored().map {
                    if (it.key == key) it.copy(persist = persist) else it
                }
                saveStored(updated)
                syncBootScript()
                mutableState.update {
                    it.copy(sysctls = it.sysctls.map { e ->
                        if (e.key == key) e.copy(persist = persist) else e
                    })
                }
            }
        }
    }

    private fun readSysctl(key: String): Result<String> = runCatching {
        val path = "/proc/sys/" + key.replace('.', '/')
        val shell = ksuCliRepository.getRootShell()
        val out = ShellUtils.fastCmd(shell, "cat '$path' 2>/dev/null").trim()
        check(out.isNotEmpty()) { "unreadable" }
        out
    }

    private fun writeSysctl(key: String, value: String): Boolean = runCatching {
        val shell = ksuCliRepository.getRootShell()
        ShellUtils.fastCmdResult(shell, "sysctl -w '$key'='$value' >/dev/null 2>&1")
    }.getOrDefault(false)

    private fun loadStored(): List<SysctlEntry> {
        val raw = prefs().getString(KEY_SYSCTLS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SysctlEntry(
                    key = o.getString("key"),
                    value = o.getString("value"),
                    persist = o.optBoolean("persist", false),
                )
            }.filter { it.key.matches(KEY_PATTERN) }
        }.getOrDefault(emptyList())
    }

    private fun saveStored(entries: List<SysctlEntry>) {
        val array = JSONArray()
        entries.sortedBy(SysctlEntry::key).forEach {
            array.put(JSONObject().put("key", it.key).put("value", it.value).put("persist", it.persist))
        }
        prefs().edit().putString(KEY_SYSCTLS, array.toString()).apply()
    }

    /**
     * Regenerate (or remove, when nothing persists) the boot-completed script.
     * Must be called with root available, on a background thread.
     */
    private fun syncBootScript() {
        val lines = mutableListOf<String>()
        val p = prefs()
        if (p.getBoolean(KEY_TCP_PERSIST, false)) {
            val choice = p.getString(KEY_TCP_CHOICE, "").orEmpty()
            if (choice.isNotEmpty()) lines.add("sysctl -w '$TCP_KEY'='$choice'")
        }
        loadStored().filter { it.persist }.forEach {
            lines.add("sysctl -w '${it.key}'='${it.value}'")
        }
        if (p.getBoolean(KEY_ZRAM_PERSIST, false)) {
            val size = p.getLong(KEY_ZRAM_SIZE, 0)
            val algo = p.getString(KEY_ZRAM_ALGO, "").orEmpty()
            val streams = p.getLong(KEY_ZRAM_STREAMS, 0)
            if (size > 0 && algo.matches(ALGO_PATTERN) && streams in 1..ZRAM_MAX_STREAMS_LIMIT) {
                lines.add("if [ -e /dev/block/zram0 ]; then ZRAM_DEV=/dev/block/zram0; " +
                        "else ZRAM_DEV=/dev/zram0; fi")
                lines.add("swapoff \"\$ZRAM_DEV\" 2>/dev/null")
                lines.add("echo 1 > $ZRAM_SYSFS/reset")
                lines.add("echo '$streams' > $ZRAM_MAX_STREAMS")
                lines.add("echo '$algo' > $ZRAM_COMP_ALGO")
                lines.add("echo '$size' > $ZRAM_DISKSIZE")
                lines.add("mkswap \"\$ZRAM_DEV\" >/dev/null 2>&1")
                lines.add("swapon \"\$ZRAM_DEV\" >/dev/null 2>&1")
            }
        }
        val shell = ksuCliRepository.getRootShell()
        if (lines.isEmpty()) {
            ShellUtils.fastCmd(shell, "rm -f '$BOOT_SCRIPT' 2>/dev/null; true")
            return
        }
        val body = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Generated by OriginSU kernel tuning — do not edit manually.")
            lines.forEach { appendLine(it) }
        }
        // Single-quoted heredoc: body never contains a single quote (validated above).
        val cmd = "mkdir -p '$BOOT_DIR' && cat > '$BOOT_SCRIPT' <<'ORIGINSU_EOF'\n" +
                body + "ORIGINSU_EOF\nchmod 755 '$BOOT_SCRIPT'"
        ShellUtils.fastCmd(shell, cmd)
    }
}
