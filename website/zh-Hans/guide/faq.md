# 常见问题

| 现象 | 解法 |
| --- | --- |
| 内核提示管理器**非官方** | 你装了 debug / dev / PR / 重签名 APK，请安装 Releases 页的官方 `Manager-release` APK。 |
| 刷入后没有 root | 重新打开管理器走设置向导，检查首页内核 / LKM 状态。LKM 需确认 `.ko` 的 KMI 与内核一致、`arch-` 前缀与设备一致。 |
| 装模块后无限重启 | 等待 bootloop 保护禁用全部模块后重启，再逐个重新启用。OriginGuard 审计日志会说明拦截原因。 |
| 应用仍能检测 root | 把它加入 Origin Veil 隐藏名单，结合 Spy 日志与 SuSFS 强隐藏预设。 |
| KPM 标签页不见了 | 32 位或非 KPM 内核属正常 —— 只有内核上报 KPM 支持时才显示。 |
| LKM 文件怎么选 | 手机用 `aarch64-*`，x86 设备用 `x86_64-*`；`<kmi>` 段（如 `android14-6.1`）必须与内核匹配。 |

英文完整版见 [FAQ](/guide/faq)。仍未解决请带设备信息、内核与管理器版本到
[GitHub Issues](https://github.com/samakshkambxj/OriginSU/issues) 反馈。
