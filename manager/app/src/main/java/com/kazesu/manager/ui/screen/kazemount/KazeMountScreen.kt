package com.kazesu.manager.ui.screen.kazemount

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kazesu.manager.ui.LocalUiMode
import com.kazesu.manager.ui.UiMode
import com.kazesu.manager.ui.navigation3.LocalNavigator
import com.kazesu.manager.ui.viewmodel.KazeMountViewModel

@Composable
fun KazeMountScreen() {
    val viewModel = viewModel<KazeMountViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val actions = KazeMountActions(
        onBack = { navigator.pop() },
        onRefresh = { viewModel.refresh(showLoading = true) },
        onModeChange = viewModel::setMode,
        onMagicEnabledChange = viewModel::setMagicMountEnabled,
        onBackendChange = viewModel::setMagicMountBackend,
        onMountNow = viewModel::mountNow,
        onSaveUids = viewModel::setUids,
        onIsolatedChange = viewModel::setIsolated,
        onClearRules = viewModel::clearRules,
    )

    when (LocalUiMode.current) {
        UiMode.Material -> KazeMountMaterial(state = state, actions = actions)
        UiMode.Miuix -> KazeMountMiuix(state = state, actions = actions)
    }
}
