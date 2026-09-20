package com.originsu.manager.domain.model

enum class AuditSeverity {
    INFO,
    NOTICE,
    HIGH,
    CRITICAL,
}

data class AuditFinding(
    val severity: AuditSeverity,
    val ruleId: String,
    val path: String,
    val line: Int?,
    val title: String,
    val evidence: String,
)

data class ModuleAuditReport(
    val moduleId: String?,
    val packageSha256: String,
    val findings: List<AuditFinding>,
    val scannedFiles: Int,
    val derivedArtifacts: Int,
) {
    fun count(severity: AuditSeverity): Int = findings.count { it.severity == severity }

    val criticalCount: Int get() = count(AuditSeverity.CRITICAL)
    val highCount: Int get() = count(AuditSeverity.HIGH)

    val hasCritical: Boolean get() = criticalCount > 0
    val hasHigh: Boolean get() = highCount > 0
}
