package com.kazesu.manager.ui.screen.tune

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazesu.manager.R
import com.kazesu.manager.data.model.BoreKnob
import com.kazesu.manager.data.model.CpuPolicy
import com.kazesu.manager.data.model.GpuDevice
import com.kazesu.manager.data.model.IoDevice
import com.kazesu.manager.data.model.LmkLevel
import com.kazesu.manager.data.model.SchedKnob
import com.kazesu.manager.data.model.SysctlEntry
import com.kazesu.manager.data.model.VmKnob
import com.kazesu.manager.ui.navigation3.LocalNavigator
import com.kazesu.manager.ui.util.CustomTextKey
import com.kazesu.manager.ui.util.customText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * KazeTune: root-side kernel tuning. Everything here is sysfs/procfs access
 * through a root shell, so it works on any rooted device and needs no kernel
 * change. The layout is written once against [TunePage]/[TuneSection]; each of
 * those picks the Material or Miuix implementation from [LocalUiMode].
 */
@Composable
fun TuneScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: TuneViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasSnapshot by viewModel.hasSnapshot.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }

    var tcpPicker by remember { mutableStateOf(false) }
    var cpuGovernorPicker by remember { mutableStateOf<CpuPolicy?>(null) }
    var cpuFreqEditor by remember { mutableStateOf<CpuPolicy?>(null) }
    var gpuGovernorPicker by remember { mutableStateOf<GpuDevice?>(null) }
    var gpuFreqEditor by remember { mutableStateOf<GpuDevice?>(null) }
    var ioSchedulerPicker by remember { mutableStateOf<IoDevice?>(null) }
    var ioReadAheadEditor by remember { mutableStateOf<IoDevice?>(null) }
    var schedutilEditor by remember { mutableStateOf(false) }
    var vmProfilePicker by remember { mutableStateOf(false) }
    var vmKnobEditor by remember { mutableStateOf<VmKnob?>(null) }
    var schedKnobEditor by remember { mutableStateOf<SchedKnob?>(null) }
    var lmkProfilePicker by remember { mutableStateOf(false) }
    var lmkLevelEditor by remember { mutableStateOf<Int?>(null) }
    var boreProfilePicker by remember { mutableStateOf(false) }
    var boreKnobEditor by remember { mutableStateOf<BoreKnob?>(null) }
    var zramSizePicker by remember { mutableStateOf(false) }
    var zramAlgoPicker by remember { mutableStateOf(false) }
    var zramStreamsEditor by remember { mutableStateOf(false) }
    var zramSwappinessEditor by remember { mutableStateOf(false) }
    var addingSysctl by remember { mutableStateOf(false) }
    var editingSysctl by remember { mutableStateOf<SysctlEntry?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val exportFailedMsg = stringResource(R.string.operation_failed)
    val exportSuccessMsg = stringResource(R.string.kernel_tuning_profile_exported)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = viewModel.exportProfileJson()
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("empty")
                }
            }
            Toast.makeText(
                context,
                if (written.isSuccess) exportSuccessMsg else exportFailedMsg,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                Toast.makeText(context, exportFailedMsg, Toast.LENGTH_SHORT).show()
            } else {
                pendingImport = json
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TuneUiEvent.Message ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.dispatch(TuneUiAction.Refresh)
        onPauseOrDispose { }
    }

    val q = query.trim()
    val tcpVisible = matchesQuery(q, "tcp", "congestion", state.tcpCurrent) ||
        state.tcpAvailable.any { matchesQuery(q, it) }
    val cpuVisible = state.cpu.policies.any { p ->
        matchesQuery(q, p.id, p.cpus, p.governor, p.availableGovernors.joinToString(" "))
    } || matchesQuery(q, "cpu", "governor", "frequency", "schedutil", "mhz")
    val gpuVisible = state.gpu.devices.any { d ->
        matchesQuery(q, d.id, d.label, d.governor, d.availableGovernors.joinToString(" "))
    } || matchesQuery(q, "gpu", "governor", "clock", "mhz")
    val ioVisible = state.io.devices.any { d ->
        matchesQuery(q, d.name, d.scheduler, d.availableSchedulers.joinToString(" "))
    } || matchesQuery(q, "io", "scheduler", "read ahead", "read-ahead", "block")
    val vmVisible = state.vm.knobs.any { matchesQuery(q, it.key) } ||
        matchesQuery(q, "memory", "vm", "swappiness", "dirty", "cache pressure", "profile")
    val schedVisible = state.sched.knobs.any { matchesQuery(q, it.key) } ||
        matchesQuery(q, "scheduler", "uclamp", "energy", "eas")
    val lmkVisible = matchesQuery(q, "lmk", "low memory", "killer", "minfree", "oom")
    val boreVisible = state.boreKnobs.any { matchesQuery(q, it.key) } ||
        matchesQuery(q, "bore", "scheduler", "burst")
    val zramVisible = matchesQuery(q, "zram", "compression", "swap", "swappiness", "memory")
    val sysctlVisible = state.sysctls.any { matchesQuery(q, it.key, it.value) } ||
        matchesQuery(q, "sysctl", "tunable", "kernel")
    val diagVisible = matchesQuery(q, "diagnostics", "load", "psi", "stall", "pressure")
    val profilesVisible = matchesQuery(
        q, "profile", "snapshot", "backup", "restore", "export", "import", "preset",
    )

    TunePage(
        title = customText(CustomTextKey.PAGE_TUNE),
        loading = state.isLoading,
        onBack = { navigator.pop() },
    ) {
        TuneSearchField(query = query, onQueryChange = { query = it })

        if (!tcpVisible && !cpuVisible && !gpuVisible && !ioVisible && !vmVisible &&
            !schedVisible && !lmkVisible && !boreVisible && !zramVisible && !sysctlVisible &&
            !diagVisible && !profilesVisible
        ) {
            TuneBodyText(stringResource(R.string.kernel_tuning_search_empty, q))
        }

        if (profilesVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_profiles)) {
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_profile_export),
                    description = stringResource(R.string.kernel_tuning_profile_export_desc),
                    onClick = { exportLauncher.launch("kazetune_profile.json") },
                )
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_profile_import),
                    description = stringResource(R.string.kernel_tuning_profile_import_desc),
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                )
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_snapshot_save),
                    description = stringResource(R.string.kernel_tuning_snapshot_save_desc),
                    onClick = { viewModel.dispatch(TuneUiAction.SaveSnapshot) },
                )
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_snapshot_restore),
                    description = stringResource(
                        if (hasSnapshot) R.string.kernel_tuning_snapshot_restore_desc
                        else R.string.kernel_tuning_snapshot_empty
                    ),
                    enabled = hasSnapshot,
                    onClick = if (hasSnapshot) {
                        { confirmRestore = true }
                    } else {
                        null
                    },
                )
                TuneBodyText(stringResource(R.string.kernel_tuning_apply_hint))
            }
        }

        if (tcpVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_tcp)) {
                if (state.tcpAvailable.isEmpty()) {
                    TuneRow(
                        title = stringResource(R.string.kernel_tuning_tcp_empty),
                        enabled = false,
                    )
                } else {
                    TuneRow(
                        title = stringResource(R.string.kernel_tuning_tcp),
                        value = state.tcpCurrent,
                        onClick = { tcpPicker = true },
                    )
                    TuneSwitchRow(
                        title = stringResource(R.string.kernel_tuning_persist),
                        checked = state.tcpPersist,
                        onCheckedChange = { viewModel.dispatch(TuneUiAction.SetTcpPersist(it)) },
                    )
                }
            }
        }

        if (state.cpu.supported && cpuVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_cpu)) {
                state.cpu.policies.forEach { policy ->
                    val policyLabel = cpuPolicyLabel(policy)
                    TuneRow(
                        title = policyLabel,
                        description = stringResource(R.string.kernel_tuning_cpu_governor),
                        value = policy.governor,
                        onClick = if (policy.availableGovernors.isNotEmpty()) {
                            { cpuGovernorPicker = policy }
                        } else {
                            null
                        },
                    )
                    TuneRow(
                        title = stringResource(R.string.kernel_tuning_cpu_freqs),
                        description = policyLabel,
                        value = stringResource(
                            R.string.kernel_tuning_cpu_freqs_value,
                            formatKhz(policy.minFreqKhz),
                            formatKhz(policy.maxFreqKhz),
                        ),
                        onClick = { cpuFreqEditor = policy },
                    )
                }
                if (state.cpu.schedutilSupported) {
                    TuneRow(
                        title = stringResource(R.string.kernel_tuning_cpu_schedutil),
                        description = stringResource(R.string.kernel_tuning_cpu_schedutil_desc),
                        value = state.cpu.schedutilRateLimitUs,
                        onClick = { schedutilEditor = true },
                    )
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = state.cpu.persist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetCpuPersist(it)) },
                )
            }
        }

        if (state.gpu.supported && gpuVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_gpu)) {
                state.gpu.devices.forEach { device ->
                    val label = device.label.ifBlank { device.id }
                    if (device.governor.isNotBlank() || device.availableGovernors.isNotEmpty()) {
                        TuneRow(
                            title = label,
                            description = stringResource(R.string.kernel_tuning_gpu_governor),
                            value = device.governor,
                            onClick = if (device.availableGovernors.isNotEmpty()) {
                                { gpuGovernorPicker = device }
                            } else {
                                null
                            },
                        )
                    }
                    if (device.minFreqHz > 0 && device.maxFreqHz > 0) {
                        TuneRow(
                            title = stringResource(R.string.kernel_tuning_gpu_freqs),
                            description = label,
                            value = stringResource(
                                R.string.kernel_tuning_gpu_freqs_value,
                                formatGpuHz(device.minFreqHz),
                                formatGpuHz(device.maxFreqHz),
                            ),
                            onClick = { gpuFreqEditor = device },
                        )
                    }
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = state.gpu.persist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetGpuPersist(it)) },
                )
            }
        }

        if (state.io.supported && ioVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_io)) {
                state.io.devices.forEach { device ->
                    if (device.scheduler.isNotBlank() || device.availableSchedulers.isNotEmpty()) {
                        TuneRow(
                            title = device.name,
                            description = stringResource(R.string.kernel_tuning_io_scheduler),
                            value = device.scheduler,
                            onClick = if (device.availableSchedulers.isNotEmpty()) {
                                { ioSchedulerPicker = device }
                            } else {
                                null
                            },
                        )
                    }
                    if (device.readAheadKb >= 0) {
                        TuneRow(
                            title = stringResource(R.string.kernel_tuning_io_read_ahead),
                            description = device.name + " · " +
                                stringResource(R.string.kernel_tuning_io_read_ahead_desc),
                            value = stringResource(
                                R.string.kernel_tuning_io_kb,
                                device.readAheadKb,
                            ),
                            onClick = { ioReadAheadEditor = device },
                        )
                    }
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = state.io.persist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetIoPersist(it)) },
                )
            }
        }

        if (state.vm.supported && vmVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_vm)) {
                val selected = viewModel.vmProfileIds.indexOf(state.vm.profileId)
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_vm_profile),
                    description = if (selected < 0) {
                        stringResource(R.string.kernel_tuning_vm_profile_custom)
                    } else {
                        vmProfileTitle(state.vm.profileId)
                    },
                    value = if (selected < 0) null else vmProfileTitle(state.vm.profileId),
                    onClick = { vmProfilePicker = true },
                )
                state.vm.knobs.forEach { knob ->
                    TuneRow(
                        title = vmKnobTitle(knob.key),
                        description = vmKnobDescription(knob.key),
                        value = knob.value,
                        onClick = { vmKnobEditor = knob },
                    )
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = state.vm.persist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetVmPersist(it)) },
                )
                TuneActionRow(
                    title = stringResource(R.string.kernel_tuning_vm_reset),
                    description = stringResource(R.string.kernel_tuning_vm_reset_desc),
                    onClick = { viewModel.dispatch(TuneUiAction.ResetVmDefaults) },
                )
            }
        }

        if (state.sched.supported && schedVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_sched)) {
                state.sched.knobs.forEach { knob ->
                    if (knob.min == 0L && knob.max == 1L) {
                        TuneSwitchRow(
                            title = schedKnobTitle(knob.key),
                            description = schedKnobDescription(knob.key),
                            checked = knob.value.trim() != "0",
                            onCheckedChange = { enabled ->
                                viewModel.dispatch(
                                    TuneUiAction.SetSchedKnob(knob.key, if (enabled) "1" else "0")
                                )
                            },
                        )
                    } else {
                        TuneRow(
                            title = schedKnobTitle(knob.key),
                            description = schedKnobDescription(knob.key),
                            value = knob.value,
                            onClick = { schedKnobEditor = knob },
                        )
                    }
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = state.sched.persist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetSchedPersist(it)) },
                )
                TuneActionRow(
                    title = stringResource(R.string.kernel_tuning_sched_reset),
                    description = stringResource(R.string.kernel_tuning_sched_reset_desc),
                    onClick = { viewModel.dispatch(TuneUiAction.ResetSchedDefaults) },
                )
            }
        }

        if (state.lmk.supported && lmkVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_lmk)) {
                val selected = viewModel.lmkProfileIds.indexOf(state.lmk.profileId)
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_lmk_profile),
                    description = if (selected < 0) {
                        stringResource(R.string.kernel_tuning_lmk_profile_custom)
                    } else {
                        lmkProfileTitle(state.lmk.profileId)
                    },
                    value = if (selected < 0) null else lmkProfileTitle(state.lmk.profileId),
                    onClick = { lmkProfilePicker = true },
                )
                state.lmk.levels.forEachIndexed { index, level ->
                    TuneRow(
                        title = stringResource(R.string.kernel_tuning_lmk_level, index + 1),
                        value = stringResource(
                            R.string.kernel_tuning_lmk_level_value,
                            formatLmkMb(level.pages),
                            level.adj,
                        ),
                        onClick = { lmkLevelEditor = index },
                    )
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = state.lmk.persist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetLmkPersist(it)) },
                )
                TuneActionRow(
                    title = stringResource(R.string.kernel_tuning_lmk_reset),
                    description = stringResource(R.string.kernel_tuning_lmk_reset_desc),
                    onClick = { viewModel.dispatch(TuneUiAction.ResetLmkStock) },
                )
            }
        }

        if (state.boreSupported && boreVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_bore)) {
                val selected = viewModel.boreProfileIds.indexOf(state.boreProfileId)
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_bore_profile),
                    description = if (selected < 0) {
                        stringResource(R.string.kernel_tuning_bore_profile_custom)
                    } else {
                        boreProfileDescription(state.boreProfileId)
                    },
                    value = if (selected < 0) null else boreProfileTitle(state.boreProfileId),
                    onClick = { boreProfilePicker = true },
                )
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_bore_enable),
                    description = stringResource(R.string.kernel_tuning_bore_enable_desc),
                    checked = state.boreEnabled,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetBoreEnabled(it)) },
                )
                state.boreKnobs.forEach { knob ->
                    TuneRow(
                        title = boreKnobTitle(knob.key),
                        description = boreKnobDescription(knob.key),
                        value = knob.value,
                        onClick = { boreKnobEditor = knob },
                    )
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = state.borePersist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetBorePersist(it)) },
                )
                TuneActionRow(
                    title = stringResource(R.string.kernel_tuning_bore_reset),
                    description = stringResource(R.string.kernel_tuning_bore_reset_desc),
                    onClick = { viewModel.dispatch(TuneUiAction.ResetBoreDefaults) },
                )
            }
        }

        if (state.zram.supported && zramVisible) {
            val zram = state.zram
            val sizePresets = remember(zram.totalRamBytes) {
                zramSizePresets(zram.totalRamBytes)
            }
            TuneSection(stringResource(R.string.kernel_tuning_zram)) {
                val selectedSize = sizePresets.indexOf(zram.disksizeBytes)
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_zram_size),
                    description = stringResource(R.string.kernel_tuning_zram_size_desc) +
                        if (selectedSize < 0 && zram.disksizeBytes > 0) {
                            " · " + stringResource(R.string.kernel_tuning_zram_custom) + ": " +
                                formatZramBytes(zram.disksizeBytes)
                        } else {
                            ""
                        },
                    value = if (selectedSize >= 0) {
                        zramPresetLabel(sizePresets[selectedSize], zram.totalRamBytes)
                    } else {
                        formatZramBytes(zram.disksizeBytes)
                    },
                    onClick = { zramSizePicker = true },
                )
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_zram_algo),
                    description = stringResource(R.string.kernel_tuning_zram_algo_desc),
                    value = zram.currentAlgo,
                    onClick = if (zram.algos.isNotEmpty()) {
                        { zramAlgoPicker = true }
                    } else {
                        null
                    },
                )
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_zram_streams),
                    description = stringResource(R.string.kernel_tuning_zram_streams_desc),
                    value = zram.maxStreams.toString(),
                    onClick = { zramStreamsEditor = true },
                )
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_zram_swappiness),
                    description = stringResource(R.string.kernel_tuning_zram_swappiness_desc),
                    value = zram.swappiness.takeIf { it.isNotBlank() },
                    onClick = { zramSwappinessEditor = true },
                )
                if (zram.origBytes > 0 && zram.comprBytes > 0) {
                    TuneStatLine(
                        stringResource(
                            R.string.kernel_tuning_zram_stats_value,
                            formatZramBytes(zram.origBytes),
                            formatZramBytes(zram.memUsedBytes),
                            zram.origBytes.toDouble() / zram.comprBytes.toDouble(),
                        )
                    )
                }
                TuneSwitchRow(
                    title = stringResource(R.string.kernel_tuning_persist),
                    checked = zram.persist,
                    onCheckedChange = { viewModel.dispatch(TuneUiAction.SetZramPersist(it)) },
                )
            }
        }

        if (sysctlVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_add)) {
                if (state.sysctls.isEmpty()) {
                    TuneBodyText(stringResource(R.string.kernel_tuning_sysctl_empty))
                } else {
                    state.sysctls.forEach { entry ->
                        TuneRow(
                            title = entry.key,
                            description = stringResource(
                                if (entry.persist) R.string.kernel_tuning_persistent
                                else R.string.kernel_tuning_temporary
                            ),
                            value = entry.value,
                            onClick = { editingSysctl = entry },
                        )
                    }
                }
                TuneActionRow(
                    title = "+ " + stringResource(R.string.kernel_tuning_add),
                    onClick = { addingSysctl = true },
                )
            }
        }

        if (state.diagnostics.supported && diagVisible) {
            TuneSection(stringResource(R.string.kernel_tuning_diag)) {
                TuneRow(
                    title = stringResource(R.string.kernel_tuning_diag_load),
                    value = state.diagnostics.loadAvg,
                )
                PsiRow(
                    title = stringResource(R.string.kernel_tuning_diag_psi_cpu),
                    someAvg10 = state.diagnostics.cpu.someAvg10,
                    fullAvg10 = state.diagnostics.cpu.fullAvg10,
                    totalUs = state.diagnostics.cpu.someTotal,
                )
                PsiRow(
                    title = stringResource(R.string.kernel_tuning_diag_psi_mem),
                    someAvg10 = state.diagnostics.memory.someAvg10,
                    fullAvg10 = state.diagnostics.memory.fullAvg10,
                    totalUs = state.diagnostics.memory.someTotal,
                )
                PsiRow(
                    title = stringResource(R.string.kernel_tuning_diag_psi_io),
                    someAvg10 = state.diagnostics.io.someAvg10,
                    fullAvg10 = state.diagnostics.io.fullAvg10,
                    totalUs = state.diagnostics.io.someTotal,
                )
            }
        }
    }

    // ----- TCP -------------------------------------------------------------
    if (tcpPicker) {
        TuneChoiceDialog(
            title = stringResource(R.string.kernel_tuning_tcp),
            choices = state.tcpAvailable.map { TuneChoice(label = it) },
            selectedIndex = state.tcpAvailable.indexOf(state.tcpCurrent),
            onDismiss = { tcpPicker = false },
            onSelect = { index ->
                state.tcpAvailable.getOrNull(index)?.let { name ->
                    viewModel.dispatch(TuneUiAction.SetTcp(name, state.tcpPersist))
                }
            },
        )
    }

    // ----- CPU -------------------------------------------------------------
    cpuGovernorPicker?.let { picked ->
        val live = state.cpu.policies.firstOrNull { it.id == picked.id } ?: picked
        TuneChoiceDialog(
            title = cpuPolicyLabel(live),
            choices = live.availableGovernors.map { TuneChoice(label = it) },
            selectedIndex = live.availableGovernors.indexOf(live.governor),
            onDismiss = { cpuGovernorPicker = null },
            onSelect = { index ->
                live.availableGovernors.getOrNull(index)?.let { governor ->
                    viewModel.dispatch(TuneUiAction.SetCpuGovernor(live.id, governor))
                }
            },
        )
    }

    cpuFreqEditor?.let { picked ->
        val live = state.cpu.policies.firstOrNull { it.id == picked.id } ?: picked
        CpuFreqEditor(
            policy = live,
            onDismiss = { cpuFreqEditor = null },
            onConfirm = { minKhz, maxKhz ->
                viewModel.dispatch(TuneUiAction.SetCpuFreqs(live.id, minKhz, maxKhz))
                cpuFreqEditor = null
            },
        )
    }

    if (schedutilEditor) {
        TuneNumberEditor(
            title = stringResource(R.string.kernel_tuning_cpu_schedutil),
            initial = state.cpu.schedutilRateLimitUs,
            hint = stringResource(R.string.kernel_tuning_bore_range, 0, 1000000),
            errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 0, 1000000),
            validate = { it.toLongOrNull()?.let { v -> v in 0..1000000 } == true },
            onDismiss = { schedutilEditor = false },
            onConfirm = { value ->
                viewModel.dispatch(TuneUiAction.SetSchedutilRateLimit(value))
                schedutilEditor = false
            },
        )
    }

    // ----- GPU -------------------------------------------------------------
    gpuGovernorPicker?.let { picked ->
        val live = state.gpu.devices.firstOrNull { it.id == picked.id } ?: picked
        TuneChoiceDialog(
            title = live.label.ifBlank { live.id },
            choices = live.availableGovernors.map { TuneChoice(label = it) },
            selectedIndex = live.availableGovernors.indexOf(live.governor),
            onDismiss = { gpuGovernorPicker = null },
            onSelect = { index ->
                live.availableGovernors.getOrNull(index)?.let { governor ->
                    viewModel.dispatch(TuneUiAction.SetGpuGovernor(live.id, governor))
                }
            },
        )
    }

    gpuFreqEditor?.let { picked ->
        val live = state.gpu.devices.firstOrNull { it.id == picked.id } ?: picked
        GpuFreqEditor(
            device = live,
            onDismiss = { gpuFreqEditor = null },
            onConfirm = { minHz, maxHz ->
                viewModel.dispatch(TuneUiAction.SetGpuFreqs(live.id, minHz, maxHz))
                gpuFreqEditor = null
            },
        )
    }

    // ----- I/O -------------------------------------------------------------
    ioSchedulerPicker?.let { picked ->
        val live = state.io.devices.firstOrNull { it.name == picked.name } ?: picked
        TuneChoiceDialog(
            title = live.name,
            choices = live.availableSchedulers.map { TuneChoice(label = it) },
            selectedIndex = live.availableSchedulers.indexOf(live.scheduler),
            onDismiss = { ioSchedulerPicker = null },
            onSelect = { index ->
                live.availableSchedulers.getOrNull(index)?.let { scheduler ->
                    viewModel.dispatch(TuneUiAction.SetIoScheduler(live.name, scheduler))
                }
            },
        )
    }

    ioReadAheadEditor?.let { picked ->
        val live = state.io.devices.firstOrNull { it.name == picked.name } ?: picked
        TuneNumberEditor(
            title = stringResource(R.string.kernel_tuning_io_read_ahead) + " · " + live.name,
            initial = live.readAheadKb.coerceAtLeast(0).toString(),
            hint = stringResource(R.string.kernel_tuning_bore_range, 0, 32768),
            errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 0, 32768),
            validate = { it.toLongOrNull()?.let { v -> v in 0..32768 } == true },
            onDismiss = { ioReadAheadEditor = null },
            onConfirm = { value ->
                viewModel.dispatch(TuneUiAction.SetIoReadAhead(live.name, value.toLong()))
                ioReadAheadEditor = null
            },
        )
    }

    // ----- VM --------------------------------------------------------------
    if (vmProfilePicker) {
        val ids = viewModel.vmProfileIds
        TuneChoiceDialog(
            title = stringResource(R.string.kernel_tuning_vm_profile),
            choices = ids.map { TuneChoice(vmProfileTitle(it), vmProfileDescription(it)) },
            selectedIndex = ids.indexOf(state.vm.profileId),
            onDismiss = { vmProfilePicker = false },
            onSelect = { index ->
                ids.getOrNull(index)?.let { id ->
                    viewModel.dispatch(TuneUiAction.ApplyVmProfile(id))
                }
            },
        )
    }

    vmKnobEditor?.let { picked ->
        val live = state.vm.knobs.firstOrNull { it.key == picked.key } ?: picked
        TuneNumberEditor(
            title = vmKnobTitle(live.key),
            initial = live.value,
            hint = stringResource(R.string.kernel_tuning_bore_range, live.min, live.max) +
                " · " + stringResource(R.string.kernel_tuning_bore_default, live.defaultValue),
            errorHint = stringResource(R.string.kernel_tuning_bore_invalid, live.min, live.max),
            validate = { it.toLongOrNull()?.let { v -> v in live.min..live.max } == true },
            onDismiss = { vmKnobEditor = null },
            onConfirm = { value ->
                viewModel.dispatch(TuneUiAction.SetVmKnob(live.key, value))
                vmKnobEditor = null
            },
        )
    }

    // ----- Scheduler extras ------------------------------------------------
    schedKnobEditor?.let { picked ->
        val live = state.sched.knobs.firstOrNull { it.key == picked.key } ?: picked
        TuneNumberEditor(
            title = schedKnobTitle(live.key),
            initial = live.value,
            hint = stringResource(R.string.kernel_tuning_bore_range, live.min, live.max) +
                " · " + stringResource(R.string.kernel_tuning_bore_default, live.defaultValue),
            errorHint = stringResource(R.string.kernel_tuning_bore_invalid, live.min, live.max),
            validate = { it.toLongOrNull()?.let { v -> v in live.min..live.max } == true },
            onDismiss = { schedKnobEditor = null },
            onConfirm = { value ->
                viewModel.dispatch(TuneUiAction.SetSchedKnob(live.key, value))
                schedKnobEditor = null
            },
        )
    }

    // ----- LMK -------------------------------------------------------------
    if (lmkProfilePicker) {
        val ids = viewModel.lmkProfileIds
        TuneChoiceDialog(
            title = stringResource(R.string.kernel_tuning_lmk_profile),
            choices = ids.map { TuneChoice(lmkProfileTitle(it), lmkProfileDescription(it)) },
            selectedIndex = ids.indexOf(state.lmk.profileId),
            onDismiss = { lmkProfilePicker = false },
            onSelect = { index ->
                ids.getOrNull(index)?.let { id ->
                    viewModel.dispatch(TuneUiAction.ApplyLmkProfile(id))
                }
            },
        )
    }

    lmkLevelEditor?.let { index ->
        state.lmk.levels.getOrNull(index)?.let { level ->
            LmkLevelEditor(
                index = index,
                level = level,
                prevPages = state.lmk.levels.getOrNull(index - 1)?.pages,
                nextPages = state.lmk.levels.getOrNull(index + 1)?.pages,
                onDismiss = { lmkLevelEditor = null },
                onConfirm = { pages, adj ->
                    viewModel.dispatch(TuneUiAction.SetLmkLevel(index, pages, adj))
                    lmkLevelEditor = null
                },
            )
        }
    }

    // ----- BORE ------------------------------------------------------------
    if (boreProfilePicker) {
        val ids = viewModel.boreProfileIds
        TuneChoiceDialog(
            title = stringResource(R.string.kernel_tuning_bore_profile),
            choices = ids.map { TuneChoice(boreProfileTitle(it), boreProfileDescription(it)) },
            selectedIndex = ids.indexOf(state.boreProfileId),
            onDismiss = { boreProfilePicker = false },
            onSelect = { index ->
                ids.getOrNull(index)?.let { id ->
                    viewModel.dispatch(TuneUiAction.ApplyBoreProfile(id))
                }
            },
        )
    }

    boreKnobEditor?.let { picked ->
        val live = state.boreKnobs.firstOrNull { it.key == picked.key } ?: picked
        TuneNumberEditor(
            title = boreKnobTitle(live.key),
            initial = live.value,
            hint = stringResource(R.string.kernel_tuning_bore_range, live.min, live.max) +
                " · " + stringResource(R.string.kernel_tuning_bore_default, live.defaultValue),
            errorHint = stringResource(R.string.kernel_tuning_bore_invalid, live.min, live.max),
            validate = { it.toLongOrNull()?.let { v -> v in live.min..live.max } == true },
            onDismiss = { boreKnobEditor = null },
            onConfirm = { value ->
                viewModel.dispatch(TuneUiAction.SetBoreKnob(live.key, value))
                boreKnobEditor = null
            },
        )
    }

    // ----- ZRAM ------------------------------------------------------------
    if (zramSizePicker) {
        val zram = state.zram
        val presets = zramSizePresets(zram.totalRamBytes)
        TuneChoiceDialog(
            title = stringResource(R.string.kernel_tuning_zram_size),
            choices = presets.map { TuneChoice(zramPresetLabel(it, zram.totalRamBytes)) },
            selectedIndex = presets.indexOf(zram.disksizeBytes),
            onDismiss = { zramSizePicker = false },
            onSelect = { index ->
                presets.getOrNull(index)?.let { size ->
                    viewModel.dispatch(
                        TuneUiAction.ConfigureZram(size, zram.currentAlgo, zram.maxStreams)
                    )
                }
            },
        )
    }

    if (zramAlgoPicker) {
        val zram = state.zram
        TuneChoiceDialog(
            title = stringResource(R.string.kernel_tuning_zram_algo),
            choices = zram.algos.map { TuneChoice(label = it) },
            selectedIndex = zram.algos.indexOf(zram.currentAlgo),
            onDismiss = { zramAlgoPicker = false },
            onSelect = { index ->
                zram.algos.getOrNull(index)?.let { algo ->
                    viewModel.dispatch(
                        TuneUiAction.ConfigureZram(zram.disksizeBytes, algo, zram.maxStreams)
                    )
                }
            },
        )
    }

    if (zramStreamsEditor) {
        val zram = state.zram
        TuneNumberEditor(
            title = stringResource(R.string.kernel_tuning_zram_streams),
            initial = zram.maxStreams.toString(),
            hint = stringResource(R.string.kernel_tuning_bore_range, 1, 64),
            errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 1, 64),
            validate = { it.toLongOrNull()?.let { v -> v in 1..64 } == true },
            onDismiss = { zramStreamsEditor = false },
            onConfirm = { value ->
                viewModel.dispatch(
                    TuneUiAction.ConfigureZram(zram.disksizeBytes, zram.currentAlgo, value.toLong())
                )
                zramStreamsEditor = false
            },
        )
    }

    if (zramSwappinessEditor) {
        TuneNumberEditor(
            title = stringResource(R.string.kernel_tuning_zram_swappiness),
            initial = state.zram.swappiness.ifBlank { "100" },
            hint = stringResource(R.string.kernel_tuning_bore_range, 0, 200),
            errorHint = stringResource(R.string.kernel_tuning_bore_invalid, 0, 200),
            validate = { it.toLongOrNull()?.let { v -> v in 0..200 } == true },
            onDismiss = { zramSwappinessEditor = false },
            onConfirm = { value ->
                viewModel.dispatch(TuneUiAction.SetZramSwappiness(value))
                zramSwappinessEditor = false
            },
        )
    }

    // ----- Sysctl ----------------------------------------------------------
    if (addingSysctl) {
        SysctlEditor(
            initial = null,
            onDismiss = { addingSysctl = false },
            onConfirm = { key, value, persist ->
                viewModel.dispatch(TuneUiAction.AddOrUpdateSysctl(key, value, persist))
                addingSysctl = false
            },
        )
    }

    editingSysctl?.let { entry ->
        val live = state.sysctls.firstOrNull { it.key == entry.key } ?: entry
        SysctlEditor(
            initial = live,
            onDismiss = { editingSysctl = null },
            onDelete = {
                viewModel.dispatch(TuneUiAction.RemoveSysctl(live))
                editingSysctl = null
            },
            onConfirm = { key, value, persist ->
                viewModel.dispatch(TuneUiAction.AddOrUpdateSysctl(key, value, persist))
                editingSysctl = null
            },
        )
    }

    // ----- Snapshot / import confirms --------------------------------------
    if (confirmRestore) {
        TuneDialog(
            title = stringResource(R.string.kernel_tuning_snapshot_restore),
            onDismiss = { confirmRestore = false },
            onConfirm = {
                viewModel.dispatch(TuneUiAction.RestoreSnapshot)
                confirmRestore = false
            },
        ) {
            TuneBodyText(stringResource(R.string.kernel_tuning_snapshot_confirm))
        }
    }

    pendingImport?.let { json ->
        TuneDialog(
            title = stringResource(R.string.kernel_tuning_profile_import),
            onDismiss = { pendingImport = null },
            onConfirm = {
                viewModel.dispatch(TuneUiAction.ImportProfile(json))
                pendingImport = null
            },
        ) {
            TuneBodyText(stringResource(R.string.kernel_tuning_profile_import_confirm))
        }
    }
}

