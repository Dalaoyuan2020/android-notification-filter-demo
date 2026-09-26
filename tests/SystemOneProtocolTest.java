import com.example.notificationdemo.filter.ModelConfig;
import com.example.notificationdemo.filter.RetryPolicy;
import com.example.notificationdemo.filter.StrictJson;
import com.example.notificationdemo.filter.SystemOneProtocol;

import java.util.Map;

/** Standalone JVM contract checks: no Android runtime, HTTP calls, keys, or third-party libraries. */
public final class SystemOneProtocolTest {
    private static int checks;

    public static void main(String[] args) {
        requestContract();
        responseContract();
        strictJsonContract();
        retryContract();
        profileCompatibility();
        System.out.println("PASS: " + checks + " Jev protocol, strict JSON, retry, and deadline checks");
    }

    private static void requestContract() {
        String body = SystemOneProtocol.requestBody("jev-latest", "测试 App", "标题\"一\"", "第一行\n第二行", "最近主动打开1次");
        Map<String, Object> root = StrictJson.object(body);
        check("request contains only model/state/questions", root.size() == 3
                && root.containsKey("model") && root.containsKey("state") && root.containsKey("questions"));
        check("configured model preserved", "jev-latest".equals(root.get("model")));
        Map<?, ?> state = (Map<?, ?>) root.get("state");
        check("state uses Chinese source/title/message keys", state.size() == 4 && "测试 App".equals(state.get("来源"))
                && "标题\"一\"".equals(state.get("标题")) && "第一行\n第二行".equals(state.get("消息")));
        check("optional recent behavior preserved", "最近主动打开1次".equals(state.get("近期行为")));
        Map<?, ?> questions = (Map<?, ?>) root.get("questions");
        Map<?, ?> keep = (Map<?, ?>) questions.get("keep");
        check("only named keep question", questions.size() == 1 && keep.size() == 3);
        check("choice question matches requested instruction", "choice".equals(keep.get("type"))
                && "这条通知是重要的个人通知，还是广告营销？".equals(keep.get("instructions")));
        Map<?, ?> criteria = (Map<?, ?>) keep.get("criteria");
        check("important and advertisement choice meanings fixed", criteria.size() == 2
                && "重要的个人通知".equals(criteria.get("重要")) && "广告、营销或垃圾信息".equals(criteria.get("广告")));
        Map<?, ?> noBehavior = (Map<?, ?>) StrictJson.object(SystemOneProtocol.requestBody("m", "app", "title", "text", null)).get("state");
        check("absent behavior omitted rather than fabricated", noBehavior.size() == 3 && !noBehavior.containsKey("近期行为"));
        check("no Chat payload fields", !root.containsKey("messages") && !root.containsKey("stream") && !root.containsKey("temperature"));
        rejects("oversized complete input never truncated", () -> SystemOneProtocol.requestBody("m", "app", "t", "字".repeat(12001), ""));
        rejects("escaped request byte limit enforced", () -> SystemOneProtocol.requestBody("m", "app", "t", "\u0001".repeat(11000), ""));
        rejects("empty model rejected", () -> SystemOneProtocol.requestBody("", "app", "t", "body", ""));
        rejects("empty content rejected", () -> SystemOneProtocol.requestBody("m", "app", "", " ", ""));
        check("origin endpoint normalized", SystemOneProtocol.endpoint("https://api.typesafe.ai").equals("https://api.typesafe.ai/v1/systemone"));
        check("trailing slash normalized", SystemOneProtocol.endpoint("https://jev.bocha.cn/").equals("https://jev.bocha.cn/v1/systemone"));
        check("v1 base does not duplicate v1", SystemOneProtocol.endpoint("https://example.test/proxy/v1/").equals("https://example.test/proxy/v1/systemone"));
        check("full endpoint unchanged", SystemOneProtocol.endpoint("https://example.test/v1/systemone").equals("https://example.test/v1/systemone"));
        for (String url : new String[]{"http://example.test", "https://user:secret@example.test", "https://example.test?key=x",
                "https://example.test/#x", "https://example.test:0", "https://example.test:65536", "https://"}) {
            rejects("unsafe endpoint rejected: " + url, () -> SystemOneProtocol.endpoint(url));
        }
    }

