# Features

## Core root

- Kernel-based `su` and root access management
- Module system based on [metamodules](https://kernelsu.org/guide/metamodule.html) for systemless
  modifications — with batch multi-select actions, update-all multi-flash, `banner=` artwork from
  `module.prop`, internal backups with restore, shareable bundle zips, and download-script (JSON)
  batch install
- [App profiles](https://kernelsu.org/guide/app-profile.html): cage root power per app, with
  **time-limited grants** (10 min / 30 min / 1 h / 8 h / 24 h) revoked by a persistent waiter plus a
  sweep on every `ksud` start
- GKI 2.0, GKI 1.0 and non-GKI (3.4+) kernel support
- Manager interop across the KernelSU family (see [Compatibility](/guide/compatibility))

## Origin Lab (Settings)

| Capability | Where to read more |
| --- | --- |
| **Origin Veil** — kernel-side probe detection, per-app cloak set, Spy live log, su-notify | [Origin Veil](/features/veil) |
| **OriginZygisk** — bundled BreZygisk engine, kill-switch, conflict blocking | [OriginZygisk](/features/zygisk) |
| **SuSFS manager** — built-in manager with one-tap strong-hiding preset | [OriginGuard & SuSFS](/features/guard) |
| **OriginGuard** — static pre-install audit of module zips | [OriginGuard & SuSFS](/features/guard) |
| **KPM** — manager KPM tab, boot-image patching | [KPM & flashing](/features/kpm-flasher) |
| **Kernel flasher** — direct install, AnyKernel3, offline patching, Horizon, LKM/GKI | [KPM & flashing](/features/kpm-flasher) |
| **Updater** — stable / beta channels | [KPM & flashing](/features/kpm-flasher) |
| **Secure root gate** — biometric confirmation before granting root | [Manager tour](/features/manager) |
| **Bootloop protection** — disables all modules after consecutive failed boots | [KPM & flashing](/features/kpm-flasher) |
| **Magic Mount** — backend selector (Auto / Overlayfs / Bind), temporarily unavailable | [KPM & flashing](/features/kpm-flasher) |

## OriginTune

Kernel tuning from a home hero card — every knob applies immediately and can persist across boot,
and only knobs the running kernel exposes are shown. Full list: [OriginTune](/features/tune).
