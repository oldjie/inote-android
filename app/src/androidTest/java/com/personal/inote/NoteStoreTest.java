package com.personal.inote;

import android.content.Context;
import android.content.ContextWrapper;
import android.test.AndroidTestCase;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/** On-device SQLite and backup integration checks, isolated from the user's database. */
@SuppressWarnings("deprecation")
public class NoteStoreTest extends AndroidTestCase {
    private NoteStore store;
    private Context isolated;
    private File testFiles;
    @Override protected void setUp() throws Exception {
        super.setUp();
        testFiles=new File(getContext().getCacheDir(),"store-tests");testFiles.mkdirs();
        isolated=TestContexts.create(getContext(),"store-tests");
        isolated.deleteDatabase("inote.db");store=new NoteStore(isolated);
    }
    @Override protected void tearDown() throws Exception {
        store.close();isolated.deleteDatabase("inote.db");deleteTree(testFiles);super.tearDown();
    }
    private static void deleteTree(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);f.delete();}

    public void testMultipleRecordsPersistAcrossReopen() {
        NoteStore.Note note=store.createNote("旅行");
        NoteStore.Record first=store.addRecord(note.id,"first");first.html="<b>first</b>";store.saveRecord(first);
        NoteStore.Record second=store.addRecord(note.id,"second");second.todo=true;second.done=true;store.saveRecord(second);
        store.close();store=new NoteStore(isolated);
        NoteStore.Note saved=store.getNote(note.id);
        assertEquals(2,saved.records.size());assertEquals("<b>first</b>",saved.records.get(0).html);
        assertTrue(saved.records.get(1).done);assertTrue(saved.records.get(1).todo);
        assertEquals(1,store.listNotes("all","second",0).size());
    }
    public void testManyToManyAndDeleteBookPreservesNotes() {
        NoteStore.Note a=store.createNote("A"),b=store.createNote("B");
        NoteStore.Book one=store.createBook("One"),two=store.createBook("Two");
        store.setNoteBooks(a.id,Arrays.asList(one.id,two.id));store.setNoteBooks(b.id,Arrays.asList(one.id));
        assertEquals(2,store.listNotes("all","",one.id).size());assertEquals(1,store.listNotes("all","",two.id).size());
        store.deleteBook(one.id);
        assertEquals(2,store.listNotes("all","",0).size());assertEquals(Arrays.asList(two.id),store.getNote(a.id).bookIds);assertTrue(store.getNote(b.id).bookIds.isEmpty());
    }
    public void testBackupMergesMetadataRecordsAndImageBytes() throws Exception {
        NoteStore.Note n=store.createNote("备份");NoteStore.Book book=store.createBook("资料");
        NoteStore.Record r=store.addRecord(n.id,"body");r.html="<i>body</i>";r.todo=true;
        File image=new File(testFiles,"sample.png");byte[] bytes=new byte[]{1,2,3,4};Files.write(image.toPath(),bytes);r.images.add(image.getAbsolutePath());store.saveRecord(r);
        n.bookIds.add(book.id);n.pinned=true;n.starred=true;n.color="#abcdef";store.saveNote(n);
        String backup=store.exportJson();store.importJson(backup);
        assertEquals(2,store.listNotes("all","",0).size());assertEquals(2,store.listBooks().size());
        NoteStore.Note imported=null;for(NoteStore.Note candidate:store.listNotes("all","",0))if(candidate.id!=n.id)imported=candidate;
        assertNotNull(imported);assertTrue(imported.pinned);assertTrue(imported.starred);assertEquals(n.createdAt,imported.createdAt);
        assertEquals("#abcdef",imported.color);assertEquals(1,imported.records.size());assertEquals("<i>body</i>",imported.records.get(0).html);
        assertFalse(imported.bookIds.contains(book.id));assertTrue(Arrays.equals(bytes,Files.readAllBytes(new File(imported.records.get(0).images.get(0)).toPath())));
    }
    public void testInvalidBackupPreservesExistingData() throws Exception {
        NoteStore.Note n=store.createNote("keep");store.addRecord(n.id,"untouched");
        String before=store.exportJson();
        assertRejected("{broken");assertEquals(before,store.exportJson());
        JSONObject bad=new JSONObject(before);bad.getJSONArray("notes").getJSONObject(0).getJSONArray("books").put(99999);
        assertRejected(bad.toString());assertEquals(before,store.exportJson());
        bad=new JSONObject(before);bad.getJSONArray("notes").getJSONObject(0).getJSONArray("records").getJSONObject(0).getJSONArray("images").put("missing-image");
        assertRejected(bad.toString());assertEquals(before,store.exportJson());
    }
    private void assertRejected(String backup){try{store.importJson(backup);fail("Expected invalid backup rejection");}catch(IllegalArgumentException expected){}}
    public void testStrictBackupTypesAndFlashcardReferences() throws Exception {
        NoteStore.Note n=store.createNote("cards");NoteStore.Record a=store.addRecord(n.id,"Q"),b=store.addRecord(n.id,"A");store.createFlashcard(n.id,a.id,b.id);
        String original=store.exportJson();JSONObject bad=new JSONObject(original);bad.getJSONArray("notes").getJSONObject(0).put("pinned","false");assertRejected(bad.toString());
        bad=new JSONObject(original);bad.getJSONArray("notes").getJSONObject(0).put("id",1.5);assertRejected(bad.toString());
        bad=new JSONObject(original);bad.getJSONArray("flashcards").getJSONObject(0).put("back_record",99999);assertRejected(bad.toString());assertEquals(original,store.exportJson());
    }
    public void testFlashcardsIndependentReviewBackupAndCascade() throws Exception {
        NoteStore.Note n=store.createNote("cards");NoteStore.Record a=store.addRecord(n.id,"Q"),b=store.addRecord(n.id,"A");
        NoteStore.Flashcard card=store.createFlashcard(n.id,a.id,b.id);assertEquals(1,store.listFlashcards(true).size());store.reviewFlashcard(card.id,true);
        assertTrue(store.listFlashcards(true).isEmpty());assertEquals(1,store.listFlashcards(false).get(0).level);assertEquals(0,store.getNote(n.id).reviewLevel);
        store.importJson(store.exportJson());assertEquals(2,store.listFlashcards(false).size());store.deleteRecord(a.id);assertEquals(1,store.listFlashcards(false).size());
    }
    public void testFoldingSortingAndSharedImageLifecycle() throws Exception {
        NoteStore.Note n=store.createNote("ordered");NoteStore.Record a=store.addRecord(n.id,"first"),b=store.addRecord(n.id,"last");
        a.createdAt=100;b.createdAt=200;a.folded=true;
        File folder=new File(testFiles,"images");folder.mkdirs();File image=new File(folder,"shared.img");Files.write(image.toPath(),new byte[]{1,2});
        a.images.add(image.getAbsolutePath());b.images.add(image.getAbsolutePath());store.saveRecord(a);store.saveRecord(b);
        n.sortOrder="newest";n.folded=true;store.saveNote(n);assertEquals(b.id,store.getNote(n.id).records.get(0).id);assertTrue(store.getNote(n.id).records.get(1).folded);assertTrue(store.getNote(n.id).folded);
        store.deleteRecord(a.id);assertTrue(image.exists());store.deleteRecord(b.id);assertFalse(image.exists());
    }
    public void testMembershipFailureRollsBackAndReadOnlyProtectsRecords() {
        NoteStore.Note n=store.createNote("safe");NoteStore.Book b=store.createBook("book");store.setNoteBooks(n.id,Arrays.asList(b.id));
        try{store.setNoteBooks(n.id,Arrays.asList(987654L));fail("Expected foreign key failure");}catch(android.database.sqlite.SQLiteConstraintException expected){}
        assertEquals(Arrays.asList(b.id),store.getNote(n.id).bookIds);
        n=store.getNote(n.id);n.readOnly=true;store.saveNote(n);
        try{store.addRecord(n.id,"blocked");fail("Expected read-only protection");}catch(IllegalStateException expected){}
        assertTrue(store.getNote(n.id).records.isEmpty());
        n.starred=true;store.saveNote(n);assertTrue(store.getNote(n.id).starred);
        n.title="changed";try{store.saveNote(n);fail("Expected locked title protection");}catch(IllegalStateException expected){}
        assertEquals("safe",store.getNote(n.id).title);n=store.getNote(n.id);n.readOnly=false;store.saveNote(n);n.title="unlocked";store.saveNote(n);assertEquals("unlocked",store.getNote(n.id).title);
    }
    public void testTrashRestoreAndPermanentDelete() {
        NoteStore.Note n=store.createNote("trash");store.addRecord(n.id,"record");
        store.purgeNote(n.id);assertNotNull(store.getNote(n.id));
        n.deleted=true;store.saveNote(n);assertTrue(store.listNotes("all","",0).isEmpty());assertEquals(1,store.listNotes("trash","",0).size());
        store.restoreNote(n.id);assertEquals(1,store.listNotes("all","",0).size());
        n=store.getNote(n.id);n.deleted=true;store.saveNote(n);store.purgeNote(n.id);assertNull(store.getNote(n.id));
    }
    private void createLegacyDatabase(int version) {
        store.close();isolated.deleteDatabase("inote.db");
        android.database.sqlite.SQLiteDatabase db=isolated.openOrCreateDatabase("inote.db",0,null);
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,color TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,pinned INTEGER NOT NULL DEFAULT 0,starred INTEGER NOT NULL DEFAULT 0,archived INTEGER NOT NULL DEFAULT 0,deleted INTEGER NOT NULL DEFAULT 0,readonly INTEGER NOT NULL DEFAULT 0,review_at INTEGER NOT NULL DEFAULT 0,review_level INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,text TEXT NOT NULL,html TEXT NOT NULL,todo INTEGER NOT NULL DEFAULT 0,done INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL,updated INTEGER NOT NULL,images TEXT NOT NULL DEFAULT '[]')");
        db.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,color TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE note_books (note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,PRIMARY KEY(note_id,book_id))");
        db.execSQL("INSERT INTO notes (id,title,color,created,updated) VALUES(1,'legacy','#abcdef',10,20)");
        db.execSQL("INSERT INTO records (id,note_id,text,html,created,updated) VALUES(1,1,'existing body','<b>existing body</b>',11,21)");
        db.execSQL("INSERT INTO books (id,name,color,created) VALUES(1,'existing book','',12)");
        db.execSQL("INSERT INTO note_books (note_id,book_id) VALUES(1,1)");
        if(version==3){
            db.execSQL("ALTER TABLE notes ADD COLUMN folded INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE notes ADD COLUMN sort_order TEXT NOT NULL DEFAULT 'oldest'");db.execSQL("ALTER TABLE records ADD COLUMN folded INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE flashcards (id INTEGER PRIMARY KEY AUTOINCREMENT,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,front_record INTEGER NOT NULL REFERENCES records(id) ON DELETE CASCADE,back_record INTEGER NOT NULL REFERENCES records(id) ON DELETE CASCADE,due_at INTEGER NOT NULL DEFAULT 0,level INTEGER NOT NULL DEFAULT 0,UNIQUE(front_record,back_record))");
            db.execSQL("ALTER TABLE books ADD COLUMN cover TEXT NOT NULL DEFAULT ''");db.execSQL("UPDATE books SET cover='cover-existing.png'");
        }
        db.setVersion(version);db.close();
    }
    public void testVersionOneMigrationPreservesNotes() {
        createLegacyDatabase(1);
        store=new NoteStore(isolated);assertEquals("legacy",store.getNote(1).title);assertEquals("oldest",store.getNote(1).sortOrder);assertFalse(store.getNote(1).folded);assertTrue(store.listFlashcards(false).isEmpty());
    }
    public void testVersionThreeMigrationPreservesCoverRecordsAndMembership() {
        createLegacyDatabase(3);store=new NoteStore(isolated);NoteStore.Note n=store.getNote(1);assertEquals(4,store.getReadableDatabase().getVersion());
        assertEquals("#abcdef",n.color);assertEquals(Arrays.asList(1L),n.bookIds);assertEquals(1,n.records.size());assertEquals("<b>existing body</b>",n.records.get(0).html);assertEquals(0,n.records.get(0).colorType);
        NoteStore.Book b=store.listBooks().get(0);assertEquals("existing book",b.name);assertEquals("cover-existing.png",b.cover);assertEquals("",b.icon);
    }
    public void testBookIconAndRecordColorBackupCompatibilityAndValidation() throws Exception {
        NoteStore.Note n=store.createNote("colors");NoteStore.Record r=store.addRecord(n.id,"body");r.colorType=5;store.saveRecord(r);NoteStore.Book b=store.createBook("icons");store.setBookIcon(b.id,"ico_book_12");
        store.close();store=new NoteStore(isolated);assertEquals(5,store.getNote(n.id).records.get(0).colorType);assertEquals("ico_book_12",store.listBooks().get(0).icon);
        String backup=store.exportJson();store.importJson(backup);assertEquals("ico_book_12",store.listBooks().get(1).icon);for(NoteStore.Note saved:store.listNotes("all","",0))assertEquals(5,saved.records.get(0).colorType);
        JSONObject old=new JSONObject(backup);old.getJSONArray("books").getJSONObject(0).remove("icon");old.getJSONArray("notes").getJSONObject(0).getJSONArray("records").getJSONObject(0).remove("color_type");store.importJson(old.toString());assertEquals("",store.listBooks().get(2).icon);
        int defaults=0;for(NoteStore.Note saved:store.listNotes("all","",0))if(saved.records.get(0).colorType==0)defaults++;assertEquals(1,defaults);
        String before=store.exportJson();JSONObject invalid=new JSONObject(backup);invalid.getJSONArray("books").getJSONObject(0).put("icon",12);assertRejected(invalid.toString());
        for(Object bad:new Object[]{"5",1.5,4294967296L}){invalid=new JSONObject(backup);invalid.getJSONArray("notes").getJSONObject(0).getJSONArray("records").getJSONObject(0).put("color_type",bad);assertRejected(invalid.toString());}assertEquals(before,store.exportJson());
    }
    public void testRichTextRoundTripKeepsFormattingAndNewlines() {
        android.text.SpannableString input=new android.text.SpannableString("bold\nhighlight\ncode\nstrike\n");int flag=android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        input.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,4,flag);
        input.setSpan(new android.text.style.BackgroundColorSpan(0xffabc123),5,14,flag);
        input.setSpan(new android.text.style.TypefaceSpan("monospace"),15,19,flag);
        input.setSpan(new android.text.style.StrikethroughSpan(),20,26,flag);
        android.text.Spanned output=(android.text.Spanned)RichText.decode(RichText.encode(input),"");assertEquals(input.toString(),output.toString());
        android.text.style.StyleSpan bold=output.getSpans(0,output.length(),android.text.style.StyleSpan.class)[0];assertEquals(android.graphics.Typeface.BOLD,bold.getStyle());assertEquals(0,output.getSpanStart(bold));assertEquals(4,output.getSpanEnd(bold));
        assertEquals(0xffabc123,output.getSpans(0,output.length(),android.text.style.BackgroundColorSpan.class)[0].getBackgroundColor());
        assertEquals("monospace",output.getSpans(0,output.length(),android.text.style.TypefaceSpan.class)[0].getFamily());
        android.text.style.StrikethroughSpan strike=output.getSpans(0,output.length(),android.text.style.StrikethroughSpan.class)[0];assertEquals(20,output.getSpanStart(strike));assertEquals(26,output.getSpanEnd(strike));
    }
    public void testCommunityTemplatesRenderAndRoundTrip() throws Exception {
        org.json.JSONArray all=TemplateLibrary.load(getContext());assertEquals(3,all.length());int hans=0,hant=0,en=0;
        for(int i=0;i<all.length();i++){
            JSONObject item=all.getJSONObject(i);String language=item.getString("language");if(language.equals("hans"))hans++;else if(language.equals("hant"))hant++;else if(language.equals("en"))en++;else fail("Unexpected template language");
            org.json.JSONArray outlines=item.getJSONArray("outlines");assertTrue(outlines.length()>0);
            for(int j=0;j<outlines.length();j++){android.text.SpannableStringBuilder rendered=TemplateLibrary.render(outlines.getJSONObject(j));assertEquals(rendered.toString(),RichText.decode(RichText.encode(rendered),"").toString());}
        }
        assertEquals(1,hans);assertEquals(1,hant);assertEquals(1,en);
    }
    public void testBookCoverPersistsAndBacksUp() {
        NoteStore.Book b=store.createBook("cover");store.setBookCover(b.id,"cover-01.png");store.close();store=new NoteStore(isolated);assertEquals("cover-01.png",store.listBooks().get(0).cover);
        store.importJson(store.exportJson());assertEquals(2,store.listBooks().size());assertEquals("cover-01.png",store.listBooks().get(1).cover);
    }
}
