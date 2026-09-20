package com.originsu.manager.ui.screen

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.data.kernel.VeilManageRepository
import com.originsu.manager.domain.model.VeilKind
import com.originsu.manager.ui.component.settings.AppBackButton
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.viewmodel.VeilUiAction
import com.originsu.manager.ui.viewmodel.VeilViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class PermRow(
    val perm: String,
    val name: String,
    val dangerous: Boolean,
    val granted: Boolean,
    val blockable: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeilDetailScreen(uid: Int) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pm = context.packageManager
    val manage: VeilManageRepository = koinInject()
    val veilViewModel = koinViewModel<VeilViewModel>()
    val veilState by veilViewModel.uiState.collectAsStateWithLifecycle()

    val pkg = remember(uid) { manage.packageForUid(uid) }
    val unknownUidLabel = stringResource(R.string.veil_uid_title, uid)
    val label = remember(uid) {
        pkg?.let {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrNull()
        } ?: (pm.getNameForUid(uid) ?: unknownUidLabel)
    }

    var count by remember { mutableIntStateOf(0) }
    var kindsLabel by remember { mutableStateOf("") }
    var isCloaked by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var perms by remember { mutableStateOf<List<PermRow>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val hist = veilState.history.firstOrNull { it.uid == uid }
        count = hist?.count ?: 0
        // Resolve kind labels here (plain context call): @Composable helpers
        // can't run inside joinToString's lambda.
        kindsLabel = hist?.kinds?.joinToString(", ") { context.getString(it.labelRes()) } ?: ""
        isCloaked = veilState.cloakedUids.any { it.uid == uid }
        if (pkg != null) {
            runCatching {
                enabled = pm.getApplicationInfo(pkg, 0).enabled
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                val req = pi.requestedPermissions ?: emptyArray()
                val flags = pi.requestedPermissionsFlags ?: IntArray(req.size)
                val ops = manage.getAppOpsModes(pkg)
                perms = req.mapIndexed { i, perm ->
                    val dangerous = runCatching {
                        (pm.getPermissionInfo(perm, 0).protectionLevel and
                            PermissionInfo.PROTECTION_DANGEROUS) != 0
                    }.getOrDefault(false)
                    val pmGranted = (flags.getOrElse(i) { 0 } and
                        PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    // Treat an appop set to ignore/deny as blocked too, so the
                    // state reflects blocks applied to non-runtime perms.
                    val opMode = VeilManageRepository.opForPermission(perm)?.let { ops[it] }
                    val granted = pmGranted && opMode != "ignore" && opMode != "deny"
                    PermRow(
                        perm = perm,
                        name = perm.substringAfterLast('.'),
                        dangerous = dangerous,
                        granted = granted,
                        blockable = VeilManageRepository.isPermissionBlockable(perm, dangerous),
                    )
                }.sortedWith(compareByDescending<PermRow> { it.dangerous }.thenBy { it.name })
            }
        }
    }

    fun applyMode(p: PermRow, block: Boolean) {
        if (pkg != null) {
            scope.launch {
                withContext(Dispatchers.IO) { manage.setPermissionMode(pkg, p.perm, block) }
                refresh()
            }
        }
    }

    LaunchedEffect(uid, veilState.history, veilState.cloakedUids) { refresh() }

    val dangerousPerms = perms.filter { it.dangerous }
    val otherPerms = perms.filter { !it.dangerous }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        pkg ?: stringResource(R.string.veil_uid_title, uid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.veil_detail_probing),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            if (count == 0) stringResource(R.string.veil_detail_probing_empty)
                            else kindsLabel
                        )
                    },
                    supportingContent = {
                        Text(stringResource(R.string.veil_detail_probes_boot, count))
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.veil_detail_permissions),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(headlineContent = {
                    Text(
                        stringResource(
                            R.string.veil_detail_dangerous_count,
                            dangerousPerms.size,
                            perms.size,
                        )
                    )
                })
            }
            items(dangerousPerms, key = { it.perm }) { p ->
                VeilPermRow(p) { block -> applyMode(p, block) }
            }
            if (otherPerms.isNotEmpty()) {
                item {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            if (showAll) stringResource(R.string.veil_detail_hide_others)
                            else stringResource(R.string.veil_detail_show_others, otherPerms.size)
                        )
                    }
                }
                if (showAll) {
                    // White (non-dangerous) perms are info-only - no Manage button.
                    items(otherPerms, key = { it.perm }) { p ->
                        VeilPermRow(p, onMode = null)
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.veil_detail_actions),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { navigator.push(Route.VeilSpy(uid)) },
                    leadingContent = { Icon(Icons.TwoTone.Visibility, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.veil_detail_spy)) },
                    supportingContent = { Text(stringResource(R.string.veil_detail_spy_summary)) },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.TwoTone.VisibilityOff, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.veil_detail_cloak)) },
                    supportingContent = { Text(stringResource(R.string.veil_detail_cloak_summary)) },
                    trailingContent = {
                        Switch(checked = isCloaked, onCheckedChange = { on ->
                            scope.launch {
                                if (on) {
                                    veilViewModel.dispatch(VeilUiAction.CloakUid(uid))
                                } else {
                                    // Reset to default and leave: an uncloaked app
                                    // can't be managed here, so return to the list.
                                    veilViewModel.dispatch(VeilUiAction.UncloakUid(uid))
                                    navigator.pop()
                                }
                            }
                        })
                    },
                )
            }
            if (pkg != null) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.TwoTone.Stop, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.veil_detail_force_stop)) },
                        trailingContent = {
                            OutlinedButton(onClick = {
                                scope.launch { withContext(Dispatchers.IO) { manage.forceStop(pkg) } }
                            }) { Text(stringResource(R.string.veil_detail_stop)) }
                        },
                    )
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(
                                if (enabled) Icons.TwoTone.Block else Icons.TwoTone.PlayArrow,
                                contentDescription = null,
                            )
                        },
                        headlineContent = {
                            Text(
                                if (enabled) stringResource(R.string.veil_detail_disable_app)
                                else stringResource(R.string.veil_detail_enable_app)
                            )
                        },
                        trailingContent = {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        manage.setAppEnabled(pkg, !enabled)
                                    }
                                    refresh()
                                }
                            }) {
                                Text(
                                    if (enabled) stringResource(R.string.veil_detail_disable)
                                    else stringResource(R.string.veil_detail_enable)
                                )
                            }
                        },
                    )
                }
            }
            item { HorizontalDivider() }
        }
    }
}

