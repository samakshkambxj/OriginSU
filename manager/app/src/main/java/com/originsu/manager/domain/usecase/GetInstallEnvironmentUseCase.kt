package com.originsu.manager.domain.usecase

import com.originsu.manager.data.flash.FlashRepository
import com.originsu.manager.domain.model.InstallEnvironment

class GetInstallEnvironmentUseCase(
    private val repository: FlashRepository,
) {
    fun cached(): InstallEnvironment? = repository.installEnvironment.value

    suspend operator fun invoke(forceRefresh: Boolean = false) =
        repository.getInstallEnvironment(forceRefresh)
}
