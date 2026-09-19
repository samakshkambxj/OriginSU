# OriginSU
<img align='right' src='docs/OriginSU.svg' width='220px' alt="OriginSU Icon">

**English**

A kernel-based root solution for Android, built on
[ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) with its own kernel +
manager signing identity, UAPI baseline and release pipeline.

[![Latest release](https://img.shields.io/github/v/release/samakshkambxj/OriginSU?label=Release&logo=github)](https://github.com/samakshkambxj/OriginSU/releases/latest)
[![Kernel License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![Other part License：GPL v3](https://img.shields.io/github/license/samakshkambxj/OriginSU?logo=gnu)](/LICENSE)

> **Note:** install only the official `Manager-release` APK from the
> [Releases](https://github.com/samakshkambxj/OriginSU/releases) page.
> Debug, PR, dev or resigned builds use a different certificate and the
> kernel will reject them as unofficial.

## Highlights

**Root access, your way**

- Magisk-style grant toasts: an overlay shows which app just got root
- Time-limited grants: give root for N minutes, auto-revoked by a
  persistent waiter plus a sweep on every `ksud` start
- Secure root (opt-in): biometric / device-credential authentication before
  any new root grant is saved
- Per-app profiles with templates, per-app mount namespace control and a
  searchable `sulog` event history

**Hiding**

- Built-in SuSFS management (spoofing, paths, TryControl) with a guided UI
- Origin Veil: kernel-side root-probe detection with a per-app cloak set
  that hides module mounts from probing apps
- Kernel umount, SELinux-hide and ADB-root toggles

**Modules**

- Install from ZIP, online catalog with update checks and one-tap update-all
- Module banners, batch enable/disable/uninstall, WebUI with shortcuts
- AnyKernel ZIP flashing via busybox and offline `boot.img` patching with
  vendored `magiskboot` — no root or PC required
- `ZYGISK_ENABLED=true` is exported to installers when Origin Zygisk runs

**Built-in Zygisk**

- Origin Zygisk (BreZygisk-based) ships inside the manager: deploy, status
  and kill-switch in Settings, no provider module needed. Conflicting
  third-party Zygisk providers are blocked while it is on.

**Manager extras**

- Themed app shortcuts (Superuser / Modules / Settings) with a Settings
  toggle to switch back to the stock icons
- Filled-leaf launcher art with adaptive + monochrome variants and a
  Play Store icon, first-run setup wizard, copy-device-info, last-flash
  timeline and floating bottom bar options

OriginSU also keeps everything it inherited: kernel-based `su`, App
Profile cage, non-GKI/GKI 1.0 support and multi-manager support (the
official OriginSU manager plus ReSukiSU, official KernelSU, RKSU, MKSU,
SukiSU-Ultra and KOWX712/KernelSU all work with the OriginSU kernel).

## Compatibility

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

## Roadmap

- KPM (KernelPatch module) support
- **Inbuilt kernel manager**: flash, back up and manage kernels from inside the app
- MIUI / official-style theme options
- Magic Mount for wider module compatibility

See `docs/feature-porting-plan.md` for the full porting status.

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
- [WildKSU](https://github.com/WildKernels/Wild_KSU): launcher icon artwork, themed app-shortcut icons and animated home logo concept
- [BreZygisk](https://github.com/rrr333nnn333/BreZygisk): base of the Origin Zygisk engine
