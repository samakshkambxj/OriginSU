package com.originsu.manager.domain.usecase

import com.originsu.manager.data.flash.FlashRepository
import com.originsu.manager.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
