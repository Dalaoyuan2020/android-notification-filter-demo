package com.example.notificationdemo.filter;

import android.content.Context;
import org.json.JSONObject;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import javax.net.ssl.HttpsURLConnection;

/** Optional fire-and-forget event transport: bounded, no redirect, no retry or response logging. */
final class AttentionReporter {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16));
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor();
    private AttentionReporter() {}
    static void submit(Context context, JSONObject event, String body) {
        Context app = context.getApplicationContext();
        AttentionStore.Config config = AttentionStore.loadConfig(app);
        if (!config.uploadEnabled) return;
        try {
            JSONObject payload = new JSONObject(event.toString());
            if (config.uploadBody) payload.put("正文", body == null ? "" : body);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65536) return;
            WORKER.execute(() -> {
                AttentionStore.Config latest = AttentionStore.loadConfig(app);
                if (!latest.uploadEnabled || latest.revision != config.revision) return;
                HttpsURLConnection connection = null;
                ScheduledFuture<?> deadline = null;
                try {
                    String base = AttentionStore.validateServiceUrl(config.serviceUrl).toString();
                    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    URL url = new URL(base.endsWith("/events") ? base : base + "/events");
                    connection = (HttpsURLConnection) url.openConnection();
                    HttpsURLConnection activeConnection = connection;
                    deadline = DEADLINES.schedule(activeConnection::disconnect, 5, TimeUnit.SECONDS);
                    connection.setInstanceFollowRedirects(false);
                    connection.setUseCaches(false);
                    connection.setConnectTimeout(3000);
                    connection.setReadTimeout(3000);
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    if (!config.serviceToken.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + config.serviceToken);
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
                    connection.getResponseCode();
                } catch (Exception ignored) {
                    // Dropped by design; do not log notification contents or server diagnostics.
                } finally {
                    if (deadline != null) deadline.cancel(false);
                    if (connection != null) connection.disconnect();
                }
            });
        } catch (org.json.JSONException | RejectedExecutionException ignored) {
            // Uploads must never block or alter the notification workflow.
        }
    }
}
