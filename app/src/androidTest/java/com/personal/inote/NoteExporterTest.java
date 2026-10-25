package com.personal.inote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.test.AndroidTestCase;
import java.io.*;

public class NoteExporterTest extends AndroidTestCase {
    private NoteStore.Note example(){NoteStore.Note n=new NoteStore.Note();n.title="旅行计划";n.createdAt=1700000000000L;NoteStore.Record r=new NoteStore.Record();r.createdAt=n.createdAt;r.text="☐ 收好行李\n☑ 记下今天的想法";n.records.add(r);return n;}
    public void testThemesDateSignatureAndUtf8Text()throws Exception{
        NoteStore.Note n=example();NoteExporter.Options options=new NoteExporter.Options();int[] corners=new int[NoteExporter.THEMES.length];
        for(int theme=0;theme<corners.length;theme++){options.theme=theme;Bitmap image=NoteExporter.preview(n,options,540);assertEquals(540,image.getWidth());corners[theme]=image.getPixel(1,20);image.recycle();}
        for(int a=0;a<corners.length;a++)for(int b=a+1;b<corners.length;b++)assertFalse("Theme backgrounds must differ",corners[a]==corners[b]);
        options.theme=0;options.dates=false;Bitmap noDate=NoteExporter.preview(n,options,540);int plainHeight=noDate.getHeight();noDate.recycle();options.dates=true;Bitmap dated=NoteExporter.preview(n,options,540);assertTrue(dated.getHeight()>plainHeight);int dateHeight=dated.getHeight();dated.recycle();options.signature="小小的日常记录";Bitmap signed=NoteExporter.preview(n,options,540);assertTrue(signed.getHeight()>dateHeight);signed.recycle();
        n.records.get(0).todo=true;n.records.get(0).done=true;ByteArrayOutputStream text=new ByteArrayOutputStream();NoteExporter.txt(n,text);String value=text.toString("UTF-8");assertTrue(value.startsWith("旅行计划"));assertTrue(value.contains("☑ ☐ 收好行李"));assertFalse(value.contains("inote-spans"));
    }
    public void testOversizedImageSuggestsPdf()throws Exception{
        NoteStore.Note n=example();StringBuilder text=new StringBuilder();for(int i=0;i<1000;i++)text.append("一行很长的测试文字\n");n.records.get(0).text=text.toString();try{Bitmap b=NoteExporter.preview(n,new NoteExporter.Options(),540);b.recycle();fail("An overlong PNG must fail before bitmap allocation");}catch(IOException expected){assertTrue(expected.getMessage().contains("PDF"));}
    }
    public void testShareProviderRejectsWritesAndTraversal()throws Exception{
        android.content.Context context=PersonalTestRunner.instrumentation.getTargetContext();File dir=new File(context.getCacheDir(),"export-shares");assertTrue(dir.isDirectory()||dir.mkdirs());File file=new File(dir,java.util.UUID.randomUUID()+".png");
        android.net.Uri uri=new android.net.Uri.Builder().scheme("content").authority(context.getPackageName()+".exports").appendPath(file.getName()).build();
        try{try(OutputStream out=new FileOutputStream(file)){NoteExporter.png(example(),out);}try(InputStream in=context.getContentResolver().openInputStream(uri)){assertNotNull(in);assertEquals(137,in.read());}
            try{ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"rw");if(fd!=null)fd.close();fail("Export provider must reject write access");}catch(FileNotFoundException expected){}
            android.net.Uri bad=new android.net.Uri.Builder().scheme("content").authority(context.getPackageName()+".exports").appendPath("../inote.db").build();try{InputStream in=context.getContentResolver().openInputStream(bad);if(in!=null)in.close();fail("Export provider must reject arbitrary paths");}catch(FileNotFoundException expected){}
        }finally{file.delete();}
    }
    public void testExportStyleActivityAndCapture()throws Exception{
        android.app.Instrumentation instrumentation=PersonalTestRunner.instrumentation;android.content.Context context=instrumentation.getTargetContext();long noteId;
        try(NoteStore store=new NoteStore(context)){NoteStore.Note note=store.createNote("纸张导出预览");noteId=note.id;store.addRecord(noteId,"☐ 整理旅行行李\n☑ 留一点时间记录想法\n\n记忆会慢慢模糊，才需要做好记录。");}
        ExportStyleActivity activity=null;android.content.SharedPreferences prefs=context.getSharedPreferences("export-style",0);int originalTheme=prefs.getInt("theme",0);String originalSignature=prefs.getString("signature","");boolean originalDates=prefs.getBoolean("dates",true),originalTitle=prefs.getBoolean("title",true);int originalSize=prefs.getInt("textSize",40);
        try{prefs.edit().putInt("theme",2).putString("signature","我的日常").putBoolean("dates",true).putBoolean("title",true).putInt("textSize",40).commit();
            activity=(ExportStyleActivity)instrumentation.startActivitySync(new android.content.Intent(context,ExportStyleActivity.class).putExtra("note_id",noteId).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));final ExportStyleActivity screen=activity;instrumentation.waitForIdleSync();
            final android.widget.ImageView[] image={null};for(int i=0;i<60;i++){instrumentation.runOnMainSync(()->image[0]=(android.widget.ImageView)find(screen.getWindow().getDecorView(),"导出图片预览"));if(image[0]!=null&&image[0].getDrawable()!=null)break;Thread.sleep(50);}
            instrumentation.runOnMainSync(()->{assertNotNull(find(screen.getWindow().getDecorView(),"选择图片样式"));assertNotNull(image[0]);assertNotNull(image[0].getDrawable());assertNotNull(find(screen.getWindow().getDecorView(),"高级设置"));assertNotNull(find(screen.getWindow().getDecorView(),"✓ 保存"));});
            // A populated ImageView does not imply the Activity's surface transition is finished.
            // Wait for drawn frames, then reject a transition/dimmed image rather than publishing it.
            java.util.concurrent.CountDownLatch frames=new java.util.concurrent.CountDownLatch(1);instrumentation.runOnMainSync(()->screen.getWindow().getDecorView().postOnAnimation(()->screen.getWindow().getDecorView().postOnAnimation(frames::countDown)));assertTrue(frames.await(3,java.util.concurrent.TimeUnit.SECONDS));
            Bitmap screenshot=null;boolean stable=false;for(int i=0;i<30;i++){Thread.sleep(150);instrumentation.waitForIdleSync();if(screenshot!=null)screenshot.recycle();screenshot=instrumentation.getUiAutomation().takeScreenshot();if(screenshot!=null&&lightPixel(screenshot,.90f,.10f)&&lightPixel(screenshot,.08f,.90f)&&lightPixel(screenshot,.50f,.50f)){stable=true;break;}}
            assertNotNull(screenshot);try{assertTrue("Export surface must be fully visible after transition",stable);assertTrue("Export status bar must have a light background",lightPixel(screenshot,.45f,.015f));File folder=new File(context.getExternalFilesDir(null),"ui-review");assertTrue(folder.isDirectory()||folder.mkdirs());try(OutputStream out=new FileOutputStream(new File(folder,"06-export-preview.png"))){assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));}}finally{screenshot.recycle();}
        }finally{if(activity!=null){final ExportStyleActivity close=activity;instrumentation.runOnMainSync(close::finish);}prefs.edit().putInt("theme",originalTheme).putString("signature",originalSignature).putBoolean("dates",originalDates).putBoolean("title",originalTitle).putInt("textSize",originalSize).commit();try(NoteStore store=new NoteStore(context)){NoteStore.Note note=store.getNote(noteId);if(note!=null){note.deleted=true;store.saveNote(note);store.purgeNote(noteId);}}}
    }
    private static boolean lightPixel(Bitmap bitmap,float x,float y){int color=bitmap.getPixel(Math.min(bitmap.getWidth()-1,(int)(bitmap.getWidth()*x)),Math.min(bitmap.getHeight()-1,(int)(bitmap.getHeight()*y)));return android.graphics.Color.red(color)>180&&android.graphics.Color.green(color)>180&&android.graphics.Color.blue(color)>180;}
    private static android.view.View find(android.view.View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View result=find(group.getChildAt(i),text);if(result!=null)return result;}}return null;}
    public void testPngAndMultipagePdf() throws Exception {
        NoteStore.Note note=new NoteStore.Note();note.title="导出检查";note.createdAt=System.currentTimeMillis();
        NoteStore.Record record=new NoteStore.Record();record.text="中文内容\n第二行";record.createdAt=note.createdAt;note.records.add(record);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();NoteExporter.png(note,bytes);
        Bitmap image=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size());assertNotNull(image);assertEquals(1080,image.getWidth());assertTrue(image.getHeight()>200);image.recycle();
        StringBuilder text=new StringBuilder();for(int i=0;i<150;i++)text.append("第 ").append(i).append(" 行，验证长笔记分页不会截断。\n");record.text=text.toString();
        File pdf=File.createTempFile("inote-export-test",".pdf",getContext().getCacheDir());
        try {try(OutputStream out=new FileOutputStream(pdf)){NoteExporter.pdf(note,out);}try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer reader=new PdfRenderer(fd)){assertTrue(reader.getPageCount()>1);for(int i=0;i<reader.getPageCount();i++){try(PdfRenderer.Page page=reader.openPage(i)){assertEquals(1080,page.getWidth());assertEquals(1528,page.getHeight());}}}}finally{pdf.delete();}
    }
}
