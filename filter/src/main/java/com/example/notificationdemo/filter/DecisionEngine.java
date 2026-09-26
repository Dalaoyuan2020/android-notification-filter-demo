package com.example.notificationdemo.filter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Android-independent, conservative rules. Only REMOVE may be passed to cancellation. */
public final class DecisionEngine {
    private DecisionEngine() {}

    public enum Action { KEEP, SKIP, REMOVE }

    public static final class Input {
        public final String packageName;
        public final String title;
        public final String text;
        public final boolean ongoing;
        public final boolean clearable;
        public final boolean groupSummary;
        public final String category;

        public Input(String packageName, String title, String text, boolean ongoing,
                     boolean clearable, boolean groupSummary, String category) {
            this.packageName = safe(packageName);
            this.title = safe(title);
            this.text = safe(text);
            this.ongoing = ongoing;
            this.clearable = clearable;
            this.groupSummary = groupSummary;
            this.category = safe(category);
        }
    }

    public static final class Rules {
        public final Set<String> targets;
        public final Set<String> keepWords;
        public final Set<String> blockWords;

        public Rules(String targets, String keepWords, String blockWords) {
            this.targets = parseList(targets);
            this.keepWords = parseList(keepWords);
            this.blockWords = parseList(blockWords);
        }
    }

    public static final class Result {
        public final Action action;
        public final String reason;

        private Result(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }
    }

    public static Set<String> parseList(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String part : safe(value).split("[,，\\r\\n]+")) {
            String item = part.trim().toLowerCase(Locale.ROOT);
            if (!item.isEmpty()) result.add(item);
        }
        return Collections.unmodifiableSet(result);
    }

    public static Result decide(Input input, Rules rules) {
        if (input == null || rules == null) return keep("规则或通知数据缺失，默认保留");
        if (!rules.targets.contains(input.packageName.toLowerCase(Locale.ROOT))) {
            return skip("来源不在目标包名列表内");
        }
        if (input.ongoing || !input.clearable) return skip("持续通知或系统标记为不可清除");
        if (input.groupSummary) return skip("分组汇总通知受保护，避免连带清除整个分组");
        String category = input.category.toLowerCase(Locale.ROOT);
        if (category.equals("call") || category.equals("navigation") || category.equals("alarm")) {
            return skip("通话、导航或闹钟类别受保护");
        }
        String content = (input.title + "\n" + input.text).trim().toLowerCase(Locale.ROOT);
        if (content.isEmpty()) return keep("没有可读标题或正文，默认保留");
        for (String word : rules.keepWords) {
            if (content.contains(word)) return keep("命中保留词「" + word + "」，保留规则优先");
        }
        for (String word : rules.blockWords) {
            if (content.contains(word)) {
                return new Result(Action.REMOVE, "命中清除词「" + word + "」");
            }
        }
        return keep("未命中清除词，默认保留");
    }

    private static Result keep(String reason) { return new Result(Action.KEEP, reason); }
    private static Result skip(String reason) { return new Result(Action.SKIP, reason); }
    private static String safe(String value) { return value == null ? "" : value; }
}
