// Copied from Sui's api/aidl module (MIT, see userspace/sui/UPSTREAM.md).
package moe.shizuku.server;

interface IShizukuServiceConnection {

    oneway void connected(IBinder service);

    oneway void died();
}
