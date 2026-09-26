# 01 · 最小项目上下文（每次会话必读，保持短）

> 原文摘自 Issue #1（UI 重构需求），按阶段拆分；原文字句不改。与 00_INDEX.md 冲突时以 00_INDEX.md 为准。

一、项目位置与基本情况
项目真实根目录：
D:\数模黑客松\android-notification-filter-demo-0.3.0\android-notification-filter-demo-0.3.0
核心 App 模块：
filter/
另外还有：
sender/
tests/
docs/
本轮只重构 filter/ 模块的前端 UI。
不要修改：
sender/
tests/
docs/
已经稳定运行的后端核心算法
通知读取/筛选/分类核心逻辑
模型调用核心逻辑
当前项目以：
Java + Android 原生 View
为主，现有页面主要通过 Java 动态创建 View，而不是 Compose。
不要为了这次 UI 重构引入 Jetpack Compose。
不要为了视觉效果引入大型第三方 UI 框架。
可以根据需要：
新建少量 Java UI helper/class；
新建 drawable；
新建 vector drawable；
新建 shape/background 资源；
新建 color/dimen 等轻量资源；
用 GradientDrawable、LayerDrawable、自定义 Drawable、Canvas 或原生 View 组合实现效果。
但整体必须保持项目轻量、容易编译。
当前已有：
MainActivity.java
ModelSettingsActivity.java
AttentionActivity.java
OnboardingActivity.java
OnboardingActivity 已经完成首次启动引导逻辑，本轮不要破坏它。
ModelSettingsActivity、AttentionActivity 现有功能也必须继续能够正常进入。

二、产品名称要求
项目正式研究题目为：
Attention isn't All You Need：面向个性化消息重要性识别的 Jev 网络设计与验证
当前产品不要再使用：
回不回 JARVIS
JARVIS
等旧产品名称。
如果 UI 中需要出现产品名称，而没有专门命名，则使用：
Attention
或：
通知助手
或直接不显示产品名。
不要擅自创造新的品牌名称。

## Napoleon 补充的事实（2026-09-26）
- **代码基线**：仓库 main 分支是 v0.3.0，**没有 OnboardingActivity.java**（队友本地版本才有）。开工前先确认基线：队友本地有更新的可以先推到分支 `ui-v040-base`；**拿不到也不用等**，P9 按 MVP 范围（动画式手势教程，有界面即可）新做 OnboardingActivity
- **路径**：原文里的 `D:\数模黑客松\...` 是队友电脑上的中文路径。本仓库一律用仓库相对路径；**新建的文件名、目录名只用英文小写 + 下划线**
- 已有的非 UI 核心：`FilterService`、`DecisionEngine`、`ModelClient`、`SystemOneProtocol`、`ShortTermMemory`、`AttentionMath`、`AttentionTracker`、`AttentionStore`、`AttentionReporter`、`ModelStore`、`StrictJson`、`RetryPolicy`。**UI 重构只调用它们，不改算法**（P0 的配置修复例外）
- 现有测试：`tests/`（纯 Java）、`filter/src/androidTest/`；每个阶段都要能通过 `.\gradlew.bat :filter:assembleDebug :filter:lintDebug` 和纯 Java 测试
