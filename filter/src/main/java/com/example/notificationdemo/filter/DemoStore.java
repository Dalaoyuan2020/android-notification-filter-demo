package com.example.notificationdemo.filter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Private device-local log and rule store. Remote transport is separately opt-in. */
public final class DemoStore {
    public static final String ACTION_CHANGED = "com.example.notificationdemo.filter.CHANGED";
    public static final String DEFAULT_TARGETS = "com.sina.weibo,com.example.notificationdemo.sender";
    public static final String DEFAULT_KEEP_WORDS = "紧急,会议,重要,家人";
    public static final String DEFAULT_BLOCK_WORDS = "热搜,推荐,优惠,广告";
    private static final int MAX_LOGS = 150;
    private static final String FILE = "notification_demo";

    private DemoStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean getAuto(Context context) { return prefs(context).getBoolean("auto", false); }
    public static synchronized void setAuto(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("auto", enabled)
                .putLong("decision_revision", getDecisionRevision(context) + 1).apply();
        notifyChanged(context);
    }
    public static String getTargets(Context context) {
        return prefs(context).getString("targets", DEFAULT_TARGETS);
    }
    public static void setTargets(Context context, String value) { putString(context, "targets", value); }
    public static String getKeepWords(Context context) {
        return prefs(context).getString("keep", DEFAULT_KEEP_WORDS);
    }
    public static void setKeepWords(Context context, String value) { putString(context, "keep", value); }
    public static String getBlockWords(Context context) {
        return prefs(context).getString("block", DEFAULT_BLOCK_WORDS);
    }
    public static void setBlockWords(Context context, String value) { putString(context, "block", value); }

    public static long getDecisionRevision(Context context) {
        return prefs(context).getLong("decision_revision", 0);
    }

    private static synchronized void putString(Context context, String key, String value) {
        prefs(context).edit().putString(key, value == null ? "" : value)
                .putLong("decision_revision", getDecisionRevision(context) + 1).apply();
        notifyChanged(context);
    }

    public static synchronized JSONArray getLogs(Context context) {
        try {
            return new JSONArray(prefs(context).getString("logs", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static synchronized void clearLogs(Context context) {
        prefs(context).edit().remove("logs").apply();
        notifyChanged(context);
    }

    static synchronized void addLog(Context context, String pkg, String title, String text,
                                    String action, String reason, String key) {
        JSONObject event = new JSONObject();
        try {
            event.put("time", System.currentTimeMillis());
            event.put("pkg", truncate(pkg, 180));
            event.put("title", truncate(title, 240));
            event.put("text", truncate(text, 900));
            event.put("action", truncate(action, 40));
            event.put("reason", truncate(reason, 400));
            event.put("key", truncate(key, 600));
        } catch (JSONException impossible) {
            return;
        }
        JSONArray old = getLogs(context);
        JSONArray next = new JSONArray().put(event);
        for (int i = 0; i < old.length() && next.length() < MAX_LOGS; i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null) next.put(item);
        }
        prefs(context).edit().putString("logs", next.toString()).apply();
        notifyChanged(context);
    }

    static void notifyChanged(Context context) {
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…[已截断]";
    }
}
