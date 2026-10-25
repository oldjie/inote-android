package com.personal.inote;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Local handwriting attachment. The successful callback owns the returned bitmap. */
public final class DrawingPad {
    private DrawingPad(){}

    public static void show(Activity activity,boolean dark,Consumer<Bitmap> onSave){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(activity,10),dp(activity,8),dp(activity,10),dp(activity,8));root.setBackgroundColor(dark?0xff292929:0xfff3f0f1);
        int ink=dark?0xffeeeeee:0xff303030;
        TextView title=new TextView(activity);title.setText("手写小笔记");title.setTextSize(18);title.setTextColor(ink);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(activity,40)));
        DrawingView drawing=new DrawingView(activity);drawing.setContentDescription("手写画布");root.addView(drawing,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);LinearLayout controls=new LinearLayout(activity);controls.setGravity(Gravity.CENTER_VERTICAL);scroll.addView(controls);root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(activity,52)));
        String[] names={"黑色","蓝色","红色"},selectedNames={"✓ 黑色","✓ 蓝色","✓ 红色"};int[] colors={0xff202020,0xff427bcc,0xffc64f4f};Button[] choices=new Button[3];
        for(int i=0;i<3;i++){
            final int selected=i;Button button=button(activity,names[i],colors[i]);choices[i]=button;button.setContentDescription("画笔："+names[i]);controls.addView(button,new LinearLayout.LayoutParams(dp(activity,64),dp(activity,44)));
            button.setOnClickListener(v->{drawing.setInk(colors[selected]);for(int j=0;j<choices.length;j++){choices[j].setText(j==selected?selectedNames[j]:names[j]);choices[j].setSelected(j==selected);}});
        }
        choices[0].setText("✓ 黑色");choices[0].setSelected(true);
        Button undo=button(activity,"撤销",ink);undo.setContentDescription("撤销笔画");controls.addView(undo,new LinearLayout.LayoutParams(dp(activity,64),dp(activity,44)));undo.setOnClickListener(v->drawing.undo());
        Button clear=button(activity,"清空",ink);clear.setContentDescription("清空画布");controls.addView(clear,new LinearLayout.LayoutParams(dp(activity,64),dp(activity,44)));clear.setOnClickListener(v->drawing.clear());
        LinearLayout footer=new LinearLayout(activity);footer.setGravity(Gravity.CENTER_VERTICAL);root.addView(footer,new LinearLayout.LayoutParams(-1,dp(activity,48)));
        Button cancel=button(activity,"取消",ink);footer.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));cancel.setOnClickListener(v->dialog.dismiss());
        Button done=button(activity,"✓ 完成",Color.WHITE);done.setContentDescription("保存手写笔记");GradientDrawable green=new GradientDrawable();green.setColor(0xff66af4a);green.setCornerRadius(dp(activity,7));done.setBackground(green);LinearLayout.LayoutParams complete=new LinearLayout.LayoutParams(0,dp(activity,40),1);complete.setMargins(dp(activity,8),0,0,0);footer.addView(done,complete);
        done.setOnClickListener(v->{
            if(drawing.isEmpty()){Toast.makeText(activity,"请先写点什么",Toast.LENGTH_SHORT).show();return;}
            Bitmap bitmap=null;
            try{bitmap=drawing.snapshot();onSave.accept(bitmap);dialog.dismiss();}
            catch(RuntimeException failure){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();Toast.makeText(activity,"手写图片未保存，请重试",Toast.LENGTH_SHORT).show();}
        });
        dialog.setContentView(root);dialog.show();int width=Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,20),dp(activity,600));int height=(int)(activity.getResources().getDisplayMetrics().heightPixels*.86f);dialog.getWindow().setLayout(Math.max(1,width),Math.max(1,height));
    }
    private static int dp(Activity activity,float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private static Button button(Activity activity,String text,int color){Button button=new Button(activity);button.setText(text);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(color);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(0,0,0,0);button.setBackgroundColor(Color.TRANSPARENT);return button;}

    static final class DrawingView extends View {
        private static final int MAX_STROKES=500,MAX_POINTS=80000,MAX_SIDE=1600;
        private final ArrayList<Stroke> strokes=new ArrayList<>();
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Stroke current;
        private int ink=0xff202020,points,activePointer=-1;
        private float lastX,lastY;
        DrawingView(android.content.Context context){super(context);setBackgroundColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(3*context.getResources().getDisplayMetrics().density);setFocusable(true);}
        void setInk(int color){ink=color;}
        boolean isEmpty(){return strokes.isEmpty();}
        void undo(){if(!strokes.isEmpty()){Stroke removed=strokes.remove(strokes.size()-1);points-=removed.points;}current=null;activePointer=-1;invalidate();}
        void clear(){strokes.clear();current=null;points=0;activePointer=-1;invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);drawStrokes(canvas);}
        private void drawStrokes(Canvas canvas){for(Stroke stroke:strokes){paint.setColor(stroke.color);canvas.drawPath(stroke.path,paint);}}
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                if(strokes.size()>=MAX_STROKES||points>=MAX_POINTS){Toast.makeText(getContext(),"笔画已较多，请完成保存或清空后继续",Toast.LENGTH_SHORT).show();return true;}
                activePointer=event.getPointerId(0);current=new Stroke(ink);lastX=clamp(event.getX(),getWidth());lastY=clamp(event.getY(),getHeight());current.path.moveTo(lastX,lastY);current.path.lineTo(lastX+.01f,lastY);current.points=1;points++;strokes.add(current);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            }
            if(action==MotionEvent.ACTION_MOVE){int pointer=event.findPointerIndex(activePointer);if(current!=null&&pointer>=0){float x=clamp(event.getX(pointer),getWidth()),y=clamp(event.getY(pointer),getHeight());if(points<MAX_POINTS&&(Math.abs(x-lastX)+Math.abs(y-lastY)>=1)){current.path.lineTo(x,y);current.points++;points++;lastX=x;lastY=y;invalidate();}}return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==activePointer)){current=null;activePointer=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        Bitmap snapshot(){
            if(isEmpty()||getWidth()<1||getHeight()<1)throw new IllegalStateException("Canvas is empty or not laid out");
            float scale=Math.min(1f,Math.min(MAX_SIDE/(float)getWidth(),MAX_SIDE/(float)getHeight()));int width=Math.max(1,Math.round(getWidth()*scale)),height=Math.max(1,Math.round(getHeight()*scale));
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale);drawStrokes(canvas);return bitmap;
        }
        private static float clamp(float n,int max){return Math.max(0,Math.min(n,max));}
        private static final class Stroke{final Path path=new Path();final int color;int points;Stroke(int color){this.color=color;}}
    }
}
