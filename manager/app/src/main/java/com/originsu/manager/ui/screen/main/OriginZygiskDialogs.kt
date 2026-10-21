package com.originsu.manager.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.originsu.manager.R
import com.originsu.manager.data.shell.OriginZygiskAppInfo
import com.originsu.manager.data.shell.OriginZygiskModule
import com.originsu.manager.data.shell.OriginZygiskStatus
import com.originsu.manager.data.shell.OriginZygiskTelemetryEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OriginZygiskModulesDialog(
    status: OriginZygiskStatus,
    onToggle: (String, Boolean) -> Unit,
    onQuarantine: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.origin_zygisk_dialog_modules)) },
        text = {
            if (status.installedModules.isEmpty()) {
                Text(stringResource(R.string.origin_zygisk_no_modules))
            } else {
                Column {
                    Text(
                        stringResource(R.string.origin_zygisk_module_next_boot),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(status.installedModules, key = { it.id }) { module ->
                            ModuleRow(
                                module = module,
                                onToggle = { onToggle(module.id, it) },
                                onQuarantine = { onQuarantine(module.id, !module.quarantined) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.origin_zygisk_dialog_close))
            }
        },
    )
}

@Composable
private fun ModuleRow(
    module: OriginZygiskModule,
    onToggle: (Boolean) -> Unit,
    onQuarantine: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(module.name, fontWeight = FontWeight.Medium)
                Text(
                    module.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    moduleHealthText(module),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        module.quarantined || !module.enabled || !module.hasCurrentAbi ->
                            MaterialTheme.colorScheme.error
                        module.loaded -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = !module.zygiskDisabled,
                onCheckedChange = onToggle,
            )
        }
        if (module.hasCurrentAbi) {
            TextButton(onClick = onQuarantine) {
                Text(
                    stringResource(
                        if (module.quarantined) R.string.origin_zygisk_module_restore
                        else R.string.origin_zygisk_module_quarantine,
                    ),
                )
            }
        }
    }
}

@Composable
private fun moduleHealthText(module: OriginZygiskModule): String = when {
    !module.enabled -> stringResource(R.string.origin_zygisk_module_disabled)
    module.quarantined -> stringResource(R.string.origin_zygisk_module_quarantined)
    module.zygiskDisabled -> stringResource(R.string.origin_zygisk_module_zygisk_off)
    !module.hasCurrentAbi -> stringResource(R.string.origin_zygisk_module_missing_abi)
    module.loaded -> stringResource(R.string.origin_zygisk_module_loaded)
    else -> stringResource(R.string.origin_zygisk_module_not_loaded)
}

@Composable
fun OriginZygiskTelemetryDialog(
    events: List<OriginZygiskTelemetryEvent>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.origin_zygisk_dialog_telemetry)) },
        text = {
            if (events.isEmpty()) {
                Text(stringResource(R.string.origin_zygisk_no_telemetry))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(events) { event ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "${telemetryTime(event.timestamp)}  ${event.event}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (event.process.isNotBlank()) {
                                Text(
                                    stringResource(
                                        R.string.origin_zygisk_telemetry_process,
                                        event.process,
                                        event.uid,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            val flags = buildString {
                                append(
                                    stringResource(
                                        R.string.origin_zygisk_telemetry_flags,
                                        event.flags,
                                    ),
                                )
                                if (event.skipped) {
                                    append(" · ")
                                    append(stringResource(R.string.origin_zygisk_telemetry_skip))
                                }
                            }
                            Text(
                                flags,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.origin_zygisk_dialog_close))
            }
        },
    )
}

@Composable
fun OriginZygiskLogDialog(
    log: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.origin_zygisk_dialog_log)) },
        text = {
            if (log.isBlank()) {
                Text(stringResource(R.string.origin_zygisk_no_log))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(log.lineSequence().toList()) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.origin_zygisk_dialog_close))
            }
        },
    )
}

@Composable
fun OriginZygiskSkipDialog(
    apps: List<OriginZygiskAppInfo>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(apps, search) {
        val query = search.trim()
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.origin_zygisk_dialog_skip)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.origin_zygisk_search_apps)) },
                )
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.origin_zygisk_no_apps),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = selected.contains(app.packageName),
                                    onCheckedChange = { onToggle(app.packageName, it) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.origin_zygisk_dialog_close))
            }
        },
    )
}

private fun telemetryTime(timestamp: Long): String {
    val millis = if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
