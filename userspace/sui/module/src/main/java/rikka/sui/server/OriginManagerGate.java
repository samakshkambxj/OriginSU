/*
 * This file is part of Sui.
 *
 * Sui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sui.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 Sui Contributors
 */

package rikka.sui.server;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import rikka.hidden.compat.PackageManagerApis;

/**
 * The OriginSU manager identity recorded by ksud at /data/adb/ksu/sui/manager.json.
 *
 * Sui's management API was reachable only from the Settings and SystemUI processes. OriginSU
 * manages Sui from its own app, so that exact uid and the signing certificate of its package are
 * the only accepted management caller. The identity file is root-owned; the certificate is
 * re-checked here so a package that merely reuses the uid cannot inherit the privilege.
 */
public final class OriginManagerGate {

    private static final String TAG = "SuiOriginGate";
    private static final String PATH = "/data/adb/ksu/sui/manager.json";
    private static final long CACHE_MS = 2000;
    private static final int GET_SIGNING_CERTIFICATES = 0x08000000;

    private static long loadedAt;
    private static String packageName = "";
    private static int uid = -1;
    private static int previousUid = -1;
    private static String certSha256 = "";

    private OriginManagerGate() {
    }

    private static synchronized void load() {
        if (Process.myUid() != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - loadedAt < CACHE_MS) {
            return;
        }
        loadedAt = now;
        packageName = "";
        uid = -1;
        previousUid = -1;
        certSha256 = "";
        try (FileInputStream input = new FileInputStream(new File(PATH))) {
            byte[] raw = new byte[4096];
            int size = input.read(raw);
            if (size <= 0) {
                return;
            }
            JSONObject json = new JSONObject(new String(raw, 0, size, StandardCharsets.UTF_8));
            if (json.optInt("schema_version") != 1) {
                return;
            }
            packageName = json.optString("package", "");
            uid = json.optInt("uid", -1);
            previousUid = json.optInt("previous_uid", -1);
            certSha256 = json.optString("cert_sha256", "");
        } catch (Throwable t) {
            Log.w(TAG, "Cannot read " + PATH + ": " + t);
        }
    }

    /** True only for the recorded uid, and only while its package still carries the signed APK. */
    public static synchronized boolean accepts(int callingUid) {
        load();
        if (uid < 0 || callingUid != uid) {
            return false;
        }
        return verify(packageName);
    }

    /** True when the named package is the registered manager and still matches its certificate. */
    public static synchronized boolean isManagerPackage(String name) {
        load();
        return !packageName.isEmpty() && packageName.equals(name) && verify(name);
    }

    public static synchronized String[] snapshot() {
        load();
        if (uid < 0 || !verify(packageName)) {
            return new String[] {"", "-1", ""};
        }
        return new String[] {packageName, Integer.toString(uid), certSha256};
    }

    public static synchronized int previousUid() {
        load();
        return previousUid;
    }

    public static synchronized void apply(String name, int registeredUid, String certificate) {
        if (Process.myUid() != 1000) {
            throw new SecurityException("Identity snapshots belong to system_server");
        }
        packageName = name == null ? "" : name;
        uid = registeredUid;
        certSha256 = certificate == null ? "" : certificate;
    }

    private static boolean verify(String name) {
        if (name.isEmpty() || certSha256.isEmpty()) {
            return false;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            PackageInfo info = PackageManagerApis.getPackageInfoNoThrow(name, GET_SIGNING_CERTIFICATES, 0);
            if (info == null || info.applicationInfo == null || info.applicationInfo.uid != uid
                    || info.signingInfo == null) {
                return false;
            }
            Signature[] signers = info.signingInfo.getApkContentsSigners();
            if (signers == null || signers.length == 0) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signers[0].toByteArray());
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return certSha256.equalsIgnoreCase(builder.toString());
        } catch (Throwable t) {
            Log.w(TAG, "Cannot verify " + name + ": " + t);
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
}
