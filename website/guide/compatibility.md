# Compatibility & hook modes

## Compatibility

| Requirement | Support |
| --- | --- |
| GKI 2.0 devices (kernel 5.10+) | ✅ Officially supported |
| Older kernels (3.4+) | ⚠️ Compatible, but the kernel has to be built manually |
| Architectures | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| [SuSFS](https://gitlab.com/simonpunk/susfs4ksu) backport | Kernel 4.3+ only |
| Tracepoint Syscall Redirect hook | GKI2 (5.10+) kernels only |
| KPM (`CONFIG_KPM`) | 64-bit kernels only |

## Hook modes

| Mode | Description |
| --- | --- |
| `Tracepoint Syscall Redirect hook` | Default mode, from [upstream](https://github.com/tiann/KernelSU); GKI2 kernels with `arm64-v8a` or `x86_64` ABI only |
| `Syscall Table Tampering` (`CONFIG_KSU_TAMPER_SYSCALL_TABLE`) | Stealth mode: patches the hooked entries directly in `sys_call_table`, registers no `sys_enter` tracepoint; GKI2 `arm64-v8a`/`x86_64` only. CI builds both LKM flavors per KMI and releases ship them side by side; pick one in the Install screen's hook-flavor selector |
| `Manual Hook` | Most compatible; supports Linux kernels 3.4 – 6.18 |
| `SuSFS Inline Hook` | From [SuSFS](https://github.com/simonpunk/susfs4ksu), like `Manual Hook` but provided by the SuSFS project |

## Manager interop

OriginSU's manager also works with official [KernelSU](https://github.com/tiann/KernelSU),
[RKSU](https://github.com/rsuntk/KernelSU), [MKSU](https://github.com/5ec1cff/KernelSU) and
[SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra) kernels — and the OriginSU kernel accepts
those official managers.
