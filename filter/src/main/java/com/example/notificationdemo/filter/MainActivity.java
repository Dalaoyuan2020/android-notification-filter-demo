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
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.Locale;

/** Small, dependency-free UI for testing the system notification listener. */
public final class MainActivity extends Activity {
    private static final int INK = ui_theme.INK;
    private static final int MUTED = ui_theme.MUTED;
    private static final int TEAL = ui_theme.ACCENT;
    private static final int BACKGROUND = ui_theme.PAPER;
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
    private String messageFilter = notification_ui_data.FILTER_ALL;
    private home_page_view homePage;
    private messages_page_view messagesPage;
    private judge_page_view judgePage;

    private TextView permissionValue;
    private TextView connectionValue;
    private TextView saveFeedback;
    private Switch autoSwitch;
    private EditText targets;
    private EditText keepWords;
    private EditText blockWords;
    private boolean receiverRegistered;
    private boolean updatingSwitch;
    private int sectionIndex;
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
        FrameLayout.LayoutParams hostPosition = new FrameLayout.LayoutParams(-1, -1);
        hostPosition.bottomMargin = dp(98); // Default 76dp bar + 14dp bottom margin + 8dp gap.
        root.addView(pageHost, hostPosition);
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
        LinearLayout content = buildPage(PAGE_HOME, "", "");
        content.setPadding(dp(ui_theme.PAGE_MARGIN), dp(20), dp(ui_theme.PAGE_MARGIN), dp(24));
        homePage = new home_page_view(this);
        homePage.setServiceAction(view -> {
            switchPage(PAGE_PROFILE, true);
            pages[PAGE_PROFILE].post(() -> pages[PAGE_PROFILE].scrollTo(0, 0));
        });
        homePage.setFolderAction((folder, filter) -> openMessages(filter));
        content.addView(homePage, fullWidth());
    }

    private void buildMessagesPage() {
        LinearLayout content = buildPage(PAGE_MESSAGES, "", "");
        messagesPage = new messages_page_view(this);
        messagesPage.setOnFilterSelectedListener(this::openMessages);
        messagesPage.setOnClearLogsListener(view -> {
            DemoStore.clearLogs(this);
            refreshState();
            toast("本机验证记录与短时注意力记录已清空");
        });
        content.addView(messagesPage, fullWidth());
    }

    private void buildIntelligencePage() {
        LinearLayout content = buildPage(PAGE_INTELLIGENCE, "", "");
        judgePage = new judge_page_view(this);
        judgePage.setModelAction((note, action) -> startActivity(new Intent(this, ModelSettingsActivity.class)));
        judgePage.setAttentionAction((note, action) -> startActivity(new Intent(this, AttentionActivity.class)));
        autoSwitch = judgePage.getAutoSwitch();
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
        content.addView(judgePage, fullWidth());
    }

    private void buildProfilePage() {
        LinearLayout content = buildPage(PAGE_PROFILE, "我的", "管理通知权限、处理范围与关键词规则。");
        makeStatusCard(content);
        makeRulesCard(content);
        addPrivacyNote(content);
    }

    private LinearLayout buildPage(int index, String title, String description) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setSaveEnabled(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
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
        if (!title.isEmpty()) {
            TextView heading = text(title, ui_theme.TITLE_SP, INK, true);
            if (Build.VERSION.SDK_INT >= 28) {
                heading.setAccessibilityHeading(true);
            }
            content.addView(heading);
            addSpace(content, 8);
            content.addView(text(description, 14, MUTED, false));
            addSpace(content, 26);
        }
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
            // Reserve the actual viewport, so accessibility/focus scrolling cannot target
            // content hidden behind the floating bar. Root padding already handles insets.
            FrameLayout.LayoutParams barPosition = (FrameLayout.LayoutParams) bottomNavigation.getLayoutParams();
            int reserved = bottomNavigation.getHeight() + barPosition.bottomMargin + dp(8);
            FrameLayout.LayoutParams hostPosition = (FrameLayout.LayoutParams) pageHost.getLayoutParams();
            if (hostPosition.bottomMargin != reserved) {
                hostPosition.bottomMargin = reserved;
                pageHost.setLayoutParams(hostPosition);
            }
        });
    }

    private void readPageState(Bundle state) {
        int[] savedPositions = state == null ? null : state.getIntArray("page_scroll_positions");
        String savedFilter = state == null ? null : state.getString("message_filter");
        if (notification_ui_data.FILTER_IMPORTANT.equals(savedFilter)
                || notification_ui_data.FILTER_LATER.equals(savedFilter)
                || notification_ui_data.FILTER_FILTERED.equals(savedFilter)) {
            messageFilter = savedFilter;
        }
        for (int i = 0; i < pages.length; i++) {
            scrollPositions[i] = savedPositions != null && i < savedPositions.length ? Math.max(0, savedPositions[i]) : 0;
            restoreScroll[i] = true;
        }
    }

    private void openMessages(String filter) {
        messageFilter = notification_ui_data.normalizeFilter(filter);
        messagesPage.render(DemoStore.getLogs(this), messageFilter);
        switchPage(PAGE_MESSAGES, true);
        scrollPositions[PAGE_MESSAGES] = 0;
        pages[PAGE_MESSAGES].post(() -> pages[PAGE_MESSAGES].scrollTo(0, 0));
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

    private void refreshState() {
        if (permissionValue == null) return;
        boolean granted = hasNotificationAccess();
        boolean connected = FilterService.isConnected();
        boolean automatic = DemoStore.getAuto(this);
        ModelConfig modelConfig = ModelStore.load(this);
        setStatus(permissionValue, granted ? "已授权" : "待开启", granted);
        setStatus(connectionValue, connected ? "已连接" : granted ? "等待连接" : "未连接", connected);
        AttentionStore.Config attention = AttentionStore.loadConfig(this);
        boolean allowed = allowsAutomatic(modelConfig);
        updatingSwitch = true;
        judgePage.update(modelConfig, attention, automatic && allowed, allowed);
        updatingSwitch = false;
        JSONArray entries = DemoStore.getLogs(this);
        long now = System.currentTimeMillis();
        homePage.update(granted, connected, notification_ui_data.snapshot(entries, now), now);
        messagesPage.render(entries, messageFilter);
    }

    private static boolean allowsAutomatic(ModelConfig config) {
        return config.mode == ModelConfig.Mode.KEYWORDS || (config.mode != ModelConfig.Mode.COMPARE
                && config.storageError.isEmpty() && config.remoteEnabled);
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
        outState.putString("message_filter", messageFilter);
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
