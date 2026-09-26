package com.example.notificationdemo.filter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

/** Six-layer paper folder. The caller supplies actual counts and owns destination filtering. */
public final class folder_view extends ViewGroup {
    public interface OnFolderClickListener {
        void onFolderClick(folder_view folder, String filterKey);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF front = new RectF();
    private final RectF page = new RectF();
    private final TextView titleView;
    private final TextView countView;
    private int folderColor = ui_theme.YELLOW;
    private int paperCount = 2;
    private long count;
    private String title = "";
    private String filterKey = "";
    private OnFolderClickListener navigation;
    private float paperTop;

    public folder_view(Context context) { this(context, null); }

    public folder_view(Context context, AttributeSet attributes) {
        super(context, attributes);
        setWillNotDraw(false);
        setClipChildren(true);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setElevation(dp(2));
        titleView = ui_theme.text(context, "", 17, ui_theme.INK, true);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        countView = ui_theme.text(context, "", 12, ui_theme.INK, false);
        countView.setSingleLine(true);
        countView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        countView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(titleView);
        addView(countView);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                if (!front.isEmpty()) {
                    outline.setRoundRect(Math.round(front.left), Math.round(front.top),
                            Math.round(front.right), Math.round(front.bottom), dp(15));
                    outline.setAlpha(0.14f);
                }
            }
        });
        setOnClickListener(view -> {
            if (navigation != null) {
                navigation.onFolderClick(this, filterKey);
            }
        });
        setClickable(false);
        setFocusable(false);
    }

    /** Call with a real log count; zero is a valid empty category, negative values are not. */
    public void bind(String title, long actualCount, int color, int exposedPapers) {
        if (actualCount < 0 || exposedPapers < 1 || exposedPapers > 3) {
            throw new IllegalArgumentException("A folder needs a nonnegative count and 1–3 papers");
        }
        this.title = title == null ? "" : title;
        folderColor = color | 0xFF000000;
        paperCount = exposedPapers;
        titleView.setText(this.title);
        double luminance = Color.luminance(folderColor);
        double whiteContrast = 1.05 / (luminance + 0.05);
        double inkContrast = (luminance + 0.05) / (Color.luminance(ui_theme.INK) + 0.05);
        int foreground = whiteContrast > inkContrast ? Color.WHITE : ui_theme.INK;
        titleView.setTextColor(foreground);
        countView.setTextColor(foreground);
        setCount(actualCount);
        requestLayout();
        invalidate();
    }

    public void setCount(long actualCount) {
        if (actualCount < 0) {
            throw new IllegalArgumentException("A log count cannot be negative");
        }
        count = actualCount;
        String quantity = java.text.NumberFormat.getIntegerInstance().format(count) + " 条";
        countView.setText(quantity);
        setContentDescription(title + "，" + quantity);
        requestLayout();
    }

    /** The shell will translate filterKey into its real destination and message filter. */
    public void setNavigation(String filterKey, OnFolderClickListener listener) {
        if (listener != null && (filterKey == null || filterKey.trim().isEmpty())) {
            throw new IllegalArgumentException("A navigation callback needs a filter key");
        }
        this.filterKey = filterKey == null ? "" : filterKey;
        navigation = listener;
        setClickable(listener != null);
        setFocusable(listener != null);
    }

    public long getCount() { return count; }
    public String getTitle() { return title; }
    public String getFilterKey() { return filterKey; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dp(180) + getPaddingLeft() + getPaddingRight();
        int width = resolveSizeAndState(Math.max(desiredWidth, getSuggestedMinimumWidth()), widthMeasureSpec, 0);
        int contentWidth = Math.max(1, (width & MEASURED_SIZE_MASK) - getPaddingLeft() - getPaddingRight() - dp(40));
        int textWidth = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.AT_MOST);
        int textHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        titleView.measure(textWidth, textHeight);
        countView.measure(textWidth, textHeight);
        int textBlock = titleView.getMeasuredHeight() + dp(7) + countView.getMeasuredHeight();
        int desiredHeight = dp(72) + Math.max(dp(92), textBlock + dp(34))
                + getPaddingTop() + getPaddingBottom();
        int height = resolveSizeAndState(Math.max(desiredHeight, getSuggestedMinimumHeight()), heightMeasureSpec, 0);
        if ((height & MEASURED_SIZE_MASK) < desiredHeight) {
            // Honor a parent's explicit height cap while keeping the actual count in the face.
            // With wrap_content the normal path expands instead, including at larger font scales.
            int availableText = Math.max(0, (height & MEASURED_SIZE_MASK)
                    - getPaddingTop() - getPaddingBottom() - dp(46));
            countView.measure(textWidth, MeasureSpec.makeMeasureSpec(availableText, MeasureSpec.AT_MOST));
            int titleHeight = Math.max(0, availableText - countView.getMeasuredHeight() - dp(7));
            titleView.measure(textWidth, MeasureSpec.makeMeasureSpec(titleHeight, MeasureSpec.AT_MOST));
        }
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        float inset = dp(6);
        paperTop = getPaddingTop() + dp(6);
        float faceBottom = Math.max(paperTop, getHeight() - getPaddingBottom() - dp(10));
        float available = Math.max(0, faceBottom - paperTop);
        float textBlock = titleView.getMeasuredHeight() + dp(7) + countView.getMeasuredHeight();
        float exposed = Math.min(dp(54), Math.max(0, available - textBlock - dp(30)));
        front.set(getPaddingLeft() + inset, paperTop + exposed,
                Math.max(getPaddingLeft() + inset, getWidth() - getPaddingRight() - inset), faceBottom);
        int textLeft = Math.round(front.left + dp(14));
        int textTop = Math.round(front.top + dp(15));
        titleView.layout(textLeft, textTop, textLeft + titleView.getMeasuredWidth(),
                textTop + titleView.getMeasuredHeight());
        int countTop = titleView.getBottom() + dp(7);
        countView.layout(textLeft, countTop, textLeft + countView.getMeasuredWidth(),
                countTop + countView.getMeasuredHeight());
        invalidateOutline();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (front.width() < dp(16) || front.height() <= 0) {
            return;
        }
        float radius = Math.min(dp(15), front.width() * 0.12f);
        float left = front.left;
        float right = front.right;
        float width = front.width();
        float backTop = paperTop + dp(22);

        // 1. A separate rear sheet peeks above the folder's rigid backplate.
        page.set(left + width * 0.12f, paperTop + dp(5), right - width * 0.11f, front.bottom - dp(14));
        drawPaper(canvas, page, -4f, false);

        // 2. The darker backplate stays visible at the shoulders and upper edges.
        fill(blend(folderColor, ui_theme.INK, 0.13f));
        canvas.drawRoundRect(left + dp(1), backTop, right - dp(1), front.bottom - dp(2), radius, radius, paint);

        // 3. A broad, raised tab has a curved base and a sloping folded shoulder.
        float tabRight = left + width * 0.48f;
        path.reset();
        path.moveTo(left + dp(1), backTop + dp(13));
        path.lineTo(left + dp(1), paperTop + dp(14));
        path.quadTo(left + dp(1), paperTop + dp(8), left + dp(8), paperTop + dp(8));
        path.lineTo(tabRight - dp(9), paperTop + dp(8));
        path.quadTo(tabRight - dp(3), paperTop + dp(8), tabRight + dp(1), paperTop + dp(15));
        path.lineTo(tabRight + dp(10), backTop);
        path.lineTo(tabRight + dp(12), backTop + dp(13));
        path.close();
        fill(blend(folderColor, Color.WHITE, 0.16f));
        canvas.drawPath(path, paint);
        paint.setColor(blend(folderColor, ui_theme.INK, 0.18f));
        paint.setStrokeWidth(dp(1));
        canvas.drawLine(tabRight - dp(1), paperTop + dp(13), tabRight + dp(8), backTop, paint);

        // 4. One to three independent, lined sheets overlap at different heights.
        for (int i = 0; i < paperCount; i++) {
            float stagger = dp(i == 1 ? 5 : i == 2 ? -2 : 0);
            page.set(left + dp(10) + i * dp(3), paperTop + dp(23) - stagger,
                    right - dp(9) - (paperCount - i - 1) * dp(3), front.bottom - dp(15));
            drawPaper(canvas, page, i == 0 ? -2f : i == 1 ? 2.5f : -0.8f, i == paperCount - 1);
        }

        // 5. Rounded colored face and layered bottom shadow cover the lower sheets.
        for (int i = 3; i > 0; i--) {
            fill(Color.argb(4 + (3 - i) * 3, 49, 43, 32));
            canvas.drawRoundRect(left + dp(3 - i), front.top + dp(i + 2), right - dp(3 - i),
                    front.bottom + dp(i + 2), radius, radius, paint);
        }
        float faceColorAmount = isPressed() ? 0.06f : 0f;
        fill(blend(folderColor, ui_theme.INK, faceColorAmount));
        canvas.drawRoundRect(front, radius, radius, paint);
        paint.setColor(blend(folderColor, Color.WHITE, 0.3f));
        paint.setStrokeWidth(dp(1));
        canvas.drawLine(left + radius, front.top + dp(1), right - radius, front.top + dp(1), paint);
        if (isFocused()) {
            paint.setColor(ui_theme.INK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawRoundRect(front, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        // 6. Native title/count children are drawn afterward, preserving text scaling.
    }

    private void drawPaper(Canvas canvas, RectF bounds, float rotation, boolean label) {
        int checkpoint = canvas.save();
        canvas.rotate(rotation, bounds.centerX(), bounds.centerY());
        fill(0x13000000);
        canvas.drawRoundRect(bounds.left, bounds.top + dp(2), bounds.right, bounds.bottom + dp(2), dp(3), dp(3), paint);
        fill(ui_theme.SHEET);
        canvas.drawRoundRect(bounds, dp(3), dp(3), paint);
        paint.setColor(ui_theme.GRID);
        paint.setStrokeWidth(Math.max(1, dp(0.5f)));
        float end = Math.min(bounds.bottom - dp(6), front.top + dp(12));
        for (float y = bounds.top + dp(10); y < end; y += dp(8)) {
            canvas.drawLine(bounds.left + dp(7), y, bounds.right - dp(7), y, paint);
        }
        if (label) {
            fill(ui_theme.SOFT_CORAL);
            canvas.drawRoundRect(bounds.right - dp(25), bounds.top + dp(6), bounds.right - dp(7),
                    bounds.top + dp(11), dp(1), dp(1), paint);
        }
        canvas.restoreToCount(checkpoint);
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
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
