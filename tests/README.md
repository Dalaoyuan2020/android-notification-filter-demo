# 测试入口与证据范围

从仓库根目录执行以下命令。这里说明如何复现及每套测试覆盖什么，不预填本版通过数量，也不代替本次运行的原始输出。

## 1. 四组纯 Java 测试

仅需 JDK 17，不需要 Android 设备、Python、网络或模型凭据。设置 `JAVA_HOME`，或将 `java`、`javac` 加入 PATH。

Windows PowerShell：

```powershell
.\tests\run-unit-tests.ps1
```

Linux／macOS Bash：

```bash
bash tests/run-unit-tests.sh
```

两个入口使用相同源码，编译至 `build/unit-tests`，依次运行四组测试；任一编译或测试失败即返回失败。

| 测试类 | 主要范围 |
|---|---|
| `DecisionEngineTest` | 来源精确匹配、保留词优先、空内容、受保护类型、分隔符、长文本 |
| `SystemOneProtocolTest` | JEV 中文请求与路径、严格 JSON／概率解析、choice 回退、阈值、预设及重试预算等纯逻辑 |
| `ShortTermMemoryTest` | 行为映射、alpha／beta 衰减、负 raw n 与非负有效 n、logit 融合、零证据／零权重原值保持、前缀隔离与持久化 |
| `p0_api_config_test` | 1052 默认预设、四模型順序及请求 body、`/jev/v1/systemone` 路径、旧 Chat 转 Jev、迁移复核暂停远程处理、官方／博查预设 |

这些是确定性逻辑检查。通知读取、真实系统移除原因、Android Keystore 和实际 TLS 连接需要下面的模拟器测试。

## 2. 三套模拟器测试

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0、可用的 `adb` 和已启动的可丢弃 Android 模拟器。设置 `ANDROID_HOME` 或把 `adb` 放入 PATH。先构建两个 App 和测试 APK：

```powershell
.\gradlew.bat :filter:assembleDebug :sender:assembleDebug :filter:assembleDebugAndroidTest :filter:lintDebug :sender:lintDebug
```

将以下 `emulator-5554` 替换为实际模拟器序列号：

```powershell
.\tests\run-device-smoke.ps1 -Device emulator-5554
.\tests\run-model-smoke.ps1 -Device emulator-5554
.\tests\run-attention-smoke.ps1 -Device emulator-5554
```

| 脚本 | 覆盖与证据 |
|---|---|
| `run-device-smoke.ps1` | 两个独立 App 之间的真实系统通知：观察保留、关键词清除、保护规则、更新通知和系统移除确认 |
| `run-model-smoke.ps1` | Android 上的模型协议、配置与密钥存储、失败回退及异步竞态；使用测试连接／合成响应 |
| `run-attention-smoke.ps1` | 真正 localhost HTTPS 请求、三路对照、设置页四个 1052 模型选择到请求 body、共享密钥加密及旧配置复核、真实点击／逐条划除回调、前缀隔离、概率融合与一次性忽略记录 |

脚本安装测试包、调整测试应用权限、发送／清理通知，并重置 Demo 配置或本地数据。不要指向保留个人状态的手机。模型与注意力脚本会要求 `emulator-*` 序列号；基线脚本同样应使用可丢弃模拟器。脚本成功标志与检查数量来自当次 instrumentation 输出，不能复制旧版本数字作为本次结果。

## 3. 注意力套件的 Python 与实际 HTTPS 服务

注意力脚本额外需要**宿主机 Python 及其 `cryptography` 包**。`cryptography` 用于生成临时 CA／服务端证书；它不是 Android App 的依赖。入口默认使用 PATH 中的 `python`，也可指定已有解释器：

```powershell
.\tests\run-attention-smoke.ps1 -Device emulator-5554 -Python 'C:\Path\To\python.exe'
```

在已有合适 Python 环境中执行即可。脚本不会为你自动安装依赖；缺少模块时，应先识别环境问题，不能把它当成模型或注意力算法失败。

