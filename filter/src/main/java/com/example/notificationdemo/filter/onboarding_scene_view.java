package com.example.notificationdemo.filter;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Self-contained tutorial art. Examples never touch notification state or a network client. */
public final class onboarding_scene_view extends LinearLayout {
    private final List<View> actors = new ArrayList<>();
    private FrameLayout stage;
    private LinearLayout content;
    private gesture_cursor cursor;
    private AnimatorSet animation;
    private ViewTreeObserver.OnPreDrawListener pendingStart;
    private Runnable playScene;
    private int scene;
    private boolean motionEnabled = true;

    public onboarding_scene_view(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setMinimumHeight(dp(260));
        setClipChildren(false);
        showScene(0, false);
    }

    /** Rebuild one of the four scenes. Layout remains readable when motion is disabled. */
    public void showScene(int index, boolean animate) {
        stopAnimations();
        scene = Math.max(0, Math.min(3, index));
        actors.clear();
        removeAllViews();
        TextView demo = text("教程示意 · 不是真实通知或模型实测", 11, ui_theme.WARNING, true);
        demo.setPadding(dp(10), dp(7), dp(10), dp(7));
        demo.setBackground(ui_theme.shape(getContext(), ui_theme.SOFT_YELLOW, 7, 0));
        addView(demo, new LayoutParams(-2, -2));

        stage = new FrameLayout(getContext());
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(dp(4), dp(scene >= 2 ? 7 : 18), dp(4), dp(scene >= 2 ? 3 : 12));
        content.setClipChildren(false);
        stage.addView(content, new FrameLayout.LayoutParams(-1, -2));
        addView(stage, new LayoutParams(-1, -2));
        cursor = new gesture_cursor(getContext());
        cursor.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        cursor.setAlpha(0);
        stage.addView(cursor, new FrameLayout.LayoutParams(dp(54), dp(66)));

        switch (scene) {
            case 1: buildJudgment(); break;
            case 2: buildSorting(); break;
            case 3: buildHome(); break;
            default: buildIncoming(); break;
        }
        if (animate) replay();
    }

    /** Permission to animate, not a start command. The host decides when to replay. */
    public void setMotionEnabled(boolean enabled) {
        motionEnabled = enabled;
        if (!enabled) stopAnimations();
    }

    public void replay() {
        stopAnimations();
        if (!motionEnabled || !ValueAnimator.areAnimatorsEnabled() || playScene == null) return;
        pendingStart = () -> {
            getViewTreeObserver().removeOnPreDrawListener(pendingStart);
            pendingStart = null;
            if (motionEnabled && isAttachedToWindow() && getWidth() > 0) playScene.run();
            return true;
        };
        // Start only after native text and folders have measured at the current font scale.
        getViewTreeObserver().addOnPreDrawListener(pendingStart);
        invalidate();
    }

    /** Cancel delayed starts and all running tracks; retain a complete static illustration. */
    public void stopAnimations() {
        if (pendingStart != null) {
            if (getViewTreeObserver().isAlive()) {
                getViewTreeObserver().removeOnPreDrawListener(pendingStart);
            }
            pendingStart = null;
        }
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        for (View actor : actors) {
            actor.setAlpha(1);
            actor.setTranslationX(0);
            actor.setTranslationY(0);
            actor.setScaleX(1);
            actor.setScaleY(1);
        }
        if (cursor != null) {
            cursor.setAlpha(0);
            cursor.setScaleX(1);
            cursor.setScaleY(1);
            cursor.pulse = 0;
        }
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimations();
        super.onDetachedFromWindow();
    }

    private void buildIncoming() {
        String[] examples = {"老师：明天上午把修改后的方案发我", "项目群：会议改到下午三点",
                "快递：包裹已放驿站", "群聊：晚上吃什么？"};
        int[] colors = {ui_theme.SHEET, ui_theme.SOFT_BLUE, ui_theme.SOFT_GREEN, ui_theme.SOFT_PURPLE};
        List<View> papers = new ArrayList<>();
        for (int i = 0; i < examples.length; i++) {
            TextView paper = paper(examples[i], colors[i]);
            paper.setRotation(i % 2 == 0 ? -1.2f : 1.2f);
            LayoutParams position = new LayoutParams(-1, -2);
            position.setMargins(dp(i % 2 == 0 ? 0 : 12), i == 0 ? 0 : dp(-3), dp(i % 2 == 0 ? 12 : 0), 0);
            content.addView(paper, position);
            papers.add(paper);
        }
        hint("消息依次出现。正式消息页只展示本机真实记录。");
        playScene = () -> {
            List<Animator> tracks = new ArrayList<>();
            for (int i = 0; i < papers.size(); i++) entrance(tracks, papers.get(i), i * 120L, -20);
            run(tracks);
        };
    }

