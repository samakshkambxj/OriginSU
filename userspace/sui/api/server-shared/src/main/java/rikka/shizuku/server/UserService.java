package rikka.shizuku.server;

import android.app.ActivityThread;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextHidden;
import android.ddm.DdmHandleAppName;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.Nullable;
import dev.rikka.tools.refine.Refine;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UserService {

    private static String TAG;

    public static void setTag(String tag) {
        UserService.TAG = tag;
    }

    @Nullable public static Pair<IBinder, String> create(String[] args) {
        String name = null;
        String token = null;
        String pkg = null;
        String cls = null;
        int uid = -1;

        for (String arg : args) {
            if (arg.startsWith("--debug-name=")) {
                name = arg.substring(13);
            } else if (arg.startsWith("--token=")) {
                token = arg.substring(8);
            } else if (arg.startsWith("--package=")) {
                pkg = arg.substring(10);
            } else if (arg.startsWith("--class=")) {
                cls = arg.substring(8);
            } else if (arg.startsWith("--uid=")) {
                uid = Integer.parseInt(arg.substring(6));
            }
        }

        int userId = uid / 100000;

        Log.i(TAG, String.format("starting service %s/%s...", pkg, cls));

        IBinder service;

        try {
            ActivityThread activityThread = ActivityThread.systemMain();
            Context systemContext = activityThread.getSystemContext();

            DdmHandleAppName.setAppName(name != null ? name : pkg + ":user_service", userId);

            //noinspection InstantiationOfUtilityClass
            UserHandle userHandle;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                userHandle = Refine.unsafeCast(UserHandleHidden.of(userId));
            } else {
                Constructor<UserHandle> constructor = UserHandle.class.getDeclaredConstructor(int.class);
                constructor.setAccessible(true);
                userHandle = constructor.newInstance(userId);
            }
            Context context = Refine.<ContextHidden>unsafeCast(systemContext)
                    .createPackageContextAsUser(
                            pkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY, userHandle);

            Application application = null;
            try {
                Field mPackageInfo = context.getClass().getDeclaredField("mPackageInfo");
                mPackageInfo.setAccessible(true);
                Object loadedApk = mPackageInfo.get(context);
                Method makeApplication =
                        loadedApk.getClass().getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
                application = (Application) makeApplication.invoke(loadedApk, true, null);
                Field mInitialApplication = activityThread.getClass().getDeclaredField("mInitialApplication");
                mInitialApplication.setAccessible(true);
                mInitialApplication.set(activityThread, application);
            } catch (Throwable e) {
                // Catch any errors initializing the application, and use the old Context method as a fallback instead
                // Especially relevant for MediaTek devices, see GitHub issue 1171
                Log.w(TAG, "Failed to initialize Application, using Context as fallback", e);
                application = null;
            }

            ClassLoader classLoader = (application != null ? application.getClassLoader() : context.getClassLoader());
            Class<?> serviceClass = classLoader.loadClass(cls);
            Constructor<?> constructorWithContext = null;
            try {
                constructorWithContext = serviceClass.getConstructor(Context.class);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            if (constructorWithContext != null) {
                service = (IBinder) constructorWithContext.newInstance(application != null ? application : context);
            } else {
                service = (IBinder) serviceClass.getConstructor().newInstance();
            }
        } catch (Throwable tr) {
            Log.w(TAG, String.format("unable to start service %s/%s...", pkg, cls), tr);
            return null;
        }

        return new Pair<>(service, token);
    }
}
