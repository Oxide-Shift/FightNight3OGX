package org.oxideshift.corner;

import java.io.*;
import java.util.Map;

public final class SessionSleepSmoke {
    public static void main(String[] args)throws Exception{
        if(args.length>0){peer();return;}
        File java=new File(System.getProperty("java.home"),"bin/java");
        CornerSession s=new CornerSession(()->System.nanoTime()/1_000_000,new File("."),java.getPath(),
                "-cp",System.getProperty("java.class.path"),SessionSleepSmoke.class.getName(),"peer");
        s.setSleeping(true);
        Thread worker=new Thread(s::run);worker.start();
        try{
            Thread.sleep(500);
            expect(s.snapshot(),"Sleeping",0);
            s.setSleeping(false);waitReplies(s,3);
            for(int i=0;i<4;i++){
                s.setSleeping(true);s.setSleeping(true);
                Thread.sleep(100);
                int count=(int)s.snapshot().get("replies");
                s.controller("PAD 000000000000000000000000000000000000");
                Thread.sleep(530);expect(s.snapshot(),"Sleeping",count);
                s.setSleeping(false);s.setSleeping(false);waitReplies(s,count+2);
            }
            s.setSleeping(true);
        }finally{s.stop();worker.join(3000);}
        if(worker.isAlive() || !"Closed".equals(s.snapshot().get("link")))throw new AssertionError("Parked exit");
        System.out.println("Session started asleep, stopped its heartbeat, resumed the same link four times and exited while parked.");
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
        boolean sleeping=false;
        System.out.println("FNR3/1 HELLO");
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in));
        for(String line;(line=input.readLine())!=null;){
            if(line.equals("SLEEP")){if(sleeping)throw new AssertionError(line);sleeping=true;}
            else if(line.equals("WAKE")){if(!sleeping)throw new AssertionError(line);sleeping=false;}
            else if(sleeping)throw new AssertionError("Command while parked "+line);
            else if(line.startsWith("PING "))System.out.println("FNR3/1 PONG "+line.substring(5));
            else if(line.equals("RUN"))System.out.println("FNR3/1 LINK-CHECK");
        }
    }
}
