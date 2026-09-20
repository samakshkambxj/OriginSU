package com.originsu.manager.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.originsu.manager.R
import com.originsu.manager.domain.model.KernelStatus

/**
 * Hero "working" card ported from the compact MIUIX home screen into Material3:
 * big Working title (+ safe/jailbreak tags), kernel version subtitle, LKM/GKI
 * mode badge and a giant check icon. Tapping opens the installer (except in
 * late-load mode, where it is inert).
 */
@Composable
fun WorkingStatusCard(
    status: KernelStatus,
    ksuVersion: Int,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val workingState = buildString {
        if (status.isSafeMode) {
            append(" [${stringResource(id = R.string.safe_mode)}]")
        }
        if (status.isLateLoadMode) {
            append(" [${stringResource(id = R.string.jailbreak_mode)}]")
        }
    }
    val workingMode = when (status.lkmMode) {
        null -> null
        true -> "LKM"
        else -> "GKI"
    }
    val workingText = "${stringResource(id = R.string.home_working)}$workingState"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        onClick = {
            if (!status.isLateLoadMode) {
                onInstallClick()
            }
        },
        enabled = !status.isLateLoadMode,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(110.dp),
                    imageVector = Icons.Rounded.CheckCircleOutline,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 14.dp),
            ) {
                Text(
                    text = workingText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = stringResource(
                        R.string.home_working_version,
                        ksuVersion
                    ),
                    fontSize = 15.sp,
                )
                if (workingMode != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = workingMode,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
