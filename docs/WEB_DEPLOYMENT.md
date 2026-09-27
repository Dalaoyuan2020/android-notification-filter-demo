# Keep Attention 展示与扫码下载

- 产品介绍与 40 秒动画：https://hhu.winnielyu.com/keep-attention/
- 下载及安装引导：https://hhu.winnielyu.com/keep-attention/download.html
- 主程序为 0.4.0，配套测试发送器保持 0.3.0。两个 APK 均来自仓库 v0.4.0 Release，页面提供同域下载及 GitHub 备用入口。
- 二维码和分享海报均指向上述下载引导页；不是直接安装链接，访客可以先阅读两款软件的用途。

## 部署方式

复用既有 hhu.winnielyu.com 的 HTTPS 与 Nginx，以独立 `/keep-attention/` 前缀提供纯静态文件。发布目录与门户、`/robotics/` 数字展馆分离，不需要新模型服务或数据库。

2026-09-27 的发布使用独立 release/current 目录。变更 Nginx 前备份配置，先执行配置检查，再 reload 并等待新路由就绪。回退应只处理 Keep Attention 的独立发布目录与路由，不覆盖门户、数字展馆或用户数据。后续 APK 使用新的版本路径，不覆盖已经发布的版本文件。

下载文件路径为 `assets/apk/v0.4.0/notification-filter-demo.apk` 与 `assets/apk/v0.4.0/notification-test-sender.apk`。校验文件由下载页提供，与 GitHub Release 一致。

## 本次验证

- 公网产品首页、下载页、两 APK、二维码与海报均响应 200；视频 Range 请求响应 206。
- 两个公网 APK 的 SHA-256 与 v0.4.0 Release 完全一致；下载 MIME 为 Android 安装包，响应设置附件下载。
- 公网二维码与海报和本地生成文件逐字节一致。独立二维码解码器核验了纯码、海报和半尺寸海报，目标均为正式下载页。
- 浏览器已打开正式下载页，页面内容、图片与两个下载链接正确；本地检查 390px、320px 窄屏无水平溢出。
- 原门户首页、数字展馆与只读健康入口在部署前后均正常；其发布版本未变。
- 上述网页与文件验证不等于实体安卓手机安装验证；APK 仍为测试版，未声称真机通过。

仓库在保留历史的基础上改为公开，并按用户选择加入 MIT 许可证。公开前对既有 22 次提交、191 个历史文件内容进行启发式凭据检查，未发现可确认的真实密钥；这不构成绝无遗漏的保证。
