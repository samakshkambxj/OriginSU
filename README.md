# OriginSU

<img align='right' src='docs/OriginSU-default.png' width='200px' alt="OriginSU Logo">

**Root your Android device. Keep control of it.**

OriginSU is a kernel-based root solution built on
[ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) (SukiSU-Ultra → KernelSU
lineage). It pairs a kernel `su` implementation with a manager that handles
root access, modules, hiding, flashing and kernel tuning in one place.

<div align="center">

  [![Latest release](https://img.shields.io/github/v/release/samakshkambxj/OriginSU?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/releases/latest)
  [![Downloads](https://img.shields.io/github/downloads/samakshkambxj/OriginSU/total?label=Downloads&logo=github)](https://github.com/samakshkambxj/OriginSU/releases)
  [![Build](https://img.shields.io/github/actions/workflow/status/samakshkambxj/OriginSU/build-manager.yml?branch=main&label=Build&logo=github)](https://github.com/samakshkambxj/OriginSU/actions/workflows/build-manager.yml)
  [![Kernel License: GPL v2](https://img.shields.io/badge/Kernel-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
  [![Manager License: GPL v3](https://img.shields.io/github/license/samakshkambxj/OriginSU?label=Manager&logo=gnu)](/LICENSE)

</div>

📖 **Documentation: <https://samakshkambxj.github.io/OriginSU/>**

> [!IMPORTANT]
> Install only the official manager APK from the
> [Releases](https://github.com/samakshkambxj/OriginSU/releases) page — the file
> whose name ends in `-release.apk`. Debug, PR, dev and resigned builds use a
> different certificate and the kernel rejects them as unofficial.

## Features

- **Kernel-based `su`** with per-app profiles, time-limited grants and a
  biometric secure-root gate.
- **Modules** based on [metamodules](https://kernelsu.org/guide/metamodule.html),
  plus Magic Mount for `system/` trees, batch actions and backups.
- **Hiding built in** — Origin Veil per-app cloaking with probe history and
  su-notify actions, the SuSFS manager with a one-tap strong-hiding preset, and
  OriginGuard's pre-install module audit.
- **OriginZygisk bundled in the manager** — no separate module, with deploy,
  integrity repair, per-app skip rules and diagnostics export.
- **Origin Sui** — a built-in Shizuku API provider on top of OriginZygisk, with
  per-app Default/Root/Shell/Deny/Hide modes, a default permission and
  system/Shizuku filters.
- **Origin Curtain** — the in-manager config layer for the HMA-OSS app-list
  hiding module: blacklist/whitelist modes, reusable templates, installation
  source spoofing and global protection toggles.
- **Flashing that feels like an installer** — direct install, AnyKernel3 zips,
  offline `boot.img` patching, LKM/KPM flows and bootloop protection.
- **OriginTune** — CPU, GPU, I/O, memory, LMK, ZRAM and sysctl tuning, showing
  only the knobs the running kernel actually exposes.

## Compatibility

| Requirement | Support |
| --- | --- |
| GKI 2.0 devices (kernel 5.10+) | ✅ Officially supported |
| Older kernels (3.4+, including GKI 1.0) | ⚠️ Compatible, but the kernel must be built manually |
| Architectures | Prebuilt LKMs: `aarch64` and `x86_64`. Manager APKs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `riscv64` and `universal`. |
| SuSFS guard LKM | `arm64-v8a` devices running KernelSU in LKM mode |
| [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) backport | Kernel 4.3+ only |
| Tracepoint Syscall Redirect hook | GKI2 (5.10+) kernels only |
| KPM (`CONFIG_KPM`) | 64-bit kernels only |

**Terms:** GKI = Generic Kernel Image (Android's standardized kernel). LKM =
loadable kernel module (root loads after boot instead of being built into the
kernel). KPM = Kernel Patch Module.

## Install

1. **Check your device** against the compatibility table above.
2. **Download the manager** — pick the `-release.apk` matching your ABI
   (`arm64-v8a` for most phones, or `universal`).
3. **Install root** using the path that matches your setup:
   - **Most phones, no custom kernel:** boot a compatible GKI image, then use
     **Install → Direct install** in the manager.
   - **You have a GKI `boot.img`:** flash it with `fastboot` or a kernel-flasher
     app.
   - **No root yet:** use **Install → offline patching** to patch `boot.img`
     into `Downloads/OriginSU/`, then flash it manually.
   - **You build your own kernel:** see [Kernel integration](#kernel-integration).

After installing, open the manager and follow the first-run setup wizard. The
[install guide](https://samakshkambxj.github.io/OriginSU/guide/install) has the
full walkthrough, including troubleshooting.

> [!TIP]
> Coming from KernelSU, RKSU, MKSU or SukiSU? OriginSU's manager also works with
> those kernels, and its kernel accepts those official managers. On a
> third-party kernel the manager runs in **compat mode**: grant it root in your
> current manager and you keep modules, flashing and reboot support.
> Origin-exclusive features require an OriginSU kernel.

## Downloads

| Asset | What it is |
| --- | --- |
| `OriginSU_<version>_<code>-<abi>-release.apk` | Install this. Signed manager build. |
| `<arch>-<kmi>-lkm-<kmi>_kernelsu.ko` | Root LKM with the default tracepoint hook. |
| `<arch>-<kmi>-lkm-tamper-<kmi>_kernelsu.ko` | Same LKM with the stealth syscall-table hook. |
| `aarch64-<kmi>-susfs-lkm-<kmi>_susfs_guard_lkm.ko` | SuSFS guard module for arm64 devices. |

Match both `<arch>` and `<kmi>` to your device and kernel. The `-tamper-`
variant only changes the hook method. The `susfs` file is a guard module, not
the root LKM. Missing KMI or architecture? Use GKI/offline patching, or
[build the kernel yourself](#kernel-integration).

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

Then enable `CONFIG_KSU` — and `CONFIG_KPM` on 64-bit kernels if you want KPM
support — rebuild and flash.

```bash
# Remove a previous integration
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --cleanup

# Register the downloaded copy as a git submodule of your kernel tree
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --submodule
```

Manual integration: clone the repo, symlink `kernel/` to `drivers/kernelsu`,
add `obj-$(CONFIG_KSU) += kernelsu/` to `drivers/Makefile`, and source
`drivers/kernelsu/Kconfig` from `drivers/Kconfig`.

### Hook modes

| Mode | Description |
| --- | --- |
| `Tracepoint Syscall Redirect hook` | Default mode, from [upstream](https://github.com/tiann/KernelSU). GKI2 kernels with `arm64-v8a` or `x86_64` ABI only. |
| `Syscall Table Tampering` (`CONFIG_KSU_TAMPER_SYSCALL_TABLE`) | Stealth mode: patches hooked entries directly in `sys_call_table` and registers no `sys_enter` tracepoint. GKI2 `arm64-v8a`/`x86_64` only. CI builds both flavors per KMI; pick one in the Install screen. |
| `Manual Hook` | Most compatible; supports Linux kernels 3.4–6.18. |
| `SuSFS Inline Hook` | From [SuSFS](https://gitlab.com/simonpunk/susfs4ksu), like `Manual Hook` but provided by the SuSFS project. |

### Build and release

Releases are built by CI (`.github/workflows/`) and signed with the project
release key. Pushing a `v*` tag publishes a GitHub Release with the manager APKs
and LKM modules.

Required Actions secrets: `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

```bash
# Tag a release (maintainers)
git tag -a vX.Y.Z -m "OriginSU vX.Y.Z"
git push origin vX.Y.Z

# Local iteration
just build_manager   # ksud (aarch64) + manager debug APK
just build_ksud      # ksud only
```

## Contributing

Bug reports and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
Manager translations are managed on
[Weblate](https://hosted.weblate.org/engage/ReSukiSU); kernel changes must stay
GPL-2.0-compatible. Please report security issues privately (see
[SECURITY.md](SECURITY.md)) rather than in public issues.

## License

- Files in the `kernel` directory are under
  [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- The OriginSU logo (`docs/OriginSU*.svg`, launcher icons) is original work for
  this project, under the same terms as the manager (GPL-3.0-or-later).
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
- [Sui](https://github.com/RikkaApps/Sui): Shizuku API implementation behind Origin Sui (GPL-3.0)
- [Sui fork](https://github.com/XiaoTong6666/Sui): pinned source of the bundled Origin Sui payload
- [Shizuku-API](https://github.com/RikkaApps/Shizuku-API): the Shizuku API Sui exposes (MIT, RikkaW)
- [HMA-OSS](https://github.com/frknkrc44/HMA-OSS): app-list hiding module behind Origin Curtain (AGPL-3.0)
- [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist): project HMA-OSS forked from
