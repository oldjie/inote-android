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
        }else if(name.contains("tab_note")){
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xffbfd9ff);canvas.drawOval(10,23,23,31,paint);
            paint.setColor(0xff88d8ee);canvas.drawOval(2,11,30,23,paint);paint.setColor(0xff7199ef);canvas.drawOval(3,14,29,22,paint);
            paint.setColor(0xffa3e6f3);canvas.drawOval(8,5,24,19,paint);paint.setColor(0xffe9fcff);canvas.drawOval(10,7,21,15,paint);
            paint.setColor(0xffffed69);canvas.drawCircle(7,17,1.5f,paint);canvas.drawCircle(25,17,1.5f,paint);paint.setColor(0xffa2e8ed);canvas.drawRect(27,3,29,9,paint);canvas.drawRect(25,5,31,7,paint);
        }else if(name.contains("tab_user")){
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xff739ae9);canvas.drawCircle(16,16,15,paint);paint.setColor(0xff527bc4);canvas.drawOval(10,7,23,25,paint);paint.setColor(Color.WHITE);canvas.drawCircle(16,13,5,paint);canvas.drawOval(7,21,25,34,paint);
        }else if(name.startsWith("btn_tab_add")){
            paint.setStyle(Paint.Style.FILL);paint.setColor(0x15555555);canvas.drawRoundRect(1,3,31,31,12,12,paint);paint.setColor(Color.WHITE);canvas.drawRoundRect(1,1,31,29,12,12,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3.1f);paint.setColor(0xff6797e5);canvas.drawLine(8,15,24,15,paint);canvas.drawLine(16,7,16,23,paint);
        }else if(name.contains("search")){canvas.drawCircle(13,13,8,paint);canvas.drawLine(19,19,27,27,paint);}
        else if(name.contains("add")){canvas.drawLine(6,16,26,16,paint);canvas.drawLine(16,6,16,26,paint);}
        else if(name.contains("back")){canvas.drawLine(21,5,10,16,paint);canvas.drawLine(10,16,21,27,paint);}
        else if(name.contains("star")){paint.setStyle(Paint.Style.FILL);paint.setColor(name.matches(".*star_[0-4]\\.png")?new int[]{0xffefca77,0xffe99ca9,0xff8da6e5,0xff84ccdb,0xff8dc99b}[name.charAt(name.length()-5)-'0']:0xffdddddd);Path p=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?13:6;float x=16+(float)Math.cos(a)*r,y=16+(float)Math.sin(a)*r;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();canvas.drawPath(p,paint);}
        else if(name.contains("more")){paint.setStyle(Paint.Style.FILL);for(int x=7;x<=25;x+=9)canvas.drawCircle(x,16,2,paint);}
        else if(name.contains("user")){canvas.drawCircle(16,10,5,paint);canvas.drawArc(6,18,26,34,180,180,false,paint);}
        else if(name.contains("book")||name.contains("tab_note")||name.equals("app_icon.png")){canvas.drawRoundRect(6,3,26,29,2,2,paint);canvas.drawLine(10,3,10,29,paint);canvas.drawLine(14,11,22,11,paint);canvas.drawLine(14,17,22,17,paint);}
        else if(name.contains("order")){for(int i=0;i<3;i++)canvas.drawLine(3,7+i*8,16-i*2,7+i*8,paint);canvas.drawLine(24,4,24,27,paint);canvas.drawLine(20,8,24,4,paint);canvas.drawLine(28,8,24,4,paint);canvas.drawLine(20,23,24,27,paint);canvas.drawLine(28,23,24,27,paint);}
        else if(name.equals("ico_note_toolbar_8.png")){Path pen=new Path();pen.moveTo(5,27);pen.lineTo(8,18);pen.lineTo(23,3);pen.lineTo(29,9);pen.lineTo(14,24);pen.close();canvas.drawPath(pen,paint);canvas.drawLine(8,18,14,24,paint);canvas.drawLine(18,26,27,26,paint);}
        else if(name.contains("filter")||name.contains("settings")){for(int i=0;i<3;i++){float y=8+i*8;canvas.drawLine(5,y,27,y,paint);canvas.drawCircle(i==1?21:11,y,2.5f,paint);}}
        else {String glyph="≡";if(name.contains("bold"))glyph="B";else if(name.contains("italic"))glyph="I";else if(name.contains("underline"))glyph="U";else if(name.contains("strikethrough"))glyph="S";else if(name.contains("highlight"))glyph="H";else if(name.contains("code"))glyph="<>";else if(name.contains("clear"))glyph="×";else if(name.contains("quote"))glyph="❞";else if(name.equals("ico_note_toolbar_202.png")){glyph="♙";paint.setColor(0xff6692d8);}else if(name.equals("ico_note_toolbar_200.png")){glyph="♒";paint.setColor(0xff6692d8);}else if(name.equals("ico_note_toolbar_4.png"))glyph="▧";else if(name.equals("ico_note_toolbar_2.png"))glyph="Aa";else if(name.equals("ico_note_toolbar_1.png"))glyph="☑";paint.setStyle(Paint.Style.FILL);paint.setTextSize(22);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(glyph,16,24,paint);}
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
