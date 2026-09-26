package com.example.notificationdemo.filter;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A compact paper directory. The host owns the retained forms and their actions. */
public final class my_page_view extends LinearLayout {
    public interface OnEntrySelectedListener { void onEntrySelected(String entry); }
    public static final String PERMISSIONS = "permissions";
    public static final String RULES = "rules";
    public static final String AUTOMATIC = "automatic";
    public static final String ADVANCED = "advanced";
    public static final String GUIDE = "guide";
    public static final String ABOUT = "about";
    private OnEntrySelectedListener listener;
    private final paper_entry permissionEntry;
    private final paper_entry rulesEntry;
    private final paper_entry automaticEntry;
    private final paper_entry aboutEntry;

    public my_page_view(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        TextView heading = text("我的", ui_theme.TITLE_SP, ui_theme.INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        addView(heading, fullWidth());

        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(VERTICAL);
        identity.setPadding(dp(18), dp(15), dp(18), dp(15));
        identity.setBackground(ui_theme.shape(context, ui_theme.SHEET, 10, ui_theme.BORDER));
        identity.setElevation(dp(1));
        identity.setRotation(-0.5f);
        identity.addView(text("Attention", 25, ui_theme.INK, true));
        TextView subtitle = text("本地优先的消息辅助工具", 13, ui_theme.MUTED, false);
        LayoutParams subtitlePosition = fullWidth();
        subtitlePosition.topMargin = dp(5);
        identity.addView(subtitle, subtitlePosition);
        LayoutParams identityPosition = fullWidth();
        identityPosition.topMargin = dp(15);
        identityPosition.bottomMargin = dp(20);
        addView(identity, identityPosition);

        LinearLayout notificationGroup = group("通知与规则", ui_theme.SOFT_BLUE);
        permissionEntry = entry(notificationGroup, PERMISSIONS, "通知权限", "", false);
        rulesEntry = entry(notificationGroup, RULES, "筛选规则", "", true);
        LinearLayout preferenceGroup = group("偏好与帮助", ui_theme.SHEET);
        automaticEntry = entry(preferenceGroup, AUTOMATIC, "自动清除", "", false);
        entry(preferenceGroup, ADVANCED, "高级设置", "模型 · 注意力", true);
        entry(preferenceGroup, GUIDE, "使用引导", "从观察模式开始", true);
        aboutEntry = entry(preferenceGroup, ABOUT, "关于", "", true);
        TextView footer = text("仅处理通知卡片，不删除原 App 内的消息。", 11, ui_theme.MUTED, false);
        LayoutParams footerPosition = fullWidth();
        footerPosition.topMargin = dp(2);
        addView(footer, footerPosition);
    }

    public void setOnEntrySelectedListener(OnEntrySelectedListener listener) { this.listener = listener; }

    public void update(boolean granted, boolean connected, boolean automatic, boolean comparison,
                       int targetCount, String version) {
        permissionEntry.setSummary(!granted ? "待授权" : connected ? "已授权 · 已连接" : "已授权 · 待连接");
        rulesEntry.setSummary(targetCount == 0 ? "目标为空" : targetCount + " 个目标 App");
        automaticEntry.setSummary(comparison ? "对照仅观察" : automatic ? "已开启" : "观察模式");
        aboutEntry.setSummary(version.isEmpty() ? "应用说明" : "v" + version);
    }

    private LinearLayout group(String label, int fill) {
        TextView title = text(label, 11, ui_theme.MUTED, true);
        LayoutParams titlePosition = fullWidth();
        titlePosition.bottomMargin = dp(7);
        addView(title, titlePosition);
        LinearLayout group = new LinearLayout(getContext());
        group.setOrientation(VERTICAL);
        group.setBackground(ui_theme.shape(getContext(), fill, 11, ui_theme.BORDER));
        LayoutParams groupPosition = fullWidth();
        groupPosition.bottomMargin = dp(18);
        addView(group, groupPosition);
        return group;
    }

    private paper_entry entry(LinearLayout group, String key, String title, String summary, boolean divider) {
        if (divider) {
            View rule = new View(getContext());
            rule.setBackgroundColor(ui_theme.BORDER);
            LayoutParams line = new LayoutParams(-1, Math.max(1, dp(0.5f)));
            line.setMargins(dp(14), 0, dp(14), 0);
            group.addView(rule, line);
        }
        paper_entry row = new paper_entry(getContext(), title);
        row.setSummary(summary);
        row.setOnClickListener(view -> {
            if (listener != null) {
                listener.onEntrySelected(key);
            }
        });
        group.addView(row, fullWidth());
        return row;
    }

    private final class paper_entry extends LinearLayout {
        private final String title;
        private final TextView summary;

        paper_entry(Context context, String title) {
            super(context);
            this.title = title;
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(9), dp(14), dp(9));
            setMinimumHeight(dp(52));
            setClickable(true);
            setFocusable(true);
            setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x18426956), null,
                    ui_theme.shape(context, android.graphics.Color.WHITE, 11, 0)));
            TextView name = text(title, 16, ui_theme.INK, true);
            name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(name, new LayoutParams(-2, -2));
            summary = text("", 11, ui_theme.MUTED, false);
            summary.setGravity(Gravity.END);
            summary.setMaxLines(2);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            summary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams summaryPosition = new LayoutParams(0, -2, 1);
            summaryPosition.leftMargin = dp(8);
            summaryPosition.rightMargin = dp(10);
            addView(summary, summaryPosition);
            TextView arrow = text("›", 22, ui_theme.MUTED, false);
            arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(arrow);
        }

        void setSummary(String value) {
            summary.setText(value);
            setContentDescription(title + (value.isEmpty() ? "" : "，" + value));
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(Button.class.getName());
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return ui_theme.text(getContext(), value, size, color, bold);
    }
    private LayoutParams fullWidth() { return new LayoutParams(-1, -2); }
    private int dp(float value) { return ui_theme.dp(getContext(), value); }
}
