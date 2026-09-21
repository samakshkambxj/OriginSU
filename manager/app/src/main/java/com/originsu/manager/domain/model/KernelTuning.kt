package com.originsu.manager.domain.model

data class SysctlEntry(
    val key: String,
    val value: String,
    val persist: Boolean = false,
)

/**
 * A single BORE scheduler tunable detected on the running kernel.
 * Range metadata comes from the upstream BORE documentation; only knobs
 * actually present under /proc/sys are ever surfaced.
 */
data class BoreKnob(
    val key: String,
    val value: String,
    val min: Long,
    val max: Long,
    val defaultValue: Long,
)

/**
 * Live ZRAM state read from sysfs. Only surfaced when /sys/block/zram0
 * exists on the running kernel.
 */
data class ZramState(
    val supported: Boolean = false,
    val disksizeBytes: Long = 0,
    val totalRamBytes: Long = 0,
    val currentAlgo: String = "",
    val algos: List<String> = emptyList(),
    val maxStreams: Long = 0,
    val origBytes: Long = 0,
    val comprBytes: Long = 0,
    val memUsedBytes: Long = 0,
    val swappiness: String = "",
    val persist: Boolean = false,
)

/**
 * One cpufreq policy (a cluster of CPUs sharing clock settings).
 * [id] is the sysfs node name ("policy0", or "cpu0" on kernels without
 * policy nodes); [cpus] is the human-readable affected-CPU list ("0-3").
 * Frequencies are in kHz, matching sysfs units.
 */
data class CpuPolicy(
    val id: String,
    val cpus: String = "",
    val governor: String = "",
    val availableGovernors: List<String> = emptyList(),
    val minFreqKhz: Long = 0,
    val maxFreqKhz: Long = 0,
    val cpuinfoMinKhz: Long = 0,
    val cpuinfoMaxKhz: Long = 0,
    /** Best-effort current frequency in kHz, 0 when unreadable. */
    val curFreqKhz: Long = 0,
)

/**
 * Live CPU frequency state. Only surfaced when the running kernel exposes
 * cpufreq sysfs (policy nodes, or per-CPU cpufreq directories).
 */
data class CpuState(
    val supported: Boolean = false,
    val policies: List<CpuPolicy> = emptyList(),
    val schedutilRateLimitUs: String = "",
    val schedutilSupported: Boolean = false,
    val persist: Boolean = false,
)

/**
 * One GPU clock domain: either a kGSL node (Adreno) or a devfreq node
 * whose name matches a GPU (e.g. Mali). [id] is the sysfs node name,
 * [label] the human-readable name. Frequencies are in Hz, matching sysfs
 * units. Governor and frequency lists may be empty when the kernel does
 * not expose them — only readable knobs are ever surfaced.
 */
data class GpuDevice(
    val id: String,
    val label: String = "",
    val governor: String = "",
    val availableGovernors: List<String> = emptyList(),
    val curFreqHz: Long = 0,
    val minFreqHz: Long = 0,
    val maxFreqHz: Long = 0,
    val availableFreqsHz: List<Long> = emptyList(),
)

/**
 * Live GPU state. Only surfaced when the running kernel exposes a GPU
 * clock node (kGSL or a GPU devfreq device).
 */
data class GpuState(
    val supported: Boolean = false,
    val devices: List<GpuDevice> = emptyList(),
    val persist: Boolean = false,
)

/**
 * One block device queue. [schedulerAvailable] is parsed from the
 * bracketed sysfs format ("[mq-deadline] kyber none").
 */
data class IoDevice(
    val name: String,
    val scheduler: String = "",
    val availableSchedulers: List<String> = emptyList(),
    val readAheadKb: Long = -1,
)

/**
 * Live block I/O state. Virtual devices (loop, ram, zram and device-mapper)
 * are skipped; only devices with a readable scheduler or read-ahead knob
 * are surfaced.
 */
