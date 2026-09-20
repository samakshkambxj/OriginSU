package com.originsu.manager.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.originsu.manager.BuildConfig
import com.originsu.manager.R
import com.originsu.manager.ui.component.WarningCard
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Navigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.theme.renderBackgroundBlur
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.koin.compose.koinInject


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f // from debugger
        )
    )

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                title = { Text(text = stringResource(id = R.string.about)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    AppBackButton(
                        onClick = {
                            navigator.pop()
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        containerColor = Color.Transparent,
    ) { innerPadding ->
        val uriHandler = LocalUriHandler.current

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
        ) {
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            }

            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    StatusCard()
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    CreatorCard(
                        onOpenRepo = {
                            uriHandler.openUri("https://github.com/samakshkambxj/OriginSU")
                        }
                    )
                }
            }

            item {
                WarningCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = cardConfig.cardAlpha
                    ),
                    message = AnnotatedString.fromHtml(
                        htmlString = stringResource(
                            id = R.string.about_anime_character_sticker
                        ),
                        linkStyles = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ),
                            pressedStyle = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                background = MaterialTheme.colorScheme.secondaryContainer,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.TwoTone.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.about)
                ) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Code,
                            title = stringResource(R.string.get_source_code),
                            description = stringResource(R.string.get_source_code_detail),
                            onClick = { uriHandler.openUri("https://github.com/samakshkambxj/OriginSU") }
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Group,
                            title = stringResource(R.string.join_telegram_group),
                            description = stringResource(R.string.join_telegram_group_detail),
                            onClick = { uriHandler.openUri("https://t.me/ReSukiSU") }
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Copyright,
                            title = stringResource(R.string.open_source_license),
                            description = stringResource(R.string.open_source_license_settings_description),
                            onClick = {
                                navigator.push(Route.OpenSourceLicense)
                            }
                        )
                    }
                }
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.about_credits)
                ) {
                    CREDITS.forEach { credit ->
                        item {
                            SettingsBaseWidget(
                                icon = null,
                                iconPlaceholder = false,
                                leadingContent = {
                                    AvatarImage(
                                        url = credit.avatarUrl,
                                        fallbackLetter = credit.name.firstOrNull() ?: '?',
                                        size = 40.dp,
                                    )
                                },
                                title = credit.name,
                                description = stringResource(credit.roleRes),
                                onClick = { uriHandler.openUri(credit.url) },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
    }
}

@Preview
@Composable
fun AboutScreenPreview() {
    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.About)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            AboutScreen()
        }
    }
}

@Composable
private fun StatusCard() {    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .renderBackgroundBlur(),
        color =
            if (themeConfig.isEnableBlurExp)
                Color.Transparent
            else
                MaterialTheme.colorScheme.primaryContainer.copy(cardConfig.cardAlpha),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Image(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        painter = rememberDrawablePainter(
                            drawable = ContextCompat.getDrawable(
                                LocalContext.current,
                                R.mipmap.ic_launcher
                            )
                        ),
                        contentDescription = stringResource(id = R.string.app_name)
                    )
                }
            }
            ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Box {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private const val CREATOR_HANDLE = "samakshkambxj"
private const val CREATOR_AVATAR_URL = "https://github.com/samakshkambxj.png"

@Composable
private fun CreatorCard(onOpenRepo: () -> Unit) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .renderBackgroundBlur(),
        color =
            if (themeConfig.isEnableBlurExp)
                Color.Transparent
            else
                MaterialTheme.colorScheme.primaryContainer.copy(cardConfig.cardAlpha),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenRepo)
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AvatarImage(
                url = CREATOR_AVATAR_URL,
                fallbackLetter = CREATOR_HANDLE.firstOrNull() ?: '?',
                size = 72.dp,
            )
            Text(
                text = stringResource(id = R.string.about_created_by, CREATOR_HANDLE),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(id = R.string.get_source_code_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Circular remote avatar with an initial-letter fallback that stays visible
 * while loading or when offline.
 */
@Composable
private fun AvatarImage(url: String?, fallbackLetter: Char, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fallbackLetter.uppercaseChar().toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
    }
}

private data class Credit(
    val name: String,
    val roleRes: Int,
    val url: String,
    val avatarUrl: String?,
)

private fun githubAvatar(userOrOrg: String) = "https://github.com/$userOrOrg.png"

private val CREDITS = listOf(
    Credit(
        name = "ReSukiSU",
        roleRes = R.string.about_credit_role_resukisu,
        url = "https://github.com/ReSukiSU/ReSukiSU",
        avatarUrl = githubAvatar("ReSukiSU"),
    ),
    Credit(
        name = "SukiSU-Ultra",
        roleRes = R.string.about_credit_role_sukisu,
        url = "https://github.com/SukiSU-Ultra/SukiSU-Ultra",
        avatarUrl = githubAvatar("SukiSU-Ultra"),
    ),
    Credit(
        name = "KernelSU",
        roleRes = R.string.about_credit_role_kernelsu,
        url = "https://github.com/tiann/KernelSU",
        avatarUrl = githubAvatar("tiann"),
    ),
    Credit(
        name = "MKSU",
        roleRes = R.string.about_credit_role_mksu,
        url = "https://github.com/5ec1cff/KernelSU",
        avatarUrl = githubAvatar("5ec1cff"),
    ),
    Credit(
        name = "RKSU",
        roleRes = R.string.about_credit_role_rksu,
        url = "https://github.com/rsuntk/KernelSU",
        avatarUrl = githubAvatar("rsuntk"),
    ),
    Credit(
        name = "SuSFS",
        roleRes = R.string.about_credit_role_susfs,
        url = "https://gitlab.com/simonpunk/susfs4ksu",
        avatarUrl = null,
    ),
    Credit(
        name = "KernelPatch",
        roleRes = R.string.about_credit_role_kernelpatch,
        url = "https://github.com/bmax121/KernelPatch",
        avatarUrl = githubAvatar("bmax121"),
    ),
    Credit(
        name = "Kernel-Assisted Superuser",
        roleRes = R.string.about_credit_role_kasu,
        url = "https://git.zx2c4.com/kernel-assisted-superuser/about/",
        avatarUrl = null,
    ),
    Credit(
        name = "Magisk",
        roleRes = R.string.about_credit_role_magisk,
        url = "https://github.com/topjohnwu/Magisk",
        avatarUrl = githubAvatar("topjohnwu"),
    ),
    Credit(
        name = "genuine",
        roleRes = R.string.about_credit_role_genuine,
        url = "https://github.com/brevent/genuine/",
        avatarUrl = githubAvatar("brevent"),
    ),
    Credit(
        name = "Diamorphine",
        roleRes = R.string.about_credit_role_diamorphine,
        url = "https://github.com/m0nad/Diamorphine",
        avatarUrl = githubAvatar("m0nad"),
    ),
    Credit(
        name = "WildKSU",
        roleRes = R.string.about_credit_role_wildksu,
        url = "https://github.com/WildKernels/Wild_KSU",
        avatarUrl = githubAvatar("WildKernels"),
    ),
)
