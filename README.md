<div align="center">

  <img src="docs/OriginSU-default.png" width="160" alt="OriginSU Logo">

  # OriginSU

  **A kernel-based root solution for Android**

  Built on [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) with its own kernel +
  manager signing identity, UAPI baseline and release pipeline.

  [![Latest release](https://img.shields.io/github/v/release/samakshkambxj/OriginSU?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/releases/latest)
  [![Kernel License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
  [![Other part License: GPL v3](https://img.shields.io/github/license/samakshkambxj/OriginSU?logo=gnu)](/LICENSE)

</div>

> [!IMPORTANT]
> Install only the official `Manager-release` APK from the
> [Releases](https://github.com/samakshkambxj/OriginSU/releases) page.
> Debug, PR, dev or resigned builds use a different certificate and the
> kernel will reject them as unofficial.

## Contents

- [Features](#features)
- [Installation](#installation)
- [Compatibility](#compatibility)
- [Hook mode](#hook-mode)
- [Building](#building)
- [Roadmap](#roadmap)
- [License](#license)
- [Credits](#credits)

## Features

**Core**

1. Kernel-based `su` and root access management
2. Module system based on [metamodules](https://kernelsu.org/guide/metamodule.html): Pluggable infrastructure for systemless modifications.
3. [App Profile](https://kernelsu.org/guide/app-profile.html): Lock up the root power in a cage
4. GKI 2.0, GKI 1.0 and non-GKI (3.4+) support
5. Works as manager with the official [KernelSU](https://github.com/tiann/KernelSU), [RKSU](https://github.com/rsuntk/KernelSU), [MKSU](https://github.com/5ec1cff/KernelSU) and [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra) kernels
6. KPM (KernelPatch module) support: load, unload and control KPM modules from the manager (the tab appears when the kernel supports it)
7. Home status cards reporting hook type plus BasebandGuard, ZeroMount, KPM and SuSFS versions

**Origin Lab**

1. Time-limited grants: give root for N minutes, auto-revoked by a persistent waiter plus a sweep on every `ksud` start
2. Origin Veil: kernel-side root-probe detection with a per-app cloak set that hides module mounts from probing apps, probe history persisted across reboots, per-app detail (permissions / freeze / force-stop), a Spy live activity log, and su-notify Grant / Cloak / Ignore prompts
3. Origin Zygisk (BreZygisk-based) ships inside the manager: deploy, status and kill-switch in Settings > Origin Lab, no provider module needed
4. Built-in SuSFS manager with a one-tap strong-hiding preset
5. Automatic bootloop protection: disables all modules after consecutive failed boots and tells you about the rescue
6. Magic Mount: Magisk-style module mounting with a backend selector (Auto / Overlayfs / Bind mounts, takes effect on next boot)
7. OriginGuard: pre-install module firewall — audits module packages for risky behavior, blocks critical findings or asks before installing
8. Inbuilt kernel flasher: direct install, AnyKernel zips, offline `boot.img` patching and Horizon kernels, with LKM and GKI flows, per-KMI module selection and a tracepoint/tamper hook-flavor picker
9. Manager updater with stable/beta channels, dynamic manager support and a multi-icon app-icon picker

## Installation

1. Flash an OriginSU-patched kernel (GKI `boot.img` or a manually built
   kernel for older devices).
2. Install the official `Manager-release` APK from the
   [Releases](https://github.com/samakshkambxj/OriginSU/releases) page.
3. Open the manager and follow the first-run setup wizard.

## Compatibility

| Requirement | Support |
| --- | --- |
| GKI 2.0 devices (kernel 5.10+) | ✅ Officially supported |
| Older kernels (3.4+) | ⚠️ Compatible, but the kernel has to be built manually |
| Architectures | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) backport | Kernel 4.3+ only |
| Tracepoint Syscall Redirect hook | GKI2 (5.10+) kernels only |

## Hook mode

| Mode | Description |
| --- | --- |
| `Tracepoint Syscall Redirect hook` | Default mode, from [upstream](https://github.com/tiann/KernelSU); GKI2 kernels with `arm64-v8a` or `x86_64` ABI only |
| `Syscall Table Tampering` (`CONFIG_KSU_TAMPER_SYSCALL_TABLE`) | Stealth mode: patches the hooked entries directly in `sys_call_table`, registers no `sys_enter` tracepoint; GKI2 `arm64-v8a`/`x86_64` only. CI builds both LKM flavors per KMI and releases ship them side by side; pick one in the Install screen's hook-flavor selector |
| `Manual Hook` | Most compatible; supports Linux kernels 3.4 – 6.18 |
| `SuSFS Inline Hook` | From [SuSFS](https://github.com/simonpunk/susfs4ksu), like `Manual Hook` but provided by the SuSFS project |

## Building

Manager releases are built by CI (`.github/workflows/`), signed with the
project release key. Pushing a `v*` tag publishes a GitHub Release with the
manager APKs and LKM modules. Required Actions secrets: `KEYSTORE`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Roadmap

- MIUI / official-style theme options (paused, see the porting plan)
- GKI OTA survival polish

See `docs/feature-porting-plan.md` for the full porting status.

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
