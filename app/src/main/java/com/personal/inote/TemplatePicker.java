package com.personal.inote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.*;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/** Paper-thumbnail browser based on the packaged template guide. */
public final class TemplatePicker {
    public static void show(Activity activity,boolean dark,Consumer<JSONObject> selected){
        try{new TemplatePicker(activity,dark,selected).render();}catch(Exception e){Toast.makeText(activity,"无法加载模板："+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private final Activity activity;
    private final boolean dark;
    private final Consumer<JSONObject> selected;
    private final JSONArray templates;
    private final Dialog dialog;
    private String language="hans",category="全部";
    private TemplatePicker(Activity a,boolean d,Consumer<JSONObject> callback)throws Exception{
        activity=a;dark=d;selected=callback;templates=TemplateLibrary.load(a);dialog=new Dialog(a,android.R.style.Theme_Material_Light_NoActionBar);
    }
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String text,int size,int color){TextView t=new TextView(activity);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private void render(){
        int bg=dark?0xff242424:0xfff8f5f5,paper=dark?0xff303030:Color.WHITE,ink=dark?0xffeeeeee:0xff383838,muted=dark?0xff999999:0xffaaaaaa;
        LinearLayout root=column();root.setBackgroundColor(bg);LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=label("‹",30,muted);back.setContentDescription("关闭模板");back.setGravity(Gravity.CENTER);header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));back.setOnClickListener(v->dialog.dismiss());
        TextView title=label("模板",17,ink);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView lang=label(language.equals("hans")?"简体中文⌄":language.equals("hant")?"繁體中文⌄":"English⌄",12,muted);lang.setGravity(Gravity.CENTER);header.addView(lang,new LinearLayout.LayoutParams(dp(88),dp(48)));
        lang.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("模板语言").setItems(new String[]{"简体中文","繁體中文","English"},(d,w)->{language=new String[]{"hans","hant","en"}[w];category="全部";render();}).show());root.addView(header);
        LinkedHashSet<String> categories=new LinkedHashSet<>();categories.add("全部");for(int i=0;i<templates.length();i++){JSONObject t=templates.optJSONObject(i);if(t!=null&&language.equals(t.optString("language")))categories.add(t.optString("category"));}
        HorizontalScrollView hs=new HorizontalScrollView(activity);hs.setHorizontalScrollBarEnabled(false);LinearLayout tabs=new LinearLayout(activity);
        for(String name:categories){TextView tab=label(name,13,name.equals(category)?ink:muted);tab.setContentDescription("模板分类："+name);tab.setGravity(Gravity.CENTER);tab.setPadding(dp(12),0,dp(12),0);if(name.equals(category))tab.setTypeface(null,Typeface.BOLD);tabs.addView(tab,new LinearLayout.LayoutParams(-2,dp(38)));tab.setOnClickListener(v->{category=name;render();});}hs.addView(tabs);root.addView(hs);
        ScrollView scroll=new ScrollView(activity);LinearLayout columns=new LinearLayout(activity);columns.setGravity(Gravity.TOP);columns.setPadding(dp(10),dp(8),dp(10),dp(12));LinearLayout[] sides={column(),column()};
        for(int i=0;i<2;i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i==0)p.rightMargin=dp(5);else p.leftMargin=dp(5);columns.addView(sides[i],p);}int index=0;
        for(int i=0;i<templates.length();i++){JSONObject t=templates.optJSONObject(i);if(t==null||!language.equals(t.optString("language"))||!category.equals("全部")&&!category.equals(t.optString("category")))continue;
            LinearLayout card=column();card.setContentDescription(t.optString("title"));card.setPadding(dp(10),dp(10),dp(10),dp(10));GradientDrawable shape=new GradientDrawable();shape.setColor(paper);shape.setCornerRadius(dp(2));card.setBackground(shape);card.setElevation(dp(.5f));
            TextView name=label(t.optString("title"),13,ink);name.setTypeface(null,Typeface.BOLD);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);card.addView(name);
            SpannableStringBuilder body=new SpannableStringBuilder();JSONArray outlines=t.optJSONArray("outlines");if(outlines!=null)for(int j=0;j<outlines.length();j++){if(j>0)body.append("\n\n");body.append(TemplateLibrary.render(outlines.optJSONObject(j)));}
            LinedTextView preview=new LinedTextView(activity);preview.setTextSize(12);preview.setTextColor(ink);preview.setDarkMode(dark);PaperChecks.apply(body);preview.setText(body);preview.setLineSpacing(dp(3),1);preview.setMaxLines(9);preview.setEllipsize(TextUtils.TruncateAt.END);preview.setPadding(0,dp(8),0,0);card.addView(preview,new LinearLayout.LayoutParams(-1,dp(166)));
            TextView kind=label(t.optString("category"),10,muted);kind.setPadding(0,dp(8),0,0);card.addView(kind);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);sides[index++%2].addView(card,p);card.setOnClickListener(v->{dialog.dismiss();selected.accept(t);});
        }
        scroll.addView(columns);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setStatusBarColor(bg);dialog.getWindow().setNavigationBarColor(bg);dialog.getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(android.os.Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});root.requestApplyInsets();}
    }
}
