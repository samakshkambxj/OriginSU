package com.originsu.manager.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.originsu.manager.R

/** One selectable row in [rememberCurtainPickerDialog]. */
data class CurtainPickerEntry(
    val id: String,
    val label: String,
    val description: String? = null,
    /** When set, the row shows this package's launcher icon. */
    val iconPackage: String? = null,
)

/** Handle returned by [rememberCurtainPickerDialog]. */
class CurtainPickerHandle internal constructor(
    private val showDialog: (
        title: String,
        entries: List<CurtainPickerEntry>,
        selected: Set<String>,
        onConfirm: (Set<String>) -> Unit,
    ) -> Unit,
) {
    fun show(
        title: String,
        entries: List<CurtainPickerEntry>,
        selected: Set<String>,
        onConfirm: (Set<String>) -> Unit,
    ) = showDialog(title, entries, selected, onConfirm)
}

private data class CurtainPickerRequest(
    val title: String,
    val entries: List<CurtainPickerEntry>,
    val selected: Set<String>,
    val onConfirm: (Set<String>) -> Unit,
)

/**
 * Searchable multi-select picker used by Origin Curtain editors.
 *
 * The dialog keeps a draft selection and only hands it back when the user
 * confirms, so a long list of toggles does not write the config file once per
 * tap.
 */
@Composable
fun rememberCurtainPickerDialog(): CurtainPickerHandle {
    val request = remember { mutableStateOf<CurtainPickerRequest?>(null) }
    val dialog = rememberCustomDialog { dismiss ->
        val current = request.value
        if (current != null) {
            CurtainPickerDialog(
                title = current.title,
                entries = current.entries,
                initialSelected = current.selected,
                onConfirm = { selection ->
                    current.onConfirm(selection)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
    }
    return remember {
        CurtainPickerHandle { title, entries, selected, onConfirm ->
            request.value = CurtainPickerRequest(title, entries, selected, onConfirm)
            dialog.show()
        }
    }
}

@Composable
private fun CurtainPickerDialog(
    title: String,
    entries: List<CurtainPickerEntry>,
    initialSelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selection by remember(initialSelected) { mutableStateOf(initialSelected) }
    val query = search.trim()
    val filtered = remember(entries, query) {
        if (query.isEmpty()) {
            entries
        } else {
            entries.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true) ||
                    it.description?.contains(query, ignoreCase = true) == true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.origin_curtain_search)) },
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.origin_curtain_no_apps),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selection = if (entry.id in selection) {
                                            selection - entry.id
                                        } else {
                                            selection + entry.id
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val iconPackage = entry.iconPackage
                                if (iconPackage != null) {
                                    PackageIcon(
                                        packageName = iconPackage,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    entry.description?.let { description ->
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = entry.id in selection,
                                    onCheckedChange = { checked ->
                                        selection = if (checked) {
                                            selection + entry.id
                                        } else {
                                            selection - entry.id
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selection) }) {
                Text(stringResource(R.string.origin_curtain_picker_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
