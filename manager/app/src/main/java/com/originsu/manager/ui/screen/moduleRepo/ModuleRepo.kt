package com.originsu.manager.ui.screen.moduleRepo

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Star
import androidx.compose.material.icons.twotone.WebAsset
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.domain.model.CatalogAuthor
import com.originsu.manager.domain.model.CatalogModule
import com.originsu.manager.domain.model.ModuleCategory
import com.originsu.manager.domain.model.ModuleRelease
import com.originsu.manager.domain.model.ModuleReleaseAsset
import com.originsu.manager.domain.model.RepoSource
import com.originsu.manager.domain.usecase.EnqueueDownloadUseCase
import com.originsu.manager.domain.usecase.ObserveDownloadUseCase
import com.originsu.manager.ui.activity.PermissionRequestInterface
import com.originsu.manager.ui.component.ConfirmDialogHandle
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.DialogHandle
import com.originsu.manager.ui.component.NetworkRefreshContent
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SwipeableSnackbarHost
import com.originsu.manager.ui.component.popupBlur
import com.originsu.manager.ui.component.popupContainerColor
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberCustomDialog
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Navigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.screen.LabelText
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.theme.renderBackgroundBlur
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.LocalPermissionRequestInterface
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.downloader.download
import com.originsu.manager.ui.viewmodel.ModuleRepoUiAction
import com.originsu.manager.ui.viewmodel.ModuleRepoUiEvent
import com.originsu.manager.ui.viewmodel.ModuleRepoUiState
import com.originsu.manager.ui.viewmodel.ModuleRepoViewModel
import com.originsu.manager.ui.viewmodel.formatFileSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author AlexLiuDev233
 * @date 2025/12/6
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModuleRepoScreen() {
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<ModuleRepoViewModel>()
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val observeDownload = koinInject<ObserveDownloadUseCase>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackBarHost = LocalSnackbarHost.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    )
    val currentModuleForChooseDialog = remember { mutableStateOf<CatalogModule?>(null) }
    val chooseDialog = rememberCustomDialog({ dismiss ->
        ChooseDialogContent(
            currentModuleForChooseDialog,
            enqueueDownload,
            observeDownload,
            dismiss,
        )
    })
    val confirmDialog = rememberConfirmDialog()
    val queueLoading = rememberLoadingDialog()
    val repoPermission = LocalPermissionRequestInterface.current
    val queueEmptyText = stringResource(R.string.module_repo_queue_empty)
    val queueInstallText = stringResource(R.string.module_repo_queue_install)
    val selectedCountFmt = stringResource(R.string.selected_count)
    val removeConfirmFmt = stringResource(R.string.module_repo_remove_confirm)
    val repoSourcesTitle = stringResource(R.string.module_repo_sources)
    val cancelText = stringResource(android.R.string.cancel)
    val okText = stringResource(android.R.string.ok)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDropdown by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var loadError by remember { mutableStateOf<String?>(null) }
    val refreshModules = {
        loadError = null
        viewModel.dispatch(ModuleRepoUiAction.Refresh)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                ModuleRepoUiEvent.Offline -> Unit
                is ModuleRepoUiEvent.Error -> {
                    loadError = event.message.ifBlank { null }
                    if (loadError != null) {
                        snackBarHost.showSnackbar(loadError.orEmpty())
                    }
                }
            }
        }
    }

    suspend fun installQueuedModules(ids: List<String>) {
        if (ids.isEmpty()) return
        val urls = queueLoading.withLoading { viewModel.resolveQueue(ids) }
        if (urls.isEmpty()) {
            snackBarHost.showSnackbar(queueEmptyText)
            return
        }
        val uris = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            for ((id, url) in urls) {
                val downloaded = CompletableDeferred<String>()
                val fileName = id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".zip"
                download(
                    context,
                    repoPermission,
                    url,
                    fileName,
                    enqueueDownload,
                    observeDownload,
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
        viewModel.dispatch(ModuleRepoUiAction.ClearSelection)
    }

    val repoManagerDialog = rememberCustomDialog { dismiss ->
        RepoManagerDialog(
            sources = uiState.sources,
            isWorking = uiState.isRefreshing,
            onToggle = { viewModel.setSourceEnabled(it.id, !it.enabled) },
            onDelete = { source ->
                scope.launch {
                    val confirmed = confirmDialog.awaitConfirm(
                        title = repoSourcesTitle,
                        content = removeConfirmFmt.format(source.name),
                        confirm = okText,
                        dismiss = cancelText,
                    )
                    if (confirmed != ConfirmResult.Confirmed) return@launch
                    viewModel.removeSource(source.id)
                }
            },
            onAdd = { name, url ->
                scope.launch {
                    val schema = queueLoading.withLoading { viewModel.probeSource(url) }
                    if (schema == null) {
                        snackBarHost.showSnackbar(
                            context.getString(R.string.module_repo_source_invalid)
                        )
                    } else if (!viewModel.addSource(name, url, schema)) {
                        snackBarHost.showSnackbar(
                            context.getString(R.string.module_repo_source_invalid)
                        )
                    }
                }
            },
            onClose = dismiss,
        )
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit

    }

    ActivityResumeEffect {
        refreshModules()
    }

    val isLoading = uiState.modules.isEmpty() && uiState.search.isEmpty() &&
        uiState.category == null

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.module_repo),
                searchText = uiState.search,
                onSearchTextChange = { query ->
                    viewModel.dispatch(ModuleRepoUiAction.Search(query))
                },
                dropdownContent = {
                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(id = R.string.settings),
                        )

                        ModuleRepoDropdown(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            viewModel = viewModel,
                            uiState = uiState,
                            onManageRepos = {
                                showDropdown = false
                                repoManagerDialog.show()
                            },
                        )
                    }
                },
                onBackClick = {
                    navigator.pop()
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_modules),
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) }
    ) { innerPadding ->
        if (isLoading || (loadError != null && uiState.modules.isEmpty() && !uiState.isRefreshing)) {
            NetworkRefreshContent(
                offline = uiState.offline,
                onRetry = refreshModules,
                errorMessage = loadError?.takeIf { !uiState.isRefreshing },
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
                    .padding(innerPadding),
            )
        } else if (uiState.modules.isEmpty() &&
            (uiState.search.isNotEmpty() || uiState.category != null)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
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
                        text = stringResource(R.string.search_no_any_match),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            PullToRefreshBox(
                modifier = Modifier.blurSource(),
                state = pullRefreshState,
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    viewModel.dispatch(ModuleRepoUiAction.Refresh)
                },
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullRefreshState,
                        isRefreshing = uiState.isRefreshing,
                        modifier = Modifier
                            .padding(top = innerPadding.calculateTopPadding())
                            .align(Alignment.TopCenter),
                    )
                }
            ) {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = remember {
                        PaddingValues(
                            start = 16.dp,
                            top = 0.dp,
                            end = 16.dp,
                            bottom = 0.dp
                        )
                    }
                ) {
                    item {
                        Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                    }

                    item(key = "filters") {
                        val chipAll = stringResource(R.string.category_all)
                        val chipLabels = mapOf(
                            ModuleCategory.OSS to stringResource(R.string.category_oss),
                            ModuleCategory.NON_FREE to stringResource(R.string.category_non_free),
                            ModuleCategory.META to stringResource(R.string.category_meta),
                            ModuleCategory.ARCHIVE to stringResource(R.string.category_archive),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.category == null,
                                onClick = { viewModel.dispatch(ModuleRepoUiAction.SetCategory(null)) },
                                label = { Text(chipAll) }
                            )
                            chipLabels.forEach { (category, label) ->
                                FilterChip(
                                    selected = uiState.category == category,
                                    onClick = {
                                        viewModel.dispatch(ModuleRepoUiAction.SetCategory(category))
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    if (uiState.selectedModuleIds.isNotEmpty()) {
                        item(key = "queue-bar") {
                            RepoQueueBar(
                                countText = selectedCountFmt.format(uiState.selectedModuleIds.size),
                                installText = queueInstallText,
                                onInstall = {
                                    scope.launch {
                                        installQueuedModules(uiState.selectedModuleIds.toList())
                                    }
                                },
                                onClear = {
                                    viewModel.dispatch(ModuleRepoUiAction.ClearSelection)
                                },
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    items(uiState.modules) { module ->
                        OnlineModuleItem(
                            module,
                            confirmDialog,
                            chooseDialog,
                            currentModuleForChooseDialog,
                            selected = module.moduleId in uiState.selectedModuleIds,
                            selectionMode = uiState.selectedModuleIds.isNotEmpty(),
                            onToggleSelect = {
                                viewModel.dispatch(ModuleRepoUiAction.ToggleSelect(module.moduleId))
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleRepoDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: ModuleRepoViewModel,
    uiState: ModuleRepoUiState,
    onManageRepos: () -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
        ) {
            CheckableDropdownMenuItem(
                checked = uiState.sortStargazerCountFirst,
                onCheckedChange = {
                    viewModel.dispatch(ModuleRepoUiAction.SetStarsFirst(it))
                },
                text = { Text(stringResource(R.string.module_sort_star_first)) },
                shapes = MenuDefaults.itemShape(
                    index = 0,
                    count = 2,
                ),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.module_repo_sources)) },
                onClick = onManageRepos,
            )
        }
    }
}

@Composable
private fun RepoQueueBar(
    countText: String,
    installText: String,
    onInstall: () -> Unit,
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
            TextButton(onClick = onInstall) {
                Text(installText)
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
private fun RepoManagerDialog(
    sources: List<RepoSource>,
    isWorking: Boolean,
    onToggle: (RepoSource) -> Unit,
    onDelete: (RepoSource) -> Unit,
    onAdd: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .popupBlur(),
        shape = MaterialTheme.shapes.extraLarge,
        color = popupContainerColor(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.module_repo_sources),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(top = 8.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = source.enabled,
                            onCheckedChange = { onToggle(source) },
                            enabled = !isWorking
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = source.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!source.builtIn) {
                            IconButton(
                                onClick = { onDelete(source) },
                                enabled = !isWorking
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Delete,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = Dp.Hairline)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.module_repo_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.module_repo_url)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onAdd(name.trim(), url.trim())
                        name = ""
                        url = ""
                    },
                    enabled = name.isNotBlank() && url.isNotBlank() && !isWorking
                ) {
                    Text(text = stringResource(R.string.module_repo_add))
                }
            }
        }
    }
}

@Composable
fun OnlineModuleItem(
    module: CatalogModule,
    confirmDialog: ConfirmDialogHandle,
    chooseDialog: DialogHandle,
    currentModuleForChooseDialog: MutableState<CatalogModule?>,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val context = LocalContext.current
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val observeDownload = koinInject<ObserveDownloadUseCase>()

    Surface(
        color =
            if (themeConfig.isEnableBlurExp)
                Color.Transparent
            else
                MaterialTheme.colorScheme.surfaceBright.copy(cardConfig.cardAlpha),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelect()
                    else navigator.push(Route.ModuleRepoDetail(module.moduleId))
                },
                onLongClick = onToggleSelect,
            )
            .renderBackgroundBlur(),
    ) {
        Column {
            if (module.bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = module.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale = ContentScale.Crop,
                    alpha = 0.9f
                )
            }
            if (selectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() }
                    )
                }
            }
            Column(
                modifier = Modifier
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = module.moduleName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            softWrap = true,
                            maxLines = 1
                        )
                        if (module.stargazerCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Star,
                                    contentDescription = "stars",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = module.stargazerCount.toString(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "$moduleVersion: ${module.latestRelease} (${module.latestVersionCode})",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                    )

                    Text(
                        text = "$moduleAuthor: ${module.authors}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = module.summary,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 文件夹名称和metamodule标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LabelText(
                    label = module.moduleId,
                    containerColor = MaterialTheme.colorScheme.primary,
                )
                if (module.metamodule) {
                    LabelText(
                        label = "META",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (module.installed) {
                    LabelText(
                        label = stringResource(R.string.installed),
                        containerColor = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (module.sourceName.isNotBlank()) {
                    LabelText(
                        label = module.sourceName,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = Dp.Hairline)

            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        onClick = {
                            navigator.push(Route.ModuleRepoDetail(module.moduleId))
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
                            imageVector = Icons.TwoTone.WebAsset,
                            contentDescription = null
                        )
                    }
                    Spacer(Modifier.width(10.dp))

                    if (module.latestAsset != null) {
                        val confirmInstallTitle =
                            stringResource(R.string.confirm_install_module_title, module.moduleName)
                        FilledTonalButton(
                            modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                            onClick = {
                                coroutineScope.launch {
                                    val result = confirmDialog.awaitConfirm(
                                        title = confirmInstallTitle,
                                        html = true,
                                        content = module.latestAsset.descriptionHTML
                                    )

                                    if (result == ConfirmResult.Canceled) return@launch

                                    val assets = module.latestAsset.assets
                                    if (assets.size <= 1) {
                                        assets.firstOrNull()?.let { asset ->
                                            downloadAssetAndInstall(
                                                context,
                                                permissionRequestInterface,
                                                module,
                                                asset,
                                                navigator,
                                                coroutineScope,
                                                enqueueDownload,
                                                observeDownload,
                                            )
                                        }
                                    } else {
                                        currentModuleForChooseDialog.value = module
                                        chooseDialog.show()
                                    }
                                }
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
                                imageVector = Icons.TwoTone.Download,
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

fun downloadAssetAndInstall(
    context: Context,
    permissionRequestInterface: PermissionRequestInterface,
    module: CatalogModule,
    asset: ModuleReleaseAsset,
    navigator: Navigator,
    coroutineScope: CoroutineScope,
    enqueueDownload: EnqueueDownloadUseCase,
    observeDownload: ObserveDownloadUseCase,
) {
    val downloadingText = context.getText(R.string.module_downloading).toString()
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            download(
                context = context,
                permissionRequestInterface = permissionRequestInterface,
                url = asset.downloadUrl,
                fileName = asset.name,
                enqueueDownload = enqueueDownload,
                observeDownload = observeDownload,
                onDownloaded = { uri ->
                    navigator.push(
                        Route.Flash.module(uri.toString())
                    )
                },
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloadingText.format(module.moduleName), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

@Composable
fun ChooseDialogContent(
    currentModuleForChooseDialog: MutableState<CatalogModule?>,
    enqueueDownload: EnqueueDownloadUseCase,
    observeDownload: ObserveDownloadUseCase,
    dismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val module = currentModuleForChooseDialog.value
    if (module == null || module.latestAsset == null) {
        dismiss()
        return
    }
    var selectedAsset by remember { mutableStateOf<ModuleReleaseAsset?>(null) }

    Dialog(
        onDismissRequest = { dismiss() }
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.assets_multiple_select_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(module.latestAsset.assets) { asset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape = RoundedCornerShape(24.dp))
                                .clickable { selectedAsset = asset }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAsset == asset,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Text(
                                    text = asset.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.assets_multiple_select_dialog_content_description,formatFileSize(asset.size), asset.downloadCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { dismiss() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (selectedAsset == null) {
                                Toast.makeText(context, R.string.assets_multiple_select_dialog_warning, Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            selectedAsset?.let { selected ->
                                dismiss()
                                downloadAssetAndInstall(
                                    context = context,
                                    permissionRequestInterface = permissionRequestInterface,
                                    module = module,
                                    asset = selected,
                                    navigator = navigator,
                                    coroutineScope = coroutineScope,
                                    enqueueDownload = enqueueDownload,
                                    observeDownload = observeDownload,
                                )
                            }
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}


// 下面全是预览相关了

fun initFakeRepoModuleForPreview(): CatalogModule {
    return CatalogModule(
        moduleId = "id",
        moduleName = "name",
        authors = "author",
        authorList = ArrayList<CatalogAuthor>().apply {
            add(
                CatalogAuthor(
                    name = "name",
                    link = "link"
                )
            )
        },
        summary = "I am a test module and i do nothing but show a very long description",
        metamodule = true,
        stargazerCount = 1,
        updatedAt = "updateAt",
        createdAt = "createAt",
        latestRelease = "latestRelease",
        latestReleaseTime = "latestReleaseTime",
        latestVersionCode = 1,
        latestAsset = ModuleRelease(
            name = "name",
            tagName = "tagName",
            publishedAt = "publishedAt",
            descriptionHTML = "descriptionHTML",
            assets = ArrayList<ModuleReleaseAsset>().apply {
                add(
                    ModuleReleaseAsset(
                        name = "name",
                        downloadUrl = "downloadUrl",
                        size = 0,
                        downloadCount = 0
                    )
                )
                add(
                    ModuleReleaseAsset(
                        name = "name2",
                        downloadUrl = "downloadUrl2",
                        size = 0,
                        downloadCount = 0
                    )
                )
            }
        ),
        installed = true,
        readme = "README",
        sourceUrl = "Source URL",
        releases = emptyList()
    )
}

@Preview(locale = "en")
@Composable
fun OnlineModuleItemPreview() {
    val currentModuleForChooseDialog = remember { mutableStateOf<CatalogModule?>(null) }

    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.ModuleRepo),
        LocalPermissionRequestInterface provides object : PermissionRequestInterface {
            override fun requestPermission(
                permission: String,
                callback: (Boolean) -> Unit,
                requestDescription: String
            ) {
            }

            override fun requestPermissions(
                permissions: Array<String>,
                callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
                requestDescription: Map<String, String>
            ) {
            }
        }
    ) {
        OnlineModuleItem(
            initFakeRepoModuleForPreview(),
            rememberConfirmDialog(),
            rememberCustomDialog { },
            currentModuleForChooseDialog,
        )
    }
}

@Preview(locale = "zh-rCN", showBackground = true)
@Composable
fun ChooseDialogPreview() {
    val currentModuleForChooseDialog =
        remember { mutableStateOf<CatalogModule?>(initFakeRepoModuleForPreview()) }

    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.ModuleRepo),
        LocalPermissionRequestInterface provides object : PermissionRequestInterface {
            override fun requestPermission(
                permission: String,
                callback: (Boolean) -> Unit,
                requestDescription: String
            ) {
            }

            override fun requestPermissions(
                permissions: Array<String>,
                callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
                requestDescription: Map<String, String>
            ) {
            }
        }
    ) {
        ChooseDialogContent(
            currentModuleForChooseDialog,
            koinInject<EnqueueDownloadUseCase>(),
            koinInject<ObserveDownloadUseCase>(),
        ) {}
    }
}
