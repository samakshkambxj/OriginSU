package com.originsu.manager.ui.screen.main

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.domain.model.HomeDashboardState
import com.originsu.manager.domain.model.HomeSystemInfo
import com.originsu.manager.domain.model.KernelStatus
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerUpdateInfo
import com.originsu.manager.magica.MagicaService
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.OriginTuneCard
import com.originsu.manager.ui.component.rememberLoadingDialog
import com.originsu.manager.ui.component.miuix.WarningCard
import com.originsu.manager.ui.component.rebootlistpopup.RebootListPopupMiuix
import com.originsu.manager.ui.miuix.LocalEnableBlur
import com.originsu.manager.ui.miuix.OriginMiuixTheme
import com.originsu.manager.ui.miuix.homeCardColors
import com.originsu.manager.ui.miuix.miuixTileColor
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.util.BlurredBar
import com.originsu.manager.ui.util.LocalHandlePageChange
import com.originsu.manager.ui.util.miuixHomeTileBlur
import com.originsu.manager.ui.util.rememberBlurBackdrop
import com.originsu.manager.ui.viewmodel.HomeUiAction
import com.originsu.manager.ui.viewmodel.HomeUiEvent
import com.originsu.manager.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds

private data class HomeMiuixActions(
    val onInstallClick: () -> Unit,
    val onRefreshClick: () -> Unit,
    val onSuperuserClick: () -> Unit,
    val onModuleClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onJailbreakClick: () -> Unit,
    val onRebootClick: (String) -> Unit,
    val onUpdateClick: (ManagerUpdateInfo) -> Unit,
    val onOriginTuneClick: () -> Unit,
)

