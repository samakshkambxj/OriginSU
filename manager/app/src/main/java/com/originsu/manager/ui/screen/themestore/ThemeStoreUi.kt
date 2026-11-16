package com.originsu.manager.ui.screen.themestore

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.originsu.manager.R


@Composable
internal fun ThemeStoreSource.displayName(): String =
    if (isDefault) {
        stringResource(R.string.theme_store_default_source)
    } else {
        name.ifBlank {
            baseUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        }
    }
