package com.originsu.manager.domain.usecase

import com.originsu.manager.data.bootloop.BootloopRepository
import com.originsu.manager.data.bootloop.BootloopStatus

class GetBootloopStatusUseCase(private val repository: BootloopRepository) {
    suspend operator fun invoke(): BootloopStatus = repository.getStatus()
}

class SetBootloopEnabledUseCase(private val repository: BootloopRepository) {
    suspend operator fun invoke(enabled: Boolean): Boolean = repository.setEnabled(enabled)
}

class SetBootloopMaxUseCase(private val repository: BootloopRepository) {
    suspend operator fun invoke(count: Int): Boolean = repository.setMax(count)
}

class ClearBootloopNoticeUseCase(private val repository: BootloopRepository) {
    suspend operator fun invoke(): Boolean = repository.clearRescued()
}
