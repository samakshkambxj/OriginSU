package com.originsu.manager.domain.usecase

import com.originsu.manager.data.kernel.SuNotifyRepository
import com.originsu.manager.data.kernel.VeilManageRepository
import com.originsu.manager.data.kernel.VeilRepository

class ObserveVeilStateUseCase(private val repository: VeilRepository) {
    operator fun invoke() = repository.state
}

class RefreshVeilUseCase(private val repository: VeilRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class SetVeilEnabledUseCase(private val repository: VeilRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setEnabled(enabled)
}

class SetVeilAutoCloakUseCase(private val repository: VeilRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setAutoCloak(enabled)
}

class SetVeilCloakedUseCase(private val repository: VeilRepository) {
    suspend operator fun invoke(uid: Int, cloaked: Boolean) = repository.setCloaked(uid, cloaked)
}

class ClearVeilCloakedUseCase(private val repository: VeilRepository) {
    suspend operator fun invoke() = repository.clearCloaked()
}

class ClearVeilHistoryUseCase(private val repository: VeilRepository) {
    suspend operator fun invoke() = repository.clearHistory()
}

class UncloakRestoreUseCase(private val manage: VeilManageRepository) {
    suspend operator fun invoke(uid: Int) = manage.uncloakRestore(uid)
}

class IsSuNotifyEnabledUseCase(private val repository: SuNotifyRepository) {
    operator fun invoke(): Boolean = repository.isEnabled()
}

class SetSuNotifyEnabledUseCase(private val repository: SuNotifyRepository) {
    suspend operator fun invoke(enabled: Boolean): Boolean = repository.setEnabled(enabled)
}
