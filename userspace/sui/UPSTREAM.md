# Sui upstream provenance

This directory is OriginSU's built-in copy of Sui, the Shizuku API implementation
that runs as a Zygisk module. Upstream source, copyright headers and license
texts are retained.

The backend is Sui upstream; the frontend and the wiring are independently
maintained by OriginSU.

## Pinned sources

- Sui: https://github.com/XiaoTong6666/Sui
  commit `e9674238a0dcfe13d9ea231cbdf866144e660db8` (v13.5.4.3).
- Shizuku-API (Sui's `api` submodule): https://github.com/XiaoTong6666/Shizuku-API
  commit `13aeedbc9d83e65b3a4c74b736ca54c768dcb819` (api 13.1.5).

## Acknowledgements

Thanks to the Sui Contributors and RikkaW for Sui and Shizuku-API. Original
authors are the Sui Contributors and RikkaW (Shizuku-API). Individual source
headers carry the upstream notices; no copyright notice has been replaced with
OriginSU attribution.

## Retained license terms

Sui is GPL-3.0; Shizuku-API is MIT. Both `LICENSE` files are preserved verbatim.
OriginSU is GPL-3.0, so the combined work is redistributable.

## Local integration changes

- Manager identity is read only by the root server. Root-authenticated UID
  snapshots carry its package, UID and certificate to system_server; package UID
  and certificate are checked again before management access is granted.
- The native bridge prefilter defers the registered UID to Java verification.
  Other hidden callers retain the existing filtering rules. Shell servers never
  publish identity or permission snapshots.
- The root server refreshes the identity snapshot and removes obsolete manager
  root grants after a UID change. Failed bridge updates keep the one-second retry.
- `SuiDiagnostics` reads the permission database without recovery or writes and
  queries a root-only bridge action for actual routing membership and binder state.
- `SUI_FOREGROUND=1` keeps the starter attached to its supervisor for child reaping.
  Standalone startup retains the original daemon behavior.
- The repository build driver accepts `SUI_BUILD_TYPE=debug` to retain Java and
  native diagnostics; release remains the default with log stripping unchanged.
