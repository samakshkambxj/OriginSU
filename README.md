# OriginSU
<img align='right' src='docs/OriginSU-default.png' width='220px' alt="OriginSU Logo">

**Root your Android device. Keep control of it.**

OriginSU is a kernel-based root solution with a manager app that brings root access,
module management, hiding, kernel flashing and performance tuning into one place.
It is built on [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) (SukiSU-Ultra →
KernelSU lineage), with its own kernel and manager signing identity, UAPI baseline
and release pipeline.

<div align="center">

  [![Latest release](https://img.shields.io/github/v/release/samakshkambxj/OriginSU?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/releases/latest)
  [![Build](https://img.shields.io/github/actions/workflow/status/samakshkambxj/OriginSU/build-manager.yml?branch=main&label=Build&logo=github)](https://github.com/samakshkambxj/OriginSU/actions/workflows/build-manager.yml)
  [![Kernel License: GPL v2](https://img.shields.io/badge/Kernel-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
  [![Manager License: GPL v3](https://img.shields.io/github/license/samakshkambxj/OriginSU?label=Manager&logo=gnu)](/LICENSE)

</div>

📖 **Documentation: <https://samakshkambxj.github.io/OriginSU/>**

> [!IMPORTANT]
> Install only the official manager APK from the
> [Releases](https://github.com/samakshkambxj/OriginSU/releases) page —
> the file whose name ends in `-release.apk`. Debug, PR, dev or resigned
> builds use a different certificate and the kernel will reject them as
> unofficial.

**Jump to:** [Start here](#start-here) · [What you get](#what-you-get) ·
[Compatibility](#compatibility) · [Downloads](#downloads-explained) ·
[Troubleshooting](#troubleshooting) ·
[For kernel developers](#for-kernel-developers)

## Why OriginSU?

- **Hiding is part of the system, not an add-on.** Origin Veil cloaks root from
  selected apps, the SuSFS manager has a one-tap strong-hiding preset, and
  OriginGuard audits module zips before installation.
- **Fewer separate downloads.** OriginZygisk is bundled in the manager, and
  OriginTune only shows kernel settings your device actually supports.
- **Flashing that feels like an installer.** Direct install, AnyKernel3 zips,
  offline `boot.img` patching, LKM/GKI flows and KPM options.
- **Safety nets included.** Bootloop rescue, a biometric root gate and a
  first-run setup wizard.

## Start here

New to OriginSU? Do these three things:

1. **Check your device.** GKI 2.0 devices (kernel 5.10+) are officially
   supported. Older kernels (3.4+) work, but need a manually built kernel.
   See [Compatibility](#compatibility).
2. **Download the manager.** Get the official manager APK from the
   [Releases](https://github.com/samakshkambxj/OriginSU/releases) page —
   the file whose name ends in `-release.apk`. Use `arm64-v8a` for most
   modern phones; `universal` works on any device.
3. **Install root.** Pick the method that matches your situation:

| Your situation | Easiest path |
| --- | --- |
| Most phones, no custom kernel | **LKM mode (recommended; root loads as a kernel module):** `fastboot boot` a compatible GKI kernel for temporary root, then use **Install → Direct install / Select a file** in the manager. |
| You have a GKI `boot.img` | **GKI mode:** flash it with `fastboot flash boot boot.img && fastboot reboot`, a kernel-flasher app, or custom recovery. |
| You have no root yet | **Offline patching:** the manager patches a `boot.img` into `Downloads/OriginSU/` for you to flash manually. |
| You build your own kernel | Integrate OriginSU into your kernel tree, enable `CONFIG_KSU`, rebuild and flash. See [For kernel developers](#for-kernel-developers). |

After installing, open the manager and follow the first-run setup wizard. Home
should show your kernel/LKM status. The
[install guide](https://samakshkambxj.github.io/OriginSU/guide/install) covers
the same steps in more detail.

> [!TIP]
> Coming from KernelSU, RKSU, MKSU or SukiSU? OriginSU's manager also works with
> those kernels, and its kernel accepts those official managers. On a
> third-party kernel the manager runs in **compat mode**: grant it root in your
> current manager and you keep modules, flashing (including another fork's LKM
> `.ko` via **Install → Use local LKM file**) and reboot support. Origin-exclusive
> features wait for an OriginSU kernel.

## What you get

### Root and app control

- Kernel-based `su` with a clear per-app permission model.
- **App profiles** that cage root power per app, including time-limited grants
  (10 min / 30 min / 1 h / 8 h / 24 h) that are revoked automatically when they
  expire.
- **Biometric secure root gate** — require confirmation before granting root.
- **Built-in updater** — stable and beta channels, with normal and spoofed
  manager variants.
- **Temporary uninstall (LKM)** — unload the module for the current boot; the
  patched boot image restores root on the next reboot.

### Hiding and detection

- **Origin Veil** — kernel-side root-probe detection with a per-app cloak set,
  reboot persistence, per-app detail (permissions / freeze / force-stop), a Spy
  live log and su-notify actions (Grant / Cloak / Ignore).
- **SuSFS manager** — built-in control panel with a one-tap strong-hiding
  preset (kernel 4.3+ backport). On LKM setups, the **SuSFS LKM** screen picks
  the matching guard module, enables boot autoload, shows the loaded module and
  can recover from autoload boot loops.
- **OriginGuard** — static pre-install audit of module zips. Critical findings
  block the install; high-severity findings ask first.

### Modules

- Module system based on
  [metamodules](https://kernelsu.org/guide/metamodule.html) for systemless
  modifications.
- **Magic Mount** for modules that ship a `system/` tree instead of a
  metamodule mount script — overlayfs by default, with a bind-mount fallback,
  a backend selector in **Settings → Origin Lab**, and active mount-engine
  detection for NoMount, ZeroMount, Kagami/Kasumi and Hybrid Mount.
- Batch multi-select actions, update-all multi-flash, internal backups with
  restore, shareable bundle zips, and download-script (JSON) batch install.
- `banner=` artwork from `module.prop`, shown as a faded backdrop in the module
  list.

### OriginZygisk

- Zygisk engine bundled directly in the manager — no separate module to hunt
  down.
- Deploy, on/off toggle, status, integrity check/repair and OriginZygisk unmount
  from **Settings → Origin Lab**.
- Native control panel with module health, per-app skip choices, pause
  injection, telemetry and boot log.
- Per-app skip policies and per-module toggle/quarantine.
- `ZYGISK_ENABLED=1` is exported to module installers and boot-stage scripts
  when enabled.
- Diagnostics export bundles state, boot logs, telemetry, policy files, pstore,
  filtered logcat, dmesg and crash snapshots.

### Flashing and recovery

- Direct install, AnyKernel3 zips (legacy busybox runner), offline `boot.img`
  patching, and local LKM files.
- LKM mode with per-KMI module selection and tracepoint/tamper hook-flavor
  picker.
- GKI flows and KPM options for supported kernels.
- **Bootloop protection** disables modules after consecutive failed boots and
  tells you what happened.
- OriginGuard shows what a blocked module zip tripped on.

### Kernel tuning

OriginTune gives you a tuning lab from the Home hero card. Every setting applies
immediately and can persist across boot; only knobs your running kernel exposes
are shown.

- CPU frequency: per-cluster governor and min/max frequencies, plus schedutil
  rate-limit tunables.
- GPU: governor and min/max clocks across Adreno/kGSL and devfreq (Mali) paths.
- I/O: per-device scheduler selection and read-ahead size.
- Memory presets: swappiness, dirty ratios and cache pressure.
- Scheduler extras: uclamp and energy-aware scheduling where supported.
- LMK levels: lmkd minfree table and kernel-driver parameters with presets and
  stock restore.
- TCP congestion control, BORE scheduler presets, ZRAM size/algorithm/streams
  and a generic sysctl editor.
- Profile sharing: export/import tuning setups as JSON, with an automatic backup
  before applying.
- Diagnostics: live clocks, load average, ZRAM ratio and PSI stalls.

### Make it yours

Material and MIUIX styles, Standard/Compact layouts, compact working hero card,
last-flash timeline chip, copy-device-info, Origin Dynamic / Monet / Default
launcher icons, Yin Yang top-bar logo options, themed shortcuts, a floating
bottom bar, blurred popups, module banners and MIUI elastic overscroll with
scroll haptics.

## A quick tour

| Screen | What it is for |
| --- | --- |
| **Home** | Root/kernel status, last flash, device info, OriginTune hero card and live status cards. |
| **SuperUser** | Apps that requested or received root, plus per-app profiles and time-limited grants. |
| **Modules** | Install, update, back up, share and batch-manage modules. |
| **Install** (from Home or Modules) | Direct install, local LKM files, AnyKernel3 zips, offline patching and KPM. |
| **Settings** | Updates, appearance, security, language and developer options. |
| **Settings → Origin Lab** | Origin Veil, OriginZygisk, SuSFS LKM, OriginGuard, bootloop protection and other advanced tools. |

## Compatibility

| Requirement | Support |
| --- | --- |
| GKI 2.0 devices (kernel 5.10+) | ✅ Officially supported |
| Older kernels (3.4+, including GKI 1.0) | ⚠️ Compatible, but the kernel must be built manually |
| Architectures | Prebuilt LKMs: `aarch64` (most phones) and `x86_64`. Manager APKs ship `arm64-v8a`, `armeabi-v7a`, `x86_64`, `riscv64` and `universal`. |
| SuSFS guard LKM | `arm64-v8a` devices running KernelSU in LKM mode |
| [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) backport | Kernel 4.3+ only |
| Tracepoint Syscall Redirect hook | GKI2 (5.10+) kernels only |
| KPM (`CONFIG_KPM`) | 64-bit kernels only |

**Terms:** GKI = Generic Kernel Image (Android's standardized kernel). LKM =
loadable kernel module (root loads after boot instead of being built into the
kernel). KPM = Kernel Patch Module.

## Downloads explained

Each `v*` tag publishes a GitHub Release. Pre-release tags containing `-rc` are
published as GitHub pre-releases.

| Asset | What it is |
| --- | --- |
| `OriginSU_<version>_<code>-<abi>-release.apk` | **Install this.** Signed manager build for `arm64-v8a`, `armeabi-v7a`, `x86_64`, `riscv64` or `universal`. Use `arm64-v8a` for most phones; `universal` works on any supported ABI but is larger. |
| `<arch>-<kmi>-lkm-<kmi>_kernelsu.ko` | Root LKM with the default tracepoint hook. Match `<kmi>` to your kernel — for example `aarch64-android14-6.1-lkm-android14-6.1_kernelsu.ko`. |
| `<arch>-<kmi>-lkm-tamper-<kmi>_kernelsu.ko` | Same LKM with the stealth syscall-table hook instead of tracepoints. |
| `aarch64-<kmi>-susfs-lkm-<kmi>_susfs_guard_lkm.ko` | SuSFS guard module for arm64 devices. Load it from **Settings → Origin Lab → SuSFS LKM**. |

**Which file do I need?** Use the manager APK that matches your device ABI. For
an LKM, match both the `<arch>` prefix and the `<kmi>` segment to your device and
kernel. `aarch64` is for phones; `x86_64` covers emulators and some tablets.
`-tamper-` only changes the hook method. The `susfs` file is the guard module,
not the root LKM.

SuSFS guard modules are built for arm64 only. If your KMI or architecture is
missing, use GKI or offline patching, or
[build the kernel yourself](#for-kernel-developers).

> [!NOTE]
> CI also builds debug and spoofed-package managers. Spoofed builds are official
> and the in-app updater can install them under a randomized package name. Debug
> builds are signed with a different certificate and the kernel rejects them as
> unofficial.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Kernel reports manager as **unofficial** | You installed a debug / dev / PR / resigned APK. Install the official `*-release.apk` manager from Releases. |
| No root after flashing | Reopen the manager and run the setup wizard; check kernel / LKM status on Home. For LKM, confirm the `.ko` KMI matches your kernel and the `arch-` prefix matches your device. |
| Bootloop after installing a module | Wait for the bootloop protector to disable all modules, reboot, then re-enable modules one by one. OriginGuard shows what a blocked zip tripped on. |
| Root-detecting app still sees root | Add it to the Origin Veil cloak set, check per-app detail + Spy log, and apply the SuSFS strong-hiding preset. |
| KPM tab is hidden | Expected on 32-bit or non-KPM kernels — the tab only appears when the kernel reports KPM support. |
| OriginZygisk shows an error | Open **Settings → Origin Lab** and check the engine status, integrity/repair and boot log. Conflicting provider modules are blocked at install. |

## For kernel developers

### Add OriginSU to a kernel tree

Run this in the **root of your kernel source**, just like the other
KernelSU-family forks:

```bash
# Latest stable tag (recommended)
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -

# Development branch
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s main

# A specific tag / commit
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s v4.2.5
```

Then enable `CONFIG_KSU` — plus `CONFIG_KPM` on 64-bit kernels if you want KPM
support — rebuild and flash.

```bash
# Remove a previous integration
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --cleanup

# Register the downloaded copy as a git submodule of your kernel tree
# (run from your kernel source root after integrating)
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --submodule
```

Prefer manual integration? Clone the repo, symlink `kernel/` to
`drivers/kernelsu`, add `obj-$(CONFIG_KSU) += kernelsu/` to
`drivers/Makefile`, and source `drivers/kernelsu/Kconfig` from
`drivers/Kconfig`.

### Hook modes

| Mode | Description |
| --- | --- |
| `Tracepoint Syscall Redirect hook` | Default mode, from [upstream](https://github.com/tiann/KernelSU). GKI2 kernels with `arm64-v8a` or `x86_64` ABI only. |
| `Syscall Table Tampering` (`CONFIG_KSU_TAMPER_SYSCALL_TABLE`) | Stealth mode: patches hooked entries directly in `sys_call_table` and registers no `sys_enter` tracepoint. GKI2 `arm64-v8a`/`x86_64` only. CI builds both LKM flavors per KMI, and releases ship them side by side; pick one in the Install screen's hook-flavor selector. |
| `Manual Hook` | Most compatible; supports Linux kernels 3.4 – 6.18. |
| `SuSFS Inline Hook` | From [SuSFS](https://github.com/simonpunk/susfs4ksu), like `Manual Hook` but provided by the SuSFS project. |

### Build and release

Releases are built by CI (`.github/workflows/`) and signed with the project
release key. Pushing a `v*` tag publishes a GitHub Release with the manager APKs
and LKM modules.

Required Actions secrets: `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

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

## Roadmap

- GKI OTA survival polish
- Broader KPM support
- OriginZygisk ZN native modules and module WebUI
- More manager customization

## Contributing

Bug reports and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
Manager translations are managed on
[Weblate](https://hosted.weblate.org/engage/ReSukiSU); kernel changes must stay
GPL-2.0-compatible. Please report security issues privately (see
[SECURITY.md](SECURITY.md)) rather than in public issues.

## License

- Files in the `kernel` directory are under
  [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- The OriginSU logo (`docs/OriginSU*.svg`, launcher icons) is original work
  for this project, under the same terms as the manager (GPL-3.0-or-later).
- Everything else is under
  [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

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
- [ReSukiSU-Ultima](https://github.com/spacealtctrl/ReSukiSU-Ultima): for sentinel, which OriginVeil is inspired from
