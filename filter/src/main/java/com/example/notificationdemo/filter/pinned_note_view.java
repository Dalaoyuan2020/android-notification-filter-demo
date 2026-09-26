package com.example.notificationdemo.filter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

/** A real-content note with a thick white mount, inset colored paper, and a dimensional pin. */
public final class pinned_note_view extends ViewGroup {
    public interface OnNoteClickListener {
        void onNoteClick(pinned_note_view note, String actionKey);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF board = new RectF();
    private final RectF paper = new RectF();
    private final RectF detail = new RectF();
    private final TextView labelView;
    private final TextView titleView;
    private final TextView bodyView;
    private int paperColor = ui_theme.SOFT_YELLOW;
    private int pinColor = ui_theme.BLUE;
    private String label = "";
    private String title = "";
    private String body = "";
    private String actionKey = "";
    private OnNoteClickListener action;

    public pinned_note_view(Context context) { this(context, null); }

    public pinned_note_view(Context context, AttributeSet attributes) {
        super(context, attributes);
        setWillNotDraw(false);
        setClipChildren(true);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setElevation(dp(2));
        labelView = ui_theme.text(context, "", 13, ui_theme.ACCENT, true);
        labelView.setMaxLines(2);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        titleView = ui_theme.text(context, "", 20, ui_theme.INK, true);
        titleView.setMaxLines(3);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        bodyView = ui_theme.text(context, "", 14, ui_theme.MUTED, false);
        for (TextView text : new TextView[]{labelView, titleView, bodyView}) {
            text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(text);
        }
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                if (!board.isEmpty()) {
                    outline.setRoundRect(Math.round(board.left), Math.round(board.top),
                            Math.round(board.right), Math.round(board.bottom), dp(20));
                    outline.setAlpha(0.13f);
                }
            }
        });
        setOnClickListener(view -> {
            if (action != null) {
                action.onNoteClick(this, actionKey);
            }
        });
        setClickable(false);
        setFocusable(false);
    }

    /** All displayed text is supplied by the caller; no sample content is generated here. */
    public void bind(String smallLabel, String title, String description, int paperColor, int pinColor) {
        label = smallLabel == null ? "" : smallLabel;
        this.title = title == null ? "" : title;
        body = description == null ? "" : description;
        this.paperColor = paperColor | 0xFF000000;
        this.pinColor = pinColor | 0xFF000000;
        labelView.setText(label);
        titleView.setText(this.title);
        bodyView.setText(body);
        labelView.setVisibility(label.isEmpty() ? GONE : VISIBLE);
        titleView.setVisibility(this.title.isEmpty() ? GONE : VISIBLE);
        bodyView.setVisibility(body.isEmpty() ? GONE : VISIBLE);
        int foreground = contrast(Color.WHITE, this.paperColor) > contrast(ui_theme.INK, this.paperColor)
                ? Color.WHITE : ui_theme.INK;
        int labelColor = blend(this.pinColor, ui_theme.INK, 0.58f);
        labelView.setTextColor(contrast(labelColor, this.paperColor) >= 4.5 ? labelColor : foreground);
        titleView.setTextColor(foreground);
        bodyView.setTextColor(contrast(ui_theme.MUTED, this.paperColor) >= 4.5 ? ui_theme.MUTED : foreground);
        StringBuilder accessibility = new StringBuilder();
        for (String text : new String[]{label, this.title, body}) {
            if (!text.isEmpty()) {
                if (accessibility.length() > 0) {
                    accessibility.append("，");
                }
                accessibility.append(text);
            }
        }
        setContentDescription(accessibility.toString());
        requestLayout();
        invalidate();
    }

    public void setAction(String actionKey, OnNoteClickListener listener) {
        if (listener != null && (actionKey == null || actionKey.trim().isEmpty())) {
            throw new IllegalArgumentException("A note action needs a destination key");
        }
        this.actionKey = actionKey == null ? "" : actionKey;
        action = listener;
        setClickable(listener != null);
        setFocusable(listener != null);
    }

    /** Optional native rotation, clamped to two degrees so the content stays easy to read. */
    public void setTilt(float degrees) {
        setRotation(Float.isFinite(degrees) ? Math.max(-2f, Math.min(2f, degrees)) : 0f);
    }

    public String getLabel() { return label; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getActionKey() { return actionKey; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSizeAndState(Math.max(dp(280) + getPaddingLeft() + getPaddingRight(),
                getSuggestedMinimumWidth()), widthMeasureSpec, 0);
        int contentWidth = Math.max(1, (width & MEASURED_SIZE_MASK)
                - getPaddingLeft() - getPaddingRight() - dp(76));
        int textWidth = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.AT_MOST);
        int textHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int contentHeight = 0;
        int visible = 0;
        for (TextView text : new TextView[]{labelView, titleView, bodyView}) {
            if (text.getVisibility() != GONE) {
                text.measure(textWidth, textHeight);
                contentHeight += text.getMeasuredHeight();
                visible++;
            }
        }
        contentHeight += Math.max(0, visible - 1) * dp(9);
        int desiredHeight = Math.max(dp(164), contentHeight + dp(92))
                + getPaddingTop() + getPaddingBottom();
        int height = resolveSizeAndState(Math.max(desiredHeight, getSuggestedMinimumHeight()), heightMeasureSpec, 0);
        if ((height & MEASURED_SIZE_MASK) < desiredHeight) {
            int remaining = Math.max(0, (height & MEASURED_SIZE_MASK)
                    - getPaddingTop() - getPaddingBottom() - dp(92));
            for (TextView text : new TextView[]{labelView, titleView, bodyView}) {
                if (text.getVisibility() != GONE) {
                    text.measure(textWidth, MeasureSpec.makeMeasureSpec(remaining, MeasureSpec.AT_MOST));
                    remaining = Math.max(0, remaining - text.getMeasuredHeight() - dp(9));
                }
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        board.set(getPaddingLeft() + dp(10), getPaddingTop() + dp(16),
                Math.max(getPaddingLeft() + dp(10), getWidth() - getPaddingRight() - dp(10)),
                Math.max(getPaddingTop() + dp(16), getHeight() - getPaddingBottom() - dp(12)));
        paper.set(board);
        paper.inset(Math.min(dp(14), board.width() / 3), Math.min(dp(14), board.height() / 3));
        int x = Math.round(paper.left + dp(14));
        int y = Math.round(paper.top + dp(22));
        for (TextView text : new TextView[]{labelView, titleView, bodyView}) {
            if (text.getVisibility() != GONE) {
                text.layout(x, y, x + text.getMeasuredWidth(), y + text.getMeasuredHeight());
                y = text.getBottom() + dp(9);
            } else {
                text.layout(0, 0, 0, 0);
            }
        }
        invalidateOutline();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board.width() < dp(30) || board.height() < dp(30)) {
            return;
        }
        float radius = Math.min(dp(20), board.width() / 4);
        // Soft, stepped contact shadow sits beneath the thick white mount.
        for (int i = 4; i > 0; i--) {
            fill(Color.argb(4 + (4 - i) * 2, 51, 44, 32));
            canvas.drawRoundRect(board.left + dp(2 - i * 0.3f), board.top + dp(i + 2),
                    board.right - dp(2 - i * 0.3f), board.bottom + dp(i + 2), radius, radius, paint);
        }
        fill(0xFFE0DDD5);
        canvas.drawRoundRect(board.left, board.top + dp(3), board.right, board.bottom + dp(3), radius, radius, paint);
        fill(0xFFFFFEFB);
        canvas.drawRoundRect(board, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, dp(0.5f)));
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(board, radius, radius, paint);

        // The colored sheet is inset on all four sides and has a small lower paper edge.
        fill(blend(paperColor, ui_theme.INK, 0.11f));
        canvas.drawRoundRect(paper.left, paper.top + dp(1), paper.right, paper.bottom + dp(1), dp(10), dp(10), paint);
        fill(isPressed() ? blend(paperColor, ui_theme.INK, 0.035f) : paperColor);
        canvas.drawRoundRect(paper, dp(10), dp(10), paint);
        drawPin(canvas, board.centerX(), board.top + dp(3));
        if (isFocused()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(ui_theme.INK);
            canvas.drawRoundRect(board, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawPin(Canvas canvas, float x, float y) {
        // Offset ellipses and a short needle place the pin above the paper rather than on it.
        fill(0x12000000);
        canvas.drawOval(x - dp(13), y + dp(15), x + dp(20), y + dp(29), paint);
        fill(0x23000000);
        canvas.drawOval(x - dp(9), y + dp(17), x + dp(14), y + dp(25), paint);
        paint.setColor(0xFF797A74);
        paint.setStrokeWidth(dp(2));
        canvas.drawLine(x, y + dp(17), x + dp(4), y + dp(24), paint);
        fill(blend(pinColor, ui_theme.INK, 0.28f));
        canvas.drawOval(x - dp(13), y + dp(9), x + dp(13), y + dp(23), paint);
        fill(blend(pinColor, ui_theme.INK, 0.1f));
        canvas.drawOval(x - dp(12), y + dp(7), x + dp(12), y + dp(19), paint);

        // A shaded cylindrical stem supports a distinct, brighter domed head.
        fill(blend(pinColor, ui_theme.INK, 0.22f));
        canvas.drawRoundRect(x - dp(8), y - dp(1), x + dp(8), y + dp(15), dp(4), dp(4), paint);
        fill(blend(pinColor, Color.WHITE, 0.12f));
        canvas.drawRoundRect(x - dp(6), y, x - dp(1), y + dp(13), dp(2), dp(2), paint);
        fill(blend(pinColor, ui_theme.INK, 0.12f));
        canvas.drawOval(x - dp(11), y - dp(7), x + dp(11), y + dp(11), paint);
        fill(blend(pinColor, Color.WHITE, 0.18f));
        canvas.drawOval(x - dp(11), y - dp(9), x + dp(11), y + dp(7), paint);
        detail.set(x - dp(9), y - dp(7), x + dp(8), y + dp(5));
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(blend(pinColor, Color.WHITE, 0.54f));
        paint.setStrokeWidth(dp(1));
        canvas.drawArc(detail, 200, 90, false, paint);
        fill(0xB3FFFFFF);
        canvas.drawOval(x - dp(6), y - dp(5), x - dp(2), y - dp(3), paint);
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private static double contrast(int foreground, int background) {
        double first = Color.luminance(foreground);
        double second = Color.luminance(background);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static int blend(int source, int target, float fraction) {
        return Color.rgb(Math.round(Color.red(source) * (1 - fraction) + Color.red(target) * fraction),
                Math.round(Color.green(source) * (1 - fraction) + Color.green(target) * fraction),
                Math.round(Color.blue(source) * (1 - fraction) + Color.blue(target) * fraction));
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(isClickable() ? Button.class.getName() : View.class.getName());
    }

    private int dp(float value) { return ui_theme.dp(getContext(), value); }
}
