package com.personal.inote;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import junit.framework.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CustomTemplateStoreTest extends TestCase {
    private Context isolated;
    @Override protected void setUp()throws Exception{super.setUp();isolated=TestContexts.create(PersonalTestRunner.instrumentation.getTargetContext(),"custom-template-tests");TestContexts.deleteTree(isolated.getFilesDir());isolated.getFilesDir().mkdirs();}
    @Override protected void tearDown()throws Exception{TestContexts.deleteTree(isolated.getFilesDir());super.tearDown();}
    public void testPrivateRichSnapshotAndDeletePreserveReadOnlyNote()throws Exception{
        NoteStore.Note note=new NoteStore.Note();note.title="source";note.readOnly=true;NoteStore.Record r=new NoteStore.Record();r.text="formatted";r.images.add("private-attachment-not-copied");
        SpannableString rich=new SpannableString(r.text);rich.setSpan(new StyleSpan(1),0,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);r.html=RichText.encode(rich);note.records.add(r);
        String id=CustomTemplateStore.save(isolated,note,"My template");JSONArray all=CustomTemplateStore.load(isolated);assertEquals(1,all.length());
        JSONObject template=all.getJSONObject(0);assertFalse(template.toString().contains("private-attachment-not-copied"));Spanned rendered=TemplateLibrary.render(template.getJSONArray("outlines").getJSONObject(0));assertEquals("formatted",rendered.toString());assertEquals(1,rendered.getSpans(0,rendered.length(),StyleSpan.class).length);
        assertTrue(note.readOnly);assertEquals("source",note.title);assertEquals(rich.toString(),r.text);assertTrue(CustomTemplateStore.delete(isolated,id));assertEquals(0,CustomTemplateStore.load(isolated).length());assertFalse(CustomTemplateStore.delete(isolated,"community-hans-01"));
    }
    public void testInvalidTemplatesDoNotPersist()throws Exception{
        NoteStore.Note note=new NoteStore.Note();try{CustomTemplateStore.save(isolated,note,"empty");fail();}catch(IllegalArgumentException expected){}
        note.records.add(new NoteStore.Record());note.records.get(0).text="body";note.deleted=true;try{CustomTemplateStore.save(isolated,note,"deleted");fail();}catch(IllegalArgumentException expected){}
        note.deleted=false;try{CustomTemplateStore.save(isolated,note,"  ");fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,CustomTemplateStore.load(isolated).length());
    }
    public void testTodoSnapshotPreservesCheckboxState()throws Exception{
        NoteStore.Note note=new NoteStore.Note();NoteStore.Record record=new NoteStore.Record();record.text="task";record.todo=true;record.done=true;note.records.add(record);CustomTemplateStore.save(isolated,note,"Checklist");
        JSONObject outline=CustomTemplateStore.load(isolated).getJSONObject(0).getJSONArray("outlines").getJSONObject(0);assertEquals("☑ task",TemplateLibrary.render(outline).toString());
    }
}
