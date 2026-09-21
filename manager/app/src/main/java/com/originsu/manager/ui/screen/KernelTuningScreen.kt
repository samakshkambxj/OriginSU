package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.NetworkCheck
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material.icons.twotone.SwapHoriz
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.domain.model.BoreKnob
import com.originsu.manager.domain.model.SysctlEntry
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.popupBlur
import com.originsu.manager.ui.component.popupContainerColor
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.component.settings.lazySegmentColumn
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.KernelTuningUiAction
import com.originsu.manager.ui.viewmodel.KernelTuningUiEvent
import com.originsu.manager.ui.viewmodel.KernelTuningViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KernelTuningScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<KernelTuningViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val pullToRefreshState = rememberPullToRefreshState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<SysctlEntry?>(null) }
    var editingBore by remember { mutableStateOf<BoreKnob?>(null) }
    var editingZramStreams by remember { mutableStateOf(false) }
    var editingZramSwappiness by remember { mutableStateOf(false) }

    val confirmDelete = stringResource(R.string.confirm_delete)

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset =
            scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is KernelTuningUiEvent.Message ->
                    snackBarHost.showReplacingSnackbar(event.message)
            }
        }
    }

    ActivityResumeEffect {
        viewModel.dispatch(KernelTuningUiAction.Refresh)
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier
                    .blurEffect(),
                title = { Text(stringResource(R.string.kernel_tuning)) },
                navigationIcon = {
                    val navigator = LocalNavigator.current
                    AppBackButton(
                        onClick = {
                            navigator.pop()
                        }
                    )
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.TwoTone.Add, contentDescription = null)
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    viewModel.dispatch(KernelTuningUiAction.Refresh)
                },
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = uiState.isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 5.dp,
                        start = 0.dp,
                        end = 0.dp,
                        bottom = paddingValues.calculateBottomPadding() + 72.dp + 5.dp + 5.dp // FAB
                    )
                ) {
                    item {
                        WarningCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            message = stringResource(R.string.changes_take_effect_immediately),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        SegmentedColumn(
                            title = stringResource(R.string.kernel_tuning_tcp),
                            content = {
                                if (uiState.tcpAvailable.isEmpty()) {
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.NetworkCheck,
                                            title = stringResource(R.string.kernel_tuning_tcp_empty),
                                            enabled = false,
                                        ) {}
                                    }
                                } else {
                                    item {
                                        val selectedIndex =
                                            uiState.tcpAvailable.indexOf(uiState.tcpCurrent)
                                        SettingsChooseWidget(
                                            icon = Icons.TwoTone.NetworkCheck,
                                            title = stringResource(R.string.kernel_tuning_tcp),
                                            items = uiState.tcpAvailable,
                                            selectedIndex = selectedIndex.coerceAtLeast(0),
                                            onSelectedIndexChange = { index ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetTcp(
                                                        uiState.tcpAvailable[index],
                                                        uiState.tcpPersist,
                                                    )
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.tcpPersist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetTcpPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (uiState.boreSupported) {
                        item {
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_bore),
                                content = {
                                    item {
                                        val profileIds = viewModel.boreProfileIds
                                        val selectedProfile =
                                            profileIds.indexOf(uiState.boreProfileId)
                                        SettingsChooseWidget(
                                            icon = Icons.TwoTone.Settings,
                                            title = stringResource(R.string.kernel_tuning_bore_profile),
                                            description = if (selectedProfile < 0) {
                                                stringResource(R.string.kernel_tuning_bore_profile_custom)
                                            } else {
                                                null
                                            },
                                            items = profileIds.map { boreProfileTitle(it) },
                                            itemDescriptions = profileIds.map {
                                                boreProfileDescription(it)
                                            },
                                            selectedIndex = selectedProfile,
                                            onSelectedIndexChange = { index ->
                                                profileIds.getOrNull(index)?.let { id ->
                                                    viewModel.dispatch(
                                                        KernelTuningUiAction.ApplyBoreProfile(id)
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Speed,
                                            title = stringResource(R.string.kernel_tuning_bore_enable),
                                            description = stringResource(R.string.kernel_tuning_bore_enable_desc),
                                            checked = uiState.boreEnabled,
                                            onCheckedChange = { enabled ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetBoreEnabled(enabled)
                                                )
                                            },
                                        )
                                    }
                                    uiState.boreKnobs.forEach { knob ->
                                        item(key = knob.key) {
                                            SettingsBaseWidget(
                                                icon = Icons.TwoTone.Tune,
                                                title = boreKnobTitle(knob.key),
                                                description = boreKnobDescription(knob.key),
                                                descriptionColumnContent = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 5.dp)
                                                    ) {
                                                        LabelText(label = knob.value)
                                                        LabelText(
                                                            label = stringResource(
                                                                R.string.kernel_tuning_bore_range,
                                                                knob.min,
                                                                knob.max
                                                            ),
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        )
                                                    }
                                                },
                                                onClick = { editingBore = knob },
                                            ) {}
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.borePersist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetBorePersist(persist)
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Refresh,
                                            title = stringResource(R.string.kernel_tuning_bore_reset),
                                            description = stringResource(R.string.kernel_tuning_bore_reset_desc),
                                            onClick = {
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.ResetBoreDefaults
                                                )
                                            },
                                        ) {}
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (uiState.zram.supported) {
                        item {
                            val zram = uiState.zram
                            val sizePresets =
                                remember(zram.totalRamBytes) { zramSizePresets(zram.totalRamBytes) }
                            val selectedSize = sizePresets.indexOf(zram.disksizeBytes)
                            val sizeDescription =
                                stringResource(R.string.kernel_tuning_zram_size_desc) +
                                        if (selectedSize < 0 && zram.disksizeBytes > 0) {
                                            " · " + stringResource(R.string.kernel_tuning_zram_custom) +
                                                    ": " + formatZramBytes(zram.disksizeBytes)
                                        } else {
                                            ""
                                        }
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_zram),
                                content = {
                                    item {
                                        SettingsChooseWidget(
                                            icon = Icons.TwoTone.Storage,
                                            title = stringResource(R.string.kernel_tuning_zram_size),
                                            description = sizeDescription,
                                            items = sizePresets.map {
                                                zramPresetLabel(it, zram.totalRamBytes)
                                            },
                                            selectedIndex = selectedSize,
                                            onSelectedIndexChange = { index ->
                                                sizePresets.getOrNull(index)?.let { size ->
                                                    viewModel.dispatch(
                                                        KernelTuningUiAction.ConfigureZram(
                                                            size,
                                                            zram.currentAlgo,
                                                            zram.maxStreams
                                                        )
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    item {
                                        val selectedAlgo = zram.algos.indexOf(zram.currentAlgo)
                                        SettingsChooseWidget(
                                            icon = Icons.TwoTone.Compress,
                                            title = stringResource(R.string.kernel_tuning_zram_algo),
                                            description = stringResource(R.string.kernel_tuning_zram_algo_desc),
                                            items = zram.algos,
                                            selectedIndex = selectedAlgo,
                                            onSelectedIndexChange = { index ->
                                                zram.algos.getOrNull(index)?.let { algo ->
                                                    viewModel.dispatch(
                                                        KernelTuningUiAction.ConfigureZram(
                                                            zram.disksizeBytes,
                                                            algo,
                                                            zram.maxStreams
                                                        )
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Memory,
                                            title = stringResource(R.string.kernel_tuning_zram_streams),
                                            description = stringResource(R.string.kernel_tuning_zram_streams_desc),
                                            descriptionColumnContent = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 5.dp)
                                                ) {
                                                    LabelText(label = zram.maxStreams.toString())
                                                }
                                            },
                                            onClick = { editingZramStreams = true },
                                        ) {}
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.SwapHoriz,
                                            title = stringResource(R.string.kernel_tuning_zram_swappiness),
                                            description = stringResource(R.string.kernel_tuning_zram_swappiness_desc),
                                            descriptionColumnContent = {
                                                if (zram.swappiness.isNotBlank()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 5.dp)
                                                    ) {
                                                        LabelText(label = zram.swappiness)
                                                    }
                                                }
                                            },
                                            onClick = { editingZramSwappiness = true },
                                        ) {}
                                    }
                                    if (zram.origBytes > 0 && zram.comprBytes > 0) {
                                        item {
                                            SettingsBaseWidget(
                                                icon = Icons.TwoTone.Info,
                                                title = stringResource(R.string.kernel_tuning_zram_stats),
                                                description = stringResource(
                                                    R.string.kernel_tuning_zram_stats_value,
                                                    formatZramBytes(zram.origBytes),
                                                    formatZramBytes(zram.memUsedBytes),
                                                    zram.origBytes.toDouble() / zram.comprBytes.toDouble()
                                                ),
                                            ) {}
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = zram.persist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetZramPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    item {
                        if (uiState.sysctls.isEmpty()) {
                            WarningCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                message = stringResource(R.string.kernel_tuning_sysctl_empty),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    lazySegmentColumn(
                        uiState.sysctls,
                        key = { _, it -> it.key }) { _, entry ->
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Tune,
                            title = entry.key,
                            description = entry.value,
                            descriptionColumnContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 5.dp)
                                ) {
                                    LabelText(
                                        label = if (entry.persist) {
                                            stringResource(R.string.persistent)
                                        } else {
                                            stringResource(R.string.temporary)
                                        }
                                    )
                                }
                            },
                            onClick = { editingEntry = entry },
                        ) {
                            val confirmDeleteSummary = stringResource(
                                R.string.confirm_delete_sysctl,
                                entry.key
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val confirmResult = confirmDialog.awaitConfirm(
                                            title = confirmDelete,
                                            content = confirmDeleteSummary
                                        )
                                        if (confirmResult != ConfirmResult.Confirmed)
                                            return@launch
                                        viewModel.dispatch(
                                            KernelTuningUiAction.RemoveSysctl(entry)
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            SysctlEditDialog(
                initial = null,
                onDismiss = { showAddDialog = false },
                onConfirm = { key, value, persist ->
                    showAddDialog = false
                    viewModel.dispatch(
                        KernelTuningUiAction.AddOrUpdateSysctl(key, value, persist)
                    )
                }
            )
        }

        editingEntry?.let { entry ->
            SysctlEditDialog(
                initial = entry,
                onDismiss = { editingEntry = null },
                onConfirm = { key, value, persist ->
                    editingEntry = null
                    viewModel.dispatch(
                        KernelTuningUiAction.AddOrUpdateSysctl(key, value, persist)
                    )
                }
            )
        }

        editingBore?.let { knob ->
            // Re-resolve against fresh state so the dialog never edits a stale copy.
            val live = uiState.boreKnobs.firstOrNull { it.key == knob.key } ?: knob
            BoreKnobDialog(
                knob = live,
                onDismiss = { editingBore = null },
                onConfirm = { value ->
                    editingBore = null
                    viewModel.dispatch(
                        KernelTuningUiAction.SetBoreKnob(live.key, value)
                    )
                }
            )
        }

        if (editingZramStreams) {
            val zram = uiState.zram
            NumberEditDialog(
                title = stringResource(R.string.kernel_tuning_zram_streams),
                initial = zram.maxStreams.toString(),
                min = 1,
                max = 64,
                hint = stringResource(R.string.kernel_tuning_bore_range, 1, 64),
                errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 1, 64),
                onDismiss = { editingZramStreams = false },
                onConfirm = { value ->
                    editingZramStreams = false
                    viewModel.dispatch(
                        KernelTuningUiAction.ConfigureZram(
                            zram.disksizeBytes,
                            zram.currentAlgo,
                            value.toLong()
                        )
                    )
                }
            )
        }

        if (editingZramSwappiness) {
            NumberEditDialog(
                title = stringResource(R.string.kernel_tuning_zram_swappiness),
                initial = uiState.zram.swappiness,
                min = 0,
                max = 200,
                hint = stringResource(R.string.kernel_tuning_bore_range, 0, 200),
                errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 0, 200),
                onDismiss = { editingZramSwappiness = false },
                onConfirm = { value ->
                    editingZramSwappiness = false
                    viewModel.dispatch(
                        KernelTuningUiAction.SetZramSwappiness(value)
                    )
                }
            )
        }
    }
}

@Composable
private fun SysctlEditDialog(
    initial: SysctlEntry?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit,
) {
    var key by remember(initial) { mutableStateOf(initial?.key.orEmpty()) }
    var value by remember(initial) { mutableStateOf(initial?.value.orEmpty()) }
    var persist by remember(initial) { mutableStateOf(initial?.persist ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.popupBlur(),
        containerColor = popupContainerColor(),
        title = { Text(stringResource(R.string.kernel_tuning_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.kernel_tuning_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = initial == null,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.kernel_tuning_value)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.kernel_tuning_persist),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = persist, onCheckedChange = { persist = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key.trim(), value, persist) },
                enabled = key.isNotBlank()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun boreProfileTitle(id: String): String = when (id) {
    "balanced" -> stringResource(R.string.bore_profile_balanced)
    "responsive" -> stringResource(R.string.bore_profile_responsive)
    "throughput" -> stringResource(R.string.bore_profile_throughput)
    "battery" -> stringResource(R.string.bore_profile_battery)
    else -> id
}

@Composable
private fun boreProfileDescription(id: String): String = when (id) {
    "balanced" -> stringResource(R.string.bore_profile_balanced_desc)
    "responsive" -> stringResource(R.string.bore_profile_responsive_desc)
    "throughput" -> stringResource(R.string.bore_profile_throughput_desc)
    "battery" -> stringResource(R.string.bore_profile_battery_desc)
    else -> id
}

@Composable
private fun boreKnobTitle(key: String): String = when (key) {
    "kernel.sched_burst_penalty_offset" -> stringResource(R.string.bore_knob_penalty_offset)
    "kernel.sched_burst_penalty_scale" -> stringResource(R.string.bore_knob_penalty_scale)
    "kernel.sched_burst_smoothness" -> stringResource(R.string.bore_knob_smoothness)
    "kernel.sched_burst_inherit_type" -> stringResource(R.string.bore_knob_inherit_type)
    "kernel.sched_burst_cache_lifetime" -> stringResource(R.string.bore_knob_cache_lifetime)
    "kernel.sched_burst_protect_slice_lv" -> stringResource(R.string.bore_knob_protect_slice_lv)
    "kernel.sched_burst_fork_atavistic" -> stringResource(R.string.bore_knob_fork_atavistic)
    else -> key.substringAfterLast('.')
}

@Composable
private fun boreKnobDescription(key: String): String = when (key) {
    "kernel.sched_burst_penalty_offset" -> stringResource(R.string.bore_knob_penalty_offset_desc)
    "kernel.sched_burst_penalty_scale" -> stringResource(R.string.bore_knob_penalty_scale_desc)
    "kernel.sched_burst_smoothness" -> stringResource(R.string.bore_knob_smoothness_desc)
    "kernel.sched_burst_inherit_type" -> stringResource(R.string.bore_knob_inherit_type_desc)
    "kernel.sched_burst_cache_lifetime" -> stringResource(R.string.bore_knob_cache_lifetime_desc)
    "kernel.sched_burst_protect_slice_lv" -> stringResource(R.string.bore_knob_protect_slice_lv_desc)
    "kernel.sched_burst_fork_atavistic" -> stringResource(R.string.bore_knob_fork_atavistic_desc)
    else -> key
}

@Composable
private fun BoreKnobDialog(
    knob: BoreKnob,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    NumberEditDialog(
        title = boreKnobTitle(knob.key),
        initial = knob.value,
        min = knob.min,
        max = knob.max,
        hint = stringResource(
            R.string.kernel_tuning_bore_range,
            knob.min,
            knob.max
        ) + " · " + stringResource(
            R.string.kernel_tuning_bore_default,
            knob.defaultValue
        ),
        errorHint = stringResource(
            R.string.kernel_tuning_bore_invalid,
            knob.min,
            knob.max
        ),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

private fun zramSizePresets(totalRamBytes: Long): List<Long> {
    val gb = 1024L * 1024 * 1024
    val min = 256L * 1024 * 1024
    return (listOf(gb, 2 * gb) +
            if (totalRamBytes > 0) {
                listOf(0.25, 0.5, 0.75).map { (totalRamBytes * it).toLong() }
            } else {
                emptyList()
            })
        .filter { it >= min }
        .filter { totalRamBytes <= 0 || it <= totalRamBytes }
        .distinct()
        .sorted()
}

@Composable
private fun zramPresetLabel(bytes: Long, totalRamBytes: Long): String {
    val gb = 1024L * 1024 * 1024
    if (totalRamBytes > 0) {
        val pct = (bytes * 100 / totalRamBytes).toInt()
        if (pct in listOf(25, 50, 75) && totalRamBytes * pct / 100 == bytes) {
            return stringResource(
                R.string.kernel_tuning_zram_ram_pct,
                pct,
                stringResource(R.string.kernel_tuning_zram_gb, bytes.toDouble() / gb)
            )
        }
    }
    return stringResource(R.string.kernel_tuning_zram_gb, bytes.toDouble() / gb)
}

@Composable
private fun formatZramBytes(bytes: Long): String {
    val mb = 1024L * 1024
    val gb = mb * 1024
    return if (bytes >= gb) {
        stringResource(R.string.kernel_tuning_zram_gb, bytes.toDouble() / gb)
    } else {
        stringResource(R.string.kernel_tuning_zram_mb, bytes.toDouble() / mb)
    }
}

@Composable
private fun NumberEditDialog(
    title: String,
    initial: String,
    min: Long,
    max: Long,
    hint: String,
    errorHint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val numeric = value.trim().toLongOrNull()
    val valid = numeric != null && numeric in min..max

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.popupBlur(),
        containerColor = popupContainerColor(),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.kernel_tuning_value)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        Text(if (valid || value.isBlank()) hint else errorHint)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = valid
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
