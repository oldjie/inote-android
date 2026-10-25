package com.personal.inote;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public final class QuickNoteWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){for(int id:ids)WidgetSupport.updateQuick(context,manager,id);}
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle options){WidgetSupport.updateQuick(context,manager,id);}
    @Override public void onDeleted(Context context,int[] ids){for(int id:ids)WidgetSupport.remove(context,id);}
    @Override public void onRestored(Context context,int[] oldIds,int[] newIds){WidgetSupport.restore(context,oldIds,newIds);onUpdate(context,AppWidgetManager.getInstance(context),newIds);}
}
