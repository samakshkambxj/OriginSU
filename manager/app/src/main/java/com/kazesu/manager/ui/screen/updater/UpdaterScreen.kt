package com.kazesu.manager.ui.screen.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazesu.manager.BuildConfig
import com.kazesu.manager.R
import com.kazesu.manager.data.model.ManagerApkSource
import com.kazesu.manager.data.model.ManagerUpdateChannel
import com.kazesu.manager.data.model.ManagerUpdateInfo
import com.kazesu.manager.data.model.ManagerVariant
import com.kazesu.manager.ui.component.dialog.rememberConfirmDialog
import com.kazesu.manager.ui.component.material.ExpressiveScaffold
import com.kazesu.manager.ui.component.material.IconTile
import com.kazesu.manager.ui.component.material.SegmentedColumn
import com.kazesu.manager.ui.component.material.SegmentedDropdownItem
import com.kazesu.manager.ui.component.material.SegmentedListItem
import com.kazesu.manager.ui.component.material.TonalCard
import com.kazesu.manager.ui.component.material.TopBarBackButton
import com.kazesu.manager.ui.component.material.expressiveTopAppBarColors
import com.kazesu.manager.ui.navigation3.LocalNavigator
import com.kazesu.manager.ui.viewmodel.UpdaterUiAction
import com.kazesu.manager.ui.viewmodel.UpdaterUiState
import com.kazesu.manager.ui.viewmodel.UpdaterViewModel

@Composable
fun UpdaterScreen(
    initialChannel: String = ManagerUpdateChannel.STABLE.name,
    initialVariant: String = ManagerVariant.NORMAL.name,
) {
    val viewModel = viewModel<UpdaterViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(initialChannel, initialVariant) {
        runCatching { ManagerUpdateChannel.valueOf(initialChannel) }.getOrNull()?.let {
            viewModel.dispatch(UpdaterUiAction.SelectChannel(it))
        }
        runCatching { ManagerVariant.valueOf(initialVariant) }.getOrNull()?.let {
            viewModel.dispatch(UpdaterUiAction.SelectVariant(it))
        }
    }

    val channels = ManagerUpdateChannel.entries
    val variants = ManagerVariant.entries
    val channelLabels = listOf(
        stringResource(R.string.updater_channel_stable),
        stringResource(R.string.updater_channel_beta),
    )
    val variantLabels = listOf(
        stringResource(R.string.updater_variant_normal),
        stringResource(R.string.updater_variant_spoofed),
    )
    val variantDescriptions = listOf(
        stringResource(R.string.updater_variant_normal_summary),
        stringResource(R.string.updater_variant_spoofed_summary),
    )
    val channelIndex = channels.indexOf(uiState.channel).coerceAtLeast(0)
    val variantIndex = variants.indexOf(uiState.variant).coerceAtLeast(0)
    val changelogDialog = rememberConfirmDialog()
    val changelogTitle = stringResource(R.string.module_changelog)
    val changelogClose = stringResource(android.R.string.ok)

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.updater)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item {
                UpdaterStatusCard(
                    state = uiState,
                    channelLabel = channelLabels[channelIndex],
                    variantLabel = variantLabels[variantIndex],
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.updater_update_source),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.SystemUpdate,
                            title = stringResource(R.string.updater_channel),
                            items = channelLabels,
                            selectedIndex = channelIndex,
                            onItemSelected = { index ->
                                channels.getOrNull(index)?.let {
                                    viewModel.dispatch(UpdaterUiAction.SelectChannel(it))
                                }
                            },
                        )
                    }
                    item {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.updater_variant),
                            summary = variantDescriptions[variantIndex],
                            items = variantLabels,
                            selectedIndex = variantIndex,
                            onItemSelected = { index ->
                                variants.getOrNull(index)?.let {
                                    viewModel.dispatch(UpdaterUiAction.SelectVariant(it))
                                }
                            },
                        )
                    }
                }
            }

            uiState.update?.let { update ->
                item {
                    SegmentedColumn(
                        title = stringResource(R.string.updater_release),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        item {
                            InfoItem(
                                icon = Icons.Filled.SystemUpdate,
                                title = update.versionName.ifBlank { update.versionCode.toString() },
                                summary = stringResource(
                                    R.string.manager_update_details,
                                    update.versionName,
                                    update.versionCode,
                                    update.abi,
                                ),
                            )
                        }
                        item {
                            InfoItem(
                                icon = Icons.Filled.Cloud,
                                title = stringResource(R.string.updater_source),
                                summary = downloadSourceLabel(update.source),
                            )
                        }
                        item(visible = update.changelog.isNotBlank()) {
                            SegmentedListItem(
                                onClick = {
                                    changelogDialog.showConfirm(
                                        title = changelogTitle,
                                        content = update.changelog,
                                        markdown = true,
                                        confirm = changelogClose,
                                    )
                                },
                                headlineContent = { Text(changelogTitle) },
                                supportingContent = {
                                    Text(stringResource(R.string.updater_changelog_hint))
                                },
                                leadingContent = { IconTile(Icons.Filled.Info, changelogTitle) },
                            )
                        }
                    }
                }
            }

            uiState.downloadFailed?.let { error ->
                item {
                    TonalCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.updater_download_failed, error),
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }

            if (uiState.downloadFailed == null && uiState.downloadId != null && !uiState.downloadComplete) {
                item {
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        item {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.updater_downloading, uiState.downloadProgress))
                                },
                                supportingContent = {
                                    LinearProgressIndicator(
                                        progress = {
                                            (uiState.downloadProgress / 100f).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                    )
                                },
                                leadingContent = {
                                    IconTile(Icons.Filled.Download, stringResource(R.string.updater_downloading, uiState.downloadProgress))
                                },
                            )
                        }
                    }
                }
            }

            if (uiState.downloadComplete && uiState.resultUri != null) {
                item {
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        item {
                            InfoItem(
                                icon = Icons.Filled.CheckCircle,
                                title = stringResource(R.string.updater_download_complete),
                                summary = uiState.update?.fileName.orEmpty(),
                            )
                        }
                    }
                }
            }

            if (uiState.update != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        when {
                            uiState.downloadComplete && uiState.resultUri != null -> {
                                Button(
                                    onClick = { installApk(context, uiState.resultUri!!) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ButtonIcon(Icons.Filled.Download)
                                    Text(stringResource(R.string.updater_install))
                                }
                            }

                            uiState.downloadFailed == null && uiState.downloadId != null -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ButtonIcon(Icons.Filled.Download)
                                    Text(stringResource(R.string.updater_downloading, uiState.downloadProgress))
                                }
                            }

                            else -> {
                                Button(
                                    onClick = { viewModel.dispatch(UpdaterUiAction.Download) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ButtonIcon(Icons.Filled.Download)
                                    Text(stringResource(R.string.updater_download_install))
                                }
                            }
                        }
                    }
                }
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.updater_build_info),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        InfoItem(
                            icon = Icons.Filled.Info,
                            title = stringResource(
                                R.string.updater_current_version,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                        )
                    }
                    item {
                        InfoItem(
                            icon = Icons.Filled.Android,
                            title = stringResource(R.string.updater_build_package),
                            summary = BuildConfig.APPLICATION_ID,
                        )
                    }
                    item {
                        InfoItem(
                            icon = Icons.Filled.Apps,
                            title = stringResource(R.string.updater_build_type),
                            summary = buildTypeLabel(),
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.dispatch(UpdaterUiAction.Check) },
                        enabled = !uiState.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonIcon(Icons.Filled.Refresh)
                        Text(
                            stringResource(
                                if (uiState.checking) R.string.updater_checking else R.string.updater_check
                            )
                        )
                    }
                }
            }
            item {
                Spacer(
                    Modifier.height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }
    }
}