@Composable
fun HomePageMiuix(
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val viewModel = koinViewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()
    val handlePageChange = LocalHandlePageChange.current

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeUiAction.AwaitInitialData)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.Error -> if (event.message.isNotBlank()) {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (!uiState.isInitialDataLoaded) return

    val actions = HomeMiuixActions(
        onInstallClick = { navigator.push(Route.Install(preselectedKernelUri = null)) },
        onRefreshClick = { viewModel.dispatch(HomeUiAction.Refresh(showIndicator = true)) },
        onSuperuserClick = { handlePageChange(1) },
        onModuleClick = { handlePageChange(2) },
        onOpenUrl = uriHandler::openUri,
        onJailbreakClick = {
            loadingDialog.showLoading()
            context.startService(Intent(context, MagicaService::class.java))
            // Manager will be force-stopped and restarted by late-load on success.
            // If that doesn't happen within timeout, jailbreak likely failed.
            scope.launch(Dispatchers.IO) {
                delay(30_000.milliseconds)
                withContext(Dispatchers.Main) {
                    loadingDialog.hide()
                    Toast.makeText(
                        context,
                        R.string.jailbreak_timeout,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        },
        onRebootClick = { reason -> viewModel.dispatch(HomeUiAction.Reboot(reason)) },
        onUpdateClick = { update ->
            navigator.push(
                Route.Updater(
                    channel = update.channel.name,
                    variant = update.variant.name,
                )
            )
        },
        onOriginTuneClick = { navigator.push(Route.KernelTuning) },
    )

    OriginMiuixTheme {
        HomePagerMiuix(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomPadding,
        )
    }
}

@Composable
private fun HomePagerMiuix(
    state: HomeDashboardState,
    actions: HomeMiuixActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
    // Transparent when a custom background is loaded so it shows through
    // (the image is painted behind at the nav-entry level, like other screens).
    val themeConfig = koinInject<com.originsu.manager.ui.theme.ThemeConfig>()
    Scaffold(
        topBar = {
            TopBar(
                status = state.systemStatus,
                onRebootClick = actions.onRebootClick,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        containerColor = if (themeConfig.backgroundImageLoaded) Color.Transparent
        else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val status = state.systemStatus
                        if (status.isManager && !status.isFullFeatured) {
                            WarningCard(
                                message = if (status.lkmMode == true)
                                    stringResource(R.string.require_kernel_version)
                                else
                                    stringResource(R.string.require_kernel_version_gki),
                                onClick = actions.onInstallClick,
                            )
                        }
                        if (status.ksuVersion != null && !status.isRootAvailable) {
                            WarningCard(
                                stringResource(id = R.string.grant_root_failed),
                                onClick = actions.onRefreshClick,
                            )
                        }
                        StatusCard(
                            state = state,
                            actions = actions,
                        )
                        if (status.isRootAvailable) {
                            OriginTuneCard(onClick = actions.onOriginTuneClick)
                        } else {
                            OriginTuneCard(
                                locked = true,
                                onClick = actions.onInstallClick,
                            )
                        }
                        if (state.showUpdateManagerCard) {
                            UpdateCard(
                                stableUpdate = state.stableManagerUpdate,
                                betaUpdate = state.betaManagerUpdate,
                                onUpdateClick = actions.onUpdateClick,
                            )
                        }
                        if (state.isExtendedDataLoaded) {
                            InfoCard(systemInfo = state.systemInfo)
                        }
                        DonateCard(onOpenUrl = actions.onOpenUrl)
                        LearnMoreCard(onOpenUrl = actions.onOpenUrl)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(
    stableUpdate: ManagerUpdateInfo?,
    betaUpdate: ManagerUpdateInfo?,
    onUpdateClick: (ManagerUpdateInfo) -> Unit,
) {
    val update = stableUpdate ?: betaUpdate ?: return
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)
    val message = if (update.channel == ManagerUpdateChannel.STABLE) {
        stringResource(id = R.string.new_version_available, update.versionCode)
    } else {
        stringResource(id = R.string.beta_version_available, update.versionCode)
    }
    val updateDialog = rememberConfirmDialog(onConfirm = { onUpdateClick(update) })

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        WarningCard(
            message = message,
            color = colorScheme.outline,
            onClick = {
                if (update.changelog.isEmpty()) {
                    onUpdateClick(update)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = update.changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    status: KernelStatus,
    onRebootClick: (String) -> Unit,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.app_name),
            titlePadding = 12.dp,
            actionIconPadding = 12.dp,
            actions = {
                RebootListPopupMiuix(
                    status = status,
                    onReboot = onRebootClick,
                )
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun StatusCard(
    state: HomeDashboardState,
    actions: HomeMiuixActions,
) {
    val status = state.systemStatus
    val systemInfo = state.systemInfo
    Column {
        when {
            status.ksuVersion != null -> {
                val workingState = buildString {
                    if (status.isSafeMode) {
                        append(" [${stringResource(id = R.string.safe_mode)}]")
                    }
                    if (status.isLateLoadMode) {
                        append(" [${stringResource(id = R.string.jailbreak_mode)}]")
                    }
                }
                val workingMode = when (status.lkmMode) {
                    null -> ""
                    true -> " <LKM>"
                    else -> " <GKI>"
                }
                val workingText = "${stringResource(id = R.string.home_working)}$workingMode$workingState"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val statusColor = when {
                        isDynamicColor -> colorScheme.secondaryContainer
                        isInDarkTheme() -> Color(0xFF1A3825)
                        else -> Color(0xFFDFFAE4)
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .miuixHomeTileBlur(statusColor),
                        colors = CardDefaults.defaultColors(
                            color = miuixTileColor(statusColor)
                        ),
                        onClick = {
                            if (!status.isLateLoadMode) {
                                actions.onInstallClick()
                            }
                        },
                        showIndication = !status.isLateLoadMode,
                        pressFeedbackType = PressFeedbackType.Tilt
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(38.dp, 45.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    modifier = Modifier.size(170.dp),
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    tint = if (isDynamicColor) {
                                        colorScheme.primary.copy(alpha = 0.8f)
                                    } else {
                                        Color(0xFF36D167)
                                    },
                                    contentDescription = null
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(all = 16.dp)
                            ) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = workingText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = stringResource(R.string.home_working_version, status.ksuVersion),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Card(
                            colors = homeCardColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .miuixHomeTileBlur(MiuixTheme.colorScheme.surfaceContainerHigh),
                            insideMargin = PaddingValues(16.dp),
                            onClick = { actions.onSuperuserClick() },
                            showIndication = true,
                            pressFeedbackType = PressFeedbackType.Tilt
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = stringResource(R.string.superuser),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = systemInfo.superuserCount.toString(),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = homeCardColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .miuixHomeTileBlur(MiuixTheme.colorScheme.surfaceContainerHigh),
                            insideMargin = PaddingValues(16.dp),
                            onClick = { actions.onModuleClick() },
                            showIndication = true,
                            pressFeedbackType = PressFeedbackType.Tilt
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = stringResource(R.string.module),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = systemInfo.moduleCount.toString(),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            status.kernelVersion.isGKI() -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        colors = homeCardColors(),
                        modifier = Modifier
                            .weight(1f)
                            .miuixHomeTileBlur(MiuixTheme.colorScheme.surfaceContainerHigh),
                        onClick = {
                            if (!status.isLateLoadMode) {
                                actions.onInstallClick()
                            }
                        },
                        showIndication = !status.isLateLoadMode,
                        pressFeedbackType = PressFeedbackType.Sink
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.home_not_installed),
                            summary = stringResource(R.string.home_click_to_install),
                            startAction = {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    stringResource(R.string.home_not_installed),
                                    modifier = Modifier.padding(end = 16.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            endActions = {
                                if (status.isSELinuxPermissive) {
                                    TextButton(
                                        text = stringResource(R.string.home_jailbreak),
                                        onClick = actions.onJailbreakClick,
                                        colors = ButtonDefaults.textButtonColorsPrimary()
                                    )
                                }
                            }
                        )
                    }
                }
            }

            else -> {
                Card(
                    colors = homeCardColors(),
                    modifier = Modifier.miuixHomeTileBlur(MiuixTheme.colorScheme.surfaceContainerHigh),
                    onClick = {
                        if (!status.isLateLoadMode) {
                            actions.onInstallClick()
                        }
                    },
                    showIndication = !status.isLateLoadMode,
                    pressFeedbackType = PressFeedbackType.Sink
                ) {
                    BasicComponent(
                        title = stringResource(R.string.home_unsupported),
                        summary = stringResource(R.string.home_unsupported_reason),
                        startAction = {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                stringResource(R.string.home_unsupported),
                                modifier = Modifier.padding(end = 16.dp),
                                tint = colorScheme.onBackground,
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LearnMoreCard(
    onOpenUrl: (String) -> Unit,
) {
    val url = stringResource(R.string.home_learn_kernelsu_url)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .miuixHomeTileBlur(MiuixTheme.colorScheme.surfaceContainerHigh),
        colors = homeCardColors(),
    ) {
        BasicComponent(
            title = stringResource(R.string.home_learn_kernelsu),
            summary = stringResource(R.string.home_click_to_learn_kernelsu),
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Link,
                    tint = colorScheme.onSurface,
                    contentDescription = null
                )
            },
            onClick = { onOpenUrl(url) }
        )
    }
}

@Composable
private fun DonateCard(
    onOpenUrl: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .miuixHomeTileBlur(MiuixTheme.colorScheme.surfaceContainerHigh),
        colors = homeCardColors(),
    ) {
        BasicComponent(
            title = stringResource(R.string.home_support_title),
            summary = stringResource(R.string.home_support_content),
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Link,
                    tint = colorScheme.onSurface,
                    contentDescription = null
                )
            },
            onClick = { onOpenUrl("https://patreon.com/weishu") },
            insideMargin = PaddingValues(18.dp)
        )
    }
}

@Composable
private fun InfoCard(
    systemInfo: HomeSystemInfo,
) {
    @Composable
    fun InfoText(
        title: String,
        content: String,
        bottomPadding: Dp = 24.dp
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
        Text(
            text = content,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding)
        )
    }

    Card(
        modifier = Modifier.miuixHomeTileBlur(MiuixTheme.colorScheme.surfaceContainerHigh),
        colors = homeCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoText(title = stringResource(R.string.home_kernel), content = systemInfo.kernelRelease)
            InfoText(
                title = stringResource(R.string.home_manager_version),
                content = "${systemInfo.managerVersion.first} (${systemInfo.managerVersion.second})"
            )
            InfoText(title = stringResource(R.string.home_fingerprint), content = Build.FINGERPRINT)
            val selinuxDisplay = when (systemInfo.selinuxStatus) {
                "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
                "Permissive" -> stringResource(R.string.selinux_status_permissive)
                "Disabled" -> stringResource(R.string.selinux_status_disabled)
                else -> stringResource(R.string.selinux_status_unknown)
            }
            InfoText(
                title = stringResource(R.string.home_selinux_status),
                content = selinuxDisplay,
            )
            val seccompDisplay = when (systemInfo.seccompStatus) {
                -1 -> stringResource(R.string.seccomp_status_not_supported)
                0 -> stringResource(R.string.seccomp_status_disabled)
                1 -> stringResource(R.string.seccomp_status_strict)
                2 -> stringResource(R.string.seccomp_status_filter)
                else -> stringResource(R.string.seccomp_status_unknown)
            }
            InfoText(
                title = stringResource(R.string.home_seccomp_status),
                content = seccompDisplay,
                bottomPadding = 0.dp
            )
        }
    }
}

@Composable
private fun isInDarkTheme(): Boolean {
    val themeConfig = koinInject<com.originsu.manager.ui.theme.ThemeConfig>()
    return themeConfig.forceDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
}
