package com.personal.inote;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/** Files and databases stay inside a test-only cache directory. No mock framework. */
final class TestContexts {
    static Context create(Context target,String name){
        File root=new File(target.getCacheDir(),name);root.mkdirs();
        return new ContextWrapper(target){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){return root;}
            @Override public File getDatabasePath(String name){return new File(root,name);}
            @Override public boolean deleteDatabase(String name){return SQLiteDatabase.deleteDatabase(getDatabasePath(name));}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
        };
    }
    static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
    private TestContexts(){}
}
