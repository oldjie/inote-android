package com.personal.inote;
import android.text.*;
import android.text.style.*;
import org.json.*;

/** Versioned local span encoding, never claimed to be the original iOS format. */
public final class RichText {
    public static String encode(Spanned text){
        try{JSONArray spans=new JSONArray();for(CharacterStyle span:text.getSpans(0,text.length(),CharacterStyle.class)){
            String type=null;int value=0;
            if(span instanceof StyleSpan){type="style";value=((StyleSpan)span).getStyle();}
            else if(span instanceof UnderlineSpan)type="underline";
            else if(span instanceof StrikethroughSpan)type="strike";
            else if(span instanceof BackgroundColorSpan){type="background";value=((BackgroundColorSpan)span).getBackgroundColor();}
            else if(span instanceof TypefaceSpan)type="code";
            else if(span instanceof URLSpan)type="link";
            if(type!=null)spans.put(new JSONObject().put("type",type).put("start",text.getSpanStart(span)).put("end",text.getSpanEnd(span)).put("value",value).put("url",span instanceof URLSpan?((URLSpan)span).getURL():""));
        }return new JSONObject().put("format","inote-spans-1").put("text",text.toString()).put("spans",spans).toString();}catch(JSONException e){throw new IllegalStateException(e);}
    }
    public static CharSequence decode(String encoded,String plain){
        if(encoded==null||encoded.isEmpty())return plain;
        if(!encoded.startsWith("{")){ SpannableString safe=new SpannableString(Html.fromHtml(encoded,Html.FROM_HTML_MODE_COMPACT)); for(URLSpan link:safe.getSpans(0,safe.length(),URLSpan.class)) if(!safeLink(link.getURL()))safe.removeSpan(link); return safe; }
        try{JSONObject root=new JSONObject(encoded);if(!root.optString("format").equals("inote-spans-1"))return plain;SpannableString result=new SpannableString(root.getString("text"));JSONArray spans=root.getJSONArray("spans");for(int i=0;i<spans.length();i++){JSONObject j=spans.getJSONObject(i);Object span=null;switch(j.getString("type")){case "style":span=new StyleSpan(j.getInt("value"));break;case "underline":span=new UnderlineSpan();break;case "strike":span=new StrikethroughSpan();break;case "background":span=new ContrastHighlightSpan(j.getInt("value"));break;case "code":span=new TypefaceSpan("monospace");break;case "link":String url=j.optString("url","");if(safeLink(url))span=new URLSpan(url);break;}int a=j.getInt("start"),b=j.getInt("end");if(span!=null&&a>=0&&b<=result.length()&&a<b)result.setSpan(span,a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return result;}catch(JSONException e){return plain;}
    }
    private static boolean safeLink(String url){return url!=null&&(url.startsWith("https://")||url.startsWith("http://")||url.startsWith("mailto:"));}
    private RichText(){}
}
