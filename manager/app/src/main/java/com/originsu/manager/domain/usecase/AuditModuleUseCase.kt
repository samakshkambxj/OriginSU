package com.originsu.manager.domain.usecase

import com.originsu.manager.data.module.ModuleAuditRepository

class AuditModuleUseCase(private val repository: ModuleAuditRepository) {
    suspend operator fun invoke(uri: String) = repository.auditModule(uri)
}
