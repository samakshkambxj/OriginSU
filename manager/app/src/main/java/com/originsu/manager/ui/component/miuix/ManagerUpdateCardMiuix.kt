package com.originsu.manager.ui.component.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.originsu.manager.R
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerUpdateInfo
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.miuix.miuixTileColor
import com.originsu.manager.ui.util.miuixHomeTileBlur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * MIUIX counterpart of KernelSU Next's `UpdateCard`: a primary-colored card with an
 * update icon instead of a plain warning row. Prefers the stable update when both
 * channels have one pending, matching the previous home behavior.
 */
@Composable
fun ManagerUpdateCardMiuix(
    stableUpdate: ManagerUpdateInfo?,
    betaUpdate: ManagerUpdateInfo?,
    onUpdateClick: (ManagerUpdateInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = stableUpdate ?: betaUpdate ?: return
    val changelogTitle = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)
    val message = if (update.channel == ManagerUpdateChannel.STABLE) {
        stringResource(
            id = R.string.new_version_available,
            update.versionName.ifBlank { update.versionCode.toString() },
        )
    } else {
        stringResource(id = R.string.beta_version_available, update.versionCode)
    }
    val updateDialog = rememberConfirmDialog(onConfirm = { onUpdateClick(update) })
    val baseColor = colorScheme.primary
    val contentColor = colorScheme.onPrimary

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Card(
            modifier = modifier.miuixHomeTileBlur(baseColor),
            colors = CardDefaults.defaultColors(
                color = miuixTileColor(baseColor),
            ),
            onClick = {
                if (update.changelog.isEmpty()) {
                    onUpdateClick(update)
                } else {
                    updateDialog.showConfirm(
                        title = changelogTitle,
                        content = update.changelog,
                        markdown = true,
                        confirm = updateText,
                    )
                }
            },
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Update,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(end = 20.dp),
                )
                Text(
                    text = message,
                    color = contentColor,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
