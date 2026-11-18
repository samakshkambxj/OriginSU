package com.kazesu.manager.ai.host

import com.kazesu.manager.Natives
import com.kazesu.manager.data.repository.ModuleRepositoryImpl
import com.kazesu.manager.data.repository.SuperUserRepositoryImpl
import com.kazesu.manager.ui.util.rootAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject


object AiManagerData {

    private val aiModuleActions = listOf(
        "search_registry", "search_web", "search_github",
        "list_installed", "install_registry", "install_github",
    )
    private val manualModuleActions = listOf(
        "enable", "disable", "uninstall", "undo_uninstall",
        "run_action", "open_webui", "check_update",
    )

    
    fun moduleCapabilities(): String = buildJsonObject {
        put("ai_actions", buildJsonArray { aiModuleActions.forEach { add(it) } })
        put("manual_ui_actions", buildJsonArray { manualModuleActions.forEach { add(it) } })
        put(
            "note",
            "Only search, list and install are executable by the assistant. Enable/disable, " +
                "uninstall/undo, action scripts, WebUI and update checks are manual actions on the " +
                "Modules page; never claim to have performed them.",
        )
    }.toString()

    suspend fun superuserApps(limit: Int = 80): String = withContext(Dispatchers.IO) {
        check(runCatching { Natives.isManager }.getOrDefault(false)) {
            "Superuser data is unavailable: no KazeSU kernel is active. Install KazeSU from the Install page and reboot."
        }
        check(runCatching { rootAvailable() }.getOrDefault(false)) {
            "Superuser data is unavailable: the manager does not have root. Grant root in Superuser, or repair KazeSU."
        }
        val apps = SuperUserRepositoryImpl().getAppList().getOrThrow().first
        val granted = apps.filter { it.allowSu }
        buildJsonObject {
            put("total_granted", granted.size)
            put("apps", buildJsonArray {
                granted.take(limit.coerceIn(1, 200)).forEach { app ->
                    add(buildJsonObject {
                        put("package", app.packageName)
                        put("label", app.label)
                        put("uid", app.uid)
                        put("allow_su", app.allowSu)
                        put("custom_profile", app.hasCustomProfile)
                    })
                }
            })
        }.toString()
    }

    suspend fun installedModules(limit: Int = 80): String = withContext(Dispatchers.IO) {
        check(runCatching { Natives.isManager }.getOrDefault(false)) {
            "Installed modules are unavailable: no KazeSU kernel is active. Install KazeSU from the Install page and reboot."
        }
        check(runCatching { rootAvailable() }.getOrDefault(false)) {
            "Installed modules are unavailable: the manager does not have root. Grant root or repair KazeSU first."
        }
        val modules = ModuleRepositoryImpl().getModules().getOrThrow()
        buildJsonObject {
            put("count", modules.size)
            put("ai_actions", buildJsonArray { aiModuleActions.forEach { add(it) } })
            put("manual_ui_actions", buildJsonArray { manualModuleActions.forEach { add(it) } })
            put("modules", buildJsonArray {
                modules.take(limit.coerceIn(1, 200)).forEach { module ->
                    add(buildJsonObject {
                        put("id", module.id)
                        put("name", module.name)
                        put("version", module.version)
                        put("version_code", module.versionCode)
                        put("enabled", module.enabled)
                        put("update", module.update)
                        put("remove", module.remove)
                        put("zygisk", module.zygisk)
                        put("metamodule", module.metamodule)
                        put("webui", module.hasWebUi)
                        put("action", module.hasActionScript)
                        put("description", module.description.take(280))
                        putJsonObject("capabilities") {
                            put("enable", true)
                            put("disable", true)
                            put("uninstall", true)
                            put("run_action", module.hasActionScript)
                            put("open_webui", module.hasWebUi)
                            put("update_check", !module.remove)
                        }
                    })
                }
            })
        }.toString()
    }
}
