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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Uses Android notification access, never accessibility or screen scraping. */
public final class FilterService extends NotificationListenerService {
    interface Classifier {
        ModelClient.Result classify(ModelConfig.Profile profile, DecisionEngine.Input input);
    }
    private static volatile Classifier classifier = ModelClient::classify;
    static void setClassifierForTests(Classifier replacement) {
        FilterService service = instance;
        if (service == null || (service.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            throw new IllegalStateException("Test hook requires connected debug service");
        }
        classifier = replacement == null ? ModelClient::classify : replacement;
    }
    private static final long CONFIRM_TIMEOUT_MS = 3500;
    private static final long REBIND_COOLDOWN_MS = 60000;
    private static volatile FilterService instance;
    private static long lastRebindAttempt = -REBIND_COOLDOWN_MS;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, PendingRemoval> pending = new LinkedHashMap<>();
    private final Map<String, ModelJob> modelJobs = new LinkedHashMap<>();
    private final ThreadPoolExecutor modelWorkers = new ThreadPoolExecutor(2, 2, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(24));
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
        abandonModels();
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
        abandonModels();
        modelWorkers.shutdownNow();
        main.removeCallbacksAndMessages(null);
        DemoStore.notifyChanged(this);
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && connected) process(sbn);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        if (sbn == null) return;
        ModelJob job = modelJobs.get(sbn.getKey());
        if (job != null && job.sbn.getPostTime() == sbn.getPostTime()) {
            job.obsolete = true;
            modelJobs.remove(sbn.getKey());
        }
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
        ModelJob oldJob = modelJobs.get(sbn.getKey());
        if (oldJob != null && oldJob.sbn.getPostTime() != sbn.getPostTime()) {
            oldJob.obsolete = true;
            modelJobs.remove(sbn.getKey());
        }
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
        ModelConfig config = ModelStore.load(this);
        if (config.mode != ModelConfig.Mode.KEYWORDS) {
            processModel(sbn, input, rules, config);
            return;
        }
        DecisionEngine.Result result = DecisionEngine.decide(input, rules);
        if (result.action != DecisionEngine.Action.REMOVE) {
            DemoStore.addLog(this, input.packageName, title, text,
                    result.action == DecisionEngine.Action.SKIP ? "跳过" : "保留", result.reason, sbn.getKey());
            return;
        }
        removeIfAllowed(sbn, input, result.reason);
    }

    private void removeIfAllowed(StatusBarNotification sbn, DecisionEngine.Input input, String reason) {
        ModelConfig config = ModelStore.load(this);
        if (!DemoStore.getAuto(this) || config.mode == ModelConfig.Mode.COMPARE) {
            DemoStore.addLog(this, input.packageName, input.title, input.text, "建议清除",
                    reason + "；当前为观察模式，未执行清除", sbn.getKey());
            return;
        }
        if (pending.containsKey(sbn.getKey())) return;
        // A queued post callback or manual scan can already be stale. Avoid acting on an
        // older version when the system now exposes an updated notification under this key.
        try {
            StatusBarNotification[] current = getActiveNotifications(new String[]{sbn.getKey()});
            if (current == null || current.length == 0) return;
            if (!sameNotification(current[0], sbn, input)) {
                DemoStore.addLog(this, input.packageName, input.title, input.text, "跳过",
                        "通知已更新，本次旧版本判断作废；等待新通知回调或再次扫描", sbn.getKey());
                return;
            }
        } catch (RuntimeException e) {
            DemoStore.addLog(this, input.packageName, input.title, input.text, "未确认",
                    "清除前无法复查当前通知，已保留（" + e.getClass().getSimpleName() + "）", sbn.getKey());
            return;
        }
        PendingRemoval request = new PendingRemoval(sbn, input);
        pending.put(request.key, request);
        log(request, "请求清除", reason + "；等待系统移除回调确认");
        main.postDelayed(request.timeout, CONFIRM_TIMEOUT_MS);
        try {
            cancelNotification(request.key);
        } catch (RuntimeException e) {
            pending.remove(request.key);
            main.removeCallbacks(request.timeout);
            log(request, "未确认", "系统未接受清除请求（" + e.getClass().getSimpleName() + "）");
        }
    }

    private void processModel(StatusBarNotification sbn, DecisionEngine.Input input,
                              DecisionEngine.Rules rules, ModelConfig config) {
        DecisionEngine.Result protection = DecisionEngine.protect(input, rules);
        if (protection != null) {
            modelLog(sbn, input, protection.action == DecisionEngine.Action.SKIP ? "跳过" : "保留",
                    protection.reason + "；未发送给模型");
            return;
        }
        if (input.text.trim().isEmpty()) {
            modelLog(sbn, input, "保留", "没有可读正文，未发送给模型");
            return;
        }
        if (!config.remoteEnabled || !config.storageError.isEmpty()) {
            modelLog(sbn, input, "保留", "远程判断未启用或配置不可用，未发送通知；不会回退执行关键词清除");
            return;
        }
        ModelJob existing = modelJobs.get(sbn.getKey());
        if (existing != null && sameNotification(sbn, existing.sbn, existing.input)
                && existing.config.revision == config.revision
                && existing.ruleRevision == DemoStore.getDecisionRevision(this)) return;
        if (existing != null) existing.obsolete = true;
        ModelJob job = new ModelJob(sbn, input, config);
        modelJobs.put(sbn.getKey(), job);
        try {
            modelWorkers.execute(() -> {
                if (!job.isValid()) { main.post(() -> finishModel(job, null, null)); return; }
                ModelClient.Result official = null;
                ModelClient.Result relay = null;
                if (config.mode == ModelConfig.Mode.OFFICIAL || config.mode == ModelConfig.Mode.COMPARE) {
                    official = classifier.classify(config.official, input);
                }
                if (job.isValid() && (config.mode == ModelConfig.Mode.RELAY || config.mode == ModelConfig.Mode.COMPARE)) {
                    relay = classifier.classify(config.relay, input);
                }
                ModelClient.Result finalOfficial = official;
                ModelClient.Result finalRelay = relay;
                main.post(() -> finishModel(job, finalOfficial, finalRelay));
            });
        } catch (RejectedExecutionException full) {
            modelJobs.remove(sbn.getKey());
            job.obsolete = true;
            modelLog(sbn, input, "保留", "模型请求队列已满，本条保留；可稍后手动扫描");
        }
    }

    private void finishModel(ModelJob job, ModelClient.Result official, ModelClient.Result relay) {
        boolean valid = job.isValid() && modelJobs.get(job.sbn.getKey()) == job;
        if (modelJobs.get(job.sbn.getKey()) == job) modelJobs.remove(job.sbn.getKey());
        if (!valid) {
            modelLog(job.sbn, job.input, "结果作废", "通知、策略、自动开关或监听连接已变化，本次模型结果不执行清除");
            return;
        }
        try {
            StatusBarNotification[] active = getActiveNotifications(new String[]{job.sbn.getKey()});
            if (active == null || active.length == 0 || !sameNotification(active[0], job.sbn, job.input)) {
                modelLog(job.sbn, job.input, "结果作废", "通知已移除或内容已更新，本次模型结果不执行清除");
                return;
            }
        } catch (RuntimeException ignored) {
            modelLog(job.sbn, job.input, "保留", "模型返回后无法复查系统通知，默认保留");
            return;
        }
        if (job.config.mode == ModelConfig.Mode.COMPARE) {
            String officialText = describeModel("官方", job.config.official, official);
            String relayText = describeModel("中转", job.config.relay, relay);
            boolean comparable = official != null && relay != null && official.success && relay.success;
            String comparison = !comparable ? "存在失败，本次不能比较" : official.action == relay.action ? "两路结论一致" : "两路结论不同";
            modelLog(job.sbn, job.input, "模型对照", officialText + "\n" + relayText + "\n" + comparison + "；对照模式只观察");
            return;
        }
        boolean isOfficial = job.config.mode == ModelConfig.Mode.OFFICIAL;
        ModelClient.Result result = isOfficial ? official : relay;
        String reason = describeModel(isOfficial ? "官方" : "中转", isOfficial ? job.config.official : job.config.relay, result);
        if (result != null && result.success && result.action == DecisionEngine.Action.REMOVE) {
            removeIfAllowed(job.sbn, job.input, reason);
        } else {
            modelLog(job.sbn, job.input, "保留", reason);
        }
    }

    private static String describeModel(String strategy, ModelConfig.Profile profile, ModelClient.Result result) {
        String identity = strategy + " / " + shortText(redact(profile.label, profile.apiKey), 24)
                + " / " + shortText(redact(profile.model, profile.apiKey), 36);
        if (result == null) return identity + "：未完成，保留";
        return identity + " · " + result.latencyMs + "ms · "
                + (result.success ? result.action.name() + "：" + shortText(result.reason, 80)
                : "失败→保留（" + shortText(result.error, 64) + "）");
    }

    private static String redact(String text, String key) {
        return key == null || key.isEmpty() ? text : text.replace(key, "[密钥已隐藏]");
    }

    private static String shortText(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }

    private boolean sameNotification(StatusBarNotification current, StatusBarNotification original,
                                     DecisionEngine.Input input) {
        if (current.getPostTime() != original.getPostTime()) return false;
        Notification notification = current.getNotification();
        if (notification == null) return false;
        try {
            Bundle extras = notification.extras;
            return input.title.equals(extras == null ? "" : string(extras.getCharSequence(Notification.EXTRA_TITLE)))
                    && input.text.equals(extractText(extras))
                    && input.ongoing == current.isOngoing() && input.clearable == current.isClearable()
                    && input.groupSummary == ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
                    && input.category.equals(string(notification.category));
        } catch (RuntimeException ignored) { return false; }
    }

    private void modelLog(StatusBarNotification sbn, DecisionEngine.Input input, String action, String reason) {
        DemoStore.addLog(this, input.packageName, input.title, input.text, action, reason, sbn.getKey());
    }

    private void abandonModels() {
        for (ModelJob job : modelJobs.values()) job.obsolete = true;
        modelJobs.clear();
        modelWorkers.getQueue().clear();
    }

    private final class ModelJob {
        final StatusBarNotification sbn;
        final DecisionEngine.Input input;
        final ModelConfig config;
        final long ruleRevision;
        volatile boolean obsolete;
        ModelJob(StatusBarNotification sbn, DecisionEngine.Input input, ModelConfig config) {
            this.sbn = sbn;
            this.input = input;
            this.config = config;
            this.ruleRevision = DemoStore.getDecisionRevision(FilterService.this);
        }
        boolean isValid() {
            return !obsolete && connected && config.revision == ModelStore.getRevision(FilterService.this)
                    && ruleRevision == DemoStore.getDecisionRevision(FilterService.this);
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
