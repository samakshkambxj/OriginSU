/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (c) 2026 Sui Contributors
 */

package rikka.sui.server;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.ServiceManager;
import org.json.JSONObject;
import rikka.sui.util.BridgeConstants;

public final class SuiDiagnostics {
    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 0) throw new SecurityException("Root is required");
        int uid = Integer.parseInt(args[0]);
        JSONObject result = new JSONObject();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                "/data/adb/sui/sui.db", null, SQLiteDatabase.OPEN_READONLY);
                Cursor rows = db.rawQuery("SELECT uid, flags FROM uid_configs WHERE uid IN (?, -1)",
                        new String[] {Integer.toString(uid)})) {
            result.put("database_read", true);
            result.put("explicit_flags", JSONObject.NULL);
            result.put("default_flags", 0);
            while (rows.moveToNext()) {
                result.put(rows.getInt(0) == -1 ? "default_flags" : "explicit_flags", rows.getInt(1));
            }
        } catch (Throwable e) {
            result.put("database_read", false);
            result.put("database_error", e.toString());
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            IBinder bridge = ServiceManager.getService(BridgeConstants.SERVICE_NAME);
            if (bridge == null) throw new IllegalStateException("Activity service unavailable");
            data.writeInterfaceToken(BridgeConstants.SERVICE_DESCRIPTOR);
            data.writeInt(BridgeConstants.ACTION_DIAGNOSTICS);
            data.writeInt(uid);
            if (!bridge.transact(BridgeConstants.TRANSACTION_CODE, data, reply, 0)) {
                throw new IllegalStateException("Bridge did not handle diagnostics");
            }
            reply.readException();
            result.put("bridge", new JSONObject(reply.readString()));
        } catch (Throwable e) {
            result.put("bridge", JSONObject.NULL);
            result.put("bridge_error", e.toString());
        } finally {
            data.recycle();
            reply.recycle();
        }
        System.out.println(result);
        System.exit(0);
    }
}
