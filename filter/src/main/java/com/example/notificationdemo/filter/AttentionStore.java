package com.example.notificationdemo.filter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;
import java.net.URI;
import java.util.List;

/** Local attention and preferences; shared preferences are excluded from backup. */
public final class AttentionStore {
    private static final String FILE = "short_attention_v1";
    private static ShortTermMemory memory;
    private AttentionStore() {}
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    public static final class Config {
        public final double halfLifeMinutes, weight;
        public final boolean recentBehaviorEnabled, uploadEnabled, uploadBody;
        public final String serviceUrl, serviceToken, storageError;
        public final long revision;
        Config(double halfLife, double weight, boolean recent, boolean upload, String url,
               String token, boolean body, long revision, String error) {
            this.halfLifeMinutes = halfLife; this.weight = weight;
            this.recentBehaviorEnabled = recent; this.uploadEnabled = upload;
            this.serviceUrl = url; this.serviceToken = token; this.uploadBody = body;
            this.revision = revision; this.storageError = error;
        }
    }
    public static synchronized Config loadConfig(Context context) {
        try {
            SharedPreferences p = prefs(context);
            String token = "", error = "";
            try { token = ModelStore.decryptSecret(p.getString("token", ""), "attention-telemetry"); }
            catch (IOException failure) { error = "注意力服务凭据不可用，上报已停用，请重新保存。"; }
            double half = Double.longBitsToDouble(p.getLong("half", Double.doubleToLongBits(30)));
            double weight = Double.longBitsToDouble(p.getLong("weight", Double.doubleToLongBits(1)));
            if (!Double.isFinite(half) || half <= 0 || !Double.isFinite(weight) || weight < 0) {
                throw new IllegalStateException("Invalid attention settings");
            }
            return new Config(half, weight, p.getBoolean("recent", true),
                    error.isEmpty() && p.getBoolean("upload", false), p.getString("url", ""), token,
                    p.getBoolean("body", false), p.getLong("revision", 0), error);
        } catch (RuntimeException invalid) {
            return new Config(30, 1, false, false, "", "", false, -1, "注意力设置损坏，请重新保存。");
        }
    }
    public static synchronized Config saveConfig(Context context, double halfLifeMinutes, double weight,
            boolean recentBehaviorEnabled, boolean uploadEnabled, String serviceUrl,
            String serviceToken, boolean uploadBody) throws IOException {
        if (!Double.isFinite(halfLifeMinutes) || halfLifeMinutes < 1 || halfLifeMinutes > 525600
                || !Double.isFinite(weight) || weight < 0 || weight > 10) {
            throw new IOException("半衰期须为1–525600分钟，融合权重须为0–10。");
        }
        String url = serviceUrl == null ? "" : serviceUrl.trim();
        String token = serviceToken == null ? "" : serviceToken.trim();
        if (uploadEnabled || !url.isEmpty()) validateServiceUrl(url);
        if (token.length() > 8192) throw new IOException("Token过长。");
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) < 33 || token.charAt(i) > 126) throw new IOException("Token字符无效。");
        }
        String cipher = ModelStore.encryptSecret(token, "attention-telemetry");
        long revision = Math.max(loadConfig(context).revision + 1, System.currentTimeMillis());
        DemoStore.setAuto(context, false);
        if (!prefs(context).edit().putLong("half", Double.doubleToLongBits(halfLifeMinutes))
                .putLong("weight", Double.doubleToLongBits(weight)).putBoolean("recent", recentBehaviorEnabled)
                .putBoolean("upload", uploadEnabled).putString("url", url).putString("token", cipher)
                .putBoolean("body", uploadBody).putLong("revision", revision).commit()) {
            throw new IOException("注意力设置写入失败，自动清除已关闭。");
        }
        DemoStore.notifyChanged(context);
        return loadConfig(context);
    }
    static URI validateServiceUrl(String base) throws IOException {
        try {
            URI uri = new URI(base);
            if (base.length() > 2048 || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IOException("注意力服务须为不带账号、查询参数的HTTPS地址。");
            }
            return uri;
        } catch (java.net.URISyntaxException error) { throw new IOException("注意力服务地址无效。"); }
    }
    private static ShortTermMemory memory(Context context) {
        if (memory == null) memory = ShortTermMemory.deserialize(prefs(context).getString("memory", ""));
        return memory;
    }
    public static synchronized List<ShortTermMemory.Entry> snapshots(Context context) {
        return memory(context).snapshots(System.currentTimeMillis(), loadConfig(context).halfLifeMinutes);
    }
    static synchronized ShortTermMemory.Entry get(Context context, String pkg, String title, String appName) {
        return memory(context).get(pkg, title, appName, System.currentTimeMillis(), loadConfig(context).halfLifeMinutes);
    }
    static synchronized String recentBehavior(Context context) {
        Config config = loadConfig(context);
        if (!config.recentBehaviorEnabled) return "";
        java.util.Set<String> targets = DecisionEngine.parseList(DemoStore.getTargets(context));
        java.util.List<ShortTermMemory.Entry> candidates = new java.util.ArrayList<>(snapshots(context));
        candidates.removeIf(entry -> !targets.contains(entry.pkg.toLowerCase(java.util.Locale.ROOT))
                || !entry.windowComplete || entry.windowCount() == 0 || entry.change <= 0);
        candidates.sort(java.util.Comparator.comparingDouble((ShortTermMemory.Entry entry) -> entry.change).reversed()
                .thenComparingInt(entry -> entry.prefix.isEmpty() ? 1 : 0));
        java.util.List<ShortTermMemory.Entry> selected = new java.util.ArrayList<>();
        for (ShortTermMemory.Entry entry : candidates) {
            boolean duplicate = false;
            for (ShortTermMemory.Entry previous : selected) {
                if (entry.pkg.equals(previous.pkg) && (entry.prefix.isEmpty() || previous.prefix.isEmpty())) duplicate = true;
            }
            if (!duplicate && selected.size() < 2) selected.add(entry);
        }
        StringBuilder text = new StringBuilder();
        for (ShortTermMemory.Entry entry : selected) {
            if (text.length() > 0) text.append("；");
            text.append(entry.behaviorText);
        }
        return text.toString();
    }
    static synchronized void observe(Context context, String pkg, String title, String appName,
                                    AttentionMath.Observation observation) {
        if (observation == null) return;
        memory(context).observe(pkg, title, appName, observation, System.currentTimeMillis(), loadConfig(context).halfLifeMinutes);
        prefs(context).edit().putString("memory", memory.serialize()).apply();
        DemoStore.notifyChanged(context);
    }
    static synchronized void clearMemory(Context context) {
        memory = new ShortTermMemory();
        prefs(context).edit().remove("memory").remove("tracked").apply();
    }
    static synchronized JSONArray loadTracked(Context context) {
        try { return new JSONArray(prefs(context).getString("tracked", "[]")); }
        catch (JSONException invalid) { return new JSONArray(); }
    }
    static synchronized void saveTracked(Context context, JSONArray entries) {
        prefs(context).edit().putString("tracked", entries.toString()).apply();
    }
}
