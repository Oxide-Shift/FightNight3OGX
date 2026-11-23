package org.oxideshift.corner;

import java.io.*;
import java.nio.file.*;

public final class DiscTraxSmoke {
    private static void le(DataOutputStream out,int n)throws IOException{out.writeInt(Integer.reverseBytes(n));}
    private static void chunk(DataOutputStream out,String id,byte[] payload)throws IOException{
        out.writeBytes(id);le(out,8+payload.length);out.write(payload);
    }
    private static byte[] samples(boolean literal,int history)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        if(literal){
            out.writeByte(0xee);out.writeShort(history);out.writeShort(0);
            for(int i=0;i<28;i++)out.writeShort(history+i);
        }else{out.writeByte(0x10);out.write(new byte[14]);}
        return bytes.toByteArray();
    }
    private static void data(DataOutputStream out,boolean literal,boolean broken)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream block=new DataOutputStream(b);
        byte[] l=samples(literal,256),r=samples(literal,-256);
        le(block,literal?28:3);le(block,0);le(block,broken?Integer.MAX_VALUE:l.length);block.write(l);block.write(r);
        chunk(out,"SCDl",b.toByteArray());
    }
    private static byte[] archive(boolean broken)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream song=new DataOutputStream(b);
        chunk(song,"SCHl",new byte[]{80,84,7,0,(byte)0xfd,(byte)0x85,1,31,(byte)0x82,1,2,(byte)0x84,2,(byte)0xac,0x44,(byte)0xa0,1,10,(byte)0xff});
        chunk(song,"SCCl",new byte[]{2,0,0,0});
        data(song,true,false);data(song,false,broken);chunk(song,"SCEl",new byte[0]);
        ByteArrayOutputStream packed=new ByteArrayOutputStream();DataOutputStream big=new DataOutputStream(packed);
        int start=30;big.writeBytes("BIGF");le(big,start+b.size());big.writeInt(1);big.writeInt(start);
        big.writeInt(start);big.writeInt(b.size());big.writeBytes("a.asf");big.writeByte(0);b.writeTo(big);
        return packed.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Path f=Files.createTempFile("round-trax-test",".big");short[] pcm=new short[64];
        try{
            Files.write(f,archive(false));
            try(DiscTrax d=new DiscTrax(f.toFile())){
                for(int play=0;play<2;play++){
                    d.select(d.songs.get(0));
                    if(d.read(pcm)!=28 || pcm[0]!=256 || pcm[1]!=-256 || pcm[54]!=283 || pcm[55]!=-229)throw new AssertionError("PCM stereo");
                    if(d.read(pcm)!=3 || pcm[0]!=240 || pcm[1]!=-240 || pcm[2]!=225 || pcm[3]!=-225 || pcm[4]!=210 || pcm[5]!=-211 || d.read(pcm)!=0)throw new AssertionError("Predictor across blocks, final partial frame");
                }
            }
            Files.write(f,archive(true));
            try(DiscTrax d=new DiscTrax(f.toFile())){
                d.select(d.songs.get(0));d.read(pcm);
                try{d.read(pcm);throw new AssertionError("Invalid stereo offset accepted");}catch(IOException expected){}
            }
        }finally{Files.deleteIfExists(f);}
        System.out.println("EA Trax stereo, predictor state, partial frames, replay and corrupt offsets passed.");
    }
}
