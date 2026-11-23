package org.oxideshift.corner;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

/** The launcher owns this quiet soundtrack voice. Fight audio stays under game control. */
final class RoundMusic implements AutoCloseable {
    private final File game;
    private Thread worker;
    private boolean wanted,closed;
    RoundMusic(File game){this.game=game;}
    synchronized void setPlaying(boolean on){
        if(closed || wanted==on)return;
        wanted=on;
        if(on && worker==null){
            worker=new Thread(this::run,"fight-ea-trax");
            worker.start();
        }
        notifyAll();
    }
    @Override public synchronized void close(){closed=true;wanted=false;notifyAll();}
    private synchronized boolean ready(AudioTrack speaker)throws InterruptedException{
        while(!closed && !wanted){
            if(speaker!=null && speaker.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)speaker.pause();
            wait();
        }
        if(!closed && speaker!=null && speaker.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)speaker.play();
        return !closed;
    }
    private void run(){
        try{
            while(ready(null)){
                try{playDisc();}
                catch(IOException | RuntimeException e){
                    Log.w("FightNight","EA Trax playback unavailable",e);
                    // Retry on the next round or explicit toggle, never on each launcher tick.
                    synchronized(this){while(wanted && !closed)wait();}
                }
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private void playDisc()throws IOException,InterruptedException{
        File archive=new File(game,"AUDIO/MUSIC/music.big");
        if(!archive.isFile())archive=new File(game,"audio/music/music.big");
        try(DiscTrax disc=new DiscTrax(archive)){
            int minimum=AudioTrack.getMinBufferSize(DiscTrax.RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IOException("Stereo output unavailable");
            AudioTrack speaker=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(DiscTrax.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(minimum,16384)).setTransferMode(AudioTrack.MODE_STREAM).build();
            try{
                speaker.setVolume(.04f);
                short[] pcm=new short[DiscTrax.MAX_FRAMES*2];
                DiscTrax.Song lastSong=null;
                while(ready(speaker)){
                    Collections.shuffle(disc.songs);
                    if(disc.songs.size()>1 && disc.songs.get(0)==lastSong)Collections.swap(disc.songs,0,1);
                    for(DiscTrax.Song song:disc.songs){
                        disc.select(song);lastSong=song;
                        int frames;
                        while(ready(speaker) && (frames=disc.read(pcm))!=0){
                            int n=frames*2;
                            for(int at=0;at<n;){
                                if(!ready(speaker))return;
                                int wrote=speaker.write(pcm,at,n-at,AudioTrack.WRITE_BLOCKING);
                                if(wrote<=0)throw new IOException("Audio output interrupted ("+wrote+")");
                                at+=wrote;
                            }
                        }
                        if(!ready(speaker))return;
                    }
                }
            }finally{speaker.release();}
        }
    }
}
