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
    private final ExpandedStart expandedStart;
    private final Rotation rotation;
    synchronized void expanded(boolean enabled){
        rotation.expanded(enabled);
        notifyAll();
    }
    synchronized void trackMask(int mask){
        rotation.mask(mask);
        notifyAll();
    }
    synchronized void endPreview(){
        rotation.endPreview();
        notifyAll();
    }
    static final class ExpandedStart {
        private boolean heard;
        synchronized String select(){
            String[] titles=ExpandedTracks.TITLES;
            return heard?titles[java.util.concurrent.ThreadLocalRandom.current().nextInt(titles.length)]:titles[0];
        }
        synchronized void audible(String stem){
            if(ExpandedTracks.TITLES[0].equals(stem))heard=true;
        }
    }
    private final Listener listener;
    private boolean playing,closed;
    private volatile boolean menuScene=true;
    private String first="";
    synchronized void requested(String stem,boolean audition){
        rotation.request(stem,audition);
        notifyAll();
    }
    RoundMusic(File game,android.content.res.AssetManager assets,boolean expanded,ExpandedStart expandedStart,int mask,Listener listener){
        this.expandedStart=expandedStart;
        rotation=new Rotation(expandedStart,expanded,mask);
        this.game=game;this.assets=assets;this.listener=listener;
    }
    private static boolean isExpanded(String stem){return ExpandedTracks.index(stem)>=0;}
    void start(){new Thread(this::run,"fight-ea-trax").start();}
    synchronized void transport(String stem,boolean play,boolean inMenu){
        menuScene=inMenu;
        if(first.isEmpty() && !stem.isEmpty()){
            first=stem;
            rotation.request(stem,false);
        }
        playing=play;
        notifyAll();
    }
    @Override public synchronized void close(){closed=true;notifyAll();}
    private synchronized boolean ready(AudioTrack out)throws InterruptedException{
        while(!closed && (!playing || (first.isEmpty() && rotation.mask!=0))){
            if(out.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)out.pause();
            wait();
        }
        if(!closed && out.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)out.play();
        return !closed;
    }
    /** Row order from the retail title lookup (sub_00162800), independent of BIG file order. */
    static final class Rotation {
        static final String[] STOCK={"SEANBIG1","ATMOSPH1","BRASCO","CONSEQ1","DILATEDP","DIPSET",
                "KRAYTWIN","LTDA","OBIETRIC","ROCC","SLIC","YOUNGROS"};
        private final ExpandedStart start;
        private final List<String> queue=new ArrayList<>();
        private boolean expanded;
        private int mask;
        private String requested="",interrupt="",audition="";

        Rotation(ExpandedStart start,boolean expanded,int mask){
            this.start=start;this.mask=mask;expanded(expanded);
        }
        void expanded(boolean enabled){
            if(expanded==enabled)return;
            expanded=enabled;queue.clear();audition="";
            requested=interrupt=enabled?start.select():"";
        }
        void mask(int enabled){
            if(mask!=enabled){mask=enabled;queue.clear();}
        }
        private boolean eligible(String stem){
            if(isExpanded(stem))return expanded;
            int row=java.util.Arrays.asList(STOCK).indexOf(stem);
            return row>=0 && mask>=0 && (mask & (1<<row))!=0;
        }
        void request(String stem,boolean preview){
            if(preview){
                requested=interrupt=audition=stem;
            }else if(!expanded && audition.isEmpty()){
                requested=stem;
            }
        }
        void endPreview(){
            if(!audition.isEmpty() && audition.equals(requested))requested=interrupt="";
            audition="";
        }
        boolean shouldChange(String current){
            return (!interrupt.isEmpty() && !interrupt.equals(current))
                    || (!eligible(current) && !current.equals(audition));
        }
        String next(String last){
            String preferred=requested;
            requested=interrupt="";
            boolean preview=!audition.isEmpty() && audition.equals(preferred);
            if(!preview)audition="";
            if(queue.isEmpty()){
                for(String stem:STOCK)if(eligible(stem))queue.add(stem);
                if(expanded)Collections.addAll(queue,FightBroadcast.EXPANDED);
                Collections.shuffle(queue);
                if(queue.size()>1 && queue.get(0).equals(last))Collections.swap(queue,0,1);
            }
            if(preview || (eligible(preferred) && !preferred.equals(last))){
                queue.remove(preferred);
                return preferred;
            }
            return queue.isEmpty()?"":queue.remove(0);
        }
    }
    private synchronized boolean changeAfterBlock(String stem){return rotation.shouldChange(stem);}
    private synchronized String nextTrack(String last){return rotation.next(last);}
    private synchronized String waitForTrack(String last)throws InterruptedException{
        while(!closed){
            String next=rotation.next(last);
            if(!next.isEmpty())return next;
            wait();
        }
        return "";
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
        java.util.Map<String,DiscTrax.Song> originals=new java.util.LinkedHashMap<>();
        for(DiscTrax.Song song:disc.songs)originals.put(song.stem,song);
        String last="";
        short[] pcm=new short[DiscTrax.MAX_FRAMES*2];
        long written=0,head=0,previousHead=0;
        java.util.ArrayDeque<Cue> cues=new java.util.ArrayDeque<>();
        boolean fadeIn=false;
        SceneMix mix=new SceneMix(menuScene);
        while(ready(out)){
            String stem=nextTrack(last);
            if(stem.isEmpty()){
                out.pause();out.flush();cues.clear();
                written=head=previousHead=0;
                listener.audible(this,"");
                stem=waitForTrack(last);
                if(stem.isEmpty() || !ready(out))return;
                fadeIn=true;
            }
            last=stem;
            try(ExpandedAudio extra=isExpanded(stem)?new ExpandedAudio(assets,stem):null){
                if(extra==null){
                    DiscTrax.Song song=originals.get(stem);
                    if(song==null)throw new IOException("Missing EA Trax disc selection: "+stem);
                    disc.select(song);
                }
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
                        while(!cues.isEmpty() && head>cues.peekFirst().frame){
                            String audible=cues.removeFirst().stem;
                            expandedStart.audible(audible);
                            listener.audible(this,audible);
                        }
                    }
                    if(skip){fadeIn=true;break;}
                }
            }
        }
    }
    /** Gains relative to decoded stereo PCM. Stock menu captures measure 0.22 at the default mix. */
    static final class SceneMix {
        static final float MENU=.22f,FIGHT=.12f;
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
                case "CONSEQ1": integrated=-11.0;break;
                case "DILATEDP": integrated=-7.8;break;
                case "DIPSET": integrated=-8.4;break;
                case "KRAYTWIN": integrated=-8.5;break;
                case "LTDA": integrated=-8.1;break;
                case "OBIETRIC": integrated=-7.6;break;
                case "ROCC": integrated=-7.3;break;
                case "SEANBIG1": integrated=-7.7;break;
                case "SLIC": integrated=-7.2;break;
                case "YOUNGROS": integrated=-8.2;break;
                default:
                    int index=ExpandedTracks.index(stem);
                    if(index<0){trackGain=1f;return;}
                    integrated=ExpandedTracks.LUFS[index];
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
