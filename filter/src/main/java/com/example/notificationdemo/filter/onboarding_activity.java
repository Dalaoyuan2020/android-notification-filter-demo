package com.example.notificationdemo.filter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Four illustrative scenes. Completion is independent of notification and model preferences. */
public final class onboarding_activity extends Activity {
    public static final String EXTRA_RETURN_HOME = "com.example.notificationdemo.filter.GUIDE_HOME";
    public static final String EXTRA_REPLAY = "com.example.notificationdemo.filter.GUIDE_REPLAY";
    private static final String PREFERENCES = "onboarding_v040";
    private static final String COMPLETED = "completed";
    private static final String[] TITLES = {
            "消息很多，\n但你的注意力有限。",
            "先判断，\n再决定是否打扰你。",
            "重要的留下，\n其余的集中查看。",
            "把注意力，\n留给真正重要的事。"
    };
    private static final String[] DESCRIPTIONS = {
            "先在“我的 → 通知权限”中授权，再用测试发送器观察结果。这里的纸条都是教程示例，不是真实通知。",
            "在“智能判断 → 模型配置”按需开启 Jev。默认仍是本机关键词；教程中的判断和概率只作示意。",
            "“消息”按记录分类；“已过滤”只统计系统确认清除。默认仅观察，自动清除需在“我的”手动开启。",
            "点击或逐条划掉通知，可影响本机短时注意力。在 Attention 查看变化与设置；本引导不会代你开启任何权限或开关。"
    };

    private int step;
    private boolean resumed;
    private boolean leaving;
    private boolean replayMode;
    private long transitionGeneration;
    private TextView title;
    private TextView description;
    private TextView progress;
    private final View[] progressMarks = new View[4];
    private Button previous;
    private Button next;
    private Button skip;
    private ScrollView scroll;
    private LinearLayout pageContent;
    private onboarding_scene_view scene;
    private android.window.OnBackInvokedCallback backCallback;

