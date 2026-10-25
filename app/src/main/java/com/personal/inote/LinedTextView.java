package com.personal.inote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * TextView with paper rules drawn behind each displayed line.
 *
 * Text, spans, selection and MovementMethod are still handled by TextView. No
 * text rewriting, click interception, forced padding or background is applied.
 * This also leaves an unruled view suitable for the original colored cards.
 */
public final class LinedTextView extends TextView {
    private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect clip = new Rect();
    private boolean ruled = true;
    private float extraInset;

    public LinedTextView(Context context) {
        this(context, null);
    }

    public LinedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public LinedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        rulePaint.setColor(0xffededed);
        rulePaint.setStyle(Paint.Style.STROKE);
        rulePaint.setStrokeWidth(dp(0.5f));
    }

    /** Enables or disables the rules, without changing text layout. */
    public void setRuled(boolean value) {
        if (ruled != value) {
            ruled = value;
            invalidate();
        }
    }

    public boolean isRuled() {
        return ruled;
    }

    /** Changes only rule contrast. Text and paper colors remain caller-owned. */
    public void setDarkMode(boolean dark) {
        setRuleColor(dark ? 0xff454545 : 0xffededed);
    }

    public void setRuleColor(int color) {
        rulePaint.setColor(color);
        invalidate();
    }

    /** Positive width in density-independent pixels; the default is 0.5 dp. */
    public void setRuleWidthDp(float width) {
        if (Float.isNaN(width) || Float.isInfinite(width) || width <= 0) {
            throw new IllegalArgumentException("Rule width must be positive and finite");
        }
        rulePaint.setStrokeWidth(dp(width));
        invalidate();
    }

    /** Extra inset inside the TextView's existing compound padding. */
    public void setRuleHorizontalInsetDp(float inset) {
        if (Float.isNaN(inset) || Float.isInfinite(inset) || inset < 0) {
            throw new IllegalArgumentException("Rule inset must be non-negative and finite");
        }
        extraInset = dp(inset);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (ruled && layout != null && layout.getLineCount() > 0) {
            // TextView's first baseline includes vertical gravity, font padding,
            // compound padding and its internal vertical offset. Use it instead
            // of assuming top gravity or a particular font's line height.
            float originY = getBaseline() - layout.getLineBaseline(0);
            float left = extraInset;
            float right = getWidth() - extraInset;
            if (right > left && canvas.getClipBounds(clip)) {
                int first = layout.getLineForVertical(
                        Math.max(0, (int) Math.floor(clip.top - originY)));
                int last = layout.getLineForVertical(
                        Math.max(0, (int) Math.ceil(clip.bottom - originY)));
                float halfStroke = rulePaint.getStrokeWidth() / 2f;
                for (int line = first; line <= last; line++) {
                    // Layout includes wrapped lines and explicit empty lines.
                    // Rules follow each actual line's measured bottom, rather
                    // than a fixed grid that would drift with spans or spacing.
                    float y = originY + layout.getLineBottom(line) - halfStroke;
                    canvas.drawLine(left, y, right, y, rulePaint);
                }
                float step=layout.getLineBottom(0)-layout.getLineTop(0);
                if(step>0)for(float y=originY+layout.getHeight()+step-halfStroke;y<getHeight();y+=step)canvas.drawLine(left,y,right,y,rulePaint);
            }
        }
        // Glyphs, highlighted spans, inline images and clickable checkbox spans
        // are rendered afterwards; rules never paint over their foreground.
        super.onDraw(canvas);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
