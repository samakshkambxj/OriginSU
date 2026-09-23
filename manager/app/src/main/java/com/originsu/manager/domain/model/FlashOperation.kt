package com.originsu.manager.domain.model

sealed interface FlashOperation {
    data class Boot(
        val bootUri: String?,
        val lkm: LkmSelection = LkmSelection.KmiNone,
        val ota: Boolean,
        val partition: String?,
        val allowShell: Boolean = false,
        val enableAdb: Boolean = false,
        val forceBackup: Boolean = false,
    ) : FlashOperation

    data class Module(
        val uri: String,
        val auditConfirmed: Boolean = false,
        val noAudit: Boolean = false,
    ) : FlashOperation
    data class AnyKernelZip(val uri: String) : FlashOperation
    data class PatchBootImage(val bootUri: String, val zipUri: String, val kmi: String? = null) : FlashOperation
    data object Restore : FlashOperation
    data object Uninstall : FlashOperation
}

sealed interface FlashOperationUpdate {
    data class Output(val line: String) : FlashOperationUpdate
    data class ErrorOutput(val line: String) : FlashOperationUpdate
    data class Completed(val showReboot: Boolean, val code: Int) : FlashOperationUpdate
}
