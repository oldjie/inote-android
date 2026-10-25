package com.personal.inote;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Independently drawn fallback artwork for the public distribution. */
final class CommunityIcon extends Drawable {
    private final String name;
    private final int size;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    CommunityIcon(String name,int size){this.name=name;this.size=size;setBounds(0,0,size,size);}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas){
        int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/32f,b.height()/32f);
        paint.setColor(0xff777777);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);
        if(name.startsWith("bg_book_cover")){
            int[] colors={0xff718578,0xff9a7971,0xff748597,0xffa28e6e,0xff8e8198,0xff648c8c};
            paint.setStyle(Paint.Style.FILL);paint.setColor(colors[Math.floorMod(name.hashCode(),colors.length)]);canvas.drawRect(0,0,32,32,paint);
            paint.setColor(0x30ffffff);canvas.drawRect(3,0,4,32,paint);
        }else if(name.contains("search")){canvas.drawCircle(13,13,8,paint);canvas.drawLine(19,19,27,27,paint);}
        else if(name.contains("add")){canvas.drawLine(6,16,26,16,paint);canvas.drawLine(16,6,16,26,paint);}
        else if(name.contains("back")){canvas.drawLine(21,5,10,16,paint);canvas.drawLine(10,16,21,27,paint);}
        else if(name.contains("star")){Path p=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?13:6;float x=16+(float)Math.cos(a)*r,y=16+(float)Math.sin(a)*r;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();canvas.drawPath(p,paint);}
        else if(name.contains("more")){paint.setStyle(Paint.Style.FILL);for(int x=7;x<=25;x+=9)canvas.drawCircle(x,16,2,paint);}
        else if(name.contains("user")){canvas.drawCircle(16,10,5,paint);canvas.drawArc(6,18,26,34,180,180,false,paint);}
        else if(name.contains("book")||name.contains("tab_note")||name.equals("app_icon.png")){canvas.drawRoundRect(6,3,26,29,2,2,paint);canvas.drawLine(10,3,10,29,paint);canvas.drawLine(14,11,22,11,paint);canvas.drawLine(14,17,22,17,paint);}
        else if(name.contains("filter")||name.contains("order")||name.contains("settings")){for(int i=0;i<3;i++){float y=8+i*8;canvas.drawLine(5,y,27,y,paint);canvas.drawCircle(i==1?21:11,y,2.5f,paint);}}
        else {String glyph="≡";if(name.contains("bold"))glyph="B";else if(name.contains("italic"))glyph="I";else if(name.contains("underline"))glyph="U";else if(name.contains("strikethrough"))glyph="S";else if(name.contains("highlight"))glyph="H";else if(name.contains("code"))glyph="<>";else if(name.contains("clear"))glyph="×";else if(name.contains("quote"))glyph="❞";else if(name.contains("toolbar_4"))glyph="☑";else if(name.contains("toolbar_2"))glyph="▧";else if(name.contains("toolbar_1"))glyph="T";paint.setStyle(Paint.Style.FILL);paint.setTextSize(22);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(glyph,16,24,paint);}
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
