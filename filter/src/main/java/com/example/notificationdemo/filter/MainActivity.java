package com.example.notificationdemo.filter;

import android.app.Activity;
import android.app.NotificationManager;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small, dependency-free UI for testing the system notification listener. */
public final class MainActivity extends Activity {
    private static final int INK = ui_theme.INK;
    private static final int MUTED = ui_theme.MUTED;
    private static final int TEAL = ui_theme.ACCENT;
    private static final int BACKGROUND = ui_theme.PAPER;
    private static final int BORDER = ui_theme.BORDER;
    private static final int SOFT_TEAL = ui_theme.SOFT_GREEN;
    private static final int AMBER = ui_theme.WARNING;
    private static final int SOFT_AMBER = ui_theme.SOFT_YELLOW;
    private static final int MAX_VISIBLE_LOGS = 80;
    private static final int PAGE_HOME = 0;
    private static final int PAGE_MESSAGES = 1;
    private static final int PAGE_INTELLIGENCE = 2;
    private static final int PAGE_PROFILE = 3;
    private final ScrollView[] pages = new ScrollView[4];
    private final int[] scrollPositions = new int[4];
    private final boolean[] restoreScroll = new boolean[4];
    private FrameLayout pageHost;
    private bottom_navigation_view bottomNavigation;
    private int selectedPage = -1;