private fun VeilKind.labelRes(): Int = when (this) {
    VeilKind.Su -> R.string.veil_kind_su
    VeilKind.Magisk -> R.string.veil_kind_magisk
    VeilKind.Ksu -> R.string.veil_kind_ksu
    VeilKind.Modules -> R.string.veil_kind_modules
    VeilKind.PkgList -> R.string.veil_kind_pkglist
    VeilKind.Busybox -> R.string.veil_kind_busybox
    VeilKind.SuExec -> R.string.veil_kind_su_exec
    VeilKind.Unknown -> R.string.veil_kind_unknown
}

@Composable
private fun VeilPermRow(p: PermRow, onMode: ((Boolean) -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                p.name,
                color = if (p.dangerous) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                if (p.granted) stringResource(R.string.veil_perm_granted)
                else stringResource(R.string.veil_perm_denied)
            )
        },
        trailingContent = if (onMode == null || !p.blockable) null else {
            {
                Box {
                    OutlinedButton(onClick = { menu = true }) {
                        Text(stringResource(R.string.veil_perm_manage))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.veil_perm_allow)) },
                            onClick = { menu = false; onMode(false) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.veil_perm_block)) },
                            onClick = { menu = false; onMode(true) },
                        )
                    }
                }
            }
        },
    )
}
