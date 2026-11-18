<div align="center">

<img src="docs/KazeSU.svg" width="140px" alt="KazeSU logo">

# KazeSU

**A kernel-based root solution for Android, with the whole toolkit in the manager.**

[Releases](https://github.com/KazeSU/KazeSU/releases) ·
[Documentation](docs/README.md) ·
[Issues](https://github.com/KazeSU/KazeSU/issues)

[![Latest release](https://img.shields.io/github/v/release/KazeSU/KazeSU?label=Release&logo=github)](https://github.com/KazeSU/KazeSU/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/KazeSU/KazeSU/total?label=Downloads&logo=github)](https://github.com/KazeSU/KazeSU/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/KazeSU/KazeSU/build-manager.yml?label=Build&logo=github)](https://github.com/KazeSU/KazeSU/actions/workflows/build-manager.yml)
[![Kernel License: GPL v2](https://img.shields.io/badge/Kernel-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![Manager License: GPL v3](https://img.shields.io/github/license/KazeSU/KazeSU?label=Manager&logo=gnu)](/LICENSE)

</div>

---

<img align="right" src="docs/KazeSU-mini.png" width="180px" alt="KazeSU">

KazeSU is a kernel-based root solution built on
[SukiSU Nova](https://github.com/SukiSU-Nova/SukiSU-Nova), the KernelSU →
SukiSU-Ultra lineage. The kernel does the work; the manager does everything
around it — App Profile, KPM, a built-in Zygisk, themes, SUSFS tooling and the
kernel extension center, without a pile of companion apps.

> [!IMPORTANT]
> Install only the official manager APK from the
> [Releases](https://github.com/KazeSU/KazeSU/releases) page — the file whose
> name ends in `-release.apk`. Debug, PR and CI builds use a different
> certificate and the kernel rejects them as unofficial.

## Features

1. Kernel-based `su` with per-app profiles and temporary root grants.
2. [App Profile](https://sukisu.org/guide/app-profile): keep root access in a cage.
3. KPM support (`CONFIG_KPM=y`), with an APatch compatibility layer in progress.
4. KazeZygisk — a built-in Zygisk engine, so no separate Zygisk module is
   needed; running status is shown right on the home screen.
5. Theme Store: browse, install and submit themes, with multi-source mirrors,
   author authorization and review.
6. Kernel Extension Center: CPU/vvar spoofing, AVC log spoofing, version
   spoofing, SUSFS and mount view hiding in one place.
7. Built-in SUSFS management with one-tap root hiding configuration.
8. Dual UI: Miuix / Material 3 Expressive, Miuix splash screen, module banners
   and sorting, and a kernel-free preview mode.
9. KazeGuard pre-install audit: reviews module ZIPs for known malware
   signatures and destructive commands before they flash. Extreme findings block
   the install until you confirm; high findings warn loudly but don't stop a
   normal install; audit failures fail open so a scanner bug can never break
   installs. Rule catalog is bundled, cached on the device, and refreshable with
   `ksud module guard update`.
10. Bootloop protection: ksud counts boots that reach `post-fs-data` but never
    finish, and disables every module once the threshold (2–10, default 3) is
    crossed. A rescue notice with Dismiss tells you what happened so you can
    re-enable modules one by one to find the culprit.
11. KazeTune kernel tuning: live read/write of CPU frequency, GPU clocks, block
    I/O, VM presets, scheduler extras, LMK levels, TCP congestion control, BORE
    scheduler, ZRAM and a generic sysctl editor. Tunables can be persisted so
    they re-apply on every boot through ksud's boot-completed hook.

## Compatibility

- GKI 2.0 devices (kernel 5.10+) are supported out of the box.
- Older kernels (minimum 4.14+) work, but you must build the kernel yourself;
  with backports, 3.4–3.18 kernels can be supported.
- Architectures: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `riscv64`.
- KazeSU evolved independently after forking from KernelSU, so compatibility is
  based on testing in this repository, not the official KernelSU support scope.

| Scenario | Method | Notes |
| --- | --- | --- |
| GKI devices (kernel 5.10+) | LKM mode | Recommended; flash the `.ko` matching your KMI. |
| Non-GKI / old kernels (3.0+) | Compile the kernel | See [kernel integration](#kernel-integration). |
| KPM features required | `CONFIG_KPM=y` | Non-GKI also needs `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`. |

## Install

1. Flash a KazeSU kernel, or load the LKM with the manager after granting it
   root through your current root solution.
2. Install the manager APK from [Releases](https://github.com/KazeSU/KazeSU/releases)
   (`KazeSU_<version>_<code>-<abi>-release.apk`).
3. Open the manager and follow the first-run setup.

On a third-party KernelSU/SukiSU kernel the manager runs in compat mode: grant
it root in your existing manager and you keep modules, flashing and reboot
support. Kernel-side features need a KazeSU kernel.

## Kernel integration

Run this in the **root of your kernel source**:

```bash
# Latest tag (recommended)
curl -LSs "https://raw.githubusercontent.com/KazeSU/KazeSU/main/kernel/setup.sh" | bash -

# A specific branch or tag
curl -LSs "https://raw.githubusercontent.com/KazeSU/KazeSU/main/kernel/setup.sh" | bash -s dev
```

Then enable `CONFIG_KSU` — and `CONFIG_KPM` on 64-bit kernels for KPM.

```bash
# Remove a previous integration
curl -LSs "https://raw.githubusercontent.com/KazeSU/KazeSU/main/kernel/setup.sh" | bash -s --cleanup
```

## Build

```bash
just bm   # cross-build ksud (aarch64) + bundled debug manager APK
just bk   # ksud only
```

`ksud` is built with [`cross`](https://github.com/cross-rs/cross) for
`aarch64-linux-android`; the manager is a Gradle/Kotlin project under
`manager/`. Kernel modules build from `kernel/`.

## Repository layout

| Path | Contents |
| --- | --- |
| `kernel/` | Kernel module: hooks, policy, SUSFS, KPM, preinit, security. |
| `userspace/` | `ksud` (Rust daemon), `ksuinit`, `sui`, module tooling. |
| `manager/` | Android manager app (Compose, Miuix/Material), `ai-api`. |
| `uapi/` | Shared kernel/userspace headers. |
| `docs/` | Project README, guides and logo assets. |
| `.github/` | CI workflows, self-hosted runner image, release assembly. |

## License

- `kernel/` is [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- The anime `ic_launcher*` icon files are covered by
  [`LICENSE_icon_English`](LICENSE_icon_English) /
  [`LICENSE_icon_SC`](LICENSE_icon_SC), authored by 怡子曰曰 and owned by 明风OuO.
- The KazeSU logo (`docs/KazeSU*.svg`) is original artwork for this project.
- Everything else is [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

## Acknowledgements

- [KernelSU](https://github.com/tiann/KernelSU): upstream.
- [SukiSU Nova](https://github.com/SukiSU-Nova/SukiSU-Nova) and
  [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra): fork lineage.
- [/xxksu](https://github.com/backslashxx/KernelSU): kernel compatibility reference.
- [susfs](https://gitlab.com/simonpunk/susfs4ksu): hiding patches and userspace modules.
- [KernelPatch](https://github.com/bmax121/KernelPatch): KPM foundation.
- ShirkNeko and the SukiSU Nova maintainers for the base this branch is rebased on.
