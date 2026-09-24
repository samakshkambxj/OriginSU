package com.originsu.manager.data.kernel

import android.app.Application
import com.originsu.manager.Natives
import com.originsu.manager.Natives.KernelPatchImplementation
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.data.system.isSELinuxPermissive
import com.originsu.manager.domain.model.KernelFeatureSettings
import com.originsu.manager.domain.model.KernelStatus
import com.originsu.manager.domain.model.ManagerRecord
import com.originsu.manager.domain.model.ManagerRuntimeInfo
import com.originsu.manager.getKernelVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KernelRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun getStatus(): KernelStatus = withContext(Dispatchers.IO) {
        val kernelVersion = getKernelVersion()
        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        // Report the driver version even when this manager is not recognized
        // (e.g. a kernel or LKM built by another KernelSU fork): the version
        // handshake needs no manager rights, and the UI uses it to offer a
        // best-effort compat mode instead of "not installed".
        val ksuVersion = runCatching { Natives.version }.getOrNull()?.takeIf { it > 0 }
        val kernelUapi = runCatching { Natives.kernelUAPIVersion }.getOrNull()
        val managerUapi = runCatching { Natives.managerUAPIVersion }.getOrDefault(1)
        val fullVersion = runCatching { Natives.getFullVersion() }.getOrDefault("Unknown")
        val isRootAvailable = runCatching { ksuCliRepository.rootAvailable() }.getOrDefault(false)
        KernelStatus(
            isManager = isManager,
            ksuVersion = ksuVersion,
            managerUAPIVersion = managerUapi,
            kernelUAPIVersion = kernelUapi,
            ksuFullVersion = "$fullVersion (${ksuVersion ?: "?"}/$kernelUapi)",
            lkmMode = ksuVersion?.let {
                if (kernelVersion.isGKI()) {
                    runCatching { Natives.isLkmMode }.getOrNull()
                } else {
                    null
                }
            },
            kernelVersion = kernelVersion,
            isRootAvailable = isRootAvailable,
            isFullFeatured = isRootAvailable && runCatching { Natives.isFullFeatured() }
                .getOrDefault(false),
            isSELinuxPermissive = runCatching { isSELinuxPermissive() }.getOrDefault(false),
            isOfficialSignature = runCatching {
                ksuCliRepository.isOfficialSignature(application.packageResourcePath)
            }.getOrDefault(false),
            kernelPatchImplementation = runCatching {
                Natives.getKernelPatchImplementation()
            }.getOrDefault(KernelPatchImplementation.NONE),
            hookType = runCatching { Natives.getHookType() }.getOrDefault(""),
            isSafeMode = runCatching { Natives.isSafeMode }.getOrDefault(false),
            isLateLoadMode = runCatching { Natives.isLateLoadMode }.getOrDefault(false),
            isPrBuild = runCatching { Natives.isPrBuild }.getOrDefault(false),
        )
    }

    suspend fun getManagerRuntimeInfo(): ManagerRuntimeInfo = withContext(Dispatchers.IO) {
        ManagerRuntimeInfo(
            managers = runCatching { Natives.getManagersList()?.managers.orEmpty() }
                .getOrDefault(emptyList())
                .map { ManagerRecord(it.uid, it.signatureIndex) },
            dynamicSignatureEnabled = runCatching {
                Natives.getDynamicManager()?.isValid() == true
            }.getOrDefault(false),
        )
    }

    suspend fun getFeatureSettings(): KernelFeatureSettings = withContext(Dispatchers.IO) {
        KernelFeatureSettings(
            suEnabled = runCatching { Natives.isSuEnabled() }.getOrDefault(false),
            kernelUmountEnabled = runCatching { Natives.isKernelUmountEnabled() }.getOrDefault(false),
            suLogEnabled = runCatching { Natives.isSuLogEnabled() }.getOrDefault(false),
            selinuxHideEnabled = runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(false),
            veilEnabled = runCatching { Natives.isVeilEnabled() }.getOrDefault(false),
            defaultUmountModules = runCatching { Natives.isDefaultUmountModules() }.getOrDefault(
                false
            ),
        )
    }

    suspend fun setSuEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setSuEnabled(enabled)
    }

    suspend fun setKernelUmountEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setKernelUmountEnabled(enabled)
    }

    suspend fun setSuLogEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setSuLogEnabled(enabled)
    }

    suspend fun setVeilEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setVeilEnabled(enabled)
    }

    suspend fun setSelinuxHideEnabled(enabled: Boolean): Int = withContext(Dispatchers.IO) {
        Natives.setSelinuxHideEnabled(enabled).also {
            ksuCliRepository.execKsud("feature save", true)
        }
    }

    suspend fun setDefaultUmountModules(enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) { Natives.setDefaultUmountModules(enabled) }

    fun isLateLoadMode(): Boolean = runCatching { Natives.isLateLoadMode }.getOrDefault(false)

    private suspend fun saveFeature(block: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        block().also { success ->
            if (success) ksuCliRepository.execKsud("feature save", true)
        }
    }
}
