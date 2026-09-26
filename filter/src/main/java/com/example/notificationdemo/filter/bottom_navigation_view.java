package com.example.notificationdemo.filter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fixed-shell navigation; the activity owns page content, safe-area margins and persistence. */
public final class bottom_navigation_view extends LinearLayout {
    public interface OnPageSelectedListener {
        void onPageSelected(int page);
    }

    private static final String[] LABELS = {"首页", "消息", "智能判断", "我的"};
    private final nav_item[] items = new nav_item[LABELS.length];
    private int selectedPage = -1;
    private OnPageSelectedListener listener;

    public bottom_navigation_view(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(6), dp(6), dp(6), dp(6));
        setBackground(ui_theme.shape(context, ui_theme.SHEET, 28, ui_theme.BORDER));
        setElevation(dp(4));
        setMinimumHeight(dp(76));
        setClipToPadding(false);
        for (int page = 0; page < items.length; page++) {
            final int destination = page;
            nav_item item = new nav_item(context, page);
            items[page] = item;
            addView(item, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            item.setOnClickListener(view -> {
                if (selectedPage == destination) return;
                setSelectedPage(destination);
                if (listener != null) listener.onPageSelected(destination);
            });
        }
        setSelectedPage(0);
    }

    public void setOnPageSelectedListener(OnPageSelectedListener listener) {
        this.listener = listener;
    }

    /** Programmatic selection never navigates again or invokes the listener. */
    public void setSelectedPage(int page) {
        if (page < 0 || page >= items.length) throw new IllegalArgumentException("Page must be between 0 and 3");
        if (selectedPage == page) return;
        selectedPage = page;
        for (int index = 0; index < items.length; index++) items[index].showSelected(index == page);
    }

    public int getSelectedPage() { return selectedPage; }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(1, items.length, false,
                AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE));
    }

    private int dp(float value) { return ui_theme.dp(getContext(), value); }

    private static final class nav_item extends LinearLayout {
        private final int page;
        private final TextView label;
        private final nav_icon icon;

        nav_item(Context context, int page) {
            super(context);
            this.page = page;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(6), dp(7), dp(6), dp(7));
            setMinimumWidth(dp(48));
            setMinimumHeight(dp(64));
            setFocusable(true);
            icon = new nav_icon(context, page);
            addView(icon, new LayoutParams(dp(24), dp(24)));
            label = ui_theme.text(context, LABELS[page], 12, ui_theme.MUTED, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams textParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            textParams.topMargin = dp(3);
            addView(label, textParams);
            // The item is one accessible button; its visible native label remains queryable by UI checks.
            setContentDescription(LABELS[page]);
        }

        void showSelected(boolean selected) {
            setSelected(selected);
            int color = selected ? Color.WHITE : ui_theme.MUTED;
            label.setTextColor(color);
            icon.setColor(color);
            setBackground(new RippleDrawable(ColorStateList.valueOf(selected ? 0x33FFFFFF : 0x15171815),
                    ui_theme.shape(getContext(), selected ? ui_theme.INK : Color.TRANSPARENT, 22, 0), null));
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(Button.class.getName());
            info.setSelected(isSelected());
            info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, page, 1,
                    false, isSelected()));
        }

        private int dp(float value) { return ui_theme.dp(getContext(), value); }
    }

    /** Four neutral vector-style pictograms drawn with native paths, never font glyphs or emoji. */
    private static final class nav_icon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int page;

        nav_icon(Context context, int page) {
            super(context);
            this.page = page;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setColor(int color) { paint.setColor(color); invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            if (scale <= 0) return;
            int checkpoint = canvas.save();
            canvas.translate((getWidth() - scale * 24) / 2f, (getHeight() - scale * 24) / 2f);
            canvas.scale(scale, scale);
            path.reset();
            if (page == 0) {
                path.moveTo(3, 10); path.lineTo(12, 3); path.lineTo(21, 10);
                path.moveTo(5, 9); path.lineTo(5, 21); path.lineTo(10, 21);
                path.lineTo(10, 14); path.lineTo(14, 14); path.lineTo(14, 21);
                path.lineTo(19, 21); path.lineTo(19, 9);
                canvas.drawPath(path, paint);
            } else if (page == 1) {
                path.moveTo(6, 4); path.lineTo(18, 4); path.quadTo(21, 4, 21, 7);
                path.lineTo(21, 15); path.quadTo(21, 18, 18, 18);
                path.lineTo(10, 18); path.lineTo(5, 21); path.lineTo(5, 17.8f);
                path.quadTo(3, 17, 3, 15); path.lineTo(3, 7); path.quadTo(3, 4, 6, 4);
                canvas.drawPath(path, paint);
                canvas.drawLine(7, 9, 17, 9, paint);
                canvas.drawLine(7, 13, 14, 13, paint);
            } else if (page == 2) {
                canvas.drawRoundRect(5, 4, 19, 20, 3, 3, paint);
                canvas.drawLine(9, 1.5f, 9, 4, paint);
                canvas.drawLine(15, 1.5f, 15, 4, paint);
                canvas.drawLine(9, 20, 9, 22.5f, paint);
                canvas.drawLine(15, 20, 15, 22.5f, paint);
                canvas.drawLine(2, 9, 5, 9, paint);
                canvas.drawLine(2, 15, 5, 15, paint);
                canvas.drawLine(19, 9, 22, 9, paint);
                canvas.drawLine(19, 15, 22, 15, paint);
                path.moveTo(8, 12); path.lineTo(11, 15); path.lineTo(16, 9);
                canvas.drawPath(path, paint);
            } else {
                canvas.drawCircle(12, 7, 4, paint);
                path.moveTo(4, 21); path.lineTo(4, 19);
                path.cubicTo(4, 12.5f, 20, 12.5f, 20, 19); path.lineTo(20, 21);
                canvas.drawPath(path, paint);
            }
            canvas.restoreToCount(checkpoint);
        }
    }
}