/** One search field, mode-adaptive. */
@Composable
private fun TuneSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TuneSection(stringResource(R.string.kernel_tuning_search)) {
        TuneDialogField(
            value = query,
            label = stringResource(R.string.kernel_tuning_search),
            onValueChange = onQueryChange,
        )
    }
}

@Composable
private fun PsiRow(
    title: String,
    someAvg10: Double,
    fullAvg10: Double,
    totalUs: Long,
) {
    TuneRow(
        title = title,
        description = stringResource(
            R.string.kernel_tuning_diag_psi_value,
            someAvg10,
            fullAvg10,
        ),
        value = stringResource(
            R.string.kernel_tuning_diag_stall_total,
            formatStallUs(totalUs),
        ),
    )
}

@Composable
private fun CpuFreqEditor(
    policy: CpuPolicy,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var minText by remember(policy.id) { mutableStateOf(khzToMhzString(policy.minFreqKhz)) }
    var maxText by remember(policy.id) { mutableStateOf(khzToMhzString(policy.maxFreqKhz)) }
    val minKhz = mhzToKhz(minText)
    val maxKhz = mhzToKhz(maxText)
    val hwMin = if (policy.cpuinfoMinKhz > 0) policy.cpuinfoMinKhz else 1L
    val hwMax = if (policy.cpuinfoMaxKhz > 0) policy.cpuinfoMaxKhz else Long.MAX_VALUE
    val valid = minKhz != null && maxKhz != null &&
        minKhz in hwMin..hwMax && maxKhz in hwMin..hwMax && minKhz <= maxKhz
    val hwRangeKnown = policy.cpuinfoMinKhz > 0 && policy.cpuinfoMaxKhz > 0

    TuneDialog(
        title = cpuPolicyLabel(policy),
        confirmEnabled = valid,
        onDismiss = onDismiss,
        onConfirm = {
            if (minKhz != null && maxKhz != null) onConfirm(minKhz, maxKhz)
        },
    ) {
        TuneDialogField(
            value = minText,
            label = stringResource(R.string.kernel_tuning_cpu_min_freq),
            onValueChange = { minText = it },
            isError = !valid,
        )
        TuneDialogField(
            value = maxText,
            label = stringResource(R.string.kernel_tuning_cpu_max_freq),
            onValueChange = { maxText = it },
            isError = !valid,
        )
        if (hwRangeKnown) {
            TuneBodyText(
                if (valid) {
                    stringResource(
                        R.string.kernel_tuning_cpu_hw_range,
                        formatKhz(hwMin),
                        formatKhz(hwMax),
                    )
                } else {
                    stringResource(
                        R.string.kernel_tuning_cpu_freq_invalid,
                        formatKhz(hwMin),
                        formatKhz(hwMax),
                    )
                }
            )
        }
    }
}

