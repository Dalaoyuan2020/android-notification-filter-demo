package com.example.notificationdemo.filter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Retained-record browser: filtering is presentational; full decisions remain available in details. */
public final class messages_page_view extends LinearLayout {
    public interface OnFilterSelectedListener { void onFilterSelected(String filter); }

    private static final int MAX_VISIBLE_LOGS = 80;
    private static final String[] FILTERS = {notification_ui_data.FILTER_ALL, notification_ui_data.FILTER_IMPORTANT,
            notification_ui_data.FILTER_LATER, notification_ui_data.FILTER_FILTERED};
    private static final String[] LABELS = {"全部", "重要", "稍后", "已过滤"};
    private final Button[] filterButtons = new Button[4];
    private final TextView todayCount;
    private final TextView logCount;
    private final Button clearButton;
    private final LinearLayout list;
    private OnFilterSelectedListener filterListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    public messages_page_view(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LinearLayout header = row();
        TextView title = text("消息", ui_theme.TITLE_SP, ui_theme.INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        header.addView(title, new LayoutParams(0, -2, 1));
        todayCount = text("", 12, ui_theme.MUTED, false);
        todayCount.setGravity(Gravity.END);
        header.addView(todayCount);
        addView(header, fullWidth());
        space(this, 18);

        LinearLayout filters = row();
        filters.setBaselineAligned(false);
        for (int i = 0; i < FILTERS.length; i++) {
            final String filter = FILTERS[i];
            Button tab = button(LABELS[i]);
            tab.setTextSize(13);
            tab.setPadding(dp(5), dp(8), dp(5), dp(8));
            tab.setMinWidth(0);
            tab.setMinimumWidth(0);
            tab.setMinHeight(dp(48));
            tab.setMinimumHeight(dp(48));
            tab.setOnClickListener(view -> {
                if (filterListener != null) {
                    filterListener.onFilterSelected(filter);
                }
            });
            filterButtons[i] = tab;
            LayoutParams size = new LayoutParams(0, -2, 1);
            if (i < FILTERS.length - 1) {
                size.setMarginEnd(dp(7));
            }
            filters.addView(tab, size);
        }
        addView(filters, fullWidth());
        space(this, 10);
        addView(text(notification_ui_data.HISTORY_NOTE, 11, ui_theme.MUTED, false), fullWidth());
        LinearLayout actions = row();
        logCount = text("", 11, ui_theme.MUTED, false);
        actions.addView(logCount, new LayoutParams(0, -2, 1));
        Button definitions = smallButton("分类说明");
        definitions.setOnClickListener(view -> new AlertDialog.Builder(getContext())
                .setTitle("统计与分类说明")
                .setMessage(notification_ui_data.HISTORY_NOTE + "\n\n" + notification_ui_data.GROUPING_NOTE)
                .setPositiveButton("知道了", null).show());
        actions.addView(definitions);
        clearButton = smallButton("清空");
        clearButton.setContentDescription("清空全部验证记录与短时注意力记录");
        LayoutParams clearSize = new LayoutParams(-2, -2);
        clearSize.setMarginStart(dp(6));
        actions.addView(clearButton, clearSize);
        addView(actions, fullWidth());
        space(this, 8);
        list = column();
        addView(list, fullWidth());
    }

    public void setOnFilterSelectedListener(OnFilterSelectedListener listener) { filterListener = listener; }
    public void setOnClearLogsListener(View.OnClickListener listener) { clearButton.setOnClickListener(listener); }

    public void render(JSONArray allEntries, String requestedFilter) {
        String filter = notification_ui_data.normalizeFilter(requestedFilter);
        notification_ui_data.Snapshot snapshot = notification_ui_data.snapshot(allEntries, System.currentTimeMillis());
        todayCount.setText("今日 " + snapshot.today.notifications + " 条");
        todayCount.setContentDescription("今日 " + snapshot.today.notifications + " 条通知判断记录，按本机留存日志计数");
        for (int i = 0; i < FILTERS.length; i++) {
            boolean selected = FILTERS[i].equals(filter);
            Button tab = filterButtons[i];
            tab.setSelected(selected);
            tab.setTextColor(ui_theme.INK);
            tab.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16426956),
                    ui_theme.shape(getContext(), selected ? ui_theme.SOFT_YELLOW : ui_theme.SHEET,
                            6, selected ? ui_theme.YELLOW : ui_theme.BORDER), null));
            tab.setElevation(selected ? dp(1) : 0);
            tab.setContentDescription(LABELS[i] + (selected ? "，已选中" : "，筛选通知记录"));
        }
        JSONArray entries = notification_ui_data.filter(allEntries, filter);
        int count = entries.length();
        logCount.setText(count + " 条 · 最新在上" + (count > MAX_VISIBLE_LOGS ? "\n显示最近 " + MAX_VISIBLE_LOGS + " 条" : ""));
        list.removeAllViews();
        if (count == 0) {
            renderEmpty(!notification_ui_data.FILTER_ALL.equals(filter));
            return;
        }
        for (int i = 0; i < Math.min(count, MAX_VISIBLE_LOGS); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            message_bubble_view bubble = new message_bubble_view(getContext(), entry);
            JSONArray models = entry.optJSONArray("models");
            if (models != null && models.length() > 0) {
                LinearLayout modelContainer = bubble.getModelContainer();
                modelContainer.setVisibility(VISIBLE);
                String comparison = entry.optString("comparison", "");
                if (!comparison.isEmpty()) {
                    modelContainer.addView(text("路线结论 · " + comparison, 11, ui_theme.MUTED, true));
                    space(modelContainer, 6);
                }
                renderProbabilityOverview(modelContainer, models);
            }
            bubble.setDetailsAction(view -> showDetails(entry));
            list.addView(bubble, fullWidth());
            if (i < Math.min(count, MAX_VISIBLE_LOGS) - 1) {
                space(list, 12);
            }
        }
    }

    private void renderEmpty(boolean filtered) {
        LinearLayout empty = column();
        empty.setPadding(dp(18), dp(26), dp(18), dp(26));
        empty.setBackground(ui_theme.shape(getContext(), ui_theme.SHEET, 10, ui_theme.BORDER));
        empty.addView(text(filtered ? "暂无该分类记录" : "等待第一条通知", 17, ui_theme.INK, true));
        space(empty, 8);
        empty.addView(text(filtered ? "试试上方“全部”，或等待新的通知。"
                : "前往“我的”开启通知使用权并打开测试发送器，也可以重新扫描现有通知。", 13, ui_theme.MUTED, false));
        list.addView(empty, fullWidth());
    }

    private void renderProbabilityOverview(LinearLayout parent, JSONArray results) {
        LinearLayout overview = row();
        overview.setGravity(Gravity.TOP);
        overview.setBaselineAligned(false);
        int count = Math.min(3, results.length());
        for (int i = 0; i < count; i++) {
            JSONObject model = results.optJSONObject(i);
            if (model == null) {
                continue;
            }
            LinearLayout box = column();
            box.setPadding(dp(6), dp(8), dp(6), dp(8));
            box.setBackground(ui_theme.shape(getContext(), ui_theme.SHEET, 7, ui_theme.BORDER));
            LayoutParams size = new LayoutParams(0, -2, 1);
            if (i < count - 1) {
                size.setMarginEnd(dp(4));
            }
            overview.addView(box, size);
            overviewLine(box, "路线 " + (i + 1), 10, ui_theme.MUTED, false);
            overviewLine(box, conciseModel(model.optString("model", "")), 11, ui_theme.INK, true);
            overviewLine(box, model.optString("label", "模型路线"), 10, ui_theme.MUTED, false);
            space(box, 5);
            boolean success = model.optBoolean("success", false);
            overviewLine(box, success && !model.optBoolean("has_probability", false) ? "choice 换算" : "p_jev",
                    11, ui_theme.MUTED, false);
            double original = model.optDouble("p_jev", Double.NaN);
            double fused = model.optDouble("p_final", Double.NaN);
            overviewLine(box, probability(original), 12, ui_theme.INK, true);
            String arrow = "＝";
            int color = ui_theme.INK;
            if (Double.isFinite(original) && Double.isFinite(fused)) {
                double change = fused - original;
                arrow = change > 0.05 ? "↑" : change < -0.05 ? "↓" : "＝";
                color = change > 0.05 ? ui_theme.ACCENT : change < -0.05 ? ui_theme.WARNING : ui_theme.INK;
            }
            space(box, 4);
            overviewLine(box, arrow + " p_final", 11, color, false);
            overviewLine(box, probability(fused), 12, color, true);
            space(box, 5);
            overviewLine(box, model.optLong("latency_ms", 0) + "ms "
                    + (success ? model.optString("action", "KEEP") : "失败"), 10,
                    success ? ui_theme.ACCENT : ui_theme.WARNING, true);
        }
        parent.addView(overview, fullWidth());
    }

    private void overviewLine(LinearLayout parent, String value, float size, int color, boolean bold) {
        TextView line = text(value, size, color, bold);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        parent.addView(line, fullWidth());
    }

    private static String conciseModel(String model) {
        switch (model) {
            case "local-systemone-ft": return "ft 微调";
            case "local-systemone-v1": return "v1 原版";
            case "typesafe-jev": return "typesafe 官方";
            case "bocha-jev": return "bocha 博查";
            default: return model.isEmpty() ? "未记录模型" : model;
        }
    }

    private void showDetails(JSONObject entry) {
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(14), dp(20), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        detail(content, "来源 App", entry.optString("pkg", "未记录来源"));
        long time = entry.optLong("time", 0);
        detail(content, "时间", time > 0 ? dateFormat.format(new Date(time)) : "未记录");
        detail(content, "标题", entry.optString("title", ""));
        detail(content, "完整正文", entry.optString("text", ""));
        detail(content, "处理动作", entry.optString("action", "记录"));
        detail(content, "判断原因", entry.optString("reason", ""));
        detail(content, "通知标识", entry.optString("key", ""));
        String comparison = entry.optString("comparison", "");
        if (!comparison.isEmpty()) {
            detail(content, "路线结论", comparison + "\n路线编号按本条记录中的顺序显示。");
        }
        JSONArray models = entry.optJSONArray("models");
        if (models != null) {
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) {
                    continue;
                }
                space(content, 12);
                content.addView(text("路线 " + (i + 1) + " · " + model.optString("label", "模型路线"),
                        17, ui_theme.INK, true));
                detail(content, "模型 ID / 协议", model.optString("model", "") + "\n" + model.optString("protocol", ""));
                detail(content, "原始 → 融合概率", "p_jev " + probability(model.optDouble("p_jev", Double.NaN))
                        + " → p_final " + probability(model.optDouble("p_final", Double.NaN))
                        + (model.optBoolean("has_probability", false) ? "" : "\n未提供原始概率，成功时由 choice 换算。"));
                if (model.has("p_short") || model.has("n")) {
                    detail(content, "短时注意力", "p_short " + probability(model.optDouble("p_short", Double.NaN))
                            + " · 有效证据 n " + probability(model.optDouble("n", Double.NaN)));
                }
                detail(content, "请求结果", (model.optBoolean("success", false) ? "成功" : "失败") + " · "
                        + model.optLong("latency_ms", 0) + " ms · " + model.optString("action", ""));
                detail(content, "原因", model.optString("reason", ""));
                if (!model.optString("error", "").isEmpty()) {
                    detail(content, "错误", model.optString("error", ""));
                }
            }
        }
        JSONObject attention = entry.optJSONObject("attention");
        if (attention != null) {
            detail(content, "行为记录", attention.toString());
        }
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("通知详情").setView(scroll)
                .setPositiveButton("关闭", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ui_theme.ACCENT);
    }

    private void detail(LinearLayout parent, String label, String value) {
        space(parent, 12);
        parent.addView(text(label, 12, ui_theme.MUTED, true));
        space(parent, 4);
        TextView body = text(value.isEmpty() ? "（未记录）" : value, 14, ui_theme.INK, false);
        body.setTextIsSelectable(true);
        parent.addView(body, fullWidth());
    }

    private static String probability(double value) {
        return Double.isFinite(value) ? String.format(Locale.CHINA, "%.3f", value) : "—";
    }

    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        ui_theme.button(button, false);
        return button;
    }

    private Button smallButton(String label) {
        Button button = button(label);
        button.setTextSize(11);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), dp(7), dp(8), dp(7));
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return ui_theme.text(getContext(), value, size, color, bold);
    }
    private LinearLayout row() { LinearLayout view = column(); view.setOrientation(HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(VERTICAL); return view; }
    private void space(LinearLayout parent, int height) { parent.addView(new View(getContext()), new LayoutParams(1, dp(height))); }
    private LayoutParams fullWidth() { return new LayoutParams(-1, -2); }
    private int dp(float value) { return ui_theme.dp(getContext(), value); }
}
