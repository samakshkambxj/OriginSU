package com.originsu.manager.data.model

import org.json.JSONObject

/**
 * One report from `ksud sui status --json`.
 *
 * The expectation is what the user asked for; [state] is what ksud derived from the payload,
 * the Zygisk provider and the running daemons. They are deliberately separate.
 */
data class SuiStatus(
    val state: String,
    val reason: String,
    val detail: String,
    val expectation: Boolean,
    val installed: Boolean,
    val payloadVersion: String,
    val payloadCurrent: Boolean,
    val moduleDisabled: Boolean,
    val rebootRequired: Boolean,
    val conflicts: List<String>,
    val zygiskEnabled: Boolean,
    val engineAvailable: Boolean,
    val injectionState: String,
    val externalProviders: List<String>,
    val daemonHealthy: Boolean,
) {
    /** True while Sui is expected to run, including states that only take effect after a reboot. */
    val selected: Boolean
        get() = state == STATE_ENABLED ||
            state == STATE_PENDING_REBOOT ||
            state == STATE_EXTERNAL_UNVERIFIED

    val verified: Boolean get() = state == STATE_ENABLED

    /** The card only expands into per-fact rows when something needs the user's attention. */
    val needsDiagnostics: Boolean
        get() = state == STATE_ERROR ||
            state == STATE_UNAVAILABLE ||
            state == STATE_EXTERNAL_UNVERIFIED

    companion object {
        const val SCHEMA_VERSION = 1
        const val MODULE_ID = "originsu-sui"
        const val STATE_DISABLED = "disabled"
        const val STATE_ENABLED = "enabled"
        const val STATE_PENDING_REBOOT = "enabled_pending_reboot"
        const val STATE_EXTERNAL_UNVERIFIED = "enabled_external_unverified"
        const val STATE_UNAVAILABLE = "unavailable"
        const val STATE_ERROR = "error"

        private val STATES = setOf(
            STATE_DISABLED,
            STATE_ENABLED,
            STATE_PENDING_REBOOT,
            STATE_EXTERNAL_UNVERIFIED,
            STATE_UNAVAILABLE,
            STATE_ERROR,
        )

        fun fromJson(json: JSONObject): SuiStatus {
            check(json.get("schema_version") == SCHEMA_VERSION) { "Unsupported Sui schema" }
            fun string(key: String): String =
                json.get(key) as? String ?: error("Invalid Sui $key")
            fun boolean(key: String): Boolean =
                json.get(key) as? Boolean ?: error("Invalid Sui $key")
            fun strings(key: String): List<String> {
                val array = json.getJSONArray(key)
                return List(array.length()) { index ->
                    array.get(index) as? String ?: error("Invalid Sui $key")
                }
            }
            val state = string("state")
            check(state in STATES) { "Unknown Sui state" }
            return SuiStatus(
                state = state,
                reason = string("reason"),
                detail = string("detail"),
                expectation = boolean("expectation"),
                installed = boolean("installed"),
                payloadVersion = string("payload_version"),
                payloadCurrent = boolean("payload_current"),
                moduleDisabled = boolean("module_disabled"),
                rebootRequired = boolean("reboot_required"),
                conflicts = strings("conflicts"),
                zygiskEnabled = boolean("zygisk_enabled"),
                engineAvailable = boolean("engine_available"),
                injectionState = string("injection_state"),
                externalProviders = strings("external_providers"),
                daemonHealthy = boolean("daemon_healthy"),
            )
        }
    }
}
