package org.oxideshift.corner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.BooleanSupplier;

final class ReleaseUpdates {
    static final int INSTALL_PERMISSION=4921;
    private final Activity activity;
    private final GameUpdate transfer;
    private final BooleanSupplier inSetup;
    private boolean checking,manual,resumeInstall;
    private long retryAfter,lastCheck;
    private ReleaseAsset pending;
    private String offered="",shown="";
    ReleaseUpdates(Activity activity,BooleanSupplier inSetup){
        this.activity=activity;this.inSetup=inSetup;transfer=GameUpdate.get(activity);
    }
    boolean installing(){return transfer.stage==GameUpdate.Stage.INSTALLING;}
    void permissionResult(){resumeInstall=activity.getPackageManager().canRequestPackageInstalls();
        if(!resumeInstall)notice("The download is ready. Allow installs from Fight Night to update.");}
    void tap(){
        if(!inSetup.getAsBoolean())return;
        switch(transfer.stage){
            case READY: install();return;
            case RETRY: transfer.download(transfer.asset);return;
            case IDLE: if(pending!=null){transfer.download(pending);return;}check(true);return;
            default: return;
        }
    }
    private void install(){
        if(activity.getPackageManager().canRequestPackageInstalls()){transfer.install();return;}
        try{activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+activity.getPackageName())),INSTALL_PERMISSION);}
        catch(RuntimeException e){notice("In Android settings, allow Fight Night to install unknown apps.");}
    }
    void refresh(Button button){
        GameUpdate.Stage stage=transfer.stage;
        String label;
        switch(stage){
            case DOWNLOADING: label="Downloading update · "+transfer.percent+"%";break;
            case VERIFYING: label="Verifying update…";break;
            case READY: label="Update game";break;
            case RETRY: label="Retry update download";break;
            case INSTALLING: label="Installing update…";break;
            default: label=checking?"Checking for updates…":pending==null?"Check for updates":"Download "+pending.version.name;
        }
        if(!label.contentEquals(button.getText()))button.setText(label);
        boolean idle=inSetup.getAsBoolean();
        button.setEnabled(idle && (stage==GameUpdate.Stage.READY || stage==GameUpdate.Stage.RETRY ||
                stage==GameUpdate.Stage.IDLE && !checking));
        if(!idle)return;
        if(stage==GameUpdate.Stage.IDLE && !checking)check(false);
        if(resumeInstall){resumeInstall=false;if(stage==GameUpdate.Stage.READY)install();}
        Intent confirm=transfer.takeConfirmation();
        if(confirm!=null)try{activity.startActivity(confirm);}catch(RuntimeException e){transfer.failedInstall();}
        if(!shown.equals(transfer.message)){shown=transfer.message;if(!shown.isEmpty())notice(shown);}
    }
    void check(boolean user){
        if(!inSetup.getAsBoolean() || transfer.stage!=GameUpdate.Stage.IDLE)return;
        if(!user && pending!=null && !offered.equals(pending.tag)){offer();return;}
        if(checking){manual|=user;return;}
        long now=System.currentTimeMillis(),elapsed=now-lastCheck;
        if(!user && (now<retryAfter || (elapsed>=0 && elapsed<6*60*60*1000L)))return;
        checking=true;manual=user;
        new Thread(()->{
            try{
                ReleaseVersion current=ReleaseVersion.parse(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName);
                if(current==null)throw new IllegalStateException("Unknown installed version");
                ReleaseAsset next=ReleaseAsset.newest(releases(),current);
                activity.runOnUiThread(()->complete(next,true));
            }catch(Exception e){activity.runOnUiThread(()->complete(null,false));}
        },"fight-release-check").start();
    }
    private static JSONArray releases() throws Exception{
        URL url=new URL("https://api.github.com/repos/"+ReleaseAsset.REPO+"/releases?per_page=30");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        try{
            conn.setConnectTimeout(7000);conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent","FightNight3OGX-Android");
            conn.setRequestProperty("Accept","application/vnd.github+json");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];
                for(int n;(n=in.read(buf))!=-1;){
                    if(out.size()+n>2*1024*1024)throw new java.io.IOException("Release list too large");
                    out.write(buf,0,n);
                }
                return new JSONArray(out.toString("UTF-8"));
            }
        }finally{conn.disconnect();}
    }
    private void complete(ReleaseAsset next,boolean success){
        checking=false;
        if(activity.isDestroyed() || activity.isFinishing() || transfer.stage!=GameUpdate.Stage.IDLE)return;
        if(success){pending=next;lastCheck=System.currentTimeMillis();}
        else retryAfter=System.currentTimeMillis()+15*60*1000L;
        if(!inSetup.getAsBoolean())return;
        if(next!=null && (manual || !offered.equals(next.tag)))offer();
        else if(manual)notice(success?"You're on the latest available Thor build.":"Couldn't reach GitHub. Try again later.");
    }
    private void offer(){
        final ReleaseAsset next=pending;offered=next.tag;
        new AlertDialog.Builder(activity).setTitle("A new version is available")
                .setMessage("Fight Night "+next.version.name+" is ready to download. Your career and settings carry over.")
                .setNegativeButton("Later",null).setPositiveButton("Download",(d,w)->transfer.download(next)).show();
    }
    private void notice(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
}
