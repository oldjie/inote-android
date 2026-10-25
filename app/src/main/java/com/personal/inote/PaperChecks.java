package com.personal.inote;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;

/** Display-only checklist marks: underlying Unicode and stored rich text stay intact. */
public final class PaperChecks {
    public static void apply(Spannable text){
        for(Box s:text.getSpans(0,text.length(),Box.class))text.removeSpan(s);
        for(Done s:text.getSpans(0,text.length(),Done.class))text.removeSpan(s);
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);if(c!='☐'&&c!='☑')continue;
            text.setSpan(new Box(c=='☑'),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(c=='☑'){int end=i+1;while(end<text.length()&&text.charAt(end)!='\n')end++;if(end>i+1)text.setSpan(new Done(),i+1,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        }
    }
    private static final class Done extends CharacterStyle {
        @Override public void updateDrawState(TextPaint paint){paint.setColor(0xff999999);paint.setStrikeThruText(true);}
    }
    private static final class Box extends ReplacementSpan {
        private final boolean checked;
        Box(boolean checked){this.checked=checked;}
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt fm){return (int)Math.ceil(paint.getTextSize()*1.08f);}
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint){
            Paint p=new Paint(paint);float size=p.getTextSize()*.78f;float left=x+p.getTextSize()*.03f;float upper=y-size*.92f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,p.getTextSize()*.045f));p.setColor(0xffb3b3b3);p.setAntiAlias(true);
            canvas.drawRoundRect(new RectF(left,upper,left+size,upper+size),size*.17f,size*.17f,p);
            if(checked){p.setColor(0xff888888);p.setStrokeWidth(Math.max(1,p.getTextSize()*.075f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path tick=new Path();tick.moveTo(left+size*.2f,upper+size*.51f);tick.lineTo(left+size*.43f,upper+size*.73f);tick.lineTo(left+size*.8f,upper+size*.25f);canvas.drawPath(tick,p);}
        }
    }
    private PaperChecks(){}
}
