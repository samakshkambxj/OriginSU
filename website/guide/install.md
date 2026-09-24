# Install

## On your phone

1. **Check compatibility**: GKI 2.0 devices (kernel 5.10+) are officially supported. Older kernels
   (3.4+) need a manually built kernel — see [Compatibility](/guide/compatibility).
2. **Get the manager**: download the official `Manager-release` APK matching your ABI from the
   [Releases](https://github.com/samakshkambxj/OriginSU/releases) page (`arm64-v8a` for most
   modern phones). Optional: the `Spoofed-Manager` build installs side-by-side under a randomized
   package name when the stock one is detected.
3. **Get root, pick one**:
   - **LKM mode (recommended for phones)**: `fastboot boot` a matching GKI kernel for temporary
     root, open the manager and use **Install → Direct install / Select a file**. The manager
     patches your stock firmware and flashes it for you.
   - **GKI mode**: flash the provided `boot.img` with
     `fastboot flash boot boot.img && fastboot reboot`, with a kernel-flasher app
     (e.g. Kernel Flasher), or via custom recovery (TWRP).
   - **Offline patching**: no root at all — the manager can patch a `boot.img` into
     `Downloads/OriginSU/` for you to flash manually.
4. **Verify**: reopen the manager — Home should report the kernel / LKM status, then finish the
   setup wizard.

## In your kernel source

Run this in the **root of your kernel source**, just like the other KernelSU-family forks:

```bash
# Latest stable tag (recommended)
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -

# Development branch
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s main

# A specific tag / commit
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s v4.2.5
```

Then enable `CONFIG_KSU` (plus `CONFIG_KPM` on 64-bit kernels if you want KPM support), rebuild,
and flash. Extras:

```bash
# Remove a previous integration
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --cleanup

# Register the downloaded copy as a git submodule of your kernel tree
# (run from your kernel source root after integrating)
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -s --submodule
```

Prefer manual integration? See [Build the kernel](/guide/build-kernel).

## Release assets

Each `v*` tag publishes a GitHub Release with:

| Asset | What it is |
| --- | --- |
| `Manager-release` APKs (`arm64-v8a`, `armeabi-v7a`, `universal`) | The only manager builds the OriginSU kernel trusts. Pick the APK matching your ABI. |
| `Spoofed-Manager-release` APK | Same manager with a randomized package name — installs side-by-side, useful when the stock package name is detected. |
| `Manager-debug` APK | Debug build for development. The kernel rejects it as unofficial — do not use for daily root. |
| `<arch>-<kmi>-lkm[-tamper]-<kmi>_kernelsu.ko` | Loadable kernel modules per KMI and architecture (`aarch64-*` for phones, `x86_64-*` for emulator/x86 tablets). Match `<kmi>` to your kernel (e.g. `android14-6.1`); the `arch-` prefix tells the two same-KMI files apart. `-tamper-` variants use the stealth syscall-table hook instead of the tracepoint default. |

Pre-release tags containing `-rc` are published as GitHub pre-releases.
