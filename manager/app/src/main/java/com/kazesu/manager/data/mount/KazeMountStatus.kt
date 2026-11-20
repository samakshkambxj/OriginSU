package com.kazesu.manager.data.mount

import org.json.JSONObject

/** Provider mode persisted by ksud. Ids are part of the CLI/config ABI. */
enum class KazeMountMode(val id: String) {
    Auto("auto"),
    MagicMount("builtin"),
    OverlayFs("metamodule"),
    ZeroMount("zeromount"),
    NoMount("nomount"),
    Off("off");

    companion object {
        fun fromId(id: String): KazeMountMode = entries.firstOrNull { it.id == id } ?: Auto
    }
}

val MAGIC_MOUNT_BACKENDS = listOf("auto", "overlay", "bind")

data class KazeMountRuntime(
    val bootId: String = "",
    val provider: String = "",
    val configured: String = "",
    val result: String = "",
    val targetCount: Int? = null,
    val error: String = "",
    val warning: String = "",
    val warningCode: String = "",
)

data class NoMountStatus(
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
)

data class KazeMountStatus(
    val available: Boolean = false,
    val mode: KazeMountMode = KazeMountMode.Auto,
    val provider: String = "none",
    val metamoduleId: String = "",
    val magicMountEnabled: Boolean = false,
    val backend: String = "auto",
    val kernelEngines: List<String> = emptyList(),
    val runtime: KazeMountRuntime? = null,
    val nomount: NoMountStatus? = null,
)

data class NoMountUidState(
    val active: List<Int> = emptyList(),
    val stored: List<Int> = emptyList(),
    val isolatedBlocked: Boolean = false,
)

private fun jsonPayload(output: String): String? = output.lineSequence()
    .map { it.trim() }
    .lastOrNull { it.startsWith("{") && it.endsWith("}") }

fun parseKazeMountStatus(output: String): KazeMountStatus {
    val payload = jsonPayload(output) ?: return KazeMountStatus()
    return runCatching {
        val root = JSONObject(payload)
        val runtime = root.optJSONObject("runtime")?.let { json ->
            KazeMountRuntime(
                bootId = json.optString("boot_id"),
                provider = json.optString("provider"),
                configured = json.optString("configured"),
                result = json.optString("result"),
                targetCount = if (json.has("target_count") && !json.isNull("target_count")) {
                    json.optInt("target_count")
                } else {
                    null
                },
                error = json.optString("error"),
                warning = json.optString("warning"),
                warningCode = json.optString("warning_code"),
            )
        }
        val nomount = root.optJSONObject("nomount")?.let { json ->
            NoMountStatus(
                driverState = json.optString("driver_state", "absent"),
                driverVersion = json.optString("driver_version"),
                rulesExpected = json.optInt("rules_expected"),
                rulesApplied = json.optInt("rules_applied"),
                uidCount = json.optInt("uid_count"),
                isolatedBlocked = json.optBoolean("isolated_blocked"),
                deferredTo = json.optString("deferred_to"),
                ownsInjection = json.optBoolean("owns_injection"),
                unavailableCode = json.optString("unavailable_code"),
                unavailableDetail = json.optString("unavailable_detail"),
                error = json.optString("error"),
            )
        }
        KazeMountStatus(
            available = true,
            mode = KazeMountMode.fromId(root.optString("mode", "auto")),
            provider = root.optString("provider", "none"),
            metamoduleId = root.optString("metamodule"),
            magicMountEnabled = root.optBoolean("magic_mount"),
            backend = root.optString("backend", "auto")
                .takeIf { it in MAGIC_MOUNT_BACKENDS } ?: "auto",
            kernelEngines = root.optJSONArray("kernel")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            }.orEmpty(),
            runtime = runtime,
            nomount = nomount,
        )
    }.getOrDefault(KazeMountStatus())
}

fun parseNoMountUidState(output: String): NoMountUidState {
    val payload = jsonPayload(output) ?: return NoMountUidState()
    return runCatching {
        val root = JSONObject(payload)
        fun uids(name: String): List<Int> = root.optJSONArray(name)?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optInt(index, -1).takeIf { it >= 0 }
            }
        }.orEmpty()
        NoMountUidState(
            active = uids("active"),
            stored = uids("stored"),
            isolatedBlocked = root.optBoolean("isolated_blocked"),
        )
    }.getOrDefault(NoMountUidState())
}
