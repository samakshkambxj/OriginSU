package rikka.shizuku.server;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.SystemProperties;
import android.system.Os;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IShizukuServiceConnection;
import rikka.hidden.compat.PermissionManagerApis;
import rikka.rish.RishConfig;
import rikka.rish.RishService;
import rikka.shizuku.ShizukuApiConstants;
import rikka.shizuku.server.api.RemoteProcessHolder;
import rikka.shizuku.server.util.Logger;
import rikka.shizuku.server.util.OsUtils;
import rikka.shizuku.server.util.UserHandleCompat;

public abstract class Service<
                UserServiceMgr extends UserServiceManager,
                ClientMgr extends ClientManager<ConfigMgr>,
                ConfigMgr extends ConfigManager>
        extends IShizukuService.Stub {

    private final UserServiceMgr userServiceManager;
    private final ConfigMgr configManager;
    private final ClientMgr clientManager;
    private final RishService rishService;
    private final ConcurrentHashMap<Integer, Set<RemoteProcessHolder>> remoteProcessHolders = new ConcurrentHashMap<>();

    protected static final Logger LOGGER = new Logger("Service");

    public Service() {
        RishConfig.init(ShizukuApiConstants.BINDER_DESCRIPTOR, 30000);

        userServiceManager = onCreateUserServiceManager();
        configManager = onCreateConfigManager();
        clientManager = onCreateClientManager();
        rishService = new RishService() {

            @Override
            public void enforceCallingPermission(String func) {
                Service.this.enforceCallingPermission(func);
            }

            @Override
            protected long beginCapabilityCreation(String kind, int uid, int pid) {
                return Service.this.beginCapabilityCreation(kind, uid, pid);
            }

            @Override
            protected boolean finishCapabilityCreation(String kind, int uid, int pid, long epoch, Runnable publisher) {
                return Service.this.finishCapabilityCreation(kind, uid, pid, epoch, publisher);
            }

            @Override
            protected void abortCapabilityCreation(String kind, int uid, int pid, long epoch) {
                Service.this.abortCapabilityCreation(kind, uid, pid, epoch);
            }

            @Override
            protected IBinder getClientBinder(int uid, int pid) {
                ClientRecord clientRecord = clientManager.findClient(uid, pid);
                return clientRecord != null ? clientRecord.client.asBinder() : null;
            }
        };
    }

    public abstract UserServiceMgr onCreateUserServiceManager();

    public abstract ClientMgr onCreateClientManager();

    public abstract ConfigMgr onCreateConfigManager();

    public final UserServiceMgr getUserServiceManager() {
        return userServiceManager;
    }

    public final ClientMgr getClientManager() {
        return clientManager;
    }

    public ConfigMgr getConfigManager() {
        return configManager;
    }

    protected final boolean hasRishHostForClient(int pid) {
        return rishService.hasHostForClient(pid);
    }

    protected final void revokeRishHostForClient(int pid) {
        rishService.revokeHostForClient(pid);
    }

    public boolean checkCallerManagerPermission(String func, int callingUid, int callingPid) {
        return false;
    }

    public final void enforceManagerPermission(String func) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingPid == Os.getpid()) {
            return;
        }

        if (checkCallerManagerPermission(func, callingUid, callingPid)) {
            return;
        }

        String msg = "Permission Denial: " + func + " from pid=" + Binder.getCallingPid() + " is not manager ";
        LOGGER.w(msg);
        throw new SecurityException(msg);
    }

    public boolean checkCallerPermission(
            String func, int callingUid, int callingPid, @Nullable ClientRecord clientRecord) {
        return false;
    }

    protected boolean isClientAuthorized(ClientRecord clientRecord) {
        return clientRecord.allowed;
    }

    protected long beginCapabilityCreation(String kind, int uid, int pid) {
        return 0;
    }

    protected boolean finishCapabilityCreation(String kind, int uid, int pid, long epoch, Runnable publisher) {
        publisher.run();
        return true;
    }

    protected void abortCapabilityCreation(String kind, int uid, int pid, long epoch) {}

    protected final boolean hasRemoteProcessesForUid(int uid) {
        synchronized (remoteProcessHolders) {
            Set<RemoteProcessHolder> holders = remoteProcessHolders.get(uid);
            return holders != null && !holders.isEmpty();
        }
    }

    protected final void revokeRemoteProcessesForUid(int uid) {
        Set<RemoteProcessHolder> holders;
        synchronized (remoteProcessHolders) {
            holders = remoteProcessHolders.remove(uid);
        }
        if (holders == null) {
            return;
        }
        for (RemoteProcessHolder holder : holders) {
            try {
                holder.destroy();
            } catch (Throwable e) {
                LOGGER.w(e, "failed to revoke remote process for uid %d", uid);
            }
        }
    }

    private void addRemoteProcessHolder(int uid, RemoteProcessHolder holder, AtomicBoolean destroyed) {
        synchronized (remoteProcessHolders) {
            if (destroyed.get()) {
                return;
            }
            Set<RemoteProcessHolder> holders = remoteProcessHolders.get(uid);
            if (holders == null) {
                holders = Collections.newSetFromMap(new ConcurrentHashMap<RemoteProcessHolder, Boolean>());
                remoteProcessHolders.put(uid, holders);
            }
            holders.add(holder);
        }
    }

    private void removeRemoteProcessHolder(int uid, RemoteProcessHolder holder) {
        synchronized (remoteProcessHolders) {
            Set<RemoteProcessHolder> holders = remoteProcessHolders.get(uid);
            if (holders != null) {
                holders.remove(holder);
                if (holders.isEmpty()) {
                    remoteProcessHolders.remove(uid);
                }
            }
        }
    }

    public final void enforceCallingPermission(String func) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingUid == OsUtils.getUid()) {
            return;
        }

        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);

        if (checkCallerPermission(func, callingUid, callingPid, clientRecord)) {
            return;
        }

        if (clientRecord == null) {
            String msg =
                    "Permission Denial: " + func + " from pid=" + Binder.getCallingPid() + " is not an attached client";
            LOGGER.w(msg);
            throw new SecurityException(msg);
        }

        if (!isClientAuthorized(clientRecord)) {
            String msg = "Permission Denial: " + func + " from pid=" + Binder.getCallingPid() + " requires permission";
            LOGGER.w(msg);
            throw new SecurityException(msg);
        }
    }

    public final void transactRemote(Parcel data, Parcel reply, int flags) throws RemoteException {
        enforceCallingPermission("transactRemote");

        IBinder targetBinder = data.readStrongBinder();
        int targetCode = data.readInt();
        int targetFlags;

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);

        if (clientRecord != null && clientRecord.apiVersion >= 13) {
            targetFlags = data.readInt();
        } else {
            targetFlags = flags;
        }

        LOGGER.d(
                "transact: uid=%d, descriptor=%s, code=%d",
                Binder.getCallingUid(), targetBinder.getInterfaceDescriptor(), targetCode);

        // Strip the Shizuku target binder/code/flags header for every target,
        // including local Java Binder. Binder.transact resets dataPosition to 0,
        // so passing the original Parcel would make onTransact read that header.
        Parcel newData = Parcel.obtain();
        try {
            try {
                newData.appendFrom(data, data.dataPosition(), data.dataAvail());
            } catch (Throwable tr) {
                LOGGER.w(tr, "appendFrom");
                return;
            }
            long id = Binder.clearCallingIdentity();
            try {
                targetBinder.transact(targetCode, newData, reply, targetFlags);
            } finally {
                // A target onTransact may throw; never leak its cleared identity
                // into subsequent Binder calls handled by this thread.
                Binder.restoreCallingIdentity(id);
            }
        } finally {
            newData.recycle();
        }
    }

    @Override
    public final int getVersion() {
        enforceCallingPermission("getVersion");
        return ShizukuApiConstants.SERVER_VERSION;
    }

    @Override
    public final int getUid() {
        enforceCallingPermission("getUid");
        return Os.getuid();
    }

    @Override
    public final int checkPermission(String permission) throws RemoteException {
        enforceCallingPermission("checkPermission");
        return PermissionManagerApis.checkPermission(permission, Os.getuid());
    }

    @Override
    public final String getSELinuxContext() {
        enforceCallingPermission("getSELinuxContext");

        try {
            return SELinux.getContext();
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }

    @Override
    public final String getSystemProperty(String name, String defaultValue) {
        enforceCallingPermission("getSystemProperty");

        try {
            return SystemProperties.get(name, defaultValue);
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }

    @Override
    public final void setSystemProperty(String name, String value) {
        enforceCallingPermission("setSystemProperty");

        try {
            SystemProperties.set(name, value);
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }

    @Override
    public final int removeUserService(IShizukuServiceConnection conn, Bundle options) {
        enforceCallingPermission("removeUserService");

        return userServiceManager.removeUserService(conn, options);
    }

    @Override
    public final int addUserService(IShizukuServiceConnection conn, Bundle options) {
        enforceCallingPermission("addUserService");

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        int callingApiVersion;
        boolean onetime;

        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);
        if (clientRecord == null) {
            callingApiVersion = ShizukuApiConstants.SERVER_VERSION;
            onetime = false;
        } else {
            callingApiVersion = clientRecord.apiVersion;
            onetime = clientRecord.onetime;
        }
        return userServiceManager.addUserService(conn, options, callingApiVersion, onetime);
    }

    @Override
    public void attachUserService(IBinder binder, Bundle options) {
        userServiceManager.attachUserService(binder, options);
    }

    @Override
    public final boolean checkSelfPermission() {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return true;
        }

        return isClientAuthorized(clientManager.requireClient(callingUid, callingPid));
    }

    @Override
    public final void requestPermission(int requestCode) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        int userId = UserHandleCompat.getUserId(callingUid);

        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return;
        }

        ClientRecord clientRecord = clientManager.requireClient(callingUid, callingPid);

        if (isClientAuthorized(clientRecord)) {
            clientRecord.dispatchRequestPermissionResult(requestCode, true);
            return;
        }

        ConfigPackageEntry entry = configManager.find(callingUid);
        if (entry != null && entry.isDenied()) {
            clientRecord.dispatchRequestPermissionResult(requestCode, false);
            return;
        }

        showPermissionConfirmation(requestCode, clientRecord, callingUid, callingPid, userId);
    }

    public abstract void showPermissionConfirmation(
            int requestCode, @NonNull ClientRecord clientRecord, int callingUid, int callingPid, int userId);

    @Override
    public final boolean shouldShowRequestPermissionRationale() {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return true;
        }

        clientManager.requireClient(callingUid, callingPid);

        ConfigPackageEntry entry = configManager.find(callingUid);
        return entry != null && entry.isDenied();
    }

    @Override
    public final IRemoteProcess newProcess(String[] cmd, String[] env, String dir) {
        enforceCallingPermission("newProcess");

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        long capabilityEpoch = beginCapabilityCreation("remote-process", callingUid, callingPid);
        boolean epochFinished = false;
        boolean capabilityPublished = false;
        RemoteProcessHolder holder = null;

        try {
            LOGGER.d(
                    "newProcess: uid=%d, cmd=%s, env=%s, dir=%s",
                    callingUid, Arrays.toString(cmd), Arrays.toString(env), dir);

            ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);
            IBinder token = clientRecord != null ? clientRecord.client.asBinder() : null;
            String[] actualCmd = cmd;
            boolean processGroup = false;
            File setsid = new File("/system/bin/setsid");
            if (setsid.canExecute()) {
                actualCmd = new String[cmd.length + 1];
                actualCmd[0] = setsid.getAbsolutePath();
                System.arraycopy(cmd, 0, actualCmd, 1, cmd.length);
                processGroup = true;
            }
            java.lang.Process process = Runtime.getRuntime().exec(actualCmd, env, dir != null ? new File(dir) : null);

            AtomicBoolean holderDestroyed = new AtomicBoolean();
            AtomicReference<RemoteProcessHolder> holderRef = new AtomicReference<>();
            holder = new RemoteProcessHolder(process, token, processGroup, () -> {
                holderDestroyed.set(true);
                RemoteProcessHolder current = holderRef.get();
                if (current != null) {
                    removeRemoteProcessHolder(callingUid, current);
                }
            });
            holderRef.set(holder);
            RemoteProcessHolder candidate = holder;
            boolean committed;
            try {
                committed = finishCapabilityCreation(
                        "remote-process",
                        callingUid,
                        callingPid,
                        capabilityEpoch,
                        () -> addRemoteProcessHolder(callingUid, candidate, holderDestroyed));
            } finally {
                // finishCapabilityCreation owns and consumes the epoch lease once invoked,
                // including its rejection and exceptional paths.
                epochFinished = true;
            }
            if (!committed) {
                throw new SecurityException("permission changed while creating remote process");
            }
            capabilityPublished = true;
            return holder;
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            if (!epochFinished) {
                abortCapabilityCreation("remote-process", callingUid, callingPid, capabilityEpoch);
            }
            if (!capabilityPublished && holder != null) {
                holder.destroy();
            }
        }
    }

    @CallSuper
    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == ShizukuApiConstants.BINDER_TRANSACTION_transact) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR);
            transactRemote(data, reply, flags);
            return true;
        } else if (code == 14 /* attachApplication <= v12 */) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR);
            IBinder binder = data.readStrongBinder();
            String packageName = data.readString();
            Bundle args = new Bundle();
            args.putString(ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME, packageName);
            args.putInt(ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION, -1);
            attachApplication(IShizukuApplication.Stub.asInterface(binder), args);
            reply.writeNoException();
            return true;
        } else if (rishService.onTransact(code, data, reply, flags)) {
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
}
