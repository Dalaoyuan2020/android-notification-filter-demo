package com.example.notificationdemo.filter;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native instrumentation integration test. Runs against the two installed debug APKs.
 * Prerequisites: sender POST_NOTIFICATIONS granted, filter notification access granted.
 * Checks the system's active NotificationRecord list, not historical dump entries.
 */
public final class SmokeInstrumentation extends Instrumentation {
    private static final String SENDER = "com.example.notificationdemo.sender";
    private static final Set<Integer> SAMPLE_IDS = ids(101, 102, 103, 104, 105, 200, 201, 202, 203, 301);
    private static final Set<Integer> BATCH_IDS = ids(101, 102, 103, 104, 105, 200, 201, 202, 203);
    private static final Set<Integer> KEPT_BATCH_IDS = ids(101, 103, 104, 105, 200, 201, 203);
    private static final Pattern RECORD = Pattern.compile("^\\s+NotificationRecord\\([^\\r\\n]*?\\bpkg=([^\\s]+)[^\\r\\n]*?\\bid=(-?\\d+)\\b.*$");
    private static final long TIMEOUT_MS = 15000;
    private Context target;
    private int passed;
    private int failed;
    private final StringBuilder report = new StringBuilder();
    private String lastDump = "";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        super.onStart();
        target = getTargetContext();
        report.append("NotificationFilterDemo Android integration smoke test\n");
        try {
            restoreDefaults();
            getUiAutomation();
            if (!FilterService.isConnected()) {
                runOnMainSync(() -> NotificationListenerService.requestRebind(new ComponentName(target, FilterService.class)));
            }
            await("notification listener connected", FilterService::isConnected);
            check("listener connected", true);
            runCase("observe batch preserves all samples and reports recommendations", this::observeBatch);
            runCase("automatic batch removes only ads and confirms system removals", this::automaticBatch);
            runCase("package outside configured targets remains untouched", this::outsideTarget);
            runCase("same notification ID changes from ad to urgent", this::updatedNotification);
        } catch (Throwable failure) {
            recordFailure("setup", failure);
        } finally {
            try {
                restoreDefaults();
                // Instrumentation finish can terminate the process before apply() reaches disk.
                // A synchronous commit waits for preceding writes and makes teardown durable.
                boolean committed = target.getApplicationContext()
                        .getSharedPreferences("notification_demo", Context.MODE_PRIVATE).edit()
                        .putBoolean("auto", false)
                        .putString("targets", DemoStore.DEFAULT_TARGETS)
                        .putString("keep", DemoStore.DEFAULT_KEEP_WORDS)
                        .putString("block", DemoStore.DEFAULT_BLOCK_WORDS)
                        .commit();
                check("default-setting synchronous commit succeeded", committed);
                assertDefaultsOnDisk();
                check("finish in observe mode with default targets and keywords", !DemoStore.getAuto(target)
                        && DemoStore.DEFAULT_TARGETS.equals(DemoStore.getTargets(target))
                        && DemoStore.DEFAULT_KEEP_WORDS.equals(DemoStore.getKeepWords(target))
                        && DemoStore.DEFAULT_BLOCK_WORDS.equals(DemoStore.getBlockWords(target)));
            } catch (Throwable failure) {
                recordFailure("restore defaults", failure);
            }
        }
        report.append("\nRESULT: ").append(passed).append(" checks passed, ").append(failed).append(" failures\n");
        Bundle result = new Bundle();
        result.putString("stream", "\n" + report);
        result.putInt("passed", passed);
        result.putInt("failed", failed);
        result.putString("result", failed == 0 ? "PASS" : "FAIL");
        finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void observeBatch() throws Exception {
        restoreDefaults();
        clearSamplesAndLogs();
        scenario("batch");
        await("observe batch publishes all 9 samples", () -> activeSampleIds().equals(BATCH_IDS));
        await("observe batch recommends both ad cards", () -> hasLog(102, "建议清除", null) && hasLog(202, "建议清除", null));
        assertStableIds("observe leaves every notification active", BATCH_IDS, 1200);
        check("observe: no removal request or confirmation", !hasAction("请求清除") && !hasAction("已清除"));
        check("observe: recommendations for 102 and 202", hasLog(102, "建议清除", null) && hasLog(202, "建议清除", null));
    }

