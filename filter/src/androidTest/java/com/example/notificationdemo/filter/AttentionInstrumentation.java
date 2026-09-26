package com.example.notificationdemo.filter;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Base64;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Actual localhost TLS + actual SystemUI dismissals. Never contacts a real model provider. */
public class AttentionInstrumentation extends Instrumentation {
    private static final String SENDER = "com.example.notificationdemo.sender";
    private static final long TIMEOUT_MS = 20000;
    private static final Set<Integer> SAMPLE_IDS = new HashSet<>(Arrays.asList(101, 102, 103, 104, 105, 200, 201, 202, 203, 301, 410, 411, 412, 413, 414, 420, 421));
    private static final Pattern RECORD = Pattern.compile("^\\s+NotificationRecord\\([^\\r\\n]*?\\bpkg=([^\\s]+)[^\\r\\n]*?\\bid=(-?\\d+)\\b.*$");
    private String origin, caBase64;
    private Context target;
    private SSLSocketFactory socketFactory;
    private ModelConfig.Profile official, relay, bocha;
    private int passed, failed;
    private final StringBuilder report = new StringBuilder();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        origin = arguments == null ? "" : arguments.getString("mock_origin", "");
        caBase64 = arguments == null ? "" : arguments.getString("ca_base64", "");
        start();
    }

    @Override public void onStart() {
        super.onStart();
        target = getTargetContext();
        report.append("Actual localhost HTTPS Jev + real Android notification attention checks\n");
        try {
            URL parsed = new URL(origin);
            if (!"https".equals(parsed.getProtocol()) || !"localhost".equals(parsed.getHost()) || parsed.getPort() < 1) {
                throw new AssertionError("Only explicitly forwarded HTTPS localhost fixture is permitted");
            }
            socketFactory = trustFixtureCa(caBase64);
            official = profile("Mock official", "/official");
            relay = profile("Mock relay", "/relay");
            bocha = profile("Mock bocha", "/bocha");
            getUiAutomation();
            AccessibilityServiceInfo serviceInfo = getUiAutomation().getServiceInfo();
            serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(serviceInfo);
            if (!FilterService.isConnected()) {
                runOnMainSync(() -> NotificationListenerService.requestRebind(new ComponentName(target, FilterService.class)));
            }
            await("listener connected", FilterService::isConnected);
            FilterService.setRichClassifierForTests((profile, input, appName, recentBehavior, threshold) ->
                    ModelClient.classify(profile, input, appName, recentBehavior, threshold, this::trustedConnection));
            runCase("fresh attention defaults keep event/body upload off", this::freshDefaults);
            resetSettings();
            runCase("actual TLS certificate verification", this::tlsVerification);
            runCase("attention token storage is encrypted and round-trips", this::attentionTokenStorage);
            runCase("existing Chat profile keeps legacy protocol on migration", this::legacyMigration);
            runCase("three and two route comparison retains notifications", this::compareRoutes);
            runCase("five real SystemUI dismissals then isolated prefix predictions", this::realDismissalsAndProbes);
            runCase("accelerated 30-minute ignore sweep records once", this::ignoreOnce);
        } catch (Throwable failure) {
            failure("setup", failure);
        } finally {
            try {
                resetSettings();
                scenario("clear");
                await("synthetic notifications cleared", () -> activeIds().isEmpty());
                DemoStore.clearLogs(target);
                waitForIdleSync();
                FilterService.setRichClassifierForTests(null);
                boolean committed = target.getSharedPreferences("notification_demo", Context.MODE_PRIVATE).edit().putBoolean("auto", false).commit();
                check("teardown leaves keyword observe mode with no remote credentials", committed && !DemoStore.getAuto(target)
                        && ModelStore.load(target).mode == ModelConfig.Mode.KEYWORDS && !ModelStore.load(target).remoteEnabled);
            } catch (Throwable failure) { failure("teardown", failure); }
        }
        report.append("\nATTENTION RESULT: ").append(passed).append(" checks passed, ").append(failed).append(" failures\n");
        Bundle result = new Bundle(); result.putString("stream", "\n" + report);
        result.putInt("passed", passed); result.putInt("failed", failed);
        finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void tlsVerification() throws Exception {
        DecisionEngine.Input input = new DecisionEngine.Input(SENDER, "合成 TLS 测试", "这是本机模拟接口的合成通知。", false, true, false, "msg");
        ModelClient.Result untrusted = ModelClient.classify(official, input, "合成应用", "", 0.5,
                endpoint -> (HttpsURLConnection) endpoint.openConnection());
        check("OS default trust rejects disposable fixture certificate", !untrusted.success && "TLS_ERROR".equals(untrusted.error));
        ModelClient.Result trusted = ModelClient.classify(official, input, "合成应用", "", 0.5, this::trustedConnection);
        check("fixture-only TrustManager accepts actual HTTPS response", trusted.success && trusted.hasProbability && close(trusted.probability, 0.7));
        check("actual Jev probability maps to KEEP at threshold 0.5", trusted.action == DecisionEngine.Action.KEEP);
    }

    private void freshDefaults() {
        AttentionStore.Config config = AttentionStore.loadConfig(target);
        check("fresh install defaults to upload=false and uploadBody=false", !config.uploadEnabled && !config.uploadBody);
        check("fresh attention defaults are half-life 30 minutes and weight 1", config.halfLifeMinutes == 30 && config.weight == 1);
        check("fresh install has no attention service token", config.serviceToken.isEmpty());
    }

    private void attentionTokenStorage() throws Exception {
        String syntheticToken = "synthetic-attention-test-token-v030-9b1a";
        AttentionStore.saveConfig(target, 30, 1, true, false, origin + "/attention", syntheticToken, false);
        AttentionStore.Config config = AttentionStore.loadConfig(target);
        check("encrypted attention token decrypts to original synthetic value", syntheticToken.equals(config.serviceToken) && config.storageError.isEmpty());
        SharedPreferences preferences = target.getSharedPreferences("short_attention_v1", Context.MODE_PRIVATE);
        check("attention preferences contain encrypted token but no plaintext", preferences.getString("token", "").startsWith("v1:")
                && !preferences.getAll().toString().contains(syntheticToken));
        check("saving service address/token alone does not enable upload or body sharing", !config.uploadEnabled && !config.uploadBody);
        resetSettings();
    }

    private void legacyMigration() throws Exception {
        SharedPreferences preferences = target.getSharedPreferences(ModelStore.PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean committed = preferences.edit().putString("official_label", "Legacy Chat synthetic")
                .putString("official_url", origin + "/legacy-chat").putString("official_model", "legacy-synthetic")
                .putString("official_key_cipher", "").remove("official_protocol").commit();
        ModelConfig migrated = ModelStore.load(target);
        check("old saved profile without protocol remains Chat Completions", committed
                && migrated.official.protocol == ModelConfig.Protocol.CHAT_COMPLETIONS);
        check("legacy endpoint/model are preserved during migration", migrated.official.baseUrl.equals(origin + "/legacy-chat")
                && migrated.official.model.equals("legacy-synthetic"));
        check("migration does not enable remote transmission", !migrated.remoteEnabled);
        resetSettings();
    }

    private void compareRoutes() throws Exception {
        cleanCase();
        configure(ModelConfig.Mode.COMPARE, true, true, true);
        DemoStore.setAuto(target, true);
        scenario("ad");
        await("three successful comparison models", () -> models(102).length() == 3 && allProbabilities(models(102), 0.7, 0.7));
        check("three route logs contain both original and final probabilities", models(102).length() == 3 && allProbabilities(models(102), 0.7, 0.7));
        stable("three-route COMPARE keeps active ad despite auto enabled", set(102));
        check("COMPARE never requests cancellation", !hasAction("请求清除"));
        captureProbabilityUi("comparison-proof.png", 3, false,
                new String[]{"Mock official", "Mock relay", "Mock bocha"});
        configure(ModelConfig.Mode.COMPARE, true, false, true);
        DemoStore.setAuto(target, true);
        scenario("normal");
        await("two selected comparison models", () -> models(101).length() == 2 && allProbabilities(models(101), 0.7, 0.7));
        check("two-route selection produces exactly two model results", models(101).length() == 2);
        stable("two-route COMPARE also preserves notifications", set(101, 102));
    }

    private void realDismissalsAndProbes() throws Exception {
        cleanCase();
        configure(ModelConfig.Mode.OFFICIAL, true, true, true);
        scenario("attention_demo");
        await("five synthetic notifications receive real HTTPS judgments", () -> {
            for (int id = 410; id <= 414; id++) if (!allProbabilities(models(id), 0.7, 0.7)) return false;
            return activeIds().containsAll(set(410, 411, 412, 413, 414));
        });
        shell("cmd statusbar expand-notifications");
        SystemClock.sleep(600);
        for (int id = 414; id >= 410; id--) {
            String title = "【淘宝】合成优惠样本 " + (id - 409);
            dismissThroughSystemUi(title);
            final int targetId = id;
            await("real REASON_CANCEL for " + id, () -> attention(targetId, 2) != null && !activeIds().contains(targetId));
            JSONObject event = attention(id, 2);
            check("SystemUI individually dismissed " + id + " with reason=2 and y=.05", event != null && close(event.optDouble("y"), 0.05));
        }
        shell("cmd statusbar collapse");
        ShortTermMemory.Entry taobao = AttentionStore.get(target, SENDER, "【淘宝】探针", "发送器");
        ShortTermMemory.Entry bank = AttentionStore.get(target, SENDER, "【银行】探针", "发送器");
        check("five actual dismiss callbacks updated Taobao memory", taobao.totalObservations == 5 && taobao.dismissals == 5 && taobao.pShort < 0.2);
        check("unseen bank prefix has no inherited sender feedback", bank.totalObservations == 0 && bank.alpha == 1 && bank.beta == 1);
        scenario("probe");
        await("probe judgments arrive", () -> models(420).length() == 1 && models(421).length() == 1);
        JSONObject taobaoModel = models(420).getJSONObject(0), bankModel = models(421).getJSONObject(0);
        check("Taobao final probability is below original real HTTPS .7", close(taobaoModel.optDouble("p_jev"), 0.7)
                && taobaoModel.optDouble("p_final", Double.NaN) < taobaoModel.optDouble("p_jev"));
        check("bank final probability exactly preserves original .7", bankModel.getDouble("p_jev") == bankModel.getDouble("p_final")
                && close(bankModel.getDouble("p_jev"), 0.7));
        evidence("PROBE: Taobao p_jev=" + taobaoModel.getDouble("p_jev") + ", p_final=" + taobaoModel.getDouble("p_final")
                + "; bank p_jev=" + bankModel.getDouble("p_jev") + ", p_final=" + bankModel.getDouble("p_final"));
        stable("observation mode retains both probes", set(420, 421));
        captureProbabilityUi("attention-proof.png", 1, true, new String[0]);

        // Repost the same probes after enabling automatic action. The .7 server result alone
        // would KEEP; only the actually learned p_final below .5 may cancel the Taobao probe.
        DemoStore.setAuto(target, true);
        scenario("probe");
        await("fused decision actually cancels Taobao while retaining bank", () -> !activeIds().contains(420)
                && activeIds().contains(421) && attention(420, 10) != null && hasNotificationAction(420, "已清除"));
        JSONObject automaticEvent = attention(420, 10);
        check("automatic cancellation uses learned p_final below threshold despite p_jev=.7",
                close(automaticEvent.optDouble("p_jev"), 0.7) && automaticEvent.optDouble("p_final", Double.NaN) < 0.5
                        && hasNotificationAction(420, "请求清除") && hasNotificationAction(420, "已清除"));
        ShortTermMemory.Entry afterAutomatic = AttentionStore.get(target, SENDER, "【淘宝】探针", "发送器");
        check("own listener cancellation reason=10 never becomes a sixth observation",
                automaticEvent.has("y") && automaticEvent.isNull("y") && afterAutomatic.totalObservations == 5
                        && afterAutomatic.dismissals == 5);
        stable("automatic fused decision leaves bank probe active", set(421));
        evidence("AUTO: Taobao original=" + automaticEvent.getDouble("p_jev") + ", final=" + automaticEvent.getDouble("p_final")
                + ", actual system removal reason=10; learning observations remain " + afterAutomatic.totalObservations);
    }

    private void ignoreOnce() throws Exception {
        cleanCase();
        configure(ModelConfig.Mode.OFFICIAL, true, true, true);
        scenario("normal");
        await("ignore sample has original model prediction", () -> models(101).length() == 1);
        check("no ignore event before accelerated interval", attention(101, -1) == null);
        runOnMainSync(() -> FilterService.sweepAttentionForTests(AttentionMath.IGNORE_AFTER_MS + 1000));
        await("accelerated ignore event", () -> attention(101, -1) != null);
        JSONObject event = attention(101, -1);
        check("accelerated 30-minute still-active sample learns y=.2", close(event.optDouble("y"), 0.2)
                && event.optDouble("停留秒数") >= 1800 && activeIds().contains(101));
        runOnMainSync(() -> FilterService.sweepAttentionForTests(AttentionMath.IGNORE_AFTER_MS + 1000));
        waitForIdleSync();
        check("repeated accelerated sweep does not duplicate ignore", attentionCount(101, -1) == 1);
        evidence("NOTE: ignore test uses a bounded debug clock offset; no real 30-minute wait is claimed.");
    }

    private SSLSocketFactory trustFixtureCa(String encoded) throws Exception {
        byte[] der = Base64.decode(encoded, Base64.NO_WRAP);
        X509Certificate ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        ca.checkValidity();
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType()); store.load(null, null);
        store.setCertificateEntry("disposable-local-test-ca", ca);
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(store);
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(null, managers.getTrustManagers(), null);
        return tls.getSocketFactory();
    }

    private HttpsURLConnection trustedConnection(URL endpoint) throws IOException {
        URL allowed = new URL(origin);
        if (!"https".equals(endpoint.getProtocol()) || !"localhost".equals(endpoint.getHost()) || endpoint.getPort() != allowed.getPort()) {
            throw new IOException("Test transport permits only its forwarded localhost fixture");
        }
        HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection();
        connection.setSSLSocketFactory(socketFactory);
        // Keep HttpsURLConnection's hostname verifier: the disposable cert has localhost SAN.
        return connection;
    }

    private ModelConfig.Profile profile(String label, String route) {
        return new ModelConfig.Profile(label, origin + route, "synthetic-jev", "", ModelConfig.Protocol.JEV_SYSTEMONE);
    }

    private void configure(ModelConfig.Mode mode, boolean includeOfficial, boolean includeRelay, boolean includeBocha) throws Exception {
        ModelStore.save(target, mode, true, official, relay, bocha, 0.5, includeOfficial, includeRelay, includeBocha);
        DemoStore.setAuto(target, false);
    }

    private void resetSettings() throws Exception {
        DemoStore.setAuto(target, false);
        ModelStore.save(target, ModelConfig.Mode.KEYWORDS, false, ModelConfig.emptyOfficial(), ModelConfig.emptyRelay(),
                ModelConfig.presetBocha(), 0.5, true, false, true);
        AttentionStore.saveConfig(target, 30, 1, true, false, "", "", false);
        DemoStore.setTargets(target, SENDER);
        DemoStore.setKeepWords(target, DemoStore.DEFAULT_KEEP_WORDS);
        DemoStore.setBlockWords(target, DemoStore.DEFAULT_BLOCK_WORDS);
    }

    private void cleanCase() throws Exception {
        resetSettings(); scenario("clear");
        await("previous synthetic samples cleared", () -> activeIds().isEmpty());
        waitForIdleSync(); DemoStore.clearLogs(target); waitForIdleSync();
    }

    private JSONArray models(int id) {
        JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) {
            JSONObject event = logs.optJSONObject(i);
            if (matches(event, id) && event.optJSONArray("models") != null) return event.optJSONArray("models");
        }
        return new JSONArray();
    }
    private boolean allProbabilities(JSONArray values, double pJev, double pFinal) {
        if (values.length() == 0) return false;
        for (int i = 0; i < values.length(); i++) {
            JSONObject result = values.optJSONObject(i);
            if (result == null || !close(result.optDouble("p_jev", Double.NaN), pJev) || !close(result.optDouble("p_final", Double.NaN), pFinal)) return false;
        }
        return true;
    }
    private JSONObject attention(int id, int reason) {
        JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) {
            JSONObject event = logs.optJSONObject(i);
            JSONObject attention = event == null ? null : event.optJSONObject("attention");
            if (matches(event, id) && attention != null && attention.optInt("reason_code", -999) == reason) return attention;
        }
        return null;
    }
    private void captureProbabilityUi(String filename, int minimumProbabilityViews, boolean requireDownward,
                                      String[] requiredLabels) throws Exception {
        shell("am start -W -n com.example.notificationdemo.filter/.MainActivity");
        boolean visible = false;
        for (int attempt = 0; attempt < 16; attempt++) {
            AccessibilityNodeInfo root = filterRoot();
            if (root == null) { SystemClock.sleep(200); continue; }
            if (hasVisibleProbabilities(root, minimumProbabilityViews, requireDownward, requiredLabels)) { visible = true; break; }
            AccessibilityNodeInfo candidate = findProbabilityPanel(root, requireDownward);
            boolean shown = candidate != null && candidate.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            if (!shown) scroll(root);
            SystemClock.sleep(300);
        }
        check(filename + ": probability values and requested routes are actually visible on screen", visible);
        SystemClock.sleep(250);
        AccessibilityNodeInfo finalRoot = filterRoot();
        check(filename + ": visible probability bounds remain inside app viewport", finalRoot != null
                && hasVisibleProbabilities(finalRoot, minimumProbabilityViews, requireDownward, requiredLabels));
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        if (screenshot == null) throw new AssertionError("Unable to capture actual probability UI");
        File proof = new File(target.getExternalFilesDir(null), filename);
        try (FileOutputStream output = new FileOutputStream(proof)) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new AssertionError("Screenshot encoding failed");
        } finally { screenshot.recycle(); }
        check(filename + ": actual visible probability UI screenshot saved", proof.isFile() && proof.length() > 0);
    }

    private AccessibilityNodeInfo filterRoot() {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        return root != null && "com.example.notificationdemo.filter".contentEquals(root.getPackageName() == null ? "" : root.getPackageName()) ? root : null;
    }
    private AccessibilityNodeInfo findProbabilityPanel(AccessibilityNodeInfo node, boolean downward) {
        if (node == null) return null;
        String value = node.getText() == null ? "" : node.getText().toString();
        if ((!downward && value.equals("p_jev")) || (downward && value.contains("↓ p_final"))) {
            AccessibilityNodeInfo column = node.getParent();
            AccessibilityNodeInfo overview = column == null ? null : column.getParent();
            return overview == null ? node : overview;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findProbabilityPanel(node.getChild(i), downward);
            if (found != null) return found;
        }
        return null;
    }
    private boolean hasVisibleProbabilities(AccessibilityNodeInfo root, int minimum, boolean downward, String[] labels) {
        Rect viewport = new Rect(); root.getBoundsInScreen(viewport);
        AccessibilityNodeInfo panel = findProbabilityPanel(root, downward);
        if (panel == null) return false;
        Rect panelBounds = new Rect(); panel.getBoundsInScreen(panelBounds);
        if (!panel.isVisibleToUser() || panelBounds.isEmpty() || !viewport.contains(panelBounds)) return false;
        List<String> visible = new ArrayList<>(); visibleText(panel, viewport, visible);
        int originalLabels = 0, finalLabels = 0, numericValues = 0;
        boolean hasDownward = false; StringBuilder combined = new StringBuilder();
        for (String value : visible) {
            combined.append(value).append('\n');
            if (value.contains("p_jev")) originalLabels++;
            if (value.contains("p_final")) finalLabels++;
            if (value.matches("(?:0|1)\\.\\d{3,}")) numericValues++;
            if (value.contains("↓")) hasDownward = true;
        }
        if (originalLabels < minimum || finalLabels < minimum || numericValues < 2 * minimum || (downward && !hasDownward)) return false;
        for (String label : labels) if (!combined.toString().contains(label)) return false;
        return true;
    }
    private void visibleText(AccessibilityNodeInfo node, Rect viewport, List<String> values) {
        if (node == null) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        if (node.isVisibleToUser() && node.getText() != null && !bounds.isEmpty() && viewport.contains(bounds)) values.add(node.getText().toString());
        for (int i = 0; i < node.getChildCount(); i++) visibleText(node.getChild(i), viewport, values);
    }
    private int attentionCount(int id, int reason) {
        int count = 0; JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) {
            JSONObject event = logs.optJSONObject(i);
            JSONObject attention = event == null ? null : event.optJSONObject("attention");
            if (matches(event, id) && attention != null && attention.optInt("reason_code", -999) == reason) count++;
        }
        return count;
    }
    private boolean matches(JSONObject event, int id) { return event != null && SENDER.equals(event.optString("pkg")) && event.optString("key").contains("|" + SENDER + "|" + id + "|"); }
    private boolean hasNotificationAction(int id, String action) {
        JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) {
            JSONObject event = logs.optJSONObject(i);
            if (matches(event, id) && action.equals(event.optString("action"))) return true;
        }
        return false;
    }
    private boolean hasAction(String action) {
        JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) if (action.equals(logs.optJSONObject(i).optString("action"))) return true;
        return false;
    }

    /** ACTION_DISMISS on a notification row follows SystemUI's ordinary user-dismiss path. */
    private void dismissThroughSystemUi(String title) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            for (AccessibilityNodeInfo root : roots()) {
                AccessibilityNodeInfo titleNode = findTitle(root, title);
                if (titleNode != null) {
                    AccessibilityNodeInfo current = titleNode;
                    for (int depth = 0; current != null && depth < 12; depth++, current = current.getParent()) {
                        if (current.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)) {
                            if (!current.performAction(AccessibilityNodeInfo.ACTION_DISMISS)) throw new AssertionError("SystemUI rejected individual dismiss for " + title);
                            return;
                        }
                    }
                }
            }
            boolean expanded = expandNotificationGroups();
            if (!expanded) scrollOne();
            SystemClock.sleep(250);
        }
        throw new AssertionError("No individual SystemUI dismissal target for " + title + "; accessibility=" + clip(uiText(), 6000));
    }
    private AccessibilityNodeInfo findTitle(AccessibilityNodeInfo node, String title) {
        if (node == null) return null;
        if (node.getText() != null && node.getText().toString().equals(title)) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = findTitle(node.getChild(i), title); if (found != null) return found; }
        return null;
    }
    private boolean expandNotificationGroups() {
        for (AccessibilityNodeInfo root : roots()) if (expand(root)) return true;
        return false;
    }
    private boolean expand(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_EXPAND)) return true;
        }
        String id = node.getViewIdResourceName();
        CharSequence description = node.getContentDescription();
        if (id != null && id.endsWith("/expand_button") && node.isClickable() && description != null
                && (description.toString().toLowerCase(java.util.Locale.ROOT).contains("expand") || description.toString().contains("展开"))) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) if (expand(node.getChild(i))) return true;
        return false;
    }
    private boolean scrollOne() {
        for (AccessibilityNodeInfo root : roots()) if (scroll(root)) return true;
        return false;
    }
    private boolean scroll(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (scroll(node.getChild(i))) return true;
        return false;
    }
    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        for (AccessibilityWindowInfo window : getUiAutomation().getWindows()) if (window.getRoot() != null) result.add(window.getRoot());
        if (result.isEmpty() && getUiAutomation().getRootInActiveWindow() != null) result.add(getUiAutomation().getRootInActiveWindow());
        return result;
    }
    private String uiText() { StringBuilder text = new StringBuilder(); for (AccessibilityNodeInfo root : roots()) text(root, text); return text.toString(); }
    private void text(AccessibilityNodeInfo node, StringBuilder text) {
        if (node == null || text.length() > 30000) return;
        if (node.getText() != null) text.append(node.getText()).append('\n');
        if (node.getContentDescription() != null) text.append(node.getContentDescription()).append('\n');
        for (int i = 0; i < node.getChildCount(); i++) text(node.getChild(i), text);
    }
    private void scenario(String name) throws Exception {
        String result = shell("am start -W -n " + SENDER + "/.MainActivity --es scenario " + name);
        if (result.contains("Error:") || result.contains("Exception")) throw new AssertionError(result);
    }
    private Set<Integer> activeIds() throws Exception {
        String dump = shell("dumpsys notification --noredact"); Set<Integer> result = new TreeSet<>();
        if (!dump.contains("Current Notification Manager state")) throw new AssertionError("Unexpected notification dump");
        boolean section = false; int indent = 0;
        for (String line : dump.split("\\r?\\n")) {
            if (line.trim().equals("Notification List:")) { section = true; indent = indentation(line); continue; }
            if (!section || line.trim().isEmpty()) continue;
            if (indentation(line) <= indent) break;
            Matcher match = RECORD.matcher(line);
            if (match.matches() && SENDER.equals(match.group(1))) { int id = Integer.parseInt(match.group(2)); if (SAMPLE_IDS.contains(id)) result.add(id); }
        }
        return result;
    }
    private String shell(String command) throws Exception {
        ParcelFileDescriptor file = getUiAutomation().executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private void stable(String name, Set<Integer> expected) throws Exception {
        long end = SystemClock.elapsedRealtime() + 800;
        do { if (!activeIds().equals(expected)) throw new AssertionError(name + ": got " + activeIds()); SystemClock.sleep(100); } while (SystemClock.elapsedRealtime() < end);
        check(name, true);
    }
    private void await(String name, Condition condition) throws Exception {
        long end = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        do { if (condition.evaluate()) return; SystemClock.sleep(150); } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError("Timed out: " + name + "; synthetic logs=" + clip(DemoStore.getLogs(target).toString(), 12000));
    }
    private void runCase(String name, CheckedAction action) { progress("CASE: " + name); try { action.run(); } catch (Throwable failed) { failure(name, failed); } }
    private void check(String name, boolean success) { if (!success) throw new AssertionError(name); passed++; report.append("PASS: ").append(name).append('\n'); progress("PASS: " + name); }
    private void failure(String name, Throwable error) { failed++; StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack)); report.append("FAIL: ").append(name).append('\n').append(stack); progress("FAIL: " + name + " — " + error.getMessage()); }
    private void progress(String text) { Bundle status = new Bundle(); status.putString("stream", text + "\n"); sendStatus(0, status); }
    private void evidence(String text) { report.append(text).append('\n'); progress(text); }
    private static int indentation(String line) { int i = 0; while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++; return i; }
    private static Set<Integer> set(Integer... values) { return new HashSet<>(Arrays.asList(values)); }
    private static boolean close(double a, double b) { return Double.isFinite(a) && Math.abs(a - b) < 1e-8; }
    private static String clip(String text, int limit) { return text.length() <= limit ? text : text.substring(0, limit); }
    private interface CheckedAction { void run() throws Exception; }
    private interface Condition { boolean evaluate() throws Exception; }
}
