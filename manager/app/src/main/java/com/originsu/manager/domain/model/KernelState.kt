package com.originsu.manager.domain.model

import com.originsu.manager.KernelVersion
import com.originsu.manager.Natives.KernelPatchImplementation

data class ManagerRecord(
    val uid: Int,
    val signatureIndex: Int,
)

data class ManagerRuntimeInfo(
    val managers: List<ManagerRecord> = emptyList(),
    val dynamicSignatureEnabled: Boolean = false,
)

data class KernelStatus(
    val isManager: Boolean = false,
    val ksuVersion: Int? = null,
    val managerUAPIVersion: Int = 1,
    val kernelUAPIVersion: Int? = 1,
    val ksuFullVersion: String? = null,
    val lkmMode: Boolean? = null,
    val kernelVersion: KernelVersion,
    val isRootAvailable: Boolean = false,
    val isFullFeatured: Boolean = false,
    val isSELinuxPermissive: Boolean = false,
    val isOfficialSignature: Boolean = true,
    val kernelPatchImplementation: KernelPatchImplementation = KernelPatchImplementation.NONE,
    val hookType: String = "",
    val isSafeMode: Boolean = false,
    val isLateLoadMode: Boolean = false,
    val isPrBuild: Boolean = false,
)

/**
 * A KernelSU driver was detected, no matter which fork built it.
 */
val KernelStatus.isKernelPresent: Boolean
    get() = ksuVersion != null

/**
 * The running kernel reports a KernelSU driver but does not recognize this
 * manager (e.g. a kernel or LKM built by another KernelSU fork such as the
 * official KernelSU, MKSU, RKSU, SukiSU or ReSukiSU). Native manager-only
 * ioctls are unavailable there; whatever works goes through the root shell.
 */
val KernelStatus.isForeignKernel: Boolean
    get() = isKernelPresent && !isManager

/**
 * Foreign (or UAPI-mismatched) kernel with root granted to this manager:
 * core flows (modules, flashing/LKM install, reboot) work, while
 * Origin-exclusive features stay hidden until an OriginSU kernel runs.
 */
val KernelStatus.isCompatMode: Boolean
    get() = isKernelPresent && !isFullFeatured && isRootAvailable

/**
 * Whether the core manager flows (Superuser/Module tabs, reboot, flashing)
 * should be enabled: full access on an OriginSU kernel, best-effort compat
 * access on other forks' kernels once root is granted.
 */
val KernelStatus.hasCoreAccess: Boolean
    get() = isFullFeatured || isCompatMode

data class KernelFeatureSettings(
    val suEnabled: Boolean,
    val kernelUmountEnabled: Boolean,
    val suLogEnabled: Boolean,
    val selinuxHideEnabled: Boolean,
    val veilEnabled: Boolean,
    val defaultUmountModules: Boolean,
)
