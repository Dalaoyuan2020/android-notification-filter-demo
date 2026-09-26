package com.example.notificationdemo.filter;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Compact home presentation. Every status and count is bound from current app state. */
public final class home_page_view extends LinearLayout {
    private static final String[] FOLDER_TITLES = {"重要消息", "稍后处理", "已过滤", "通知记录"};
    private static final int[] FOLDER_COLORS = {ui_theme.YELLOW, ui_theme.BLUE, ui_theme.PURPLE, ui_theme.GREEN};
    private static final String[] FILTERS = {notification_ui_data.FILTER_IMPORTANT,
            notification_ui_data.FILTER_LATER, notification_ui_data.FILTER_FILTERED, notification_ui_data.FILTER_ALL};
    private final TextView date;
    private final TextView serviceText;
    private final View serviceDot;
    private final LinearLayout serviceStrip;
    private final TextView[] values = new TextView[4];
    private final folder_view[] folders = new folder_view[4];
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日", Locale.CHINA);

    public home_page_view(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        LinearLayout heading = row();
        TextView title = ui_theme.text(context, "通知", ui_theme.TITLE_SP, ui_theme.INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        heading.addView(title, new LayoutParams(0, -2, 1));
        date = ui_theme.text(context, "", 12, ui_theme.MUTED, false);
        date.setGravity(Gravity.END);
        heading.addView(date);
        addView(heading, fullWidth());
        space(8);

        serviceStrip = row();
        serviceStrip.setPadding(dp(12), dp(10), dp(12), dp(10));
        serviceStrip.setBackground(ui_theme.shape(context, ui_theme.SHEET, 8, ui_theme.BORDER));
        serviceStrip.setMinimumHeight(dp(48));
        serviceDot = new View(context);
        LayoutParams dotSize = new LayoutParams(dp(7), dp(7));
        dotSize.rightMargin = dp(8);
        serviceStrip.addView(serviceDot, dotSize);
        serviceText = ui_theme.text(context, "", 13, ui_theme.INK, false);
        serviceStrip.addView(serviceText, new LayoutParams(0, -2, 1));
        TextView settingsArrow = ui_theme.text(context, "›", 22, ui_theme.MUTED, false);
        settingsArrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        serviceStrip.addView(settingsArrow);
        addView(serviceStrip, fullWidth());
        space(12);

        LinearLayout overview = new LinearLayout(context);
        overview.setOrientation(VERTICAL);
        overview.setPadding(dp(14), dp(11), dp(14), dp(10));
        overview.setBackground(ui_theme.shape(context, ui_theme.SHEET, 6, ui_theme.BORDER));
        overview.setElevation(dp(2));
        overview.setRotation(-0.7f);
        overview.addView(ui_theme.text(context, "今日概览", 17, ui_theme.INK, true));
        View rule = new View(context);
        rule.setBackgroundColor(ui_theme.GRID);
        LayoutParams ruleSize = new LayoutParams(-1, dp(1));
        ruleSize.topMargin = dp(7);
        ruleSize.bottomMargin = dp(7);
        overview.addView(rule, ruleSize);
        LinearLayout statistics = row();
        statistics.setBaselineAligned(false);
        String[] names = {"今日通知", "重要", "稍后", "已过滤"};
        for (int i = 0; i < names.length; i++) {
            LinearLayout statistic = new LinearLayout(context);
            statistic.setOrientation(VERTICAL);
            values[i] = ui_theme.text(context, "—", 26, ui_theme.INK, true);
            statistic.addView(values[i]);
            statistic.addView(ui_theme.text(context, names[i], 11, ui_theme.MUTED, false));
            statistics.addView(statistic, new LayoutParams(0, -2, 1));
        }
        overview.addView(statistics, fullWidth());
        TextView note = ui_theme.text(context, notification_ui_data.HISTORY_NOTE, 10, ui_theme.MUTED, false);
        LayoutParams noteSize = fullWidth();
        noteSize.topMargin = dp(7);
        overview.addView(note, noteSize);
        LayoutParams paperSize = fullWidth();
        paperSize.leftMargin = dp(3);
        paperSize.rightMargin = dp(3);
        addView(overview, paperSize);
        space(6);

        for (int row = 0; row < 2; row++) {
            LinearLayout pair = row();
            pair.setGravity(Gravity.TOP);
            pair.setBaselineAligned(false);
            pair.setClipChildren(false);
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                folders[index] = new folder_view(context);
                LayoutParams folderSize = new LayoutParams(0, -2, 1);
                if (column == 0) {
                    folderSize.rightMargin = dp(8);
                }
                pair.addView(folders[index], folderSize);
            }
            addView(pair, fullWidth());
            if (row == 0) {
                space(4);
            }
        }
    }

    public void setServiceAction(View.OnClickListener listener) {
        serviceStrip.setOnClickListener(listener);
        serviceStrip.setFocusable(listener != null);
    }

    public void setFolderAction(folder_view.OnFolderClickListener listener) {
        for (int i = 0; i < folders.length; i++) {
            folders[i].setNavigation(FILTERS[i], listener);
        }
    }

    public void update(boolean accessGranted, boolean connected, notification_ui_data.Snapshot snapshot, long now) {
        date.setText("今日\n" + dateFormat.format(new Date(now)));
        boolean running = accessGranted && connected;
        String state = !accessGranted ? "通知使用权未开启"
                : connected ? "通知筛选服务正在运行" : "已授权 · 等待监听连接";
        serviceText.setText(state);
        serviceDot.setBackground(ui_theme.shape(getContext(), running ? ui_theme.ACCENT : ui_theme.WARNING, 4, 0));
        serviceStrip.setContentDescription(state + "，前往我的管理通知权限");
        serviceText.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        notification_ui_data.Counts today = snapshot.today;
        int[] todayValues = {today.notifications, today.important, today.later, today.filtered};
        for (int i = 0; i < values.length; i++) {
            values[i].setText(Integer.toString(todayValues[i]));
        }
        notification_ui_data.Counts total = snapshot.total;
        int[] folderCounts = {total.important, total.later, total.filtered, total.records};
        for (int i = 0; i < folders.length; i++) {
            folders[i].bind(FOLDER_TITLES[i], folderCounts[i], FOLDER_COLORS[i], i == 2 ? 1 : i == 0 ? 3 : 2);
        }
    }

    private LinearLayout row() {
        LinearLayout result = new LinearLayout(getContext());
        result.setOrientation(HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    private void space(int height) { addView(new View(getContext()), new LayoutParams(1, dp(height))); }
    private LayoutParams fullWidth() { return new LayoutParams(-1, -2); }
    private int dp(float value) { return ui_theme.dp(getContext(), value); }
}
