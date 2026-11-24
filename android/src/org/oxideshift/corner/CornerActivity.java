package org.oxideshift.corner;

import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.hardware.display.DisplayManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.Map;

public final class CornerActivity extends Activity implements DisplayManager.DisplayListener, InputManager.InputDeviceListener {
    private static final int DISC=1, SAVE=2, EXPORT_FOLDER=3, WRITE_SAVE=4, BACKUP_FOLDER=5;
    private static final class Engine {
        volatile boolean importing;
        volatile long buildStarted;
        volatile String note="";
        volatile CornerSession session;
        volatile File frame;
    }
    private static final Engine engine=new Engine();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final FightPad pad=new FightPad();
    private SharedPreferences prefs;
    private DisplayManager displays;
    private InputManager inputs;
    private LinearLayout root;
    private FrameLayout body;
    private FightMenu menu;
    private ReleaseUpdates updates;
    private CrashReports crashReports;
    private PadRumble rumble;
    private boolean resumed,awake,screenOff,hingeClosed;
    private SensorManager sensors;
    private final BroadcastReceiver powerEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            screenOff=Intent.ACTION_SCREEN_OFF.equals(intent.getAction());
            syncPower();
        }
    };
    private final SensorEventListener hinge=new SensorEventListener(){
        public void onAccuracyChanged(Sensor sensor,int accuracy){}
        public void onSensorChanged(SensorEvent event){
            float angle=event.values[0];
            boolean closed=angle<=3f || (hingeClosed && angle<12f);
            if(closed!=hingeClosed){hingeClosed=closed;syncPower();}
        }
    };
    private int hapticGeneration;
    private final MenuContext soundMenus=new MenuContext();
    private long firstPlaylistBack,lastPlaylistBack;
    private GameScreen screen;
    private TopFpsCounter fpsCounter;
    private WebView local;
    private Button back;
    private Presentation presentation;
    private FrameLayout lowerPanel;
    private AchievementDisplay achievementDisplay;
    private RingsideView ringside;
    private final java.util.HashSet<Long> backKeys=new java.util.HashSet<>();
    private CornerControls.Key touchKey;
    private boolean treating;
    private int treatX,treatY;
    private final Runnable release=()->{touchKey=null;sendPad();};
    private MediaPlayer music;
    private SaveBackups backups;
    private boolean folderPromptShown;
    private TraxOverlay traxOverlay;
    private String html,lastJson="";
    private File base,host;
    private DiscBuild discBuild;
    private boolean foreground, viewingGame,viewingCard;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        base=getFilesDir();prefs=getSharedPreferences("fight-setup",MODE_PRIVATE);
        try{GameLibrary.recoverDisc(base.toPath());}catch(IOException e){engine.note="Could not recover the previous disc installation";}
        crashReports=new CrashReports(this);
        backups=SaveBackups.get(this);
        if(!active() && !backups.selectionPending())backups.snapshot(saves());
        inputs=getSystemService(InputManager.class);
        sensors=getSystemService(SensorManager.class);
        displays=(DisplayManager)getSystemService(DISPLAY_SERVICE);
        host=new File(getApplicationInfo().nativeLibraryDir,"libfightnight_host.so");
        if(new File(getApplicationInfo().nativeLibraryDir,"libdisc_runner.so").isFile()){
            try{discBuild=new DiscBuild(this);host=discBuild.runner;}
            catch(IOException e){engine.note="Cannot read the disc build kit";}
        }
        try(InputStream in=getAssets().open("corner.html");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];for(int n;(n=in.read(buf))!=-1;)out.write(buf,0,n);html=out.toString("UTF-8");
        }catch(IOException e){throw new IllegalStateException(e);}
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(FightMenu.INK);
        back=new Button(this);back.setText("Return to fight setup");back.setOnClickListener(v->setup());root.addView(back);
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        updates=new ReleaseUpdates(this,()->awake && !viewingGame && !viewingCard && !active() && !engine.importing);
        String version="";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        menu=new FightMenu(this,prefs,version==null?"":version,()->{if(active()){
            CornerSession current=engine.session;
            new android.app.AlertDialog.Builder(this).setMessage("End this game session?")
                    .setNegativeButton("Cancel",null).setPositiveButton("End session",(dialog,which)->{
                        if(engine.session==current){current.stop();setup();}
                    }).show();
        }else choose(DISC);},
                this::saveOptions,this::prepareSaveExport,this::launch,this::showCard,()->{
            prefs.edit().remove("save").apply();engine.note="Default save selected";
        },updates::tap);
        rumble=new PadRumble(this);
        menu.soundtrack.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("music",on).apply();syncMusic();});
        menu.saveFolder.setOnClickListener(v->chooseBackupFolder());
        body.addView(menu);back.setVisibility(View.GONE);
        root.post(this::immersive);
        if(state!=null && state.getBoolean("game") && engine.frame!=null)showGame();
    }
    private File game(){
        File fresh=new File(base,"retail/game");
        return new File(fresh,"default.xbe").isFile()?fresh:new File(base,"game");
    }
    private File saves(){
        String id=prefs.getString("save","");
        return id.isEmpty()?new File(base,"saves"):new File(new File(base,"save-slots"),id);
    }
    private void choose(int type){
        if(updates.installing())return;
        if(engine.importing||active())return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(intent,type);
    }
    private void chooseBackupFolder(){
        if(engine.importing || active() || updates.installing())return;
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if(backups.folder()!=null)picker.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,backups.folder());
        startActivityForResult(picker,BACKUP_FOLDER);
    }
    private void offerBackupFolder(){
        if(folderPromptShown || !hasWindowFocus() || isFinishing() || isDestroyed())return;
        folderPromptShown=true;
        if(backups.hasFolder()){
            if(backups.selectionPending())configureBackupFolder(backups.folder());
            return;
        }
        new AlertDialog.Builder(this).setTitle("Where should we back up your career?")
                .setMessage("Choose a folder you can find after reinstalling. Each profile gets its own backup folder. On a fresh install, weâ€™ll restore your most recent career from here.")
                .setPositiveButton("Choose folder",(d,w)->chooseBackupFolder())
                .setNegativeButton("Later",null).show();
    }
    private void saveOptions(){
        if(updates.installing())return;
        if(engine.importing || active())return;
        new AlertDialog.Builder(this).setTitle("Import a Fight Night save")
            .setPositiveButton("Choose ZIP",(d,w)->choose(SAVE))
            .setNeutralButton("Find in export folder",(d,w)->findExport())
            .setNegativeButton("Cancel",null).show();
    }
    private void pickExportFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,EXPORT_FOLDER);
    }
    private File stagedExport(){return new File(getCacheDir(),"fight-night-save-export.zip");}
    private void prepareSaveExport(){
        if(engine.importing || active() || updates.installing())return;
        File source=saves(),stage=stagedExport();
        engine.importing=true;engine.note="Preparing save export";
        new Thread(()->{
            try{
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(stage))){
                    GameLibrary.exportSave(source.toPath(),out);
                }
                main.post(()->{
                    engine.importing=false;engine.note="";
                    if(isFinishing() || isDestroyed()){stage.delete();return;}
                    Intent pick=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip").putExtra(Intent.EXTRA_TITLE,"Fight Night Round 3 saves.zip");
                    startActivityForResult(pick,WRITE_SAVE);
                });
            }catch(Exception ex){
                stage.delete();engine.note="Export failed â€” "+ex.getMessage();engine.importing=false;
            }
        },"prepare-fight-save").start();
    }
    private void writeSaveExport(Uri destination){
        File stage=stagedExport();engine.importing=true;engine.note="Exporting save";
        ContentResolver resolver=getApplicationContext().getContentResolver();
        new Thread(()->{
            try(OutputStream out=resolver.openOutputStream(destination,"wt")){
                if(out==null)throw new IOException("Could not open the selected destination");
                Files.copy(stage.toPath(),out);
            }catch(Exception ex){
                engine.note="Export failed â€” "+ex.getMessage();engine.importing=false;stage.delete();return;
            }
            stage.delete();engine.note="Save exported for hakuX";engine.importing=false;
        },"write-fight-save").start();
    }
    private void findExport(){
        String saved=prefs.getString("exportFolder","");
        Uri folder=saved.isEmpty()?backups.folder():Uri.parse(saved);
        if(folder==null)pickExportFolder();else scanExportFolder(folder);
    }
    private void scanExportFolder(Uri tree){
        if(engine.importing || active())return;
        engine.importing=true;engine.note="Looking for Fight Night saves";
        ContentResolver resolver=getContentResolver();
        new Thread(()->{
            java.util.List<SaveExports.Entry> found=new java.util.ArrayList<>();
            String failure=null;
            try{found=SaveExports.find(resolver,tree);}catch(Exception e){failure="This folder could not be read.";}
            final java.util.List<SaveExports.Entry> choices=found;
            String message=failure!=null?failure:"No Fight Night save ZIP was found in this folder.";
            main.post(()->{
                engine.importing=false;engine.note="";
                if(isFinishing()||isDestroyed()||active())return;
                AlertDialog.Builder dialog=new AlertDialog.Builder(this)
                    .setNeutralButton("Change folder",(d,w)->pickExportFolder()).setNegativeButton("Cancel",null);
                if(choices.isEmpty())dialog.setTitle("Find an exported save").setMessage(message)
                    .setPositiveButton("Choose ZIP",(d,w)->choose(SAVE));
                else{
                    String[] labels=new String[choices.size()];
                    for(int i=0;i<labels.length;i++)labels[i]=choices.get(i).name;
                    dialog.setTitle("Choose a Fight Night save").setItems(labels,(d,w)->importDocument(SAVE,choices.get(w).uri));
                }
                dialog.show();
            });
        },"find-fight-save").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==BACKUP_FOLDER){
            if(result==RESULT_OK && data!=null && data.getData()!=null){
                Uri uri=data.getData();
                try{
                    getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    configureBackupFolder(uri);
                }catch(SecurityException e){engine.note="Folder access unavailable. Choose a writable folder under Save & sound.";}
            }
            return;
        }
        if(request==WRITE_SAVE){
            if(result==RESULT_OK && data!=null && data.getData()!=null)writeSaveExport(data.getData());
            else{stagedExport().delete();engine.note="";}
            return;
        }
        if(request==ReleaseUpdates.INSTALL_PERMISSION){updates.permissionResult();return;}
        if(result!=RESULT_OK||data==null||data.getData()==null||engine.importing||active())return;
        Uri uri=data.getData();
        if(request==EXPORT_FOLDER){
            try{
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("exportFolder",uri.toString()).apply();
            }catch(SecurityException ignored){}
            scanExportFolder(uri);
        }else if(request==DISC || request==SAVE)importDocument(request,uri);
    }
    private String importCareer(Uri uri,boolean requireProfile)throws IOException{
        return SaveExports.importDocument(getContentResolver(),uri,base.toPath(),keys->{
            SharedPreferences.Editor restored=getSharedPreferences("achievements",MODE_PRIVATE).edit();
            for(Achievements.Award award:Achievements.Award.values())
                if(keys.contains(award.name()))restored.putBoolean(award.name(),true);
            restored.apply();
        },requireProfile);
    }
    private void selectImported(String slot)throws IOException{
        if(!prefs.edit().putString("save",slot).putBoolean("savePromptSeen",true).commit())
            throw new IOException("Could not select the restored career");
    }
    private void configureBackupFolder(Uri tree){
        if(engine.importing || active())return;
        engine.importing=true;engine.note="Checking save backup folder";
        File current=saves();
        backups.configure(tree,()->{
            try{
                if(!prefs.getBoolean("savePromptSeen",false) && prefs.getString("save","").isEmpty() &&
                        ProfileSaves.list(current.toPath()).isEmpty()){
                    java.util.List<SaveExports.Entry> files=SaveExports.candidates(getContentResolver(),tree);
                    SaveExports.Entry restored=SaveExports.restoreNewest(files,uri->importCareer(uri,true),this::selectImported);
                    if(restored!=null){
                        engine.note="Restored "+restored.name;
                        backups.rememberRestore(saves(),restored);return;
                    }
                    engine.note=files.isEmpty()?"Save backup folder ready":"No usable backup found. Choose a save ZIP or start fresh.";
                }else{
                    backups.snapshot(current);
                    engine.note="Save backup folder ready";
                }
            }finally{engine.importing=false;}
        },error->{engine.note="Save folder unavailable Â· "+error;engine.importing=false;});
    }
    private void importDocument(int request,Uri uri){
        if(engine.importing||active())return;
        Context app=getApplicationContext();File files=base;
        engine.importing=true;engine.note=request==DISC?"Opening Xbox ISO":"Opening save archive";
        engine.buildStarted=request==DISC?SystemClock.elapsedRealtime():0;
        new Thread(()->{
            DiscImport attempt=new DiscImport(message->engine.note=message);
            try {
                if(request==DISC){
                    try(android.database.Cursor meta=app.getContentResolver().query(uri,
                            new String[]{android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE},null,null,null)){
                        if(meta!=null && meta.moveToFirst()){
                            int name=meta.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            int size=meta.getColumnIndex(android.provider.OpenableColumns.SIZE);
                            if(name>=0)attempt.filenameHint(meta.getString(name));
                            if(size>=0 && !meta.isNull(size))attempt.bytes=Math.max(-1,meta.getLong(size));
                        }
                    }catch(RuntimeException ignored){/* Some providers supply only a readable descriptor. */}
                    AssetFileDescriptor descriptor=app.getContentResolver().openAssetFileDescriptor(uri,"r");
                    if(descriptor==null)throw new FileNotFoundException();
                    GameLibrary.Status status=attempt;
                    GameLibrary.Prepare prepare=discBuild==null?null:(xbe,progress)->{
                        try{return discBuild.prepare(xbe.toFile(),progress);}
                        catch(Exception e){throw new DiscImport.Failure(DiscImport.Reason.COMPILER,"The disc was verified, but game preparation stopped. Tap Prepare game to retry.");}
                    };
                    try(AssetFileDescriptor fd=descriptor;
                        android.os.ParcelFileDescriptor.AutoCloseInputStream raw=new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                            android.os.ParcelFileDescriptor.dup(fd.getFileDescriptor()))){
                        java.nio.channels.FileChannel channel=raw.getChannel();
                        long size=-1,offset=fd.getStartOffset();
                        try{
                            long available=channel.size()-offset;
                            if(available>0 && channel.read(java.nio.ByteBuffer.allocate(1),offset)==1)
                                size=fd.getDeclaredLength()<0?available:fd.getDeclaredLength();
                        }catch(IOException ignored){/* A pipe-backed provider supplies a stream below. */}
                        String result;
                        if(size>0){attempt.bytes=size;result=GameLibrary.importDisc(channel,offset,size,files.toPath(),status,prepare);}
                        else try(InputStream in=fd.createInputStream()){
                            result=GameLibrary.importDisc(in,fd.getLength(),files.toPath(),status,prepare);
                        }
                        engine.note=result+"  Â·  Disc to ready "+DiscBuild.duration(SystemClock.elapsedRealtime()-engine.buildStarted);
                    }
                }else{
                    selectImported(importCareer(uri,false));
                    backups.snapshot(saves());
                    engine.note="Imported save selected";
                }
            }catch(Exception e){
                if(request==DISC){
                    DiscImport.Failure failure=attempt.explain(e);
                    engine.note=failure.getMessage();
                    crashReports.recordImport(attempt,failure);
                    return;
                }
                String reason=e.getMessage();
                if(e instanceof java.util.zip.ZipException)reason="The ZIP could not be opened. Choose the original export file.";
                if(reason==null || reason.trim().isEmpty())reason=e.getClass().getSimpleName();
                engine.note="Import failed â€” "+reason;
                final String details=reason;
                if(request==SAVE)main.post(()->{
                    if(isFinishing() || isDestroyed() || !resumed)return;
                    new AlertDialog.Builder(this).setTitle("Save import failed")
                        .setMessage(details).setPositiveButton("OK",null).show();
                });
            }
            finally{engine.buildStarted=0;engine.importing=false;}
        },"fight-night-import").start();
    }
    private boolean active(){CornerSession s=engine.session;return s!=null&&s.active();}
    private void launch(){
        if(updates.installing())return;
        if(active()){showGame();return;}
        File disc=game(),xbe=new File(disc,"default.xbe"),save=saves();
        if(!xbe.isFile()||!host.canExecute()||engine.importing)return;
        try{DiscImage.checkInstalledMenu(disc.toPath());}
        catch(IOException e){
            DiscImport attempt=new DiscImport(message->engine.note=message);
            attempt.stage("verify");
            DiscImport.Failure issue=attempt.explain(e);
            engine.note=issue.getMessage();
            new Thread(()->{
                attempt.facts=DiscFacts.installed(base.toPath());
                crashReports.recordImport(attempt,issue);
            },"report-installed-disc").start();
            return;
        }
        if(discBuild!=null && discBuild.needsPreparation()){
            engine.importing=true;
            engine.note="Opening the installed disc";engine.buildStarted=SystemClock.elapsedRealtime();
            new Thread(()->{
                try{engine.note=discBuild.prepare(xbe,message->engine.note=message);}
                catch(Exception e){
                    DiscImport attempt=new DiscImport(message->engine.note=message);
                    attempt.stage("prepare");attempt.facts=DiscFacts.installed(base.toPath());
                    DiscImport.Failure issue=attempt.explain(e);
                    engine.note=issue.getMessage();crashReports.recordImport(attempt,issue);
                }
                finally{engine.buildStarted=0;engine.importing=false;}
            },"prepare-xbox-disc").start();
            return;
        }
        if(!prefs.getBoolean("savePromptSeen",false)){
            boolean installed;
            try{getPackageManager().getPackageInfo("com.rfandango.haku_x",0);installed=true;}
            catch(android.content.pm.PackageManager.NameNotFoundException e){installed=false;}
            String text=installed?"hakuX is installed. In hakuX, open Xbox hard disk and export your Fight Night saves. Choose the resulting ZIP here."
                    :"Choose an Xbox save ZIP or a hakuX backup to bring your boxer and career into the game.";
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Bring your career?")
                .setMessage(text+" Imported saves open in a separate slot.")
                .setPositiveButton("Choose save ZIP",(d,w)->choose(SAVE))
                .setNeutralButton("Find in folder",(d,w)->findExport())
                .setNegativeButton(save.isDirectory()?"Use current save":"Start fresh",(d,w)->{
                    prefs.edit().putBoolean("savePromptSeen",true).apply();launch();
                }).create();
            dialog.show();
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL})
                dialog.getButton(which).setTypeface(menu.fonts().brush);
            return;
        }
        if(!save.isDirectory()&&!save.mkdirs()){engine.note="Cannot open the save folder";return;}
        try {
            File folder=new File(getCacheDir(),"video");
            Files.createDirectories(folder.toPath());
            File frame=File.createTempFile("session-",".rgba",folder);
            File previous=engine.frame;
            engine.frame=frame;
            if(previous!=null)Files.deleteIfExists(previous.toPath());
            String[] command=discBuild==null
                    ? new String[]{host.getAbsolutePath(),xbe.getAbsolutePath(),disc.getAbsolutePath(),save.getAbsolutePath(),"--corner"}
                    : new String[]{host.getAbsolutePath(),discBuild.playableModule().getAbsolutePath(),xbe.getAbsolutePath(),disc.getAbsolutePath(),save.getAbsolutePath(),"--corner"};
            CornerSession next=new CornerSession(base,command);
            next.installMenuFrom(disc);
            next.musicAssets=getAssets();
            next.musicOptions(prefs.getBoolean("fightMusic",false),prefs.getBoolean("expandedMusic",false));
            String model=android.os.Build.MODEL+" "+android.os.Build.DEVICE+" "+android.os.Build.PRODUCT;
            if(model.toLowerCase(java.util.Locale.ROOT).contains("thor")){
                android.content.res.AssetManager assets=getAssets();
                next.thorGuide=(name,length)->{
                    byte[] bytes=new byte[length];
                    try(java.io.DataInputStream in=new java.io.DataInputStream(assets.open(name))){in.readFully(bytes);}
                    return bytes;
                };
            }
            CrashReports reports=crashReports;
            final int fpsLimit=DisplayCadence.target(menu.pacing.index());
            next.onCrash((exit,pc,event,round,pixels,soft,state)->reports.record("game",exit,pc,event,round,pixels,soft?"software":"vulkan",state,fpsLimit));
            SharedPreferences medals=getSharedPreferences("achievements",MODE_PRIVATE);
            next.achievements=new Achievements(key->medals.getBoolean(key,false),key->{medals.edit().putBoolean(key,true).apply();backups.snapshot(save);});
            next.setFrameFile(frame);
            int quality=menu.qualityValue(),height=FightMenu.HEIGHTS[menu.resolution.getSelectedItemPosition()];
            if(height==0){
                android.graphics.Rect bounds=getWindowManager().getCurrentWindowMetrics().getBounds();
                height=Math.max(480,Math.min(1080,Math.min(bounds.height(),bounds.width()*3/4)))/6*6;
            }
            next.setWidescreen(menu.aspect.getSelectedItemPosition()==0);
            next.setVideo(height,quality,menu.renderer.getSelectedItemPosition()==1);
            engine.session=next;engine.note="Starting original Xbox";backups.start(save);
            new Thread(()->{try{next.run();}finally{backups.finish(save);}},"fight-night-host").start();
            showGame();
        }catch(IOException e){engine.note="Cannot prepare video Â· "+e.getMessage();}
    }
    private void bindRumble(){
        int generation=++hapticGeneration;
        if(rumble==null)return;
        rumble.stop();
        CornerSession s=engine.session;
        if(s==null)return;
        int strength=menu.rumble.getSelectedItemPosition();
        if(!acceptsPad() || strength<=0){s.motors(null);return;}
        s.motors(new CornerSession.Motors(){
            public void changed(int a,int b,int ms){main.post(()->{
                if(generation==hapticGeneration && s==engine.session)rumble.apply(a,b,ms,strength);
            });}
            public void callout(){main.post(()->{
                if(generation==hapticGeneration && s==engine.session)rumble.callout(strength);
            });}
            public void count(int value){main.post(()->{
                if(generation==hapticGeneration && s==engine.session)rumble.count(value,strength);
            });}
        });
    }
    private void showGame(){
        if(engine.frame==null||engine.session==null||viewingGame)return;
        removeLocal();menu.setVisibility(View.GONE);viewingCard=false;viewingGame=true;sendPad();bindRumble();
        firstPlaylistBack=lastPlaylistBack=0;
        screen=new GameScreen(this,engine.frame,menu.qualityValue(),prefs.getInt("aspect",0)==0,engine.session.presentationHeight(),DisplayCadence.target(menu.pacing.index()),menu.vsync.isChecked(),engine.session);
        updateLower();
        screen.setKeepScreenOn(awake);
        body.addView(screen);
        if(prefs.getBoolean("showFpsCounter",false)){
            fpsCounter=new TopFpsCounter(this,screen,engine.session);
            FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
            int inset=Math.round(5*getResources().getDisplayMetrics().density);
            position.setMargins(inset,inset,0,0);
            body.addView(fpsCounter,position);
            fpsCounter.running(awake);
        }
        achievementDisplay=new AchievementDisplay(this,body,menu.fonts(),menu::mainMenuArt,engine.session,game(),prefs.getInt("aspect",0)==0,this::neutralPad);
        CornerSession owner=engine.session;
        owner.onFrontChanged(()->main.post(()->{
            if(viewingGame && screen!=null && owner==engine.session && ringside!=null)ringside.frontChanged();
        }));
        owner.onMenuSelection(stem->main.post(()->{
            if(owner!=engine.session || screen==null || !viewingGame || !awake || owner.cabinetOpen || owner.leaveTrax)return;
            if(owner.browsingTrax || owner.frontPage==CornerSession.FRONT_TRAX){owner.previewTrack(stem);return;}
            if(owner.frontPage>=0)return;
            LiveFrame frame=screen.latestFrame();
            MenuContext.Page page=frame==null?null:soundMenus.identify(frame);
            if(page!=null && "EA Trax".equals(page.title))owner.previewTrack(stem);
        }));
        owner.onSong(()->main.post(this::syncTraxOverlay));
        engine.session.onCabinet(()->main.post(this::refresh));
        back.setVisibility(View.GONE);immersive();syncPower();
    }
    private void syncTraxOverlay(){
        CornerSession owner=engine.session;
        boolean gameVisible=screen!=null && viewingGame && awake && owner!=null && owner.active();
        boolean routed=gameVisible && (lowerPanel!=null || owner.continuousMusic());
        if(owner!=null)owner.setTrax(routed);
        FrameLayout host=routed?(lowerPanel!=null?lowerPanel:body):null;
        if(traxOverlay!=null && traxOverlay.getParent()!=host){
            traxOverlay.detach();traxOverlay=null;
        }
        if(host==null)return;
        if(traxOverlay==null)traxOverlay=new TraxOverlay(host,menu.fonts(),host==body);
        traxOverlay.show(owner.introMovie()==0?owner.song():null,menu.traxArt(),
                lowerPanel!=null && ringside!=null?ringside.traxInset():0);
    }
    private void closeAchievements(){
        if(engine.session!=null){engine.session.onSong(null);engine.session.onMenuSelection(null);engine.session.onFrontChanged(null);}
        if(traxOverlay!=null){traxOverlay.detach();traxOverlay=null;}
        if(achievementDisplay!=null){achievementDisplay.close();achievementDisplay=null;}
    }
    private void showCard(){
        if(viewingGame||presentation!=null)return;
        if(local==null){local=card(this);body.addView(local);}
        viewingCard=true;menu.setVisibility(View.GONE);back.setVisibility(View.VISIBLE);lastJson="";refresh();
    }
    private void removeLocal(){if(local!=null){body.removeView(local);local.destroy();local=null;}}
    private void setup(){
        closeAchievements();neutralPad();
        if(fpsCounter!=null){fpsCounter.running(false);body.removeView(fpsCounter);fpsCounter=null;}
        if(screen!=null){screen.onPause();body.removeView(screen);screen=null;}
        updateLower();
        removeLocal();viewingGame=false;bindRumble();viewingCard=false;back.setVisibility(View.GONE);menu.setVisibility(View.VISIBLE);syncPower();
    }
    @Override public void onBackPressed(){
        if(viewingCard || viewingGame){
            if(achievementDisplay!=null)achievementDisplay.rememberOpenPage();
            setup();
        }else super.onBackPressed();
    }
    private static void fullscreen(Window window){
        if(window==null)return;
        View decor=window.peekDecorView();
        if(decor==null || !decor.isAttachedToWindow())return;
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams attrs=window.getAttributes();
        attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attrs);window.setDecorFitsSystemWindows(false);
        WindowInsetsController bars=decor.getWindowInsetsController();
        if(bars!=null){
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(WindowInsets.Type.systemBars());
        }
    }
    private void immersive(){fullscreen(getWindow());if(presentation!=null)fullscreen(presentation.getWindow());}
    @Override public void onWindowFocusChanged(boolean focus){
        super.onWindowFocusChanged(focus);
        if(focus){immersive();finishMusicExit();}else if(viewingGame)neutralPad();
        bindRumble();
    }
    @Override public void onInputDeviceAdded(int id){}
    @Override public void onInputDeviceChanged(int id){forgetDevice(id);}
    @Override public void onInputDeviceRemoved(int id){forgetDevice(id);}
    private void forgetDevice(int id){
        if(pad.remove(id))sendPad();
        rumble.forget(id);
    }
    private boolean acceptsPad(){return awake && viewingGame && hasWindowFocus() && engine.session!=null;}
    private boolean pauseMenu(){
        if(screen==null || engine.session==null || !engine.session.inLiveBout())return false;
        LiveFrame frame=screen.latestFrame();
        return frame!=null && frame.pauseRow()>=0 && screen.current(frame);
    }
    private void finishMusicExit(){
        CornerSession session=engine==null?null:engine.session;
        if(session==null || !session.leaveTrax){firstPlaylistBack=lastPlaylistBack=0;return;}
        if(!acceptsPad() || screen==null)return;
        long now=SystemClock.uptimeMillis();
        if(firstPlaylistBack!=0 && now-lastPlaylistBack<750)return;
        LiveFrame frame=screen.latestFrame();
        MenuContext.Page page=frame==null?null:soundMenus.identify(frame);
        if(frame!=null && !screen.current(frame))return;
        if(MenuContext.isMain(page) || (firstPlaylistBack!=0 && now-firstPlaylistBack>=5000)){
            session.leaveTrax=false;firstPlaylistBack=lastPlaylistBack=0;return;
        }
        // The underlying Xbox menu can still be animating when the Music window closes.
        // Only repeat Back while the playlist itself is still visible.
        if(firstPlaylistBack!=0 && (page==null || !"EA Trax".equals(page.title)))return;
        if(press(CornerControls.Key.BACK)){
            if(firstPlaylistBack==0)firstPlaylistBack=now;
            lastPlaylistBack=now;
        }
    }
    private void sendPad(){
        if(viewingGame && engine.session!=null)engine.session.controller(pad.report(touchKey,treating,treatX,treatY));
    }
    private boolean press(CornerControls.Key key){
        if(!acceptsPad()||!active()||touchKey!=null)return false;
        if(key==CornerControls.Key.B && achievementDisplay!=null && achievementDisplay.backFromTrax())return true;
        touchKey=key==CornerControls.Key.B && pauseMenu()?CornerControls.Key.START:key;
        sendPad();
        boolean rootDirection=engine.session.frontPage>=0 && key.index==0 && key.mask<=8;
        main.postDelayed(release,rootDirection?64:215);return true;
    }
    private void treatmentStick(boolean down,int x,int y){
        if(down && (!acceptsPad()||!active()))return;
        if(!down&&!treating)return;
        treating=down;treatX=x;treatY=y;sendPad();
    }
    private void releaseTouch(){
        main.removeCallbacks(release);touchKey=null;treating=false;
        if(ringside!=null)ringside.releaseInput();
        sendPad();
    }
    private void neutralPad(){backKeys.clear();pad.clear();releaseTouch();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_BACK && (viewingGame || viewingCard)){
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled())onBackPressed();
            return true;
        }
        if(code==KeyEvent.KEYCODE_BUTTON_B){
            long button=((long)event.getDeviceId()<<32)|code;
            if(event.getAction()==KeyEvent.ACTION_UP && backKeys.remove(button)){
                if(!event.isCanceled() && acceptsPad() && achievementDisplay!=null)achievementDisplay.backFromTrax();
                return true;
            }
            if(event.getAction()==KeyEvent.ACTION_DOWN && acceptsPad()){
                if(event.getRepeatCount()>0 && backKeys.contains(button))return true;
                if(event.getRepeatCount()==0){
                    backKeys.remove(button);
                    if(engine.session.browsingTrax || (pauseMenu() && press(CornerControls.Key.START))){
                        backKeys.add(button);return true;
                    }
                }
            }
        }
        if(acceptsPad() && (event.getAction()==KeyEvent.ACTION_DOWN||event.getAction()==KeyEvent.ACTION_UP)
                && pad.key(event,event.getAction()==KeyEvent.ACTION_DOWN)){
            if(event.getDeviceId()>=0)rumble.use(event.getDeviceId());
            sendPad();return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){
        if(acceptsPad()&&pad.motion(event)){
            if(event.getDeviceId()>=0)rumble.use(event.getDeviceId());
            sendPad();return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }
    private WebView card(Context context){
        WebView view=new WebView(context);WebSettings settings=view.getSettings();
        settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);view.setBackgroundColor(FightMenu.INK);
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return true;}
            @Override public void onPageFinished(WebView v,String url){lastJson="";refresh();}
        });
        String scoreHtml=html;
        File stencil=DiscFonts.stencilFile(game());
        if(stencil!=null)try{
            String data=android.util.Base64.encodeToString(Files.readAllBytes(stencil.toPath()),android.util.Base64.NO_WRAP);
            scoreHtml=html.replace("/* imported-stencil */","@font-face{font-family:RingStencil;src:url(data:font/ttf;base64,"
                    +data+") format('truetype');font-weight:100 900}");
        }catch(IOException ignored){}
        view.loadDataWithBaseURL("https://corner.invalid/",scoreHtml,"text/html","UTF-8",null);return view;
    }
    private void closeLower(){
        if(achievementDisplay!=null && lowerPanel!=null)achievementDisplay.detach(lowerPanel);
        lowerPanel=null;
        if(ringside!=null){releaseTouch();ringside.activate(false);}ringside=null;
        if(presentation!=null){presentation.dismiss();presentation=null;}
    }
    private void updateLower(){
        if(ringside==null)return;
        ringside.follow(screen,engine.session);
        ringside.activate(awake);
    }
    private void layout(){
        if(!foreground)return;
        Display target=null;
        for(Display d:displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))
            if(d.isValid()&&d.getDisplayId()!=getDisplay().getDisplayId()){target=d;break;}
        if(target!=null&&presentation!=null&&presentation.isShowing()&&target.getDisplayId()==presentation.getDisplay().getDisplayId())return;
        closeLower();
        if(target!=null)try{
            presentation=new Presentation(this,target);
            Context ctx=presentation.getContext();FrameLayout panel=new FrameLayout(ctx);
            lowerPanel=panel;panel.setBackgroundColor(RingsideView.INK);
            ringside=new RingsideView(ctx,this::press,this::treatmentStick);
            panel.addView(ringside,new FrameLayout.LayoutParams(-1,-1));
            presentation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            updateLower();presentation.setContentView(panel);presentation.show();
            Window window=presentation.getWindow();window.setLayout(-1,-1);panel.post(()->fullscreen(window));
            if(viewingCard)setup();
        }catch(WindowManager.InvalidDisplayException e){closeLower();}
        lastJson="";refresh();
    }
    private void syncMusic(){
        syncTraxOverlay();
        float volume=.12f;
        boolean wanted=awake && !viewingGame && !viewingCard && prefs.getBoolean("music",true);
        if(!wanted){
            if(music!=null && music.isPlaying())music.pause();
            return;
        }
        if(music!=null){if(!music.isPlaying())music.start();return;}
        try(AssetFileDescriptor fd=getAssets().openFd("ring-focus-loop.ogg")){
            music=new MediaPlayer();music.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            music.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());music.setLooping(true);
            music.setVolume(volume,volume);music.prepare();
            android.media.VolumeShaper fade=music.createVolumeShaper(new android.media.VolumeShaper.Configuration.Builder()
                    .setDuration(1200).setCurve(new float[]{0,1},new float[]{0,1}).build());
            fade.apply(android.media.VolumeShaper.Operation.PLAY);music.start();
        }catch(Exception e){android.util.Log.w("FightNight","Launcher music could not start",e);if(music!=null)music.release();music=null;}
    }
    private void refresh(){
        if(!foreground)return;
        syncMusic();
        if(viewingGame && screen!=null && local==null && active()){
            // Both displays are native. Building the WebView status and probing the
            // installed disc on the UI thread here delays controller dispatch.
            if(awake && achievementDisplay!=null)achievementDisplay.refresh(lowerPanel);
            if(ringside!=null){
                ringside.useFonts(menu.fonts());ringside.setCornerArt(menu.cornerArt());
                ringside.setTrainingArt(menu.trainingArt());ringside.setCareerArt(menu.careerArt());
            }
            finishMusicExit();return;
        }
        CornerSession session=engine.session;boolean ready=new File(game(),"default.xbe").isFile(),live=active();
        Map<String,Object> data=session==null?new CornerSession(base).snapshot():session.snapshot();
        String info=engine.note;
        if(info.isEmpty())info=ready?"Xbox disc ready":"Choose your original Xbox ISO to install Fight Night.";
        if(live)info="Xbox session Â· "+data.get("phase");
        if(session!=null&&!live&&!engine.importing&&"Closed".equals(data.get("link"))&&engine.note.startsWith("Starting"))
            info="Session ended Â· "+data.get("reason");
        if(!host.isFile()&&ready)info="This package has no Fight Night runtime";
        if(!viewingGame){
            menu.showBuildProgress(info,engine.importing?engine.buildStarted:0);
            menu.setKeepScreenOn(engine.importing && engine.buildStarted!=0);
            menu.showState(info,ready,engine.importing || updates.installing(),live,host.canExecute(),!prefs.getString("save","").isEmpty(),presentation!=null,
                    discBuild!=null && discBuild.needsPreparation());
            menu.backupStatus.setText(backups.status);
            updates.refresh(menu.updates);
        }
        if(ready)menu.loadDiscPoster(game());
        if(ready && ringside!=null)ringside.useFonts(menu.fonts());
        if(ringside!=null){ringside.setCornerArt(menu.cornerArt());ringside.setTrainingArt(menu.trainingArt());ringside.setCareerArt(menu.careerArt());}
        if(viewingGame&&screen!=null){
            if(awake && live && achievementDisplay!=null)achievementDisplay.refresh(lowerPanel);
            finishMusicExit();
            data.put("output",screen.output());
            if(!live && "Closed".equals(data.get("link")))setup();
        }else if(viewingCard)back.setText("Return to fight setup");
        if(!live && resumed && awake && !viewingGame && !viewingCard && !engine.importing && !updates.installing()){
            crashReports.refresh(()->choose(DISC),this::launch);
            if(!crashReports.showing())offerBackupFolder();
        }
        if(local==null)return;
        String json=new JSONObject(data).toString();
        if(!json.equals(lastJson)){lastJson=json;WebView v=local;if(v!=null)v.evaluateJavascript("window.cornerUpdate && window.cornerUpdate("+json+")",null);}
    }
    private final Runnable tick=new Runnable(){
        public void run(){if(awake){refresh();main.postDelayed(this,750);}}
    };
    private void syncPower(){
        Display display=getDisplay();
        int state=display==null?Display.STATE_UNKNOWN:display.getState();
        boolean visible=state==Display.STATE_ON || state==Display.STATE_UNKNOWN || state==Display.STATE_VR;
        boolean next=foreground && resumed && !screenOff && !hingeClosed && visible
                && getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        boolean changed=next!=awake;
        awake=next;
        if(changed && !next)neutralPad();
        if(next && updates!=null && !viewingCard && !viewingGame && !active() && !engine.importing)updates.check(false);
        if(engine.session!=null)engine.session.setSleeping(!next || !viewingGame);
        if(changed){
            main.removeCallbacks(tick);
            if(fpsCounter!=null)fpsCounter.running(next);
            if(screen!=null){
                screen.setKeepScreenOn(next);
                if(next)screen.onResume();else screen.onPause();
            }
            if(ringside!=null)ringside.activate(next);
            bindRumble();
            if(next)main.post(tick);
        }
        syncMusic();
    }
    @Override protected void onStart(){
        super.onStart();foreground=true;screenOff=false;hingeClosed=false;
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(powerEvents,filter);
        Sensor angle=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
        if(angle!=null)sensors.registerListener(hinge,angle,SensorManager.SENSOR_DELAY_NORMAL,main);
        inputs.registerInputDeviceListener(this,main);displays.registerDisplayListener(this,main);
        layout();syncPower();
    }
    @Override protected void onResume(){super.onResume();resumed=true;syncPower();}
    @Override protected void onPause(){resumed=false;syncPower();super.onPause();}
    @Override protected void onStop(){
        foreground=false;syncPower();unregisterReceiver(powerEvents);
        if(sensors!=null)sensors.unregisterListener(hinge);
        inputs.unregisterInputDeviceListener(this);displays.unregisterDisplayListener(this);closeLower();super.onStop();
    }
    @Override protected void onDestroy(){if(music!=null){music.release();music=null;}closeAchievements();removeLocal();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("game",viewingGame);}
    @Override public void onDisplayAdded(int id){layout();}
    @Override public void onDisplayRemoved(int id){layout();}
    @Override public void onDisplayChanged(int id){syncPower();layout();}
}