@Composable
private fun GpuFreqEditor(
    device: GpuDevice,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var minText by remember(device.id) { mutableStateOf(hzToMhzString(device.minFreqHz)) }
    var maxText by remember(device.id) { mutableStateOf(hzToMhzString(device.maxFreqHz)) }
    val minHz = mhzToHz(minText)
    val maxHz = mhzToHz(maxText)
    val freqs = device.availableFreqsHz
    val valid = minHz != null && maxHz != null && minHz <= maxHz &&
        (freqs.isEmpty() || (minHz in freqs && maxHz in freqs))

    TuneDialog(
        title = stringResource(R.string.kernel_tuning_gpu_freqs),
        confirmEnabled = valid,
        onDismiss = onDismiss,
        onConfirm = {
            if (minHz != null && maxHz != null) onConfirm(minHz, maxHz)
        },
    ) {
        TuneDialogField(
            value = minText,
            label = stringResource(R.string.kernel_tuning_gpu_min_freq),
            onValueChange = { minText = it },
            isError = !valid,
        )
        TuneDialogField(
            value = maxText,
            label = stringResource(R.string.kernel_tuning_gpu_max_freq),
            onValueChange = { maxText = it },
            isError = !valid,
        )
        if (freqs.isNotEmpty()) {
            TuneBodyText(
                if (valid) {
                    stringResource(
                        R.string.kernel_tuning_gpu_range,
                        formatGpuHz(freqs.first()),
                        formatGpuHz(freqs.last()),
                    )
                } else {
                    stringResource(R.string.kernel_tuning_gpu_freq_invalid)
                }
            )
        }
    }
}

