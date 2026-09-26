package com.originsu.manager.ui.util

import android.os.SystemClock

/**
 * Global app-lock state shared by every activity (manager + WebUI), ported from
 * KernelSU Next's AppLockManager. Activities report start/stop; the lock overlay
 * prompts for biometrics when the app returns from background after [timeout].
 *
 * The re-lock [timeout] is passed in by callers so the manager stays independent
 * of the settings backing store (DataStore).
 */
object AppLockManager {
    var activeActivities = 0
    var lastBackgroundTime = 0L
    var isUnlocked = false
    var wasUnlockedBeforeBackground = false

    fun onActivityStart() {
        activeActivities++
    }

    fun onActivityStop(timeout: Long) {
        activeActivities--
        if (activeActivities == 0) {
            val timeInBackground = SystemClock.elapsedRealtime() - lastBackgroundTime
            val isLogicallyUnlocked =
                isUnlocked || (wasUnlockedBeforeBackground && timeInBackground < timeout)

            lastBackgroundTime = SystemClock.elapsedRealtime()
            wasUnlockedBeforeBackground = isLogicallyUnlocked
            isUnlocked = false
        }
    }

    fun shouldPrompt(timeout: Long): Boolean {
        if (!isUnlocked) {
            if (lastBackgroundTime == 0L) return true
            if (!wasUnlockedBeforeBackground) return true

            val timeInBackground = SystemClock.elapsedRealtime() - lastBackgroundTime
            if (timeInBackground < timeout) {
                isUnlocked = true
                return false
            }
            return true
        }
        return false
    }

    fun unlock() {
        isUnlocked = true
        wasUnlockedBeforeBackground = true
        lastBackgroundTime = 0L
    }
}
