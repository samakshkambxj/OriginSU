package com.originsu.manager.domain.usecase

import com.originsu.manager.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
