package com.personal.inote;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.app.Instrumentation;
import android.content.Intent;
import junit.framework.TestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.AdapterView;
import android.os.Bundle;
import junit.framework.AssertionFailedError;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.FileOutputStream;
import android.graphics.Bitmap;

/** Public view-tree smoke test. Dialog enumeration uses the API 29+ WindowInspector. */
@SuppressWarnings("deprecation")
public class MainActivitySmokeTest extends TestCase {
    private MainActivity activity;
    private SharedPreferences prefs;
    private boolean originalDark, originalColumns;
    private final Set<Long> createdNoteIds=new HashSet<>();
    private final Set<Long> createdBookIds=new HashSet<>();
    private String title;
    public MainActivitySmokeTest(){}
    private Instrumentation getInstrumentation(){return PersonalTestRunner.instrumentation;}
    private MainActivity getActivity(){if(activity==null){Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)getInstrumentation().startActivitySync(intent);}return activity;}
    private void setActivity(MainActivity value){activity=value;}

    @Override protected void setUp() throws Exception {
        super.setUp();getInstrumentation().setInTouchMode(true);
        prefs=getInstrumentation().getTargetContext().getSharedPreferences(MainActivity.class.getName(),Context.MODE_PRIVATE);
        originalDark=prefs.getBoolean("dark",false);originalColumns=prefs.getBoolean("twoColumns",true);title="Smoke note "+System.currentTimeMillis();
        activity=getActivity();getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown() throws Exception {
        if(activity!=null)onMain("finish activity during cleanup",()->activity.finish());
        getInstrumentation().waitForIdleSync();
        try(NoteStore store=new NoteStore(getInstrumentation().getTargetContext())) {
            for(String filter:new String[]{"all","archived","trash"})for(NoteStore.Note n:store.listNotes(filter,title,0))if(title.equals(n.title))createdNoteIds.add(n.id);
            for(long id:createdNoteIds){NoteStore.Note n=store.getNote(id);if(n!=null){n.deleted=true;store.saveNote(n);store.purgeNote(id);}}
            for(long id:createdBookIds)store.deleteBook(id);
        }
        prefs.edit().putBoolean("dark",originalDark).putBoolean("twoColumns",originalColumns).commit();super.tearDown();
    }

    public void testLaunchNavigationCreateAppendAndRestart() {
        assertNotNull(activity);assertText("新建笔记");assertText("所有笔记");
        try(NoteStore store=new NoteStore(getInstrumentation().getTargetContext())) {
            if(store.listNotes("all","",0).isEmpty())assertText("还没有笔记");
        }
        clickText("笔记本");assertText("新建笔记本");assertText("我的");
        clickText("我的");assertText("关于灵感笔记");
        String toggle=originalDark?"切换浅色主题":"切换深色主题";clickText(toggle);
        assertText(originalDark?"切换深色主题":"切换浅色主题");assertEquals(!originalDark,prefs.getBoolean("dark",originalDark));
        clickText("记忆卡片");assertText("待复习");assertText("全部卡片");clickText("返回");
        clickText("所有笔记");
        // API 26-28 can exercise navigation, but do not expose public dialog roots.
        if(Build.VERSION.SDK_INT<29)return;
        clickText("新建笔记");clickText("空白笔记");setTopEditor("",title);clickText("保存");
        assertText("添加小笔记");setTopEditor("写下你的灵感…","First captured idea");clickText("保存");
        assertText(title);assertText("First captured idea");
        clickText("＋ 添加小笔记");setTopEditor("写下你的灵感…","Second appended idea");clickText("保存");assertText("Second appended idea");
        try(NoteStore store=new NoteStore(getInstrumentation().getTargetContext())) {
            List<NoteStore.Note> notes=store.listNotes("all",title,0);assertEquals(1,notes.size());createdNoteIds.add(notes.get(0).id);assertEquals(2,notes.get(0).records.size());
        }
        onMain("finish activity for persistence restart",()->activity.finish());getInstrumentation().waitForIdleSync();setActivity(null);activity=getActivity();getInstrumentation().waitForIdleSync();
        assertText(title);clickText(title);assertText("First captured idea");assertText("Second appended idea");
        clickText("First captured idea");assertText("编辑小笔记");setTopEditor("写下你的灵感…","Updated captured idea");clickText("保存");assertText("Updated captured idea");
        clickText(title);clickText("移入回收本");
        try(NoteStore store=new NoteStore(getInstrumentation().getTargetContext())){assertEquals(1,store.listNotes("trash",title,0).size());}
        clickText("筛选与排序");clickText("回收本");clickText(title);clickText("恢复笔记");assertText("Updated captured idea");
        clickText("‹ 返回");clickText("我的");assertText(originalDark?"切换深色主题":"切换浅色主题");
    }

    /** Captures real UI-built content without seeding application records. */
    public void testVisualCommunityTemplatePaperAndBookshelf() throws Exception {
        if(Build.VERSION.SDK_INT<29)return;
        status("visual: start light theme and two-column view");prefs.edit().putBoolean("dark",false).putBoolean("twoColumns",true).commit();
        onMain("restart for visual theme",()->activity.finish());getInstrumentation().waitForIdleSync();setActivity(null);activity=getActivity();getInstrumentation().waitForIdleSync();
        String bookName="旅行记录 · "+(System.currentTimeMillis()%10000);
        clickText("笔记本");clickText("新建笔记本");setTopEditor("",bookName);clickText("保存");assertText(bookName);
        try(NoteStore store=new NoteStore(getInstrumentation().getTargetContext())){for(NoteStore.Book b:store.listBooks())if(b.name.equals(bookName))createdBookIds.add(b.id);}
        assertEquals("Visual book was not created",1,createdBookIds.size());
        clickText(bookName);
        Set<Long> before=new HashSet<>();try(NoteStore store=new NoteStore(getInstrumentation().getTargetContext())){for(NoteStore.Note n:store.listNotes("all","",0))before.add(n.id);}
        clickText("新建笔记");clickText("从模板创建");
        // Keep this compatible with the transitional dialog picker and the new
        // full-screen community template picker.
        final boolean[] oldPicker={false};onMain("inspect template picker",()->oldPicker[0]=topText("简体中文 · 54 个")!=null);
        if(oldPicker[0]){clickText("简体中文 · 54 个");clickText("每日规划 · 7");}else clickText("模板分类：每日规划");
        screenshot("05-template-picker.png");clickText("每日待办");clickText("创建笔记");
        NoteStore.Note created=null;
        try(NoteStore store=new NoteStore(getInstrumentation().getTargetContext())){for(NoteStore.Note n:store.listNotes("all","每日待办",0))if(!before.contains(n.id)){createdNoteIds.add(n.id);created=n;}}
        assertNotNull("Community template did not create a note",created);assertFalse("Template has no records",created.records.isEmpty());assertTrue("Template did not retain book membership",created.bookIds.containsAll(createdBookIds));
        assertText("每日待办");screenshot("02-template-detail.png");
        clickText(created.records.get(0).text);assertText("编辑小笔记");clickText("文本格式");assertText("高亮");screenshot("03-template-editor.png");clickText("保存");
        clickText("‹ 返回");clickText("所有笔记");assertText("每日待办");screenshot("01-home-template.png");
        clickText("笔记本");assertText(bookName);screenshot("04-bookshelf.png");
        status("visual: captures complete; teardown removes only this test's note and book");
    }
    private void screenshot(String name) throws Exception {
        status("capture: "+name);getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(250);
        Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull("Screenshot unavailable: "+name,image);
        File folder=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"ui-review");assertTrue("Unable to create screenshot directory",folder.isDirectory()||folder.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(folder,name))){assertTrue("PNG encoding failed",image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
        status("saved: "+new File(folder,name).getAbsolutePath());
    }
    private void status(String step){Bundle progress=new Bundle();progress.putString("stream","\nSMOKE STEP: "+step+"\n");getInstrumentation().sendStatus(1,progress);}

    private List<View> roots() {
        if(Build.VERSION.SDK_INT>=29)return WindowInspector.getGlobalWindowViews();
        List<View> roots=new ArrayList<>();roots.add(activity.getWindow().getDecorView());return roots;
    }
    private View findText(View root,String expected) {
        if(root.getVisibility()!=View.VISIBLE)return null;
        if(expected.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof TextView && expected.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=group.getChildCount()-1;i>=0;i--){View found=findText(group.getChildAt(i),expected);if(found!=null)return found;}}
        return null;
    }
    private View topText(String expected) {
        List<View> roots=roots();for(int i=roots.size()-1;i>=0;i--){View found=findText(roots.get(i),expected);if(found!=null)return found;}return null;
    }
    private void assertText(String expected) {
        onMain("assert text: "+expected,()->assertNotNull("Missing visible label: "+expected,topText(expected)));
    }
    private void clickText(String expected) {
        onMain("click text: "+expected,()->{
            View target=topText(expected);assertNotNull("Missing clickable label: "+expected,target);
            // AlertDialog list rows dispatch through the AdapterView's item listener.
            // Text selectable labels may be clickable without any click listener.
            View item=target;
            while(item.getParent() instanceof View){
                View parent=(View)item.getParent();
                if(parent instanceof AdapterView){
                    AdapterView<?> adapter=(AdapterView<?>)parent;int position=adapter.getPositionForView(item);
                    assertTrue("No adapter position for: "+expected,position!=AdapterView.INVALID_POSITION);
                    assertTrue("Adapter item did not handle: "+expected,adapter.performItemClick(item,position,adapter.getItemIdAtPosition(position)));return;
                }
                item=parent;
            }
            while(target!=null&&!target.hasOnClickListeners())target=target.getParent() instanceof View?(View)target.getParent():null;
            assertNotNull("No click listener for: "+expected,target);assertTrue("Click listener did not handle: "+expected,target.performClick());
        });
    }
    private EditText findEditor(View root,String hint) {
        if(root.getVisibility()!=View.VISIBLE)return null;
        if(root instanceof EditText && (hint.isEmpty()||hint.contentEquals(((EditText)root).getHint())))return (EditText)root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=group.getChildCount()-1;i>=0;i--){EditText editor=findEditor(group.getChildAt(i),hint);if(editor!=null)return editor;}}
        return null;
    }
    private void setTopEditor(String hint,String value) {
        onMain("set editor: "+(hint.isEmpty()?"dialog title":hint),()->{
            List<View> roots=roots();EditText editor=null;for(int i=roots.size()-1;i>=0&&editor==null;i--)editor=findEditor(roots.get(i),hint);
            assertNotNull("Missing editor: "+hint,editor);editor.setText(value);
        });
    }
    private void onMain(String step,Runnable action) {
        Bundle progress=new Bundle();progress.putString("stream","\nSMOKE STEP: "+step+"\n");getInstrumentation().sendStatus(1,progress);
        getInstrumentation().waitForIdleSync();final Throwable[] caught=new Throwable[1];
        getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable error){caught[0]=error;}});
        if(caught[0]!=null){AssertionFailedError failure=new AssertionFailedError("Smoke step failed ["+step+"]: "+caught[0]);failure.initCause(caught[0]);throw failure;}
        getInstrumentation().waitForIdleSync();
    }
}