@Composable
private fun UpdaterStatusCard(
    state: UpdaterUiState,
    channelLabel: String,
    variantLabel: String,
    modifier: Modifier = Modifier,
) {
    val failed = state.checkFailed && !state.checking
    val update = state.update
    val title = when {
        state.checking -> stringResource(R.string.updater_status_checking)
        failed -> stringResource(R.string.updater_status_failed)
        update != null -> stringResource(
            R.string.updater_available,
            update.versionName.ifBlank { update.versionCode.toString() },
            update.versionCode,
        )
        state.checked -> stringResource(R.string.updater_up_to_date)
        else -> stringResource(R.string.updater_status_idle)
    }
    val subtitle = when {
        state.checking -> null
        failed -> stringResource(R.string.updater_check_failed)
        update != null -> stringResource(
            R.string.updater_status_available_desc,
            channelLabel,
            variantLabel,
        )
        state.checked -> stringResource(
            R.string.updater_status_latest_desc,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            channelLabel,
        )
        else -> null
    }
    val icon: ImageVector? = when {
        state.checking -> null
        failed -> Icons.Filled.Error
        update != null -> Icons.Filled.SystemUpdate
        state.checked -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Info
    }
    val contentColor = if (failed) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    TonalCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = contentColor,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.checking) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                } else if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(34.dp))
                }
            }
            Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun InfoItem(
    icon: ImageVector,
    title: String,
    summary: String? = null,
) {
    SegmentedListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { IconTile(icon, title) },
    )
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
}

@Composable
private fun downloadSourceLabel(source: ManagerApkSource): String {
    val host = Uri.parse(source.url).host ?: source.url
    return when (source) {
        is ManagerApkSource.DirectApk -> stringResource(R.string.updater_source_direct, host)
        is ManagerApkSource.NightlyArtifact -> stringResource(
            R.string.updater_source_nightly,
            source.preferredAbi,
            host,
        )
    }
}

@Composable
private fun buildTypeLabel(): String {
    val type = stringResource(
        if (BuildConfig.DEBUG) R.string.updater_build_debug else R.string.updater_build_release
    )
    return if (BuildConfig.IS_PR_BUILD) {
        stringResource(
            R.string.updater_build_type_value,
            type,
            stringResource(R.string.updater_build_pr),
        )
    } else {
        type
    }
}

private fun installApk(context: Context, resultUri: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(context, R.string.ci_update_install_permission, Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(resultUri), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }.onFailure {
        Toast.makeText(context, R.string.ci_update_install_failed, Toast.LENGTH_LONG).show()
    }
}