    private void buildJudgment() {
        TextView message = paper("项目群 · 示例\n会议改到下午三点", ui_theme.SHEET);
        content.addView(message, new LayoutParams(-1, -2));
        List<View> tags = new ArrayList<>();
        String[] names = {"来源", "内容", "时间", "个性化"};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = line();
            for (int column = 0; column < 2; column++) {
                TextView tag = text(names[row * 2 + column], 12, ui_theme.MUTED, true);
                tag.setGravity(Gravity.CENTER);
                tag.setPadding(dp(9), dp(8), dp(9), dp(8));
                tag.setBackground(ui_theme.shape(getContext(), ui_theme.SOFT_BLUE, 8, 0));
                actors.add(tag);
                tags.add(tag);
                addCell(line, tag, column);
            }
            content.addView(line, spaced(8));
        }
        TextView result = paper("重要 · 判断示意\n示意概率  0.42 → 0.78", ui_theme.SOFT_YELLOW);
        content.addView(result, spaced(12));
        hint("点开消息可查看判断与概率。此处仅演示概念，不表示服务已开启。");
        playScene = () -> {
            List<Animator> tracks = new ArrayList<>();
            entrance(tracks, message, 0, 10);
            for (int i = 0; i < tags.size(); i++) {
                View tag = tags.get(i);
                entrance(tracks, tag, 140L + i * 70L, 6);
                tracks.add(track(tag, View.SCALE_X, 140L + i * 70L, 230, 0.85f, 1));
                tracks.add(track(tag, View.SCALE_Y, 140L + i * 70L, 230, 0.85f, 1));
            }
            entrance(tracks, result, 650, 9);
            tap(tracks, message, 430);
            run(tracks);
        };
    }

    private void buildSorting() {
        List<TextView> papers = new ArrayList<>();
        String[] examples = {"老师 · 方案修改", "快递 · 包裹到了", "广告 · 限时优惠", "群聊 · 晚饭安排"};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = line();
            for (int column = 0; column < 2; column++) {
                TextView paper = paper(examples[row * 2 + column], ui_theme.SHEET);
                paper.setTextSize(11);
                paper.setPadding(dp(8), dp(6), dp(8), dp(6));
                addCell(line, paper, column);
                papers.add(paper);
            }
            content.addView(line, spaced(row == 0 ? 0 : 7));
        }
        List<View> folders = folders(new String[]{"重要消息", "稍后处理", "已过滤"},
                new long[]{1, 1, 1}, new int[]{ui_theme.YELLOW, ui_theme.BLUE, ui_theme.CORAL});
        hint("重要=保留；过滤需系统确认。手势仅示意。");
        playScene = () -> {
            List<Animator> tracks = new ArrayList<>();
            for (int i = 0; i < folders.size(); i++) entrance(tracks, folders.get(i), i * 70L, 12);
            for (int i = 0; i < 3; i++) {
                View paper = papers.get(i);
                float[] from = center(paper);
                float[] to = center(folders.get(i));
                long delay = 370L + i * 160L;
                tracks.add(track(paper, View.TRANSLATION_X, delay, 480, 0, to[0] - from[0]));
                tracks.add(track(paper, View.TRANSLATION_Y, delay, 480, 0, to[1] - from[1]));
                tracks.add(track(paper, View.SCALE_X, delay, 480, 1, 0.85f));
                tracks.add(track(paper, View.SCALE_Y, delay, 480, 1, 0.85f));
                tracks.add(track(paper, View.ALPHA, delay + 240, 240, 1, 0));
                tracks.add(track(folders.get(i), View.SCALE_X, delay + 390, 240, 1, 1.03f, 1));
                tracks.add(track(folders.get(i), View.SCALE_Y, delay + 390, 240, 1, 1.03f, 1));
            }
            // A small drawn hand follows the illustrative advertising paper, not a real swipe handler.
            float[] start = center(papers.get(2));
            float[] end = center(folders.get(2));
            positionCursor(start);
            tracks.add(track(cursor, View.ALPHA, 630, 680, 0, 1, 1, 0));
            tracks.add(track(cursor, View.TRANSLATION_X, 690, 480, start[0] - dp(24), end[0] - dp(24)));
            tracks.add(track(cursor, View.TRANSLATION_Y, 690, 480, start[1] - dp(10), end[1] - dp(10)));
            run(tracks);
        };
    }

    private void buildHome() {
        TextView overview = text("今日概览 · 示例共 4 条记录", 13, ui_theme.INK, true);
        overview.setPadding(dp(8), 0, dp(8), dp(2));
        content.addView(overview, new LayoutParams(-1, -2));
        List<View> folders = folders(new String[]{"重要消息", "稍后处理", "已过滤", "通知记录"},
                new long[]{1, 2, 1, 4}, new int[]{ui_theme.YELLOW, ui_theme.BLUE, ui_theme.CORAL, ui_theme.PURPLE});
        hint("点文件夹查看分类；设置在“我的”。");
        playScene = () -> {
            List<Animator> tracks = new ArrayList<>();
            for (int i = 0; i < folders.size(); i++) entrance(tracks, folders.get(i), i * 80L, 12);
            tap(tracks, folders.get(0), 650);
            run(tracks);
        };
    }

    /** Full-size native folders are scaled as complete illustrations, never squeezed as text. */
    private List<View> folders(String[] titles, long[] counts, int[] colors) {
        List<View> result = new ArrayList<>();
        int columns = scene == 2 ? 3 : 2;
        float scale = scene == 2 ? 0.66f : 0.52f;
        for (int i = 0; i < titles.length; i += columns) {
            LinearLayout line = line();
            for (int column = 0; column < columns; column++) {
                int index = i + column;
                if (index == titles.length) {
                    addCell(line, new View(getContext()), column);
                    break;
                }
                folder_view folder = new folder_view(getContext());
                folder.bind(titles[index], counts[index], colors[index], 2);
                folder.setContentDescription("教程示意，" + titles[index] + "，" + counts[index] + " 条，不是真实数量");
                mini_folder miniature = new mini_folder(folder, scale);
                actors.add(miniature);
                addCell(line, miniature, column);
                result.add(miniature);
            }
            content.addView(line, spaced(i == 0 ? 6 : 1));
        }
        return result;
    }

    /** Layout reserves the transformed bounds, while the folder measures its text naturally. */
    private final class mini_folder extends ViewGroup {
        private final folder_view folder;
        private final float preferredScale;
        private float drawScale;

        mini_folder(folder_view folder, float preferredScale) {
            super(onboarding_scene_view.this.getContext());
            this.folder = folder;
            this.preferredScale = preferredScale;
            setClipChildren(false);
            setClipToPadding(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(folder);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int naturalWidth = dp(180);
            folder.measure(MeasureSpec.makeMeasureSpec(naturalWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int width = resolveSize(Math.round(naturalWidth * preferredScale), widthSpec);
            drawScale = Math.min(preferredScale, Math.max(0, width) / (float) naturalWidth);
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                drawScale = Math.min(drawScale, MeasureSpec.getSize(heightSpec)
                        / (float) Math.max(1, folder.getMeasuredHeight()));
            }
            int height = Math.round(folder.getMeasuredHeight() * drawScale);
            setMeasuredDimension(width, resolveSize(height, heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int x = Math.round((getWidth() - folder.getMeasuredWidth() * drawScale) / 2);
            folder.layout(x, 0, x + folder.getMeasuredWidth(), folder.getMeasuredHeight());
            folder.setPivotX(0);
            folder.setPivotY(0);
            folder.setScaleX(drawScale);
            folder.setScaleY(drawScale);
        }
    }

    private TextView paper(String value, int fill) {
        TextView view = text(value, 14, ui_theme.INK, true);
        view.setPadding(dp(13), dp(14), dp(13), dp(14));
        view.setBackground(ui_theme.shape(getContext(), fill, 9, ui_theme.BORDER));
        view.setElevation(dp(2));
        actors.add(view);
        return view;
    }

    private void hint(String value) {
        TextView view = text(value, 12, ui_theme.MUTED, false);
        LayoutParams position = new LayoutParams(-1, -2);
        position.topMargin = dp(9);
        addView(view, position);
    }

    private LinearLayout line() {
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(HORIZONTAL);
        line.setClipChildren(false);
        line.setClipToPadding(false);
        return line;
    }

    private void addCell(LinearLayout line, View view, int column) {
        LayoutParams position = new LayoutParams(0, -2, 1);
        if (column == 0) position.rightMargin = dp(4);
        else position.leftMargin = dp(4);
        line.addView(view, position);
    }

    private LayoutParams spaced(int top) {
        LayoutParams result = new LayoutParams(-1, -2);
        result.topMargin = dp(top);
        return result;
    }

    private float[] center(View view) {
        Rect bounds = new Rect(0, 0, view.getWidth(), view.getHeight());
        stage.offsetDescendantRectToMyCoords(view, bounds);
        return new float[]{bounds.exactCenterX(), bounds.exactCenterY()};
    }

    private void entrance(List<Animator> tracks, View view, long delay, int distance) {
        view.setAlpha(0);
        view.setTranslationY(dp(distance));
        tracks.add(track(view, View.ALPHA, delay, 300, 0, 1));
        tracks.add(track(view, View.TRANSLATION_Y, delay, 300, dp(distance), 0));
    }

    private void positionCursor(float[] point) {
        cursor.setTranslationX(point[0] - dp(24));
        cursor.setTranslationY(point[1] - dp(10));
    }

    private void tap(List<Animator> tracks, View target, long delay) {
        positionCursor(center(target));
        tracks.add(track(cursor, View.ALPHA, delay, 600, 0, 1, 1, 0));
        tracks.add(track(cursor, View.SCALE_X, delay, 600, 1, 0.9f, 1));
        tracks.add(track(cursor, View.SCALE_Y, delay, 600, 1, 0.9f, 1));
        ValueAnimator pulse = ValueAnimator.ofFloat(0, 1);
        pulse.setStartDelay(delay + 150);
        pulse.setDuration(320);
        pulse.addUpdateListener(value -> {
            cursor.pulse = (float) value.getAnimatedValue();
            cursor.invalidate();
        });
        tracks.add(pulse);
    }

    private ObjectAnimator track(View view, android.util.Property<View, Float> property,
                                 long delay, long duration, float... values) {
        ObjectAnimator result = ObjectAnimator.ofFloat(view, property, values);
        result.setStartDelay(delay);
        result.setDuration(duration);
        return result;
    }

    private void run(List<Animator> tracks) {
        animation = new AnimatorSet();
        animation.playTogether(tracks);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return ui_theme.text(getContext(), value, size, color, bold);
    }

    private int dp(float value) { return ui_theme.dp(getContext(), value); }

    /** Decorative finger with a tap ring; no gesture interception or external image assets. */
    private final class gesture_cursor extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path hand = new Path();
        private float pulse;

        gesture_cursor(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 54f, getHeight() / 66f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f);
            paint.setColor(ui_theme.ACCENT);
            paint.setAlpha(Math.round(160 * (1 - pulse)));
            canvas.drawCircle(24, 10, 5 + pulse * 8, paint);
            paint.setAlpha(255);
            hand.reset();
            hand.moveTo(19, 32);
            hand.lineTo(19, 13);
            hand.cubicTo(19, 6, 29, 6, 29, 13);
            hand.lineTo(29, 28);
            hand.cubicTo(33, 25, 38, 28, 38, 32);
            hand.cubicTo(42, 30, 46, 34, 46, 39);
            hand.lineTo(45, 49);
            hand.quadTo(44, 55, 40, 60);
            hand.lineTo(23, 60);
            hand.quadTo(18, 51, 12, 43);
            hand.cubicTo(8, 37, 13, 31, 19, 39);
            hand.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ui_theme.SHEET);
            paint.setShadowLayer(2, 0, 2, 0x26342E23);
            canvas.drawPath(hand, paint);
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.7f);
            paint.setColor(ui_theme.INK);
            canvas.drawPath(hand, paint);
            canvas.restore();
        }
    }
}
