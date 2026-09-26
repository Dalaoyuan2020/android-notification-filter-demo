package com.example.notificationdemo.filter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Optional, explicit model configuration. Credentials never enter saved instance state. */
public final class ModelSettingsActivity extends Activity {
    private static final int INK = 0xFF182D43;
    private static final int MUTED = 0xFF617181;
    private static final int TEAL = 0xFF007372;
    private static final int BACKGROUND = 0xFFF4F7FA;
    private static final int BORDER = 0xFFDFE7ED;
    private static final int SOFT_TEAL = 0xFFE6F6F1;
    private static final int AMBER = 0xFF96590F;
    private static final int SOFT_AMBER = 0xFFFFF6E2;
    private static final int MODE_ID_BASE = 4100;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Draft[] drafts = {new Draft(), new Draft()};
    private Future<?> testTask;
    private ModelConfig.Mode selectedMode = ModelConfig.Mode.KEYWORDS;
    private ModelConfig saved;
    private int selectedProfile;
    private boolean loading;
    private boolean dirty;
    private boolean testRunning;
    private boolean destroyed;
    private boolean storageUnavailable;
    private RadioGroup modes;
    private Switch remoteSwitch;
    private Button officialButton;
    private Button relayButton;
    private Button saveButton;
    private Button testButton;
    private TextView strategyDetail;
    private TextView saveStatus;
    private TextView profileTitle;
    private TextView testStatus;
    private EditText labelField;
    private EditText urlField;
    private EditText modelField;
    private EditText keyField;

    private static final class Draft {
        String label = "";
        String baseUrl = "";
        String model = "";
        String key = "";

        void read(ModelConfig.Profile profile) {
            label = profile.label;
            baseUrl = profile.baseUrl;
            model = profile.model;
            key = profile.apiKey;
        }

        ModelConfig.Profile profile() {
            return new ModelConfig.Profile(label.trim(), baseUrl.trim(), model.trim(), key.trim());
        }

        void clearKey() { key = ""; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saved = ModelStore.load(this);
        selectedMode = saved.mode;
        storageUnavailable = !saved.storageError.isEmpty();
        drafts[0].read(saved.official);
        drafts[1].read(saved.relay);
        selectedProfile = selectedMode == ModelConfig.Mode.RELAY ? 1 : 0;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets edge = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(edge.left, edge.top, edge.right, edge.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame, new ScrollView.LayoutParams(-1, -2));
        LinearLayout page = column();
        page.setPadding(dp(20), dp(22), dp(20), dp(28));
        int width = getResources().getDisplayMetrics().widthPixels;
        frame.addView(page, new FrameLayout.LayoutParams(width > dp(720) ? dp(720) : -1,
                -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        Button back = button("返回通知筛选", false);
        back.setOnClickListener(view -> finish());
        page.addView(back, fullWidth());
        space(page, 22);
        page.addView(text("模型与双路对照", 28, INK, true));
        space(page, 8);
        page.addView(text("先保存配置，再用合成消息测试连接。所有服务地址与模型 ID 均由您填写。", 14, MUTED, false));
        space(page, 22);

        makeStrategyCard(page);
        makeProfileCard(page);
        makeTestCard(page);
        TextView note = text("保存任何模型配置都会关闭自动清除。未保存的编辑在离开页面或旋转屏幕后丢弃；密钥不会写入页面恢复状态。", 12, MUTED, false);
        note.setGravity(Gravity.CENTER);
        page.addView(note);
        setContentView(scroll);
        scroll.requestApplyInsets();

        loading = true;
        modes.check(MODE_ID_BASE + selectedMode.ordinal());
        remoteSwitch.setChecked(saved.remoteEnabled);
        populateProfile();
        loading = false;
        dirty = false;
        saveStatus.setText(storageUnavailable
                ? "密钥存储不可用：连接测试已暂停。请重新填写所需配置的完整密钥并保存；旧密钥无法在此恢复。"
                : "当前配置已保存；保存修改后将切回观察模式。");
        if (state != null && !storageUnavailable) {
            saveStatus.setText("已重新读取上次保存的配置。未保存内容（含密钥）不会随页面重建恢复。");
        }
        updateViewState();
    }

    private void makeStrategyCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        card.addView(text("01  判断策略", 17, INK, true));
        space(card, 10);
        modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        String[] names = {"关键词 · 本机规则（默认）", "官方模型 · 使用官方配置",
                "中转模型 · 使用中转配置", "双路对照 · 只观察，不清除"};
        for (int i = 0; i < names.length; i++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(MODE_ID_BASE + i);
            choice.setText(names[i]);
            choice.setTextColor(INK);
            choice.setTextSize(14);
            choice.setMinHeight(dp(48));
            choice.setSaveEnabled(false);
            choice.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));
            modes.addView(choice, fullWidth());
        }
        modes.setOnCheckedChangeListener((group, id) -> {
            int index = id - MODE_ID_BASE;
            if (index < 0 || index >= ModelConfig.Mode.values().length) {
                return;
            }
            selectedMode = ModelConfig.Mode.values()[index];
            markDirty();
        });
        card.addView(modes);
        space(card, 8);
        strategyDetail = text("", 13, MUTED, false);
        card.addView(strategyDetail);
        space(card, 14);
        TextView disclosure = text("开启并保存远程处理后，符合目标范围与保护规则的通知，其包名、标题、正文和类别将发送给您填写的服务；双路对照会发送给两路服务。请先使用合成样本确认服务与规则。", 13, AMBER, false);
        disclosure.setPadding(dp(12), dp(12), dp(12), dp(12));
        disclosure.setBackground(background(SOFT_AMBER, 10, 0));
        card.addView(disclosure);
        space(card, 10);
        remoteSwitch = new Switch(this);
        remoteSwitch.setText("允许远程处理目标通知");
        remoteSwitch.setTextSize(14);
        remoteSwitch.setTextColor(INK);
        remoteSwitch.setMinHeight(dp(52));
        remoteSwitch.setPadding(0, dp(8), 0, dp(8));
        remoteSwitch.setShowText(false);
        remoteSwitch.setSaveEnabled(false);
        remoteSwitch.setOnCheckedChangeListener((button, checked) -> markDirty());
        card.addView(remoteSwitch, fullWidth());
        card.addView(text("默认关闭，修改开关后需保存才生效。模型策略下关闭并保存后，将保留通知且不发新请求，不会回退到关键词清除；已发出的请求无法收回。关键词策略始终在本机处理。", 12, MUTED, false));
    }

