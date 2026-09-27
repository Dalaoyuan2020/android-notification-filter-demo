# Keep Attention · Android 通知辅助工具

**后端 Jev 管长期判断，手机本地管短时注意力。** 当前主程序 **v0.4.0** 提供首页、消息、智能判断、我的四页纸面界面，以及四幕首次使用教程；沿用 v0.3.0 的 SystemOne 概率判断、本地衰减记忆和最多三路对照：先获得模型保留概率 `p_jev`，再用近期真实行为计算 `p_final`。

默认仍是**本机关键词策略、自动清除关闭、模型远程处理关闭、事件上传关闭、正文上传关闭**。近期行为摘要选项默认开启，但只有主动启用 JEV 模型请求后才随请求发送；单路连接测试会在点击按钮后单独发送固定合成消息。无内置 API Key。

项目包括两个独立 App：主程序 **Keep Attention** 负责监听与判断，当前 APK 的桌面名称仍是 **“通知筛选 Demo”**；配套 **“通知测试发送器”** 只发布合成样本，不读取其他 App 通知，也不联网。最低 Android 8.0（API 26），compile/target SDK 35，无第三方运行时依赖。

## 扫码下载与三分钟体验

[打开手机端下载页](https://hhu.winnielyu.com/keep-attention/download.html)，或扫描下方二维码。微信内浏览器无法下载时，请通过菜单在系统浏览器中打开。

<a href="https://hhu.winnielyu.com/keep-attention/download.html"><img src="docs/assets/keep_attention_download_qr.png" alt="扫码打开 Keep Attention 下载页" width="180"></a>

| 安装包 | 作用与桌面名称 | APK 版本 | 下载 |
|---|---|---|---|
| `notification-filter-demo.apk` | **主程序 Keep Attention**；桌面显示“通知筛选 Demo” | **0.4.0 / versionCode 4** | [下载主程序](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/download/v0.4.0/notification-filter-demo.apk) |
| `notification-test-sender.apk` | **配套测试工具**；桌面显示“通知测试发送器” | **0.3.0 / versionCode 3** | [下载测试发送器](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/download/v0.4.0/notification-test-sender.apk) |

两个 APK 均随 [v0.4.0 Release](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/tag/v0.4.0) 提供，**发送器没有升级为 0.4.0**。安装 APK，不要安装或解压 `Source code` 源码包。校验值见 [SHA256SUMS.txt](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/download/v0.4.0/SHA256SUMS.txt)。

1. 安装两个 APK，打开“通知筛选 Demo”并完成或跳过教程。
2. 到主程序 **我的 → 通知权限 → 开启通知使用权**，在系统设置中授权，返回确认已授权、已连接。
3. 打开“通知测试发送器”；Android 13 及以上还需允许它**发送通知**。这和主程序的“通知使用权”是两项不同权限。
4. 保持默认本机关键词、观察模式，发送“101 · 普通消息”和“102 · 推荐 / 优惠”两个样本，再到主程序的 **消息** 查看判断、**首页** 查看留存记录概览。

详细操作和下载问题见 [三分钟扫码体验指南](docs/HACKATHON_DOWNLOAD.md)。不需要 API Key 就能体验关键词判断；查看模型概率需要另外配置服务和自己的 Key，项目不内置凭据。

- 更完整的样本验收见[手机测试指南](docs/PHONE_TESTING.md)；模型参数和协议细节见[模型测试指南](docs/MODEL_TESTING.md)，当前预设与迁移行为以下文及 v0.4.0 发布说明为准。
- 演示“手动划掉淘宝样本后，淘宝概率下降、银行保持自身判断”，看[三分钟注意力演示](docs/ATTENTION_DEMO.md)。
- 获取其他版本可查看 [Releases](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/latest)。

v0.4.0 已通过 **279 项纯 Java 检查和 209 项 Android 模拟器检查**，以及两个 APK、测试 APK 与 lint 构建。本机 HTTPS 合成演示中，淘宝探针 `0.700 → 0.473`，银行对照基本不变；这些是模拟服务结果。**未进行实体手机或真实模型服务验证。** 本版详情见 [v0.4.0 发布说明](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/tag/v0.4.0)；v0.3.0 的 234 项纯 Java / 166 项模拟器检查和早期截图保留在[历史验证记录](docs/VERIFICATION.md)。

## 四个页面

| 页面 | 可以做什么 |
|---|---|
| 首页 | 查看真实监听状态、今日概览和 2×2 文件夹；点文件夹进入对应消息分类。 |
| 消息 | 按全部、重要、稍后、已过滤筛选真实记录；点气泡看详情，模型记录并排显示最多三路 `p_jev → p_final`。 |
| 智能判断 | 查看当前实际策略，进入模型配置或 Attention；不会因为存在模型预设就自动联网。 |
| 我的 | 管理通知权限、重新扫描、测试发送器、筛选规则、自动清除和高级设置；可重播使用引导。 |

数量来自本机最近 150 条留存记录，同一通知可有多条记录，并非完整通知总量。“重要”指保留记录，“已过滤”仅指系统确认清除；建议清除及对照记录不会被当作已经删除。

## 三条模型路线

| 路线 | 新安装预设 | 协议 |
|---|---|---|
| 路线 1 | 团队中转 1052：`https://10521052.xyz/jev`；`local-systemone-ft` | JEV SystemOne |
| 路线 2 | 团队中转 1052：`https://10521052.xyz/jev`；`local-systemone-v1` | JEV SystemOne |
| 路线 3 | 团队中转 1052：`https://10521052.xyz/jev`；`typesafe-jev` | JEV SystemOne |

在 **智能判断 → 模型配置** 中编辑路线。1052 下拉可选 `local-systemone-ft`、`local-systemone-v1`、`typesafe-jev`、`bocha-jev`，这些模型共用一份本机加密的平台 Key。也可改选 TypeSafe 官方（`https://api.typesafe.ai`，`jev-latest`）、Bocha 团队网关（`https://tokendance.space/gateway/typesafe`，`bocha-jev-v1`）或自定义 HTTPS 服务。

路线保存地址、模型 ID、标签及凭据；v0.4.0 界面统一使用 **Jev SystemOne**，不再提供 Chat Completions 选择。旧 Chat 配置升级后保留地址、模型和 Key，关闭远程处理并提示复核；重新保存后统一使用 Jev。跨服务修改地址会清空当前编辑器的旧服务 Key，同平台模型切换沿用共享 Key。预设不是连接成功证明；自训服务仍需实际部署，填写模型名不会完成训练或部署。

JEV 使用 `POST /v1/systemone`（网关保留其地址前缀），按 `answers.keep.probabilities["重要"]` 获得 `p_jev`。仅当概率字段或“重要”键缺失时，才将合法 `choice` 的重要／广告映射为 1／0，并标明没有原始概率；已提供但无效的概率按失败处理。保留阈值默认 `0.5`。连接测试显示 HTTP 状态、概率与耗时；它只发送固定合成消息，即使远程通知处理关闭也可由用户点击触发。

五种策略为关键词、路线 1 单路、路线 2 单路、路线 3 单路、多路对照。对照可勾选最多三路，并排显示原始／融合概率、耗时、结果与一致性，**始终不清除通知**。保存配置会关闭自动清除；需要删除时，再到 **我的 → 自动清除** 手动开启。

## 本地短时注意力

手机只把明确的行为计入衰减记忆：60 秒内点击 `y=1`、稍后点击 `y=0.7`、单条手动划掉（系统 reason 2）`y=0.05`、持续 30 分钟未处理 `y=0.2`。系统“全部清空”、来源 App 自行移除、监听器自动清除等其他原因不训练偏好。

默认半衰期 30 分钟、融合权重 `w=1`；希望更缓慢遗忘时可手动设为 **1440 分钟**。同一个包下的 `【淘宝】`、`【银行】` 等标题前缀分别统计，新前缀从先验 `(1,1)` 开始，不继承包级偏好。注意力页可查看 `p_short` 最高／最低 5 项及非负有效证据数。

每项从先验 `alpha=1, beta=1` 开始。设距上次更新的时间为 `dt`、半衰期为 `h`（两者使用相同单位），收到真实行为 `y` 时按以下公式更新：

```text
lambda  = 2^(-dt/h)
alpha   = lambda * alpha + y
beta    = lambda * beta + (1-y)
p_short = alpha / (alpha + beta)；若两者之和为 0，则取 0.5
n_raw   = alpha + beta - 2
n       = max(0, n_raw)

若 n = 0 或 w = 0：p_final = p_jev（原值直接返回）
否则：p_final = sigmoid(logit(p_jev) + w * n/(n+3) * logit(p_short))

logit(p) = ln(clamp(p, 0.01, 0.99) / (1-clamp(p, 0.01, 0.99)))
sigmoid(z) = 1 / (1 + exp(-z))
```

原始 `alpha`、`beta` 连同先验一起衰减，因此 `n_raw` 可能为负；只有非负有效 `n` 进入融合。只看快照时执行衰减，不增加虚构的 `y`。无证据或 `w=0` 的分支在 logit 裁剪前返回，连原始 0／1 都完全保持。

默认 `h=30 分钟`、`w=1`、保留阈值 `0.5`；`p_final >= 阈值` 时保留，否则单路模型可建议清除。实际删除仍须开启自动清除；多路对照始终只观察。极值影响与演示判读见[注意力说明](docs/ATTENTION_DEMO.md)。

“近期行为摘要”会从当前目标 App 的真实行为中选择变化最大的至多两项，随 JEV 请求发送，可能包含标题前缀。需要只观察本地融合效果时，关闭此摘要选项再保存。独立事件上传是另一个默认关闭的功能，启用后才向指定 HTTPS `/events` 服务发送事件；正文仍需单独选择。

## 关键词基线

1. 安装 `notification-filter-demo.apk` 和 `notification-test-sender.apk`。
2. 在主程序“我的 → 通知权限”点“开启通知使用权”，返回确认已授权／已连接；在发送器点“开启通知权限”。
3. 保持关键词策略、观察模式，在“我的 → 筛选规则”把目标包名暂设为 `com.example.notificationdemo.sender` 并保存。
4. 在发送器清空旧样本，再点“发送一组验收样本 · 9 张”；观察模式应保留全部 9 个对象。
5. 再次清空发送器样本，到“我的 → 自动清除”开启开关并重发；默认关键词应仅清除 102、202，保留 101、103、104、105、200、201、203。

默认目标为 `com.sina.weibo,com.example.notificationdemo.sender`；微信 `com.tencent.mm` 默认不参与清除。保留词为 `紧急,会议,重要,家人`，清除词为 `热搜,推荐,优惠,广告`。保留词和受保护通知优先；清除词仅用于关键词策略。模型失败、远程关闭或旧请求结果失效时保留通知。

“我的 → 通知权限 → 重新扫描现有通知”按当前策略处理仍存在的卡片。**自 v0.3.0 起，“消息”中的“清空”还会重置本地短时记忆和通知生命周期追踪**，适合重做演示；它不会清理系统通知。发送器的“清空本发送器的全部通知”只移除发送器样本，也不等于手动划掉行为。

## 边界与数据

- 操作单位是系统通知卡片，不是聊天消息；不能逐句改写来源 App 的合并通知，也不能保证拦住已发生的声音、振动或横幅。
- 数据由来源 App 与系统提供；隐藏预览、缺失正文及验证码脱敏都会影响可判断信息。Android 8–10 的结构化多消息卡片保守保留。
- “请求清除”不是成功，需要核对“已清除”的系统回调与发送器当前 ID。来源 App 可以重发；系统移除原因不能完全区分多个监听器同时操作的归因。
- 默认本机保存最近 150 条事件，消息页最多显示当前分类的最近 80 条；写入日志的正文超过 900 字符会截断，气泡展示摘要，点击可看留存详情。模型输入超过限额时保留，不靠截断内容作决定。
- API Key 和事件服务 Token 使用 Android Keystore 加密。通知日志和设置不参与备份或设备迁移；短时记忆不保存通知正文，但本机验证日志仍可包含正文。
- 关闭“自动清除”仅停止删除，不停止模型观察请求。停止远程请求应关闭并保存相应远程开关；完全停止读取需撤回系统通知使用权。

## 构建与验证

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。Gradle Wrapper 固定 8.13、Android Gradle Plugin 固定 8.11.1。

```powershell
.\gradlew.bat :filter:assembleDebug :sender:assembleDebug :filter:assembleDebugAndroidTest :filter:lintDebug :sender:lintDebug
```

输出在 `filter/build/outputs/apk/debug/` 和 `sender/build/outputs/apk/debug/`。这是 debug 签名测试包；不同来源的 debug APK 可能签名不同。使用指定 Release 给队友测试，覆盖安装冲突时先记录旧结果再处理。

纯 Java 检查与三套模拟器测试的运行说明见 [tests/README.md](tests/README.md)。先运行单元测试，再按需要执行模拟器套件：

```powershell
.\tests\run-unit-tests.ps1
.\tests\run-device-smoke.ps1 -Device emulator-5554
.\tests\run-model-smoke.ps1 -Device emulator-5554
.\tests\run-attention-smoke.ps1 -Device emulator-5554
```

`run-attention-smoke.ps1` 额外需要**宿主机 Python 和 `cryptography` 包**，用于运行真正的 localhost HTTPS 合成服务与生成临时证书；该依赖不进入 APK。可用 `-Python` 指定已有 Python 解释器。临时 CA、私钥与证据放在 Git 仓库外的独立 work 目录，测试仍执行正常主机名验证，不修改生产 APK 的信任策略。

设备脚本会安装测试包、调整测试授权并重置配置／日志，应使用可丢弃模拟器。注意力套件的 30 分钟忽略用例使用有界 debug 时间偏移，不代表真实等待或后台运行了 30 分钟。仿真模型结果和本地 HTTPS 请求不等于官方服务接通。Actions 构建与 Lint 通过也不等于实体手机通过。

发送器 debug 场景包含原有 `batch` 等样本及新场景 `attention_demo`、`probe`：

```powershell
adb -s emulator-5554 shell am start -n com.example.notificationdemo.sender/.MainActivity --es scenario attention_demo
```

当前仓库没有 `attention_suite_v1.jsonl`，`suite_s1` 明确跳过，没有生成替代题库。v0.4.0 未新增题库评测页；四页界面和首次使用教程均已提供可用入口。

## 开源与参与

本仓库采用 [MIT License](LICENSE)，保留既有 Git 提交历史。可以浏览源码、Fork 后提交 Pull Request，或通过 [Issues](https://github.com/Dalaoyuan2020/android-notification-filter-demo/issues/new/choose) 反馈；访问公开仓库不需要协作者邀请。克隆地址为 `https://github.com/Dalaoyuan2020/android-notification-filter-demo.git`，构建与开发操作见[开发指南](CONTRIBUTING.md)。

反馈请附机型、Android 版本、两个 App 版本、操作步骤和合成样本结果；不要提交 API Key、验证码或真实私聊正文。MIT 许可原文见仓库根目录 `LICENSE`。

## 目录与依据

- `filter/`：通知监听、关键词和模型判断、短时记忆、概率融合、凭据与日志。
- `sender/`：独立合成通知发送器。
- `filter/src/androidTest/`、`tests/`：设备集成与纯 Java 测试。
- `docs/`：手机验收、模型接入和注意力演示；版本变化见 [CHANGELOG](CHANGELOG.md)。

Android 能力依据：[通知监听服务](https://developer.android.com/reference/android/service/notification/NotificationListenerService)、[通知结构](https://developer.android.com/reference/android/app/Notification)、[Android 15 敏感通知保护](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction)。JEV 接口依据：[TypeSafe OpenAPI](https://api.typesafe.ai/openapi.json)、[TypeSafe 文档](https://api.typesafe.ai/docs)、[Bocha 官方接口说明](https://jev.bocha.cn/install/bocha-jev/SKILL.md)。