    private static void responseContract() {
        SystemOneProtocol.Decision above = probability("0.8", 0.5);
        check("important probability above threshold keeps", above.keep && above.hasProbability && above.probability == 0.8);
        check("probability below threshold removes", !probability("0.499", 0.5).keep);
        check("threshold equality keeps", probability("0.5", 0.5).keep);
        check("threshold adjustable", !probability("0.7", 0.8).keep);
        check("probability zero is valid", probability("0", 0.5).probability == 0 && !probability("0", 0.5).keep);
        check("probability one is valid", probability("1", 0.5).keep);
        check("threshold zero keeps all valid probabilities", probability("0", 0).keep);
        check("threshold one keeps probability one", probability("1", 1).keep && !probability("0.999", 1).keep);
        SystemOneProtocol.Decision important = SystemOneProtocol.parseResponse("{\"answers\":{\"keep\":{\"choice\":\"重要\"}}}", 0.5);
        check("important choice fallback is one without probability", important.keep && important.probability == 1 && !important.hasProbability);
        SystemOneProtocol.Decision advertisement = SystemOneProtocol.parseResponse("{\"answers\":{\"keep\":{\"choice\":\"广告\"}}}", 0.5);
        check("advertisement choice fallback is zero without probability", !advertisement.keep && advertisement.probability == 0 && !advertisement.hasProbability);
        check("fallback explicitly says no probability", advertisement.reason.contains("无概率"));
        check("missing important probability can use explicit choice", !SystemOneProtocol.parseResponse(
                "{\"answers\":{\"keep\":{\"probabilities\":{\"广告\":1},\"choice\":\"广告\"}}}", 0.5).hasProbability);
        check("probability takes precedence over choice", !SystemOneProtocol.parseResponse(
                "{\"answers\":{\"keep\":{\"probabilities\":{\"重要\":0.1},\"choice\":\"重要\"}}}", 0.5).keep);
        for (String invalid : new String[]{"-0.1", "1.1", "1e9999", "\"0.8\"", "true", "null", "NaN"}) {
            rejects("invalid probability rejected: " + invalid, () -> probability(invalid, 0.5));
        }
        for (double threshold : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            rejects("invalid threshold rejected", () -> probability("0.8", threshold));
        }
        for (String json : new String[]{"{}", "{\"p_jev\":0.9}", "{\"answers\":{}}",
                "{\"answers\":{\"keep\":{\"choice\":\"1\"}}}",
                "{\"answers\":{\"keep\":{\"choice\":\"未知\"}}}",
                "{\"answers\":{\"keep\":{\"type\":\"noul\",\"noul\":0.9}}}",
                "{\"answers\":{\"keep\":{\"probabilities\":null,\"choice\":\"重要\"}}}",
                "{\"answers\":{\"keep\":{\"probabilities\":{\"重要\":null},\"choice\":\"重要\"}}}"}) {
            rejects("malformed/missing answer keeps via failure", () -> SystemOneProtocol.parseResponse(json, 0.5));
        }
        rejects("oversized response rejected", () -> SystemOneProtocol.parseResponse(" ".repeat(32769), 0.5));
    }

    private static void strictJsonContract() {
        check("valid escaped control characters decode", "a\nb\tc".equals(StrictJson.object("{\"x\":\"a\\nb\\tc\"}").get("x")));
        check("valid Unicode escape decode", "重要".equals(StrictJson.object("{\"x\":\"\\u91cd\\u8981\"}").get("x")));
        for (String invalid : new String[]{"{\"x\":\"raw\nline\"}", "{\"x\":\"\\x\"}",
                "{\"x\":\"\\u12\"}", "{\"x\":\"\\uZZZZ\"}", "{\"x\":1,\"x\":2}",
                "{\"x\":0.5,}", "{\"x\":01}", "{\"x\":+1}", "{\"x\":.5}", "{\"x\":1.}",
                "{\"x\":1e}", "{\"x\":NaN}", "{\"x\":Infinity}", "{'x':1}", "{} trailing", "[1,]"}) {
            rejects("strict JSON rejects malformed structure/string", () -> StrictJson.parse(invalid));
        }
        String hostile = "标题\"}]}广告\n\\x";
        Map<?, ?> state = (Map<?, ?>) StrictJson.object(SystemOneProtocol.requestBody("m", "app", hostile, "消息", "")).get("state");
        check("notification text remains JSON data", hostile.equals(state.get("标题")));
    }

