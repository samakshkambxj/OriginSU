package com.originsu.manager.data.module

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.AuditFinding
import com.originsu.manager.domain.model.AuditSeverity
import com.originsu.manager.domain.model.ModuleAuditReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * OriginGuard pre-install audit: stages a module [Uri] to a root-readable
 * file, scans it with `ksud module audit --json`, and parses the report.
 */
class ModuleAuditRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun auditModule(uri: String): Result<ModuleAuditReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                val staged = stageModule(uri.toUri())
                try {
                    val raw = ksuCliRepository.auditModuleZip(staged.absolutePath)
                    require(raw.isNotBlank()) { "audit produced no report" }
                    parseReport(raw)
                } finally {
                    staged.delete()
                }
            }
        }

    private fun stageModule(uri: Uri): File {
        val file = File(application.cacheDir, "originguard-audit.zip")
        application.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        require(file.isFile && file.length() > 0) { "cannot stage module for audit" }
        return file
    }

    private fun parseReport(raw: String): ModuleAuditReport {
        val json = JSONObject(raw)
        val findings = mutableListOf<AuditFinding>()
        val array = json.optJSONArray("findings")
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                findings.add(
                    AuditFinding(
                        severity = parseSeverity(item.optString("severity")),
                        ruleId = item.optString("rule_id"),
                        path = item.optString("path"),
                        line = item.optInt("line", -1).takeIf { it >= 0 },
                        title = item.optString("title"),
                        evidence = item.optString("evidence"),
                    )
                )
            }
        }
        return ModuleAuditReport(
            moduleId = json.optString("module_id").ifEmpty { null },
            packageSha256 = json.optString("package_sha256"),
            findings = findings,
            scannedFiles = json.optInt("scanned_files"),
            derivedArtifacts = json.optInt("derived_artifacts"),
        )
    }

    private fun parseSeverity(raw: String): AuditSeverity = when (raw.lowercase()) {
        "critical" -> AuditSeverity.CRITICAL
        "high" -> AuditSeverity.HIGH
        "notice" -> AuditSeverity.NOTICE
        else -> AuditSeverity.INFO
    }
}
