package org.oxideshift.corner;

import java.io.*;
import java.util.Map;

public final class SessionSleepSmoke {
    public static void main(String[] args)throws Exception{
        if(args.length>0){peer();return;}
        File java=new File(System.getProperty("java.home"),"bin/java");
        CornerSession s=new CornerSession(()->System.nanoTime()/1_000_000,new File("."),java.getPath(),
                "-cp",System.getProperty("java.class.path"),SessionSleepSmoke.class.getName(),"peer");
        s.controller("PAD 000000000000000000000000000000000000");
        s.setSleeping(true);
        Thread worker=new Thread(s::run);worker.start();
        try{
            Thread.sleep(500);
            expect(s.snapshot(),"Sleeping",0);
            s.setSleeping(false);waitReplies(s,3);
            checkPlaylistNavigation(s);
            for(int i=0;i<4;i++){
                s.setSleeping(true);s.setSleeping(true);
                Thread.sleep(100);
                int count=(int)s.snapshot().get("replies");
                s.controller("PAD 000000000000000000000000000000000000");
                Thread.sleep(530);expect(s.snapshot(),"Sleeping",count);
                s.setSleeping(false);s.setSleeping(false);waitReplies(s,count+2);
                FightPad pad=new FightPad();
                int device=20+i;
                pad.key(device,android.view.InputDevice.SOURCE_GAMEPAD,android.view.KeyEvent.KEYCODE_BUTTON_A,true);
                pad.axes(device,.75f,0,0,0,0,1,0,0);
                s.controller(pad.report(null,false,0,0));waitForPad(s,6);
                pad.remove(device);s.controller(pad.report(null,false,0,0));waitForPad(s,7);
            }
            s.setSleeping(true);
        }finally{s.stop();worker.join(3000);}
        if(worker.isAlive() || !"Closed".equals(s.snapshot().get("link")))throw new AssertionError("Parked exit");
        System.out.println("Four wakes restored the P1 packet and delivered A, D-pad, stick and release from four device IDs.");
    }
    private static void waitForPad(CornerSession s,int receipt)throws Exception{
        long end=System.nanoTime()+2_000_000_000L;
        while(s.frontPage!=receipt && System.nanoTime()<end)Thread.sleep(5);
        if(s.frontPage!=receipt)throw new AssertionError("Missing post-wake input receipt "+receipt);
    }
    private static void checkPlaylistNavigation(CornerSession s)throws Exception{
        s.line("FNR3/1 FRONT 2");s.line("FNR3/1 FRONT 1");
        if(s.takeMusicRequest())throw new AssertionError("An unrelated exit opened Music");
        s.line("FNR3/1 MUSICMENU OPEN");
        if(!s.takeMusicRequest() || s.playlistOpening())throw new AssertionError("Music entry opened EA Trax");
        s.cabinetOpen=true;
        if(!s.requestPlaylist())throw new AssertionError("Open row unavailable");
        s.line("FNR3/1 PLAYLIST OPENING");s.line("FNR3/1 FRONT 2");
        if(!s.playlistOpening() || !s.browsingTrax)throw new AssertionError("Missing EA Trax transition");
        s.cabinetOpen=false;s.line("FNR3/1 FRONT 1");
        if(!s.takeMusicRequest() || !s.takePlaylistReturn() || s.playlistOpening())
            throw new AssertionError("EA Trax did not return to the Open row");
        s.menuCancelSound();s.line("FNR3/1 FRONT 1");
        if(s.takeMusicRequest() || s.browsingTrax)throw new AssertionError("Back reopened EA Trax");
    }
    private static void waitReplies(CornerSession s,int min)throws Exception{
        long deadline=System.nanoTime()+4_000_000_000L;
        while((int)s.snapshot().get("replies")<min && System.nanoTime()<deadline)Thread.sleep(15);
        Map<String,Object> m=s.snapshot();
        if(!"Live".equals(m.get("link")) || (int)m.get("replies")<min)throw new AssertionError(m);
    }
    private static void expect(Map<String,Object> m,String state,int replies){
        if(!state.equals(m.get("link")) || (int)m.get("replies")!=replies)throw new AssertionError(m);
    }
    private static void peer()throws Exception{
        boolean sleeping=false,running=false,expectPad=false;
        System.out.println("FNR3/1 HELLO");
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in));
        for(String line;(line=input.readLine())!=null;){
            if(line.equals("SLEEP")){if(sleeping)throw new AssertionError(line);sleeping=true;}
            else if(line.equals("WAKE")){if(!sleeping)throw new AssertionError(line);sleeping=false;expectPad=running;}
            else if(sleeping)throw new AssertionError("Command while parked "+line);
            else if(line.startsWith("PAD ")){
                boolean neutral=line.equals("PAD 000000000000000000000000000000000000");
                if(!neutral && (expectPad || !line.startsWith("PAD 0200ff") || line.substring(24,28).equals("0000")))
                    throw new AssertionError(line);
                expectPad=false;
                System.out.println("FNR3/1 MAINHOVER "+(neutral?7:6));
            }else if(line.startsWith("PING ")){
                if(expectPad)throw new AssertionError("Missing controller state after wake");
                System.out.println("FNR3/1 PONG "+line.substring(5));
            }else if(line.equals("RUN")){running=true;System.out.println("FNR3/1 LINK-CHECK");}
        }
    }
}
