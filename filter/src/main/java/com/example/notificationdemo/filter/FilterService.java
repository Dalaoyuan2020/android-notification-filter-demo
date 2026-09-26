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
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Uses Android notification access, never accessibility or screen scraping. */
public final class FilterService extends NotificationListenerService {
    interface Classifier {
        ModelClient.Result classify(ModelConfig.Profile profile, DecisionEngine.Input input);
    }
    private static volatile Classifier classifier;
    interface RichClassifier {
        ModelClient.Result classify(ModelConfig.Profile profile, DecisionEngine.Input input,
                String appName, String recentBehavior, double threshold);
    }
    private static volatile RichClassifier richClassifier;
    static void setRichClassifierForTests(RichClassifier replacement) {
        requireDebugService();
        richClassifier = replacement;
    }
    private static void requireDebugService() {
        FilterService service = instance;
        if (service == null || (service.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            throw new IllegalStateException("Test hook requires connected debug service");
        }
    }
    public static void clearAttentionTracking() {
        FilterService service = instance;
        if (service == null) return;
        Runnable clear = () -> { if (service.attention != null) service.attention.clear(); };
        if (Looper.myLooper() == Looper.getMainLooper()) clear.run(); else service.main.post(clear);
    }
    static void sweepAttentionForTests(long elapsedOffsetMs) {
        requireDebugService();
        if (elapsedOffsetMs < 0 || elapsedOffsetMs > AttentionTracker.IGNORE_AFTER_MS + 1000) {
            throw new IllegalArgumentException("Invalid test clock offset");
        }
        instance.attention.sweep(System.currentTimeMillis() + elapsedOffsetMs);
    }
    static void setClassifierForTests(Classifier replacement) {
        FilterService service = instance;
        if (service == null || (service.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            throw new IllegalStateException("Test hook requires connected debug service");
        }
        classifier = replacement;
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
    private AttentionTracker attention;
    private final Runnable attentionSweep = new Runnable() {
        @Override public void run() {
            if (!connected || attention == null) return;
            attention.sweep(System.currentTimeMillis());
            main.postDelayed(this, 60000);
        }
    };
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
        attention = new AttentionTracker(this);
        main.removeCallbacks(attentionSweep);
        main.post(attentionSweep);
        DemoStore.notifyChanged(this);
        scanActive();
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        if (instance == this) instance = null;
        abandonPending("监听连接已断开，无法确认系统清除结果");
        abandonModels();
        main.removeCallbacks(attentionSweep);
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
        if (attention != null) attention.removed(sbn, reason);
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
            if (attention != null) { attention.reconcile(notifications); attention.sweep(System.currentTimeMillis()); }
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
        if (attention != null) attention.posted(sbn, input);
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
                List<ModelClient.Result> results = new ArrayList<>();
                for (ModelConfig.Profile profile : job.profiles) {
                    if (!job.isValid()) break;
                    RichClassifier rich = richClassifier;
                    Classifier legacy = classifier;
                    ModelClient.Result result = rich != null
                            ? rich.classify(profile, input, job.appName, job.recentBehavior, config.threshold)
                            : legacy != null ? legacy.classify(profile, input)
                            : ModelClient.classify(profile, input, job.appName, job.recentBehavior, config.threshold);
                    results.add(result);
                }
                main.post(() -> finishModel(job, results));
            });
        } catch (RejectedExecutionException full) {
            modelJobs.remove(sbn.getKey());
            job.obsolete = true;
            modelLog(sbn, input, "保留", "模型请求队列已满，本条保留；可稍后手动扫描");
        }
    }

    private void finishModel(ModelJob job, List<ModelClient.Result> results) {
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
        JSONArray models = new JSONArray();
        ShortTermMemory.Entry shortMemory = AttentionStore.get(this, job.input.packageName, job.input.title, job.appName);
        AttentionStore.Config attentionConfig = AttentionStore.loadConfig(this);
        boolean allSuccess = !job.profiles.isEmpty() && results.size() == job.profiles.size();
        Boolean firstKeep = null;
        boolean agreement = true;
        boolean selectedKeep = true;
        StringBuilder reasons = new StringBuilder();
        for (int i = 0; i < job.profiles.size(); i++) {
            ModelConfig.Profile profile = job.profiles.get(i);
            ModelClient.Result result = i < results.size() ? results.get(i) : null;
            boolean success = result != null && result.success && Double.isFinite(result.probability);
            allSuccess &= success;
            double pJev = success ? result.probability : Double.NaN;
            double pFinal = success ? AttentionMath.fuse(pJev, shortMemory.alpha, shortMemory.beta, attentionConfig.weight) : Double.NaN;
            boolean keep = !success || pFinal >= job.config.threshold;
            if (i == 0) {
                selectedKeep = keep;
                if (attention != null) attention.prediction(job.sbn.getKey(), pJev, pFinal);
            }
            if (firstKeep == null) firstKeep = keep;
            else if (firstKeep != keep) agreement = false;
            try {
                models.put(new JSONObject().put("label", redact(profile.label, profile.apiKey))
                        .put("model", redact(profile.model, profile.apiKey)).put("protocol", profile.protocol.name())
                        .put("p_jev", success ? pJev : JSONObject.NULL).put("p_final", success ? pFinal : JSONObject.NULL)
                        .put("has_probability", success && result.hasProbability)
                        .put("latency_ms", result == null ? 0 : result.latencyMs)
                        .put("action", keep ? "KEEP" : "REMOVE").put("success", success)
                        .put("error", result == null ? "INCOMPLETE" : result.error)
                        .put("reason", result == null ? "请求未完成" : result.reason)
                        .put("p_short", shortMemory.pShort).put("n", shortMemory.effectiveCount));
            } catch (JSONException invalid) { allSuccess = false; selectedKeep = true; }
            if (reasons.length() > 0) reasons.append("；");
            reasons.append(describeModel("", profile, result));
        }
        if (job.config.mode == ModelConfig.Mode.COMPARE) {
            String comparison = !allSuccess ? "存在失败" : agreement ? "一致" : "不同";
            DemoStore.addModelLog(this, job.input, "模型对照", "融合后结论" + comparison + "；对照只观察",
                    job.sbn.getKey(), models, comparison);
            return;
        }
        String reason = reasons.toString();
        DemoStore.addModelLog(this, job.input, selectedKeep || !allSuccess ? "保留" : "建议清除",
                reason, job.sbn.getKey(), models, null);
        if (allSuccess && !selectedKeep) removeIfAllowed(job.sbn, job.input, "短时注意力融合后低于阈值；" + reason);
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
        final long attentionRevision;
        final String appName, recentBehavior;
        final List<ModelConfig.Profile> profiles = new ArrayList<>();
        volatile boolean obsolete;
        ModelJob(StatusBarNotification sbn, DecisionEngine.Input input, ModelConfig config) {
            this.sbn = sbn;
            this.input = input;
            this.config = config;
            this.ruleRevision = DemoStore.getDecisionRevision(FilterService.this);
            this.attentionRevision = AttentionStore.loadConfig(FilterService.this).revision;
            this.appName = attention == null ? input.packageName : attention.appName(input.packageName);
            this.recentBehavior = AttentionStore.recentBehavior(FilterService.this);
            if (config.mode == ModelConfig.Mode.COMPARE) {
                if (config.compareOfficial) profiles.add(config.official);
                if (config.compareBocha) profiles.add(config.bocha);
                if (config.compareRelay) profiles.add(config.relay);
            } else if (config.mode == ModelConfig.Mode.OFFICIAL) profiles.add(config.official);
            else if (config.mode == ModelConfig.Mode.BOCHA) profiles.add(config.bocha);
            else if (config.mode == ModelConfig.Mode.RELAY) profiles.add(config.relay);
        }
        boolean isValid() {
            return !obsolete && connected && config.revision == ModelStore.getRevision(FilterService.this)
                    && ruleRevision == DemoStore.getDecisionRevision(FilterService.this)
                    && attentionRevision == AttentionStore.loadConfig(FilterService.this).revision;
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
