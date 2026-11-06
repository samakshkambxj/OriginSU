package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.model.SuiApp
import com.originsu.manager.data.model.SuiMode
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SearchNoMatch
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.SuiPermissionsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** The permission words Sui itself uses, in the order they are offered. */
private val MODE_OPTIONS = listOf(
    SuiMode.DEFAULT,
    SuiMode.ROOT,
    SuiMode.SHELL,
    SuiMode.DENY,
    SuiMode.HIDDEN,
)

@Composable
private fun modeLabel(mode: SuiMode): String = stringResource(
    when (mode) {
        SuiMode.DEFAULT -> R.string.sui_mode_default
        SuiMode.ROOT -> R.string.sui_mode_root
        SuiMode.SHELL -> R.string.sui_mode_shell
        SuiMode.DENY -> R.string.sui_mode_deny
        SuiMode.HIDDEN -> R.string.sui_mode_hidden
    },
)

@Composable
private fun modeLabels(): List<String> = MODE_OPTIONS.map { modeLabel(it) }

private fun modeIndexOf(flags: Int): Int =
    MODE_OPTIONS.indexOfFirst { it.flags == (flags and SuiApp.MODE_MASK) }.coerceAtLeast(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuiPermissionsScreen(viewModel: SuiPermissionsViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = stringResource(R.string.sui_manage_permissions)
    val labels = modeLabels()

    Scaffold(
        topBar = {
            SearchAppBar(
                title = title,
                searchText = uiState.search,
                onSearchTextChange = viewModel::search,
                onBackClick = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_apps),
            )
        },
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
    ) { innerPadding ->
        val apps = uiState.visible
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (uiState.defaultMode != null) {
                item(key = "default") {
                    SegmentedColumn(
                        title = title,
                        content = {
                            item {
                                SettingsChooseWidget(
                                    title = stringResource(R.string.sui_default_permission),
                                    description = stringResource(
                                        R.string.sui_default_permission_summary,
                                    ),
                                    items = labels,
                                    selectedIndex = modeIndexOf(uiState.defaultMode ?: 0),
                                    onSelectedIndexChange = { index ->
                                        viewModel.setDefaultMode(MODE_OPTIONS[index].flags)
                                    },
                                )
                            }
                        },
                    )
                }
            }
            if (!uiState.loading && apps.isEmpty()) {
                item(key = "empty") {
                    SearchNoMatch()
                }
            }
            if (apps.isNotEmpty()) {
                item(key = "apps-header") {
                    Text(
                        text = stringResource(R.string.sui_apps_header),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp),
                    )
                }
            }
            items(count = apps.size, key = { apps[it].uid }) { index ->
                val app = apps[index]
                SettingsChooseWidget(
                    title = app.label,
                    description = app.packageName,
                    items = labels,
                    selectedIndex = modeIndexOf(app.mode),
                    onSelectedIndexChange = { index ->
                        viewModel.setAppMode(app.uid, MODE_OPTIONS[index].flags)
                    },
                )
            }
        }
    }
}
