package com.originsu.manager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.BuildConfig
import com.originsu.manager.R
import com.originsu.manager.domain.model.ManagerApkSource
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerUpdateInfo
import com.originsu.manager.domain.model.ManagerVariant
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.theme.renderBackgroundBlur
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.UpdaterUiAction
import com.originsu.manager.ui.viewmodel.UpdaterViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdaterScreen(
    initialChannel: String = ManagerUpdateChannel.STABLE.name,
    initialVariant: String = ManagerVariant.NORMAL.name,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<UpdaterViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    LaunchedEffect(initialChannel, initialVariant) {
        runCatching { ManagerUpdateChannel.valueOf(initialChannel) }
            .getOrNull()?.let { viewModel.dispatch(UpdaterUiAction.SelectChannel(it)) }
        runCatching { ManagerVariant.valueOf(initialVariant) }
            .getOrNull()?.let { viewModel.dispatch(UpdaterUiAction.SelectVariant(it)) }
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

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.updater)) },
                navigationIcon = {
                    val navigator = LocalNavigator.current
                    AppBackButton(onClick = { navigator.pop() })
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                        },
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                        },
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { paddingValues ->
        val pendingUpdate = uiState.update
        val downloadError = uiState.downloadFailed
        val completedUri = uiState.resultUri
        val changelogDialog = rememberConfirmDialog()
        val changelogTitle = stringResource(R.string.module_changelog)
        val changelogClose = stringResource(android.R.string.ok)
        val channelLabel = channelLabels[channels.indexOf(uiState.channel).coerceAtLeast(0)]
        val variantLabel = variantLabels[variants.indexOf(uiState.variant).coerceAtLeast(0)]

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = paddingValues.calculateBottomPadding() + 5.dp,
            ),
        ) {
            item {
                UpdaterStatusCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    checking = uiState.checking,
                    checked = uiState.checked,
                    checkFailed = uiState.checkFailed,
                    update = pendingUpdate,
                    channelLabel = channelLabel,
                    variantLabel = variantLabel,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                SegmentedColumn(title = stringResource(R.string.updater_update_source)) {
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Update,
                            title = stringResource(R.string.updater_channel),
                            items = channelLabels,
                            selectedIndex = channels.indexOf(uiState.channel),
                            onSelectedIndexChange = { index ->
                                channels.getOrNull(index)?.let {
                                    viewModel.dispatch(UpdaterUiAction.SelectChannel(it))
                                }
                            },
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Shield,
                            title = stringResource(R.string.updater_variant),
                            items = variantLabels,
                            itemDescriptions = variantDescriptions,
                            selectedIndex = variants.indexOf(uiState.variant),
                            onSelectedIndexChange = { index ->
                                variants.getOrNull(index)?.let {
                                    viewModel.dispatch(UpdaterUiAction.SelectVariant(it))
                                }
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (pendingUpdate != null) {
                item {
                    SegmentedColumn(title = stringResource(R.string.updater_release)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Update,
                                title = pendingUpdate.versionName.ifBlank {
                                    pendingUpdate.versionCode.toString()
                                },
                                description = stringResource(
                                    R.string.manager_update_details,
                                    pendingUpdate.versionName,
                                    pendingUpdate.versionCode,
                                    pendingUpdate.abi,
                                ),
                            ) {}
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Cloud,
                                title = stringResource(R.string.updater_source),
                                description = downloadSourceLabel(pendingUpdate.source),
                            ) {}
                        }
                        if (pendingUpdate.changelog.isNotBlank()) {
                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Info,
                                    title = changelogTitle,
                                    description = stringResource(R.string.updater_changelog_hint),
                                    onClick = {
                                        changelogDialog.showConfirm(
                                            title = changelogTitle,
                                            content = pendingUpdate.changelog,
                                            markdown = true,
                                            confirm = changelogClose,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (downloadError != null) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.updater_download_failed, downloadError),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (downloadError == null && uiState.downloadId != null && !uiState.downloadComplete) {
                item {
                    SegmentedColumn {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Download,
                                title = stringResource(
                                    R.string.updater_downloading,
                                    uiState.downloadProgress,
                                ),
                                descriptionColumnContent = {
                                    LinearProgressIndicator(
                                        progress = {
                                            (uiState.downloadProgress / 100f).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                    )
                                },
                            ) {}
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (uiState.downloadComplete && completedUri != null && pendingUpdate != null) {
                item {
                    SegmentedColumn {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.CheckCircle,
                                title = stringResource(R.string.updater_download_complete),
                                description = pendingUpdate.fileName,
                            ) {}
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (pendingUpdate != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        when {
                            uiState.downloadComplete && completedUri != null -> {
                                Button(
                                    onClick = { installApk(context, completedUri) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Download,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.updater_install))
                                }
                            }

                            downloadError == null && uiState.downloadId != null -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Download,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.updater_downloading, uiState.downloadProgress))
                                }
                            }

                            else -> {
                                Button(
                                    onClick = { viewModel.dispatch(UpdaterUiAction.Download) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Download,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.updater_download_install))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.updater_build_info)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Info,
                            title = stringResource(
                                R.string.updater_current_version,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                        ) {}
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Android,
                            title = stringResource(R.string.updater_build_package),
                            description = BuildConfig.APPLICATION_ID,
                        ) {}
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Apps,
                            title = stringResource(R.string.updater_build_type),
                            description = buildTypeLabel(),
                        ) {}
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.dispatch(UpdaterUiAction.Check) },
                        enabled = !uiState.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.checking) {
                                stringResource(R.string.updater_checking)
                            } else {
                                stringResource(R.string.updater_check)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdaterStatusCard(
    checking: Boolean,
    checked: Boolean,
    checkFailed: Boolean,
    update: ManagerUpdateInfo?,
    channelLabel: String,
    variantLabel: String,
    modifier: Modifier = Modifier,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val failed = checkFailed && !checking
    val containerColor = if (failed) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (failed) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val title = when {
        checking -> stringResource(R.string.updater_status_checking)
        failed -> stringResource(R.string.updater_status_failed)
        update != null -> stringResource(
            R.string.updater_available,
            update.versionName.ifBlank { update.versionCode.toString() },
            update.versionCode,
        )
        checked -> stringResource(R.string.updater_up_to_date)
        else -> stringResource(R.string.updater_status_idle)
    }
    val subtitle = when {
        checking -> null
        failed -> stringResource(R.string.updater_check_failed)
        update != null -> stringResource(
            R.string.updater_status_available_desc,
            channelLabel,
            variantLabel,
        )
        checked -> stringResource(
            R.string.updater_status_latest_desc,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            channelLabel,
        )
        else -> null
    }
    val icon: ImageVector? = when {
        checking -> null
        failed -> Icons.TwoTone.Error
        update != null -> Icons.TwoTone.Update
        checked -> Icons.TwoTone.CheckCircle
        else -> Icons.TwoTone.Info
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .renderBackgroundBlur(),
        color = if (themeConfig.isEnableBlurExp) Color.Transparent else containerColor.copy(cardConfig.cardAlpha),
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
                if (checking) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
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
    val type = if (BuildConfig.DEBUG) {
        stringResource(R.string.updater_build_debug)
    } else {
        stringResource(R.string.updater_build_release)
    }
    return if (BuildConfig.IS_PR_BUILD) {
        stringResource(R.string.updater_build_type_value, type, stringResource(R.string.updater_build_pr))
    } else {
        type
    }
}

private fun installApk(context: android.content.Context, resultUri: String) {
    val path = Uri.parse(resultUri).path ?: return
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        file,
    )
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )
}
