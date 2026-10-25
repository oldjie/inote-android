package com.personal.inote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local reconstruction. Review intervals are our own, not the original iOS algorithm. */
public final class NoteStore extends SQLiteOpenHelper {
    public static class Note {
        public long id, createdAt, updatedAt, reviewAt;
        public int reviewLevel;
        public String title = "", color = "", sortOrder = "oldest";
        public boolean pinned, starred, archived, deleted, readOnly, folded;
        public List<Record> records = new ArrayList<>();
        public List<Long> bookIds = new ArrayList<>();
    }
    public static class Record {
        public long id, noteId, createdAt, updatedAt;
        public int colorType;
        public String text = "", html = "";
        public boolean todo, done, folded;
        public List<String> images = new ArrayList<>();
    }
    public static class Book {
        public long id, createdAt;
        public String name = "", color = "", cover = "", icon = "";
    }
    public static class Flashcard {
        public long id,noteId,frontRecordId,backRecordId,dueAt;
        public int level;
    }
    private final File filesDir;
    public NoteStore(Context context) {
        super(context.getApplicationContext(), "inote.db", null, 4);
        filesDir = context.getApplicationContext().getFilesDir();
    }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,color TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,pinned INTEGER NOT NULL DEFAULT 0,starred INTEGER NOT NULL DEFAULT 0,archived INTEGER NOT NULL DEFAULT 0,deleted INTEGER NOT NULL DEFAULT 0,readonly INTEGER NOT NULL DEFAULT 0,review_at INTEGER NOT NULL DEFAULT 0,review_level INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,text TEXT NOT NULL,html TEXT NOT NULL,todo INTEGER NOT NULL DEFAULT 0,done INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL,updated INTEGER NOT NULL,images TEXT NOT NULL DEFAULT '[]')");
        db.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,color TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE note_books (note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,PRIMARY KEY(note_id,book_id))");
        db.execSQL("CREATE INDEX records_note ON records(note_id)");
        db.execSQL("CREATE INDEX memberships_book ON note_books(book_id)");
        upgradeToTwo(db);
        upgradeToThree(db);
        upgradeToFour(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) upgradeToTwo(db);
        if(oldVersion<3) upgradeToThree(db);
        if(oldVersion<4) upgradeToFour(db);
    }
    private static void upgradeToFour(SQLiteDatabase db){db.execSQL("ALTER TABLE books ADD COLUMN icon TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE records ADD COLUMN color_type INTEGER NOT NULL DEFAULT 0");}
    private static void upgradeToThree(SQLiteDatabase db){db.execSQL("ALTER TABLE books ADD COLUMN cover TEXT NOT NULL DEFAULT ''");}
    private static void upgradeToTwo(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE notes ADD COLUMN folded INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE notes ADD COLUMN sort_order TEXT NOT NULL DEFAULT 'oldest'");
        db.execSQL("ALTER TABLE records ADD COLUMN folded INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE flashcards (id INTEGER PRIMARY KEY AUTOINCREMENT,note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,front_record INTEGER NOT NULL REFERENCES records(id) ON DELETE CASCADE,back_record INTEGER NOT NULL REFERENCES records(id) ON DELETE CASCADE,due_at INTEGER NOT NULL DEFAULT 0,level INTEGER NOT NULL DEFAULT 0,UNIQUE(front_record,back_record))");
        db.execSQL("CREATE INDEX flashcards_due ON flashcards(due_at)");
    }
    public Note createNote(String title) {
        Note n = new Note(); n.title = safe(title); n.createdAt = n.updatedAt = System.currentTimeMillis();
        n.id = getWritableDatabase().insertOrThrow("notes", null, noteValues(n)); return n;
    }
    public void saveNote(Note n) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT title,readonly FROM notes WHERE id=?",args(n.id))) {
                if(c.moveToFirst() && c.getInt(1)!=0 && !str(c,"title").equals(safe(n.title))) throw new IllegalStateException("Unlock the note before changing its title");
            }
            n.updatedAt = System.currentTimeMillis();
            if (db.update("notes", noteValues(n), "id=?", args(n.id)) != 1) throw new IllegalArgumentException("Note not found");
            replaceBooks(db, n.id, n.bookIds); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public Note getNote(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM notes WHERE id=?", args(id))) {
            if (!c.moveToFirst()) return null;
            Note n = readNote(c); hydrate(n); return n;
        }
    }
    public List<Note> listNotes(String filter, String query, long bookId) {
        String where = "deleted=0 AND archived=0";
        if ("trash".equals(filter)) where = "deleted=1";
        else if ("archived".equals(filter)) where = "deleted=0 AND archived=1";
        else if ("starred".equals(filter)) where = "deleted=0 AND starred=1";
        List<String> params = new ArrayList<>();
        if (bookId > 0) { where += " AND id IN (SELECT note_id FROM note_books WHERE book_id=?)"; params.add(Long.toString(bookId)); }
        if (query != null && !query.trim().isEmpty()) {
            where += " AND (instr(lower(title),lower(?))>0 OR id IN (SELECT note_id FROM records WHERE instr(lower(text),lower(?))>0))";
            params.add(query); params.add(query);
        }
        return queryNotes("SELECT * FROM notes WHERE " + where + " ORDER BY pinned DESC,updated DESC,id DESC", params.toArray(new String[0]));
    }
    private List<Note> queryNotes(String sql, String[] params) {
        List<Note> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, params)) { while (c.moveToNext()) { Note n = readNote(c); hydrate(n); result.add(n); } }
        return result;
    }
    private void hydrate(Note n) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT * FROM records WHERE note_id=? ORDER BY created "+("newest".equals(n.sortOrder)?"DESC":"ASC")+",id "+("newest".equals(n.sortOrder)?"DESC":"ASC"), args(n.id))) {
            while (c.moveToNext()) {
                Record r = new Record(); r.id = number(c,"id"); r.noteId=n.id; r.text=str(c,"text"); r.html=str(c,"html");
                r.todo=flag(c,"todo"); r.done=flag(c,"done"); r.createdAt=number(c,"created"); r.updatedAt=number(c,"updated");
                r.folded=flag(c,"folded");
                r.colorType=(int)number(c,"color_type");
                try { JSONArray a = new JSONArray(str(c,"images")); for(int i=0;i<a.length();i++) r.images.add(a.getString(i)); }
                catch (JSONException e) { throw new IllegalStateException("Invalid stored attachment list",e); }
                n.records.add(r);
            }
        }
        try (Cursor c = db.rawQuery("SELECT book_id FROM note_books WHERE note_id=? ORDER BY book_id",args(n.id))) { while(c.moveToNext()) n.bookIds.add(c.getLong(0)); }
    }
    public Record addRecord(long noteId, String text) {
        requireEditable(noteId); Record r = new Record(); r.noteId=noteId; r.text=safe(text); r.createdAt=r.updatedAt=System.currentTimeMillis();
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { r.id=db.insertOrThrow("records",null,recordValues(r)); touch(db,noteId); db.setTransactionSuccessful(); } finally { db.endTransaction(); } return r;
    }
    public void saveRecord(Record r) {
        validateImages(r.images);
        requireEditable(r.noteId); r.updatedAt=System.currentTimeMillis(); SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            if(db.update("records",recordValues(r),"id=? AND note_id=?",new String[]{""+r.id,""+r.noteId})!=1) throw new IllegalArgumentException("Record not found");
            touch(db,r.noteId); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        cleanupUnreferencedImages();
    }
    public void deleteRecord(long id) {
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT note_id FROM records WHERE id=?",args(id))) {
            if(!c.moveToFirst()) return; long noteId=c.getLong(0); requireEditable(noteId); db.beginTransaction();
            try { db.delete("records","id=?",args(id)); touch(db,noteId); db.setTransactionSuccessful(); } finally { db.endTransaction(); }
        }
        cleanupUnreferencedImages();
    }
    private void requireEditable(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT readonly,deleted FROM notes WHERE id=?",args(id))) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Note not found");
            if(c.getInt(0)!=0 || c.getInt(1)!=0) throw new IllegalStateException("Note is read only or in trash");
        }
    }
    public void restoreNote(long id) { ContentValues v=new ContentValues(); v.put("deleted",0); v.put("updated",System.currentTimeMillis()); getWritableDatabase().update("notes",v,"id=?",args(id)); }
    public void purgeNote(long id) { getWritableDatabase().delete("notes","id=? AND deleted=1",args(id)); cleanupUnreferencedImages(); }
    public List<Book> listBooks() {
        List<Book> books=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM books ORDER BY created,id",null)) {
            while(c.moveToNext()) { Book b=new Book(); b.id=number(c,"id"); b.name=str(c,"name"); b.color=str(c,"color"); b.cover=str(c,"cover"); b.icon=str(c,"icon"); b.createdAt=number(c,"created"); books.add(b); }
        } return books;
    }
    public Book createBook(String name) { Book b=new Book(); b.name=safe(name).trim(); if(b.name.isEmpty()) throw new IllegalArgumentException("Empty book name"); b.createdAt=System.currentTimeMillis(); b.id=getWritableDatabase().insertOrThrow("books",null,bookValues(b)); return b; }
    public void renameBook(long id,String name) { String value=safe(name).trim(); if(value.isEmpty()) throw new IllegalArgumentException("Empty book name"); ContentValues v=new ContentValues();v.put("name",value);getWritableDatabase().update("books",v,"id=?",args(id)); }
    public void deleteBook(long id) { getWritableDatabase().delete("books","id=?",args(id)); }
    public void setBookCover(long id,String cover){ContentValues v=new ContentValues();v.put("cover",safe(cover));getWritableDatabase().update("books",v,"id=?",args(id));}
    public void setBookIcon(long id,String icon){ContentValues v=new ContentValues();v.put("icon",safe(icon));getWritableDatabase().update("books",v,"id=?",args(id));}
    public void setNoteBooks(long id,List<Long> bookIds) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { replaceBooks(db,id,bookIds); touch(db,id); db.setTransactionSuccessful(); } finally { db.endTransaction(); }
    }
    private void replaceBooks(SQLiteDatabase db,long id,List<Long> bookIds) {
        db.delete("note_books","note_id=?",args(id));
        for(long bookId:new HashSet<>(bookIds)) { ContentValues v=new ContentValues();v.put("note_id",id);v.put("book_id",bookId);db.insertOrThrow("note_books",null,v); }
    }
    public List<Note> dueNotes() { return queryNotes("SELECT * FROM notes WHERE deleted=0 AND archived=0 AND starred=1 AND review_at<=? ORDER BY review_at,updated",args(System.currentTimeMillis())); }
    public void reviewNote(long id,boolean remembered) {
        Note n=getNote(id); if(n==null) return;
        n.reviewLevel=remembered ? Math.min(8,n.reviewLevel+1) : 0;
        long days=remembered ? (1L << Math.max(0,n.reviewLevel-1)) : 1;
        n.reviewAt=System.currentTimeMillis()+days*86400000L; saveNote(n);
    }
    public Flashcard createFlashcard(long noteId,long frontRecordId,long backRecordId) {
        requireEditable(noteId);SQLiteDatabase db=getWritableDatabase();
        if(frontRecordId==backRecordId)throw new IllegalArgumentException("Choose different question and answer records");
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM records WHERE note_id=? AND id IN (?,?)",new String[]{""+noteId,""+frontRecordId,""+backRecordId})) {
            c.moveToFirst();if(c.getInt(0)!=2)throw new IllegalArgumentException("Flashcard records must belong to this note");
        }
        Flashcard f=new Flashcard();f.noteId=noteId;f.frontRecordId=frontRecordId;f.backRecordId=backRecordId;
        f.id=db.insertOrThrow("flashcards",null,flashValues(f));return f;
    }
    public List<Flashcard> listFlashcards(boolean dueOnly) {
        List<Flashcard> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT f.* FROM flashcards f JOIN notes n ON n.id=f.note_id WHERE n.deleted=0 AND n.archived=0"+(dueOnly?" AND f.due_at<=?":"")+" ORDER BY f.due_at,f.id",dueOnly?args(System.currentTimeMillis()):null)) {
            while(c.moveToNext())result.add(readFlashcard(c));
        }return result;
    }
    public void reviewFlashcard(long id,boolean remembered) {
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT * FROM flashcards WHERE id=?",args(id))) {
            if(!c.moveToFirst())return;Flashcard f=readFlashcard(c);f.level=remembered?Math.min(8,f.level+1):0;
            f.dueAt=System.currentTimeMillis()+(remembered?(1L<<Math.max(0,f.level-1)):1)*86400000L;
            db.update("flashcards",flashValues(f),"id=?",args(id));
        }
    }
    public void deleteFlashcard(long id){getWritableDatabase().delete("flashcards","id=?",args(id));}
    private static ContentValues flashValues(Flashcard f){ContentValues v=new ContentValues();v.put("note_id",f.noteId);v.put("front_record",f.frontRecordId);v.put("back_record",f.backRecordId);v.put("due_at",f.dueAt);v.put("level",f.level);return v;}
    private static Flashcard readFlashcard(Cursor c){Flashcard f=new Flashcard();f.id=number(c,"id");f.noteId=number(c,"note_id");f.frontRecordId=number(c,"front_record");f.backRecordId=number(c,"back_record");f.dueAt=number(c,"due_at");f.level=(int)number(c,"level");return f;}
    private void validateImages(List<String> images) {
        try {for(String path:images){File f=new File(path).getCanonicalFile();if(!f.toPath().startsWith(filesDir.getCanonicalFile().toPath())||!f.isFile()||f.length()>25*1024*1024)throw new IllegalArgumentException("Invalid attachment");}}
        catch(IOException e){throw new IllegalArgumentException("Invalid attachment",e);}
    }
    private void cleanupUnreferencedImages() {
        // Only owned attachment directories are eligible. Shared image references survive.
        try {
            Set<String> used=new HashSet<>();
            try(Cursor c=getReadableDatabase().rawQuery("SELECT images FROM records",null)){while(c.moveToNext()){JSONArray a=new JSONArray(c.getString(0));for(int i=0;i<a.length();i++)used.add(new File(a.getString(i)).getCanonicalPath());}}
            for(String dir:new String[]{"images","imported-images"}){
                File directory=new File(filesDir,dir).getCanonicalFile();if(!directory.toPath().startsWith(filesDir.getCanonicalFile().toPath()))continue;
                File[] candidates=directory.listFiles();if(candidates!=null)for(File file:candidates)if(file.isFile()&&!used.contains(file.getCanonicalPath())&&file.getCanonicalFile().getParentFile().equals(directory))file.delete();
            }
        }catch(IOException|JSONException e){/* Preserve files if references cannot be read safely. */}
    }
    private static ContentValues noteValues(Note n) {
        ContentValues v=new ContentValues();v.put("title",safe(n.title));v.put("color",safe(n.color));v.put("created",n.createdAt);v.put("updated",n.updatedAt);
        v.put("pinned",n.pinned);v.put("starred",n.starred);v.put("archived",n.archived);v.put("deleted",n.deleted);v.put("readonly",n.readOnly);v.put("review_at",n.reviewAt);v.put("review_level",n.reviewLevel);v.put("folded",n.folded);v.put("sort_order","newest".equals(n.sortOrder)?"newest":"oldest");return v;
    }
    private static ContentValues recordValues(Record r) {
        ContentValues v=new ContentValues();v.put("note_id",r.noteId);v.put("text",safe(r.text));v.put("html",safe(r.html));v.put("todo",r.todo);v.put("done",r.done);v.put("created",r.createdAt);v.put("updated",r.updatedAt);v.put("images",new JSONArray(r.images).toString());v.put("folded",r.folded);v.put("color_type",r.colorType);return v;
    }
    private static ContentValues bookValues(Book b) { ContentValues v=new ContentValues();v.put("name",b.name);v.put("color",b.color);v.put("cover",b.cover);v.put("icon",b.icon);v.put("created",b.createdAt);return v; }
    private static Note readNote(Cursor c) {
        Note n=new Note();n.id=number(c,"id");n.title=str(c,"title");n.color=str(c,"color");n.createdAt=number(c,"created");n.updatedAt=number(c,"updated");
        n.pinned=flag(c,"pinned");n.starred=flag(c,"starred");n.archived=flag(c,"archived");n.deleted=flag(c,"deleted");n.readOnly=flag(c,"readonly");n.reviewAt=number(c,"review_at");n.reviewLevel=(int)number(c,"review_level");n.folded=flag(c,"folded");n.sortOrder=str(c,"sort_order");return n;
    }
    private static void touch(SQLiteDatabase db,long id) { ContentValues v=new ContentValues();v.put("updated",System.currentTimeMillis());db.update("notes",v,"id=?",args(id)); }
    private static String safe(String value) { return value==null ? "" : value; }
    private static String[] args(long value) { return new String[]{Long.toString(value)}; }
    private static long number(Cursor c,String key) { return c.getLong(c.getColumnIndexOrThrow(key)); }
    private static String str(Cursor c,String key) { return c.getString(c.getColumnIndexOrThrow(key)); }
    private static boolean flag(Cursor c,String key) { return number(c,key)!=0; }

    /** Self-contained backup, including attachment bytes; excludes external/unreadable paths. */
    public String exportJson() {
        try {
            JSONObject root=new JSONObject().put("format","personal-inote").put("version",2);
            JSONArray books=new JSONArray(),notes=new JSONArray(); JSONObject assets=new JSONObject();long attachmentBytes=0;
            for(Book b:listBooks()) books.put(new JSONObject().put("id",b.id).put("name",b.name).put("color",b.color).put("cover",b.cover).put("icon",b.icon).put("created",b.createdAt));
            for(Note n:queryNotes("SELECT * FROM notes ORDER BY id",null)) {
                JSONObject j=new JSONObject().put("id",n.id).put("title",n.title).put("color",n.color).put("created",n.createdAt).put("updated",n.updatedAt)
                    .put("pinned",n.pinned).put("starred",n.starred).put("archived",n.archived).put("deleted",n.deleted).put("readonly",n.readOnly)
                    .put("review_at",n.reviewAt).put("review_level",n.reviewLevel).put("books",new JSONArray(n.bookIds)).put("folded",n.folded).put("sort_order",n.sortOrder);
                JSONArray records=new JSONArray();
                for(Record r:n.records) {
                    JSONArray images=new JSONArray();
                    for(String path:r.images) {
                        File file=new File(path).getCanonicalFile();
                        if(!file.toPath().startsWith(filesDir.getCanonicalFile().toPath()) || !file.isFile()) throw new IOException("Attachment unavailable: "+file.getName());
                        if(file.length()>25*1024*1024)throw new IOException("Attachment exceeds 25 MB limit");
                        attachmentBytes+=file.length();if(attachmentBytes>64*1024*1024)throw new IOException("Backup attachments exceed 64 MB limit");
                        String key="image-"+assets.length(); assets.put(key,Base64.encodeToString(Files.readAllBytes(file.toPath()),Base64.NO_WRAP));images.put(key);
                    }
                    records.put(new JSONObject().put("id",r.id).put("text",r.text).put("html",r.html).put("todo",r.todo).put("done",r.done).put("created",r.createdAt).put("updated",r.updatedAt).put("images",images).put("folded",r.folded).put("color_type",r.colorType));
                }
                j.put("records",records);notes.put(j);
            }
            JSONArray cards=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM flashcards ORDER BY id",null)){while(c.moveToNext()){Flashcard f=readFlashcard(c);cards.put(new JSONObject().put("id",f.id).put("note_id",f.noteId).put("front_record",f.frontRecordId).put("back_record",f.backRecordId).put("due_at",f.dueAt).put("level",f.level));}}
            String result=root.put("books",books).put("notes",notes).put("assets",assets).put("flashcards",cards).toString(2);
            if(result.length()>100*1024*1024)throw new IOException("Backup exceeds 100 MB limit");return result;
        } catch(JSONException|IOException e) { throw new IllegalStateException("Backup export failed",e); }
    }

    /** Validates everything first, then merges with new IDs; never replaces existing user data. */
    public void importJson(String json) {
        List<File> written=new ArrayList<>(); boolean committed=false;
        try {
            if(json==null || json.length()>100*1024*1024) throw new JSONException("Backup exceeds 100 MB limit");
            JSONObject root=new JSONObject(json);
            if(!"personal-inote".equals(root.getString("format")) || (root.getInt("version")!=1&&root.getInt("version")!=2)) throw new JSONException("Unsupported backup format");
            validateBackupTypes(root);
            JSONArray jb=root.getJSONArray("books"), jn=root.getJSONArray("notes");JSONObject assets=root.getJSONObject("assets");
            List<Book> books=new ArrayList<>(); List<Note> notes=new ArrayList<>();Set<Long> bookIds=new HashSet<>(),noteIds=new HashSet<>(),recordIds=new HashSet<>();
            List<Flashcard> cards=new ArrayList<>();Map<Long,Long> recordOwners=new HashMap<>();Map<String,byte[]> decoded=new HashMap<>(); long total=0;
            java.util.Iterator<String> keys=assets.keys();while(keys.hasNext()) {
                String key=keys.next();byte[] data=Base64.decode(assets.getString(key),Base64.DEFAULT);total+=data.length;
                if(data.length>25*1024*1024||total>64*1024*1024) throw new JSONException("Attachments exceed size limit");decoded.put(key,data);
            }
            for(int i=0;i<jb.length();i++) {
                JSONObject j=jb.getJSONObject(i);Book b=new Book();b.id=positive(j,"id");b.name=j.getString("name");b.color=j.getString("color");b.cover=j.optString("cover","");b.icon=j.optString("icon","");b.createdAt=nonnegative(j,"created");
                if(b.name.trim().isEmpty() || !bookIds.add(b.id)) throw new JSONException("Invalid or duplicate book");books.add(b);
            }
            for(int i=0;i<jn.length();i++) {
                JSONObject j=jn.getJSONObject(i);Note n=new Note();n.id=positive(j,"id");if(!noteIds.add(n.id))throw new JSONException("Duplicate note");
                n.title=j.getString("title");n.color=j.getString("color");n.createdAt=nonnegative(j,"created");n.updatedAt=nonnegative(j,"updated");
                n.pinned=j.getBoolean("pinned");n.starred=j.getBoolean("starred");n.archived=j.getBoolean("archived");n.deleted=j.getBoolean("deleted");n.readOnly=j.getBoolean("readonly");
                n.reviewAt=nonnegative(j,"review_at");n.reviewLevel=j.getInt("review_level");if(n.reviewLevel<0 || n.reviewLevel>8) throw new JSONException("Invalid review level");
                n.folded=j.optBoolean("folded",false);n.sortOrder=j.optString("sort_order","oldest");if(!"oldest".equals(n.sortOrder)&&!"newest".equals(n.sortOrder))throw new JSONException("Invalid sort order");
                JSONArray refs=j.getJSONArray("books");for(int k=0;k<refs.length();k++){long id=refs.getLong(k);if(!bookIds.contains(id))throw new JSONException("Missing book");n.bookIds.add(id);}
                JSONArray rr=j.getJSONArray("records");for(int k=0;k<rr.length();k++) {
                    JSONObject x=rr.getJSONObject(k);Record r=new Record();r.id=positive(x,"id");if(!recordIds.add(r.id))throw new JSONException("Duplicate record");
                    r.text=x.getString("text");r.html=x.getString("html");r.todo=x.getBoolean("todo");r.done=x.getBoolean("done");r.createdAt=nonnegative(x,"created");r.updatedAt=nonnegative(x,"updated");
                    r.folded=x.optBoolean("folded",false);recordOwners.put(r.id,n.id);
                    r.colorType=x.optInt("color_type",0);
                    JSONArray ii=x.getJSONArray("images");for(int p=0;p<ii.length();p++){String key=ii.getString(p);if(!decoded.containsKey(key))throw new JSONException("Missing attachment");r.images.add(key);}n.records.add(r);
                }notes.add(n);
            }
            JSONArray fc=root.optJSONArray("flashcards");Set<Long> cardIds=new HashSet<>();Set<String> pairs=new HashSet<>();if(fc!=null)for(int i=0;i<fc.length();i++){
                JSONObject x=fc.getJSONObject(i);Flashcard f=new Flashcard();f.id=positive(x,"id");f.noteId=positive(x,"note_id");f.frontRecordId=positive(x,"front_record");f.backRecordId=positive(x,"back_record");f.dueAt=nonnegative(x,"due_at");f.level=x.getInt("level");
                if(!cardIds.add(f.id)||!pairs.add(f.frontRecordId+":"+f.backRecordId)||f.level<0||f.level>8||f.frontRecordId==f.backRecordId||!Long.valueOf(f.noteId).equals(recordOwners.get(f.frontRecordId))||!Long.valueOf(f.noteId).equals(recordOwners.get(f.backRecordId)))throw new JSONException("Invalid flashcard references");cards.add(f);
            }
            Map<String,String> paths=new HashMap<>();File directory=new File(filesDir,"imported-images");
            if(!decoded.isEmpty() && !directory.isDirectory() && !directory.mkdirs())throw new IOException("Cannot create image directory");
            for(Map.Entry<String,byte[]> entry:decoded.entrySet()){File file=File.createTempFile("image-",".bin",directory);written.add(file);Files.write(file.toPath(),entry.getValue());paths.put(entry.getKey(),file.getAbsolutePath());}
            SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
            try {
                Map<Long,Long> mapped=new HashMap<>(),mappedNotes=new HashMap<>(),mappedRecords=new HashMap<>();for(Book b:books)mapped.put(b.id,db.insertOrThrow("books",null,bookValues(b)));
                for(Note n:notes) {
                    long id=db.insertOrThrow("notes",null,noteValues(n));mappedNotes.put(n.id,id);List<Long> ids=new ArrayList<>();for(Long old:n.bookIds)ids.add(mapped.get(old));replaceBooks(db,id,ids);
                    for(Record r:n.records){r.noteId=id;List<String> images=new ArrayList<>();for(String key:r.images)images.add(paths.get(key));r.images=images;mappedRecords.put(r.id,db.insertOrThrow("records",null,recordValues(r)));}
                }
                for(Flashcard f:cards){f.noteId=mappedNotes.get(f.noteId);f.frontRecordId=mappedRecords.get(f.frontRecordId);f.backRecordId=mappedRecords.get(f.backRecordId);db.insertOrThrow("flashcards",null,flashValues(f));}
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            committed=true;
        } catch(JSONException|IOException|IllegalArgumentException e) { throw new IllegalArgumentException("Invalid backup; existing notes preserved",e); }
        finally { if(!committed)for(File file:written)file.delete(); }
    }
    private static void validateBackupTypes(JSONObject root)throws JSONException {
        typed(root,"format",String.class);integer(root,"version");
        JSONArray books=root.getJSONArray("books"),notes=root.getJSONArray("notes");
        if(books.length()>10000||notes.length()>100000)throw new JSONException("Too many items");
        for(int i=0;i<books.length();i++){JSONObject b=books.getJSONObject(i);integer(b,"id");integer(b,"created");typed(b,"name",String.class);typed(b,"color",String.class);if(b.has("cover"))typed(b,"cover",String.class);if(b.has("icon"))typed(b,"icon",String.class);}
        for(int i=0;i<notes.length();i++){
            JSONObject n=notes.getJSONObject(i);for(String k:new String[]{"id","created","updated","review_at","review_level"})integer(n,k);
            for(String k:new String[]{"title","color"})typed(n,k,String.class);
            for(String k:new String[]{"pinned","starred","archived","deleted","readonly"})typed(n,k,Boolean.class);
            if(n.has("folded"))typed(n,"folded",Boolean.class);if(n.has("sort_order"))typed(n,"sort_order",String.class);
            JSONArray refs=n.getJSONArray("books");for(int j=0;j<refs.length();j++)if(!(refs.get(j) instanceof Integer)&&!(refs.get(j) instanceof Long))throw new JSONException("Invalid book reference type");
            JSONArray records=n.getJSONArray("records");if(records.length()>100000)throw new JSONException("Too many records");
            for(int j=0;j<records.length();j++){
                JSONObject r=records.getJSONObject(j);for(String k:new String[]{"id","created","updated"})integer(r,k);
                for(String k:new String[]{"text","html"})typed(r,k,String.class);for(String k:new String[]{"todo","done"})typed(r,k,Boolean.class);
                if(r.has("folded"))typed(r,"folded",Boolean.class);JSONArray images=r.getJSONArray("images");
                if(r.has("color_type")){integer(r,"color_type");long color=r.getLong("color_type");if(color<Integer.MIN_VALUE||color>Integer.MAX_VALUE)throw new JSONException("Color type out of range");}
                for(int p=0;p<images.length();p++)if(!(images.get(p) instanceof String))throw new JSONException("Invalid image reference type");
            }
        }
        JSONObject assets=root.getJSONObject("assets");java.util.Iterator<String> keys=assets.keys();while(keys.hasNext())typed(assets,keys.next(),String.class);
        if(root.has("flashcards")){JSONArray cards=root.getJSONArray("flashcards");for(int i=0;i<cards.length();i++){JSONObject f=cards.getJSONObject(i);for(String k:new String[]{"id","note_id","front_record","back_record","due_at","level"})integer(f,k);}}
        else if(root.getInt("version")==2)throw new JSONException("Missing flashcards");
    }
    private static void typed(JSONObject j,String key,Class<?> type)throws JSONException {if(!type.isInstance(j.get(key)))throw new JSONException("Invalid type: "+key);}
    private static void integer(JSONObject j,String key)throws JSONException {Object v=j.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw new JSONException("Invalid integer: "+key);if(("version".equals(key)||"review_level".equals(key)||"level".equals(key))&&(((Number)v).longValue()>Integer.MAX_VALUE||((Number)v).longValue()<Integer.MIN_VALUE))throw new JSONException("Integer out of range: "+key);}
    private static long nonnegative(JSONObject j,String key)throws JSONException {long n=j.getLong(key);if(n<0)throw new JSONException("Invalid "+key);return n;}
    private static long positive(JSONObject j,String key)throws JSONException {long n=nonnegative(j,key);if(n==0)throw new JSONException("Invalid "+key);return n;}
}
