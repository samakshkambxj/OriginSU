package com.originsu.manager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuSFSLkmStatusTest {
    private fun status(
        supportedKmis: List<String> = listOf("5.10", "6.1"),
        currentKmi: String = "6.1",
        isLoaded: Boolean = false,
        isLkm: Boolean = false,
        isArm64: Boolean = true,
        isLkmMode: Boolean = true,
        rootAvailable: Boolean = true,
    ) = SuSFSLkmStatus(
        currentKmi = currentKmi,
        supportedKmis = supportedKmis,
        isLoaded = isLoaded,
        isLkm = isLkm,
        isArm64 = isArm64,
        isLkmMode = isLkmMode,
        rootAvailable = rootAvailable,
    )

    private fun stateOf(
        results: List<SuSFSLkmCheckResult>,
        check: SuSFSLkmCheck,
    ): SuSFSLkmCheckState = results.first { it.check == check }.state

    @Test
    fun `preflight passes when all load prerequisites are available`() {
        val results = status().preflight()
        assertEquals(SuSFSLkmCheckState.Ok, stateOf(results, SuSFSLkmCheck.Arm64))
        assertEquals(SuSFSLkmCheckState.Ok, stateOf(results, SuSFSLkmCheck.LkmMode))
        assertEquals(SuSFSLkmCheckState.Ok, stateOf(results, SuSFSLkmCheck.Root))
        assertEquals(SuSFSLkmCheckState.Ok, stateOf(results, SuSFSLkmCheck.Kmi))
        assertEquals(SuSFSLkmCheckState.Ok, stateOf(results, SuSFSLkmCheck.GuardAsset))
        assertEquals(SuSFSLkmCheckState.Warning, stateOf(results, SuSFSLkmCheck.Active))
    }

    @Test
    fun `preflight reports device and root blockers`() {
        val results = status(isArm64 = false, isLkmMode = false, rootAvailable = false)
            .preflight()
        assertEquals(SuSFSLkmCheckState.Error, stateOf(results, SuSFSLkmCheck.Arm64))
        assertEquals(SuSFSLkmCheckState.Error, stateOf(results, SuSFSLkmCheck.LkmMode))
        assertEquals(SuSFSLkmCheckState.Error, stateOf(results, SuSFSLkmCheck.Root))
    }

    @Test
    fun `preflight rejects a KMI without a guard asset`() {
        val results = status(currentKmi = "4.14", supportedKmis = listOf("5.10", "6.1"))
            .preflight()
        assertEquals(SuSFSLkmCheckState.Error, stateOf(results, SuSFSLkmCheck.GuardAsset))
    }

    @Test
    fun `preflight reports unknown assets when the list is empty`() {
        val results = status(supportedKmis = emptyList()).preflight()
        assertEquals(SuSFSLkmCheckState.Unknown, stateOf(results, SuSFSLkmCheck.GuardAsset))
    }

    @Test
    fun `built in SuSFS only reports the active check`() {
        val results = status(isLoaded = true, isLkm = false).preflight()
        assertEquals(1, results.size)
        assertEquals(SuSFSLkmCheck.Active, results.single().check)
        assertEquals(SuSFSLkmCheckState.Ok, results.single().state)
    }

    @Test
    fun `supportsKmi is case insensitive and permissive without a list`() {
        assertTrue(status().supportsKmi("6.1"))
        assertTrue(status().supportsKmi("6.1".uppercase()))
        assertTrue(status(supportedKmis = emptyList()).supportsKmi("anything"))
        assertTrue(status().supportsKmi(null))
        assertFalse(status().supportsKmi("4.14"))
    }

    @Test
    fun `diagnostics report includes module and action data`() {
        val report = buildSuSFSLkmDiagnosticsReport(
            status = status(isLoaded = true, isLkm = true).copy(
                version = "v2.3.0-gki",
                loadedKmi = "6.1",
                assetName = "6.1_susfs_guard_lkm.ko",
                assetSha256 = "abc123",
                assetSize = 42L,
                loadedAt = 99L,
                autoloadEnabled = true,
                autoloadKmi = "6.1",
                autoloadAttempts = 1,
            ),
            action = SuSFSLkmActionResult(
                success = true,
                command = "susfs load --kmi 6.1",
                stdout = "Loaded",
            ),
        )

        assertTrue(report.contains("version=v2.3.0-gki"))
        assertTrue(report.contains("loaded_kmi=6.1"))
        assertTrue(report.contains("asset_sha256=abc123"))
        assertTrue(report.contains("autoload_attempts=1"))
        assertTrue(report.contains("last_command=susfs load --kmi 6.1"))
        assertTrue(report.contains("last_stdout=Loaded"))
    }
}
