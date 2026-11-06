package com.originsu.manager.data.sui

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.originsu.manager.data.model.SuiApp
import moe.shizuku.server.IShizukuService

/**
 * Sui's bridge handshake. Only the caller Sui trusts as its manager gets the root server binder,
 * which is the one exposing the management transactions.
 */
enum class SuiAdbRootMode(val flags: Int) {
    OFF(0),
    ONCE(SuiBridge.FLAG_ADB_ROOT_ONCE),
    ALWAYS(SuiBridge.FLAG_ADB_ROOT_ALWAYS);

    companion object {
        fun of(flags: Int): SuiAdbRootMode = when {
            flags and SuiBridge.FLAG_ADB_ROOT_ALWAYS != 0 -> ALWAYS
            flags and SuiBridge.FLAG_ADB_ROOT_ONCE != 0 -> ONCE
            else -> OFF
        }
    }
}

object SuiBridge {
    private const val TRANSACTION_CODE =
        ('_'.code shl 24) or ('S'.code shl 16) or ('U'.code shl 8) or 'I'.code
    private const val SERVICE_NAME = "activity"
    private const val SERVICE_DESCRIPTOR = "android.app.IActivityManager"
    private const val ACTION_GET_BINDER = 2
    private const val BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService"
    private const val TRANSACTION_SET_GLOBAL_SETTINGS = 10007
    private const val TRANSACTION_GET_GLOBAL_SETTINGS = 10008
    private const val TRANSACTION_GET_HIDDEN_UIDS = 107
    private const val TRANSACTION_BATCH_UPDATE_UNCONFIGURED = 10006

    const val FLAG_ALLOWED = 1 shl 1
    const val FLAG_DENIED = 1 shl 2
    const val FLAG_HIDDEN = 1 shl 3
    const val FLAG_ALLOWED_SHELL = 1 shl 4
    const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED or FLAG_HIDDEN or FLAG_ALLOWED_SHELL

    const val FLAG_SHOW_ONLY_SHIZUKU_APPS = 1 shl 0
    const val FLAG_LEGACY_SHIZUKU_BINDER_COMPAT = 1 shl 2
    const val FLAG_KSU_NO_ESCAPE = 1 shl 3
    const val FLAG_ADB_ROOT_ONCE = 1 shl 4
    const val FLAG_ADB_ROOT_ALWAYS = 1 shl 5
    const val MASK_ADB_ROOT = FLAG_ADB_ROOT_ONCE or FLAG_ADB_ROOT_ALWAYS

    /** Sui keeps the permission used for apps without a rule of their own under this pseudo uid. */
    const val UID_DEFAULT_PERMISSION = -1

    private const val TAG = "SuiManagement"

    private var binder: IBinder? = null

    fun service(): IShizukuService? {
        binder?.let {
            if (it.isBinderAlive) {
                return IShizukuService.Stub.asInterface(it)
            }
            binder = null
        }
        val activity = activityBinder() ?: return null
        Log.i(TAG, "activity binder acquired, asking the bridge for the server binder")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.writeInt(ACTION_GET_BINDER)
            activity.transact(TRANSACTION_CODE, data, reply, 0)
            Log.i(TAG, "bridge transact returned")
            reply.readException()
            val received = reply.readStrongBinder() ?: return null
            Log.i(TAG, "server binder received")
            binder = received
            IShizukuService.Stub.asInterface(received)
        } catch (t: Throwable) {
            Log.w(TAG, "bridge handshake failed: $t")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun activityBinder(): IBinder? = runCatching {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        getService.invoke(null, SERVICE_NAME) as? IBinder
    }.getOrNull()

    /** Global (default) settings word; the permission bits live inside it. */
    fun globalSettings(): Int? = call(TRANSACTION_GET_GLOBAL_SETTINGS, null) { it.readInt() }

    /** Writes the global settings word back; `flags` must carry the untouched unrelated bits. */
    fun setGlobalSettings(flags: Int): Boolean =
        call(TRANSACTION_SET_GLOBAL_SETTINGS, { it.writeInt(flags) }, null) != null

    /** The permission Sui applies to apps that have no rule of their own. */
    fun defaultMode(): Int? = runCatching {
        service()?.getFlagsForUid(UID_DEFAULT_PERMISSION, SuiApp.MODE_MASK)
    }.getOrNull()

    /**
     * Sui's own path for changing the default permission: unlike a plain settings write it also
     * refreshes the clients that were relying on the old default.
     */
    fun setDefaultMode(mode: Int): Boolean =
        call(TRANSACTION_BATCH_UPDATE_UNCONFIGURED, { it.writeInt(mode) }, null) != null

    /** Uids Sui hides itself from. */
    fun hiddenUids(): IntArray? {
        val target = serverBinder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR)
            target.transact(TRANSACTION_GET_HIDDEN_UIDS, data, reply, 0)
            reply.readException()
            reply.createIntArray()
        } catch (t: Throwable) {
            Log.w(TAG, "hidden uid query failed: $t")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun call(code: Int, write: ((Parcel) -> Unit)?, read: ((Parcel) -> Int)?): Int? {
        val target = serverBinder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR)
            write?.invoke(data)
            target.transact(code, data, reply, 0)
            reply.readException()
            read?.invoke(reply) ?: 0
        } catch (t: Throwable) {
            Log.w(TAG, "transaction $code failed: $t")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun serverBinder(): IBinder? {
        service()
        return binder
    }
}
