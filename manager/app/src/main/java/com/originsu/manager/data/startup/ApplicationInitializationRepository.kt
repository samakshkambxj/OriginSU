package com.originsu.manager.data.startup

import android.annotation.SuppressLint
import android.app.Application
import android.system.Os
import coil.Coil
import coil.ImageLoader
import com.originsu.manager.data.appearance.AppIconRepository
import com.originsu.manager.data.flash.FlashRepository
import com.originsu.manager.data.grant.GrantToastRepository
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.data.shortcuts.AppShortcutsRepository
import com.originsu.manager.data.su.SuRequestRepository
import com.originsu.manager.data.theme.MonetCompatColorSource
import com.topjohnwu.superuser.internal.MainShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class ApplicationInitializationRepository(
    private val application: Application,
    private val imageLoader: ImageLoader,
    private val applicationScope: CoroutineScope,
    private val flashRepository: FlashRepository,
    private val ksuCliRepository: KsuCliRepository,
    private val monetCompatColorSource: MonetCompatColorSource,
    private val grantToastRepository: GrantToastRepository,
    private val suRequestRepository: SuRequestRepository,
    private val appShortcutsRepository: AppShortcutsRepository,
    private val appIconRepository: AppIconRepository,
) {
    @SuppressLint("RestrictedApi")
    suspend fun initialize() {
        MainShell.setBuilder(ksuCliRepository.generateMainShellBuilder())
        monetCompatColorSource.initialize()
        Coil.setImageLoader(imageLoader)
        File(application.dataDir, "webroot").mkdirs()
        Os.setenv("TMPDIR", application.cacheDir.absolutePath, true)
        applicationScope.launch {
            runCatching { flashRepository.getInstallEnvironment() }
        }
        // Restore toast + blocking-prompt monitors after reboot / process restart.
        // Previously they only started on toggle, so after a reboot no popup
        // ever appeared until the user toggled the switch again. Start the
        // toast monitor whenever enabled: GrantToastService falls back to a
        // notification when overlay permission is missing.
        runCatching {
            if (grantToastRepository.isToastEnabled()) {
                grantToastRepository.startMonitor()
            }
        }
        // Re-apply saved shortcut icons: dynamic shortcuts can be cleared by
        // the launcher / system, so the toggle looked "not working" after restart.
        runCatching { appShortcutsRepository.applySaved() }
        // Re-apply the chosen launcher icon: component states reset to the
        // manifest defaults on app update, which would silently revert the icon.
        runCatching { appIconRepository.applySaved() }
        runCatching {
            if (suRequestRepository.isPromptEnabled()) {
                suRequestRepository.syncToKernel()
                suRequestRepository.startPolling()
            }
        }
    }
}
