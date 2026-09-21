package com.originsu.manager.data.kernel

import android.app.Application
import android.content.Context
import android.util.Log
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.BoreKnob
import com.originsu.manager.domain.model.CpuPolicy
import com.originsu.manager.domain.model.CpuState
import com.originsu.manager.domain.model.DiagnosticsState
import com.originsu.manager.domain.model.GpuDevice
import com.originsu.manager.domain.model.GpuState
import com.originsu.manager.domain.model.IoDevice
import com.originsu.manager.domain.model.IoState
import com.originsu.manager.domain.model.KernelTuningState
import com.originsu.manager.domain.model.LmkLevel
import com.originsu.manager.domain.model.LmkState
import com.originsu.manager.domain.model.PsiStats
import com.originsu.manager.domain.model.SchedKnob
import com.originsu.manager.domain.model.SchedState
import com.originsu.manager.domain.model.VmKnob
import com.originsu.manager.domain.model.VmState
import com.originsu.manager.domain.model.SysctlEntry
import com.originsu.manager.domain.model.ZramState
import com.topjohnwu.superuser.Shell
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
import java.io.File

/**
 * Kernel tunables: CPU frequency, GPU clocks, block I/O, VM presets,
 * scheduler extras, LMK levels, TCP congestion control, BORE scheduler,
 * ZRAM and a generic sysctl editor.
 *
 * Live values are read/written as root via sysfs, system properties and
 * `sysctl`.
 * Entries flagged [SysctlEntry.persist] (plus the TCP choice when
 * [KernelTuningState.tcpPersist] is set, plus BORE values when
 * [KernelTuningState.borePersist] is set, plus VM values when
 * [VmState.persist] is set, plus scheduler values when
 * [SchedState.persist] is set, plus the LMK setup when
 * [LmkState.persist] is set, plus the ZRAM setup when
 * [ZramState.persist] is set, plus the CPU setup when
 * [CpuState.persist] is set, plus the GPU setup when
 * [GpuState.persist] is set, plus the I/O setup when
 * [IoState.persist] is set) are re-applied at every boot through a
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
        private const val KEY_CPU_PERSIST = "cpu_persist"
        private const val KEY_CPU_JSON = "cpu_json"
        private const val KEY_GPU_PERSIST = "gpu_persist"
        private const val KEY_GPU_JSON = "gpu_json"
        private const val KEY_IO_PERSIST = "io_persist"
        private const val KEY_IO_JSON = "io_json"
        private const val KEY_VM_PERSIST = "vm_persist"
        private const val KEY_SCHED_PERSIST = "sched_persist"
        private const val KEY_LMK_PERSIST = "lmk_persist"
        private const val KEY_LMK_JSON = "lmk_json"
        private const val KEY_LMK_STOCK = "lmk_stock"

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

    /** Shell fragment collapsing multi-line output to one space-separated line. */
        private const val ONE_LINE = " | tr '\\n' ' '"

        private const val CPUFREQ_BASE = "/sys/devices/system/cpu/cpufreq"
        private const val CPU_SYS_BASE = "/sys/devices/system/cpu"
        private const val SCHEDUTIL_GLOBAL_RATE = "$CPUFREQ_BASE/schedutil/rate_limit_us"

        private const val KGSL_BASE = "/sys/class/kgsl"
        private const val KGSL_NODE = "kgsl-3d0"
        private const val DEVFREQ_BASE = "/sys/class/devfreq"
        private const val BLOCK_BASE = "/sys/block"

        /** devfreq `name` fragments identifying a GPU clock domain. */
        private val GPU_NAME_KEYWORDS = listOf("mali", "gpu", "kgsl", "adreno", "v3d", "3d0")

        /** Block devices never surfaced for tuning (virtual/mapped). */
        private val IO_SKIP_PREFIXES = listOf("loop", "ram", "zram", "dm-")

        /** Upper bound accepted for read-ahead (32 MB, in kB). */
        private const val READ_AHEAD_MAX_KB = 32768L

        private val SCHEDULER_PATTERN = Regex("^[A-Za-z0-9_.-]+$")

        private val GOVERNOR_PATTERN = Regex("^[A-Za-z0-9_-]+$")

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

        /** Display order of VM preset profiles. */
        val VM_PROFILE_ORDER = listOf("balanced", "responsive", "throughput", "battery")

        /**
         * Known VM tunables: key to (min, max, default). Conservative bounds
         * across kernel generations; only knobs readable on the running
         * kernel are surfaced, the rest are silently skipped.
         */
        private val VM_KNOBS = linkedMapOf(
            "vm.swappiness" to Triple(0L, 200L, 60L),
            "vm.dirty_ratio" to Triple(0L, 100L, 20L),
            "vm.dirty_background_ratio" to Triple(0L, 100L, 10L),
            "vm.dirty_expire_centisecs" to Triple(0L, 60000L, 3000L),
            "vm.dirty_writeback_centisecs" to Triple(0L, 60000L, 500L),
            "vm.vfs_cache_pressure" to Triple(0L, 1000L, 100L),
            "vm.overcommit_memory" to Triple(0L, 2L, 0L),
        )

        /**
         * Curated VM presets, in the style of the BORE profiles:
         *
         * - balanced: upstream defaults.
         * - responsive: reclaim eagerly, write back promptly, keep dentry
         *   caches hot — snappier app switching and gaming.
         * - throughput: roomy write buffers and lazy writeback — best for
         *   builds and batch jobs.
         * - battery: large dirty windows and calm writeback — fewer disk
         *   wakeups.
         */
        val VM_PROFILES: Map<String, Map<String, Long>> = mapOf(
            "balanced" to mapOf(
                "vm.swappiness" to 60,
                "vm.dirty_ratio" to 20,
                "vm.dirty_background_ratio" to 10,
                "vm.dirty_expire_centisecs" to 3000,
                "vm.dirty_writeback_centisecs" to 500,
                "vm.vfs_cache_pressure" to 100,
                "vm.overcommit_memory" to 0,
            ),
            "responsive" to mapOf(
                "vm.swappiness" to 80,
                "vm.dirty_ratio" to 10,
                "vm.dirty_background_ratio" to 5,
                "vm.dirty_expire_centisecs" to 2000,
                "vm.dirty_writeback_centisecs" to 300,
                "vm.vfs_cache_pressure" to 80,
                "vm.overcommit_memory" to 0,
            ),
            "throughput" to mapOf(
                "vm.swappiness" to 30,
                "vm.dirty_ratio" to 30,
                "vm.dirty_background_ratio" to 15,
                "vm.dirty_expire_centisecs" to 5000,
                "vm.dirty_writeback_centisecs" to 1000,
                "vm.vfs_cache_pressure" to 100,
                "vm.overcommit_memory" to 0,
            ),
            "battery" to mapOf(
                "vm.swappiness" to 40,
                "vm.dirty_ratio" to 40,
                "vm.dirty_background_ratio" to 20,
                "vm.dirty_expire_centisecs" to 6000,
                "vm.dirty_writeback_centisecs" to 1500,
                "vm.vfs_cache_pressure" to 120,
                "vm.overcommit_memory" to 0,
            ),
        )

        /**
         * Scheduler-extra tunables (uclamp / energy-aware): key to
         * (min, max, default). Only knobs readable on the running kernel
         * are surfaced; the rest are silently skipped. uclamp values are in
         * permille of capacity (0–1024).
         */
        private val SCHED_KNOBS = linkedMapOf(
            "kernel.sched_util_clamp_min" to Triple(0L, 1024L, 1024L),
            "kernel.sched_util_clamp_max" to Triple(0L, 1024L, 1024L),
            "kernel.sched_util_clamp_min_rt_default" to Triple(0L, 1024L, 1024L),
            "kernel.sched_energy_aware" to Triple(0L, 1L, 1L),
        )

        private const val UCLAMP_MIN_KEY = "kernel.sched_util_clamp_min"
        private const val UCLAMP_MAX_KEY = "kernel.sched_util_clamp_max"

        /** Display order of LMK preset profiles. */
        val LMK_PROFILE_ORDER = listOf("balanced", "multitasking", "aggressive")

        /**
         * Curated LMK presets as per-level free-memory thresholds in MB
         * (converted to 4K pages at apply time). The stock 6-level table on
         * most devices is 72/90/108/126/216/315 MB, which is the balanced
         * preset here.
         */
        val LMK_PROFILES_MB: Map<String, List<Long>> = mapOf(
            "balanced" to listOf(72, 90, 108, 126, 216, 315),
            "multitasking" to listOf(36, 45, 54, 63, 108, 160),
            "aggressive" to listOf(144, 180, 216, 252, 432, 630),
        )

        /** 4K pages per megabyte. */
        private const val PAGES_PER_MB = 256L

        private const val LMK_PROP = "sys.lmk.minfree_levels"
        private const val LMK_MINFREE_SYSFS = "/sys/module/lowmemorykiller/parameters/minfree"
        private const val LMK_ADJ_SYSFS = "/sys/module/lowmemorykiller/parameters/adj"

        private const val BOOT_DIR = "/data/adb/boot-completed.d"
        private const val BOOT_SCRIPT = "$BOOT_DIR/origin_tuning.sh"

        /** Schema version for exported tuning profiles. */
        private const val PROFILE_VERSION = 1

        private const val PROFILE_BACKUP_DIR = "profile_backups"

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
                // One root shell per refresh, closed on the way out: spawning
                // a shell per read costs hundreds of `su` forks per refresh.
                ksuCliRepository.createRootShell().use { shell ->
                val available = ShellUtils.fastCmd(shell, "cat $TCP_AVAILABLE 2>/dev/null")
                    .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val current = ShellUtils.fastCmd(shell, "cat $TCP_CURRENT 2>/dev/null").trim()
                val stored = loadStored()
                // Re-read live values so the UI shows actual kernel state.
                val live = stored.map { entry ->
                    entry.copy(value = readSysctl(entry.key, shell).getOrDefault(entry.value))
                }
                val p = prefs()
                val boreEnabledRaw = readSysctl(BORE_ENABLE_KEY, shell).getOrNull()
                val boreKnobs = if (boreEnabledRaw == null) {
                    emptyList()
                } else {
                    BORE_KNOBS.mapNotNull { (key, range) ->
                        readSysctl(key, shell).getOrNull()?.let { value ->
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
                val zram = readZramState(p, shell)
                val cpu = readCpuState(p, shell)
                val gpu = readGpuState(p, shell)
                val io = readIoState(p, shell)
                val vmKnobs = VM_KNOBS.mapNotNull { (key, range) ->
                    readSysctl(key, shell).getOrNull()?.let { value ->
                        VmKnob(key, value, range.first, range.second, range.third)
                    }
                }
                val vmProfileId = if (vmKnobs.isEmpty()) {
                    ""
                } else {
                    val live = vmKnobs.associate { it.key to it.value.trim() }
                    VM_PROFILE_ORDER.firstOrNull { id ->
                        VM_PROFILES[id]?.all { (key, want) ->
                            live[key]?.let { it == want.toString() } ?: true
                        } == true
                    }.orEmpty()
                }
                val vm = VmState(
                    supported = vmKnobs.isNotEmpty(),
                    persist = p.getBoolean(KEY_VM_PERSIST, false),
                    knobs = vmKnobs,
                    profileId = vmProfileId,
                )
                val schedKnobs = SCHED_KNOBS.mapNotNull { (key, range) ->
                    readSysctl(key, shell).getOrNull()?.let { value ->
                        SchedKnob(key, value, range.first, range.second, range.third)
                    }
                }
                val sched = SchedState(
                    supported = schedKnobs.isNotEmpty(),
                    persist = p.getBoolean(KEY_SCHED_PERSIST, false),
                    knobs = schedKnobs,
                )
                val lmk = readLmkState(p, shell)
                val diagnostics = readDiagnosticsState(shell)
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
                    cpu = cpu,
                    gpu = gpu,
                    io = io,
                    vm = vm,
                    sched = sched,
                    lmk = lmk,
                    diagnostics = diagnostics,
                    sysctls = live,
                    isLoading = false,
                    isRefreshing = false,
                )
                }
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
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val available = mutableState.value.tcpAvailable
                check(name.isNotEmpty()) { "empty algorithm" }
                check(available.isEmpty() || name in available) { "unknown algorithm" }
                check(writeSysctl(TCP_KEY, name, shell)) { "sysctl write failed" }
                prefs().edit()
                    .putBoolean(KEY_TCP_PERSIST, persist)
                    .putString(KEY_TCP_CHOICE, name)
                    .apply()
                syncBootScript(shell)
                mutableState.update {
                    it.copy(tcpCurrent = name, tcpPersist = persist)
                }
            }
            }
        }
    }

    suspend fun setTcpPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                prefs().edit().putBoolean(KEY_TCP_PERSIST, persist).apply()
                if (persist) {
                    prefs().edit()
                        .putString(KEY_TCP_CHOICE, mutableState.value.tcpCurrent)
                        .apply()
                }
                syncBootScript(shell)
                mutableState.update { it.copy(tcpPersist = persist) }
            }
            }
        }
    }

    suspend fun setBoreEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.boreSupported) { "BORE not supported" }
                val value = if (enabled) "1" else "0"
                check(writeSysctl(BORE_ENABLE_KEY, value, shell)) { "sysctl write failed" }
                storeBoreValue(BORE_ENABLE_KEY, value, shell)
                mutableState.update { it.copy(boreEnabled = enabled, boreProfileId = "") }
            }
            }
        }
    }

    suspend fun setBoreKnob(key: String, value: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val range = BORE_KNOBS[key.trim()]
                check(range != null) { "unknown BORE knob" }
                val numeric = value.trim().toLongOrNull()
                check(numeric != null && numeric in range.first..range.second) {
                    "value out of range"
                }
                check(writeSysctl(key, numeric.toString(), shell)) { "sysctl write failed" }
                storeBoreValue(key, numeric.toString(), shell)
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
    }

    suspend fun setBorePersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                prefs().edit().putBoolean(KEY_BORE_PERSIST, persist).apply()
                val boreKeys = mutableState.value.boreKnobs.map { it.key } + BORE_ENABLE_KEY
                val stored = loadStored()
                // Backfill live values so newly-enabled persistence captures
                // the whole BORE setup, not just knobs touched this session.
                val backfill = if (persist) {
                    boreKeys.filter { key -> stored.none { it.key == key } }.mapNotNull { key ->
                        readSysctl(key, shell).getOrNull()?.let { SysctlEntry(key, it, true) }
                    }
                } else {
                    emptyList()
                }
                val updated = stored.map {
                    if (it.key in boreKeys) it.copy(persist = persist) else it
                } + backfill
                saveStored(updated)
                syncBootScript(shell)
                mutableState.update { it.copy(borePersist = persist) }
            }
            }
        }
    }

    suspend fun resetBoreDefaults(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.boreSupported) { "BORE not supported" }
                check(writeSysctl(BORE_ENABLE_KEY, "1", shell)) { "sysctl write failed" }
                storeBoreValue(BORE_ENABLE_KEY, "1", shell)
                val knobs = mutableState.value.boreKnobs.map { knob ->
                    val range = BORE_KNOBS[knob.key] ?: return@map knob
                    val def = range.third.toString()
                    check(writeSysctl(knob.key, def, shell)) { "sysctl write failed: ${knob.key}" }
                    storeBoreValue(knob.key, def, shell)
                    knob.copy(value = def)
                }
                mutableState.update { it.copy(boreEnabled = true, boreKnobs = knobs, boreProfileId = "balanced") }
            }
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
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.boreSupported) { "BORE not supported" }
                val profile = BORE_PROFILES[id]
                check(profile != null) { "unknown profile" }
                check(writeSysctl(BORE_ENABLE_KEY, "1", shell)) { "sysctl write failed" }
                storeBoreValue(BORE_ENABLE_KEY, "1", shell)
                val knobs = mutableState.value.boreKnobs.map { knob ->
                    val want = profile[knob.key] ?: return@map knob
                    check(writeSysctl(knob.key, want.toString(), shell)) {
                        "sysctl write failed: ${knob.key}"
                    }
                    storeBoreValue(knob.key, want.toString(), shell)
                    knob.copy(value = want.toString())
                }
                mutableState.update {
                    it.copy(boreEnabled = true, boreKnobs = knobs, boreProfileId = id)
                }
            }
            }
        }
    }

    suspend fun setVmKnob(key: String, value: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val range = VM_KNOBS[key.trim()]
                check(range != null) { "unknown VM knob" }
                val numeric = value.trim().toLongOrNull()
                check(numeric != null && numeric in range.first..range.second) {
                    "value out of range"
                }
                check(writeSysctl(key, numeric.toString(), shell)) { "sysctl write failed" }
                storeVmValue(key, numeric.toString(), shell)
                mutableState.update {
                    it.copy(
                        vm = it.vm.copy(
                            knobs = it.vm.knobs.map { knob ->
                                if (knob.key == key) knob.copy(value = numeric.toString()) else knob
                            },
                            profileId = "",
                        ),
                    )
                }
            }
            }
        }
    }

    suspend fun setVmPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                prefs().edit().putBoolean(KEY_VM_PERSIST, persist).apply()
                val vmKeys = mutableState.value.vm.knobs.map { it.key }
                val stored = loadStored()
                // Backfill live values so newly-enabled persistence captures
                // the whole VM setup, not just knobs touched this session.
                val backfill = if (persist) {
                    vmKeys.filter { key -> stored.none { it.key == key } }.mapNotNull { key ->
                        readSysctl(key, shell).getOrNull()?.let { SysctlEntry(key, it, true) }
                    }
                } else {
                    emptyList()
                }
                val updated = stored.map {
                    if (it.key in vmKeys) it.copy(persist = persist) else it
                } + backfill
                saveStored(updated)
                syncBootScript(shell)
                mutableState.update { it.copy(vm = it.vm.copy(persist = persist)) }
            }
            }
        }
    }

    suspend fun resetVmDefaults(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.vm.supported) { "VM not supported" }
                val knobs = mutableState.value.vm.knobs.map { knob ->
                    val range = VM_KNOBS[knob.key] ?: return@map knob
                    val def = range.third.toString()
                    check(writeSysctl(knob.key, def, shell)) { "sysctl write failed: ${knob.key}" }
                    storeVmValue(knob.key, def, shell)
                    knob.copy(value = def)
                }
                mutableState.update { it.copy(vm = it.vm.copy(knobs = knobs, profileId = "balanced")) }
            }
            }
        }
    }

    /**
     * Apply a curated preset: writes every preset value for knobs present
     * on this kernel. Knobs the kernel doesn't expose are skipped so one
     * preset works across kernel generations.
     */
    suspend fun applyVmProfile(id: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.vm.supported) { "VM not supported" }
                val profile = VM_PROFILES[id]
                check(profile != null) { "unknown profile" }
                val knobs = mutableState.value.vm.knobs.map { knob ->
                    val want = profile[knob.key] ?: return@map knob
                    check(writeSysctl(knob.key, want.toString(), shell)) {
                        "sysctl write failed: ${knob.key}"
                    }
                    storeVmValue(knob.key, want.toString(), shell)
                    knob.copy(value = want.toString())
                }
                mutableState.update {
                    it.copy(vm = it.vm.copy(knobs = knobs, profileId = id))
                }
            }
            }
        }
    }

    /**
     * Set a scheduler-extra knob. uclamp min/max are cross-checked against
     * each other's live value so the kernel never transiently sees
     * min > max.
     */
    suspend fun setSchedKnob(key: String, value: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val range = SCHED_KNOBS[key.trim()]
                check(range != null) { "unknown scheduler knob" }
                val numeric = value.trim().toLongOrNull()
                check(numeric != null && numeric in range.first..range.second) {
                    "value out of range"
                }
                if (key == UCLAMP_MIN_KEY) {
                    val liveMax = mutableState.value.sched.knobs
                        .firstOrNull { it.key == UCLAMP_MAX_KEY }
                        ?.value?.trim()?.toLongOrNull()
                    check(liveMax == null || numeric <= liveMax) { "min must not exceed max" }
                }
                if (key == UCLAMP_MAX_KEY) {
                    val liveMin = mutableState.value.sched.knobs
                        .firstOrNull { it.key == UCLAMP_MIN_KEY }
                        ?.value?.trim()?.toLongOrNull()
                    check(liveMin == null || numeric >= liveMin) { "max must not be below min" }
                }
                check(writeSysctl(key, numeric.toString(), shell)) { "sysctl write failed" }
                storeSchedValue(key, numeric.toString(), shell)
                mutableState.update {
                    it.copy(
                        sched = it.sched.copy(
                            knobs = it.sched.knobs.map { knob ->
                                if (knob.key == key) knob.copy(value = numeric.toString()) else knob
                            },
                        ),
                    )
                }
            }
            }
        }
    }

    suspend fun setSchedPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                prefs().edit().putBoolean(KEY_SCHED_PERSIST, persist).apply()
                val schedKeys = mutableState.value.sched.knobs.map { it.key }
                val stored = loadStored()
                // Backfill live values so newly-enabled persistence captures
                // the whole scheduler setup, not just knobs touched this session.
                val backfill = if (persist) {
                    schedKeys.filter { key -> stored.none { it.key == key } }.mapNotNull { key ->
                        readSysctl(key, shell).getOrNull()?.let { SysctlEntry(key, it, true) }
                    }
                } else {
                    emptyList()
                }
                val updated = stored.map {
                    if (it.key in schedKeys) it.copy(persist = persist) else it
                } + backfill
                saveStored(updated)
                syncBootScript(shell)
                mutableState.update { it.copy(sched = it.sched.copy(persist = persist)) }
            }
            }
        }
    }

    suspend fun resetSchedDefaults(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.sched.supported) { "scheduler not supported" }
                // Order matters: raise max before min so min never exceeds max.
                val ordered = mutableState.value.sched.knobs.sortedBy {
                    if (it.key == UCLAMP_MAX_KEY) 0 else 1
                }
                val updated = mutableMapOf<String, String>()
                ordered.forEach { knob ->
                    val range = SCHED_KNOBS[knob.key] ?: return@forEach
                    val def = range.third.toString()
                    check(writeSysctl(knob.key, def, shell)) { "sysctl write failed: ${knob.key}" }
                    storeSchedValue(knob.key, def, shell)
                    updated[knob.key] = def
                }
                mutableState.update {
                    it.copy(
                        sched = it.sched.copy(
                            knobs = it.sched.knobs.map { knob ->
                                updated[knob.key]?.let { knob.copy(value = it) } ?: knob
                            },
                        ),
                    )
                }
            }
            }
        }
    }

    /**
     * Mirror a scheduler value into the stored sysctl list (honoring the
     * scheduler persist flag) and regenerate the boot script.
     */
    private fun storeSchedValue(key: String, value: String, shell: Shell) {
        val persist = prefs().getBoolean(KEY_SCHED_PERSIST, false)
        val updated = loadStored().filterNot { it.key == key } +
                SysctlEntry(key, value, persist)
        saveStored(updated)
        syncBootScript(shell)
    }

    /**
     * Mirror a VM value into the stored sysctl list (honoring the VM
     * persist flag) and regenerate the boot script.
     */
    private fun storeVmValue(key: String, value: String, shell: Shell) {
        val persist = prefs().getBoolean(KEY_VM_PERSIST, false)
        val updated = loadStored().filterNot { it.key == key } +
                SysctlEntry(key, value, persist)
        saveStored(updated)
        syncBootScript(shell)
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
                ksuCliRepository.createRootShell().use { shell ->
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
                    check(reinitZram(sizeBytes, cleanAlgo, streams, shell)) { "zram reinit failed" }
                    prefs().edit()
                        .putLong(KEY_ZRAM_SIZE, sizeBytes)
                        .putString(KEY_ZRAM_ALGO, cleanAlgo)
                        .putLong(KEY_ZRAM_STREAMS, streams)
                        .apply()
                    syncBootScript(shell)
                    mutableState.update { it.copy(zram = readZramState(prefs(), shell)) }
                }
            }
            }
        }

    suspend fun setZramSwappiness(value: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.zram.supported) { "ZRAM not supported" }
                val numeric = value.trim().toLongOrNull()
                check(numeric != null && numeric in 0..200) { "value out of range" }
                upsertSysctl(
                    SWAPPINESS_KEY,
                    numeric.toString(),
                    prefs().getBoolean(KEY_ZRAM_PERSIST, false),
                    shell,
                ).getOrThrow()
                mutableState.update {
                    it.copy(zram = it.zram.copy(swappiness = numeric.toString()))
                }
            }
            }
        }
    }

    suspend fun setZramPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
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
                syncBootScript(shell)
                mutableState.update { it.copy(zram = it.zram.copy(persist = persist)) }
            }
            }
        }
    }

    suspend fun setCpuGovernor(policyId: String, governor: String): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                ksuCliRepository.createRootShell().use { shell ->
                runCatching {
                    val policy = mutableState.value.cpu.policies.firstOrNull { it.id == policyId }
                    check(policy != null) { "unknown CPU policy" }
                    val clean = governor.trim()
                    check(clean.matches(GOVERNOR_PATTERN)) { "invalid governor" }
                    check(policy.availableGovernors.isEmpty() || clean in policy.availableGovernors) {
                        "unknown governor"
                    }
                    val dir = cpuPolicyDir(policyId)
                    check(writeSysfs("$dir/scaling_governor", clean, shell)) { "cpufreq write failed" }
                    storeCpuValue(policyId, governor = clean, shell = shell)
                    mutableState.update {
                        it.copy(
                            cpu = it.cpu.copy(
                                policies = it.cpu.policies.map { p ->
                                    if (p.id == policyId) p.copy(governor = clean) else p
                                },
                            ),
                        )
                    }
                }
            }
            }
        }

    /**
     * Set a policy's min/max frequency (kHz). Writes the bound that moves
     * away from the current window first so the kernel never transiently
     * sees min > max.
     */
    suspend fun setCpuFreqs(policyId: String, minKhz: Long, maxKhz: Long): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                ksuCliRepository.createRootShell().use { shell ->
                runCatching {
                    val policy = mutableState.value.cpu.policies.firstOrNull { it.id == policyId }
                    check(policy != null) { "unknown CPU policy" }
                    check(minKhz > 0 && maxKhz > 0 && minKhz <= maxKhz) {
                        "min must not exceed max"
                    }
                    if (policy.cpuinfoMinKhz > 0 && policy.cpuinfoMaxKhz > 0) {
                        check(minKhz >= policy.cpuinfoMinKhz && maxKhz <= policy.cpuinfoMaxKhz) {
                            "frequency out of range"
                        }
                    }
                    val dir = cpuPolicyDir(policyId)
                    val minFirst = maxKhz < policy.minFreqKhz
                    if (minFirst) {
                        check(writeSysfs("$dir/scaling_min_freq", minKhz.toString(), shell)) {
                            "cpufreq write failed"
                        }
                        check(writeSysfs("$dir/scaling_max_freq", maxKhz.toString(), shell)) {
                            "cpufreq write failed"
                        }
                    } else {
                        check(writeSysfs("$dir/scaling_max_freq", maxKhz.toString(), shell)) {
                            "cpufreq write failed"
                        }
                        check(writeSysfs("$dir/scaling_min_freq", minKhz.toString(), shell)) {
                            "cpufreq write failed"
                        }
                    }
                    storeCpuValue(policyId, minKhz = minKhz, maxKhz = maxKhz, shell = shell)
                    mutableState.update {
                        it.copy(
                            cpu = it.cpu.copy(
                                policies = it.cpu.policies.map { p ->
                                    if (p.id == policyId) {
                                        p.copy(minFreqKhz = minKhz, maxFreqKhz = maxKhz)
                                    } else {
                                        p
                                    }
                                },
                            ),
                        )
                    }
                }
            }
            }
        }

    /**
     * Set the schedutil rate-limit (microseconds). Written to every
     * rate_limit_us node the kernel exposes (global and/or per-policy) so
     * one knob covers both cpufreq layouts.
     */
    suspend fun setSchedutilRateLimit(valueUs: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                check(mutableState.value.cpu.schedutilSupported) { "schedutil not supported" }
                val numeric = valueUs.trim().toLongOrNull()
                check(numeric != null && numeric in 0..1_000_000) { "value out of range" }
                val targets = schedutilRatePaths(shell)
                check(targets.isNotEmpty()) { "schedutil not supported" }
                targets.forEach { path ->
                    check(writeSysfs(path, numeric.toString(), shell)) { "cpufreq write failed" }
                }
                storeCpuValue(policyId = "", rateLimitUs = numeric.toString(), shell = shell)
                mutableState.update {
                    it.copy(cpu = it.cpu.copy(schedutilRateLimitUs = numeric.toString()))
                }
            }
            }
        }
    }

    suspend fun setCpuPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val p = prefs()
                p.edit().putBoolean(KEY_CPU_PERSIST, persist).apply()
                if (persist && !p.contains(KEY_CPU_JSON)) {
                    // Backfill live config so enabling persistence captures the
                    // current setup, not just values changed afterwards.
                    backfillCpuStored()
                }
                syncBootScript(shell)
                mutableState.update { it.copy(cpu = it.cpu.copy(persist = persist)) }
            }
            }
        }
    }

    suspend fun setGpuGovernor(id: String, governor: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val node = gpuNode(id, shell)
                check(node != null && node.governorPath != null) { "governor not supported" }
                val device = node.device
                val clean = governor.trim()
                check(clean.matches(GOVERNOR_PATTERN)) { "invalid governor" }
                check(device.availableGovernors.isEmpty() || clean in device.availableGovernors) {
                    "unknown governor"
                }
                check(writeSysfs(node.governorPath, clean, shell)) { "gpu write failed" }
                storeGpuValue(id, governor = clean, shell = shell)
                mutableState.update {
                    it.copy(
                        gpu = it.gpu.copy(
                            devices = it.gpu.devices.map { d ->
                                if (d.id == id) d.copy(governor = clean) else d
                            },
                        ),
                    )
                }
            }
            }
        }
    }

    /**
     * Set a GPU's min/max clock (Hz). Like the CPU path, the bound moving
     * away from the current window is written first so the kernel never
     * transiently sees min > max.
     */
    suspend fun setGpuFreqs(id: String, minHz: Long, maxHz: Long): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val node = gpuNode(id, shell)
                check(node != null && node.minPath != null && node.maxPath != null) {
                    "clocks not supported"
                }
                val device = node.device
                check(minHz > 0 && maxHz > 0 && minHz <= maxHz) {
                    "min must not exceed max"
                }
                if (device.availableFreqsHz.isNotEmpty()) {
                    check(minHz in device.availableFreqsHz && maxHz in device.availableFreqsHz) {
                        "frequency not supported"
                    }
                }
                val minFirst = maxHz < device.minFreqHz
                if (minFirst) {
                    check(writeSysfs(node.minPath, minHz.toString(), shell)) { "gpu write failed" }
                    check(writeSysfs(node.maxPath, maxHz.toString(), shell)) { "gpu write failed" }
                } else {
                    check(writeSysfs(node.maxPath, maxHz.toString(), shell)) { "gpu write failed" }
                    check(writeSysfs(node.minPath, minHz.toString(), shell)) { "gpu write failed" }
                }
                storeGpuValue(id, minHz = minHz, maxHz = maxHz, shell = shell)
                mutableState.update {
                    it.copy(
                        gpu = it.gpu.copy(
                            devices = it.gpu.devices.map { d ->
                                if (d.id == id) d.copy(minFreqHz = minHz, maxFreqHz = maxHz) else d
                            },
                        ),
                    )
                }
            }
            }
        }
    }

    suspend fun setGpuPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val p = prefs()
                p.edit().putBoolean(KEY_GPU_PERSIST, persist).apply()
                if (persist && !p.contains(KEY_GPU_JSON)) {
                    backfillGpuStored()
                }
                syncBootScript(shell)
                mutableState.update { it.copy(gpu = it.gpu.copy(persist = persist)) }
            }
            }
        }
    }

    suspend fun setIoScheduler(name: String, scheduler: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val device = mutableState.value.io.devices.firstOrNull { it.name == name }
                check(device != null) { "unknown block device" }
                val clean = scheduler.trim()
                check(clean.matches(SCHEDULER_PATTERN)) { "invalid scheduler" }
                check(device.availableSchedulers.isEmpty() || clean in device.availableSchedulers) {
                    "unknown scheduler"
                }
                check(writeSysfs("$BLOCK_BASE/$name/queue/scheduler", clean, shell)) {
                    "scheduler write failed"
                }
                storeIoValue(name, scheduler = clean, shell = shell)
                mutableState.update {
                    it.copy(
                        io = it.io.copy(
                            devices = it.io.devices.map { d ->
                                if (d.name == name) d.copy(scheduler = clean) else d
                            },
                        ),
                    )
                }
            }
            }
        }
    }

    suspend fun setIoReadAhead(name: String, kb: Long): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val device = mutableState.value.io.devices.firstOrNull { it.name == name }
                check(device != null) { "unknown block device" }
                check(kb in 0..READ_AHEAD_MAX_KB) { "value out of range" }
                check(writeSysfs("$BLOCK_BASE/$name/queue/read_ahead_kb", kb.toString(), shell)) {
                    "read-ahead write failed"
                }
                storeIoValue(name, readAheadKb = kb, shell = shell)
                mutableState.update {
                    it.copy(
                        io = it.io.copy(
                            devices = it.io.devices.map { d ->
                                if (d.name == name) d.copy(readAheadKb = kb) else d
                            },
                        ),
                    )
                }
            }
            }
        }
    }

    suspend fun setIoPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val p = prefs()
                p.edit().putBoolean(KEY_IO_PERSIST, persist).apply()
                if (persist && !p.contains(KEY_IO_JSON)) {
                    backfillIoStored()
                }
                syncBootScript(shell)
                mutableState.update { it.copy(io = it.io.copy(persist = persist)) }
            }
            }
        }
    }

    /**
     * Enumerate cpufreq policies. Modern kernels expose
     * `cpufreq/policyN` (one node per cluster); older ones only have
     * per-CPU `cpuN/cpufreq` directories, which are treated as one policy
     * each.
     */
    private fun readCpuState(prefs: android.content.SharedPreferences, shell: Shell): CpuState {
        val policyIds = ShellUtils.fastCmd(
            shell,
            "ls -d $CPUFREQ_BASE/policy* 2>/dev/null$ONE_LINE",
        ).trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { it.substringAfterLast('/') }
        val ids = policyIds.ifEmpty {
            ShellUtils.fastCmd(
                shell,
                "ls -d $CPU_SYS_BASE/cpu[0-9]*/cpufreq/scaling_governor 2>/dev/null$ONE_LINE",
            ).trim().split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .mapNotNull { Regex("cpu\\d+").find(it)?.value }
                .distinct()
        }
        if (ids.isEmpty()) return CpuState()
        val policies = ids.mapNotNull { id ->
            val dir = cpuPolicyDir(id)
            val governor = readSysfs("$dir/scaling_governor", shell).getOrNull()?.trim()
                ?: return@mapNotNull null
            val available = readSysfs("$dir/scaling_available_governors", shell).getOrNull()
                ?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }
                .orEmpty()
            val cpus = readSysfs("$dir/affected_cpus", shell).getOrNull()?.trim().orEmpty()
            CpuPolicy(
                id = id,
                cpus = cpus,
                governor = governor,
                availableGovernors = available,
                minFreqKhz = readSysfs("$dir/scaling_min_freq", shell).getOrNull()
                    ?.trim()?.toLongOrNull() ?: 0,
                maxFreqKhz = readSysfs("$dir/scaling_max_freq", shell).getOrNull()
                    ?.trim()?.toLongOrNull() ?: 0,
                cpuinfoMinKhz = readSysfs("$dir/cpuinfo_min_freq", shell).getOrNull()
                    ?.trim()?.toLongOrNull() ?: 0,
                cpuinfoMaxKhz = readSysfs("$dir/cpuinfo_max_freq", shell).getOrNull()
                    ?.trim()?.toLongOrNull() ?: 0,
                curFreqKhz = readSysfs("$dir/scaling_cur_freq", shell).getOrNull()
                    ?.trim()?.toLongOrNull() ?: 0,
            )
        }
        if (policies.isEmpty()) return CpuState()
        val rateLimit = schedutilRatePaths(shell).firstNotNullOfOrNull { path ->
            readSysfs(path, shell).getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }
        return CpuState(
            supported = true,
            policies = policies,
            schedutilRateLimitUs = rateLimit.orEmpty(),
            schedutilSupported = rateLimit != null,
            persist = prefs.getBoolean(KEY_CPU_PERSIST, false),
        )
    }

    /** Sysfs directory backing a policy id (policy node or per-CPU fallback). */
    private fun cpuPolicyDir(id: String): String = if (id.startsWith("policy")) {
        "$CPUFREQ_BASE/$id"
    } else {
        "$CPU_SYS_BASE/$id/cpufreq"
    }

    /** All existing schedutil rate_limit_us nodes (global + per-policy). */
    private fun schedutilRatePaths(shell: Shell): List<String> {
        val out = ShellUtils.fastCmd(
            shell,
            "ls $SCHEDUTIL_GLOBAL_RATE $CPUFREQ_BASE/policy*/schedutil/rate_limit_us " +
                    "$CPU_SYS_BASE/cpu[0-9]*/cpufreq/schedutil/rate_limit_us 2>/dev/null$ONE_LINE",
        ).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return out.distinct()
    }

    private data class StoredCpuPolicy(
        val id: String,
        val governor: String?,
        val minKhz: Long?,
        val maxKhz: Long?,
    )

    private data class StoredCpu(val policies: List<StoredCpuPolicy>, val rateLimitUs: String?)

    private fun loadCpuStored(): StoredCpu {
        val raw = prefs().getString(KEY_CPU_JSON, "").orEmpty()
        if (raw.isEmpty()) return StoredCpu(emptyList(), null)
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("policies") ?: JSONArray()
            val policies = (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val id = o.optString("id", "")
                if (id.isEmpty()) return@mapNotNull null
                StoredCpuPolicy(
                    id = id,
                    governor = o.optString("governor", "").takeIf { it.isNotEmpty() },
                    minKhz = o.optLong("min", -1).takeIf { it >= 0 },
                    maxKhz = o.optLong("max", -1).takeIf { it >= 0 },
                )
            }
            StoredCpu(policies, root.optString("rate_limit_us", "").takeIf { it.isNotEmpty() })
        }.getOrDefault(StoredCpu(emptyList(), null))
    }

    private fun saveCpuStored(stored: StoredCpu) {
        val root = JSONObject()
        val array = JSONArray()
        stored.policies.sortedBy { it.id }.forEach {
            val o = JSONObject().put("id", it.id)
            it.governor?.let { g -> o.put("governor", g) }
            it.minKhz?.let { v -> o.put("min", v) }
            it.maxKhz?.let { v -> o.put("max", v) }
            array.put(o)
        }
        root.put("policies", array)
        stored.rateLimitUs?.let { root.put("rate_limit_us", it) }
        prefs().edit().putString(KEY_CPU_JSON, root.toString()).apply()
    }

    /**
     * Mirror an applied CPU value into the stored config and regenerate the
     * boot script. Only the changed field is updated; other fields keep
     * their previously stored (or absent) values.
     */
    private fun storeCpuValue(
        policyId: String,
        governor: String? = null,
        minKhz: Long? = null,
        maxKhz: Long? = null,
        rateLimitUs: String? = null,
        shell: Shell,
    ) {
        val stored = loadCpuStored()
        val policies = if (policyId.isEmpty()) {
            stored.policies
        } else {
            val prev = stored.policies.firstOrNull { it.id == policyId }
            val next = StoredCpuPolicy(
                id = policyId,
                governor = governor ?: prev?.governor,
                minKhz = minKhz ?: prev?.minKhz,
                maxKhz = maxKhz ?: prev?.maxKhz,
            )
            stored.policies.filterNot { it.id == policyId } + next
        }
        saveCpuStored(stored.copy(policies = policies, rateLimitUs = rateLimitUs ?: stored.rateLimitUs))
        syncBootScript(shell)
    }

    private fun backfillCpuStored() {
        val live = mutableState.value.cpu
        val policies = live.policies.map {
            StoredCpuPolicy(it.id, it.governor, it.minFreqKhz, it.maxFreqKhz)
        }
        saveCpuStored(
            StoredCpu(policies, live.schedutilRateLimitUs.takeIf { it.isNotEmpty() }),
        )
    }

    /**
     * Boot-script lines for the stored CPU setup. Every write is guarded by
     * `[ -e path ]` so one script works across policy/per-CPU layouts and
     * kernels where a node is missing.
     */
    private fun cpuBootLines(): List<String> {
        if (!prefs().getBoolean(KEY_CPU_PERSIST, false)) return emptyList()
        val stored = loadCpuStored()
        val lines = mutableListOf<String>()
        stored.policies.forEach { policy ->
            val dir = cpuPolicyDir(policy.id)
            policy.governor?.let { gov ->
                if (gov.matches(GOVERNOR_PATTERN)) {
                    lines.add("[ -e '$dir/scaling_governor' ] && echo '$gov' > '$dir/scaling_governor'")
                }
            }
            // Same min/max ordering rule as the live path: move the outer
            // bound first so min never transiently exceeds max.
            val min = policy.minKhz
            val max = policy.maxKhz
            if (min != null && max != null && min > 0 && max > 0 && min <= max) {
                val first = "'$dir/scaling_max_freq' ] && echo '$max' > '$dir/scaling_max_freq'"
                val second = "'$dir/scaling_min_freq' ] && echo '$min' > '$dir/scaling_min_freq'"
                // When lowering the window the min must move first; the
                // stored snapshot has no "current" reference, so compare
                // against the live value when available.
                val liveMin = mutableState.value.cpu.policies
                    .firstOrNull { it.id == policy.id }?.minFreqKhz ?: 0
                if (max < liveMin) {
                    lines.add("[ -e '$dir/scaling_min_freq' ] && echo '$min' > '$dir/scaling_min_freq'")
                    lines.add("[ -e $first")
                } else {
                    lines.add("[ -e $first")
                    lines.add("[ -e $second")
                }
            } else {
                max?.let {
                    if (it > 0) lines.add("[ -e '$dir/scaling_max_freq' ] && echo '$it' > '$dir/scaling_max_freq'")
                }
                min?.let {
                    if (it > 0) lines.add("[ -e '$dir/scaling_min_freq' ] && echo '$it' > '$dir/scaling_min_freq'")
                }
            }
        }
        stored.rateLimitUs?.let { rate ->
            if (rate.toLongOrNull() != null) {
                lines.add(
                    "for f in $SCHEDUTIL_GLOBAL_RATE $CPUFREQ_BASE/policy*/schedutil/rate_limit_us; " +
                            "do [ -e \"\$f\" ] && echo '$rate' > \"\$f\"; done",
                )
            }
        }
        return lines
    }

    /**
     * Resolved writable nodes for a GPU device. Paths are null when the
     * kernel does not expose that knob for the device.
     */
    private data class GpuNode(
        val device: GpuDevice,
        val governorPath: String?,
        val minPath: String?,
        val maxPath: String?,
    )

    /** Resolve a live GPU device (by sysfs id) to its writable nodes. */
    private fun gpuNode(id: String, shell: Shell): GpuNode? {
        val device = mutableState.value.gpu.devices.firstOrNull { it.id == id }
            ?: return null
        return resolveGpuNode(device, shell)
    }

    private fun resolveGpuNode(device: GpuDevice, shell: Shell): GpuNode {
        if (device.id.startsWith("kgsl-")) {
            val base = "$KGSL_BASE/${device.id}"
            val devfreqGov = "$base/devfreq/governor"
            return if (readSysfs(devfreqGov, shell).isSuccess) {
                GpuNode(
                    device,
                    governorPath = devfreqGov,
                    minPath = "$base/devfreq/min_freq".takeIf { readSysfs(it, shell).isSuccess },
                    maxPath = "$base/devfreq/max_freq".takeIf { readSysfs(it, shell).isSuccess },
                )
            } else {
                // Legacy kGSL layout without a devfreq governor node.
                GpuNode(
                    device,
                    governorPath = null,
                    minPath = "$base/min_gpuclk".takeIf { readSysfs(it, shell).isSuccess },
                    maxPath = "$base/max_gpuclk".takeIf { readSysfs(it, shell).isSuccess },
                )
            }
        }
        val base = "$DEVFREQ_BASE/${device.id}"
        return GpuNode(
            device,
            governorPath = "$base/governor".takeIf { readSysfs(it, shell).isSuccess },
            minPath = "$base/min_freq".takeIf { readSysfs(it, shell).isSuccess },
            maxPath = "$base/max_freq".takeIf { readSysfs(it, shell).isSuccess },
        )
    }

    /**
     * Enumerate GPU clock domains: the kGSL node (Adreno) plus devfreq
     * nodes whose `name` matches a GPU. Only nodes with at least one
     * readable clock knob are surfaced.
     */
    private fun readGpuState(prefs: android.content.SharedPreferences, shell: Shell): GpuState {
        val devices = mutableListOf<GpuDevice>()
        if (readSysfs("$KGSL_BASE/$KGSL_NODE/gpuclk", shell).isSuccess ||
            readSysfs("$KGSL_BASE/$KGSL_NODE/devfreq/cur_freq", shell).isSuccess
        ) {
            readKgslDevice(shell)?.let { devices.add(it) }
        }
        val devfreqNodes = ShellUtils.fastCmd(shell, "ls -d $DEVFREQ_BASE/* 2>/dev/null$ONE_LINE")
            .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        devfreqNodes.forEach { path ->
            val id = path.substringAfterLast('/')
            val name = readSysfs("$path/name", shell).getOrNull()?.trim().orEmpty()
            val haystack = "$id $name".lowercase()
            if (GPU_NAME_KEYWORDS.none { haystack.contains(it) }) return@forEach
            readDevfreqDevice(id, shell)?.let { devices.add(it) }
        }
        if (devices.isEmpty()) return GpuState()
        return GpuState(
            supported = true,
            devices = devices.distinctBy { it.id }.sortedBy { it.id },
            persist = prefs.getBoolean(KEY_GPU_PERSIST, false),
        )
    }

    private fun readKgslDevice(shell: Shell): GpuDevice? {
        val base = "$KGSL_BASE/$KGSL_NODE"
        val devfreq = readSysfs("$base/devfreq/cur_freq", shell).getOrNull() != null
        val cur = if (devfreq) {
            readSysfs("$base/devfreq/cur_freq", shell).getOrNull()?.trim()?.toLongOrNull()
        } else {
            readSysfs("$base/gpuclk", shell).getOrNull()?.trim()?.toLongOrNull()
        } ?: return null
        val (governor, governors) = if (devfreq) {
            readSysfs("$base/devfreq/governor", shell).getOrNull()?.trim().orEmpty() to
                    readSysfs("$base/devfreq/available_governors", shell).getOrNull()
                        ?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
        } else {
            "" to emptyList()
        }
        val avail = if (devfreq) {
            readSysfs("$base/devfreq/available_frequencies", shell).getOrNull()
        } else {
            readSysfs("$base/gpu_available_frequencies", shell).getOrNull()
        }?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }.orEmpty()
        return GpuDevice(
            id = KGSL_NODE,
            label = "Adreno",
            governor = governor,
            availableGovernors = governors,
            curFreqHz = cur,
            minFreqHz = if (devfreq) {
                readSysfs("$base/devfreq/min_freq", shell).getOrNull()?.trim()?.toLongOrNull() ?: 0
            } else {
                readSysfs("$base/min_gpuclk", shell).getOrNull()?.trim()?.toLongOrNull() ?: 0
            },
            maxFreqHz = if (devfreq) {
                readSysfs("$base/devfreq/max_freq", shell).getOrNull()?.trim()?.toLongOrNull() ?: 0
            } else {
                readSysfs("$base/max_gpuclk", shell).getOrNull()?.trim()?.toLongOrNull() ?: 0
            },
            availableFreqsHz = avail,
        )
    }

    private fun readDevfreqDevice(id: String, shell: Shell): GpuDevice? {
        val base = "$DEVFREQ_BASE/$id"
        val cur = readSysfs("$base/cur_freq", shell).getOrNull()?.trim()?.toLongOrNull()
            ?: return null
        val name = readSysfs("$base/name", shell).getOrNull()?.trim().orEmpty()
        return GpuDevice(
            id = id,
            label = name.ifEmpty { id },
            governor = readSysfs("$base/governor", shell).getOrNull()?.trim().orEmpty(),
            availableGovernors = readSysfs("$base/available_governors", shell).getOrNull()
                ?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty(),
            curFreqHz = cur,
            minFreqHz = readSysfs("$base/min_freq", shell).getOrNull()?.trim()?.toLongOrNull() ?: 0,
            maxFreqHz = readSysfs("$base/max_freq", shell).getOrNull()?.trim()?.toLongOrNull() ?: 0,
            availableFreqsHz = readSysfs("$base/available_frequencies", shell).getOrNull()
                ?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }.orEmpty(),
        )
    }

    private fun readIoState(prefs: android.content.SharedPreferences, shell: Shell): IoState {
        val names = ShellUtils.fastCmd(shell, "ls $BLOCK_BASE 2>/dev/null$ONE_LINE")
            .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .filter { name -> IO_SKIP_PREFIXES.none { name.startsWith(it) } }
        val devices = names.mapNotNull { name ->
            val schedRaw = readSysfs("$BLOCK_BASE/$name/queue/scheduler", shell).getOrNull()?.trim()
            val current = schedRaw?.let { Regex("\\[(.+?)]").find(it)?.groupValues?.get(1) }
            val available = schedRaw
                ?.replace("[", "")?.replace("]", "")
                ?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
            val readAhead = readSysfs("$BLOCK_BASE/$name/queue/read_ahead_kb", shell).getOrNull()
                ?.trim()?.toLongOrNull() ?: -1
            if (available.isEmpty() && readAhead < 0) return@mapNotNull null
            IoDevice(
                name = name,
                scheduler = current.orEmpty(),
                availableSchedulers = available,
                readAheadKb = readAhead,
            )
        }
        if (devices.isEmpty()) return IoState()
        return IoState(
            supported = true,
            devices = devices.sortedBy { it.name },
            persist = prefs.getBoolean(KEY_IO_PERSIST, false),
        )
    }

    private data class StoredGpuDevice(
        val id: String,
        val governor: String?,
        val minHz: Long?,
        val maxHz: Long?,
    )

    private fun loadGpuStored(): List<StoredGpuDevice> {
        val raw = prefs().getString(KEY_GPU_JSON, "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val id = o.optString("id", "")
                if (id.isEmpty()) return@mapNotNull null
                StoredGpuDevice(
                    id = id,
                    governor = o.optString("governor", "").takeIf { it.isNotEmpty() },
                    minHz = o.optLong("min", -1).takeIf { it >= 0 },
                    maxHz = o.optLong("max", -1).takeIf { it >= 0 },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveGpuStored(devices: List<StoredGpuDevice>) {
        val array = JSONArray()
        devices.sortedBy { it.id }.forEach {
            val o = JSONObject().put("id", it.id)
            it.governor?.let { g -> o.put("governor", g) }
            it.minHz?.let { v -> o.put("min", v) }
            it.maxHz?.let { v -> o.put("max", v) }
            array.put(o)
        }
        prefs().edit().putString(KEY_GPU_JSON, array.toString()).apply()
    }

    private fun storeGpuValue(id: String, governor: String? = null, minHz: Long? = null, maxHz: Long? = null, shell: Shell) {
        val prev = loadGpuStored().firstOrNull { it.id == id }
        val next = StoredGpuDevice(
            id = id,
            governor = governor ?: prev?.governor,
            minHz = minHz ?: prev?.minHz,
            maxHz = maxHz ?: prev?.maxHz,
        )
        saveGpuStored(loadGpuStored().filterNot { it.id == id } + next)
        syncBootScript(shell)
    }

    private fun backfillGpuStored() {
        saveGpuStored(
            mutableState.value.gpu.devices.map {
                StoredGpuDevice(it.id, it.governor.ifEmpty { null }, it.minFreqHz, it.maxFreqHz)
            },
        )
    }

    private data class StoredIoDevice(
        val name: String,
        val scheduler: String?,
        val readAheadKb: Long?,
    )

    private fun loadIoStored(): List<StoredIoDevice> {
        val raw = prefs().getString(KEY_IO_JSON, "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val name = o.optString("name", "")
                if (name.isEmpty()) return@mapNotNull null
                StoredIoDevice(
                    name = name,
                    scheduler = o.optString("scheduler", "").takeIf { it.isNotEmpty() },
                    readAheadKb = o.optLong("read_ahead_kb", -1).takeIf { it >= 0 },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveIoStored(devices: List<StoredIoDevice>) {
        val array = JSONArray()
        devices.sortedBy { it.name }.forEach {
            val o = JSONObject().put("name", it.name)
            it.scheduler?.let { s -> o.put("scheduler", s) }
            it.readAheadKb?.let { v -> o.put("read_ahead_kb", v) }
            array.put(o)
        }
        prefs().edit().putString(KEY_IO_JSON, array.toString()).apply()
    }

    private fun storeIoValue(name: String, scheduler: String? = null, readAheadKb: Long? = null, shell: Shell) {
        val prev = loadIoStored().firstOrNull { it.name == name }
        val next = StoredIoDevice(
            name = name,
            scheduler = scheduler ?: prev?.scheduler,
            readAheadKb = readAheadKb ?: prev?.readAheadKb,
        )
        saveIoStored(loadIoStored().filterNot { it.name == name } + next)
        syncBootScript(shell)
    }

    private fun backfillIoStored() {
        saveIoStored(
            mutableState.value.io.devices.map {
                StoredIoDevice(
                    it.name,
                    it.scheduler.ifEmpty { null },
                    it.readAheadKb.takeIf { v -> v >= 0 },
                )
            },
        )
    }

    /**
     * Boot-script lines for the stored GPU/I/O setup. Every write is guarded
     * by `[ -e path ]`; kGSL covers both the devfreq and legacy layouts so
     * one script works across Adreno generations.
     */
    private fun gpuIoBootLines(): List<String> {
        val lines = mutableListOf<String>()
        if (prefs().getBoolean(KEY_GPU_PERSIST, false)) {
            loadGpuStored().forEach { stored ->
                stored.governor?.let { gov ->
                    if (!gov.matches(GOVERNOR_PATTERN)) return@let
                    if (stored.id.startsWith("kgsl-")) {
                        val base = "$KGSL_BASE/${stored.id}"
                        lines.add("[ -e '$base/devfreq/governor' ] && echo '$gov' > '$base/devfreq/governor'")
                    } else {
                        val path = "$DEVFREQ_BASE/${stored.id}/governor"
                        lines.add("[ -e '$path' ] && echo '$gov' > '$path'")
                    }
                }
                val min = stored.minHz
                val max = stored.maxHz
                if (min != null && max != null && min > 0 && max > 0 && min <= max) {
                    val liveMin = mutableState.value.gpu.devices
                        .firstOrNull { it.id == stored.id }?.minFreqHz ?: 0
                    // Same min/max ordering rule as the live path: move the
                    // outer bound first so min never transiently exceeds max.
                    val ordered = if (max < liveMin) {
                        listOf(true to min, false to max)
                    } else {
                        listOf(false to max, true to min)
                    }
                    ordered.forEach { (isMin, value) ->
                        gpuFreqPaths(stored.id, isMin).forEach { path ->
                            lines.add("[ -e '$path' ] && echo '$value' > '$path'")
                        }
                    }
                }
            }
        }
        if (prefs().getBoolean(KEY_IO_PERSIST, false)) {
            loadIoStored().forEach { stored ->
                stored.scheduler?.let { sched ->
                    if (sched.matches(SCHEDULER_PATTERN)) {
                        val path = "$BLOCK_BASE/${stored.name}/queue/scheduler"
                        lines.add("[ -e '$path' ] && echo '$sched' > '$path'")
                    }
                }
                stored.readAheadKb?.let { kb ->
                    if (kb in 0..READ_AHEAD_MAX_KB) {
                        val path = "$BLOCK_BASE/${stored.name}/queue/read_ahead_kb"
                        lines.add("[ -e '$path' ] && echo '$kb' > '$path'")
                    }
                }
            }
        }
        return lines
    }

    /**
     * Candidate min/max clock paths for a GPU id. kGSL covers both layouts;
     * the `[ -e ]` guard in the boot script picks the one that exists.
     */
    private fun gpuFreqPaths(id: String, isMin: Boolean): List<String> {
        val leaf = if (isMin) "min" else "max"
        return if (id.startsWith("kgsl-")) {
            val base = "$KGSL_BASE/$id"
            listOf("$base/devfreq/${leaf}_freq", "$base/${leaf}_gpuclk")
        } else {
            listOf("$DEVFREQ_BASE/$id/${leaf}_freq")
        }
    }

    /**
     * Set one LMK level by index. Page thresholds must stay strictly
     * ascending; adj follows the oom_score_adj range.
     */
    suspend fun setLmkLevel(index: Int, pages: Long, adj: Long): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                ksuCliRepository.createRootShell().use { shell ->
                runCatching {
                    val state = mutableState.value.lmk
                    check(state.supported) { "LMK not supported" }
                    check(index in state.levels.indices) { "unknown level" }
                    check(pages > 0) { "threshold must be positive" }
                    check(adj in -1000..1000) { "adj out of range" }
                    val prev = state.levels.getOrNull(index - 1)?.pages ?: -1
                    val next = state.levels.getOrNull(index + 1)?.pages ?: Long.MAX_VALUE
                    check(pages > prev && pages < next) { "levels must ascend" }
                    val levels = state.levels.mapIndexed { i, level ->
                        if (i == index) LmkLevel(pages, adj) else level
                    }
                    check(writeLmkLevels(state.useProps, levels, shell)) { "lmk write failed" }
                    storeLmkLevels(levels, shell)
                    mutableState.update {
                        it.copy(lmk = it.lmk.copy(levels = levels, profileId = ""))
                    }
                }
            }
            }
        }

    /**
     * Apply a curated preset: page thresholds come from the preset (MB),
     * adj values are preserved from the live table so device-specific oom
     * tuning is kept. The preset must have as many levels as the device.
     */
    suspend fun applyLmkProfile(id: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val state = mutableState.value.lmk
                check(state.supported) { "LMK not supported" }
                val presetMb = LMK_PROFILES_MB[id]
                check(presetMb != null) { "unknown profile" }
                check(presetMb.size == state.levels.size) { "profile does not fit this device" }
                val levels = state.levels.mapIndexed { i, level ->
                    LmkLevel(presetMb[i] * PAGES_PER_MB, level.adj)
                }
                check(writeLmkLevels(state.useProps, levels, shell)) { "lmk write failed" }
                storeLmkLevels(levels, shell)
                mutableState.update {
                    it.copy(lmk = it.lmk.copy(levels = levels, profileId = id))
                }
            }
            }
        }
    }

    /** Restore the stock table captured on first read. */
    suspend fun resetLmkStock(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val state = mutableState.value.lmk
                check(state.supported) { "LMK not supported" }
                val stock = loadLmkStock()
                check(stock != null && stock.size == state.levels.size) { "no stock table saved" }
                check(writeLmkLevels(state.useProps, stock, shell)) { "lmk write failed" }
                storeLmkLevels(stock, shell)
                mutableState.update {
                    it.copy(lmk = it.lmk.copy(levels = stock, profileId = matchLmkProfile(stock)))
                }
            }
            }
        }
    }

    suspend fun setLmkPersist(persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val p = prefs()
                p.edit().putBoolean(KEY_LMK_PERSIST, persist).apply()
                if (persist && !p.contains(KEY_LMK_JSON)) {
                    // Backfill live levels so enabling persistence captures
                    // the current table, not just values changed afterwards.
                    saveLmkStored(mutableState.value.lmk.levels, shell)
                }
                syncBootScript(shell)
                mutableState.update { it.copy(lmk = it.lmk.copy(persist = persist)) }
            }
            }
        }
    }

    /**
     * Serialize the live tuning setup to a versioned JSON profile. Reads
     * current state only; callers write the result wherever the user picks.
     */
    fun exportProfile(): String {
        val s = mutableState.value
        val root = JSONObject()
        root.put("version", PROFILE_VERSION)
        root.put("exported_at", System.currentTimeMillis())
        root.put("tcp", JSONObject().put("value", s.tcpCurrent).put("persist", s.tcpPersist))
        root.put(
            "bore",
            JSONObject()
                .put("persist", s.borePersist)
                .put("enabled", s.boreEnabled)
                .put("values", knobMap(s.boreKnobs.map { it.key to it.value })),
        )
        root.put(
            "vm",
            JSONObject()
                .put("persist", s.vm.persist)
                .put("values", knobMap(s.vm.knobs.map { it.key to it.value })),
        )
        root.put(
            "sched",
            JSONObject()
                .put("persist", s.sched.persist)
                .put("values", knobMap(s.sched.knobs.map { it.key to it.value })),
        )
        if (s.zram.supported) {
            root.put(
                "zram",
                JSONObject()
                    .put("persist", s.zram.persist)
                    .put("size", s.zram.disksizeBytes)
                    .put("algo", s.zram.currentAlgo)
                    .put("streams", s.zram.maxStreams)
                    .put("swappiness", s.zram.swappiness),
            )
        }
        if (s.cpu.supported) {
            val policies = JSONArray()
            s.cpu.policies.forEach {
                policies.put(
                    JSONObject().put("id", it.id).put("governor", it.governor)
                        .put("min", it.minFreqKhz).put("max", it.maxFreqKhz),
                )
            }
            root.put(
                "cpu",
                JSONObject().put("persist", s.cpu.persist)
                    .put("rate_limit_us", s.cpu.schedutilRateLimitUs).put("policies", policies),
            )
        }
        if (s.gpu.supported) {
            val devices = JSONArray()
            s.gpu.devices.forEach {
                devices.put(
                    JSONObject().put("id", it.id).put("governor", it.governor)
                        .put("min", it.minFreqHz).put("max", it.maxFreqHz),
                )
            }
            root.put("gpu", JSONObject().put("persist", s.gpu.persist).put("devices", devices))
        }
        if (s.io.supported) {
            val devices = JSONArray()
            s.io.devices.forEach {
                devices.put(
                    JSONObject().put("name", it.name).put("scheduler", it.scheduler)
                        .put("read_ahead_kb", it.readAheadKb),
                )
            }
            root.put("io", JSONObject().put("persist", s.io.persist).put("devices", devices))
        }
        if (s.lmk.supported) {
            val levels = JSONArray()
            s.lmk.levels.forEach {
                levels.put(JSONObject().put("pages", it.pages).put("adj", it.adj))
            }
            root.put("lmk", JSONObject().put("persist", s.lmk.persist).put("levels", levels))
        }
        val sysctls = JSONArray()
        s.sysctls.forEach {
            sysctls.put(
                JSONObject().put("key", it.key).put("value", it.value).put("persist", it.persist),
            )
        }
        root.put("sysctls", sysctls)
        return root.toString(2)
    }

    private fun knobMap(entries: List<Pair<String, String>>): JSONObject {
        val o = JSONObject()
        entries.forEach { (key, value) -> o.put(key, value) }
        return o
    }

    /**
     * Apply a JSON profile exported by [exportProfile]. The current setup is
     * automatically backed up to the app's profile_backups directory first;
     * the backup file name is the success value. Every section is attempted
     * even if an earlier one fails; leftover failures fail the result after
     * a final [refresh] converges the UI with the live kernel state.
     *
     * Note: deliberately not holding [mutex] here — each setter below takes
     * it individually, and holding it across them would deadlock.
     */
    suspend fun importProfile(raw: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(raw)
            check(root.optInt("version", -1) == PROFILE_VERSION) { "unsupported profile" }
            val backupName = "backup_${System.currentTimeMillis()}.json"
            val dir = File(application.filesDir, PROFILE_BACKUP_DIR).apply { mkdirs() }
            File(dir, backupName).writeText(exportProfile())
            val failures = mutableListOf<String>()
            suspend fun attempt(label: String, block: suspend () -> Result<Unit>) {
                val result = runCatching { block() }.getOrElse { Result.failure(it) }
                if (result.isFailure) failures.add(label)
            }
            // Generic sysctls first so dedicated sections below win on overlap.
            root.optJSONArray("sysctls")?.let { array ->
                (0 until array.length()).forEach { i ->
                    val o = array.getJSONObject(i)
                    val key = o.optString("key", "")
                    if (key.isNotEmpty()) {
                        attempt("sysctl:$key") {
                            addOrUpdateSysctl(key, o.getString("value"), o.optBoolean("persist", false))
                        }
                    }
                }
            }
            root.optJSONObject("tcp")?.let { o ->
                val value = o.optString("value", "")
                if (value.isNotEmpty()) {
                    attempt("tcp") { setTcp(value, o.optBoolean("persist", false)) }
                }
            }
            root.optJSONObject("bore")?.let { o ->
                o.optJSONObject("values")?.let { values ->
                    val keys = values.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = values.optString(key, "")
                        if (value.isEmpty()) continue
                        attempt("bore:$key") {
                            if (key == BORE_ENABLE_KEY) {
                                setBoreEnabled(value.trim() != "0")
                            } else {
                                setBoreKnob(key, value)
                            }
                        }
                    }
                }
                attempt("bore:persist") { setBorePersist(o.optBoolean("persist", false)) }
            }
            root.optJSONObject("vm")?.let { o ->
                applyKnobMap(o, "vm", failures) { key, value -> setVmKnob(key, value) }
                attempt("vm:persist") { setVmPersist(o.optBoolean("persist", false)) }
            }
            root.optJSONObject("sched")?.let { o ->
                applyKnobMap(o, "sched", failures) { key, value -> setSchedKnob(key, value) }
                attempt("sched:persist") { setSchedPersist(o.optBoolean("persist", false)) }
            }
            root.optJSONObject("zram")?.let { o ->
                if (mutableState.value.zram.supported) {
                    val size = o.optLong("size", 0)
                    val algo = o.optString("algo", "")
                    val streams = o.optLong("streams", 0)
                    if (size > 0 && algo.isNotEmpty() && streams > 0) {
                        attempt("zram") { configureZram(size, algo, streams) }
                    }
                    val swappiness = o.optString("swappiness", "")
                    if (swappiness.isNotEmpty()) {
                        attempt("zram:swappiness") { setZramSwappiness(swappiness) }
                    }
                    attempt("zram:persist") { setZramPersist(o.optBoolean("persist", false)) }
                }
            }
            root.optJSONObject("cpu")?.let { o ->
                if (mutableState.value.cpu.supported) {
                    o.optJSONArray("policies")?.let { array ->
                        (0 until array.length()).forEach { i ->
                            val p = array.getJSONObject(i)
                            val id = p.optString("id", "")
                            if (id.isEmpty()) return@forEach
                            val governor = p.optString("governor", "")
                            if (governor.isNotEmpty()) {
                                attempt("cpu:$id:governor") { setCpuGovernor(id, governor) }
                            }
                            val min = p.optLong("min", 0)
                            val max = p.optLong("max", 0)
                            if (min > 0 && max > 0) {
                                attempt("cpu:$id:freqs") { setCpuFreqs(id, min, max) }
                            }
                        }
                    }
                    val rate = o.optString("rate_limit_us", "")
                    if (rate.isNotEmpty()) {
                        attempt("cpu:schedutil") { setSchedutilRateLimit(rate) }
                    }
                    attempt("cpu:persist") { setCpuPersist(o.optBoolean("persist", false)) }
                }
            }
            root.optJSONObject("gpu")?.let { o ->
                if (mutableState.value.gpu.supported) {
                    o.optJSONArray("devices")?.let { array ->
                        (0 until array.length()).forEach { i ->
                            val d = array.getJSONObject(i)
                            val id = d.optString("id", "")
                            if (id.isEmpty()) return@forEach
                            val governor = d.optString("governor", "")
                            if (governor.isNotEmpty()) {
                                attempt("gpu:$id:governor") { setGpuGovernor(id, governor) }
                            }
                            val min = d.optLong("min", 0)
                            val max = d.optLong("max", 0)
                            if (min > 0 && max > 0) {
                                attempt("gpu:$id:clocks") { setGpuFreqs(id, min, max) }
                            }
                        }
                    }
                    attempt("gpu:persist") { setGpuPersist(o.optBoolean("persist", false)) }
                }
            }
            root.optJSONObject("io")?.let { o ->
                if (mutableState.value.io.supported) {
                    o.optJSONArray("devices")?.let { array ->
                        (0 until array.length()).forEach { i ->
                            val d = array.getJSONObject(i)
                            val name = d.optString("name", "")
                            if (name.isEmpty()) return@forEach
                            val scheduler = d.optString("scheduler", "")
                            if (scheduler.isNotEmpty()) {
                                attempt("io:$name:scheduler") { setIoScheduler(name, scheduler) }
                            }
                            if (d.has("read_ahead_kb")) {
                                val kb = d.optLong("read_ahead_kb", -1)
                                if (kb >= 0) {
                                    attempt("io:$name:read_ahead") { setIoReadAhead(name, kb) }
                                }
                            }
                        }
                    }
                    attempt("io:persist") { setIoPersist(o.optBoolean("persist", false)) }
                }
            }
            root.optJSONObject("lmk")?.let { o ->
                val live = mutableState.value.lmk
                if (live.supported) {
                    o.optJSONArray("levels")?.let { array ->
                        val levels = (0 until array.length()).mapNotNull { i ->
                            val e = array.getJSONObject(i)
                            val pages = e.optLong("pages", 0)
                            val adj = e.optLong("adj", Long.MIN_VALUE)
                            if (pages > 0 && adj != Long.MIN_VALUE) LmkLevel(pages, adj) else null
                        }
                        if (levels.size == live.levels.size) {
                            levels.forEachIndexed { index, level ->
                                attempt("lmk:level${index + 1}") {
                                    setLmkLevel(index, level.pages, level.adj)
                                }
                            }
                        } else if (levels.isNotEmpty()) {
                            failures.add("lmk:level-count")
                        }
                    }
                    attempt("lmk:persist") { setLmkPersist(o.optBoolean("persist", false)) }
                }
            }
            refresh()
            check(failures.isEmpty()) { "failed: ${failures.joinToString()}" }
            backupName
        }
    }

    /** Apply a "values" knob map from a profile section, collecting per-knob failures. */
    private suspend fun applyKnobMap(
        section: JSONObject,
        label: String,
        failures: MutableList<String>,
        setter: suspend (String, String) -> Result<Unit>,
    ) {
        section.optJSONObject("values")?.let { values ->
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = values.optString(key, "")
                if (value.isEmpty()) return@let
                val result = runCatching { setter(key, value) }.getOrElse { Result.failure(it) }
                if (result.isFailure) failures.add("$label:$key")
            }
        }
    }

    private fun readLmkState(prefs: android.content.SharedPreferences, shell: Shell): LmkState {
        // Modern path: userspace lmkd through a system property.
        val propLevels = readProp(LMK_PROP, shell).getOrNull()?.let { parseLmkLevels(it) }
        if (!propLevels.isNullOrEmpty()) {
            if (!prefs.contains(KEY_LMK_STOCK)) saveLmkStock(propLevels)
            return LmkState(
                supported = true,
                useProps = true,
                levels = propLevels,
                profileId = matchLmkProfile(propLevels),
                persist = prefs.getBoolean(KEY_LMK_PERSIST, false),
            )
        }
        // Legacy path: in-kernel lowmemorykiller module parameters.
        val minfree = readSysfs(LMK_MINFREE_SYSFS, shell).getOrNull()
        val adj = readSysfs(LMK_ADJ_SYSFS, shell).getOrNull()
        if (minfree != null && adj != null) {
            val levels = zipLmkLists(minfree, adj)
            if (levels.isNotEmpty()) {
                if (!prefs.contains(KEY_LMK_STOCK)) saveLmkStock(levels)
                return LmkState(
                    supported = true,
                    useProps = false,
                    levels = levels,
                    profileId = matchLmkProfile(levels),
                    persist = prefs.getBoolean(KEY_LMK_PERSIST, false),
                )
            }
        }
        return LmkState()
    }

    private fun matchLmkProfile(levels: List<LmkLevel>): String {
        return LMK_PROFILE_ORDER.firstOrNull { id ->
            val want = LMK_PROFILES_MB[id] ?: return@firstOrNull false
            want.size == levels.size &&
                    want.withIndex().all { (i, mb) -> levels[i].pages == mb * PAGES_PER_MB }
        }.orEmpty()
    }

    /** Parse "pages:adj,pages:adj,..." into levels, dropping malformed entries. */
    private fun parseLmkLevels(raw: String): List<LmkLevel> {
        return raw.trim().split(',').mapNotNull { entry ->
            val parts = entry.trim().split(':')
            if (parts.size != 2) return@mapNotNull null
            val pages = parts[0].trim().toLongOrNull()
            val adj = parts[1].trim().toLongOrNull()
            if (pages == null || adj == null || pages <= 0 || adj !in -1000..1000) {
                return@mapNotNull null
            }
            LmkLevel(pages, adj)
        }
    }

    /** Zip kernel-driver "p,p,..." minfree with "a,a,..." adj into levels. */
    private fun zipLmkLists(minfreeRaw: String, adjRaw: String): List<LmkLevel> {
        val pages = minfreeRaw.trim().split(Regex("[,\\s]+")).mapNotNull { it.toLongOrNull() }
        val adjs = adjRaw.trim().split(Regex("[,\\s]+")).mapNotNull { it.toLongOrNull() }
        if (pages.isEmpty() || pages.size != adjs.size) return emptyList()
        return pages.zip(adjs) { p, a -> LmkLevel(p, a) }
            .filter { it.pages > 0 && it.adj in -1000..1000 }
    }

    private fun writeLmkLevels(useProps: Boolean, levels: List<LmkLevel>, shell: Shell): Boolean {
        return runCatching {
            if (useProps) {
                val value = levels.joinToString(",") { "${it.pages}:${it.adj}" }
                if (!ShellUtils.fastCmdResult(shell, "setprop '$LMK_PROP' '$value' >/dev/null 2>&1")) {
                    return false
                }
                // Verify lmkd accepted the table.
                parseLmkLevels(readProp(LMK_PROP, shell).getOrDefault("")).map { it.pages } ==
                        levels.map { it.pages }
            } else {
                writeSysfs(LMK_MINFREE_SYSFS, levels.joinToString(",") { it.pages.toString() }, shell) &&
                        writeSysfs(LMK_ADJ_SYSFS, levels.joinToString(",") { it.adj.toString() }, shell)
            }
        }.getOrDefault(false)
    }

    private fun readProp(name: String, shell: Shell): Result<String> = runCatching {
        val out = ShellUtils.fastCmd(shell, "getprop '$name' 2>/dev/null").trim()
        check(out.isNotEmpty()) { "unreadable" }
        out
    }

    private fun levelsToJson(levels: List<LmkLevel>): String {
        val array = JSONArray()
        levels.forEach {
            array.put(JSONObject().put("pages", it.pages).put("adj", it.adj))
        }
        return array.toString()
    }

    private fun levelsFromJson(raw: String): List<LmkLevel>? {
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                LmkLevel(o.getLong("pages"), o.getLong("adj"))
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun saveLmkStock(levels: List<LmkLevel>) {
        prefs().edit().putString(KEY_LMK_STOCK, levelsToJson(levels)).apply()
    }

    private fun loadLmkStock(): List<LmkLevel>? {
        val raw = prefs().getString(KEY_LMK_STOCK, "").orEmpty()
        if (raw.isEmpty()) return null
        return levelsFromJson(raw)
    }

    private fun saveLmkStored(levels: List<LmkLevel>, shell: Shell) {
        prefs().edit().putString(KEY_LMK_JSON, levelsToJson(levels)).apply()
        syncBootScript(shell)
    }

    private fun loadLmkStored(): List<LmkLevel>? {
        val raw = prefs().getString(KEY_LMK_JSON, "").orEmpty()
        if (raw.isEmpty()) return null
        return levelsFromJson(raw)
    }

    /** Mirror applied levels into the stored config and refresh the boot script. */
    private fun storeLmkLevels(levels: List<LmkLevel>, shell: Shell) {
        prefs().edit().putString(KEY_LMK_JSON, levelsToJson(levels)).apply()
        syncBootScript(shell)
    }

    /** Boot-script lines for the stored LMK table. */
    private fun lmkBootLines(): List<String> {
        if (!prefs().getBoolean(KEY_LMK_PERSIST, false)) return emptyList()
        val levels = loadLmkStored() ?: return emptyList()
        if (levels.isEmpty()) return emptyList()
        val value = levels.joinToString(",") { "${it.pages}:${it.adj}" }
        val useProps = mutableState.value.lmk.useProps
        // Stored tables always carry adj values; fall back to the property
        // path when the driver nodes are absent (guarded by [ -e ]).
        return if (useProps) {
            listOf("setprop '$LMK_PROP' '$value'")
        } else {
            val minfree = levels.joinToString(",") { it.pages.toString() }
            val adj = levels.joinToString(",") { it.adj.toString() }
            listOf(
                "[ -e '$LMK_MINFREE_SYSFS' ] && echo '$minfree' > '$LMK_MINFREE_SYSFS'",
                "[ -e '$LMK_ADJ_SYSFS' ] && echo '$adj' > '$LMK_ADJ_SYSFS'",
                "setprop '$LMK_PROP' '$value'",
            )
        }
    }

    /**
     * Read-only diagnostics snapshot: load average plus PSI stall counters
     * for CPU, memory and I/O. Never fails the refresh — missing nodes
     * simply mark diagnostics unsupported.
     */
    private fun readDiagnosticsState(shell: Shell): DiagnosticsState {
        return runCatching {
            val load = ShellUtils.fastCmd(shell, "cat /proc/loadavg 2>/dev/null").trim()
            check(load.isNotEmpty()) { "no loadavg" }
            DiagnosticsState(
                supported = true,
                loadAvg = load.split(Regex("\\s+")).take(3).joinToString(" "),
                cpu = parsePsiStats(readPressure("cpu", shell)),
                memory = parsePsiStats(readPressure("memory", shell)),
                io = parsePsiStats(readPressure("io", shell)),
            )
        }.getOrDefault(DiagnosticsState())
    }

    /**
     * Read a /proc/pressure node. fastCmd returns only the last line, so
     * lines are joined with ';' for the multi-line parser below.
     */
    private fun readPressure(resource: String, shell: Shell): String {
        return ShellUtils.fastCmd(
            shell,
            "cat /proc/pressure/$resource 2>/dev/null | tr '\\n' ';'",
        )
    }

    private fun parsePsiStats(raw: String): PsiStats {
        var someAvg = 0.0
        var someTotal = 0L
        var fullAvg = 0.0
        var fullTotal = 0L
        // Lines look like: "some avg10=0.00 avg60=0.00 avg300=0.00 total=6537201".
        val pattern = Regex("(\\w+) avg10=([\\d.]+) avg60=[\\d.]+ avg300=[\\d.]+ total=(\\d+)")
        raw.split(';').forEach { entry ->
            val match = pattern.find(entry.trim()) ?: return@forEach
            val avg = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val total = match.groupValues[3].toLongOrNull() ?: 0L
            if (match.groupValues[1] == "full") {
                fullAvg = avg
                fullTotal = total
            } else {
                someAvg = avg
                someTotal = total
            }
        }
        return PsiStats(someAvg, someTotal, fullAvg, fullTotal)
    }

    private fun readZramState(prefs: android.content.SharedPreferences, shell: Shell): ZramState {
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
        val swappiness = readSysctl(SWAPPINESS_KEY, shell).getOrDefault("")
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
    private fun reinitZram(sizeBytes: Long, algo: String, streams: Long, shell: Shell): Boolean {
        return runCatching {
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
    private fun storeBoreValue(key: String, value: String, shell: Shell) {
        val persist = prefs().getBoolean(KEY_BORE_PERSIST, false)
        val updated = loadStored().filterNot { it.key == key } +
                SysctlEntry(key, value, persist)
        saveStored(updated)
        syncBootScript(shell)
    }

    suspend fun addOrUpdateSysctl(key: String, value: String, persist: Boolean): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                ksuCliRepository.createRootShell().use { shell ->
                upsertSysctl(key, value, persist, shell)
                }
            }
        }

    /**
     * Same as [addOrUpdateSysctl] but assumes [mutex] is already held, so ZRAM
     * and other composite operations can reuse it without deadlocking.
     */
    private suspend fun upsertSysctl(key: String, value: String, persist: Boolean, shell: Shell): Result<Unit> =
        runCatching {
            val cleanKey = key.trim()
            check(cleanKey.matches(KEY_PATTERN)) { "invalid key" }
            check(!value.contains('\n') && !value.contains('\'')) { "invalid value" }
            check(writeSysctl(cleanKey, value, shell)) { "sysctl write failed" }
            val updated = loadStored()
                .filterNot { it.key == cleanKey } + SysctlEntry(cleanKey, value, persist)
            saveStored(updated)
            syncBootScript(shell)
            mutableState.update {
                it.copy(sysctls = it.sysctls.filterNot { e -> e.key == cleanKey } +
                        SysctlEntry(cleanKey, value, persist))
            }
        }

    suspend fun removeSysctl(key: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                saveStored(loadStored().filterNot { it.key == key })
                syncBootScript(shell)
                mutableState.update { it.copy(sysctls = it.sysctls.filterNot { e -> e.key == key }) }
            }
            }
        }
    }

    suspend fun setSysctlPersist(key: String, persist: Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            ksuCliRepository.createRootShell().use { shell ->
            runCatching {
                val updated = loadStored().map {
                    if (it.key == key) it.copy(persist = persist) else it
                }
                saveStored(updated)
                syncBootScript(shell)
                mutableState.update {
                    it.copy(sysctls = it.sysctls.map { e ->
                        if (e.key == key) e.copy(persist = persist) else e
                    })
                }
            }
            }
        }
    }

    private fun readSysctl(key: String, shell: Shell): Result<String> = runCatching {
        val path = "/proc/sys/" + key.replace('.', '/')
        val out = ShellUtils.fastCmd(shell, "cat '$path' 2>/dev/null").trim()
        check(out.isNotEmpty()) { "unreadable" }
        out
    }

    private fun writeSysctl(key: String, value: String, shell: Shell): Boolean = runCatching {
        ShellUtils.fastCmdResult(shell, "sysctl -w '$key'='$value' >/dev/null 2>&1")
    }.getOrDefault(false)

    private fun readSysfs(path: String, shell: Shell): Result<String> = runCatching {
        val out = ShellUtils.fastCmd(shell, "cat '$path' 2>/dev/null").trim()
        check(out.isNotEmpty()) { "unreadable" }
        out
    }

    private fun writeSysfs(path: String, value: String, shell: Shell): Boolean = runCatching {
        check(!value.contains('\n') && !value.contains('\'')) { "invalid value" }
        ShellUtils.fastCmdResult(shell, "echo '$value' > '$path' 2>/dev/null")
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
    private fun syncBootScript(shell: Shell) {
        val lines = mutableListOf<String>()
        val p = prefs()
        lines.addAll(cpuBootLines())
        lines.addAll(gpuIoBootLines())
        lines.addAll(lmkBootLines())
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