@Composable
private fun LmkLevelEditor(
    index: Int,
    level: LmkLevel,
    prevPages: Long?,
    nextPages: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var mbText by remember(level) { mutableStateOf(pagesToMbString(level.pages)) }
    var adjText by remember(level) { mutableStateOf(level.adj.toString()) }
    val pages = mbToPages(mbText)
    val adj = adjText.trim().toLongOrNull()
    val lo = prevPages?.plus(1) ?: 1L
    val hi = nextPages?.minus(1) ?: Long.MAX_VALUE
    val valid = pages != null && adj != null && pages in lo..hi && adj in -1000..1000

    TuneDialog(
        title = stringResource(R.string.kernel_tuning_lmk_level, index + 1),
        confirmEnabled = valid,
        onDismiss = onDismiss,
        onConfirm = {
            if (pages != null && adj != null) onConfirm(pages, adj)
        },
    ) {
        TuneDialogField(
            value = mbText,
            label = stringResource(R.string.kernel_tuning_lmk_pages),
            onValueChange = { mbText = it },
            isError = !valid,
        )
        TuneDialogField(
            value = adjText,
            label = stringResource(R.string.kernel_tuning_lmk_adj),
            onValueChange = { adjText = it },
            isError = !valid,
        )
        if (!valid) {
            TuneBodyText(
                stringResource(
                    R.string.kernel_tuning_lmk_invalid,
                    formatLmkMb(lo),
                    if (hi == Long.MAX_VALUE) {
                        stringResource(R.string.kernel_tuning_lmk_no_limit)
                    } else {
                        formatLmkMb(hi)
                    },
                )
            )
        }
    }
}

