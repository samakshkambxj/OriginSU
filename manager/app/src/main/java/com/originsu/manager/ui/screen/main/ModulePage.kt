package com.originsu.manager.ui.screen.main

import android.annotation.SuppressLint
import android.app.Activity.CLIPBOARD_SERVICE
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.automirrored.twotone.Wysiwyg
import androidx.compose.material.icons.twotone.AddToHomeScreen
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Photo
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.topjohnwu.superuser.io.SuFile
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousRoundedRectangle
import com.originsu.manager.R
import com.originsu.manager.domain.model.InstalledModule
import com.originsu.manager.domain.model.MetaModuleStatus
import com.originsu.manager.domain.usecase.EnqueueDownloadUseCase
import com.originsu.manager.domain.usecase.ExtractModuleNameUseCase
import com.originsu.manager.domain.usecase.FetchRemoteTextUseCase
import com.originsu.manager.domain.usecase.IsModuleUriAccessibleUseCase
import com.originsu.manager.domain.usecase.ObserveDownloadUseCase
import com.originsu.manager.domain.usecase.TakeModuleUriPermissionUseCase
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.InstallConfirmationDialog
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.ZipFileDetector
import com.originsu.manager.ui.component.ZipFileInfo
import com.originsu.manager.ui.component.ZipType
import com.originsu.manager.ui.component.popupBlur
import com.originsu.manager.ui.component.popupContainerColor
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.SettingsTextFieldWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.screen.LabelText
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.theme.renderBackgroundBlur
import com.originsu.manager.ui.util.LocalPermissionRequestInterface
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.downloader.download
import com.originsu.manager.ui.util.module.Shortcut
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.HomeViewModel
import com.originsu.manager.ui.viewmodel.ModuleUiAction
import com.originsu.manager.ui.viewmodel.ModuleUiEvent
import com.originsu.manager.ui.viewmodel.ModuleUiState
import com.originsu.manager.ui.viewmodel.ModuleViewModel
import com.originsu.manager.ui.webui.WebUIActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


