package com.originsu.manager.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Download
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.BuildConfig
import com.originsu.manager.R
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerVariant
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsChooseWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = paddingValues.calculateBottomPadding() + 5.dp,
            ),
        ) {
            item {
                SegmentedColumn {
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
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            when {
                uiState.checking -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                }

                uiState.checkFailed -> {
                    item {
                        WarningCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            message = stringResource(R.string.updater_check_failed),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                uiState.checked && pendingUpdate == null -> {
                    item {
                        WarningCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            message = stringResource(R.string.updater_up_to_date),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                pendingUpdate != null -> {
                    item {
                        SegmentedColumn {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.CheckCircle,
                                    title = stringResource(
                                        R.string.updater_available,
                                        pendingUpdate.versionName,
                                        pendingUpdate.versionCode,
                                    ),
                                    description = stringResource(
                                        R.string.manager_update_details,
                                        pendingUpdate.versionName,
                                        pendingUpdate.versionCode,
                                        pendingUpdate.abi,
                                    ) + pendingUpdate.changelog.takeIf { it.isNotBlank() }
                                        ?.let { "\n\n$it" }.orEmpty(),
                                ) {}
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (downloadError != null) {
                        item {
                            WarningCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                message = stringResource(
                                    R.string.updater_download_failed,
                                    downloadError,
                                ),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (uiState.downloadId != null && !uiState.downloadComplete) {
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
                                                progress = { uiState.downloadProgress / 100f },
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

                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            if (uiState.downloadComplete && completedUri != null) {
                                Button(
                                    onClick = {
                                        installApk(context, completedUri)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Download,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.updater_install))
                                }
                            } else if (uiState.downloadId == null || downloadError != null) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
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
