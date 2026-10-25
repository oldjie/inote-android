package com.personal.inote;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.MotionEvent;
import junit.framework.TestCase;

public final class DrawingPadTest extends TestCase {
    public void testBoundedWhiteBitmapContainsOnlyCurrentStrokes(){
        PersonalTestRunner.instrumentation.runOnMainSync(()->{
            DrawingPad.DrawingView view=new DrawingPad.DrawingView(PersonalTestRunner.instrumentation.getTargetContext());view.layout(0,0,3200,2000);
            assertTrue(view.isEmpty());try{view.snapshot();fail("Empty canvas saved");}catch(IllegalStateException expected){}
            stroke(view,200,200,600,200);Bitmap bitmap=view.snapshot();assertEquals(1600,bitmap.getWidth());assertEquals(1000,bitmap.getHeight());assertEquals(Color.WHITE,bitmap.getPixel(10,10));assertTrue(bitmap.getPixel(200,100)!=Color.WHITE);bitmap.recycle();
            view.setInk(Color.RED);stroke(view,200,400,600,400);view.undo();bitmap=view.snapshot();assertEquals(Color.WHITE,bitmap.getPixel(200,200));assertTrue(bitmap.getPixel(200,100)!=Color.WHITE);bitmap.recycle();
            view.clear();assertTrue(view.isEmpty());try{view.snapshot();fail("Cleared canvas saved");}catch(IllegalStateException expected){}
        });
    }
    private static void stroke(DrawingPad.DrawingView view,float x,float y,float endX,float endY){
        long time=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0),move=MotionEvent.obtain(time,time+20,MotionEvent.ACTION_MOVE,endX,endY,0),up=MotionEvent.obtain(time,time+30,MotionEvent.ACTION_UP,endX,endY,0);
        try{view.onTouchEvent(down);view.onTouchEvent(move);view.onTouchEvent(up);}finally{down.recycle();move.recycle();up.recycle();}
    }
}