private enum class ShortcutType {
    Action,
    WebUI
}

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
@SuppressLint("ResourceType", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModulePage(bottomPadding: Dp) {
    val isModuleUriAccessible = koinInject<IsModuleUriAccessibleUseCase>()
    val takeModuleUriPermission = koinInject<TakeModuleUriPermissionUseCase>()
    val extractModuleName = koinInject<ExtractModuleNameUseCase>()
    val zipFileDetector = koinInject<ZipFileDetector>()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = koinViewModel<ModuleViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val homeState by koinViewModel<HomeViewModel>().uiState.collectAsStateWithLifecycle()
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var lastClickTime by remember { mutableStateOf(0L) }

    val updateAllText = stringResource(R.string.update_all)
    val updateAllConfirm = stringResource(R.string.update_all_confirm)
    val updateText = stringResource(R.string.module_update)
    val cancelText = stringResource(android.R.string.cancel)
    val updateAllConfirmDialog = rememberConfirmDialog()
    val updateAllEnqueue = koinInject<EnqueueDownloadUseCase>()
    val updateAllObserve = koinInject<ObserveDownloadUseCase>()
    val updateAllPermission = LocalPermissionRequestInterface.current

    suspend fun onUpdateAllClicked(modules: List<InstalledModule>) {
        val updatable = modules.filter { it.moduleUpdate != null && !it.remove }
        if (updatable.isEmpty()) {
            return
        }
        val names = updatable.joinToString("\n") { "• ${it.name} → ${it.moduleUpdate!!.version}" }
        val confirmResult = updateAllConfirmDialog.awaitConfirm(
            updateAllText,
            content = updateAllConfirm.format(updatable.size, names),
            confirm = updateText,
            dismiss = cancelText
        )
        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }
        val uris = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            for (module in updatable) {
                val update = module.moduleUpdate ?: continue
                val fileName = "${module.name}-${update.version}.zip"
                val downloaded = kotlinx.coroutines.CompletableDeferred<String>()
                download(
                    context,
                    updateAllPermission,
                    update.zipUrl,
                    fileName,
                    updateAllEnqueue,
                    updateAllObserve,
                    onDownloaded = { uri ->
                        if (!downloaded.isCompleted) {
                            downloaded.complete(uri.toString())
                        }
                    },
                )
                uris.add(downloaded.await())
            }
        }
        if (uris.isNotEmpty()) {
            navigator.push(Route.Flash.modules(uris))
        }
    }

    var showDropdown by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var pendingZipFiles by remember { mutableStateOf<List<ZipFileInfo>>(emptyList()) }
    InstallConfirmationDialog(
        show = showConfirmationDialog,
        zipFiles = pendingZipFiles,
        onConfirm = { info ->
            showConfirmationDialog = false
            navigator.push(
                Route.Flash.modules(info.filter { it.type == ZipType.MODULE }
                    .map { it.uri.toString() })
            )
            viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
        },
        onDismiss = {
            showConfirmationDialog = false
            pendingZipFiles = emptyList()
        }
    )

    val selectZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult

        scope.launch {
            val zipFiles = mutableListOf<ZipFileInfo>()
            val clipData = data.clipData
            if (clipData != null) {
                val selectedModules = mutableListOf<Uri>()
                val selectedModuleNames = mutableMapOf<Uri, String>()

                fun processUri(uri: Uri) {
                    try {
                        val uriString = uri.toString()
                        if (!isModuleUriAccessible(uriString)) {
                            return
                        }
                        takeModuleUriPermission(uriString)
                        val moduleName = extractModuleName(uriString)
                        selectedModules.add(uri)
                        selectedModuleNames[uri] = moduleName
                    } catch (e: Exception) {
                        Log.e("ModuleScreen", "Error while processing URI: $uri, Error: ${e.message}")
                    }
                }

                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    processUri(uri)
                }

                if (selectedModules.isEmpty()) {
                    snackBarHost.showReplacingSnackbar("Unable to access selected module files")
                    return@launch
                }
                selectedModules.forEach { it ->
                    zipFiles.add(zipFileDetector.parseModuleInfo(context, it))
                }
                pendingZipFiles = zipFiles

                showConfirmationDialog = true
            } else {
                val uri = data.data ?: return@launch
                // 单个安装模块
                try {
                    val uriString = uri.toString()
                    if (!isModuleUriAccessible(uriString)) {
                        snackBarHost.showReplacingSnackbar("Unable to access selected module files")
                        return@launch
                    }

                    takeModuleUriPermission(uriString)

                    zipFiles.add(zipFileDetector.parseModuleInfo(context, uri))
                    pendingZipFiles = zipFiles

                    showConfirmationDialog = true
                } catch (e: Exception) {
                    Log.e("ModuleScreen", "Error processing a single URI: $uri, Error: ${e.message}")
                    snackBarHost.showReplacingSnackbar("Error processing module file: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.dispatch(ModuleUiAction.Search(""))
        if (uiState.moduleList.isEmpty() || uiState.isNeedRefresh) {
            viewModel.dispatch(ModuleUiAction.Refresh())
        }
    }

    val isSafeMode = homeState.systemStatus.isSafeMode
    val hideInstallButton = isSafeMode || uiState.hasMagisk

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    )

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.module),
                searchText = uiState.search,
                onSearchTextChange = { query ->
                    viewModel.dispatch(ModuleUiAction.Search(query))
                },
                dropdownContent = {
                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(id = R.string.settings),
                        )

                        ModuleDropdown(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            viewModel = viewModel,
                            uiState = uiState,
                            onUpdateAll = {
                                showDropdown = false
                                scope.launch {
                                    onUpdateAllClicked(
                                        uiState.moduleList
                                    )
                                }
                            },
                        )
                    }
                },
                navigationContent = {
                    IconButton(
                        onClick = {
                            navigator.push(Route.ModuleRepo)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Cloud,
                            contentDescription = stringResource(id = R.string.module_repo),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_modules),
            )
        },
        floatingActionButton = {
            if (hideInstallButton) return@Scaffold

            FloatingActionButton(
                modifier = Modifier.padding(bottom = bottomPadding + 5.dp),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    selectZipLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    )
                },
                content = {
                    Icon(
                        painter = painterResource(id = R.drawable.package_import),
                        contentDescription = null
                    )
                }
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(includeBottom = false),
        snackbarHost = {
            SwipeableSnackbarHost(
                hostState = snackBarHost
            )
        }
    ) { innerPadding ->
        when {
            uiState.hasMagisk -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            stringResource(R.string.module_magisk_conflict),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            uiState.moduleList.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Extension,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            text =
                                if (uiState.search.isNotEmpty())
                                    stringResource(R.string.search_no_any_match)
                                else
                                    stringResource(R.string.module_empty),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            else -> {
                ModuleList(
                    viewModel = viewModel,
                    uiState = uiState,
                    listState = listState,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    onUpdateModule = {
                        navigator.push(Route.Flash.moduleUpdate(it.toString()))
                    },
                    onClickModule = { id, name, hasWebUi ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 600) {
                            Log.d("ModuleScreen", "Click too fast, ignoring")
                            return@ModuleList
                        }
                        lastClickTime = currentTime

                        if (hasWebUi) {
                            try {
                                context.startActivity(
                                    Intent(context, WebUIActivity::class.java)
                                    .setData("kernelsu://webui/$id".toUri())
                                    .putExtra("id", id)
                                        .putExtra("name", name)
                                )
                            } catch (e: Exception) {
                                Log.e("ModuleScreen", "Error launching WebUI: ${e.message}", e)
                                scope.launch {
                                    snackBarHost.showReplacingSnackbar("Error launching WebUI: ${e.message}")
                                }
                            }
                            return@ModuleList
                        }
                    },
                    context = context,
                    snackBarHost = snackBarHost,
                    bottomPadding = bottomPadding + innerPadding.calculateBottomPadding(),
                    topPadding = innerPadding.calculateTopPadding(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BatchActionBar(
    countText: String,
    enableText: String,
    disableText: String,
    uninstallText: String,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onUninstall: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = countText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) {
                Text(enableText)
            }
            TextButton(onClick = onDisable) {
                Text(disableText)
            }
            TextButton(onClick = onUninstall) {
                Text(uninstallText)
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(android.R.string.cancel)
                )
            }
        }
    }
}

