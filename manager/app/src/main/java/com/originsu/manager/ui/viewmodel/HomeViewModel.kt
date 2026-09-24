package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.data.count.CountRepository
import com.originsu.manager.data.module.ModuleRepository
import com.originsu.manager.data.packageinfo.SuperUserRepository
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.data.system.HomeStateRepository
import com.originsu.manager.domain.model.HomeDashboardState
import com.originsu.manager.domain.model.HomeSystemInfo
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.usecase.CheckManagerUpdateUseCase
import com.originsu.manager.domain.usecase.GetBooleanPreferenceUseCase
import com.originsu.manager.domain.usecase.GetStringSetPreferenceUseCase
import com.originsu.manager.domain.usecase.GetLongPreferenceUseCase
import com.originsu.manager.domain.usecase.LAST_FLASH_PREF_KEY
import com.originsu.manager.domain.usecase.THEMED_SHORTCUTS_PREF_KEY
import com.originsu.manager.domain.usecase.GetHomeBasicInfoUseCase
import com.originsu.manager.domain.usecase.GetKernelStatusUseCase
import com.originsu.manager.domain.usecase.GetManagerRuntimeInfoUseCase
import com.originsu.manager.domain.usecase.GetSuSFSStatusUseCase
import com.originsu.manager.domain.usecase.IsNetworkAvailableUseCase
import com.originsu.manager.domain.usecase.RebootUseCase
import com.originsu.manager.domain.usecase.SetBooleanPreferenceUseCase
import com.originsu.manager.domain.usecase.SetStringSetPreferenceUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias HomeUiState = HomeDashboardState

sealed interface HomeUiAction {
    data object AwaitInitialData : HomeUiAction
    data class Refresh(val showIndicator: Boolean = true) : HomeUiAction
    data class SetSimpleMode(val enabled: Boolean) : HomeUiAction
    data class SetNavigationBarBadge(val enabled: Boolean) : HomeUiAction
    data class SetNavigationBarTabs(val hiddenTabs: Set<String>) : HomeUiAction
    data class SetHomeCardIcons(val enabled: Boolean) : HomeUiAction
    data class SetUpdateManagerCard(val enabled: Boolean) : HomeUiAction
    data class Reboot(val reason: String) : HomeUiAction
}

sealed interface HomeUiEvent {
    data class Error(val message: String) : HomeUiEvent
}

