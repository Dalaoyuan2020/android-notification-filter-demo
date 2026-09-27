# Keep Attention：扫码下载，约三分钟体验

需要 Android 8.0 或以上。请安装下面**两个不同的 App**：主程序负责读取与判断，发送器负责制造安全的合成测试样本。

## 1. 下载与安装

[打开下载页](https://hhu.winnielyu.com/keep-attention/download.html)，或扫描二维码：

<a href="https://hhu.winnielyu.com/keep-attention/download.html"><img src="assets/keep_attention_download_qr.png" alt="扫码打开 Keep Attention 下载页" width="220"></a>

| 下载 | 安装后找哪个图标 | 版本与作用 |
|---|---|---|
| [Keep Attention 主程序 APK](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/download/v0.4.0/notification-filter-demo.apk) | **通知筛选 Demo** | **0.4.0 / versionCode 4**；读取通知、判断与查看记录。当前桌面名称尚未改为 Keep Attention。 |
| [配套测试发送器 APK](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/download/v0.4.0/notification-test-sender.apk) | **通知测试发送器** | **0.3.0 / versionCode 3**；只发合成通知，不联网，也不读取其他 App 通知。 |

两个文件都在 [v0.4.0 Release](https://github.com/Dalaoyuan2020/android-notification-filter-demo/releases/tag/v0.4.0) 中，发送器沿用 0.3.0；不要把 Release 版本理解为两个 App 的版本都相同，也不要下载 `Source code` 压缩包来安装。

下载后通过系统安装器按提示安装。如果微信内浏览器不能下载，用右上角菜单在**系统浏览器**中打开同一下载页。若系统或设备管理策略阻止安装，先核对来源并联系维护者；不要忽略安全告警或关闭系统防护。覆盖安装出现签名冲突时，先保留现有测试结果，再反馈问题；本发布包是 debug 签名测试包。

## 2. 分别开两项权限

1. 打开主程序 **“通知筛选 Demo”**，看完或跳过首次教程。教程内的消息、数量和概率都是示意，不是实际采集结果。
2. 进入 **我的 → 通知权限 → 开启通知使用权**，在 Android 系统设置中授权主程序读取通知。返回 App，确认“通知使用权：已授权”和“监听服务：已连接”。这项授权也允许监听器按规则请求清除通知卡片。
3. 打开 **“通知测试发送器”**。Android 13 及以上需允许它发送通知；也可点发送器的 **“开启通知权限”** 按钮。

**主程序的“通知使用权”与发送器的“发送通知权限”不同。** 两个 App 各管一项；发送器获准发送，不代表主程序已能读取。

## 3. 保持观察，发两条样本

1. 主程序保持默认 **关键词 · 本机规则**，在 **我的 → 自动清除** 确认仍是观察模式。默认不调用模型、不上传事件，也不删除通知。
2. 在发送器依次点 **“101 · 普通消息 → 保留”** 和 **“102 · 推荐 / 优惠 → 清除”**。
3. 回主程序 **消息 → 全部**，查看新增的普通消息与推荐样本。默认规则应分别记录保留、建议清除；因为仍处于观察模式，主程序不会移除这两张系统通知。
4. 回 **首页** 查看真实记录概览，或点“重要消息”“稍后处理”等文件夹进入对应分类。旧日志可能仍在，因此概览总数不一定只有两条。

发送器按钮上的“清除”描述的是启用自动清除后的预期；本轮保持观察即可完成体验。默认目标包括发送器和微博，**微信默认不参与清除**。如只想测试发送器，可到 **我的 → 筛选规则** 把目标包名改为 `com.example.notificationdemo.sender` 并保存。

## 想看模型概率，再配置服务

关键词体验不需要 API Key。要看 `p_jev → p_final`，到 **智能判断 → 模型配置** 选择单路或多路对照，填写自己的服务凭据，保存后先运行固定合成消息连接测试。1052 预设地址是 `https://10521052.xyz/jev`，可选 `local-systemone-ft`、`local-systemone-v1`、`typesafe-jev`、`bocha-jev`；预设不含 Key，也不代表服务已经接通。

连接测试由点击按钮单独触发，即使远程通知处理关闭也会发送固定合成样本。开启并保存远程处理后，目标通知的来源、标题与正文才会发给配置的模型服务；若近期行为摘要选项开启，也会附带相应摘要。多路对照始终只观察，保存配置会关闭自动清除。更多协议和配置细节见 [README 的三条模型路线](../README.md#三条模型路线)。

## 记录结果与参与开发

发布前通过的是 **279 项纯 Java 检查和 209 项 Android 模拟器检查**，不是实体手机验收，也不代表真实模型服务已联调。真机体验请记录手机型号、Android 版本、两个 App 的版本、操作步骤和合成样本结果，通过 [Issues](https://github.com/Dalaoyuan2020/android-notification-filter-demo/issues/new/choose) 反馈；不要提交密钥、验证码或真实私聊正文。

源码与既有提交历史保留在 [GitHub 仓库](https://github.com/Dalaoyuan2020/android-notification-filter-demo)，采用 [MIT License](../LICENSE)。公开仓库可直接浏览、Fork 和提交 Pull Request，无需私有协作者邀请。完整样本验收见[手机测试指南](PHONE_TESTING.md)，开发构建见 [README](../README.md#构建与验证)。
