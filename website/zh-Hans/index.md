---
layout: home

hero:
  name: OriginSU
  text: 为 Android 打造的内核级 root 方案 —— 拥有自己的身份。
  tagline: 隐藏内置而非外挂。Veil 隐藏、内置 Zygisk、内核调参与安装器级刷写，源自 KernelSU 一脉。
  image:
    src: /logo.png
    alt: OriginSU 标志
  actions:
    - theme: brand
      text: 开始使用
      link: /zh-Hans/guide/install
    - theme: alt
      text: GitHub
      link: https://github.com/samakshkambxj/OriginSU

features:
  - title: Origin Veil
    details: 内核级 root 探测感知，按应用隐藏，断电保持，附实时 Spy 日志。
  - title: OriginZygisk
    details: 管理器内置 BreZygisk 引擎 —— 部署、状态、一键开关，无需再找模块。
  - title: OriginTune
    details: 首页卡片直达内核调参 —— CPU、GPU、I/O、ZRAM、LMK，只显示当前内核支持的选项。
  - title: OriginGuard 与 SuSFS
    details: 每个模块 zip 安装前静态审计，加上一键强隐藏 SuSFS 预设。
---

## 快速开始

1. 刷入 OriginSU 内核（GKI `boot.img`、LKM 模块，或老设备手动编译的内核）。
2. 从 [Releases](https://github.com/samakshkambxj/OriginSU/releases) 安装官方 `Manager-release` APK。
3. 打开管理器，按首次运行向导完成设置。

> [!IMPORTANT]
> 请只安装官方 `Manager-release` APK。Debug / PR / dev 或重签名构建证书不同，会被内核判为非官方。

完整步骤见[安装指南](/zh-Hans/guide/install)。欢迎帮助翻译 —— 完整中文文档持续补充中。
