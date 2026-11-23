package org.oxideshift.corner;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One streaming voice per game session. Only sleep, movies and leaving the game pause it. */
final class RoundMusic implements AutoCloseable {
    interface Listener {
        void prepared(RoundMusic player);
        void audible(RoundMusic player,String stem);
        void failed(RoundMusic player);
    }
    private final File game;
    private final Listener listener;
    private boolean playing,closed;
    private String first="",choice="",preview="";
    // File reads can announce the next song while the current song is still audible.
    synchronized void requested(String stem,boolean audition){
        choice=stem;
        if(audition)preview=stem;
    }
    private synchronized String takeChoice(){String next=choice;choice="";return next;}
    private synchronized boolean changeAfterBlock(String current){
        boolean change=!preview.isEmpty() && !preview.equals(current);
        preview="";return change;
    }
    RoundMusic(File game,Listener listener){this.game=game;this.listener=listener;}
    void start(){new Thread(this::run,"fight-ea-trax").start();}
    synchronized void transport(String stem,boolean play){
        if(first.isEmpty() && !stem.isEmpty())first=stem;
        playing=play;
        notifyAll();
    }
    @Override public synchronized void close(){closed=true;notifyAll();}
    private synchronized boolean ready(AudioTrack out)throws InterruptedException{
        while(!closed && (!playing || first.isEmpty())){
            if(out.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)out.pause();
            wait();
        }
        if(!closed && out.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)out.play();
        return !closed;
    }
    private synchronized String initialTrack(){return first;}
    private File archive()throws IOException{
        File entry=game;
        for(String part:new String[]{"audio","music","music.big"}){
            File[] matches=entry.listFiles(f->f.getName().equalsIgnoreCase(part));
            if(matches==null || matches.length!=1)throw new IOException("Disc soundtrack unavailable");
            entry=matches[0];
        }
        return entry;
    }
    private void run(){
        try(DiscTrax disc=new DiscTrax(archive())){
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            // Confirm every candidate is a playable stereo stream before asking the guest to yield music.
            for(DiscTrax.Song song:disc.songs){
                if(FightBroadcast.song(song.stem)==null)throw new IOException("Unrecognized EA Trax disc selection");
                disc.select(song);
            }
            int min=AudioTrack.getMinBufferSize(DiscTrax.RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IOException("Stereo output unavailable");
            AudioTrack out=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(DiscTrax.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(min,DiscTrax.RATE)).setTransferMode(AudioTrack.MODE_STREAM).build();
            try{
                if(out.getState()!=AudioTrack.STATE_INITIALIZED || out.setVolume(.12f)!=AudioTrack.SUCCESS)
                    throw new IOException("Cannot open EA Trax output");
                listener.prepared(this);
                if(ready(out))stream(disc,out);
            }finally{out.release();}
        }catch(IOException | RuntimeException e){
            Log.w("FightNight","Restoring original disc music",e);listener.failed(this);
        }catch(InterruptedException e){Thread.currentThread().interrupt();listener.failed(this);}
    }
    private void stream(DiscTrax disc,AudioTrack out)throws IOException,InterruptedException{
        List<DiscTrax.Song> queue=new ArrayList<>();
        DiscTrax.Song last=null;
        String next=initialTrack();
        short[] pcm=new short[DiscTrax.MAX_FRAMES*2];
        long written=0,head=0,previousHead=0;
        java.util.ArrayDeque<Cue> cues=new java.util.ArrayDeque<>();
        boolean fadeIn=false;
        while(ready(out)){
            if(queue.isEmpty()){
                queue.addAll(disc.songs);Collections.shuffle(queue);
                if(queue.size()>1 && queue.get(0)==last)Collections.swap(queue,0,1);
            }
            DiscTrax.Song song=null;
            if(!next.isEmpty()){
                for(DiscTrax.Song candidate:disc.songs)
                    if(candidate.stem.equals(next) && candidate!=last){song=candidate;break;}
                if(last==null && song==null)throw new IOException("Menu track missing from disc");
            }
            if(song==null)song=queue.get(0);
            queue.remove(song);
            last=song;disc.select(song);cues.addLast(new Cue(written/2,song.stem));
            int frames;
            while(ready(out) && (frames=disc.read(pcm))>0){
                boolean skip=changeAfterBlock(song.stem);
                if(skip)frames=Math.min(frames,441);
                if(fadeIn || skip){
                    int ramp=Math.min(441,frames);
                    for(int i=0;i<ramp;i++){
                        float gain=(skip?ramp-1-i:i)/(float)Math.max(1,ramp-1);
                        pcm[i*2]=(short)(pcm[i*2]*gain);pcm[i*2+1]=(short)(pcm[i*2+1]*gain);
                    }
                    fadeIn=false;
                }
                for(int at=0;at<frames*2;){
                    if(!ready(out))return;
                    int n=out.write(pcm,at,frames*2-at,AudioTrack.WRITE_BLOCKING);
                    if(n<=0)throw new IOException("EA Trax write failed: "+n);
                    at+=n;written+=n;
                    long raw=out.getPlaybackHeadPosition()&0xffffffffL;
                    head+=(raw-previousHead)&0xffffffffL;previousHead=raw;
                    while(!cues.isEmpty() && head>cues.peekFirst().frame)
                        listener.audible(this,cues.removeFirst().stem);
                }
                if(skip){fadeIn=true;break;}
            }
            next=takeChoice();
        }
    }
    private static final class Cue {
        final long frame;final String stem;
        Cue(long frame,String stem){this.frame=frame;this.stem=stem;}
    }
}
