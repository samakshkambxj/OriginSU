# 关于 OriginSU

**为 Android 打造的内核级 root 方案 —— 拥有自己的身份。**

基于 [ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)（SukiSU-Ultra → KernelSU 一脉），拥有独立的
内核 + 管理器签名身份、UAPI 基线与发布流水线。

- **隐藏内置而非外挂**：Origin Veil 按应用隐藏、一键强隐藏 SuSFS 预设、每个模块 zip 的
  OriginGuard 安装前审计。
- **无需再找模块**：OriginZygisk 内置于管理器；OriginTune 只显示当前内核支持的调参。
- **安装器级刷写**：直接安装、AnyKernel3、离线 `boot.img` 修补、Horizon 内核、LKM/GKI 与 KPM 流程。
- **安全网**：bootloop 救援、生物识别 root 确认、首次运行向导。

英文完整版（含兼容性、Hook 模式、许可与致谢）见 [About OriginSU](/guide/about)。
