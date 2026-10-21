# OriginZygisk assets

Built-in Zygisk engine for OriginSU (BreZygisk based, built from the
`origin-rebrand` branch of `samakshkambxj/OriginZygisk` with built-in
`/data/adb/ksu/originzygisk` paths — no `/data/adb/modules` entry needed).

- `payload.zip` — universal payload for all ABIs: `bin/<abi>/zygiskd`,
  `lib/<abi>/{libzygisk.so,libzygisk_ptrace.so}`, `machikado.*`,
  `sepolicy.rule`, `version`. Rebuild with
  `scripts/package-manager-payload.sh` in the engine repo.
- `setup.sh` — arrange payload into the runtime layout + write the
  engine's own `module.prop`. Run with the deploy dir as `$1`.
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
