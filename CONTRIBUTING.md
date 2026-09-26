# 协作开发与测试

## rui460 从这里开始

已向 GitHub 账号 **rui460** 发出本私有仓库的 **Write** 协作者邀请。请登录该账号打开 [接受仓库邀请](https://github.com/Dalaoyuan2020/android-notification-filter-demo/invitations)。邀请未接受前，仓库和下载页可能显示 404。Write 权限允许推送开发分支、提交代码和管理测试 Issue；不需要仓库管理员权限。

接受后先做以下三件事：

1. 按 [真机测试指南](docs/PHONE_TESTING.md) 下载 Release 中的两个 APK，完成关键词基线测试。
2. v0.3.0 按[三路模型配置指南](docs/MODEL_TESTING.md)核对 TypeSafe／Bocha／自训中转、协议及各自凭据，再做[注意力演示](docs/ATTENTION_DEMO.md)。官方预设已明确，实际凭据未联调或自训端点未配置时，分别标记，不要当成已接通。
3. 在 [手机实测反馈](https://github.com/Dalaoyuan2020/android-notification-filter-demo/issues/new/choose) 提交机型、版本、结果和复现步骤。只使用合成通知文本，截图隐藏私人通知；不要提交 API Key。

## 修改代码

建议每项修改建立独立分支，提交 Pull Request 便于对照测试。无需 fork 私有仓库：

```powershell
git clone https://github.com/Dalaoyuan2020/android-notification-filter-demo.git
cd android-notification-filter-demo
git switch -c rui460/phone-test-fixes
```

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。可在 Android Studio 打开仓库根目录，或配置 JAVA_HOME 与 ANDROID_HOME 后执行：

```powershell
.\gradlew.bat :filter:assembleDebug :sender:assembleDebug :filter:assembleDebugAndroidTest :filter:lintDebug :sender:lintDebug
```

安装包分别位于 `filter/build/outputs/apk/debug/filter-debug.apk` 与 `sender/build/outputs/apk/debug/sender-debug.apk`。Release APK、GitHub Actions APK、自己本地构建的 APK 可能使用不同 debug 签名；覆盖安装冲突时先记录旧版本的结果，再卸载旧包安装。卸载会清除本地配置与日志，需要重新授权。开发测试使用专门的设备或模拟器。

提交前运行 [规则测试](tests/README.md)，并按修改范围补做通知或模型设备测试。GitHub Actions 会构建及执行规则测试、Lint，但不替代真机验收：

```powershell
.\tests\run-device-smoke.ps1 -Device emulator-5554
.\tests\run-model-smoke.ps1 -Device emulator-5554
git add <本次修改的文件>
git commit -m "Describe the fix and its behavior"
git push -u origin rui460/phone-test-fixes
```

`-Device` 必须填写你实际选择的测试设备序列号。设备测试脚本会安装测试包、开启测试应用权限、发送和清理合成样本，并重置本 Demo 配置；不要在存有需要保留配置或通知的个人环境上直接运行。模型自动测试使用仿真 HTTPS 连接，不消耗官方服务或中转站额度。

随后在 GitHub 打开 Pull Request，描述问题、修改后的行为、测试机型与结果。没有做的测试写“未测试”。不要提交密钥、签名文件、local.properties、个人通知内容或构建缓存。模型版本用界面中的版本标签和模型 ID 标识，API Key 在各自手机配置，不写进源码或 Issue。

## v0.3.0 核心测试

协议与注意力核心均可脱离 Android 运行，使用 JDK 17：

```powershell
.\tests\run-unit-tests.ps1
.\tests\run-attention-smoke.ps1 -Device emulator-5554
```

应覆盖严格概率解析、choice 回退、旧 Chat 配置迁移、半衰期原始 alpha／beta 衰减、负 raw n 与非负有效 n、前缀隔离、无证据／零权重原值保持。设备测试另外覆盖真实通知回调、清除确认、三路观察和异步结果作废；不能用纯 Java 测试代替系统行为测试。

固定 mock `p_jev=0.7` 用来隔离本地融合；真实模型可能受近期行为文本影响，二者的记录必须区分。界面箭头用 0.05 差值门槛，不能把“没有箭头”简单当成数学融合失败。

注意力模拟器测试另需宿主 Python 与 cryptography，用于生成仓库外的一次性 HTTPS 测试证书；这不是 App 的运行时依赖。详细前置条件见 [tests/README.md](tests/README.md)。

同一构建或测试错误连续尝试 3 次仍未解决时，停止该失败路线，在 PR 或 Issue 记录命令、错误、已尝试修改和阻断原因；不要继续盲目重复。未进行的真机、真实凭据或长期后台测试写“未测试”。

## 参数与状态的变更约定

- 新配置默认关键词，自动清除、模型远程、事件上传和正文上传均关闭。近期摘要默认为开，但只随用户启用的 JEV 请求发送。
- 保留旧配置的协议，不要把 v0.2.0 Chat 路线静默改成 JEV。TypeSafe／Bocha 预设只有公开地址和模型，不含任何 Key。
- 配置保存关闭自动清除，并使在途旧结果失效。修改 UI 标签或数据字段时，同步手机和模型指南。
- 原始 alpha／beta 包含衰减先验；不要改成固定加回 `(1,1)` 而仍声称公式不变。融合使用 `max(0,alpha+beta−2)`；无证据或权重为 0 时不做 clamp。
- “清空”在 v0.3.0 同时重置日志、短时记忆和 tracker。手动划掉只认可系统 reason 2；系统全清、App 自行清除、监听器取消不得训练偏好。
- Key 与服务 Token 只由 Android Keystore 保存；仅提交公开预设，不提交真实凭据、私人通知、日志导出、签名材料或个人 `local.properties`。

## 验收顺序

安装与两类权限 → 关键词观察保留 9 个对象 → 关键词自动仅清除 102／202 → 后台与重启 → 各路线固定合成连接测试 → 三路观察对照 → 五条淘宝手动划除与银行隔离探针 → 单路模型观察 → 按需单路自动清除。

每轮记录协议、模型、阈值、近期摘要开关、半衰期和权重。若原始概率接近极值，结合 logit 裁剪核对方向与公式，不承诺所有模型都有超过 0.05 的变化。当前没有 `attention_suite_v1.jsonl`，`suite_s1` 跳过；v0.4 评测页面没有实现。
