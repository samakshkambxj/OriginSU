package rikka.shizuku.server;

import android.os.IBinder;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.List;
import moe.shizuku.server.IShizukuApplication;
import rikka.shizuku.server.util.Logger;

public class ClientManager<ConfigMgr extends ConfigManager> {

    protected static final Logger LOGGER = new Logger("UserServiceRecord");

    private final ConfigMgr configManager;
    private final List<ClientRecord> clientRecords = new ArrayList<>();

    public ClientManager(ConfigMgr configManager) {
        this.configManager = configManager;
    }

    public ConfigMgr getConfigManager() {
        return configManager;
    }

    protected boolean isClientAllowed(ConfigPackageEntry entry) {
        return entry != null && (entry.isAllowed() || entry.isAllowedShell());
    }

    public List<ClientRecord> getClients() {
        synchronized (clientRecords) {
            return new ArrayList<>(clientRecords);
        }
    }

    public List<ClientRecord> findClients(int uid) {
        synchronized (clientRecords) {
            List<ClientRecord> res = new ArrayList<>();
            for (ClientRecord clientRecord : clientRecords) {
                if (clientRecord.uid == uid) {
                    res.add(clientRecord);
                }
            }
            return res;
        }
    }

    public ClientRecord findClient(int uid, int pid) {
        synchronized (clientRecords) {
            for (ClientRecord clientRecord : clientRecords) {
                if (clientRecord.pid == pid && clientRecord.uid == uid) {
                    return clientRecord;
                }
            }
        }
        return null;
    }

    public ClientRecord requireClient(int callingUid, int callingPid) {
        return requireClient(callingUid, callingPid, false);
    }

    public ClientRecord requireClient(int callingUid, int callingPid, boolean requiresPermission) {
        ClientRecord clientRecord = findClient(callingUid, callingPid);
        if (clientRecord == null) {
            LOGGER.w("Caller (uid %d, pid %d) is not an attached client", callingUid, callingPid);
            throw new IllegalStateException("Not an attached client");
        }
        if (requiresPermission && !clientRecord.allowed) {
            throw new SecurityException("Caller has no permission");
        }
        return clientRecord;
    }

    public ClientRecord addClient(int uid, int pid, IShizukuApplication client, String packageName, int apiVersion) {
        ClientRecord clientRecord = new ClientRecord(uid, pid, client, packageName, apiVersion);

        ConfigPackageEntry entry = configManager.find(uid);
        if (isClientAllowed(entry)) {
            clientRecord.allowed = true;
        }

        IBinder binder = client.asBinder();
        IBinder.DeathRecipient deathRecipient = () -> {
            synchronized (clientRecords) {
                clientRecords.remove(clientRecord);
            }
        };
        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            LOGGER.w(e, "addClient: linkToDeath failed");
            return null;
        }

        synchronized (clientRecords) {
            clientRecords.add(clientRecord);
        }
        return clientRecord;
    }
}
