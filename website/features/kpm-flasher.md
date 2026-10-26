# KPM & flashing

## KPM

Kernel `CONFIG_KPM` (64-bit only) with a manager KPM tab: load now or embed to `/data/adb/kpm`.
The `Install > KPM` tab patches / un-patches boot images, AnyKernel zips or kernel zips before
flashing.

The tab only appears when the kernel reports KPM support — it stays hidden on 32-bit or non-KPM
kernels.

## Kernel flasher

- **Direct install** and AnyKernel3 zips (legacy busybox runner).
- **Offline `boot.img` patching** to `Downloads/OriginSU/` without root.
- Horizon kernels; LKM (per-KMI module plus tracepoint/tamper hook-flavor picker) and GKI flows.
- See [hook modes](/guide/compatibility) for tracepoint vs syscall-table-tamper vs manual.

## Safety nets

- **Bootloop protection**: disables all modules after consecutive failed boots and tells you about
  the rescue — then re-enable modules one by one.
- **Updater**: stable / beta channels with dynamic-manager support.

## Magic Mount

Magisk-style userspace module mounting, for modules that ship a `system/` tree instead of a
metamodule mount script. Enable it in `Settings → Origin Lab`; changes take effect on the next boot.

- **Auto** (default) uses overlayfs when the kernel supports it and falls back to bind mounts.
- **Overlayfs** merges directories into one read-only view.
- **Bind** uses Magisk-style tmpfs skeletons and bind mounts, without copying module payloads into
  RAM.

Magic Mount supports regular files, directories, symlinks, whiteouts and `.replace` directories.
It stands down automatically when an enabled metamodule owns mounting.

`Settings → Origin Lab` also shows the **Mount engine**: Magic Mount, NoMount, ZeroMount,
Kagami/Kasumi, Hybrid Mount, or another metamodule. Detection covers the active metamodule plus
kernel driver markers (`/dev/zeromount`, `/sys/module/kasumi_lkm`, `/sys/module/nomount`), and
OriginSU never runs Magic Mount while a metamodule owns the mount pipeline. To switch engines,
install the desired metamodule, reboot, then reinstall your modules as that metamodule requires.
