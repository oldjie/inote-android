package com.personal.inote;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import junit.framework.TestCase;

public final class RichTextSecurityTest extends TestCase {
    public void testLegacyHtmlRejectsAppAndLocalLinks(){
        for(String url:new String[]{"intent://example","file:///private/example","content://example","javascript:alert(1)","data:text/html,example"}){
            Spanned text=(Spanned)RichText.decode("<a href=\""+url+"\">label</a>","");
            assertTrue(text.toString().contains("label"));assertEquals(url,0,text.getSpans(0,text.length(),URLSpan.class).length);
        }
    }
    public void testJsonRejectsAppAndLocalLinks(){
        for(String url:new String[]{"intent://example","file:///private/example","content://example","javascript:alert(1)"}){
            SpannableString input=new SpannableString("label");input.setSpan(new URLSpan(url),0,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            Spanned output=(Spanned)RichText.decode(RichText.encode(input),"");assertEquals("label",output.toString());assertEquals(0,output.getSpans(0,output.length(),URLSpan.class).length);
        }
    }
    public void testAllowedLinksSurviveBothFormats(){
        for(String url:new String[]{"https://example.org","http://example.org","mailto:example@example.org"}){
            SpannableString input=new SpannableString("label");input.setSpan(new URLSpan(url),0,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            for(String encoded:new String[]{RichText.encode(input),"<a href=\""+url+"\">label</a>"}){
                Spanned output=(Spanned)RichText.decode(encoded,"");assertEquals(1,output.getSpans(0,output.length(),URLSpan.class).length);
            }
        }
    }
}
