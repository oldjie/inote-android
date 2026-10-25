package com.personal.inote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.RemoteViews;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

/** Standard Android AppWidgets; no proprietary launcher service or network access. */
public final class WidgetSupport {
    public static final String ACTION_OPEN_NOTE="com.personal.inote.widget.OPEN_NOTE";
    public static final String ACTION_NEW_NOTE="com.personal.inote.widget.NEW_NOTE";
    public static final String EXTRA_NOTE_ID="widget_note_id";
    public static final String PAPER="paper", WOOD="wood", SKY="sky";
    private static final String PREFS="desktop_widgets";
    private static final String EXTRA_TOKEN="widget_launch_token";
    private static final ExecutorService UPDATES=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean PENDING=new AtomicBoolean();
    private WidgetSupport(){}

    static SharedPreferences preferences(Context context){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static long selectedNote(Context context,int id){return preferences(context).getLong("note_"+id,0);}
    public static String selectedTheme(Context context,int id){return validTheme(preferences(context).getString("theme_"+id,PAPER));}
    static boolean quoteMode(Context context,int id){return preferences(context).getBoolean("quote_"+id,false);}
    static String validTheme(String theme){return WOOD.equals(theme)?WOOD:SKY.equals(theme)?SKY:PAPER;}
    static void configure(Context context,int id,long note,String theme){preferences(context).edit().putLong("note_"+id,note).putString("theme_"+id,validTheme(theme)).apply();}
    static void remove(Context context,int id){preferences(context).edit().remove("note_"+id).remove("theme_"+id).remove("quote_"+id).remove("token_"+id).apply();}
    static void restore(Context context,int[] oldIds,int[] newIds){
        int count=Math.min(oldIds.length,newIds.length);long[] notes=new long[count];String[] themes=new String[count];boolean[] quotes=new boolean[count];
        for(int i=0;i<count;i++){notes[i]=selectedNote(context,oldIds[i]);themes[i]=selectedTheme(context,oldIds[i]);quotes[i]=quoteMode(context,oldIds[i]);}
        for(int id:oldIds)remove(context,id);
        for(int i=0;i<count;i++){configure(context,newIds[i],notes[i],themes[i]);preferences(context).edit().putBoolean("quote_"+newIds[i],quotes[i]).apply();}
    }
    public static boolean isNoteWidget(Context context,int id){
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
        return info!=null&&new ComponentName(context,NoteWidgetProvider.class).equals(info.provider);
    }
    public static boolean isQuickWidget(Context context,int id){
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
        return info!=null&&new ComponentName(context,QuickNoteWidgetProvider.class).equals(info.provider);
    }
    private static synchronized String launchToken(Context context,int id){
        String token=preferences(context).getString("token_"+id,"");
        if(token.isEmpty()){token=UUID.randomUUID().toString();preferences(context).edit().putString("token_"+id,token).apply();}return token;
    }
    /** Do this before handling a widget action in the exported launcher Activity. */
    public static boolean isValidLaunch(Context context,Intent intent){
        if(intent==null)return false;int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(!(ACTION_OPEN_NOTE.equals(intent.getAction())&&isNoteWidget(context,id))&&!(ACTION_NEW_NOTE.equals(intent.getAction())&&isQuickWidget(context,id)))return false;
        String expected=preferences(context).getString("token_"+id,""),provided=intent.getStringExtra(EXTRA_TOKEN);
        return !expected.isEmpty()&&expected.equals(provided);
    }

    /** Call after a committed store change. Coalesced background refresh avoids blocking typing. */
    public static void refreshAll(Context context){
        Context app=context.getApplicationContext();
        // Isolated database test contexts must never refresh the real user's launcher widgets.
        if(!(app instanceof android.app.Application))return;
        if(!PENDING.compareAndSet(false,true))return;
        new Handler(Looper.getMainLooper()).postDelayed(()->UPDATES.execute(()->{
            PENDING.set(false);
            AppWidgetManager manager=AppWidgetManager.getInstance(app);
            for(int id:manager.getAppWidgetIds(new ComponentName(app,NoteWidgetProvider.class)))updateNote(app,manager,id);
            for(int id:manager.getAppWidgetIds(new ComponentName(app,QuickNoteWidgetProvider.class)))updateQuick(app,manager,id);
        }),120);
    }
    static void updateNote(Context context,AppWidgetManager manager,int id){
        try(NoteStore store=new NoteStore(context)){manager.updateAppWidget(id,noteViews(context,id,store.getNote(selectedNote(context,id)),manager.getAppWidgetOptions(id)));}
        catch(RuntimeException failure){manager.updateAppWidget(id,noteViews(context,id,null,manager.getAppWidgetOptions(id)));}
    }
    static void updateQuick(Context context,AppWidgetManager manager,int id){manager.updateAppWidget(id,quickViews(context,id));}

    static Intent actionIntent(Context context,String action,long note,int widgetId){
        return new Intent(context,MainActivity.class).setAction(action)
                .setData(new Uri.Builder().scheme("inote-widget").authority(context.getPackageName()).appendPath(Integer.toString(widgetId)).appendPath(action).build())
                .putExtra(EXTRA_NOTE_ID,note).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId).putExtra(EXTRA_TOKEN,launchToken(context,widgetId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    static PendingIntent action(Context context,String action,long note,int widgetId){return PendingIntent.getActivity(context,widgetId,actionIntent(context,action,note,widgetId),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static PendingIntent configuration(Context context,int id){
        Intent intent=new Intent(context,NoteWidgetConfigActivity.class).setData(Uri.parse("inote-widget://configure/"+id)).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        return PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews quickViews(Context context,int id){
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_quick_note);
        views.setImageViewBitmap(R.id.widget_art,quickArtwork());
        views.setOnClickPendingIntent(R.id.widget_root,action(context,ACTION_NEW_NOTE,0,id));
        views.setOnClickPendingIntent(R.id.widget_add,action(context,ACTION_NEW_NOTE,0,id));
        return views;
    }
    static RemoteViews noteViews(Context context,int id,NoteStore.Note note,Bundle options){
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_note);
        views.setImageViewBitmap(R.id.widget_background,background(selectedTheme(context,id)));
        int height=options==null?160:options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,160);
        boolean quote=quoteMode(context,id);
        views.setInt(R.id.widget_body,"setMaxLines",Math.max(2,Math.min(30,(height-65)/(quote?29:21))));
        views.setTextViewTextSize(R.id.widget_body,android.util.TypedValue.COMPLEX_UNIT_SP,quote?22:16);
        boolean visible=note!=null&&!note.deleted&&!note.archived;
        String title=visible?(note.title.trim().isEmpty()?"灵感便签":note.title):"灵感便签";
        CharSequence body=visible?preview(note):note==null?"轻点右上角，选择一篇笔记":"笔记已归档或移入回收站";
        views.setTextViewText(R.id.widget_title,title.length()>100?title.substring(0,100):title);
        views.setTextViewText(R.id.widget_body,body);
        views.setTextViewText(R.id.widget_hint,visible?(note.readOnly?"只读 · 轻点阅读":"轻点打开笔记"):"可重新选择笔记");
        views.setOnClickPendingIntent(R.id.widget_root,visible?action(context,ACTION_OPEN_NOTE,note.id,id):configuration(context,id));
        views.setOnClickPendingIntent(R.id.widget_settings,configuration(context,id));
        return views;
    }
    /** Only framework formatting spans cross the launcher boundary. No URLs or attachment paths. */
    static CharSequence preview(NoteStore.Note note){
        SpannableStringBuilder result=new SpannableStringBuilder();
        for(NoteStore.Record record:note.records){
            if(result.length()>0)result.append("\n");
            if(record.todo)result.append(record.done?"☑ ":"☐ ");
            CharSequence rich=RichText.decode(record.html,record.text);
            SpannableStringBuilder safe=new SpannableStringBuilder(rich);
            for(CharacterStyle span:safe.getSpans(0,safe.length(),CharacterStyle.class)){
                int start=safe.getSpanStart(span),end=safe.getSpanEnd(span),flags=safe.getSpanFlags(span);safe.removeSpan(span);
                CharacterStyle replacement=null;
                if(span instanceof StyleSpan)replacement=new StyleSpan(((StyleSpan)span).getStyle());
                else if(span instanceof UnderlineSpan)replacement=new UnderlineSpan();
                else if(span instanceof StrikethroughSpan)replacement=new StrikethroughSpan();
                else if(span instanceof BackgroundColorSpan)replacement=new BackgroundColorSpan(((BackgroundColorSpan)span).getBackgroundColor());
                else if(span instanceof TypefaceSpan)replacement=new TypefaceSpan("monospace");
                if(replacement!=null)safe.setSpan(replacement,start,end,flags);
            }
            int remaining=3000-result.length();if(remaining<=0)break;
            result.append(safe,0,Math.min(remaining,safe.length()));
            if(!record.images.isEmpty()&&result.length()<2995)result.append(" [图片]");
            if(result.length()>=3000)break;
        }
        if(result.length()==0)result.append("这篇笔记还没有内容");
        return result;
    }
    static Bitmap background(String style){
        Bitmap bitmap=Bitmap.createBitmap(256,192,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        int color=WOOD.equals(style)?0xffbf9e6e:SKY.equals(style)?0xff9bd6ec:0xfff4f2ed;
        p.setColor(color);canvas.drawRoundRect(1,1,255,191,21,21,p);canvas.save();Path clip=new Path();clip.addRoundRect(new RectF(1,1,255,191),21,21,Path.Direction.CW);canvas.clipPath(clip);
        if(WOOD.equals(style)){p.setStrokeWidth(1);for(int x=3;x<256;x+=5){p.setColor(x%3==0?0x22804d1e:0x22ffffff);canvas.drawLine(x,0,x+3,192,p);}}
        if(SKY.equals(style)){p.setColor(0x60ffffff);for(int x=0;x<270;x+=28)for(int y=0;y<205;y+=28)canvas.drawCircle(x+(y%56==0?0:14),y,5,p);}
        canvas.restore();p.setColor(Color.WHITE);canvas.drawRoundRect(9,9,247,183,15,15,p);
        if(PAPER.equals(style)){p.setColor(0xfff1efeb);p.setStrokeWidth(.65f);for(int y=40;y<177;y+=18)canvas.drawLine(15,y,241,y,p);}
        return bitmap;
    }
    static Bitmap quickArtwork(){
        Bitmap b=Bitmap.createBitmap(96,72,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xffc8ddfd);c.drawOval(32,54,64,70,p);Path beam=new Path();beam.moveTo(34,40);beam.lineTo(28,62);beam.lineTo(67,62);beam.lineTo(62,40);beam.close();p.setColor(0x6684cfff);c.drawPath(beam,p);
        p.setColor(0xff79d4ef);c.drawOval(18,25,78,49,p);p.setColor(0xff7899ed);c.drawOval(18,31,78,49,p);p.setColor(0xffb9eafa);c.drawOval(31,13,65,38,p);p.setColor(Color.WHITE);c.drawArc(35,17,61,39,200,115,false,p);
        p.setColor(0xffffdf74);for(int x=29;x<74;x+=16)c.drawCircle(x,38,2.5f,p);p.setColor(0xff96dfeb);p.setStrokeWidth(2);c.drawLine(77,10,77,20,p);c.drawLine(72,15,82,15,p);return b;
    }
}
