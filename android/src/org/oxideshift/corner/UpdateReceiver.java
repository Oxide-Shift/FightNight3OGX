package org.oxideshift.corner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;

public final class UpdateReceiver extends BroadcastReceiver {
    static final String RESULT="org.oxideshift.corner.UPDATE_RESULT";
    @Override public void onReceive(Context c,Intent intent){
        SharedPreferences prefs=c.getSharedPreferences("launcher-update",0);
        if(RESULT.equals(intent.getAction())){
            int id=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1);
            if(id<0 || id!=prefs.getInt("session",-1))return;
            int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
            if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
                Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if(confirm!=null)GameUpdate.get(c).pendingConfirmation(confirm);
                else GameUpdate.get(c).failedInstall();
                return;
            }
            if(status!=PackageInstaller.STATUS_SUCCESS){GameUpdate.get(c).failedInstall();return;}
        }else if(!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;
        try{
            String actual=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
            if(!actual.equals(prefs.getString("requested","")))return;
            long elapsed=System.currentTimeMillis()-prefs.getLong("requestedAt",0);
            prefs.edit().clear().commit();
            java.io.File dir=new java.io.File(c.getFilesDir(),"launcher-update");
            new java.io.File(dir,"release.apk").delete();new java.io.File(dir,"release.part").delete();
            if(elapsed>=0 && elapsed<15*60*1000L)c.startActivity(new Intent(c,CornerActivity.class)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }catch(Exception ignored){}
    }
}
