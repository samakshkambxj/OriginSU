// Copied from Sui's api/aidl module (MIT, see userspace/sui/UPSTREAM.md).
package moe.shizuku.server;

interface IRemoteProcess {

    ParcelFileDescriptor getOutputStream();

    ParcelFileDescriptor getInputStream();

    ParcelFileDescriptor getErrorStream();

    int waitFor();

    int exitValue();

    void destroy();
}
