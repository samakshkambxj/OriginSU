package com.originsu.manager.domain.usecase

import com.originsu.manager.data.update.ManagerUpdateRepository
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
        }
}
