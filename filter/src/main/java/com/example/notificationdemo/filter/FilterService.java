package com.example.notificationdemo.filter;

import android.app.Notification;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses Android notification access, never accessibility or screen scraping. */
public final class FilterService extends NotificationListenerService {
    private static final long CONFIRM_TIMEOUT_MS = 3500;
    private static final long REBIND_COOLDOWN_MS = 60000;
    private static volatile FilterService instance;
    private static long lastRebindAttempt = -REBIND_COOLDOWN_MS;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, PendingRemoval> pending = new LinkedHashMap<>();
    private volatile boolean connected;
    private boolean rebindAttempted;

    public static boolean isConnected() {
        FilterService service = instance;
        return service != null && service.connected;
    }

    /** Returns whether a scan could be queued. Every scan follows current observe/auto mode. */
    public static boolean scanExisting() {
        FilterService service = instance;
        if (service == null || !service.connected) return false;
        return service.main.post(service::scanActive);
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        instance = this;
        DemoStore.notifyChanged(this);
        scanActive();
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        if (instance == this) instance = null;
        abandonPending("监听连接已断开，无法确认系统清除结果");
        DemoStore.notifyChanged(this);
        // One attempt per service lifetime, plus a process-wide cooldown across recreations.
        long now = SystemClock.elapsedRealtime();
        if (!rebindAttempted && now - lastRebindAttempt >= REBIND_COOLDOWN_MS) {
            rebindAttempted = true;
            lastRebindAttempt = now;
            try {
                requestRebind(new ComponentName(this, FilterService.class));
            } catch (RuntimeException ignored) {
                // Access may have been revoked. The UI continues to show disconnected.
            }
        }
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        connected = false;
        if (instance == this) instance = null;
        abandonPending("监听服务已停止，无法确认系统清除结果");
        main.removeCallbacksAndMessages(null);
        DemoStore.notifyChanged(this);
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && connected) process(sbn);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        if (sbn == null) return;
        PendingRemoval request = pending.get(sbn.getKey());
        if (request == null || request.postTime != sbn.getPostTime()) return;
        pending.remove(sbn.getKey());
        main.removeCallbacks(request.timeout);
        if (reason == REASON_LISTENER_CANCEL) {
            log(request, "已清除", "匹配待确认请求，系统移除回调原因为通知监听器清除（reason=" + reason + "）");
        } else {
            log(request, "未确认", "通知已移除，但系统原因为 " + reason + "，不能认定由本次请求清除");
        }
    }

    private void scanActive() {
        if (!connected) return;
        try {
            StatusBarNotification[] notifications = getActiveNotifications();
            if (notifications != null) {
                for (StatusBarNotification sbn : notifications) process(sbn);
            }
        } catch (RuntimeException e) {
            DemoStore.addLog(this, getPackageName(), "扫描未完成", "", "未确认",
                    "无法读取当前通知，请检查通知使用权及服务连接（" + e.getClass().getSimpleName() + "）", "");
        }
        DemoStore.notifyChanged(this);
    }

    private void process(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        String title;
        String text;
        try {
            Bundle extras = notification.extras;
            title = extras == null ? "" : string(extras.getCharSequence(Notification.EXTRA_TITLE));
            text = extractText(extras);
            if (Build.VERSION.SDK_INT < 30 && extras != null
                    && extras.getParcelableArray(Notification.EXTRA_MESSAGES) != null) {
                DemoStore.addLog(this, sbn.getPackageName(), title, text, "保留",
                        "Android 8–10 的结构化多消息通知保守保留，避免仅凭折叠正文误清除", sbn.getKey());
                return;
            }
        } catch (RuntimeException e) {
            DemoStore.addLog(this, sbn.getPackageName(), "内容无法解析", "", "保留",
                    "通知数据不完整，默认保留（" + e.getClass().getSimpleName() + "）", sbn.getKey());
            return;
        }
        DecisionEngine.Input input = new DecisionEngine.Input(sbn.getPackageName(), title, text,
                sbn.isOngoing(), sbn.isClearable(),
                (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0, notification.category);
        DecisionEngine.Rules rules = new DecisionEngine.Rules(DemoStore.getTargets(this),
                DemoStore.getKeepWords(this), DemoStore.getBlockWords(this));
        DecisionEngine.Result result = DecisionEngine.decide(input, rules);
        if (result.action != DecisionEngine.Action.REMOVE) {
            DemoStore.addLog(this, input.packageName, title, text,
                    result.action == DecisionEngine.Action.SKIP ? "跳过" : "保留", result.reason, sbn.getKey());
            return;
        }
        if (!DemoStore.getAuto(this)) {
            DemoStore.addLog(this, input.packageName, title, text, "建议清除",
                    result.reason + "；当前为观察模式，未执行清除", sbn.getKey());
            return;
        }
        if (pending.containsKey(sbn.getKey())) return;
        // A queued post callback or manual scan can already be stale. Avoid acting on an
        // older version when the system now exposes an updated notification under this key.
        try {
            StatusBarNotification[] current = getActiveNotifications(new String[]{sbn.getKey()});
            if (current == null || current.length == 0) return;
            if (current[0].getPostTime() != sbn.getPostTime()) {
                DemoStore.addLog(this, input.packageName, title, text, "跳过",
                        "通知已更新，本次旧版本判断作废；等待新通知回调或再次扫描", sbn.getKey());
                return;
            }
        } catch (RuntimeException e) {
            DemoStore.addLog(this, input.packageName, title, text, "未确认",
                    "清除前无法复查当前通知，已保留（" + e.getClass().getSimpleName() + "）", sbn.getKey());
            return;
        }
        PendingRemoval request = new PendingRemoval(sbn, input);
        pending.put(request.key, request);
        log(request, "请求清除", result.reason + "；等待系统移除回调确认");
        main.postDelayed(request.timeout, CONFIRM_TIMEOUT_MS);
        try {
            cancelNotification(request.key);
        } catch (RuntimeException e) {
            pending.remove(request.key);
            main.removeCallbacks(request.timeout);
            log(request, "未确认", "系统未接受清除请求（" + e.getClass().getSimpleName() + "）");
        }
    }

    private String extractText(Bundle extras) {
        if (extras == null) return "";
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        add(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) add(parts, line);
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && Build.VERSION.SDK_INT >= 30) {
            for (Notification.MessagingStyle.Message message :
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)) {
                add(parts, message.getSender());
                add(parts, message.getText());
            }
        }
        return String.join("\n", parts);
    }

    private static void add(LinkedHashSet<String> parts, CharSequence value) {
        if (value != null && value.length() > 0) parts.add(value.toString());
    }
    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }

    private void confirmTimeout(PendingRemoval request) {
        if (pending.get(request.key) != request) return;
        pending.remove(request.key);
        if (!connected) {
            log(request, "未确认", "监听连接不可用，未收到系统移除确认");
            return;
        }
        try {
            StatusBarNotification[] remaining = getActiveNotifications(new String[]{request.key});
            if (remaining != null && remaining.length > 0) {
                log(request, "未确认", "请求后通知仍存在，可能受系统保护或被来源应用重新发布");
            } else {
                log(request, "未确认", "通知已不在列表，但未收到匹配的监听器移除原因，不能归因于本次请求");
            }
        } catch (RuntimeException e) {
            log(request, "未确认", "无法复查系统通知（" + e.getClass().getSimpleName() + "）");
        }
    }

    private void abandonPending(String reason) {
        for (PendingRemoval request : new ArrayList<>(pending.values())) {
            main.removeCallbacks(request.timeout);
            log(request, "未确认", reason);
        }
        pending.clear();
    }

    private void log(PendingRemoval request, String action, String reason) {
        DemoStore.addLog(this, request.input.packageName, request.input.title,
                request.input.text, action, reason, request.key);
    }

    private final class PendingRemoval {
        final String key;
        final long postTime;
        final DecisionEngine.Input input;
        final Runnable timeout;

        PendingRemoval(StatusBarNotification sbn, DecisionEngine.Input input) {
            key = sbn.getKey();
            postTime = sbn.getPostTime();
            this.input = input;
            timeout = () -> confirmTimeout(this);
        }
    }
}
