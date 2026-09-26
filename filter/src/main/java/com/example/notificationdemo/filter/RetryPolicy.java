package com.example.notificationdemo.filter;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Shared finite budget: at most two retries, only for rate limiting/overload. */
public final class RetryPolicy {
    public static final int MAX_RETRIES = 2;
    public static final long INITIAL_BACKOFF_MS = 500;
    private RetryPolicy() {}

    public static long nextDelayMillis(int status, int retriesUsed, long elapsedMs, long totalBudgetMs,
                                       String retryAfter, long nowEpochMs) {
        if ((status != 429 && status != 529) || retriesUsed < 0 || retriesUsed >= MAX_RETRIES) return -1;
        long exponential = INITIAL_BACKOFF_MS << retriesUsed;
        long delay = Math.max(exponential, retryAfterMillis(retryAfter, nowEpochMs));
        return delay < remainingMillis(elapsedMs, totalBudgetMs) ? delay : -1;
    }

    public static long remainingMillis(long elapsedMs, long totalBudgetMs) {
        if (totalBudgetMs <= 0 || elapsedMs >= totalBudgetMs) return 0;
        return totalBudgetMs - Math.max(0, elapsedMs);
    }

    public static int timeoutMillis(int preferredMs, long elapsedMs, long totalBudgetMs) {
        return (int) Math.min(Math.max(0, preferredMs), remainingMillis(elapsedMs, totalBudgetMs));
    }

    static long retryAfterMillis(String value, long nowEpochMs) {
        if (value == null || value.trim().isEmpty()) return 0;
        String trimmed = value.trim();
        if (trimmed.matches("[0-9]+")) {
            if (trimmed.length() > 18) return Long.MAX_VALUE;
            BigInteger millis = new BigInteger(trimmed).multiply(BigInteger.valueOf(1000));
            return millis.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : millis.longValue();
        }
        try {
            long timestamp = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            if (timestamp <= nowEpochMs) return 0;
            return Math.subtractExact(timestamp, nowEpochMs);
        } catch (DateTimeParseException | ArithmeticException failure) { return 0; }
    }
}
