package com.personal.inote;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local document rendering; does not claim to reproduce the iOS image renderer. */
public final class NoteExporter {
    private static final int WIDTH=1080, PAD=64, CONTENT=WIDTH-PAD*2;
    private static final class Block {
        StaticLayout text; Bitmap image; int height;
        Block(StaticLayout t){text=t;height=t.getHeight();}
        Block(Bitmap b,int h){image=b;height=h;}
        void draw(Canvas c,int y){if(text!=null){c.save();c.translate(PAD,y);text.draw(c);c.restore();}else if(image!=null){float w=(float)image.getWidth()*height/image.getHeight();c.drawBitmap(image,null,new RectF((WIDTH-w)/2,y,(WIDTH+w)/2,y+height),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));}}
        void release(){if(image!=null)image.recycle();}
    }
    private static Block text(CharSequence value,float size,int color){TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(size);p.setColor(color);return new Block(StaticLayout.Builder.obtain(value,0,value.length(),p,CONTENT).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(8,1.1f).build());}
    private static List<Block> blocks(NoteStore.Note note)throws IOException{
        List<Block> out=new ArrayList<>();out.add(text(note.title,48,0xff252525));
        out.add(text(new SimpleDateFormat("yyyy.MM.dd",Locale.CHINA).format(new Date(note.createdAt)),24,0xff929292));
        for(NoteStore.Record record:note.records){
            out.add(text((record.todo?(record.done?"☑ ":"☐ "):"")+new SimpleDateFormat("MM.dd HH:mm",Locale.CHINA).format(new Date(record.createdAt)),22,0xff929292));
            CharSequence content=RichText.decode(record.html,record.text);if(content.length()>0)out.add(text(content,32,0xff333333));
            for(String path:record.images){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);if(o.outWidth<1||o.outHeight<1)continue;int sample=1;while(o.outWidth/sample>CONTENT*2||o.outHeight/sample>2400)sample*=2;o.inJustDecodeBounds=false;o.inSampleSize=sample;Bitmap b=BitmapFactory.decodeFile(path,o);if(b!=null){int h=Math.min(1100,(int)((long)b.getHeight()*CONTENT/b.getWidth()));out.add(new Block(b,h));}}
        }
        return out;
    }
    public static void png(NoteStore.Note note,OutputStream output)throws IOException{
        List<Block> blocks=blocks(note);Bitmap page=null;
        try{long total=PAD*2;for(Block b:blocks)total+=b.height+30;if(total>16000)throw new IOException("笔记过长，请导出 PDF");page=Bitmap.createBitmap(WIDTH,(int)total,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(page);c.drawColor(Color.WHITE);int y=PAD;for(Block b:blocks){b.draw(c,y);y+=b.height+30;}if(!page.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("图片写入失败");}finally{if(page!=null)page.recycle();for(Block b:blocks)b.release();}
    }
    public static void pdf(NoteStore.Note note,OutputStream output)throws IOException{
        List<Block> blocks=blocks(note);PdfDocument doc=new PdfDocument();PdfDocument.Page page=null;int y=PAD,count=0;
        try{for(Block b:blocks){int consumed=0;while(consumed<b.height){if(page==null){page=doc.startPage(new PdfDocument.PageInfo.Builder(WIDTH,1528,++count).create());page.getCanvas().drawColor(Color.WHITE);y=PAD;}int available=1528-PAD-y;if(available<48){doc.finishPage(page);page=null;continue;}int take=Math.min(available,b.height-consumed);
                    if(b.text!=null&&take<b.height-consumed){int desired=consumed+take;int line=b.text.getLineForVertical(desired);int boundary=b.text.getLineTop(line);if(boundary>consumed)take=boundary-consumed;}
                    if(b.image!=null&&consumed==0&&b.height>available&&y>PAD){doc.finishPage(page);page=null;continue;}
                    Canvas c=page.getCanvas();c.save();c.clipRect(0,y,WIDTH,y+take);b.draw(c,y-consumed);c.restore();consumed+=take;y+=take;if(consumed<b.height){doc.finishPage(page);page=null;}}
                y+=30;}
            if(page!=null){doc.finishPage(page);page=null;}doc.writeTo(output);
        }finally{if(page!=null)doc.finishPage(page);doc.close();for(Block b:blocks)b.release();}
    }
    private NoteExporter(){}
}
