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
    private final android.content.res.AssetManager assets;
    private volatile boolean expanded;
    private boolean pickExpanded;
    synchronized void expanded(boolean enabled){
        if(expanded!=enabled){
            expanded=enabled;pickExpanded=enabled;
            if(enabled){choice=randomExpanded();preview=choice;}
            else choice=preview="";
        }
    }
    private static String randomExpanded(){
        String[] tracks=FightBroadcast.EXPANDED;
        return tracks[java.util.concurrent.ThreadLocalRandom.current().nextInt(tracks.length)];
    }
    private final Listener listener;
    private boolean playing,closed;
    private volatile boolean menuScene=true;
    private String first="",choice="",preview="";
    // File reads can announce the next song while the current song is still audible.
    synchronized void requested(String stem,boolean audition){
        if(expanded && !audition)return;
        choice=stem;
        if(audition)preview=stem;
    }
    private synchronized String takeChoice(){
        String next=choice;choice="";pickExpanded=false;return next;
    }
    private synchronized boolean changeAfterBlock(String current){
        boolean change=(!preview.isEmpty() && !preview.equals(current)) || (!expanded && isExpanded(current));
        preview="";return change;
    }
    RoundMusic(File game,android.content.res.AssetManager assets,boolean expanded,Listener listener){
        this.game=game;this.assets=assets;this.expanded=expanded;this.pickExpanded=expanded;this.listener=listener;
    }
    private static boolean isExpanded(String stem){return java.util.Arrays.asList(FightBroadcast.EXPANDED).contains(stem);}
    void start(){new Thread(this::run,"fight-ea-trax").start();}
    synchronized void transport(String stem,boolean play,boolean inMenu){
        menuScene=inMenu;
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
    private synchronized String initialTrack(){
        if(pickExpanded){pickExpanded=false;choice=preview="";return randomExpanded();}
        return first;
    }
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
                if(out.getState()!=AudioTrack.STATE_INITIALIZED)
                    throw new IOException("Cannot open EA Trax output");
                listener.prepared(this);
                if(ready(out))stream(disc,out);
            }finally{out.release();}
        }catch(IOException | RuntimeException e){
            Log.w("FightNight","Restoring original disc music",e);listener.failed(this);
        }catch(InterruptedException e){Thread.currentThread().interrupt();listener.failed(this);}
    }
    private void stream(DiscTrax disc,AudioTrack out)throws IOException,InterruptedException{
        List<String> queue=new ArrayList<>();
        java.util.Map<String,DiscTrax.Song> originals=new java.util.LinkedHashMap<>();
        for(DiscTrax.Song song:disc.songs)originals.put(song.stem,song);
        String last="";
        boolean mixed=expanded;
        String next=initialTrack();
        short[] pcm=new short[DiscTrax.MAX_FRAMES*2];
        long written=0,head=0,previousHead=0;
        java.util.ArrayDeque<Cue> cues=new java.util.ArrayDeque<>();
        boolean fadeIn=false;
        SceneMix mix=new SceneMix(menuScene);
        while(ready(out)){
            if(mixed!=expanded){mixed=expanded;queue.clear();}
            if(queue.isEmpty()){
                queue.addAll(originals.keySet());
                if(mixed)Collections.addAll(queue,FightBroadcast.EXPANDED);
                Collections.shuffle(queue);
                if(queue.size()>1 && queue.get(0).equals(last))Collections.swap(queue,0,1);
            }
            String stem=!next.equals(last) && (originals.containsKey(next) || (mixed && isExpanded(next)))?next:queue.get(0);
            queue.remove(stem);last=stem;
            try(ExpandedAudio extra=isExpanded(stem)?new ExpandedAudio(assets,stem):null){
                if(extra==null)disc.select(originals.get(stem));
                mix.selectTrack(stem);
                cues.addLast(new Cue(written/2,stem));
                int frames;
                while(ready(out) && (frames=extra==null?disc.read(pcm):extra.read(pcm))>0){
                    boolean skip=changeAfterBlock(stem);
                    if(skip)frames=Math.min(frames,441);
                    if(fadeIn || skip){
                        int ramp=Math.min(441,frames);
                        for(int i=0;i<ramp;i++){
                            float gain=(skip?ramp-1-i:i)/(float)Math.max(1,ramp-1);
                            pcm[i*2]=(short)(pcm[i*2]*gain);pcm[i*2+1]=(short)(pcm[i*2+1]*gain);
                        }
                        fadeIn=false;
                    }
                    mix.apply(pcm,frames,menuScene);
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
            }
            next=takeChoice();
        }
    }
    /** Gains relative to decoded stereo PCM. Stock menu captures measure 0.22 at the default mix. */
    static final class SceneMix {
        static final float MENU=.22f,FIGHT=.15f;
        static final int FADE_FRAMES=DiscTrax.RATE*3/4;
        private float current,target,step;
        private int remaining;
        private float trackGain=1f;
        void selectTrack(String stem){
            // Full-track integrated LUFS of the decoded stereo sources. Match the disc median.
            double integrated;
            switch(stem){
                case "ATMOSPH1": integrated=-5.7;break;
                case "BRASCO": integrated=-7.4;break;
                case "Blood + Sweat": integrated=-15.4;break;
                case "Body Shots": integrated=-16.0;break;
                case "CONSEQ1": integrated=-11.0;break;
                case "Corner Talk": integrated=-15.3;break;
                case "DILATEDP": integrated=-7.8;break;
                case "DIPSET": integrated=-8.4;break;
                case "Heavy Hands": integrated=-15.7;break;
                case "KRAYTWIN": integrated=-8.5;break;
                case "Knockout City": integrated=-15.9;break;
                case "LTDA": integrated=-8.1;break;
                case "No Mercy": integrated=-15.5;break;
                case "OBIETRIC": integrated=-7.6;break;
                case "ROCC": integrated=-7.3;break;
                case "SEANBIG1": integrated=-7.7;break;
                case "SLIC": integrated=-7.2;break;
                case "The Contender": integrated=-15.4;break;
                case "YOUNGROS": integrated=-8.2;break;
                default:trackGain=1f;return;
            }
            trackGain=(float)Math.pow(10,(-7.8-integrated)/20);
        }
        SceneMix(boolean menu){current=target=menu?MENU:FIGHT;}
        void apply(short[] pcm,int frames,boolean menu){
            float requested=menu?MENU:FIGHT;
            if(target!=requested){
                target=requested;step=(target-current)/FADE_FRAMES;remaining=FADE_FRAMES;
            }
            for(int i=0;i<frames*2;i+=2){
                if(remaining>0)current=target-step*(--remaining);
                float gain=current*trackGain;
                pcm[i]=(short)Math.round(pcm[i]*gain);pcm[i+1]=(short)Math.round(pcm[i+1]*gain);
            }
        }
    }
    private static final class Cue {
        final long frame;final String stem;
        Cue(long frame,String stem){this.frame=frame;this.stem=stem;}
    }
}
