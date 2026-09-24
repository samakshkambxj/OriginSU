package com.originsu.manager.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.originsu.manager.R
import com.originsu.manager.ui.screen.main.HomePage
import com.originsu.manager.ui.screen.main.KpmPage
import com.originsu.manager.ui.screen.main.ModulePage
import com.originsu.manager.ui.screen.main.SettingsPage
import com.originsu.manager.ui.screen.main.SuperUserPage

enum class BottomBarDestination(
    val direction: @Composable (bottomPadding: Dp) -> Unit,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
    @param:androidx.annotation.DrawableRes val themedIconRes: Int? = null,
    val kpmRequired: Boolean = false,
) {
    Home(
        { bottomPadding -> HomePage(bottomPadding) },
        R.string.home,
        Icons.TwoTone.Home,
        Icons.TwoTone.Home,
        false
    ),
    SuperUser(
        { bottomPadding -> SuperUserPage(bottomPadding) },
        R.string.superuser,
        Icons.TwoTone.AdminPanelSettings,
        Icons.TwoTone.AdminPanelSettings,
        true,
        R.drawable.nav_icon_superuser
    ),
    Module(
        { bottomPadding -> ModulePage(bottomPadding) },
        R.string.module,
        Icons.TwoTone.Extension,
        Icons.TwoTone.Extension,
        true,
        R.drawable.nav_icon_modules
    ),
    Kpm(
        { bottomPadding -> KpmPage(bottomPadding) },
        R.string.kpm_title,
        Icons.TwoTone.Memory,
        Icons.TwoTone.Memory,
        true,
        kpmRequired = true
    ),
    Settings(
        { bottomPadding -> SettingsPage(bottomPadding) },
        R.string.settings,
        Icons.TwoTone.Settings,
        Icons.TwoTone.Settings,
        false,
        R.drawable.nav_icon_settings
    );

    companion object {
        fun getPages(hasCoreAccess: Boolean, isKpmEnabled: Boolean): List<BottomBarDestination> {
            return BottomBarDestination.entries.filter {
                (!it.rootRequired || hasCoreAccess) && (!it.kpmRequired || isKpmEnabled)
            }
        }
    }
}
