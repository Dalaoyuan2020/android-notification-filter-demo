# P10 · 响应式与安全区、真实数据刷新、动画

> 原文摘自 Issue #1（UI 重构需求），按阶段拆分；原文字句不改。与 00_INDEX.md 冲突时以 00_INDEX.md 为准。

十八、响应式与安全区
页面至少保证在常见 Android 手机：
360dp
393dp
412dp
宽度下正常。
不能使用大量写死 px。
使用：
dp
sp
weight
MATCH_PARENT
WRAP_CONTENT
等合理布局方式。
注意：
状态栏；
导航栏；
底部手势区；
内容与悬浮底栏之间的 padding。
消息列表最后一条不能被底栏盖住。

十九、真实数据与 UI 刷新
UI 中能够从现有逻辑获取的数据必须使用真实数据。
例如：
今日通知总数
重要消息数量
稍后消息数量
已过滤消息数量
通知列表
服务是否开启
当前模型状态
优先读取项目已有状态。
不要为了让 UI 看起来好看直接写：
24
8
12
6
这类假数据。
数据为空时显示正常 empty state，例如：
今天还没有通知
而不是制造示例通知。

二十、动画要求
动画只做增强，不做炫技。
可以包含：
页面切换
150ms 左右淡入 + 轻微上移
文件夹点击
scale 1 → 0.97 → 1
便签点击
translationY -2dp
+ elevation 略微提高
首次页面出现
文件夹可以：
alpha 0 → 1
translationY 12dp → 0
四个文件夹依次延迟几十毫秒即可。
不需要复杂物理动画。
不要明显拖慢启动速度。

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
