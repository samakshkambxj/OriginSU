# OriginSU Feature-Porting Plan

Sources: WildKernels/Wild_KSU (archived), spacealtctrl/ReSukiSU-Ultima,
tiann/KernelSU (official manager). Phases are ordered so each ships and
tests independently. Baseline: CI green, `CERT_MAX_LENGTH=2048` fix in.

Difficulty scale: ★☆☆☆☆ trivial → ★★★★★ deep kernel surgery.

## Phase 1 — Wild_KSU boot tooling ★★☆☆☆
(userspace + manager, no kernel change)
- Problem: `anykernel3.rs` bails on any AK3 zip without the mkbootfs
  injection marker; nothing provides `magiskboot` for scripts that need it.
- Vendor `libmagiskboot.so` (arm64 + x86_64, from Wild_KSU) into
  `manager/app/src/main/jniLibs`; `install --magiskboot <path>` copies it to
  ksud's bin dir (Wild `utils.rs:189`, `defs.rs:16`, `KsuCli.kt:122`).
- Port `flashAnyKernelZip`: update-binary presence validation, extract to
  cache, execute via busybox ash with AKHOME, root cleanup; wire as an
  Install-screen option.
- Verify: flash a non-mkbootfs AK3 zip on device.

## Phase 2 — MIUI / official theme options ★☆☆☆☆
(manager only)
- Diff `tiann/KernelSU` manager Settings theme block against
  `ThemeSettings` / `SettingsViewModel`; port missing options.
- Follow `manager/AGENTS.md` (settings widgets, `stringResource`, all locales).
- Why ★: pure UI work, zero risk to root functionality.

## Phase 3 — Built-in Zygisk ★★★☆☆
(userspace + manager + CI, no kernel change)
- Port Ultima `manager/.../assets/zygisk` engine (ReZygisk-derived, GPL-3.0;
  keep attribution), ksud deploy path, Settings toggle (off by default) +
  `post-fs-data.d` kill-switch.
- Extend `getZygiskImplement()` to report the built-in engine.
- CI: package zygisk assets in repack.
- Why ★★★: ptrace injector lifecycle is fiddly (boot loops risk), but no
  kernel changes; kill-switch bounds the blast radius.

## Phase 4 — Origin Veil ★★★★☆
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

## Phase 5 — KPM support ★★★★★
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
