# FAQ

| Symptom | Fix |
| --- | --- |
| Kernel reports manager as **unofficial** | You installed a debug / dev / PR / resigned APK. Install the official `Manager-release` APK from Releases. Tapping the warning opens the Releases page. |
| No root after flashing | Reopen the manager and run the setup wizard; check kernel / LKM status on Home. For LKM, confirm the `.ko` KMI matches your kernel and the `arch-` prefix matches your device. |
| Bootloop after installing a module | Wait for the bootloop protector to disable all modules, reboot, then re-enable modules one by one. OriginGuard audit logs explain what a blocked zip tripped on. |
| Root-detecting app still sees root | Add it to the Origin Veil cloak set, check per-app detail + Spy log, and apply the SuSFS strong-hiding preset. See [Origin Veil](/features/veil). |
| KPM tab is hidden | Expected on 32-bit or non-KPM kernels — the tab only appears when the kernel reports KPM support. |
| Which LKM file do I need | `aarch64-*` for phones, `x86_64-*` for x86 devices; the `<kmi>` segment (e.g. `android14-6.1`) must match your kernel. |
| Magic Mount changes do not apply immediately | Magic Mount is applied during boot, so toggling it or changing its backend takes effect on the next reboot. |
| Magic Mount toggle is disabled | A metamodule (NoMount, ZeroMount, Kagami/Kasumi, Hybrid Mount, ...) owns module mounting. **Settings → Origin Lab → Mount engine** shows which one; Magic Mount stands down until that metamodule is disabled or removed. |

Still stuck? Open an issue on
[GitHub](https://github.com/samakshkambxj/OriginSU/issues) with your device info (copy it from
Home), kernel version, manager version and the relevant logs. Please report security issues
privately per `SECURITY.md` rather than in public issues.