@Composable
private fun TuneNumberEditor(
    title: String,
    initial: String,
    hint: String,
    errorHint: String,
    validate: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val valid = validate(text.trim())
    TuneDialog(
        title = title,
        confirmEnabled = valid,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(text.trim()) },
    ) {
        TuneDialogField(
            value = text,
            label = stringResource(R.string.kernel_tuning_value),
            onValueChange = { text = it },
            isError = !valid,
        )
        TuneBodyText(if (valid || text.isBlank()) hint else errorHint)
    }
}

@Composable
private fun SysctlEditor(
    initial: SysctlEntry?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var key by remember(initial) { mutableStateOf(initial?.key ?: "") }
    var value by remember(initial) { mutableStateOf(initial?.value ?: "") }
    var persist by remember(initial) { mutableStateOf(initial?.persist ?: false) }
    val keyPattern = remember { Regex("^[A-Za-z0-9_.-]+$") }
    val valid = keyPattern.matches(key.trim()) && value.isNotBlank()

    TuneDialog(
        title = stringResource(R.string.kernel_tuning_add),
        confirmEnabled = valid,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(key.trim(), value.trim(), persist) },
    ) {
        TuneDialogField(
            value = key,
            label = stringResource(R.string.kernel_tuning_key),
            onValueChange = { key = it },
            isError = key.isNotBlank() && !keyPattern.matches(key.trim()),
        )
        TuneDialogField(
            value = value,
            label = stringResource(R.string.kernel_tuning_value),
            onValueChange = { value = it },
            isError = false,
        )
        TuneDialogSwitch(
            label = stringResource(R.string.kernel_tuning_persist),
            checked = persist,
            onCheckedChange = { persist = it },
        )
        if (onDelete != null) {
            TuneActionRow(
                title = stringResource(R.string.kernel_tuning_sysctl_delete, key),
                onClick = onDelete,
            )
        }
    }
}