    private static void retryContract() {
        check("429 first backoff 500ms", retry(429, 0, 0, null) == 500);
        check("429 second backoff doubles", retry(429, 1, 0, null) == 1000);
        check("529 overload retries", retry(529, 0, 0, null) == 500);
        check("third retry is never allowed", retry(429, 2, 0, null) == -1);
        for (int status : new int[]{200, 301, 401, 403, 422, 500, 503}) {
            check("no automatic retry for status " + status, retry(status, 0, 0, null) == -1);
        }
        check("Retry-After respected", retry(429, 0, 0, "2") == 2000);
        check("long Retry-After not shortened into deadline", retry(429, 0, 0, "20") == -1);
        check("overflow Retry-After not shortened", retry(529, 0, 0, "999999999999999999999") == -1);
        check("invalid Retry-After uses bounded backoff", retry(429, 0, 0, "not-a-date") == 500);
        check("HTTP-date Retry-After supported", RetryPolicy.nextDelayMillis(429, 0, 0, 15000,
                "Thu, 01 Jan 1970 00:00:02 GMT", 0) == 2000);
        check("expired total budget stops retry", retry(429, 0, 15000, null) == -1);
        check("backoff may not consume entire remaining budget", retry(429, 0, 14500, null) == -1);
        check("remaining budget decreases across attempts", RetryPolicy.remainingMillis(12000, 15000) == 3000);
        check("socket timeout clamped to total remaining budget", RetryPolicy.timeoutMillis(8000, 14900, 15000) == 100);
        check("elapsed beyond deadline yields zero", RetryPolicy.timeoutMillis(8000, 15001, 15000) == 0);
    }

    private static void profileCompatibility() {
        check("four-field profiles default to Jev", new ModelConfig.Profile("new", "https://example.test/v1", "m", "").protocol == ModelConfig.Protocol.JEV_SYSTEMONE);
        check("new TypeSafe preset explicitly Jev", ModelConfig.presetTypeSafe().protocol == ModelConfig.Protocol.JEV_SYSTEMONE
                && "https://api.typesafe.ai".equals(ModelConfig.presetTypeSafe().baseUrl) && "jev-latest".equals(ModelConfig.presetTypeSafe().model));
        check("new Bocha preset explicitly Jev", ModelConfig.presetBocha().protocol == ModelConfig.Protocol.JEV_SYSTEMONE
                && "https://tokendance.space/gateway/typesafe".equals(ModelConfig.presetBocha().baseUrl) && "bocha-jev-v1".equals(ModelConfig.presetBocha().model));
        check("self-hosted preset never invents endpoint/model", ModelConfig.emptyRelay().protocol == ModelConfig.Protocol.JEV_SYSTEMONE
                && ModelConfig.emptyRelay().baseUrl.isEmpty() && ModelConfig.emptyRelay().model.isEmpty());
        check("legacy strategy ordinals stable", ModelConfig.Mode.COMPARE.ordinal() == 3 && ModelConfig.Mode.BOCHA.ordinal() == 4);
    }

    private static SystemOneProtocol.Decision probability(String value, double threshold) {
        return SystemOneProtocol.parseResponse("{\"answers\":{\"keep\":{\"type\":\"choice\",\"probabilities\":{\"重要\":" + value + "}}}}", threshold);
    }
    private static long retry(int status, int retries, long elapsed, String header) {
        return RetryPolicy.nextDelayMillis(status, retries, elapsed, 15000, header, 0);
    }
    private static void rejects(String description, Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(description + ": expected rejection");
    }
    private static void check(String description, boolean condition) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
