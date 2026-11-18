package com.kazesu.manager.ui.screen.flash

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kazesu.manager.Natives
import com.kazesu.manager.R
import com.kazesu.manager.data.repository.isSoftRebootPreferred
import com.kazesu.manager.ui.LocalUiMode
import com.kazesu.manager.ui.UiMode
import com.kazesu.manager.ui.navigation3.LocalNavigator
import com.kazesu.manager.ui.util.GuardReport
import com.kazesu.manager.ui.util.auditModule
import com.kazesu.manager.ui.util.reboot
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun FlashScreen(flashIt: FlashIt, onBackEnabledChange: (Boolean) -> Unit) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var flashingStatus by rememberSaveable { mutableStateOf(FlashingStatus.FLASHING) }
    var actionInProgress by rememberSaveable { mutableStateOf(false) }
    val needJailbreakWarning = flashIt is FlashIt.FlashBoot && Natives.isLateLoadMode
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = flashIt is FlashIt.FlashModules && isSoftRebootPreferred()
    var flashingEnabled by rememberSaveable {
        mutableStateOf(!needJailbreakWarning && flashIt !is FlashIt.FlashModules)
    }
    // KazeGuard review runs once per FlashModules navigation, before anything installs.
    var guardReports by remember { mutableStateOf<List<GuardReport>>(emptyList()) }
    var guardReviewed by remember { mutableStateOf(flashIt !is FlashIt.FlashModules) }
    var guardConfirmed by remember { mutableStateOf(false) }
    val uiMode = LocalUiMode.current
    val snackbarHost = remember { SnackbarHostState() }
    val backEnabled = flashingStatus != FlashingStatus.FLASHING

    SideEffect { onBackEnabledChange(backEnabled) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !backEnabled,
        onBackCompleted = {},
    )

    fun showMessage(message: String) {
        scope.launch {
            if (uiMode == UiMode.Material) {
                snackbarHost.showSnackbar(message)
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    FlashEffect(
        flashIt = flashIt,
        text = text,
        logContent = logContent,
        onTextUpdate = { text = it },
        onShowRebootChange = { showRebootAction = it },
        onFlashingStatusChange = { flashingStatus = it },
        enabled = flashingEnabled,
        guardConfirmed = guardConfirmed,
    )

    LaunchedEffect(flashIt, guardReviewed) {
        if (flashIt !is FlashIt.FlashModules || guardReviewed) return@LaunchedEffect
        // An in-progress flash (restored after a config change) already passed review.
        if (text.isNotEmpty()) {
            guardReviewed = true
            return@LaunchedEffect
        }
        val flagged = withContext(Dispatchers.IO) {
            flashIt.uris.mapNotNull { auditModule(it) }.filter { it.needsReview }
        }
        guardReports = flagged
        guardReviewed = true
        if (flagged.isEmpty()) {
            flashingEnabled = true
        }
    }

    LaunchedEffect(flashingStatus, flashIt) {
        if (flashingStatus == FlashingStatus.SUCCESS && flashIt is FlashIt.FlashModules) {
            val intent = activity?.intent
            val isFromExternalIntent = intent?.action?.let { action ->
                action == Intent.ACTION_VIEW ||
                        action == Intent.ACTION_SEND ||
                        action == Intent.ACTION_SEND_MULTIPLE
            } ?: false

            if (isFromExternalIntent) {
                delay(1000.milliseconds)
                activity.finish()
            }
        }
    }

    if (guardReports.isNotEmpty()) {
        KazeGuardReviewDialog(
            reports = guardReports,
            onConfirm = {
                guardConfirmed = true
                guardReports = emptyList()
                flashingEnabled = true
            },
            onDismiss = dropUnlessResumed { navigator.pop() },
        )
    }

    val state = FlashUiState(
        text = text,
        showRebootAction = showRebootAction,
        flashingStatus = flashingStatus,
        showJailbreakWarning = needJailbreakWarning && !flashingEnabled,
        rebootLabelRes = if (softReboot) R.string.reboot_soft else R.string.reboot,
        actionInProgress = actionInProgress,
    )
    val actions = FlashScreenActions(
        onBack = dropUnlessResumed { if (backEnabled) navigator.pop() },
        onSaveLog = saveLog(logContent, scope) { showMessage(it) },
        onReboot = {
            actionInProgress = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    reboot(if (softReboot) "soft_reboot" else "")
                }
            }
        },
        onClose = dropUnlessResumed { navigator.pop() },
        onConfirmJailbreakWarning = { flashingEnabled = true },
        onDismissJailbreakWarning = dropUnlessResumed { navigator.pop() },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> FlashScreenMiuix(state, actions)
        UiMode.Material -> FlashScreenMaterial(state, actions, snackbarHost)
    }
}
