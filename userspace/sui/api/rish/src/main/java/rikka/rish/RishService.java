package rikka.rish;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class RishService {

    private static final String TAG = "RishService";

    private static final Map<Integer, RishHost> HOSTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> EXIT_CODES = new ConcurrentHashMap<>();

    private static final boolean IS_ROOT = Os.getuid() == 0;

    private static RishHost putHost(int pid, RishHost host) {
        synchronized (HOSTS) {
            return HOSTS.put(pid, host);
        }
    }

    private static RishHost removeHost(int pid) {
        synchronized (HOSTS) {
            return HOSTS.remove(pid);
        }
    }

    private static boolean removeHost(int pid, RishHost expected) {
        synchronized (HOSTS) {
            if (HOSTS.get(pid) != expected) {
                return false;
            }
            HOSTS.remove(pid);
            return true;
        }
    }

    private static void putExitCode(int pid, int exitCode) {
        synchronized (EXIT_CODES) {
            EXIT_CODES.put(pid, exitCode);
        }
    }

    private static void removeExitCode(int pid, int expected) {
        synchronized (EXIT_CODES) {
            Integer current = EXIT_CODES.get(pid);
            if (current != null && current == expected) {
                EXIT_CODES.remove(pid);
            }
        }
    }

    private void createHost(
            String[] args,
            String[] env,
            String dir,
            byte tty,
            ParcelFileDescriptor stdin,
            ParcelFileDescriptor stdout,
            ParcelFileDescriptor stderr) {

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        long capabilityEpoch = beginCapabilityCreation("rish", callingUid, callingPid);
        boolean epochFinished = false;
        AtomicBoolean capabilityPublished = new AtomicBoolean();

        // Termux app set PATH and LD_PRELOAD to Termux's internal path.
        // Adb does not have sufficient permissions to access such places.

        // Under adb, users need to set RISH_PRESERVE_ENV=1 to preserve env.
        // Under root, keep env unless RISH_PRESERVE_ENV=0 is set.

        boolean allowEnv = IS_ROOT;
        for (String e : env) {
            if ("RISH_PRESERVE_ENV=1".equals(e)) {
                allowEnv = true;
                break;
            } else if ("RISH_PRESERVE_ENV=0".equals(e)) {
                allowEnv = false;
                break;
            }
        }
        if (!allowEnv) {
            env = null;
        }

        RishHost host = new RishHost(args, env, dir, tty, stdin, stdout, stderr);
        IBinder clientBinder = getClientBinder(callingUid, callingPid);
        AtomicBoolean ownerDead = new AtomicBoolean();
        IBinder.DeathRecipient ownerDeath = () -> {
            if (!ownerDead.compareAndSet(false, true)) {
                return;
            }
            EXIT_CODES.remove(callingPid);
            RishHost current = removeHost(callingPid);
            if (current != null) {
                current.destroy();
            }
            if (current != host) {
                host.destroy();
            }
        };
        try {
            host.start();
            Log.d(TAG, "Forked " + host.getPid());

            if (clientBinder != null) {
                try {
                    clientBinder.linkToDeath(ownerDeath, 0);
                } catch (Throwable e) {
                    ownerDeath.binderDied();
                }
            }
            if (ownerDead.get()) {
                throw new SecurityException("client binder died while creating rish host");
            }

            boolean committed;
            try {
                committed = finishCapabilityCreation("rish", callingUid, callingPid, capabilityEpoch, () -> {
                    if (ownerDead.get()) {
                        return;
                    }
                    EXIT_CODES.remove(callingPid);
                    RishHost old = putHost(callingPid, host);
                    host.setExitCleanup(() -> {
                        if (removeHost(callingPid, host)) {
                            putExitCode(callingPid, host.getExitCode());
                        }
                    });
                    capabilityPublished.set(true);
                    if (old != null) {
                        old.destroy();
                    }
                });
            } finally {
                epochFinished = true;
            }
            if (!committed || !capabilityPublished.get()) {
                throw new SecurityException("permission changed while creating rish host");
            }
        } finally {
            if (!epochFinished) {
                abortCapabilityCreation("rish", callingUid, callingPid, capabilityEpoch);
            }
            if (!capabilityPublished.get()) {
                host.destroy();
            }
        }
    }

    public boolean hasHostForClient(int callingPid) {
        return HOSTS.containsKey(callingPid);
    }

    public void revokeHostForClient(int callingPid) {
        EXIT_CODES.remove(callingPid);
        RishHost host = removeHost(callingPid);
        if (host != null) {
            host.destroy();
            Log.i(TAG, "Revoked host created by " + callingPid);
        }
    }

    private void setWindowSize(long size) {
        int callingPid = Binder.getCallingPid();

        RishHost host = HOSTS.get(callingPid);
        if (host == null) {
            Log.d(TAG, "Not existing host created by " + callingPid);
            return;
        }

        host.setWindowSize(size);
    }

    private int getExitCode() {
        int callingPid = Binder.getCallingPid();

        RishHost host = HOSTS.get(callingPid);
        if (host == null) {
            Integer exitCode = EXIT_CODES.remove(callingPid);
            if (exitCode == null) {
                Log.d(TAG, "Not existing host created by " + callingPid);
                return -1;
            }
            return exitCode;
        }

        int exitCode = host.getExitCode();
        if (exitCode != Integer.MAX_VALUE) {
            removeHost(callingPid, host);
            removeExitCode(callingPid, exitCode);
        }
        return exitCode;
    }

    public abstract void enforceCallingPermission(String func);

    protected long beginCapabilityCreation(String kind, int uid, int pid) {
        return 0;
    }

    protected boolean finishCapabilityCreation(String kind, int uid, int pid, long epoch, Runnable publisher) {
        publisher.run();
        return true;
    }

    protected void abortCapabilityCreation(String kind, int uid, int pid, long epoch) {}

    protected IBinder getClientBinder(int uid, int pid) {
        return null;
    }

    public boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) {
        if (code == RishConfig.getTransactionCode(RishConfig.TRANSACTION_createHost)) {
            Log.d(TAG, "TRANSACTION_createHost");

            enforceCallingPermission("createHost");

            if (reply == null || (flags & IBinder.FLAG_ONEWAY) != 0) {
                return true;
            }

            ParcelFileDescriptor stdin;
            ParcelFileDescriptor stdout;
            ParcelFileDescriptor stderr = null;

            data.enforceInterface(RishConfig.getInterfaceToken());
            byte tty = data.readByte();
            stdin = data.readFileDescriptor();
            stdout = data.readFileDescriptor();
            if ((tty & RishConstants.ATTY_ERR) == 0) {
                stderr = data.readFileDescriptor();
            }
            String[] args = data.createStringArray();
            String[] env = data.createStringArray();
            String dir = data.readString();
            createHost(args, env, dir, tty, stdin, stdout, stderr);
            reply.writeNoException();
            return true;
        } else if (code == RishConfig.getTransactionCode(RishConfig.TRANSACTION_setWindowSize)) {
            Log.d(TAG, "TRANSACTION_setWindowSize");

            enforceCallingPermission("setWindowSize");

            data.enforceInterface(RishConfig.getInterfaceToken());
            long size = data.readLong();
            setWindowSize(size);
            if (reply != null) {
                reply.writeNoException();
            }
            return true;
        } else if (code == RishConfig.getTransactionCode(RishConfig.TRANSACTION_getExitCode)) {
            Log.d(TAG, "TRANSACTION_getExitCode");

            enforceCallingPermission("getExitCode");

            data.enforceInterface(RishConfig.getInterfaceToken());
            int exitCode = getExitCode();
            if (reply != null) {
                reply.writeNoException();
                reply.writeInt(exitCode);
            }
            return true;
        }
        return false;
    }
}
