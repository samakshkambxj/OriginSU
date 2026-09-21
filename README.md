<div align="center">

  <img src="docs/OriginSU-default.png" width="160" alt="OriginSU Logo">

  # OriginSU

  **A kernel-based root solution for Android — with its own identity.**

  Built on [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) (SukiSU-Ultra → KernelSU lineage)
  with its own kernel + manager signing identity, UAPI baseline and release pipeline.

  [![Latest release](https://img.shields.io/github/v/release/samakshkambxj/OriginSU?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/releases/latest)
  [![Release workflow](https://img.shields.io/github/actions/workflow/status/samakshkambxj/OriginSU/release.yml?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/actions/workflows/release.yml)
  [![Kernel License: GPL v2](https://img.shields.io/badge/Kernel-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
  [![Manager License: GPL v3](https://img.shields.io/github/license/samakshkambxj/OriginSU?label=Manager&logo=gnu)](/LICENSE)

</div>

> [!IMPORTANT]
> Install only the official `Manager-release` APK from the
> [Releases](https://github.com/samakshkambxj/OriginSU/releases) page.
> Debug, PR, dev or resigned builds use a different certificate and the
> kernel will reject them as unofficial.

## Contents

- [Why OriginSU](#why-originsu)
- [Quick start](#quick-start)
- [Release assets](#release-assets)
- [Features](#features)
- [Manager tour](#manager-tour)
- [Compatibility](#compatibility)
- [Hook modes](#hook-modes)
- [Building](#building)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Credits](#credits)

## Why OriginSU

- **Kernel-first root**: `su` access control lives in the kernel, managed per-app with profiles — including time-limited grants that auto-revoke.
- **Hiding built in, not bolted on**: Origin Veil cloaks root traces per app, SuSFS manager ships with a one-tap strong-hiding preset, and every module zip passes through the OriginGuard pre-install audit.
- **No Zygisk module hunt**: OriginZygisk (BreZygisk-based) ships inside the manager — deploy and toggle it from Settings, no provider module required.
- **Kernel tuning with guardrails**: OriginTune exposes only the knobs your running kernel actually supports — CPU governor/frequencies, GPU clocks, I/O scheduler, memory presets, scheduler extras, LMK levels, TCP congestion, BORE presets, ZRAM, sysctls — applied live, optionally persisted, shareable as JSON profiles with live diagnostics.
- **Installer-grade flashing**: direct install, AnyKernel3 zips, offline `boot.img` patching without root, Horizon kernels, plus LKM and GKI flows with KPM patch / undo-patch options.
- **Safety nets**: automatic bootloop rescue (disables all modules after consecutive failed boots), biometric root gate, first-run setup wizard.

## Quick start

1. Flash an OriginSU-patched kernel (GKI `boot.img`, LKM module, or a manually built kernel for older devices — see [Release assets](#release-assets)).
2. Install the official `Manager-release` APK from the
   [Releases](https://github.com/samakshkambxj/OriginSU/releases) page.
3. Open the manager and follow the first-run setup wizard (status check, LKM / built-in detection, kernel check).

> [!TIP]
> Coming from KernelSU / RKSU / MKSU / SukiSU? OriginSU's manager also works with those kernels, and its kernel accepts those official managers.

## Release assets

Each `v*` tag publishes a GitHub Release with:

| Asset | What it is |
| --- | --- |
| `Manager-release` APKs (`arm64-v8a`, `armeabi-v7a`, `universal`) | The only manager builds the OriginSU kernel trusts. Pick the APK matching your ABI (`arm64-v8a` for most modern phones). |
| `Spoofed-Manager-release` APK | Same manager with a randomized package name — installs side-by-side, useful when the stock package name is detected. |
| `Manager-debug` APK | Debug build for development. The kernel rejects it as unofficial — do not use for daily root. |
| `<arch>-<kmi>-lkm[-tamper]-<kmi>_kernelsu.ko` | Loadable kernel modules per KMI and architecture (`aarch64-*` for phones, `x86_64-*` for emulator/x86 tablets). Match `<kmi>` to your kernel (e.g. `android14-6.1`); the `arch-` prefix tells the two same-KMI files apart. `-tamper-` variants use the stealth syscall-table hook instead of the tracepoint default. |

Pre-release tags containing `-rc` are published as GitHub pre-releases.

## Features

### Core root

- Kernel-based `su` and root access management
- Module system based on [metamodules](https://kernelsu.org/guide/metamodule.html) for systemless modifications — with batch multi-select actions, update-all multi-flash, and `banner=` artwork from `module.prop`
- [App profiles](https://kernelsu.org/guide/app-profile.html): cage root power per app, with **time-limited grants** (10 min / 30 min / 1 h / 8 h / 24 h) revoked by a persistent waiter plus a sweep on every `ksud` start
- GKI 2.0, GKI 1.0 and non-GKI (3.4+) kernel support
- Manager interop: works with official [KernelSU](https://github.com/tiann/KernelSU), [RKSU](https://github.com/rsuntk/KernelSU), [MKSU](https://github.com/5ec1cff/KernelSU) and [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra) kernels

### Origin Lab (Settings)

| Capability | Notes |
| --- | --- |
| **Origin Veil** | Kernel-side root-probe detection with a per-app cloak set, reboot persistence, per-app detail (permissions / freeze / force-stop), Spy live log and su-notify (Grant / Cloak / Ignore). |
| **OriginZygisk** | BreZygisk-based engine bundled in the manager (`Settings > Origin Lab`): deploy, status, kill-switch. Conflicting provider modules are blocked at install; `ZYGISK_ENABLED=true` is exported to module installers when on. |
| **SuSFS manager** | Built-in manager with a one-tap strong-hiding preset (kernel 4.3+ backport). |
| **OriginGuard** | Static pre-install audit of module zips — critical findings block the install, high-severity findings ask first. Toggle in `Settings > Origin Lab`. |
| **KPM** | Kernel `CONFIG_KPM` (64-bit only) plus a manager KPM tab gated on KPM status: load now or embed to `/data/adb/kpm`, with patch / undo-patch options at flash time. |
| **Bootloop protection** | Disables all modules after consecutive failed boots and tells you about the rescue. |
| **Magic Mount** | Magisk-style module mounting with a backend selector (Auto / Overlayfs / Bind); takes effect on next boot. |
| **Kernel flasher** | Direct install, AnyKernel3 zips (legacy busybox runner), offline `boot.img` patching to `Downloads/OriginSU/` without root, Horizon kernels; LKM (per-KMI module plus tracepoint/tamper hook-flavor picker) and GKI flows. |
| **Updater** | Stable / beta channels with dynamic-manager support. |
| **Secure root gate** | Biometric confirmation before granting root, with a Settings toggle. |

### OriginTune (home hero card → tuning lab)

Kernel tuning from a home hero card — every knob applies immediately and can persist across boot, and only knobs the running kernel exposes are shown:

- CPU frequency: per-cluster governor and min/max frequencies, plus schedutil rate-limit tunables
- GPU: governor and min/max clocks across Adreno/kGSL and devfreq (Mali) paths
- I/O: per-device scheduler selection and read-ahead size
- Memory preset profiles: curated VM presets (swappiness, dirty ratios, cache pressure) in the style of the BORE profiles
- Scheduler extras: uclamp and energy-aware scheduling tunables where the kernel exposes them
- LMK levels: lmkd minfree table and kernel-driver params with per-level presets and stock restore
- Profile sharing: export/import tuning setups as JSON, with automatic backup before applying
- Diagnostics: visible effect per tuning (live clocks, load average, ZRAM ratio and PSI stalls)
- TCP congestion control
- BORE scheduler presets (Balanced / Responsive / Throughput / Battery saver) plus manual knobs
- ZRAM size / algorithm / streams / swappiness with live compression stats
- Generic sysctl editor

See [Roadmap](#roadmap) for the CPU / GPU / I/O / LMK / profile-sharing work queued next.

### Manager tour

- **Home your way**: Material and MIUIX styles, MIUIX Standard / Compact layouts, compact working hero card, OriginTune hero card, last-flash timeline chip, copy-device-info.
- **Live status cards**: KPM, BasebandGuard (kernel LSM, version read from `dmesg` and cached per kernel release) and ZeroMount (VFS driver at `/dev/zeromount` + module version) versions right on Home.
- **Make it yours**: Origin Dynamic / Monet / Default launcher icons (plus upstream marks), Yin Yang top-bar logo options, themed shortcuts toggle, floating Apple-style bottom bar, blurred popups, module banners, MIUI elastic overscroll with scroll haptics.
- **Module safety**: OriginGuard audit runs before every module install; batch enable / disable / uninstall for cleanup.

## Compatibility

| Requirement | Support |
| --- | --- |
| GKI 2.0 devices (kernel 5.10+) | ✅ Officially supported |
| Older kernels (3.4+) | ⚠️ Compatible, but the kernel has to be built manually |
| Architectures | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) backport | Kernel 4.3+ only |
| Tracepoint Syscall Redirect hook | GKI2 (5.10+) kernels only |
| KPM (`CONFIG_KPM`) | 64-bit kernels only |

## Hook modes

| Mode | Description |
| --- | --- |
| `Tracepoint Syscall Redirect hook` | Default mode, from [upstream](https://github.com/tiann/KernelSU); GKI2 kernels with `arm64-v8a` or `x86_64` ABI only |
| `Syscall Table Tampering` (`CONFIG_KSU_TAMPER_SYSCALL_TABLE`) | Stealth mode: patches the hooked entries directly in `sys_call_table`, registers no `sys_enter` tracepoint; GKI2 `arm64-v8a`/`x86_64` only. CI builds both LKM flavors per KMI and releases ship them side by side; pick one in the Install screen's hook-flavor selector |
| `Syscall Table Tampering` (`CONFIG_KSU_TAMPER_SYSCALL_TABLE`) | Stealth mode: patches the hooked entries directly in `sys_call_table`, registers no `sys_enter` tracepoint; GKI2 `arm64-v8a`/`x86_64` only. CI builds both LKM flavors per KMI and releases ship them side by side; pick one in the Install screen's hook-flavor selector |
| `Manual Hook` | Most compatible; supports Linux kernels 3.4 – 6.18 |
| `SuSFS Inline Hook` | From [SuSFS](https://github.com/simonpunk/susfs4ksu), like `Manual Hook` but provided by the SuSFS project |

## Building

Releases are built by CI (`.github/workflows/`), signed with the project release key.
Pushing a `v*` tag publishes a GitHub Release with the manager APKs and LKM modules.

Required Actions secrets: `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

```bash
# tag a release (maintainers)
git tag -a vX.Y.Z -m "OriginSU vX.Y.Z"
git push origin vX.Y.Z
```

Local iteration:

```bash
just build_manager   # ksud (aarch64) + manager debug APK
just build_ksud      # ksud only
```

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Kernel reports manager as **unofficial** | You installed a debug / dev / PR / resigned APK. Install the official `Manager-release` APK from Releases. |
| No root after flashing | Reopen the manager and run the setup wizard; check kernel / LKM status on Home. For LKM, confirm the `.ko` KMI matches your kernel and the `arch-` prefix matches your device. |
| Bootloop after installing a module | Wait for the bootloop protector to disable all modules, reboot, then re-enable modules one by one. OriginGuard audit logs explain what a blocked zip tripped on. |
| Root-detecting app still sees root | Add it to the Origin Veil cloak set, check per-app detail + Spy log, and apply the SuSFS strong-hiding preset. |
| KPM tab is hidden | Expected on 32-bit or non-KPM kernels — the tab only appears when the kernel reports KPM support. |
| Which LKM file do I need | `aarch64-*` for phones, `x86_64-*` for x86 devices; the `<kmi>` segment (e.g. `android14-6.1`) must match your kernel. |

## Roadmap

- Extensive manager customization support
- GKI OTA survival polish

### OriginTune roadmap

- CPU ✅ (shipped): per-cluster governor and min/max frequency, plus schedutil rate-limit tunables
- GPU ✅ (shipped): governor and min/max clocks across Adreno/kGSL and Mali paths
- I/O ✅ (shipped): per-device scheduler selection and read-ahead size
- Memory preset profiles ✅ (shipped): curated VM presets (swappiness, dirty ratios, cache pressure) in the style of the BORE profiles
- Scheduler extras ✅ (shipped): uclamp and energy-aware scheduling tunables where the kernel exposes them
- LMK tuning ✅ (shipped): `lmkd`/PSI knobs with per-level presets
- Profile sharing ✅ (shipped): export/import tuning setups as JSON, with automatic backup before applying
- Diagnostics ✅ (shipped): visible effect per tuning (live clocks, load average, ZRAM ratio and PSI stalls)

See `docs/feature-porting-plan.md` for the full porting status.

## Contributing

Bug reports and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
Manager translations live on Weblate; kernel changes must stay GPL-2.0-compatible.
Please report security issues privately (see [SECURITY.md](SECURITY.md)) rather than in public issues.

## License

- Files in the `kernel` directory are under
  [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- The OriginSU logo (`docs/OriginSU*.svg`, launcher icons) is original work
  for this project, under the same terms as the manager (GPL-3.0-or-later).
- Everything else is under [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

## Credits

- [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU): fork source
- [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra): upstream of the fork source
- [KernelSU](https://github.com/tiann/KernelSU): upstream
- [MKSU](https://github.com/5ec1cff/KernelSU): Magic Mount
- [RKSU](https://github.com/rsuntk/KernelsU): non-GKI support
- [susfs](https://gitlab.com/simonpunk/susfs4ksu): root-hiding kernel patches and userspace module
- [KernelPatch](https://github.com/bmax121/KernelPatch): key part of the APatch kernel-module implementation
- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): the KernelSU idea
- [Magisk](https://github.com/topjohnwu/Magisk): the powerful root tool
- [genuine](https://github.com/brevent/genuine/): APK v2 signature validation
- [Diamorphine](https://github.com/m0nad/Diamorphine): some rootkit skills
- [WildKSU](https://github.com/WildKernels/Wild_KSU): launcher icon artwork, themed app-shortcut icons and animated home logo concept
