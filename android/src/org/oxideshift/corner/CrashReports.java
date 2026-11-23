package org.oxideshift.corner;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import org.json.JSONObject;
import java.util.*;

/** Shows the player recent crash details locally. */
final class CrashReports {
    private final Activity activity;
    private final SharedPreferences store;
    private final String version;
    private static final Object LOCK=new Object();
    private boolean dialogOpen;

    CrashReports(Activity activity){
        this.activity=activity;
        store=activity.getSharedPreferences("crash-inbox",Context.MODE_PRIVATE);
        PackageInfo info;
        try{info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);}
        catch(android.content.pm.PackageManager.NameNotFoundException e){throw new IllegalStateException(e);}
        version=info.versionName;
        ActivityManager manager=activity.getSystemService(ActivityManager.class);
        if(manager==null)return;
        long since=Math.max(info.lastUpdateTime,store.getLong("lastExit",0)),latest=since;
        for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(activity.getPackageName(),0,8)){
            long time=exit.getTimestamp();
            if(time<=since || !activity.getPackageName().equals(exit.getProcessName()))continue;
            latest=Math.max(latest,time);
            if(exit.getReason()==ApplicationExitInfo.REASON_CRASH || exit.getReason()==ApplicationExitInfo.REASON_CRASH_NATIVE)
                record("launcher",exit.getStatus(),"unknown","LAUNCHER",0,0,"none");
        }
        if(latest>since)store.edit().putLong("lastExit",latest).apply();
    }
    void record(String component,int exit,String pc,String event,int round,int height,String renderer){
        synchronized(LOCK){
            // Include reports retained across app upgrades.
            Map<String,?> items=store.getAll();String oldest=null;int count=0;
            for(String key:items.keySet())if(key.startsWith("pending.") || key.startsWith("send.")){
                count++;
                if(oldest==null || key.compareTo(oldest)<0)oldest=key;
            }
            if(count>=8 && oldest==null)return;
            try{
                String id=UUID.randomUUID().toString();
                JSONObject body=new JSONObject().put("id",id).put("version",version).put("component",component)
                    .put("exit",exit).put("pc",pc).put("event",event).put("round",round).put("height",height)
                    .put("renderer",renderer).put("android",Build.VERSION.SDK_INT);
                SharedPreferences.Editor edit=store.edit();
                if(count>=8)edit.remove(oldest);
                edit.putString("pending."+System.currentTimeMillis()+"."+id,body.toString()).commit();
            }catch(org.json.JSONException ignored){}
        }
    }
    void refresh(){
        String pending=null;
        for(String key:store.getAll().keySet()){
            if((key.startsWith("pending.") || key.startsWith("send.")) && (pending==null || key.compareTo(pending)<0))pending=key;
        }
        if(pending==null || dialogOpen || !activity.hasWindowFocus() || activity.isFinishing() || activity.isDestroyed())return;
        final String key=pending,body=store.getString(key,"");
        try{
            JSONObject d=new JSONObject(body);
            String context="Build "+d.getString("version")+" · Android API "+d.getInt("android")+
                "\n"+d.getString("component")+" · exit "+d.getInt("exit")+" · code location "+d.getString("pc")+
                "\nLast event "+d.getString("event")+" · round "+d.getInt("round")+
                (d.getInt("height")>0?" · "+d.getInt("height")+"p "+d.getString("renderer"):"");
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Fight Night crash details")
                .setMessage(context)
                .setNegativeButton("Dismiss",(a,b)->store.edit().remove(key).apply())
                .setPositiveButton("Copy details",(a,b)->{
                    android.content.ClipboardManager clipboard=activity.getSystemService(android.content.ClipboardManager.class);
                    if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Fight Night crash details",context));
                    store.edit().remove(key).apply();
                }).create();
            dialog.setOnCancelListener(a->store.edit().remove(key).apply());
            dialog.setOnDismissListener(a->dialogOpen=false);dialogOpen=true;dialog.show();
        }catch(org.json.JSONException e){store.edit().remove(key).apply();}
    }
}
