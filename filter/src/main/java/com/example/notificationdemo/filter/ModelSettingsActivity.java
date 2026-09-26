package com.example.notificationdemo.filter;

import android.app.Activity;
import android.graphics.Color;
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
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Optional, explicit model configuration. Credentials never enter saved instance state. */
public final class ModelSettingsActivity extends Activity {
    private static final int INK = ui_theme.INK;
    private static final int MUTED = ui_theme.MUTED;
    private static final int TEAL = ui_theme.ACCENT;
    private static final int BACKGROUND = ui_theme.PAPER;
    private static final int BORDER = ui_theme.BORDER;
    private static final int SOFT_TEAL = ui_theme.SOFT_GREEN;
    private static final int AMBER = ui_theme.WARNING;
    private static final int SOFT_AMBER = ui_theme.SOFT_YELLOW;
    private static final int MODE_ID_BASE = 4100;
    private static final int PRESET_TEAM = 0;
    private static final int PRESET_TYPESAFE = 1;
    private static final int PRESET_BOCHA = 2;
    private static final int PRESET_CUSTOM = 3;
    private static final ModelConfig.Mode[] MODE_CHOICES = {ModelConfig.Mode.KEYWORDS,
            ModelConfig.Mode.OFFICIAL, ModelConfig.Mode.BOCHA, ModelConfig.Mode.RELAY, ModelConfig.Mode.COMPARE};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Draft[] drafts = {new Draft(), new Draft(), new Draft()};
    private Future<?> testTask;
    private ModelConfig.Mode selectedMode = ModelConfig.Mode.KEYWORDS;
    private ModelConfig saved;
    private String teamKey = "";
    private String displayedKeyOrigin = "";
    private int selectedProfile;
    private boolean loading;
    private boolean dirty;
    private boolean testRunning;
    private boolean destroyed;
    private boolean storageUnavailable;
    private boolean teamKeyNeedsReview;
    private int sectionIndex;
    private RadioGroup modes;
    private Switch remoteSwitch;
    private Button officialButton;
    private Button bochaButton;
    private Button relayButton;
    private Button saveButton;
    private Button testButton;
    private TextView strategyDetail;
    private TextView saveStatus;
    private TextView profileTitle;
    private TextView testStatus;
    private TextView migrationNotice;
    private TextView keyDescription;
    private TextView externalWarning;
    private EditText labelField;
    private EditText urlField;
    private EditText modelField;
    private EditText keyField;
    private EditText thresholdField;
    private Spinner presetField;
    private Spinner teamModelField;
    private LinearLayout teamModelPanel;
    private LinearLayout modelPanel;
    private CheckBox compareOfficial;
    private CheckBox compareBocha;
    private CheckBox compareRelay;
    private LinearLayout comparePanel;

    private static final class Draft {
        String label = "";
        String baseUrl = "";
        String model = "";
        String key = "";
        boolean originKeyCleared;
        int preset = PRESET_CUSTOM;

        void read(ModelConfig.Profile profile) {
            label = profile.label;
            baseUrl = profile.baseUrl;
            model = profile.model;
            key = profile.apiKey;
            originKeyCleared = false;
            preset = presetIndex(profile);
        }

        ModelConfig.Profile profile() {
            return new ModelConfig.Profile(label.trim(), baseUrl.trim(), model.trim(), key.trim(),
                    ModelConfig.Protocol.JEV_SYSTEMONE);
        }

