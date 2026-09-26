# 通知筛选 Demo

两个独立的原生 Android App，用于验证通知读取、判断和清除。v0.2.0 支持关键词、官方模型、中转站模型与双路对照四种策略；默认关键词且不发送网络请求。模型模式需手动配置兼容接口并允许远程判断，无内置 API Key 或默认模型厂商。最低 Android 8.0（API 26），当前 compile/target SDK 35，无第三方运行时依赖。

## 给手机测试同学

请打开 [APK 下载页](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/latest)，下载 `notification-filter-demo.apk` 和 `notification-test-sender.apk`，安装两个 App；不用下载源码，也不用安装 Android Studio。

具体操作和反馈格式见 [真机测试指南](docs/PHONE_TESTING.md)；配置模型后再按 [模型对比指南](docs/MODEL_TESTING.md) 测试。协作者 rui460 请先 [接受邀请](https://github.com/Dalaoyuan2020/android-notification-filter-demo/invitations)，代码修改与提交见 [协作开发指南](CONTRIBUTING.md)。当前仅通过 Android 15 模拟器验证，不代表所有品牌手机已经实测通过。

仓库的 Actions 自动编译并运行规则测试、Android Lint。Actions 附件是临时开发构建，每次的调试签名可能不同；给队友安装时统一使用指定 Release 的 APK，避免混用签名导致覆盖安装失败。设备集成测试需另外运行，不能仅凭 Actions 构建通过就认定真机行为通过。

## 安装与使用

1. 安装筛选器和测试发送器两个 APK。
2. 打开“通知筛选 Demo”，点击“开启通知使用权”，在系统设置里允许通知访问，返回后应显示“已授权 / 已连接”。这与允许发送通知是两种权限。
3. 保持默认观察模式。打开“通知测试发送器”，允许发送通知，点击“发送一组验收样本”。
4. 回筛选器看记录：可以看到原始标题、文本、来源包名和判断理由，此时不会移除通知。
5. 在发送器中清空测试通知，回筛选器开启“自动清除”，然后重新发送验收样本。9 张样本中，只应清除两张广告通知；保留普通、紧急、持续和汇总通知。

默认目标包名为 `com.sina.weibo,com.example.notificationdemo.sender`。微信 `com.tencent.mm` 默认不参与自动清除，但已授权时可在记录中观察系统提供的通知内容。修改包名和关键词后需点击“保存规则”。

默认保留词：`紧急,会议,重要,家人`。默认清除词：`热搜,推荐,优惠,广告`。规则顺序：非目标来源跳过 → 持续/不可清除/汇总/通话导航闹钟保护 → 保留词优先 → 清除词命中 → 未命中保留。

自动开关只改变新到通知的处理方式；需要处理已经存在的通知时，点击“重新扫描现有通知”。“清空记录”仅清空本 App 日志，不清理系统通知。

## 两路模型判断

官方和中转站分别保存 Base URL、模型 ID、版本标签及可选 API Key。当前仅实现兼容 Chat Completions 的 HTTPS JSON 接口，不把未确认的 JVE/JEV、1052 或 duorive 名称猜成某个厂商。服务实际地址和协议仍待提供；本仓库的仿真测试不等于这两项真实服务已经接通。

双路对照把同一条符合处理条件的通知依次交给两路接口，记录各自结论、理由、调用耗时与是否一致，始终只观察。单路模式在观察中确认效果后可手动开启自动清除。目标包名、保留关键词、持续通知等保护仍优先；未启用远程、配置/联网/响应失败默认保留，通知更新或配置修改后旧结果作废。保存模型设置会关闭自动清除。

API Key 使用 Android Keystore 加密保存在本机；不提交到仓库或打进 APK。只有启用模型策略且允许远程判断后，才会发送符合条件的通知包名、标题、正文到配置的服务。连接测试仅发送固定合成样本。测试期请只把发送器列为目标。

## 处理边界

- 操作的是系统通知卡片，不是微信聊天数据；一张卡片内的多条消息无法逐条删改。
- 通知发布后再判断，不能保证阻止已经发生的声音、振动和横幅。
- 通知内容由来源 App 与系统决定；空内容、隐藏预览、验证码脱敏会限制可判断的信息。
- Android 8–10 的结构化多消息通知保守地整卡保留；Android 11+ 解析当前消息列表。
- 本机保留最近 150 条处理事件；记录中的正文超过 900 字符会截断，但关键词判断使用提取的完整文本。应用私有数据不参与备份和设备迁移。
- “请求清除”不是成功。“已清除”需要同 key/同版本的系统移除回调与监听器清除原因；超时或其它原因记录为“未确认”。系统回调不提供具体发起清除的监听器身份，其他监听器同时清除的竞争不能完全区分。
- 源 App 可以再次发布通知。受保护的通知和来源 App 自己的行为可能导致最终状态不同。
- 验证环境是 Android 15 AOSP x86_64 模拟器；没有验证用户手机、微信/微博实际版本、厂商后台策略、锁屏/重启后的长期稳定性，也未进行商店上架验证。

## 构建

需要 JDK 17、Android SDK Platform 35 与 Build Tools 35.0.0。配置 `JAVA_HOME`、`ANDROID_HOME`，或在 Android Studio 中打开该目录并让 IDE 配置 SDK。

```powershell
.\gradlew.bat :filter:assembleDebug :sender:assembleDebug :filter:assembleDebugAndroidTest :filter:lintDebug :sender:lintDebug
```

Gradle Wrapper 固定 8.13，Android Gradle Plugin 固定 8.11.1；已固定 Wrapper 分发包 SHA-256。第一次构建需要网络下载构建依赖。过滤器仅在显式启用模型或点击连接测试时联网；发送器不联网。

APK 位于 `filter/build/outputs/apk/debug/` 和 `sender/build/outputs/apk/debug/`。当前交付的是 debug 签名测试包，不是商店发布包。重新编译时若签名不同，覆盖安装需先卸载旧版本（会移除原本地日志和授权）。

## 测试

规则测试：见 `tests/README.md`。设备集成测试在专用测试设备/模拟器上执行：

```powershell
.\tests\run-device-smoke.ps1 -Device emulator-5554
```

此脚本安装两个 App 和测试 APK，为这两个测试 App 开启必要权限，并运行原生 Instrumentation。测试会临时修改筛选规则和模式、生成并清理发送器的测试通知，结束后恢复默认规则及观察模式。应使用专门的测试设备；测试期间默认规则包含微博。

设备测试比较系统中真实的活动通知 ID，并检查监听器确认日志；不会把历史归档记录误计为当前通知。

发送器 debug 场景入口：

```powershell
adb -s emulator-5554 shell am start -n com.example.notificationdemo.sender/.MainActivity --es scenario batch
```

支持 `clear`, `batch`, `normal`, `ad`, `conflict`, `empty`, `ongoing`, `group`, `update_ad`, `update_urgent`。Release 构建忽略调试场景入口。

模型协议、配置存储及通知竞态测试在专用模拟器运行：

```powershell
.\tests\run-model-smoke.ps1 -Device emulator-5554
```

此脚本会清空过滤器的本机配置与日志，仅支持模拟器。测试使用仿真连接和合成结果，不请求真实模型服务。真实官方/中转服务需按模型指南另行测试。

## 项目结构

- `filter/`：原生界面、通知监听、规则引擎、两个模型配置、HTTPS 协议适配、加密凭据与本地日志。
- `sender/`：独立的测试通知发送器。
- `filter/src/androidTest/`：真实跨 App 通知集成测试。
- `tests/`：纯 Java 规则测试与设备测试脚本。

## 官方依据

- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [通知结构](https://developer.android.com/reference/android/app/Notification)
- [Android 15 敏感通知保护](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction)
- [侧载应用受限设置](https://support.google.com/android/answer/12623953)
