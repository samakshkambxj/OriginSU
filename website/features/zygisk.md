# OriginZygisk

BreZygisk-based Zygisk engine bundled in the manager (`Settings > Origin Lab`) — no separate
module hunt.

- Deploy / status / kill-switch from Origin Lab; Home shows the implementation.
- Conflicting provider modules are blocked at install time.
- `ZYGISK_ENABLED=true` is exported to module installers when enabled, so modules can adapt.
