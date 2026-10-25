package com.personal.inote;

import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.CheckBox;
import android.widget.EditText;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashSet;
import java.util.Set;
import junit.framework.TestCase;

/** Real RemoteViews inflation and AppWidgetHost binding, using generated notes only. */
@android.annotation.TargetApi(29)
public final class NoteWidgetTest extends TestCase {
    private Instrumentation instrumentation(){return PersonalTestRunner.instrumentation;}
    private Context context(){return instrumentation().getTargetContext();}
    private void main(Runnable task){AtomicReference<Throwable> failure=new AtomicReference<>();instrumentation().runOnMainSync(()->{try{task.run();}catch(Throwable error){failure.set(error);}});instrumentation().waitForIdleSync();if(failure.get()!=null)throw new AssertionError("Widget UI operation failed",failure.get());}
    private Bundle options(int height){Bundle b=new Bundle();b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,300);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);b.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height);return b;}
    private NoteStore.Note sample(){NoteStore.Note n=new NoteStore.Note();n.id=999;n.title="旅行清单";NoteStore.Record r=new NoteStore.Record();r.text="☐ 收好护照\n☐ 准备水杯\n☑ 记下沿途的灵感";n.records.add(r);return n;}
    public void testWidgetNeverShowsDeletedOrArchivedContent(){
        NoteStore.Note note=sample();int id=910001;WidgetSupport.configure(context(),id,note.id,WidgetSupport.PAPER);
        try{for(boolean archived:new boolean[]{false,true}){note.archived=archived;note.deleted=!archived;main(()->{View v=WidgetSupport.noteViews(context(),id,note,options(180)).apply(context(),new FrameLayout(context()));assertFalse(((TextView)v.findViewById(R.id.widget_body)).getText().toString().contains("护照"));});}
            note.deleted=false;note.archived=false;note.readOnly=true;main(()->{View v=WidgetSupport.noteViews(context(),id,note,options(180)).apply(context(),new FrameLayout(context()));assertTrue(((TextView)v.findViewById(R.id.widget_hint)).getText().toString().contains("只读"));});
        }finally{WidgetSupport.remove(context(),id);}
    }
    public void testThemesAndRestorePersistSelectionAndQuote(){
        int oldId=910002,newId=910003;try{WidgetSupport.configure(context(),oldId,87,WidgetSupport.SKY);WidgetSupport.preferences(context()).edit().putBoolean("quote_"+oldId,true).commit();WidgetSupport.restore(context(),new int[]{oldId},new int[]{newId});assertEquals(87,WidgetSupport.selectedNote(context(),newId));assertEquals(WidgetSupport.SKY,WidgetSupport.selectedTheme(context(),newId));assertTrue(WidgetSupport.quoteMode(context(),newId));assertEquals(0,WidgetSupport.selectedNote(context(),oldId));assertEquals(WidgetSupport.PAPER,WidgetSupport.validTheme("../unknown"));
            Bitmap paper=WidgetSupport.background(WidgetSupport.PAPER),wood=WidgetSupport.background(WidgetSupport.WOOD),sky=WidgetSupport.background(WidgetSupport.SKY);assertTrue(paper.getAllocationByteCount()<256*1024);assertFalse(paper.getPixel(128,4)==wood.getPixel(128,4));assertFalse(wood.getPixel(128,4)==sky.getPixel(128,4));paper.recycle();wood.recycle();sky.recycle();
        }finally{WidgetSupport.remove(context(),oldId);WidgetSupport.remove(context(),newId);}
    }
    public void testPreviewDropsLinksAndBoundsText(){
        NoteStore.Note note=sample();SpannableString rich=new SpannableString("https://example.org");rich.setSpan(new URLSpan("https://example.org"),0,rich.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);note.records.get(0).html=RichText.encode(rich);Spanned result=(Spanned)WidgetSupport.preview(note);assertEquals(0,result.getSpans(0,result.length(),URLSpan.class).length);note.records.get(0).html="";note.records.get(0).text=new String(new char[10000]).replace('\0','x');assertTrue(WidgetSupport.preview(note).length()<=3000);
        try{Intent intent=WidgetSupport.actionIntent(context(),WidgetSupport.ACTION_OPEN_NOTE,42,910004);assertEquals(MainActivity.class.getName(),intent.getComponent().getClassName());assertEquals(42,intent.getLongExtra(WidgetSupport.EXTRA_NOTE_ID,0));assertFalse(WidgetSupport.isValidLaunch(context(),intent));}finally{WidgetSupport.remove(context(),910004);}
    }
    public void testRealWidgetHostBindingConfigurationResizeAndUpdate()throws Exception{
        Context c=context();AppWidgetManager manager=AppWidgetManager.getInstance(c);AppWidgetHost host=new AppWidgetHost(c,71025);int noteWidget=host.allocateAppWidgetId(),quickWidget=host.allocateAppWidgetId();long noteId=0;AtomicReference<NoteWidgetConfigActivity> configActivity=new AtomicReference<>();AtomicReference<MainActivity> launchedActivity=new AtomicReference<>();Set<Long> createdQuickIds=new HashSet<>();
        try{
            // The emulator test runner may adopt this permission; the production app never requests it.
            instrumentation().getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            assertTrue("Widget binding permission is required for the integration test",manager.bindAppWidgetIdIfAllowed(noteWidget,new ComponentName(c,NoteWidgetProvider.class),options(180)));
            assertTrue(manager.bindAppWidgetIdIfAllowed(quickWidget,new ComponentName(c,QuickNoteWidgetProvider.class),options(150)));
            instrumentation().getUiAutomation().dropShellPermissionIdentity();
            try(NoteStore store=new NoteStore(c)){NoteStore.Note note=store.createNote("小组件测试 · "+System.nanoTime());noteId=note.id;store.addRecord(note.id,"☐ 收好护照\n☐ 准备水杯\n☑ 记下沿途的灵感");}
            String selectedTitle;try(NoteStore store=new NoteStore(c)){selectedTitle=store.getNote(noteId).title;}
            NoteWidgetConfigActivity configuration=(NoteWidgetConfigActivity)instrumentation().startActivitySync(new Intent(c,NoteWidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,noteWidget).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));configActivity.set(configuration);instrumentation().waitForIdleSync();
            main(()->{View row=findText(configuration.getWindow().getDecorView(),selectedTitle);assertTrue(row instanceof RadioButton);row.performClick();findText(configuration.getWindow().getDecorView(),"天空").performClick();findText(configuration.getWindow().getDecorView(),"完成").performClick();});assertEquals(noteId,WidgetSupport.selectedNote(c,noteWidget));assertEquals(WidgetSupport.SKY,WidgetSupport.selectedTheme(c,noteWidget));assertTrue(WidgetSupport.isNoteWidget(c,noteWidget));assertFalse(WidgetSupport.isNoteWidget(c,quickWidget));
            assertTrue(WidgetSupport.isValidLaunch(c,WidgetSupport.actionIntent(c,WidgetSupport.ACTION_OPEN_NOTE,noteId,noteWidget)));
            assertFalse(WidgetSupport.isValidLaunch(c,new Intent(c,MainActivity.class).setAction(WidgetSupport.ACTION_OPEN_NOTE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,noteWidget)));
            assertFalse(WidgetSupport.isValidLaunch(c,WidgetSupport.actionIntent(c,WidgetSupport.ACTION_NEW_NOTE,0,noteWidget)));
            WidgetSupport.updateNote(c,manager,noteWidget);WidgetSupport.updateQuick(c,manager,quickWidget);AtomicReference<AppWidgetHostView> noteView=new AtomicReference<>(),quickView=new AtomicReference<>();
            main(()->{host.startListening();noteView.set(host.createView(c,noteWidget,manager.getAppWidgetInfo(noteWidget)));quickView.set(host.createView(c,quickWidget,manager.getAppWidgetInfo(quickWidget)));});
            main(()->{assertTrue(((TextView)noteView.get().findViewById(R.id.widget_body)).getText().toString().contains("护照"));assertNotNull(quickView.get().findViewById(R.id.widget_add));});
            manager.updateAppWidgetOptions(noteWidget,options(300));new NoteWidgetProvider().onAppWidgetOptionsChanged(c,manager,noteWidget,options(300));Thread.sleep(300);main(()->assertTrue(((TextView)noteView.get().findViewById(R.id.widget_body)).getMaxLines()>6));
            capture(noteView.get(),"06-note-widget.png",360,230);capture(quickView.get(),"07-quick-widget.png",180,180);
            MainActivity launched=(MainActivity)instrumentation().startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launchedActivity.set(launched);instrumentation().waitForIdleSync();
            main(()->assertTrue("Real note widget root must handle the tap",noteView.get().findViewById(R.id.widget_root).performClick()));
            awaitUi(()->{assertNotNull(findText(launched.getWindow().getDecorView(),selectedTitle));assertNotNull("Tap must navigate to note details, not merely the home list",findText(launched.getWindow().getDecorView(),"笔记菜单"));});
            Set<Long> beforeQuick=noteIds();main(()->assertTrue("Real quick widget add button must handle the tap",quickView.get().findViewById(R.id.widget_add).performClick()));
            for(int attempt=0;attempt<40;attempt++){createdQuickIds.addAll(noteIds());createdQuickIds.removeAll(beforeQuick);if(!createdQuickIds.isEmpty())break;Thread.sleep(50);}
            assertEquals("A quick widget tap should create exactly one new note",1,createdQuickIds.size());long quickNoteId=createdQuickIds.iterator().next();
            awaitUi(()->assertNotNull("Quick widget must open the editor",topEditor()));
            int beforeForged=noteIds().size();c.startActivity(new Intent(c,MainActivity.class).setAction(WidgetSupport.ACTION_NEW_NOTE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,quickWidget).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));Thread.sleep(200);instrumentation().waitForIdleSync();assertEquals("Forged widget action must not create data",beforeForged,noteIds().size());
            main(()->{EditText editor=topEditor();assertNotNull("Forged intent must not dismiss the active editor",editor);editor.setText("小组件快捷记录验证");View save=topText("保存");assertNotNull(save);assertTrue(save.performClick());});
            try(NoteStore store=new NoteStore(c)){NoteStore.Note quick=store.getNote(quickNoteId);assertEquals(1,quick.records.size());assertEquals("小组件快捷记录验证",quick.records.get(0).text);}
            main(()->assertTrue(noteView.get().findViewById(R.id.widget_root).performClick()));awaitUi(()->{assertNotNull(findText(launched.getWindow().getDecorView(),selectedTitle));assertNull("Opening a saved note must close a previous editor",topEditor());});
            try(NoteStore store=new NoteStore(c)){NoteStore.Note n=store.getNote(noteId);n.archived=true;store.saveNote(n);}WidgetSupport.updateNote(c,manager,noteWidget);Thread.sleep(200);main(()->assertFalse(((TextView)noteView.get().findViewById(R.id.widget_body)).getText().toString().contains("护照")));
        }finally{
            instrumentation().getUiAutomation().dropShellPermissionIdentity();main(()->{host.stopListening();if(configActivity.get()!=null)configActivity.get().finish();if(launchedActivity.get()!=null)launchedActivity.get().finish();});host.deleteAppWidgetId(noteWidget);host.deleteAppWidgetId(quickWidget);WidgetSupport.remove(c,noteWidget);WidgetSupport.remove(c,quickWidget);
            if(noteId>0)try(NoteStore store=new NoteStore(c)){NoteStore.Note n=store.getNote(noteId);if(n!=null){n.deleted=true;store.saveNote(n);store.purgeNote(noteId);}}
            for(long quickId:createdQuickIds)try(NoteStore store=new NoteStore(c)){NoteStore.Note n=store.getNote(quickId);if(n!=null){n.deleted=true;store.saveNote(n);store.purgeNote(quickId);}c.getSharedPreferences(MainActivity.class.getName(),Context.MODE_PRIVATE).edit().remove("draft-"+quickId+"-0").remove("draft-"+quickId+"-0-todo").apply();}
        }
    }
    private Set<Long> noteIds(){Set<Long> result=new HashSet<>();try(NoteStore store=new NoteStore(context())){for(NoteStore.Note note:store.listNotes("all","",0))result.add(note.id);}return result;}
    private void awaitUi(Runnable assertion)throws Exception{AssertionError last=null;for(int attempt=0;attempt<40;attempt++){try{main(assertion);return;}catch(AssertionError failure){last=failure;}Thread.sleep(50);}throw last;}
    private View topText(String text){java.util.List<View> roots=WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0;i--){View match=findText(roots.get(i),text);if(match!=null)return match;}return null;}
    private EditText topEditor(){java.util.List<View> roots=WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0;i--){EditText match=findEditor(roots.get(i));if(match!=null)return match;}return null;}
    private EditText findEditor(View view){if(view.getVisibility()!=View.VISIBLE)return null;if(view instanceof EditText&&"写下你的灵感…".contentEquals(((EditText)view).getHint()))return (EditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText match=findEditor(group.getChildAt(i));if(match!=null)return match;}}return null;}
    private View findText(View view,String text){if(view.getVisibility()!=View.VISIBLE)return null;if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=findText(group.getChildAt(i),text);if(match!=null)return match;}}return null;}
    private void capture(View view,String name,int widthDp,int heightDp)throws Exception{
        AtomicReference<Bitmap> image=new AtomicReference<>();main(()->{float density=context().getResources().getDisplayMetrics().density;int w=Math.round(widthDp*density),h=Math.round(heightDp*density);view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));image.set(b);});File directory=new File(context().getExternalFilesDir(null),"ui-review");assertTrue(directory.isDirectory()||directory.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(directory,name))){image.get().compress(Bitmap.CompressFormat.PNG,100,out);}image.get().recycle();
    }
}
