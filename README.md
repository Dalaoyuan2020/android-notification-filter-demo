# 通知筛选 Demo

**后端 Jev 管长期判断，手机本地管短时注意力。** v0.3.0 在原生 Android 通知监听器上加入 SystemOne 概率判断、本地衰减记忆和最多三路对照：先获得模型保留概率 `p_jev`，再用近期真实行为计算 `p_final`。

默认仍是**本机关键词策略、自动清除关闭、模型远程处理关闭、事件上传关闭、正文上传关闭**。近期行为摘要选项默认开启，但只有主动启用 JEV 模型请求后才随请求发送；单路连接测试会在点击按钮后单独发送固定合成消息。无内置 API Key。

项目包括两个独立 App：**通知筛选 Demo**负责监听与判断；**通知测试发送器**发布合成样本，且不联网。最低 Android 8.0（API 26），compile/target SDK 35，无第三方运行时依赖。

## 开始测试

- [下载指定 Release 的两个 APK](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/latest)，不要把源码压缩包当成安装包。
- 第一次安装看[手机测试指南](docs/PHONE_TESTING.md)，模型设置看[三路模型配置](docs/MODEL_TESTING.md)。
- 演示“手动划掉淘宝样本后，淘宝概率下降、银行保持自身判断”，看[三分钟注意力演示](docs/ATTENTION_DEMO.md)。
- 协作者 `rui460` 先[接受私有仓库邀请](https://github.com/Dalaoyuan2020/android-notification-filter-demo/invitations)；修改代码看[开发指南](CONTRIBUTING.md)。

v0.3.0 已完成 234 项纯 Java 检查和 Android 15 AOSP 模拟器上的 166 项检查；实际本机 HTTPS 演示中，淘宝探针 `0.7000 → 0.4734`，银行 `0.7000 → 0.7000`。构建、截图及边界见[本版验证记录](docs/VERIFICATION.md)。未进行实体手机测试，也没有使用真实凭据联调官方服务或自训部署；不能把 mock 结果当作这些验证结果。

## 三条模型路线

| 路线 | 新安装预设 | 协议 |
|---|---|---|
| TypeSafe 官方 | `https://api.typesafe.ai`；模型 `jev-latest` | JEV SystemOne |
| Bocha 官方 | `https://jev.bocha.cn`；模型 `bocha-jev-v1` | JEV SystemOne |
| 自建／自训中转 | 地址和模型 ID 留空，按实际部署填写 | 默认 JEV SystemOne，可显式改为 Chat Completions |

三条路线分别保存地址、协议、模型 ID、标签和密钥。预设不是连接成功证明；自训中转需要实际可访问的部署，填写模型名不会完成训练或部署。v0.2.0 已有配置保留原值和 **Chat Completions** 协议，不会被静默改成 JEV；可在界面显式载入 TypeSafe／Bocha 预设，原密钥保留。

JEV 使用 `POST /v1/systemone`，按 `answers.keep.probabilities["重要"]` 获得 `p_jev`；只有 `choice` 时按重要／广告映射为 1／0，并在界面标明没有原始概率。保留阈值默认 `0.5`。API 细节及官方资料见[模型测试指南](docs/MODEL_TESTING.md)。

五种策略为关键词、TypeSafe 单路、Bocha 单路、自训中转单路、多路对照。对照可勾选最多三路，并排显示原始／融合概率、耗时、结果与一致性，**始终不清除通知**。单路保存后先观察，需要删除时另行开启“自动清除”。

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
2. 在筛选器点“开启通知使用权”，返回确认已授权／已连接；在发送器点“开启通知权限”。
3. 保持关键词策略、观察模式，把目标包名暂设为 `com.example.notificationdemo.sender` 并保存。
4. 在发送器清空旧样本，再点“发送一组验收样本 · 9 张”；观察模式应保留全部 9 个对象。
5. 再次清空发送器样本，开启自动清除并重发；默认关键词应仅清除 102、202，保留 101、103、104、105、200、201、203。

默认目标为 `com.sina.weibo,com.example.notificationdemo.sender`；微信 `com.tencent.mm` 默认不参与清除。保留词为 `紧急,会议,重要,家人`，清除词为 `热搜,推荐,优惠,广告`。保留词和受保护通知优先；清除词仅用于关键词策略。模型失败、远程关闭或旧请求结果失效时保留通知。

“重新扫描现有通知”按当前策略处理仍存在的卡片。**v0.3.0 中，验证记录的“清空”还会重置本地短时记忆和通知生命周期追踪**，适合重做演示；它不会清理系统通知。发送器的“清空本发送器的全部通知”只移除发送器样本，也不等于手动划掉行为。

## 边界与数据

- 操作单位是系统通知卡片，不是聊天消息；不能逐句改写来源 App 的合并通知，也不能保证拦住已发生的声音、振动或横幅。
- 数据由来源 App 与系统提供；隐藏预览、缺失正文及验证码脱敏都会影响可判断信息。Android 8–10 的结构化多消息卡片保守保留。
- “请求清除”不是成功，需要核对“已清除”的系统回调与发送器当前 ID。来源 App 可以重发；系统移除原因不能完全区分多个监听器同时操作的归因。
- 默认本机记录最近 150 条事件；UI 日志正文超过 900 字符会截断。模型输入超过限额时保留，不靠截断内容作决定。
- API Key 和事件服务 Token 使用 Android Keystore 加密。通知日志和设置不参与备份或设备迁移；短时记忆不保存通知正文，但本机验证日志仍可包含正文。
- 关闭“自动清除”仅停止删除，不停止模型观察请求。停止远程请求应关闭并保存相应远程开关；完全停止读取需撤回系统通知使用权。

## 构建与验证

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。Gradle Wrapper 固定 8.13、Android Gradle Plugin 固定 8.11.1。

```powershell
.\gradlew.bat :filter:assembleDebug :sender:assembleDebug :filter:assembleDebugAndroidTest :filter:lintDebug :sender:lintDebug
```

输出在 `filter/build/outputs/apk/debug/` 和 `sender/build/outputs/apk/debug/`。这是 debug 签名测试包；不同来源的 debug APK 可能签名不同。使用指定 Release 给队友测试，覆盖安装冲突时先记录旧结果再处理。

三组纯 Java 测试与三套模拟器测试统一说明见 [tests/README.md](tests/README.md)。先运行单元测试，再按需要执行模拟器套件：

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

当前仓库没有 `attention_suite_v1.jsonl`，`suite_s1` 明确跳过，没有生成替代题库。**v0.4 的评测页面尚未实现。**

## 目录与依据

- `filter/`：通知监听、关键词和模型判断、短时记忆、概率融合、凭据与日志。
- `sender/`：独立合成通知发送器。
- `filter/src/androidTest/`、`tests/`：设备集成与纯 Java 测试。
- `docs/`：手机验收、模型接入和注意力演示；版本变化见 [CHANGELOG](CHANGELOG.md)。

Android 能力依据：[通知监听服务](https://developer.android.com/reference/android/service/notification/NotificationListenerService)、[通知结构](https://developer.android.com/reference/android/app/Notification)、[Android 15 敏感通知保护](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction)。JEV 接口依据：[TypeSafe OpenAPI](https://api.typesafe.ai/openapi.json)、[TypeSafe 文档](https://api.typesafe.ai/docs)、[Bocha 官方接口说明](https://jev.bocha.cn/install/bocha-jev/SKILL.md)。
