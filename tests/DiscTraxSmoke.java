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
        return mix(mix,menu?RoundMusic.SceneMix.SCENE_MENU:RoundMusic.SceneMix.SCENE_FIGHT,frames);
    }
    private static short mix(RoundMusic.SceneMix mix,int scene,int frames){
        short[] pcm=new short[2*frames];
        for(int i=0;i<pcm.length;i+=2){pcm[i]=20000;pcm[i+1]=-20000;}
        mix.apply(pcm,frames,scene);
        for(int i=0;i<pcm.length;i+=2)if(Math.abs(pcm[i]+pcm[i+1])>1)
            throw new AssertionError("Stereo balance during fade");
        return pcm[pcm.length-2];
    }
    private static final int FIGHT_LEVEL=Math.round(20000*RoundMusic.SceneMix.FIGHT);
    private static void trackLevels(){
        RoundMusic.SceneMix mix=new RoundMusic.SceneMix(true);
        mix.selectTrack("DILATEDP");
        if(mix(mix,true,1)!=4400)throw new AssertionError("Disc reference");
        mix.selectTrack("Make It Two");
        float boost=(float)Math.pow(10,8.30/20);
        if(Math.abs(mix(mix,true,1)-4400*boost)>1 ||
                Math.abs(mix(mix,false,RoundMusic.SceneMix.FADE_FRAMES)-FIGHT_LEVEL*boost)>1)
            throw new AssertionError("Expanded loudness across scenes");
        mix.selectTrack("ATMOSPH1");
        if(Math.abs(mix(mix,false,1)-FIGHT_LEVEL*Math.pow(10,-2.1/20))>1)
            throw new AssertionError("Disc trim on a track change during a fight");
        mix.selectTrack("unknown");
        if(mix(mix,false,1)!=FIGHT_LEVEL)throw new AssertionError("Unknown tracks use unity gain");
    }
    private static void sceneLevels(){
        RoundMusic.SceneMix mix=new RoundMusic.SceneMix(true);
        int fade=RoundMusic.SceneMix.FADE_FRAMES;
        if(mix(mix,true,1)!=4400)throw new AssertionError("Original menu level");
        int half=mix(mix,false,fade/2);
        // Equal decibel steps: halfway in time is the geometric mean of the two levels, not the average.
        if(Math.abs(half-Math.sqrt(4400.0*FIGHT_LEVEL))>2 || Math.abs(mix(mix,true,1)-half)>1)
            throw new AssertionError("Continuous reversal during loading: "+half);
        if(mix(mix,true,fade)!=4400 || mix(mix,false,1)!=4400)
            throw new AssertionError("Fade starts at the previous level");
        for(int i=1;i<fade;i+=441)mix(mix,false,Math.min(441,fade-i));
        if(mix(mix,false,1)!=FIGHT_LEVEL || mix(mix,true,fade)!=4400)
            throw new AssertionError("Fight level and menu restoration");
        if(mix(new RoundMusic.SceneMix(false),false,1)!=FIGHT_LEVEL)
            throw new AssertionError("Starting inside a fight");
        if(Math.abs(20*Math.log10(RoundMusic.SceneMix.FIGHT)-(-17.72))>.01)
            throw new AssertionError("Fight music is 13% linear (-17.7 dB)");
        returning();
        entryFade();
        trackLog();
    }
    private static void trackLog(){
        RoundMusic.Rotation r=new RoundMusic.Rotation(new RoundMusic.ExpandedStart(),false,-1);
        r.request("OBIETRIC",true);
        if(!r.shouldChange("LTDA") || !r.changeReason("LTDA").equals("menu preview of Wanna Know"))
            throw new AssertionError("Preview cut reason: "+r.changeReason("LTDA"));
        RoundMusic.Rotation off=new RoundMusic.Rotation(new RoundMusic.ExpandedStart(),false,0);
        if(!off.changeReason("LTDA").equals("removed from the playlist"))throw new AssertionError("Playlist cut reason");
        TrackLog.clearForTest();
        if(!TrackLog.text().isEmpty())throw new AssertionError("Empty log adds nothing to Copy details");
        TrackLog.record("Still Want the Crown",RATE*221+RATE/5,RATE*222,"ended");
        String text=TrackLog.text();
        if(!text.startsWith("\n\n"+TrackLog.HEADER+"\n") || !text.endsWith("Still Want the Crown at 3:41.2 of 3:42.0: ended"))
            throw new AssertionError("Track log line: "+text);
        for(int i=0;i<TrackLog.LIMIT+10;i++)TrackLog.add("entry "+i+" é"+new String(new char[300]).replace('\0','x'));
        String[] lines=TrackLog.text().trim().split("\n");
        if(lines.length!=TrackLog.LIMIT+1 || !lines[lines.length-1].contains("entry "+(TrackLog.LIMIT+9)+" ?"))
            throw new AssertionError("Track log stays bounded and ASCII");
        for(String line:lines)if(line.length()>TrackLog.LINE)throw new AssertionError("Track log line length");
        TrackLog.clearForTest();
    }
    private static final int RATE=DiscTrax.RATE;
    private static void returning(){
        int ret=RoundMusic.SceneMix.SCENE_RETURNING,menu=RoundMusic.SceneMix.SCENE_MENU;
        // Late menu: the 7 s ramp from the result reaches menu level and holds there.
        RoundMusic.SceneMix late=new RoundMusic.SceneMix(false);
        int mid=mix(late,ret,RATE*7/2);
        if(Math.abs(mid-Math.sqrt(4400.0*FIGHT_LEVEL))>2)throw new AssertionError("Post-fight ramp midpoint: "+mid);
        if(mix(late,ret,RATE*7/2)!=4400 || mix(late,ret,RATE)!=4400 || mix(late,menu,RATE)!=4400)
            throw new AssertionError("Post-fight ramp holds at the menu level");
        // Early menu after 1 s: the rest finishes within QUICK_FRAMES, without a jump.
        RoundMusic.SceneMix early=new RoundMusic.SceneMix(false);
        int before=mix(early,ret,RATE);
        int next=mix(early,menu,1);
        if(next<before || next-before>2)throw new AssertionError("Early menu continues from the current level");
        if(mix(early,menu,RoundMusic.SceneMix.QUICK_FRAMES-1)!=4400)throw new AssertionError("Early menu finishes quickly");
        // Menu just before the ramp ends keeps the original schedule.
        RoundMusic.SceneMix nearly=new RoundMusic.SceneMix(false);
        mix(nearly,ret,RATE*7-RATE/10);
        if(mix(nearly,menu,RATE/10)!=4400)throw new AssertionError("Nearly finished ramp completes on time");
    }
    private static void entryFade(){
        RoundMusic.EntryFade f=new RoundMusic.EntryFade();
        if(f.active() || f.volume(0)!=1f)throw new AssertionError("Idle entry fade is unity");
        f.begin(1000);
        if(Math.abs(f.volume(1000)-.001f)>1e-5)throw new AssertionError("Entry starts at -60 dB");
        double mid=20*Math.log10(f.volume(1000+RoundMusic.EntryFade.FRAMES/2));
        if(Math.abs(mid+30)>.01)throw new AssertionError("Entry is linear in decibels: "+mid);
        if(f.volume(1000+RoundMusic.EntryFade.FRAMES)!=1f || f.active())throw new AssertionError("Entry ends at full");
        f.begin(0);
        long at=RATE*2;
        float now=f.volume(at);
        f.hurry(at);
        if(Math.abs(f.volume(at)-now)>1e-6)throw new AssertionError("Hurry keeps the current level");
        if(f.volume(at+RoundMusic.SceneMix.QUICK_FRAMES)!=1f)throw new AssertionError("Hurry finishes within the quick time");
        f.begin(500,RoundMusic.EntryFade.SHORT_FRAMES);
        if(Math.abs(f.volume(500)-.001f)>1e-5 || f.volume(500+RoundMusic.EntryFade.SHORT_FRAMES)!=1f)
            throw new AssertionError("Menu start uses the short entry");
    }
    private static void transport(){
        RoundMusic.Rotation t=new RoundMusic.Rotation(new RoundMusic.ExpandedStart(),false,0xfff);
        String a=t.next(""),b=t.next(a);
        t.skip(-1);
        if(!t.shouldChange(b) || !t.next(b).equals(a))throw new AssertionError("Back returns to the previous track");
        t.skip(1);
        if(!t.shouldChange(a))throw new AssertionError("Next cuts the current track");
        String c=t.next(a);
        if(c.equals(a) || c.isEmpty())throw new AssertionError("Next moves on");
        if(t.shouldChange(c))throw new AssertionError("Skip is consumed");
        RoundMusic.Rotation fresh=new RoundMusic.Rotation(new RoundMusic.ExpandedStart(),false,0xfff);
        String only=fresh.next("");fresh.skip(-1);
        if(!fresh.next(only).equals(only))throw new AssertionError("Back with no history restarts the track");
    }
    private static void playlist(){
        transport();
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
