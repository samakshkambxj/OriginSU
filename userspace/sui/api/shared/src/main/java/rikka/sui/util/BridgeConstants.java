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

package rikka.sui.util;

public final class BridgeConstants {

    public static final int TRANSACTION_CODE = ('_' << 24) | ('S' << 16) | ('U' << 8) | 'I';
    public static final String SERVICE_DESCRIPTOR = "android.app.IActivityManager";
    public static final String SERVICE_NAME = "activity";

    public static final int ACTION_SEND_BINDER = 1;
    public static final int ACTION_GET_BINDER = ACTION_SEND_BINDER + 1;
    public static final int ACTION_NOTIFY_FINISHED = ACTION_SEND_BINDER + 2;
    public static final int ACTION_SYNC_UIDS = ACTION_SEND_BINDER + 3;
    public static final int ACTION_DIAGNOSTICS = ACTION_SEND_BINDER + 4;

    public static final int SERVER_UID_ROOT = 0;
    public static final int SERVER_UID_SHELL = 2000;

    private BridgeConstants() {}
}