    private void makeProfileCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        card.addView(text("02  服务配置", 17, INK, true));
        space(card, 8);
        card.addView(text("“官方”和“中转”仅区分两条测试路线。未预设供应商，也尚未验证您的服务是否兼容。", 13, MUTED, false));
        space(card, 14);
        LinearLayout selectors = new LinearLayout(this);
        selectors.setOrientation(LinearLayout.HORIZONTAL);
        officialButton = button("编辑官方配置", false);
        relayButton = button("编辑中转配置", false);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.setMarginEnd(dp(8));
        selectors.addView(officialButton, first);
        selectors.addView(relayButton, new LinearLayout.LayoutParams(0, -2, 1));
        officialButton.setOnClickListener(view -> selectProfile(0));
        relayButton.setOnClickListener(view -> selectProfile(1));
        card.addView(selectors, fullWidth());
        space(card, 16);
        profileTitle = text("", 14, TEAL, true);
        card.addView(profileTitle);
        labelField = input(card, "配置标签 / 版本标识", "例如：本轮基线 v1", false, false);
        urlField = input(card, "Base URL · HTTPS", "服务文档提供的兼容 API 基础地址", false, true);
        modelField = input(card, "模型 ID", "服务文档中的精确模型标识", false, false);
        keyField = input(card, "API Key · 本机加密保存", "无需鉴权的自训端点可留空", true, false);
        card.addView(text("地址不含账号密码、查询参数或片段。手机测试时不要填写电脑的 localhost；密钥请填写在独立的 API Key 字段。", 12, MUTED, false));
        space(card, 16);
        saveButton = button("保存配置并关闭自动清除", true);
        saveButton.setOnClickListener(view -> saveConfiguration());
        card.addView(saveButton, fullWidth());
        space(card, 10);
        saveStatus = text("", 12, TEAL, false);
        card.addView(saveStatus);
    }

    private void makeTestCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        card.addView(text("03  单路连接测试", 17, INK, true));
        space(card, 10);
        card.addView(text("只测试上方正在编辑的这一份配置。点击测试会单独发送下方固定合成消息，不读取真实通知；允许在远程处理开关关闭时测试。", 13, MUTED, false));
        space(card, 12);
        DecisionEngine.Input sample = ModelClient.connectionTestInput();
        TextView payload = text("包名：" + sample.packageName + "\n标题：" + sample.title
                + "\n正文：" + sample.text, 12, MUTED, false);
        payload.setPadding(dp(12), dp(12), dp(12), dp(12));
        payload.setBackground(background(BACKGROUND, 10, 0));
        card.addView(payload, fullWidth());
        space(card, 14);
        testButton = button("仅测试当前配置（合成消息）", false);
        testButton.setOnClickListener(view -> testConnection());
        card.addView(testButton, fullWidth());
        space(card, 10);
        testStatus = text("尚未测试。接口协议兼容性和真实服务连接均待验证。", 13, MUTED, false);
        testStatus.setTextIsSelectable(true);
        card.addView(testStatus);
    }

    private void selectProfile(int which) {
        if (selectedProfile == which || testRunning) {
            return;
        }
        captureProfile();
        selectedProfile = which;
        populateProfile();
        testStatus.setText("已切换配置。下次测试仅发送到当前这一条路线。");
        updateViewState();
    }

    private void captureProfile() {
        Draft draft = drafts[selectedProfile];
        draft.label = labelField.getText().toString();
        draft.baseUrl = urlField.getText().toString();
        draft.model = modelField.getText().toString();
        draft.key = keyField.getText().toString();
    }

    private void populateProfile() {
        boolean previousLoading = loading;
        loading = true;
        Draft draft = drafts[selectedProfile];
        labelField.setText(draft.label);
        urlField.setText(draft.baseUrl);
        modelField.setText(draft.model);
        keyField.setText(draft.key);
        keyField.setSelection(keyField.length());
        loading = previousLoading;
    }

    private void markDirty() {
        if (loading || destroyed || saveStatus == null) {
            return;
        }
        dirty = true;
        saveStatus.setText("有未保存修改 · 请先保存，再测试连接或返回主页使用。");
        updateViewState();
    }

    private void saveConfiguration() {
        if (testRunning) {
            return;
        }
        captureProfile();
        try {
            saved = ModelStore.save(this, selectedMode, remoteSwitch.isChecked(),
                    drafts[0].profile(), drafts[1].profile());
            drafts[0].read(saved.official);
            drafts[1].read(saved.relay);
            storageUnavailable = !saved.storageError.isEmpty();
            loading = true;
            selectedMode = saved.mode;
            modes.check(MODE_ID_BASE + selectedMode.ordinal());
            remoteSwitch.setChecked(saved.remoteEnabled);
            populateProfile();
            loading = false;
            dirty = false;
            saveStatus.setTextColor(TEAL);
            saveStatus.setText("已保存 · 自动清除已关闭，回到主界面后按需重新开启。");
            testStatus.setText("配置已保存，尚未验证本次配置的服务连接。");
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(saveButton.getWindowToken(), 0);
            }
            Toast.makeText(this, "配置已保存，已切回观察模式", Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalArgumentException error) {
            // Error text may contain endpoint details; do not expose arbitrary exception content.
            saveStatus.setText("保存未完成。请检查 HTTPS 基础地址、模型 ID 和系统密钥存储，返回主界面核对当前配置与自动开关后重试。");
            saveStatus.setTextColor(AMBER);
        }
        updateViewState();
    }

    private void testConnection() {
        if (testRunning) {
            return;
        }
        if (dirty) {
            testStatus.setText("有未保存修改，请先点击“保存配置并关闭自动清除”，再测试连接。");
            Toast.makeText(this, "请先保存配置", Toast.LENGTH_SHORT).show();
            return;
        }
        if (storageUnavailable) {
            testStatus.setText("密钥存储不可用。请重新填写所需配置的完整密钥并保存，连接测试暂不执行。");
            return;
        }
        final ModelConfig.Profile profile = selectedProfile == 0 ? saved.official : saved.relay;
        if (profile.baseUrl.trim().isEmpty() || profile.model.trim().isEmpty()) {
            testStatus.setText("请先填写当前配置的 HTTPS Base URL 和模型 ID，然后保存。");
            return;
        }
        final String route = selectedProfile == 0 ? "官方配置" : "中转配置";
        testRunning = true;
        testStatus.setText("正在测试" + route + " · 仅发送固定合成消息，请稍候…");
        updateViewState();
        final WeakReference<ModelSettingsActivity> screen = new WeakReference<>(this);
        final Handler resultHandler = main;
        testTask = worker.submit(() -> {
            ModelClient.Result result = null;
            try {
                result = ModelClient.testConnection(profile);
            } catch (RuntimeException ignored) {
                // Neither request details nor exception messages enter logs or UI.
            }
            final ModelClient.Result completed = result;
            resultHandler.post(() -> {
                ModelSettingsActivity activity = screen.get();
                if (activity == null || activity.destroyed || activity.isFinishing()) {
                    return;
                }
                activity.finishTest(route, profile, completed);
            });
        });
    }

    private void finishTest(String route, ModelConfig.Profile profile, ModelClient.Result result) {
        testRunning = false;
        if (result == null) {
            testStatus.setText("连接测试未完成。请检查服务配置后重试；本次没有处理真实通知。");
        } else if (result.success) {
            String verdict = result.action == DecisionEngine.Action.REMOVE ? "建议清除"
                    : result.action == DecisionEngine.Action.SKIP ? "跳过" : "保留";
            String output = String.format(Locale.CHINA, "%s测试成功 · %d ms\n返回判断：%s\n原因：%s\n仅代表这次合成请求成功，尚未验证真实通知效果。",
                    route, result.latencyMs, verdict, redact(result.reason, profile.apiKey));
            testStatus.setText(output);
        } else {
            testStatus.setText("测试失败 · " + redact(result.error, profile.apiKey)
                    + "\n未验证该接口兼容。请核对基础地址、鉴权方式、模型 ID 和服务文档。");
        }
        updateViewState();
    }

    private void updateViewState() {
        if (strategyDetail == null || profileTitle == null || testButton == null) {
            return;
        }
        String detail = selectedMode == ModelConfig.Mode.KEYWORDS
                ? "关键词在本机判断；本策略不会调用远程模型。"
                : selectedMode == ModelConfig.Mode.OFFICIAL
                ? "目标通知送到官方配置对应的服务。保存后先观察结果，再按需开启自动清除。"
                : selectedMode == ModelConfig.Mode.RELAY
                ? "目标通知送到中转配置对应的服务；自训模型需由该服务提供兼容接口。"
                : "同一通知分别交给两路配置，分别记录结果。即使两路均建议清除，也不会执行删除。";
        strategyDetail.setText(detail);
        profileTitle.setText(selectedProfile == 0 ? "正在编辑：官方配置" : "正在编辑：中转配置");
        officialButton.setBackground(background(selectedProfile == 0 ? TEAL : SOFT_TEAL, 11, 0));
        officialButton.setTextColor(selectedProfile == 0 ? Color.WHITE : TEAL);
        relayButton.setBackground(background(selectedProfile == 1 ? TEAL : SOFT_TEAL, 11, 0));
        relayButton.setTextColor(selectedProfile == 1 ? Color.WHITE : TEAL);
        officialButton.setEnabled(!testRunning);
        relayButton.setEnabled(!testRunning);
        saveButton.setEnabled(!testRunning);
        testButton.setEnabled(!testRunning && !storageUnavailable);
        labelField.setEnabled(!testRunning);
        urlField.setEnabled(!testRunning);
        modelField.setEnabled(!testRunning);
        keyField.setEnabled(!testRunning);
        remoteSwitch.setEnabled(!testRunning);
        for (int i = 0; i < modes.getChildCount(); i++) {
            modes.getChildAt(i).setEnabled(!testRunning);
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (testTask != null) {
            testTask.cancel(true);
        }
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        for (Draft draft : drafts) {
            draft.clearKey();
        }
        if (keyField != null) {
            keyField.getText().clear();
        }
        saved = null;
        super.onDestroy();
    }

    private static String redact(String source, String key) {
        String safe = source == null ? "无详细说明" : source;
        if (key != null && !key.isEmpty()) {
            safe = safe.replace(key, "[密钥已隐藏]");
        }
        return safe.length() > 700 ? safe.substring(0, 700) + "…" : safe;
    }

    private EditText input(LinearLayout parent, String label, String hint, boolean password, boolean uri) {
        space(parent, 15);
        parent.addView(text(label, 13, INK, true));
        space(parent, 7);
        EditText field = new EditText(this);
        field.setTextColor(INK);
        field.setHintTextColor(0xFF85939E);
        field.setTextSize(14);
        field.setHint(hint);
        field.setContentDescription(label);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : uri ? InputType.TYPE_TEXT_VARIATION_URI : InputType.TYPE_TEXT_VARIATION_NORMAL));
        field.setPadding(dp(12), dp(12), dp(12), dp(12));
        field.setBackground(background(BACKGROUND, 10, BORDER));
        field.setSaveEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { markDirty(); }
            @Override public void afterTextChanged(Editable editable) {}
        });
        parent.addView(field, fullWidth());
        space(parent, 5);
        return field;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout view = column();
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        view.setBackground(background(Color.WHITE, 18, BORDER));
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(16);
        parent.addView(view, params);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextColor(primary ? Color.WHITE : TEAL);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setBackground(background(primary ? TEAL : SOFT_TEAL, 11, 0));
        view.setBackgroundTintList(null);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setStateListAnimator(null);
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(3), 1f);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private GradientDrawable background(int fill, int radius, int stroke) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(dp(radius));
        if (stroke != 0) {
            result.setStroke(dp(1), stroke);
        }
        return result;
    }

    private void space(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
