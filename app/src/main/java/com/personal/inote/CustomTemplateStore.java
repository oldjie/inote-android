package com.personal.inote;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Private, text-only snapshots. Saving a template never changes the source note. */
public final class CustomTemplateStore {
    private static final int MAX_BYTES=2*1024*1024,MAX_ITEMS=200;
    private static AtomicFile file(Context context){return new AtomicFile(new File(context.getFilesDir(),"custom-templates.json"));}
    public static String save(Context context,NoteStore.Note note,String title)throws IOException,JSONException{return saveFromNote(context,note,title);}
    public static synchronized JSONArray load(Context context)throws IOException,JSONException{
        AtomicFile f=file(context);if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return new JSONArray();
        if(f.getBaseFile().length()>MAX_BYTES)throw new IOException("模板文件过大");
        JSONArray all=new JSONArray(new String(f.readFully(),StandardCharsets.UTF_8));
        if(all.length()>MAX_ITEMS)throw new IOException("模板数量超出限制");return all;
    }
    public static synchronized String saveFromNote(Context context,NoteStore.Note note,String title)throws IOException,JSONException{
        if(note==null||note.deleted)throw new IllegalArgumentException("笔记不存在或已删除");
        title=title==null?"":title.trim();if(title.isEmpty()||title.length()>80)throw new IllegalArgumentException("模板名称须为 1 至 80 个字符");
        JSONArray all=load(context);if(all.length()>=MAX_ITEMS)throw new IOException("最多保存 200 个自定义模板");
        JSONArray outlines=new JSONArray();
        for(NoteStore.Record record:note.records){
            if(record.text.isEmpty()&&record.html.isEmpty())continue;
            String text=record.text,rich=record.html;
            if(record.todo){android.text.SpannableStringBuilder formatted=new android.text.SpannableStringBuilder(record.done?"☑ ":"☐ ");formatted.append(RichText.decode(rich,text));text=formatted.toString();rich=RichText.encode(formatted);}
            outlines.put(new JSONObject().put("text",text).put("rich",rich));
        }
        if(outlines.length()==0)throw new IllegalArgumentException("请先添加文字；自定义模板不包含图片附件");
        String id="custom-"+UUID.randomUUID();all.put(new JSONObject().put("id",id).put("language","hans").put("category","我的模板").put("title",title).put("custom",true).put("outlines",outlines));
        write(context,all);return id;
    }
    public static synchronized boolean delete(Context context,String id)throws IOException,JSONException{
        if(id==null||!id.startsWith("custom-"))return false;
        JSONArray all=load(context),kept=new JSONArray();boolean removed=false;
        for(int i=0;i<all.length();i++){JSONObject item=all.getJSONObject(i);if(id.equals(item.optString("id")))removed=true;else kept.put(item);}
        if(removed)write(context,kept);return removed;
    }
    private static void write(Context context,JSONArray all)throws IOException{
        byte[] bytes=all.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("自定义模板总量不能超过 2 MB");
        AtomicFile f=file(context);FileOutputStream out=null;
        try{out=f.startWrite();out.write(bytes);f.finishWrite(out);}catch(IOException e){if(out!=null)f.failWrite(out);throw e;}
    }
    private CustomTemplateStore(){}
}
