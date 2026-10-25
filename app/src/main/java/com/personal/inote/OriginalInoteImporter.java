package com.personal.inote;

import android.database.sqlite.SQLiteDatabase;
import android.text.SpannableStringBuilder;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Offline v1 compatibility, verified against twelve original bundled archives. */
public final class OriginalInoteImporter {
    private static final int MAX_ARCHIVE=16*1024*1024, MAX_ENTRY=4*1024*1024, MAX_ENTRIES=1024;
    private static final String PREFIX="com.chii.inote.importable.";
    private static final Set<String> RUN_KEYS=new HashSet<>(Arrays.asList("s_t","s_dt","a_f","a_b","a_u","a_st","a_l","a_tcl","a_tbl","a_tnl","a_tqblk"));
    private OriginalInoteImporter(){}
    public static long importBytes(NoteStore store,byte[] bytes) throws Exception {
        if(BuildConfig.IMPORT_KEY.isEmpty())throw invalid("公开构建未配置原版格式解码参数，请导入本应用的 JSON 备份");
        return importBytes(store,bytes,BuildConfig.IMPORT_KEY);
    }
    static long importBytes(NoteStore store,byte[] bytes,String compatibilityKey) throws Exception {
        if(bytes==null||bytes.length==0||bytes.length>MAX_ARCHIVE||bytes.length%16!=0)throw invalid("文件大小或加密格式无效");
        byte[] key=Arrays.copyOf(compatibilityKey.getBytes(StandardCharsets.UTF_8),32);
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(new byte[16]));
        byte[] zip;
        try{zip=cipher.doFinal(bytes);}catch(Exception e){throw invalid("无法解码此 iNote 文件；仅支持已验证的原版 v1 格式");}
        Map<String,byte[]> files=readZip(zip);
        if(files.containsKey("Note/")&&files.get("Note/").length!=0)throw invalid("ZIP 目录不能包含数据");
        JSONObject info=new JSONObject(utf8(required(files,"importable_info.json")));
        if(!"v1".equals(info.optString(PREFIX+"file.version")))throw invalid("尚不支持此 iNote 版本");
        JSONArray docs=info.optJSONArray(PREFIX+"doc.tokens"),old=info.optJSONArray(PREFIX+"record.tokens");
        if(docs!=null&&old!=null&&!docs.toString().equals(old.toString()))throw invalid("笔记顺序字段冲突");
        JSONArray tokens=docs==null?old:docs;
        if(tokens==null||tokens.length()==0||tokens.length()>MAX_ENTRIES-2)throw invalid("缺少有效小笔记列表");
        Set<String> expected=new HashSet<>(Arrays.asList("importable_info.json","Note/title","Note/"));
        Set<String> used=new HashSet<>();List<SpannableStringBuilder> rendered=new ArrayList<>();
        for(int i=0;i<tokens.length();i++){
            String token=tokens.getString(i);
            if(!token.matches("[A-Za-z0-9_-]{1,100}")||!used.add(token))throw invalid("小笔记标识无效或重复");
            String path="Note/"+token+"_v1";expected.add(path);
            JSONArray runs=new JSONArray(utf8(required(files,path)));
            validateRuns(runs);
            rendered.add(TemplateLibrary.render(new JSONObject().put("runs",runs)));
        }
        for(String path:files.keySet())if(!expected.contains(path))throw invalid("文件包含尚未支持的附件或数据："+path);
        String title=files.containsKey("Note/title")?utf8(files.get("Note/title")):"";
        if(title.length()>10000)throw invalid("笔记标题过长");
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            NoteStore.Note note=store.createNote(title);
            long created=System.currentTimeMillis();
            for(int i=0;i<rendered.size();i++){
                SpannableStringBuilder text=rendered.get(i);
                NoteStore.Record record=store.addRecord(note.id,text.toString());
                record.createdAt=created+i;record.html=RichText.encode(text);
                store.saveRecord(record);
            }
            db.setTransactionSuccessful();return note.id;
        }finally{db.endTransaction();}
    }
    private static void validateRuns(JSONArray runs)throws Exception{
        if(runs.length()>50000)throw invalid("小笔记片段过多");
        for(int i=0;i<runs.length();i++){
            JSONObject r=runs.getJSONObject(i);
            for(Iterator<String> it=r.keys();it.hasNext();){String k=it.next();if(!RUN_KEYS.contains(k))throw invalid("尚不支持的富文本属性："+k);}
            if(!r.has("s_t")&&!r.has("s_dt"))throw invalid("富文本片段缺少内容");
            if(r.has("s_t")&&!(r.get("s_t") instanceof String))throw invalid("文本字段类型无效");
            for(String k:new String[]{"a_f","a_b","a_tcl","a_tbl","a_tnl","a_tqblk","s_dt"})if(r.has(k)&&!(r.get(k) instanceof JSONObject))throw invalid("富文本属性类型无效："+k);
            for(String k:new String[]{"a_u","a_st"})if(r.has(k)&&!(r.get(k) instanceof Number))throw invalid("富文本属性类型无效："+k);
            if(r.has("a_l")&&!(r.get("a_l") instanceof String))throw invalid("链接格式尚不支持");
            for(String k:new String[]{"a_tcl","a_tbl","a_tnl","a_tqblk"})if(r.has(k)&&r.getJSONObject(k).optString("t").isEmpty())throw invalid("列表或引用标识缺失");
        }
    }
    private static byte[] required(Map<String,byte[]> files,String name){byte[] b=files.get(name);if(b==null)throw invalid("缺少文件："+name);return b;}
    private static String utf8(byte[] data)throws CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
    private static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private static long u32(byte[] b,int p){return ((long)u16(b,p))|((long)u16(b,p+2)<<16);}
    private static Map<String,byte[]> readZip(byte[] bytes)throws Exception{
        if(bytes.length<22||bytes.length>MAX_ARCHIVE)throw invalid("ZIP 大小无效");
        int eocd=-1;
        for(int p=bytes.length-22;p>=Math.max(0,bytes.length-65557);p--)if(u32(bytes,p)==0x06054b50L&&p+22+u16(bytes,p+20)==bytes.length){eocd=p;break;}
        if(eocd<0||u16(bytes,eocd+4)!=0||u16(bytes,eocd+6)!=0)throw invalid("ZIP 目录无效");
        int count=u16(bytes,eocd+10);long offset=u32(bytes,eocd+16),size=u32(bytes,eocd+12);
        if(count==0||count>MAX_ENTRIES||u16(bytes,eocd+8)!=count||offset+size!=eocd)throw invalid("ZIP 目录或条目数无效");
        Map<String,long[]> metadata=new LinkedHashMap<>();int pos=(int)offset;
        long total=0;
        for(int i=0;i<count;i++){
            if(pos<0||pos+46>eocd||u32(bytes,pos)!=0x02014b50L)throw invalid("ZIP 中央目录损坏");
            int nameLength=u16(bytes,pos+28),extra=u16(bytes,pos+30),comment=u16(bytes,pos+32),next=pos+46+nameLength+extra+comment;
            if(next>eocd||(u16(bytes,pos+8)&1)!=0||u16(bytes,pos+34)!=0)throw invalid("ZIP 条目格式无效");
            int method=u16(bytes,pos+10);if(method!=0&&method!=8)throw invalid("ZIP 压缩方法不支持");
            String name=utf8(Arrays.copyOfRange(bytes,pos+46,pos+46+nameLength));
            if(name.isEmpty()||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.contains("\u0000")||Arrays.asList(name.split("/",-1)).contains("..")||name.contains("//"))throw invalid("ZIP 路径无效");
            long plain=u32(bytes,pos+24),packed=u32(bytes,pos+20),local=u32(bytes,pos+42);total+=plain;
            if(plain>MAX_ENTRY||total>MAX_ARCHIVE||packed>MAX_ARCHIVE||local>=offset||metadata.containsKey(name))throw invalid("ZIP 重复条目或大小超限");
            metadata.put(name,new long[]{u32(bytes,pos+16),plain,method});pos=next;
        }
        if(pos!=eocd)throw invalid("ZIP 目录长度不符");
        Map<String,byte[]> files=new LinkedHashMap<>();total=0;
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=input.getNextEntry())!=null){
                long[] expected=metadata.get(entry.getName());
                if(expected==null||files.containsKey(entry.getName())||files.size()>=MAX_ENTRIES||entry.getMethod()!=expected[2])throw invalid("ZIP 本地目录不匹配");
                ByteArrayOutputStream output=new ByteArrayOutputStream();CRC32 crc=new CRC32();int n;
                while((n=input.read(buffer))!=-1){total+=n;if(output.size()+n>MAX_ENTRY||total>MAX_ARCHIVE)throw invalid("ZIP 解压大小超限");output.write(buffer,0,n);crc.update(buffer,0,n);}
                input.closeEntry();
                if(output.size()!=expected[1]||crc.getValue()!=expected[0])throw invalid("ZIP CRC 或大小校验失败");
                files.put(entry.getName(),output.toByteArray());
            }
        }
        if(files.size()!=metadata.size())throw invalid("ZIP 文件条目不完整");return files;
    }
}
