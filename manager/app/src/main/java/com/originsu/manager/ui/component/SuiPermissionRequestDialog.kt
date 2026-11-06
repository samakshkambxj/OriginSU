package com.originsu.manager.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.sui.SuiPermissionDecision
import com.originsu.manager.data.sui.SuiPermissionHost
import com.originsu.manager.data.sui.SuiPermissionRequest

/**
 * Shows the prompt Sui hands to its registered manager. Hosted at the app root so
 * it appears whatever screen is open when an app asks for Shizuku.
 */
@Composable
fun SuiPermissionRequestDialog() {
    val pending by SuiPermissionHost.pending.collectAsStateWithLifecycle()
    val dialog = rememberCustomDialog { dismiss ->
        val request = pending
        if (request != null) {
            SuiPermissionPrompt(
                request = request,
                onDecision = { decision ->
                    SuiPermissionHost.reply(decision)
                    dismiss()
                },
                onDismiss = {
                    SuiPermissionHost.reply(SuiPermissionDecision.DISMISS)
                    dismiss()
                },
            )
        }
    }
    LaunchedEffect(pending) {
        if (pending != null) {
            dialog.show()
        }
    }
}

@Composable
private fun SuiPermissionPrompt(
    request: SuiPermissionRequest,
    onDecision: (SuiPermissionDecision) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sui_permission_title)) },
        text = {
            Text(stringResource(R.string.sui_permission_message, request.packageName))
        },
        confirmButton = {
            TextButton(onClick = { onDecision(SuiPermissionDecision.ALLOW_ROOT) }) {
                Text(stringResource(R.string.sui_permission_allow_root))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDecision(SuiPermissionDecision.ALLOW_SHELL) }) {
                    Text(stringResource(R.string.sui_permission_allow_shell))
                }
                TextButton(onClick = { onDecision(SuiPermissionDecision.ALLOW_ONCE) }) {
                    Text(stringResource(R.string.sui_permission_allow_once))
                }
                TextButton(onClick = { onDecision(SuiPermissionDecision.DENY) }) {
                    Text(stringResource(R.string.sui_permission_deny))
                }
            }
        },
    )
}
