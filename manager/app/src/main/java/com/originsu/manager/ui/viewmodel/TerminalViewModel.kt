package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_ENTRIES = 200
private const val MAX_HISTORY = 100

/** One submitted command together with its captured output. */
data class TerminalEntry(
    val id: Long,
    val command: String,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val timedOut: Boolean = false,
    val rootUnavailable: Boolean = false,
    val isRunning: Boolean = true,
)

data class TerminalUiState(
    val entries: List<TerminalEntry> = emptyList(),
    val input: String = "",
    val history: List<String> = emptyList(),
    val historyCursor: Int? = null,
    val isRunning: Boolean = false,
)

sealed interface TerminalUiAction {
    data class UpdateInput(val text: String) : TerminalUiAction
    data object Submit : TerminalUiAction
    data object Clear : TerminalUiAction
    data object PreviousCommand : TerminalUiAction
    data object NextCommand : TerminalUiAction
}

class TerminalViewModel(
    private val ksuCli: KsuCliRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TerminalUiState())
    private var nextId = 0L

    val state: StateFlow<TerminalUiState> = mutableState.asStateFlow()

    fun dispatch(action: TerminalUiAction) {
        when (action) {
            is TerminalUiAction.UpdateInput -> mutableState.update {
                it.copy(input = action.text, historyCursor = null)
            }

            TerminalUiAction.Submit -> run(mutableState.value.input)
            TerminalUiAction.Clear -> mutableState.update { it.copy(entries = emptyList()) }

            TerminalUiAction.PreviousCommand -> showHistory(older = true)
            TerminalUiAction.NextCommand -> showHistory(older = false)
        }
    }

    private fun showHistory(older: Boolean) {
        val current = mutableState.value
        if (current.history.isEmpty()) return
        if (older) {
            val cursor = ((current.historyCursor ?: current.history.size) - 1)
                .coerceIn(0, current.history.lastIndex)
            mutableState.update {
                it.copy(input = current.history[cursor], historyCursor = cursor)
            }
        } else {
            val cursor = current.historyCursor ?: return
            if (cursor >= current.history.lastIndex) {
                mutableState.update { it.copy(input = "", historyCursor = null) }
            } else {
                val next = cursor + 1
                mutableState.update {
                    it.copy(input = current.history[next], historyCursor = next)
                }
            }
        }
    }

    private fun run(rawCommand: String) {
        val command = rawCommand.trim()
        val current = mutableState.value
        if (command.isEmpty() || current.isRunning) return

        val entryId = nextId++
        mutableState.update {
            it.copy(
                entries = (it.entries + TerminalEntry(id = entryId, command = command))
                    .takeLast(MAX_ENTRIES),
                input = "",
                history = (it.history + command).takeLast(MAX_HISTORY),
                historyCursor = null,
                isRunning = true,
            )
        }

        viewModelScope.launch {
            val result = ksuCli.runShellCommand(command)
            mutableState.update { state ->
                state.copy(
                    entries = state.entries.map { entry ->
                        if (entry.id == entryId) {
                            entry.copy(
                                stdout = result.stdout,
                                stderr = result.stderr,
                                exitCode = result.exitCode,
                                timedOut = result.timedOut,
                                rootUnavailable = result.rootUnavailable,
                                isRunning = false,
                            )
                        } else {
                            entry
                        }
                    },
                    isRunning = false,
                )
            }
        }
    }
}
