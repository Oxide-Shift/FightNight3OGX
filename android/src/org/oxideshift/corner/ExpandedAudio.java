package org.oxideshift.corner;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/** Decode one bundled MP3 into the same 44.1 kHz stereo bus as the disc songs. */
final class ExpandedAudio implements AutoCloseable {
    private final MediaExtractor source=new MediaExtractor();
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    private MediaCodec codec;
    private boolean inputDone,outputDone;
    private int sampleRate,available,offset;
    private short[] decoded=new short[0];
    private long frame,next;
    private int previousL,previousR;
    /** Container-stated duration at the 44.1 kHz bus rate, or 0 when the file does not state one. */
    final long storedFrames;

    ExpandedAudio(AssetManager assets,String title)throws IOException{
        long stored=0;
        try(AssetFileDescriptor file=assets.openFd("expanded/"+title+".mp3")){
            source.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());
            MediaFormat format=source.getTrackFormat(0);
            if(format.containsKey(MediaFormat.KEY_DURATION))stored=format.getLong(MediaFormat.KEY_DURATION)*DiscTrax.RATE/1000000L;
            source.selectTrack(0);
            sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0);
            codec.start();
        }catch(IOException | RuntimeException problem){close();throw problem;}
        storedFrames=stored;
    }
    int read(short[] pcm)throws IOException{
        while(offset==available && !outputDone){
            if(!inputDone){
                int index=codec.dequeueInputBuffer(10000);
                if(index>=0){
                    ByteBuffer buffer=codec.getInputBuffer(index);
                    int size=source.readSampleData(buffer,0);
                    inputDone=size<0;
                    codec.queueInputBuffer(index,0,Math.max(0,size),inputDone?0:source.getSampleTime(),
                            inputDone?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);
                    if(!inputDone)source.advance();
                }
            }
            int index=codec.dequeueOutputBuffer(info,10000);
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                MediaFormat format=codec.getOutputFormat();
                sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if(sampleRate<8000 || sampleRate>192000 || format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!=2 ||
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)!=AudioFormat.ENCODING_PCM_16BIT)
                    throw new IOException("Unsupported expanded music format");
            }
            if(index<0)continue;
            try{
                if(info.size>0){
                    ByteBuffer bytes=codec.getOutputBuffer(index).duplicate();
                    bytes.position(info.offset);bytes.limit(info.offset+info.size);
                    convert(bytes.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer());
                }
                outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            }finally{codec.releaseOutputBuffer(index,false);}
        }
        int n=Math.min(available-offset,pcm.length);
        System.arraycopy(decoded,offset,pcm,0,n);offset+=n;
        return n/2;
    }
    private void convert(ShortBuffer input){
        int needed=(2+(int)((long)(input.remaining()/2)*DiscTrax.RATE/sampleRate))*2;
        if(decoded.length<needed)decoded=new short[needed];
        offset=available=0;
        while(input.remaining()>=2){
            int left=input.get(),right=input.get();
            long end=frame*DiscTrax.RATE;
            if(frame==0){previousL=left;previousR=right;}
            while(next<=end){
                long back=end-next;
                decoded[available++]=(short)(left+(previousL-left)*back/DiscTrax.RATE);
                decoded[available++]=(short)(right+(previousR-right)*back/DiscTrax.RATE);
                next+=sampleRate;
            }
            previousL=left;previousR=right;frame++;
        }
    }
    public void close(){
        try{if(codec!=null)codec.release();}finally{source.release();}
    }
}
