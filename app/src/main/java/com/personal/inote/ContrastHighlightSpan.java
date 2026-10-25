package com.personal.inote;

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;

/** Keeps highlighted text legible in both themes without adding a second stored span. */
public final class ContrastHighlightSpan extends BackgroundColorSpan {
    public ContrastHighlightSpan(int color) { super(color); }

    @Override public void updateDrawState(TextPaint paint) {
        super.updateDrawState(paint);
        int color = getBackgroundColor();
        double luminance = 0.2126 * linear(Color.red(color))
                + 0.7152 * linear(Color.green(color))
                + 0.0722 * linear(Color.blue(color));
        // Compare WCAG contrast ratios against black and white.
        paint.setColor((luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)
                ? Color.BLACK : Color.WHITE);
    }

    private static double linear(int component) {
        double channel = component / 255.0;
        return channel <= 0.04045 ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
