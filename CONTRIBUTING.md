# 协作开发与测试

## rui460 从这里开始

已向 GitHub 账号 **rui460** 发出本私有仓库的 **Write** 协作者邀请。请登录该账号打开 [接受仓库邀请](https://github.com/Dalaoyuan2020/android-notification-filter-demo/invitations)。邀请未接受前，仓库和下载页可能显示 404。Write 权限允许推送开发分支、提交代码和管理测试 Issue；不需要仓库管理员权限。

接受后先做以下三件事：

1. 按 [真机测试指南](docs/PHONE_TESTING.md) 下载 Release 中的两个 APK，完成关键词基线测试。
2. 如果测试 v0.2.0 的模型功能，再按 [模型配置与对比指南](docs/MODEL_TESTING.md) 配置官方和中转站接口。未提供 URL、模型 ID 或必要凭据时，标记“接口未配置”，不要当成已接通。
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

## 验收顺序

先确认安装和两类权限 → 关键词观察模式保留 9 个对象 → 自动模式只清除 102 和 202 → 后台与重启 → 两个接口的合成连接测试 → 双路对照 → 单路模型观察 → 单路模型自动清除。模型判断不一定与关键词相同，应记录每个样本的实际结论、理由和耗时，不预填通过。
