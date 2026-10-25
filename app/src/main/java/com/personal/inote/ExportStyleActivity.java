package com.personal.inote;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Paper image preview; saved and shared images use the exact preview options. */
@SuppressWarnings("deprecation")
public final class ExportStyleActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final NoteExporter.Options options=new NoteExporter.Options();
    private NoteStore.Note note;
    private ImageView preview;
    private TextView message,save,share;
    private EditText signature;
    private Bitmap displayed;
    private boolean busy;
    private final Runnable refreshPreview=this::preview;
    public static void start(Activity activity,long noteId){activity.startActivity(new Intent(activity,ExportStyleActivity.class).putExtra("note_id",noteId));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try(NoteStore store=new NoteStore(this)){note=store.getNote(getIntent().getLongExtra("note_id",0));}
        if(note==null||note.deleted){Toast.makeText(this,"笔记不存在",Toast.LENGTH_SHORT).show();finish();return;}
        SharedPreferences prefs=getSharedPreferences("export-style",MODE_PRIVATE);
        options.theme=prefs.getInt("theme",0);options.dates=prefs.getBoolean("dates",true);options.title=prefs.getBoolean("title",true);options.textSize=prefs.getInt("textSize",40);options.signature=prefs.getString("signature","");
        if(state!=null){options.theme=state.getInt("theme",options.theme);options.dates=state.getBoolean("dates",options.dates);options.title=state.getBoolean("title",options.title);options.textSize=state.getInt("textSize",options.textSize);options.signature=state.getString("signature",options.signature);}
        draw();preview();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);return v;}
    private void draw(){
        int bg=0xfff2f0f0,muted=0xff999999;LinearLayout root=column();root.setBackgroundColor(bg);
        LinearLayout top=row();TextView back=label("‹",38,muted);back.setContentDescription("返回笔记");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(60)));TextView title=label("选择图片样式",18,Color.BLACK);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title);root.addView(top);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout imagePanel=column();imagePanel.setPadding(dp(8),dp(18),dp(8),dp(18));preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("导出图片预览");imagePanel.addView(preview,new LinearLayout.LayoutParams(-1,-2));message=label("正在生成预览…",14,muted);message.setPadding(dp(16),dp(18),dp(16),dp(18));imagePanel.addView(message);scroll.addView(imagePanel);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView choices=new HorizontalScrollView(this);choices.setHorizontalScrollBarEnabled(false);choices.setBackgroundColor(0xfffafafa);LinearLayout themes=row();themes.setPadding(dp(8),dp(10),dp(8),dp(8));int[] colors={0xffb99b68,0xff96cce1,0xff494c4f,0xffe9a7b5,0xff90a0a9};
        for(int i=0;i<NoteExporter.THEMES.length;i++){final int theme=i;FrameLayout tile=new FrameLayout(this);tile.setBackground(rounded(colors[i],6));tile.setPadding(dp(9),dp(9),dp(9),dp(9));TextView caption=label(NoteExporter.THEMES[i],16,0xff444444);caption.setTypeface(null,Typeface.BOLD);caption.setBackground(rounded(Color.WHITE,4));tile.addView(caption,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(70),dp(70));lp.setMargins(0,0,dp(12),0);themes.addView(tile,lp);tile.setContentDescription("图片样式："+NoteExporter.THEMES[i]);tile.setOnClickListener(v->{options.theme=theme;preview();});}choices.addView(themes);root.addView(choices);
        LinearLayout controls=row();controls.setPadding(dp(8),dp(10),dp(8),dp(10));controls.setBackgroundColor(0xfffafafa);signature=new EditText(this);signature.setSingleLine(true);signature.setTextSize(17);signature.setTextColor(0xff666666);signature.setHint("✎  写下你的署名");signature.setContentDescription("导出署名");signature.setPadding(dp(16),0,dp(12),0);signature.setBackground(rounded(Color.WHITE,24));signature.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});signature.setText(options.signature);controls.addView(signature,new LinearLayout.LayoutParams(0,dp(44),1));TextView advanced=label("高级设置",14,Color.BLACK);advanced.setPadding(dp(10),0,dp(4),0);advanced.setOnClickListener(v->advanced());controls.addView(advanced,new LinearLayout.LayoutParams(-2,dp(44)));root.addView(controls);
        signature.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){options.signature=s.toString();handler.removeCallbacks(refreshPreview);handler.postDelayed(refreshPreview,250);}public void afterTextChanged(Editable e){}});
        LinearLayout footer=row();footer.setPadding(dp(16),dp(12),dp(16),dp(14));footer.setBackgroundColor(Color.WHITE);View spacer=new View(this);footer.addView(spacer,new LinearLayout.LayoutParams(0,1,1));save=label("✓ 保存",22,Color.WHITE);save.setBackground(rounded(0xff38b844,28));save.setOnClickListener(v->chooseFile());footer.addView(save,new LinearLayout.LayoutParams(dp(100),dp(44)));share=label("↗ 分享",19,0xff6696dd);share.setOnClickListener(v->share());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(44),1);footer.addView(share,sp);root.addView(footer);
        if(Build.VERSION.SDK_INT>=29)root.setForceDarkAllowed(false);
        setContentView(root);lightSystemBars();
        if(Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});root.requestApplyInsets();}
    }
    private void lightSystemBars(){
        Window window=getWindow();window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);window.setStatusBarColor(0xfff2f0f0);window.setNavigationBarColor(Color.WHITE);window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=window.getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)lightSystemBars();}
    private void advanced(){
        LinearLayout panel=column();panel.setPadding(dp(20),dp(8),dp(20),dp(8));Switch dates=new Switch(this);dates.setText("正文日期");dates.setChecked(options.dates);dates.setPadding(0,dp(12),0,dp(12));panel.addView(dates);Switch title=new Switch(this);title.setText("笔记标题");title.setChecked(options.title);title.setPadding(0,dp(12),0,dp(12));panel.addView(title);TextView size=label("正文字号",15,0xff666666);size.setGravity(Gravity.START);panel.addView(size);RadioGroup fonts=new RadioGroup(this);fonts.setOrientation(LinearLayout.HORIZONTAL);String[] names={"小","标准","大"};int[] sizes={32,40,48};RadioButton[] sizeButtons=new RadioButton[3];for(int i=0;i<3;i++){RadioButton b=new RadioButton(this);b.setText(names[i]);b.setId(View.generateViewId());sizeButtons[i]=b;fonts.addView(b);if(options.textSize==sizes[i])fonts.check(b.getId());}panel.addView(fonts);
        new AlertDialog.Builder(this).setTitle("高级设置").setView(panel).setNegativeButton("取消",null).setPositiveButton("应用",(d,w)->{options.dates=dates.isChecked();options.title=title.isChecked();for(int i=0;i<sizeButtons.length;i++)if(sizeButtons[i].isChecked())options.textSize=sizes[i];preview();}).show();
    }
    private void remember(){getSharedPreferences("export-style",MODE_PRIVATE).edit().putInt("theme",options.theme).putBoolean("dates",options.dates).putBoolean("title",options.title).putInt("textSize",options.textSize).putString("signature",options.signature).apply();}
    private void preview(){
        if(isFinishing()||isDestroyed())return;remember();final int ticket=generation.incrementAndGet();NoteExporter.Options selected=options.copy();message.setText("正在生成预览…");message.setVisibility(View.VISIBLE);
        worker.execute(()->{if(ticket!=generation.get())return;try{Bitmap bitmap=NoteExporter.preview(note,selected,540);runOnUiThread(()->{if(ticket!=generation.get()||isFinishing()||isDestroyed()){bitmap.recycle();return;}Bitmap old=displayed;displayed=bitmap;preview.setImageBitmap(bitmap);if(old!=null)old.recycle();message.setVisibility(View.GONE);});}catch(Exception e){runOnUiThread(()->{if(ticket==generation.get()&&!isDestroyed()){message.setText(e.getMessage());preview.setImageDrawable(null);if(displayed!=null){displayed.recycle();displayed=null;}}});}});
    }
    private void chooseFile(){if(busy)return;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"灵感笔记.png");try{startActivityForResult(intent,31);}catch(ActivityNotFoundException e){toast("此设备没有可用的文件保存器");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==31&&result==RESULT_OK&&data!=null&&data.getData()!=null)write(data.getData(),false);}
    private void share(){if(!busy)write(null,true);}
    private void write(Uri target,boolean sharing){
        busy=true;save.setEnabled(false);share.setEnabled(false);toast("正在生成图片…");NoteExporter.Options selected=options.copy();
        worker.execute(()->{File shared=null;try{Uri uri=target;if(sharing){File directory=new File(getCacheDir(),"export-shares");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建分享文件");File[] old=directory.listFiles();if(old!=null)for(File f:old)if(f.isFile()&&f.getName().matches("[0-9a-f-]{36}\\.png")&&System.currentTimeMillis()-f.lastModified()>24*60*60*1000L)f.delete();shared=new File(directory,UUID.randomUUID()+".png");try(OutputStream out=new FileOutputStream(shared)){NoteExporter.png(note,out,selected);}uri=new Uri.Builder().scheme("content").authority(getPackageName()+".exports").appendPath(shared.getName()).build();}else try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("无法写入所选文件");NoteExporter.png(note,out,selected);}final Uri ready=uri;runOnUiThread(()->{busy=false;save.setEnabled(true);share.setEnabled(true);if(isFinishing()||isDestroyed())return;if(sharing){Intent send=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,ready).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("笔记图片",ready));try{startActivity(Intent.createChooser(send,"分享笔记图片"));}catch(ActivityNotFoundException e){toast("没有可用的分享应用");}}else toast("图片已保存");});}catch(Exception e){if(shared!=null)shared.delete();runOnUiThread(()->{busy=false;save.setEnabled(true);share.setEnabled(true);if(!isDestroyed())toast("导出失败："+e.getMessage());});}});
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("theme",options.theme);state.putBoolean("dates",options.dates);state.putBoolean("title",options.title);state.putInt("textSize",options.textSize);state.putString("signature",options.signature);}
    @Override protected void onDestroy(){generation.incrementAndGet();handler.removeCallbacks(refreshPreview);worker.shutdown();if(preview!=null)preview.setImageDrawable(null);if(displayed!=null){displayed.recycle();displayed=null;}super.onDestroy();}
}
