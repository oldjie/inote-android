package com.personal.inote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Reads the original packaged blueprint runs; Android display is an explicit adapter. */
public final class TemplateLibrary {
    public static JSONArray load(Context context)throws IOException,JSONException{
        try(InputStream in=context.getAssets().open("templates.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONArray(out.toString("UTF-8"));}
    }
    public static SpannableStringBuilder render(JSONObject outline){
        SpannableStringBuilder result=new SpannableStringBuilder();JSONArray runs=outline.optJSONArray("runs");if(runs==null)return result.append(outline.optString("text"));
        Set<String> groups=new HashSet<>();
        for(int k=0;k<runs.length();k++){JSONObject run=runs.optJSONObject(k);if(run==null)continue;
            JSONObject check=run.optJSONObject("a_tcl"),number=run.optJSONObject("a_tnl"),bullet=run.optJSONObject("a_tbl"),quote=run.optJSONObject("a_tqblk");
            String prefix="";
            if(check!=null&&groups.add("check:"+check.optString("t")))prefix=check.optBoolean("checked")?"☑ ":"☐ ";
            else if(number!=null&&groups.add("number:"+number.optString("t")))prefix=(number.optInt("number",0)+1)+". ";
            else if(bullet!=null&&groups.add("bullet:"+bullet.optString("t")))prefix="• ";
            else if(quote!=null&&groups.add("quote:"+quote.optString("t")))prefix="│ ";
            result.append(prefix);int start=result.length();String text=run.optString("s_t","");JSONObject date=run.optJSONObject("s_dt");if(date!=null){JSONObject info=date.optJSONObject("i");String format=info==null?"yyyy-MM-dd":info.optString("format","yyyy-MM-dd");try{text=new SimpleDateFormat(format,Locale.getDefault()).format(new Date());}catch(IllegalArgumentException e){text="[日期]";}}
            result.append(text);int end=result.length();if(start==end)continue;JSONObject font=run.optJSONObject("a_f");if(font!=null){int style=(font.optBoolean("b")?Typeface.BOLD:0)|(font.optBoolean("i")?Typeface.ITALIC:0);if(style!=0)result.setSpan(new StyleSpan(style),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            if(run.optInt("a_u",0)!=0)result.setSpan(new UnderlineSpan(),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(run.optInt("a_st",0)!=0)result.setSpan(new StrikethroughSpan(),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            String link=run.optString("a_l","");if(link.startsWith("https://")||link.startsWith("http://")||link.startsWith("mailto:"))result.setSpan(new URLSpan(link),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            JSONObject background=run.optJSONObject("a_b");if(background!=null&&!background.optString("h").isEmpty())try{result.setSpan(new ContrastHighlightSpan(Color.parseColor("#"+background.getString("h"))),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}catch(Exception ignored){}
        }return result;
    }
    private TemplateLibrary(){}
}
