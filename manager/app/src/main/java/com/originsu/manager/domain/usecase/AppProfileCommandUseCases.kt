package com.originsu.manager.domain.usecase

import com.originsu.manager.data.profile.ProfileRepository
import com.originsu.manager.domain.model.AppControlAction

class GetAppSepolicyUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(packageName: String) = repository.getSepolicy(packageName)
}

class SetAppSepolicyUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(packageName: String, rules: String) =
        repository.setSepolicy(packageName, rules)
}

class ControlAppUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(packageName: String, action: AppControlAction) =
        repository.controlApp(packageName, action)
}

class ValidateSepolicyUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(rules: String) = repository.isSepolicyValid(rules)
}
