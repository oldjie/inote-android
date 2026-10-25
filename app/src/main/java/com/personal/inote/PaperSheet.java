package com.personal.inote;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.*;

/** Native bottom sheet with the paper app's grouped rows and pill actions. */
final class PaperSheet {
    final Dialog dialog;
    final LinearLayout body;
    private final Activity activity;
    private final boolean dark;
    private final int ink,muted,paper;
    PaperSheet(Activity activity,boolean dark,String title){
        this.activity=activity;this.dark=dark;ink=dark?0xffeeeeee:Color.BLACK;muted=dark?0xff999999:0xffbcbcbc;paper=dark?0xff303030:Color.WHITE;
        dialog=new Dialog(activity);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        body=column();body.setPadding(dp(12),dp(16),dp(12),dp(12));body.setBackgroundColor(dark?0xff252525:0xfff8f8f8);
        if(title!=null&&!title.isEmpty()){TextView heading=label(title,17,0xff888888);heading.setPadding(dp(4),0,0,dp(13));body.addView(heading);}
    }
    int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView label(String text,int size,int color){TextView t=new TextView(activity);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    GradientDrawable shape(int radius){GradientDrawable d=new GradientDrawable();d.setColor(paper);d.setCornerRadius(dp(radius));d.setStroke(dp(.5f),dark?0xff404040:0xffeeeeee);return d;}
    void gap(int height){body.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(height)));}
    LinearLayout group(){LinearLayout group=column();group.setBackground(shape(7));group.setElevation(dp(.6f));body.addView(group);gap(9);return group;}
    void line(LinearLayout group){View line=new View(activity);line.setBackgroundColor(dark?0xff404040:0xfff0f0f0);group.addView(line,new LinearLayout.LayoutParams(-1,dp(.5f)));}
    View action(String icon,String title,String subtitle,Runnable action){
        LinearLayout row=row();row.setPadding(dp(14),0,dp(12),0);row.setMinimumHeight(dp(52));row.setContentDescription(title);
        TextView symbol=label(icon,24,0xff7198dc);symbol.setGravity(Gravity.CENTER);row.addView(symbol,new LinearLayout.LayoutParams(dp(37),dp(36)));
        TextView name=label(title,18,title.startsWith("删除")?0xffe86559:ink);name.setPadding(dp(12),0,dp(8),0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(subtitle!=null){TextView sub=label(subtitle,15,muted);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(sub,new LinearLayout.LayoutParams(-2,-2));row.addView(label(" ›",25,muted));}
        row.setOnClickListener(v->{dialog.dismiss();action.run();});return row;
    }
    void paired(String iconA,String a,Runnable runA,String iconB,String b,Runnable runB){LinearLayout group=group(),row=row();group.addView(row);row.addView(action(iconA,a,null,runA),new LinearLayout.LayoutParams(0,dp(52),1));View divider=new View(activity);divider.setBackgroundColor(dark?0xff404040:0xfff0f0f0);row.addView(divider,new LinearLayout.LayoutParams(dp(.5f),-1));row.addView(action(iconB,b,null,runB),new LinearLayout.LayoutParams(0,dp(52),1));}
    void tiles(String[] icons,String[] labels,Runnable[] actions){LinearLayout row=row();body.addView(row);for(int i=0;i<labels.length;i++){final int index=i;LinearLayout tile=column();tile.setGravity(Gravity.CENTER);TextView symbol=label(icons[i],26,0xff6e97db);symbol.setGravity(Gravity.CENTER);symbol.setBackground(shape(40));symbol.setElevation(dp(1));tile.addView(symbol,new LinearLayout.LayoutParams(dp(58),dp(58)));TextView name=label(labels[i],15,ink);name.setPadding(0,dp(9),0,dp(12));tile.addView(name);tile.setContentDescription(labels[i]);tile.setOnClickListener(v->{dialog.dismiss();actions[index].run();});row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));}}
    void pill(String label,Runnable action){TextView button=label(label,18,ink);button.setGravity(Gravity.CENTER);button.setBackground(shape(28));button.setOnClickListener(v->{dialog.dismiss();action.run();});body.addView(button,new LinearLayout.LayoutParams(-1,dp(42)));gap(8);}
    void show(){
        pill("取消",()->{});ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(body);dialog.setContentView(scroll);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=dialog.getWindow().getAttributes();p.dimAmount=.58f;p.gravity=Gravity.BOTTOM;dialog.getWindow().setAttributes(p);
        dialog.show();int max=(int)(activity.getResources().getDisplayMetrics().heightPixels*.88f);body.measure(View.MeasureSpec.makeMeasureSpec(activity.getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));dialog.getWindow().setLayout(-1,Math.min(body.getMeasuredHeight(),max));dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setNavigationBarColor(dark?0xff252525:0xfff8f8f8);
        if(Build.VERSION.SDK_INT>=35){scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,0,bars.right,bars.bottom);return insets;});scroll.requestApplyInsets();}
    }
}
