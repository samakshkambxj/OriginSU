package com.originsu.manager.ui.activity.component

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailColors
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.ui.component.FloatingBottomBar
import com.originsu.manager.ui.component.FloatingBottomBarItem
import com.originsu.manager.ui.screen.BottomBarDestination
import com.originsu.manager.ui.theme.BottomBarStyle
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.util.LocalBlurState
import com.originsu.manager.ui.util.LocalHandlePageChange
import com.originsu.manager.ui.util.LocalSelectedPage
import com.originsu.manager.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    destinations: List<BottomBarDestination>,
    isBottomBar: Boolean
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val superuserCount = uiState.systemInfo.superuserCount
    val moduleCount = uiState.systemInfo.moduleCount
    val kpmModuleCount = uiState.systemInfo.kpmModuleCount
    val showNavigationBarBadge = uiState.showNavigationBarBadge
    val themedIcons = uiState.isThemedShortcutsEnabled
    val page = LocalSelectedPage.current
    val handlePageChange = LocalHandlePageChange.current

    if (isBottomBar && themeConfig.bottomBarStyle == BottomBarStyle.FLOATING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                )
                .padding(
                    bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                ),
            contentAlignment = Alignment.Center
        ) {
            FloatingBottomBar(
                // Drive the indicator from the synchronously-updated selected page
                // (SukiSU-Ultra's MainPagerState.selectedPage), not PagerState.targetPage,
                // so the liquid pill starts moving on the tap frame instead of waiting
                // for the pager scroll state to propagate.
                selectedIndex = page,
                onSelected = { handlePageChange(it) },
                tabsCount = destinations.size,
                isBlurEnabled = LocalBlurState.current != null,
            ) { activateTab ->
                destinations.forEachIndexed { index, destination ->
                    FloatingBottomBarItem(
                        selected = index == page,
                        onClick = { activateTab(index) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                    ) {
                        val contentColor = LocalContentColor.current
                        val count = when (destination) {
                            BottomBarDestination.SuperUser -> superuserCount
                            BottomBarDestination.Module -> moduleCount
                            BottomBarDestination.Kpm -> kpmModuleCount
                            else -> 0
                        }
                        val icon: @Composable () -> Unit = {
                            DestinationIcon(
                                destination = destination,
                                themed = themedIcons,
                                imageVector = destination.iconSelected,
                            )
                        }
                        if (count > 0 && showNavigationBarBadge) {
                            BadgedBox(badge = { Badge { Text(count.toString()) } }) { icon() }
                        } else {
                            icon()
                        }
                        Text(
                            text = stringResource(destination.label),
                            color = contentColor,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible
                        )
                    }
                }
            }
        }
    } else if (isBottomBar) {
        FlexibleBottomAppBar(
            modifier = modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .blurEffect(
                    compensateHorizontalOverscroll = true,
                    compensateVerticalOverscroll = true,
                    useFixedSurfaceBoundsForOverscroll = true,
                ),
            containerColor =
                if (themeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            destinations.forEachIndexed { index, destination ->
                BottomBarNavigationItem(
                    isSelected = index == page,
                    destination = destination,
                    onClick = {
                        handlePageChange(index)
                    },
                    superuserCount = superuserCount,
                    moduleCount = moduleCount,
                    kpmModuleCount = kpmModuleCount,
                    showNavigationBarBadge = showNavigationBarBadge,
                    themedIcons = themedIcons,
                )
            }
        }
    } else {
        WideNavigationRail(
            modifier = modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .blurEffect(
                    compensateHorizontalOverscroll = true,
                    compensateVerticalOverscroll = false,
                    useFixedSurfaceBoundsForOverscroll = true,
                ),
            colors = WideNavigationRailColors(
                containerColor =
                    if (themeConfig.isEnableBlur)
                        Color.Transparent
                    else
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modalContainerColor = WideNavigationRailDefaults.colors().modalContainerColor,
                modalScrimColor = WideNavigationRailDefaults.colors().modalScrimColor,
                modalContentColor = WideNavigationRailDefaults.colors().modalContentColor,
            ),
        ) {
            destinations.forEachIndexed { index, destination ->
                NavigationRailItem(
                    isSelected = index == page,
                    destination = destination,
                    onClick = {
                        handlePageChange(index)
                    },
                    superuserCount = superuserCount,
                    moduleCount = moduleCount,
                    kpmModuleCount = kpmModuleCount,
                    showNavigationBarBadge = showNavigationBarBadge,
                    themedIcons = themedIcons,
                )
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    destination: BottomBarDestination,
    themed: Boolean,
    imageVector: ImageVector,
) {
    val themedRes = if (themed) destination.themedIconRes else null
    if (themedRes != null) {
        Image(
            painter = painterResource(themedRes),
            contentDescription = stringResource(destination.label),
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(LocalContentColor.current),
        )
    } else {
        Icon(imageVector, stringResource(destination.label))
    }
}

@Composable
private fun NavigationRailItem(
    isSelected: Boolean,
    destination: BottomBarDestination,
    onClick: () -> Unit,
    superuserCount: Int,
    moduleCount: Int,
    kpmModuleCount: Int,
    showNavigationBarBadge: Boolean,
    themedIcons: Boolean,
) {
    WideNavigationRailItem(
        railExpanded = false,
        selected = isSelected,
        onClick = onClick,
        icon = {
            BadgedBox(
                badge = {
                    DestinationBadge(
                        dest = destination,
                        superUser = superuserCount,
                        module = moduleCount,
                        kpm = kpmModuleCount,
                        show = showNavigationBarBadge,
                    )
                }
            ) {
                if (isSelected) {
                    DestinationIcon(destination, themedIcons, destination.iconSelected)
                } else {
                    DestinationIcon(destination, themedIcons, destination.iconNotSelected)
                }
            }
        },
        label = {
            Text(
                stringResource(destination.label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        },
    )
}

@Composable
private fun RowScope.BottomBarNavigationItem(
    isSelected: Boolean,
    destination: BottomBarDestination,
    onClick: () -> Unit,
    superuserCount: Int,
    moduleCount: Int,
    kpmModuleCount: Int,
    showNavigationBarBadge: Boolean,
    themedIcons: Boolean,
) {
    NavigationBarItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            BadgedBox(
                badge = {
                    DestinationBadge(
                        dest = destination,
                        superUser = superuserCount,
                        module = moduleCount,
                        kpm = kpmModuleCount,
                        show = showNavigationBarBadge,
                    )
                }
            ) {
                if (isSelected) {
                    DestinationIcon(destination, themedIcons, destination.iconSelected)
                } else {
                    DestinationIcon(destination, themedIcons, destination.iconNotSelected)
                }
            }
        },
        label = {
            Text(
                stringResource(destination.label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        },
        alwaysShowLabel = false
    )
}

@Composable
private fun DestinationBadge(
    dest: BottomBarDestination,
    superUser: Int,
    module: Int,
    kpm: Int,
    show: Boolean,
) {
    val count = when (dest) {
        BottomBarDestination.SuperUser -> superUser
        BottomBarDestination.Module -> module
        BottomBarDestination.Kpm -> kpm
        else -> 0
    }

    AnimatedVisibility(
        visible = count > 0 && show,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Badge(
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text(count.toString())
        }
    }
}
