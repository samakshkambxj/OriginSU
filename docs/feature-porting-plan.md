# OriginSU Feature-Porting Plan

Sources: WildKernels/Wild_KSU (archived), spacealtctrl/ReSukiSU-Ultima,
tiann/KernelSU (official manager). Phases run in order; each ships and
tests independently. Baseline: CI green, `CERT_MAX_LENGTH=2048` fix in.

Difficulty scale: ★☆☆☆☆ trivial → ★★★★★ deep kernel surgery.
Status: ✅ done · 🚧 in progress · ⏳ queued · ⏸️ deferred.

## Phase 1 — Wild_KSU boot tooling ★★☆☆☆ ✅
(userspace + manager, no kernel change)
- Problem: `anykernel3.rs` bails on any AK3 zip without the mkbootfs
  injection marker; nothing provides `magiskboot` for scripts that need it.
- ✅ Vendored `libmagiskboot.so` (arm64 + x86_64); `install --magiskboot`
  deploys it to `/data/adb/ksu/bin`.
- ✅ `flashAnyKernelZip`: legacy busybox runner for the zip's own
  `update-binary`, new Install entry (Route/FlashIt/FlashOperation chain).
- ✅ Offline patcher (no root): stock `boot.img` + AK3 kernel →
  `Downloads/OriginSU/`, via ksud's own `boot-patch --kernel --no-install`
  (proven rootless on-device; magiskboot exec abandoned — Wild's blob is
  non-PIE and app exec is SELinux-denied, kept only for AK3 installer
  scripts that call it under root).

## Phase 1b — Module banners (WildKSU/KSU-Next) ★☆☆☆☆ ✅
(manager only)
- ✅ `banner=` from `module.prop` parsed (flows through ksud JSON untouched).
- ✅ Faded Coil backdrop on installed-module cards (URL direct, local file
  via SuFile with `modules_update` fallback), `show_banners` preference +
  dropdown toggle, strings for all 43 locales.

## Phase 2 — MIUI / official theme options ★☆☆☆☆ — DEFERRED
(manager only; skipped per maintainer decision 2026-09-19: full dual-kit
material↔miuix port is a rewrite, not a port)
- Why ★: pure UI work, zero risk to root functionality.

## Phase 3 — Built-in Zygisk ★★★☆☆ 🚧
(userspace + manager + CI, no kernel change)
- Port Ultima `manager/.../assets/zygisk` engine (ReZygisk-derived, GPL-3.0;
  keep attribution): `payload.zip` + `setup.sh` + `launch.sh`, deployed under
  `/data/adb/ksu/zygisk` (NOT a module).
- Settings toggle (off by default) + `post-fs-data.d` launch hook doubling
  as kill-switch (`enable` flag + hook removal + pkill).
- Status checks: engine deployed (`enable` file + hook) vs monitor alive
  (`pgrep zygisk-ptrace`); report built-in engine in `getZygiskImplement()`;
  block conflicting Zygisk *provider* module installs (zygisksu, rezygisk,
  zygisk_next…), modules themselves stay installable.
- CI: package zygisk assets in repack (assets ship in APK, no build change).
- Why ★★★: ptrace injector lifecycle is fiddly (boot-loop risk), but no
  kernel changes; kill-switch bounds the blast radius.

## Phase 4 — Origin Veil ★★★★☆ ⏳
(kernel + UAPI + manager; formerly "Sentinel")
- `kernel/feature/sentinel.{c,h}` + `uapi/sentinel.h` (renamed to veil),
  `CONFIG_KSU_ORIGIN_VEIL` (default y, `depends on KSU`); new supercall/UAPI
  IDs → bump `MINIMAL_SUPPORTED_KERNEL` past 35002 in lockstep with `Natives`.
- Port `SentinelScreen`, `SentinelSpyScreen`, `SentinelAppDetailScreen` +
  ViewModel/usecase layer per project architecture; auto-cloak vs notify UX;
  strings for all locales.
- Requires `~/Kernel` rebuild + reflash to test.
- Why ★★★★: new syscalls + LSM hooks + UAPI version discipline; a mistake
  breaks manager↔kernel compat for every user.

## Phase 5 — KPM support ★★★★★ ⏳
(kernel + manager, last)
- `kernel/kpm/*` (SukiSU_KernelPatch_patch lineage), `CONFIG_KPM`,
  KALLSYMS requirements for non-GKI; coexistence check with tracepoint-hook
  default.
- Port `KpmPage` + `KpmViewModel`, template docs link, CI LKM matrix impact.
- Last: touches hook paths, regression risk.
- Why ★★★★★: rewrites patch/execution paths in the kernel; highest
  boot-loop and stability risk, hardest to review.

## Suggested extras
- (a) Magic Mount (MKSU `magic_mount.rs` port, userspace-only,
  module-compat win) ★★☆☆☆.
- (b) GKI OTA survival polish in Install screen copy ★☆☆☆☆.

Order: 1 → 2 → 3 → 4 → 5.
