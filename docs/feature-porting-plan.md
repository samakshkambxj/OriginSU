# OriginSU Feature-Porting Plan

Sources: WildKernels/Wild_KSU (archived), spacealtctrl/ReSukiSU-Ultima,
tiann/KernelSU (official manager). Phases are ordered so each ships and
tests independently. Baseline: CI green, `CERT_MAX_LENGTH=2048` fix in.

## Phase 1 — Wild_KSU boot tooling (S, userspace + manager, no kernel change)
- Vendor `libmagiskboot.so` per-arch into `manager/app/src/main/jniLibs`
  + `install --magiskboot` path (Wild `KsuCli.kt:122`).
- Port `flashAnyKernelZip` (update-binary validation, cache extract) into
  the Install flow (current flow only handles downloads/dirs).
- Verify: install + AK3 flash on device.

## Phase 2 — MIUI / official theme options (S, manager only)
- Diff `tiann/KernelSU` manager Settings theme block against
  `ThemeSettings` / `SettingsViewModel`; port missing options.
- Follow `manager/AGENTS.md` (settings widgets, `stringResource`, all locales).

## Phase 3 — Built-in Zygisk (M, userspace + manager + CI, no kernel change)
- Port Ultima `manager/.../assets/zygisk` engine (ReZygisk-derived, GPL-3.0;
  keep attribution), ksud deploy path, Settings toggle (off by default) +
  `post-fs-data.d` kill-switch.
- Extend `getZygiskImplement()` to report the built-in engine.
- CI: package zygisk assets in repack.

## Phase 4 — Origin Veil (L, kernel + UAPI + manager)
- `kernel/feature/sentinel.{c,h}` + `uapi/sentinel.h`,
  `CONFIG_KSU_SENTINEL` (default y, `depends on KSU`); new supercall/UAPI IDs
  → bump `MINIMAL_SUPPORTED_KERNEL` past 35002 in lockstep with `Natives`.
- Port `SentinelScreen`, `SentinelSpyScreen`, `SentinelAppDetailScreen` +
  ViewModel/usecase layer per project architecture; auto-cloak vs notify UX;
  strings for all locales.
- Requires `~/Kernel` rebuild + reflash to test.

## Phase 5 — KPM support (XL, kernel + manager, last)
- `kernel/kpm/*` (SukiSU_KernelPatch_patch lineage), `CONFIG_KPM`,
  KALLSYMS requirements for non-GKI; coexistence check with tracepoint-hook
  default.
- Port `KpmPage` + `KpmViewModel`, template docs link, CI LKM matrix impact.
- Last: touches hook paths, regression risk.

## Suggested extras
- (a) Magic Mount (MKSU `magic_mount.rs` port, userspace-only, module-compat win).
- (b) GKI OTA survival polish in Install screen copy.

Order: 1 → 2 → 3 → 4 → 5.