    private TextView permissionValue;
    private TextView connectionValue;
    private TextView modeValue;
    private TextView modeDescription;
    private TextView logCount;
    private TextView saveFeedback;
    private TextView strategySummary;
    private Switch autoSwitch;
    private EditText targets;
    private EditText keepWords;
    private EditText blockWords;
    private LinearLayout logList;
    private boolean receiverRegistered;
    private boolean updatingSwitch;
    private int sectionIndex;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshState();
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(ui_theme.paper(this));
        root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets edge = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                                | WindowInsets.Type.ime());
                view.setPadding(edge.left, edge.top, edge.right, edge.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        pageHost = new FrameLayout(this);
        pageHost.setFocusableInTouchMode(true);
        pageHost.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        root.addView(pageHost, new FrameLayout.LayoutParams(-1, -1));
        readPageState(savedInstanceState);
        // All four pages are created exactly once before refreshing their shared state.
        // Keeping their native views alive also keeps unsaved rule edits between tabs.
        buildHomePage();
        buildMessagesPage();
        buildIntelligencePage();
        buildProfilePage();
        buildBottomNavigation(root);

        targets.setText(savedInstanceState == null ? DemoStore.getTargets(this)
                : savedInstanceState.getString("targets", DemoStore.getTargets(this)));
        keepWords.setText(savedInstanceState == null ? DemoStore.getKeepWords(this)
                : savedInstanceState.getString("keepWords", DemoStore.getKeepWords(this)));
        blockWords.setText(savedInstanceState == null ? DemoStore.getBlockWords(this)
                : savedInstanceState.getString("blockWords", DemoStore.getBlockWords(this)));
        setContentView(root);
        root.requestApplyInsets();
        root.requestFocus();
        refreshState();
        int restoredPage = savedInstanceState == null ? PAGE_HOME : savedInstanceState.getInt("selected_page", PAGE_HOME);
        switchPage(restoredPage, false);
    }

    private void buildHomePage() {
        LinearLayout content = buildPage(PAGE_HOME, "首页", "留下重要消息，让通知栏清爽一点。");
        makeStatusCard(content);
        addPrivacyNote(content);
    }

    private void buildMessagesPage() {
        LinearLayout content = buildPage(PAGE_MESSAGES, "消息", "查看通知的真实判断与处理记录。");
        makeLogsCard(content);
    }

    private void buildIntelligencePage() {
        LinearLayout content = buildPage(PAGE_INTELLIGENCE, "智能判断", "管理判断策略、自动清除和短时注意力。");
        makeModelCard(content);
        makeModeCard(content);
    }

    private void buildProfilePage() {
        LinearLayout content = buildPage(PAGE_PROFILE, "我的", "设置通知处理范围与关键词规则。");
        makeRulesCard(content);
        addPrivacyNote(content);
    }

    private LinearLayout buildPage(int index, String title, String description) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setSaveEnabled(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scroll.setPadding(0, 0, 0, dp(104));
        scroll.setVisibility(View.GONE);
        pages[index] = scroll;
        pageHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        scroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (restoreScroll[index] && scroll.getVisibility() == View.VISIBLE && scroll.getHeight() > 0) {
                restoreScroll[index] = false;
                scroll.post(() -> scroll.scrollTo(0, scrollPositions[index]));
            }
        });
        FrameLayout frame = new capped_page_frame(this);
        scroll.addView(frame, new ScrollView.LayoutParams(-1, -2));
        LinearLayout content = column();
        content.setPadding(dp(ui_theme.PAGE_MARGIN), dp(ui_theme.PAGE_TOP),
                dp(ui_theme.PAGE_MARGIN), dp(24));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        TextView heading = text(title, ui_theme.TITLE_SP, INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        content.addView(heading);
        addSpace(content, 8);
        content.addView(text(description, 14, MUTED, false));
        addSpace(content, 26);
        return content;
    }

    /** Width is capped after the shell has consumed system/IME insets, including in landscape. */
    private static final class capped_page_frame extends FrameLayout {
        capped_page_frame(Context context) { super(context); }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (getChildCount() > 0) {
                View content = getChildAt(0);
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) content.getLayoutParams();
                int cap = ui_theme.dp(getContext(), 720);
                int available = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? cap
                        : Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft()
                        - getPaddingRight() - params.leftMargin - params.rightMargin);
                params.width = Math.min(cap, available);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void addPrivacyNote(LinearLayout content) {
        content.addView(text("默认关键词在本机处理 · 模型远程处理需手动开启\n仅处理通知卡片，不删除原 App 内的消息。",
                12, MUTED, false));
    }

    private void buildBottomNavigation(FrameLayout root) {
        bottomNavigation = new bottom_navigation_view(this);
        bottomNavigation.setOnPageSelectedListener(page -> switchPage(page, true));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        position.leftMargin = dp(22);
        position.rightMargin = dp(22);
        position.bottomMargin = dp(14);
        root.addView(bottomNavigation, position);
        bottomNavigation.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int reserved = bottomNavigation.getHeight() + dp(28);
            for (ScrollView page : pages) {
                if (page != null && page.getPaddingBottom() != reserved) {
                    page.setPadding(0, 0, 0, reserved);
                }
            }
        });
    }

    private void readPageState(Bundle state) {
        int[] savedPositions = state == null ? null : state.getIntArray("page_scroll_positions");
        for (int i = 0; i < pages.length; i++) {
            scrollPositions[i] = savedPositions != null && i < savedPositions.length ? Math.max(0, savedPositions[i]) : 0;
            restoreScroll[i] = true;
        }
    }

    private void switchPage(int requested, boolean animate) {
        int destination = requested >= 0 && requested < pages.length ? requested : PAGE_HOME;
        if (destination == selectedPage) {
            return;
        }
        if (animate) {
            View focused = getCurrentFocus();
            if (focused != null) {
                focused.clearFocus();
            }
            pageHost.requestFocus();
            // The editor can lose focus when its page becomes GONE. Close its IME
            // first, using the attached shell's token rather than an optional editor.
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(pageHost.getWindowToken(), 0);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController controller = pageHost.getWindowInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.ime());
                }
            }
        }
        if (selectedPage >= 0) {
            ScrollView previous = pages[selectedPage];
            scrollPositions[selectedPage] = previous.getScrollY();
            previous.animate().cancel();
            previous.setAlpha(1f);
            previous.setTranslationY(0f);
            previous.setVisibility(View.GONE);
        }
        selectedPage = destination;
        bottomNavigation.setSelectedPage(destination);
        ScrollView next = pages[destination];
        next.animate().cancel();
        next.setVisibility(View.VISIBLE);
        if (animate) {
            next.setAlpha(0f);
            next.setTranslationY(dp(6));
            next.animate().alpha(1f).translationY(0f).setDuration(150).start();
        } else {
            next.setAlpha(1f);
            next.setTranslationY(0f);
        }
    }

    private void makeStatusCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        sectionTitle(card, "01", "连接通知中心");
        addSpace(card, 18);
        permissionValue = statusRow(card, "通知使用权");
        addSpace(card, 11);
        connectionValue = statusRow(card, "监听服务");
        addSpace(card, 16);
        card.addView(text("先开启通知使用权，再用测试发送器验证读取和清除。", 13, MUTED, false));
        addSpace(card, 14);
        Button permission = button("开启通知使用权", true);
        permission.setOnClickListener(view -> openNotificationSettings());
        card.addView(permission, fullWidth());
        addSpace(card, 8);
        Button sender = button("打开测试发送器", false);
        sender.setOnClickListener(view -> openSender());
        card.addView(sender, fullWidth());
        addSpace(card, 8);
        Button refresh = button("重新扫描现有通知", false);
        refresh.setOnClickListener(view -> {
            if (FilterService.scanExisting()) {
                toast("已请求扫描，结果将出现在“消息”的验证记录中");
            } else {
                toast("监听尚未连接，请先授权；必要时在设置中关闭后重新开启");
            }
            refreshState();
        });
        card.addView(refresh, fullWidth());
    }

    private void makeModeCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        LinearLayout heading = row();
        TextView title = text("02  处理方式", ui_theme.SECTION_SP, INK, true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        modeValue = chip("观察模式", TEAL, SOFT_TEAL);
        heading.addView(modeValue);
        card.addView(heading);
        addSpace(card, 12);
        autoSwitch = new Switch(this);
        autoSwitch.setText("自动清除");
        autoSwitch.setTextSize(16);
        autoSwitch.setTextColor(INK);
        autoSwitch.setPadding(0, dp(8), 0, dp(8));
        autoSwitch.setMinHeight(dp(52));
        autoSwitch.setShowText(false);
        ui_theme.toggle(autoSwitch);
        autoSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (updatingSwitch) return;
            ModelConfig config = ModelStore.load(this);
            if (enabled && !allowsAutomatic(config)) {
                DemoStore.setAuto(this, false);
                refreshState();
                toast("当前策略仅观察，请先检查模型配置与远程处理开关");
                return;
            }
            DemoStore.setAuto(this, enabled);
            refreshState();
        });
        card.addView(autoSwitch, fullWidth());
        modeDescription = text("", 13, MUTED, false);
        card.addView(modeDescription);
        addSpace(card, 12);
        TextView caution = text("开启即按已保存的规则清除目标 App 的通知。清除的是通知卡片，不能保证恢复；也不能保证拦住声音、振动或横幅。", 12, AMBER, false);
        caution.setPadding(dp(12), dp(10), dp(12), dp(10));
        caution.setBackground(background(SOFT_AMBER, 10, 0));
        card.addView(caution);
    }

    private void makeModelCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        sectionTitle(card, "03", "判断策略");
        addSpace(card, 10);
        strategySummary = text("", 13, MUTED, false);
        card.addView(strategySummary);
        addSpace(card, 14);
        Button settings = button("配置模型与三路对照", false);
        settings.setOnClickListener(view -> startActivity(new Intent(this, ModelSettingsActivity.class)));
        card.addView(settings, fullWidth());
        addSpace(card, 8);
        Button attention = button("短时注意力 · 行为与上传设置", false);
        attention.setOnClickListener(view -> startActivity(new Intent(this, AttentionActivity.class)));
        card.addView(attention, fullWidth());
    }

    private void makeRulesCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        sectionTitle(card, "04", "关键词规则");
        addSpace(card, 6);
        card.addView(text("修改后点击保存，自动清除仅作用于这些 App。", 13, MUTED, false));
        targets = input(card, "目标 App 包名", "com.sina.weibo,com.example.notificationdemo.sender", 2, false);
        card.addView(text("默认包含微博和测试发送器；微信不在处理范围内。", 12, MUTED, false));
        keepWords = input(card, "保留关键词 · 优先匹配", "紧急,会议,重要,家人", 2, true);
        blockWords = input(card, "清除关键词", "热搜,推荐,优惠,广告", 2, true);
        card.addView(text("多个值用逗号或换行分隔。保留词始终优先，持续通知跳过。清除词仅用于关键词策略；模型策略使用模型判断。关键词未命中默认保留。", 12, MUTED, false));
        addSpace(card, 14);
        Button save = button("保存规则", true);
        save.setOnClickListener(view -> {
            String targetValue = normalizeEntries(targets.getText().toString());
            if (!isValidTargets(targetValue)) {
                targets.setError("请填写 App 包名，多个包名用逗号或换行分隔");
                targets.requestFocus();
                return;
            }
            String keepValue = normalizeEntries(keepWords.getText().toString());
            String blockValue = normalizeEntries(blockWords.getText().toString());
            DemoStore.setTargets(this, targetValue);
            DemoStore.setKeepWords(this, keepValue);
            DemoStore.setBlockWords(this, blockValue);
            targets.setText(targetValue);
            keepWords.setText(keepValue);
            blockWords.setText(blockValue);
            saveFeedback.setText(targetValue.isEmpty() ? "已保存 · 目标为空，所有通知保留"
                    : blockValue.isEmpty() ? "已保存 · 清除词为空，所有通知保留"
                    : "已保存 · 下次通知和手动扫描使用新规则");
            saveFeedback.setVisibility(View.VISIBLE);
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(save.getWindowToken(), 0);
            save.clearFocus();
            toast("规则已保存");
        });
        card.addView(save, fullWidth());
        saveFeedback = text("", 12, TEAL, false);
        saveFeedback.setPadding(0, dp(10), 0, 0);
        saveFeedback.setVisibility(View.GONE);
        card.addView(saveFeedback);
    }

    private void makeLogsCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        LinearLayout heading = row();
        heading.addView(text("05  验证记录", ui_theme.SECTION_SP, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = button("清空", false);
        clear.setTextSize(12);
        clear.setMinWidth(0);
        clear.setMinimumWidth(0);
        clear.setPadding(dp(14), dp(6), dp(14), dp(6));
        clear.setOnClickListener(view -> {
            DemoStore.clearLogs(this);
            refreshState();
            toast("本机验证记录已清空");
        });
        heading.addView(clear, new LinearLayout.LayoutParams(-2, dp(44)));
        card.addView(heading);
        addSpace(card, 8);
        logCount = text("", 12, MUTED, false);
        card.addView(logCount);
        addSpace(card, 12);
        logList = column();
        card.addView(logList, fullWidth());
    }

    private void refreshState() {
        if (permissionValue == null) return;
        boolean granted = hasNotificationAccess();
        boolean connected = FilterService.isConnected();
        boolean automatic = DemoStore.getAuto(this);
        ModelConfig modelConfig = ModelStore.load(this);
        boolean comparison = modelConfig.mode == ModelConfig.Mode.COMPARE;
        setStatus(permissionValue, granted ? "已授权" : "待开启", granted);
        setStatus(connectionValue, connected ? "已连接" : granted ? "等待连接" : "未连接", connected);
        modeValue.setText(comparison ? "对照观察" : automatic ? "自动模式" : "观察模式");
        modeValue.setTextColor(automatic ? AMBER : TEAL);
        modeValue.setBackground(background(automatic ? SOFT_AMBER : SOFT_TEAL, 30, 0));
        modeDescription.setText(comparison
                ? "多路对照只记录选定路线的判断，绝不发起清除。切换策略并保存后，需重新手动开启自动清除。"
                : !allowsAutomatic(modelConfig)
                ? "当前模型策略尚未开启远程处理，或配置存储不可用；自动清除暂不可用。"
                : automatic
                ? "已开启：命中清除规则后请求系统移除通知，并记录结果。"
                : "默认仅观察：展示保留或清除建议，不移除通知。先确认规则，再开启自动清除。" );
        updatingSwitch = true;
        autoSwitch.setChecked(automatic);
        autoSwitch.setEnabled(allowsAutomatic(modelConfig));
        updatingSwitch = false;
        String route = modelConfig.mode == ModelConfig.Mode.KEYWORDS ? "关键词 · 本机规则"
                : modelConfig.mode == ModelConfig.Mode.OFFICIAL ? "路线 1 · " + profileLabel(modelConfig.official)
                : modelConfig.mode == ModelConfig.Mode.BOCHA ? "路线 2 · " + profileLabel(modelConfig.bocha)
                : modelConfig.mode == ModelConfig.Mode.RELAY ? "路线 3 · " + profileLabel(modelConfig.relay)
                : "多路对照 · 只观察，不清除";
        String remote = modelConfig.mode == ModelConfig.Mode.KEYWORDS
                ? "当前在本机处理通知，不调用远程模型。"
                : modelConfig.remoteEnabled
                ? "远程处理已开启：目标通知的包名、标题、正文和类别可发送到您配置的服务。"
                : "远程处理已关闭：保留通知，不发送；不回退执行关键词清除。";
        AttentionStore.Config attention = AttentionStore.loadConfig(this);
        strategySummary.setText(route + "\n" + remote
                + "\n近期行为摘要：" + (attention.recentBehaviorEnabled ? "开启" : "关闭")
                + " · 独立事件上传：" + (attention.uploadEnabled ? "开启" : "关闭")
                + (modelConfig.storageError.isEmpty() ? "" : "\n密钥存储不可用，请进入配置页检查。"));
        renderLogs();
    }

    private static boolean allowsAutomatic(ModelConfig config) {
        return config.mode == ModelConfig.Mode.KEYWORDS || (config.mode != ModelConfig.Mode.COMPARE
                && config.storageError.isEmpty() && config.remoteEnabled);
    }

    private static String profileLabel(ModelConfig.Profile profile) {
        return profile.label.isEmpty() ? "未命名配置" : profile.label;
    }

    private void renderLogs() {
        JSONArray entries = DemoStore.getLogs(this);
        int count = entries.length();
        logCount.setText(count == 0 ? "最新记录显示在最上方 · 仅保存在本机"
                : "共 " + count + " 条 · 最新在上" + (count > MAX_VISIBLE_LOGS ? " · 展示最近 " + MAX_VISIBLE_LOGS + " 条" : ""));
        logList.removeAllViews();
        if (count == 0) {
            LinearLayout empty = column();
            empty.setPadding(dp(16), dp(23), dp(16), dp(23));
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(background(BACKGROUND, 12, 0));
            TextView title = text("等待第一条通知", 15, INK, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            addSpace(empty, 7);
            TextView hint = text("开启使用权，然后打开测试发送器。\n也可以重新扫描通知栏中已有的通知。", 12, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            empty.addView(hint);
            logList.addView(empty, fullWidth());
            return;
        }
        for (int i = 0; i < Math.min(count, MAX_VISIBLE_LOGS); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            LinearLayout item = column();
            item.setPadding(dp(13), dp(13), dp(13), dp(13));
            item.setBackground(background(BACKGROUND, 12, 0));
            LinearLayout top = row();
            String action = entry.optString("action", "记录");
            boolean warning = action.contains("清除") || action.contains("未确认");
            top.addView(chip(action, warning ? AMBER : TEAL, warning ? SOFT_AMBER : SOFT_TEAL));
            TextView time = text(timeFormat.format(new Date(entry.optLong("time", 0))), 11, MUTED, false);
            time.setGravity(Gravity.END);
            top.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
            item.addView(top);
            addSpace(item, 9);
            TextView source = text(entry.optString("pkg", "未知来源"), 11, MUTED, false);
            source.setTextIsSelectable(true);
            item.addView(source);
            String title = entry.optString("title", "").trim();
            String body = entry.optString("text", "").trim();
            addSpace(item, 6);
            TextView titleView = text(title.isEmpty() ? "（无标题）" : title, 15, INK, true);
            titleView.setTextIsSelectable(true);
            item.addView(titleView);
            if (!body.isEmpty()) {
                addSpace(item, 4);
                TextView bodyView = text(body, 13, INK, false);
                bodyView.setTextIsSelectable(true);
                item.addView(bodyView);
            }
            addSpace(item, 9);
            item.addView(text("原因 · " + entry.optString("reason", "无说明"), 12, MUTED, false));
            JSONArray modelResults = entry.optJSONArray("models");
            if (modelResults != null && modelResults.length() > 0) {
                renderModelResults(item, modelResults, entry.optString("comparison", ""));
            }
            logList.addView(item, fullWidth());
            if (i < Math.min(count, MAX_VISIBLE_LOGS) - 1) addSpace(logList, 10);
        }
    }

    private void renderModelResults(LinearLayout parent, JSONArray results, String comparison) {
        addSpace(parent, 12);
        if (!comparison.isEmpty()) {
            parent.addView(text("路线结论 · " + comparison, 12, INK, true));
            addSpace(parent, 8);
        }
        renderProbabilityOverview(parent, results);
        addSpace(parent, 8);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(true);
        LinearLayout columns = row();
        columns.setGravity(Gravity.TOP);
        scroll.addView(columns, new HorizontalScrollView.LayoutParams(-2, -2));
        for (int i = 0; i < Math.min(3, results.length()); i++) {
            JSONObject model = results.optJSONObject(i);
            if (model == null) continue;
            LinearLayout box = column();
            box.setPadding(dp(12), dp(12), dp(12), dp(12));
            box.setBackground(background(ui_theme.SHEET, 10, BORDER));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(218), -2);
            size.setMarginEnd(dp(8));
            columns.addView(box, size);
            box.addView(text(model.optString("label", "模型路线"), 14, INK, true));
            addSpace(box, 5);
            box.addView(text(model.optString("model", "") + "\n" + model.optString("protocol", ""), 11, MUTED, false));
            addSpace(box, 10);
            double original = model.optDouble("p_jev", Double.NaN);
            double fused = model.optDouble("p_final", Double.NaN);
            String arrow = "＝";
            int probabilityColor = MUTED;
            if (Double.isFinite(original) && Double.isFinite(fused)) {
                double difference = fused - original;
                arrow = difference > 0.05 ? "↑" : difference < -0.05 ? "↓" : "＝";
                probabilityColor = difference > 0.05 ? TEAL : difference < -0.05 ? AMBER : INK;
            }
            box.addView(text("p_jev " + probability(original) + "\n→ p_final " + probability(fused) + "  " + arrow,
                    15, probabilityColor, true));
            if (!model.optBoolean("has_probability", false) && model.optBoolean("success", false)) {
                addSpace(box, 5);
                box.addView(text("无原始概率 · 由 choice 换算", 11, AMBER, false));
            }
            addSpace(box, 10);
            String action = model.optString("action", "");
            String decision = "KEEP".equals(action) ? "保留" : "REMOVE".equals(action) ? "建议清除" : "未完成";
            boolean success = model.optBoolean("success", false);
            box.addView(text((success ? decision : "请求失败 · 保留") + " · "
                    + model.optLong("latency_ms", 0) + " ms", 12, success ? TEAL : AMBER, true));
            addSpace(box, 7);
            box.addView(text(success ? model.optString("reason", "") : model.optString("error", "无详细错误"),
                    11, MUTED, false));
        }
        parent.addView(scroll, fullWidth());
    }

    private void renderProbabilityOverview(LinearLayout parent, JSONArray results) {
        LinearLayout overview = row();
        overview.setGravity(Gravity.TOP);
        int count = Math.min(3, results.length());
        for (int i = 0; i < count; i++) {
            JSONObject model = results.optJSONObject(i);
            if (model == null) {
                continue;
            }
            LinearLayout box = column();
            box.setPadding(dp(6), dp(9), dp(6), dp(9));
            box.setBackground(background(ui_theme.SHEET, 8, BORDER));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            if (i < count - 1) {
                size.setMarginEnd(dp(4));
            }
            overview.addView(box, size);
            addOverviewLine(box, model.optString("label", "模型路线"), 12, INK, true);
            addSpace(box, 7);
            boolean success = model.optBoolean("success", false);
            boolean hasProbability = model.optBoolean("has_probability", false);
            addOverviewLine(box, success && !hasProbability ? "choice 换算" : "p_jev", 11, MUTED, false);
            double original = model.optDouble("p_jev", Double.NaN);
            double fused = model.optDouble("p_final", Double.NaN);
            addOverviewLine(box, probability(original), 12, INK, true);
            String arrow = "＝";
            int color = INK;
            if (Double.isFinite(original) && Double.isFinite(fused)) {
                double difference = fused - original;
                arrow = difference > 0.05 ? "↑" : difference < -0.05 ? "↓" : "＝";
                color = difference > 0.05 ? TEAL : difference < -0.05 ? AMBER : INK;
            }
            addSpace(box, 5);
            addOverviewLine(box, arrow + " p_final", 11, color, false);
            addOverviewLine(box, probability(fused), 12, color, true);
            addSpace(box, 7);
            addOverviewLine(box, model.optLong("latency_ms", 0) + " ms", 11, MUTED, false);
            String action = model.optString("action", "KEEP");
            addOverviewLine(box, success ? action : "失败 · KEEP", 11, success ? TEAL : AMBER, true);
        }
        parent.addView(overview, fullWidth());
    }

    private void addOverviewLine(LinearLayout parent, String value, float size, int color, boolean bold) {
        TextView line = text(value, size, color, bold);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        parent.addView(line, fullWidth());
    }

    private static String probability(double value) {
        return Double.isFinite(value) ? String.format(Locale.CHINA, "%.3f", value) : "—";
    }

    private boolean hasNotificationAccess() {
        ComponentName component = new ComponentName(this, FilterService.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) return manager.isNotificationListenerAccessGranted(component);
        }
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled != null) {
            for (String candidate : enabled.split(":")) {
                if (component.equals(ComponentName.unflattenFromString(candidate))) return true;
            }
        }
        return false;
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException error) {
            toast("请在系统设置中搜索「通知使用权」，开启通知筛选 Demo");
        }
    }

    private void openSender() {
        try {
            startActivity(new Intent().setComponent(new ComponentName(
                    "com.example.notificationdemo.sender", "com.example.notificationdemo.sender.MainActivity")));
        } catch (ActivityNotFoundException error) {
            toast("请先安装配套的「通知测试发送器」APK");
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(DemoStore.ACTION_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerLegacyStateReceiver(filter);
            receiverRegistered = true;
        }
        refreshState();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyStateReceiver(IntentFilter filter) {
        // Android 8-12 have no NOT_EXPORTED flag. This app's manifest declares and
        // requests the signature-only permission below, so only the same signing
        // identity may send this private UI invalidation broadcast on those APIs.
        // Keep this compatibility suppression local instead of disabling lint.
        registerReceiver(changes, filter, getPackageName() + ".INTERNAL_EVENTS", null, 0);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshState();
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(changes);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("targets", targets.getText().toString());
        outState.putString("keepWords", keepWords.getText().toString());
        outState.putString("blockWords", blockWords.getText().toString());
        outState.putInt("selected_page", selectedPage < 0 ? PAGE_HOME : selectedPage);
        int[] currentScroll = scrollPositions.clone();
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null && !restoreScroll[i]) {
                currentScroll[i] = pages[i].getScrollY();
            }
        }
        outState.putIntArray("page_scroll_positions", currentScroll);
        super.onSaveInstanceState(outState);
    }

    private EditText input(LinearLayout parent, String label, String hint, int lines, boolean words) {
        addSpace(parent, 16);
        parent.addView(text(label, 13, INK, true));
        addSpace(parent, 7);
        EditText field = new EditText(this);
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setTextSize(14);
        field.setHint(hint);
        field.setContentDescription(label);
        field.setMinLines(1);
        field.setMaxLines(lines + 1);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | (words ? 0 : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        field.setGravity(Gravity.TOP | Gravity.START);
        ui_theme.input(field);
        field.setSelectAllOnFocus(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        parent.addView(field, fullWidth());
        addSpace(parent, 6);
        return field;
    }

    private TextView statusRow(LinearLayout parent, String label) {
        LinearLayout line = row();
        line.addView(text(label, 14, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("", 14, TEAL, true);
        line.addView(value);
        parent.addView(line, fullWidth());
        return value;
    }

    private void setStatus(TextView value, String label, boolean good) {
        value.setText(String.format(Locale.CHINA, "%s  %s", good ? "●" : "○", label));
        value.setTextColor(good ? TEAL : MUTED);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = column();
        int index = sectionIndex++;
        ui_theme.section(card, index == 0 ? ui_theme.SOFT_BLUE
                : index == 2 ? ui_theme.SOFT_GREEN : Color.TRANSPARENT);
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(ui_theme.SECTION_GAP);
        parent.addView(card, params);
        return card;
    }

    private void sectionTitle(LinearLayout parent, String number, String title) {
        parent.addView(text(number + "  " + title, ui_theme.SECTION_SP, INK, true));
    }

    private TextView chip(String label, int foreground, int fill) {
        TextView view = text(label, 11, foreground, true);
        view.setPadding(dp(9), dp(5), dp(9), dp(5));
        view.setBackground(background(fill, 30, 0));
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        ui_theme.button(view, primary);
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return ui_theme.text(this, value, size, color, bold);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable background(int fill, int radius, int stroke) {
        return ui_theme.shape(this, fill, radius, stroke);
    }

    private void addSpace(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String normalizeEntries(String source) {
        StringBuilder result = new StringBuilder();
        for (String item : source.split("[,，;；\\r\\n]+")) {
            item = item.trim();
            if (item.isEmpty()) continue;
            if (result.length() > 0) result.append(',');
            result.append(item);
        }
        return result.toString();
    }

    private static boolean isValidTargets(String source) {
        if (source.isEmpty()) return true;
        for (String item : source.split(",")) {
            if (!item.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+")) return false;
        }
        return true;
    }
}
