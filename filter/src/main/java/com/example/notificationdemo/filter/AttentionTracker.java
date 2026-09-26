package com.example.notificationdemo.filter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main-thread lifecycle book; system/app removals never masquerade as user feedback. */
final class AttentionTracker {
    static final long IGNORE_AFTER_MS = 30 * 60 * 1000L;
    private final Context context;
    private final Map<String, Tracked> active = new LinkedHashMap<>();
    private final Map<String, JSONObject> restored = new LinkedHashMap<>();
    AttentionTracker(Context context) {
        this.context = context;
        JSONArray entries = AttentionStore.loadTracked(context);
        for (int i = 0; i < Math.min(256, entries.length()); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry != null) restored.put(entry.optString("key"), entry);
        }
    }
    String appName(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException error) { return pkg; }
    }
    void posted(StatusBarNotification sbn, DecisionEngine.Input input) {
        if (!DecisionEngine.parseList(DemoStore.getTargets(context)).contains(input.packageName.toLowerCase(java.util.Locale.ROOT))
                || input.groupSummary || input.ongoing || !input.clearable) return;
        long now = System.currentTimeMillis();
        Tracked item = active.get(sbn.getKey());
        if (item == null) {
            long appeared = Math.min(now, Math.max(0, sbn.getPostTime()));
            item = new Tracked(sbn.getKey(), appeared, input, appName(input.packageName));
            JSONObject saved = restored.remove(sbn.getKey());
            if (saved != null && saved.optLong("postTime") == sbn.getPostTime()) {
                item.appeared = Math.min(now, saved.optLong("appeared", appeared));
                item.ignored = saved.optBoolean("ignored", false);
                item.pJev = saved.optDouble("p_jev", Double.NaN);
                item.pFinal = saved.optDouble("p_final", Double.NaN);
            }
            active.put(item.key, item);
            if (active.size() > 256) active.remove(active.keySet().iterator().next());
        } else if (item.postTime != sbn.getPostTime()) {
            item.appeared = Math.min(now, Math.max(0, sbn.getPostTime()));
            item.ignored = false;
            item.pJev = Double.NaN; item.pFinal = Double.NaN;
        }
        item.input = input;
        item.postTime = sbn.getPostTime();
        persist();
    }
    void prediction(String key, double pJev, double pFinal) {
        Tracked item = active.get(key);
        if (item == null) return;
        item.pJev = pJev; item.pFinal = pFinal;
        persist();
    }
    void removed(StatusBarNotification sbn, int reason) {
        Tracked item = active.get(sbn.getKey());
        if (item == null || item.postTime != sbn.getPostTime()) return;
        active.remove(item.key);
        long now = System.currentTimeMillis();
        AttentionMath.Observation observation = AttentionMath.observationForRemoval(reason, Math.max(0, now - item.appeared));
        String label = reason == 1 ? "点开" : reason == 2 ? "划掉" : "其他";
        record(item, now, label, reason, observation);
        persist();
    }
    void sweep(long now) {
        for (Tracked item : new ArrayList<>(active.values())) {
            if (!item.ignored && now - item.appeared >= IGNORE_AFTER_MS) {
                item.ignored = true;
                record(item, now, "放着不管", -1, AttentionMath.ignoredObservation());
            }
        }
        persist();
    }
    void reconcile(StatusBarNotification[] existing) {
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        if (existing != null) for (StatusBarNotification sbn : existing) keys.add(sbn.getKey());
        active.keySet().retainAll(keys);
        restored.clear();
        persist();
    }
    void clear() { active.clear(); restored.clear(); persist(); }
    private void record(Tracked item, long now, String reason, int code, AttentionMath.Observation observation) {
        if (!DecisionEngine.parseList(DemoStore.getTargets(context)).contains(
                item.input.packageName.toLowerCase(java.util.Locale.ROOT))) return;
        AttentionStore.observe(context, item.input.packageName, item.input.title, item.appName, observation);
        try {
            JSONObject event = new JSONObject().put("包名", item.input.packageName).put("App名", item.appName)
                    .put("出现时间", item.appeared).put("消失时间", code == -1 ? JSONObject.NULL : now)
                    .put("事件时间", now).put("原因", reason).put("reason_code", code)
                    .put("停留秒数", Math.max(0, now - item.appeared) / 1000.0)
                    .put("y", observation == null ? JSONObject.NULL : observation.y)
                    .put("p_jev", finite(item.pJev)).put("p_final", finite(item.pFinal));
            DemoStore.addAttentionLog(context, item.input, reason + " · " + Math.max(0, now - item.appeared) / 1000
                    + "秒 · " + (observation == null ? "不学习" : "y=" + observation.y), item.key, event);
            AttentionReporter.submit(context, event, item.input.text);
        } catch (JSONException ignored) { /* No fabricated observation if the event cannot be represented. */ }
    }
    private static Object finite(double value) { return Double.isFinite(value) ? value : JSONObject.NULL; }
    private void persist() {
        JSONArray data = new JSONArray();
        for (Tracked item : active.values()) {
            try {
                data.put(new JSONObject().put("key", item.key).put("appeared", item.appeared)
                        .put("postTime", item.postTime).put("ignored", item.ignored)
                        .put("p_jev", finite(item.pJev)).put("p_final", finite(item.pFinal)));
            } catch (JSONException ignored) { }
        }
        AttentionStore.saveTracked(context, data);
    }
    private static final class Tracked {
        final String key, appName;
        long appeared, postTime;
        DecisionEngine.Input input;
        boolean ignored;
        double pJev = Double.NaN, pFinal = Double.NaN;
        Tracked(String key, long appeared, DecisionEngine.Input input, String appName) {
            this.key = key; this.appeared = appeared; this.input = input; this.appName = appName;
        }
    }
}
