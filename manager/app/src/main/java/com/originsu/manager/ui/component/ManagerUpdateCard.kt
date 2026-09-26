package com.originsu.manager.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.originsu.manager.R
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerUpdateInfo

/**
 * Manager update card styled after KernelSU Next's `UpdateCard`: a primary-colored
 * [ElevatedCard] with an update icon and 24.dp content padding instead of a plain
 * warning row. Tapping shows the changelog dialog when one exists, otherwise
 * navigates straight to the updater via [onUpdateClick].
 */
@Composable
fun ManagerUpdateCard(
    update: ManagerUpdateInfo?,
    onUpdateClick: (ManagerUpdateInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibilityState = remember { MutableTransitionState(false) }
    var displayedUpdate by remember { mutableStateOf<ManagerUpdateInfo?>(null) }

    LaunchedEffect(update) {
        if (update != null) displayedUpdate = update
        visibilityState.targetState = update != null
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        displayedUpdate?.let { updateInfo ->
            Column(modifier = modifier) {
                ManagerUpdateCardContent(
                    updateInfo = updateInfo,
                    onUpdateClick = onUpdateClick,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun ManagerUpdateCardContent(
    updateInfo: ManagerUpdateInfo,
    onUpdateClick: (ManagerUpdateInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val changelogTitle = stringResource(R.string.module_changelog)
    val updateText = stringResource(R.string.module_update)
    val message = if (updateInfo.channel == ManagerUpdateChannel.STABLE) {
        stringResource(
            R.string.new_version_available,
            updateInfo.versionName.ifBlank { updateInfo.versionCode.toString() },
        )
    } else {
        stringResource(R.string.beta_version_available, updateInfo.versionCode)
    }
    val updateDialog = rememberConfirmDialog(onConfirm = { onUpdateClick(updateInfo) })

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (updateInfo.changelog.isEmpty()) {
                        onUpdateClick(updateInfo)
                    } else {
                        updateDialog.showConfirm(
                            title = changelogTitle,
                            content = updateInfo.changelog,
                            markdown = true,
                            confirm = updateText,
                        )
                    }
                }
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(end = 20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Preview
@Composable
private fun ManagerUpdateCardPreview() {
    MaterialTheme {
        ManagerUpdateCardContent(
            updateInfo = ManagerUpdateInfo(
                channel = ManagerUpdateChannel.STABLE,
                versionCode = 12345,
                versionName = "v1.2.3",
                abi = "arm64-v8a",
                fileName = "OriginSU_v1.2.3_12345-arm64-v8a-release.apk",
                source = com.originsu.manager.domain.model.ManagerApkSource.DirectApk(
                    url = "https://example.com/manager.apk",
                ),
            ),
            onUpdateClick = {},
        )
    }
}
