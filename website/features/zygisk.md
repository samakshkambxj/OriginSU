# OriginZygisk

BreZygisk-based Zygisk engine bundled in the manager (`Settings > Origin Lab`) — no separate
module hunt.

- Deploy / status / kill-switch from Origin Lab; Home shows the implementation.
- Conflicting provider modules are blocked at install time.
- `ZYGISK_ENABLED=1` is exported to module installers and boot-stage scripts when enabled,
  matching Magisk and LSPosed expectations.
- Status in Origin Lab reads the engine's `state.json` and reports loaded modules plus
  monitor/daemon errors; `boot.log` is rotated at 256 KiB.
