package com.example.notificationdemo.filter;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Caps content using its measured parent space, after window insets and margins are consumed. */
public final class cap_frame extends FrameLayout {
    private final int maximumWidth;

    public cap_frame(Context context) { this(context, 720); }

    public cap_frame(Context context, int maximumWidthDp) {
        super(context);
        if (maximumWidthDp <= 0) throw new IllegalArgumentException("Content width must be positive");
        maximumWidth = ui_theme.dp(context, maximumWidthDp);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            FrameLayout.LayoutParams position = (FrameLayout.LayoutParams) child.getLayoutParams();
            int available = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? maximumWidth
                    : Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight()
                    - position.leftMargin - position.rightMargin);
            position.width = Math.min(maximumWidth, available);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
