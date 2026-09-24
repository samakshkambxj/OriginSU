# Origin Veil

Kernel-side root-probe detection with a per-app cloak set (`Settings > Origin Lab`).

## What it does

- Detects root probes hitting cloaked apps and hides the traces.
- Cloak set survives reboot (`veil.json` + restore at boot).
- Per-app detail: permissions, freeze, force-stop.
- **Spy** live log shows probes as they happen.
- su-notify: Grant / Cloak / Ignore notifications per request.
- Uncloak-restore and probe pagination for noisy apps.

## Typical flow

1. An app still detects root → add it to the Veil cloak set.
2. Open the per-app detail and the Spy log to confirm probes are being cloaked.
3. If detection persists, combine with the [SuSFS strong-hiding preset](/features/guard).

Veil needs `CONFIG_KSU_ORIGIN_VEIL` in the kernel (default y) and a manager whose UAPI baseline
matches the running kernel — the official `Manager-release` APK.
