package rikka.shizuku.server;

import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_COMPONENT;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_DAEMON;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_DEBUGGABLE;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_NO_CREATE;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_PROCESS_NAME;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_REMOVE;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TAG;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_USE_32_BIT_APP_PROCESS;
import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_VERSION_CODE;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import moe.shizuku.server.IShizukuServiceConnection;
import rikka.hidden.compat.PackageManagerApis;
import rikka.shizuku.ShizukuApiConstants;
import rikka.shizuku.server.util.AbiUtil;
import rikka.shizuku.server.util.Logger;
import rikka.shizuku.server.util.UserHandleCompat;

public abstract class UserServiceManager {

    protected static final Logger LOGGER = new Logger("UserServiceManager");

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Map<String, UserServiceRecord> userServiceRecords = Collections.synchronizedMap(new ArrayMap<>());
    private final Map<String, List<UserServiceRecord>> packageUserServiceRecords =
            Collections.synchronizedMap(new ArrayMap<>());

    public UserServiceManager() {}

    protected long beginUserServiceCapabilityCreation(int uid, int pid) {
        return 0;
    }

    protected boolean isUserServiceCapabilityCurrent(UserServiceRecord record) {
        return true;
    }

    protected boolean finishUserServiceCapabilityCreation(UserServiceRecord record, Runnable publisher) {
        publisher.run();
        return true;
    }

    protected void abortUserServiceCapabilityCreation(UserServiceRecord record) {}

    protected boolean requiresUserServiceProcessRegistration() {
        return false;
    }

    public PackageInfo ensureCallingPackageForUserService(String packageName, int appId, int userId) {
        @SuppressLint("UnsafeOptInUsageError")
        PackageInfo packageInfo = PackageManagerApis.getPackageInfoNoThrow(
                packageName, 0x00002000 /*PackageManager.MATCH_UNINSTALLED_PACKAGES*/, userId);
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            throw new SecurityException("unable to find package " + packageName);
        }

