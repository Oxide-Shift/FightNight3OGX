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
    private static short mix(RoundMusic.SceneMix mix,boolean menu,int frames){
        short[] pcm=new short[2*frames];
        for(int i=0;i<pcm.length;i+=2){pcm[i]=20000;pcm[i+1]=-20000;}
        mix.apply(pcm,frames,menu);
        for(int i=0;i<pcm.length;i+=2)if(Math.abs(pcm[i]+pcm[i+1])>1)
            throw new AssertionError("Stereo balance during fade");
        return pcm[pcm.length-2];
    }
    private static void trackLevels(){
        RoundMusic.SceneMix mix=new RoundMusic.SceneMix(true);
        mix.selectTrack("DILATEDP");
        if(mix(mix,true,1)!=4400)throw new AssertionError("Disc reference");
        mix.selectTrack("Make It Two");
        float boost=(float)Math.pow(10,8.30/20);
        if(Math.abs(mix(mix,true,1)-4400*boost)>1 ||
                Math.abs(mix(mix,false,RoundMusic.SceneMix.FADE_FRAMES)-2400*boost)>1)
            throw new AssertionError("Expanded loudness across scenes");
        mix.selectTrack("ATMOSPH1");
        if(Math.abs(mix(mix,false,1)-2400*Math.pow(10,-2.1/20))>1)
            throw new AssertionError("Disc trim on a track change during a fight");
        mix.selectTrack("unknown");
        if(mix(mix,false,1)!=2400)throw new AssertionError("Unknown tracks use unity gain");
    }
    private static void sceneLevels(){
        RoundMusic.SceneMix mix=new RoundMusic.SceneMix(true);
        int fade=RoundMusic.SceneMix.FADE_FRAMES;
        if(mix(mix,true,1)!=4400)throw new AssertionError("Original menu level");
        int half=mix(mix,false,fade/2);
        if(Math.abs(half-3400)>1 || Math.abs(mix(mix,true,1)-half)>1)
            throw new AssertionError("Continuous reversal during loading");
        if(mix(mix,true,fade)!=4400 || mix(mix,false,1)!=4400)
            throw new AssertionError("Fade starts at the previous level");
        for(int i=1;i<fade;i+=441)mix(mix,false,Math.min(441,fade-i));
        if(mix(mix,false,1)!=2400 || mix(mix,true,fade)!=4400)
            throw new AssertionError("Fight level and menu restoration");
        if(mix(new RoundMusic.SceneMix(false),false,1)!=2400)
            throw new AssertionError("Starting inside a fight");
    }
    private static void playlist(){
        RoundMusic.Rotation r=new RoundMusic.Rotation(new RoundMusic.ExpandedStart(),false,-1);
        if(!r.next("").isEmpty())throw new AssertionError("Uninitialized playlist");
        for(int mask=0;mask<4096;mask++){
            r.mask(mask);
            java.util.Set<String> played=new java.util.HashSet<>();
            for(int i=0;i<Integer.bitCount(mask);i++)played.add(r.next(""));
            for(int i=0;i<12;i++)
                if(played.contains(RoundMusic.Rotation.STOCK[i])!=((mask & 1<<i)!=0))
                    throw new AssertionError("Wrong stock rotation for "+mask);
        }
        for(int row=0;row<12;row++){
            int mask=0xfff^(1<<row);
            RoundMusic.Rotation mixed=new RoundMusic.Rotation(new RoundMusic.ExpandedStart(),true,mask);
            String disabled=RoundMusic.Rotation.STOCK[row],last="";
            java.util.Set<String> all=new java.util.HashSet<>();
            for(int i=0;i<11+ExpandedTracks.TITLES.length;i++){
                mixed.request(disabled,false);
                last=mixed.next(last);
                if(i==0 && !last.equals("Still Want the Crown"))throw new AssertionError("Opener");
                if(last.equals(disabled) || !all.add(last))throw new AssertionError("Mixed shuffle");
            }
            mixed.request(disabled,true);
            if(!mixed.next(last).equals(disabled) || mixed.shouldChange(disabled))throw new AssertionError("Explicit preview");
            mixed.endPreview();
            if(!mixed.shouldChange(disabled) || mixed.next(disabled).equals(disabled))throw new AssertionError("Left preview");
            mixed.mask(0);
            for(int i=0;i<32;i++)if(ExpandedTracks.index(mixed.next(""))<0)throw new AssertionError("Expanded-only rotation");
            mixed.expanded(false);
            if(!mixed.next("").isEmpty())throw new AssertionError("All tracks disabled");
            mixed.mask(1<<row);
            if(!mixed.next("").equals(disabled) || !mixed.next(disabled).equals(disabled))throw new AssertionError("One enabled track");
            mixed.mask(0);
            if(!mixed.shouldChange(disabled))throw new AssertionError("Audible song was disabled");
        }
    }
    public static void main(String[] args)throws Exception{
        playlist();
        sceneLevels();trackLevels();
        RoundMusic.ExpandedStart launch=new RoundMusic.ExpandedStart();
        launch.audible("BRASCO");
        if(!launch.select().equals("Still Want the Crown") || !launch.select().equals("Still Want the Crown"))
            throw new AssertionError("The opener was consumed before playback");
        launch.audible("Still Want the Crown");
        for(int i=0;i<32;i++)if(ExpandedTracks.index(launch.select())<0)
            throw new AssertionError("Expansion selection outside the current roster");
        if(!new RoundMusic.ExpandedStart().select().equals("Still Want the Crown"))
            throw new AssertionError("Fresh game launch did not reset the opening song");
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
