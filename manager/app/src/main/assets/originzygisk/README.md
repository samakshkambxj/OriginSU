# Origin Zygisk assets

This directory ships the Origin Zygisk engine (BreZygisk-based) deployed by
the manager when the user enables it in Settings:

- `payload.zip` — `bin/zygisk-ptrace{64,32}` + `sepolicy.rule` + `version`
  (built by `scripts/package-origin-payload.sh` in the OriginZygisk fork
  from a BreZygisk release build).
- `setup.sh` — unpack + perms, run with the deploy dir as `$1`.
- `launch.sh` — boot hook installed to `/data/adb/post-fs-data.d/originzygisk.sh`.

The manager (`KsuCliRepository.setOriginZygiskEnabled`) copies these three
files here, so a manager pull request or local build without them simply
reports the toggle as unavailable. Do NOT commit large binaries to any
other location; CI packages whatever is in this directory into the APK.