    private void automaticBatch() throws Exception {
        restoreDefaults();
        clearSamplesAndLogs();
        DemoStore.setAuto(target, true);
        scenario("batch");
        await("automatic batch leaves expected 7 samples", () -> activeSampleIds().equals(KEPT_BATCH_IDS));
        await("both ad removals have listener confirmation", () -> hasLog(102, "已清除", null) && hasLog(202, "已清除", null));
        assertStableIds("auto: precisely 102 and 202 removed", KEPT_BATCH_IDS, 1200);
        check("auto: only IDs 102/202 have confirmed removals", confirmedRemovalIds().equals(ids(102, 202)));
        check("conflict 103 protected", hasLog(103, "保留", "紧急"));
        check("ongoing ad 105 protected", hasLog(105, "跳过", null));
        check("group summary 200 protected", hasLog(200, "跳过", null));
        check("empty-body notification 104 preserved", hasLog(104, "保留", null));
    }

    private void outsideTarget() throws Exception {
        restoreDefaults();
        clearSamplesAndLogs();
        DemoStore.setTargets(target, "com.example.unconfigured.app");
        DemoStore.setAuto(target, true);
        scenario("ad");
        await("non-target ad remains active", () -> activeSampleIds().equals(ids(102)));
        await("non-target ad logged as skipped", () -> hasLog(102, "跳过", null));
        assertStableIds("auto: non-target 102 remains active", ids(102), 1200);
        check("non-target: no removal request", !hasAction("请求清除") && !hasAction("已清除"));
    }

    private void updatedNotification() throws Exception {
        restoreDefaults();
        clearSamplesAndLogs();
        // First update the same still-active system record in observe mode.
        scenario("update_ad");
        await("301 ad observed", () -> activeSampleIds().equals(ids(301)) && hasLog(301, "建议清除", null));
        scenario("update_urgent");
        await("301 active record updated to urgent", () -> activeSampleIds().equals(ids(301)) && hasLog(301, "保留", "紧急"));
        check("same-ID update is re-evaluated in observe mode", hasLog(301, "建议清除", null) && hasLog(301, "保留", "紧急"));

        // Then verify auto removal of an ad followed by an urgent replacement with the same ID.
        clearSamplesAndLogs();
        DemoStore.setAuto(target, true);
        scenario("update_ad");
        await("301 ad removed with callback confirmation", () -> activeSampleIds().isEmpty() && hasLog(301, "已清除", null));
        scenario("update_urgent");
        await("urgent replacement of 301 remains", () -> activeSampleIds().equals(ids(301)) && hasLog(301, "保留", "紧急"));
        assertStableIds("auto: urgent replacement 301 stays active", ids(301), 1200);
        check("301 confirmed ad removal and later urgent keep both recorded", hasLog(301, "已清除", null) && hasLog(301, "保留", "紧急"));
    }

    private void restoreDefaults() {
        DemoStore.setAuto(target, false);
        DemoStore.setTargets(target, DemoStore.DEFAULT_TARGETS);
        DemoStore.setKeepWords(target, DemoStore.DEFAULT_KEEP_WORDS);
        DemoStore.setBlockWords(target, DemoStore.DEFAULT_BLOCK_WORDS);
    }