    public static boolean shouldShow(Context context, Intent intent) {
        return intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && !context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(COMPLETED, false);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        step = state == null ? 0 : Math.max(0, Math.min(3, state.getInt("guide_step", 0)));
        replayMode = state == null ? getIntent().getBooleanExtra(EXTRA_REPLAY, false)
                : state.getBoolean("guide_replay", false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(ui_theme.PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ui_theme.paper(this));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets edge = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(edge.left, edge.top, edge.right, edge.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(4), dp(14), 0);
        header.addView(text("Attention", 20, ui_theme.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        skip = quietButton(replayMode ? "关闭" : "跳过");
        skip.setContentDescription(replayMode ? "关闭教程，返回应用" : "跳过引导，进入首页");
        skip.setOnClickListener(view -> finishGuide());
        header.addView(skip, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        scroll = new tutorial_scroll_view(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        paper_frame frame = new paper_frame(this);
        scroll.addView(frame, new ScrollView.LayoutParams(-1, -2));
        LinearLayout content = new LinearLayout(this);
        pageContent = content;
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(10), dp(22), dp(12));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        title = text("", 32, ui_theme.INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        description = text("", 14, ui_theme.MUTED, false);
        LinearLayout.LayoutParams descriptionPosition = new LinearLayout.LayoutParams(-1, -2);
        descriptionPosition.topMargin = dp(12);
        descriptionPosition.bottomMargin = dp(8);
        content.addView(description, descriptionPosition);
        scene = new onboarding_scene_view(this);
        content.addView(scene, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(22), dp(4), dp(22), dp(14));
        controls.setBackgroundColor(ui_theme.PAPER);
        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progress = text("", 12, ui_theme.MUTED, false);
        progress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progressRow.addView(progress, new LinearLayout.LayoutParams(0, -2, 1));
        Button replay = quietButton("重播本幕");
        replay.setOnClickListener(view -> {
            cancelPageTransition();
            bindPage(false);
            scroll.smoothScrollTo(0, Math.max(0, scene.getTop() - dp(12)));
            scene.replay();
        });
        progressRow.addView(replay, new LinearLayout.LayoutParams(-2, -2));
        controls.addView(progressRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout marks = new LinearLayout(this);
        marks.setGravity(Gravity.CENTER);
        for (int i = 0; i < progressMarks.length; i++) {
            View mark = new View(this);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams markPosition = new LinearLayout.LayoutParams(dp(6), dp(6));
            if (i != progressMarks.length - 1) {
                markPosition.rightMargin = dp(7);
            }
            marks.addView(mark, markPosition);
            progressMarks[i] = mark;
        }
        LinearLayout.LayoutParams marksPosition = new LinearLayout.LayoutParams(-1, -2);
        marksPosition.bottomMargin = dp(14);
        controls.addView(marks, marksPosition);
        LinearLayout navigation = new LinearLayout(this);
        previous = new Button(this);
        previous.setText("上一幕");
        ui_theme.button(previous, false);
        previous.setOnClickListener(view -> showStep(step - 1, true));
        LinearLayout.LayoutParams previousPosition = new LinearLayout.LayoutParams(0, -2, 1);
        previousPosition.rightMargin = dp(10);
        navigation.addView(previous, previousPosition);
        next = new Button(this);
        ui_theme.button(next, true);
        next.setOnClickListener(view -> {
            if (step == 3) {
                finishGuide();
            } else {
                showStep(step + 1, true);
            }
        });
        navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        root.requestApplyInsets();
        showStep(step, false);
        if (state != null) {
            int position = Math.max(0, state.getInt("guide_scroll", 0));
            scroll.post(() -> scroll.scrollTo(0, position));
        }
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::goBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    private void showStep(int requested, boolean animate) {
        if (leaving) return;
        int destination = Math.max(0, Math.min(3, requested));
        int direction = destination >= step ? 1 : -1;
        boolean changed = destination != step;
        cancelPageTransition();
        scene.stopAnimations();
        step = destination;
        progress.setText("第 " + (step + 1) + " 幕 / 共 4 幕");
        previous.setEnabled(step > 0);
        skip.setVisibility(step == 3 ? View.INVISIBLE : View.VISIBLE);
        next.setText(step == 3 ? (replayMode ? "返回应用" : "开始使用") : "下一幕");
        for (int i = 0; i < progressMarks.length; i++) {
            progressMarks[i].setBackground(ui_theme.shape(this,
                    i == step ? ui_theme.INK : ui_theme.BORDER, 3, 0));
            LinearLayout.LayoutParams position = (LinearLayout.LayoutParams) progressMarks[i].getLayoutParams();
            position.width = dp(i == step ? 24 : 6);
            progressMarks[i].setLayoutParams(position);
        }
        boolean motion = animate && changed && resumed && android.animation.ValueAnimator.areAnimatorsEnabled();
        long generation = transitionGeneration;
        if (motion) {
            pageContent.animate().translationX(dp(-24 * direction)).alpha(0).setDuration(110)
                    .withEndAction(() -> {
                        if (generation != transitionGeneration || leaving || !resumed) return;
                        bindPage(true);
                        scroll.scrollTo(0, 0);
                        pageContent.setTranslationX(dp(24 * direction));
                        pageContent.setAlpha(0);
                        pageContent.animate().translationX(0).alpha(1).setDuration(150)
                                .withEndAction(() -> {
                                    if (generation == transitionGeneration) {
                                        pageContent.setAlpha(1);
                                        pageContent.setTranslationX(0);
                                    }
                                }).start();
                    }).start();
        } else {
            bindPage(animate && resumed);
            scroll.post(() -> {
                if (generation == transitionGeneration) scroll.scrollTo(0, 0);
            });
        }
        if (motion && step == 3) {
            next.setAlpha(0);
            next.setScaleX(0.95f);
            next.setScaleY(0.95f);
            next.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).start();
        }
    }

    private void bindPage(boolean animateScene) {
        title.setText(TITLES[step]);
        description.setText(DESCRIPTIONS[step]);
        scene.showScene(step, animateScene);
    }

    /** Cancelling an entrance must leave the current controls and content fully visible. */
    private void cancelPageTransition() {
        transitionGeneration++;
        pageContent.animate().withEndAction(null).setListener(null).cancel();
        pageContent.setAlpha(1);
        pageContent.setTranslationX(0);
        pageContent.setTranslationY(0);
        pageContent.setScaleX(1);
        pageContent.setScaleY(1);
        next.animate().withEndAction(null).setListener(null).cancel();
        next.setAlpha(1);
        next.setTranslationX(0);
        next.setTranslationY(0);
        next.setScaleX(1);
        next.setScaleY(1);
    }

    private void finishGuide() {
        if (leaving) return;
        leaving = true;
        cancelPageTransition();
        scene.stopAnimations();
        // Replay is read-only even if the original installation has never completed this guide.
        if (!replayMode) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(COMPLETED, true).apply();
        }
        Intent home = new Intent(this, MainActivity.class)
                .putExtra(EXTRA_RETURN_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void goBack() {
        if (step > 0) {
            showStep(step - 1, true);
        } else if (replayMode) {
            finishGuide();
        } else {
            // System Back is an interruption, not the explicit Skip/Start completion action.
            leaving = true;
            cancelPageTransition();
            scene.stopAnimations();
            finish();
        }
    }

    @Override public void onBackPressed() { goBack(); }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        scene.setMotionEnabled(true);
        scene.showScene(step, true);
    }

    @Override protected void onPause() {
        resumed = false;
        cancelPageTransition();
        scene.setMotionEnabled(false);
        bindPage(false);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("guide_step", step);
        outState.putBoolean("guide_replay", replayMode);
        outState.putInt("guide_scroll", scroll.getScrollY());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        cancelPageTransition();
        scene.stopAnimations();
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        super.onDestroy();
    }

    private Button quietButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(ui_theme.MUTED);
        button.setBackground(ui_theme.shape(this, Color.TRANSPARENT, 10, 0));
        button.setMinWidth(dp(64));
        button.setMinimumWidth(dp(64));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        return button;
    }

    /** Horizontal paging is confined to the illustration/body, never the navigation buttons. */
    private final class tutorial_scroll_view extends ScrollView {
        private final int touchSlop;
        private float downX;
        private float downY;
        private boolean horizontal;
        private boolean vertical;
        private boolean multiplePointers;

        tutorial_scroll_view(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                horizontal = false;
                vertical = false;
                multiplePointers = false;
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                multiplePointers = true;
            }
            float deltaX = event.getX() - downX;
            float deltaY = event.getY() - downY;
            if (action == MotionEvent.ACTION_MOVE && !multiplePointers && !horizontal && !vertical) {
                if (Math.abs(deltaY) > touchSlop && Math.abs(deltaY) >= Math.abs(deltaX)) {
                    vertical = true;
                } else if (Math.abs(deltaX) > touchSlop && Math.abs(deltaX) > Math.abs(deltaY) * 1.5f) {
                    horizontal = true;
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            if (horizontal) {
                if (action == MotionEvent.ACTION_UP) {
                    if (!multiplePointers && Math.abs(deltaX) >= dp(64)
                            && Math.abs(deltaX) > Math.abs(deltaY) * 1.5f) {
                        int destination = step + (deltaX < 0 ? 1 : -1);
                        if (destination >= 0 && destination <= 3) showStep(destination, true);
                    }
                    horizontal = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    horizontal = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private static final class paper_frame extends FrameLayout {
        paper_frame(Context context) { super(context); }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (getChildCount() > 0) {
                LayoutParams position = (LayoutParams) getChildAt(0).getLayoutParams();
                int cap = ui_theme.dp(getContext(), 600);
                int available = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? cap
                        : Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
                position.width = Math.min(cap, available);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return ui_theme.text(this, value, size, color, bold);
    }
    private int dp(float value) { return ui_theme.dp(this, value); }
}
