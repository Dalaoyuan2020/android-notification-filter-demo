package com.example.notificationdemo.filter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Inspect real short-term observations and explicitly opt into optional transports. */
public final class AttentionActivity extends Activity {
    private static final int INK = ui_theme.INK;
    private static final int MUTED = ui_theme.MUTED;
    private static final int TEAL = ui_theme.ACCENT;
    private static final int BG = ui_theme.PAPER;
    private static final int BORDER = ui_theme.BORDER;
    private int sectionIndex;
    private EditText halfLife;
    private EditText weight;
    private EditText serviceUrl;
    private EditText serviceToken;
    private Switch recentBehavior;
    private Switch upload;
    private Switch uploadBody;
    private TextView status;
    private TextView rankingStatus;
    private LinearLayout highList;
    private LinearLayout lowList;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(ui_theme.paper(this));
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
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
        int available = getResources().getDisplayMetrics().widthPixels;
        frame.addView(page, new FrameLayout.LayoutParams(available > dp(720) ? dp(720) : -1,
                -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        Button back = button("返回通知筛选");
        back.setOnClickListener(view -> finish());
        page.addView(back, fullWidth());
        space(page, 22);
        page.addView(text("短时注意力", ui_theme.TITLE_SP, INK, true));
        space(page, 8);
        page.addView(text("从真实点击、手动划除和持续未处理的通知学习，随时间衰减。不会把 App 自动清除当作您的偏好。", 14, MUTED, false));
        space(page, 20);

        LinearLayout settings = card(page, "01  近期行为与概率融合");
        halfLife = input(settings, "半衰期 · 分钟（1–525600）", false, true);
        weight = input(settings, "融合权重 w · 0–10", false, true);
        settings.addView(text("默认半衰期 30 分钟、w = 1。w = 0 时保留模型原始概率；有效证据数 n 会随时间衰减。", 12, MUTED, false));
        recentBehavior = toggle(settings, "JEV 请求附带近期行为摘要");
        settings.addView(text("默认开启。在启用远程模型后，JEV 请求会附带目标 App 中变化最大的至多 2 条真实行为摘要，包含来源／标题前缀；此开关不会自行开启远程模型。", 12, MUTED, false));

        LinearLayout transport = card(page, "02  可选事件上传");
        upload = toggle(transport, "上传事件到独立注意力服务");
        transport.addView(text("默认关闭。这是独立于模型请求的上传开关；开启并保存后，将发送来源 App、出现／消失时间、处理原因、停留时间及概率等事件字段。已发出的数据无法收回。", 12, MUTED, false));
        serviceUrl = input(transport, "服务 HTTPS 地址", false, false);
        serviceUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serviceUrl.setHint("基础地址或以 /events 结尾的端点");
        serviceToken = input(transport, "服务 Token · 本机加密保存", true, false);
        serviceToken.setHint("需要鉴权时填写");
        uploadBody = toggle(transport, "事件上传包含通知正文");
        transport.addView(text("默认不上传正文。勾选后可能发送真实通知内容；请先用合成样本。地址与 Token 不会替代模型配置，手机的 localhost 也不是电脑地址。", 12, MUTED, false));
        space(transport, 14);
        Button save = button("保存注意力设置并关闭自动清除");
        ui_theme.button(save, true);
        save.setOnClickListener(view -> saveSettings(save));
        transport.addView(save, fullWidth());
        space(transport, 10);
        status = text("", 12, TEAL, false);
        transport.addView(status);

        LinearLayout ranking = card(page, "03  当前偏好快照");
        rankingStatus = text("", 12, MUTED, false);
        ranking.addView(rankingStatus);
        space(ranking, 10);
        Button refresh = button("刷新注意力快照");
        refresh.setOnClickListener(view -> refreshRankings());
        ranking.addView(refresh, fullWidth());
        space(ranking, 16);
        ranking.addView(text("p_short 最高的 5 项", 16, TEAL, true));
        highList = column();
        ranking.addView(highList, fullWidth());
        space(ranking, 20);
        ranking.addView(text("p_short 最低的 5 项", 16, INK, true));
        lowList = column();
        ranking.addView(lowList, fullWidth());
        space(page, 2);
        page.addView(text("同一个来源下的【淘宝】、【银行】等标题前缀分别统计。新前缀从 0.5 开始，不继承整个发送器的行为。条目不足 10 项时，高低榜可能重复。", 12, MUTED, false));
        setContentView(scroll);
        scroll.requestApplyInsets();
        populate(AttentionStore.loadConfig(this));
        refreshRankings();
    }

    private void populate(AttentionStore.Config config) {
        halfLife.setText(Double.toString(config.halfLifeMinutes));
        weight.setText(Double.toString(config.weight));
        recentBehavior.setChecked(config.recentBehaviorEnabled);
        upload.setChecked(config.uploadEnabled);
        uploadBody.setChecked(config.uploadBody);
        serviceUrl.setText(config.serviceUrl);
        serviceToken.setText(config.serviceToken);
        status.setText(config.storageError.isEmpty()
                ? "修改后点击保存才生效。Token 不写入页面恢复状态；未保存编辑在离开后丢弃。"
                : "上传凭据不可用，上报已停用。请重新填写 Token 并保存；本地快照仍可查看。");
    }

    private void saveSettings(Button save) {
        double half;
        double w;
        try {
            half = Double.parseDouble(halfLife.getText().toString().trim());
            w = Double.parseDouble(weight.getText().toString().trim());
            if (!Double.isFinite(half) || half < 1 || half > 525600
                    || !Double.isFinite(w) || w < 0 || w > 10) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException invalid) {
            status.setText("半衰期需为 1–525600 分钟，融合权重需为 0–10。");
            return;
        }
        try {
            AttentionStore.Config config = AttentionStore.saveConfig(this, half, w,
                    recentBehavior.isChecked(), upload.isChecked(), serviceUrl.getText().toString(),
                    serviceToken.getText().toString(), uploadBody.isChecked());
            populate(config);
            status.setText(config.storageError.isEmpty()
                    ? "已保存 · 自动清除已关闭，请先观察新参数下的结果。"
                    : "配置已写入，但上传凭据仍不可用，请检查设备密钥存储。");
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(save.getWindowToken(), 0);
            }
            refreshRankings();
        } catch (IOException | IllegalArgumentException failure) {
            status.setText("保存未完成。请检查数值范围、有效 HTTPS 地址和 Token；不要在地址中放凭据。返回主界面核对自动开关后重试。");
        }
    }

