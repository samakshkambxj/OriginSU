# About OriginSU

**A kernel-based root solution for Android — with its own identity.**

Built on [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU) (SukiSU-Ultra → KernelSU lineage) with
its own kernel + manager signing identity, UAPI baseline and release pipeline.

## Why OriginSU

- **Hiding built in, not bolted on**: Origin Veil per-app cloaking, a SuSFS manager with a one-tap
  strong preset, and an OriginGuard audit on every module zip.
- **No module hunt**: OriginZygisk ships inside the manager; kernel tuning (OriginTune) shows only
  knobs your kernel supports.
- **Installer-grade flashing**: direct install, AnyKernel3, offline `boot.img` patching, Horizon
  kernels, LKM/GKI flows with KPM options.
- **Safety nets**: bootloop rescue, biometric root gate, first-run wizard.

## License

- Files in the `kernel` directory are under
  [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- The OriginSU logo (`docs/OriginSU*.svg`, launcher icons) is original work for this project,
  under the same terms as the manager (GPL-3.0-or-later).
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

Manager translations live on Weblate; kernel changes must stay GPL-2.0-compatible. Bug reports and
pull requests are welcome — see `CONTRIBUTING.md` in the repo.
