package com.originsu.manager.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.domain.model.BoreKnob
import com.originsu.manager.domain.model.CpuPolicy
import com.originsu.manager.domain.model.GpuDevice
import com.originsu.manager.domain.model.IoDevice
import com.originsu.manager.domain.model.LmkLevel
import com.originsu.manager.domain.model.PsiStats
import com.originsu.manager.domain.model.SchedKnob
import com.originsu.manager.domain.model.SysctlEntry
import com.originsu.manager.domain.model.VmKnob
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
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong
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
    var editingVm by remember { mutableStateOf<VmKnob?>(null) }
    var editingSched by remember { mutableStateOf<SchedKnob?>(null) }
    var editingLmkIndex by remember { mutableStateOf<Int?>(null) }
    var editingCpuFreqs by remember { mutableStateOf<CpuPolicy?>(null) }
    var editingGpuFreqs by remember { mutableStateOf<GpuDevice?>(null) }
    var editingIoReadAhead by remember { mutableStateOf<IoDevice?>(null) }
    var editingSchedutil by remember { mutableStateOf(false) }
    var editingZramStreams by remember { mutableStateOf(false) }
    var editingZramSwappiness by remember { mutableStateOf(false) }

    val confirmDelete = stringResource(R.string.confirm_delete)
    val context = LocalContext.current
    val exportSuccessMsg = stringResource(R.string.kernel_tuning_profile_exported)
    val transferFailedMsg = stringResource(R.string.operation_failed)
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(viewModel.exportProfileJson().toByteArray())
                    } ?: error("open failed")
                }.isSuccess
            }
            snackBarHost.showReplacingSnackbar(if (ok) exportSuccessMsg else transferFailedMsg)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                snackBarHost.showReplacingSnackbar(transferFailedMsg)
            } else {
                pendingImportJson = json
            }
        }
    }

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
                            title = stringResource(R.string.kernel_tuning_profiles),
                            content = {
                                item {
                                    SettingsJumpPageWidget(
                                        icon = Icons.TwoTone.Save,
                                        title = stringResource(R.string.kernel_tuning_profile_export),
                                        description = stringResource(R.string.kernel_tuning_profile_export_desc),
                                        onClick = {
                                            exportLauncher.launch("origintune_profile.json")
                                        },
                                    )
                                }
                                item {
                                    SettingsJumpPageWidget(
                                        icon = Icons.TwoTone.Restore,
                                        title = stringResource(R.string.kernel_tuning_profile_import),
                                        description = stringResource(R.string.kernel_tuning_profile_import_desc),
                                        onClick = {
                                            importLauncher.launch(arrayOf("application/json"))
                                        },
                                    )
                                }
                            }
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

                    if (uiState.cpu.supported) {
                        item {
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_cpu),
                                content = {
                                    uiState.cpu.policies.forEach { policy ->
                                        val govIndex =
                                            policy.availableGovernors.indexOf(policy.governor)
                                        if (policy.availableGovernors.isNotEmpty() && govIndex >= 0) {
                                            item(key = "cpu_gov_${policy.id}") {
                                                val policyLabel = cpuPolicyLabel(policy)
                                                SettingsChooseWidget(
                                                    icon = Icons.TwoTone.Speed,
                                                    title = policyLabel,
                                                    description = stringResource(R.string.kernel_tuning_cpu_governor),
                                                    items = policy.availableGovernors,
                                                    selectedIndex = govIndex,
                                                    onSelectedIndexChange = { index ->
                                                        policy.availableGovernors.getOrNull(index)
                                                            ?.let { governor ->
                                                                viewModel.dispatch(
                                                                    KernelTuningUiAction.SetCpuGovernor(
                                                                        policy.id,
                                                                        governor
                                                                    )
                                                                )
                                                            }
                                                    },
                                                )
                                            }
                                        } else {
                                            item(key = "cpu_gov_${policy.id}") {
                                                val policyLabel = cpuPolicyLabel(policy)
                                                SettingsBaseWidget(
                                                    icon = Icons.TwoTone.Speed,
                                                    title = policyLabel,
                                                    description = stringResource(R.string.kernel_tuning_cpu_governor) +
                                                            ": " + policy.governor,
                                                    enabled = false,
                                                ) {}
                                            }
                                        }
                                        item(key = "cpu_freq_${policy.id}") {
                                            val policyLabel = cpuPolicyLabel(policy)
                                            SettingsBaseWidget(
                                                icon = Icons.TwoTone.Tune,
                                                title = stringResource(R.string.kernel_tuning_cpu_freqs),
                                                description = policyLabel,
                                                descriptionColumnContent = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 5.dp)
                                                    ) {
                                                        LabelText(
                                                            label = stringResource(
                                                                R.string.kernel_tuning_cpu_freqs_value,
                                                                formatCpuMhz(policy.minFreqKhz),
                                                                formatCpuMhz(policy.maxFreqKhz)
                                                            )
                                                        )
                                                        if (policy.curFreqKhz > 0) {
                                                            LabelText(
                                                                label = formatCpuMhz(policy.curFreqKhz),
                                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = { editingCpuFreqs = policy },
                                            ) {}
                                        }
                                    }
                                    if (uiState.cpu.schedutilSupported) {
                                        item {
                                            SettingsBaseWidget(
                                                icon = Icons.TwoTone.Memory,
                                                title = stringResource(R.string.kernel_tuning_cpu_schedutil),
                                                description = stringResource(R.string.kernel_tuning_cpu_schedutil_desc),
                                                descriptionColumnContent = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 5.dp)
                                                    ) {
                                                        LabelText(label = uiState.cpu.schedutilRateLimitUs)
                                                    }
                                                },
                                                onClick = { editingSchedutil = true },
                                            ) {}
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.cpu.persist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetCpuPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (uiState.gpu.supported) {
                        item {
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_gpu),
                                content = {
                                    uiState.gpu.devices.forEach { device ->
                                        val gpuLabel = device.label.ifBlank { device.id }
                                        val govIndex =
                                            device.availableGovernors.indexOf(device.governor)
                                        if (device.availableGovernors.isNotEmpty() && govIndex >= 0) {
                                            item(key = "gpu_gov_${device.id}") {
                                                SettingsChooseWidget(
                                                    icon = Icons.TwoTone.Speed,
                                                    title = gpuLabel,
                                                    description = stringResource(R.string.kernel_tuning_gpu_governor),
                                                    items = device.availableGovernors,
                                                    selectedIndex = govIndex,
                                                    onSelectedIndexChange = { index ->
                                                        device.availableGovernors.getOrNull(index)
                                                            ?.let { governor ->
                                                                viewModel.dispatch(
                                                                    KernelTuningUiAction.SetGpuGovernor(
                                                                        device.id,
                                                                        governor
                                                                    )
                                                                )
                                                            }
                                                    },
                                                )
                                            }
                                        } else if (device.governor.isNotBlank()) {
                                            item(key = "gpu_gov_${device.id}") {
                                                SettingsBaseWidget(
                                                    icon = Icons.TwoTone.Speed,
                                                    title = gpuLabel,
                                                    description = stringResource(R.string.kernel_tuning_gpu_governor) +
                                                            ": " + device.governor,
                                                    enabled = false,
                                                ) {}
                                            }
                                        }
                                        if (device.minFreqHz > 0 && device.maxFreqHz > 0) {
                                            item(key = "gpu_freq_${device.id}") {
                                                SettingsBaseWidget(
                                                    icon = Icons.TwoTone.Tune,
                                                    title = stringResource(R.string.kernel_tuning_gpu_freqs),
                                                    description = gpuLabel,
                                                    descriptionColumnContent = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 5.dp)
                                                        ) {
                                                            LabelText(
                                                                label = stringResource(
                                                                    R.string.kernel_tuning_gpu_freqs_value,
                                                                    formatGpuMhz(device.minFreqHz),
                                                                    formatGpuMhz(device.maxFreqHz)
                                                                )
                                                            )
                                                            if (device.curFreqHz > 0) {
                                                                LabelText(
                                                                    label = formatGpuMhz(device.curFreqHz),
                                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = { editingGpuFreqs = device },
                                                ) {}
                                            }
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.gpu.persist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetGpuPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (uiState.io.supported) {
                        item {
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_io),
                                content = {
                                    uiState.io.devices.forEach { device ->
                                        val schedIndex =
                                            device.availableSchedulers.indexOf(device.scheduler)
                                        if (device.availableSchedulers.isNotEmpty() && schedIndex >= 0) {
                                            item(key = "io_sched_${device.name}") {
                                                SettingsChooseWidget(
                                                    icon = Icons.TwoTone.SwapHoriz,
                                                    title = device.name,
                                                    description = stringResource(R.string.kernel_tuning_io_scheduler),
                                                    items = device.availableSchedulers,
                                                    selectedIndex = schedIndex,
                                                    onSelectedIndexChange = { index ->
                                                        device.availableSchedulers.getOrNull(index)
                                                            ?.let { scheduler ->
                                                                viewModel.dispatch(
                                                                    KernelTuningUiAction.SetIoScheduler(
                                                                        device.name,
                                                                        scheduler
                                                                    )
                                                                )
                                                            }
                                                    },
                                                )
                                            }
                                        } else if (device.scheduler.isNotBlank()) {
                                            item(key = "io_sched_${device.name}") {
                                                SettingsBaseWidget(
                                                    icon = Icons.TwoTone.SwapHoriz,
                                                    title = device.name,
                                                    description = stringResource(R.string.kernel_tuning_io_scheduler) +
                                                            ": " + device.scheduler,
                                                    enabled = false,
                                                ) {}
                                            }
                                        }
                                        if (device.readAheadKb >= 0) {
                                            item(key = "io_ra_${device.name}") {
                                                SettingsBaseWidget(
                                                    icon = Icons.TwoTone.Storage,
                                                    title = stringResource(R.string.kernel_tuning_io_read_ahead),
                                                    description = device.name + " · " +
                                                            stringResource(R.string.kernel_tuning_io_read_ahead_desc),
                                                    descriptionColumnContent = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 5.dp)
                                                        ) {
                                                            LabelText(
                                                                label = stringResource(
                                                                    R.string.kernel_tuning_io_kb,
                                                                    device.readAheadKb
                                                                )
                                                            )
                                                        }
                                                    },
                                                    onClick = { editingIoReadAhead = device },
                                                ) {}
                                            }
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.io.persist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetIoPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (uiState.vm.supported) {
                        item {
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_vm),
                                content = {
                                    item {
                                        val profileIds = viewModel.vmProfileIds
                                        val selectedProfile =
                                            profileIds.indexOf(uiState.vm.profileId)
                                        SettingsChooseWidget(
                                            icon = Icons.TwoTone.Settings,
                                            title = stringResource(R.string.kernel_tuning_vm_profile),
                                            description = if (selectedProfile < 0) {
                                                stringResource(R.string.kernel_tuning_vm_profile_custom)
                                            } else {
                                                null
                                            },
                                            items = profileIds.map { vmProfileTitle(it) },
                                            itemDescriptions = profileIds.map {
                                                vmProfileDescription(it)
                                            },
                                            selectedIndex = selectedProfile,
                                            onSelectedIndexChange = { index ->
                                                profileIds.getOrNull(index)?.let { id ->
                                                    viewModel.dispatch(
                                                        KernelTuningUiAction.ApplyVmProfile(id)
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    uiState.vm.knobs.forEach { knob ->
                                        item(key = knob.key) {
                                            SettingsBaseWidget(
                                                icon = Icons.TwoTone.Tune,
                                                title = vmKnobTitle(knob.key),
                                                description = vmKnobDescription(knob.key),
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
                                                onClick = { editingVm = knob },
                                            ) {}
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.vm.persist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetVmPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Refresh,
                                            title = stringResource(R.string.kernel_tuning_vm_reset),
                                            description = stringResource(R.string.kernel_tuning_vm_reset_desc),
                                            onClick = {
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.ResetVmDefaults
                                                )
                                            },
                                        ) {}
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (uiState.sched.supported) {
                        item {
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_sched),
                                content = {
                                    uiState.sched.knobs.forEach { knob ->
                                        if (knob.min == 0L && knob.max == 1L) {
                                            item(key = knob.key) {
                                                SettingsSwitchWidget(
                                                    icon = Icons.TwoTone.Tune,
                                                    title = schedKnobTitle(knob.key),
                                                    description = schedKnobDescription(knob.key),
                                                    checked = knob.value.trim() != "0",
                                                    onCheckedChange = { enabled ->
                                                        viewModel.dispatch(
                                                            KernelTuningUiAction.SetSchedKnob(
                                                                knob.key,
                                                                if (enabled) "1" else "0"
                                                            )
                                                        )
                                                    },
                                                )
                                            }
                                        } else {
                                            item(key = knob.key) {
                                                SettingsBaseWidget(
                                                    icon = Icons.TwoTone.Tune,
                                                    title = schedKnobTitle(knob.key),
                                                    description = schedKnobDescription(knob.key),
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
                                                    onClick = { editingSched = knob },
                                                ) {}
                                            }
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.sched.persist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetSchedPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Refresh,
                                            title = stringResource(R.string.kernel_tuning_sched_reset),
                                            description = stringResource(R.string.kernel_tuning_sched_reset_desc),
                                            onClick = {
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.ResetSchedDefaults
                                                )
                                            },
                                        ) {}
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (uiState.lmk.supported) {
                        item {
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_lmk),
                                content = {
                                    item {
                                        val profileIds = viewModel.lmkProfileIds
                                        val selectedProfile =
                                            profileIds.indexOf(uiState.lmk.profileId)
                                        SettingsChooseWidget(
                                            icon = Icons.TwoTone.Settings,
                                            title = stringResource(R.string.kernel_tuning_lmk_profile),
                                            description = if (selectedProfile < 0) {
                                                stringResource(R.string.kernel_tuning_lmk_profile_custom)
                                            } else {
                                                null
                                            },
                                            items = profileIds.map { lmkProfileTitle(it) },
                                            itemDescriptions = profileIds.map {
                                                lmkProfileDescription(it)
                                            },
                                            selectedIndex = selectedProfile,
                                            onSelectedIndexChange = { index ->
                                                profileIds.getOrNull(index)?.let { id ->
                                                    viewModel.dispatch(
                                                        KernelTuningUiAction.ApplyLmkProfile(id)
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    uiState.lmk.levels.forEachIndexed { index, level ->
                                        item(key = "lmk_$index") {
                                            SettingsBaseWidget(
                                                icon = Icons.TwoTone.Tune,
                                                title = stringResource(
                                                    R.string.kernel_tuning_lmk_level,
                                                    index + 1
                                                ),
                                                description = stringResource(
                                                    R.string.kernel_tuning_lmk_level_value,
                                                    formatLmkMb(level.pages),
                                                    level.adj
                                                ),
                                                onClick = { editingLmkIndex = index },
                                            ) {}
                                        }
                                    }
                                    item {
                                        SettingsSwitchWidget(
                                            icon = Icons.TwoTone.Tune,
                                            title = stringResource(R.string.kernel_tuning_persist),
                                            checked = uiState.lmk.persist,
                                            onCheckedChange = { persist ->
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.SetLmkPersist(persist)
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Refresh,
                                            title = stringResource(R.string.kernel_tuning_lmk_reset),
                                            description = stringResource(R.string.kernel_tuning_lmk_reset_desc),
                                            onClick = {
                                                viewModel.dispatch(
                                                    KernelTuningUiAction.ResetLmkStock
                                                )
                                            },
                                        ) {}
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
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

                    if (uiState.diagnostics.supported) {
                        item {
                            val diag = uiState.diagnostics
                            SegmentedColumn(
                                title = stringResource(R.string.kernel_tuning_diag),
                                content = {
                                    item {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Info,
                                            title = stringResource(R.string.kernel_tuning_diag_load),
                                            description = diag.loadAvg,
                                        ) {}
                                    }
                                    item {
                                        PsiRow(
                                            title = stringResource(R.string.kernel_tuning_diag_psi_cpu),
                                            stats = diag.cpu,
                                        )
                                    }
                                    item {
                                        PsiRow(
                                            title = stringResource(R.string.kernel_tuning_diag_psi_mem),
                                            stats = diag.memory,
                                        )
                                    }
                                    item {
                                        PsiRow(
                                            title = stringResource(R.string.kernel_tuning_diag_psi_io),
                                            stats = diag.io,
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
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

        pendingImportJson?.let { json ->
            val importTitle = stringResource(R.string.kernel_tuning_profile_import)
            val importConfirm = stringResource(R.string.kernel_tuning_profile_import_confirm)
            LaunchedEffect(json) {
                val result = confirmDialog.awaitConfirm(
                    title = importTitle,
                    content = importConfirm
                )
                if (result == ConfirmResult.Confirmed) {
                    viewModel.dispatch(KernelTuningUiAction.ImportProfile(json))
                }
                pendingImportJson = null
            }
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

        editingVm?.let { knob ->
            // Re-resolve against fresh state so the dialog never edits a stale copy.
            val live = uiState.vm.knobs.firstOrNull { it.key == knob.key } ?: knob
            VmKnobDialog(
                knob = live,
                onDismiss = { editingVm = null },
                onConfirm = { value ->
                    editingVm = null
                    viewModel.dispatch(
                        KernelTuningUiAction.SetVmKnob(live.key, value)
                    )
                }
            )
        }

        editingSched?.let { knob ->
            // Re-resolve against fresh state so the dialog never edits a stale copy.
            val live = uiState.sched.knobs.firstOrNull { it.key == knob.key } ?: knob
            SchedKnobDialog(
                knob = live,
                onDismiss = { editingSched = null },
                onConfirm = { value ->
                    editingSched = null
                    viewModel.dispatch(
                        KernelTuningUiAction.SetSchedKnob(live.key, value)
                    )
                }
            )
        }

        editingLmkIndex?.let { index ->
            // Re-resolve against fresh state so the dialog never edits a stale copy.
            val live = uiState.lmk.levels.getOrNull(index)
            if (live != null) {
                LmkLevelDialog(
                    index = index,
                    level = live,
                    prevPages = uiState.lmk.levels.getOrNull(index - 1)?.pages,
                    nextPages = uiState.lmk.levels.getOrNull(index + 1)?.pages,
                    onDismiss = { editingLmkIndex = null },
                    onConfirm = { pages, adj ->
                        editingLmkIndex = null
                        viewModel.dispatch(
                            KernelTuningUiAction.SetLmkLevel(index, pages, adj)
                        )
                    }
                )
            }
        }

        editingCpuFreqs?.let { policy ->
            // Re-resolve against fresh state so the dialog never edits a stale copy.
            val live = uiState.cpu.policies.firstOrNull { it.id == policy.id } ?: policy
            CpuFreqDialog(
                policy = live,
                onDismiss = { editingCpuFreqs = null },
                onConfirm = { minKhz, maxKhz ->
                    editingCpuFreqs = null
                    viewModel.dispatch(
                        KernelTuningUiAction.SetCpuFreqs(live.id, minKhz, maxKhz)
                    )
                }
            )
        }

        editingGpuFreqs?.let { device ->
            // Re-resolve against fresh state so the dialog never edits a stale copy.
            val live = uiState.gpu.devices.firstOrNull { it.id == device.id } ?: device
            GpuFreqDialog(
                device = live,
                onDismiss = { editingGpuFreqs = null },
                onConfirm = { minHz, maxHz ->
                    editingGpuFreqs = null
                    viewModel.dispatch(
                        KernelTuningUiAction.SetGpuFreqs(live.id, minHz, maxHz)
                    )
                }
            )
        }

        editingIoReadAhead?.let { device ->
            // Re-resolve against fresh state so the dialog never edits a stale copy.
            val live = uiState.io.devices.firstOrNull { it.name == device.name } ?: device
            NumberEditDialog(
                title = stringResource(R.string.kernel_tuning_io_read_ahead) + " · " + live.name,
                initial = live.readAheadKb.coerceAtLeast(0).toString(),
                min = 0,
                max = 32768,
                hint = stringResource(R.string.kernel_tuning_bore_range, 0, 32768),
                errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 0, 32768),
                onDismiss = { editingIoReadAhead = null },
                onConfirm = { value ->
                    editingIoReadAhead = null
                    viewModel.dispatch(
                        KernelTuningUiAction.SetIoReadAhead(live.name, value.toLong())
                    )
                }
            )
        }

        if (editingSchedutil) {
            NumberEditDialog(
                title = stringResource(R.string.kernel_tuning_cpu_schedutil),
                initial = uiState.cpu.schedutilRateLimitUs,
                min = 0,
                max = 1000000,
                hint = stringResource(R.string.kernel_tuning_bore_range, 0, 1000000),
                errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 0, 1000000),
                onDismiss = { editingSchedutil = false },
                onConfirm = { value ->
                    editingSchedutil = false
                    viewModel.dispatch(
                        KernelTuningUiAction.SetSchedutilRateLimit(value)
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
private fun cpuPolicyLabel(policy: CpuPolicy): String =
    if (policy.cpus.isNotBlank()) {
        stringResource(R.string.kernel_tuning_cpu_policy, policy.cpus)
    } else {
        policy.id
    }

@Composable
private fun formatCpuMhz(khz: Long): String {
    return stringResource(R.string.kernel_tuning_cpu_mhz, khz / 1000.0)
}

/** Exact MHz rendering for dialog prefill (e.g. 1996800 kHz → "1996.8"). */
private fun khzToMhzString(khz: Long): String {
    val mhz = khz / 1000.0
    return if (mhz == kotlin.math.floor(mhz)) {
        mhz.toLong().toString()
    } else {
        mhz.toString()
    }
}

@Composable
private fun CpuFreqDialog(
    policy: CpuPolicy,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var minText by remember(policy) { mutableStateOf(khzToMhzString(policy.minFreqKhz)) }
    var maxText by remember(policy) { mutableStateOf(khzToMhzString(policy.maxFreqKhz)) }
    fun mhzToKhz(text: String): Long? {
        val mhz = text.trim().toDoubleOrNull() ?: return null
        if (!mhz.isFinite() || mhz <= 0) return null
        return (mhz * 1000).roundToLong()
    }
    val minKhz = mhzToKhz(minText)
    val maxKhz = mhzToKhz(maxText)
    val hwMin = if (policy.cpuinfoMinKhz > 0) policy.cpuinfoMinKhz else 1L
    val hwMax = if (policy.cpuinfoMaxKhz > 0) policy.cpuinfoMaxKhz else Long.MAX_VALUE
    val valid = minKhz != null && maxKhz != null &&
            minKhz in hwMin..hwMax && maxKhz in hwMin..hwMax && minKhz <= maxKhz
    val hwRangeKnown = policy.cpuinfoMinKhz > 0 && policy.cpuinfoMaxKhz > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.popupBlur(),
        containerColor = popupContainerColor(),
        title = {
            Text(
                stringResource(
                    R.string.kernel_tuning_cpu_policy,
                    policy.cpus.ifBlank { policy.id }
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text(stringResource(R.string.kernel_tuning_cpu_min_freq)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text(stringResource(R.string.kernel_tuning_cpu_max_freq)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        if (hwRangeKnown) {
                            Text(
                                if (valid) {
                                    stringResource(
                                        R.string.kernel_tuning_cpu_hw_range,
                                        formatCpuMhz(hwMin),
                                        formatCpuMhz(hwMax)
                                    )
                                } else {
                                    stringResource(
                                        R.string.kernel_tuning_cpu_freq_invalid,
                                        formatCpuMhz(hwMin),
                                        formatCpuMhz(hwMax)
                                    )
                                }
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && minKhz != null && maxKhz != null) onConfirm(minKhz, maxKhz)
                },
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

@Composable
private fun formatGpuMhz(hz: Long): String {
    return stringResource(R.string.kernel_tuning_gpu_mhz, hz / 1000000.0)
}

/** Exact MHz rendering for dialog prefill (e.g. 1300000000 Hz → "1300"). */
private fun hzToMhzString(hz: Long): String {
    val mhz = hz / 1000000.0
    return if (mhz == kotlin.math.floor(mhz)) {
        mhz.toLong().toString()
    } else {
        mhz.toString()
    }
}

@Composable
private fun GpuFreqDialog(
    device: GpuDevice,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var minText by remember(device) { mutableStateOf(hzToMhzString(device.minFreqHz)) }
    var maxText by remember(device) { mutableStateOf(hzToMhzString(device.maxFreqHz)) }
    fun mhzToHz(text: String): Long? {
        val mhz = text.trim().toDoubleOrNull() ?: return null
        if (!mhz.isFinite() || mhz <= 0) return null
        return (mhz * 1000000).roundToLong()
    }
    val minHz = mhzToHz(minText)
    val maxHz = mhzToHz(maxText)
    val advertised = device.availableFreqsHz
    val valid = minHz != null && maxHz != null && minHz <= maxHz &&
            (advertised.isEmpty() || (minHz in advertised && maxHz in advertised))

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.popupBlur(),
        containerColor = popupContainerColor(),
        title = { Text(device.label.ifBlank { device.id }) },
        text = {
            Column {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text(stringResource(R.string.kernel_tuning_gpu_min_freq)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text(stringResource(R.string.kernel_tuning_gpu_max_freq)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        if (advertised.isNotEmpty()) {
                            Text(
                                if (valid) {
                                    stringResource(
                                        R.string.kernel_tuning_gpu_range,
                                        formatGpuMhz(advertised.min()),
                                        formatGpuMhz(advertised.max())
                                    )
                                } else {
                                    stringResource(R.string.kernel_tuning_gpu_freq_invalid)
                                }
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && minHz != null && maxHz != null) onConfirm(minHz, maxHz)
                },
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

@Composable
private fun vmProfileTitle(id: String): String = when (id) {
    "balanced" -> stringResource(R.string.vm_profile_balanced)
    "responsive" -> stringResource(R.string.vm_profile_responsive)
    "throughput" -> stringResource(R.string.vm_profile_throughput)
    "battery" -> stringResource(R.string.vm_profile_battery)
    else -> id
}

@Composable
private fun vmProfileDescription(id: String): String = when (id) {
    "balanced" -> stringResource(R.string.vm_profile_balanced_desc)
    "responsive" -> stringResource(R.string.vm_profile_responsive_desc)
    "throughput" -> stringResource(R.string.vm_profile_throughput_desc)
    "battery" -> stringResource(R.string.vm_profile_battery_desc)
    else -> id
}

@Composable
private fun vmKnobTitle(key: String): String = when (key) {
    "vm.swappiness" -> stringResource(R.string.vm_knob_swappiness)
    "vm.dirty_ratio" -> stringResource(R.string.vm_knob_dirty_ratio)
    "vm.dirty_background_ratio" -> stringResource(R.string.vm_knob_dirty_background_ratio)
    "vm.dirty_expire_centisecs" -> stringResource(R.string.vm_knob_dirty_expire_centisecs)
    "vm.dirty_writeback_centisecs" -> stringResource(R.string.vm_knob_dirty_writeback_centisecs)
    "vm.vfs_cache_pressure" -> stringResource(R.string.vm_knob_vfs_cache_pressure)
    "vm.overcommit_memory" -> stringResource(R.string.vm_knob_overcommit_memory)
    else -> key.substringAfterLast('.')
}

@Composable
private fun vmKnobDescription(key: String): String = when (key) {
    "vm.swappiness" -> stringResource(R.string.vm_knob_swappiness_desc)
    "vm.dirty_ratio" -> stringResource(R.string.vm_knob_dirty_ratio_desc)
    "vm.dirty_background_ratio" -> stringResource(R.string.vm_knob_dirty_background_ratio_desc)
    "vm.dirty_expire_centisecs" -> stringResource(R.string.vm_knob_dirty_expire_centisecs_desc)
    "vm.dirty_writeback_centisecs" -> stringResource(R.string.vm_knob_dirty_writeback_centisecs_desc)
    "vm.vfs_cache_pressure" -> stringResource(R.string.vm_knob_vfs_cache_pressure_desc)
    "vm.overcommit_memory" -> stringResource(R.string.vm_knob_overcommit_memory_desc)
    else -> key
}

@Composable
private fun VmKnobDialog(
    knob: VmKnob,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    NumberEditDialog(
        title = vmKnobTitle(knob.key),
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

@Composable
private fun schedKnobTitle(key: String): String = when (key) {
    "kernel.sched_util_clamp_min" -> stringResource(R.string.sched_knob_uclamp_min)
    "kernel.sched_util_clamp_max" -> stringResource(R.string.sched_knob_uclamp_max)
    "kernel.sched_util_clamp_min_rt_default" -> stringResource(R.string.sched_knob_uclamp_min_rt)
    "kernel.sched_energy_aware" -> stringResource(R.string.sched_knob_eas)
    else -> key.substringAfterLast('.')
}

@Composable
private fun schedKnobDescription(key: String): String = when (key) {
    "kernel.sched_util_clamp_min" -> stringResource(R.string.sched_knob_uclamp_min_desc)
    "kernel.sched_util_clamp_max" -> stringResource(R.string.sched_knob_uclamp_max_desc)
    "kernel.sched_util_clamp_min_rt_default" -> stringResource(R.string.sched_knob_uclamp_min_rt_desc)
    "kernel.sched_energy_aware" -> stringResource(R.string.sched_knob_eas_desc)
    else -> key
}

@Composable
private fun SchedKnobDialog(
    knob: SchedKnob,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    NumberEditDialog(
        title = schedKnobTitle(knob.key),
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

@Composable
private fun lmkProfileTitle(id: String): String = when (id) {
    "balanced" -> stringResource(R.string.lmk_profile_balanced)
    "multitasking" -> stringResource(R.string.lmk_profile_multitasking)
    "aggressive" -> stringResource(R.string.lmk_profile_aggressive)
    else -> id
}

@Composable
private fun lmkProfileDescription(id: String): String = when (id) {
    "balanced" -> stringResource(R.string.lmk_profile_balanced_desc)
    "multitasking" -> stringResource(R.string.lmk_profile_multitasking_desc)
    "aggressive" -> stringResource(R.string.lmk_profile_aggressive_desc)
    else -> id
}

@Composable
private fun formatLmkMb(pages: Long): String {
    return stringResource(R.string.kernel_tuning_lmk_mb, pages / 256.0)
}

/** Exact MB rendering for dialog prefill (e.g. 18432 pages → "72"). */
private fun pagesToMbString(pages: Long): String {
    val mb = pages / 256.0
    return if (mb == kotlin.math.floor(mb)) {
        mb.toLong().toString()
    } else {
        mb.toString()
    }
}

@Composable
private fun LmkLevelDialog(
    index: Int,
    level: LmkLevel,
    prevPages: Long?,
    nextPages: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var mbText by remember(level) { mutableStateOf(pagesToMbString(level.pages)) }
    var adjText by remember(level) { mutableStateOf(level.adj.toString()) }
    fun mbToPages(text: String): Long? {
        val mb = text.trim().toDoubleOrNull() ?: return null
        if (!mb.isFinite() || mb <= 0) return null
        return (mb * 256).roundToLong()
    }
    val pages = mbToPages(mbText)
    val adj = adjText.trim().toLongOrNull()
    // Neighbor bounds, in pages; open ends fall back to the widest range.
    val lo = prevPages?.plus(1) ?: 1L
    val hi = nextPages?.minus(1) ?: Long.MAX_VALUE
    val valid = pages != null && adj != null &&
            pages in lo..hi && adj in -1000..1000

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.popupBlur(),
        containerColor = popupContainerColor(),
        title = { Text(stringResource(R.string.kernel_tuning_lmk_level, index + 1)) },
        text = {
            Column {
                OutlinedTextField(
                    value = mbText,
                    onValueChange = { mbText = it },
                    label = { Text(stringResource(R.string.kernel_tuning_lmk_pages)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = adjText,
                    onValueChange = { adjText = it },
                    label = { Text(stringResource(R.string.kernel_tuning_lmk_adj)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        if (prevPages != null || nextPages != null) {
                            Text(
                                if (valid) {
                                    stringResource(
                                        R.string.kernel_tuning_lmk_level_value,
                                        formatLmkMb(level.pages),
                                        level.adj
                                    )
                                } else {
                                    stringResource(
                                        R.string.kernel_tuning_lmk_invalid,
                                        formatLmkMb(lo),
                                        if (hi == Long.MAX_VALUE) {
                                            stringResource(R.string.kernel_tuning_lmk_no_limit)
                                        } else {
                                            formatLmkMb(hi)
                                        }
                                    )
                                }
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && pages != null && adj != null) onConfirm(pages, adj)
                },
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

@Composable
private fun PsiRow(
    title: String,
    stats: PsiStats,
) {
    SettingsBaseWidget(
        icon = Icons.TwoTone.Info,
        title = title,
        description = stringResource(
            R.string.kernel_tuning_diag_psi_value,
            stats.someAvg10,
            stats.fullAvg10
        ),
        descriptionColumnContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
            ) {
                LabelText(
                    label = stringResource(
                        R.string.kernel_tuning_diag_stall_total,
                        formatStallUs(stats.someTotal)
                    ),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    ) {}
}

@Composable
private fun formatStallUs(us: Long): String {
    return if (us >= 1000000) {
        stringResource(R.string.kernel_tuning_diag_stall_s, us / 1000000.0)
    } else {
        stringResource(R.string.kernel_tuning_diag_stall_ms, (us / 1000.0).roundToLong())
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
