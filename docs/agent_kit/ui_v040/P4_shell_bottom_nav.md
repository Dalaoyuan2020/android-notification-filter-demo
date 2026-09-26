# P4 · 四页信息架构、底部导航与代码结构

> 原文摘自 Issue #1（UI 重构需求），按阶段拆分；原文字句不改。与 00_INDEX.md 冲突时以 00_INDEX.md 为准。

三、本轮最重要的改造目标
现在已有版本最大的问题是：
把几乎所有功能都堆在首页，导致首页像一个很长的后台管理面板。
这一次必须彻底解决。
我不要：
一个页面从上到下展示运行状态、日志、API、关键词、Attention、模型参数、上传配置、Debug 等所有东西
我要的是一个真正的移动 App：
顶部只展示当前页面的核心信息，底部有固定导航，不同功能真正拆分到不同页面。
最终 App 固定为四个一级页面：
首页
消息
智能判断
我的
使用底部导航栏进行切换。
四个页面在 MainActivity 的内容容器内切换即可，不需要为了四个 tab 重构成四套复杂 Activity。


六、底部导航栏——必须真正实现
整个 MainActivity 底部固定一个漂浮式导航栏。
四项：
首页
消息
智能判断
我的
导航栏不能随着首页内容滚走。
推荐样式：
白色或暖白色悬浮胶囊
圆角约 24～30dp
有很轻的阴影
距离屏幕左右约 18～24dp
距离底部安全区约 12～18dp
四个按钮横向均分。
默认 icon + 简短文字。
当前选中状态不要简单把文字变蓝。
可以参考图 1：
选中项形成黑色圆形 / 胶囊形焦点区域，图标反白。
未选中项为浅灰黑。
点击时只切换 MainActivity 内容区。
不要重新启动 MainActivity。
页面切换动画可以非常轻：
alpha + translationY 4～8dp
120～180ms
不要复杂动画。


二十一、代码结构要求
虽然当前 UI 主要动态创建，但不要继续把所有代码无脑塞进 onCreate()。
如果 MainActivity 已经过长，请适当拆出类似：
buildHomePage()
buildMessagesPage()
buildIntelligencePage()
buildProfilePage()
buildBottomNavigation()
buildFolderCard(...)
buildPinnedNote(...)
buildMessageBubble(...)
dp(...)
sp(...)
这样的明确方法。
可以新增一个轻量 UI 工具类，但不要过度架构。
代码应当：
可读；
可维护；
不重复；
不产生大量匿名嵌套；
不把业务逻辑复制四份。
