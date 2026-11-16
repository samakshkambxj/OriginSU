package com.originsu.manager.ui.screen.susfs.content.miuix

import androidx.compose.runtime.Composable
import com.originsu.manager.ui.screen.susfs.content.EnabledFeaturesContent
import com.originsu.manager.ui.screen.susfs.util.EnabledFeature

@Composable
fun EnabledFeaturesContentMiuix(
    enabledFeatures: List<EnabledFeature>,
    onRefresh: () -> Unit,
) = EnabledFeaturesContent(enabledFeatures, onRefresh)