`systemone_mock_server.py` 只绑定宿主机 `127.0.0.1` 的临时端口，使用真实 HTTPS。`adb reverse` 将模拟器中的 `https://localhost:<port>` 转发到该服务。服务核对三条对照测试路由及 `/jev/v1/systemone` 的 `state/questions.keep`，固定返回合成保留概率 `0.7`。P0 用例实际操作设置页模型下拉框并保存，仅在测试中把预设地址替换为 localhost；主机脚本独立检查四个 1052 模型名称均出现在成功请求的 body 中。不请求外部模型，不截取凭据页面。

这是实际 HTTP／TLS 请求链路，并非仅替换 Java 返回值。它证明的是客户端与本地合成服务的传输和处理流程，不是任一公开服务的可用性。

### 临时 CA 与正常主机名验证

- 每次生成一次性 CA 和含 `localhost` SAN 的服务端证书。Android 测试只接收 CA 公共证书，不接收 CA／服务端私钥。
- 测试连接工厂仅允许该次指定的 `https://localhost:<port>`，使用只信任临时 CA 的测试 SSLContext；**保留 `HttpsURLConnection` 正常主机名验证**，没有“信任全部证书”或关闭 hostname 检查。
- 临时信任仅在 instrumentation 测试代码中使用，不安装为系统永久信任，也不包含在提供给用户的 App APK 中。正常 App 的 HTTPS 验证策略保持独立。
- 默认状态目录在 Git checkout 外的 `work/attention-https-<随机标识>`。可用 `-StateDirectory` 指定另一个**仓库外的新目录**；脚本拒绝把临时私钥放进仓库。不要复用含旧 `ready.json` 的目录。

脚本结束时移除端口反向转发并停止本地服务，保留外部 work 目录供检查。目录可能包含 `ready.json`、证书／私钥、服务日志、`requests.jsonl`、`attention-instrumentation.log` 和 `attention-proof.png`。按需保存合成测试证据，**不要把临时私钥加入 Git、Release 或 APK**。

## 4. 真实行为与时间加速的区别

五条 `【淘宝】` 样本使用不同通知 ID 和独立分组；测试经 SystemUI 对每张卡执行 `ACTION_DISMISS`，核对真实系统单条取消原因，没有直接注入学习数据。新 `【银行】` 前缀从先验开始，不继承淘宝偏好。点击通过通知真实 PendingIntent 触发。系统全部清空、整组连带取消和 App 自行移除不计作这五次划除。

忽略用例不会等待现实中的 30 分钟。它对仍然活动的通知使用**有界 debug 时间偏移**触发 30 分钟扫描，并确认 `y=0.2` 且重复扫描不重复计数。因此该用例不能证明 30 分钟锁屏、后台或重启后的稳定性；这些需另行按手机指南实测。

固定 `p_jev=0.7` 将后端输出保持不变，便于检查本地融合。真实模型可能受内容和近期行为摘要影响，不能把此处固定响应的效果直接外推到真实模型。

## 5. 失败记录与停止规则

单个脚本失败后，先保存命令、完整错误、环境版本和外部证据目录。区分编译、设备连接、权限、缺失 Python 依赖、TLS、协议解析和业务断言，再做针对性修改。

**同一构建或测试错误连续尝试 3 次仍未解决，停止这条失败路线并在 PR 或 Issue 报告**，附上三次尝试和变化；不要无限重跑，也不要改断言凑通过。脚本本身不会自动循环重试整个套件。模型客户端对 429／529 的有限请求重试与此构建／测试停止规则不同。

当前缺少 `attention_suite_v1.jsonl`，`suite_s1` 明确跳过，没有生成替代评测数据；v0.4 评测页面尚未实现。手动操作与报告格式见[注意力演示](../docs/ATTENTION_DEMO.md)及[手机指南](../docs/PHONE_TESTING.md)。
