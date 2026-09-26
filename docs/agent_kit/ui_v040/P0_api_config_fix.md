# P0 · 先修 API 配置 bug（最先做，改动小）

> 来源：吕博 2026-09-26 真机测试反馈"安卓上配置 API key 有 bug，要按 1052 中转的方式配置"。以下问题由 Napoleon 读 v0.3.0 源码确认。

## 已确认的问题
1. **没有团队中转预设**：路线只有"TypeSafe 官方 / 博查 / 自建"，用户得自己填中转地址，容易填错
2. **协议默认值是坑**：`ModelConfig.Profile` 的 4 参数构造函数默认用 `Protocol.CHAT_COMPLETIONS`。自建 / 中转路线走 Chat 协议时，1052 中转会返回 **422**（中转只支持 Jev 原生接口）
3. **博查预设地址不对**：`presetBocha()` 用的是 `https://jev.bocha.cn`；团队拿到的博查 key 只能走 `https://tokendance.space/gateway/typesafe`（直连会返回 401）
4. 实测可用的调用方式（2026-09-26，从外网测过）：`POST https://10521052.xyz/jev/v1/systemone`；`SystemOneProtocol.endpoint()` 对根地址 `https://10521052.xyz/jev` 会自动补 `/v1/systemone`，这条路径是通的

## 要改成
- 新增**第一个、默认**预设 **「团队中转 1052（推荐）」**：
  - 协议 = `JEV_SYSTEMONE`；地址 = `https://10521052.xyz/jev`
  - 模型用**下拉选择**，4 个选项：
    - `local-systemone-ft`（最终微调版，默认）
    - `local-systemone-v1`（原版）
    - `typesafe-jev`（官方）
    - `bocha-jev`（博查）
  - key = 用户在 1052 平台的 key（只填一次，4 个模型共用）
  - 选 `typesafe-jev` / `bocha-jev` 时显示一行提示："该模型会把通知内容发往外部公司，真实私聊请用本地模型"
- **协议只保留 Jev SystemOne**：设置页去掉 Chat Completions 选项（团队决定，Chat 格式不达标）。已存的旧配置如果是 Chat 协议，读取时迁移为 JEV_SYSTEMONE，并提示用户检查
- 博查预设地址改为 `https://tokendance.space/gateway/typesafe`（model `bocha-jev-v1`）；官方预设不变（`https://api.typesafe.ai`，`jev-latest`）
- "连接测试"按钮对当前预设发一条固定的合成通知，显示：HTTP 状态、保留概率、耗时；401 显示"key 无效"，422 显示"协议或格式不对"，404 显示"地址不对，1052 请用 /jev"
- 三路对照：默认三路 = 1052 下的 local-systemone-ft / local-systemone-v1 / typesafe-jev

## 测试（必须有）
- 纯 Java 单测：预设内容、旧 Chat 配置迁移、`endpoint("https://10521052.xyz/jev") == "https://10521052.xyz/jev/v1/systemone"`、模型下拉值写进请求体
- 模拟器：用模拟 systemone 服务验证 4 个模型名都能发出请求

## 完成标准
编译和测试通过 → `git commit -m "ui-v040: P0 fix API config for 1052 relay"` → 在 STATE.md 记一行
