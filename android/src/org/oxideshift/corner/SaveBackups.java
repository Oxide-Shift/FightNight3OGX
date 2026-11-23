package org.oxideshift.corner;

import android.content.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.database.Cursor;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Publishes and verifies each backup in the selected folder or public Downloads. */
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
    private final Map<String,String> candidates=new HashMap<>();
    volatile String status;
    private SaveBackups(Context context){
        app=context;state=app.getSharedPreferences("save-backups",0);
        status=state.getString("last","Automatic backups · Downloads / Fight Night Round 3");
    }
    private SaveChanges changes;
    private ScheduledFuture<?> periodic;
    private File activeSlot;
    Uri folder(){
        String value=state.getString("folder","");
        return value.isEmpty()?null:Uri.parse(value);
    }
    boolean hasFolder(){
        Uri folder=folder();
        if(folder!=null)for(UriPermission grant:app.getContentResolver().getPersistedUriPermissions())
            if(folder.equals(grant.getUri()) && grant.isReadPermission() && grant.isWritePermission())return true;
        return false;
    }
    interface Configured {void run()throws IOException;}
    boolean selectionPending(){return state.getBoolean("checkingSelection",false);}
    void configure(Uri tree,Configured ready,java.util.function.Consumer<String> failure){
        io.execute(()->{
            try{
                ContentResolver r=app.getContentResolver();
                Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                String label;
                try(Cursor c=r.query(parent,new String[]{Document.COLUMN_DISPLAY_NAME,Document.COLUMN_FLAGS},null,null,null)){
                    if(c==null || !c.moveToFirst() || (c.getInt(1)&Document.FLAG_DIR_SUPPORTS_CREATE)==0)
                        throw new IOException("Choose a writable folder");
                    label=c.getString(0);
                }
                String message="Automatic backups · "+label;
                if(!state.edit().putString("folder",tree.toString()).putString("folderLabel",label).putString("last",message).putBoolean("checkingSelection",true).commit())
                    throw new IOException("Cannot remember this folder");
                status=message;ready.run();state.edit().putBoolean("checkingSelection",false).apply();
            }catch(Exception e){status="Save folder unavailable · "+e.getMessage();failure.accept(e.getMessage());}
        });
    }
    void start(File slot){io.execute(()->{
        stopWatching();
        activeSlot=slot;candidates.clear();
        changes=new SaveChanges(slot,io,()->check(slot,true));changes.refresh();
        check(slot,true);
        periodic=io.scheduleWithFixedDelay(()->{changes.refresh();check(slot,false);},10,10,TimeUnit.SECONDS);
    });}
    private void stopWatching(){
        if(changes!=null){changes.stop();changes=null;}
        if(periodic!=null){periodic.cancel(false);periodic=null;}
        activeSlot=null;
    }
    void finish(File slot){io.execute(()->{if(slot.equals(activeSlot))stopWatching();check(slot,true);});}
    void snapshot(File slot){io.execute(()->check(slot,true));}
    private TreeSet<String> awards(){
        TreeSet<String> keys=new TreeSet<>();
        SharedPreferences medals=app.getSharedPreferences("achievements",0);
        for(Achievements.Award a:Achievements.Award.values())if(medals.getBoolean(a.name(),false))keys.add(a.name());
        return keys;
    }
    private void check(File slot,boolean settled){
        try{
            Uri destination=folder();Set<String> awards=awards();
            IOException failure=null;
            for(ProfileSaves profile:ProfileSaves.list(slot.toPath())){
                try{
                    String key=key(slot,destination,profile);
                    String fingerprint=GameLibrary.saveFingerprint(slot.toPath(),awards,profile);
                    String previous=candidates.put(key,fingerprint);
                    if(fingerprint.isEmpty() || !settled && !fingerprint.equals(previous))continue;
                    if(fingerprint.equals(state.getString("stamp."+key,"")) && present(state.getString("current."+key,"")))continue;
                    save(slot,key,fingerprint,awards,destination,profile);
                }catch(IOException e){failure=e;}
            }
            if(failure!=null)throw failure;
        }catch(Exception e){
            status="Backup pending · "+(e.getMessage()==null?"Storage unavailable":e.getMessage());
            android.util.Log.w("FightNight","Automatic save backup",e);
        }
    }
    private String key(File slot,Uri destination,ProfileSaves profile){
        return slot.getName()+"@"+destination+"#"+Uri.encode(profile.folder);
    }
    void rememberRestore(File slot,SaveExports.Entry file)throws IOException{
        for(ProfileSaves profile:ProfileSaves.list(slot.toPath())){
            if(!file.name.startsWith(profile.folder+"/")){snapshot(slot);return;}
            String key=key(slot,folder(),profile);
            state.edit().putString("current."+key,file.uri.toString())
                    .putString("stamp."+key,GameLibrary.saveFingerprint(slot.toPath(),awards(),profile)).apply();
        }
    }
    private static Uri profileDirectory(ContentResolver resolver,Uri tree,String name)throws IOException{
        String root=DocumentsContract.getTreeDocumentId(tree);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,root);
        String[] columns={Document.COLUMN_DISPLAY_NAME,Document.COLUMN_DOCUMENT_ID,Document.COLUMN_MIME_TYPE};
        Uri alternate=null;
        try(Cursor rows=resolver.query(children,columns,null,null,null)){
            if(rows==null)throw new IOException("Cannot list backup folder");
            while(rows.moveToNext()){
                if(!name.equalsIgnoreCase(rows.getString(0)))continue;
                if(!Document.MIME_TYPE_DIR.equals(rows.getString(2)))throw new IOException("A file already uses the profile name "+name);
                Uri existing=DocumentsContract.buildDocumentUriUsingTree(tree,rows.getString(1));
                if(name.equals(rows.getString(0)))return existing;
                alternate=existing;
            }
        }
        if(alternate!=null)return alternate;
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,root);
        Uri dir=DocumentsContract.createDocument(resolver,parent,Document.MIME_TYPE_DIR,name);
        if(dir==null)throw new IOException("Could not create profile folder "+name);
        return dir;
    }
    private boolean present(String uri){
        if(uri.isEmpty())return false;
        try(android.database.Cursor c=app.getContentResolver().query(Uri.parse(uri),null,null,null,null)){
            return c!=null && c.moveToFirst();
        }
    }
    private void save(File slot,String key,String fingerprint,Set<String> awards,Uri tree,ProfileSaves profile)throws Exception{
        ContentResolver resolver=app.getContentResolver();
        String date=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS",Locale.US).format(new Date());
        String id=slot.getName();
        String name="Career-"+(id.equals("saves")?"default":id.substring(0,Math.min(13,id.length())))+"-"+date+".zip";
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,DIRECTORY+profile.folder+"/");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri parent=tree==null?null:profileDirectory(resolver,tree,profile.folder);
        boolean rename=false;
        Uri uri=parent==null?resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values):
                DocumentsContract.createDocument(resolver,parent,"application/octet-stream",name+".partial");
        if(uri==null)throw new IOException("Backup folder unavailable");
        boolean visible=false;
        try{
            if(parent!=null){
                try(Cursor c=resolver.query(uri,new String[]{Document.COLUMN_FLAGS},null,null,null)){
                    rename=c!=null && c.moveToFirst() && (c.getInt(0)&Document.FLAG_SUPPORTS_RENAME)!=0;
                }
                if(!rename){
                    Uri finalUri=DocumentsContract.createDocument(resolver,parent,"application/zip",name);
                    if(finalUri==null)throw new IOException("Cannot create backup");
                    try{remove(resolver,uri,true);}finally{uri=finalUri;}
                }
            }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(OutputStream out=resolver.openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Cannot write backup");
                GameLibrary.exportSave(slot.toPath(),new DigestOutputStream(out,digest),awards,profile);
            }
            if(!fingerprint.equals(GameLibrary.saveFingerprint(slot.toPath(),awards(),profile)))return;
            byte[] expected=digest.digest(), buffer=new byte[65536];
            try(InputStream raw=resolver.openInputStream(uri)){
                if(raw==null)throw new IOException("Cannot verify backup");
                for(int n;(n=raw.read(buffer))!=-1;)digest.update(buffer,0,n);
            }
            if(!MessageDigest.isEqual(expected,digest.digest()))throw new IOException("Backup verification failed");
            if(rename){
                Uri published=DocumentsContract.renameDocument(resolver,uri,name);
                if(published==null)throw new IOException("Cannot publish backup");
                uri=published;
            }
            if(parent==null){
                values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
                if(resolver.update(uri,values,null,null)!=1)throw new IOException("Cannot publish backup");
            }
            visible=true;
            String label="Backed up · "+(tree==null?"Downloads / Fight Night Round 3":state.getString("folderLabel","Save folder"))+" / "+profile.folder+" / "+name;
            boolean recorded=state.edit().putString("current."+key,uri.toString())
                .putString("stamp."+key,fingerprint).putString("last",label).commit();
            if(!recorded)throw new IOException("Cannot record backup");
            status=label;
        }finally{if(!visible)remove(resolver,uri,tree!=null);}
    }
    private static void remove(ContentResolver r,Uri uri,boolean document)throws IOException{
        if(document)DocumentsContract.deleteDocument(r,uri);
        else r.delete(uri,null,null);
    }
}