// ----- small formatting helpers --------------------------------------------

private fun matchesQuery(query: String, vararg texts: String?): Boolean {
    if (query.isBlank()) return true
    return texts.any { it != null && it.contains(query, ignoreCase = true) }
}

@Composable
private fun cpuPolicyLabel(policy: CpuPolicy): String =
    if (policy.cpus.isNotBlank()) {
        stringResource(R.string.kernel_tuning_cpu_policy, policy.cpus)
    } else {
        policy.id
    }

@Composable
private fun formatKhz(khz: Long): String =
    stringResource(R.string.kernel_tuning_cpu_mhz, khz / 1000.0)

@Composable
private fun formatGpuHz(hz: Long): String =
    stringResource(R.string.kernel_tuning_gpu_mhz, hz / 1_000_000.0)

@Composable
private fun formatLmkMb(pages: Long): String =
    stringResource(R.string.kernel_tuning_lmk_mb, pages / 256.0)

@Composable
private fun formatStallUs(us: Long): String =
    if (us >= 1_000_000) {
        stringResource(R.string.kernel_tuning_diag_stall_s, us / 1_000_000.0)
    } else {
        stringResource(R.string.kernel_tuning_diag_stall_ms, (us / 1000.0).roundToLong())
    }

private fun khzToMhzString(khz: Long): String {
    val mhz = khz / 1000.0
    return if (mhz == kotlin.math.floor(mhz)) mhz.toLong().toString() else mhz.toString()
}