    private void assertDefaultsOnDisk() throws Exception {
        File file = new File(target.getApplicationContext().getDataDir(), "shared_prefs/notification_demo.xml");
        Map<String, String> values = new HashMap<>();
        try (InputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                String name = parser.getAttributeValue(null, "name");
                if ("boolean".equals(parser.getName()) && "auto".equals(name)) {
                    values.put(name, parser.getAttributeValue(null, "value"));
                } else if ("string".equals(parser.getName())
                        && ("targets".equals(name) || "keep".equals(name) || "block".equals(name))) {
                    values.put(name, parser.nextText());
                }
            }
        }
        check("on-disk preferences contain observe mode and all default rules", "false".equals(values.get("auto"))
                && DemoStore.DEFAULT_TARGETS.equals(values.get("targets"))
                && DemoStore.DEFAULT_KEEP_WORDS.equals(values.get("keep"))
                && DemoStore.DEFAULT_BLOCK_WORDS.equals(values.get("block")));
    }

    private void clearSamplesAndLogs() throws Exception {
        DemoStore.setAuto(target, false);
        scenario("clear");
        await("sender samples cleared", () -> activeSampleIds().isEmpty());
        // Allow queued removal callbacks to drain before isolating this case's log.
        waitForIdleSync();
        DemoStore.clearLogs(target);
    }

    private void scenario(String scenario) throws Exception {
        String output = shell("am start -W -n " + SENDER + "/.MainActivity --es scenario " + scenario);
        if (output.contains("Error:") || output.contains("Exception")) {
            throw new AssertionError("Cannot launch sender scenario " + scenario + ": " + output);
        }
    }

    private Set<Integer> activeSampleIds() throws Exception {
        lastDump = shell("dumpsys notification --noredact");
        if (!lastDump.contains("Current Notification Manager state")) {
            throw new AssertionError("Unexpected dumpsys output: " + clip(lastDump, 1000));
        }
        Set<Integer> active = new TreeSet<>();
        boolean inActiveList = false;
        int sectionIndent = 0;
        for (String line : lastDump.split("\\r?\\n")) {
            if (line.trim().equals("Notification List:")) {
                inActiveList = true;
                sectionIndent = indentation(line);
                continue;
            }
            if (!inActiveList || line.trim().isEmpty()) continue;
            // AOSP prints active records indented beneath this one exact section.
            // Stop at mArchive or any other section, even if historical records follow.
            if (indentation(line) <= sectionIndent) break;
            Matcher match = RECORD.matcher(line);
            if (match.matches() && SENDER.equals(match.group(1))) {
                int id = Integer.parseInt(match.group(2));
                if (SAMPLE_IDS.contains(id)) active.add(id);
            }
        }
        return active;
    }

    private Set<Integer> confirmedRemovalIds() {
        Set<Integer> removed = new TreeSet<>();
        JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) {
            JSONObject log = logs.optJSONObject(i);
            if (log == null || !SENDER.equals(log.optString("pkg")) || !"已清除".equals(log.optString("action"))) continue;
            Integer id = notificationId(log.optString("key"));
            if (id != null) removed.add(id);
        }
        return removed;
    }

    private boolean hasLog(int id, String action, String titleFragment) {
        JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) {
            JSONObject log = logs.optJSONObject(i);
            if (log == null || !SENDER.equals(log.optString("pkg")) || !action.equals(log.optString("action"))) continue;
            Integer logId = notificationId(log.optString("key"));
            if (logId != null && logId == id && (titleFragment == null || log.optString("title").contains(titleFragment))) return true;
        }
        return false;
    }

    private boolean hasAction(String action) {
        JSONArray logs = DemoStore.getLogs(target);
        for (int i = 0; i < logs.length(); i++) {
            JSONObject log = logs.optJSONObject(i);
            if (log != null && SENDER.equals(log.optString("pkg")) && action.equals(log.optString("action"))) return true;
        }
        return false;
    }

    private Integer notificationId(String key) {
        String[] fields = key.split("\\|");
        if (fields.length < 3 || !SENDER.equals(fields[1])) return null;
        try { return Integer.valueOf(fields[2]); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String shell(String command) throws Exception {
        ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void await(String description, Condition condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        do {
            if (condition.evaluate()) return;
            SystemClock.sleep(200);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Timed out: " + description + "\nActive sample IDs: " + activeSampleIds()
                + "\nRecent app logs: " + clip(DemoStore.getLogs(target).toString(), 9000)
                + "\nActive dump excerpt: " + clip(activeDumpExcerpt(), 7000));
    }

    private void assertStableIds(String description, Set<Integer> expected, long millis) throws Exception {
        long end = SystemClock.elapsedRealtime() + millis;
        do {
            Set<Integer> current = activeSampleIds();
            if (!current.equals(expected)) throw new AssertionError(description + ": expected " + expected + ", actual " + current);
            SystemClock.sleep(200);
        } while (SystemClock.elapsedRealtime() < end);
        check(description + " " + expected, true);
    }

    private String activeDumpExcerpt() {
        int start = lastDump.indexOf("  Notification List:");
        return start < 0 ? lastDump : lastDump.substring(start);
    }

    private void runCase(String name, CheckedAction action) {
        progress("CASE: " + name);
        try { action.run(); }
        catch (Throwable failure) { recordFailure(name, failure); }
    }

    private void check(String name, boolean success) {
        if (!success) throw new AssertionError(name);
        passed++;
        report.append("PASS: ").append(name).append('\n');
        progress("PASS: " + name);
    }

    private void recordFailure(String name, Throwable failure) {
        failed++;
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        report.append("FAIL: ").append(name).append('\n').append(stack).append('\n');
        progress("FAIL: " + name + " — " + failure.getMessage());
    }

    private void progress(String message) {
        Bundle state = new Bundle();
        state.putString("stream", message + "\n");
        sendStatus(0, state);
    }

    private static int indentation(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) index++;
        return index;
    }

    private static Set<Integer> ids(Integer... values) { return new HashSet<>(Arrays.asList(values)); }
    private static String clip(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private interface CheckedAction { void run() throws Exception; }
    private interface Condition { boolean evaluate() throws Exception; }
}
