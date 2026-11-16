<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/OriginSU.svg">
  <img src="docs/OriginSU-light.svg" width="140px" alt="OriginSU logo">
</picture>

# OriginSU

**A kernel-based root solution for Android.**

[Releases](https://github.com/samakshkambxj/OriginSU/releases) ·
[Documentation](https://samakshkambxj.github.io/OriginSU/) ·
[Issues](https://github.com/samakshkambxj/OriginSU/issues)

[![Latest release](https://img.shields.io/github/v/release/samakshkambxj/OriginSU?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/samakshkambxj/OriginSU/build-manager.yml?branch=main&label=Build&logo=github)](https://github.com/samakshkambxj/OriginSU/actions/workflows/build-manager.yml)
[![Kernel License: GPL v2](https://img.shields.io/badge/Kernel-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![Manager License: GPL v3](https://img.shields.io/github/license/samakshkambxj/OriginSU?label=Manager&logo=gnu)](/LICENSE)

</div>

---

OriginSU is a kernel-based root solution for Android, evolved from
[`tiann/KernelSU`](https://github.com/tiann/KernelSU). The kernel provides
`su` and its permission checks; the manager app handles everything on top.

## Features

1. Kernel-based `su` and permission management.
2. [App Profile](https://samakshkambxj.github.io/OriginSU/guide/app-profile): keep root access in a cage.
3. KPM support.
4. Built-in Zygisk (NyaZygisk) — no separate Zygisk module needed; running status is shown right on the home screen.
5. Theme Store: browse, install and submit themes, with multi-source mirrors, author authorization and review.
6. Kernel Extension Center: CPU/vvar spoofing, AVC log spoofing, version spoofing, SUSFS and mount view hiding — all in one place.
7. Built-in SUSFS management tools with one-tap root hiding configuration.
8. Dual UI: Miuix / Material 3 Expressive; supports Miuix splash screen, module banners and sorting, temporary root grant, and kernel-free preview.

## Compatibility

- OriginSU supports GKI 2.0 devices (kernel version 5.10 and above).
- Older kernels are also compatible (minimum 4.14+), but you need to compile the kernel yourself.
- With additional backports, 3.x kernels (3.4–3.18) can be supported.
- Currently supported architectures: `arm64-v8a`, `armeabi-v7a (bare)`, `X86_64`, `riscv64`.
- **Note**: This project evolves independently after forking from `tiann/KernelSU`; compatibility is based on testing in this repository and no longer equals the official KernelSU support scope.

## Install

Use the official manager APK from the
[Releases](https://github.com/samakshkambxj/OriginSU/releases) page. Debug, PR
and other non-release builds are signed with a different certificate, so the
kernel rejects them as unofficial.

The [install guide](https://samakshkambxj.github.io/OriginSU/guide/install) has
the full walkthrough, including troubleshooting.

> [!TIP]
> Coming from KernelSU or a related fork? The OriginSU manager also works with
> those kernels, and its kernel accepts their official managers. On a
> third-party kernel the manager runs in **compat mode**: grant it root in your
> current manager, and modules, flashing and reboot support keep working.

## Kernel integration

Run this in the **root of your kernel source**:

```bash
# Latest stable tag (recommended)
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -

# Development branch
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s main

# A specific tag / commit
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s v4.2.7
```

Then enable `CONFIG_KSU` (and `CONFIG_KPM` on 64-bit kernels if you want KPM
support), rebuild and flash.

```bash
# Remove a previous integration
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --cleanup

# Register the downloaded copy as a git submodule of your kernel tree
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --submodule
```

Manual integration: clone the repo, symlink `kernel/` to `drivers/kernelsu`,
add `obj-$(CONFIG_KSU) += kernelsu/` to `drivers/Makefile`, and source
`drivers/kernelsu/Kconfig` from `drivers/Kconfig`.

## Documentation

The full documentation lives in [`docs/`](docs/README.md) and on the project
website: <https://samakshkambxj.github.io/OriginSU/>.

## Contributing

Bug reports and pull requests are welcome. Manager translations are managed on
[Weblate](https://hosted.weblate.org/engage/sukisu-ultra/); kernel changes must
stay GPL-2.0-compatible. Please report security issues privately (see
[SECURITY.md](SECURITY.md)) rather than in public issues.

## License

- Files in the `kernel` directory are under
  [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- The OriginSU logo (`docs/OriginSU*.svg`, launcher icons) is original work for
  this project.
- Icon files matching `ic_launcher(?!.*alt.*).*` that feature anime character
  images are licensed separately; see [`LICENSE_icon_English`](LICENSE_icon_English)
  and [`LICENSE_icon_SC`](LICENSE_icon_SC) for details.
- All other parts are under [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

## Acknowledgements

- [SukiSU-Nova](https://github.com/SukiSU-Nova/SukiSU-Nova): manager base this project is built on.
- [KernelSU](https://github.com/tiann/KernelSU): upstream.
- [/xxksu](https://github.com/backslashxx/KernelSU): reference for kernel compatibility support.
- [susfs](https://gitlab.com/simonpunk/susfs4ksu): KernelSU addon with hiding kernel patches and userspace modules.
- [KernelPatch](https://github.com/bmax121/KernelPatch): key part of the kernel module implementation.
- NyaZygisk: built-in Zygisk implementation.
