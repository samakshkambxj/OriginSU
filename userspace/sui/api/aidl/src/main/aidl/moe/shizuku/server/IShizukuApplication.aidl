package moe.shizuku.server;

interface IShizukuApplication {

    oneway void bindApplication(in Bundle data) = 1;

    oneway void dispatchRequestPermissionResult(int requestCode, in Bundle data) = 2;

    // Sui only
    void showPermissionConfirmation(int requestUid, int requestPid, in String requestPackageName, int requestCode) = 10000;

    // Sui only. Prepares and commits a replacement Sui server binder in the client process.
    boolean dispatchServerBinder(in IBinder binder, in String packageName, long generation) = 10001;
}
