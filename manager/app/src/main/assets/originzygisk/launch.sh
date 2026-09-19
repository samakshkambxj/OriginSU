#!/system/bin/sh
# OriginZygisk boot hook. Managed by the OriginSU manager:
# deleting this file (or the enable flag) disables OriginZygisk on next boot.
ZYGISK_DIR="/data/adb/ksu/originzygisk"
[ -f "$ZYGISK_DIR/enable" ] || exit 0
export TMP_PATH=/data/adb/rezygisk
rm -rf "$TMP_PATH"
mkdir -p "$TMP_PATH"
chmod 755 "$TMP_PATH"
CPU_ABIS_PROP1=$(getprop ro.system.product.cpu.abilist)
CPU_ABIS_PROP2=$(getprop ro.product.cpu.abilist)
if [ "${#CPU_ABIS_PROP2}" -gt "${#CPU_ABIS_PROP1}" ]; then
    CPU_ABIS=$CPU_ABIS_PROP2
else
    CPU_ABIS=$CPU_ABIS_PROP1
fi
cd "$ZYGISK_DIR/payload" || exit 1
case "$CPU_ABIS" in
    *arm64-v8a*|*x86_64*) exec ./bin/zygisk-ptrace64 monitor & ;;
    *) exec ./bin/zygisk-ptrace32 monitor & ;;
esac
