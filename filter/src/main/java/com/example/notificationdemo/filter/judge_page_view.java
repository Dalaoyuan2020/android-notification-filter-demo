package com.example.notificationdemo.filter;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** The judgment desk displays saved state; its host remains responsible for all mutations. */
public final class judge_page_view extends LinearLayout {
    private final pinned_note_view currentNote;
    private final pinned_note_view modelNote;
    private final pinned_note_view attentionNote;
    private final LinearLayout destinations;
    private final TextView modeValue;
    private final TextView modeDescription;
    private final TextView sharingSummary;
    private final Switch automatic;
    private Boolean wideLayout;

    public judge_page_view(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        TextView heading = text("智能判断", ui_theme.TITLE_SP, ui_theme.INK, true);
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        addView(heading, fullWidth());
        TextView introduction = text("模型判断与本机短时注意力", 13, ui_theme.MUTED, false);
        LayoutParams introductionPosition = fullWidth();
        introductionPosition.topMargin = dp(8);
        introductionPosition.bottomMargin = dp(12);
        addView(introduction, introductionPosition);

        currentNote = new pinned_note_view(context);
        currentNote.setTilt(-0.7f);
        addView(currentNote, fullWidth());

        destinations = new LinearLayout(context);
        destinations.setClipChildren(false);
        destinations.setClipToPadding(false);
        destinations.setGravity(Gravity.TOP);
        LayoutParams destinationPosition = fullWidth();
        destinationPosition.topMargin = dp(8);
        addView(destinations, destinationPosition);
        modelNote = new pinned_note_view(context);
        modelNote.setTilt(1.0f);
        destinations.addView(modelNote);
        attentionNote = new pinned_note_view(context);
        attentionNote.setTilt(-1.2f);
        destinations.addView(attentionNote);

        sharingSummary = text("", 12, ui_theme.MUTED, false);
        LayoutParams sharingPosition = fullWidth();
        sharingPosition.setMargins(dp(10), dp(10), dp(10), dp(22));
        addView(sharingSummary, sharingPosition);

        LinearLayout handling = new LinearLayout(context);
        handling.setOrientation(VERTICAL);
        handling.setPadding(dp(16), dp(14), dp(16), dp(16));
        handling.setBackground(ui_theme.shape(context, ui_theme.SHEET, 12, ui_theme.BORDER));
        addView(handling, fullWidth());
        LinearLayout handlingHeader = new LinearLayout(context);
        handlingHeader.setGravity(Gravity.CENTER_VERTICAL);
        handlingHeader.addView(text("通知处理", 17, ui_theme.INK, true), new LayoutParams(0, -2, 1));
        modeValue = text("", 11, ui_theme.ACCENT, true);
        modeValue.setPadding(dp(9), dp(5), dp(9), dp(5));
        handlingHeader.addView(modeValue);
        handling.addView(handlingHeader, fullWidth());

        automatic = new Switch(context);
        automatic.setText("自动清除");
        automatic.setTextSize(16);
        automatic.setTextColor(ui_theme.INK);
        automatic.setMinHeight(dp(52));
        automatic.setPadding(0, dp(8), 0, dp(8));
        automatic.setShowText(false);
        ui_theme.toggle(automatic);
        handling.addView(automatic, fullWidth());
        modeDescription = text("", 13, ui_theme.MUTED, false);
        handling.addView(modeDescription, fullWidth());
        TextView caution = text("开启即按已保存的规则清除目标 App 的通知。清除的是通知卡片，不能保证恢复；也不能保证拦住声音、振动或横幅。",
                12, ui_theme.WARNING, false);
        LayoutParams cautionPosition = fullWidth();
        cautionPosition.topMargin = dp(12);
        handling.addView(caution, cautionPosition);
    }

    public void setModelAction(pinned_note_view.OnNoteClickListener listener) {
        modelNote.setAction("model_settings", listener);
    }

    public void setAttentionAction(pinned_note_view.OnNoteClickListener listener) {
        attentionNote.setAction("attention", listener);
    }

    public Switch getAutoSwitch() { return automatic; }