data class IoState(
    val supported: Boolean = false,
    val devices: List<IoDevice> = emptyList(),
    val persist: Boolean = false,
)

/**
 * A single VM tunable detected on the running kernel. Range metadata is
 * conservative across kernel generations; only knobs actually present
 * under /proc/sys are ever surfaced.
 */
data class VmKnob(
    val key: String,
    val value: String,
    val min: Long,
    val max: Long,
    val defaultValue: Long,
)

/**
 * Live VM state: curated memory-preset knobs read from sysctl. Only
 * surfaced when at least one known knob is readable.
 */
data class VmState(
    val supported: Boolean = false,
    val persist: Boolean = false,
    val knobs: List<VmKnob> = emptyList(),
    /** Id of the VM preset matching current values, or "" for manual tweaks. */
    val profileId: String = "",
)

/**
 * A single scheduler-extra tunable (uclamp / energy-aware) detected on the
 * running kernel. Only knobs actually present under /proc/sys are ever
 * surfaced.
 */
data class SchedKnob(
    val key: String,
    val value: String,
    val min: Long,
    val max: Long,
    val defaultValue: Long,
)

/**
 * Live scheduler-extra state. Only surfaced when at least one known knob
 * is readable.
 */
data class SchedState(
    val supported: Boolean = false,
    val persist: Boolean = false,
    val knobs: List<SchedKnob> = emptyList(),
)

/**
 * One low-memory-killer level: free-memory threshold in pages (4K) plus
 * the oom_score_adj killed at that threshold.
 */
data class LmkLevel(
    val pages: Long,
    val adj: Long,
)

/**
 * Live LMK state. Modern kernels tune userspace lmkd through the
 * `sys.lmk.minfree_levels` property ("pages:adj,..."); older kernels with
 * the in-kernel lowmemorykiller driver use its minfree/adj module
 * parameters instead. Only surfaced when one of the two is available.
 */
data class LmkState(
    val supported: Boolean = false,
    /** True for the lmkd property path, false for the kernel driver path. */
    val useProps: Boolean = true,
    val levels: List<LmkLevel> = emptyList(),
    /** Id of the LMK preset matching current page thresholds, or "" for custom. */
    val profileId: String = "",
    val persist: Boolean = false,
)

/**
 * Pressure-stall counters for one resource, parsed from
 * /proc/pressure/<resource>. Averages are percentages over the last 10s;
 * totals are microseconds of stall time accumulated since boot.
 */
data class PsiStats(
    val someAvg10: Double = 0.0,
    val someTotal: Long = 0,
    val fullAvg10: Double = 0.0,
    val fullTotal: Long = 0,
)

/**
 * Read-only diagnostics snapshot: load average plus per-resource PSI
 * stalls, so the effect of a tuning is visible next to the knobs.
 */
data class DiagnosticsState(
    val supported: Boolean = false,
    val loadAvg: String = "",
    val cpu: PsiStats = PsiStats(),
    val memory: PsiStats = PsiStats(),
    val io: PsiStats = PsiStats(),
)

data class KernelTuningState(
    val tcpAvailable: List<String> = emptyList(),
    val tcpCurrent: String = "",
    val tcpPersist: Boolean = false,
    val boreSupported: Boolean = false,
    val boreEnabled: Boolean = false,
    val borePersist: Boolean = false,
    val boreKnobs: List<BoreKnob> = emptyList(),
    /** Id of the BORE preset matching current values, or "" for manual tweaks. */
    val boreProfileId: String = "",
    val zram: ZramState = ZramState(),
    val cpu: CpuState = CpuState(),
    val gpu: GpuState = GpuState(),
    val io: IoState = IoState(),
    val vm: VmState = VmState(),
    val sched: SchedState = SchedState(),
    val lmk: LmkState = LmkState(),
    val diagnostics: DiagnosticsState = DiagnosticsState(),
    val sysctls: List<SysctlEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)
