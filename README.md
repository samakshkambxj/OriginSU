# OriginSU
<img align='right' src='docs/ReSukiSU_blue.svg' width='220px' alt="OriginSU Icon">

**English**

A KernelSU-based root solution for Android, forked from
[ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) with its own official
manager signing identity and release automation.

[![Latest release](https://img.shields.io/github/v/release/samakshkambxj/OriginSU?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/releases/latest)
[![Kernel License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![Other part License：GPL v3](https://img.shields.io/github/license/samakshkambxj/OriginSU?logo=gnu)](/LICENSE)

## Current focus

Full compliance with the **Origin kernel**: the manager and the kernel
share one signing identity, one UAPI baseline and one release pipeline, so
an official build pair always detects root, never warns about unofficial
signatures, and supports large (4096-bit) release certificates on both
sides. See `docs/feature-porting-plan.md` for the roadmap.

## Features

OriginSU keeps **all original ReSukiSU features** — kernel-based `su`,
metamodule system, App Profile cage, non-GKI/GKI 1.0 support, manager theme
tweaks, built-in SuSFS management tool and multi-manager support — and adds
its own identity and automation on top:

1. Kernel-based `su` and root access management
2. Module system based on [metamodules](https://kernelsu.org/guide/metamodule.html): pluggable infrastructure for systemless modifications
3. [App Profile](https://kernelsu.org/guide/app-profile.html): lock up the root power in a cage
4. Support non-GKI and GKI 1.0
5. Tweaks to the manager theme and the built-in susfs management tool
6. Multi-manager support: the official OriginSU manager plus
   [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU),
   [Official KernelSU](https://github.com/tiann/KernelSU),
   [RKSU](https://github.com/rsuntk/KernelSU),
   [MKSU](https://github.com/5ec1cff/KernelSU),
   [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) and
   [KOWX712/KernelSU](https://github.com/KOWX712/KernelSU) all work as
   managers with OriginSU's kernel
7. Automated releases: tag pushes build and publish the signed manager APK
   and LKM modules via GitHub Actions (see `docs/feature-porting-plan.md`
   for the roadmap)

> **Note:** install only the official `Manager-release` APK from the
> [Releases](https://github.com/samakshkambxj/OriginSU/releases) page.
> Debug, PR, dev or resigned builds use a different certificate and the
> kernel will reject them as unofficial.

## Compatibility Status

- Officially supports Android GKI 2.0 devices (kernel 5.10+).
- Older kernels (3.4+) are also compatible, but the kernel has to be built manually.
- Currently, only `arm64-v8a`, `armeabi-v7a` and `x86_64` are supported.
- [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) in this project **only**
  supports backport to kernel 4.3+.
- `Tracepoint Syscall Redirect hook` is only supported with GKI2 (5.10+) kernels.

## Hook Mode

- `Tracepoint Syscall Redirect hook`: the default hook mode, from
  [upstream](https://github.com/tiann/KernelSU); only supports GKI2 kernels
  with `arm64-v8a` or `x86_64` ABI.
- `Manual Hook`: the most compatible hook, supports Linux kernels 3.4 – 6.18.
- `SuSFS Inline Hook`: a hook from [SuSFS](https://github.com/simonpunk/susfs4ksu),
  like `Manual Hook`, but provided by the SuSFS project.

## Building

Manager releases are built by CI (`.github/workflows/`), signed with the
project release key. Pushing a `v*` tag publishes a GitHub Release with the
manager APKs and LKM modules. Required Actions secrets: `KEYSTORE`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Upcoming features

- Boot tooling: `magiskboot` support and AnyKernel-zip flashing from the manager
- **Inbuilt kernel manager**: flash, back up and manage kernels from inside the app
- Built-in Zygisk (no separate provider needed)
- Origin Veil: kernel-side root-probe detection with per-app cloaking
- KPM (KernelPatch module) support
- MIUI / official-style theme options
- Magic Mount for wider module compatibility

## License

- Files in the `kernel` directory are under
  [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- The OriginSU logo (`docs/OriginSU*.svg`, launcher icons) is original work
  for this project, under the same terms as the manager (GPL-3.0-or-later).
- Everything else is under [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

## Credit

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
