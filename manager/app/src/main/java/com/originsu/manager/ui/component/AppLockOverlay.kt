package com.originsu.manager.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.originsu.manager.R
import com.originsu.manager.ui.util.AppLockManager
import com.originsu.manager.ui.util.authenticateSecureRoot

/**
 * Full-screen lock overlay shown while the app lock is engaged. Triggers a
 * biometric / device-credential prompt on first composition and on every resume,
 * honouring [AppLockManager]'s re-lock [timeoutMillis]. Calls [onUnlocked] once
 * authentication succeeds (or the timeout has not elapsed) and [onAuthFailed]
 * when the user fails or cancels authentication.
 */
@Composable
fun AppLockOverlay(
    activity: FragmentActivity,
    timeoutMillis: Long,
    onUnlocked: () -> Unit,
    onAuthFailed: () -> Unit,
) {
    val title = stringResource(R.string.app_lock_prompt_title)
    val subtitle = stringResource(R.string.app_lock_prompt_subtitle)
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPromptShowing by remember { mutableStateOf(false) }

    fun promptIfNeeded() {
        if (!AppLockManager.shouldPrompt(timeoutMillis)) {
            onUnlocked()
            return
        }
        if (isPromptShowing) return
        isPromptShowing = true
        authenticateSecureRoot(activity, title, subtitle) { success ->
            isPromptShowing = false
            if (success) {
                AppLockManager.unlock()
                onUnlocked()
            } else {
                onAuthFailed()
            }
        }
    }

    LaunchedEffect(Unit) {
        promptIfNeeded()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                promptIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
        }
    }
}