    private void refreshRankings() {
        if (highList == null) return;
        List<ShortTermMemory.Entry> entries = new ArrayList<>(AttentionStore.snapshots(this));
        entries.sort(Comparator.comparingDouble(entry -> entry.pShort));
        rankingStatus.setText(String.format(Locale.CHINA,
                "共 %d 个来源／前缀条目 · 概率按当前半衰期计算\n有效证据 n 为非负衰减值，不是通知总条数。", entries.size()));
        renderRank(lowList, entries, false);
        renderRank(highList, entries, true);
    }

    private void renderRank(LinearLayout parent, List<ShortTermMemory.Entry> entries, boolean descending) {
        parent.removeAllViews();
        if (entries.isEmpty()) {
            space(parent, 10);
            parent.addView(text("暂无行为证据。先在发送器发送五条【淘宝】样本，并逐条手动划掉，再回来刷新。", 13, MUTED, false));
            return;
        }
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            ShortTermMemory.Entry entry = entries.get(descending ? entries.size() - 1 - i : i);
            space(parent, 10);
            LinearLayout row = column();
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(background(BG, 10, 0));
            row.addView(text(entry.label.isEmpty() ? entry.pkg : entry.label, 14, INK, true));
            space(row, 5);
            row.addView(text(entry.pkg + (entry.prefix.isEmpty() ? "" : "\n前缀：" + entry.prefix), 11, MUTED, false));
            space(row, 8);
            row.addView(text(String.format(Locale.CHINA, "p_short = %.3f    有效证据 n = %.2f",
                    entry.pShort, entry.effectiveCount), 14, TEAL, true));
            if (!entry.behaviorText.isEmpty()) {
                space(row, 7);
                row.addView(text(entry.behaviorText, 12, MUTED, false));
            }
            parent.addView(row, fullWidth());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshRankings();
    }

    @Override protected void onDestroy() {
        if (serviceToken != null) {
            serviceToken.getText().clear();
        }
        super.onDestroy();
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout card = column();
        int index = sectionIndex++;
        ui_theme.section(card, index == 0 ? ui_theme.SOFT_GREEN
                : index == 2 ? ui_theme.SHEET : Color.TRANSPARENT);
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(ui_theme.SECTION_GAP);
        parent.addView(card, params);
        card.addView(text(title, ui_theme.SECTION_SP, INK, true));
        space(card, 10);
        return card;
    }

    private EditText input(LinearLayout parent, String label, boolean secret, boolean decimal) {
        space(parent, 10);
        parent.addView(text(label, 13, INK, true));
        space(parent, 7);
        EditText field = new EditText(this);
        field.setTextSize(14);
        field.setTextColor(INK);
        field.setContentDescription(label);
        field.setSingleLine(true);
        field.setInputType(decimal ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_NORMAL));
        field.setSaveEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        ui_theme.input(field);
        parent.addView(field, fullWidth());
        space(parent, 8);
        return field;
    }

    private Switch toggle(LinearLayout parent, String label) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(14);
        toggle.setTextColor(INK);
        toggle.setMinHeight(dp(54));
        toggle.setShowText(false);
        toggle.setSaveEnabled(false);
        ui_theme.toggle(toggle);
        parent.addView(toggle, fullWidth());
        return toggle;
    }

    private Button button(String title) {
        Button button = new Button(this);
        button.setText(title);
        ui_theme.button(button, false);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return ui_theme.text(this, value, size, color, bold);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private GradientDrawable background(int color, int radius, int stroke) {
        return ui_theme.shape(this, color, radius, stroke);
    }

    private void space(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private LinearLayout.LayoutParams fullWidth() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
