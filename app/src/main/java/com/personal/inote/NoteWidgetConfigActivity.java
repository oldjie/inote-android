package com.personal.inote;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.*;
import java.util.List;

/** Launcher-facing configuration requires a real widget owned by this provider. */
public final class NoteWidgetConfigActivity extends Activity {
    private int widgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private long selected;
    private String theme;
    private boolean quote;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID||!WidgetSupport.isNoteWidget(this,widgetId)){finish();return;}
        selected=saved==null?WidgetSupport.selectedNote(this,widgetId):saved.getLong("selected");theme=saved==null?WidgetSupport.selectedTheme(this,widgetId):saved.getString("theme",WidgetSupport.PAPER);quote=saved==null?WidgetSupport.quoteMode(this,widgetId):saved.getBoolean("quote");render();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putLong("selected",selected);state.putString("theme",theme);state.putBoolean("quote",quote);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff383838);t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;}
    private void render(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(14),dp(18),dp(12));root.setBackgroundColor(0xfff5f3f3);
        TextView title=text("灵感便签",24);title.setTypeface(null,android.graphics.Typeface.BOLD);root.addView(title);
        root.addView(text("选择一篇笔记放到桌面。便签内容会显示在解锁后的桌面上，请选择适合公开查看的内容。",14));
        LinearLayout styles=new LinearLayout(this);String[] names={"纸张","木纹","天空"},values={WidgetSupport.PAPER,WidgetSupport.WOOD,WidgetSupport.SKY};
        for(int i=0;i<3;i++){String value=values[i];Button button=new Button(this);button.setText(value.equals(theme)?getString(R.string.widget_selected_theme,names[i]):names[i]);button.setOnClickListener(v->{theme=value;render();});styles.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));}root.addView(styles);
        CheckBox quoteChoice=new CheckBox(this);quoteChoice.setText("大字标语便签");quoteChoice.setTextColor(0xff333333);quoteChoice.setChecked(quote);quoteChoice.setOnCheckedChangeListener((button,checked)->quote=checked);root.addView(quoteChoice);
        ScrollView scroll=new ScrollView(this);RadioGroup notes=new RadioGroup(this);scroll.addView(notes);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try(NoteStore store=new NoteStore(this)){List<NoteStore.Note> choices=store.listNotes("all","",0);if(choices.isEmpty())notes.addView(text("还没有可选笔记，请先回到应用创建一篇。",16));
            for(NoteStore.Note note:choices){RadioButton choice=new RadioButton(this);String name=note.title.trim().isEmpty()?"无标题笔记":note.title;choice.setText(note.readOnly?getString(R.string.widget_read_only_title,name):name);choice.setTextColor(0xff333333);choice.setTextSize(17);choice.setPadding(0,dp(10),0,dp(10));choice.setChecked(note.id==selected);choice.setOnClickListener(v->selected=note.id);notes.addView(choice);}}
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END);Button cancel=new Button(this);cancel.setText("取消");cancel.setOnClickListener(v->finish());actions.addView(cancel);Button add=new Button(this);add.setText("完成");add.setOnClickListener(v->save());actions.addView(add);root.addView(actions);setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18)+b.left,dp(14)+b.top,dp(18)+b.right,dp(12)+b.bottom);return insets;});root.requestApplyInsets();}
    }
    private void save(){
        if(!WidgetSupport.isNoteWidget(this,widgetId)){finish();return;}
        try(NoteStore store=new NoteStore(this)){NoteStore.Note note=store.getNote(selected);if(note==null||note.deleted||note.archived){Toast.makeText(this,"请选择一篇可用笔记",Toast.LENGTH_SHORT).show();return;}}
        WidgetSupport.configure(this,widgetId,selected,theme);WidgetSupport.preferences(this).edit().putBoolean("quote_"+widgetId,quote).apply();WidgetSupport.updateNote(this,AppWidgetManager.getInstance(this),widgetId);
        setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish();
    }
}
