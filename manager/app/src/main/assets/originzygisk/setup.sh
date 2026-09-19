#!/system/bin/sh
# OriginZygisk setup: unpack payload + fix perms + compat shim.
# Usage: setup.sh <deploy-dir>
set -e
D="$1"
mkdir -p "$D/payload"
cd "$D/payload" && unzip -o "$D/payload.zip" >/dev/null
chmod -R 755 "$D/payload/bin"
chcon -R u:object_r:system_file:s0 "$D/payload/bin" 2>/dev/null || true
# Compat shim: the BreZygisk monitor reads and updates its status in
# /data/adb/modules/rezygisk/module.prop and exits without it.
# This entry doubles as the engine status display in module lists.
mkdir -p /data/adb/modules/rezygisk
if [ ! -f /data/adb/modules/rezygisk/module.prop ]; then
    printf 'id=rezygisk\nname=OriginZygisk engine\nversion=v1\nversionCode=1\nauthor=OriginSU\ndescription=Standalone engine status is written here by the monitor.\n' > /data/adb/modules/rezygisk/module.prop
fi
cp /data/adb/modules/rezygisk/module.prop /data/adb/modules/rezygisk/module.prop.bak
