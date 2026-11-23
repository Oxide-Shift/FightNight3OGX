package org.oxideshift.corner;

import android.os.FileObserver;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

/** Watch Xbox E: title directories. FileObserver does not recurse into new profile folders. */
final class SaveChanges {
    private final Path root;
    private final Runnable changed;
    private final ScheduledExecutorService io;
    private final Map<Path,FileObserver> watchers=new HashMap<>();
    private ScheduledFuture<?> pending;
    private boolean stopped;
    private static final int MASK=FileObserver.CLOSE_WRITE|FileObserver.MOVED_TO|FileObserver.MOVED_FROM|
            FileObserver.CREATE|FileObserver.DELETE|FileObserver.DELETE_SELF|FileObserver.MOVE_SELF;

    SaveChanges(File slot,ScheduledExecutorService io,Runnable changed){
        root=slot.toPath();this.io=io;this.changed=changed;
    }
    private boolean relevant(Path path){
        if(path.equals(root))return true;
        Path rel=root.relativize(path);
        String first=rel.getName(0).toString();
        return (first.equalsIgnoreCase("UDATA") || first.equalsIgnoreCase("TDATA"))
                && (rel.getNameCount()==1 || rel.getName(1).toString().equalsIgnoreCase("45410086"));
    }
    void refresh(){
        if(stopped || !Files.isDirectory(root))return;
        Set<Path> current=new HashSet<>();
        try{
            Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
                @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs){
                    if(!relevant(dir))return FileVisitResult.SKIP_SUBTREE;
                    current.add(dir);
                    if(!watchers.containsKey(dir)){
                        FileObserver observer=new FileObserver(dir.toFile(),MASK){
                            @Override public void onEvent(int event,String name){
                                if(name==null || relevant(dir.resolve(name)))io.execute(()->signal());
                            }
                        };
                        watchers.put(dir,observer);observer.startWatching();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }catch(IOException e){return;}
        Iterator<Map.Entry<Path,FileObserver>> it=watchers.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<Path,FileObserver> entry=it.next();
            if(!current.contains(entry.getKey())){entry.getValue().stopWatching();it.remove();}
        }
    }
    private void signal(){
        if(stopped)return;
        if(pending!=null)pending.cancel(false);
        pending=io.schedule(()->{refresh();changed.run();},350,TimeUnit.MILLISECONDS);
    }
    void stop(){
        stopped=true;
        if(pending!=null)pending.cancel(false);
        for(FileObserver observer:watchers.values())observer.stopWatching();
        watchers.clear();
    }
}
