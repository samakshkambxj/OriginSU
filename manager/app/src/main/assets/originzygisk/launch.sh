#!/system/bin/sh
# OriginZygisk boot hook. Managed by the OriginSU manager:
# deleting this file (or the enable flag) disables OriginZygisk on next boot.
# ksud runs post-fs-data.d at boot; this file's presence is the on/off switch
# + the recovery kill-switch.
#
# Backend: OriginZygisk engine (BreZygisk based) with built-in
# /data/adb/ksu/originzygisk paths. The monitor is setsid-detached so it
# survives after ksud's script runner returns, with boot logging and
# sepolicy re-applied each boot.
#
# Safety:
#   * External-provider step-aside: if another Zygisk provider is installed and
#     enabled, OriginZygisk stays out of the way instead of fighting for zygote.
#   * Boot guard: a boot that never reaches a ready monitor is counted; after
#     three consecutive failures OriginZygisk latches off until the user runs
#     `originzygiskctl bootguard reset`.
ZYGISK_DIR="/data/adb/ksu/originzygisk"
LOG="$ZYGISK_DIR/boot.log"
BG="$ZYGISK_DIR/bootguard"
STATE="/data/adb/rezygisk/state.json"
FAILURE_LIMIT=3

# Keep boot diagnostics bounded: one previous generation is enough to debug
# a failed boot without letting boot.log grow forever.
if [ -f "$LOG" ]; then
  LOG_SIZE=$(wc -c < "$LOG" 2>/dev/null || echo 0)
  if [ "$LOG_SIZE" -gt 262144 ]; then
    rm -f "$LOG.1"
    mv -f "$LOG" "$LOG.1"
  fi
fi

mkdir -p "$BG" 2>/dev/null
chmod 0755 "$BG" 2>/dev/null

log() {
  echo "$(date) $*" >> "$LOG" 2>/dev/null
  echo "$(date) $*" >> "$BG/history" 2>/dev/null
}

echo "--- $(date) launch.sh start ---" >> "$LOG" 2>/dev/null

boot_id() {
  cat /proc/sys/kernel/random/boot_id 2>/dev/null || uptime 2>/dev/null || echo unknown
}

# Another Zygisk provider is active: do not start a second injection engine.
find_external_provider() {
  for d in /data/adb/modules/*; do
    [ -d "$d" ] || continue
    [ -f "$d/disable" ] && continue
    [ -f "$d/remove" ] && continue
    id=${d##*/}
    case "$id" in
      zygisksu|rezygisk|brezygisk|shirokozygisk|zygisk_next|zygisknext|neozygisk|onyxzygisk|yukizygisk|nyazygisk) ;;
      *) continue ;;
    esac
    # Only step aside for a provider that actually ships an injector.
    ls "$d/zygisk"/*.so >/dev/null 2>&1 || continue
    printf '%s' "$d"
    return 0
  done
  return 1
}

# Readiness means the monitor is tracing and at least one zygote was injected.
state_ready() {
  [ -f "$STATE" ] || return 1
  grep -q '"state": "0"' "$STATE" 2>/dev/null || return 1
  grep -q '"64": 1' "$STATE" 2>/dev/null || grep -q '"32": 1' "$STATE" 2>/dev/null
}

# Off-switch / safety: only run if the engine is present and enabled.
[ -f "$ZYGISK_DIR/enable" ] || { echo "no enable flag, skip" >> "$LOG" 2>/dev/null; exit 0; }
[ -d "$ZYGISK_DIR/bin" ] || { echo "no bin dir, skip" >> "$LOG" 2>/dev/null; exit 0; }

if [ -f "$BG/latched" ]; then
  log "bootguard: latched after ${FAILURE_LIMIT} failed boots, staying off (run originzygiskctl bootguard reset)"
  exit 0
fi

PROVIDER=$(find_external_provider)
if [ -n "$PROVIDER" ]; then
  log "external Zygisk provider active: $PROVIDER — stepping aside"
  exit 0
fi

BOOT_ID=$(boot_id)
LAST_ATTEMPT=$(cat "$BG/attempted" 2>/dev/null || true)
LAST_CONFIRMED=$(cat "$BG/confirmed" 2>/dev/null || true)

# The same boot can reach post-fs-data more than once during late-load; only
# one attempt and one boot-guard increment are allowed per boot.
if [ -n "$LAST_ATTEMPT" ] && [ "$LAST_ATTEMPT" = "$BOOT_ID" ]; then
  log "bootguard: already attempted this boot ($BOOT_ID)"
  exit 0
fi

# A previous attempt that was never confirmed is a failed boot. Do not count
# the very first boot (no previous attempt).
if [ -n "$LAST_ATTEMPT" ] && [ "$LAST_ATTEMPT" != "$LAST_CONFIRMED" ]; then
  FAILURES=$(cat "$BG/failures" 2>/dev/null || echo 0)
  case "$FAILURES" in ''|*[!0-9]*) FAILURES=0 ;; esac
  FAILURES=$((FAILURES + 1))
  printf '%s\n' "$FAILURES" > "$BG/failures"
  chmod 0644 "$BG/failures" 2>/dev/null
  log "bootguard: previous boot ($LAST_ATTEMPT) did not confirm; failure $FAILURES/$FAILURE_LIMIT"
  if [ "$FAILURES" -ge "$FAILURE_LIMIT" ]; then
    : > "$BG/latched"
    chmod 0644 "$BG/latched" 2>/dev/null
    log "bootguard: latched off after $FAILURES failed boots"
    exit 0
  fi
fi

printf '%s\n' "$BOOT_ID" > "$BG/attempted"
chmod 0644 "$BG/attempted" 2>/dev/null

cd "$ZYGISK_DIR" || exit 1

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
  exit 0
fi

# Confirm readiness once Android has finished booting. A boot that never gets
# here stays unconfirmed and counts as a failure on the next launch.
(
  i=0
  while [ "$i" -lt 180 ]; do
    if [ "$(getprop sys.boot_completed 2>/dev/null)" = "1" ] && state_ready; then
      printf '%s\n' "$BOOT_ID" > "$BG/confirmed"
      chmod 0644 "$BG/confirmed" 2>/dev/null
      printf '%s\n' 0 > "$BG/failures"
      chmod 0644 "$BG/failures" 2>/dev/null
      echo "$(date) bootguard: confirmed boot $BOOT_ID" >> "$BG/history" 2>/dev/null
      exit 0
    fi
    sleep 2
    i=$((i + 1))
  done
  echo "$(date) bootguard: boot $BOOT_ID did not reach a ready monitor" >> "$BG/history" 2>/dev/null
) >> "$LOG" 2>&1 &

exit 0
