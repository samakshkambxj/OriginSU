package com.originsu.manager.domain.usecase

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