        if (UserHandleCompat.getAppId(packageInfo.applicationInfo.uid) != appId) {
            throw new SecurityException("package " + packageName + " is not owned by " + appId);
        }
        return packageInfo;
    }

    public int removeUserService(IShizukuServiceConnection conn, Bundle options) {
        ComponentName componentName = Objects.requireNonNull(
                getParcelable(options, USER_SERVICE_ARG_COMPONENT, ComponentName.class), "component is null");

        int uid = Binder.getCallingUid();
        int appId = UserHandleCompat.getAppId(uid);
        int userId = UserHandleCompat.getUserId(uid);

        String packageName = componentName.getPackageName();
        ensureCallingPackageForUserService(packageName, appId, userId);

        String className = Objects.requireNonNull(componentName.getClassName(), "class is null");
        String tag = options.getString(USER_SERVICE_ARG_TAG);
        String key = packageName + ":" + (tag != null ? tag : className);

        // API < 13.1.4 will not send USER_SERVICE_ARG_REMOVE, true by default
        boolean remove = true;
        if (options.containsKey(USER_SERVICE_ARG_REMOVE)) {
            remove = options.getBoolean(USER_SERVICE_ARG_REMOVE);
        }

        synchronized (this) {
            UserServiceRecord record = getUserServiceRecordLocked(key);
            if (record == null) return 1;
            if (remove) {
                removeUserServiceLocked(record);
            } else {
                record.callbacks.unregister(conn);
            }
        }
        return 0;
    }

    private void removeUserServiceLocked(UserServiceRecord record) {
        removeUserServiceLocked(record, false);
    }

    private void removeUserServiceLocked(UserServiceRecord record, boolean securityRevoke) {
        if (userServiceRecords.values().remove(record)) {
            if (record.capabilityPending) {
                record.capabilityPending = false;
                abortUserServiceCapabilityCreation(record);
            }
            if (securityRevoke) {
                record.securityDestroy();
            } else {
                record.destroy();
            }
            onUserServiceRecordRemoved(record);
        }
    }

    public int addUserService(IShizukuServiceConnection conn, Bundle options, int callingApiVersion, boolean onetime) {
        Objects.requireNonNull(conn, "connection is null");
        Objects.requireNonNull(options, "options is null");

        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        int appId = UserHandleCompat.getAppId(uid);
        int userId = UserHandleCompat.getUserId(uid);

        ComponentName componentName = Objects.requireNonNull(
                getParcelable(options, USER_SERVICE_ARG_COMPONENT, ComponentName.class), "component is null");
        String packageName = Objects.requireNonNull(componentName.getPackageName(), "package is null");
        PackageInfo packageInfo = ensureCallingPackageForUserService(packageName, appId, userId);

        String className = Objects.requireNonNull(componentName.getClassName(), "class is null");
        String sourceDir = Objects.requireNonNull(packageInfo.applicationInfo.sourceDir, "apk path is null");

        int versionCode = options.getInt(USER_SERVICE_ARG_VERSION_CODE, 1);
        String tag = options.getString(USER_SERVICE_ARG_TAG);
        String processNameSuffix = options.getString(USER_SERVICE_ARG_PROCESS_NAME);
        boolean debug = options.getBoolean(USER_SERVICE_ARG_DEBUGGABLE, false);
        boolean noCreate = options.getBoolean(USER_SERVICE_ARG_NO_CREATE, false);
        boolean daemon = !onetime && options.getBoolean(USER_SERVICE_ARG_DAEMON, true);
        boolean use32Bits = options.getBoolean(USER_SERVICE_ARG_USE_32_BIT_APP_PROCESS, false);
        String key = packageName + ":" + (tag != null ? tag : className);

        synchronized (this) {
            UserServiceRecord record = getUserServiceRecordLocked(key);
            if (noCreate) {
                if (record != null) {
                    record.callbacks.register(conn);

                    if (record.service != null && record.service.pingBinder()) {
                        record.broadcastBinderReceived();

                        if (callingApiVersion >= 13) {
                            return record.versionCode;
                        } else {
                            return 0;
                        }
                    }
                }

                if (callingApiVersion >= 13) {
                    return -1;
                } else {
                    return 1;
                }
            } else {
                UserServiceRecord newRecord =
                        createUserServiceRecordIfNeededLocked(record, key, versionCode, daemon, packageInfo, uid, pid);
                newRecord.callbacks.register(conn);

                if (newRecord.service != null && newRecord.service.pingBinder()) {
                    newRecord.broadcastBinderReceived();
                } else if (!newRecord.starting) {
                    newRecord.setStartingTimeout(DateUtils.SECOND_IN_MILLIS * 30);

                    Runnable runnable = () -> startUserService(
                            newRecord,
                            key,
                            newRecord.token,
                            packageName,
                            className,
                            processNameSuffix,
                            uid,
                            use32Bits,
                            debug);
                    executor.execute(runnable);
                    return 0;
                }
                return 0;
            }
        }
    }

    private UserServiceRecord getUserServiceRecordLocked(String key) {
        return userServiceRecords.get(key);
    }

    @SuppressWarnings("deprecation")
    private static <T extends Parcelable> T getParcelable(Bundle bundle, String key, Class<T> clazz) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelable(key, clazz);
        }
        return bundle.getParcelable(key);
    }

    private UserServiceRecord createUserServiceRecordIfNeededLocked(
            UserServiceRecord record,
            String key,
            int versionCode,
            boolean daemon,
            PackageInfo packageInfo,
            int ownerUid,
            int ownerPid) {

        if (record != null) {
            if (record.versionCode != versionCode) {
                LOGGER.v(
                        "Remove service record %s (%s) because version code not matched (old=%d, new=%d)",
                        key, record.token, record.versionCode, versionCode);
            } else if (!record.starting && (record.service == null || !record.service.pingBinder())) {
                LOGGER.v("Service in record %s (%s) is dead", key, record.token);
            } else {
                LOGGER.i("Found existing service record %s (%s)", key, record.token);

                if (record.daemon != daemon) {
                    record.setDaemon(daemon);
                }
                return record;
            }

            removeUserServiceLocked(record);
        }

        long capabilityEpoch = beginUserServiceCapabilityCreation(ownerUid, ownerPid);
        record = new UserServiceRecord(versionCode, daemon, ownerUid, ownerPid, capabilityEpoch) {

            @Override
            public void removeSelf() {
                synchronized (UserServiceManager.this) {
                    removeUserServiceLocked(this);
                }
            }
        };

        String packageName = packageInfo.packageName;
        List<UserServiceRecord> list = packageUserServiceRecords.get(packageName);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<>());
            packageUserServiceRecords.put(packageName, list);
        }
        list.add(record);

        onUserServiceRecordCreated(record, packageInfo);

        userServiceRecords.put(key, record);
        LOGGER.i(
                "New service record %s (%s): version=%d, daemon=%s, apk=%s",
                key, record.token, versionCode, Boolean.toString(daemon), packageInfo.applicationInfo.sourceDir);
        return record;
    }

    private void startUserService(
            UserServiceRecord record,
            String key,
            String token,
            String packageName,
            String classname,
            String processNameSuffix,
            int callingUid,
            boolean use32Bits,
            boolean debug) {

        LOGGER.v("Starting process for service record %s (%s)...", key, token);

        synchronized (this) {
            if (!userServiceRecords.containsValue(record) || !isUserServiceCapabilityCurrent(record)) {
                removeUserServiceLocked(record, true);
                LOGGER.w("Skip stale user service start for %s (%s)", key, token);
                return;
            }
        }

        String cmd = getUserServiceStartCmd(
                record,
                key,
                token,
                packageName,
                classname,
                processNameSuffix,
                callingUid,
                use32Bits && AbiUtil.has32Bit(),
                debug);
        int exitCode;
        try {
            java.lang.Process process = Runtime.getRuntime().exec("sh");
            OutputStream os = process.getOutputStream();
            os.write(cmd.getBytes());
            os.flush();
            os.close();

            exitCode = process.waitFor();
        } catch (Throwable e) {
            throw new IllegalStateException(e.getMessage());
        }
        if (exitCode != 0) {
            throw new IllegalStateException("sh exited with " + exitCode);
        }
    }

    public abstract String getUserServiceStartCmd(
            UserServiceRecord record,
            String key,
            String token,
            String packageName,
            String classname,
            String processNameSuffix,
            int callingUid,
            boolean use32Bits,
            boolean debug);

    private UserServiceRecord findUserServiceByTokenLocked(String token) {
        Map.Entry<String, UserServiceRecord> entry = null;
        for (Map.Entry<String, UserServiceRecord> e : userServiceRecords.entrySet()) {
            if (e.getValue().token.equals(token)) {
                entry = e;
                break;
            }
        }

        if (entry == null) {
            throw new IllegalArgumentException("unable to find token " + token);
        }

        return entry.getValue();
    }

    private void sendUserServiceLocked(IBinder binder, String token, int callingPid) {
        UserServiceRecord record = findUserServiceByTokenLocked(token);

        LOGGER.v("Received binder for service record %s", token);

        if (requiresUserServiceProcessRegistration()) {
            if (!record.hasProcessIdentity() || record.getProcessPid() != callingPid) {
                throw new SecurityException("user service process was not registered for token " + token);
            }
        } else if (!record.hasProcessIdentity()) {
            record.setProcessIdentity(callingPid, -1);
        }

        if (record.capabilityPending) {
            record.capabilityPending = false;
            boolean committed = finishUserServiceCapabilityCreation(record, () -> record.setBinder(binder));
            if (!committed) {
                removeUserServiceLocked(record, true);
                throw new SecurityException("permission changed while starting user service");
            }
        } else {
            record.setBinder(binder);
        }
    }

    public void attachUserService(IBinder binder, Bundle options) {
        Objects.requireNonNull(binder, "binder is null");
        String token =
                Objects.requireNonNull(options.getString(ShizukuApiConstants.USER_SERVICE_ARG_TOKEN), "token is null");
        int callingPid = Binder.getCallingPid();

        synchronized (this) {
            sendUserServiceLocked(binder, token, callingPid);
        }
    }

    public boolean registerUserServiceProcess(String token, int pid, int pgid) {
        synchronized (this) {
            UserServiceRecord record;
            try {
                record = findUserServiceByTokenLocked(token);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (!record.registerProcessIdentity(pid, pgid)) {
                return false;
            }
            if (!isUserServiceCapabilityCurrent(record)) {
                removeUserServiceLocked(record, true);
                return false;
            }
            return true;
        }
    }

    public void onUserServiceRecordCreated(UserServiceRecord record, PackageInfo packageInfo) {}

    public void onUserServiceRecordRemoved(UserServiceRecord record) {}

    public void removeUserServicesForPackage(String packageName) {
        List<UserServiceRecord> list = packageUserServiceRecords.remove(packageName);
        if (list != null) {
            List<UserServiceRecord> snapshot;
            synchronized (list) {
                snapshot = new ArrayList<>(list);
            }
            synchronized (this) {
                for (UserServiceRecord record : snapshot) {
                    removeUserServiceLocked(record);
                    LOGGER.i("Remove user service %s for package %s", record.token, packageName);
                }
            }
        }
    }

    public void revokeUserServicesForPackage(String packageName) {
        List<UserServiceRecord> list = packageUserServiceRecords.remove(packageName);
        if (list == null) {
            return;
        }
        List<UserServiceRecord> snapshot;
        synchronized (list) {
            snapshot = new ArrayList<>(list);
        }
        synchronized (this) {
            for (UserServiceRecord record : snapshot) {
                removeUserServiceLocked(record, true);
                LOGGER.i("Security-revoked user service %s for package %s", record.token, packageName);
            }
        }
    }

    public boolean hasUserServicesForPackage(String packageName) {
        List<UserServiceRecord> list = packageUserServiceRecords.get(packageName);
        return list != null && !list.isEmpty();
    }

    public void revokeUserServicesForUid(int uid) {
        List<UserServiceRecord> snapshot = new ArrayList<>();
        synchronized (this) {
            for (UserServiceRecord record : new ArrayList<>(userServiceRecords.values())) {
                if (record.ownerUid == uid) {
                    snapshot.add(record);
                }
            }
            for (UserServiceRecord record : snapshot) {
                removeUserServiceLocked(record, true);
                LOGGER.i("Security-revoked user service %s for uid %d", record.token, uid);
            }
        }
    }

    public boolean hasUserServicesForUid(int uid) {
        synchronized (this) {
            for (UserServiceRecord record : userServiceRecords.values()) {
                if (record.ownerUid == uid) {
                    return true;
                }
            }
            return false;
        }
    }
}
