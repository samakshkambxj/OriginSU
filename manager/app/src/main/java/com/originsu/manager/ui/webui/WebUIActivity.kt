package com.originsu.manager.ui.webui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.data.packageinfo.AppIconDataSource
import com.originsu.manager.data.packageinfo.InstalledPackageRepository
import com.originsu.manager.data.webui.WebUiRepository
import com.originsu.manager.domain.usecase.APP_LOCK_PREF_KEY
import com.originsu.manager.domain.usecase.APP_LOCK_TIMEOUT_PREF_KEY
import com.originsu.manager.ui.component.AppLockOverlay
import com.originsu.manager.ui.theme.KernelSUTheme
import com.originsu.manager.ui.util.AppLockManager
import com.originsu.manager.ui.viewmodel.ModuleViewModel
import com.originsu.manager.ui.viewmodel.SuperUserViewModel
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : FragmentActivity() {

    private val appSettingsRepository: AppSettingsRepository by inject()
    private var appLockState = mutableStateOf(false)

    private fun appLockTimeout(): Long =
        appSettingsRepository.getLong(APP_LOCK_TIMEOUT_PREF_KEY, 60000L)

    override fun onCreate(savedInstanceState: Bundle?) {

        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        super.onCreate(savedInstanceState)

        val lockEnabled = appSettingsRepository.getBoolean(APP_LOCK_PREF_KEY, false)
        appLockState.value = if (savedInstanceState != null) {
            savedInstanceState.getBoolean("appLockState", lockEnabled)
        } else {
            lockEnabled
        }

        setContent {
            KernelSUTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainContent(activity = this@WebUIActivity, onFinish = { finish() })
                    if (appLockState.value) {
                        AppLockOverlay(
                            activity = this@WebUIActivity,
                            timeoutMillis = appLockTimeout(),
                            onUnlocked = { appLockState.value = false },
                            onAuthFailed = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLockManager.onActivityStart()
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.onActivityStop(appLockTimeout())
        if (appSettingsRepository.getBoolean(APP_LOCK_PREF_KEY, false)) {
            appLockState.value = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("appLockState", appLockState.value)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainContent(activity: FragmentActivity, onFinish: () -> Unit) {
    val moduleId = remember { activity.intent.getStringExtra("id") }
    val webUIState = remember { WebUIState() }
    val moduleViewModel = koinViewModel<ModuleViewModel>()
    val superUserViewModel = koinViewModel<SuperUserViewModel>()
    val settingsRepository = koinInject<AppSettingsRepository>()
    val packageRepository = koinInject<InstalledPackageRepository>()
    val appIconDataSource = koinInject<AppIconDataSource>()
    val webUiRepository = koinInject<WebUiRepository>()
    val monetColorsProvider = koinInject<MonetColorsProvider>()
    val colorsCss = monetColorsProvider.getColorsCss()
    val currentColorsCss = rememberUpdatedState(colorsCss)

    LaunchedEffect(moduleId) {
        if (moduleId == null) {
            onFinish()
            return@LaunchedEffect
        }
        prepareWebView(
            activity,
            moduleId,
            webUIState,
            moduleViewModel,
            superUserViewModel,
            settingsRepository,
            packageRepository,
            appIconDataSource,
            webUiRepository,
            { currentColorsCss.value },
        )
    }

    DisposableEffect(Unit) {
        onDispose { webUIState.dispose() }
    }

    when (val event = webUIState.uiEvent) {
        is WebUIEvent.Error -> {
            LaunchedEffect(event) {
                Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                onFinish()
            }
        }

        is WebUIEvent.Close -> {
            LaunchedEffect(event) { onFinish() }
        }

        else -> {}
    }
    val isLoading = webUIState.uiEvent is WebUIEvent.Loading

    Crossfade(targetState = isLoading, animationSpec = tween(300)) { loading ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            WebUIScreen(webUIState = webUIState)
        }
    }
}
