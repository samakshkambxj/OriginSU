package com.originsu.manager.domain.model

import com.originsu.manager.KernelVersion

data class HomeBasicInfo(
    val kernelRelease: String = "",
    val androidVersion: String = "",
    val deviceModel: String = "",
    val managerVersion: Triple<String, Int, Int> = Triple("", 0, 0),
    val selinuxStatus: String = "",
    val seccompStatus: Int = -1,
)

data class HomeModuleOverview(
    val count: Int = 0,
    val zygiskImplementation: String = "",
    val metaModuleImplementation: String = "",
)

data class HomeSystemInfo(
    val kernelRelease: String = "",
    val androidVersion: String = "",
    val deviceModel: String = "",
    val managerVersion: Triple<String, Int, Int> = Triple("", 0, 0),
    val selinuxStatus: String = "",
    val susfsEnabled: Boolean = false,
    val susfsVersionSupported: Boolean = false,
    val susfsVersion: String = "",
    val susfsFeatures: String = "",
    val superuserCount: Int = 0,
    val moduleCount: Int = 0,
    val kpmVersion: String = "",
    val kpmModuleCount: Int = 0,
    val isKpmEnabled: Boolean = false,
    val bbgEnabled: Boolean = false,
    val bbgVersion: String = "",
    val zeromountEnabled: Boolean = false,
    val zeromountVersion: String = "",
    val managersList: ManagerRuntimeInfo? = null,
    val isDynamicSignEnabled: Boolean = false,
    val zygiskImplement: String = "",
    val metaModuleImplement: String = "",
    val seccompStatus: Int = -1,
    val lastFlashTime: Long = 0L,
)

data class HomeDashboardState(
    val systemStatus: KernelStatus = KernelStatus(kernelVersion = KernelVersion(0, 0, 0)),
    val systemInfo: HomeSystemInfo = HomeSystemInfo(),
    val stableManagerUpdate: ManagerUpdateInfo? = null,
    val betaManagerUpdate: ManagerUpdateInfo? = null,
    val isBetaManagerUpdateCheckFailed: Boolean = false,
    val isSimpleMode: Boolean = false,
    val isThemedShortcutsEnabled: Boolean = false,
    val showNavigationBarBadge: Boolean = true,
    /** Names of [com.originsu.manager.ui.screen.BottomBarDestination] entries hidden from the navbar. */
    val hiddenNavigationBarTabs: Set<String> = emptySet(),
    val showHomeCardIcons: Boolean = false,
    val showUpdateManagerCard: Boolean = true,
    val isInitialDataLoaded: Boolean = false,
    val isCoreDataLoaded: Boolean = false,
    val isExtendedDataLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
)