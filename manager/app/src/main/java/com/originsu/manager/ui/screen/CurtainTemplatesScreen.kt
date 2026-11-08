package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Groups
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.curtain.CurtainLoadError
import com.originsu.manager.data.curtain.CurtainTemplate
import com.originsu.manager.ui.component.ConfirmResult
import com.originsu.manager.ui.component.rememberConfirmDialog
import com.originsu.manager.ui.component.rememberCustomDialog
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.component.settings.SegmentedColumn
import com.originsu.manager.ui.component.settings.SettingsBaseWidget
import com.originsu.manager.ui.component.settings.SettingsJumpPageWidget
import com.originsu.manager.ui.component.settings.lazySegmentColumn
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.blurSource
import com.originsu.manager.ui.util.ActivityResumeEffect
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.CurtainTemplatesViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Manage the reusable app lists Origin Curtain applies to target apps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtainTemplatesScreen(viewModel: CurtainTemplatesViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val deleteTitle = stringResource(R.string.origin_curtain_template_delete)
    val confirm = stringResource(R.string.confirm)
    val templatesTitle = stringResource(R.string.origin_curtain_templates)

    ActivityResumeEffect {
        viewModel.refresh()
    }

    val createDialog = rememberCustomDialog { dismiss ->
        CreateTemplateDialog(
            existingNames = uiState.templates.map { it.name }.toSet(),
            onCreate = { name, whitelist ->
                viewModel.create(name, whitelist)
                dismiss()
            },
            onDismiss = dismiss,
        )
    }

    val errorText = when (uiState.error) {
        CurtainLoadError.NO_ROOT -> stringResource(R.string.origin_curtain_error_no_root)
        CurtainLoadError.NOT_INITIALIZED ->
            stringResource(R.string.origin_curtain_error_not_initialized)
        CurtainLoadError.UNREADABLE -> stringResource(R.string.origin_curtain_error_unreadable)
        CurtainLoadError.WRITE_FAILED -> stringResource(R.string.origin_curtain_error_write)
        null -> null
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(templatesTitle) },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(onClick = { createDialog.show() }) {
                        Icon(
                            imageVector = Icons.TwoTone.Add,
                            contentDescription = stringResource(R.string.origin_curtain_template_new),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (themeConfig.isEnableBlur) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                    },
                    scrolledContainerColor = if (themeConfig.isEnableBlur) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                    },
                ),
            )
        },
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (errorText != null) {
                item(key = "error") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_templates)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.origin_curtain_error_title),
                                description = errorText,
                                isError = true,
                            )
                        }
                    }
                }
            }

            if (uiState.templates.isEmpty()) {
                item(key = "empty") {
                    SegmentedColumn(title = stringResource(R.string.origin_curtain_templates)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Groups,
                                title = stringResource(R.string.origin_curtain_template_empty),
                                description = stringResource(
                                    R.string.origin_curtain_template_empty_summary,
                                ),
                            )
                        }
                    }
                }
            } else {
                lazySegmentColumn(
                    items = uiState.templates,
                    title = templatesTitle,
                    key = { _, template -> template.name },
                ) { _, template ->
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Groups,
                        title = template.name,
                        description = templateSummary(template),
                        onClick = { navigator.push(Route.CurtainTemplateApps(template.name)) },
                    ) { _ ->
                        val deleteMessage =
                            stringResource(R.string.origin_curtain_template_delete_confirm, template.name)
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val result = confirmDialog.awaitConfirm(
                                        title = deleteTitle,
                                        content = deleteMessage,
                                        confirm = confirm,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        viewModel.delete(template.name)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Delete,
                                contentDescription = stringResource(
                                    R.string.origin_curtain_template_delete,
                                ),
                            )
                        }
                    }
                }
            }

            item(key = "create") {
                SegmentedColumn {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Add,
                            title = stringResource(R.string.origin_curtain_template_new),
                            description = stringResource(R.string.origin_curtain_template_new_summary),
                            onClick = { createDialog.show() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun templateSummary(template: CurtainTemplate): String =
    stringResource(
        R.string.origin_curtain_template_summary,
        if (template.isWhitelist) {
            stringResource(R.string.origin_curtain_mode_whitelist)
        } else {
            stringResource(R.string.origin_curtain_mode_blacklist)
        },
        template.appList.size,
    )

@Composable
private fun CreateTemplateDialog(
    existingNames: Set<String>,
    onCreate: (name: String, isWhitelist: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var whitelist by remember { mutableStateOf(false) }
    val trimmed = name.trim()
    val duplicate = trimmed.isNotEmpty() && existingNames.any { it.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.origin_curtain_template_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.origin_curtain_template_name)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !whitelist,
                        onClick = { whitelist = false },
                        label = { Text(stringResource(R.string.origin_curtain_mode_blacklist)) },
                    )
                    FilterChip(
                        selected = whitelist,
                        onClick = { whitelist = true },
                        label = { Text(stringResource(R.string.origin_curtain_mode_whitelist)) },
                    )
                }
                if (duplicate) {
                    Text(
                        text = stringResource(R.string.origin_curtain_template_name_exists),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(trimmed, whitelist) },
                enabled = trimmed.isNotEmpty() && !duplicate,
            ) {
                Text(stringResource(R.string.origin_curtain_template_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
