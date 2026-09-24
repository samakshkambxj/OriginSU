# 安装

## 手机端

1. **确认兼容性**：GKI 2.0 设备（内核 5.10+）为官方支持。更老的内核（3.4+）需要手动编译内核。
2. **获取管理器**：从 [Releases](https://github.com/samakshkambxj/OriginSU/releases) 下载与你 ABI
   对应的官方 `Manager-release` APK（绝大多数新手机用 `arm64-v8a`）。可选：当原包名被检测时，
   `Spoofed-Manager` 构建可用随机包名共存安装。
3. **获取 root，三选一**：
   - **LKM 模式（手机推荐）**：`fastboot boot` 一个匹配的 GKI 内核获得临时 root，打开管理器用
     **安装 → 直接安装 / 选择文件**，管理器会帮你修补并刷入官方固件。
   - **GKI 模式**：用 `fastboot flash boot boot.img && fastboot reboot`、内核刷写 App 或第三方
     Recovery 刷入提供的 `boot.img`。
   - **离线修补**：无需 root —— 管理器可把 `boot.img` 修补输出到 `Downloads/OriginSU/`，手动刷入。
4. **验证**：重新打开管理器，首页确认内核 / LKM 状态，完成设置向导。

## 内核源码集成

在**内核源码根目录**执行（与其他 KernelSU 系分支用法一致）：

```bash
# 最新稳定 tag（推荐）
curl -LSs "https://raw.githubusercontent.com/samakshkambxj/OriginSU/main/kernel/setup.sh" | bash -
```

然后启用 `CONFIG_KSU`（64 位内核如需 KPM 再加 `CONFIG_KPM`），重新编译并刷入。

## 发布产物

每个 `v*` tag 会发布 GitHub Release，包含 `Manager-release` APK（`arm64-v8a` / `armeabi-v7a` /
`universal`）、`Spoofed-Manager-release` APK、`Manager-debug` APK（内核会判为非官方，仅开发用）
以及按 KMI / 架构划分的 LKM 内核模块。英文完整说明见 [Install](/guide/install)。
