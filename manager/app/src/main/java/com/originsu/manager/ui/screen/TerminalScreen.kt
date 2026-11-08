package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.MonospaceFontFamily
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.blurEffect
import com.originsu.manager.ui.theme.renderBackgroundBlur
import com.originsu.manager.ui.util.adaptiveScaffoldWindowInsets
import com.originsu.manager.ui.viewmodel.TerminalEntry
import com.originsu.manager.ui.viewmodel.TerminalUiAction
import com.originsu.manager.ui.viewmodel.TerminalViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<TerminalViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val monospace = MonospaceFontFamily()

    val lastEntry = uiState.entries.lastOrNull()
    LaunchedEffect(
        lastEntry?.id,
        lastEntry?.isRunning,
        lastEntry?.stdout,
        lastEntry?.stderr,
    ) {
        if (uiState.entries.isNotEmpty()) {
            listState.animateScrollToItem(uiState.entries.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.terminal_title)) },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.dispatch(TerminalUiAction.Clear) },
                        enabled = uiState.entries.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.DeleteSweep,
                            contentDescription = stringResource(R.string.terminal_clear),
                        )
                    }
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
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .then(
                        if (cardConfig.flashCardBackgroundEnabled) {
                            Modifier.renderBackgroundBlur(MaterialTheme.colorScheme.surfaceBright)
                        } else {
                            Modifier
                        },
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (
                        themeConfig.isEnableBlurExp || !cardConfig.flashCardBackgroundEnabled
                    ) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardConfig.cardAlpha)
                    },
                ),
            ) {
                if (uiState.entries.isEmpty()) {
                    TerminalEmptyState(monospace = monospace)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.entries, key = { it.id }) { entry ->
                            TerminalEntryItem(entry = entry, monospace = monospace)
                        }
                    }
                }
            }

            TerminalInputBar(
                value = uiState.input,
                enabled = !uiState.isRunning,
                monospace = monospace,
                onValueChange = {
                    viewModel.dispatch(TerminalUiAction.UpdateInput(it))
                },
                onSubmit = { viewModel.dispatch(TerminalUiAction.Submit) },
            )
        }
    }
}

@Composable
private fun TerminalEmptyState(monospace: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Terminal,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.terminal_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.terminal_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TerminalEntryItem(entry: TerminalEntry, monospace: FontFamily) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.terminal_command_line, entry.command),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = monospace,
                color = MaterialTheme.colorScheme.primary,
            )

            if (entry.isRunning) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.terminal_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            if (entry.rootUnavailable) {
                TerminalError(stringResource(R.string.terminal_root_unavailable), monospace)
            } else if (entry.timedOut) {
                TerminalError(stringResource(R.string.terminal_timed_out), monospace)
            }

            if (entry.stdout.isNotBlank()) {
                Text(
                    text = entry.stdout,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (entry.stderr.isNotBlank()) {
                Text(
                    text = entry.stderr,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = monospace,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!entry.timedOut && !entry.rootUnavailable) {
                entry.exitCode?.let { code ->
                    Text(
                        text = stringResource(R.string.terminal_exit_code, code),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = monospace,
                        color = if (code == 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalError(text: String, monospace: FontFamily) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = monospace,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun TerminalInputBar(
    value: String,
    enabled: Boolean,
    monospace: FontFamily,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = true,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.terminal_input_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = monospace),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Send,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onSend = { if (enabled) onSubmit() },
            ),
            shape = MaterialTheme.shapes.large,
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSubmit,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.PlayArrow,
                contentDescription = stringResource(R.string.terminal_run),
            )
        }
    }
}
