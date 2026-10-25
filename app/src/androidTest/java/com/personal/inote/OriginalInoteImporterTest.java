package com.personal.inote;

import android.content.*;
import android.app.Instrumentation;
import junit.framework.TestCase;
import android.text.Spanned;
import android.text.style.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Generated ciphertext fixtures plus malformed-container and transaction checks. */
@SuppressWarnings("deprecation")
public class OriginalInoteImporterTest extends TestCase {
    private NoteStore store;private Context isolated;
    private Instrumentation getInstrumentation(){return PersonalTestRunner.instrumentation;}
    @Override protected void setUp()throws Exception{
        super.setUp();
        isolated=TestContexts.create(getInstrumentation().getTargetContext(),"original-import-tests");
        isolated.deleteDatabase("inote.db");store=new NoteStore(isolated);
    }
    @Override protected void tearDown()throws Exception{store.close();isolated.deleteDatabase("inote.db");TestContexts.deleteTree(isolated.getFilesDir());super.tearDown();}
    public void testGeneratedCiphertext()throws Exception{long id=OriginalInoteImporter.importBytes(store,crypt(archive("v1",null,"[{\"s_t\":\"sample\"}]")),"test-fixture-key");assertEquals("sample",store.getNote(id).records.get(0).text);}
    private byte[] crypt(byte[] clear)throws Exception{Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(Arrays.copyOf("test-fixture-key".getBytes(StandardCharsets.UTF_8),32),"AES"),new IvParameterSpec(new byte[16]));return c.doFinal(clear);}
    private byte[] archive(String version,String extra,String runs)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){
            put(z,"importable_info.json","{\"com.chii.inote.importable.file.version\":\""+version+"\",\"com.chii.inote.importable.doc.tokens\":[\"record1\"]}");
            put(z,"Note/record1_v1",runs);
            if(extra!=null)put(z,extra,"unknown");
        }return bytes.toByteArray();
    }
    private void put(ZipOutputStream z,String name,String value)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(value.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private void reject(byte[] bytes)throws Exception{int before=store.listNotes("all","",0).size();try{OriginalInoteImporter.importBytes(store,bytes,"test-fixture-key");fail("Expected rejection");}catch(Exception expected){}assertEquals(before,store.listNotes("all","",0).size());}
    public void testRejectUnknownVersionAttachmentAndTraversal()throws Exception{
        String runs="[{\"s_t\":\"body\",\"a_f\":{\"b\":true}}]";
        reject(crypt(archive("v2",null,runs)));reject(crypt(archive("v1","Attachment/image.png",runs)));reject(crypt(archive("v1","../outside",runs)));
        reject(crypt(archive("v1",null,"[{\"s_t\":\"body\",\"a_tia\":{}}]")));
    }
    public void testRejectCorruptionAndOversize()throws Exception{
        byte[] zip=archive("v1",null,"[{\"s_t\":\"body\"}]");
        for(int i=0;i<zip.length-20;i++)if(zip[i]==0x50&&zip[i+1]==0x4b&&zip[i+2]==1&&zip[i+3]==2){zip[i+16]^=1;break;}
        reject(crypt(zip));reject(new byte[16*1024*1024+16]);reject(new byte[17]);
    }
    public void testRecordFailureRollsBackWholeNote()throws Exception{
        store.getWritableDatabase().execSQL("CREATE TRIGGER fail_second_record BEFORE INSERT ON records WHEN (SELECT COUNT(*) FROM records)>=0 BEGIN SELECT RAISE(ABORT,'test rollback'); END");
        reject(crypt(archive("v1",null,"[{\"s_t\":\"first\"}]")));assertEquals(0,store.listNotes("all","",0).size());
    }
}
