package com.example.notificationdemo.filter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Date;

/** Compact presentation of one actual retained log; the owner provides its full detail view. */
public final class message_bubble_view extends LinearLayout {
    private final LinearLayout modelContainer;
    private final TextView detailLink;

    public message_bubble_view(Context context, JSONObject entry) {
        super(context);
        if (entry == null) throw new IllegalArgumentException("A message bubble needs a log record");
        setOrientation(VERTICAL);
        setPadding(dp(22), dp(14), dp(14), dp(17));
        setMinimumHeight(dp(48));
        setBackground(new bubble_background(backgroundColor(entry), context.getResources().getDisplayMetrics().density));
        setElevation(dp(1));
        setForeground(new RippleDrawable(ColorStateList.valueOf(0x12171815), null,
                ui_theme.shape(context, Color.WHITE, 18, 0)));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.TOP);
        header.setOrientation(HORIZONTAL);
        TextView source = text(sourceName(context, entry.optString("pkg", "")), 12, ui_theme.INK, true);
        source.setMaxLines(2);
        source.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sourceParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        sourceParams.setMarginEnd(dp(10));
        header.addView(source, sourceParams);
        TextView time = text(timeLabel(context, entry), 11, ui_theme.MUTED, false);
        time.setGravity(Gravity.END);
        time.setMaxWidth(dp(126));
        time.setMaxLines(2);
        time.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(time, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(header, fullWidth());

        TextView classification = text(groupLabel(entry) + " · " + actionLabel(entry), 11, ui_theme.MUTED, false);
        classification.setMaxLines(2);
        classification.setEllipsize(TextUtils.TruncateAt.END);
        addView(classification, spaced(6));

        String title = entry.optString("title", "");
        TextView headline = text(title.trim().isEmpty() ? "无标题通知" : title, 16, ui_theme.INK, true);
        headline.setMaxLines(2);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        addView(headline, spaced(8));

        String body = entry.optString("text", "");
        TextView summary = text(body.trim().isEmpty() ? "未提供正文" : body, 13, ui_theme.INK, false);
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        addView(summary, spaced(5));

        modelContainer = new LinearLayout(context);
        modelContainer.setOrientation(VERTICAL);
        modelContainer.setVisibility(GONE);
        addView(modelContainer, spaced(10));
        detailLink = text("详情 ›", 12, ui_theme.ACCENT, true);
        detailLink.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        detailLink.setMinHeight(dp(20));
        detailLink.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(detailLink, spaced(2));
        setDetailsAction(null);
    }

    /** Add the existing model overview here and explicitly show it when a record has model data. */
    public LinearLayout getModelContainer() { return modelContainer; }

    /** The whole bubble is the generous detail target; its slim footer is a visual affordance. */
    public void setDetailsAction(View.OnClickListener listener) {
        setOnClickListener(listener);
        setClickable(listener != null);
        setFocusable(listener != null);
        detailLink.setOnClickListener(null);
        detailLink.setClickable(false);
        detailLink.setVisibility(listener == null ? GONE : VISIBLE);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(isClickable() ? Button.class.getName() : LinearLayout.class.getName());
    }

    private static String groupLabel(JSONObject row) {
        if (notification_ui_data.matches(row, notification_ui_data.FILTER_IMPORTANT)) return "重要消息";
        if (notification_ui_data.matches(row, notification_ui_data.FILTER_FILTERED)) return "已过滤";
        if (notification_ui_data.matches(row, notification_ui_data.FILTER_LATER)) return "稍后处理";
        return "通知记录";
    }

    private static int backgroundColor(JSONObject row) {
        if (notification_ui_data.matches(row, notification_ui_data.FILTER_IMPORTANT)) return ui_theme.SOFT_CORAL;
        if (notification_ui_data.matches(row, notification_ui_data.FILTER_FILTERED)) return ui_theme.SOFT_PURPLE;
        if (notification_ui_data.matches(row, notification_ui_data.FILTER_LATER)) return ui_theme.SOFT_BLUE;
        return ui_theme.SHEET;
    }

    private static String actionLabel(JSONObject row) {
        String action = row.optString("action", "");
        return action.trim().isEmpty() ? "未标注动作" : action;
    }

    private static String sourceName(Context context, String pkg) {
        if (pkg.trim().isEmpty()) return "未知来源";
        try {
            PackageManager manager = context.getPackageManager();
            CharSequence label = manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0));
            if (label != null && !label.toString().trim().isEmpty()) return label.toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            // Removed apps or Android package visibility limits still have the actual stored package name.
        }
        return pkg;
    }

    private static String timeLabel(Context context, JSONObject row) {
        Object timestamp = row.opt("time");
        if (!(timestamp instanceof Number) || ((Number) timestamp).longValue() <= 0) return "时间未记录";
        Date date = new Date(((Number) timestamp).longValue());
        return android.text.format.DateFormat.getDateFormat(context).format(date) + " "
                + android.text.format.DateFormat.getTimeFormat(context).format(date);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return ui_theme.text(getContext(), value, size, color, bold);
    }

    private LayoutParams fullWidth() { return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); }

    private LayoutParams spaced(int top) {
        LayoutParams params = fullWidth();
        params.topMargin = dp(top);
        return params;
    }

    private int dp(float value) { return ui_theme.dp(getContext(), value); }

    private static final class bubble_background extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tail = new Path();
        private final float density;

        bubble_background(int color, float density) {
            this.density = Float.isFinite(density) && density > 0 ? density : 1f;
            paint.setColor(color);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            float left = bounds.left;
            float bottom = bounds.bottom;
            tail.reset();
            tail.moveTo(left + dp(15), bottom - dp(23));
            tail.quadTo(left + dp(12), bottom - dp(11), left + dp(2), bottom - dp(5));
            tail.quadTo(left + dp(19), bottom - dp(6), left + dp(28), bottom - dp(13));
            tail.close();
            canvas.drawPath(tail, paint);
            canvas.drawRoundRect(left + dp(8), bounds.top, bounds.right, bottom - dp(6), dp(18), dp(18), paint);
        }

        @Override public void getOutline(Outline outline) {
            Rect bounds = getBounds();
            if (bounds.width() > dp(8) && bounds.height() > dp(6)) {
                outline.setRoundRect(bounds.left + Math.round(dp(8)), bounds.top, bounds.right,
                        bounds.bottom - Math.round(dp(6)), dp(18));
                outline.setAlpha(0.15f);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(Math.max(0, Math.min(255, alpha))); invalidateSelf(); }
        @Override public int getAlpha() { return paint.getAlpha(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public ColorFilter getColorFilter() { return paint.getColorFilter(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        private float dp(float value) { return value * density; }
    }
}
