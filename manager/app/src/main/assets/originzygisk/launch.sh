#!/system/bin/sh
# OriginZygisk boot hook. Managed by the OriginSU manager:
# deleting this file (or the enable flag) disables OriginZygisk on next boot.
# ksud runs post-fs-data.d at boot; this file's presence is the on/off switch
# + the recovery kill-switch.
#
# Backend: BreZygisk payload (bin/zygisk-ptrace{64,32} under payload/).
# The monitor is setsid-detached so it survives after ksud's script runner
# returns, with boot logging and sepolicy re-applied each boot.
ZYGISK_DIR="/data/adb/ksu/originzygisk"
LOG="$ZYGISK_DIR/boot.log"
echo "--- $(date) launch.sh start ---" >> "$LOG" 2>/dev/null

# Off-switch / safety: only run if the engine is present and enabled.
[ -f "$ZYGISK_DIR/enable" ] || { echo "no enable flag, skip" >> "$LOG" 2>/dev/null; exit 0; }
[ -d "$ZYGISK_DIR/payload/bin" ] || { echo "no bin dir, skip" >> "$LOG" 2>/dev/null; exit 0; }

cd "$ZYGISK_DIR/payload" || exit 1

# sepolicy isn't a module, so ksud won't auto-load it at boot - re-apply here.
if [ -f "$ZYGISK_DIR/payload/sepolicy.rule" ]; then
  /data/adb/ksu/bin/ksud sepolicy apply "$ZYGISK_DIR/payload/sepolicy.rule" >> "$LOG" 2>&1
  echo "$(date) sepolicy applied (exit $?)" >> "$LOG" 2>/dev/null
fi

# Working dir expected by the engine.
export TMP_PATH=/data/adb/rezygisk
rm -rf "$TMP_PATH"
mkdir -p "$TMP_PATH"
chmod 555 "$TMP_PATH"
chcon u:object_r:system_file:s0 "$TMP_PATH" 2>/dev/null || true

CPU_ABIS_PROP1=$(getprop ro.system.product.cpu.abilist)
CPU_ABIS_PROP2=$(getprop ro.product.cpu.abilist)
if [ "${#CPU_ABIS_PROP2}" -gt "${#CPU_ABIS_PROP1}" ]; then
    CPU_ABIS=$CPU_ABIS_PROP2
else
    CPU_ABIS=$CPU_ABIS_PROP1
fi
case "$CPU_ABIS" in
    *arm64-v8a*|*x86_64*) BIN=./bin/zygisk-ptrace64 ;;
    *)                    BIN=./bin/zygisk-ptrace32 ;;
esac

# Fully detach so the monitor survives after ksud's script runner returns.
if [ -x "$BIN" ]; then
  echo "$(date) launching $BIN monitor (setsid)" >> "$LOG" 2>/dev/null
  setsid "$BIN" monitor >> "$LOG" 2>&1 &
  echo "$(date) launched (pid $!)" >> "$LOG" 2>/dev/null
else
  echo "$(date) ERROR: $BIN not executable" >> "$LOG" 2>/dev/null
fi

exit 0
