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
    val sysctls: List<SysctlEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)
