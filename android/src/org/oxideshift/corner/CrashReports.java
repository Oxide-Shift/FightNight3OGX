package org.oxideshift.corner;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.SystemClock;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reports stay on this device until the player consents to that individual report. */
final class CrashReports {
    static final String BASE="https://195.58.58.124:18443/v1/";
    private final Activity activity;
    private final SharedPreferences store;
    private final String version;
    private static final Object LOCK=new Object();
    private static volatile boolean sending;
    private static volatile long nextSend;
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
            // Keep a bounded queue. A report already approved for delivery has priority.
            Map<String,?> items=store.getAll();String oldest=null;int count=0;
            for(String key:items.keySet())if(key.startsWith("pending.") || key.startsWith("send.")){
                count++;
                if(key.startsWith("pending.") && (oldest==null || key.compareTo(oldest)<0))oldest=key;
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
    private javax.net.ssl.SSLSocketFactory inboxTls;
    private synchronized HttpURLConnection open(String path)throws IOException{
        HttpURLConnection connection=(HttpURLConnection)new URL(BASE+path).openConnection();
        if(inboxTls==null)try(InputStream pem=activity.getAssets().open("crash-inbox.pem")){
            java.security.KeyStore keys=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            keys.load(null,null);
            keys.setCertificateEntry("inbox",java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(pem));
            javax.net.ssl.TrustManagerFactory trust=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            trust.init(keys);
            javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");
            tls.init(null,trust.getTrustManagers(),null);inboxTls=tls.getSocketFactory();
        }catch(java.security.GeneralSecurityException e){throw new IOException("Inbox certificate unavailable",e);}
        ((javax.net.ssl.HttpsURLConnection)connection).setSSLSocketFactory(inboxTls);
        connection.setConnectTimeout(5000);connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
        return connection;
    }
    boolean showing(){return dialogOpen;}
    void refresh(){
        long now=SystemClock.elapsedRealtime();
        String pending=null,accepted=null;
        for(String key:store.getAll().keySet()){
            if(key.startsWith("send.") && (accepted==null || key.compareTo(accepted)<0))accepted=key;
            if(key.startsWith("pending.") && (pending==null || key.compareTo(pending)<0))pending=key;
        }
        if(accepted!=null && !sending && now>=nextSend)deliver(accepted,false);
        if(pending==null || dialogOpen || !activity.hasWindowFocus() || activity.isFinishing() || activity.isDestroyed())return;
        final String key=pending,body=store.getString(key,"");
        try{
            JSONObject d=new JSONObject(body);
            String context="Build "+d.getString("version")+" · Android API "+d.getInt("android")+
                "\n"+d.getString("component")+" · exit "+d.getInt("exit")+" · code location "+d.getString("pc")+
                "\nLast event "+d.getString("event")+" · round "+d.getInt("round")+
                (d.getInt("height")>0?" · "+d.getInt("height")+"p "+d.getString("renderer"):"");
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Send crash report?")
                .setMessage("Send these technical details to the FightNight3OGX project server?\n\n"+context)
                .setNeutralButton("Copy details",null)
                .setNegativeButton("Skip",(a,b)->store.edit().remove(key).apply())
                .setPositiveButton("Send",(a,b)->{
                    String queued="send."+key.substring("pending.".length());
                    store.edit().remove(key).putString(queued,body).commit();
                    if(!sending)deliver(queued,true);
                }).create();
            dialog.setOnCancelListener(a->store.edit().remove(key).apply());
            dialog.setOnDismissListener(a->dialogOpen=false);dialogOpen=true;dialog.show();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                android.content.ClipboardManager clipboard=activity.getSystemService(android.content.ClipboardManager.class);
                    if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("FightNight3OGX crash",context));
                    Toast.makeText(activity,"Copied. The report is still available to send.",Toast.LENGTH_SHORT).show();
            });
        }catch(org.json.JSONException e){store.edit().remove(key).apply();}
    }
    private void deliver(String key,boolean playerTappedSend){
        final String body=store.getString(key,null);if(body==null)return;
        sending=true;nextSend=SystemClock.elapsedRealtime()+60000;
        new Thread(()->{
            boolean ok=false;
            try{
                HttpURLConnection conn=open("reports");
                try{
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    conn.setRequestMethod("POST");conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type","application/json");conn.setFixedLengthStreamingMode(bytes.length);
                    try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
                    ok=conn.getResponseCode()==201 && "1".equals(conn.getHeaderField("X-FightNight-Inbox"));
                }finally{conn.disconnect();}
                if(ok){store.edit().remove(key).commit();nextSend=0;}
            }catch(IOException ignored){}finally{sending=false;}
            final boolean delivered=ok;
            activity.runOnUiThread(()->{
                if((delivered || playerTappedSend) && !activity.isDestroyed() && activity.hasWindowFocus())
                    Toast.makeText(activity,delivered?"Crash report sent. Thank you.":"Report saved. Fight Setup will retry.",Toast.LENGTH_SHORT).show();
            });
        },"send-crash-report").start();
    }
}
