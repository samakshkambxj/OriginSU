package com.originsu.manager.domain.usecase

import com.originsu.manager.data.packageinfo.SuperUserRepository

class GetSuperUserAppGroupUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke(uid: Int, primaryPackageName: String) =
        repository.getAppGroup(uid, primaryPackageName)
}