private fun mhzToKhz(text: String): Long? {
    val mhz = text.trim().toDoubleOrNull() ?: return null
    if (!mhz.isFinite() || mhz <= 0) return null
    return (mhz * 1000).roundToLong()
}

private fun hzToMhzString(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return if (mhz == kotlin.math.floor(mhz)) mhz.toLong().toString() else mhz.toString()
}

private fun mhzToHz(text: String): Long? {
    val mhz = text.trim().toDoubleOrNull() ?: return null
    if (!mhz.isFinite() || mhz <= 0) return null
    return (mhz * 1_000_000).roundToLong()
}

private fun pagesToMbString(pages: Long): String {
    val mb = pages / 256.0
    return if (mb == kotlin.math.floor(mb)) mb.toLong().toString() else mb.toString()
}

private fun mbToPages(text: String): Long? {
    val mb = text.trim().toDoubleOrNull() ?: return null
    if (!mb.isFinite() || mb <= 0) return null
    return (mb * 256).roundToLong()
}

private fun zramSizePresets(totalRamBytes: Long): List<Long> {
    val gb = 1024L * 1024 * 1024
    val min = 256L * 1024 * 1024
    return (
        listOf(gb, 2 * gb) +
            if (totalRamBytes > 0) {
                listOf(0.25, 0.5, 0.75).map { (totalRamBytes * it).toLong() }
            } else {
                emptyList()
            }
        )
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
                stringResource(R.string.kernel_tuning_zram_gb, bytes.toDouble() / gb),
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

// ----- profile / knob labels -----------------------------------------------

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
