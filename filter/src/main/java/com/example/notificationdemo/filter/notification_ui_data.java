package com.example.notificationdemo.filter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Read-only presentation of retained log records, never a second notification classifier. */
public final class notification_ui_data {
    public static final String FILTER_ALL = "all";
    public static final String FILTER_IMPORTANT = "important";
    public static final String FILTER_LATER = "later";
    public static final String FILTER_FILTERED = "filtered";
    public static final String HISTORY_NOTE = "仅统计本机留存的最近150条记录；同一通知可有多条记录。";
    public static final String GROUPING_NOTE = "重要仅指保留记录；已过滤仅指系统确认清除；其余判断记录归入稍后。行为记录只在通知记录中显示。";

    private notification_ui_data() {}

    public static final class Counts {
        /** All retained JSON records, including attention feedback and service diagnostics. */
        public final int records;
        /** Recognized notification decision records; repeated events are not deduplicated. */
        public final int notifications;
        public final int important;
        public final int later;
        public final int filtered;

        private Counts(int records, int important, int later, int filtered) {
            this.records = records;
            this.important = important;
            this.later = later;
            this.filtered = filtered;
            this.notifications = important + later + filtered;
        }
    }

    public static final class Snapshot {
        public final Counts today;
        public final Counts total;

        private Snapshot(Counts today, Counts total) {
            this.today = today;
            this.total = total;
        }
    }

    /** Uses the device's current local date, including daylight-saving changes at midnight. */
    public static Snapshot snapshot(JSONArray logs, long nowMillis) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        long dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        tally today = new tally();
        tally total = new tally();
        if (logs != null) {
            for (int index = 0; index < logs.length(); index++) {
                JSONObject row = logs.optJSONObject(index);
                if (row == null) continue;
                String group = notificationGroup(row);
                total.add(group);
                Object timestamp = row.opt("time");
                if (timestamp instanceof Number) {
                    long time = ((Number) timestamp).longValue();
                    if (time >= dayStart && time < dayEnd) today.add(group);
                }
            }
        }
        return new Snapshot(today.counts(), total.counts());
    }

    /** "All" retains behavior and diagnostic rows; category views contain only decisions. */
    public static boolean matches(JSONObject row, String filter) {
        if (row == null) return false;
        String selected = normalizeFilter(filter);
        return FILTER_ALL.equals(selected) || selected.equals(notificationGroup(row));
    }

    /** Returns a new array in original (normally newest-first) order without modifying rows. */
    public static JSONArray filter(JSONArray logs, String filter) {
        JSONArray result = new JSONArray();
        if (logs != null) {
            for (int index = 0; index < logs.length(); index++) {
                JSONObject row = logs.optJSONObject(index);
                if (matches(row, filter)) result.put(row);
            }
        }
        return result;
    }

    public static String normalizeFilter(String filter) {
        if (FILTER_IMPORTANT.equals(filter) || FILTER_LATER.equals(filter) || FILTER_FILTERED.equals(filter)) {
            return filter;
        }
        return FILTER_ALL;
    }

    public static String filterLabel(String filter) {
        switch (normalizeFilter(filter)) {
            case FILTER_IMPORTANT: return "重要消息";
            case FILTER_LATER: return "稍后处理";
            case FILTER_FILTERED: return "已过滤";
            default: return "通知记录";
        }
    }

    private static String notificationGroup(JSONObject row) {
        String action = row.optString("action", "");
        // Feedback rows repeat notification content but are not additional notification decisions.
        if ("注意力".equals(action) || row.optJSONObject("attention") != null) return null;
        Object key = row.opt("key");
        // Scanner/service diagnostics have no notification identity and must not inflate counts.
        if (!(key instanceof String) || ((String) key).trim().isEmpty()) return null;
        switch (action) {
            case "保留": return FILTER_IMPORTANT;
            case "已清除": return FILTER_FILTERED;
            case "建议清除":
            case "请求清除":
            case "未确认":
            case "跳过":
            case "模型对照":
            case "结果作废": return FILTER_LATER;
            default: return null;
        }
    }

    private static final class tally {
        int records;
        int important;
        int later;
        int filtered;

        void add(String group) {
            records++;
            if (FILTER_IMPORTANT.equals(group)) important++;
            else if (FILTER_LATER.equals(group)) later++;
            else if (FILTER_FILTERED.equals(group)) filtered++;
        }

        Counts counts() { return new Counts(records, important, later, filtered); }
    }
}
