package org.oxideshift.corner;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reports stay on this device until the player consents to that individual report. */
final class CrashReports {
    static final String BASE="https://195.58.58.124:18443/";
    private final Activity activity;
    private final SharedPreferences store;
    private final String version;
    private static final Object LOCK=new Object();
    private static volatile boolean sending;
    private boolean dialogOpen;
    private boolean checkingFiles;

    CrashReports(Activity activity){
        this.activity=activity;
        store=activity.getSharedPreferences("crash-inbox",Context.MODE_PRIVATE);
        PackageInfo info;
        try{info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);}
        catch(android.content.pm.PackageManager.NameNotFoundException e){throw new IllegalStateException(e);}
        version=info.versionName;
        final long installedAt=info.lastUpdateTime;
        new Thread(()->{synchronized(LOCK){
            try{readPreviousExits(installedAt);}catch(SecurityException ignored){}
        }},"check-launcher-exits").start();
    }
    private void readPreviousExits(long installedAt){
        ActivityManager manager=activity.getSystemService(ActivityManager.class);
        if(manager==null)return;
        long since=Math.max(installedAt,store.getLong("lastExit",0)),latest=since;
        for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(activity.getPackageName(),0,8)){
            long time=exit.getTimestamp();
            if(time<=since || !activity.getPackageName().equals(exit.getProcessName()))continue;
            latest=Math.max(latest,time);
            int reason=exit.getReason();
            if(reason==ApplicationExitInfo.REASON_LOW_MEMORY &&
                    exit.getImportance()>=ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)continue;
            if(reason==ApplicationExitInfo.REASON_CRASH || reason==ApplicationExitInfo.REASON_CRASH_NATIVE
                    || reason==ApplicationExitInfo.REASON_ANR || reason==ApplicationExitInfo.REASON_LOW_MEMORY){
                Map<String,Object> history=new LinkedHashMap<>();
                history.put("android_exit_reason",reason);history.put("importance",exit.getImportance());
                if(exit.getPss()>0)history.put("process_pss_kib",exit.getPss());
                if(exit.getRss()>0)history.put("process_rss_kib",exit.getRss());
                recordCrash("launcher",exit.getStatus(),"unknown","LAUNCHER",0,0,"none",null,0,history);
            }
        }
        if(latest>since)store.edit().putLong("lastExit",latest).apply();
    }
    private static String deviceLabel(String value){
        if(value==null)return "unknown";
        String clean=value.replaceAll("[^\\x20-\\x7e]"," ").trim();
        return clean.isEmpty()?"unknown":clean.substring(0,Math.min(64,clean.length()));
    }
    void record(String component,int exit,String pc,String event,int round,int height,String renderer,CornerSession.CrashState state,int fps){
        recordCrash(component,exit,pc,event,round,height,renderer,state,fps,state==null?Collections.emptyMap():state.details);
    }
    private void recordCrash(String component,int exit,String pc,String event,int round,int height,String renderer,CornerSession.CrashState state,int fps,Map<String,Object> failure){
        synchronized(LOCK){
            try{
                String id=UUID.randomUUID().toString();
                JSONObject body=new JSONObject().put("id",id).put("version",version).put("component",component)
                    .put("exit",exit).put("pc",pc).put("event",event).put("round",round).put("height",height)
                    .put("renderer",renderer).put("android",Build.VERSION.SDK_INT);
                JSONObject context=new JSONObject()
                        .put("manufacturer",deviceLabel(Build.MANUFACTURER)).put("model",deviceLabel(Build.MODEL))
                        .put("abi",deviceLabel(Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:null))
                        .put("milestone",state==null?JSONObject.NULL:state.milestone)
                        .put("elapsed_ms",state==null?JSONObject.NULL:state.elapsedMs)
                        .put("last_frame",state==null || state.lastFrame==null?JSONObject.NULL:state.lastFrame)
                        .put("call_target",state==null || state.target==null?JSONObject.NULL:state.target)
                        .put("msaa",state==null?JSONObject.NULL:state.samples)
                        .put("anisotropy",state==null?JSONObject.NULL:state.anisotropy)
                        .put("fps_limit",state==null?JSONObject.NULL:fps);
                body.put("context",context);
                body.put("details",ReportDetails.capture(activity,DiscFacts.installed(activity.getFilesDir().toPath()),failure));
                savePending(body);
            }catch(org.json.JSONException ignored){}
        }
    }
    void recordImport(DiscImport attempt,DiscImport.Failure failure){
        try{
            String id=UUID.randomUUID().toString();
            String format=failure.format!=null?failure.format:attempt.format;
            JSONObject body=new JSONObject().put("id",id).put("version",version).put("android",Build.VERSION.SDK_INT)
                    .put("reason",failure.reason.code).put("stage",failure.reason==DiscImport.Reason.COMPILER?"prepare":attempt.stage)
                    .put("format",format).put("bytes",attempt.bytes)
                    .put("title",failure.title==null?JSONObject.NULL:failure.title)
                    .put("region",failure.region==null?JSONObject.NULL:failure.region)
                    .put("revision",failure.revision==null?JSONObject.NULL:Long.valueOf(failure.revision))
                    .put("sha256",failure.hash==null?JSONObject.NULL:failure.hash);
            String message=failure.getMessage().split("\n\ndefault.xbe SHA-256",2)[0];
            body.put("details",ReportDetails.capture(activity,attempt.facts,Collections.emptyMap()));
            if(failure.reason==DiscImport.Reason.TRUNCATED || failure.reason==DiscImport.Reason.INVALID_TREE)
                message+=" Check that the image is complete and all split parts have been joined.";
            if(failure.reason==DiscImport.Reason.SPACE)message+=" Free device storage and retry.";
            if(failure.reason==DiscImport.Reason.INVALID_XBE)message+=" Try an intact backup of the original Xbox disc.";
            if(failure.reason==DiscImport.Reason.REVISION && "00000004".equals(failure.region))
                message+=" The launcher currently supports the USA/Canada release.";
            body.put("display",message);
            savePending(body);
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
    private void savePending(JSONObject body){
        synchronized(LOCK){
            String oldest=null;int count=0;
            for(String key:store.getAll().keySet())if(key.startsWith("pending.")){
                count++;
                if(oldest==null || key.compareTo(oldest)<0)oldest=key;
            }
            SharedPreferences.Editor edit=store.edit();
            if(count>=8)edit.remove(oldest);
            edit.putString("pending."+System.currentTimeMillis()+"."+body.optString("id"),body.toString()).commit();
        }
    }

    private static String importPreview(JSONObject d)throws org.json.JSONException{
        String result="Build "+d.getString("version")+" · Android API "+d.getInt("android")
                +"\nStage "+d.getString("stage")+" · Format "+d.getString("format")+" · Error "+d.getInt("reason");
        if(d.getLong("bytes")>=0)result+="\nSelected file size "+d.getLong("bytes")+" bytes";
        if(!d.isNull("title"))result+="\nXbox title "+d.getString("title")+" · Region mask "+d.getString("region")+" · XBE revision "+d.getLong("revision");
        if(!d.isNull("sha256"))result+="\ndefault.xbe SHA-256\n"+d.getString("sha256");
        return result;
    }
    private static String crashPreview(JSONObject c)throws org.json.JSONException{
        String out="\nDevice "+c.getString("manufacturer")+" "+c.getString("model")+" · Preferred ABI "+c.getString("abi");
        if(!c.isNull("milestone")){
            out+="\nFurthest milestone "+c.getString("milestone")+" · Session "+c.getLong("elapsed_ms")+" ms"
                    +"\nLast logged frame "+(c.isNull("last_frame")?"unknown":Long.toString(c.getLong("last_frame")))
                    +" · Target "+c.getInt("fps_limit")+" FPS · MSAA "+c.getInt("msaa")+"× · Filtering "+c.getInt("anisotropy")+"×";
        }
        if(!c.isNull("call_target"))out+="\nAttempted Xbox call 0x"+c.getString("call_target");
        return out;
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
    boolean showing(){return dialogOpen || checkingFiles;}
    void refresh(Runnable chooseDisc,Runnable prepareGame){
        long now=System.currentTimeMillis();
        String pending=null,accepted=null;
        for(String key:store.getAll().keySet()){
            if(key.startsWith("send.") && store.getLong("retry."+key,0)<=now && (accepted==null || key.compareTo(accepted)<0))accepted=key;
            if(key.startsWith("pending.") && (pending==null || key.compareTo(pending)<0))pending=key;
        }
        if(accepted!=null && !sending)deliver(accepted,false);
        if(store.contains("pending.import"))pending="pending.import";
        if(pending==null || showing() || !activity.hasWindowFocus() || activity.isFinishing() || activity.isDestroyed())return;
        final String key=pending,body=store.getString(key,"");
        try{
            JSONObject d=new JSONObject(body);
            boolean importing=d.has("reason");
            String problem=d.optString("display","");
            d.remove("display");
            String context;
            if(importing)context=importPreview(d);
            else{
                boolean invalidCall="game".equals(d.getString("component")) && d.getInt("exit")==41;
                context="Build "+d.getString("version")+" · Android API "+d.getInt("android")+
                    "\n"+d.getString("component")+" · exit "+d.getInt("exit")+
                    (invalidCall?" · invalid Xbox call · guest stack return ":" · code location ")+d.getString("pc")+
                    "\nLast event "+d.getString("event")+" · round "+d.getInt("round")+
                    (d.getInt("height")>0?" · "+d.getInt("height")+"p "+d.getString("renderer"):"");
                if(d.has("context"))context+=crashPreview(d.getJSONObject("context"));
            }
            if(d.has("details"))context+=ReportDetails.preview(d.getJSONObject("details"));
            String copyDetails=importing?problem+"\n\n"+context:d.has("copyTarget")?context+"\nAttempted Xbox call target "+d.getString("copyTarget"):context;
            d.remove("copyTarget");
            String uploadBody=d.toString();
            JSONObject rom=d.has("details")?d.getJSONObject("details").getJSONObject("rom"):null;
            boolean canVerify=rom!=null && "installed_only".equals(rom.optString("origin")) && rom.has("installed_xbe_sha256") && !rom.has("disc_files_sha256");
            boolean prepare=importing && d.getInt("reason")==DiscImport.Reason.COMPILER.code;
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(importing?"Disc import failed":"Send crash report?")
                .setMessage((importing?problem+"\n\nSend these diagnostics to the FightNight3OGX project server?":"Send these technical details to the FightNight3OGX project server?")+"\n\n"+context)
                .setNeutralButton(canVerify?"More options":"Copy details",null)
                .setNegativeButton(importing?(prepare?"Prepare game":"Choose another file"):"Skip",(a,b)->{
                    store.edit().remove(key).apply();
                    if(importing)(prepare?prepareGame:chooseDisc).run();
                })
                .setPositiveButton(importing?"Send report":"Send",(a,b)->{
                    String queued="send."+d.optString("id");
                    int count=0;
                    for(String item:store.getAll().keySet())if(item.startsWith("send."))count++;
                    if(count>=64){Toast.makeText(activity,"Report queue is full. Copy details or retry when online.",Toast.LENGTH_LONG).show();return;}
                    store.edit().remove(key).putString(queued,uploadBody).commit();
                    if(!sending)deliver(queued,true);
                }).create();
            dialog.setOnCancelListener(a->store.edit().remove(key).apply());
            dialog.setOnDismissListener(a->dialogOpen=false);dialogOpen=true;dialog.show();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                if(!canVerify){copy(copyDetails);return;}
                new AlertDialog.Builder(activity).setItems(new String[]{"Copy details","Check installed game files"},(options,which)->{
                    if(which==0){copy(copyDetails);return;}
                    checkingFiles=true;dialog.dismiss();
                    checkFiles(key,body,rom.optString("installed_xbe_sha256"));
                }).show();
            });
        }catch(org.json.JSONException e){store.edit().remove(key).apply();}
    }
    private void copy(String details){
        android.content.ClipboardManager clipboard=activity.getSystemService(android.content.ClipboardManager.class);
        if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("FightNight3OGX diagnostics",details));
        Toast.makeText(activity,"Copied. The report is still available to send.",Toast.LENGTH_SHORT).show();
    }
    private void checkFiles(String key,String original,String xbe){
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        AlertDialog progress=new AlertDialog.Builder(activity).setTitle("Checking installed game files")
                .setMessage("Reading your installed game to compare it with the tested USA disc. You can review the result before sending.")
                .setNegativeButton("Cancel",(d,w)->cancelled.set(true)).create();
        progress.setOnCancelListener(d->cancelled.set(true));progress.show();
        new Thread(()->{
            boolean complete=false;
            try{
                DiscFacts checked=DiscFacts.checkInstalledFiles(activity.getFilesDir().toPath(),xbe,()->cancelled.get() || activity.isDestroyed());
                JSONObject updated=new JSONObject(original),rom=updated.getJSONObject("details").getJSONObject("rom");
                rom.put("files",Integer.parseInt(checked.values.getProperty("files")));
                rom.put("payload_bytes",Long.parseLong(checked.values.getProperty("payload_bytes")));
                rom.put("disc_files_sha256",checked.values.getProperty("disc_files_sha256"));
                synchronized(LOCK){
                    if(!cancelled.get() && original.equals(store.getString(key,null)))
                        complete=store.edit().putString(key,updated.toString()).commit();
                }
            }catch(IOException | org.json.JSONException | RuntimeException ignored){}
            final boolean saved=complete;
            activity.runOnUiThread(()->{
                checkingFiles=false;
                if(activity.isDestroyed() || activity.isFinishing())return;
                progress.dismiss();
                if(!saved && !cancelled.get())Toast.makeText(activity,"Could not verify all installed files. The original report is available.",Toast.LENGTH_LONG).show();
            });
        },"check-installed-disc").start();
    }
    private void deliver(String key,boolean playerTappedSend){
        final String body=store.getString(key,null);if(body==null)return;
        sending=true;
        new Thread(()->{
            boolean ok=false,summary=false;
            try{
                JSONObject report=new JSONObject(body);
                boolean importing=report.has("reason");
                boolean detailed=report.has("context");
                String endpoint=importing?"imports":detailed?"crashes":"reports";
                boolean rich=report.has("details");
                int response=post((rich?"v3/":"v1/")+endpoint,body);
                boolean reduced=false;
                if(rich && response==404){
                    JSONObject details=report.getJSONObject("details");
                    reduced=details.getJSONObject("device").has("host_page_bytes")
                            || details.getJSONObject("device").has("presentation_displays")
                            || details.getJSONObject("failure").has("fault_location")
                            || details.getJSONObject("failure").has("native_return_offset");
                    details.getJSONObject("device").remove("host_page_bytes");
                    details.getJSONObject("device").remove("presentation_displays");
                    details.getJSONObject("failure").remove("fault_location");
                    details.getJSONObject("failure").remove("native_return_offset");
                    response=post("v2/"+endpoint,report.toString());
                }
                ok=response==201 && !reduced;
                summary=response==201 && reduced;
                if(rich && response==404){
                    report.remove("details");
                    response=post("v1/"+endpoint,report.toString());
                    summary=response==201;
                }
                if(importing && response==404){
                    // Existing inbox versions already accept numeric launcher error codes.
                    // Keep the detailed report for delivery after the server upgrade.
                    JSONObject basic=new JSONObject().put("id",report.getString("id")).put("version",report.getString("version"))
                            .put("android",report.getInt("android")).put("component","launcher").put("exit",report.getInt("reason"))
                            .put("pc","unknown").put("event","LAUNCHER").put("round",0).put("height",0).put("renderer","none");
                    summary=store.getBoolean("basic."+key,false) || post("v1/reports",basic.toString())==201;
                }else if(detailed && response==404){
                    report.remove("context");
                    summary=store.getBoolean("basic."+key,false) || post("v1/reports",report.toString())==201;
                }
            }catch(IOException | org.json.JSONException ignored){}
            finally{
                SharedPreferences.Editor update=store.edit();
                if(ok)update.remove(key).remove("retry."+key).remove("basic."+key);
                else{
                    long delay=summary?6*60*60*1000L:60000L;
                    update.putLong("retry."+key,System.currentTimeMillis()+delay);
                    if(summary)update.putBoolean("basic."+key,true);
                }
                update.commit();sending=false;
            }
            final boolean delivered=ok,basicSent=summary;
            activity.runOnUiThread(()->{
                if((delivered || playerTappedSend) && !activity.isDestroyed() && activity.hasWindowFocus())
                    Toast.makeText(activity,delivered?"Report sent. Thank you.":basicSent?"Error summary sent. Full details are queued.":"Report saved. Fight Setup will retry.",Toast.LENGTH_SHORT).show();
            });
        },"send-crash-report").start();
    }
    private int post(String path,String body)throws IOException{
        HttpURLConnection conn=open(path);
        try{
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestMethod("POST");conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            int status=conn.getResponseCode();
            return "1".equals(conn.getHeaderField("X-FightNight-Inbox"))?status:-1;
        }finally{conn.disconnect();}
    }
}