class HomeViewModel(
    val homeStateRepository: HomeStateRepository,
    appSettingsRepository: AppSettingsRepository,
    superUserRepository: SuperUserRepository,
    moduleRepository: ModuleRepository,
    private val countRepository: CountRepository,
    private val ksuCliRepository: KsuCliRepository,
    private val checkManagerUpdate: CheckManagerUpdateUseCase,
    private val getKernelStatus: GetKernelStatusUseCase,
    private val getManagerRuntimeInfo: GetManagerRuntimeInfoUseCase,
    private val getSuSFSStatus: GetSuSFSStatusUseCase,
    private val getBasicInfo: GetHomeBasicInfoUseCase,
    private val isNetworkAvailable: IsNetworkAvailableUseCase,
    private val getBooleanPreference: GetBooleanPreferenceUseCase,
    private val setBooleanPreference: SetBooleanPreferenceUseCase,
    private val getStringSetPreference: GetStringSetPreferenceUseCase,
    private val setStringSetPreference: SetStringSetPreferenceUseCase,
    private val getLongPreference: GetLongPreferenceUseCase,
    private val reboot: RebootUseCase,
) : ViewModel() {
    val uiState = combine(
        homeStateRepository.state,
        superUserRepository.state,
        moduleRepository.installedModules,
        countRepository.state,
    ) { homeState, superUserState, moduleState, countState ->
        val superuserCount = if (superUserState.groups.isNotEmpty()) {
            superUserState.groups.filter { it.allowSu }.size
        } else {
            countState.superuserCount
        }
        val moduleCount = if (moduleState.modules.isNotEmpty()) {
            moduleState.modules.size
        } else {
            countState.moduleCount
        }
        homeState.copy(
            systemInfo = homeState.systemInfo.copy(
                moduleCount = moduleCount,
                superuserCount = superuserCount,
                zygiskImplement = ksuCliRepository.getZygiskImplement(),
                metaModuleImplement = ksuCliRepository.getMetaModuleImplement(),
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private val mutableEvents = MutableSharedFlow<HomeUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeUiEvent> = mutableEvents.asSharedFlow()

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var updateJob: Job? = null

    init {
        applyUserSettings()
        // Themed navbar icons must flip the moment the toggle changes.
        viewModelScope.launch {
            appSettingsRepository.observeBoolean(THEMED_SHORTCUTS_PREF_KEY, false)
                .collect { enabled ->
                    homeStateRepository.update { it.copy(isThemedShortcutsEnabled = enabled) }
                }
        }
        viewModelScope.launch { countRepository.refresh() }
    }

    suspend fun awaitInitialData() {
        refreshData(refreshUI = false).join()
    }

    fun refreshData(refreshUI: Boolean = false): Job {
        if (!refreshUI) {
            refreshJob?.takeIf(Job::isActive)?.let { return it }
            if (uiState.value.isInitialDataLoaded) return completedJob()
        }
        refreshManagerUpdates(force = refreshUI)
        return viewModelScope.launch {
            refreshMutex.withLock {
                homeStateRepository.update { it.copy(isRefreshing = refreshUI) }
                try {
                    applyUserSettings()
                    val kernelStatus = runCatching { getKernelStatus() }
                        .getOrElse { uiState.value.systemStatus }
                    homeStateRepository.update {
                        it.copy(systemStatus = kernelStatus, isCoreDataLoaded = true)
                    }

                    val includeSelinuxStatus = !uiState.value.isInitialDataLoaded
                    val basic = async {
                        getBasicInfo(
                            managerUapiVersion = kernelStatus.managerUAPIVersion,
                            includeSelinuxStatus = includeSelinuxStatus,
                        )
                    }
                    val managers = async { getManagerRuntimeInfo() }
                    val susfs = async { getSuSFSStatus() }
                    val bbg = async {
                        runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val enabled = ksuCliRepository.isBbgEnabled()
                                val version = if (enabled) {
                                    ksuCliRepository.getBbgVersion()
                                } else {
                                    ""
                                }
                                enabled to version
                            }
                        }.getOrDefault(false to "")
                    }
                    val zeromount = async {
                        runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val version = ksuCliRepository.getZeromountVersion()
                                val enabled = ksuCliRepository.isZeromountDriverPresent() ||
                                    version.isNotEmpty()
                                enabled to version
                            }
                        }.getOrDefault(false to "")
                    }
                    val kpm = async {
                        runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val enabled = ksuCliRepository.isKpmEnabled()
                                val version = if (enabled) {
                                    ksuCliRepository.getKpmVersion()
                                } else {
                                    ""
                                }
                                val count = if (version.isNotBlank()) {
                                    ksuCliRepository.getKpmModuleCount()
                                } else {
                                    0
                                }
                                Triple(enabled, version, count)
                            }
                        }.getOrDefault(Triple(false, "", 0))
                    }
                    val basicInfo = basic.await()
                    val managerInfo = managers.await()
                    val susfsInfo = susfs.await()
                    val kpmInfo = kpm.await()
                    val bbgInfo = bbg.await()
                    val zeromountInfo = zeromount.await()
                    homeStateRepository.update { current ->
                        current.copy(
                            systemInfo = HomeSystemInfo(
                                kernelRelease = basicInfo.kernelRelease,
                                androidVersion = basicInfo.androidVersion,
                                deviceModel = basicInfo.deviceModel,
                                managerVersion = basicInfo.managerVersion,
                                selinuxStatus = current.systemInfo.selinuxStatus.ifEmpty {
                                    basicInfo.selinuxStatus
                                },
                                susfsEnabled = susfsInfo.enabled,
                                susfsVersionSupported = susfsInfo.enabled,
                                susfsVersion = susfsInfo.version,
                                susfsFeatures = susfsInfo.enabledFeatures,
                                managersList = managerInfo,
                                isDynamicSignEnabled = managerInfo.dynamicSignatureEnabled,
                                seccompStatus = basicInfo.seccompStatus,
                                lastFlashTime = getLongPreference(LAST_FLASH_PREF_KEY, 0L),
                                isKpmEnabled = kpmInfo.first,
                                kpmVersion = kpmInfo.second,
                                kpmModuleCount = kpmInfo.third,
                                bbgEnabled = bbgInfo.first,
                                bbgVersion = bbgInfo.second,
                                zeromountEnabled = zeromountInfo.first,
                                zeromountVersion = zeromountInfo.second,
                            ),
                            isInitialDataLoaded = true,
                            isExtendedDataLoaded = true,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableEvents.emit(HomeUiEvent.Error(error.message.orEmpty()))
                } finally {
                    homeStateRepository.update {
                        it.copy(isInitialDataLoaded = true, isRefreshing = false)
                    }
                }
            }
        }.also { refreshJob = it }
    }
    fun handleSimpleModeChange(enabled: Boolean) =
        updatePreference(PREF_SIMPLE_MODE, enabled) { it.copy(isSimpleMode = enabled) }

    fun handleNavigationBarBadgeChange(enabled: Boolean) =
        updatePreference(PREF_SHOW_NAVIGATION_BAR_BADGE, enabled) {
            it.copy(showNavigationBarBadge = enabled)
        }

    fun handleNavigationBarTabsChange(hiddenTabs: Set<String>) =
        updatePreference(PREF_HIDDEN_NAVIGATION_BAR_TABS, hiddenTabs) {
            it.copy(hiddenNavigationBarTabs = hiddenTabs)
        }

    fun handleHomeCardIconsChange(enabled: Boolean) =
        updatePreference(PREF_SHOW_HOME_CARD_ICONS, enabled) {
            it.copy(showHomeCardIcons = enabled)
        }

    fun handleUpdateManagerCardChange(enabled: Boolean) =
        updatePreference(PREF_SHOW_UPDATE_MANAGER_CARD, enabled) {
            it.copy(showUpdateManagerCard = enabled)
        }

    fun dispatch(action: HomeUiAction) {
        when (action) {
            HomeUiAction.AwaitInitialData -> viewModelScope.launch { awaitInitialData() }
            is HomeUiAction.Refresh -> refreshData(action.showIndicator)
            is HomeUiAction.SetSimpleMode -> handleSimpleModeChange(action.enabled)
            is HomeUiAction.SetNavigationBarBadge -> handleNavigationBarBadgeChange(action.enabled)
            is HomeUiAction.SetNavigationBarTabs -> handleNavigationBarTabsChange(action.hiddenTabs)
            is HomeUiAction.SetHomeCardIcons -> handleHomeCardIconsChange(action.enabled)
            is HomeUiAction.SetUpdateManagerCard -> handleUpdateManagerCardChange(action.enabled)
            is HomeUiAction.Reboot -> viewModelScope.launch {
                reboot(action.reason).onFailure {
                    mutableEvents.tryEmit(HomeUiEvent.Error(it.message.orEmpty()))
                }
            }
        }
    }

    private fun refreshManagerUpdates(force: Boolean) {
        val stableEnabled = getBooleanPreference(PREF_CHECK_UPDATE, true)
        val betaEnabled = getBooleanPreference(PREF_CHECK_BETA_UPDATE, true)
        if (!stableEnabled && !betaEnabled) {
            homeStateRepository.update {
                it.copy(
                    stableManagerUpdate = null,
                    betaManagerUpdate = null,
                    isBetaManagerUpdateCheckFailed = false,
                )
            }
            return
        }
        if (!isNetworkAvailable()) return
        if (!force && updateJob?.isActive == true) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            if (stableEnabled) launch {
                val update =
                    runCatching { checkManagerUpdate(ManagerUpdateChannel.STABLE) }.getOrNull()
                homeStateRepository.update { it.copy(stableManagerUpdate = update) }
            }
            if (betaEnabled) launch {
                val result = runCatching { checkManagerUpdate(ManagerUpdateChannel.BETA) }
                homeStateRepository.update {
                    it.copy(
                        betaManagerUpdate = result.getOrNull(),
                        isBetaManagerUpdateCheckFailed = result.isFailure,
                    )
                }
            }
        }
    }

    private fun applyUserSettings() {
        homeStateRepository.update {
            it.copy(
                isSimpleMode = getBooleanPreference(PREF_SIMPLE_MODE),
                showNavigationBarBadge = getBooleanPreference(
                    PREF_SHOW_NAVIGATION_BAR_BADGE,
                    true,
                ),
                hiddenNavigationBarTabs = getStringSetPreference(
                    PREF_HIDDEN_NAVIGATION_BAR_TABS,
                ),
                showHomeCardIcons = getBooleanPreference(PREF_SHOW_HOME_CARD_ICONS),
                showUpdateManagerCard = getBooleanPreference(
                    PREF_SHOW_UPDATE_MANAGER_CARD,
                    true,
                ),
            )
        }
    }

    private fun updatePreference(
        key: String,
        value: Boolean,
        reducer: (HomeUiState) -> HomeUiState,
    ) {
        setBooleanPreference(key, value)
        homeStateRepository.update(reducer)
    }

    private fun updatePreference(
        key: String,
        value: Set<String>,
        reducer: (HomeUiState) -> HomeUiState,
    ) {
        setStringSetPreference(key, value)
        homeStateRepository.update(reducer)
    }

    private fun completedJob(): Job = Job().apply { complete() }

    private companion object {
        const val PREF_CHECK_UPDATE = "check_update"
        const val PREF_CHECK_BETA_UPDATE = "check_beta_update"
        const val PREF_SIMPLE_MODE = "is_simple_mode"
        const val PREF_SHOW_NAVIGATION_BAR_BADGE = "show_navigation_bar_badge"
        const val PREF_HIDDEN_NAVIGATION_BAR_TABS = "hidden_navigation_bar_tabs"
        const val PREF_SHOW_HOME_CARD_ICONS = "show_home_card_icons"
        const val PREF_SHOW_UPDATE_MANAGER_CARD = "show_update_manager_card"
    }
}
