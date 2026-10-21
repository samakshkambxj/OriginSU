# OriginZygisk assets

Built-in Zygisk engine for OriginSU (BreZygisk based, built from the
`origin-rebrand` branch of `samakshkambxj/OriginZygisk` with built-in
`/data/adb/ksu/originzygisk` paths — no `/data/adb/modules` entry needed).

- `payload.zip` — universal payload for all ABIs: `bin/<abi>/zygiskd`,
  `lib/<abi>/{libzygisk.so,libzygisk_ptrace.so}`, `machikado.*`,
  `sepolicy.rule`, `originzygiskctl.sh`, `manifest.sha256`, `version`.
  Rebuild with `scripts/package-manager-payload.sh` in the engine repo.
- `setup.sh` — verify `manifest.sha256`, arrange payload into the runtime
  layout, install `bin/originzygiskctl`, write the engine's own
  `module.prop`, and emit `deployed.sha256`. Run with the deploy dir as `$1`.
- `launch.sh` — boot hook installed to
  `/data/adb/post-fs-data.d/originzygisk.sh`; rotates `boot.log` at
  256 KiB and leaves the previous generation as `boot.log.1`.

Deployed by `KsuCliRepository.setOriginZygiskEnabled` when the user flips
the toggle in Settings > Origin Lab. If these assets are missing from the
APK, the toggle reports unavailable.

The manager reads `/data/adb/rezygisk/state.json` for loaded modules,
root implementation and monitor/daemon errors. The engine writes that file
atomically and emits valid JSON; the manager still tolerates trailing commas
from older payloads. Module installers and boot-stage scripts receive
`ZYGISK_ENABLED=1` while the enable flag and boot hook are both present.

The engine also honors `/data/adb/ksu/originzygisk/unmount`; the module WebUI
exposes this as the OriginZygisk Umount toggle. The bundled manager payload
contains the engine binaries only, not the WebUI.

The native manager control panel adds:

- atomic repair/redeploy with `manifest.sha256` + `deployed.sha256` rollback;
- `originzygiskctl` for status, integrity, modules, telemetry and policy;
- per-app skip policy in `skip_packages.txt` / `skip_uids.txt`;
- per-module Zygisk toggle/quarantine in `disabled_modules.txt` /
  `quarantine.txt` (applied next boot);
- module health, injection telemetry (`telemetry.jsonl`), boot logs and
  a diagnostics zip with state, pstore, logcat and crash snapshots.

Policy changes are read by the engine at process specialization time; module
policy changes are read when the Zygisk daemons start, so they apply next boot.
