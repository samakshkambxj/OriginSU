package com.originsu.manager.domain.model

/**
 * Snapshot of the optional HMA-OSS integration surfaced by Origin Curtain.
 *
 * Origin Curtain never bundles HMA-OSS: the module is downloaded from the
 * upstream GitHub release and installed through the normal module pipeline.
 */
data class OriginCurtainStatus(
    val moduleInstalled: Boolean = false,
    val moduleEnabled: Boolean = false,
    val moduleLoaded: Boolean = false,
    val moduleVersion: String = "",
    val managerInstalled: Boolean = false,
    val managerVersion: String = "",
    val zygiskEnabled: Boolean = false,
) {
    val needsReboot: Boolean get() = moduleInstalled && moduleEnabled && !moduleLoaded
}

/** One installable upstream HMA-OSS release artifact. */
data class OriginCurtainRelease(
    val version: String,
    val fileName: String,
    val downloadUrl: String,
)
