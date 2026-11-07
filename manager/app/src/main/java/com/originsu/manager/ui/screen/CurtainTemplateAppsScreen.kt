package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.curtain.CurtainLoadError
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.PackageIcon
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SearchNoMatch
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.lazySegmentColumn
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.CurtainTemplateAppsEvent
import com.originsu.manager.ui.viewmodel.CurtainTemplateAppsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Choose which apps a reusable template hides or shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtainTemplateAppsScreen(templateName: String) {
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val viewModel = koinViewModel<CurtainTemplateAppsViewModel>(
        parameters = { parametersOf(templateName) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    )
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val saveFailed = stringResource(R.string.origin_curtain_error_write)
    val deleteTitle = stringResource(R.string.origin_curtain_template_delete)
    val deleteMessage = stringResource(R.string.origin_curtain_template_delete_confirm, templateName)
    val confirm = stringResource(R.string.confirm)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                CurtainTemplateAppsEvent.Saved,
                CurtainTemplateAppsEvent.Deleted -> navigator.pop()
                CurtainTemplateAppsEvent.SaveFailed ->
                    snackBarHost.showReplacingSnackbar(saveFailed)
            }
        }
    }

    val template = uiState.template
    val appCountTitle = stringResource(
        R.string.origin_curtain_template_app_count,
        uiState.selection.size,
    )
    val errorText = when (uiState.error) {
        CurtainLoadError.NO_ROOT -> stringResource(R.string.origin_curtain_error_no_root)
        CurtainLoadError.NOT_INITIALIZED ->
            stringResource(R.string.origin_curtain_error_not_initialized)
        CurtainLoadError.UNREADABLE -> stringResource(R.string.origin_curtain_error_unreadable)
        CurtainLoadError.WRITE_FAILED -> stringResource(R.string.origin_curtain_error_write)
        null -> null
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = template?.name ?: templateName,
                searchText = uiState.search,
                onSearchTextChange = viewModel::search,
                onBackClick = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.origin_curtain_search_apps),
                dropdownContent = {
                    IconButton(
                        onClick = { viewModel.saveApps() },
                        enabled = !uiState.busy && template != null,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Check,
                            contentDescription = stringResource(R.string.origin_curtain_picker_apply),
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val result = confirmDialog.awaitConfirm(
                                    title = deleteTitle,
                                    content = deleteMessage,
                                    confirm = confirm,
                                )
                                if (result == ConfirmResult.Confirmed) {
                                    viewModel.delete()
                                }
                            }
                        },
                        enabled = !uiState.busy && template != null,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Delete,
                            contentDescription = stringResource(
                                R.string.origin_curtain_template_delete,
                            ),
                        )
                    }
                },
            )
        },
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
    ) { innerPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (errorText != null) {
                item(key = "error") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_templates)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.origin_curtain_error_title),
                                description = errorText,
                                isError = true,
                            )
                        }
                    }
                }
            }

            if (template != null) {
                item(key = "mode") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_mode)) {
                        item {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Lock,
                                title = stringResource(R.string.origin_curtain_template_mode),
                                description = stringResource(R.string.origin_curtain_template_mode_summary),
                                items = listOf(
                                    stringResource(R.string.origin_curtain_mode_blacklist),
                                    stringResource(R.string.origin_curtain_mode_whitelist),
                                ),
                                selectedIndex = if (template.isWhitelist) 1 else 0,
                                enabled = !uiState.busy,
                                onSelectedIndexChange = { index ->
                                    viewModel.setMode(index == 1)
                                },
                            )
                        }
                    }
                }
            }

            if (uiState.visibleApps.isEmpty()) {
                item(key = "no-apps") { SearchNoMatch() }
            } else {
                lazySegmentColumn(
                    items = uiState.visibleApps,
                    title = appCountTitle,
                    key = { _, app -> app.packageName },
                ) { _, app ->
                    SettingsBaseWidget(
                        title = app.label,
                        description = app.packageName,
                        leadingContent = {
                            PackageIcon(
                                packageName = app.packageName,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        onClick = {
                            viewModel.toggle(
                                app.packageName,
                                app.packageName !in uiState.selection,
                            )
                        },
                    ) { _ ->
                        Checkbox(
                            checked = app.packageName in uiState.selection,
                            enabled = !uiState.busy,
                            onCheckedChange = { checked ->
                                viewModel.toggle(app.packageName, checked)
                            },
                        )
                    }
                }
            }
        }
    }
}
