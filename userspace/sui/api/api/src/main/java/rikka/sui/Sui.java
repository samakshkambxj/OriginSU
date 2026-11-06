package rikka.sui;

import android.os.IBinder;
import android.os.Parcel;
import rikka.shizuku.Shizuku;
import rikka.shizuku.SystemServiceHelper;
import rikka.sui.util.BridgeConstants;

public class Sui {

    private static IBinder requestBinder() {
        IBinder binder = SystemServiceHelper.getSystemService(BridgeConstants.SERVICE_NAME);
        if (binder == null) return null;

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BridgeConstants.SERVICE_DESCRIPTOR);
            data.writeInt(BridgeConstants.ACTION_GET_BINDER);
            binder.transact(BridgeConstants.TRANSACTION_CODE, data, reply, 0);
            reply.readException();
            IBinder received = reply.readStrongBinder();
            if (received != null) {
                return received;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            data.recycle();
            reply.recycle();
        }
        return null;
    }

    private static volatile boolean isSui;

    public static boolean isSui() {
        // A dead Sui binder must not prevent ShizukuProvider from accepting
        // an ordinary Shizuku binder after the Sui server has exited.
        return isSui && Shizuku.pingBinder();
    }

    /**
     * Request binder from Sui. This method must only be called once.
     *
     * @param packageName Package name of the current process
     * @return If binder is received (this also shows if Sui is installed and if it is working)
     */
    public static boolean init(String packageName) {
        IBinder binder = requestBinder();
        if (binder != null) {
            Shizuku.onBinderReceived(binder, packageName);
            // onBinderReceived may reject a binder that died before publication.
            // A non-null bridge reply alone does not mean Sui was initialized.
            boolean installed = Shizuku.getBinder() == binder && binder.pingBinder();
            isSui = installed;
            return installed;
        }
        isSui = false;
        return false;
    }
}