@Composable
private fun ModuleDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: ModuleViewModel,
    uiState: ModuleUiState,
    onUpdateAll: () -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
        ) {
            if (uiState.moduleList.any { it.moduleUpdate != null && !it.remove }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.update_all)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.TwoTone.Download,
                            contentDescription = null
                        )
                    },
                    onClick = onUpdateAll,
                )
            }
            CheckableDropdownMenuItem(
                checked = uiState.sortActionFirst,
                onCheckedChange = {
                    viewModel.dispatch(
                        ModuleUiAction.Sort(uiState.sortEnabledFirst, it)
                    )
                },
                text = { Text(stringResource(R.string.module_sort_action_first)) },
                shapes = MenuDefaults.itemShape(
                    index = 0,
                    count = 3,
                ),
            )
            CheckableDropdownMenuItem(
                checked = uiState.sortEnabledFirst,
                onCheckedChange = {
                    viewModel.dispatch(
                        ModuleUiAction.Sort(it, uiState.sortActionFirst)
                    )
                },
                text = { Text(stringResource(R.string.module_sort_enabled_first)) },
                shapes = MenuDefaults.itemShape(
                    index = 1,
                    count = 3,
                ),
            )
            CheckableDropdownMenuItem(
                checked = uiState.showBanners,
                onCheckedChange = {
                    viewModel.dispatch(
                        ModuleUiAction.SetShowBanners(it)
                    )
                },
                text = { Text(stringResource(R.string.show_module_banners)) },
                shapes = MenuDefaults.itemShape(
                    index = 2,
                    count = 3,
                ),
            )
        }
    }
}