        void clearKey() { key = ""; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        saved = ModelStore.load(this);
        selectedMode = saved.mode;
        storageUnavailable = !saved.storageError.isEmpty();
        drafts[0].read(saved.official);
        drafts[1].read(saved.bocha);
        drafts[2].read(saved.relay);
        readSharedTeamKey();
        selectedProfile = selectedMode == ModelConfig.Mode.BOCHA ? 1 : selectedMode == ModelConfig.Mode.RELAY ? 2 : 0;
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
        scroll.setBackground(ui_theme.paper(this));
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
        page.setPadding(dp(ui_theme.PAGE_MARGIN), dp(ui_theme.PAGE_TOP),
                dp(ui_theme.PAGE_MARGIN), dp(32));
        int width = getResources().getDisplayMetrics().widthPixels;
        frame.addView(page, new FrameLayout.LayoutParams(width > dp(720) ? dp(720) : -1,
                -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        Button back = button("返回通知筛选", false);
        back.setOnClickListener(view -> finish());
        page.addView(back, fullWidth());
        space(page, 22);
        page.addView(text("模型与三路对照", ui_theme.TITLE_SP, INK, true));
        space(page, 8);
        page.addView(text("统一使用 Jev SystemOne。先选择服务预设与模型，保存后用合成消息测试；预设不含密钥。", 14, MUTED, false));
        space(page, 22);

        makeStrategyCard(page);
        makeProfileCard(page);
        makeTestCard(page);
        TextView note = text("保存任何模型配置都会关闭自动清除。未保存的编辑在离开页面或旋转屏幕后丢弃；密钥不会写入页面恢复状态。", 12, MUTED, false);
        note.setGravity(Gravity.START);
        page.addView(note);
        setContentView(scroll);
        scroll.requestApplyInsets();

        loading = true;
        modes.check(MODE_ID_BASE + modeIndex(selectedMode));
        remoteSwitch.setChecked(saved.remoteEnabled);
        thresholdField.setText(Double.toString(saved.threshold));
        compareOfficial.setChecked(saved.compareOfficial);
        compareBocha.setChecked(saved.compareBocha);
        compareRelay.setChecked(saved.compareRelay);
        populateProfile();
        loading = false;
        dirty = teamKeyNeedsReview;
        saveStatus.setText(teamKeyNeedsReview
                ? "旧 1052 路线的 key 不一致，页面未沿用任何一份。请重新填写共用的平台 key 并保存。"
                : storageUnavailable
                ? "密钥存储不可用：连接测试已暂停。请重新填写所需配置的完整密钥并保存；旧密钥无法在此恢复。"
                : "当前配置已保存；保存修改后将切回观察模式。");
        if (state != null && !storageUnavailable && !teamKeyNeedsReview) {
            saveStatus.setText("已重新读取上次保存的配置。未保存内容（含密钥）不会随页面重建恢复。");
        }
        updateViewState();
    }

    private void makeStrategyCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        card.addView(text("01  判断策略", ui_theme.SECTION_SP, INK, true));
        space(card, 10);
        modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        String[] names = {"关键词 · 本机规则（默认）", "路线 1 · 单路判断",
                "路线 2 · 单路判断", "路线 3 · 单路判断", "多路对照 · 只观察，不清除"};
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
            if (index < 0 || index >= MODE_CHOICES.length) {
                return;
            }
            selectedMode = MODE_CHOICES[index];
            markDirty();
        });
        card.addView(modes);
        space(card, 8);
        strategyDetail = text("", 13, MUTED, false);
        card.addView(strategyDetail);
        comparePanel = column();
        space(comparePanel, 10);
        comparePanel.addView(text("选择参与对照的路线", 13, INK, true));
        compareOfficial = routeCheck(comparePanel, "路线 1");
        compareBocha = routeCheck(comparePanel, "路线 2");
        compareRelay = routeCheck(comparePanel, "路线 3");
        card.addView(comparePanel, fullWidth());
        thresholdField = input(card, "保留阈值 · 0 至 1", "0.5", false, false);
        thresholdField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(text("最终概率达到阈值时保留。默认 0.5；多路对照无论概率多少都不清除。", 12, MUTED, false));
        space(card, 14);
        TextView disclosure = text("开启并保存后，符合目标与保护规则的通知数据将发送给选定服务；JEV 路线还可附带注意力页启用的近期行为摘要。多路对照只发送给已勾选路线。请先用合成样本测试。", 13, AMBER, false);
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
        ui_theme.toggle(remoteSwitch);
        remoteSwitch.setOnCheckedChangeListener((button, checked) -> markDirty());
        card.addView(remoteSwitch, fullWidth());
        card.addView(text("默认关闭，修改开关后需保存才生效。模型策略下关闭并保存后，将保留通知且不发新请求，不会回退到关键词清除；已发出的请求无法收回。关键词策略始终在本机处理。", 12, MUTED, false));
    }

