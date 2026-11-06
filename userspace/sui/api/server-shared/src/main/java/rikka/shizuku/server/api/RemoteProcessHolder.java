package rikka.shizuku.server.api;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import moe.shizuku.server.IRemoteProcess;
import rikka.shizuku.server.util.Logger;
import rikka.shizuku.server.util.ParcelFileDescriptorUtil;

public class RemoteProcessHolder extends IRemoteProcess.Stub {

    private static final Logger LOGGER = new Logger("RemoteProcessHolder");

    private final Process process;
    private final int pid;
    private final boolean processGroup;
    private final Runnable onDestroyed;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private ParcelFileDescriptor in;
    private ParcelFileDescriptor out;

    public RemoteProcessHolder(Process process, IBinder token) {
        this(process, token, false, null);
    }

    public RemoteProcessHolder(Process process, IBinder token, boolean processGroup) {
        this(process, token, processGroup, null);
    }

    public RemoteProcessHolder(Process process, IBinder token, boolean processGroup, Runnable onDestroyed) {
        this.process = process;
        this.pid = getProcessPid(process);
        this.processGroup = processGroup && pid > 0;
        this.onDestroyed = onDestroyed;

        if (token != null) {
            try {
                DeathRecipient deathRecipient = () -> {
                    try {
                        // The Process leader may already have exited while descendants in the
                        // same session/process group remain alive. Security cleanup must therefore
                        // not be gated on Process.isAlive()/exitValue().
                        destroy();
                        LOGGER.i("destroy process because the owner is dead");
                    } catch (Throwable e) {
                        LOGGER.w(e, "failed to destroy process");
                    }
                };
                token.linkToDeath(deathRecipient, 0);
            } catch (Throwable e) {
                LOGGER.w(e, "linkToDeath");
                destroy();
                throw new IllegalStateException("owner binder is already dead", e);
            }
        }
    }

    private static int getProcessPid(Process process) {
        try {
            Method method = Process.class.getMethod("pid");
            long value = (long) method.invoke(process);
            if (value > 0 && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        } catch (Throwable ignored) {
        }
        try {
            Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            return field.getInt(process);
        } catch (Throwable e) {
            LOGGER.w(e, "cannot resolve process pid");
            return -1;
        }
    }

    @Override
    public ParcelFileDescriptor getOutputStream() {
        if (out == null) {
            try {
                out = ParcelFileDescriptorUtil.pipeTo(process.getOutputStream());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return out;
    }

    @Override
    public ParcelFileDescriptor getInputStream() {
        if (in == null) {
            try {
                in = ParcelFileDescriptorUtil.pipeFrom(process.getInputStream());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return in;
    }

    @Override
    public ParcelFileDescriptor getErrorStream() {
        try {
            return ParcelFileDescriptorUtil.pipeFrom(process.getErrorStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int waitFor() {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (processGroup) {
                try {
                    Os.kill(-pid, OsConstants.SIGKILL);
                    return;
                } catch (ErrnoException e) {
                    if (e.errno != OsConstants.ESRCH) {
                        LOGGER.w(e, "failed to kill process group %d", pid);
                    }
                }
            }
            process.destroy();
        } finally {
            if (onDestroyed != null) {
                onDestroyed.run();
            }
        }
    }

    @Override
    public boolean alive() throws RemoteException {
        try {
            this.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    @Override
    public boolean waitForTimeout(long timeout, String unitName) throws RemoteException {
        TimeUnit unit = TimeUnit.valueOf(unitName);
        long startTime = System.nanoTime();
        long rem = unit.toNanos(timeout);

        do {
            try {
                exitValue();
                return true;
            } catch (IllegalThreadStateException ex) {
                if (rem > 0) {
                    try {
                        Thread.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException();
                    }
                }
            }
            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        } while (rem > 0);
        return false;
    }
}