    /** Bind existing preferences only; this method never starts a request or changes a setting. */
    public void update(ModelConfig config, AttentionStore.Config attention, boolean auto, boolean allowsAutomatic) {
        boolean keywords = config.mode == ModelConfig.Mode.KEYWORDS;
        boolean comparison = config.mode == ModelConfig.Mode.COMPARE;
        String handling = comparison ? "对照观察" : auto ? "自动模式" : "观察模式";
        String title;
        String detail;
        if (keywords) {
            title = "关键词 · 本机规则";
            detail = handling + "\n通知在本机处理，不调用远程模型。";
        } else if (comparison) {
            title = "多路对照 · 只观察";
            int routes = (config.compareOfficial ? 1 : 0) + (config.compareBocha ? 1 : 0)
                    + (config.compareRelay ? 1 : 0);
            detail = "已选 " + routes + " 条路线 · " + (config.remoteEnabled ? "已允许模型请求" : "远程判断未开启")
                    + "\n只记录判断结果，绝不发起清除。";
        } else {
            int route = config.mode == ModelConfig.Mode.OFFICIAL ? 1 : config.mode == ModelConfig.Mode.BOCHA ? 2 : 3;
            ModelConfig.Profile profile = route == 1 ? config.official : route == 2 ? config.bocha : config.relay;
            title = "路线 " + route + " · 模型判断";
            detail = (profile.label.isEmpty() ? "未命名配置" : profile.label)
                    + "\n" + (profile.model.isEmpty() ? "模型尚未填写" : profile.model)
                    + "\n" + handling + " · " + (config.remoteEnabled ? "已允许模型请求" : "远程判断未开启");
        }
        if (!keywords && !config.remoteEnabled) {
            detail += "\n保留通知，不发送给模型，也不回退关键词清除。";
        }
        if (!config.storageError.isEmpty()) {
            detail += "\n模型配置或凭据不可用，请进入模型配置检查。";
        } else if (config.needsReview) {
            detail += "\n旧配置待复核，请进入模型配置重新保存。";
        }
        currentNote.bind("当前判断模式", title, detail, ui_theme.SOFT_YELLOW, ui_theme.CORAL);
        modelNote.bind(String.format(Locale.CHINA, "阈值 %.2f", config.threshold), "模型配置",
                "模式与三路服务\n合成连接测试\n点按配置 →", ui_theme.SOFT_BLUE, ui_theme.BLUE);
        attentionNote.bind("本机短时记忆", "Attention",
                "半衰期 " + number(attention.halfLifeMinutes) + " 分钟\n融合权重 " + number(attention.weight)
                        + "\n查看行为与设置 →", ui_theme.SOFT_PURPLE, ui_theme.PURPLE);

        String sharing = keywords ? "当前关键词模式不发送模型请求。"
                : config.remoteEnabled ? "远程处理可发送目标通知的来源、标题与正文到已配置的服务。"
                : "远程处理关闭，当前不会发送通知到模型服务。";
        sharing += "\n近期行为摘要：" + (attention.recentBehaviorEnabled ? "开启，仅随已开启的 Jev 请求发送" : "关闭")
                + "\n独立事件上传：" + (attention.uploadEnabled ? "开启" : "关闭")
                + " · 正文上传：" + (attention.uploadBody ? "已允许" : "关闭");
        if (!attention.storageError.isEmpty()) {
            sharing += "\n注意力设置或凭据不可用，请进入 Attention 检查。";
        }
        sharingSummary.setText(sharing);
        modeValue.setText(handling);
        modeValue.setTextColor(auto ? ui_theme.WARNING : ui_theme.ACCENT);
        modeValue.setBackground(ui_theme.shape(getContext(), auto ? ui_theme.SOFT_YELLOW : ui_theme.SOFT_GREEN, 30, 0));
        modeDescription.setText(comparison
                ? "多路对照只记录选定路线的判断，绝不发起清除。切换策略并保存后，需重新手动开启自动清除。"
                : !allowsAutomatic
                ? "当前模型策略尚未开启远程处理，或配置存储不可用；自动清除暂不可用。"
                : auto ? "已开启：命中清除规则后请求系统移除通知，并记录结果。"
                : "默认仅观察：展示保留或清除建议，不移除通知。先确认规则，再开启自动清除。");
        automatic.setChecked(auto);
        automatic.setEnabled(allowsAutomatic);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean wide = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
                && MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight() >= dp(340);
        if (wideLayout == null || wideLayout != wide) {
            wideLayout = wide;
            destinations.setOrientation(wide ? HORIZONTAL : VERTICAL);
            LayoutParams modelPosition = new LayoutParams(wide ? 0 : -1, -2, wide ? 1 : 0);
            LayoutParams attentionPosition = new LayoutParams(wide ? 0 : -1, -2, wide ? 1 : 0);
            if (wide) {
                modelPosition.rightMargin = dp(4);
                attentionPosition.leftMargin = dp(4);
                attentionPosition.topMargin = dp(20);
            } else {
                modelPosition.rightMargin = dp(20);
                attentionPosition.leftMargin = dp(20);
                attentionPosition.topMargin = dp(8);
            }
            modelNote.setLayoutParams(modelPosition);
            attentionNote.setLayoutParams(attentionPosition);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.format(Locale.CHINA, "%.0f", value)
                : String.format(Locale.CHINA, "%.2f", value);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        return ui_theme.text(getContext(), value, size, color, bold);
    }

    private LayoutParams fullWidth() { return new LayoutParams(-1, -2); }
    private int dp(float value) { return ui_theme.dp(getContext(), value); }
}
