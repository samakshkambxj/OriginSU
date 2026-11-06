package rikka.shizuku.server;

import rikka.shizuku.server.util.Logger;

public abstract class ConfigPackageEntry {

    protected static final Logger LOGGER = new Logger("ConfigPackageEntry");

    public ConfigPackageEntry() {}

    public abstract boolean isAllowed();

    public boolean isAllowedShell() {
        return false;
    }

    public abstract boolean isDenied();
}
