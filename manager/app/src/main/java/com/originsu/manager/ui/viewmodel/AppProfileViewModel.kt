package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.grant.GrantToastRepository
import com.originsu.manager.domain.model.AppControlAction
import com.originsu.manager.domain.model.AppProfile
import com.originsu.manager.domain.model.InstalledAppGroup
import com.originsu.manager.domain.model.TempGrantDuration
import com.originsu.manager.domain.model.WEBVIEW_ZYGOTE_UID
import com.originsu.manager.domain.usecase.ControlAppUseCase
import com.originsu.manager.domain.usecase.GetAppProfileUseCase
import com.originsu.manager.domain.usecase.GetAppSepolicyUseCase
import com.originsu.manager.domain.usecase.GetBooleanPreferenceUseCase
import com.originsu.manager.domain.usecase.GetDefaultUmountModulesUseCase
import com.originsu.manager.domain.usecase.GetSuperUserAppGroupUseCase
import com.originsu.manager.domain.usecase.GrantTempAccessUseCase
import com.originsu.manager.domain.usecase.ObserveTempGrantsUseCase
import com.originsu.manager.domain.usecase.SECURE_ROOT_PREF_KEY
import com.originsu.manager.domain.usecase.SetAppProfileUseCase
import com.originsu.manager.domain.usecase.SetAppSepolicyUseCase
import com.originsu.manager.domain.usecase.RevokeTempAccessUseCase
import com.originsu.manager.domain.usecase.RefreshTempGrantsUseCase
import com.originsu.manager.domain.usecase.ValidateSepolicyUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppProfileUiState(
    val appGroup: InstalledAppGroup? = null,
    val profile: AppProfile? = null,
    val defaultUmountModules: Boolean = true,
    val isLoading: Boolean = true,
    val sepolicyValid: Boolean = true,
    val isSecureRootEnabled: Boolean = false,
    val isTempGrantEnabled: Boolean = false,
    val tempRemainingSecs: Long? = null,
)

sealed interface AppProfileUiAction {
    data object Load : AppProfileUiAction
    data class Save(val profile: AppProfile) : AppProfileUiAction
    data class ControlApp(val action: AppControlAction) : AppProfileUiAction
    data class ValidateSepolicy(val rules: String) : AppProfileUiAction
    data class GrantTemp(val duration: TempGrantDuration) : AppProfileUiAction
    data object RevokeTemp : AppProfileUiAction
}

sealed interface AppProfileUiEvent {
    data object Saved : AppProfileUiEvent
    data class Error(val cause: Throwable? = null) : AppProfileUiEvent
    data object SepolicyUpdateFailed : AppProfileUiEvent
}

