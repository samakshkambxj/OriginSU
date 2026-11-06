package me.yuki.suirequester;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import rikka.shizuku.Shizuku;

/** Asks Sui for a Shizuku permission so the OriginSU manager host can be exercised for real. */
public class MainActivity extends Activity {
    private static final String TAG = "SuiRequester";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "asking Sui for permission");
        Shizuku.addRequestPermissionResultListener((requestCode, grantResult) ->
                Log.i(TAG, "permission result: code=" + requestCode + " granted=" + grantResult));
        if (Shizuku.isPreV11()) {
            Log.w(TAG, "pre-v11 Shizuku is not supported");
            return;
        }
        try {
            Shizuku.requestPermission(0);
        } catch (Throwable t) {
            Log.w(TAG, "requestPermission failed: " + t);
        }
    }
}
