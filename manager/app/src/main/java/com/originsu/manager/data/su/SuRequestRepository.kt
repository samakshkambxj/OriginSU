package com.originsu.manager.data.su

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.originsu.manager.Natives
import com.originsu.manager.data.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SuRequestRepository(
    private val context: Context,
    private val appSettings: AppSettingsRepository,
) {
    fun isPromptEnabled(): Boolean = appSettings.getBoolean(PREF_SU_PROMPT, false)

    fun setPromptEnabled(enabled: Boolean) {
        appSettings.putBoolean(PREF_SU_PROMPT, enabled)
        runCatching { Natives.setSuPromptEnabled(enabled) }
        if (enabled) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    fun syncToKernel(): Boolean = runCatching {
        val enabled = isPromptEnabled()
        Natives.setSuPromptEnabled(enabled)
        true
    }.getOrDefault(false)

    fun isSupported(): Boolean = runCatching {
        Natives.isFullFeatured() && Natives.kernelUAPIVersion >= 5
    }.getOrDefault(false)

    fun startPolling() {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SuRequestPollService::class.java)
                    .setAction(SuRequestPollService.ACTION_START),
            )
        }
    }

    fun stopPolling() {
        runCatching {
            context.startService(
                Intent(context, SuRequestPollService::class.java)
                    .setAction(SuRequestPollService.ACTION_STOP),
            )
        }
    }

    suspend fun answer(
        requestId: Long,
        uid: Int,
        packageName: String,
        allow: Boolean,
        remember: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val answered = runCatching {
            Natives.answerSuRequest(requestId, allow, remember)
        }.getOrDefault(false)
        if (answered && allow && remember) {
            runCatching {
                val current = Natives.getAppProfile(packageName, uid)
                val updated = current.copy(allowSu = true)
                Natives.setAppProfile(updated)
            }
        }
        // Fallback persist when kernel lacks the prompt queue (older kernel):
        // still honor "remember" via the allowlist so the next su succeeds.
        if (allow && remember && !answered) {
            runCatching {
                val current = Natives.getAppProfile(packageName, uid)
                Natives.setAppProfile(current.copy(allowSu = true))
            }.getOrDefault(false)
        } else {
            answered
        }
    }

    suspend fun ensureAllowed(uid: Int, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = Natives.getAppProfile(packageName, uid)
                if (current.allowSu) return@runCatching true
                Natives.setAppProfile(current.copy(allowSu = true))
            }.getOrDefault(false)
        }

    companion object {
        const val PREF_SU_PROMPT = "su_prompt_enabled"
    }
}