class AppProfileViewModel(
    private val uid: Int,
    private val packageName: String,
    private val getAppGroup: GetSuperUserAppGroupUseCase,
    private val getProfile: GetAppProfileUseCase,
    private val getDefaultUmountModules: GetDefaultUmountModulesUseCase,
    private val setProfile: SetAppProfileUseCase,
    private val getSepolicy: GetAppSepolicyUseCase,
    private val setSepolicy: SetAppSepolicyUseCase,
    private val controlApp: ControlAppUseCase,
    private val validateSepolicy: ValidateSepolicyUseCase,
    private val getBooleanPreference: GetBooleanPreferenceUseCase,
    private val grantTempAccess: GrantTempAccessUseCase,
    private val revokeTempAccess: RevokeTempAccessUseCase,
    private val observeTempGrants: ObserveTempGrantsUseCase,
    private val refreshTempGrants: RefreshTempGrantsUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppProfileUiState())
    val state: StateFlow<AppProfileUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<AppProfileUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AppProfileUiEvent> = mutableEvents.asSharedFlow()
    private var validationJob: Job? = null
    private val saveMutex = Mutex()

    init {
        dispatch(AppProfileUiAction.Load)
    }

    fun dispatch(action: AppProfileUiAction) {
        when (action) {
            AppProfileUiAction.Load -> viewModelScope.launch {
                mutableState.update { it.copy(isLoading = true) }
                runCatching {
                    val profile = getProfile(packageName, uid)
                    val isSpecial = uid == WEBVIEW_ZYGOTE_UID
                    val loadedProfile = if (isSpecial) {
                        profile.copy(allowSu = false)
                    } else if (profile.allowSu) {
                        profile.copy(
                            rules = runCatching { getSepolicy(packageName) }
                                .getOrDefault(profile.rules)
                        )
                    } else {
                        profile
                    }
                    Triple(
                        getAppGroup(uid, packageName),
                        loadedProfile,
                        runCatching { getDefaultUmountModules() }
                            .getOrDefault(profile.umountModules),
                    )
                }.onSuccess { (group, profile, defaultUmountModules) ->
                    mutableState.value = AppProfileUiState(
                        appGroup = group,
                        profile = profile,
                        defaultUmountModules = defaultUmountModules,
                        isLoading = false,
                        isSecureRootEnabled = getBooleanPreference(
                            SECURE_ROOT_PREF_KEY,
                            false,
                        ),
                        isTempGrantEnabled = getBooleanPreference(
                            GrantToastRepository.PREF_TEMP_GRANT,
                            false,
                        ),
                        tempRemainingSecs = observeTempGrants()
                            .value.grants.firstOrNull { it.uid == uid }?.remainingSecs,
                    )
                    refreshTempGrants()
                }.onFailure { error ->
                    mutableState.update { it.copy(isLoading = false) }
                    mutableEvents.tryEmit(AppProfileUiEvent.Error(error))
                }
            }

            is AppProfileUiAction.Save -> {
                val previous = mutableState.value.profile
                val isSpecial = uid == WEBVIEW_ZYGOTE_UID
                val profileToSave = if (isSpecial) {
                    action.profile.copy(allowSu = false)
                } else {
                    action.profile
                }
                mutableState.update { it.copy(profile = profileToSave) }
                viewModelScope.launch {
                    saveMutex.withLock {
                        if (!isSpecial) {
                            val sepolicyKey = profileToSave.rootTemplate ?: profileToSave.name
                            if (profileToSave.allowSu && !profileToSave.rootUseDefault &&
                                profileToSave.rules.isNotEmpty() &&
                                !setSepolicy(sepolicyKey, profileToSave.rules)
                            ) {
                                rollbackIfCurrent(profileToSave, previous)
                                mutableEvents.emit(AppProfileUiEvent.SepolicyUpdateFailed)
                                return@withLock
                            }
                        }
                        runCatching { setProfile(profileToSave) }
                            .onSuccess { saved ->
                                if (saved) {
                                    mutableEvents.tryEmit(AppProfileUiEvent.Saved)
                                } else {
                                    rollbackIfCurrent(profileToSave, previous)
                                    mutableEvents.tryEmit(AppProfileUiEvent.Error())
                                }
                            }
                            .onFailure {
                                rollbackIfCurrent(profileToSave, previous)
                                mutableEvents.tryEmit(AppProfileUiEvent.Error(it))
                            }
                    }
                }
            }

            is AppProfileUiAction.ControlApp -> viewModelScope.launch {
                controlApp(packageName, action.action)
                    .onFailure { mutableEvents.tryEmit(AppProfileUiEvent.Error(it)) }
            }

            is AppProfileUiAction.ValidateSepolicy -> {
                validationJob?.cancel()
                mutableState.update { it.copy(sepolicyValid = false) }
                validationJob = viewModelScope.launch {
                    val valid = runCatching { validateSepolicy(action.rules) }.getOrDefault(false)
                    mutableState.update { it.copy(sepolicyValid = valid) }
                }
            }

            is AppProfileUiAction.GrantTemp -> viewModelScope.launch {
                grantTempAccess(packageName, uid, action.duration)
                    .onSuccess {
                        refreshTempGrants()
                        mutableState.update {
                            it.copy(
                                tempRemainingSecs = observeTempGrants()
                                    .value.grants.firstOrNull { grant -> grant.uid == uid }
                                    ?.remainingSecs,
                            )
                        }
                    }
                    .onFailure { mutableEvents.tryEmit(AppProfileUiEvent.Error(it)) }
            }

            AppProfileUiAction.RevokeTemp -> viewModelScope.launch {
                revokeTempAccess(uid)
                    .onSuccess {
                        refreshTempGrants()
                        mutableState.update { it.copy(tempRemainingSecs = null) }
                    }
                    .onFailure { mutableEvents.tryEmit(AppProfileUiEvent.Error(it)) }
            }
        }
    }

    private fun rollbackIfCurrent(failed: AppProfile, previous: AppProfile?) {
        mutableState.update { current ->
            if (current.profile == failed) current.copy(profile = previous) else current
        }
    }
}
