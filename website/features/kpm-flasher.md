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

Magisk-style module mounting with a backend selector (Auto / Overlayfs / Bind). Temporarily
unavailable — the `Settings` toggle is disabled until the feature stabilizes (takes effect on next
boot).
