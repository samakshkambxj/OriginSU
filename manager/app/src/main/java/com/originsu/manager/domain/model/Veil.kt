package com.originsu.manager.domain.model

import com.originsu.manager.Natives

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class VeilKind(val id: Int) {
    Su(1),
    Magisk(2),
    Ksu(3),
    Modules(4),
    PkgList(5),
    Busybox(6),
    SuExec(7),
    Unknown(0),
    ;

    companion object {
        fun fromId(id: Int): VeilKind = entries.firstOrNull { it.id == id } ?: Unknown
    }
}

fun Int.toVeilKinds(): Set<VeilKind> = VeilKind.entries
    .filter { it != VeilKind.Unknown && (this and (1 shl (it.id - 1))) != 0 }
    .toSet()

data class VeilCloakedUid(
    val uid: Int,
    val userName: String?,
)

data class VeilProbeHistory(
    val uid: Int,
    val userName: String?,
    val count: Int,
    val kinds: Set<VeilKind>,
    val lastNs: Long,
)

data class VeilState(
    val status: String = "",
    val enabled: Boolean = false,
    val autoCloak: Boolean = false,
    val cloakedUids: List<VeilCloakedUid> = emptyList(),
    val history: List<VeilProbeHistory> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

fun Natives.VeilHistoryEntry.toProbeHistory(userName: String?): VeilProbeHistory =
    VeilProbeHistory(
        uid = uid,
        userName = userName,
        count = count,
        kinds = kinds.toVeilKinds(),
        lastNs = lastNs,
    )

fun veilTimestampText(
    lastNs: Long,
    currentTimeMillis: Long,
    uptimeMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    if (lastNs < 0 || uptimeMillis < 0) return null
    val eventTimeMillis = currentTimeMillis - uptimeMillis + lastNs / 1_000_000L
    if (eventTimeMillis < 0) return null
    return Instant.ofEpochMilli(eventTimeMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
}
