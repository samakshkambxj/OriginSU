package com.originsu.manager.magica;

import static com.originsu.manager.magica.AppZygotePreload.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        var action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"com.originsu.manager.magica.LAUNCH".equals(action)) {
            return;
        }
        try {
            context.startService(new Intent(context, MagicaService.class));
            Log.i(TAG, "MagicaService started from boot action: " + action);
        } catch (Throwable e) {

            Log.e(TAG, "Failed to start MagicaService from boot action: " + action, e);
        }
        // Restore su grant toast + blocking root prompt monitors after reboot.
        // Each service self-exits when its toggle is off, so starting them
        // unconditionally here is safe and avoids reading DataStore from a
        // receiver. Previously nothing restarted them, so no popup appeared
        // until the user opened the manager and toggled the switch again.
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    new Intent(context, com.originsu.manager.data.grant.GrantToastService.class)
                            .setAction("com.originsu.manager.action.GRANT_TOAST_START"));
        } catch (Throwable e) {
            Log.e(TAG, "Failed to restore GrantToastService from boot action: " + action, e);
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    new Intent(context, com.originsu.manager.data.su.SuRequestPollService.class)
                            .setAction("com.originsu.manager.action.SU_REQUEST_START"));
        } catch (Throwable e) {
            Log.e(TAG, "Failed to restore SuRequestPollService from boot action: " + action, e);
        }
    }
}
