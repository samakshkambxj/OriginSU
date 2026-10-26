package com.originsu.manager.domain.model

/**
 * Which engine currently owns module mounting.
 *
 * [owner] is one of `none`, `metamodule`, `magic_mount`, or `unknown` when an
 * older ksud did not report engine information.
 */
data class MountEngineInfo(
    val owner: String = "unknown",
    val engine: String = "",
    val name: String = "",
    val metamoduleId: String = "",
    val backend: String = "auto",
    val magicMountEnabled: Boolean = false,
    val kernelEngines: List<String> = emptyList(),
) {
    val metamoduleOwnsMounting: Boolean
        get() = owner == "metamodule"

    val isKnown: Boolean
        get() = owner != "unknown"
}