    private void makeProfileCard(LinearLayout parent) {
        LinearLayout card = card(parent);
        card.addView(text("02  服务配置", ui_theme.SECTION_SP, INK, true));
        space(card, 8);
        card.addView(text("默认三路为团队 1052 的微调版 / 原版 / 官方模型。1052 的四个模型共用一份平台 key；其他服务使用各自的 key。", 13, MUTED, false));
        space(card, 8);
        migrationNotice = text("", 13, AMBER, false);
        card.addView(migrationNotice, fullWidth());
        space(card, 14);
        LinearLayout selectors = new LinearLayout(this);
        selectors.setOrientation(LinearLayout.HORIZONTAL);
        officialButton = button("路线 1", false);
        bochaButton = button("路线 2", false);
        relayButton = button("路线 3", false);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.setMarginEnd(dp(8));
        selectors.addView(officialButton, first);
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, -2, 1);
        middle.setMarginEnd(dp(8));
        selectors.addView(bochaButton, middle);
        selectors.addView(relayButton, new LinearLayout.LayoutParams(0, -2, 1));
        officialButton.setOnClickListener(view -> selectProfile(0));
        bochaButton.setOnClickListener(view -> selectProfile(1));
        relayButton.setOnClickListener(view -> selectProfile(2));
        card.addView(selectors, fullWidth());
        space(card, 16);
        profileTitle = text("", 14, TEAL, true);
        card.addView(profileTitle);
        space(card, 12);
        card.addView(text("服务预设", 13, INK, true));
        presetField = spinner(new String[]{"团队中转 1052（推荐）", "TypeSafe 官方", "Bocha 团队网关", "自定义 Jev 服务"});
        presetField.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!loading && !testRunning && drafts[selectedProfile].preset != position) {
                    loadPreset(position);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        card.addView(presetField, fullWidth());
        space(card, 8);
        card.addView(text("接口协议：Jev SystemOne（固定）", 12, MUTED, false));
        labelField = input(card, "配置标签 / 版本标识", "例如：本轮基线 v1", false, false);
        urlField = input(card, "接口地址 · HTTPS", "JEV 可填根地址、/v1 或 /v1/systemone", false, true);
        teamModelPanel = column();
        space(teamModelPanel, 15);
        teamModelPanel.addView(text("1052 模型 · 共用平台 key", 13, INK, true));
        teamModelField = spinner(new String[]{"local-systemone-ft · 最终微调版（默认）",
                "local-systemone-v1 · 原版", "typesafe-jev · 官方", "bocha-jev · 博查"});
        teamModelField.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String[] models = ModelConfig.team1052Models();
                if (!loading && !testRunning && drafts[selectedProfile].preset == PRESET_TEAM
                        && position >= 0 && position < models.length
                        && !models[position].equals(drafts[selectedProfile].model)) {
                    Draft draft = drafts[selectedProfile];
                    draft.model = models[position];
                    boolean previousLoading = loading;
                    loading = true;
                    modelField.setText(draft.model);
                    loading = previousLoading;
                    markDirty();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        teamModelPanel.addView(teamModelField, fullWidth());
        card.addView(teamModelPanel, fullWidth());
        modelPanel = column();
        modelField = input(modelPanel, "模型 ID", "服务文档中的精确模型标识", false, false);
        card.addView(modelPanel, fullWidth());
        externalWarning = text("该模型会把通知内容发往外部公司，真实私聊请用本地模型", 13, AMBER, false);
        card.addView(externalWarning, fullWidth());
        keyField = input(card, "API Key · 本机加密保存", "无需鉴权的自训端点可留空", true, false);
        keyDescription = text("", 12, MUTED, false);
        card.addView(keyDescription, fullWidth());
        urlField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!loading) {
                    enforceKeyOrigin();
                }
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
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
        card.addView(text("03  单路连接测试", ui_theme.SECTION_SP, INK, true));
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

    private void loadPreset(int which) {
        if (testRunning) {
            return;
        }
        captureProfile();
        Draft draft = drafts[selectedProfile];
        if (which == PRESET_CUSTOM) {
            // An editable endpoint must never carry a key copied from another service.
            draft.label = "自定义 Jev 服务";
            draft.baseUrl = "";
            draft.model = "";
            draft.key = "";
        } else {
            ModelConfig.Profile preset = which == PRESET_TEAM ? ModelConfig.presetTeam1052()
                    : which == PRESET_TYPESAFE ? ModelConfig.presetTypeSafe() : ModelConfig.presetBocha();
            draft.label = preset.label;
            draft.baseUrl = preset.baseUrl;
            draft.model = preset.model;
            draft.key = which == PRESET_TEAM ? teamKey : "";
        }
        draft.preset = which;
        draft.originKeyCleared = false;
        populateProfile();
        markDirty();
        saveStatus.setText(which == PRESET_TEAM
                ? "已载入 1052 预设；四个模型共用平台 key。请保存后测试。"
                : "已切换服务预设，该路线 key 已清空。请填写对应服务的 key，保存后测试。");
    }

    private void captureProfile() {
        enforceKeyOrigin();
        Draft draft = drafts[selectedProfile];
        draft.label = labelField.getText().toString();
        draft.baseUrl = urlField.getText().toString();
        draft.model = modelField.getText().toString();
        draft.key = keyField.getText().toString();
        if (!draft.key.isEmpty()) {
            draft.originKeyCleared = false;
        }
        if (ModelConfig.isTeam1052(draft.profile())) {
            // A host edit clears this editor, not the platform credential already
            // used by other routes. Only an explicit key edit at a stable origin
            // (or selecting the team preset) may replace/clear the shared key.
            if (!draft.originKeyCleared) {
                teamKey = draft.key;
            }
            syncSharedTeamKey();
        }
    }

    private void populateProfile() {
        boolean previousLoading = loading;
        loading = true;
        Draft draft = drafts[selectedProfile];
        labelField.setText(draft.label);
        urlField.setText(draft.baseUrl);
        modelField.setText(draft.model);
        keyField.setText(draft.key);
        presetField.setSelection(draft.preset);
        String[] models = ModelConfig.team1052Models();
        int selectedModel = 0;
        for (int i = 0; i < models.length; i++) {
            if (models[i].equals(draft.model)) {
                selectedModel = i;
                break;
            }
        }
        teamModelField.setSelection(selectedModel);
        keyField.setSelection(keyField.length());
        displayedKeyOrigin = credentialOrigin(draft.baseUrl);
        loading = previousLoading;
    }

    private void enforceKeyOrigin() {
        String nextOrigin = credentialOrigin(urlField.getText().toString());
        if (displayedKeyOrigin.equals(nextOrigin)) {
            return;
        }
        displayedKeyOrigin = nextOrigin;
        Draft draft = drafts[selectedProfile];
        draft.originKeyCleared = true;
        draft.key = "";
        // Clear only this editor's credential. The independent 1052 key remains
        // available to other 1052 routes and to an explicit return to that preset.
        keyField.getText().clear();
        updateViewState();
    }

    private static String credentialOrigin(String address) {
        String value = address.trim();
        int schemeEnd = value.indexOf("://");
        if (schemeEnd < 1) {
            return "";
        }
        int end = value.length();
        // Parse only the authority so an in-progress same-host path correction
        // (including temporarily unescaped characters) cannot discard its key.
        for (char delimiter : new char[]{'/', '?', '#'}) {
            int index = value.indexOf(delimiter, schemeEnd + 3);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        try {
            URI origin = URI.create(value.substring(0, end));
            int port = origin.getPort();
            if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null
                    || origin.getRawUserInfo() != null || port == 0 || port < -1 || port > 65535) {
                return "";
            }
            return "https://" + origin.getHost().toLowerCase(Locale.ROOT) + ":" + (port == -1 ? 443 : port);
        } catch (IllegalArgumentException invalid) {
            return "";
        }
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
        syncSharedTeamKey();
        boolean hasTeamRoute = false;
        for (Draft draft : drafts) {
            hasTeamRoute |= ModelConfig.isTeam1052(draft.profile());
        }
        if (teamKeyNeedsReview && hasTeamRoute && teamKey.trim().isEmpty()) {
            saveStatus.setText("旧 1052 路线的 key 不一致。请重新填写要共用的 1052 平台 key，再保存；不会替您选择旧 key。");
            saveStatus.setTextColor(AMBER);
            return;
        }
        double threshold;
        try {
            threshold = Double.parseDouble(thresholdField.getText().toString().trim());
            if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException invalid) {
            thresholdField.setError("请填写 0 至 1 的概率阈值");
            return;
        }
        try {
            saved = ModelStore.save(this, selectedMode, remoteSwitch.isChecked(),
                    drafts[0].profile(), drafts[2].profile(), drafts[1].profile(), threshold,
                    compareOfficial.isChecked(), compareRelay.isChecked(), compareBocha.isChecked());
            drafts[0].read(saved.official);
            drafts[1].read(saved.bocha);
            drafts[2].read(saved.relay);
            readSharedTeamKey();
            storageUnavailable = !saved.storageError.isEmpty();
            loading = true;
            selectedMode = saved.mode;
            modes.check(MODE_ID_BASE + modeIndex(selectedMode));
            remoteSwitch.setChecked(saved.remoteEnabled);
            thresholdField.setText(Double.toString(saved.threshold));
            compareOfficial.setChecked(saved.compareOfficial);
            compareBocha.setChecked(saved.compareBocha);
            compareRelay.setChecked(saved.compareRelay);
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
        if (teamKeyNeedsReview) {
            testStatus.setText("请先重新填写共用的 1052 平台 key 并保存，再测试连接。");
            return;
        }
        final ModelConfig.Profile profile = selectedProfile == 0 ? saved.official
                : selectedProfile == 1 ? saved.bocha : saved.relay;
        final double testThreshold = saved.threshold;
        if (profile.baseUrl.trim().isEmpty() || profile.model.trim().isEmpty()) {
            testStatus.setText("请先填写当前配置的 HTTPS Base URL 和模型 ID，然后保存。");
            return;
        }
        final String route = routeName(selectedProfile);
        testRunning = true;
        testStatus.setText("正在测试" + route + " · 仅发送固定合成消息，请稍候…");
        updateViewState();
        final WeakReference<ModelSettingsActivity> screen = new WeakReference<>(this);
        final Handler resultHandler = main;
        testTask = worker.submit(() -> {
            ModelClient.Result result = null;
            try {
                result = ModelClient.testConnection(profile, testThreshold);
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
            String probability = String.format(Locale.CHINA, "保留概率 p_jev = %.3f", result.probability)
                    + (result.hasProbability ? "" : "（由 choice 换算）");
            String output = String.format(Locale.CHINA, "%s测试成功 · %s · %d ms\n%s\n返回判断：%s\n原因：%s\n仅代表这次合成请求成功，尚未验证真实通知效果。",
                    route, httpStatus(result.httpStatus), result.latencyMs, probability, verdict,
                    redact(result.reason, profile.apiKey));
            testStatus.setText(output);
        } else {
            String reason = result.httpStatus == 401 ? "key 无效"
                    : result.httpStatus == 422 ? "协议或格式不对"
                    : result.httpStatus == 404 ? "地址不对，1052 请用 /jev"
                    : redact(result.reason, profile.apiKey);
            testStatus.setText("测试失败 · " + httpStatus(result.httpStatus) + " · " + result.latencyMs + " ms"
                    + "\n保留概率 p_jev = —（本次未取得有效结果）"
                    + "\n" + reason + " · " + redact(result.error, profile.apiKey)
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
                ? "允许远程处理并保存后，目标通知才会送到路线 1。先观察概率，再按需开启自动清除。"
                : selectedMode == ModelConfig.Mode.BOCHA
                ? "允许远程处理并保存后，目标通知才会送到路线 2。请先用合成消息确认配置。"
                : selectedMode == ModelConfig.Mode.RELAY
                ? "允许远程处理并保存后，目标通知才会送到路线 3。请先用合成消息确认配置。"
                : "允许远程处理并保存后，同一通知分别交给已勾选路线，最多三路；显示原始与融合概率，对照始终不清除。";
        strategyDetail.setText(detail);
        profileTitle.setText("正在编辑：" + routeName(selectedProfile));
        officialButton.setBackground(background(selectedProfile == 0 ? TEAL : SOFT_TEAL, 11, 0));
        officialButton.setTextColor(selectedProfile == 0 ? Color.WHITE : TEAL);
        bochaButton.setBackground(background(selectedProfile == 1 ? TEAL : SOFT_TEAL, 11, 0));
        bochaButton.setTextColor(selectedProfile == 1 ? Color.WHITE : TEAL);
        relayButton.setBackground(background(selectedProfile == 2 ? TEAL : SOFT_TEAL, 11, 0));
        relayButton.setTextColor(selectedProfile == 2 ? Color.WHITE : TEAL);
        officialButton.setEnabled(!testRunning);
        bochaButton.setEnabled(!testRunning);
        relayButton.setEnabled(!testRunning);
        presetField.setEnabled(!testRunning);
        saveButton.setEnabled(!testRunning);
        testButton.setEnabled(!testRunning && !storageUnavailable && !teamKeyNeedsReview);
        labelField.setEnabled(!testRunning);
        boolean teamPreset = drafts[selectedProfile].preset == PRESET_TEAM;
        urlField.setEnabled(!testRunning && !teamPreset);
        modelField.setEnabled(!testRunning);
        keyField.setEnabled(!testRunning);
        keyField.setHint(teamPreset ? "填写 1052 平台 key，四个模型共用" : "填写对应服务 key；无需鉴权的端点可留空");
        teamModelField.setEnabled(!testRunning);
        teamModelPanel.setVisibility(teamPreset ? View.VISIBLE : View.GONE);
        modelPanel.setVisibility(teamPreset ? View.GONE : View.VISIBLE);
        String selectedModel = drafts[selectedProfile].model;
        externalWarning.setVisibility(teamPreset && ("typesafe-jev".equals(selectedModel)
                || "bocha-jev".equals(selectedModel)) ? View.VISIBLE : View.GONE);
        keyDescription.setText(drafts[selectedProfile].originKeyCleared && keyField.length() == 0
                ? "服务地址的主机或端口已更换，原 key 已清空。请重新填写这个服务的 key；无需鉴权的端点可留空。"
                : teamPreset
                ? "1052 平台 key 只需填写一次；保存时同步到所有使用 1052 的路线和模型。"
                : "请使用该服务的 key；切换到其他服务预设时不会携带这份 key。");
        migrationNotice.setVisibility(saved != null && saved.needsReview ? View.VISIBLE : View.GONE);
        if (saved != null && saved.needsReview) {
            migrationNotice.setText(saved.migrationNotice);
        }
        thresholdField.setEnabled(!testRunning);
        compareOfficial.setEnabled(!testRunning);
        compareBocha.setEnabled(!testRunning);
        compareRelay.setEnabled(!testRunning);
        comparePanel.setVisibility(selectedMode == ModelConfig.Mode.COMPARE ? View.VISIBLE : View.GONE);
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
        teamKey = "";
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

    private static int modeIndex(ModelConfig.Mode mode) {
        for (int i = 0; i < MODE_CHOICES.length; i++) {
            if (MODE_CHOICES[i] == mode) return i;
        }
        return 0;
    }

    private static String routeName(int profile) {
        return "路线 " + (profile + 1);
    }

    private static String httpStatus(int status) {
        return status > 0 ? "HTTP " + status : "未收到 HTTP 响应";
    }

    private void readSharedTeamKey() {
        teamKey = "";
        teamKeyNeedsReview = false;
        for (Draft draft : drafts) {
            if (ModelConfig.isTeam1052(draft.profile()) && !draft.key.isEmpty()) {
                if (!teamKey.isEmpty() && !teamKey.equals(draft.key)) {
                    teamKeyNeedsReview = true;
                    break;
                }
                teamKey = draft.key;
            }
        }
        if (teamKeyNeedsReview) {
            teamKey = "";
        }
        syncSharedTeamKey();
    }

    private void syncSharedTeamKey() {
        for (Draft draft : drafts) {
            if (ModelConfig.isTeam1052(draft.profile())) {
                draft.key = teamKey;
            }
        }
    }

    private static int presetIndex(ModelConfig.Profile profile) {
        if (ModelConfig.isTeam1052(profile)) {
            for (String model : ModelConfig.team1052Models()) {
                if (model.equals(profile.model)
                        && sameEndpoint(profile.baseUrl, ModelConfig.TEAM_1052_BASE_URL)) {
                    return PRESET_TEAM;
                }
            }
        }
        if (sameEndpoint(profile.baseUrl, ModelConfig.presetTypeSafe().baseUrl)) {
            return PRESET_TYPESAFE;
        }
        if (sameEndpoint(profile.baseUrl, ModelConfig.presetBocha().baseUrl)) {
            return PRESET_BOCHA;
        }
        return PRESET_CUSTOM;
    }

    private static boolean sameEndpoint(String first, String second) {
        try {
            return SystemOneProtocol.endpoint(first).equals(SystemOneProtocol.endpoint(second));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private Spinner spinner(String[] choices) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSaveEnabled(false);
        spinner.setMinimumHeight(dp(48));
        return spinner;
    }

    private CheckBox routeCheck(LinearLayout parent, String title) {
        CheckBox box = new CheckBox(this);
        box.setText(title);
        box.setTextSize(14);
        box.setTextColor(INK);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));
        box.setMinHeight(dp(44));
        box.setSaveEnabled(false);
        box.setOnCheckedChangeListener((button, checked) -> markDirty());
        parent.addView(box, fullWidth());
        return box;
    }

    private EditText input(LinearLayout parent, String label, String hint, boolean password, boolean uri) {
        space(parent, 15);
        parent.addView(text(label, 13, INK, true));
        space(parent, 7);
        EditText field = new EditText(this);
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setTextSize(14);
        field.setHint(hint);
        field.setContentDescription(label);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : uri ? InputType.TYPE_TEXT_VARIATION_URI : InputType.TYPE_TEXT_VARIATION_NORMAL));
        ui_theme.input(field);
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
        int index = sectionIndex++;
        ui_theme.section(view, index == 1 ? ui_theme.SHEET
                : index == 2 ? ui_theme.SOFT_YELLOW : Color.TRANSPARENT);
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(ui_theme.SECTION_GAP);
        parent.addView(view, params);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        ui_theme.button(view, primary);
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return ui_theme.text(this, value, size, color, bold);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private GradientDrawable background(int fill, int radius, int stroke) {
        return ui_theme.shape(this, fill, radius, stroke);
    }

    private void space(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