private fun getMetaModuleWarningText(
    hasModuleRequireMount: Boolean,
    showWarning: Boolean,
    context: Context,
    status: MetaModuleStatus,
) : String? {
    if (!showWarning) return null
    if (!hasModuleRequireMount) return null

    return when (status) {
        MetaModuleStatus.MISSING -> context.getString(R.string.no_meta_module_installed)
        MetaModuleStatus.REMOVED -> context.getString(R.string.meta_module_removed)
        MetaModuleStatus.DISABLED -> context.getString(R.string.meta_module_disabled)
        MetaModuleStatus.ACTIVE -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaModuleWarningCard(
    text: String,
    visible: Boolean,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        WarningCard(
            shape = CardDefaults.elevatedShape,
            message = text,
            onClose = onClose,
        )

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleList(
    viewModel: ModuleViewModel,
    uiState: ModuleUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    boxModifier: Modifier = Modifier,
    onUpdateModule: (Uri) -> Unit,
    onClickModule: (id: String, name: String, hasWebUi: Boolean) -> Unit,
    context: Context,
    snackBarHost: SnackbarHostState,
    bottomPadding : Dp,
    topPadding : Dp,
) {
    val shortcut = koinInject<Shortcut>()
    var showMetaModuleWarning by rememberSaveable { mutableStateOf(true) }
    val fetchRemoteText = koinInject<FetchRemoteTextUseCase>()
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val observeDownload = koinInject<ObserveDownloadUseCase>()
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()
    val failedEnable = stringResource(R.string.module_failed_to_enable)
    val failedDisable = stringResource(R.string.module_failed_to_disable)
    val failedUninstall = stringResource(R.string.module_uninstall_failed)
    val successUninstall = stringResource(R.string.module_uninstall_success)
    val reboot = stringResource(R.string.reboot)
    val rebootToApply = stringResource(R.string.reboot_to_apply)
    val moduleStr = stringResource(R.string.module)
    val uninstall = stringResource(R.string.uninstall)
    val cancel = stringResource(android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(R.string.module_uninstall_confirm)
    val batchResult = stringResource(R.string.batch_result)
    val batchUninstallConfirm = stringResource(R.string.batch_uninstall_confirm)
    val batchEnable = stringResource(R.string.batch_enable)
    val batchDisable = stringResource(R.string.batch_disable)
    val batchUninstall = stringResource(R.string.batch_uninstall)
    val selectedCountFmt = stringResource(R.string.selected_count)
    val metaModuleUninstallConfirm = stringResource(R.string.metamodule_uninstall_confirm)
    val updateText = stringResource(R.string.module_update)
    val updateAllText = stringResource(R.string.update_all)
    val updateAllConfirm = stringResource(R.string.update_all_confirm)
    val changelogText = stringResource(R.string.module_changelog)
    val downloadingText = stringResource(R.string.module_downloading)
    val startDownloadingText = stringResource(R.string.module_start_downloading)
    val fetchChangeLogFailed = stringResource(R.string.module_changelog_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ModuleUiEvent.EnabledChanged -> {
                    val moduleName = uiState.moduleList
                        .find { it.dirId == event.moduleId }
                        ?.name ?: event.moduleId
                    if (event.successful) {
                        val result = snackBarHost.showReplacingSnackbar(
                            message = rebootToApply,
                            actionLabel = reboot,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.dispatch(ModuleUiAction.Reboot)
                        }
                    } else {
                        val message = if (event.enabled) failedEnable else failedDisable
                        snackBarHost.showReplacingSnackbar(message.format(moduleName))
                    }
                }

                is ModuleUiEvent.RemovedChanged -> {
                    val moduleName = uiState.moduleList
                        .find { it.dirId == event.moduleId }
                        ?.name ?: event.moduleId
                    if (event.successful) {
                        viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                        viewModel.dispatch(ModuleUiAction.Refresh())
                    }
                    if (event.removed) {
                        val message = if (event.successful) successUninstall else failedUninstall
                        val result = snackBarHost.showReplacingSnackbar(
                            message = message.format(moduleName),
                            actionLabel = reboot.takeIf { event.successful },
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.dispatch(ModuleUiAction.Reboot)
                        }
                    }
                }

                is ModuleUiEvent.Error -> if (event.message.isNotBlank()) {
                    snackBarHost.showReplacingSnackbar(event.message)
                }

                is ModuleUiEvent.BatchCompleted -> {
                    viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                    val message = if (event.failed == 0) {
                        rebootToApply
                    } else {
                        batchResult.format(event.succeeded, event.failed)
                    }
                    val result = snackBarHost.showReplacingSnackbar(
                        message = message,
                        actionLabel = reboot.takeIf { event.failed == 0 },
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.dispatch(ModuleUiAction.Reboot)
                    }
                }

                ModuleUiEvent.RefreshCompleted -> Unit
            }
        }
    }

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    var shortcutModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    val textFieldState = rememberTextFieldState()
    var shortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultActionShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultWebUiShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedShortcutType by rememberSaveable { mutableStateOf<ShortcutType?>(null) }
    val showShortcutDialog = remember { mutableStateOf(false) }
    val showShortcutTypeRow = remember { mutableStateOf(false) }

    fun openShortcutDialogForType(type: ShortcutType) {
        selectedShortcutType = type
        val defaultIcon = when (type) {
            ShortcutType.Action -> defaultActionShortcutIconUri ?: defaultWebUiShortcutIconUri
            ShortcutType.WebUI -> defaultWebUiShortcutIconUri ?: defaultActionShortcutIconUri
        }
        defaultShortcutIconUri = defaultIcon
        shortcutIconUri = defaultIcon
        showShortcutDialog.value = true
    }

    fun hasModuleShortcut(context: Context, moduleId: String, type: ShortcutType): Boolean {
        return when (type) {
            ShortcutType.Action -> shortcut.hasModuleActionShortcut(context, moduleId)
            ShortcutType.WebUI -> shortcut.hasModuleWebUiShortcut(context, moduleId)
        }
    }

    fun deleteModuleShortcut(context: Context, moduleId: String, type: ShortcutType) {
        when (type) {
            ShortcutType.Action -> shortcut.deleteModuleActionShortcut(context, moduleId)
            ShortcutType.WebUI -> shortcut.deleteModuleWebUiShortcut(context, moduleId)
        }
    }

    fun createModuleShortcut(
        context: Context,
        moduleId: String,
        name: String,
        iconUri: String?,
        type: ShortcutType
    ) {
        when (type) {
            ShortcutType.Action -> {
                shortcut.createModuleActionShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri
                )
            }

            ShortcutType.WebUI -> {
                shortcut.createModuleWebUiShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri
                )
            }
        }
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutIconUri = uri?.toString()
    }

    val shortcutPreviewIcon = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shortcutIconUri) {
        val uriStr = shortcutIconUri
        if (uriStr.isNullOrBlank()) {
            shortcutPreviewIcon.value = null
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.IO) {
            shortcut.loadShortcutBitmap(context, uriStr)
        }
        shortcutPreviewIcon.value = bitmap?.asImageBitmap()
    }

    var hasExistingShortcut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shortcutModuleId, selectedShortcutType, showShortcutDialog.value) {
        val moduleId = shortcutModuleId
        val type = selectedShortcutType
        if (!showShortcutDialog.value || moduleId.isNullOrBlank() || type == null) {
            hasExistingShortcut = false
            return@LaunchedEffect
        }
        val exists = withContext(Dispatchers.IO) {
            hasModuleShortcut(context, moduleId, type)
        }
        hasExistingShortcut = exists
    }

    suspend fun onModuleUpdate(
        module: InstalledModule,
        changelogUrl: String,
        downloadUrl: String,
        fileName: String
    ) {
        val changelogResult = loadingDialog.withLoading {
            fetchRemoteText(changelogUrl)
        }

        val showToast: suspend (String) -> Unit = { msg ->
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    msg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val changelog = changelogResult.getOrElse {
            showToast(fetchChangeLogFailed.format(it.message))
            return
        }

        val confirmResult = confirmDialog.awaitConfirm(
            changelogText,
            content = changelog,
            markdown = true,
            confirm = updateText,
        )

        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        showToast(startDownloadingText.format(module.name))

        val downloading = downloadingText.format(module.name)
        withContext(Dispatchers.IO) {
            download(
                context,
                permissionRequestInterface,
                downloadUrl,
                fileName,
                enqueueDownload,
                observeDownload,
                onDownloaded = { uri ->
                    onUpdateModule(uri)
                },
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloading, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    suspend fun onModuleUninstallClicked(module: InstalledModule) {        val isUninstall = !module.remove
        if (isUninstall) {
            val formatter = if (module.metamodule) metaModuleUninstallConfirm else moduleUninstallConfirm
            val confirmResult = confirmDialog.awaitConfirm(
                moduleStr,
                content = formatter.format(module.name),
                confirm = uninstall,
                dismiss = cancel
            )
            if (confirmResult != ConfirmResult.Confirmed) {
                return
            }
        }

        if (isUninstall) {
            withContext(Dispatchers.IO) {
                shortcut.deleteModuleActionShortcut(context, module.id)
                shortcut.deleteModuleWebUiShortcut(context, module.id)
            }
        }
        viewModel.dispatch(ModuleUiAction.SetRemoved(module.dirId, isUninstall))
    }

    fun onModuleAddShortcut(module: InstalledModule) {
        shortcutModuleId = module.id
        textFieldState.edit {
            replace(0, length, module.name)
        }
        shortcutIconUri = null
        defaultShortcutIconUri = null
        defaultActionShortcutIconUri = module.actionIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        defaultWebUiShortcutIconUri = module.webUiIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        if (module.hasActionScript && module.hasWebUi) {
            selectedShortcutType = null
            showShortcutTypeRow.value = true
            openShortcutDialogForType(ShortcutType.Action)
        } else if (module.hasActionScript) {
            openShortcutDialogForType(ShortcutType.Action)
        } else if (module.hasWebUi) {
            openShortcutDialogForType(ShortcutType.WebUI)
        }
    }

    PullToRefreshBox(
        state = pullRefreshState,
        onRefresh = {
            viewModel.dispatch(ModuleUiAction.Refresh(manual = true))
        },
        modifier = boxModifier
            .fillMaxSize()
            .blurSource(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier
                    .padding(top = topPadding)
                    .align(Alignment.TopCenter),
                state = pullRefreshState,
                isRefreshing = uiState.isRefreshing,
            )
        },
        isRefreshing = uiState.isRefreshing
    ) {
        val metaModuleWarningText by produceState<String?>(
            initialValue = null,
            uiState.hasModuleRequireMount,
            showMetaModuleWarning,
            uiState.metaModuleStatus,
        ) {
            value = withContext(Dispatchers.IO) {
                getMetaModuleWarningText(
                    uiState.hasModuleRequireMount,
                    showMetaModuleWarning,
                    context,
                    uiState.metaModuleStatus,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .scrollEndHaptic()
                .overScrollVertical(),
            overscrollEffect = null,
            contentPadding = remember {
                PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 72.dp + 5.dp + 5.dp // FAB + bottom padding of FAB
                )
            },
        ) {
            item {
                Spacer(modifier = Modifier.height(topPadding))
            }

            if (metaModuleWarningText != null) {
                item(
                    key = "warning"
                ) {
                    MetaModuleWarningCard(
                        text = metaModuleWarningText!!,
                        visible = showMetaModuleWarning,
                        onClose = { showMetaModuleWarning = false },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (uiState.selectedModuleIds.isNotEmpty()) {
                item(key = "batch-bar") {
                    BatchActionBar(
                        countText = selectedCountFmt.format(uiState.selectedModuleIds.size),
                        enableText = batchEnable,
                        disableText = batchDisable,
                        uninstallText = batchUninstall,
                        onEnable = {
                            viewModel.dispatch(ModuleUiAction.BatchSetEnabled(true))
                        },
                        onDisable = {
                            viewModel.dispatch(ModuleUiAction.BatchSetEnabled(false))
                        },
                        onUninstall = {
                            scope.launch {
                                val confirmResult = confirmDialog.awaitConfirm(
                                    moduleStr,
                                    content = batchUninstallConfirm.format(uiState.selectedModuleIds.size),
                                    confirm = uninstall,
                                    dismiss = cancel
                                )
                                if (confirmResult != ConfirmResult.Confirmed) {
                                    return@launch
                                }
                                withContext(Dispatchers.IO) {
                                    val modulesById = uiState.moduleList.associateBy { it.dirId }
                                    uiState.selectedModuleIds.forEach { dirId ->
                                        modulesById[dirId]?.let { module ->
                                            shortcut.deleteModuleActionShortcut(context, module.id)
                                            shortcut.deleteModuleWebUiShortcut(context, module.id)
                                        }
                                    }
                                }
                                viewModel.dispatch(ModuleUiAction.BatchSetRemoved)
                            }
                        },
                        onClear = {
                            viewModel.dispatch(ModuleUiAction.ClearSelection)
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(
                items = uiState.moduleList,
                key = { "module-$it.id" }
            ) { module ->
                ModuleItem(
                    viewModel = viewModel,
                    module = module,
                    moduleSizes = uiState.moduleSizes,
                    updateUrl = module.moduleUpdate?.zipUrl.orEmpty(),
                    onUninstallClicked = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUninstallClicked(module)
                            }
                        }
                    },
                    onCheckChanged = { enabled ->
                        viewModel.dispatch(ModuleUiAction.SetEnabled(module.dirId, enabled))
                        true
                    },
                    onUpdate = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUpdate(
                                    module,
                                    module.moduleUpdate!!.changelog,
                                    module.moduleUpdate.zipUrl,
                                    "${module.name}-${module.moduleUpdate.version}.zip"
                                )
                            }
                        }
                    },
                    onClick = {
                        onClickModule(it.dirId, it.name, it.hasWebUi)
                    },
                    onModuleAddShortcut = {
                        onModuleAddShortcut(it)
                    },
                    showMoreModuleInfo = uiState.showMoreModuleInfo,
                    showBanners = uiState.showBanners,
                    selected = module.dirId in uiState.selectedModuleIds,
                    selectionMode = uiState.selectedModuleIds.isNotEmpty(),
                    onToggleSelect = {
                        viewModel.dispatch(ModuleUiAction.ToggleSelect(module.dirId))
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }
    }

    if (showShortcutDialog.value) {
        ModalBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            ),
            onDismissRequest = {
                showShortcutDialog.value = false
                showShortcutTypeRow.value = false
            },
            modifier = Modifier.popupBlur(),
            containerColor = popupContainerColor(),
        ) {
            var error by remember { mutableStateOf("") }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.module_shortcut_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                )
                if (showShortcutTypeRow.value) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedShortcutType?.ordinal ?: 0,
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedShortcutType == ShortcutType.Action,
                            onClick = {
                                selectedShortcutType = ShortcutType.Action
                                shortcutIconUri = defaultActionShortcutIconUri
                                defaultShortcutIconUri = defaultActionShortcutIconUri
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("Action") }
                        )

                        Tab(
                            selected = selectedShortcutType == ShortcutType.WebUI,
                            onClick = {
                                selectedShortcutType = ShortcutType.WebUI
                                shortcutIconUri = defaultWebUiShortcutIconUri
                                defaultShortcutIconUri = defaultWebUiShortcutIconUri
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("WebUI") }
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(100.dp)
                        .clip(ContinuousRoundedRectangle(25.dp))
                ) {
                    val preview = shortcutPreviewIcon.value
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            modifier = Modifier.size(100.dp),
                            contentDescription = null,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color.White)
                        )
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = FixedScale(1.5f)
                        )
                    }
                }
                SegmentedColumn {
                    if (shortcutIconUri == defaultShortcutIconUri) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Photo,
                                isOnBackground = false,
                                title = stringResource(id = R.string.module_shortcut_icon_pick),
                                onClick = {
                                    pickShortcutIconLauncher.launch("image/*")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Restore,
                                isOnBackground = false,
                                title = stringResource(id = R.string.restore),
                                onClick = {
                                    shortcutIconUri = defaultShortcutIconUri
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.TwoTone.Undo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    item {
                        val shouldNotEmpty =
                            stringResource(R.string.module_shortcut_should_not_empty)
                        SettingsTextFieldWidget(
                            state = textFieldState,
                            title = stringResource(id = R.string.module_shortcut_name_label),
                            error = error,
                            renderBackgroundBlur = false,
                        )

                        LaunchedEffect(textFieldState.text) {
                            error = if (textFieldState.text.isBlank()) {
                                shouldNotEmpty
                            } else ""
                        }
                    }

                    if (hasExistingShortcut) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Delete,
                                renderBackgroundBlur = false,
                                title = stringResource(id = R.string.module_shortcut_delete),
                                onClick = {
                                    val moduleId = shortcutModuleId
                                    val type = selectedShortcutType
                                    if (!moduleId.isNullOrBlank() && type != null) {
                                        deleteModuleShortcut(context, moduleId, type)
                                    }
                                    showShortcutDialog.value = false
                                },
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showShortcutDialog.value = false },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = stringResource(id = android.R.string.cancel),
                        )
                    }

                    Button(
                        onClick = {
                            val moduleId = shortcutModuleId
                            val type = selectedShortcutType
                            if (!moduleId.isNullOrBlank() && textFieldState.text.isNotBlank() && type != null) {
                                createModuleShortcut(
                                    context = context,
                                    moduleId = moduleId,
                                    name = textFieldState.text.toString(),
                                    iconUri = shortcutIconUri,
                                    type = type
                                )
                            }
                            showShortcutDialog.value = false
                        },
                        enabled = error.isBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = if (hasExistingShortcut) {
                                stringResource(id = R.string.module_update)
                            } else {
                                stringResource(id = android.R.string.ok)
                            },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun ModuleBannerBackdrop(
    modifier: Modifier = Modifier,
    moduleId: String,
    banner: String,
) {
    val context = LocalContext.current
    val fadeColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (banner.startsWith("http", ignoreCase = true)) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop,
                alpha = 0.18f
            )
        } else {
            val bannerData = remember(moduleId, banner) {
                runCatching {
                    val file = SuFile("/data/adb/modules/$moduleId/$banner")
                    if (file.exists()) {
                        return@runCatching file
                    }
                    SuFile("/data/adb/modules_update/$moduleId/$banner")
                }.getOrNull()?.let { file ->
                    runCatching { file.newInputStream().use { it.readBytes() } }.getOrNull()
                }
            }
            if (bannerData != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bannerData)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.18f
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            fadeColor.copy(alpha = 0.0f),
                            fadeColor.copy(alpha = 0.8f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
    }
}

@Composable
fun ModuleItem(
    viewModel: ModuleViewModel,
    module: InstalledModule,
    moduleSizes: Map<String, String>,
    updateUrl: String,
    onUninstallClicked: (InstalledModule) -> Unit,
    onCheckChanged: suspend (Boolean) -> Boolean,
    onUpdate: (InstalledModule) -> Unit,
    onClick: (InstalledModule) -> Unit,
    onModuleAddShortcut: (InstalledModule) -> Unit,
    showMoreModuleInfo: Boolean,
    showBanners: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isEnabled by remember(module.dirId) { mutableStateOf(module.enabled) }
    var isChangingEnabled by remember(module.dirId) { mutableStateOf(false) }

    LaunchedEffect(module.enabled) {
        isEnabled = module.enabled
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .renderBackgroundBlur(),
        color =
            if (themeConfig.isEnableBlurExp)
                Color.Transparent
            else
                MaterialTheme.colorScheme.surfaceBright.copy(cardConfig.cardAlpha),
        shape = RoundedCornerShape(16.dp)
    ) {
        val textDecoration = if (!module.remove) null else TextDecoration.LineThrough
        val interactionSource = remember { MutableInteractionSource() }

        LaunchedEffect(module.dirId) {
            viewModel.dispatch(ModuleUiAction.LoadSize(module.dirId))
        }

        val sizeStr = moduleSizes[module.dirId]

        Box(modifier = Modifier.fillMaxWidth()) {
            if (showBanners && module.banner.isNotEmpty()) {
                ModuleBannerBackdrop(
                    modifier = Modifier.matchParentSize(),
                    moduleId = module.dirId,
                    banner = module.banner,
                )
            }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                )
            }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .combinedClickable(
                    onLongClick = {
                        if (selectionMode) {
                            onToggleSelect()
                        } else if (module.hasActionScript || module.hasWebUi) {
                            onModuleAddShortcut(module)
                        } else {
                            onToggleSelect()
                        }
                    },
                    onClick = {
                        if (selectionMode) {
                            onToggleSelect()
                        } else if (module.hasWebUi) {
                            onClick(module)
                        }
                    }
                )
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val moduleVersion = stringResource(id = R.string.module_version)
                val moduleAuthor = stringResource(id = R.string.module_author)

                Column(
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = module.name,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                            textDecoration = textDecoration,
                            modifier = Modifier.weight(1f, false)
                        )
                    }

                    Text(
                        text = "$moduleVersion: ${module.version}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration,
                    )

                    Text(
                        text = "$moduleAuthor: ${module.author}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration,
                    )

                    // 显示更多模块信息时添加updateJson
                    if (showMoreModuleInfo && module.updateJson.isNotEmpty()) {
                        val updateJsonLabel = stringResource(R.string.module_update_json)
                        val updateJsonCopied = stringResource(R.string.module_update_json_copied)
                        Text(
                            text = "$updateJsonLabel: ${module.updateJson}",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                            textDecoration = textDecoration,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        val clipData = ClipData.newPlainText(
                                            "Update JSON URL",
                                            module.updateJson
                                        )
                                        clipboardManager.setPrimaryClip(clipData)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                        Toast.makeText(
                                            context,
                                            updateJsonCopied,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Switch(
                        enabled = !module.update && !isChangingEnabled,
                        checked = isEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                isChangingEnabled = true
                                try {
                                    if (onCheckChanged(enabled)) {
                                        isEnabled = enabled
                                    }
                                } finally {
                                    isChangingEnabled = false
                                }
                            }
                        },
                        interactionSource = if (!module.hasWebUi) interactionSource else null,
                        thumbContent = {
                            if (isEnabled) {
                                Icon(
                                    imageVector = Icons.TwoTone.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            } else
                            {
                                Icon(
                                    imageVector = Icons.TwoTone.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surfaceBright,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = module.description,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
                textDecoration = textDecoration,
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LabelText(
                    label = module.dirId,
                    containerColor = MaterialTheme.colorScheme.primary,
                )
                if (module.metamodule) {
                    LabelText(
                        label = "META",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
                LabelText(
                    label = sizeStr ?: "0 KB",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = Dp.Hairline)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (module.hasActionScript || module.hasWebUi) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove,
                        onClick = { onModuleAddShortcut(module) },
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.AddToHomeScreen,
                            contentDescription = stringResource(R.string.module_shortcut_title)
                        )
                    }
                }
                if (module.hasActionScript) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove && isEnabled,
                        onClick = {
                            navigator.push(Route.ExecuteModuleAction(module.dirId))
                            viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                        },
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.PlayArrow,
                            contentDescription = null
                        )
                    }
                }

                if (module.hasWebUi) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove && isEnabled,
                        onClick = { onClick(module) },
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.AutoMirrored.TwoTone.Wysiwyg,
                            contentDescription = null
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f, true))

                if (updateUrl.isNotEmpty()) {
                    Button(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove,
                        onClick = { onUpdate(module) },
                        shape = ButtonDefaults.textShape,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.Download,
                            contentDescription = null
                        )
                    }
                }

                FilledTonalButton(
                    modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                    onClick = { onUninstallClicked(module) },
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 9.dp,
                        end = 12.dp,
                        bottom = 7.dp,
                    ),
                ) {
                    if (!module.remove) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.Delete,
                            contentDescription = null,
                        )
                    } else {
                        Icon(
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(180f),
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = null
                        )
                    }
                }
            }
        }
        }
        }
    }
}

@Preview
@Composable
fun ModuleItemPreview() {
    val module = InstalledModule(
        id = "id",
        name = "name",
        version = "version",
        versionCode = 1,
        author = "author",
        description = "I am a test module and i do nothing but show a very long description",
        enabled = true,
        update = true,
        remove = false,
        updateJson = "",
        hasWebUi = true,
        hasActionScript = true,
        metamodule = true,
        actionIconPath = null,
        webUiIconPath = null,
        dirId = "dirId",
        moduleUpdate = null
    )
    ModuleItem(
        koinViewModel<ModuleViewModel>(),
        module,
        emptyMap(),
        "",
        {},
        { true },
        {},
        {},
        {},
        false,
        true,
        false,
        false,
        {},
    )
}
