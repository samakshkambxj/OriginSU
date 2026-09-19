package com.originsu.manager.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.appearance.AppIcon
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.util.LocalSnackbarHost
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.util.showReplacingSnackbar
import com.originsu.manager.ui.viewmodel.AppIconUiEvent
import com.originsu.manager.ui.viewmodel.AppIconViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class AppIconOption(
    val icon: AppIcon,
    val preview: Int,
    val titleRes: Int,
    val summaryRes: Int,
)

private val APP_ICON_OPTIONS = listOf(
    AppIconOption(
        icon = AppIcon.ORIGIN_BLACK,
        preview = R.mipmap.ic_launcher,
        titleRes = R.string.app_icon_origin_black,
        summaryRes = R.string.app_icon_origin_black_summary,
    ),
    AppIconOption(
        icon = AppIcon.ORIGIN_LIGHT,
        preview = R.mipmap.ic_launcher_light,
        titleRes = R.string.app_icon_origin_light,
        summaryRes = R.string.app_icon_origin_light_summary,
    ),
    AppIconOption(
        icon = AppIcon.KSU_OFFICIAL,
        preview = R.mipmap.ic_launcher_ksu,
        titleRes = R.string.app_icon_ksu_official,
        summaryRes = R.string.app_icon_ksu_official_summary,
    ),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<AppIconViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackBarHost = LocalSnackbarHost.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AppIconUiEvent.Error ->
                    snackBarHost.showReplacingSnackbar(event.message)
            }
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.app_icon_title)) },
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
                    APP_ICON_OPTIONS.forEach { option ->
                        item {
                            val selected = uiState.current == option.icon
                            SettingsBaseWidget(
                                title = stringResource(option.titleRes),
                                description = stringResource(option.summaryRes),
                                selected = selected,
                                onClick = { viewModel.select(option.icon) },
                                leadingContent = {
                                    Image(
                                        painter = painterResource(option.preview),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                    )
                                },
                                foreContent = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { viewModel.select(option.icon) },
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
