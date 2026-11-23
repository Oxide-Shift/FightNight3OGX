package org.oxideshift.corner;

import android.content.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** App-owned public Downloads entries. A new generation is committed before retiring an older one. */
final class SaveBackups {
    static final String DIRECTORY=Environment.DIRECTORY_DOWNLOADS+"/Fight Night Round 3/";
    private static SaveBackups instance;
    static synchronized SaveBackups get(Context context){
        if(instance==null)instance=new SaveBackups(context.getApplicationContext());
        return instance;
    }
    private final Context app;
    private final SharedPreferences state;
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> watch;
    private String candidate="";
    volatile String status;
    private SaveBackups(Context context){
        app=context;state=app.getSharedPreferences("save-backups",0);
        status=state.getString("last","Automatic backups · Downloads / Fight Night Round 3");
    }
    synchronized void start(File slot){
        if(watch!=null)watch.cancel(false);
        io.execute(()->candidate="");
        watch=io.scheduleWithFixedDelay(()->check(slot,false),4,5,TimeUnit.SECONDS);
    }
    synchronized void finish(File slot){
        if(watch!=null){watch.cancel(false);watch=null;}
        io.execute(()->check(slot,true));
    }
    void snapshot(File slot){io.execute(()->check(slot,true));}
    private TreeSet<String> awards(){
        TreeSet<String> keys=new TreeSet<>();
        SharedPreferences medals=app.getSharedPreferences("achievements",0);
        for(Achievements.Award a:Achievements.Award.values())if(medals.getBoolean(a.name(),false))keys.add(a.name());
        return keys;
    }
    private void check(File slot,boolean settled){
        try{
            String key=slot.getName();Set<String> awards=awards();
            String fingerprint=GameLibrary.saveFingerprint(slot.toPath(),awards);
            if(fingerprint.isEmpty())return;
            String stamp=key+"/"+fingerprint;
            if(!settled && !candidate.equals(stamp)){candidate=stamp;return;}
            candidate=stamp;
            if(fingerprint.equals(state.getString("stamp."+key,"")) && present(state.getString("current."+key,"")))return;
            save(slot,key,fingerprint,awards);
        }catch(Exception e){
            status="Backup pending · "+(e.getMessage()==null?"Storage unavailable":e.getMessage());
            android.util.Log.w("FightNight","Automatic save backup",e);
        }
    }
    private boolean present(String uri){
        if(uri.isEmpty())return false;
        try(android.database.Cursor c=app.getContentResolver().query(Uri.parse(uri),new String[]{"_id"},null,null,null)){
            return c!=null && c.moveToFirst();
        }
    }
    private void save(File slot,String key,String fingerprint,Set<String> awards)throws Exception{
        ContentResolver resolver=app.getContentResolver();
        String date=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date());
        String name="Career-"+(key.equals("saves")?"default":key.substring(0,Math.min(13,key.length())))+"-"+date+".zip";
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,DIRECTORY);
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IOException("Downloads unavailable");
        boolean visible=false;
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(OutputStream out=resolver.openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Cannot write backup");
                GameLibrary.exportSave(slot.toPath(),new DigestOutputStream(out,digest),awards);
            }
            if(!fingerprint.equals(GameLibrary.saveFingerprint(slot.toPath(),awards())))return;
            byte[] expected=digest.digest(), buffer=new byte[65536];
            try(InputStream raw=resolver.openInputStream(uri)){
                if(raw==null)throw new IOException("Cannot verify backup");
                for(int n;(n=raw.read(buffer))!=-1;)digest.update(buffer,0,n);
            }
            if(!MessageDigest.isEqual(expected,digest.digest()))throw new IOException("Backup verification failed");
            values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(resolver.update(uri,values,null,null)!=1)throw new IOException("Cannot publish backup");
            visible=true;
            String older=state.getString("previous."+key,""),current=state.getString("current."+key,"");
            String label="Backed up · Downloads / Fight Night Round 3 / "+name;
            boolean recorded=state.edit().putString("previous."+key,current).putString("current."+key,uri.toString())
                .putString("stamp."+key,fingerprint).putString("last",label).commit();
            if(!recorded)throw new IOException("Cannot record backup");
            status=label;
            if(!older.isEmpty())resolver.delete(Uri.parse(older),null,null);
        }finally{if(!visible)resolver.delete(uri,null,null);}
    }
}
