package com.personal.inote;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** A read-only, individually granted view of generated export images, never app data. */
public final class ExportShareProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{
        if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".exports").equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new FileNotFoundException("Invalid export URI");
        String name=uri.getLastPathSegment();if(name==null||!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png"))throw new FileNotFoundException("Invalid export name");
        try{File directory=new File(getContext().getCacheDir(),"export-shares").getCanonicalFile();File file=new File(directory,name).getCanonicalFile();if(!directory.equals(file.getParentFile())||!file.isFile())throw new FileNotFoundException("Export no longer available");return file;}catch(IOException e){throw new FileNotFoundException("Export no longer available");}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read-only export");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){return "image/png";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor result=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"灵感笔记.png":OpenableColumns.SIZE.equals(columns[i])?f.length():null;result.addRow(values);return result;}catch(FileNotFoundException e){return null;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only export");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only export");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read-only export");}
}
