package com.personal.inote;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Offline rendering with independently drawn paper themes. */
public final class NoteExporter {
    public static final int WIDTH=1080;
    private static final int PAD=96, CONTENT=WIDTH-PAD*2, MAX_HEIGHT=16000;
    public static final String[] THEMES={"无印","天空","纸张","樱花","宝丽来"};
    public static final class Options {
        public int theme=0;
        public boolean dates=true,title=true;
        public String signature="";
        public int textSize=40;
        public Options copy(){Options o=new Options();o.theme=Math.max(0,Math.min(THEMES.length-1,theme));o.dates=dates;o.title=title;o.signature=signature==null?"":signature.substring(0,Math.min(signature.length(),80));o.textSize=Math.max(28,Math.min(56,textSize));return o;}
    }
    private static final class Block {
        StaticLayout text; String imagePath; int height;
        Block(StaticLayout text){this.text=text;height=text.getHeight();}
        Block(String path,int height){imagePath=path;this.height=height;}
        void draw(Canvas c,int y){
            if(text!=null){c.save();c.translate(PAD,y);text.draw(c);c.restore();return;}
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(imagePath,o);if(o.outWidth<1||o.outHeight<1)return;
            int sample=1;while(o.outWidth/sample>CONTENT*2||o.outHeight/sample>2400)sample*=2;
            o.inJustDecodeBounds=false;o.inSampleSize=sample;Bitmap image=BitmapFactory.decodeFile(imagePath,o);
            if(image!=null)try{float w=(float)image.getWidth()*height/image.getHeight();c.drawBitmap(image,null,new RectF((WIDTH-w)/2,y,(WIDTH+w)/2,y+height),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));}finally{image.recycle();}
        }
    }
    private static Block text(CharSequence value,float size,int color,boolean bold){
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,bold?Typeface.BOLD:Typeface.NORMAL));
        return new Block(StaticLayout.Builder.obtain(value,0,value.length(),p,CONTENT).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(7,1.16f).build());
    }
    private static List<Block> blocks(NoteStore.Note note,Options options)throws IOException{
        if(note==null)throw new IOException("笔记不存在");
        long length=note.title.length();for(NoteStore.Record r:note.records)length+=Math.max(r.text.length(),r.html.length());
        if(length>500000||note.records.size()>10000)throw new IOException("笔记内容过多，请分篇导出");
        List<Block> out=new ArrayList<>();
        if(!options.signature.isEmpty())out.add(text("✎  "+options.signature,30,0xffa0a0a0,false));
        if(options.title&&!note.title.isEmpty())out.add(text(note.title,48,0xff303030,true));
        SimpleDateFormat date=new SimpleDateFormat("yyyy.MM.dd",Locale.CHINA);
        for(NoteStore.Record record:note.records){
            if(options.dates)out.add(text(date.format(new Date(record.createdAt)),27,0xff454545,true));
            SpannableStringBuilder rich=new SpannableStringBuilder();if(record.todo)rich.append(record.done?"☑ ":"☐ ");rich.append(RichText.decode(record.html,record.text));PaperChecks.apply(rich);
            if(rich.length()>0)out.add(text(rich,options.textSize,0xff414141,false));
            for(String path:record.images){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,bounds);if(bounds.outWidth>0&&bounds.outHeight>0)out.add(new Block(path,Math.max(1,Math.min(1100,(int)((long)bounds.outHeight*CONTENT/bounds.outWidth)))));}
            out.add(text(" ",12,Color.TRANSPARENT,false));
        }
        return out;
    }
    private static int height(List<Block> blocks)throws IOException{
        long total=PAD*2;for(Block b:blocks)total+=b.height+22;
        if(total>MAX_HEIGHT)throw new IOException("笔记过长，请导出 PDF 或分篇保存图片");return (int)Math.max(380,total);
    }
    private static void frame(Canvas c,int height,Options options){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int theme=options.theme;
        c.drawColor(theme==0?0xffb99b68:theme==1?0xff96cce1:theme==2?0xff494c4f:theme==3?0xffe9a7b5:0xff90a0a9);
        if(theme==0){for(int x=1;x<WIDTH;x+=9){p.setColor(x%3==0?0x1b5c3b1a:0x18ffffff);p.setStrokeWidth(x%5==0?2:1);c.drawLine(x,0,x+(x%7-3)*2,height,p);}}
        if(theme==1||theme==3){p.setColor(0x45ffffff);for(int y=0;y<height;y+=92)for(int x=(y/92%2)*46;x<WIDTH;x+=92)c.drawCircle(x,y,19,p);}
        if(theme==2){p.setColor(0xff242424);c.drawRoundRect(24,88,WIDTH-18,height-16,52,52,p);}
        p.setColor(0x28000000);c.drawRoundRect(26,theme==2?102:30,WIDTH-20,height-18,theme==4?2:46,theme==4?2:46,p);
        p.setColor(Color.WHITE);c.drawRoundRect(24,theme==2?100:24,WIDTH-24,height-24,theme==4?2:40,theme==4?2:40,p);
        if(theme==2){p.setColor(0xffe7e7e7);for(int y=122;y<height-40;y+=38)for(int x=58;x<WIDTH-40;x+=38)c.drawCircle(x,y,2.3f,p);}
    }
    private static Bitmap render(NoteStore.Note note,Options raw,int requestedWidth)throws IOException{
        Options options=(raw==null?new Options():raw).copy();List<Block> blocks=blocks(note,options);int h=height(blocks);
        if(options.theme==2){h+=80;if(h>MAX_HEIGHT)throw new IOException("笔记过长，请导出 PDF");}
        int width=Math.max(240,Math.min(WIDTH,requestedWidth));float scale=width/(float)WIDTH;Bitmap page=null;
        try{page=Bitmap.createBitmap(width,(int)Math.ceil(h*scale),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(page);c.scale(scale,scale);frame(c,h,options);int y=PAD+(options.theme==2?80:0);for(Block block:blocks){block.draw(c,y);y+=block.height+22;}return page;}
        catch(RuntimeException|OutOfMemoryError e){if(page!=null)page.recycle();throw new IOException("图片过大，请导出 PDF 或分篇保存",e);}
    }
    /** Caller owns the bitmap; previews use a smaller canvas without changing layout. */
    public static Bitmap preview(NoteStore.Note note,Options options,int width)throws IOException{return render(note,options,width);}
    public static void png(NoteStore.Note note,OutputStream output)throws IOException{png(note,output,new Options());}
    public static void png(NoteStore.Note note,OutputStream output,Options options)throws IOException{
        Bitmap page=render(note,options,WIDTH);try{if(!page.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("图片写入失败");}finally{page.recycle();}
    }
    public static String plainText(NoteStore.Note note){
        StringBuilder out=new StringBuilder();if(!note.title.isEmpty())out.append(note.title).append("\n\n");
        for(NoteStore.Record record:note.records){if(record.todo)out.append(record.done?"☑ ":"☐ ");out.append(RichText.decode(record.html,record.text));if(!record.images.isEmpty())out.append("\n[图片 × ").append(record.images.size()).append(']');out.append("\n\n");}
        return out.toString().trim();
    }
    public static void txt(NoteStore.Note note,OutputStream output)throws IOException{output.write(plainText(note).getBytes(StandardCharsets.UTF_8));}
    public static void pdf(NoteStore.Note note,OutputStream output)throws IOException{
        Options options=new Options();List<Block> blocks=blocks(note,options);PdfDocument doc=new PdfDocument();PdfDocument.Page page=null;int y=PAD,count=0;
        try{for(Block b:blocks){int consumed=0;while(consumed<b.height){if(page==null){page=doc.startPage(new PdfDocument.PageInfo.Builder(WIDTH,1528,++count).create());page.getCanvas().drawColor(Color.WHITE);y=PAD;}int available=1528-PAD-y;if(available<48){doc.finishPage(page);page=null;continue;}int take=Math.min(available,b.height-consumed);
                    if(b.text!=null&&take<b.height-consumed){int desired=consumed+take;int line=b.text.getLineForVertical(desired);int boundary=b.text.getLineTop(line);if(boundary>consumed)take=boundary-consumed;}
                    if(b.imagePath!=null&&consumed==0&&b.height>available&&y>PAD){doc.finishPage(page);page=null;continue;}
                    Canvas c=page.getCanvas();c.save();c.clipRect(0,y,WIDTH,y+take);b.draw(c,y-consumed);c.restore();consumed+=take;y+=take;if(consumed<b.height){doc.finishPage(page);page=null;}}
                y+=22;}
            if(page==null&&count==0){page=doc.startPage(new PdfDocument.PageInfo.Builder(WIDTH,1528,1).create());page.getCanvas().drawColor(Color.WHITE);}
            if(page!=null){doc.finishPage(page);page=null;}doc.writeTo(output);
        }finally{if(page!=null)doc.finishPage(page);doc.close();}
    }
    private NoteExporter(){}
}
