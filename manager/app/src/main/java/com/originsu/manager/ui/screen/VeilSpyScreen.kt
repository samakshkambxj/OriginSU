package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.originsu.manager.R
import com.originsu.manager.data.kernel.VeilManageRepository
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.navigation.LocalNavigator
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// High-signal phrases (attestation / Play Store integrity) - highlighted so they pop.
private val SPY_KEYWORDS = listOf(
    "attest", "integrity", "safetynet", "play protect", "playprotect",
    "vending", "play store", "playstore", "denied", "rejected", "license",
    "keymaster", "keymint", "keystore", "droidguard", "abort_operation",
)

private fun logColor(line: String): Color {
    if (SPY_KEYWORDS.any { it in line.lowercase() }) return Color(0xFFFF79C6) // pink: attestation/Play
    val level = Regex("""\d+\s+\d+\s+([VDIWEF])\s""").find(line)?.groupValues?.get(1)
    return when (level) {
        "E", "F" -> Color(0xFFFF5555) // red: error/fatal
        "W" -> Color(0xFFFFB454) // amber: warning
        "I" -> Color(0xFF6FCF6F) // green: info
        "D" -> Color(0xFF7FB3FF) // blue: debug
        else -> Color(0xFF9E9E9E) // gray: verbose / markers
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeilSpyScreen(uid: Int) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val pm = context.packageManager
    val manage: VeilManageRepository = koinInject()

    val unknownUidLabel = stringResource(R.string.veil_uid_title, uid)
    val label = remember(uid) {
        val pkg = manage.packageForUid(uid)
        pkg?.let {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrNull()
        } ?: (pm.getNameForUid(uid) ?: unknownUidLabel)
    }

    val lines = remember { mutableStateListOf<String>() }
    var paused by remember { mutableStateOf(false) }
    var lastLine by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Live tail: poll the app's logcat (~1.2s), append only new lines.
    LaunchedEffect(uid, paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            val dump = manage.dumpAppLog(uid, 400)
            if (dump.isNotEmpty()) {
                val start = if (lastLine.isEmpty()) 0 else {
                    val i = dump.lastIndexOf(lastLine)
                    if (i >= 0) i + 1 else 0
                }
                if (start < dump.size) {
                    lines.addAll(dump.subList(start, dump.size))
                    while (lines.size > 2000) lines.removeAt(0)
                    lastLine = dump.last()
                }
            }
            delay(1200)
        }
    }

    LaunchedEffect(lines.size, paused) {
        if (!paused && lines.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(lines.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.veil_spy_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = { paused = !paused }) {
                        Icon(
                            if (paused) Icons.TwoTone.PlayArrow else Icons.TwoTone.Pause,
                            contentDescription = if (paused) stringResource(R.string.veil_spy_resume)
                            else stringResource(R.string.veil_spy_pause),
                        )
                    }
                    IconButton(onClick = { lines.clear() }) {
                        Icon(
                            Icons.TwoTone.Delete,
                            contentDescription = stringResource(R.string.veil_spy_clear),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.veil_spy_waiting),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(lines) { line ->
                Text(
                    text = line,
                    color = logColor(line),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
    }
}
