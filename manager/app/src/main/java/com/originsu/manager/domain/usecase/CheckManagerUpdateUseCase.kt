package com.originsu.manager.domain.usecase

import com.originsu.manager.data.update.ManagerUpdateRepository
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerUpdateInfo
import com.originsu.manager.domain.model.ManagerVariant

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(
        channel: ManagerUpdateChannel,
        variant: ManagerVariant = ManagerVariant.NORMAL,
    ): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate(variant)
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate(variant)
        }
}
