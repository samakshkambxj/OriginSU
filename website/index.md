---
layout: home

hero:
  name: OriginSU
  text: Kernel-based root for Android — with its own identity.
  tagline: Hiding built in, not bolted on. Veil cloaking, bundled Zygisk, kernel tuning and installer-grade flashing on the KernelSU lineage.
  image:
    src: /logo.png
    alt: OriginSU logo
  actions:
    - theme: brand
      text: Get started
      link: /guide/install
    - theme: alt
      text: Features
      link: /features/
    - theme: alt
      text: GitHub
      link: https://github.com/samakshkambxj/OriginSU

features:
  - icon: <i class="ri-eye-off-line"></i>
    title: Origin Veil
    details: Kernel-side root-probe detection with a per-app cloak set, reboot persistence, per-app detail and a live Spy log.
    link: /features/veil
  - icon: <i class="ri-puzzle-line"></i>
    title: OriginZygisk
    details: BreZygisk-based engine bundled in the manager — deploy, status and kill-switch, no extra module hunt.
    link: /features/zygisk
  - icon: <i class="ri-dashboard-line"></i>
    title: OriginTune
    details: Kernel tuning from a home hero card — CPU, GPU, I/O, ZRAM, LMK, TCP and BORE presets, only showing knobs your kernel supports.
    link: /features/tune
  - icon: <i class="ri-shield-check-line"></i>
    title: OriginGuard & SuSFS
    details: Static pre-install audit of every module zip, plus a one-tap strong-hiding SuSFS preset.
    link: /features/guard
  - icon: <i class="ri-install-line"></i>
    title: Installer-grade flashing
    details: Direct install, AnyKernel3, offline boot.img patching, Horizon kernels and LKM/GKI flows with KPM options.
    link: /features/kpm-flasher
  - icon: <i class="ri-user-settings-line"></i>
    title: Manager your way
    details: Material and MIUIX styles, compact layouts, dynamic icons, floating bar and module banners.
    link: /features/manager
---

## Quick start

1. Flash an OriginSU-patched kernel (GKI `boot.img`, LKM module, or a manually built kernel for older devices).
2. Install the official `Manager-release` APK from the [Releases](https://github.com/samakshkambxj/OriginSU/releases) page.
3. Open the manager and follow the first-run setup wizard.

> [!IMPORTANT]
> Install only the official `Manager-release` APK. Debug, PR, dev or resigned builds use a
> different certificate and the kernel will reject them as unofficial.

Coming from KernelSU / RKSU / MKSU / SukiSU? OriginSU's manager also works with those kernels,
and its kernel accepts those official managers. See the [installation guide](/guide/install).
