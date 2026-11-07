package com.originsu.manager.data.curtain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed view over the HMA-OSS `config.json` model.
 *
 * Only the keys Origin Curtain edits are modelled here. Reads and writes always
 * go through [CurtainConfigCodec], which mutates the original document in place
 * so options we do not surface (settings presets, disabled hooks, zygote
 * permission lists, future upstream keys) survive untouched.
 */
data class CurtainGlobalConfig(
    val forceMountData: Boolean = true,
    val disableActivityLaunchProtection: Boolean = false,
    val altAppDataIsolation: Boolean = false,
    val altVoldAppDataIsolation: Boolean = false,
    val skipSystemAppDataIsolation: Boolean = true,
    val detailLog: Boolean = false,
    val errorOnlyLog: Boolean = false,
    val maxLogSize: Int = 512,
)

/** Mirrors HMA-OSS `JsonConfig.AppConfig`. */
data class CurtainAppConfig(
    val useWhitelist: Boolean = false,
    val excludeSystemApps: Boolean = true,
    val hideInstallationSource: Boolean = false,
    val hideSystemInstallationSource: Boolean = false,
    val excludeTargetInstallationSource: Boolean = false,
    val invertActivityLaunchProtection: Boolean = false,
    val excludeVoldIsolation: Boolean = false,
    val restrictedZygotePermissions: List<Int> = emptyList(),
    val applyTemplates: Set<String> = emptySet(),
    val extraAppList: Set<String> = emptySet(),
    val extraOppositeAppList: Set<String> = emptySet(),
)

/** Mirrors HMA-OSS `JsonConfig.Template`. */
data class CurtainTemplate(
    val name: String,
    val isWhitelist: Boolean = false,
    val appList: Set<String> = emptySet(),
)

data class CurtainConfig(
    val configVersion: Int = 0,
    val global: CurtainGlobalConfig = CurtainGlobalConfig(),
    val apps: Map<String, CurtainAppConfig> = emptyMap(),
    val templates: Map<String, CurtainTemplate> = emptyMap(),
    val ignoredPackagesForPresets: Set<String> = emptySet(),
)

/** An installed app as shown by the pickers. */
data class CurtainInstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

object CurtainConfigCodec {

    fun parse(raw: JSONObject): CurtainConfig = CurtainConfig(
        configVersion = raw.optInt("configVersion", 0),
        global = CurtainGlobalConfig(
            forceMountData = raw.optBoolean("forceMountData", true),
            disableActivityLaunchProtection =
                raw.optBoolean("disableActivityLaunchProtection", false),
            altAppDataIsolation = raw.optBoolean("altAppDataIsolation", false),
            altVoldAppDataIsolation = raw.optBoolean("altVoldAppDataIsolation", false),
            skipSystemAppDataIsolation = raw.optBoolean("skipSystemAppDataIsolation", true),
            detailLog = raw.optBoolean("detailLog", false),
            errorOnlyLog = raw.optBoolean("errorOnlyLog", false),
            maxLogSize = raw.optInt("maxLogSize", 512),
        ),
        apps = raw.optJSONObject("scope").orEmptyObject().let { scope ->
            buildMap {
                scope.keys().forEach { packageName ->
                    scope.optJSONObject(packageName)?.let { put(packageName, parseApp(it)) }
                }
            }
        },
        templates = raw.optJSONObject("templates").orEmptyObject().let { templates ->
            buildMap {
                templates.keys().forEach { name ->
                    templates.optJSONObject(name)?.let { put(name, parseTemplate(name, it)) }
                }
            }
        },
        ignoredPackagesForPresets = raw.stringSet("ignoredPackagesForPresets"),
    )

