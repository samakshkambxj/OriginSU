package com.originsu.manager.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Input
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.BuildConfig
import com.originsu.manager.domain.model.LkmSelection
import com.originsu.manager.domain.usecase.PatchAnyKernelWithKpmUseCase
import com.originsu.manager.domain.usecase.PatchBootImageWithKpmUseCase
import com.originsu.manager.domain.usecase.SaveKpmPatchedFileUseCase
import com.originsu.manager.ui.component.DialogHandle
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberCustomDialog
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsChooseDialog
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.SettingsSwitchWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.screen.kernelFlash.component.SlotSelectionDialog
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.renderBackgroundBlur
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.InstallUiEvent
import com.originsu.manager.ui.viewmodel.InstallViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.core.content.FileProvider
import java.io.File
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */

enum class KpmPatchOption {
    FOLLOW_KERNEL,
    PATCH_KPM,
    UNDO_PATCH_KPM
}

enum class HookFlavor {
    TRACEPOINT,
    TAMPER;

    fun id(): String = when (this) {
        TRACEPOINT -> "tracepoint"
        TAMPER -> "tamper"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallScreen(
    preselectedKernelUri: String? = null
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<InstallViewModel>()
    val installState by viewModel.state.collectAsStateWithLifecycle()
    val environment = installState.environment
    val context = LocalContext.current
    var installMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var lkmSelection by remember { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var kpmPatchOption by remember { mutableStateOf(KpmPatchOption.FOLLOW_KERNEL) }
    var hookFlavor by remember { mutableStateOf(HookFlavor.TRACEPOINT) }
    var showSlotSelectionDialog by remember { mutableStateOf(false) }
    var tempKernelUri by remember { mutableStateOf<Uri?>(null) }
    // 0 = LKM tab, 1 = GKI tab, 2 = KPM tab.
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // Standalone KPM injection source (KPM tab only).
    var kpmSource by remember { mutableStateOf<KpmInstallSource?>(null) }
    var kpmSlot by remember { mutableStateOf<String?>(null) }
    var showKpmSlotDialog by remember { mutableStateOf(false) }
    var kpmSavePatched by remember { mutableStateOf(true) }
    var kpmKmi by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val kpmLoading = rememberLoadingDialog()
    val patchAnyKernelWithKpm = koinInject<PatchAnyKernelWithKpmUseCase>()
    val patchBootImageWithKpm = koinInject<PatchBootImageWithKpmUseCase>()
    val saveKpmPatchedFile = koinInject<SaveKpmPatchedFileUseCase>()

    val isGKI = environment.isGki
    val isAbDevice = environment.isAbDevice
    val summary = stringResource(R.string.horizon_kernel_summary)
    val failedReboot = stringResource(R.string.failed_reboot)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InstallUiEvent.Error -> {
                    val message = event.message.ifBlank { failedReboot }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 处理预选的内核文件
    LaunchedEffect(preselectedKernelUri, installState.loading, isAbDevice) {
        if (installState.loading) return@LaunchedEffect
        preselectedKernelUri?.let { uriString ->
            try {
                val preselectedUri = uriString.toUri()
                val horizonMethod = InstallMethod.HorizonKernel(
                    uri = preselectedUri,
                    summary = summary
                )
                installMethod = horizonMethod
                tempKernelUri = preselectedUri
                selectedTabIndex = 1

                if (isAbDevice) {
                    showSlotSelectionDialog = true
                }
            } catch (_: Exception) {
            }
        }
    }

    val navigator = LocalNavigator.current

    // Partition default is derived, never assigned during composition.
    val partitions = environment.availablePartitions
    val defaultPartitionIndex =
        partitions.indexOf(environment.defaultPartition).takeIf { it >= 0 } ?: 0
    var partitionSelectionIndex by remember(partitions, environment.defaultPartition) {
        mutableIntStateOf(defaultPartitionIndex)
    }

    val onInstall = {
        installMethod?.let { method ->
            when (method) {
                is InstallMethod.HorizonKernel -> {
                    method.uri?.let { uri ->
                        navigator.push(
                            Route.KernelFlash(
                                kernelUri = uri.toString(),
                                selectedSlot = method.slot,
                                kpmPatchEnabled = false,
                                kpmUndoPatch = false
                            )
                        )
                    }
                }
                is InstallMethod.AnyKernelZip -> {
                    method.uri?.let { uri ->
                        navigator.push(Route.Flash.anyKernelZip(uri.toString()))
                    }
                }
                is InstallMethod.PatchBootImage -> {
                    val bootUri = method.bootUri
                    val zipUri = method.zipUri
                    if (bootUri != null && zipUri != null) {
                        navigator.push(
                            Route.Flash.patchBootImage(
                                bootUri.toString(),
                                zipUri.toString(),
                                (lkmSelection as? LkmSelection.KmiString)?.value
                            )
                        )
                    }
                }
                else -> {
                    val isOta = method is InstallMethod.DirectInstallToInactiveSlot
                    val partitionSelection = partitions.getOrNull(partitionSelectionIndex)
                    navigator.push(
                        Route.Flash.boot(
                            bootUri = if (method is InstallMethod.SelectFile) {
                                method.uri?.toString()
                            } else {
                                null
                            },
                            lkmUri = (lkmSelection as? LkmSelection.LkmUri)?.uri,
                            kmi = (lkmSelection as? LkmSelection.KmiString)?.value,
                            ota = isOta,
                            partition = partitionSelection,
                            hook = hookFlavor.id(),
                        )
                    )
                }
            }
        }
        Unit
    }

    // 槽位选择
    SlotSelectionDialog(
        show = showSlotSelectionDialog && isAbDevice,
        currentSlot = environment.activeSlotSuffix.removePrefix("_")
            .takeIf { it == "a" || it == "b" },
        onDismiss = { showSlotSelectionDialog = false },
        onSlotSelected = { slot ->
            showSlotSelectionDialog = false
            val horizonMethod = InstallMethod.HorizonKernel(
                uri = tempKernelUri,
                slot = slot,
                summary = summary
            )
            installMethod = horizonMethod
        }
    )

    // KPM-tab kernel zips need their own slot pick (kept off installMethod).
    SlotSelectionDialog(
        show = showKpmSlotDialog && isAbDevice,
        currentSlot = environment.activeSlotSuffix.removePrefix("_")
            .takeIf { it == "a" || it == "b" },
        onDismiss = { showKpmSlotDialog = false },
        onSlotSelected = { slot ->
            showKpmSlotDialog = false
            kpmSlot = slot
        }
    )

    fun onKpmKernelZipPicked(uri: Uri) {
        kpmSource = KpmInstallSource.KernelZip(uri)
        if (isAbDevice) {
            showKpmSlotDialog = true
        }
    }

    fun onKpmNext() {
        val source = kpmSource ?: return
        val uri = source.uri ?: return
        val patch = kpmPatchOption == KpmPatchOption.PATCH_KPM
        val undo = kpmPatchOption == KpmPatchOption.UNDO_PATCH_KPM
        val partition = partitions.getOrNull(partitionSelectionIndex)
        // KPM injects into the kernel image, which lives in boot: never
        // inherit the KSU default partition (e.g. init_boot, ramdisk-only).
        val kpmBootPartition = partitions.firstOrNull { it == "boot" } ?: partition
        when (source) {
            is KpmInstallSource.KernelZip -> {
                navigator.push(
                    Route.KernelFlash(
                        kernelUri = uri.toString(),
                        selectedSlot = kpmSlot,
                        kpmPatchEnabled = patch,
                        kpmUndoPatch = undo
                    )
                )
            }

            is KpmInstallSource.AnyKernelZip -> {
                if (!patch && !undo) {
                    navigator.push(Route.Flash.anyKernelZip(uri.toString()))
                    return
                }
                scope.launch {
                    val target = kpmLoading.withLoading {
                        runCatching {
                            val ts = System.currentTimeMillis()
                            val cacheFile =
                                File(context.cacheDir, "kpm-anykernel-$ts.zip")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                cacheFile.outputStream().use { input.copyTo(it) }
                            } ?: error(context.getString(R.string.horizon_copy_failed))
                            patchAnyKernelWithKpm(context, cacheFile, undo) {}.getOrThrow()
                            if (kpmSavePatched) {
                                val saved = saveKpmPatchedFile(
                                    context, cacheFile, "originsu-kpm-anykernel-$ts.zip"
                                )
                                runCatching { cacheFile.delete() }
                                saved.toString()
                            } else {
                                FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    cacheFile
                                ).toString()
                            }
                        }
                    }
                    target
                        .onSuccess { targetUri ->
                            if (kpmSavePatched) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.kpm_file_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            navigator.push(Route.Flash.anyKernelZip(targetUri))
                        }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: failedReboot,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }

            is KpmInstallSource.BootImage -> {
                if (!patch && !undo) {
                    navigator.push(
                        Route.Flash.boot(
                            bootUri = uri.toString(),
                            lkmUri = null,
                            kmi = kpmKmi,
                            ota = false,
                            partition = partition,
                            hook = null
                        )
                    )
                    return
                }
                scope.launch {
                    val target = kpmLoading.withLoading {
                        runCatching {
                            val ts = System.currentTimeMillis()
                            val patched = patchBootImageWithKpm(context, uri, undo) {}.getOrThrow()
                            if (kpmSavePatched) {
                                val saved = saveKpmPatchedFile(
                                    context, patched, "originsu-kpm-boot-$ts.img"
                                )
                                runCatching { patched.delete() }
                                saved.toString()
                            } else {
                                FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    patched
                                ).toString()
                            }
                        }
                    }
                    target
                        .onSuccess { targetUri ->
                            if (kpmSavePatched) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.kpm_file_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            navigator.push(
                                Route.Flash.boot(
                                    bootUri = targetUri,
                                    lkmUri = null,
                                    kmi = null,
                                    ota = false,
                                    partition = kpmBootPartition,
                                    hook = null,
                                    noInstall = true,
                                )
                            )
                        }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: failedReboot,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
        }
    }

    val currentKmi = environment.currentKmi

    val selectKmiDialog = rememberSelectKmiDialog(environment.supportedKmis) { kmi ->
        kmi?.let {
            lkmSelection = LkmSelection.KmiString(it)
            onInstall()
        }
    }

    val kpmKmiDialog = rememberSelectKmiDialog(environment.supportedKmis) { kmi ->
        kpmKmi = kmi
    }

    val onClickNext = {
        if (selectedTabIndex == 2) {
            onKpmNext()
        } else
        // SettingsChooseDialog renders nothing for an empty list, so only
        // gate on the dialog when there is actually something to pick.
        // Otherwise fall through and let ksud attempt auto-detection.
        if (isGKI && lkmSelection == LkmSelection.KmiNone && currentKmi.isBlank() &&
            environment.supportedKmis.isNotEmpty() && installMethod !is InstallMethod.HorizonKernel
        ) {
            selectKmiDialog.show()
        } else {
            onInstall()
        }
    }

    val installOnlySupportKoFile = stringResource(R.string.install_only_support_ko_file)
    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val isKo = isKoFile(context, uri)
                if (isKo) {
                    lkmSelection = LkmSelection.LkmUri(uri.toString())
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    Toast.makeText(
                        context,
                        installOnlySupportKoFile,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val onLkmUpload = {
        selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/octet-stream"
        })
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            TopBar(
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        if (installState.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            InstallBody(
                innerPaddingTop = innerPadding.calculateTopPadding(),
                innerPaddingBottom = innerPadding.calculateBottomPadding(),
                isGKI = isGKI,
                rootAvailable = environment.rootAvailable,
                isAbDevice = isAbDevice,
                defaultPartitionName = environment.defaultPartition,
                inactiveSlotSuffix = environment.inactiveSlotSuffix,
                partitions = partitions,
                partitionSelectionIndex = partitionSelectionIndex,
                onPartitionSelected = { partitionSelectionIndex = it },
                installMethod = installMethod,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                onMethodSelected = { method ->
                    selectedTabIndex = if (method is InstallMethod.HorizonKernel) 1 else 0
                    if (method is InstallMethod.HorizonKernel && method.uri != null) {
                        if (isAbDevice) {
                            tempKernelUri = method.uri
                            showSlotSelectionDialog = true
                        } else {
                            installMethod = method
                        }
                    } else {
                        installMethod = method
                    }
                },
                lkmSelection = lkmSelection,
                onLkmUpload = onLkmUpload,
                onClickNext = onClickNext,
                isNextEnabled = if (selectedTabIndex == 2) kpmSource?.uri != null else installMethod != null,
                kpmSource = kpmSource,
                onKpmSourceSelected = { kpmSource = it },
                onKpmKernelZipPicked = { onKpmKernelZipPicked(it) },
                kpmSlot = kpmSlot,
                kpmSavePatched = kpmSavePatched,
                onKpmSavePatchedChanged = { kpmSavePatched = it },
                kpmKmi = kpmKmi,
                showKpmKmiRow = environment.supportedKmis.isNotEmpty(),
                onKpmKmiClick = { kpmKmiDialog.show() },
                kpmPatchOption = kpmPatchOption,
                onKpmPatchOptionChanged = { kpmPatchOption = it },
                hookFlavor = hookFlavor,
                onHookFlavorChanged = { hookFlavor = it },
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceBright.copy(
                    alpha = cardConfig.cardAlpha
                ),
                blurEnabled = themeConfig.isEnableBlurExp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallBody(
    innerPaddingTop: Dp,
    innerPaddingBottom: Dp,
    isGKI: Boolean,
    rootAvailable: Boolean,
    isAbDevice: Boolean,
    defaultPartitionName: String,
    inactiveSlotSuffix: String,
    partitions: List<String>,
    partitionSelectionIndex: Int,
    onPartitionSelected: (Int) -> Unit,
    installMethod: InstallMethod?,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onMethodSelected: (InstallMethod) -> Unit,
    lkmSelection: LkmSelection,
    onLkmUpload: () -> Unit,
    onClickNext: () -> Unit,
    isNextEnabled: Boolean,
    kpmSource: KpmInstallSource?,
    onKpmSourceSelected: (KpmInstallSource) -> Unit,
    onKpmKernelZipPicked: (Uri) -> Unit,
    kpmSlot: String?,
    kpmSavePatched: Boolean,
    onKpmSavePatchedChanged: (Boolean) -> Unit,
    kpmKmi: String?,
    showKpmKmiRow: Boolean,
    onKpmKmiClick: () -> Unit,
    kpmPatchOption: KpmPatchOption = KpmPatchOption.FOLLOW_KERNEL,
    onKpmPatchOptionChanged: (KpmPatchOption) -> Unit = {},
    hookFlavor: HookFlavor = HookFlavor.TRACEPOINT,
    onHookFlavorChanged: (HookFlavor) -> Unit = {},
    containerColor: Color,
    disabledContainerColor: Color,
    blurEnabled: Boolean,
) {
    val context = LocalContext.current
    val horizonKernelSummary = stringResource(R.string.horizon_kernel_summary)
    val anyKernelZipSummary = stringResource(R.string.flash_anykernel_zip_summary)
    val patchBootImageSummary = stringResource(R.string.patch_boot_anykernel_summary)
    val selectBootFirst = stringResource(R.string.select_boot_image_first)
    val selectZipNext = stringResource(R.string.select_anykernel_zip_next)
    val patchBootPickedFmt = stringResource(R.string.patch_boot_picked)
    val selectFileTip = stringResource(
        id = R.string.select_file_tip, defaultPartitionName
    )
    // KPM injects into the kernel image, which lives in boot: point at boot
    // here instead of the KSU default partition (e.g. ramdisk-only init_boot).
    val kpmSelectBootTip = stringResource(
        id = R.string.select_file_tip, "boot"
    )

    var akPatchBootUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var showPartitionDialog by remember { mutableStateOf(false) }

    fun Uri.displayName(): String {
        return runCatching {
            context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: lastPathSegment.orEmpty()
    }

    val akPatchZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { zipUri ->
                val bootUri = akPatchBootUri
                onMethodSelected(
                    InstallMethod.PatchBootImage(
                        bootUri = bootUri,
                        zipUri = zipUri,
                        summary = patchBootPickedFmt.format(
                            bootUri?.displayName().orEmpty(),
                            zipUri.displayName()
                        )
                    )
                )
            }
        }
    }

    val akPatchBootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { bootUri ->
                akPatchBootUri = bootUri
                Toast.makeText(context, selectZipNext, Toast.LENGTH_SHORT).show()
                akPatchZipPicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/zip"
                    addCategory(Intent.CATEGORY_OPENABLE)
                })
            }
        }
    }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val pending = pendingFileMethod
                val option = when (pending) {
                    is InstallMethod.SelectFile -> InstallMethod.SelectFile(
                        uri,
                        summary = selectFileTip
                    )

                    is InstallMethod.HorizonKernel -> InstallMethod.HorizonKernel(
                        uri,
                        summary = horizonKernelSummary
                    )

                    is InstallMethod.AnyKernelZip -> InstallMethod.AnyKernelZip(
                        uri,
                        summary = anyKernelZipSummary
                    )

                    else -> null
                }
                option?.let { onMethodSelected(it) }
            }
        }
    }

    val kpmBootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                onKpmSourceSelected(KpmInstallSource.BootImage(uri))
            }
        }
    }

    val kpmAnyKernelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                onKpmSourceSelected(KpmInstallSource.AnyKernelZip(uri))
            }
        }
    }

    val kpmKernelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                onKpmKernelZipPicked(uri)
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = { onMethodSelected(InstallMethod.DirectInstallToInactiveSlot) },
        onDismiss = null
    )

    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogContent = stringResource(id = R.string.install_inactive_slot_warning)

    val onRowClick = { option: InstallMethod ->
        when (option) {
            is InstallMethod.PatchBootImage -> {
                Toast.makeText(context, selectBootFirst, Toast.LENGTH_SHORT).show()
                akPatchBootPicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/octet-stream", "application/x-boot-image")
                    )
                })
            }
            is InstallMethod.SelectFile, is InstallMethod.HorizonKernel, is InstallMethod.AnyKernelZip -> {
                pendingFileMethod = option
                selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/octet-stream", "application/zip")
                    )
                })
            }

            is InstallMethod.DirectInstall -> onMethodSelected(option)

            is InstallMethod.DirectInstallToInactiveSlot -> {
                confirmDialog.showConfirm(dialogTitle, dialogContent)
            }
        }
    }

    // LKM tab: everything except the GKI kernel flash. Offline patch
    // needs no root: stock boot.img + AnyKernel kernel -> file.
    val lkmMethods = buildList {
        add(InstallMethod.SelectFile(summary = selectFileTip))
        add(InstallMethod.PatchBootImage(summary = patchBootImageSummary))
        if (rootAvailable) {
            add(InstallMethod.DirectInstall)
            if (isAbDevice) add(InstallMethod.DirectInstallToInactiveSlot)
            add(InstallMethod.AnyKernelZip(summary = anyKernelZipSummary))
        }
    }
    val gkiMethods = listOf(InstallMethod.HorizonKernel(summary = horizonKernelSummary))

    if (showPartitionDialog) {
        val suffix = if (installMethod is InstallMethod.DirectInstallToInactiveSlot) {
            inactiveSlotSuffix
        } else {
            null
        }
        PartitionChooseDialog(
            partitions = partitions,
            defaultPartitionName = defaultPartitionName,
            suffix = suffix,
            selectedIndex = partitionSelectionIndex,
            onDismiss = { showPartitionDialog = false },
            onSelected = {
                onPartitionSelected(it)
                showPartitionDialog = false
            },
        )
    }

    val isDirectMethod = installMethod is InstallMethod.DirectInstall ||
        installMethod is InstallMethod.DirectInstallToInactiveSlot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .scrollEndHaptic()
            .overScrollVertical()
            .padding(top = innerPaddingTop + 12.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { onTabSelected(0) },
                text = { Text(stringResource(R.string.install_tab_lkm)) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { onTabSelected(1) },
                text = { Text(stringResource(R.string.install_tab_gki)) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // KPM injection needs root: hide the tab entirely without root
            // (its content, including the KPM patch options, is root-gated too).
            if (rootAvailable) {
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { onTabSelected(2) },
                    text = { Text(stringResource(R.string.install_tab_kpm)) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                lkmMethods.forEach { method ->
                    InstallMethodRow(
                        title = stringResource(id = method.label),
                        summary = method.summary,
                        selected = installMethod?.javaClass == method.javaClass,
                        onClick = { onRowClick(method) },
                    )
                }

                if (isDirectMethod && partitions.isNotEmpty()) {
                    InstallActionRow(
                        icon = Icons.TwoTone.Edit,
                        title = stringResource(R.string.install_select_partition),
                        description = partitions.getOrNull(partitionSelectionIndex),
                        onClick = { showPartitionDialog = true },
                    )
                }

                if (isGKI) {
                    InstallActionRow(
                        icon = Icons.AutoMirrored.TwoTone.Input,
                        title = stringResource(id = R.string.install_upload_lkm_file),
                        description = (lkmSelection as? LkmSelection.LkmUri)?.let {
                            stringResource(
                                id = R.string.selected_lkm,
                                it.uri.toUri().lastPathSegment ?: "(file)"
                            )
                        } ?: stringResource(id = R.string.install_lkm_fork_hint),
                        onClick = onLkmUpload,
                    )

                    HookFlavorSelector(
                        selectedOption = hookFlavor,
                        onOptionChanged = onHookFlavorChanged
                    )
                }
            }

            1 -> {
                if (rootAvailable) {
                    gkiMethods.forEach { method ->
                        InstallMethodRow(
                            title = stringResource(id = method.label),
                            summary = method.summary,
                            selected = installMethod?.javaClass == method.javaClass,
                            onClick = { onRowClick(method) },
                        )
                    }

                    (installMethod as? InstallMethod.HorizonKernel)?.slot?.let { slot ->
                        Text(
                            text = stringResource(
                                id = R.string.selected_slot,
                                if (slot == "a") stringResource(id = R.string.slot_a)
                                else stringResource(id = R.string.slot_b)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.root_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            2 -> {
                if (rootAvailable) {
                    Text(
                        text = stringResource(R.string.kpm_standalone_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    InstallMethodRow(
                        title = stringResource(id = R.string.select_file),
                        summary = kpmSelectBootTip,
                        selected = kpmSource is KpmInstallSource.BootImage,
                        onClick = {
                            kpmBootPicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "application/*"
                                putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    arrayOf("application/octet-stream", "application/x-boot-image")
                                )
                            })
                        },
                    )
                    InstallMethodRow(
                        title = stringResource(id = R.string.flash_anykernel_zip),
                        summary = anyKernelZipSummary,
                        selected = kpmSource is KpmInstallSource.AnyKernelZip,
                        onClick = {
                            kpmAnyKernelPicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "application/zip"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            })
                        },
                    )
                    InstallMethodRow(
                        title = stringResource(id = R.string.horizon_kernel),
                        summary = horizonKernelSummary,
                        selected = kpmSource is KpmInstallSource.KernelZip,
                        onClick = {
                            kpmKernelPicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "application/zip"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            })
                        },
                    )

                    if (kpmSource is KpmInstallSource.KernelZip) {
                        kpmSlot?.let { slot ->
                            Text(
                                text = stringResource(
                                    id = R.string.selected_slot,
                                    if (slot == "a") stringResource(id = R.string.slot_a)
                                    else stringResource(id = R.string.slot_b)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    // KMI only feeds the KernelSU install, which the KPM
                    // patch/undo flash skips (--no-install): show it solely
                    // for the follow-kernel (flash as-is) path.
                    if (kpmSource is KpmInstallSource.BootImage && showKpmKmiRow &&
                        kpmPatchOption == KpmPatchOption.FOLLOW_KERNEL
                    ) {
                        InstallActionRow(
                            icon = Icons.TwoTone.Edit,
                            title = stringResource(id = R.string.select_kmi),
                            description = kpmKmi
                                ?: stringResource(id = R.string.magic_mount_backend_auto),
                            onClick = onKpmKmiClick,
                        )
                    }

                    KpmPatchOptionSelector(
                        selectedOption = kpmPatchOption,
                        onOptionChanged = onKpmPatchOptionChanged
                    )

                    if (kpmPatchOption != KpmPatchOption.FOLLOW_KERNEL) {
                        SegmentedColumn(
                            title = stringResource(R.string.kpm_save_patched),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            item {
                                SettingsSwitchWidget(
                                    title = stringResource(R.string.kpm_save_patched),
                                    description = stringResource(R.string.kpm_save_patched_summary),
                                    checked = kpmSavePatched,
                                    onCheckedChange = onKpmSavePatchedChanged
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.root_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .renderBackgroundBlur(if (isNextEnabled) containerColor else disabledContainerColor),
            enabled = isNextEnabled,
            onClick = onClickNext,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (blurEnabled) Color.Transparent else containerColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = if (blurEnabled) Color.Transparent else disabledContainerColor,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.6f
                )
            )
        ) {
            Text(
                stringResource(id = R.string.install_next),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(innerPaddingBottom + 16.dp))
    }
}

@Composable
private fun KpmPatchOptionSelector(
    selectedOption: KpmPatchOption,
    onOptionChanged: (KpmPatchOption) -> Unit,
) {
    val options = KpmPatchOption.entries.toList()
    val labels = listOf(
        stringResource(R.string.kpm_follow_kernel_file),
        stringResource(R.string.enable_kpm_patch),
        stringResource(R.string.enable_kpm_undo_patch)
    )
    val descriptions = listOf(
        stringResource(R.string.kpm_follow_kernel_description),
        stringResource(R.string.kpm_patch_switch_description),
        stringResource(R.string.kpm_undo_patch_switch_description)
    )
    SegmentedColumn(
        title = stringResource(R.string.kpm_patch_options),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SettingsChooseWidget(
                title = stringResource(R.string.kpm_patch_options),
                description = stringResource(R.string.kpm_patch_description),
                items = labels,
                itemDescriptions = descriptions,
                selectedIndex = options.indexOf(selectedOption).takeIf { it >= 0 } ?: 0,
                onSelectedIndexChange = { index ->
                    options.getOrNull(index)?.let(onOptionChanged)
                }
            )
        }
    }
}

@Composable
private fun HookFlavorSelector(
    selectedOption: HookFlavor,
    onOptionChanged: (HookFlavor) -> Unit,
) {
    val options = HookFlavor.entries.toList()
    val labels = listOf(
        stringResource(R.string.hook_flavor_tracepoint),
        stringResource(R.string.hook_flavor_tamper)
    )
    val descriptions = listOf(
        stringResource(R.string.hook_flavor_tracepoint_description),
        stringResource(R.string.hook_flavor_tamper_description)
    )
    SegmentedColumn(
        title = stringResource(R.string.install_hook_flavor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SettingsChooseWidget(
                title = stringResource(R.string.install_hook_flavor),
                description = stringResource(R.string.install_hook_flavor_summary),
                items = labels,
                itemDescriptions = descriptions,
                selectedIndex = options.indexOf(selectedOption).takeIf { it >= 0 } ?: 0,
                onSelectedIndexChange = { index ->
                    options.getOrNull(index)?.let(onOptionChanged)
                }
            )
        }
    }
}

/**
 * Wild KSU style radio row: plain toggleable row, no cards.
 */
@Composable
private fun InstallMethodRow(
    title: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                role = Role.RadioButton,
                indication = LocalIndication.current,
                interactionSource = interactionSource,
                onValueChange = { onClick() }
            )
            .padding(vertical = 12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            interactionSource = interactionSource
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Plain clickable row for auxiliary picks (partition, LKM file).
 */
@Composable
private fun InstallActionRow(
    icon: ImageVector,
    title: String,
    description: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

sealed class KpmInstallSource {
    abstract val uri: Uri?

    data class BootImage(override val uri: Uri? = null) : KpmInstallSource()

    data class AnyKernelZip(override val uri: Uri? = null) : KpmInstallSource()

    data class KernelZip(override val uri: Uri? = null) : KpmInstallSource()
}

sealed class InstallMethod {
    data class SelectFile(
        val uri: Uri? = null,
        @param:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    data class HorizonKernel(
        val uri: Uri? = null,
        val slot: String? = null,
        @param:StringRes override val label: Int = R.string.horizon_kernel,
        override val summary: String? = null
    ) : InstallMethod()

    data class AnyKernelZip(
        val uri: Uri? = null,
        @param:StringRes override val label: Int = R.string.flash_anykernel_zip,
        override val summary: String? = null
    ) : InstallMethod()

    data class PatchBootImage(
        val bootUri: Uri? = null,
        val zipUri: Uri? = null,
        @param:StringRes override val label: Int = R.string.patch_boot_anykernel,
        override val summary: String? = null
    ) : InstallMethod()

    abstract val label: Int
    open val summary: String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSelectKmiDialog(
    supportedKmi: List<String>,
    onSelected: (String?) -> Unit,
): DialogHandle {
    return rememberCustomDialog { dismiss ->
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.surfaceBright
            )
        ) {
            SettingsChooseDialog(
                show = true,
                title = stringResource(R.string.select_kmi),
                items = supportedKmi,
                selectedIndex = -1,
                onDismiss = dismiss,
                onSelectedIndexChange = { index ->
                    onSelected(supportedKmi.getOrNull(index))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartitionChooseDialog(
    partitions: List<String>,
    defaultPartitionName: String,
    suffix: String?,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val displayNames = partitions.map { name ->
        if (defaultPartitionName == name) "$name (default)" else name
    }
    val titleSuffix = suffix?.let { " ($it)" }.orEmpty()
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        SettingsChooseDialog(
            show = true,
            title = stringResource(R.string.install_select_partition) + titleSuffix,
            items = displayNames,
            selectedIndex = selectedIndex,
            onDismiss = onDismiss,
            onSelectedIndexChange = onSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(
        ),
        title = {
            Text(
                stringResource(R.string.install)
            )
        },
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
        ),
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

private fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true

    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                name?.endsWith(".ko", ignoreCase = true) == true
            } else {
                false
            }
        } ?: false
    } catch (_: Throwable) {
        false
    }
}

@Preview
@Composable
fun SelectInstallPreview() {
    InstallScreen()
}
