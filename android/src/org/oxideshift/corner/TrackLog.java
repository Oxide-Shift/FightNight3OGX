package org.oxideshift.corner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** The last few EA Trax track changes (track, position, reason), kept on the device in one small file.
 *  It appears only in Copy details; nothing here is ever uploaded. */
final class TrackLog {
    static final int LIMIT=24,LINE=160;
    static final String HEADER="Recent EA Trax track changes (newest last)";
    private static final ArrayDeque<String> lines=new ArrayDeque<>();
    private static File file;
    private static final java.util.concurrent.ExecutorService disk=java.util.concurrent.Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"track-log");t.setDaemon(true);return t;
    });
    private TrackLog(){}

    static void attach(File dir){
        File target=new File(dir,"track-log.txt");
        String saved="";
        try{if(target.isFile() && target.length()<=LIMIT*(LINE+2))saved=new String(java.nio.file.Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8);}
        catch(IOException ignored){}
        synchronized(TrackLog.class){
            file=target;
            if(lines.isEmpty())for(String line:saved.split("\n"))if(!line.isEmpty())keep(line);
        }
    }
    static String label(String stem){
        if(stem==null || stem.isEmpty())return "(none)";
        FightBroadcast.Song song=FightBroadcast.song(stem);
        return song==null?stem:song.title;
    }
    static String clock(long frames){
        long tenths=Math.max(0,frames)*10/DiscTrax.RATE;
        return String.format(java.util.Locale.ROOT,"%d:%02d.%d",tenths/600,tenths/10%60,tenths%10);
    }
    /** reason: ended, preview, playlist, error, stopped, or a game-side change that was ignored. */
    static void record(String stem,long frames,long lengthFrames,String reason){
        StringBuilder line=new StringBuilder(label(stem)).append(" at ").append(clock(frames));
        if(lengthFrames>0)line.append(" of ").append(clock(lengthFrames));
        line.append(": ").append(reason);
        add(line.toString());
    }
    static void add(String text){
        String stamp=new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date());
        String line=(stamp+" "+text).replaceAll("[^\\x20-\\x7e]","?");
        if(line.length()>LINE)line=line.substring(0,LINE);
        final String snapshot;final File target;
        synchronized(TrackLog.class){keep(line);snapshot=joined();target=file;}
        if(target!=null)disk.execute(()->{
            try{
                File temp=new File(target.getPath()+".tmp");
                java.nio.file.Files.write(temp.toPath(),snapshot.getBytes(StandardCharsets.UTF_8));
                if(!temp.renameTo(target)){target.delete();temp.renameTo(target);}
            }catch(IOException ignored){}
        });
    }
    private static void keep(String line){
        lines.addLast(line.length()>LINE?line.substring(0,LINE):line);
        while(lines.size()>LIMIT)lines.removeFirst();
    }
    private static String joined(){
        StringBuilder out=new StringBuilder();
        for(String line:lines)out.append(line).append('\n');
        return out.toString();
    }
    /** Copy details section, or "" when nothing has been recorded. */
    static synchronized String text(){
        return lines.isEmpty()?"":"\n\n"+HEADER+"\n"+joined().trim();
    }
    static synchronized void clearForTest(){lines.clear();file=null;}
}
