package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;

/** PCM excerpts of the front-end events, decoded only from the supported imported disc. */
final class DiscMenuSounds {
    enum Cue {
        FORWARD(2), BACK(3), HORIZONTAL(21), VERTICAL(14), SCORECARD_PAGE(17);
        final int bankIndex;
        Cue(int bankIndex){this.bankIndex=bankIndex;}
    }
    private static final String BANK_SHA="6aef0c8ed179ece52e7f021bbab9f46d87a4bcdf665883c4a9583fddfd1a55fb";
    private static final int[] INDEX={-1,-1,-1,-1,2,4,6,8};
    private static final int[] STEP={7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,
        45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,
        408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,
        2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,
        10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767};
    private final ByteBuffer bank;

    DiscMenuSounds(File game)throws IOException{
        File file=new File(game,"AUDIO/AEMS/fe_sfx.abk");
        if(!file.isFile())file=new File(game,"audio/aems/fe_sfx.abk");
        if(file.length()!=577860)throw new IOException("Front-end sound bank unavailable");
        byte[] data=Files.readAllBytes(file.toPath());
        byte[] digest;
        try{digest=MessageDigest.getInstance("SHA-256").digest(data);}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        for(int i=0;i<digest.length;i++)
            if((digest[i]&255)!=Integer.parseInt(BANK_SHA.substring(2*i,2*i+2),16))
                throw new IOException("Unknown front-end sound bank");
        bank=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    byte[] wave(Cue cue)throws IOException{
        int base=bank.getInt(0x20);
        int entry=base+24+4*cue.bankIndex;
        int header=entry+bank.getInt(entry);
        int samples=0,rate=0,channels=1,left=0,right=0,codec=0;
        // Each PT patch stores a tag, a byte count, then a big-endian integer.
        for(int p=header+4;p<bank.limit();){
            int tag=bank.get(p++)&255;
            if(tag==0xff)break;
            if(tag==0xfd)continue;
            int length=bank.get(p++)&255,value=0;
            for(int i=0;i<length;i++)value=(value<<8)|(bank.get(p++)&255);
            switch(tag){
                case 0x82:channels=value;break;
                case 0x84:rate=value;break;
                case 0x85:samples=value;break;
                case 0x88:left=value;break;
                case 0x89:right=value;break;
                case 0xa0:codec=value;break;
            }
        }
        if(codec!=0x14 || rate<8000 || samples<1 || samples>96000 || channels<1 || channels>2)
            throw new IOException("Unsupported front-end sample");
        int size=samples*channels*2;
        ByteBuffer wav=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
        wav.putInt(0x46464952).putInt(size+36).putInt(0x45564157).putInt(0x20746d66).putInt(16)
                .putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2)
                .putShort((short)(channels*2)).putShort((short)16).putInt(0x61746164).putInt(size);
        for(int channel=0;channel<channels;channel++){
            int start=base+(channel==0?left:right);
            for(int first=0;first<samples;first+=64){
                int block=start+first/64*36;
                int sample=bank.getShort(block),index=bank.get(block+2)&255;
                if(index>=STEP.length)throw new IOException("Invalid sound block");
                for(int j=0;j<Math.min(64,samples-first);j++){
                    if(j>0){
                        int k=j-1;
                        int nibble=(bank.get(block+4+k/2)>>>((k&1)*4))&15;
                        int step=STEP[index], delta=step>>3;
                        if((nibble&1)!=0)delta+=step>>2;
                        if((nibble&2)!=0)delta+=step>>1;
                        if((nibble&4)!=0)delta+=step;
                        sample=Math.max(-32768,Math.min(32767,sample+((nibble&8)==0?delta:-delta)));
                        index=Math.max(0,Math.min(88,index+INDEX[nibble&7]));
                    }
                    wav.putShort(44+((first+j)*channels+channel)*2,(short)sample);
                }
            }
        }
        return wav.array();
    }
}
