package com.example.notificationdemo.filter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

/** Shared paper palette and native-view primitives. Values are dp/sp unless named as colors. */
public final class ui_theme {
    public static final int PAPER = 0xFFF7F4EE;
    public static final int GRID = 0xFFE7E1D8;
    public static final int SHEET = 0xFFFFFCF7;
    public static final int INK = 0xFF171815;
    public static final int MUTED = 0xFF68685F;
    public static final int BORDER = 0xFFDDD7CB;
    public static final int ACCENT = 0xFF426956;
    public static final int WARNING = 0xFF825218;
    public static final int YELLOW = 0xFFE9C55B;
    public static final int BLUE = 0xFF8BB3DE;
    public static final int CORAL = 0xFFE89588;
    public static final int PURPLE = 0xFFBAA6D5;
    public static final int GREEN = 0xFF91C3A6;
    public static final int SOFT_YELLOW = 0xFFF4EBD0;
    public static final int SOFT_BLUE = 0xFFE8EEF5;
    public static final int SOFT_CORAL = 0xFFF5E5DF;
    public static final int SOFT_PURPLE = 0xFFEFE9F5;
    public static final int SOFT_GREEN = 0xFFE6EEE4;
    public static final int PAGE_MARGIN = 22;
    public static final int PAGE_TOP = 28;
    public static final int SECTION_GAP = 28;
    public static final int SHEET_PADDING = 18;
    public static final int TITLE_SP = 32;
    public static final int SECTION_SP = 18;
    public static final int GRID_STEP = 24;

    private ui_theme() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static grid_paper_drawable paper(Context context) {
        return new grid_paper_drawable(context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable shape(Context context, int fill, float radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, radius));
        if (stroke != 0) {
            shape.setStroke(dp(context, 1), stroke);
        }
        return shape;
    }

    /** Transparent sections sit directly on the paper; colored sections are quiet paper sheets. */
    public static void section(View view, int fill) {
        Context context = view.getContext();
        int inset = fill == Color.TRANSPARENT ? 0 : dp(context, SHEET_PADDING);
        view.setPadding(inset, dp(context, fill == Color.TRANSPARENT ? 6 : SHEET_PADDING), inset,
                dp(context, fill == Color.TRANSPARENT ? 6 : SHEET_PADDING));
        view.setBackground(fill == Color.TRANSPARENT ? null : shape(context, fill, 9, BORDER));
        view.setElevation(fill == Color.TRANSPARENT ? 0 : dp(context, 1));
    }

    public static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, size >= 13 ? 4 : 3), 1f);
        if (size >= 28) {
            view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            view.setLetterSpacing(-0.025f);
        } else if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    public static void button(Button view, boolean primary) {
        Context context = view.getContext();
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_enabled}, {}},
                new int[]{INK, MUTED}));
        view.setBackgroundTintList(null);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20426956),
                shape(context, primary ? YELLOW : SHEET, 10, primary ? 0 : BORDER), null));
        view.setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13));
        view.setMinHeight(dp(context, 50));
        view.setMinimumHeight(dp(context, 50));
        view.setStateListAnimator(null);
    }

    public static void input(EditText field) {
        Context context = field.getContext();
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setHighlightColor(SOFT_GREEN);
        field.setTextSize(14);
        field.setPadding(dp(context, 13), dp(context, 13), dp(context, 13), dp(context, 13));
        field.setMinimumHeight(dp(context, 50));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(context, SHEET, 8, ACCENT));
        states.addState(new int[]{}, shape(context, SHEET, 8, BORDER));
        field.setBackground(states);
    }

    public static void toggle(Switch view) {
        view.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ACCENT, SHEET}));
        view.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{GREEN, BORDER}));
    }
}
