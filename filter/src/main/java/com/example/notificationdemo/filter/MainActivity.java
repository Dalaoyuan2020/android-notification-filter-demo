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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small, dependency-free UI for testing the system notification listener. */
public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 45, 67);
    private static final int MUTED = Color.rgb(97, 113, 129);
    private static final int TEAL = Color.rgb(0, 115, 114);
    private static final int BACKGROUND = Color.rgb(244, 247, 250);
    private static final int BORDER = Color.rgb(223, 231, 237);
    private static final int SOFT_TEAL = Color.rgb(230, 246, 241);
    private static final int AMBER = Color.rgb(150, 89, 15);
    private static final int SOFT_AMBER = Color.rgb(255, 246, 226);
    private static final int MAX_VISIBLE_LOGS = 80;

    private TextView permissionValue;
    private TextView connectionValue;
    private TextView modeValue;
    private TextView modeDescription;
    private TextView logCount;
    private TextView saveFeedback;
    private Switch autoSwitch;
    private EditText targets;
    private EditText keepWords;
    private EditText blockWords;
    private LinearLayout logList;
    private boolean receiverRegistered;
    private boolean updatingSwitch;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
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

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        FrameLayout contentFrame = new FrameLayout(this);
        scroll.addView(contentFrame, new ScrollView.LayoutParams(-1, -2));
        LinearLayout column = column();
        column.setPadding(dp(20), dp(26), dp(20), dp(28));
        int availableWidth = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                availableWidth > dp(720) ? dp(720) : -1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        contentFrame.addView(column, columnParams);

        TextView eyebrow = text("ANDROID  /  本机规则验证", 11, TEAL, true);
        eyebrow.setLetterSpacing(0.09f);
        column.addView(eyebrow);
        addSpace(column, 8);
        column.addView(text("通知筛选", 32, INK, true));
        addSpace(column, 7);
        column.addView(text("留下重要消息，让通知栏清爽一点。", 14, MUTED, false));
        addSpace(column, 24);

        makeStatusCard(column);
        makeModeCard(column);
        makeRulesCard(column);
        makeLogsCard(column);
        addSpace(column, 2);
        TextView privacy = text("本机处理 · 无网络权限\n仅处理通知卡片，不删除原 App 内的消息。", 12, MUTED, false);
        privacy.setGravity(Gravity.CENTER);
        column.addView(privacy);

        targets.setText(savedInstanceState == null ? DemoStore.getTargets(this)
                : savedInstanceState.getString("targets", DemoStore.getTargets(this)));
        keepWords.setText(savedInstanceState == null ? DemoStore.getKeepWords(this)
                : savedInstanceState.getString("keepWords", DemoStore.getKeepWords(this)));
        blockWords.setText(savedInstanceState == null ? DemoStore.getBlockWords(this)
                : savedInstanceState.getString("blockWords", DemoStore.getBlockWords(this)));
        setContentView(root);
        root.requestApplyInsets();
        refreshState();
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
                toast("已请求扫描，结果将出现在下方记录中");
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
        TextView title = text("02  处理方式", 17, INK, true);
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
        autoSwitch.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{TEAL, Color.WHITE}));
        autoSwitch.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.rgb(144, 203, 190), Color.rgb(185, 197, 206)}));
        autoSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (updatingSwitch) return;
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

    private void makeRulesCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        sectionTitle(card, "03", "关键词规则");
        addSpace(card, 6);
        card.addView(text("修改后点击保存，自动清除仅作用于这些 App。", 13, MUTED, false));
        targets = input(card, "目标 App 包名", "com.sina.weibo,com.example.notificationdemo.sender", 2, false);
        card.addView(text("默认包含微博和测试发送器；微信不在处理范围内。", 12, MUTED, false));
        keepWords = input(card, "保留关键词 · 优先匹配", "紧急,会议,重要,家人", 2, true);
        blockWords = input(card, "清除关键词", "热搜,推荐,优惠,广告", 2, true);
        card.addView(text("多个值用逗号或换行分隔。标题和正文包含任一关键词即命中；保留优先，未命中默认保留，持续通知跳过。", 12, MUTED, false));
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
        heading.addView(text("04  验证记录", 17, INK, true),
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
        setStatus(permissionValue, granted ? "已授权" : "待开启", granted);
        setStatus(connectionValue, connected ? "已连接" : granted ? "等待连接" : "未连接", connected);
        modeValue.setText(automatic ? "自动模式" : "观察模式");
        modeValue.setTextColor(automatic ? AMBER : TEAL);
        modeValue.setBackground(background(automatic ? SOFT_AMBER : SOFT_TEAL, 30, 0));
        modeDescription.setText(automatic
                ? "已开启：命中清除规则后请求系统移除通知，并记录结果。"
                : "默认仅观察：展示保留或清除建议，不移除通知。先确认规则，再开启自动清除。" );
        updatingSwitch = true;
        autoSwitch.setChecked(automatic);
        updatingSwitch = false;
        renderLogs();
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
            logList.addView(item, fullWidth());
            if (i < Math.min(count, MAX_VISIBLE_LOGS) - 1) addSpace(logList, 10);
        }
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
        super.onSaveInstanceState(outState);
    }

    private EditText input(LinearLayout parent, String label, String hint, int lines, boolean words) {
        addSpace(parent, 16);
        parent.addView(text(label, 13, INK, true));
        addSpace(parent, 7);
        EditText field = new EditText(this);
        field.setTextColor(INK);
        field.setHintTextColor(Color.rgb(133, 147, 158));
        field.setTextSize(14);
        field.setHint(hint);
        field.setContentDescription(label);
        field.setMinLines(1);
        field.setMaxLines(lines + 1);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | (words ? 0 : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setPadding(dp(12), dp(12), dp(12), dp(12));
        field.setBackground(background(BACKGROUND, 10, BORDER));
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
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(background(Color.WHITE, 18, BORDER));
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(16);
        parent.addView(card, params);
        return card;
    }

    private void sectionTitle(LinearLayout parent, String number, String title) {
        parent.addView(text(number + "  " + title, 17, INK, true));
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
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(primary ? Color.WHITE : TEAL);
        view.setBackground(background(primary ? TEAL : SOFT_TEAL, 11, 0));
        view.setBackgroundTintList(null);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setStateListAnimator(null);
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
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
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
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
