package com.originsu.manager.ui.screen.main

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.KpmViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun KpmPage(bottomPadding: Dp) {
    val context = LocalContext.current
    val viewModel = koinViewModel<KpmViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current
    val confirmDialog = rememberConfirmDialog()

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    )

    val title = stringResource(R.string.kpm_title)
    val searchHint = stringResource(R.string.search_modules)
    val refreshDesc = stringResource(R.string.refresh)
    val emptyText = stringResource(R.string.kpm_empty)
    val versionText = if (uiState.version.isNotBlank()) {
        stringResource(R.string.kpm_version, uiState.version)
    } else {
        ""
    }
    val loadSuccess = stringResource(R.string.kpm_install_success)
    val unloadSuccess = stringResource(R.string.kpm_uninstall_success)
    val controlSuccess = stringResource(R.string.kpm_control_success)

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    var detailModuleId by remember { mutableStateOf<String?>(null) }

    val pickKpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching context.getString(R.string.kpm_install_failed, uri.toString())
                    val file = File(context.cacheDir, "kpm_upload_${System.currentTimeMillis()}.kpm")
                    file.outputStream().use { out -> input.copyTo(out) }
                    val out = viewModel.loadModule(file.absolutePath, null)
                    file.delete()
                    out.ifBlank { loadSuccess }
                }.getOrElse { e ->
                    context.getString(R.string.kpm_install_failed, e.message.orEmpty())
                }
            }
            snackBarHost.showSnackbar(outcome)
            viewModel.refresh()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SearchAppBar(
                title = title,
                searchText = uiState.search,
                onSearchTextChange = viewModel::setSearch,
                navigationContent = {
                    Icon(
                        imageVector = Icons.TwoTone.Memory,
                        contentDescription = null,
                    )
                },
                dropdownContent = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.TwoTone.Refresh, refreshDesc)
                    }
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = searchHint,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = bottomPadding + 5.dp),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    }
                    pickKpmLauncher.launch(intent)
                }
            ) {
                Icon(Icons.TwoTone.Add, contentDescription = title)
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(includeBottom = false),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (versionText.isNotBlank()) {
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (!uiState.isRefreshing && uiState.modules.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = bottomPadding + 80.dp,
                        top = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.modules, key = { it.id }) { module ->
                        KpmModuleCard(
                            name = module.name,
                            id = module.id,
                            version = module.version,
                            author = module.author,
                            description = module.description,
                            onDetail = {
                                detailModuleId = module.id
                                viewModel.loadDetail(module.id)
                            },
                            onControl = { viewModel.showControlDialog(module.id) },
                            onUnload = {
                                scope.launch {
                                    val confirmed = try {
                                        confirmDialog.awaitConfirm(
                                            title = context.getString(R.string.kpm_uninstall),
                                            content = context.getString(
                                                R.string.confirm_uninstall_content,
                                                module.id
                                            ),
                                            confirm = context.getString(R.string.uninstall),
                                            dismiss = context.getString(android.R.string.cancel)
                                        ) == com.originsu.manager.ui.component.ConfirmResult.Confirmed
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (!confirmed) return@launch
                                    val outcome = withContext(Dispatchers.IO) {
                                        runCatching {
                                            viewModel.unloadModule(module.id)
                                            unloadSuccess
                                        }.getOrElse { e ->
                                            context.getString(
                                                R.string.kpm_uninstall_failed,
                                                e.message.orEmpty()
                                            )
                                        }
                                    }
                                    snackBarHost.showSnackbar(outcome)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showControlDialog && uiState.selectedModuleId != null) {
        val moduleId = uiState.selectedModuleId.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::hideControlDialog,
            title = { Text(stringResource(R.string.kpm_control)) },
            text = {
                Column {
                    Text(
                        text = moduleId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.controlArgs,
                        onValueChange = viewModel::setControlArgs,
                        label = { Text(stringResource(R.string.kpm_args)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val code = withContext(Dispatchers.IO) {
                                viewModel.executeControl()
                            }
                            val message = if (code == 0) {
                                controlSuccess
                            } else {
                                context.getString(R.string.kpm_control_failed, code.toString())
                            }
                            snackBarHost.showSnackbar(message)
                            viewModel.refresh()
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideControlDialog) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    detailModuleId?.let { moduleId ->
        AlertDialog(
            onDismissRequest = { detailModuleId = null },
            title = { Text(moduleId) },
            text = {
                Text(
                    text = uiState.detail.ifBlank { moduleId },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { detailModuleId = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun KpmModuleCard(
    name: String,
    id: String,
    version: String,
    author: String,
    description: String,
    onDetail: () -> Unit,
    onControl: () -> Unit,
    onUnload: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (version.isNotBlank()) {
                Text(
                    text = stringResource(R.string.module_version, version),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (author.isNotBlank()) {
                Text(
                    text = stringResource(R.string.kpm_author, author),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDetail) {
                    Icon(Icons.TwoTone.Info, contentDescription = null)
                    Text(stringResource(R.string.kpm_details))
                }
                OutlinedButton(onClick = onControl) {
                    Icon(Icons.TwoTone.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.kpm_control))
                }
                OutlinedButton(onClick = onUnload) {
                    Icon(Icons.TwoTone.Delete, contentDescription = null)
                    Text(stringResource(R.string.kpm_uninstall))
                }
            }
        }
    }
}
