package com.kazesu.manager.data.update

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagerUpdateRepositoryTest {
    private val repository = ManagerUpdateRepository(OkHttpClient())

    @Test
    fun `parses commit count from GitHub pagination`() {
        val link = "<https://api.github.com/repositories/1/commits?per_page=1&page=2>; rel=\"next\", " +
            "<https://api.github.com/repositories/1/commits?per_page=1&page=4055>; rel=\"last\""
        assertEquals(4055, repository.parseCommitCount(link))
        assertNull(repository.parseCommitCount(null))
    }

    @Test
    fun `selects matching ABI from nightly artifact`() {
        val arm = entry("KazeSU_v1.0.0_35051-arm64-v8a-release.apk")
        val x86 = entry("nested/KazeSU_v1.0.0_35051-x86_64-release.apk")
        val universal = entry("KazeSU_v1.0.0_35051-universal-release.apk")

        assertEquals(arm, repository.findNightlyApkEntry(listOf(universal, arm), 35051, "arm64-v8a"))
        assertEquals(x86, repository.findNightlyApkEntry(listOf(arm, x86), 35051, "x86_64"))
        assertEquals(universal, repository.findNightlyApkEntry(listOf(arm, universal), 35051, "riscv64"))
    }

    @Test
    fun `rejects unrelated or incorrectly versioned APKs`() {
        val entries = listOf(
            entry("OriginSU_v1_35051-arm64-v8a-release.apk"),
            entry("KazeSU_v1.0.0_35050-arm64-v8a-release.apk"),
            entry("KazeSU_v1.0.0_35051-arm64-v8a-debug.apk"),
        )
        assertNull(repository.findNightlyApkEntry(entries, 35051, "arm64-v8a"))
    }

    private fun entry(name: String) = ZipEntryMetadata(
        name = name,
        flags = 0,
        compressionMethod = 0,
        crc32 = 0,
        compressedSize = 0,
        uncompressedSize = 0,
        localHeaderOffset = 0,
    )
}
