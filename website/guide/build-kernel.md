# Build the kernel

## Automatic integration (recommended)

Run the one-liner from the [installation guide](/guide/install) in the root of your kernel source:

```bash
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -
```

Then enable `CONFIG_KSU` in your kernel config (plus `CONFIG_KPM` on 64-bit kernels for KPM
support), rebuild, and flash.

## Manual integration

1. Clone the repo and symlink `kernel/` to `drivers/kernelsu` in your kernel tree.
2. Add `obj-$(CONFIG_KSU) += kernelsu/` to `drivers/Makefile`.
3. Source `drivers/kernelsu/Kconfig` from `drivers/Kconfig`.
4. Enable `CONFIG_KSU` (and `CONFIG_KPM` on 64-bit if wanted) and rebuild.

## Local manager iteration

Releases are built by CI (`.github/workflows/`), signed with the project release key. For local
iteration:

```bash
just build_manager   # ksud (aarch64) + manager debug APK
just build_ksud      # ksud only
```

Tagging a release (maintainers):

```bash
git tag -a vX.Y.Z -m "OriginSU vX.Y.Z"
git push origin vX.Y.Z
```

Pushing a `v*` tag publishes a GitHub Release with the manager APKs and LKM modules. Required
Actions secrets: `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
