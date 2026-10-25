package com.personal.inote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.test.AndroidTestCase;
import java.io.*;

public class NoteExporterTest extends AndroidTestCase {
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
