package org.oxideshift.corner;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Streams the stereo EA-XA songs inside the player's original Xbox music.big. */
final class DiscTrax implements Closeable {
    static final int RATE=44100, MAX_FRAMES=4096;
    private static final int DATA=0x5343446c, COUNT=0x5343436c, END=0x5343456c;
    private static final int[] FIRST={0,240,460,392}, SECOND={0,0,-208,-220};
    static final class Song {
        final long start,end;
        final String stem;
        Song(long start,long length,String stem){this.start=start;end=start+length;this.stem=stem;}
    }
    final List<Song> songs=new ArrayList<>();
    private final RandomAccessFile file;
    private final byte[] block=new byte[MAX_FRAMES*5+128];
    private final int[] last=new int[2],previous=new int[2];
    private long end,remaining;

    DiscTrax(File archive)throws IOException{
        file=new RandomAccessFile(archive,"r");
        try{
            if(file.readInt()!=0x42494746)throw invalid();
            long length=Integer.reverseBytes(file.readInt())&0xffffffffL;
            int count=file.readInt(),indexEnd=file.readInt();
            if(length!=file.length() || count<1 || count>256 || indexEnd<16 || indexEnd>length)throw invalid();
            for(int i=0;i<count;i++){
                long start=file.readInt()&0xffffffffL,size=file.readInt()&0xffffffffL;
                StringBuilder name=new StringBuilder();
                for(int ch;(ch=file.readUnsignedByte())!=0;){
                    if(name.length()>=128 || file.getFilePointer()>=indexEnd)throw invalid();
                    name.append((char)ch);
                }
                if(file.getFilePointer()>indexEnd || start<indexEnd || size>length-start)throw invalid();
                if(name.toString().toLowerCase(Locale.ROOT).endsWith(".asf"))songs.add(new Song(start,size,name.substring(0,name.length()-4).toUpperCase(Locale.ROOT)));
            }
            if(songs.isEmpty())throw invalid();
        }catch(IOException e){file.close();throw e;}
    }
    void select(Song song)throws IOException{
        file.seek(song.start);end=song.end;
        if(end-song.start<16 || file.readInt()!=0x5343486c)throw invalid();
        int size=Integer.reverseBytes(file.readInt());
        if(size<12 || size>512 || size>end-song.start)throw invalid();
        file.readFully(block,0,size-8);
        if(block[0]!='P' || block[1]!='T')throw invalid();
        int channels=0,rate=0,codec=-1;
        remaining=0;
        for(int p=4;p<size-8;){
            int tag=block[p++]&255;
            if(tag==0xff)break;
            if(tag==0xfd)continue;
            if(p==size-8)throw invalid();
            int bytes=block[p++]&255,value=0;
            if(bytes>4 || bytes>size-8-p)throw invalid();
            while(bytes-->0)value=(value<<8)|(block[p++]&255);
            if(tag==0x82)channels=value;
            if(tag==0x84)rate=value;
            if(tag==0x85)remaining=value;
            if(tag==0xa0)codec=value;
        }
        if(channels!=2 || rate!=RATE || codec!=10 || remaining<=0)throw invalid();
        last[0]=last[1]=previous[0]=previous[1]=0;
    }
    /** Return stereo frames, or zero at the selected song's end. */
    int read(short[] pcm)throws IOException{
        while(file.getFilePointer()<=end-8){
            int tag=file.readInt(),size=Integer.reverseBytes(file.readInt());
            int bytes=size-8;
            if(size<8 || bytes>end-file.getFilePointer())throw invalid();
            if(tag==END){if(remaining!=0)throw invalid();return 0;}
            if(tag==COUNT){file.seek(file.getFilePointer()+bytes);continue;}
            if(tag!=DATA || bytes<12 || bytes>block.length)throw invalid();
            file.readFully(block,0,bytes);
            int frames=little(0),left=little(4),right=little(8);
            if(frames<1 || frames>MAX_FRAMES || frames>remaining || pcm.length<frames*2
                    || left<0 || right<=left || right>=bytes-12)throw invalid();
            decode(0,12+left,12+right,frames,pcm);
            decode(1,12+right,bytes,frames,pcm);
            remaining-=frames;
            return frames;
        }
        throw invalid();
    }
    private void decode(int ch,int at,int limit,int frames,short[] pcm)throws IOException{
        int recent=last[ch],older=previous[ch];
        for(int first=0;first<frames;first+=28){
            if(at>=limit)throw invalid();
            int mode=block[at++]&255,count=Math.min(28,frames-first);
            if(mode==0xee){
                if(limit-at<60)throw invalid();
                // The embedded predictor state is independent of the following literal samples.
                recent=bigShort(at);older=bigShort(at+2);at+=4;
                for(int i=0;i<count;i++)pcm[2*(first+i)+ch]=bigShort(at+2*i);
                at+=56;
            }else{
                int filter=mode>>>4,shift=mode&15;
                if(filter>=FIRST.length || limit-at<14)throw invalid();
                int a=FIRST[filter],b=SECOND[filter];
                for(int i=0;i<count;i++){
                    int packed=block[at+(i>>1)];
                    int nibble=(i&1)==0?packed>>4:(packed<<28)>>28;
                    int residual=(nibble<<20)>>shift;
                    int sample=(residual+a*recent+b*older)>>8;
                    older=recent;recent=Math.max(-32768,Math.min(32767,sample));
                    pcm[2*(first+i)+ch]=(short)recent;
                }
                at+=14;
            }
        }
        last[ch]=recent;previous[ch]=older;
    }
    private int little(int p){
        return (block[p]&255)|(block[p+1]&255)<<8|(block[p+2]&255)<<16|block[p+3]<<24;
    }
    private short bigShort(int p){return (short)(block[p]<<8|(block[p+1]&255));}
    private static IOException invalid(){return new IOException("Unsupported or incomplete EA Trax music archive");}
    @Override public void close()throws IOException{file.close();}
}
