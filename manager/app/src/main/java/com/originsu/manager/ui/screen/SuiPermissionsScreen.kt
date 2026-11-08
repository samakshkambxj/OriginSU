package com.originsu.manager.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.originsu.manager.R
import com.originsu.manager.data.model.SuiApp
import com.originsu.manager.data.model.SuiMode
import com.originsu.manager.ui.component.SearchAppBar
import com.originsu.manager.ui.component.SearchNoMatch
import com.originsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import com.originsu.manager.ui.component.settings.BentoCaption
import com.originsu.manager.ui.component.settings.BentoCard
import com.originsu.manager.ui.component.settings.BentoChooseRow
import com.originsu.manager.ui.component.settings.BentoGrid
import com.originsu.manager.ui.component.settings.BentoHeader
import com.originsu.manager.ui.component.settings.BentoStatusChip
import com.originsu.manager.ui.component.settings.SettingsChooseDialog
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.blurSource
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

private enum class SuiFilter {
    ALL,
    SHIZUKU,
    CONFIGURED,
    SYSTEM,
}

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuiPermissionsScreen(viewModel: SuiPermissionsViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = stringResource(R.string.sui_manage_permissions)
    val labels = modeLabels()
    var filter by remember { mutableStateOf(SuiFilter.ALL) }
    var selectedApp by remember { mutableStateOf<SuiApp?>(null) }

    val apps = uiState.visible.filter { app ->
        when (filter) {
            SuiFilter.ALL -> true
            SuiFilter.SHIZUKU -> app.isShizukuApp
            SuiFilter.CONFIGURED -> app.suiMode != SuiMode.DEFAULT
            SuiFilter.SYSTEM -> app.isSystem
        }
    }

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
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        BentoGrid(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (uiState.defaultMode != null) {
                item(key = "default", span = StaggeredGridItemSpan.FullLine) {
                    BentoCard {
                        BentoHeader(
                            icon = Icons.TwoTone.WaterDrop,
                            title = title,
                        )
                        Spacer(Modifier.height(4.dp))
                        BentoCaption(
                            stringResource(R.string.sui_manage_permissions_summary),
                        )
                        Spacer(Modifier.height(8.dp))
                        BentoChooseRow(
                            icon = Icons.TwoTone.Policy,
                            title = stringResource(R.string.sui_default_permission),
                            subtitle = labels[modeIndexOf(uiState.defaultMode ?: 0)],
                            items = labels,
                            selectedIndex = modeIndexOf(uiState.defaultMode ?: 0),
                            onSelectedIndexChange = { index ->
                                viewModel.setDefaultMode(MODE_OPTIONS[index].flags)
                            },
                        )
                        BentoCaption(stringResource(R.string.sui_default_permission_summary))
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = filter == SuiFilter.ALL,
                                onClick = { filter = SuiFilter.ALL },
                                label = { Text(stringResource(R.string.sui_filter_all)) },
                            )
                            FilterChip(
                                selected = filter == SuiFilter.SHIZUKU,
                                onClick = { filter = SuiFilter.SHIZUKU },
                                label = { Text(stringResource(R.string.sui_filter_shizuku)) },
                            )
                            FilterChip(
                                selected = filter == SuiFilter.CONFIGURED,
                                onClick = { filter = SuiFilter.CONFIGURED },
                                label = { Text(stringResource(R.string.sui_filter_configured)) },
                            )
                            FilterChip(
                                selected = filter == SuiFilter.SYSTEM,
                                onClick = { filter = SuiFilter.SYSTEM },
                                label = { Text(stringResource(R.string.sui_filter_system)) },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        BentoCaption(
                            stringResource(R.string.sui_apps_count, apps.size),
                        )
                    }
                }
            }

            if (!uiState.loading && apps.isEmpty()) {
                item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    SearchNoMatch()
                }
            }

            items(count = apps.size, key = { apps[it].packageName }) { index ->
                val app = apps[index]
                SuiAppTile(
                    app = app,
                    onClick = { selectedApp = app },
                )
            }
        }
    }

    val app = selectedApp
    if (app != null) {
        SettingsChooseDialog(
            show = true,
            title = app.label,
            items = labels,
            selectedIndex = modeIndexOf(app.mode),
            onDismiss = { selectedApp = null },
            onSelectedIndexChange = { index ->
                viewModel.setAppMode(app.uid, MODE_OPTIONS[index].flags)
                selectedApp = null
            },
        )
    }
}

@Composable
private fun SuiAppTile(
    app: SuiApp,
    onClick: () -> Unit,
) {
    val mode = app.suiMode
    BentoCard(
        modifier = Modifier.heightIn(min = 112.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SuiAppIcon(app, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BentoStatusChip(
            text = modeLabel(mode),
            active = mode != SuiMode.DEFAULT && mode != SuiMode.DENY,
            isWarning = mode == SuiMode.DENY,
        )
        if (app.requestsShizuku) {
            Spacer(Modifier.height(6.dp))
            BentoCaption(stringResource(R.string.sui_requests_shizuku))
        }
    }
}

@Composable
private fun SuiAppIcon(app: SuiApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(app.packageName) {
        runCatching {
            app.applicationInfo?.loadIcon(context.packageManager)
        }.getOrNull()
    }
    val painter: Painter? = drawable?.let { rememberDrawablePainter(it) }

    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.TwoTone.Android,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
