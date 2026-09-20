# OriginSU Feature-Porting Plan

Sources: WildKernels/Wild_KSU (archived), spacealtctrl/ReSukiSU-Ultima,
tiann/KernelSU (official manager), KernelSU-Next.
Phases run in order; each ships and tests independently. Baseline: CI green,
`CERT_MAX_LENGTH=2048` fix in.

Difficulty scale: ★☆☆☆☆ trivial → ★★★★★ deep kernel surgery.
Status: ✅ done · 🚧 in progress · ⏳ queued · ⏸️ deferred.

## Phase 1 — Wild_KSU boot tooling ★★☆☆☆ ✅
(userspace + manager, no kernel change)
- ✅ Vendored `libmagiskboot.so` (arm64 + x86_64); `install --magiskboot`
  deploys it to `/data/adb/ksu/bin`.
- ✅ `flashAnyKernelZip`: legacy busybox runner for the zip's own
  `update-binary`, new Install entry (Route/FlashIt/FlashOperation chain).
- ✅ Offline patcher (no root): stock `boot.img` + AK3 kernel →
  `Downloads/OriginSU/`, via ksud's own `boot-patch --kernel --no-install`
  (proven rootless on-device). Guided UX: step toasts + chosen file names.
  Terminal banner rebranded to OriginSU figlet.

## Phase 1b — Module banners (WildKSU/KSU-Next) ★☆☆☆☆ ✅
(manager only)
- ✅ `banner=` parsed from `module.prop` JSON; faded Coil backdrop (URL or
  module-dir file); `show_banners` pref in module dropdown + Theme Settings;
  strings for all locales.

## Phase 2 — MIUI / official theme options ★☆☆☆☆ ⏸️
DEFERRED per maintainer decision: full dual-kit material↔miuix port is a
rewrite, not a port.

## Phase 3 — Zygisk removal ✅
- Former built-in engine and all Zygisk integration removed completely
  (vendored payload, Settings toggle, provider install-block, Home
  detection, `ZYGISK_ENABLED` export).

## Phase 4 — Origin Veil ★★★★☆ ⏳
(kernel + UAPI + manager; formerly "Sentinel")
- `kernel/feature/sentinel.{c,h}` + `uapi/sentinel.h` (renamed to veil),
  `CONFIG_KSU_ORIGIN_VEIL` (default y, `depends on KSU`); new supercall/UAPI
  IDs → bump `MINIMAL_SUPPORTED_KERNEL` past 35002 in lockstep with `Natives`.
- Port Sentinel screens + ViewModel/usecase layer; auto-cloak vs notify UX;
  strings for all locales. Requires `~/Kernel` rebuild + reflash to test.

## Phase 5 — KPM support ★★★★★ ⏳
(kernel + manager, last)
- `kernel/kpm/*` (SukiSU_KernelPatch_patch lineage), `CONFIG_KPM`,
  KALLSYMS for non-GKI; coexistence with tracepoint-hook default.
- Port `KpmPage` + `KpmViewModel`, template docs, CI LKM matrix impact.

## UI/UX batch ✅ (all six shipped)
1. Batch module actions (multi-select, enable/disable/uninstall)
2. Update-all into multi-flash screen
3. Copy-device-info on Home
6. First-run setup wizard (status + LKM/built-in + kernel + deep links)
7. Biometric secure-root gate + Settings toggle
9. Last-flash timeline chip on Home

## Suggested extras ⏳
- (a) Magic Mount (MKSU `magic_mount.rs`, userspace-only) ★★☆☆☆.
- (b) GKI OTA survival polish ★☆☆☆☆.

Order: 1 → 1b → 3 → 4 → 5 (+ UI/UX interleaved).
