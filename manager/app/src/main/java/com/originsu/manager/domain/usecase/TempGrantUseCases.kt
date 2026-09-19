package com.originsu.manager.domain.usecase

import com.originsu.manager.data.grant.TempGrantRepository
import com.originsu.manager.domain.model.TempGrantDuration

class ObserveTempGrantsUseCase(private val repository: TempGrantRepository) {
    operator fun invoke() = repository.state
}

class RefreshTempGrantsUseCase(private val repository: TempGrantRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class GrantTempAccessUseCase(private val repository: TempGrantRepository) {
    suspend operator fun invoke(packageName: String, uid: Int, duration: TempGrantDuration) =
        repository.grant(packageName, uid, duration)
}

class RevokeTempAccessUseCase(private val repository: TempGrantRepository) {
    suspend operator fun invoke(uid: Int) = repository.revoke(uid)
}