    fun writeGlobal(raw: JSONObject, global: CurtainGlobalConfig) {
        raw.put("forceMountData", global.forceMountData)
        raw.put("disableActivityLaunchProtection", global.disableActivityLaunchProtection)
        raw.put("altAppDataIsolation", global.altAppDataIsolation)
        raw.put("altVoldAppDataIsolation", global.altVoldAppDataIsolation)
        raw.put("skipSystemAppDataIsolation", global.skipSystemAppDataIsolation)
        raw.put("detailLog", global.detailLog)
        raw.put("errorOnlyLog", global.errorOnlyLog)
        raw.put("maxLogSize", global.maxLogSize)
    }

    fun writeApp(raw: JSONObject, packageName: String, app: CurtainAppConfig) {
        val scope = raw.optJSONObject("scope") ?: JSONObject().also { raw.put("scope", it) }
        // Reuse the existing object so keys this screen does not model (settings
        // preset selections, for example) are preserved.
        val entry = scope.optJSONObject(packageName) ?: JSONObject()
        entry.put("useWhitelist", app.useWhitelist)
        entry.put("excludeSystemApps", app.excludeSystemApps)
        entry.put("hideInstallationSource", app.hideInstallationSource)
        entry.put("hideSystemInstallationSource", app.hideSystemInstallationSource)
        entry.put("excludeTargetInstallationSource", app.excludeTargetInstallationSource)
        entry.put("invertActivityLaunchProtection", app.invertActivityLaunchProtection)
        entry.put("excludeVoldIsolation", app.excludeVoldIsolation)
        entry.put(
            "restrictedZygotePermissions",
            JSONArray(app.restrictedZygotePermissions.distinct().sorted()),
        )
        entry.put("applyTemplates", JSONArray(app.applyTemplates.sorted()))
        entry.put("extraAppList", JSONArray(app.extraAppList.sorted()))
        entry.put("extraOppositeAppList", JSONArray(app.extraOppositeAppList.sorted()))
        scope.put(packageName, entry)
    }

    fun removeApp(raw: JSONObject, packageName: String) {
        raw.optJSONObject("scope")?.remove(packageName)
    }

    fun writeTemplate(
        raw: JSONObject,
        template: CurtainTemplate,
        previousName: String? = null,
    ) {
        val templates =
            raw.optJSONObject("templates") ?: JSONObject().also { raw.put("templates", it) }
        if (previousName != null && previousName != template.name) {
            templates.remove(previousName)
        }
        templates.put(
            template.name,
            JSONObject().apply {
                put("isWhitelist", template.isWhitelist)
                put("appList", JSONArray(template.appList.sorted()))
            },
        )
    }

    fun removeTemplate(raw: JSONObject, name: String) {
        raw.optJSONObject("templates")?.remove(name)
    }

    private fun parseApp(raw: JSONObject) = CurtainAppConfig(
        useWhitelist = raw.optBoolean("useWhitelist", false),
        excludeSystemApps = raw.optBoolean("excludeSystemApps", true),
        hideInstallationSource = raw.optBoolean("hideInstallationSource", false),
        hideSystemInstallationSource = raw.optBoolean("hideSystemInstallationSource", false),
        excludeTargetInstallationSource = raw.optBoolean("excludeTargetInstallationSource", false),
        invertActivityLaunchProtection = raw.optBoolean("invertActivityLaunchProtection", false),
        excludeVoldIsolation = raw.optBoolean("excludeVoldIsolation", false),
        restrictedZygotePermissions = raw.intList("restrictedZygotePermissions"),
        applyTemplates = raw.stringSet("applyTemplates"),
        extraAppList = raw.stringSet("extraAppList"),
        extraOppositeAppList = raw.stringSet("extraOppositeAppList"),
    )

    private fun parseTemplate(name: String, raw: JSONObject) = CurtainTemplate(
        name = name,
        isWhitelist = raw.optBoolean("isWhitelist", false),
        appList = raw.stringSet("appList"),
    )

    private fun JSONObject?.orEmptyObject(): JSONObject = this ?: JSONObject()

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun JSONObject.intList(key: String): List<Int> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }
    }
}
