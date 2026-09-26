package com.example.notificationdemo.filter;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** A lightweight, resolution-independent warm sheet with a subtle 24dp square grid. */
public final class grid_paper_drawable extends Drawable {
    private final Paint paper = new Paint();
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float step;
    private int alpha = 255;

    public grid_paper_drawable(float density) {
        float safeDensity = Float.isFinite(density) && density > 0 ? density : 1f;
        step = ui_theme.GRID_STEP * safeDensity;
        paper.setColor(ui_theme.PAPER);
        grid.setColor(ui_theme.GRID);
        grid.setStrokeWidth(Math.max(1f, safeDensity * 0.5f));
        grid.setAlpha(150);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        int checkpoint = canvas.save();
        canvas.clipRect(bounds);
        canvas.drawRect(bounds, paper);
        for (float x = bounds.left + step; x < bounds.right; x += step) {
            canvas.drawLine(x, bounds.top, x, bounds.bottom, grid);
        }
        for (float y = bounds.top + step; y < bounds.bottom; y += step) {
            canvas.drawLine(bounds.left, y, bounds.right, y, grid);
        }
        canvas.restoreToCount(checkpoint);
    }

    @Override public void setAlpha(int value) {
        alpha = Math.max(0, Math.min(255, value));
        paper.setAlpha(alpha);
        grid.setAlpha(Math.round(alpha * (150f / 255f)));
        invalidateSelf();
    }

    @Override public int getAlpha() { return alpha; }

    @Override public void setColorFilter(ColorFilter filter) {
        paper.setColorFilter(filter);
        grid.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
