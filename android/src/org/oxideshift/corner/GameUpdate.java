package org.oxideshift.corner;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;

/** An accepted download belongs to the app, including while Fight Setup is recreated. */
final class GameUpdate {
    enum Stage { IDLE, DOWNLOADING, VERIFYING, READY, RETRY, INSTALLING }
    private static GameUpdate instance;
    static synchronized GameUpdate get(Context context){
        if(instance==null)instance=new GameUpdate(context.getApplicationContext());
        return instance;
    }
    final Context context;
    final SharedPreferences prefs;
    final File apk,part;
    volatile ReleaseAsset asset;
    volatile Stage stage=Stage.IDLE;
    volatile int percent;
    volatile String message="";
    private Intent confirmation;
    private GameUpdate(Context c){
        context=c;prefs=c.getSharedPreferences("launcher-update",0);
        File dir=new File(c.getFilesDir(),"launcher-update");
        apk=new File(dir,"release.apk");part=new File(dir,"release.part");
        ReleaseAsset saved=ReleaseAsset.parse(prefs.getString("tag",""),prefs.getLong("bytes",0),prefs.getString("digest",""));
        // Downloads restart from byte zero. Only a complete, identified APK can be resumed.
        part.delete();
        if(saved==null || !ReleaseAsset.REPO.equals(prefs.getString("release-repo",""))){apk.delete();return;}
        try{
            ReleaseVersion installed=ReleaseVersion.parse(c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
            if(installed==null || saved.version.compareTo(installed)<=0){apk.delete();return;}
            asset=saved;
            if(apk.length()!=saved.bytes){stage=Stage.RETRY;return;}
            stage=Stage.VERIFYING;
            new Thread(()->{
                try(InputStream in=new FileInputStream(apk)){
                    saved.copyVerified(in,null,n->{});checkPackage(saved,apk);
                    synchronized(this){if(stage==Stage.VERIFYING)stage=Stage.READY;}
                }catch(Exception e){apk.delete();failDownload();}
            },"verify-saved-update").start();
        }catch(Exception e){stage=Stage.RETRY;}
    }
    synchronized void download(ReleaseAsset next){
        if(stage!=Stage.IDLE && stage!=Stage.RETRY)return;
        asset=next;percent=0;message="";stage=Stage.DOWNLOADING;
        new Thread(()->{
            try{
                if(!prefs.edit().putString("release-repo",ReleaseAsset.REPO).putString("tag",next.tag).putLong("bytes",next.bytes).putString("digest",next.digest).commit())
                    throw new IOException("Could not save update details");
                if(!apk.getParentFile().isDirectory() && !apk.getParentFile().mkdirs())throw new IOException("Could not create directory");
                HttpURLConnection conn=openApk(next);
                try(InputStream in=conn.getInputStream(); FileOutputStream out=new FileOutputStream(part)){
                    next.copyVerified(in,out,n->percent=n);out.getFD().sync();
                }finally{conn.disconnect();}
                stage=Stage.VERIFYING;checkPackage(next,part);
                if(!part.renameTo(apk))throw new IOException("Could not store update");
                stage=Stage.READY;
            }catch(Exception e){part.delete();failDownload();}
        },"download-fight-update").start();
    }
    private synchronized void failDownload(){message="Download or verification failed. Tap to retry.";stage=Stage.RETRY;}
    private static HttpURLConnection openApk(ReleaseAsset next)throws IOException{
        URL url=new URL(next.url());
        for(int i=0;i<6;i++){
            String host=url.getHost();
            if(!"https".equals(url.getProtocol()) || url.getUserInfo()!=null || (url.getPort()!=-1 && url.getPort()!=443) ||
                    !("github.com".equalsIgnoreCase(host) || "release-assets.githubusercontent.com".equalsIgnoreCase(host) ||
                    "objects.githubusercontent.com".equalsIgnoreCase(host)))throw new IOException("Unexpected download location");
            HttpURLConnection conn=(HttpURLConnection)url.openConnection();
            try{
                conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(12000);conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent","FightNight3OGX-Android");conn.setRequestProperty("Accept-Encoding","identity");
                int code=conn.getResponseCode();
                if(code==200)return conn;
                String loc=conn.getHeaderField("Location");
                if(code<300 || code>=400 || loc==null)throw new IOException("Download unavailable");
                url=new URL(url,loc);
            }catch(IOException e){conn.disconnect();throw e;}
            conn.disconnect();
        }
        throw new IOException("Too many redirects");
    }
    private void checkPackage(ReleaseAsset file,File path)throws Exception{
        PackageManager pm=context.getPackageManager();
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo next=pm.getPackageArchiveInfo(path.getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(next==null || !installed.packageName.equals(next.packageName) || !file.version.name.equals(next.versionName) ||
                next.getLongVersionCode()<=installed.getLongVersionCode() || next.signingInfo==null || installed.signingInfo==null)
            throw new IOException("Incompatible package");
        Signature[] current=installed.signingInfo.getApkContentsSigners(), candidate=next.signingInfo.getApkContentsSigners();
        if(current.length==0 || !new HashSet<>(Arrays.asList(current)).equals(new HashSet<>(Arrays.asList(candidate))))
            throw new IOException("Incompatible signature");
    }
    synchronized void install(){
        if(stage!=Stage.READY)return;
        stage=Stage.INSTALLING;message="";
        new Thread(()->{
            PackageInstaller installer=context.getPackageManager().getPackageInstaller();int id=-1;
            try{
                int stale=prefs.getInt("session",-1);
                if(stale!=-1)try{installer.abandonSession(stale);}catch(RuntimeException ignored){}
                PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(context.getPackageName());params.setSize(asset.bytes);
                if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                id=installer.createSession(params);
                try(PackageInstaller.Session session=installer.openSession(id)){
                    try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,asset.bytes)){
                        asset.copyVerified(in,out,n->{});session.fsync(out);
                    }
                    if(!prefs.edit().putInt("session",id).putString("requested",asset.version.name)
                            .putLong("requestedAt",System.currentTimeMillis()).commit())throw new IOException("Could not save install request");
                    Intent intent=new Intent(context,UpdateReceiver.class).setAction(UpdateReceiver.RESULT);
                    int flags=PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
                    session.commit(PendingIntent.getBroadcast(context,id,intent,flags).getIntentSender());
                }
            }catch(Exception e){
                if(id!=-1)try{installer.abandonSession(id);}catch(RuntimeException ignored){}
                failedInstall();
            }
        },"install-fight-update").start();
    }
    synchronized void pendingConfirmation(Intent intent){confirmation=intent;stage=Stage.INSTALLING;}
    synchronized Intent takeConfirmation(){Intent intent=confirmation;confirmation=null;return intent;}
    synchronized void failedInstall(){
        confirmation=null;prefs.edit().remove("requested").remove("requestedAt").remove("session").apply();
        message="Update paused. Tap Update game to try again.";stage=apk.isFile()?Stage.READY:Stage.RETRY;
    }
}
