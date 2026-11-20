package com.kazesu.manager.ui.screen.kazemount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kazesu.manager.R
import com.kazesu.manager.data.mount.KazeMountMode
import com.kazesu.manager.ui.component.material.ExpressiveScaffold
import com.kazesu.manager.ui.component.material.SegmentedColumn
import com.kazesu.manager.ui.component.material.SegmentedDropdownItem
import com.kazesu.manager.ui.component.material.SegmentedSwitchItem
import com.kazesu.manager.ui.component.material.TonalCard
import com.kazesu.manager.ui.component.material.TopBarBackButton
import com.kazesu.manager.ui.component.material.expressiveTopAppBarColors
import com.kazesu.manager.ui.viewmodel.KazeMountUiState

@Composable
fun KazeMountMaterial(
    state: KazeMountUiState,
    actions: KazeMountActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val status = state.status
    val modes = if (state.zeroMountDriverPresent || status.mode == KazeMountMode.ZeroMount) {
        KazeMountMode.entries
    } else {
        KazeMountMode.entries.filter { it != KazeMountMode.ZeroMount }
    }
    val modeLabels = modes.map { kazeMountModeLabel(it) }
    val backendLabels = listOf("auto", "overlay", "bind").map { magicMountBackendLabel(it) }
    var uidInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.uidState.stored) {
        uidInput = state.uidState.stored.joinToString(", ")
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.kaze_mount_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                actions = {
                    IconButton(onClick = actions.onRefresh, enabled = !state.isWorking) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            item {
                TonalCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.kaze_mount_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (status.available) {
                                kazeMountProviderLabel(status.provider)
                            } else {
                                stringResource(R.string.kaze_mount_unavailable)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(
                                R.string.kaze_mount_configured_mode,
                                kazeMountModeLabel(status.mode),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        status.runtime?.let { runtime ->
                            Text(
                                text = stringResource(
                                    R.string.kaze_mount_runtime_result,
                                    runtime.result.ifBlank { "—" },
                                    runtime.provider.ifBlank { "—" },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            status.runtime?.warning?.takeIf { it.isNotBlank() }?.let { warning ->
                item {
                    TonalCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(text = warning, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            item {
                SegmentedColumn(title = stringResource(R.string.kaze_mount_provider_section)) {
                    item {
                        SegmentedDropdownItem(
                            icon = Icons.Rounded.RestartAlt,
                            title = stringResource(R.string.kaze_mount_provider_section),
                            summary = stringResource(R.string.kaze_mount_provider_summary),
                            items = modeLabels,
                            selectedIndex = modes.indexOf(status.mode).coerceAtLeast(0),
                            enabled = status.available && !state.isWorking,
                            onItemSelected = { index -> actions.onModeChange(modes[index]) },
                        )
                    }
                }
            }
            item {
                SegmentedColumn(title = stringResource(R.string.kaze_mount_magic_section)) {
                    item {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.Security,
                            title = stringResource(R.string.kaze_mount_magic_enable),
                            summary = stringResource(R.string.kaze_mount_magic_enable_summary),
                            checked = status.magicMountEnabled,
                            enabled = status.available && !state.isWorking,
                            onCheckedChange = actions.onMagicEnabledChange,
                        )
                    }
                    item {
                        SegmentedDropdownItem(
                            icon = Icons.Rounded.Memory,
                            title = stringResource(R.string.kaze_mount_magic_backend),
                            summary = stringResource(R.string.kaze_mount_magic_backend_summary),
                            items = backendLabels,
                            selectedIndex = listOf("auto", "overlay", "bind").indexOf(status.backend).coerceAtLeast(0),
                            enabled = status.available && !state.isWorking,
                            onItemSelected = { index ->
                                actions.onBackendChange(listOf("auto", "overlay", "bind")[index])
                            },
                        )
                    }
                }
            }
            item {
                TonalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.kaze_mount_nomount_section),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.kaze_mount_nomount_status,
                                status.nomount?.driverState ?: "absent",
                                status.nomount?.rulesApplied ?: 0,
                                status.nomount?.rulesExpected ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.kaze_mount_nomount_active_uids,
                                state.uidState.active.joinToString(", ").ifBlank { "—" },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = uidInput,
                            onValueChange = { uidInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isWorking,
                            label = { Text(stringResource(R.string.kaze_mount_nomount_uids)) },
                            supportingText = { Text(stringResource(R.string.kaze_mount_nomount_uid_hint)) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { actions.onSaveUids(uidInput) },
                                enabled = !state.isWorking,
                            ) {
                                Text(stringResource(R.string.kaze_mount_nomount_save_uids))
                            }
                            OutlinedButton(
                                onClick = actions.onClearRules,
                                enabled = !state.isWorking,
                            ) {
                                Text(stringResource(R.string.kaze_mount_nomount_clear))
                            }
                        }
                    }
                }
            }
            item {
                SegmentedColumn {
                    item {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.Security,
                            title = stringResource(R.string.kaze_mount_nomount_isolated),
                            summary = stringResource(R.string.kaze_mount_nomount_isolated_summary),
                            checked = state.uidState.isolatedBlocked,
                            enabled = !state.isWorking,
                            onCheckedChange = actions.onIsolatedChange,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = actions.onMountNow,
                    enabled = status.available && !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.kaze_mount_now))
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
