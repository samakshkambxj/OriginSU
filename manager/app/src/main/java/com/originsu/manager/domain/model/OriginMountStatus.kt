package com.originsu.manager.domain.model

/** The provider-selection mode persisted by OriginMount. */
enum class OriginMountMode(val id: String) {
    Auto("auto"),
    Builtin("builtin"),
    Metamodule("metamodule"),
    Zeromount("zeromount"),
    Nomount("nomount"),
    Off("off");

    companion object {
        fun fromId(id: String): OriginMountMode =
            entries.firstOrNull { it.id == id } ?: Auto
    }
}

/** One boot's provider attempt, written by ksud after mounting. */
data class OriginMountRuntime(
    val bootId: String = "",
    val provider: String = "",
    val configured: String = "",
    val result: String = "",
    val targetCount: Int? = null,
    val error: String = "",
    val warning: String = "",
    val warningCode: String = "",
)

/** Compact NoMount driver summary embedded in the OriginMount status. */
data class OriginMountNoMountStatus(
    val driverState: String = "absent",
    val driverVersion: String = "",
    val rulesExpected: Int = 0,
    val rulesApplied: Int = 0,
    val uidCount: Int = 0,
    val isolatedBlocked: Boolean = false,
    val deferredTo: String = "",
    val ownsInjection: Boolean = false,
    val unavailableCode: String = "",
    val unavailableDetail: String = "",
    val error: String = "",
) {
    val isReady: Boolean
        get() = driverState == "ready"

    val isDeferred: Boolean
        get() = deferredTo.isNotBlank()
}

/** Snapshot returned by `ksud origin-mount status --json`. */
data class OriginMountStatus(
    val mode: OriginMountMode = OriginMountMode.Auto,
    val provider: String = "none",
    val metamoduleId: String = "",
    val magicMountEnabled: Boolean = false,
    val backend: String = "auto",
    val kernelEngines: List<String> = emptyList(),
    val runtime: OriginMountRuntime? = null,
    val nomount: OriginMountNoMountStatus? = null,
) {
    val isKnown: Boolean
        get() = provider != "none" || runtime != null || nomount != null
}

/** Stored and active NoMount UID exclusions. */
data class NomountUidState(
    val active: List<Int> = emptyList(),
    val stored: List<Int> = emptyList(),
    val isolatedBlocked: Boolean = false,
) {
    val selected: Set<Int>
        get() = stored.toSet()
}
