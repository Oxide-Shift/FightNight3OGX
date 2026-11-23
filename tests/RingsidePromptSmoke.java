package org.oxideshift.corner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

final class RingsidePromptSmoke {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] paths)throws Exception {
        FightBroadcast.Bout paused=FightBroadcast.Bout.parse("1 47 99 88 99 99");
        if(paused==null || paused.levels[1]!=88 || !FightBroadcast.Bout.time(paused.seconds).equals("0:47") ||
           !FightBroadcast.Bout.time(180).equals("3:00") || FightBroadcast.Bout.parse("1 47 101 88 99 99")!=null)
            throw new AssertionError("Retail bout packet");
        FightBroadcast.Song trax=FightBroadcast.Song.parse("004A006100620073 004D0026004D");
        if(trax==null || !trax.title.equals("Jabs") || !trax.artist.equals("M&M") ||
           FightBroadcast.Song.parse("0000 0041")!=null)throw new AssertionError("Disc EA Trax packet");

        check(new BroadcastCues().route(LiveFrame.Scene.PAUSE)==BroadcastCues.NONE,"Pause menu stays upstairs");
        DeskScene desk=new DeskScene();
        check(desk.layout(LiveFrame.Scene.CORNER,false)==LiveFrame.Scene.RINGSIDE,"Retail healing chooser stays upstairs");
        check(desk.layout(LiveFrame.Scene.TREATMENT,false)==LiveFrame.Scene.TREATMENT,"Manual opens treatment desk");
        check(desk.observe(LiveFrame.Scene.MENU,15,100),"initial selection");
        check(!desk.observe(LiveFrame.Scene.RINGSIDE,0,220),"single bad frame");
        check(!desk.observe(LiveFrame.Scene.MENU,15,340),"recovered selection");
        check(!desk.observe(LiveFrame.Scene.MENU,0,450),"first unmarked frame");
        check(!desk.observe(LiveFrame.Scene.MENU,0,730),"fading caption");
        check(desk.observe(LiveFrame.Scene.MENU,0,900),"genuine menu transition");
        check(!desk.observe(LiveFrame.Scene.ROUND,0,1000),"round settles");
        check(desk.observe(LiveFrame.Scene.ROUND,0,1160),"bout shown");
        check(desk.layout(LiveFrame.Scene.RINGSIDE,true)==LiveFrame.Scene.ROUND,"Hold bout during impact close-ups");
        check(desk.layout(LiveFrame.Scene.RINGSIDE,false)==LiveFrame.Scene.RINGSIDE,"Leave bout for posted scores");
        check(desk.layout(LiveFrame.Scene.PAUSE,true)==LiveFrame.Scene.PAUSE,"Recognized pause wins immediately");
        check(!desk.observe(LiveFrame.Scene.FINAL,0,1200) && desk.settling(),"Final bell settling");
        check(desk.observe(LiveFrame.Scene.FINAL,0,1400) && !desk.settling(),"Final bell displayed");
        check(!desk.observe(LiveFrame.Scene.MENU,9,1500) && desk.settling(),"Static menu needs one more observation");
        check(desk.observe(LiveFrame.Scene.MENU,9,1720) && !desk.settling(),"Menu replaces result on the same frame");
        FightBroadcast.Fighters pair=FightBroadcast.Fighters.parse("524159204C454F4E415244 4D49434B592057415244");
        check(pair!=null && pair.blue.equals("RAY LEONARD") && pair.red.equals("MICKY WARD"),"Selected full names");
        check(FightBroadcast.Fighters.parse("- -")==null && FightBroadcast.Fighters.parse("A0 00")==null,"Reject partial name rows");
        BoutFrameTimes timing=new BoutFrameTimes();timing.active(true);
        for(int i=0;i<100;i++)timing.frame(1L+i*33333333L);
        timing.active(false);timing.frame(10000000000L);
        check(timing.reading().startsWith("30.0 FPS"),timing.reading());
        FightBroadcast.Fighters cards=FightBroadcast.Fighters.parse("4741545449 57415244 39,7,0,30 38,13,0,27");
        check(cards.records[0].bouts()==46 && cards.records[1].knockouts==27,"Roster totals");
        check(FightBroadcast.Record.parse("0,0,0,0").bouts()==0,"Debut fighter");
        check(FightBroadcast.Record.parse("1,0,0,2")==null,"Invalid KO count");
        check(FightBroadcast.Fighters.parse("4741545449 57415244 - 38,13,0,27").records[0]==null,"Missing career record");
        FightBroadcast.Bout counts=FightBroadcast.Bout.parse("1 43 100 90 93 82 9 16 5 12");
        check(counts!=null && counts.punches[3]==12 && FightBroadcast.Bout.parse("1 43 100 90 93 82 5 4 1 2")==null,"Live punch counts");
        BoutFrameTimes skipped=new BoutFrameTimes();skipped.active(true);
        for(int i=0;i<100;i++)skipped.frame(1L+i*33333333L,i*2);
        check(skipped.reading().contains("30.0 FPS displayed") && skipped.reading().contains("60.0 FPS produced"),skipped.reading());
        for(String path:paths){
            ByteBuffer pixels=ByteBuffer.wrap(Files.readAllBytes(Paths.get(path))).order(ByteOrder.LITTLE_ENDIAN);
            LiveFrame frame=new LiveFrame(pixels,pixels.getInt(0),pixels.getInt(4),1,true);
            String capture=Paths.get(path).getFileName().toString();
            boolean gallery=frame.scene(false)==LiveFrame.Scene.MENU && new MenuContext().mainMenu(frame);
            check(gallery==capture.startsWith("main"),"Achievements availability on "+capture);
            boolean scout=capture.startsWith("contract-offer");
            check(frame.scoutPrompt()==scout,"Scout caption "+capture);
            BroadcastCues cues=new BroadcastCues();cues.read(frame);
            int mask=cues.knownPrompts(frame,BroadcastCues.ALL_PROMPTS);
            System.out.println(Paths.get(path).getFileName()+" keys="+cues.mask+" native="+mask);
            if(Paths.get(path).getFileName().toString().startsWith("boxer")){
                check(mask==15,"Select, Back, Ratings and Help on boxer select");
                int badgeLeft=frame.sourceX(28)*frame.width/640;
                int badgeRight=frame.sourceX(92)*frame.width/640;
                for(int y=380*frame.height/480;y<448*frame.height/480;y++)
                    for(int x=badgeLeft;x<badgeRight;x++)pixels.putInt(8+4*(y*frame.width+x),0xff382a21);
                cues.read(frame);
                check(!BroadcastCues.hasMenuLogo(frame) && frame.scene(false)==LiveFrame.Scene.MENU &&
                      cues.knownPrompts(frame,15)==15,"Unbadged menu keeps its touch controls");
                int from=frame.sourceX(cues.centers[2]+18)*frame.width/640;
                int until=frame.sourceX(cues.centers[2]+FooterLegend.RIGHT[2])*frame.width/640;
                for(int y=417*frame.height/480;y<438*frame.height/480;y++)
                    for(int x=from;x<=until;x++)pixels.putInt(8+4*(y*frame.width+x),0xff361f17);
                check(cues.knownPrompts(frame,15)==11,"Unrecognized X caption must stay upstairs");
            }else if(Paths.get(path).getFileName().toString().startsWith("career")){
                int retire=1<<BroadcastCues.RETIRE;
                check(mask==(retire|2|8),"Career offers Retire, Back and Help without Ratings");
                check(BroadcastCues.KEYS[BroadcastCues.RETIRE]==CornerControls.Key.X,"Retire must send X");
                int left=frame.sourceX(cues.centers[BroadcastCues.RETIRE]+15)*frame.width/640;
                int right=frame.sourceX(cues.centers[BroadcastCues.RETIRE]+83)*frame.width/640;
                for(int y=417*frame.height/480;y<438*frame.height/480;y++)
                    for(int x=left;x<right;x++)pixels.putInt(8+4*(y*frame.width+x),0xff361f17);
                check(cues.knownPrompts(frame,retire)==0,"Old Retire touch cannot send X after the caption changes");
            }else if(Paths.get(path).getFileName().toString().startsWith("main")){
                check((mask&(1<<BroadcastCues.RETIRE))==0 && (mask&8)!=0 && cues.route(LiveFrame.Scene.MENU)==BroadcastCues.FOOTER,
                        "The main menu keeps Y Help beside the track banner");
                check(BroadcastCues.KEYS[3]==CornerControls.Key.Y,"Lower Help must send Y");
            }else if(Paths.get(path).getFileName().toString().startsWith("round"))check(mask==0,"No menu prompts in the ring");
        }
    }
}
