package com.originsu.manager.data.grant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.originsu.manager.R
import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.data.profile.ProfileRepository
import java.security.MessageDigest

/**
 * Signature pinning for root grants.
 *
 * Every time an app (or any package sharing its UID) is granted root, we pin
 * the SHA-256 of its signing certificate. If the certificate ever changes
 * without a new grant, root is revoked automatically. An APK update signed
 * with a different key therefore cannot silently inherit an existing grant.
 */
class GrantSignerRepository(
    private val context: Context,
    private val appSettings: AppSettingsRepository,
    private val profileRepository: ProfileRepository,
) {
    /**
     * Returns true when [packages] still match the pinned signer for [uid]
     * (or when no pin exists yet, in which case the current signer is pinned).
     *
     * The caller is responsible for revoking the profile when this returns
     * false.
     */
    suspend fun verifyOrPin(uid: Int, packages: List<String>): Boolean {
        val digest = combinedDigest(packages) ?: return true
        val pinned = appSettings.getString(pinKey(uid))
        if (pinned == null) {
            appSettings.putString(pinKey(uid), digest)
            return true
        }
        return pinned == digest
    }

    /** Replaces the pin after a deliberate new grant. */
    suspend fun pin(uid: Int, packages: List<String>) {
        val digest = combinedDigest(packages) ?: return
        appSettings.putString(pinKey(uid), digest)
    }

    /** Pins every package that currently shares [uid]. */
    suspend fun pinUid(uid: Int) {
        pin(uid, packagesForUid(uid))
    }

    private fun packagesForUid(uid: Int): List<String> =
        context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()

    /**
     * Verifies one package after install/update and revokes its UID when the
     * signer no longer matches. Called from the package change receiver.
     */
    suspend fun verifyPackage(packageName: String, uid: Int) {
        if (uid < 0) return
        val packages = packagesForUid(uid).ifEmpty { listOf(packageName) }

        if (verifyOrPin(uid, packages)) return

        val profile = runCatching {
            profileRepository.getProfile(packageName, uid)
        }.getOrNull() ?: return
        if (!profile.allowSu) {
            // Not granted: keep the old pin so a later manual grant re-pins it.
            return
        }

        val revoked = profile.copy(allowSu = false)
        if (profileRepository.setProfile(revoked)) {
            // Remember the new signer so the denial is stable until the user
            // grants root again on purpose.
            pin(uid, packages)
            notifyRevoked(packageName)
        }
    }

    private fun combinedDigest(packages: List<String>): String? {
        val digests = packages.mapNotNull(::signerDigest).distinct().sorted()
        if (digests.isEmpty()) return null
        return digests.joinToString(",")
    }

    private fun signerDigest(packageName: String): String? {
        val packageManager = context.packageManager
        val signatures = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                info.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures
                    .orEmpty()
            }
        }.getOrNull().orEmpty()

        if (signatures.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = signatures
            .map { it.toByteArray() }
            .sortedWith { a, b -> a.size.compareTo(b.size) }
            .flatMap { it.toList() }
            .toByteArray()
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun notifyRevoked(packageName: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.root_gate_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.grant_signer_changed_title))
            .setContentText(
                context.getString(R.string.grant_signer_changed_summary, packageName)
            )
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(packageName.hashCode(), notification)
        }
    }

    private fun pinKey(uid: Int): String = "$PIN_PREFIX$uid"

    companion object {
        private const val CHANNEL_ID = "root_safety"
        private const val PIN_PREFIX = "grant_signer_"
    }
}
