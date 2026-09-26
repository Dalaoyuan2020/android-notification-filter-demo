package com.example.notificationdemo.filter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Pure Java implementation of the user-selected Jev choice question. No network or Android APIs. */
public final class SystemOneProtocol {
    public static final int MAX_INPUT_CHARS = 12000;
    public static final int MAX_REQUEST_BYTES = 65536;
    public static final int MAX_RESPONSE_BYTES = 32768;
    private SystemOneProtocol() {}

    public static final class Decision {
        public final boolean keep;
        public final double probability;
        public final boolean hasProbability;
        public final String reason;
        Decision(double probability, boolean hasProbability, double threshold, String reason) {
            this.keep = probability >= threshold;
            this.probability = probability;
            this.hasProbability = hasProbability;
            this.reason = reason;
        }
    }

    public static String requestBody(String model, String appName, String title, String text, String recentBehavior) {
        model = safe(model);
        appName = safe(appName);
        title = safe(title);
        text = safe(text);
        recentBehavior = safe(recentBehavior);
        long size = (long) appName.length() + title.length() + text.length() + recentBehavior.length();
        if (model.trim().isEmpty() || size > MAX_INPUT_CHARS || (title + text).trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid or oversized complete notification input");
        }
        Map<String, Object> state = map("来源", appName, "标题", title, "消息", text);
        if (!recentBehavior.trim().isEmpty()) state.put("近期行为", recentBehavior);
        Map<String, Object> question = map("type", "choice",
                "instructions", "这条通知是重要的个人通知，还是广告营销？",
                "criteria", map("重要", "重要的个人通知", "广告", "广告、营销或垃圾信息"));
        String body = StrictJson.stringify(map("model", model, "state", state,
                "questions", map("keep", question)));
        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Complete request exceeds size limit");
        }
        return body;
    }

    public static Decision parseResponse(String json, double threshold) {
        if (!validThreshold(threshold)) throw new IllegalArgumentException("Invalid keep threshold");
        if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Response exceeds size limit");
        }
        Map<String, Object> root = StrictJson.object(json);
        Map<?, ?> answers = object(root.get("answers"));
        Map<?, ?> keep = object(answers.get("keep"));
        if (keep.containsKey("type") && !"choice".equals(keep.get("type"))) {
            throw new IllegalArgumentException("Unexpected answer type");
        }
        if (keep.containsKey("probabilities")) {
            Map<?, ?> probabilities = object(keep.get("probabilities"));
            if (probabilities.containsKey("重要")) {
                Object raw = probabilities.get("重要");
                if (!(raw instanceof Number)) throw new IllegalArgumentException("Probability must be numeric");
                double probability = ((Number) raw).doubleValue();
                if (!validThreshold(probability)) throw new IllegalArgumentException("Probability outside unit interval");
                String reason = String.format(Locale.ROOT, "保留概率 %.4f %s 阈值 %.4f",
                        probability, probability >= threshold ? "≥" : "<", threshold);
                return new Decision(probability, true, threshold, reason);
            }
        }
        Object choice = keep.get("choice");
        if (!"重要".equals(choice) && !"广告".equals(choice)) {
            throw new IllegalArgumentException("Missing usable probability or choice");
        }
        double mapped = "重要".equals(choice) ? 1 : 0;
        return new Decision(mapped, false, threshold,
                "接口未返回概率，按choice「" + choice + "」映射为" + (int) mapped + "（无概率）");
    }

    /** Supports an origin, a /v1 base, or the complete /v1/systemone endpoint. */
    public static String endpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.length() > 2048) throw new IllegalArgumentException("Invalid HTTPS endpoint");
        try {
            URI uri = new URI(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.isOpaque()) {
                throw new IllegalArgumentException("Invalid HTTPS endpoint");
            }
            String normalized = baseUrl;
            while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            if (normalized.endsWith("/v1/systemone")) return normalized;
            if (normalized.endsWith("/v1")) return normalized + "/systemone";
            return normalized + "/v1/systemone";
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Invalid HTTPS endpoint");
        }
    }

    public static boolean validThreshold(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    private static Map<?, ?> object(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Missing decision object");
        return (Map<?, ?>) value;
    }

    private static Map<String, Object> map(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    private static String safe(String value) { return value == null ? "" : value; }
}
