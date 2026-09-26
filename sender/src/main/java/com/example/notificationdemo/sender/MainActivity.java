package com.example.notificationdemo.sender;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** A separate package which publishes real notifications for cross-app verification. */
public final class MainActivity extends Activity {
    private static final String CHANNEL = "test_messages_v1";
    private static final String GROUP = "acceptance_samples";
    private static final int REQUEST_NOTIFICATIONS = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NotificationManager notifications;
    private TextView permissionStatus;
    private TextView activeStatus;
    private TextView eventLog;
    private final ArrayList<String> logLines = new ArrayList<>();
    private String pendingScenario;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "验收测试消息", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("用于验证读取、规则匹配、保护和跨 App 清除；默认静音。");
        channel.setSound(null, null);
        channel.enableVibration(false);
        notifications.createNotificationChannel(channel);
        buildUi();
        if (savedInstanceState == null) handleDebugIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDebugIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(243, 246, 251));
        LinearLayout page = vertical();
        int horizontal = dp(20);
        page.setPadding(horizontal, dp(24), horizontal, dp(32));
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right;
            } else {
                top = windowInsets.getSystemWindowInsetTop();
                bottom = windowInsets.getSystemWindowInsetBottom();
                left = windowInsets.getSystemWindowInsetLeft();
                right = windowInsets.getSystemWindowInsetRight();
            }
            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });
        setContentView(scroll);

        TextView eyebrow = label("NOTIFICATION LAB  /  发送端", 12, 0xFF2463EB);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(eyebrow);
        TextView title = label("通知测试发送器", 29, 0xFF142238);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addMargin(page, title, 7);
        addMargin(page, label("向系统通知栏发送可控样本，验证另一个 App 是否真的能读取并清除通知。", 15, 0xFF5C6A80), 9);

        LinearLayout status = card(page, "01  发送权限与当前通知");
        permissionStatus = label("", 15, 0xFF142238);
        status.addView(permissionStatus);
        activeStatus = label("", 13, 0xFF5C6A80);
        addMargin(status, activeStatus, 8);
        button(status, "开启通知权限", () -> ensurePermission(null), false);
        button(status, "刷新当前通知", this::refreshStatus, false);

        LinearLayout batch = card(page, "02  一键验证");
        batch.addView(label("先在「通知筛选 Demo」开启通知使用权，并启用本发送器来源的自动清除。\n保留关键词：紧急、会议、重要、家人\n清除关键词：热搜、推荐、优惠、广告", 14, 0xFF5C6A80));
        button(batch, "发送一组验收样本 · 9 张", () -> runScenario("batch"), true);
        addMargin(batch, label("完整新样本为 9 张：保留 7 张、清除 2 张（102 和 202）。实际结果受筛选模式、规则和系统分组影响；请结合筛选日志核对。", 12, 0xFF5C6A80), 8);

        LinearLayout cases = card(page, "03  单项测试");
        button(cases, "101 · 普通消息 → 保留", () -> runScenario("normal"), false);
        button(cases, "102 · 推荐 / 优惠 → 清除", () -> runScenario("ad"), false);
        button(cases, "103 · 紧急 + 优惠 → 保留优先", () -> runScenario("conflict"), false);
        button(cases, "104 · 空正文 → 保留", () -> runScenario("empty"), false);
        button(cases, "105 · 持续广告通知 → 保护", () -> runScenario("ongoing"), false);
        button(cases, "200–203 · 汇总 + 3 条分组消息", () -> runScenario("group"), false);

        LinearLayout update = card(page, "04  同一通知更新");
        update.addView(label("两个按钮都使用 ID 301。先发送广告，再更新为紧急消息，验证每次更新都重新判断。", 14, 0xFF5C6A80));
        button(update, "301 · 第一步：发送广告", () -> runScenario("update_ad"), false);
        button(update, "301 · 第二步：更新为紧急消息", () -> runScenario("update_urgent"), false);

        LinearLayout log = card(page, "05  发送记录");
        button(log, "清空本发送器的全部通知", () -> runScenario("clear"), false);
        eventLog = label("尚未发送。样本不包含真实私人消息。", 12, 0xFF5C6A80);
        eventLog.setTextIsSelectable(true);
        addMargin(log, eventLog, 10);
        addMargin(page, label("测试发送器为独立 App，不读取其他 App 的通知，无网络权限。", 12, 0xFF6E7C90), 18);
    }

    private void handleDebugIntent(Intent intent) {
        if (intent == null || !intent.hasExtra("scenario")) return;
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            appendLog("忽略外部场景参数：仅 debug 构建开放自动验收入口。");
            return;
        }
        String scenario = intent.getStringExtra("scenario");
        intent.removeExtra("scenario");
        runScenario(scenario);
    }

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ensurePermission(String scenario) {
        if (!hasPermission()) {
            pendingScenario = scenario;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            appendLog("等待通知发送授权；允许后才会发送。");
            return false;
        }
        if (!notifications.areNotificationsEnabled()) {
            appendLog("系统已关闭本 App 通知，请在设置中开启。");
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            return false;
        }
        NotificationChannel channel = notifications.getNotificationChannel(CHANNEL);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            appendLog("测试通知渠道已关闭，请在设置中开启。");
            startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL));
            return false;
        }
        refreshStatus();
        return true;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        String scenario = pendingScenario;
        pendingScenario = null;
        if (hasPermission()) {
            appendLog("通知发送权限已开启。");
            if (scenario != null) runScenario(scenario);
        } else {
            appendLog("未获通知发送权限；没有发送样本。");
        }
        refreshStatus();
    }

    private void runScenario(String scenario) {
        if (scenario == null) return;
        if ("clear".equals(scenario)) {
            notifications.cancelAll();
            appendLog("已请求清除本 App 的全部测试通知。");
            scheduleRefresh();
            return;
        }
        if (!isScenario(scenario)) {
            appendLog("未知场景：" + scenario);
            return;
        }
        if (!ensurePermission(scenario)) return;
        switch (scenario) {
            case "batch":
                normal(); ad(); conflict(); empty(); ongoing(); group();
                appendLog("batch：已发送 101–105、200–203，共 9 张；覆盖同 ID 旧样本。");
                break;
            case "normal": normal(); appendLog("normal：101 普通消息，预期保留。"); break;
            case "ad": ad(); appendLog("ad：102 推荐优惠，预期清除。"); break;
            case "conflict": conflict(); appendLog("conflict：103 紧急与优惠冲突，预期保留。"); break;
            case "empty": empty(); appendLog("empty：104 没有正文，预期保留。"); break;
            case "ongoing": ongoing(); appendLog("ongoing：105 持续通知含广告，预期保护。"); break;
            case "group": group(); appendLog("group：200 汇总、201 普通、202 广告、203 重要；预期仅清除 202。"); break;
            case "update_ad":
                post(301, "更新测试｜推荐广告", "这是一条优惠推荐，等待手动更新。", false, null, false);
                appendLog("update_ad：301 广告，预期清除。"); break;
            case "update_urgent":
                post(301, "更新测试｜紧急消息", "紧急：家人发来重要消息，请及时查看。", false, null, false);
                appendLog("update_urgent：301 更新为紧急，预期保留。"); break;
            default: break;
        }
        scheduleRefresh();
    }

    private boolean isScenario(String name) {
        switch (name) {
            case "batch": case "normal": case "ad": case "conflict": case "empty":
            case "ongoing": case "group": case "update_ad": case "update_urgent": return true;
            default: return false;
        }
    }

    private void normal() { post(101, "测试｜日常消息", "今天下午一起喝杯茶。", false, null, false); }
    private void ad() { post(102, "测试｜推荐优惠", "限时优惠广告，点击查看推荐内容。", false, null, false); }
    private void conflict() { post(103, "测试｜紧急事项", "紧急：会议调整，请优先查看本条优惠提醒。", false, null, false); }
    private void empty() { post(104, "测试｜内容未提供", null, false, null, false); }
    private void ongoing() { post(105, "测试｜持续服务", "广告推荐：持续通知保护样本。", true, null, false); }
    private void group() {
        post(201, "分组｜普通消息", "周末天气不错，可以出去走走。", false, GROUP, false);
        post(202, "分组｜推荐优惠", "热搜广告推荐，今日优惠已更新。", false, GROUP, false);
        post(203, "分组｜重要会议", "重要：会议将在十分钟后开始。", false, GROUP, false);
        post(200, "测试｜消息分组", "3 条独立测试消息", false, GROUP, true);
    }

    private void post(int id, String title, String text, boolean ongoing, String group, boolean summary) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(com.example.notificationdemo.sender.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentIntent(content)
                .setColor(0xFF2463EB)
                .setAutoCancel(!ongoing)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE);
        if (text != null) builder.setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
        if (group != null) builder.setGroup(group).setGroupSummary(summary).setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
        notifications.notify(id, builder.build());
    }

    private void scheduleRefresh() {
        refreshStatus();
        handler.postDelayed(this::refreshStatus, 1500);
    }

    private void refreshStatus() {
        if (permissionStatus == null) return;
        boolean enabled = hasPermission() && notifications.areNotificationsEnabled();
        NotificationChannel channel = notifications.getNotificationChannel(CHANNEL);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) enabled = false;
        permissionStatus.setText(enabled ? "● 已允许发送测试通知" : "● 尚未允许发送测试通知");
        permissionStatus.setTextColor(enabled ? 0xFF137B5D : 0xFFB45309);
        ArrayList<Integer> ids = new ArrayList<>();
        for (StatusBarNotification item : notifications.getActiveNotifications()) {
            // Framework auto-group summaries are not one of the app's published samples.
            if (item.getId() >= 101 && item.getId() <= 301) ids.add(item.getId());
        }
        Collections.sort(ids);
        activeStatus.setText(getString(R.string.active_sample_summary, ids.size(), ids.isEmpty() ? "无" : ids.toString()));
    }

    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        logLines.add(0, time + "  " + message);
        while (logLines.size() > 24) logLines.remove(logLines.size() - 1);
        if (eventLog != null) eventLog.setText(android.text.TextUtils.join("\n\n", logLines));
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout view = vertical();
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(16));
        view.setBackground(background);
        TextView title = label(heading, 16, 0xFF142238);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(12));
        view.addView(title);
        addMargin(parent, view, 18);
        return view;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private void button(LinearLayout parent, String text, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setMinHeight(dp(48));
        button.setTextColor(primary ? Color.WHITE : 0xFF2459B8);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? 0xFF2463EB : 0xFFEDF3FF);
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        button.setOnClickListener(v -> action.run());
        addMargin(parent, button, 10);
    }

    private void addMargin(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
